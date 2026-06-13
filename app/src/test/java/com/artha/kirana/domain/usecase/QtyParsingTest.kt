package com.artha.kirana.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class QtyParsingTest {

    @Test
    fun parsesLeadingNumberWithUnit() {
        assertEquals(2.0, parseLeadingQty("2 kg"), 0.001)
    }

    @Test
    fun parsesDecimal() {
        assertEquals(2.5, parseLeadingQty("2.5 kg"), 0.001)
    }

    @Test
    fun nullIsZero() {
        assertEquals(0.0, parseLeadingQty(null), 0.001)
    }

    @Test
    fun noDigitsIsZero() {
        assertEquals(0.0, parseLeadingQty("kuch"), 0.001)
    }
}
