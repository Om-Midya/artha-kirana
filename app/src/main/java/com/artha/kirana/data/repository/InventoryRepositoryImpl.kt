package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.ItemsDao
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InventoryRepositoryImpl @Inject constructor(
    private val dao: ItemsDao,
) : InventoryRepository {
    override fun observeAll(): Flow<List<ItemEntity>> = dao.observeAll()
    override suspend fun findByName(name: String): ItemEntity? = dao.findByName(name)
    override suspend fun addItem(item: ItemEntity): Long = dao.insert(item)
    override suspend fun decrementStock(id: Long, qty: Double) = dao.decrementStock(id, qty)
}
