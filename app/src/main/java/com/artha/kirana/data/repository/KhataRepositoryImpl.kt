package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.KhataDao
import com.artha.kirana.data.db.dao.KhataTransactionDao
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.KhataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class KhataRepositoryImpl @Inject constructor(
    private val khataDao: KhataDao,
    private val txnDao: KhataTransactionDao,
    private val customers: CustomerRepository,
) : KhataRepository {

    override fun observeAll(): Flow<List<KhataEntity>> = khataDao.observeAll()
    override fun totalOutstanding(): Flow<Double> = khataDao.totalOutstanding()
    override fun observeParty(id: Long): Flow<KhataEntity?> = khataDao.observeById(id)
    override fun observeTransactions(id: Long): Flow<List<KhataTransactionEntity>> =
        txnDao.observeForParty(id)

    override suspend fun applyCredit(party: String, amount: Double, saleId: Long?) =
        adjust(party, delta = amount, type = "credit", saleId = saleId, amount = amount)

    override suspend fun applyRepayment(party: String, amount: Double, saleId: Long?) =
        adjust(party, delta = -amount, type = "repayment", saleId = saleId, amount = amount)

    override suspend fun reverseSaleEffect(saleId: Long) {
        txnDao.findBySaleId(saleId).forEach { t ->
            val delta = if (t.type == "credit") t.amount else -t.amount // original balance impact
            khataDao.findById(t.partyId)?.let { p ->
                khataDao.update(p.copy(balance = p.balance - delta, lastUpdated = System.currentTimeMillis()))
            }
        }
        txnDao.deleteBySaleId(saleId)
    }

    override suspend fun balanceForCustomer(customerId: Long): Double =
        khataDao.balanceForCustomer(customerId)

    private suspend fun adjust(party: String, delta: Double, type: String, saleId: Long?, amount: Double) {
        val customerId = customers.resolveOrCreate(party)
        val existing = khataDao.findByCustomerId(customerId)
        val partyId = if (existing == null) {
            khataDao.insert(KhataEntity(customerId = customerId, partyName = party, balance = delta))
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
