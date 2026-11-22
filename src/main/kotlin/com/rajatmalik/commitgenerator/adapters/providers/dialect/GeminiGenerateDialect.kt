package com.rajatmalik.commitgenerator.adapters.providers.dialect

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.json.Json
import com.rajatmalik.commitgenerator.core.json.array
import com.rajatmalik.commitgenerator.core.json.asArrayOrNull
import com.rajatmalik.commitgenerator.core.json.asObjectOrNull
import com.rajatmalik.commitgenerator.core.json.asStringOrNull
import com.rajatmalik.commitgenerator.core.json.int
import com.rajatmalik.commitgenerator.core.json.obj
import com.rajatmalik.commitgenerator.core.json.string
import com.rajatmalik.commitgenerator.core.ports.FinishReason
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import com.rajatmalik.commitgenerator.core.ports.GenerateResult
import com.rajatmalik.commitgenerator.core.ports.ModelInfo
import com.rajatmalik.commitgenerator.core.ports.Usage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Gemini's API key is sent as a header, never as a query parameter, so it can
 * never leak through URL logging.
 */
object GeminiGenerateDialect : Dialect {
    override val id: WireDialect = WireDialect.GEMINI_GENERATE

    override fun buildGenerate(context: DialectContext, request: GenerateRequest): DialectRequest {
        val model = URLEncoder.encode(request.model.removePrefix("models/"), StandardCharsets.UTF_8)
            .replace("+", "%20")
        return DialectRequest(
            url = "${baseUrlOf(context.profile)}/v1beta/models/$model:generateContent",
            headers = jsonHeaders(context.authHeaders) + context.profile.headers,
            body = Json.stringify(
                mapOf(
                    "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to request.system))),
                    "contents" to listOf(
                        mapOf("role" to "user", "parts" to listOf(mapOf("text" to request.user))),
                    ),
                    "generationConfig" to mapOf(
                        "temperature" to minOf(request.temperature, 2.0),
                        "maxOutputTokens" to request.maxOutputTokens,
                    ),
                ),
            ),
        )
    }

    override fun parseGenerate(status: Int, text: String): CwResult<GenerateResult> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned a response that is not valid JSON."))
        val first = payload.array("candidates").firstOrNull()?.asObjectOrNull()
        val output = first?.obj("content")?.array("parts")
            ?.joinToString("") { it.asObjectOrNull()?.string("text") ?: "" }
            ?: ""

        if (output.isBlank()) {
            val blocked = payload.obj("promptFeedback")?.get("blockReason")?.asStringOrNull()
            return err(
                appError(
                    ErrorCode.INVALID_RESPONSE,
                    if (blocked != null) "The provider blocked the request (safety filter)."
                    else "The provider returned an empty completion.",
                ),
            )
        }

        val finish = first?.string("finishReason")
        val usage = payload.obj("usageMetadata")
        return ok(
            GenerateResult(
                text = output,
                finishReason = when (finish) {
                    "MAX_TOKENS" -> FinishReason.LENGTH
                    "SAFETY", "RECITATION" -> FinishReason.FILTERED
                    else -> FinishReason.STOP
                },
                usage = usage?.let {
                    Usage(it.int("promptTokenCount") ?: 0, it.int("candidatesTokenCount") ?: 0)
                },
            ),
        )
    }

    override fun buildModels(context: DialectContext): DialectRequest = DialectRequest(
        url = "${baseUrlOf(context.profile)}/v1beta/models",
        headers = mapOf("accept" to "application/json") + context.authHeaders,
        body = null,
    )

    override fun parseModels(status: Int, text: String): CwResult<List<ModelInfo>> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider model list is not valid JSON."))
        val models = payload.array("models").mapNotNull { entry ->
            val record = entry.asObjectOrNull() ?: return@mapNotNull null
            val rawName = record.string("name") ?: return@mapNotNull null
            val methods = record.array("supportedGenerationMethods").mapNotNull { it.asStringOrNull() }
            if (methods.isNotEmpty() && "generateContent" !in methods) {
                return@mapNotNull null
            }
            val id = rawName.removePrefix("models/")
            ModelInfo(id, record.string("displayName") ?: id, record.int("inputTokenLimit"))
        }
        return ok(models)
    }
}
