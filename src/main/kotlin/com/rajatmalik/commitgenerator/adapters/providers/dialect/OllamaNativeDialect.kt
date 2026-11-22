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
import com.rajatmalik.commitgenerator.core.json.int
import com.rajatmalik.commitgenerator.core.json.obj
import com.rajatmalik.commitgenerator.core.json.string
import com.rajatmalik.commitgenerator.core.ports.FinishReason
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import com.rajatmalik.commitgenerator.core.ports.GenerateResult
import com.rajatmalik.commitgenerator.core.ports.ModelInfo
import com.rajatmalik.commitgenerator.core.ports.Usage

/**
 * Ollama's native API. It differs from the OpenAI format in endpoint, request
 * envelope (`options.num_predict`) and response shape (`message.content`).
 */
object OllamaNativeDialect : Dialect {
    override val id: WireDialect = WireDialect.OLLAMA_NATIVE

    override fun buildGenerate(context: DialectContext, request: GenerateRequest): DialectRequest = DialectRequest(
        url = appendPath(baseUrlOf(context.profile), "api/chat"),
        headers = jsonHeaders(context.authHeaders) + context.profile.headers,
        body = Json.stringify(
            mapOf(
                "model" to request.model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to request.system),
                    mapOf("role" to "user", "content" to request.user),
                ),
                "stream" to false,
                "options" to mapOf(
                    "temperature" to request.temperature,
                    "num_predict" to request.maxOutputTokens,
                ),
            ),
        ),
    )

    override fun parseGenerate(status: Int, text: String): CwResult<GenerateResult> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned a response that is not valid JSON."))
        val output = payload.obj("message")?.string("content") ?: ""
        if (output.isBlank()) {
            return err(appError(ErrorCode.INVALID_RESPONSE, "The provider returned an empty completion."))
        }
        return ok(
            GenerateResult(
                text = output,
                finishReason = if (payload.string("done_reason") == "length") FinishReason.LENGTH else FinishReason.STOP,
                usage = Usage(
                    payload.int("prompt_eval_count") ?: 0,
                    payload.int("eval_count") ?: 0,
                ),
            ),
        )
    }

    override fun buildModels(context: DialectContext): DialectRequest = DialectRequest(
        url = appendPath(baseUrlOf(context.profile), "api/tags"),
        headers = mapOf("accept" to "application/json") + context.authHeaders,
        body = null,
    )

    override fun parseModels(status: Int, text: String): CwResult<List<ModelInfo>> {
        val payload = Json.parse(text)?.asObjectOrNull()
            ?: return err(appError(ErrorCode.INVALID_RESPONSE, "The provider model list is not valid JSON."))
        val models = payload.array("models").mapNotNull { entry ->
            val record = entry.asObjectOrNull() ?: return@mapNotNull null
            val name = record.string("name") ?: return@mapNotNull null
            ModelInfo(name, name)
        }
        return ok(models)
    }
}
