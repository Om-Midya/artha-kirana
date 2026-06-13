package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.util.HindiNumbers
import javax.inject.Inject

/**
 * Parses free Hindi/Hinglish sale text into [SaleEntry]s via the on-device LLM, then
 * enriches each entry with an auto-computed price from inventory when the shopkeeper gave a
 * quantity but no amount (see [computeAutoPrice]). Both Sale Entry and the Assistant call
 * this, so both get auto-price for free.
 */
class ParseSaleEntryUseCase @Inject constructor(
    private val engine: LlmEngine,
    private val inventory: InventoryRepository,
) {
    suspend operator fun invoke(text: String): Result<List<SaleEntry>> =
        // Convert Hindi number-words to digits first — the 3B reliably reads digits but flubs
        // spoken Hindi numbers (पचास→40) when a quantity number is nearby.
        engine.parseSale(HindiNumbers.normalize(text)).map { entries ->
            entries.map { e ->
                val item = e.item?.let { inventory.findByName(it) }
                e.copy(amount = computeAutoPrice(e, item))
            }
        }
}
