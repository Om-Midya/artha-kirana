package com.artha.kirana.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Dark-only — there is no light mode (the canvas IS the product). Tokens from Color.kt.
 * Money semantics: mint = in/credit · hot-pink = out/debit · yellow = udhaar · ultraviolet = today.
 */
private val ArthaColors = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = Mint,
    onPrimaryContainer = Ink,
    secondary = Ultraviolet,
    onSecondary = HazardWhite,
    secondaryContainer = Slate,
    onSecondaryContainer = HazardWhite,
    tertiary = TileYellow,
    onTertiary = Ink,
    error = HotPink,
    onError = Ink,
    background = Canvas,
    onBackground = HazardWhite,
    surface = Canvas,
    onSurface = HazardWhite,
    surfaceVariant = Slate,
    onSurfaceVariant = TextMuted,
    outline = Line,
    outlineVariant = Line,
    scrim = Canvas,
)

/** Radius scale (design brief): xs/sm for inputs+tags, md buttons, lg cards, xl the loudest pill. */
private val ArthaShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),   // inputs / tags
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(24.dp),      // buttons
    large = RoundedCornerShape(20.dp),       // cards
    extraLarge = RoundedCornerShape(40.dp),  // outlined CTA
)

@Composable
fun ArthaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ArthaColors,
        typography = Typography,
        shapes = ArthaShapes,
        content = content,
    )
}
