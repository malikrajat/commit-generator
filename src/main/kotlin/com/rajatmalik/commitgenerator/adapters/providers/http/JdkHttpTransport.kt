package com.rajatmalik.commitgenerator.adapters.providers.http

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.ports.HttpClientPort
import com.rajatmalik.commitgenerator.core.ports.HttpRequestData
import com.rajatmalik.commitgenerator.core.ports.HttpResponseData
import com.rajatmalik.commitgenerator.core.security.evaluateUrl
import com.rajatmalik.commitgenerator.core.security.redactSecrets
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/**
 * The only `java.net.http` call site in the product. Timeouts, cancellation,
 * redirect policy and URL validation are wired here so no adapter can forget
 * them.
 */
class JdkHttpTransport : HttpClientPort {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    override fun send(request: HttpRequestData): CwResult<HttpResponseData> {
        if (request.signal.isCancelled) {
            return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
        }
        val urlCheck = evaluateUrl(request.url, "provider")
        if (urlCheck is CwResult.Err) {
            return urlCheck
        }
        val uri = runCatching { URI.create(request.url) }.getOrNull()
            ?: return err(appError(ErrorCode.BAD_REQUEST, "The provider URL is invalid."))

        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(request.timeoutMs.toLong()))
        for ((name, value) in request.headers) {
            val normalized = name.lowercase()
            if (normalized in RESTRICTED_HEADERS) {
                continue
            }
            runCatching { builder.header(name, value) }
        }
        runCatching { builder.setHeader("Accept", request.headers["accept"] ?: "application/json") }

        when (request.method) {
            com.rajatmalik.commitgenerator.core.ports.HttpMethod.GET -> builder.GET()
            com.rajatmalik.commitgenerator.core.ports.HttpMethod.DELETE -> builder.DELETE()
            com.rajatmalik.commitgenerator.core.ports.HttpMethod.POST -> {
                if (request.body.isNullOrEmpty()) {
                    builder.POST(HttpRequest.BodyPublishers.noBody())
                } else {
                    builder.POST(HttpRequest.BodyPublishers.ofString(request.body, Charsets.UTF_8))
                }
            }
        }

        val future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        val subscription = request.signal.onCancel { future.cancel(true) }

        try {
            val response = future.get(request.timeoutMs + 5_000L, TimeUnit.MILLISECONDS)
            if (response.statusCode() in 300..399) {
                return err(
                    appError(
                        ErrorCode.BAD_REQUEST,
                        "The endpoint redirected the request; redirects are not followed.",
                        httpStatus = response.statusCode(),
                    ),
                )
            }
            val responseHeaders = response.headers().map().entries.associate { (key, values) ->
                key.lowercase() to (values.firstOrNull() ?: "")
            }
            return ok(
                HttpResponseData(
                    status = response.statusCode(),
                    headers = responseHeaders,
                    text = response.body(),
                ),
            )
        } catch (_: CancellationException) {
            return err(if (request.signal.isCancelled) appError(ErrorCode.CANCELLED, "Generation cancelled.")
            else appError(ErrorCode.NETWORK, "The provider request was interrupted.", retryable = true))
        } catch (_: TimeoutException) {
            future.cancel(true)
            return err(appError(ErrorCode.TIMEOUT, "The provider did not respond before the timeout.", retryable = true))
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (request.signal.isCancelled || cause is CancellationException) {
                return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
            }
            if (cause is HttpTimeoutException) {
                return err(appError(ErrorCode.TIMEOUT, "The provider did not respond before the timeout.", retryable = true))
            }
            if (cause is SSLException || cause?.cause is SSLException) {
                return err(appError(ErrorCode.TLS, "TLS negotiation with the provider failed."))
            }
            if (cause is ConnectException || cause is java.net.UnknownHostException) {
                return err(appError(ErrorCode.NETWORK, "Could not reach the provider endpoint.", retryable = true))
            }
            return err(appError(ErrorCode.NETWORK, "The provider request failed.", retryable = true))
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
        } finally {
            subscription.close()
        }
    }

    private companion object {
        val RESTRICTED_HEADERS = setOf(
            "host", "content-length", "connection", "expect", "upgrade",
            "proxy-connection", "transfer-encoding",
        )
    }
}

/**
 * Redaction interceptor. Request bodies are redacted before serialization to
 * the socket, and failed responses are redacted before any caller can surface
 * them. Successful response bodies are untouched so model output is never
 * corrupted.
 */
class RedactingHttpClient(private val inner: HttpClientPort) : HttpClientPort {
    override fun send(request: HttpRequestData): CwResult<HttpResponseData> {
        val body = request.body?.let { redactSecrets(it).text }
        val result = inner.send(request.copy(body = body))
        if (result is CwResult.Ok && result.value.status >= 400) {
            return ok(result.value.copy(text = redactSecrets(result.value.text).text))
        }
        return result
    }
}
