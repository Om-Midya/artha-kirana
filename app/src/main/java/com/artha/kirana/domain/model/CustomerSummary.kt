package com.artha.kirana.domain.model

/** Aggregate view of one customer for analytics. */
data class CustomerSummary(
    val customerId: Long,
    val lifetimeValue: Double,   // sum of non-repayment sale amounts
    val outstanding: Double,     // current khata balance (positive = owes us)
)
