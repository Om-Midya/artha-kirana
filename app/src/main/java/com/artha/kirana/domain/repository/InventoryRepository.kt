package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun observeAll(): Flow<List<ItemEntity>>
    suspend fun findByName(name: String): ItemEntity?
    suspend fun addItem(item: ItemEntity): Long
    suspend fun decrementStock(id: Long, qty: Double)
    suspend fun incrementStock(id: Long, qty: Double)
    suspend fun updateItem(item: ItemEntity)
}
