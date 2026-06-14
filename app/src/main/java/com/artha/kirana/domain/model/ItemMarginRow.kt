package com.artha.kirana.domain.model

/** One item's margin (sellPrice-costPrice snapshots x qty) and revenue over a period. */
data class ItemMarginRow(
    val itemId: Long?,
    val itemName: String?,
    val margin: Double,
    val revenue: Double,
)
