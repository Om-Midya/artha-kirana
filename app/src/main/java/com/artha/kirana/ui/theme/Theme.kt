package com.artha.kirana.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ArthaDarkColors = darkColorScheme(
    primary = BrandGold,
    onPrimary = OnGold,
    secondary = AccentBlue,
    onSecondary = OnDark,
    tertiary = AccentGreen,
    onTertiary = OnDark,
    error = AccentRed,
    background = BrandDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
)

private val ArthaLightColors = lightColorScheme(
    primary = BrandGold,
    onPrimary = OnGold,
    secondary = AccentBlue,
    tertiary = AccentGreen,
    error = AccentRed,
)

@Composable
fun ArthaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ArthaDarkColors else ArthaLightColors,
        typography = Typography,
        content = content,
    )
}
