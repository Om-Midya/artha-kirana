package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
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
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val useCase = LogSaleUseCase(sales, inventory, khata, customers)

    @Test
    fun creditSaleSnapshotsPriceLinksCustomerAndAppliesCredit() = runTest {
        val item = ItemEntity(id = 7, name = "rice", qtyInStock = 10.0, sellPrice = 40.0, costPrice = 30.0)
        coEvery { inventory.findByName("rice") } returns item
        coEvery { customers.resolveOrCreate("Ramesh") } returns 3L
        coEvery { sales.logSale(any()) } returns 42L

        val entry = SaleEntry(item = "rice", qty = "2 kg", amount = 80.0, type = "credit", party = "Ramesh")
        val id = useCase(entry, inputMethod = "typed", rawInput = "raw")

        assertEquals(42L, id)
        coVerify(exactly = 1) {
            sales.logSale(
                match {
                    it.type == "credit" && it.amount == 80.0 && it.party == "Ramesh" &&
                        it.customerId == 3L && it.itemId == 7L && it.itemName == "rice" &&
                        it.qtySold == 2.0 && it.unitPrice == 40.0 && it.unitCost == 30.0 &&
                        it.inputMethod == "typed"
                },
            )
        }
        coVerify(exactly = 1) { inventory.decrementStock(7L, 2.0) }
        coVerify(exactly = 1) { khata.applyCredit("Ramesh", 80.0, 42L) }
    }

    @Test
    fun anonymousCashSaleHasNoCustomerId() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { sales.logSale(any()) } returns 1L

        val entry = SaleEntry(item = null, qty = null, amount = 50.0, type = "cash", party = null)
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 0) { customers.resolveOrCreate(any()) }
        coVerify(exactly = 1) { sales.logSale(match { it.customerId == null && it.unitPrice == null }) }
        coVerify(exactly = 0) { inventory.decrementStock(any(), any()) }
        coVerify(exactly = 0) { khata.applyCredit(any(), any(), any()) }
    }

    @Test
    fun repaymentAppliesRepayment() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { customers.resolveOrCreate("Ramesh") } returns 4L
        coEvery { sales.logSale(any()) } returns 9L

        val entry = SaleEntry(item = null, qty = null, amount = 50.0, type = "repayment", party = "Ramesh")
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 1) { khata.applyRepayment("Ramesh", 50.0, 9L) }
    }

    @Test
    fun itemWithZeroPriceSnapshotsNullNotZero() = runTest {
        val item = ItemEntity(id = 3, name = "mystery", qtyInStock = 5.0, sellPrice = 0.0, costPrice = 0.0)
        coEvery { inventory.findByName("mystery") } returns item
        coEvery { sales.logSale(any()) } returns 1L

        val entry = SaleEntry(item = "mystery", qty = "1", amount = 10.0, type = "cash", party = null)
        useCase(entry, inputMethod = "typed", rawInput = null)

        coVerify(exactly = 1) { sales.logSale(match { it.unitPrice == null && it.unitCost == null }) }
    }
}
