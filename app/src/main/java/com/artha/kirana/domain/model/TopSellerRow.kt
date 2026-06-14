package com.artha.kirana.domain.model

/** One item's aggregate sales over a period (excludes repayments). */
data class TopSellerRow(
    val itemId: Long?,
    val itemName: String?,
    val qty: Double,
    val revenue: Double,
)
