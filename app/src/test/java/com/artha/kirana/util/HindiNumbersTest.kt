package com.artha.kirana.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HindiNumbersTest {

    @Test fun roundTensThatQwenMisreads() {
        assertEquals("2 किलो चावल 80 रुपये", HindiNumbers.normalize("दो किलो चावल अस्सी रुपये"))
        // पचास=50 and साठ=60 are the words Qwen flips when a qty number is nearby
        assertEquals("500 ग्राम चीनी 50 रुपये", HindiNumbers.normalize("पाँच सौ ग्राम चीनी पचास रुपये"))
        assertEquals("2 दर्जन अंडे 60 रुपये", HindiNumbers.normalize("दो दर्जन अंडे साठ रुपये"))
    }

    @Test fun compoundsWithSauAndHazaar() {
        assertEquals("250 रुपये का तेल", HindiNumbers.normalize("दो सौ पचास रुपये का तेल"))
        assertEquals("रमेश को 10 किलो आटा 300 का उधार", HindiNumbers.normalize("रमेश को दस किलो आटा तीन सौ का उधार"))
        assertEquals("1500", HindiNumbers.normalize("डेढ़ हज़ार"))
    }

    @Test fun standaloneFractions() {
        assertEquals("2.5 किलो दाल 90 रुपये", HindiNumbers.normalize("ढाई किलो दाल नब्बे रुपये"))
        assertEquals("1.5 किलो", HindiNumbers.normalize("डेढ़ किलो"))
    }

    @Test fun repeatedCardinalIsDistributiveNotSum() {
        // "बीस बीस के" = 20 each, NOT 40 — consecutive same cardinals must stay separate
        assertEquals("3 साबुन 20 20 के", HindiNumbers.normalize("तीन साबुन बीस बीस के"))
    }

    @Test fun nonNumberTextPassesThrough() {
        assertEquals("चावल और दाल", HindiNumbers.normalize("चावल और दाल"))
        assertEquals("hello world", HindiNumbers.normalize("hello world"))
    }

    @Test fun spellingVariantsFold() {
        // "पांच" (anusvara) and "पाँच" (chandrabindu) must both resolve
        assertEquals("5 किलो", HindiNumbers.normalize("पांच किलो"))
        assertEquals("5 किलो", HindiNumbers.normalize("पाँच किलो"))
    }
}
