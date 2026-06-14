package com.artha.kirana.data.llm

import com.artha.kirana.domain.model.AssistantIntent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentRouterTest {

    private val router = IntentRouter(client = mockk(relaxed = true))

    @Test
    fun parsesEachKnownIntent() {
        assertEquals(AssistantIntent.LOG_SALE, router.parseIntent("""{"intent":"log_sale"}"""))
        assertEquals(AssistantIntent.RECORD_PAYMENT, router.parseIntent("""{"intent":"record_payment"}"""))
        assertEquals(AssistantIntent.QUERY_PNL, router.parseIntent("""{"intent":"query_pnl"}"""))
    }

    @Test
    fun parsesMarkdownFenced() {
        assertEquals(
            AssistantIntent.QUERY_PNL,
            router.parseIntent("```json\n{\"intent\":\"query_pnl\"}\n```"),
        )
    }

    @Test
    fun unknownStringFallsBackToUnknown() {
        assertEquals(AssistantIntent.UNKNOWN, router.parseIntent("""{"intent":"frobnicate"}"""))
    }

    @Test
    fun garbageFallsBackToUnknown() {
        assertEquals(AssistantIntent.UNKNOWN, router.parseIntent("not json at all"))
    }

    @Test
    fun parsesNewAnalyticsIntents() {
        assertEquals(AssistantIntent.QUERY_TOP_SELLERS, router.parseIntent("""{"intent":"query_top_sellers"}"""))
        assertEquals(AssistantIntent.QUERY_CUSTOMER, router.parseIntent("""{"intent":"query_customer"}"""))
        assertEquals(AssistantIntent.QUERY_DAY_TREND, router.parseIntent("""{"intent":"query_day_trend"}"""))
    }
}
