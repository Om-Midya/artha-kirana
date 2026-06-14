package com.artha.kirana.ui.assistant

import com.artha.kirana.domain.model.CustomerSummary
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.TopSellerRow

/** Formats read-only analytics results into Hindi/Hinglish chat-bubble text. Pure + testable. */
object AnalyticsChatFormatter {

    private fun periodLabel(p: PnlPeriod): String = when (p) {
        PnlPeriod.TODAY -> "आज"
        PnlPeriod.THIS_WEEK -> "इस हफ्ते"
        PnlPeriod.THIS_MONTH -> "इस महीने"
    }

    private val weekdays = listOf("रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि")

    private fun rupees(v: Double): String = "₹${v.toLong()}"

    fun topSellers(period: PnlPeriod, rows: List<TopSellerRow>): String {
        if (rows.isEmpty()) return "${periodLabel(period)} की कोई बिक्री नहीं मिली।"
        val lines = rows.take(5).mapIndexed { i, r ->
            "${i + 1}. ${r.itemName ?: "अन्य"} — ${rupees(r.revenue)} (${r.qty.toLong()})"
        }
        return "📊 ${periodLabel(period)} के टॉप आइटम:\n" + lines.joinToString("\n")
    }

    fun customer(name: String, summary: CustomerSummary): String =
        "👤 $name\nकुल खरीदा: ${rupees(summary.lifetimeValue)}\nबकाया: ${rupees(summary.outstanding)}"

    fun dayTrend(period: PnlPeriod, buckets: DoubleArray): String {
        if (buckets.all { it == 0.0 }) return "${periodLabel(period)} का कोई डेटा नहीं।"
        val maxIdx = buckets.indices.maxByOrNull { buckets[it] } ?: 0
        val lines = buckets.indices.map { i ->
            val mark = if (i == maxIdx) " ⭐" else ""
            "${weekdays[i]}: ${rupees(buckets[i])}$mark"
        }
        return "📅 ${periodLabel(period)} (दिन के हिसाब से):\n" + lines.joinToString("\n")
    }
}
