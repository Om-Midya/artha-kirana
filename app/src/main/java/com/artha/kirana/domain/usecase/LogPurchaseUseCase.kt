package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.PurchaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Applies a confirmed scanned bill: for each line, resolve-or-create the item, increment stock,
 * refresh its cost from this purchase, and record a [PurchaseEntity]. Returns the number of lines
 * applied. Runs off the main thread.
 */
class LogPurchaseUseCase @Inject constructor(
    private val inventory: InventoryRepository,
    private val purchases: PurchaseRepository,
) {
    suspend operator fun invoke(items: List<ParsedPurchaseItem>, supplier: String?): Int =
        withContext(Dispatchers.IO) {
            var applied = 0
            for (line in items) {
                val name = line.name.trim()
                if (name.isEmpty() || line.qty <= 0.0) continue
                val existing = inventory.findByName(name)
                val itemId: Long = if (existing != null) {
                    val newCost = if (line.unitPrice != null && line.unitPrice > 0.0) line.unitPrice else existing.costPrice
                    val newSell = if (line.sellPrice != null && line.sellPrice > 0.0) line.sellPrice else existing.sellPrice
                    if (newCost != existing.costPrice || newSell != existing.sellPrice) {
                        inventory.updateItem(existing.copy(costPrice = newCost, sellPrice = newSell))
                    }
                    existing.id
                } else {
                    inventory.addItem(
                        ItemEntity(
                            name = name,
                            unit = line.unit,
                            costPrice = line.unitPrice ?: 0.0,
                            sellPrice = line.sellPrice ?: 0.0,
                        ),
                    )
                }
                inventory.incrementStock(itemId, line.qty)
                val cost = line.amount ?: (line.unitPrice?.let { it * line.qty } ?: 0.0)
                purchases.add(
                    PurchaseEntity(
                        itemId = itemId,
                        qty = line.qty,
                        cost = cost,
                        supplier = supplier,
                    ),
                )
                applied++
            }
            applied
        }
}
