package com.artha.kirana.data.llm

import com.artha.kirana.util.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Extracted khata-repayment args from the LLM. */
data class ParsedPayment(val party: String?, val amount: Double?)

@Serializable
private data class PaymentDto(
    val party: String? = null,
    val amount: Double? = null,
)

/**
 * Turns raw LLM output into a [ParsedPayment]. Never throws: on any extraction/parse failure
 * it returns null so the caller can fall back to a manual confirm card (CLAUDE.md §6).
 * Mirrors [SaleParser].
 */
class PaymentParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(rawContent: String): ParsedPayment? {
        val jsonStr = JsonParser.extractJson(rawContent) ?: return null
        return try {
            val dto = json.decodeFromString(PaymentDto.serializer(), jsonStr)
            ParsedPayment(party = dto.party.clean(), amount = dto.amount)
        } catch (t: Throwable) {
            null
        }
    }

    /** The model sometimes emits the literal strings "null"/"none" or blanks instead of JSON null. */
    private fun String?.clean(): String? =
        this?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) || it.equals("none", true) }
}
