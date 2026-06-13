package com.artha.kirana.domain.model

enum class PnlPeriod { TODAY, THIS_WEEK, THIS_MONTH }

data class PnlSummary(
    val grossRevenue: Double,
    val cogs: Double,
    val grossProfit: Double,
    val cashCollected: Double,
    val totalOutstanding: Double,
    val period: PnlPeriod,
)

/** One bar of the 7-day revenue chart. */
data class DailyRevenue(val dayLabel: String, val amount: Double)
