package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.artha.kirana.data.db.entity.PurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchasesDao {
    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>
}
