package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class KhataRepositoryImplTest {

    private val khataDao = mockk<KhataDao>(relaxed = true)
    private val txnDao = mockk<KhataTransactionDao>(relaxed = true)
    private val repo = KhataRepositoryImpl(khataDao, txnDao)

    @Test
    fun reverseSaleEffectUndoesCreditBalanceAndDeletesTxns() = runTest {
        val party = KhataEntity(id = 1, partyName = "Ramesh", balance = 80.0)
        coEvery { txnDao.findBySaleId(5) } returns listOf(
            KhataTransactionEntity(id = 1, partyId = 1, amount = 80.0, type = "credit", saleId = 5),
        )
        coEvery { khataDao.findById(1) } returns party

        repo.reverseSaleEffect(5)

        // credit originally added +80 → reversing subtracts 80 → balance 0
        coVerify(exactly = 1) { khataDao.update(match { it.id == 1L && it.balance == 0.0 }) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(5) }
    }

    @Test
    fun reverseSaleEffectUndoesRepaymentBalance() = runTest {
        val party = KhataEntity(id = 2, partyName = "Priya", balance = 0.0)
        coEvery { txnDao.findBySaleId(9) } returns listOf(
            KhataTransactionEntity(id = 1, partyId = 2, amount = 50.0, type = "repayment", saleId = 9),
        )
        coEvery { khataDao.findById(2) } returns party

        repo.reverseSaleEffect(9)

        // repayment originally subtracted -50 → reversing adds 50 → balance 50
        coVerify(exactly = 1) { khataDao.update(match { it.id == 2L && it.balance == 50.0 }) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(9) }
    }

    @Test
    fun reverseSaleEffectWithNoTxnsJustDeletes() = runTest {
        coEvery { txnDao.findBySaleId(3) } returns emptyList()

        repo.reverseSaleEffect(3)

        coVerify(exactly = 0) { khataDao.update(any()) }
        coVerify(exactly = 1) { txnDao.deleteBySaleId(3) }
    }
}
