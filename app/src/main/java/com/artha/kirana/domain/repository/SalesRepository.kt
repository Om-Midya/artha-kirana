package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.TopSellerRow
import kotlinx.coroutines.flow.Flow

interface SalesRepository {
    suspend fun logSale(sale: SaleEntity): Long
    suspend fun updateSale(sale: SaleEntity)
    fun observeSince(start: Long): Flow<List<SaleEntity>>
    fun revenueBetween(start: Long, end: Long): Flow<Double>
    fun cashBetween(start: Long, end: Long): Flow<Double>
    fun cogsBetween(start: Long, end: Long): Flow<Double>
    suspend fun topSellers(start: Long, end: Long): List<TopSellerRow>
}
