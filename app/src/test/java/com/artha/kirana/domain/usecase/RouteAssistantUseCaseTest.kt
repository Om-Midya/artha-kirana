package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.CustomerEntity
import com.artha.kirana.data.llm.IntentRouter
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.data.llm.ParsedPayment
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.usecase.AssistantAgentUseCase
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.model.TopSellerRow
import com.artha.kirana.domain.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAssistantUseCaseTest {

    private val agent = mockk<AssistantAgentUseCase>()
    private val intentRouter = mockk<IntentRouter>()
    private val parseSale = mockk<ParseSaleEntryUseCase>()
    private val engine = mockk<LlmEngine>()
    private val getPnl = mockk<GetPnlSummaryUseCase>()
    private val getTopSellers = mockk<GetTopSellersUseCase>(relaxed = true)
    private val getCustomerSummary = mockk<GetCustomerSummaryUseCase>(relaxed = true)
    private val getDayTrend = mockk<GetDayOfWeekTrendUseCase>(relaxed = true)
    private val customers = mockk<CustomerRepository>(relaxed = true)
    private val useCase = RouteAssistantUseCase(
        agent, intentRouter, parseSale, engine, getPnl,
        getTopSellers, getCustomerSummary, getDayTrend, customers,
    )

    init {
        // All existing tests exercise the on-device fallback path: make the cloud agent
        // unavailable so RouteAssistantUseCase falls through to classifyFallback().
        coEvery { agent.run(any(), any()) } throws LlmUnavailableException(null)
    }

    private val summary = PnlSummary(0.0, 0.0, 0.0, 0.0, 0.0, PnlPeriod.TODAY)

    @Test
    fun logSaleWithEntriesProducesSaleDraft() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.LOG_SALE)
        val entry = SaleEntry(item = "चावल", qty = "2 kg", amount = 80.0, type = "cash", party = null)
        coEvery { parseSale(any()) } returns Result.success(listOf(entry))

        val result = useCase("दो किलो चावल अस्सी रुपये")

        assertTrue(result is AssistantResult.SaleDraft)
        assertEquals(listOf(entry), (result as AssistantResult.SaleDraft).entries)
    }

    @Test
    fun logSaleWithNoEntriesProducesReply() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.LOG_SALE)
        coEvery { parseSale(any()) } returns Result.success(emptyList())

        assertTrue(useCase("blah") is AssistantResult.Reply)
    }

    @Test
    fun recordPaymentProducesPaymentDraft() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.RECORD_PAYMENT)
        coEvery { engine.parsePayment(any()) } returns Result.success(ParsedPayment("रमेश", 50.0))

        val result = useCase("रमेश ने पचास रुपये दिए")

        assertTrue(result is AssistantResult.PaymentDraft)
        result as AssistantResult.PaymentDraft
        assertEquals("रमेश", result.party)
        assertEquals(50.0, result.amount!!, 0.001)
    }

    @Test
    fun queryPnlUsesDetectedPeriodAndProducesAnswer() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_PNL)
        every { getPnl(PnlPeriod.THIS_WEEK, any()) } returns flowOf(summary)

        val result = useCase("इस हफ्ते का मुनाफा")

        assertTrue(result is AssistantResult.PnlAnswer)
        assertEquals(summary, (result as AssistantResult.PnlAnswer).summary)
    }

    @Test
    fun classifyFailureProducesUnavailable() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.failure(LlmUnavailableException(null))

        assertEquals(AssistantResult.Unavailable, useCase("anything"))
    }

    @Test
    fun unknownIntentProducesReply() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.UNKNOWN)

        assertTrue(useCase("नमस्ते") is AssistantResult.Reply)
    }

    @Test
    fun topSellersIntentReturnsRankingForDetectedPeriod() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_TOP_SELLERS)
        val rows = listOf(TopSellerRow(1L, "चावल", 9.0, 450.0))
        coEvery { getTopSellers(any(), any()) } returns rows

        val result = useCase("इस हफ्ते सबसे ज्यादा क्या बिका")

        assertTrue(result is AssistantResult.TopSellersAnswer)
        result as AssistantResult.TopSellersAnswer
        assertEquals(PnlPeriod.THIS_WEEK, result.period)
        assertEquals(rows, result.rows)
    }

    @Test
    fun customerIntentResolvesNameToSummary() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_CUSTOMER)
        coEvery { engine.extractCustomerName(any()) } returns Result.success("ramesh")   // LLM may differ in casing
        coEvery { customers.findByName("ramesh") } returns CustomerEntity(id = 3, name = "Ramesh") // canonical
        coEvery { getCustomerSummary(3L) } returns CustomerSummary(3L, 500.0, 120.0)

        val result = useCase("रमेश का हिसाब")

        assertTrue(result is AssistantResult.CustomerAnswer)
        result as AssistantResult.CustomerAnswer
        assertEquals("Ramesh", result.name)
        assertEquals(120.0, result.summary.outstanding, 0.001)
    }

    @Test
    fun customerIntentNotFoundReplies() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_CUSTOMER)
        coEvery { engine.extractCustomerName(any()) } returns Result.success("Mystery")
        coEvery { customers.findByName("Mystery") } returns null

        val result = useCase("मिस्ट्री का हिसाब")

        assertTrue(result is AssistantResult.Reply)
    }

    @Test
    fun customerIntentNullNameAsksWhich() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_CUSTOMER)
        coEvery { engine.extractCustomerName(any()) } returns Result.success(null)

        val result = useCase("हिसाब बताओ")

        assertTrue(result is AssistantResult.Reply)
    }

    @Test
    fun dayTrendIntentReturnsBuckets() = runTest {
        coEvery { intentRouter.classify(any()) } returns Result.success(AssistantIntent.QUERY_DAY_TREND)
        coEvery { getDayTrend(any(), any()) } returns DoubleArray(7) { it.toDouble() }

        val result = useCase("कौन सा दिन busy")

        assertTrue(result is AssistantResult.DayTrendAnswer)
    }
}
