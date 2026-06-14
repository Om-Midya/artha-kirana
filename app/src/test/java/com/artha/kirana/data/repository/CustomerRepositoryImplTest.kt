package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.CustomersDao
import com.artha.kirana.data.db.entity.CustomerEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerRepositoryImplTest {

    private val dao = mockk<CustomersDao>(relaxed = true)
    private val repo = CustomerRepositoryImpl(dao)

    @Test
    fun returnsExistingIdWhenNameFound() = runTest {
        coEvery { dao.findByName("Ramesh") } returns CustomerEntity(id = 7, name = "Ramesh")

        assertEquals(7L, repo.resolveOrCreate("Ramesh"))
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun insertsAndReturnsNewIdWhenNameNotFound() = runTest {
        coEvery { dao.findByName("Priya") } returns null
        coEvery { dao.insert(any()) } returns 12L

        assertEquals(12L, repo.resolveOrCreate("Priya"))
        coVerify(exactly = 1) { dao.insert(match { it.name == "Priya" }) }
    }

    @Test
    fun trimsNameBeforeLookupAndInsert() = runTest {
        coEvery { dao.findByName("Ramesh") } returns null
        coEvery { dao.insert(any()) } returns 5L

        assertEquals(5L, repo.resolveOrCreate("  Ramesh  "))

        // looked up and inserted with the trimmed name (no leading/trailing space)
        coVerify(exactly = 1) { dao.findByName("Ramesh") }
        coVerify(exactly = 1) { dao.insert(match { it.name == "Ramesh" }) }
    }
}
