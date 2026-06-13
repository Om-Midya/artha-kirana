package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val qty: Double,
    val cost: Double,
    val supplier: String? = null,
    val billScanUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
