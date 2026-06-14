@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.artha.kirana.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.artha.kirana.R

/**
 * Three voices (design brief):
 * - [Display] Anton — the "shout": ARTHA wordmark, hero ₹ number, screen titles (≥36sp only).
 * - [Body] Hanken Grotesk — body, headlines ≤24sp, the thin-300 capitalized "whisper" eyebrow.
 * - [Mono] Space Mono — EVERY uppercase label: kickers, timestamps, tags, tabs, button text.
 *   Always uppercase + tracking.
 */
val Display = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))

val Body = FontFamily(
    Font(R.font.hanken_grotesk, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.hanken_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.hanken_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.hanken_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.hanken_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/**
 * Material typography mapped to the three voices. Display slots = Anton; titles/body = Hanken;
 * label slots = Space Mono with tracking (callers uppercase the text, per the brief).
 */
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 40.sp),

    headlineMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),

    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    // Label slots = Space Mono, tracked. The brief: every label is UPPERCASE + tracking.
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 1.4.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp),
)
