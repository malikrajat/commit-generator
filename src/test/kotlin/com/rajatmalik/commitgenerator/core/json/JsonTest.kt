package com.rajatmalik.commitgenerator.core.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {
    @Test
    fun `parses nested payloads`() {
        val value = Json.parse("""{"a":1,"b":[true,null,"x\n"],"c":{"d":2.5}}""")!!
        val obj = value.asObjectOrNull()!!
        assertEquals(1, obj["a"]!!.asIntOrNull())
        val array = obj["b"]!!.asArrayOrNull()!!
        assertEquals(3, array.size)
        assertEquals("x\n", array[2].asStringOrNull())
        assertEquals(2.5, obj["c"]!!.asObjectOrNull()!!["d"]!!.asNumberOrNull())
    }

    @Test
    fun `stringifies and escapes values`() {
        val json = Json.stringify(
            mapOf(
                "model" to "gpt-4o",
                "messages" to listOf(mapOf("role" to "user", "content" to "line\n\"quoted\"")),
                "temperature" to 0.2,
            ),
        )
        assertTrue(json.contains("\"model\":\"gpt-4o\""))
        assertTrue(json.contains("line\\n\\\"quoted\\\""))
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(Json.parse("{"))
        assertNull(Json.parse("not json"))
    }
}
