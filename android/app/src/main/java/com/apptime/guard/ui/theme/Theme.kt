package com.apptime.guard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6DF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E8FF),
    secondary = Color(0xFFFF9F43),
    background = Color(0xFFF7F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F6),
    onSurface = Color(0xFF1F2430),
    error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7D92FF),
    onPrimary = Color(0xFF102046),
    background = Color(0xFF141833),
    surface = Color(0xFF1D2342),
    onSurface = Color(0xFFE6E8F0)
)

@Composable
fun AppTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
