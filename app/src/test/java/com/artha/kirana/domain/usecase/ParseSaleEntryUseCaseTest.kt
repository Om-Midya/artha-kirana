package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseSaleEntryUseCaseTest {

    private val engine = mockk<LlmEngine>()
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val useCase = ParseSaleEntryUseCase(engine, inventory)

    @Test
    fun fillsAmountFromInventoryWhenNull() = runTest {
        coEvery { engine.parseSale(any()) } returns Result.success(
            listOf(SaleEntry(item = "rice", qty = "2 kg", amount = null, type = "cash", party = null)),
        )
        coEvery { inventory.findByName("rice") } returns ItemEntity(id = 1, name = "rice", sellPrice = 40.0)

        val result = useCase("do kilo chawal").getOrThrow()

        assertEquals(80.0, result.single().amount!!, 0.001)
    }

    @Test
    fun leavesExplicitAmountUntouched() = runTest {
        coEvery { engine.parseSale(any()) } returns Result.success(
            listOf(SaleEntry(item = "rice", qty = "2 kg", amount = 100.0, type = "cash", party = null)),
        )
        coEvery { inventory.findByName("rice") } returns ItemEntity(id = 1, name = "rice", sellPrice = 40.0)

        val result = useCase("do kilo chawal sau rupaye").getOrThrow()

        assertEquals(100.0, result.single().amount!!, 0.001)
    }

    @Test
    fun leavesAmountNullWhenItemNotInInventory() = runTest {
        coEvery { engine.parseSale(any()) } returns Result.success(
            listOf(SaleEntry(item = "mystery_item", qty = "2", amount = null, type = "cash", party = null)),
        )
        coEvery { inventory.findByName("mystery_item") } returns null

        val result = useCase("some input").getOrThrow()

        assertNull(result.single().amount)
    }
}
