package com.artha.kirana.domain.model

/** One editable line item read from a supplier bill (pre-confirmation). */
data class ParsedPurchaseItem(
    val name: String,
    val qty: Double = 1.0,
    val unit: String = "pcs",
    val unitPrice: Double? = null,
    val amount: Double? = null,
)

/** Result of a cloud bill scan: line items + an optional grand total. */
data class ParsedBill(
    val items: List<ParsedPurchaseItem> = emptyList(),
    val total: Double? = null,
)
