package com.example.obdlab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BC4FF),
    onPrimary = Color(0xFF002F55),
    primaryContainer = Color(0xFF16466B),
    onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFFB7C9DE),
    onSecondary = Color(0xFF223242),
    background = Color(0xFF101419),
    onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF101419),
    onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF29313B),
    onSurfaceVariant = Color(0xFFC0C8D2),
    outline = Color(0xFF87929E),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF195B91),
    onPrimary = Color.White,
    background = Color(0xFFFAFBFF),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFAFBFF),
    onSurface = Color(0xFF171C22),
)

@Composable
fun OBDLabTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Deliberately avoid device-derived dynamic colors for consistent contrast.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
