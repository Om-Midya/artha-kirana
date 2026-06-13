package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "khata_transactions",
    foreignKeys = [
        ForeignKey(
            entity = KhataEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
        ),
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("partyId"), Index("saleId")],
)
data class KhataTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val amount: Double,
    val type: String,                  // "credit" | "repayment"
    val saleId: Long? = null,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
