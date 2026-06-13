package com.artha.kirana.util

/**
 * Converts Hindi number-words in free text to digits, BEFORE the text reaches the LLM.
 *
 * Why: the on-device 3B reliably extracts structure but mis-converts Hindi number-words
 * (पचास→40, साठ→70) when a quantity number sits nearby. Feeding it digits removes that
 * ambiguity — verified against the live server. Number-word→digit is deterministic, so it
 * belongs in code, not in the model.
 *
 * Covers cardinals commonly used as kirana prices/quantities (units, tens, the X5 series),
 * सौ/हज़ार/लाख scaling and compounds (दो सौ पचास=250), and the standalone fractions डेढ़/ढाई.
 * Devanagari is folded (nukta + chandrabindu→anusvara) so ASR spelling variants still match.
 * Repeated cardinals ("बीस बीस" = 20 each) are kept separate, not summed.
 */
object HindiNumbers {

    private fun fold(s: String): String =
        s.replace("़", "")        // ़ nukta
            .replace("ँ", "ं") // ँ chandrabindu -> ं anusvara

    private val CARDINALS: Map<String, Double> = listOf(
        "शून्य" to 0, "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5, "छह" to 6,
        "छे" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11, "बारह" to 12,
        "तेरह" to 13, "चौदह" to 14, "पंद्रह" to 15, "पन्द्रह" to 15, "सोलह" to 16, "सत्रह" to 17,
        "अठारह" to 18, "उन्नीस" to 19, "बीस" to 20, "पच्चीस" to 25, "तीस" to 30, "पैंतीस" to 35,
        "चालीस" to 40, "पैंतालीस" to 45, "पचास" to 50, "पचपन" to 55, "साठ" to 60, "पैंसठ" to 65,
        "सत्तर" to 70, "पचहत्तर" to 75, "अस्सी" to 80, "पचासी" to 85, "नब्बे" to 90, "पंचानवे" to 95,
    ).associate { (k, v) -> fold(k) to v.toDouble() }

    private val FRACTIONS: Map<String, Double> = listOf(
        "डेढ़" to 1.5, "ढाई" to 2.5,
    ).associate { (k, v) -> fold(k) to v }

    private val SCALES: Map<String, Double> = listOf(
        "सौ" to 100.0, "हज़ार" to 1000.0, "हजार" to 1000.0, "लाख" to 100000.0,
    ).associate { (k, v) -> fold(k) to v }

    private val PUNCT = charArrayOf(',', '।', '.', '!', '?')

    /** Replace Hindi number-word runs with digits; non-number tokens pass through unchanged. */
    fun normalize(text: String): String {
        val tokens = text.split(" ")
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val (value, consumed) = parseRun(tokens, i)
            if (consumed > 0) {
                out.add(format(value))
                i += consumed
            } else {
                out.add(tokens[i])
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun parseRun(tokens: List<String>, start: Int): Pair<Double, Int> {
        var result = 0.0
        var current = 0.0
        var any = false
        var prevCardinal = false // last consumed was a cardinal/fraction (not a scale)
        var i = start
        while (i < tokens.size) {
            val w = fold(tokens[i].trim(*PUNCT))
            val card = CARDINALS[w] ?: FRACTIONS[w]
            when {
                card != null -> {
                    if (prevCardinal) break // two cardinals in a row → distributive idiom, split
                    current += card
                    prevCardinal = true
                    any = true
                }
                SCALES.containsKey(w) -> {
                    val s = SCALES.getValue(w)
                    if (s == 100.0) {
                        current = (if (current == 0.0) 1.0 else current) * 100.0
                    } else {
                        result += (if (current == 0.0) 1.0 else current) * s
                        current = 0.0
                    }
                    prevCardinal = false
                    any = true
                }
                else -> break
            }
            i++
        }
        return if (!any) 0.0 to 0 else (result + current) to (i - start)
    }

    private fun format(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
