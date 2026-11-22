package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.ProfileAuthStyle
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.ports.SecretKind
import com.rajatmalik.commitgenerator.core.ports.SecretStorePort
import java.util.Base64

/**
 * The only component allowed to read credentials. Values are converted straight
 * into request headers and are never returned to callers or logs.
 */
class AuthResolver(private val secrets: SecretStorePort) {

    fun resolve(profile: ProviderProfile): CwResult<Map<String, String>> {
        val preset = presetFor(profile.provider)
        val style = profile.authStyle

        if (style == ProfileAuthStyle.NONE) {
            return ok(emptyMap())
        }
        if (style == ProfileAuthStyle.API_KEY_HEADER) {
            val key = secrets.get(profile.id, SecretKind.API_KEY)
                ?: return err(
                    appError(
                        ErrorCode.SECRET_MISSING,
                        "No API key stored for profile \"${profile.label}\".",
                        providerId = profile.provider.key,
                    ),
                )
            return ok(mapOf((profile.authHeaderName ?: "x-api-key") to key))
        }
        if (style == ProfileAuthStyle.BEARER) {
            val key = secrets.get(profile.id, SecretKind.API_KEY)
                ?: return err(
                    appError(
                        ErrorCode.SECRET_MISSING,
                        "No API key stored for profile \"${profile.label}\".",
                        providerId = profile.provider.key,
                    ),
                )
            return ok(mapOf("authorization" to "Bearer $key"))
        }

        return when (preset.auth) {
            PresetAuth.NONE -> ok(emptyMap())
            PresetAuth.BASIC -> {
                val password = secrets.get(profile.id, SecretKind.BASIC_PASSWORD)
                if (password.isNullOrEmpty()) {
                    ok(emptyMap())
                } else {
                    val encoded = Base64.getEncoder()
                        .encodeToString("opencode:$password".toByteArray(Charsets.UTF_8))
                    ok(mapOf("authorization" to "Basic $encoded"))
                }
            }

            PresetAuth.ANTHROPIC -> secretHeader(profile, "x-api-key")
            PresetAuth.GEMINI -> secretHeader(profile, "x-goog-api-key")
            PresetAuth.BEARER -> secretHeader(profile, "authorization", "Bearer ")
        }
    }

    private fun secretHeader(profile: ProviderProfile, name: String, prefix: String = ""): CwResult<Map<String, String>> {
        val key = secrets.get(profile.id, SecretKind.API_KEY)
            ?: return err(
                appError(
                    ErrorCode.SECRET_MISSING,
                    "No API key stored for profile \"${profile.label}\".",
                    providerId = profile.provider.key,
                ),
            )
        return ok(mapOf(name to "$prefix$key"))
    }
}
