package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = CardWhite,
    background = DarkText,
    onBackground = BackgroundBeige,
    surface = DarkText,
    onSurface = BackgroundBeige
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = CardWhite,
    background = BackgroundBeige,
    onBackground = DarkText,
    surface = CardWhite,
    onSurface = DarkText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
