package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.WireDialect

enum class PresetAuth {
    BEARER,
    ANTHROPIC,
    GEMINI,
    BASIC,
    NONE,
}

data class ProviderPreset(
    val id: ProviderId,
    val label: String,
    val dialect: WireDialect,
    val baseUrl: String,
    val auth: PresetAuth,
    val supportsModelListing: Boolean,
    val defaultContextWindow: Int,
    val modelHint: String,
)

val PROVIDER_PRESETS: Map<ProviderId, ProviderPreset> = mapOf(
    ProviderId.DEEPSEEK to ProviderPreset(
        ProviderId.DEEPSEEK, "DeepSeek", WireDialect.OPENAI_CHAT,
        "https://api.deepseek.com/v1", PresetAuth.BEARER, true, 64_000, "deepseek-chat",
    ),
    ProviderId.OPENAI to ProviderPreset(
        ProviderId.OPENAI, "OpenAI", WireDialect.OPENAI_CHAT,
        "https://api.openai.com/v1", PresetAuth.BEARER, true, 128_000, "gpt-4o-mini",
    ),
    ProviderId.ANTHROPIC to ProviderPreset(
        ProviderId.ANTHROPIC, "Anthropic (Claude)", WireDialect.ANTHROPIC_MESSAGES,
        "https://api.anthropic.com", PresetAuth.ANTHROPIC, true, 200_000, "claude-3-5-sonnet-latest",
    ),
    ProviderId.GEMINI to ProviderPreset(
        ProviderId.GEMINI, "Google Gemini", WireDialect.GEMINI_GENERATE,
        "https://generativelanguage.googleapis.com", PresetAuth.GEMINI, true, 1_000_000, "gemini-1.5-flash",
    ),
    ProviderId.OPENROUTER to ProviderPreset(
        ProviderId.OPENROUTER, "OpenRouter", WireDialect.OPENAI_CHAT,
        "https://openrouter.ai/api/v1", PresetAuth.BEARER, true, 128_000, "openai/gpt-4o-mini",
    ),
    ProviderId.OLLAMA to ProviderPreset(
        ProviderId.OLLAMA, "Ollama (local)", WireDialect.OLLAMA_NATIVE,
        "http://127.0.0.1:11434", PresetAuth.NONE, true, 8_192, "qwen2.5-coder:7b",
    ),
    ProviderId.LMSTUDIO to ProviderPreset(
        ProviderId.LMSTUDIO, "LM Studio (local)", WireDialect.OPENAI_CHAT,
        "http://127.0.0.1:1234/v1", PresetAuth.NONE, true, 8_192, "local-model",
    ),
    ProviderId.LLAMACPP to ProviderPreset(
        ProviderId.LLAMACPP, "llama.cpp server (local)", WireDialect.OPENAI_CHAT,
        "http://127.0.0.1:8080/v1", PresetAuth.NONE, true, 8_192, "local-model",
    ),
    ProviderId.VLLM to ProviderPreset(
        ProviderId.VLLM, "vLLM (local)", WireDialect.OPENAI_CHAT,
        "http://127.0.0.1:8000/v1", PresetAuth.NONE, true, 8_192, "local-model",
    ),
    ProviderId.OPENCODE to ProviderPreset(
        ProviderId.OPENCODE, "OpenCode (local server)", WireDialect.OPENCODE_SESSION,
        "http://127.0.0.1:4096", PresetAuth.BASIC, true, 128_000, "anthropic/claude-sonnet-4",
    ),
    ProviderId.CUSTOM to ProviderPreset(
        ProviderId.CUSTOM, "Custom OpenAI-compatible endpoint", WireDialect.OPENAI_CHAT,
        "", PresetAuth.BEARER, true, 128_000, "gpt-4o-mini",
    ),
)

fun presetFor(providerId: ProviderId): ProviderPreset = PROVIDER_PRESETS.getValue(providerId)
