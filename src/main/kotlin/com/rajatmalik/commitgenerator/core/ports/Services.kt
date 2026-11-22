package com.rajatmalik.commitgenerator.core.ports

import com.rajatmalik.commitgenerator.core.domain.EgressConsent
import com.rajatmalik.commitgenerator.core.domain.ResolvedConfig
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile

interface ConfigPort {
    fun read(): ResolvedConfig
}

enum class SecretKind(val key: String) {
    API_KEY("apiKey"),
    BASIC_PASSWORD("basicPassword"),
}

/**
 * Credential abstraction. Implementations must store values in the operating
 * system keychain and must never expose them through settings serialization.
 */
interface SecretStorePort {
    fun get(profileId: String, kind: SecretKind): String?
    fun set(profileId: String, kind: SecretKind, value: String)
    fun clear(profileId: String, kind: SecretKind)
    fun has(profileId: String, kind: SecretKind): Boolean
}

interface ConsentPort {
    fun isGranted(request: com.rajatmalik.commitgenerator.core.domain.ConsentRequest): Boolean
    fun record(request: com.rajatmalik.commitgenerator.core.domain.ConsentRequest)
    fun revokeAll()
    fun list(): List<EgressConsent>
}

interface ClockPort {
    fun now(): Long
}

interface LoggerPort {
    fun debug(message: String, metadata: Map<String, Any?> = emptyMap())
    fun info(message: String, metadata: Map<String, Any?> = emptyMap())
    fun warn(message: String, metadata: Map<String, Any?> = emptyMap())
    fun error(message: String, metadata: Map<String, Any?> = emptyMap())
}

interface ProfileCatalog {
    fun list(): List<ProviderProfile>
    fun activeProfile(): ProviderProfile?
    fun select(profileId: String): Boolean
    fun updateModel(profileId: String, model: String): Boolean
}
