package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.CustomersDao
import com.artha.kirana.data.db.entity.CustomerEntity
import com.artha.kirana.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(
    private val dao: CustomersDao,
) : CustomerRepository {

    override suspend fun resolveOrCreate(name: String): Long =
        dao.findByName(name)?.id ?: dao.insert(CustomerEntity(name = name))

    override suspend fun findByName(name: String): CustomerEntity? = dao.findByName(name)

    override fun observeAll(): Flow<List<CustomerEntity>> = dao.observeAll()
}
