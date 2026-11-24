package com.example.ui.theme

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tool_toan_theme_prefs", Context.MODE_PRIVATE)

    fun load(): AppThemeConfig {
        val modeName = prefs.getString(KEY_MODE, AppThemeMode.DARK.name)
        val mode = AppThemeMode.entries.firstOrNull { it.name == modeName } ?: AppThemeMode.DARK
        var preset = AppThemePreset.fromId(prefs.getString(KEY_PRESET, AppThemePreset.MIDNIGHT_OBSERVATORY.id))
        val hasCustomAccent = prefs.contains(KEY_ACCENT_ARGB)
        val customAccent = if (hasCustomAccent) prefs.getLong(KEY_ACCENT_ARGB, 0L) else null
        val defaultGlass = LiquidGlassConfig(enabled = false)
        val perfOffMigrated = prefs.getBoolean(KEY_LG_PERF_OFF_V4, false)
        val liquidGlass = if (!perfOffMigrated) {
            if (preset.isLiquidGlass) {
                preset = if (preset.isDark) AppThemePreset.MIDNIGHT_OBSERVATORY else AppThemePreset.IVORY_PAPER
            }
            val migrated = LiquidGlassPresetStyle.MEETUBE.config.copy(
                enabled = false,
                dynamicShimmer = false
            ).sanitized()
            prefs.edit()
                .putString(KEY_PRESET, preset.id)
                .putBoolean(KEY_LG_PERF_OFF_V4, true)
                .putBoolean(KEY_LG_MEETUBE_MIGRATED_V3, true)
                .putBoolean(KEY_LG_PERF_MIGRATED_V2, true)
                .putBoolean(KEY_LG_ENABLED, false)
                .putFloat(KEY_LG_BLUR, migrated.blurRadius)
                .putFloat(KEY_LG_VIBRANCY, migrated.vibrancy)
                .putFloat(KEY_LG_FROST_GRAIN, migrated.frostGrain)
                .putFloat(KEY_LG_LENS_REFRACTION, migrated.refraction)
                .putFloat(KEY_LG_BEVEL_THICKNESS, migrated.bevelThickness)
                .putFloat(KEY_LG_DISPERSION, migrated.chromaticDispersion)
                .putFloat(KEY_LG_TRANSLUCENCY, migrated.translucency)
                .putFloat(KEY_LG_SPECULAR, migrated.specularGlow)
                .putFloat(KEY_LG_REFRACTION, migrated.refractionTint)
                .putFloat(KEY_LG_BORDER, migrated.borderIntensity)
                .putBoolean(KEY_LG_SHIMMER, false)
                .putBoolean(KEY_LG_HIGH_CONTRAST, migrated.highContrastText)
                .apply()
            migrated
        } else {
            LiquidGlassConfig(
                enabled = prefs.getBoolean(KEY_LG_ENABLED, defaultGlass.enabled),
                blurRadius = prefs.getFloat(KEY_LG_BLUR, defaultGlass.blurRadius),
                vibrancy = prefs.getFloat(KEY_LG_VIBRANCY, defaultGlass.vibrancy),
                frostGrain = prefs.getFloat(KEY_LG_FROST_GRAIN, defaultGlass.frostGrain),
                refraction = prefs.getFloat(KEY_LG_LENS_REFRACTION, defaultGlass.refraction),
                bevelThickness = prefs.getFloat(KEY_LG_BEVEL_THICKNESS, defaultGlass.bevelThickness),
                chromaticDispersion = prefs.getFloat(KEY_LG_DISPERSION, defaultGlass.chromaticDispersion),
                translucency = prefs.getFloat(KEY_LG_TRANSLUCENCY, defaultGlass.translucency),
                specularGlow = prefs.getFloat(KEY_LG_SPECULAR, defaultGlass.specularGlow),
                refractionTint = prefs.getFloat(KEY_LG_REFRACTION, defaultGlass.refractionTint),
                borderIntensity = prefs.getFloat(KEY_LG_BORDER, defaultGlass.borderIntensity),
                dynamicShimmer = prefs.getBoolean(KEY_LG_SHIMMER, defaultGlass.dynamicShimmer),
                highContrastText = prefs.getBoolean(KEY_LG_HIGH_CONTRAST, defaultGlass.highContrastText)
            ).sanitized()
        }
        return AppThemeConfig(
            mode = mode,
            preset = preset,
            customAccentArgb = customAccent,
            liquidGlass = liquidGlass
        )
    }

    fun save(config: AppThemeConfig) {
        val lg = config.liquidGlass.sanitized()
        prefs.edit().apply {
            putString(KEY_MODE, config.mode.name)
            putString(KEY_PRESET, config.preset.id)
            if (config.customAccentArgb != null) {
                putLong(KEY_ACCENT_ARGB, config.customAccentArgb)
            } else {
                remove(KEY_ACCENT_ARGB)
            }
            putBoolean(KEY_LG_ENABLED, lg.enabled)
            putFloat(KEY_LG_BLUR, lg.blurRadius)
            putFloat(KEY_LG_VIBRANCY, lg.vibrancy)
            putFloat(KEY_LG_FROST_GRAIN, lg.frostGrain)
            putFloat(KEY_LG_LENS_REFRACTION, lg.refraction)
            putFloat(KEY_LG_BEVEL_THICKNESS, lg.bevelThickness)
            putFloat(KEY_LG_DISPERSION, lg.chromaticDispersion)
            putFloat(KEY_LG_TRANSLUCENCY, lg.translucency)
            putFloat(KEY_LG_SPECULAR, lg.specularGlow)
            putFloat(KEY_LG_REFRACTION, lg.refractionTint)
            putFloat(KEY_LG_BORDER, lg.borderIntensity)
            putBoolean(KEY_LG_SHIMMER, lg.dynamicShimmer)
            putBoolean(KEY_LG_HIGH_CONTRAST, lg.highContrastText)
            putBoolean(KEY_LG_PERF_MIGRATED_V2, true)
            putBoolean(KEY_LG_MEETUBE_MIGRATED_V3, true)
            putBoolean(KEY_LG_PERF_OFF_V4, true)
            apply()
        }
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_PRESET = "theme_preset"
        private const val KEY_ACCENT_ARGB = "theme_custom_accent_argb"
        private const val KEY_LG_ENABLED = "lg_enabled"
        private const val KEY_LG_BLUR = "lg_blur_radius"
        private const val KEY_LG_VIBRANCY = "lg_vibrancy"
        private const val KEY_LG_FROST_GRAIN = "lg_frost_grain"
        private const val KEY_LG_LENS_REFRACTION = "lg_lens_refraction"
        private const val KEY_LG_BEVEL_THICKNESS = "lg_bevel_thickness"
        private const val KEY_LG_DISPERSION = "lg_chromatic_dispersion"
        private const val KEY_LG_TRANSLUCENCY = "lg_translucency"
        private const val KEY_LG_SPECULAR = "lg_specular"
        private const val KEY_LG_REFRACTION = "lg_refraction"
        private const val KEY_LG_BORDER = "lg_border"
        private const val KEY_LG_SHIMMER = "lg_shimmer"
        private const val KEY_LG_HIGH_CONTRAST = "lg_high_contrast"
        private const val KEY_LG_PERF_MIGRATED_V2 = "lg_perf_migrated_v2"
        private const val KEY_LG_MEETUBE_MIGRATED_V3 = "lg_meetube_migrated_v3"
        private const val KEY_LG_PERF_OFF_V4 = "lg_perf_off_v4"
    }
}

data class AppThemeUiState(
    val config: AppThemeConfig = AppThemeConfig(),
    val showDialog: Boolean = false
)

class AppThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ThemePreferences(application.applicationContext)

    private val _uiState = MutableStateFlow(AppThemeUiState(config = prefs.load()))
    val uiState: StateFlow<AppThemeUiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        updateConfig { cfg ->
            val alignedPreset = when (mode) {
                AppThemeMode.DARK -> when {
                    cfg.preset.isDark -> cfg.preset
                    cfg.preset.isLiquidGlass -> AppThemePreset.LIQUID_GLASS_DARK
                    else -> AppThemePreset.MIDNIGHT_OBSERVATORY
                }
                AppThemeMode.LIGHT -> when {
                    !cfg.preset.isDark -> cfg.preset
                    cfg.preset.isLiquidGlass -> AppThemePreset.LIQUID_GLASS_LIGHT
                    else -> AppThemePreset.IVORY_PAPER
                }
                AppThemeMode.SYSTEM -> cfg.preset
            }
            cfg.copy(mode = mode, preset = alignedPreset)
        }
    }

    fun selectPreset(preset: AppThemePreset) {
        updateConfig { cfg ->
            cfg.copy(
                mode = if (preset.isDark) AppThemeMode.DARK else AppThemeMode.LIGHT,
                preset = preset,
                customAccentArgb = null,
                liquidGlass = cfg.liquidGlass.copy(enabled = preset.isLiquidGlass)
            )
        }
    }

    fun selectAccentColor(argb: Long?) {
        updateConfig { it.copy(customAccentArgb = argb) }
    }

    fun toggleLiquidGlass(enabled: Boolean) {
        updateConfig { cfg ->
            cfg.copy(liquidGlass = cfg.liquidGlass.copy(enabled = enabled))
        }
    }

    fun applyLiquidGlassStyle(style: LiquidGlassPresetStyle) {
        updateConfig { cfg ->
            cfg.copy(liquidGlass = style.config)
        }
    }

    fun updateLiquidGlass(
        blurRadius: Float? = null,
        vibrancy: Float? = null,
        frostGrain: Float? = null,
        refraction: Float? = null,
        bevelThickness: Float? = null,
        chromaticDispersion: Float? = null,
        translucency: Float? = null,
        specularGlow: Float? = null,
        refractionTint: Float? = null,
        borderIntensity: Float? = null,
        dynamicShimmer: Boolean? = null,
        highContrastText: Boolean? = null
    ) {
        updateConfig { cfg ->
            val lg = cfg.liquidGlass
            cfg.copy(
                liquidGlass = lg.copy(
                    enabled = true,
                    blurRadius = blurRadius ?: lg.blurRadius,
                    vibrancy = vibrancy ?: lg.vibrancy,
                    frostGrain = frostGrain ?: lg.frostGrain,
                    refraction = refraction ?: lg.refraction,
                    bevelThickness = bevelThickness ?: lg.bevelThickness,
                    chromaticDispersion = chromaticDispersion ?: lg.chromaticDispersion,
                    translucency = translucency ?: lg.translucency,
                    specularGlow = specularGlow ?: lg.specularGlow,
                    refractionTint = refractionTint ?: lg.refractionTint,
                    borderIntensity = borderIntensity ?: lg.borderIntensity,
                    dynamicShimmer = dynamicShimmer ?: lg.dynamicShimmer,
                    highContrastText = highContrastText ?: lg.highContrastText
                ).sanitized()
            )
        }
    }

    fun resetToDefault() {
        updateConfig { AppThemeConfig() }
    }

    private fun updateConfig(transform: (AppThemeConfig) -> AppThemeConfig) {
        _uiState.update { current ->
            val next = transform(current.config)
            prefs.save(next)
            current.copy(config = next)
        }
    }
}
