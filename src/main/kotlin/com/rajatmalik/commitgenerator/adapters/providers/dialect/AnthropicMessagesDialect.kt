package com.rajatmalik.commitgenerator.adapters.providers.dialect

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.json.Json
import com.rajatmalik.commitgenerator.core.json.array
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

private const val ANTHROPIC_VERSION = "2023-06-01"

object AnthropicMessagesDialect : Dialect {
    override val id: WireDialect = WireDialect.ANTHROPIC_MESSAGES

    override fun buildGenerate(context: DialectContext, request: GenerateRequest): DialectRequest {
        val base = baseUrlOf(context.profile)
        return DialectRequest(
            url = appendPath(base, "v1/messages"),
            headers = jsonHeaders(context.authHeaders) +
                mapOf("anthropic-version" to ANTHROPIC_VERSION) +
                context.profile.headers,
            body = Json.stringify(
                mapOf(
                    "model" to request.model,
                    "system" to request.system,
                    "messages" to listOf(mapOf("role" to "user", "content" to request.user)),
                    "max_tokens" to request.maxOutputTokens,
                    "temperature" to minOf(request.temperature, 1.0),
                ),
            ),
        )
    }

    override fun parseGenerate(status: Int, text: String): CwResult<GenerateResult> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned a response that is not valid JSON."))
        val output = payload.array("content")
            .mapNotNull { it.asObjectOrNull() }
            .filter { it.string("type") == "text" }
            .joinToString("") { it.string("text") ?: "" }
        if (output.isBlank()) {
            return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned an empty completion."))
        }

        val stop = payload.string("stop_reason")
        val usage = payload.obj("usage")
        return ok(
            GenerateResult(
                text = output,
                finishReason = when (stop) {
                    "max_tokens" -> FinishReason.LENGTH
                    "refusal" -> FinishReason.FILTERED
                    else -> FinishReason.STOP
                },
                usage = usage?.let {
                    Usage(it.int("input_tokens") ?: 0, it.int("output_tokens") ?: 0)
                },
                requestId = payload.string("id"),
            ),
        )
    }

    override fun buildModels(context: DialectContext): DialectRequest = DialectRequest(
        url = appendPath(baseUrlOf(context.profile), "v1/models"),
        headers = mapOf("accept" to "application/json", "anthropic-version" to ANTHROPIC_VERSION) +
            context.authHeaders,
        body = null,
    )

    override fun parseModels(status: Int, text: String): CwResult<List<ModelInfo>> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider model list is not valid JSON."))
        val models = payload.array("data").mapNotNull { entry ->
            val record = entry.asObjectOrNull() ?: return@mapNotNull null
            val id = record.string("id") ?: return@mapNotNull null
            ModelInfo(id, record.string("display_name") ?: id)
        }
        return ok(models)
    }
}
