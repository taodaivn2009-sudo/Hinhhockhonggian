package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Reactive semantic palette tokens backed by ActiveThemeState so switching Dark/Light,
// curated presets, or custom accent colors updates every workspace instantaneously.
val SlateDeepest: Color get() = ActiveThemeState.current.bgDeepest
val SlateDark: Color get() = ActiveThemeState.current.bgDark
val SlateSurface: Color get() = ActiveThemeState.current.surface
val SlateBorder: Color get() = ActiveThemeState.current.border
val SlateMuted: Color get() = ActiveThemeState.current.muted
val SlateTextSecondary: Color get() = ActiveThemeState.current.textSecondary
val SlateTextLight: Color get() = ActiveThemeState.current.textLight
val SlateTextPrimary: Color get() = ActiveThemeState.current.textPrimary

val IndigoPrimary: Color get() = ActiveThemeState.current.primary
val IndigoBright: Color get() = ActiveThemeState.current.primaryBright
val IndigoLight: Color get() = ActiveThemeState.current.primaryLight
val IndigoSoft: Color get() = ActiveThemeState.current.primarySoft

val CyanAccent: Color get() = ActiveThemeState.current.cyanAccent
val SkyBlue: Color get() = ActiveThemeState.current.skyBlue
val EmeraldAccent: Color get() = ActiveThemeState.current.emeraldAccent
val EmeraldLight: Color get() = ActiveThemeState.current.emeraldLight
val EmeraldDark: Color get() = ActiveThemeState.current.emeraldDark
val AmberAccent: Color get() = ActiveThemeState.current.amberAccent
val AmberDeep: Color get() = ActiveThemeState.current.amberDeep
val RoseAccent: Color get() = ActiveThemeState.current.roseAccent
val RoseLight: Color get() = ActiveThemeState.current.roseLight
val PinkAccent: Color get() = ActiveThemeState.current.pinkAccent
val PinkLight: Color get() = ActiveThemeState.current.pinkLight
val RedAxis = Color(0xFFEF4444)
val GreenAxis = Color(0xFF22C55E)
val BlueAxis = Color(0xFF3B82F6)
val PurpleAccent: Color get() = ActiveThemeState.current.purpleAccent
