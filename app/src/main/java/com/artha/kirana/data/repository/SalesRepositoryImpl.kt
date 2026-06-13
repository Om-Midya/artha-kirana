package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.SalesDao
import com.artha.kirana.data.db.entity.SaleEntity
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
}
