package com.artha.kirana.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"], unique = true)],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                  // looked up COLLATE NOCASE in resolveOrCreate
    val nameHi: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
