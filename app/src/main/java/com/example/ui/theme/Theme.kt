package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF002029),
    primaryContainer = JarvisSurfaceVariant,
    onPrimaryContainer = JarvisCyanLight,
    secondary = JarvisElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = JarvisSurface,
    onSecondaryContainer = JarvisCyan,
    tertiary = JarvisAccentOrange,
    onTertiary = Color.Black,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorder,
    outlineVariant = JarvisBorderGlow,
    error = JarvisAlertRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
