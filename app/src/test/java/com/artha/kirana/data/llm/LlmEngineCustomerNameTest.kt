package com.artha.kirana.data.llm

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmEngineCustomerNameTest {
    private val engine = LlmEngine(client = mockk(relaxed = true), saleParser = SaleParser(), paymentParser = PaymentParser())

    @Test
    fun parsesCustomerName() {
        assertEquals("Ramesh", engine.parseCustomerName("""{"customer":"Ramesh"}"""))
    }

    @Test
    fun trimsAndStripsNullLiterals() {
        assertEquals("Priya", engine.parseCustomerName("""{"customer":"  Priya  "}"""))
        assertNull(engine.parseCustomerName("""{"customer":"null"}"""))
        assertNull(engine.parseCustomerName("""{"customer":null}"""))
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(engine.parseCustomerName("not json"))
    }
}
