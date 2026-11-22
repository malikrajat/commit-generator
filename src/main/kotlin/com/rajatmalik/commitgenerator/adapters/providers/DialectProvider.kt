package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.adapters.providers.dialect.Dialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.DialectContext
import com.rajatmalik.commitgenerator.core.domain.AppError
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.ports.AIProvider
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import com.rajatmalik.commitgenerator.core.ports.GenerateResult
import com.rajatmalik.commitgenerator.core.ports.HttpClientPort
import com.rajatmalik.commitgenerator.core.ports.HttpMethod
import com.rajatmalik.commitgenerator.core.ports.HttpRequestData
import com.rajatmalik.commitgenerator.core.ports.ModelInfo

/** Shared implementation for every wire-dialect provider. */
class DialectProvider(
    override val id: ProviderId,
    private val dialect: Dialect,
    private val http: HttpClientPort,
    private val auth: AuthResolver,
    override val supportsModelListing: Boolean,
) : AIProvider {

    override fun generate(profile: ProviderProfile, request: GenerateRequest): CwResult<GenerateResult> {
        val authHeaders = when (val result = auth.resolve(profile)) {
            is CwResult.Ok -> result.value
            is CwResult.Err -> return result
        }
        val context = DialectContext(profile, authHeaders)
        var lastError: AppError = appError(ErrorCode.INTERNAL, "The provider did not return a result.")

        for (attempt in 0..request.maxRetries) {
            if (request.signal.isCancelled) {
                return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
            }

            val built = dialect.buildGenerate(context, request.copy(model = profile.model))
            val response = http.send(
                HttpRequestData(
                    url = built.url,
                    method = HttpMethod.POST,
                    headers = built.headers,
                    body = built.body,
                    timeoutMs = request.timeoutMs,
                    signal = request.signal,
                ),
            )

            var retryAfter: Long? = null
            when (response) {
                is CwResult.Err -> {
                    lastError = response.error
                    if (lastError.cancelled) {
                        return response
                    }
                }

                is CwResult.Ok -> {
                    val status = response.value.status
                    if (status in 200..299) {
                        val parsed = dialect.parseGenerate(status, response.value.text)
                        if (parsed is CwResult.Ok) {
                            return parsed
                        }
                        lastError = (parsed as CwResult.Err).error
                    } else {
                        lastError = normalizeProviderError(
                            status = status,
                            detail = response.value.text,
                            providerId = this.id.key,
                            model = profile.model,
                        )
                        retryAfter = parseRetryAfterMs(response.value.headers)
                    }
                }
            }

            if (!shouldRetry(lastError, attempt, request.maxRetries)) {
                return err(lastError)
            }
            abortableDelay(backoffMs(attempt, retryAfter), request.signal)
        }
        return err(lastError)
    }

    override fun listModels(profile: ProviderProfile, signal: CancelSignal): CwResult<List<ModelInfo>> {
        if (!supportsModelListing) {
            return err(appError(ErrorCode.BAD_REQUEST, "This provider does not expose a model list."))
        }
        val authHeaders = when (val result = auth.resolve(profile)) {
            is CwResult.Ok -> result.value
            is CwResult.Err -> return result
        }
        val context = DialectContext(profile, authHeaders)
        val built = dialect.buildModels(context)
            ?: return err(appError(ErrorCode.BAD_REQUEST, "This provider does not expose a model list."))

        val response = http.send(
            HttpRequestData(
                url = built.url,
                method = HttpMethod.GET,
                headers = built.headers,
                body = null,
                timeoutMs = 15_000,
                signal = signal,
            ),
        )
        return when (response) {
            is CwResult.Err -> response
            is CwResult.Ok -> {
                if (response.value.status !in 200..299) {
                    err(
                        normalizeProviderError(
                            status = response.value.status,
                            detail = response.value.text,
                            providerId = this.id.key,
                        ),
                    )
                } else {
                    dialect.parseModels(response.value.status, response.value.text)
                }
            }
        }
    }
}
