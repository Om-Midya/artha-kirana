package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EditSaleUseCaseTest {

    private val sales = mockk<SalesRepository>(relaxed = true)
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val khata = mockk<KhataRepository>(relaxed = true)
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val useCase = EditSaleUseCase(sales, inventory, khata, customers)

    @Test
    fun creditToCashReversesOldKhataAndAppliesNoNewKhata() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        val old = SaleEntity(
            id = 5, itemId = null, itemName = "चावल", qtySold = 0.0, amount = 80.0,
            type = "credit", party = "Ramesh", inputMethod = "typed", rawInput = null, timestamp = 111L,
        )
        val edited = SaleEntry(item = "चावल", qty = null, amount = 80.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 1) { khata.reverseSaleEffect(5) }
        coVerify(exactly = 0) { khata.applyCredit(any(), any(), any()) }
        coVerify(exactly = 0) { khata.applyRepayment(any(), any(), any()) }
        coVerify(exactly = 1) {
            sales.updateSale(match { it.id == 5L && it.type == "cash" && it.party == null && it.customerId == null && it.timestamp == 111L })
        }
    }

    @Test
    fun itemSwapReSnapshotsPriceAndAdjustsStock() = runTest {
        val sugar = ItemEntity(id = 2, name = "sugar", qtyInStock = 5.0, sellPrice = 30.0, costPrice = 22.0)
        coEvery { inventory.findByName("sugar") } returns sugar
        val old = SaleEntity(
            id = 7, itemId = 1, itemName = "rice", qtySold = 2.0, amount = 80.0,
            type = "cash", party = null, inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = "sugar", qty = "3", amount = 90.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 1) { inventory.incrementStock(1, 2.0) }
        coVerify(exactly = 1) { inventory.decrementStock(2, 3.0) }
        coVerify(exactly = 1) { khata.reverseSaleEffect(7) }
        coVerify(exactly = 1) {
            sales.updateSale(match {
                it.itemId == 2L && it.itemName == "sugar" && it.qtySold == 3.0 &&
                    it.unitPrice == 30.0 && it.unitCost == 22.0
            })
        }
    }

    @Test
    fun partyAndAmountChangeOnCreditLinksNewCustomerAndAppliesCredit() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        coEvery { customers.resolveOrCreate("Priya") } returns 8L
        val old = SaleEntity(
            id = 9, itemId = null, itemName = null, qtySold = 0.0, amount = 80.0,
            type = "credit", party = "Ramesh", inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = null, qty = null, amount = 50.0, type = "credit", party = "Priya")

        useCase(old, edited)

        coVerify(exactly = 1) { khata.reverseSaleEffect(9) }
        coVerify(exactly = 1) { khata.applyCredit("Priya", 50.0, 9) }
        coVerify(exactly = 1) { sales.updateSale(match { it.customerId == 8L && it.party == "Priya" }) }
    }

    @Test
    fun unknownNewItemSkipsDecrement() = runTest {
        coEvery { inventory.findByName(any()) } returns null
        val old = SaleEntity(
            id = 3, itemId = null, itemName = null, qtySold = 0.0, amount = 20.0,
            type = "cash", party = null, inputMethod = "typed", rawInput = null, timestamp = 0L,
        )
        val edited = SaleEntry(item = "mystery", qty = "2", amount = 20.0, type = "cash", party = null)

        useCase(old, edited)

        coVerify(exactly = 0) { inventory.decrementStock(any(), any()) }
        coVerify(exactly = 1) { sales.updateSale(match { it.itemName == "mystery" && it.itemId == null }) }
    }
}
