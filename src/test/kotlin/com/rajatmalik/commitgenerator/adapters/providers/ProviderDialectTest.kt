package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.adapters.providers.dialect.AnthropicMessagesDialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.DialectContext
import com.rajatmalik.commitgenerator.adapters.providers.dialect.GeminiGenerateDialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.OllamaNativeDialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.OpenAiChatDialect
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.valueUnsafe
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.domain.NeverCancelled
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderDialectTest {
    private fun profile(provider: ProviderId, dialect: WireDialect, model: String = "test-model") = ProviderProfile(
        id = "test-profile",
        label = "Test",
        provider = provider,
        dialect = dialect,
        baseUrl = "https://example.com/v1",
        model = model,
    )

    private fun request() = GenerateRequest(
        model = "test-model",
        system = "system",
        user = "user",
        maxOutputTokens = 64,
        temperature = 0.2,
        timeoutMs = 10_000,
        maxRetries = 1,
        signal = NeverCancelled,
    )

    @Test
    fun `openai request and response`() {
        val context = DialectContext(profile(ProviderId.OPENAI, WireDialect.OPENAI_CHAT), mapOf("authorization" to "Bearer x"))
        val built = OpenAiChatDialect.buildGenerate(context, request())
        assertTrue(built.url.endsWith("/chat/completions"))
        assertTrue(built.body!!.contains("\"model\":\"test-model\""))

        val parsed = OpenAiChatDialect.parseGenerate(
            200,
            """{"id":"r1","choices":[{"message":{"content":"feat: add x"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5}}""",
        )
        assertTrue(parsed is CwResult.Ok)
        assertEquals("feat: add x", parsed.valueUnsafe().text)
        assertEquals(10, parsed.valueUnsafe().usage!!.input)
    }

    @Test
    fun `anthropic request and response`() {
        val context = DialectContext(profile(ProviderId.ANTHROPIC, WireDialect.ANTHROPIC_MESSAGES), mapOf("x-api-key" to "x"))
        val built = AnthropicMessagesDialect.buildGenerate(context, request())
        assertTrue(built.url.endsWith("/v1/messages"))
        assertTrue(built.headers.containsKey("anthropic-version"))

        val parsed = AnthropicMessagesDialect.parseGenerate(
            200,
            """{"id":"a1","content":[{"type":"text","text":"fix: correct y"}],"stop_reason":"end_turn","usage":{"input_tokens":8,"output_tokens":4}}""",
        )
        assertTrue(parsed is CwResult.Ok)
        assertEquals("fix: correct y", parsed.valueUnsafe().text)
    }

    @Test
    fun `gemini request and response`() {
        val context = DialectContext(profile(ProviderId.GEMINI, WireDialect.GEMINI_GENERATE, "gemini-1.5-flash"), emptyMap())
        val built = GeminiGenerateDialect.buildGenerate(context, request())
        assertTrue(built.url.contains(":generateContent"))

        val parsed = GeminiGenerateDialect.parseGenerate(
            200,
            """{"candidates":[{"content":{"parts":[{"text":"chore: update z"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":6,"candidatesTokenCount":3}}""",
        )
        assertTrue(parsed is CwResult.Ok)
        assertEquals("chore: update z", parsed.valueUnsafe().text)
    }

    @Test
    fun `ollama request and response`() {
        val context = DialectContext(profile(ProviderId.OLLAMA, WireDialect.OLLAMA_NATIVE), emptyMap())
        val built = OllamaNativeDialect.buildGenerate(context, request())
        assertTrue(built.url.endsWith("/api/chat"))

        val parsed = OllamaNativeDialect.parseGenerate(
            200,
            """{"message":{"content":"refactor: simplify q"},"done_reason":"stop","prompt_eval_count":4,"eval_count":2}""",
        )
        assertTrue(parsed is CwResult.Ok)
        assertEquals("refactor: simplify q", parsed.valueUnsafe().text)
    }

    @Test
    fun `empty completions are rejected`() {
        val parsed = OpenAiChatDialect.parseGenerate(200, """{"choices":[{"message":{"content":""}}]}""")
        assertTrue(parsed is CwResult.Err)
    }

    @Test
    fun `model lists parse`() {
        val models = OpenAiChatDialect.parseModels(200, """{"data":[{"id":"a"},{"id":"b"}]}""")
        assertTrue(models is CwResult.Ok)
        assertEquals(listOf("a", "b"), models.valueUnsafe().map { it.id })
    }
}
