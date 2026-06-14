package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "khata",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["customerId"], unique = true)],  // one ledger row per customer
)
data class KhataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,              // FK→customers
    val partyName: String,             // denormalized for display
    val balance: Double = 0.0,         // positive = they owe us
    val lastUpdated: Long = System.currentTimeMillis(),
)
