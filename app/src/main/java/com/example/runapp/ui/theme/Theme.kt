package com.example.runapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// We define that "Primary" means "NeonYellow"
private val DarkColorScheme = darkColorScheme(
    primary = NeonYellow,
    onPrimary = DarkBlack, // Text color on top of yellow buttons
    background = DarkBlack,
    surface = DarkSurface,
    onSurface = WhiteText,
    error = DangerRed
)

@Composable
fun RunAppTheme(
    // We force dark mode because your design is dark!
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}