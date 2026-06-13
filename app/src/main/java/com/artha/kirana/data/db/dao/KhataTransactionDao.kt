package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataTransactionDao {
    @Insert
    suspend fun insert(txn: KhataTransactionEntity): Long

    @Query("SELECT * FROM khata_transactions WHERE partyId = :partyId ORDER BY timestamp DESC")
    fun observeForParty(partyId: Long): Flow<List<KhataTransactionEntity>>
}
