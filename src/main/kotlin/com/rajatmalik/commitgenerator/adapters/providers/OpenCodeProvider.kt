package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.AppError
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.NeverCancelled
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.json.Json
import com.rajatmalik.commitgenerator.core.json.array
import com.rajatmalik.commitgenerator.core.json.asObjectOrNull
import com.rajatmalik.commitgenerator.core.json.asStringOrNull
import com.rajatmalik.commitgenerator.core.json.string
import com.rajatmalik.commitgenerator.core.ports.AIProvider
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import com.rajatmalik.commitgenerator.core.ports.GenerateResult
import com.rajatmalik.commitgenerator.core.ports.HttpClientPort
import com.rajatmalik.commitgenerator.core.ports.HttpMethod
import com.rajatmalik.commitgenerator.core.ports.HttpRequestData
import com.rajatmalik.commitgenerator.core.ports.ModelInfo

/**
 * OpenCode local server adapter, verified against the V1 HTTP API:
 * /session, /session/:id/message, /session/:id/abort, DELETE /session/:id,
 * and /config/providers.
 */
class OpenCodeProvider(
    private val http: HttpClientPort,
    private val auth: AuthResolver,
) : AIProvider {
    override val id: ProviderId = ProviderId.OPENCODE
    override val supportsModelListing: Boolean = true

    override fun generate(profile: ProviderProfile, request: GenerateRequest): CwResult<GenerateResult> {
        val model = splitModel(profile.model)
            ?: return err(
                appError(
                    ErrorCode.PROFILE_INVALID,
                    "OpenCode model must be written as \"providerID/modelID\".",
                    providerId = id.key,
                ),
            )

        val authHeaders = when (val result = auth.resolve(profile)) {
            is CwResult.Ok -> result.value
            is CwResult.Err -> return result
        }
        val headers = mapOf("content-type" to "application/json", "accept" to "application/json") + authHeaders
        val root = profile.baseUrl.trimEnd('/')

        val sessionId = when (val session = createSession(root, headers, request)) {
            is CwResult.Ok -> session.value
            is CwResult.Err -> return session
        }

        try {
            var lastError: AppError = appError(ErrorCode.INTERNAL, "OpenCode did not return a result.")
            for (attempt in 0..request.maxRetries) {
                if (request.signal.isCancelled) {
                    return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
                }
                val response = http.send(
                    HttpRequestData(
                        url = "$root/session/${encode(sessionId)}/message",
                        method = HttpMethod.POST,
                        headers = headers,
                        body = Json.stringify(
                            mapOf(
                                "model" to mapOf(
                                    "providerID" to model.first,
                                    "modelID" to model.second,
                                ),
                                "system" to request.system,
                                "parts" to listOf(mapOf("type" to "text", "text" to request.user)),
                            ),
                        ),
                        timeoutMs = request.timeoutMs,
                        signal = request.signal,
                    ),
                )
                when (response) {
                    is CwResult.Err -> {
                        lastError = response.error
                        if (lastError.cancelled) {
                            return response
                        }
                    }

                    is CwResult.Ok -> {
                        if (response.value.status in 200..299) {
                            val parsed = parseMessage(response.value.text)
                            if (parsed is CwResult.Ok) {
                                return parsed
                            }
                            lastError = (parsed as CwResult.Err).error
                        } else {
                            lastError = normalizeProviderError(
                                response.value.status,
                                response.value.text,
                                id.key,
                                profile.model,
                            )
                        }
                    }
                }
                if (!shouldRetry(lastError, attempt, request.maxRetries)) {
                    return err(lastError)
                }
                abortableDelay(backoffMs(attempt), request.signal)
            }
            return err(lastError)
        } finally {
            if (request.signal.isCancelled) {
                abortSession(root, headers, sessionId)
            }
            deleteSession(root, headers, sessionId)
        }
    }

    override fun listModels(profile: ProviderProfile, signal: CancelSignal): CwResult<List<ModelInfo>> {
        val authHeaders = when (val result = auth.resolve(profile)) {
            is CwResult.Ok -> result.value
            is CwResult.Err -> return result
        }
        val response = http.send(
            HttpRequestData(
                url = "${profile.baseUrl.trimEnd('/')}/config/providers",
                method = HttpMethod.GET,
                headers = mapOf("accept" to "application/json") + authHeaders,
                body = null,
                timeoutMs = 10_000,
                signal = signal,
            ),
        )
        if (response is CwResult.Err) {
            return response
        }
        val value = (response as CwResult.Ok).value
        if (value.status !in 200..299) {
            return err(normalizeProviderError(value.status, value.text, id.key))
        }
        val payload = Json.parse(value.text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "OpenCode model list is not valid JSON."))
        val models = mutableListOf<ModelInfo>()
        for (entry in payload.array("providers")) {
            val record = entry.asObjectOrNull() ?: continue
            val providerId = record.string("id") ?: continue
            val rawModels = record["models"]
            val ids = when (rawModels) {
                is com.rajatmalik.commitgenerator.core.json.JsonValue.Arr ->
                    rawModels.items.mapNotNull { it.asObjectOrNull()?.string("id") ?: it.asStringOrNull() }

                is com.rajatmalik.commitgenerator.core.json.JsonValue.Obj -> rawModels.fields.keys.toList()
                else -> emptyList()
            }
            for (modelId in ids) {
                models += ModelInfo("$providerId/$modelId", "$providerId/$modelId")
            }
        }
        return ok(models)
    }

    private fun createSession(
        root: String,
        headers: Map<String, String>,
        request: GenerateRequest,
    ): CwResult<String> {
        val response = http.send(
            HttpRequestData(
                url = "$root/session",
                method = HttpMethod.POST,
                headers = headers,
                body = Json.stringify(mapOf("title" to "${Product.NAME} commit message")),
                timeoutMs = minOf(request.timeoutMs, 15_000),
                signal = request.signal,
            ),
        )
        if (response is CwResult.Err) {
            return response
        }
        val value = (response as CwResult.Ok).value
        if (value.status !in 200..299) {
            return err(normalizeProviderError(value.status, value.text, id.key))
        }
        val payload = Json.parse(value.text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "OpenCode did not return valid JSON."))
        val sessionId = payload.string("id")
            ?: payload["session"]?.asObjectOrNull()?.string("id")
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "OpenCode did not return a session id."))
        return ok(sessionId)
    }

    private fun parseMessage(text: String): CwResult<GenerateResult> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "OpenCode returned a response that is not valid JSON."))
        val output = payload.array("parts")
            .mapNotNull { it.asObjectOrNull()?.string("text") }
            .joinToString("")
            .trim()
        if (output.isEmpty()) {
            return err(appError(ErrorCode.INVALID_RESPONSE, "OpenCode returned an empty completion."))
        }
        return ok(GenerateResult(output))
    }

    private fun abortSession(root: String, headers: Map<String, String>, sessionId: String) {
        runCatching {
            http.send(
                HttpRequestData(
                    url = "$root/session/${encode(sessionId)}/abort",
                    method = HttpMethod.POST,
                    headers = headers,
                    body = "{}",
                    timeoutMs = 3_000,
                    signal = NeverCancelled,
                ),
            )
        }
    }

    private fun deleteSession(root: String, headers: Map<String, String>, sessionId: String) {
        runCatching {
            http.send(
                HttpRequestData(
                    url = "$root/session/${encode(sessionId)}",
                    method = HttpMethod.DELETE,
                    headers = headers,
                    body = null,
                    timeoutMs = 3_000,
                    signal = NeverCancelled,
                ),
            )
        }
    }

    private fun splitModel(model: String): Pair<String, String>? {
        val index = model.indexOf('/')
        if (index <= 0 || index == model.length - 1) {
            return null
        }
        return model.substring(0, index) to model.substring(index + 1)
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
