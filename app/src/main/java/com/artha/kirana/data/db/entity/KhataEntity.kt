package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "khata")
data class KhataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyName: String,
    val balance: Double = 0.0,         // positive = they owe us
    val lastUpdated: Long = System.currentTimeMillis(),
)
