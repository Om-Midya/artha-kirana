package com.artha.kirana.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonParserTest {

    @Test
    fun extractsPlainJson() {
        assertEquals("""{"a":1}""", JsonParser.extractJson("""{"a":1}"""))
    }

    @Test
    fun stripsMarkdownFences() {
        assertEquals("""{"a":1}""", JsonParser.extractJson("```json\n{\"a\":1}\n```"))
    }

    @Test
    fun stripsPreambleAndTrailing() {
        assertEquals("""{"a":1}""", JsonParser.extractJson("Here is the JSON: {\"a\":1} thanks"))
    }

    @Test
    fun extractsOutermostObjectWithNesting() {
        val raw = """prefix {"entries":[{"item":"rice"}]} suffix"""
        assertEquals("""{"entries":[{"item":"rice"}]}""", JsonParser.extractJson(raw))
    }

    @Test
    fun returnsNullWhenNoBraces() {
        assertNull(JsonParser.extractJson("no json here"))
    }

    @Test
    fun returnsNullWhenOnlyOpenBrace() {
        assertNull(JsonParser.extractJson("oops { broken"))
    }
}
