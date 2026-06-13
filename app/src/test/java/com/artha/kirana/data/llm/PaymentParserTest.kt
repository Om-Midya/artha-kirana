package com.artha.kirana.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private val parser = PaymentParser()

    @Test
    fun parsesPartyAndAmount() {
        val raw = """{"party":"रमेश","amount":50}"""
        val result = parser.parse(raw)!!
        assertEquals("रमेश", result.party)
        assertEquals(50.0, result.amount!!, 0.001)
    }

    @Test
    fun parsesMarkdownFencedJson() {
        val raw = "```json\n{\"party\":\"Priya\",\"amount\":100}\n```"
        val result = parser.parse(raw)!!
        assertEquals("Priya", result.party)
        assertEquals(100.0, result.amount!!, 0.001)
    }

    @Test
    fun normalizesLiteralNullPartyToNull() {
        val raw = """{"party":"null","amount":75}"""
        val result = parser.parse(raw)!!
        assertNull(result.party)
        assertEquals(75.0, result.amount!!, 0.001)
    }

    @Test
    fun returnsNullOnGarbage() {
        assertNull(parser.parse("the model refused to answer"))
    }
}
