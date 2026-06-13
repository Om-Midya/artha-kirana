package com.artha.kirana.data.llm

import com.artha.kirana.data.remote.LlmHttpClient
import com.artha.kirana.data.remote.LlmUnavailableException
import com.artha.kirana.domain.model.SaleEntry
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
        val content = client.chat(SALE_SYSTEM_PROMPT, text)
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
- qty = amount of product WITH its unit (e.g. "2 किलो", "3"). Never put the rupee price here.
- amount = the total rupee PRICE as a plain number in digits.
- party = person's name only. Strip "को/ko/ने/ne/से/se".

Convert Hindi number words to digits:
एक=1 दो=2 तीन=3 चार=4 पाँच=5 छह=6 सात=7 आठ=8 नौ=9 दस=10 ग्यारह=11 बारह=12
बीस=20 तीस=30 चालीस=40 पचास=50 साठ=60 सत्तर=70 अस्सी=80 नब्बे=90 सौ=100 हज़ार=1000
पैंतालीस=45 पचपन=55 पैंसठ=65 पचहत्तर=75 पचासी=85 पंचानवे=95
Compounds add up: "दो सौ"=200, "दो सौ पचास"=250, "साढ़े तीन सौ"=350.
Fractions: डेढ़=1.5 ढाई=2.5 सवा=1.25 पौने=0.75 आधा=0.5.

Rules:
- type defaults to "cash". "उधार"/"udhaar" = "credit". "दिए"/"ne diya" = "repayment".
- "X X के" (e.g. "बीस बीस के") = each unit costs X; set amount = qty × X.
- ONE product = ONE entry. Do not split one product into multiple entries.
- Return ONLY valid JSON.

Examples:
Input: दो किलो चावल अस्सी रुपये
{"entries":[{"item":"चावल","qty":"2 किलो","amount":80,"type":"cash","party":null}]}
Input: रमेश को दस किलो आटा तीन सौ का उधार
{"entries":[{"item":"आटा","qty":"10 किलो","amount":300,"type":"credit","party":"रमेश"}]}
Input: तीन साबुन बीस बीस के
{"entries":[{"item":"साबुन","qty":"3","amount":60,"type":"cash","party":null}]}
Input: रमेश ने पचास रुपये दिए
{"entries":[{"item":null,"qty":null,"amount":50,"type":"repayment","party":"रमेश"}]}
Input: ढाई किलो दाल नब्बे रुपये
{"entries":[{"item":"दाल","qty":"2.5 किलो","amount":90,"type":"cash","party":null}]}"""
    }
}
