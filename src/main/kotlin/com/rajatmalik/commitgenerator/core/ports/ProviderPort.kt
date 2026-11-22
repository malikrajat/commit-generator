package com.rajatmalik.commitgenerator.core.ports

import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile

data class Usage(
    val input: Int,
    val output: Int,
)

enum class FinishReason {
    STOP,
    LENGTH,
    FILTERED,
}

data class GenerateRequest(
    val model: String,
    val system: String,
    val user: String,
    val maxOutputTokens: Int,
    val temperature: Double,
    val timeoutMs: Int,
    val maxRetries: Int,
    val signal: CancelSignal,
)

data class GenerateResult(
    val text: String,
    val finishReason: FinishReason = FinishReason.STOP,
    val usage: Usage? = null,
    val requestId: String? = null,
)

data class ModelInfo(
    val id: String,
    val label: String,
    val contextWindow: Int? = null,
)

interface AIProvider {
    val id: ProviderId
    val supportsModelListing: Boolean
    fun generate(profile: ProviderProfile, request: GenerateRequest): CwResult<GenerateResult>
    fun listModels(profile: ProviderProfile, signal: CancelSignal): CwResult<List<ModelInfo>>
}

interface ProviderRegistryPort {
    fun get(profile: ProviderProfile): AIProvider
}
