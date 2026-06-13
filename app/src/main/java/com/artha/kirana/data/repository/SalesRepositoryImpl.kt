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
    override fun observeSince(start: Long): Flow<List<SaleEntity>> = dao.observeSince(start)
    override fun revenueBetween(start: Long, end: Long): Flow<Double> = dao.revenueBetween(start, end)
}
