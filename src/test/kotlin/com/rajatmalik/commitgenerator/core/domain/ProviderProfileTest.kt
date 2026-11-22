package com.rajatmalik.commitgenerator.core.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderProfileTest {
    private fun profile(
        id: String = "openai-main",
        label: String = "OpenAI",
        model: String = "gpt-4o-mini",
        headers: Map<String, String> = emptyMap(),
        authStyle: ProfileAuthStyle? = null,
        authHeaderName: String? = null,
    ) = ProviderProfile(
        id = id,
        label = label,
        provider = ProviderId.OPENAI,
        dialect = WireDialect.OPENAI_CHAT,
        baseUrl = "https://api.openai.com/v1",
        model = model,
        headers = headers,
        authStyle = authStyle,
        authHeaderName = authHeaderName,
    )

    @Test
    fun `valid profile passes`() {
        assertTrue(validateProfile(profile()).valid)
    }

    @Test
    fun `invalid ids labels and models are rejected`() {
        assertFalse(validateProfile(profile(id = "Bad ID")).valid)
        assertFalse(validateProfile(profile(label = " ")).valid)
        assertFalse(validateProfile(profile(model = " ")).valid)
        assertFalse(validateProfile(profile(id = "a")).valid)
    }

    @Test
    fun `credential headers cannot be configured`() {
        val validation = validateProfile(profile(headers = mapOf("Authorization" to "Bearer nope")))
        assertFalse(validation.valid)
        assertTrue(validation.problems.any { it.contains("reserved") })
    }

    @Test
    fun `custom auth header must be safe`() {
        assertFalse(
            validateProfile(
                profile(authStyle = ProfileAuthStyle.API_KEY_HEADER, authHeaderName = "authorization"),
            ).valid,
        )
        assertTrue(
            validateProfile(
                profile(authStyle = ProfileAuthStyle.API_KEY_HEADER, authHeaderName = "x-custom-key"),
            ).valid,
        )
    }
}
