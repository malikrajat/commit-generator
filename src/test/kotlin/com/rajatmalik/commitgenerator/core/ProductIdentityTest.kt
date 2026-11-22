package com.rajatmalik.commitgenerator.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ProductIdentityTest {
    @Test
    fun `identity is stable`() {
        assertEquals("com.rajatmalik.commitgenerator", Product.ID)
        assertEquals("Commit Generator", Product.NAME)
        assertEquals("Commit Generator", Product.SHORT_NAME)
        assertEquals("Commit Generator Notifications", Product.NOTIFICATION_GROUP)
        assertEquals("CommitGeneratorSettings", Product.STATE_NAME)
        assertEquals("MIT", Product.LICENSE_NAME)
    }
}
