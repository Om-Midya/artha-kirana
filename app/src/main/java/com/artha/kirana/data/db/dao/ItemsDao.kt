package com.artha.kirana.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.artha.kirana.data.db.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemsDao {
    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM items ORDER BY name")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun findById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ItemEntity?

    @Query("UPDATE items SET qtyInStock = qtyInStock - :qty WHERE id = :id")
    suspend fun decrementStock(id: Long, qty: Double)

    @Query("SELECT * FROM items WHERE reorderThreshold > 0 AND qtyInStock < reorderThreshold")
    suspend fun lowStock(): List<ItemEntity>
}
