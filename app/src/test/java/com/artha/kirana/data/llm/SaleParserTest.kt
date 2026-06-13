package com.artha.kirana.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleParserTest {

    private val parser = SaleParser()

    @Test
    fun parsesCreditSaleWithHindiFields() {
        val raw = """{"entries":[{"item":"चावल","qty":"2 kg","amount":80,"type":"credit","party":"रमेश"}]}"""
        val result = parser.parse(raw)
        assertEquals(1, result.size)
        assertEquals("credit", result[0].type)
        assertEquals("रमेश", result[0].party)
        assertEquals(80.0, result[0].amount!!, 0.001)
    }

    @Test
    fun parsesMarkdownFencedJson() {
        val raw = "```json\n{\"entries\":[{\"item\":\"soap\",\"qty\":\"3\",\"amount\":60,\"type\":\"cash\",\"party\":null}]}\n```"
        val result = parser.parse(raw)
        assertEquals(1, result.size)
        assertEquals("cash", result[0].type)
        assertNull(result[0].party)
    }

    @Test
    fun parsesMultipleEntries() {
        val raw = """{"entries":[{"item":"rice","qty":"1","amount":50,"type":"cash","party":null},{"item":"dal","qty":"1","amount":70,"type":"cash","party":null}]}"""
        assertEquals(2, parser.parse(raw).size)
    }

    @Test
    fun returnsEmptyOnGarbage() {
        assertTrue(parser.parse("the model refused to answer").isEmpty())
    }

    @Test
    fun coercesNullTypeToCash() {
        val raw = """{"entries":[{"item":"x","qty":"1","amount":10,"type":null,"party":null}]}"""
        val result = parser.parse(raw)
        assertEquals(1, result.size)
        assertEquals("cash", result[0].type)
    }
}
