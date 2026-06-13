package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPriceTest {

    private fun entry(amount: Double?, qty: String?, item: String? = "rice") =
        SaleEntry(item = item, qty = qty, amount = amount, type = "cash", party = null)

    private fun item(sellPrice: Double) = ItemEntity(id = 1, name = "rice", sellPrice = sellPrice)

    @Test
    fun explicitAmountAlwaysWins() {
        assertEquals(80.0, computeAutoPrice(entry(amount = 80.0, qty = "2 kg"), item(40.0))!!, 0.001)
        // wins even when no item resolves (short-circuit before inventory access)
        assertEquals(80.0, computeAutoPrice(entry(amount = 80.0, qty = null), null)!!, 0.001)
    }

    @Test
    fun computesFromSellPriceTimesQtyWhenAmountNull() {
        assertEquals(80.0, computeAutoPrice(entry(amount = null, qty = "2 kg"), item(40.0))!!, 0.001)
    }

    @Test
    fun nullWhenItemNotResolved() {
        assertNull(computeAutoPrice(entry(amount = null, qty = "2 kg"), null))
    }

    @Test
    fun nullWhenSellPriceZeroOrUnset() {
        assertNull(computeAutoPrice(entry(amount = null, qty = "2 kg"), item(0.0)))
    }

    @Test
    fun nullWhenQtyMissingOrZero() {
        assertNull(computeAutoPrice(entry(amount = null, qty = null), item(40.0)))
        assertNull(computeAutoPrice(entry(amount = null, qty = "0"), item(40.0)))
    }
}
