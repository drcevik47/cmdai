package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ConsoleColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = TerminalBg,
    primaryContainer = TerminalSurfaceElevated,
    onPrimaryContainer = NeonGreen,
    secondary = NeonCyan,
    onSecondary = TerminalBg,
    secondaryContainer = TerminalSurface,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonAmber,
    onTertiary = TerminalBg,
    background = TerminalBg,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = TerminalBorder,
    error = NeonRed,
    onError = TerminalBg
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ConsoleColorScheme,
        typography = Typography,
        content = content
    )
}
