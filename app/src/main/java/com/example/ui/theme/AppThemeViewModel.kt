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
        val preset = AppThemePreset.fromId(prefs.getString(KEY_PRESET, AppThemePreset.MIDNIGHT_OBSERVATORY.id))
        val hasCustomAccent = prefs.contains(KEY_ACCENT_ARGB)
        val customAccent = if (hasCustomAccent) prefs.getLong(KEY_ACCENT_ARGB, 0L) else null
        return AppThemeConfig(
            mode = mode,
            preset = preset,
            customAccentArgb = customAccent
        )
    }

    fun save(config: AppThemeConfig) {
        prefs.edit().apply {
            putString(KEY_MODE, config.mode.name)
            putString(KEY_PRESET, config.preset.id)
            if (config.customAccentArgb != null) {
                putLong(KEY_ACCENT_ARGB, config.customAccentArgb)
            } else {
                remove(KEY_ACCENT_ARGB)
            }
            apply()
        }
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_PRESET = "theme_preset"
        private const val KEY_ACCENT_ARGB = "theme_custom_accent_argb"
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

    fun openDialog() {
        _uiState.update { it.copy(showDialog = true) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        updateConfig { cfg ->
            val alignedPreset = when (mode) {
                AppThemeMode.DARK -> if (cfg.preset.isDark) cfg.preset else AppThemePreset.MIDNIGHT_OBSERVATORY
                AppThemeMode.LIGHT -> if (!cfg.preset.isDark) cfg.preset else AppThemePreset.IVORY_PAPER
                AppThemeMode.SYSTEM -> cfg.preset
            }
            cfg.copy(mode = mode, preset = alignedPreset)
        }
    }

    fun selectPreset(preset: AppThemePreset) {
        updateConfig {
            AppThemeConfig(
                mode = if (preset.isDark) AppThemeMode.DARK else AppThemeMode.LIGHT,
                preset = preset,
                customAccentArgb = null
            )
        }
    }

    fun selectAccentColor(argb: Long?) {
        updateConfig { it.copy(customAccentArgb = argb) }
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
