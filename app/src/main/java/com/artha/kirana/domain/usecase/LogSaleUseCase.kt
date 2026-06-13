package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Persists a parsed [SaleEntry]: writes the sale, decrements item stock when the item is
 * known, and updates the party's khata balance for credit/repayment entries.
 * Returns the new sale's row id.
 */
class LogSaleUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val inventory: InventoryRepository,
    private val khata: KhataRepository,
) {
    suspend operator fun invoke(
        entry: SaleEntry,
        inputMethod: String,
        rawInput: String?,
    ): Long {
        val item = entry.item?.let { inventory.findByName(it) }
        val qty = parseLeadingNumber(entry.qty)

        val saleId = sales.logSale(
            SaleEntity(
                itemId = item?.id,
                qtySold = qty,
                amount = entry.amount ?: 0.0,
                type = entry.type,
                party = entry.party,
                inputMethod = inputMethod,
                rawInput = rawInput,
            ),
        )

        if (item != null && qty > 0.0) {
            inventory.decrementStock(item.id, qty)
        }

        when (entry.type) {
            "credit" -> entry.party?.let { khata.applyCredit(it, entry.amount ?: 0.0, saleId) }
            "repayment" -> entry.party?.let { khata.applyRepayment(it, entry.amount ?: 0.0, saleId) }
        }

        return saleId
    }

    /** Pulls the first number out of a qty string ("2 kg" -> 2.0). Hindi number words -> 0.0. */
    private fun parseLeadingNumber(qty: String?): Double {
        if (qty == null) return 0.0
        val match = Regex("""\d+(\.\d+)?""").find(qty) ?: return 0.0
        return match.value.toDoubleOrNull() ?: 0.0
    }
}
