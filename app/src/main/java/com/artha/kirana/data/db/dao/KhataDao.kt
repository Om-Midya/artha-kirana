package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.artha.kirana.data.db.entity.KhataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataDao {
    @Insert
    suspend fun insert(khata: KhataEntity): Long

    @Update
    suspend fun update(khata: KhataEntity)

    @Query("SELECT * FROM khata ORDER BY balance DESC")
    fun observeAll(): Flow<List<KhataEntity>>

    @Query("SELECT * FROM khata WHERE partyName = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): KhataEntity?

    @Query("SELECT COALESCE(SUM(balance), 0) FROM khata WHERE balance > 0")
    fun totalOutstanding(): Flow<Double>
}
