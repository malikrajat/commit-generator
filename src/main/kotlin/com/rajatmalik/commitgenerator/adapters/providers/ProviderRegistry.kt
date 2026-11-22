package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.adapters.providers.dialect.AnthropicMessagesDialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.Dialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.GeminiGenerateDialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.OllamaNativeDialect
import com.rajatmalik.commitgenerator.adapters.providers.dialect.OpenAiChatDialect
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.ports.AIProvider
import com.rajatmalik.commitgenerator.core.ports.HttpClientPort

private val DIALECTS: Map<WireDialect, Dialect> = mapOf(
    WireDialect.OPENAI_CHAT to OpenAiChatDialect,
    WireDialect.ANTHROPIC_MESSAGES to AnthropicMessagesDialect,
    WireDialect.GEMINI_GENERATE to GeminiGenerateDialect,
    WireDialect.OLLAMA_NATIVE to OllamaNativeDialect,
)

fun dialectFor(profile: ProviderProfile): Dialect? =
    if (profile.dialect == WireDialect.OPENCODE_SESSION) null else DIALECTS[profile.dialect] ?: OpenAiChatDialect

class ProviderRegistry(
    private val http: HttpClientPort,
    private val auth: AuthResolver,
) : com.rajatmalik.commitgenerator.core.ports.ProviderRegistryPort {
    private val cache = mutableMapOf<String, AIProvider>()

    @Synchronized
    override fun get(profile: ProviderProfile): AIProvider {
        val key = "${profile.provider.key}:${profile.dialect.key}"
        return cache.getOrPut(key) {
            if (profile.dialect == WireDialect.OPENCODE_SESSION) {
                OpenCodeProvider(http, auth)
            } else {
                DialectProvider(
                    id = profile.provider,
                    dialect = dialectFor(profile) ?: OpenAiChatDialect,
                    http = http,
                    auth = auth,
                    supportsModelListing = presetFor(profile.provider).supportsModelListing,
                )
            }
        }
    }
}
