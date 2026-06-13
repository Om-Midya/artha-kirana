package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,          // null when the item isn't a tracked inventory item
    val itemName: String? = null,      // denormalized name of what was sold (for display)
    val qtySold: Double = 0.0,
    val amount: Double,
    val type: String,                  // "cash" | "credit" | "repayment"
    val party: String? = null,
    val inputMethod: String,           // "voice" | "scan" | "typed"
    val rawInput: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
