package com.example.runapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- UPDATED DARK THEME ---
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,    // Keep Blue
    secondary = SecondaryBlue,
    background = Color.Black, // <--- PITCH BLACK BACKGROUND
    surface = Color.Black,    // <--- PITCH BLACK SURFACES (Cards will need borders or lighter grays to be visible)
    onPrimary = White,
    onBackground = White,
    onSurface = White,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryBlue,
    background = NearWhite,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onBackground = DarkGray,
    onSurface = DarkGray,
)

@Composable
fun RunAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // ... (Rest of the function stays exactly the same) ...
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = HeaderBlueStart.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}