package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.artha.kirana.data.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {
    @Insert
    suspend fun insert(sale: SaleEntity): Long

    @Query("SELECT * FROM sales WHERE timestamp >= :start ORDER BY timestamp DESC")
    fun observeSince(start: Long): Flow<List<SaleEntity>>

    /** Gross revenue = everything except repayments (which are not new sales). */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM sales WHERE type != 'repayment' AND timestamp BETWEEN :start AND :end")
    fun revenueBetween(start: Long, end: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM sales WHERE type = 'cash' AND timestamp BETWEEN :start AND :end")
    fun cashBetween(start: Long, end: Long): Flow<Double>

    @Query("SELECT * FROM sales WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun between(start: Long, end: Long): List<SaleEntity>
}
