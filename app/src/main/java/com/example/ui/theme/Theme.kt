package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShortsStudioColorScheme = darkColorScheme(
    primary = ShortsRed,
    onPrimary = Color.White,
    primaryContainer = ShortsRedContainer,
    onPrimaryContainer = OnShortsRedContainer,
    secondary = TealAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D47),
    onSecondaryContainer = Color(0xFF80FFF2),
    tertiary = AmberAccent,
    onTertiary = Color.Black,
    background = DarkCanvas,
    onBackground = TextHighEmphasis,
    surface = DarkSurface,
    onSurface = TextHighEmphasis,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextMediumEmphasis,
    outline = DarkOutline,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShortsStudioColorScheme,
        typography = Typography,
        content = content
    )
}
