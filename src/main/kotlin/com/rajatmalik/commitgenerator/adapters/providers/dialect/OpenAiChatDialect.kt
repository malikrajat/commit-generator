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

object OpenAiChatDialect : Dialect {
    override val id: WireDialect = WireDialect.OPENAI_CHAT

    override fun buildGenerate(context: DialectContext, request: GenerateRequest): DialectRequest {
        val base = baseUrlOf(context.profile)
        return DialectRequest(
            url = appendPath(base, "chat/completions"),
            headers = jsonHeaders(context.authHeaders) + context.profile.headers,
            body = Json.stringify(
                mapOf(
                    "model" to request.model,
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to request.system),
                        mapOf("role" to "user", "content" to request.user),
                    ),
                    "temperature" to request.temperature,
                    "max_tokens" to request.maxOutputTokens,
                    "stream" to false,
                ),
            ),
        )
    }

    override fun parseGenerate(status: Int, text: String): CwResult<GenerateResult> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned a response that is not valid JSON."))
        val first = payload.array("choices").firstOrNull()?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned no completion choices."))
        val message = first.obj("message")
        val output = when (val content = message?.get("content")) {
            is com.rajatmalik.commitgenerator.core.json.JsonValue.Str -> content.value
            is com.rajatmalik.commitgenerator.core.json.JsonValue.Arr -> content.items.joinToString("") {
                it.asObjectOrNull()?.string("text") ?: ""
            }

            else -> ""
        }
        if (output.isBlank()) {
            return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned an empty completion."))
        }

        val finish = first.string("finish_reason")
        val usage = payload.obj("usage")
        return ok(
            GenerateResult(
                text = output,
                finishReason = when (finish) {
                    "length" -> FinishReason.LENGTH
                    "content_filter" -> FinishReason.FILTERED
                    else -> FinishReason.STOP
                },
                usage = usage?.let {
                    Usage(it.int("prompt_tokens") ?: 0, it.int("completion_tokens") ?: 0)
                },
                requestId = payload.string("id"),
            ),
        )
    }

    override fun buildModels(context: DialectContext): DialectRequest = DialectRequest(
        url = appendPath(baseUrlOf(context.profile), "models"),
        headers = mapOf("accept" to "application/json") + context.authHeaders,
        body = null,
    )

    override fun parseModels(status: Int, text: String): CwResult<List<ModelInfo>> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider model list is not valid JSON."))
        val models = payload.array("data").mapNotNull { entry ->
            val record = entry.asObjectOrNull() ?: return@mapNotNull null
            val id = record.string("id") ?: return@mapNotNull null
            ModelInfo(id, id, record.int("context_length"))
        }
        return ok(models)
    }
}
