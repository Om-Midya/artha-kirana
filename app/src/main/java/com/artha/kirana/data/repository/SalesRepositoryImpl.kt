package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.SalesDao
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.ItemMarginRow
import com.artha.kirana.domain.model.TopSellerRow
import com.artha.kirana.domain.repository.SalesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SalesRepositoryImpl @Inject constructor(
    private val dao: SalesDao,
) : SalesRepository {
    override suspend fun logSale(sale: SaleEntity): Long = dao.insert(sale)
    override suspend fun updateSale(sale: SaleEntity) = dao.update(sale)
    override fun observeSince(start: Long): Flow<List<SaleEntity>> = dao.observeSince(start)
    override fun revenueBetween(start: Long, end: Long): Flow<Double> = dao.revenueBetween(start, end)
    override fun cashBetween(start: Long, end: Long): Flow<Double> = dao.cashBetween(start, end)
    override fun cogsBetween(start: Long, end: Long): Flow<Double> = dao.cogsBetween(start, end)
    override suspend fun topSellers(start: Long, end: Long): List<TopSellerRow> = dao.topSellers(start, end)
    override suspend fun itemMargins(start: Long, end: Long): List<ItemMarginRow> = dao.itemMargins(start, end)
    override fun observeForCustomer(customerId: Long): Flow<List<SaleEntity>> = dao.observeForCustomer(customerId)
    override suspend fun lifetimeValue(customerId: Long): Double = dao.lifetimeValue(customerId)
    override suspend fun between(start: Long, end: Long): List<SaleEntity> = dao.between(start, end)
    override fun observeBetween(start: Long, end: Long): Flow<List<SaleEntity>> = dao.observeBetween(start, end)
}
