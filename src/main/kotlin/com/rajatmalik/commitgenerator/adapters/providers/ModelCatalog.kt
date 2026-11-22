package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.ports.AIProvider
import com.rajatmalik.commitgenerator.core.ports.ClockPort
import com.rajatmalik.commitgenerator.core.ports.ModelInfo

private data class CacheEntry(
    val models: List<ModelInfo>,
    val at: Long,
)

class ModelCatalog(
    private val clock: ClockPort,
    private val ttlMs: Long = 24 * 60 * 60 * 1000L,
) {
    private val cache = mutableMapOf<String, CacheEntry>()

    @Synchronized
    fun list(profile: ProviderProfile, provider: AIProvider, signal: CancelSignal): CwResult<List<ModelInfo>> {
        val key = "${profile.provider.key}|${profile.id}|${profile.baseUrl}"
        val cached = cache[key]
        val now = clock.now()
        if (cached != null && now - cached.at < ttlMs) {
            return CwResult.Ok(cached.models)
        }

        val result = provider.listModels(profile, signal)
        if (result is CwResult.Ok) {
            cache[key] = CacheEntry(result.value, now)
        }
        return result
    }

    @Synchronized
    fun invalidate(profileId: String? = null) {
        if (profileId == null) {
            cache.clear()
            return
        }
        cache.keys.filter { it.contains("|$profileId|") }.forEach(cache::remove)
    }
}
