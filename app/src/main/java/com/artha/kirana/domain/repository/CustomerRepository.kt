package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    /** Returns the id of the customer named [name], inserting a new row if none exists. */
    suspend fun resolveOrCreate(name: String): Long
    suspend fun findByName(name: String): CustomerEntity?
    fun observeAll(): Flow<List<CustomerEntity>>
}
