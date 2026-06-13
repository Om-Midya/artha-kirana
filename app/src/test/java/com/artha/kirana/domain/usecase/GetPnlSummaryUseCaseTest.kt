package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPnlSummaryUseCaseTest {

    private val sales = mockk<SalesRepository>()
    private val khata = mockk<KhataRepository>()
    private val useCase = GetPnlSummaryUseCase(sales, khata)

    @Test
    fun grossProfitIsRevenueMinusCogs_andFieldsMapThrough() = runTest {
        every { sales.revenueBetween(any(), any()) } returns flowOf(500.0)
        every { sales.cogsBetween(any(), any()) } returns flowOf(300.0)
        every { sales.cashBetween(any(), any()) } returns flowOf(420.0)
        every { khata.totalOutstanding() } returns flowOf(80.0)

        val summary = useCase(PnlPeriod.TODAY).first()

        assertEquals(500.0, summary.grossRevenue, 0.001)
        assertEquals(300.0, summary.cogs, 0.001)
        assertEquals(200.0, summary.grossProfit, 0.001)
        assertEquals(420.0, summary.cashCollected, 0.001)
        assertEquals(80.0, summary.totalOutstanding, 0.001)
        assertEquals(PnlPeriod.TODAY, summary.period)
    }
}
