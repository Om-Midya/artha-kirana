package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.KhataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class KhataRepositoryImpl @Inject constructor(
    private val khataDao: KhataDao,
    private val txnDao: KhataTransactionDao,
) : KhataRepository {

    override fun observeAll(): Flow<List<KhataEntity>> = khataDao.observeAll()
    override fun totalOutstanding(): Flow<Double> = khataDao.totalOutstanding()

    override suspend fun applyCredit(party: String, amount: Double, saleId: Long?) =
        adjust(party, delta = amount, type = "credit", saleId = saleId, amount = amount)

    override suspend fun applyRepayment(party: String, amount: Double, saleId: Long?) =
        adjust(party, delta = -amount, type = "repayment", saleId = saleId, amount = amount)

    private suspend fun adjust(party: String, delta: Double, type: String, saleId: Long?, amount: Double) {
        val existing = khataDao.findByName(party)
        val partyId = if (existing == null) {
            khataDao.insert(KhataEntity(partyName = party, balance = delta))
        } else {
            khataDao.update(
                existing.copy(
                    balance = existing.balance + delta,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
            existing.id
        }
        txnDao.insert(
            KhataTransactionEntity(partyId = partyId, amount = amount, type = type, saleId = saleId),
        )
    }
}
