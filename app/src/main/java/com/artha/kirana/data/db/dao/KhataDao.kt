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

    @Query("SELECT * FROM khata WHERE id = :id")
    fun observeById(id: Long): Flow<KhataEntity?>

    @Query("SELECT * FROM khata WHERE id = :id")
    suspend fun findById(id: Long): KhataEntity?

    @Query("SELECT * FROM khata WHERE customerId = :customerId LIMIT 1")
    suspend fun findByCustomerId(customerId: Long): KhataEntity?

    @Query("SELECT COALESCE(SUM(balance), 0) FROM khata WHERE balance > 0")
    fun totalOutstanding(): Flow<Double>
}
