package com.ynot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Red400,
    onPrimary = Gray900,
    surface = Gray900,
    onSurface = Gray100,
    surfaceVariant = Gray800,
)

private val LightColorScheme = lightColorScheme(
    primary = Red600,
)

@Composable
fun YnotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
