package com.rajatmalik.commitgenerator.core.domain

enum class ProviderId(val key: String, val label: String) {
    DEEPSEEK("deepseek", "DeepSeek"),
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic (Claude)"),
    GEMINI("gemini", "Google Gemini"),
    OPENROUTER("openrouter", "OpenRouter"),
    OLLAMA("ollama", "Ollama (local)"),
    LMSTUDIO("lmstudio", "LM Studio (local)"),
    LLAMACPP("llamacpp", "llama.cpp server (local)"),
    VLLM("vllm", "vLLM (local)"),
    OPENCODE("opencode", "OpenCode (local server)"),
    CUSTOM("custom", "Custom OpenAI-compatible endpoint");

    companion object {
        fun fromKey(value: String?): ProviderId? = entries.firstOrNull { it.key == value }

        val keys: List<String> = entries.map { it.key }
    }
}

enum class WireDialect(val key: String) {
    OPENAI_CHAT("openai-chat"),
    ANTHROPIC_MESSAGES("anthropic-messages"),
    GEMINI_GENERATE("gemini-generate"),
    OLLAMA_NATIVE("ollama-native"),
    OPENCODE_SESSION("opencode-session");

    companion object {
        fun fromKey(value: String?): WireDialect? = entries.firstOrNull { it.key == value }
    }
}

enum class ProfileAuthStyle(val key: String) {
    BEARER("bearer"),
    API_KEY_HEADER("api-key-header"),
    NONE("none");

    companion object {
        fun fromKey(value: String?): ProfileAuthStyle? = entries.firstOrNull { it.key == value }
    }
}

data class ProviderProfile(
    val id: String,
    val label: String,
    val provider: ProviderId,
    val dialect: WireDialect,
    val baseUrl: String,
    val model: String,
    val fallbackModel: String? = null,
    val contextWindow: Int = 128_000,
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 512,
    val timeoutMs: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val authStyle: ProfileAuthStyle? = null,
    val authHeaderName: String? = null,
)

data class ProfileValidation(
    val valid: Boolean,
    val problems: List<String>,
)

private val FORBIDDEN_HEADERS = setOf(
    "authorization",
    "x-api-key",
    "x-goog-api-key",
    "proxy-authorization",
    "cookie",
)

private val RESERVED_AUTH_HEADERS = setOf(
    "authorization",
    "cookie",
    "host",
    "content-length",
    "content-type",
    "accept",
    "connection",
)

private val HEADER_NAME = Regex("^[A-Za-z0-9-]+$")
private val PROFILE_ID = Regex("^[a-z0-9][a-z0-9-]{1,30}$")

fun isForbiddenHeader(name: String): Boolean = name.lowercase() in FORBIDDEN_HEADERS

fun isReservedAuthHeader(name: String): Boolean = name.lowercase() in RESERVED_AUTH_HEADERS

fun validateProfile(profile: ProviderProfile): ProfileValidation {
    val problems = mutableListOf<String>()
    if (!PROFILE_ID.matches(profile.id)) {
        problems += "profile id must match ^[a-z0-9][a-z0-9-]{1,30}$"
    }
    if (profile.label.isBlank()) {
        problems += "profile label must not be empty"
    } else if (profile.label.length > 60) {
        problems += "profile label must be at most 60 characters"
    }
    if (profile.model.isBlank()) {
        problems += "model must not be empty"
    } else if (profile.model.length > 120) {
        problems += "model must be at most 120 characters"
    }
    if (profile.baseUrl.isBlank()) {
        problems += "base URL must not be empty"
    } else if (profile.baseUrl.length > 2048) {
        problems += "base URL is too long"
    }
    if (profile.contextWindow < 1024) {
        problems += "context window must be at least 1024"
    }
    if (profile.temperature < 0.0 || profile.temperature > 2.0 || !profile.temperature.isFinite()) {
        problems += "temperature must be between 0 and 2"
    }
    if (profile.maxOutputTokens < 32 || profile.maxOutputTokens > 16_384) {
        problems += "max output tokens must be between 32 and 16384"
    }
    val timeout = profile.timeoutMs
    if (timeout != null && (timeout < 5_000 || timeout > 300_000)) {
        problems += "timeout must be between 5000 and 300000 ms"
    }
    for ((header, value) in profile.headers) {
        if (!HEADER_NAME.matches(header)) {
            problems += "header \"$header\" is not a valid HTTP header name"
        }
        if (isForbiddenHeader(header)) {
            problems += "header \"$header\" is reserved for credentials and cannot be configured"
        }
        if (value.contains('\r') || value.contains('\n')) {
            problems += "header \"$header\" contains a newline"
        }
    }
    if (profile.authStyle == ProfileAuthStyle.API_KEY_HEADER) {
        val name = profile.authHeaderName ?: "x-api-key"
        if (!HEADER_NAME.matches(name)) {
            problems += "auth header name must be a valid HTTP header name"
        } else if (isReservedAuthHeader(name)) {
            problems += "header \"$name\" cannot carry credentials"
        }
    }
    return ProfileValidation(problems.isEmpty(), problems)
}
