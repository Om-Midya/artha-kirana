package com.artha.kirana.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Artha design system — "The Verge, adapted". Near-black canvas, acid-mint + ultraviolet
 * hazard-tape accents, flat depth (1px hairlines + saturated color blocks, never shadows).
 * Dark-only — there is no light mode. Components read these tokens (directly or via the
 * MaterialTheme colorScheme mapping in Theme.kt).
 */

// Canvas / surfaces
val Canvas = Color(0xFF131313)       // bg + surface — the product. Cards = canvas + hairline.
val Slate = Color(0xFF2D2D2D)        // surfaceAlt — inputs / secondary fills
val Line = Color(0xFF313131)         // quiet hairline border
val LineStrong = Color(0xFFFFFFFF)   // feature / active hairline

// Accents (hazard tape)
val Mint = Color(0xFF3CFFD0)         // primary — CTAs, kickers, active, MONEY IN / credit
val Ultraviolet = Color(0xFF5200FF)  // accent — secondary hazard, TODAY bar
val TileYellow = Color(0xFFFFD84D)   // tile yellow — UDHAAR
val HotPink = Color(0xFFFF3B6B)      // danger — MONEY OUT / debit

// Text
val HazardWhite = Color(0xFFFFFFFF)  // text
val TextMuted = Color(0xFF949494)    // metadata
val Ink = Color(0xFF000000)          // text on mint / yellow / white tiles

// ── Back-compat aliases ────────────────────────────────────────────────────────
// Older screens reference these names; repoint them at the new palette so the whole
// app recolors at once. New code should prefer the tokens above.
val BrandGold = Mint
val BrandDark = Canvas
val AccentGreen = Mint        // money in / positive
val AccentRed = HotPink       // money out / negative
val AccentBlue = Ultraviolet
val SurfaceDark = Canvas
val OnGold = Ink
val OnDark = HazardWhite
