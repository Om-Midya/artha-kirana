package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.IntentRouter
import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.data.llm.ParsedPayment
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.model.SaleEntry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAssistantUseCaseTest {

    private val intentRouter = mockk<IntentRouter>()
    private val parseSale = mockk<ParseSaleEntryUseCase>()
    private val engine = mockk<LlmEngine>()
    private val getPnl = mockk<GetPnlSummaryUseCase>()
    private val useCase = RouteAssistantUseCase(intentRouter, parseSale, engine, getPnl)

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
}
