package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.PurchaseRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogPurchaseUseCaseTest {
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val purchases = mockk<PurchaseRepository>(relaxed = true)
    private val subject = LogPurchaseUseCase(inventory, purchases)

    @Test
    fun `existing item is restocked and cost refreshed`() = runTest {
        val existing = ItemEntity(id = 7, name = "Rice", unit = "kg", costPrice = 30.0, sellPrice = 45.0)
        coEvery { inventory.findByName("Rice") } returns existing
        val updated = slot<ItemEntity>()
        coEvery { inventory.updateItem(capture(updated)) } just Runs

        subject(listOf(ParsedPurchaseItem("Rice", qty = 2.0, unit = "kg", unitPrice = 40.0, amount = 80.0)), supplier = "Acme")

        coVerify { inventory.incrementStock(7, 2.0) }
        assertEquals(40.0, updated.captured.costPrice, 0.0)
        coVerify { purchases.add(match<PurchaseEntity> { it.itemId == 7L && it.qty == 2.0 && it.cost == 80.0 && it.supplier == "Acme" }) }
    }

    @Test
    fun `unknown item is created then stocked`() = runTest {
        coEvery { inventory.findByName("Maggi") } returns null
        coEvery { inventory.addItem(any()) } returns 42L

        subject(listOf(ParsedPurchaseItem("Maggi", qty = 12.0, unit = "pcs", unitPrice = 10.0, amount = 120.0)), supplier = null)

        coVerify { inventory.addItem(match<ItemEntity> { it.name == "Maggi" && it.unit == "pcs" && it.costPrice == 10.0 }) }
        coVerify { inventory.incrementStock(42L, 12.0) }
        coVerify { purchases.add(match<PurchaseEntity> { it.itemId == 42L && it.cost == 120.0 }) }
    }

    @Test
    fun `cost falls back to unitPrice times qty when amount missing`() = runTest {
        coEvery { inventory.findByName("Sugar") } returns null
        coEvery { inventory.addItem(any()) } returns 5L

        subject(listOf(ParsedPurchaseItem("Sugar", qty = 3.0, unit = "kg", unitPrice = 20.0, amount = null)), supplier = null)

        coVerify { purchases.add(match<PurchaseEntity> { it.cost == 60.0 }) }
    }
}
