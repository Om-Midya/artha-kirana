package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.KhataEntity
import kotlinx.coroutines.flow.Flow

interface KhataRepository {
    fun observeAll(): Flow<List<KhataEntity>>
    fun totalOutstanding(): Flow<Double>

    /** Increase what [party] owes us by [amount] (creating the party if new). */
    suspend fun applyCredit(party: String, amount: Double, saleId: Long?)

    /** Reduce what [party] owes us by [amount] (creating the party if new). */
    suspend fun applyRepayment(party: String, amount: Double, saleId: Long?)
}
