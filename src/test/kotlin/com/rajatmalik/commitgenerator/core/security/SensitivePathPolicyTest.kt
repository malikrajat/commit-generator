package com.rajatmalik.commitgenerator.core.security

import com.rajatmalik.commitgenerator.core.domain.Sensitivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensitivePathPolicyTest {
    @Test
    fun `blocks environment files and key material`() {
        listOf(".env", ".env.local", "config/.env.production", "certs/server.pem", "id_ed25519", "secrets/token.txt")
            .forEach { path ->
                assertEquals(Sensitivity.BLOCKED, classifySensitivePath(path).sensitivity, path)
            }
    }

    @Test
    fun `flags suspicious infrastructure files`() {
        assertEquals(Sensitivity.SUSPICIOUS, classifySensitivePath("kubeconfig").sensitivity)
        assertEquals(Sensitivity.SUSPICIOUS, classifySensitivePath("infra/prod.tfvars").sensitivity)
    }

    @Test
    fun `allows ordinary source paths`() {
        assertEquals(Sensitivity.NONE, classifySensitivePath("src/main/kotlin/App.kt").sensitivity)
        assertTrue(!isBlockedPath("README.md"))
    }
}
