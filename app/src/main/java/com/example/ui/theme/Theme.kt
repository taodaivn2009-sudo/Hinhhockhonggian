package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ObservatoryDarkColorScheme = darkColorScheme(
    primary = IndigoBright,
    onPrimary = SlateTextPrimary,
    primaryContainer = IndigoPrimary,
    onPrimaryContainer = SlateTextPrimary,
    secondary = CyanAccent,
    onSecondary = SlateDeepest,
    secondaryContainer = SlateSurface,
    onSecondaryContainer = CyanAccent,
    tertiary = EmeraldAccent,
    onTertiary = SlateDeepest,
    background = SlateDeepest,
    onBackground = SlateTextPrimary,
    surface = SlateDark,
    onSurface = SlateTextPrimary,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateBorder,
    outlineVariant = SlateMuted,
    error = RoseAccent,
    onError = SlateTextPrimary
)

@Composable
fun Geo3DTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ObservatoryDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
