package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.security.evaluateUrl

/**
 * Normalizes a configured profile into an effective one: fills preset defaults,
 * derives the dialect for non-custom providers, and applies the URL policy
 * before any network activity.
 */
fun resolveEffectiveProfile(
    profile: ProviderProfile,
    localOnly: Boolean = false,
): CwResult<ProviderProfile> {
    val preset = presetFor(profile.provider)
    val baseUrl = profile.baseUrl.trim().ifEmpty { preset.baseUrl }
    if (baseUrl.isEmpty()) {
        return err(
            appError(
                ErrorCode.PROFILE_INVALID,
                "Profile has no base URL.",
                providerId = profile.provider.key,
            ),
        )
    }

    val urlCheck = evaluateUrl(baseUrl, profile.provider.key, localOnly)
    if (urlCheck is CwResult.Err) {
        return urlCheck
    }

    return ok(
        profile.copy(
            baseUrl = baseUrl,
            dialect = if (profile.provider == com.rajatmalik.commitgenerator.core.domain.ProviderId.CUSTOM) {
                profile.dialect
            } else {
                preset.dialect
            },
            contextWindow = if (profile.contextWindow > 0) profile.contextWindow else preset.defaultContextWindow,
        ),
    )
}
