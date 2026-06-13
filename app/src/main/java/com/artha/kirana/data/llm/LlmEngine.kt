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
        // CLAUDE.md §5, plus one §18-driven rule (party postposition stripping) added in Task 1.7.
        // Validated 5/5 on §18 cases against Qwen 2.5 3B. Re-run /tmp/tune.py-style harness if you change this.
        const val SALE_SYSTEM_PROMPT = """You are a kirana store billing assistant. Parse the input into JSON only.
Return ONLY the JSON object. No explanation. No markdown. No preamble.
No "here is the JSON". Just the raw JSON object.

Schema:
{"entries":[{"item":string|null,"qty":string|null,"amount":number|null,"type":"cash"|"credit"|"repayment","party":string|null}]}

Rules:
- type defaults to "cash" if not mentioned
- "उधार" or "udhaar" means type = "credit"
- "दिए" or "ne diya" means type = "repayment"
- party is ONLY the person's name. Strip postpositions like "को"/"ko"/"ने"/"ne"/"से"/"se" (e.g. "रमेश को" -> "रमेश", "Ramesh ko" -> "Ramesh")
- Multiple items in one sentence = multiple entries in the array
- Return ONLY valid JSON. Nothing else."""
    }
}
