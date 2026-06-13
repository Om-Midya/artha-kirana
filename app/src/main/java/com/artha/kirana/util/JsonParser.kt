package com.artha.kirana.util

/**
 * Safe JSON extraction from LLM output (CLAUDE-1.md §6). The model may wrap JSON in
 * markdown fences, preamble, or trailing text despite the system prompt, so we strip
 * fences and slice from the first '{' to the last '}'.
 */
object JsonParser {
    fun extractJson(raw: String): String? {
        val stripped = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }
}
