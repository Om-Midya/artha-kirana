package com.artha.kirana.domain.usecase

import com.artha.kirana.data.llm.LlmEngine
import com.artha.kirana.domain.model.SaleEntry
import javax.inject.Inject

/** Parses free Hindi/Hinglish sale text into [SaleEntry]s via the on-device LLM. */
class ParseSaleEntryUseCase @Inject constructor(
    private val engine: LlmEngine,
) {
    suspend operator fun invoke(text: String): Result<List<SaleEntry>> = engine.parseSale(text)
}
