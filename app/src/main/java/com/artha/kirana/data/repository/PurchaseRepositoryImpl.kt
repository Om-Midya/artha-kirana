package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.PurchasesDao
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.domain.repository.PurchaseRepository
import javax.inject.Inject

class PurchaseRepositoryImpl @Inject constructor(
    private val dao: PurchasesDao,
) : PurchaseRepository {
    override suspend fun add(purchase: PurchaseEntity): Long = dao.insert(purchase)
}
