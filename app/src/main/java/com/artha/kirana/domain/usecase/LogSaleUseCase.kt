package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.CustomerRepository
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Persists a parsed [SaleEntry]: resolves the named customer (if any), snapshots the item's
 * unit price/cost, writes the sale, decrements stock when the item is known, and updates the
 * party's khata balance for credit/repayment. Returns the new sale's row id.
 */
class LogSaleUseCase @Inject constructor(
    private val sales: SalesRepository,
    private val inventory: InventoryRepository,
    private val khata: KhataRepository,
    private val customers: CustomerRepository,
) {
    suspend operator fun invoke(
        entry: SaleEntry,
        inputMethod: String,
        rawInput: String?,
    ): Long {
        val item = entry.item?.let { inventory.findByName(it) }
        val qty = parseLeadingQty(entry.qty)
        val customerId = entry.party?.takeIf { it.isNotBlank() }?.let { customers.resolveOrCreate(it) }

        val saleId = sales.logSale(
            SaleEntity(
                itemId = item?.id,
                itemName = entry.item,
                customerId = customerId,
                qtySold = qty,
                amount = entry.amount ?: 0.0,
                unitPrice = item?.sellPrice,
                unitCost = item?.costPrice,
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
}
