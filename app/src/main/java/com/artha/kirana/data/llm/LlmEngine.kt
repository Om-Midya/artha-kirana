package com.artha.kirana.data.llm

import com.artha.kirana.data.remote.LlmHttpClient
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.model.SaleEntry
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates on-device LLM calls: builds the prompt, calls [LlmHttpClient], extracts JSON.
 * Returns a [Result] so callers can degrade gracefully when the server is offline.
 */
@Singleton
class LlmEngine @Inject constructor(
    private val client: LlmHttpClient,
    private val saleParser: SaleParser,
) {
    suspend fun health(): Boolean = client.health()

    suspend fun parseSale(text: String): Result<List<SaleEntry>> = try {
        val content = client.chat(SALE_SYSTEM_PROMPT, text, SALE_RESPONSE_FORMAT)
        Result.success(saleParser.parse(content))
    } catch (e: LlmUnavailableException) {
        Result.failure(e)
    }

    companion object {
        // CLAUDE.md §5 + Devanagari hardening (Phase 4): once whisper started feeding clean spoken
        // Hindi (number WORDS, not digits), Qwen mis-read amounts (अस्सी→2000, नब्बे→11) and leaked
        // prices into qty/item. Added a Hindi number table + field-meaning rules + Devanagari few-shot.
        // Validated against the live llama-server. KEEP scripts/validate-sale-prompt.py in sync.
        const val SALE_SYSTEM_PROMPT = """You are a kirana store billing assistant. Parse the input into JSON only.
Return ONLY the JSON object. No explanation. No markdown. No preamble. Just the raw JSON object.

Schema:
{"entries":[{"item":string|null,"qty":string|null,"amount":number|null,"type":"cash"|"credit"|"repayment","party":string|null}]}

Field meaning:
- item = the product name only (e.g. "चावल"). Never include a price or number in item.
- qty = quantity WITH its unit in ENGLISH (e.g. "2 kg", "3", "1 dozen"). Never put the rupee price here.
- amount = the total rupee PRICE as a plain number in digits.
- party = the person's name ONLY. Remove any को/ko/ने/ne/से/se tokens before AND after it (e.g. "रमेश को"→"रमेश", "ने रिया ने"→"रिया").

Convert Hindi number words to digits:
एक=1 दो=2 तीन=3 चार=4 पाँच=5 छह=6 सात=7 आठ=8 नौ=9 दस=10 ग्यारह=11 बारह=12
बीस=20 तीस=30 चालीस=40 पचास=50 साठ=60 सत्तर=70 अस्सी=80 नब्बे=90 सौ=100 हज़ार=1000
पैंतालीस=45 पचपन=55 पैंसठ=65 पचहत्तर=75 पचासी=85 पंचानवे=95
Compounds add up: "दो सौ"=200, "दो सौ पचास"=250, "साढ़े तीन सौ"=350.
Fractions: डेढ़=1.5 ढाई=2.5 सवा=1.25 पौने=0.75 आधा=0.5.

Translate units to English: किलो/किलोग्राम=kg, ग्राम=g, लीटर=litre, मिलीलीटर=ml, दर्जन=dozen, पैकेट=packet, पाव=kg (पाव=0.25 kg), अद्धा=kg (अद्धा=0.5 kg).

Rules:
- type defaults to "cash". "उधार"/"udhaar" = "credit". "दिए"/"ne diya" = "repayment".
- "X X के" (e.g. "बीस बीस के") = each unit costs X; set amount = qty × X.
- ONE product = ONE entry. Do not split one product into multiple entries.
- If NO rupee price is spoken, amount = null. NEVER invent a price from the quantity ("4 किलो" does NOT mean amount 40).
- The party may come BEFORE the items (e.g. "रिया ने ..."). "लिया/लिया है" = that party took the goods.
- Return ONLY valid JSON.

Examples:
Input: दो किलो चावल अस्सी रुपये
{"entries":[{"item":"चावल","qty":"2 kg","amount":80,"type":"cash","party":null}]}
Input: रमेश को दस किलो आटा तीन सौ का उधार
{"entries":[{"item":"आटा","qty":"10 kg","amount":300,"type":"credit","party":"रमेश"}]}
Input: तीन साबुन बीस बीस के
{"entries":[{"item":"साबुन","qty":"3","amount":60,"type":"cash","party":null}]}
Input: रमेश ने पचास रुपये दिए
{"entries":[{"item":null,"qty":null,"amount":50,"type":"repayment","party":"रमेश"}]}
Input: ढाई किलो दाल नब्बे रुपये
{"entries":[{"item":"दाल","qty":"2.5 kg","amount":90,"type":"cash","party":null}]}
Input: ने रिया ने चार किलो दाल और पांच किलो आलू लिया
{"entries":[{"item":"दाल","qty":"4 kg","amount":null,"type":"cash","party":"रिया"},{"item":"आलू","qty":"5 kg","amount":null,"type":"cash","party":"रिया"}]}"""

        /**
         * Forces llama-server to emit JSON matching the sale schema (llama.cpp converts this to a
         * GBNF grammar). Guarantees valid structure + a valid `type` enum every time — no markdown,
         * preamble, or malformed JSON regardless of how the model would otherwise drift.
         */
        val SALE_RESPONSE_FORMAT = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "sale")
                putJsonObject("schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("entries") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("item") { putJsonArray("type") { add("string"); add("null") } }
                                    putJsonObject("qty") { putJsonArray("type") { add("string"); add("null") } }
                                    putJsonObject("amount") { putJsonArray("type") { add("number"); add("null") } }
                                    putJsonObject("type") { putJsonArray("enum") { add("cash"); add("credit"); add("repayment") } }
                                    putJsonObject("party") { putJsonArray("type") { add("string"); add("null") } }
                                }
                                putJsonArray("required") { add("item"); add("qty"); add("amount"); add("type"); add("party") }
                            }
                        }
                    }
                    putJsonArray("required") { add("entries") }
                }
            }
        }
    }
}
