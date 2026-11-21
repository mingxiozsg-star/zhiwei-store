package com.zhiwei.store.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ZhiweiPink = Color(0xFFFF4D7D)
val ZhiweiPinkDark = Color(0xFFB3124A)
val ZhiweiInk = Color(0xFF1C1C22)
val ZhiweiMist = Color(0xFFFFF7FA)
val ZhiweiLine = Color(0xFFF0E3E9)

private val LightColors = lightColorScheme(
    primary = ZhiweiPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3B0016),
    secondary = Color(0xFF6D5B66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7EE),
    onSecondaryContainer = Color(0xFF231A20),
    background = ZhiweiMist,
    onBackground = ZhiweiInk,
    surface = Color.White,
    onSurface = ZhiweiInk,
    surfaceVariant = Color(0xFFF7EEF3),
    onSurfaceVariant = Color(0xFF5C4E56),
    outline = Color(0xFFD9C6CE),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C6),
    onPrimary = Color(0xFF5E0022),
    primaryContainer = Color(0xFF8A0E3B),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFD8C2CC),
    onSecondary = Color(0xFF3B2C34),
    secondaryContainer = Color(0xFF3A2A33),
    onSecondaryContainer = Color(0xFFF5DEE8),
    background = Color(0xFF121014),
    onBackground = Color(0xFFEDE0E6),
    surface = Color(0xFF1B171C),
    onSurface = Color(0xFFEDE0E6),
    surfaceVariant = Color(0xFF2A2228),
    onSurfaceVariant = Color(0xFFD4C2CA),
    outline = Color(0xFF53444C),
    error = Color(0xFFFFB4AB)
)

@Composable
fun ZhiweiStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}