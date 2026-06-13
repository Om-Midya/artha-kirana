package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

interface SalesRepository {
    suspend fun logSale(sale: SaleEntity): Long
    fun observeSince(start: Long): Flow<List<SaleEntity>>
    fun revenueBetween(start: Long, end: Long): Flow<Double>
}
