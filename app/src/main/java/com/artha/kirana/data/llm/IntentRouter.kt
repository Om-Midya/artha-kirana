package com.artha.kirana.data.llm

import com.artha.kirana.data.remote.LlmHttpClient
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.model.AssistantIntent
import com.artha.kirana.util.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class IntentDto(val intent: String = "unknown")

/**
 * Stage 1 of the Assistant router: classifies an utterance into an [AssistantIntent] with one
 * grammar-constrained LLM call (enum-only json_schema → reliable for Qwen 3B). Holds the prompt
 * + grammar as the single source of truth — keep scripts/validate-intent-prompt.py in sync.
 */
@Singleton
class IntentRouter @Inject constructor(
    private val client: LlmHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun classify(text: String): Result<AssistantIntent> = try {
        Result.success(parseIntent(client.chat(INTENT_SYSTEM_PROMPT, text, INTENT_RESPONSE_FORMAT)))
    } catch (e: LlmUnavailableException) {
        Result.failure(e)
    }

    /** Pure mapping of raw LLM content → intent; never throws (unknown/garbage → UNKNOWN). */
    fun parseIntent(rawContent: String): AssistantIntent {
        val jsonStr = JsonParser.extractJson(rawContent) ?: return AssistantIntent.UNKNOWN
        return try {
            when (json.decodeFromString(IntentDto.serializer(), jsonStr).intent.trim().lowercase()) {
                "log_sale" -> AssistantIntent.LOG_SALE
                "record_payment" -> AssistantIntent.RECORD_PAYMENT
                "query_pnl" -> AssistantIntent.QUERY_PNL
                else -> AssistantIntent.UNKNOWN
            }
        } catch (t: Throwable) {
            AssistantIntent.UNKNOWN
        }
    }

    companion object {
        const val INTENT_SYSTEM_PROMPT = """You are a router for a kirana shop assistant. Read the shopkeeper's message (Hindi/Hinglish) and output ONLY which action it wants, as JSON.
Return ONLY: {"intent": one of "log_sale" | "record_payment" | "query_pnl" | "unknown"}
No explanation. No markdown. Just the raw JSON object.

Meaning:
- log_sale = recording a sale/purchase of goods (items + quantity, cash or उधार/credit). e.g. selling rice, sugar, soap.
- record_payment = a customer PAID BACK money they owed (दिए / चुकाए / चुका दिया / जमा / paid). No goods involved.
- query_pnl = asking about earnings/profit/sales totals (कमाई, मुनाफा, बिक्री, कितना कमाया; today/week/month).
- unknown = anything else.

Examples:
Input: दो किलो चावल अस्सी रुपये उधार रमेश को
{"intent":"log_sale"}
Input: तीन साबुन बीस बीस के
{"intent":"log_sale"}
Input: रमेश ने पचास रुपये दिए
{"intent":"record_payment"}
Input: प्रिया ने अपना उधार चुका दिया सौ रुपये
{"intent":"record_payment"}
Input: आज की कमाई कितनी हुई
{"intent":"query_pnl"}
Input: इस हफ्ते का मुनाफा बताओ
{"intent":"query_pnl"}
Input: नमस्ते
{"intent":"unknown"}"""

        val INTENT_RESPONSE_FORMAT = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "intent")
                putJsonObject("schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("intent") {
                            putJsonArray("enum") {
                                add("log_sale"); add("record_payment"); add("query_pnl"); add("unknown")
                            }
                        }
                    }
                    putJsonArray("required") { add("intent") }
                }
            }
        }
    }
}
