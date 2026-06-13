package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.model.SaleEntry

/**
 * Computes a sale line total from inventory when the LLM gave a quantity but no amount.
 * Returns the entry's existing amount when present (an explicit spoken/typed price always
 * wins). Returns null — leaving the amount unfilled for the user to edit — unless the item
 * resolved in inventory with sellPrice > 0 and a positive quantity.
 */
internal fun computeAutoPrice(entry: SaleEntry, item: ItemEntity?): Double? {
    if (entry.amount != null) return entry.amount
    if (item == null || item.sellPrice <= 0.0) return null
    val qty = parseLeadingQty(entry.qty)
    if (qty <= 0.0) return null
    return item.sellPrice * qty
}
