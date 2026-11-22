package com.rajatmalik.commitgenerator.core.security

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlPolicyTest {
    @Test
    fun `accepts https endpoints`() {
        val result = evaluateUrl("https://api.openai.com/v1", "openai")
        assertTrue(result is CwResult.Ok)
    }

    @Test
    fun `accepts plain http only for loopback`() {
        assertTrue(evaluateUrl("http://127.0.0.1:11434", "ollama") is CwResult.Ok)
        assertTrue(evaluateUrl("http://localhost:1234/v1", "lmstudio") is CwResult.Ok)
        val remote = evaluateUrl("http://example.com/v1", "custom")
        assertTrue(remote is CwResult.Err)
        assertEquals(ErrorCode.URL_REJECTED, remote.error.code)
    }

    @Test
    fun `rejects embedded credentials and metadata addresses`() {
        assertTrue(evaluateUrl("https://user:pass@example.com/v1", "custom") is CwResult.Err)
        assertTrue(evaluateUrl("https://169.254.169.254/latest", "custom") is CwResult.Err)
    }

    @Test
    fun `local only mode rejects cloud endpoints`() {
        val result = evaluateUrl("https://api.deepseek.com/v1", "deepseek", localOnly = true)
        assertTrue(result is CwResult.Err)
        assertTrue(evaluateUrl("http://127.0.0.1:11434", "ollama", localOnly = true) is CwResult.Ok)
    }

    @Test
    fun `rejects non http schemes`() {
        assertTrue(evaluateUrl("file:///etc/passwd", "custom") is CwResult.Err)
        assertTrue(evaluateUrl("ftp://example.com", "custom") is CwResult.Err)
    }
}
