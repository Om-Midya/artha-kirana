package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class KhataRepositoryImplTest {

    private val khataDao = mockk<KhataDao>(relaxed = true)
    private val txnDao = mockk<KhataTransactionDao>(relaxed = true)
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val repo = KhataRepositoryImpl(khataDao, txnDao, customers)

    @Test
    fun applyCreditCreatesLedgerKeyedByResolvedCustomer() = runTest {
        coEvery { customers.resolveOrCreate("Ramesh") } returns 7L
        coEvery { khataDao.findByCustomerId(7L) } returns null

        repo.applyCredit("Ramesh", amount = 80.0, saleId = 5L)

        coVerify(exactly = 1) {
            khataDao.insert(match { it.customerId == 7L && it.partyName == "Ramesh" && it.balance == 80.0 })
        }
        coVerify(exactly = 1) { txnDao.insert(match { it.amount == 80.0 && it.type == "credit" && it.saleId == 5L }) }
    }

    @Test
    fun applyRepaymentUpdatesExistingLedger() = runTest {
        coEvery { customers.resolveOrCreate("Ramesh") } returns 7L
        coEvery { khataDao.findByCustomerId(7L) } returns
            KhataEntity(id = 1, customerId = 7L, partyName = "Ramesh", balance = 80.0)

        repo.applyRepayment("Ramesh", amount = 30.0, saleId = 9L)

        coVerify(exactly = 1) { khataDao.update(match { it.id == 1L && it.balance == 50.0 }) }
        coVerify(exactly = 1) { txnDao.insert(match { it.amount == 30.0 && it.type == "repayment" }) }
    }

    @Test
    fun reverseSaleEffectUndoesCreditBalanceAndDeletesTxns() = runTest {
        val party = KhataEntity(id = 1, customerId = 7L, partyName = "Ramesh", balance = 80.0)
        coEvery { txnDao.findBySaleId(5) } returns listOf(
            KhataTransactionEntity(id = 1, partyId = 1, amount = 80.0, type = "credit", saleId = 5),
        )
        coEvery { khataDao.findById(1) } returns party

        repo.reverseSaleEffect(5)

        coVerify(exactly = 1) { khataDao.update(match { it.id == 1L && it.balance == 0.0 }) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(5) }
    }

    @Test
    fun reverseSaleEffectWithNoTxnsJustDeletes() = runTest {
        coEvery { txnDao.findBySaleId(3) } returns emptyList()

        repo.reverseSaleEffect(3)

        coVerify(exactly = 0) { khataDao.update(any()) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(3) }
    }
}
