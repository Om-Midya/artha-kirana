package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import kotlinx.coroutines.flow.Flow

interface KhataRepository {
    fun observeAll(): Flow<List<KhataEntity>>
    fun totalOutstanding(): Flow<Double>

    /** Increase what [party] owes us by [amount] (creating the party if new). */
    suspend fun applyCredit(party: String, amount: Double, saleId: Long?)

    /** Reduce what [party] owes us by [amount] (creating the party if new). */
    suspend fun applyRepayment(party: String, amount: Double, saleId: Long?)

    fun observeParty(id: Long): Flow<KhataEntity?>
    fun observeTransactions(id: Long): Flow<List<KhataTransactionEntity>>

    /** Remove the khata transaction(s) created by [saleId] and undo their balance impact. */
    suspend fun reverseSaleEffect(saleId: Long)

    /** Current khata balance for [customerId] (positive = owes us; 0 if no khata row). */
    suspend fun balanceForCustomer(customerId: Long): Double
}
