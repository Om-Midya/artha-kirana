package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Edits a saved sale in place: reverses the original entry's inventory + khata effects, then
 * applies the edited entry's effects. Keeps the sale's id, timestamp, inputMethod, rawInput.
 * The §18-stable add path ([LogSaleUseCase]) is untouched.
 */
class EditSaleUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val inventory: InventoryRepository,
    private val khata: KhataRepository,
) {
    suspend operator fun invoke(old: SaleEntity, edited: SaleEntry) {
        // 1. reverse old stock
        if (old.itemId != null && old.qtySold > 0.0) {
            inventory.incrementStock(old.itemId, old.qtySold)
        }
        // 2. reverse old khata (clean rewrite)
        khata.reverseSaleEffect(old.id)

        // 3. resolve new item + qty
        val newItem = edited.item?.let { inventory.findByName(it) }
        val newQty = parseLeadingQty(edited.qty)

        // 4. update the row in place (id, timestamp, inputMethod, rawInput preserved by copy)
        sales.updateSale(
            old.copy(
                itemId = newItem?.id,
                itemName = edited.item,
                qtySold = newQty,
                amount = edited.amount ?: 0.0,
                type = edited.type,
                party = edited.party,
            ),
        )

        // 5. apply new stock
        if (newItem != null && newQty > 0.0) {
            inventory.decrementStock(newItem.id, newQty)
        }

        // 6. apply new khata
        when (edited.type) {
            "credit" -> edited.party?.let { khata.applyCredit(it, edited.amount ?: 0.0, old.id) }
            "repayment" -> edited.party?.let { khata.applyRepayment(it, edited.amount ?: 0.0, old.id) }
        }
    }
}
