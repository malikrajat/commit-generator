package com.rajatmalik.commitgenerator.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactorTest {
    @Test
    fun `redacts common provider credentials`() {
        val input = """
            OPENAI_API_KEY=sk-proj-abcdefghijklmnopqrstuvwxyz123456
            github_token=ghp_1234567890abcdefghijklmnopqrstuvwxyz
            Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456
            password = "correct-horse-battery-staple"
            url = postgres://user:supersecret@example.com/db
        """.trimIndent()

        val result = redactSecrets(input)

        assertTrue(result.redactions >= 5)
        assertFalse(result.text.contains("sk-proj-abcdefghijklmnopqrstuvwxyz123456"))
        assertFalse(result.text.contains("ghp_1234567890abcdefghijklmnopqrstuvwxyz"))
        assertFalse(result.text.contains("correct-horse-battery-staple"))
        assertFalse(result.text.contains("supersecret"))
        assertTrue(result.text.contains("[REDACTED"))
    }

    @Test
    fun `redacts private key blocks`() {
        val key = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA1234567890
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        assertFalse(redactSecrets(key).text.contains("MIIEowIBAAKCAQEA"))
    }

    @Test
    fun `keeps git hashes and ordinary code`() {
        val input = "commit 3f786850e387550fdab836ed7e6dc881de23001b\nfun add(a: Int, b: Int) = a + b"
        val result = redactSecrets(input)
        assertTrue(result.text.contains("3f786850e387550fdab836ed7e6dc881de23001b"))
        assertTrue(result.text.contains("fun add"))
    }
}
