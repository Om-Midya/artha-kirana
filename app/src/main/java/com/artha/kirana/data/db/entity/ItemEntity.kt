package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameHi: String? = null,        // Hindi display name
    val qtyInStock: Double = 0.0,
    val unit: String = "piece",        // kg / litre / piece / dozen
    val costPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val reorderThreshold: Double = 0.0,
    val category: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
