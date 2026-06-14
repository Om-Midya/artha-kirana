package com.artha.kirana.data.llm

import com.artha.kirana.data.remote.ChatClient
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
    private val client: ChatClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun classify(text: String): Result<AssistantIntent> = try {
        Result.success(parseIntent(client.chat(INTENT_SYSTEM_PROMPT, text, INTENT_RESPONSE_FORMAT)))
    } catch (e: LlmUnavailableException) {
        Result.failure(e)
    }

    /**
     * Primes the server with the intent system prompt so the first real classify reuses the
     * cached prefix (KV cache) and returns fast. Fire-and-forget; swallows errors (server may
     * be offline). The result is intentionally discarded.
     */
    suspend fun warmUp() {
        runCatching { client.chat(INTENT_SYSTEM_PROMPT, "नमस्ते", INTENT_RESPONSE_FORMAT) }
    }

    /** Pure mapping of raw LLM content → intent; never throws (unknown/garbage → UNKNOWN). */
    fun parseIntent(rawContent: String): AssistantIntent {
        val jsonStr = JsonParser.extractJson(rawContent) ?: return AssistantIntent.UNKNOWN
        return try {
            when (json.decodeFromString(IntentDto.serializer(), jsonStr).intent.trim().lowercase()) {
                "log_sale" -> AssistantIntent.LOG_SALE
                "record_payment" -> AssistantIntent.RECORD_PAYMENT
                "query_pnl" -> AssistantIntent.QUERY_PNL
                "query_top_sellers" -> AssistantIntent.QUERY_TOP_SELLERS
                "query_customer" -> AssistantIntent.QUERY_CUSTOMER
                "query_day_trend" -> AssistantIntent.QUERY_DAY_TREND
                else -> AssistantIntent.UNKNOWN
            }
        } catch (t: Throwable) {
            AssistantIntent.UNKNOWN
        }
    }

    companion object {
        const val INTENT_SYSTEM_PROMPT = """You are a router for a kirana shop assistant. Read the shopkeeper's message (Hindi/Hinglish) and output ONLY which action it wants, as JSON.
Return ONLY: {"intent": one of "log_sale" | "record_payment" | "query_pnl" | "query_top_sellers" | "query_customer" | "query_day_trend" | "unknown"}
No explanation. No markdown. Just the raw JSON object.

Meaning:
- log_sale = recording a sale/purchase of goods (items + quantity, cash or उधार/credit). e.g. selling rice, sugar, soap.
- record_payment = a customer PAID BACK money they owed (दिए / चुकाए / चुका दिया / जमा / paid). No goods involved.
- query_pnl = asking about TOTAL earnings/profit/sales over a period (कमाई, मुनाफा, बिक्री, कितना कमाया; today/week/month).
- query_top_sellers = asking WHICH ITEMS sell the most (सबसे ज्यादा क्या बिका, बेस्ट सेलर, टॉप आइटम, कौन सा सामान ज्यादा बिकता है). A per-item ranking, NOT a total.
- query_customer = asking about ONE customer's account: how much they owe, their total, their history (रमेश का हिसाब, प्रिया कितना बकाया है, सुरेश ने कुल कितना लिया). Names a person.
- query_day_trend = asking WHICH DAY/weekday is busiest or sells most (कौन सा दिन सबसे busy, किस दिन सबसे ज्यादा बिक्री, सबसे अच्छा दिन).
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
Input: सबसे ज्यादा क्या बिका
{"intent":"query_top_sellers"}
Input: इस महीने के टॉप आइटम कौन से हैं
{"intent":"query_top_sellers"}
Input: रमेश का हिसाब बताओ
{"intent":"query_customer"}
Input: प्रिया कितना बकाया है
{"intent":"query_customer"}
Input: कौन सा दिन सबसे busy रहता है
{"intent":"query_day_trend"}
Input: किस दिन सबसे ज्यादा बिक्री होती है
{"intent":"query_day_trend"}
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
                                add("log_sale"); add("record_payment"); add("query_pnl")
                                add("query_top_sellers"); add("query_customer"); add("query_day_trend")
                                add("unknown")
                            }
                        }
                    }
                    putJsonArray("required") { add("intent") }
                }
            }
        }
    }
}
