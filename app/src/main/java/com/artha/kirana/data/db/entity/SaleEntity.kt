package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("timestamp"), Index("itemId"), Index("customerId")],
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,          // null when the item isn't a tracked inventory item
    val itemName: String? = null,      // denormalized name of what was sold (for display)
    val customerId: Long? = null,      // FK→customers; null for anonymous cash sales
    val qtySold: Double = 0.0,
    val amount: Double,
    val unitPrice: Double? = null,     // snapshot of items.sellPrice at sale time
    val unitCost: Double? = null,      // snapshot of items.costPrice at sale time
    val type: String,                  // "cash" | "credit" | "repayment"
    val party: String? = null,         // denormalized customer name (display + fallback)
    val inputMethod: String,           // "voice" | "scan" | "typed"
    val rawInput: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
