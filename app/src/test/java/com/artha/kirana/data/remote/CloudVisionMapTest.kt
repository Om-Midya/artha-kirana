package com.artha.kirana.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudVisionMapTest {

    @Test
    fun `maps items with defaults and fenced json`() {
        val content = """
            ```json
            {"items":[
              {"name":"Rice","qty":2,"unit":"kg","unitPrice":40,"amount":80},
              {"name":"Soap","unit":"pcs"}
            ],"total":80}
            ```
        """.trimIndent()
        val bill = CloudVisionClient.mapBill(content)
        assertEquals(2, bill.items.size)
        assertEquals("Rice", bill.items[0].name)
        assertEquals(2.0, bill.items[0].qty, 0.0)
        assertEquals(40.0, bill.items[0].unitPrice!!, 0.0)
        assertEquals(1.0, bill.items[1].qty, 0.0)
        assertEquals("pcs", bill.items[1].unit)
        assertNull(bill.items[1].unitPrice)
        assertEquals(80.0, bill.total!!, 0.0)
    }

    @Test
    fun `drops blank or null-named rows`() {
        val content = """{"items":[{"name":""},{"name":"null"},{"name":"Atta","qty":1}],"total":null}"""
        val bill = CloudVisionClient.mapBill(content)
        assertEquals(1, bill.items.size)
        assertEquals("Atta", bill.items[0].name)
        assertNull(bill.total)
    }

    @Test
    fun `unreadable content yields empty bill`() {
        val bill = CloudVisionClient.mapBill("sorry I cannot read this")
        assertTrue(bill.items.isEmpty())
        assertNull(bill.total)
    }
}
