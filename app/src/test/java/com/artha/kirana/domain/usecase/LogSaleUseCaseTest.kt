package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogSaleUseCaseTest {

    private val sales = mockk<SalesRepository>(relaxed = true)
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val khata = mockk<KhataRepository>(relaxed = true)
    private val useCase = LogSaleUseCase(sales, inventory, khata)

    @Test
    fun creditSaleInsertsDecrementsStockAndAppliesCredit() = runTest {
        val item = ItemEntity(id = 7, name = "rice", qtyInStock = 10.0)
        coEvery { inventory.findByName("rice") } returns item
        coEvery { sales.logSale(any()) } returns 42L

        val entry = SaleEntry(item = "rice", qty = "2 kg", amount = 80.0, type = "credit", party = "Ramesh")
        val id = useCase(entry, inputMethod = "typed", rawInput = "raw")

        assertEquals(42L, id)
        coVerify(exactly = 1) {
            sales.logSale(
                match {
                    it.type == "credit" && it.amount == 80.0 && it.party == "Ramesh" &&
                        it.itemId == 7L && it.qtySold == 2.0 && it.inputMethod == "typed"
                },
            )
        }
        coVerify(exactly = 1) { inventory.decrementStock(7L, 2.0) }
        coVerify(exactly = 1) { khata.applyCredit("Ramesh", 80.0, 42L) }
    }

    @Test
    fun cashSaleWithUnknownItemSkipsStockAndKhata() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { sales.logSale(any()) } returns 1L

        val entry = SaleEntry(item = null, qty = null, amount = 50.0, type = "cash", party = null)
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 0) { inventory.decrementStock(any(), any()) }
        coVerify(exactly = 0) { khata.applyCredit(any(), any(), any()) }
        coVerify(exactly = 0) { khata.applyRepayment(any(), any(), any()) }
    }

    @Test
    fun repaymentAppliesRepayment() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { sales.logSale(any()) } returns 9L

        val entry = SaleEntry(item = null, qty = null, amount = 50.0, type = "repayment", party = "Ramesh")
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 1) { khata.applyRepayment("Ramesh", 50.0, 9L) }
    }
}
