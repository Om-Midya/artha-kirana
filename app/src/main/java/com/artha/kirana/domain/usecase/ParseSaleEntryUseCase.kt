package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.util.HindiNumbers
import javax.inject.Inject

/** Parses free Hindi/Hinglish sale text into [SaleEntry]s via the on-device LLM. */
class ParseSaleEntryUseCase @Inject constructor(
    private val engine: LlmEngine,
) {
    suspend operator fun invoke(text: String): Result<List<SaleEntry>> =
        // Convert Hindi number-words to digits first — the 3B reliably reads digits but flubs
        // spoken Hindi numbers (पचास→40) when a quantity number is nearby.
        engine.parseSale(HindiNumbers.normalize(text))
}
