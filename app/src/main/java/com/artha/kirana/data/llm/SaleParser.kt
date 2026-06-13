package com.artha.kirana.data.llm

import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.util.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class SaleEntryDto(
    val item: String? = null,
    val qty: String? = null,
    val amount: Double? = null,
    val type: String = "cash",
    val party: String? = null,
)

@Serializable
private data class SaleParseDto(
    val entries: List<SaleEntryDto> = emptyList(),
)

/**
 * Turns raw LLM output into [SaleEntry]s. Never throws: on any extraction/parse failure
 * it returns an empty list so the caller can fall back to manual entry (CLAUDE-1.md §6).
 */
class SaleParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(rawContent: String): List<SaleEntry> {
        val jsonStr = JsonParser.extractJson(rawContent) ?: return emptyList()
        return try {
            json.decodeFromString(SaleParseDto.serializer(), jsonStr).entries.map {
                SaleEntry(
                    item = it.item.clean(),
                    qty = it.qty.clean(),
                    amount = it.amount,
                    type = it.type.clean() ?: "cash",
                    party = it.party.clean(),
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** The model sometimes emits the literal strings "null"/"none" or blanks instead of JSON null. */
    private fun String?.clean(): String? =
        this?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) || it.equals("none", true) }
}
