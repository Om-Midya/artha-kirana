package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import com.artha.kirana.data.llm.SaleParser
import com.artha.kirana.data.remote.dto.ChatCompletionResponse
import com.artha.kirana.domain.model.ParsedBill
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.util.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud OCR via OpenRouter's vision endpoint (Claude Opus 4.8). Two modes:
 * - [extractLedger]: reads a handwritten daily khata into our [SaleEntry] schema (reusing [SaleParser]).
 * - [extractBill]: reads a wholesaler bill into [ParsedBill].
 * Throws on failure (blank key / non-2xx / blank) so the ViewModel shows an error (cloud-only — no
 * local-vision fallback by decision). The image is sent as a base64 data: URI with NO response_format.
 */
@Singleton
class CloudVisionClient @Inject constructor(
    private val client: HttpClient,
    private val saleParser: SaleParser,
) {
    private val apiKey: String get() = BuildConfig.OPENROUTER_KEY
    private val visionModel: String get() = BuildConfig.OPENROUTER_VISION_MODEL

    /** HERO path: handwritten khata photo → our sale/udhaar entries. */
    suspend fun extractLedger(imageBase64: String): List<SaleEntry> =
        saleParser.parse(callVision(LEDGER_SYSTEM, LEDGER_USER, imageBase64))

    /** Wholesaler bill photo → purchase line items. */
    suspend fun extractBill(imageBase64: String): ParsedBill =
        mapBill(callVision(BILL_SYSTEM, BILL_USER, imageBase64))

    private suspend fun callVision(system: String, user: String, imageBase64: String): String {
        if (apiKey.isBlank()) throw LlmUnavailableException(null)
        require(imageBase64.isNotBlank()) { "No image to read." }
        val response = client.post("$BASE_URL/chat/completions") {
            timeout { requestTimeoutMillis = VISION_TIMEOUT_MS }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://artha.kirana")
            header("X-Title", "Artha Kirana")
            setBody(buildVisionBody(visionModel, system, user, imageBase64))
        }
        if (!response.status.isSuccess()) {
            throw LlmUnavailableException(RuntimeException("OpenRouter ${response.status.value}"))
        }
        return response.body<ChatCompletionResponse>().choices.firstOrNull()?.message?.content
            ?: throw LlmUnavailableException(null)
    }

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        private const val VISION_TIMEOUT_MS = 45_000L
        private val mapper = Json { ignoreUnknownKeys = true; isLenient = true }

        // Vision request: NO response_format (not all vision models accept it); image as data: URI.
        private fun buildVisionBody(model: String, system: String, user: String, imageBase64: String): JsonObject =
            buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", user)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,$imageBase64")
                                }
                            }
                        }
                    }
                }
            }

        /** Pure: vision JSON content → [ParsedBill]. Never throws; unreadable → empty bill. */
        fun mapBill(content: String): ParsedBill {
            val jsonStr = JsonParser.extractJson(content) ?: return ParsedBill()
            val root = try {
                mapper.parseToJsonElement(jsonStr).jsonObject
            } catch (t: Throwable) {
                return ParsedBill()
            }
            val items = (root["items"] as? JsonArray)?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.strOrNull()?.trim()
                if (name.isNullOrEmpty() || name.equals("null", true)) return@mapNotNull null
                ParsedPurchaseItem(
                    name = name,
                    qty = o["qty"].dblOrNull() ?: 1.0,
                    unit = o["unit"]?.strOrNull()?.trim()?.ifBlank { "pcs" } ?: "pcs",
                    unitPrice = o["unitPrice"].dblOrNull(),
                    amount = o["amount"].dblOrNull(),
                )
            } ?: emptyList()
            return ParsedBill(items = items, total = root["total"].dblOrNull())
        }

        private fun JsonElement.strOrNull(): String? = (this as? JsonPrimitive)?.content
        private fun JsonElement?.dblOrNull(): Double? {
            val p = (this as? JsonPrimitive) ?: return null
            if (p.content.equals("null", true) || p.content.isBlank()) return null
            return p.content.toDoubleOrNull()
        }

        // LEDGER mode: handwritten daily khata → OUR sale schema (parsed by SaleParser).
        val LEDGER_SYSTEM = """
            You read a photo of a handwritten Indian kirana shopkeeper's daily ledger / bahi-khata
            (Hindi, Hinglish, or English) and convert each line into JSON. Output JSON ONLY — no
            markdown, no commentary.

            Schema:
            {"entries":[{"item":string|null,"qty":string|null,"amount":number|null,"type":"cash"|"credit"|"repayment","party":string|null}]}

            Rules:
            - CRITICAL: Read ONLY what is actually written. Never invent items or numbers. If a line is
              illegible, omit it rather than guess.
            - One JSON entry per ledger line.
            - item = the product name only (e.g. "चावल"/"rice"); null if the line is only a payment.
            - qty = quantity with unit in English (e.g. "2 kg", "3"); null if not written.
            - amount = the rupee amount as a plain number.
            - type = "credit" if it is udhaar/उधार (taken on credit); "repayment" if a customer paid back
              money (दिए/जमा/paid); otherwise "cash".
            - party = the customer's name if the line names a person, else null.
            - Return ONLY valid JSON. If you cannot read any line, return {"entries":[]}.
        """.trimIndent()

        private const val LEDGER_USER =
            "Read this handwritten kirana daily ledger photo and return every line as JSON entries."

        // BILL mode: wholesaler bill → inventory line items (anti-hallucination).
        val BILL_SYSTEM = """
            You read a photo of an Indian wholesaler/grocery BILL or RECEIPT (printed or handwritten)
            and convert it into a strict JSON object. Output JSON ONLY — no markdown, no commentary.

            Schema:
            {"items":[{"name":string,"qty":number,"unit":string,"unitPrice":number|null,"amount":number|null}],"total":number|null}

            Rules:
            - CRITICAL: Read ONLY what is actually written. Transcribe item names EXACTLY as written.
              NEVER invent, guess, or substitute typical grocery items, and do NOT "fill in" a usual
              kirana list. Accuracy over completeness — if a line is illegible, omit it rather than guess.
            - One object per line item printed on the bill. Keep item names short and clean (just the product).
            - qty = quantity bought (default 1). units: kg/किलो->"kg", gram/ग्राम->"g", litre/लीटर/लि->"l",
              packet/पैकेट/नग/piece/pcs->"pcs", dozen/दर्जन->"dozen". Default "pcs".
            - unitPrice = price per unit if shown; amount = the line total (rupees). Use null (not 0) for unknown numbers.
            - total = the grand total / bill amount in rupees if printed, else null.
            - Ignore taxes, shop header, phone numbers and non-item text. If you cannot read any items, return {"items":[],"total":null}.
        """.trimIndent()

        private const val BILL_USER =
            "Read this wholesaler/grocery bill or receipt photo and return the line items and total as JSON."
    }
}
