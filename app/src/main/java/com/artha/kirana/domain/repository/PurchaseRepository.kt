package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.PurchaseEntity

interface PurchaseRepository {
    suspend fun add(purchase: PurchaseEntity): Long
}
