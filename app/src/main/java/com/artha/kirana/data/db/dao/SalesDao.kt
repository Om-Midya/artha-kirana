package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.TopSellerRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {
    @Insert
    suspend fun insert(sale: SaleEntity): Long

    @Update
    suspend fun update(sale: SaleEntity)

    @Query("SELECT * FROM sales WHERE timestamp >= :start ORDER BY timestamp DESC")
    fun observeSince(start: Long): Flow<List<SaleEntity>>

    /** Gross revenue = everything except repayments (which are not new sales). */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM sales WHERE type != 'repayment' AND timestamp BETWEEN :start AND :end")
    fun revenueBetween(start: Long, end: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM sales WHERE type = 'cash' AND timestamp BETWEEN :start AND :end")
    fun cashBetween(start: Long, end: Long): Flow<Double>

    @Query("SELECT * FROM sales WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun between(start: Long, end: Long): List<SaleEntity>

    /** Cost of goods sold = sum of qty*costPrice for non-repayment sales joined to items. */
    @Query(
        "SELECT COALESCE(SUM(s.qtySold * i.costPrice), 0) FROM sales s " +
            "JOIN items i ON s.itemId = i.id " +
            "WHERE s.type != 'repayment' AND s.timestamp BETWEEN :start AND :end",
    )
    fun cogsBetween(start: Long, end: Long): Flow<Double>

    @Query(
        "SELECT itemId, itemName, COALESCE(SUM(qtySold),0) AS qty, COALESCE(SUM(amount),0) AS revenue " +
            "FROM sales WHERE type != 'repayment' AND timestamp BETWEEN :start AND :end " +
            "GROUP BY itemId ORDER BY revenue DESC",
    )
    suspend fun topSellers(start: Long, end: Long): List<TopSellerRow>
}
