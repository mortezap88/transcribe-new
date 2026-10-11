package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackColorScheme = darkColorScheme(
    primary = Color(0xFF00B0FF),
    onPrimary = Color.Black,
    secondary = Color(0xFF81D4FA),
    onSecondary = Color.Black,
    tertiary = Color(0xFF00E676),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB0BEC5)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlackColorScheme,
        typography = Typography,
        content = content
    )
}
