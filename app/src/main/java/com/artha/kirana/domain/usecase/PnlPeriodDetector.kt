package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod

/**
 * Picks a [PnlPeriod] from a P&L question by keyword. Deterministic, so it lives in code
 * (no second LLM call). Month is checked before week so "इस महीने" never matches a week stem.
 * Stems match inflections: महीन→महीना/महीने, हफ्त→हफ्ता/हफ्ते.
 */
object PnlPeriodDetector {
    fun detect(text: String): PnlPeriod {
        val t = text.lowercase()
        return when {
            t.contains("महीन") || t.contains("month") || t.contains("mahin") || t.contains("mahee") ->
                PnlPeriod.THIS_MONTH
            t.contains("हफ्त") || t.contains("हफ़्त") || t.contains("सप्ताह") || t.contains("week") || t.contains("haft") ->
                PnlPeriod.THIS_WEEK
            else -> PnlPeriod.TODAY
        }
    }
}
