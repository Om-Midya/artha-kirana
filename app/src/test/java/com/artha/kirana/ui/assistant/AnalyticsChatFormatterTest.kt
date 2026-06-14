package com.artha.kirana.ui.assistant

import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.TopSellerRow
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsChatFormatterTest {

    @Test
    fun topSellersListsRankedRows() {
        val text = AnalyticsChatFormatter.topSellers(
            PnlPeriod.THIS_WEEK,
            listOf(TopSellerRow(1, "चावल", 9.0, 450.0), TopSellerRow(2, "तेल", 3.0, 390.0)),
        )
        assertTrue(text.contains("चावल"))
        assertTrue(text.contains("450"))
        assertTrue(text.contains("1."))
    }

    @Test
    fun topSellersEmptyShowsNoData() {
        val text = AnalyticsChatFormatter.topSellers(PnlPeriod.TODAY, emptyList())
        assertTrue(text.contains("कोई"))
    }

    @Test
    fun customerShowsNameOutstandingAndTotal() {
        val text = AnalyticsChatFormatter.customer("Ramesh", CustomerSummary(3, 500.0, 120.0))
        assertTrue(text.contains("Ramesh"))
        assertTrue(text.contains("500"))
        assertTrue(text.contains("120"))
    }

    @Test
    fun dayTrendMarksBusiestDay() {
        val buckets = DoubleArray(7).also { it[5] = 300.0; it[0] = 50.0 }
        val text = AnalyticsChatFormatter.dayTrend(PnlPeriod.THIS_MONTH, buckets)
        assertTrue(text.contains("शुक्र"))
        assertTrue(text.contains("300"))
    }
}
