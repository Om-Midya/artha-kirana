package com.artha.kirana.domain.model

/**
 * One parsed sale line from the LLM (CLAUDE-1.md §5 schema), in domain terms.
 * [type] is one of "cash" | "credit" | "repayment".
 */
data class SaleEntry(
    val item: String?,
    val qty: String?,
    val amount: Double?,
    val type: String,
    val party: String?,
)
