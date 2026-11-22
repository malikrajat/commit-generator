package com.rajatmalik.commitgenerator.secrets

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.ports.SecretKind
import com.rajatmalik.commitgenerator.core.ports.SecretStorePort

/**
 * Credential storage through JetBrains PasswordSafe (the operating-system
 * keychain). Secrets are never written to XML state, project files or logs.
 */
@Service(Service.Level.APP)
class PasswordSafeSecretStore : SecretStorePort {

    override fun get(profileId: String, kind: SecretKind): String? =
        runCatching { PasswordSafe.instance.getPassword(attributes(profileId, kind)) }.getOrNull()

    override fun set(profileId: String, kind: SecretKind, value: String) {
        if (value.isEmpty()) {
            clear(profileId, kind)
            return
        }
        PasswordSafe.instance.set(attributes(profileId, kind), Credentials(kind.key, value))
    }

    override fun clear(profileId: String, kind: SecretKind) {
        PasswordSafe.instance.set(attributes(profileId, kind), null)
    }

    override fun has(profileId: String, kind: SecretKind): Boolean = !get(profileId, kind).isNullOrEmpty()

    private fun attributes(profileId: String, kind: SecretKind): CredentialAttributes =
        CredentialAttributes("${Product.CREDENTIAL_SERVICE}:$profileId", kind.key)
}
