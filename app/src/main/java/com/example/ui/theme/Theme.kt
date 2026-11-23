package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

fun buildMaterialColorScheme(palette: AppThemePalette): ColorScheme =
    if (palette.isDark) {
        darkColorScheme(
            primary = palette.primaryBright,
            onPrimary = Color.White,
            primaryContainer = palette.primary,
            onPrimaryContainer = Color.White,
            secondary = palette.cyanAccent,
            onSecondary = palette.bgDeepest,
            secondaryContainer = palette.surface,
            onSecondaryContainer = palette.cyanAccent,
            tertiary = palette.emeraldAccent,
            onTertiary = palette.bgDeepest,
            background = palette.bgDeepest,
            onBackground = palette.textPrimary,
            surface = palette.bgDark,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.muted,
            error = palette.roseAccent,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.surface,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.cyanAccent,
            onSecondary = Color.White,
            secondaryContainer = palette.surface,
            onSecondaryContainer = palette.textPrimary,
            tertiary = palette.emeraldAccent,
            onTertiary = Color.White,
            background = palette.bgDeepest,
            onBackground = palette.textPrimary,
            surface = palette.bgDark,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.muted,
            error = palette.roseAccent,
            onError = Color.White
        )
    }

@Composable
fun Geo3DTheme(
    config: AppThemeConfig = AppThemeConfig(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = ThemePaletteResolver.resolve(config, systemDark)
    if (ActiveThemeState.current != palette) {
        ActiveThemeState.current = palette
    }
    SideEffect {
        if (ActiveThemeState.current != palette) {
            ActiveThemeState.current = palette
        }
    }

    ProvideLiquidGlassShimmerClock(config = palette.liquidGlass) {
        MaterialTheme(
            colorScheme = buildMaterialColorScheme(palette),
            typography = AppTypography,
            content = content
        )
    }
}
