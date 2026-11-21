package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

enum class AppThemeMode(val label: String) {
    DARK("Tối"),
    LIGHT("Sáng"),
    SYSTEM("Theo máy")
}

enum class AppThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val isDark: Boolean,
    val isLiquidGlass: Boolean,
    val previewBg: Color,
    val previewSurface: Color,
    val previewAccent: Color
) {
    LIQUID_GLASS_DARK(
        id = "liquid_glass_dark",
        title = "Liquid Glass Tối",
        subtitle = "Kính lỏng trong suốt phản quang",
        isDark = true,
        isLiquidGlass = true,
        previewBg = Color(0xFF061024),
        previewSurface = Color(0xFF1A355E),
        previewAccent = Color(0xFF38BDF8)
    ),
    LIQUID_GLASS_LIGHT(
        id = "liquid_glass_light",
        title = "Liquid Glass Sáng",
        subtitle = "Pha lê băng trong trẻo hiện đại",
        isDark = false,
        isLiquidGlass = true,
        previewBg = Color(0xFFDCEBFF),
        previewSurface = Color(0xFFFFFFFF),
        previewAccent = Color(0xFF0EA5E9)
    ),
    MIDNIGHT_OBSERVATORY(
        id = "midnight_observatory",
        title = "Thiên Văn Tối",
        subtitle = "Xanh đêm dịu mắt • Mặc định",
        isDark = true,
        isLiquidGlass = false,
        previewBg = Color(0xFF090D16),
        previewSurface = Color(0xFF1E293B),
        previewAccent = Color(0xFF4F46E5)
    ),
    IVORY_PAPER(
        id = "ivory_paper",
        title = "Giấy Trắng Học Tập",
        subtitle = "Sáng rõ như vở toán in",
        isDark = false,
        isLiquidGlass = false,
        previewBg = Color(0xFFF4F7FB),
        previewSurface = Color(0xFFFFFFFF),
        previewAccent = Color(0xFF2563EB)
    ),
    CHALKBOARD_FOREST(
        id = "chalkboard_forest",
        title = "Bảng Xanh Lớp Học",
        subtitle = "Xanh bảng trầm chống mỏi mắt",
        isDark = true,
        isLiquidGlass = false,
        previewBg = Color(0xFF071712),
        previewSurface = Color(0xFF122C23),
        previewAccent = Color(0xFF10B981)
    ),
    WARM_SAND_LIGHT(
        id = "warm_sand_light",
        title = "Giấy Kem Nắng Ấm",
        subtitle = "Tông ấm đọc công thức êm mắt",
        isDark = false,
        isLiquidGlass = false,
        previewBg = Color(0xFFF8F4EC),
        previewSurface = Color(0xFFFFFDF8),
        previewAccent = Color(0xFFD97706)
    ),
    VIOLET_NEBULA(
        id = "violet_nebula",
        title = "Ngân Hà Tím",
        subtitle = "Tím sâu hiện đại & sắc nét",
        isDark = true,
        isLiquidGlass = false,
        previewBg = Color(0xFF0D091A),
        previewSurface = Color(0xFF1F1638),
        previewAccent = Color(0xFF8B5CF6)
    ),
    SKY_MINIMAL_LIGHT(
        id = "sky_minimal_light",
        title = "Bầu Trời Sáng",
        subtitle = "Xanh băng tươi sáng hiện đại",
        isDark = false,
        isLiquidGlass = false,
        previewBg = Color(0xFFEDF5FF),
        previewSurface = Color(0xFFFFFFFF),
        previewAccent = Color(0xFF0284C7)
    );

    companion object {
        fun fromId(id: String?): AppThemePreset =
            entries.firstOrNull { it.id == id } ?: MIDNIGHT_OBSERVATORY
    }
}

/**
 * Fine-grained optical controls for the Liquid Glass material renderer.
 */
data class LiquidGlassConfig(
    val enabled: Boolean = false,
    /** 0.25f..0.90f — higher means clearer, more transparent crystal glass */
    val translucency: Float = 0.62f,
    /** 0.0f..1.0f — specular top-bevel highlight & glossy sheen intensity */
    val specularGlow: Float = 0.72f,
    /** 0.0f..1.0f — chromatic tint & aurora refraction intensity */
    val refractionTint: Float = 0.58f,
    /** 0.2f..1.0f — prismatic rim border brightness & stroke thickness */
    val borderIntensity: Float = 0.78f,
    /** Smooth animated specular shimmer sweep across glass panels */
    val dynamicShimmer: Boolean = true,
    /** Boosts frosted glass opacity slightly for extra legibility */
    val highContrastText: Boolean = false
) {
    fun sanitized(): LiquidGlassConfig = copy(
        translucency = translucency.coerceIn(0.25f, 0.90f),
        specularGlow = specularGlow.coerceIn(0f, 1f),
        refractionTint = refractionTint.coerceIn(0f, 1f),
        borderIntensity = borderIntensity.coerceIn(0.2f, 1f)
    )
}

enum class LiquidGlassPresetStyle(
    val id: String,
    val label: String,
    val config: LiquidGlassConfig
) {
    CRYSTAL(
        id = "crystal",
        label = "Pha lê trong",
        config = LiquidGlassConfig(
            enabled = true,
            translucency = 0.80f,
            specularGlow = 0.92f,
            refractionTint = 0.70f,
            borderIntensity = 0.92f,
            dynamicShimmer = true,
            highContrastText = false
        )
    ),
    FROSTED(
        id = "frosted",
        label = "Kính mờ chuẩn",
        config = LiquidGlassConfig(
            enabled = true,
            translucency = 0.62f,
            specularGlow = 0.72f,
            refractionTint = 0.58f,
            borderIntensity = 0.78f,
            dynamicShimmer = true,
            highContrastText = false
        )
    ),
    SOFT(
        id = "soft",
        label = "Kính dịu mắt",
        config = LiquidGlassConfig(
            enabled = true,
            translucency = 0.38f,
            specularGlow = 0.45f,
            refractionTint = 0.35f,
            borderIntensity = 0.55f,
            dynamicShimmer = false,
            highContrastText = true
        )
    )
}

data class AppAccentSwatch(
    val id: String,
    val label: String,
    val color: Color
)

object AppAccentSwatches {
    val all: List<AppAccentSwatch> = listOf(
        AppAccentSwatch("indigo", "Chàm", Color(0xFF4F46E5)),
        AppAccentSwatch("blue", "Xanh dương", Color(0xFF2563EB)),
        AppAccentSwatch("sky", "Xanh trời", Color(0xFF0EA5E9)),
        AppAccentSwatch("teal", "Xanh ngọc", Color(0xFF0D9488)),
        AppAccentSwatch("emerald", "Lục bảo", Color(0xFF059669)),
        AppAccentSwatch("violet", "Tím", Color(0xFF7C3AED)),
        AppAccentSwatch("rose", "Hồng đỏ", Color(0xFFE11D48)),
        AppAccentSwatch("amber", "Hổ phách", Color(0xFFD97706))
    )
}

data class AppThemeConfig(
    val mode: AppThemeMode = AppThemeMode.DARK,
    val preset: AppThemePreset = AppThemePreset.MIDNIGHT_OBSERVATORY,
    val customAccentArgb: Long? = null,
    val liquidGlass: LiquidGlassConfig = LiquidGlassConfig()
)

data class CanvasThemeColors(
    val isLight: Boolean,
    val bgInnerArgb: Int,
    val bgOuterArgb: Int,
    val gridMinorArgb: Int,
    val gridMajorArgb: Int,
    val axisArgb: Int,
    val axisArrowArgb: Int,
    val edgeSolidArgb: Int,
    val edgeDashedArgb: Int,
    val faceTintArgb: Int,
    val labelPillBgArgb: Int,
    val labelTextArgb: Int
)

data class AppThemePalette(
    val isDark: Boolean,
    val liquidGlass: LiquidGlassConfig,
    val bgDeepest: Color,
    val bgDark: Color,
    val surface: Color,
    val border: Color,
    val muted: Color,
    val textSecondary: Color,
    val textLight: Color,
    val textPrimary: Color,
    val primary: Color,
    val primaryBright: Color,
    val primaryLight: Color,
    val primarySoft: Color,
    val cyanAccent: Color,
    val skyBlue: Color,
    val emeraldAccent: Color,
    val emeraldLight: Color,
    val emeraldDark: Color,
    val amberAccent: Color,
    val amberDeep: Color,
    val roseAccent: Color,
    val roseLight: Color,
    val pinkAccent: Color,
    val pinkLight: Color,
    val purpleAccent: Color,
    val canvas: CanvasThemeColors
)

object ThemePaletteResolver {

    fun hueToAccentColor(hueDegrees: Float, isDark: Boolean): Color {
        val h = ((hueDegrees % 360f) + 360f) % 360f
        val s = if (isDark) 0.72f else 0.78f
        val v = if (isDark) 0.88f else 0.74f
        return Color.hsv(h, s, v)
    }

    fun resolveIsDark(config: AppThemeConfig, systemDark: Boolean): Boolean = when (config.mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    /**
     * Resolves a full, high-contrast [AppThemePalette] for Compose UI, Liquid Glass chrome, and 2D/3D Canvases.
     */
    fun resolve(config: AppThemeConfig, systemDark: Boolean): AppThemePalette {
        val useDark = resolveIsDark(config, systemDark)
        val safeGlass = config.liquidGlass.sanitized()

        // Pick base surfaces matching the chosen preset when brightness aligns, or a clean counterpart when toggled
        val basePreset = if (config.preset.isDark == useDark) {
            config.preset
        } else {
            when (config.preset) {
                AppThemePreset.LIQUID_GLASS_DARK -> AppThemePreset.LIQUID_GLASS_LIGHT
                AppThemePreset.LIQUID_GLASS_LIGHT -> AppThemePreset.LIQUID_GLASS_DARK
                AppThemePreset.MIDNIGHT_OBSERVATORY -> AppThemePreset.IVORY_PAPER
                AppThemePreset.IVORY_PAPER -> AppThemePreset.MIDNIGHT_OBSERVATORY
                AppThemePreset.CHALKBOARD_FOREST -> AppThemePreset.IVORY_PAPER
                AppThemePreset.WARM_SAND_LIGHT -> AppThemePreset.MIDNIGHT_OBSERVATORY
                AppThemePreset.VIOLET_NEBULA -> AppThemePreset.SKY_MINIMAL_LIGHT
                AppThemePreset.SKY_MINIMAL_LIGHT -> AppThemePreset.MIDNIGHT_OBSERVATORY
            }
        }

        val customAccent = config.customAccentArgb?.let { Color(it.toInt()) }
        val baseAccent = customAccent ?: config.preset.previewAccent

        val primary = if (useDark) {
            baseAccent
        } else {
            // Keep primary slightly deeper on light surfaces for strong button contrast
            lerp(baseAccent, Color(0xFF0F172A), 0.14f)
        }
        val primaryBright = if (useDark) lerp(primary, Color.White, 0.18f) else primary
        val primaryLight = if (useDark) lerp(primary, Color.White, 0.36f) else lerp(primary, Color(0xFF0F172A), 0.12f)
        val primarySoft = if (useDark) lerp(primary, Color.White, 0.56f) else lerp(primary, Color(0xFF1E293B), 0.25f)

        return if (useDark) {
            val (bgDeepest, bgDark, surface, border, muted, bgInner, bgOuter, gMinor, gMajor) = when (basePreset) {
                AppThemePreset.LIQUID_GLASS_DARK -> SurfaceTuple(
                    bgDeepest = Color(0xFF050B18),
                    bgDark = Color(0xFF0D1B34),
                    surface = Color(0xFF172C52),
                    border = Color(0xFF3A5F94),
                    muted = Color(0xFF567EB5),
                    bgInner = 0xFF142A4F.toInt(),
                    bgOuter = 0xFF040914.toInt(),
                    gMinor = 0xFF122342.toInt(),
                    gMajor = 0xFF213C6B.toInt()
                )
                AppThemePreset.CHALKBOARD_FOREST -> SurfaceTuple(
                    bgDeepest = Color(0xFF06140F),
                    bgDark = Color(0xFF0C221A),
                    surface = Color(0xFF153429),
                    border = Color(0xFF285545),
                    muted = Color(0xFF3E705D),
                    bgInner = 0xFF143528.toInt(),
                    bgOuter = 0xFF06140F.toInt(),
                    gMinor = 0xFF122C22.toInt(),
                    gMajor = 0xFF1F4738.toInt()
                )
                AppThemePreset.VIOLET_NEBULA -> SurfaceTuple(
                    bgDeepest = Color(0xFF0C0818),
                    bgDark = Color(0xFF150F29),
                    surface = Color(0xFF231A40),
                    border = Color(0xFF3C2F66),
                    muted = Color(0xFF564785),
                    bgInner = 0xFF221840.toInt(),
                    bgOuter = 0xFF0C0818.toInt(),
                    gMinor = 0xFF1A1333.toInt(),
                    gMajor = 0xFF2D2254.toInt()
                )
                else -> SurfaceTuple(
                    bgDeepest = Color(0xFF090D16),
                    bgDark = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    border = Color(0xFF334155),
                    muted = Color(0xFF475569),
                    bgInner = 0xFF1B273C.toInt(),
                    bgOuter = 0xFF090D16.toInt(),
                    gMinor = 0xFF152034.toInt(),
                    gMajor = 0xFF23324C.toInt()
                )
            }

            AppThemePalette(
                isDark = true,
                liquidGlass = safeGlass,
                bgDeepest = bgDeepest,
                bgDark = bgDark,
                surface = surface,
                border = border,
                muted = muted,
                textSecondary = if (safeGlass.enabled && safeGlass.highContrastText) Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                textLight = if (safeGlass.enabled && safeGlass.highContrastText) Color(0xFFE2E8F0) else Color(0xFFCBD5E1),
                textPrimary = Color(0xFFF8FAFC),
                primary = primary,
                primaryBright = primaryBright,
                primaryLight = primaryLight,
                primarySoft = primarySoft,
                cyanAccent = if (customAccent != null) lerp(primaryBright, Color(0xFF38BDF8), 0.35f) else Color(0xFF38BDF8),
                skyBlue = Color(0xFF0284C7),
                emeraldAccent = Color(0xFF10B981),
                emeraldLight = Color(0xFF34D399),
                emeraldDark = Color(0xFF059669),
                amberAccent = Color(0xFFFBBF24),
                amberDeep = Color(0xFFB45309),
                roseAccent = Color(0xFFF43F5E),
                roseLight = Color(0xFFFDA4AF),
                pinkAccent = Color(0xFFDB2777),
                pinkLight = Color(0xFFF472B6),
                purpleAccent = Color(0xFFC084FC),
                canvas = CanvasThemeColors(
                    isLight = false,
                    bgInnerArgb = bgInner,
                    bgOuterArgb = bgOuter,
                    gridMinorArgb = gMinor,
                    gridMajorArgb = gMajor,
                    axisArgb = 0xFF94A3B8.toInt(),
                    axisArrowArgb = 0xFFCBD5E1.toInt(),
                    edgeSolidArgb = 0xFFF8FAFC.toInt(),
                    edgeDashedArgb = 0xFF94A3B8.toInt(),
                    faceTintArgb = primaryBright.toArgb(),
                    labelPillBgArgb = bgDark.copy(alpha = if (safeGlass.enabled) 0.74f else 0.86f).toArgb(),
                    labelTextArgb = 0xFFF8FAFC.toInt()
                )
            )
        } else {
            val (bgDeepest, bgDark, surface, border, muted, bgInner, bgOuter, gMinor, gMajor) = when (basePreset) {
                AppThemePreset.LIQUID_GLASS_LIGHT -> SurfaceTuple(
                    bgDeepest = Color(0xFFDFECFF),
                    bgDark = Color(0xFFF4F9FF),
                    surface = Color(0xFFD6E8FF),
                    border = Color(0xFF9EC5F5),
                    muted = Color(0xFF7CAEEB),
                    bgInner = 0xFFF7FBFF.toInt(),
                    bgOuter = 0xFFD8E9FF.toInt(),
                    gMinor = 0xFFCDE1FA.toInt(),
                    gMajor = 0xFFA5C7F2.toInt()
                )
                AppThemePreset.WARM_SAND_LIGHT -> SurfaceTuple(
                    bgDeepest = Color(0xFFF5EFE4),
                    bgDark = Color(0xFFFFFCF7),
                    surface = Color(0xFFEDE4D3),
                    border = Color(0xFFD6C8B0),
                    muted = Color(0xFFA8987E),
                    bgInner = 0xFFFFFDF8.toInt(),
                    bgOuter = 0xFFF3EBDC.toInt(),
                    gMinor = 0xFFEAE0CE.toInt(),
                    gMajor = 0xFFD9C9B0.toInt()
                )
                AppThemePreset.SKY_MINIMAL_LIGHT -> SurfaceTuple(
                    bgDeepest = Color(0xFFEAF3FF),
                    bgDark = Color(0xFFFFFFFF),
                    surface = Color(0xFFDBEAFE),
                    border = Color(0xFFBFDBFE),
                    muted = Color(0xFF93C5FD),
                    bgInner = 0xFFF8FBFF.toInt(),
                    bgOuter = 0xFFE6F0FF.toInt(),
                    gMinor = 0xFFDCE9FC.toInt(),
                    gMajor = 0xFFBCD4F6.toInt()
                )
                else -> SurfaceTuple(
                    bgDeepest = Color(0xFFF1F5F9),
                    bgDark = Color(0xFFFFFFFF),
                    surface = Color(0xFFE2E8F0),
                    border = Color(0xFFCBD5E1),
                    muted = Color(0xFF94A3B8),
                    bgInner = 0xFFFFFFFF.toInt(),
                    bgOuter = 0xFFF1F5F9.toInt(),
                    gMinor = 0xFFE8EEF5.toInt(),
                    gMajor = 0xFFCBD5E1.toInt()
                )
            }

            AppThemePalette(
                isDark = false,
                liquidGlass = safeGlass,
                bgDeepest = bgDeepest,
                bgDark = bgDark,
                surface = surface,
                border = border,
                muted = muted,
                textSecondary = if (safeGlass.enabled && safeGlass.highContrastText) Color(0xFF1E293B) else Color(0xFF475569),
                textLight = if (safeGlass.enabled && safeGlass.highContrastText) Color(0xFF0F172A) else Color(0xFF334155),
                textPrimary = Color(0xFF0F172A),
                primary = primary,
                primaryBright = primary,
                primaryLight = primaryLight,
                primarySoft = primarySoft,
                cyanAccent = if (customAccent != null) primary else Color(0xFF0284C7),
                skyBlue = Color(0xFF0369A1),
                emeraldAccent = Color(0xFF059669),
                emeraldLight = Color(0xFF047857),
                emeraldDark = Color(0xFF047857),
                amberAccent = Color(0xFFD97706),
                amberDeep = Color(0xFFB45309),
                roseAccent = Color(0xFFE11D48),
                roseLight = Color(0xFFBE123C),
                pinkAccent = Color(0xFFBE185D),
                pinkLight = Color(0xFFDB2777),
                purpleAccent = Color(0xFF7C3AED),
                canvas = CanvasThemeColors(
                    isLight = true,
                    bgInnerArgb = bgInner,
                    bgOuterArgb = bgOuter,
                    gridMinorArgb = gMinor,
                    gridMajorArgb = gMajor,
                    axisArgb = 0xFF334155.toInt(),
                    axisArrowArgb = 0xFF0F172A.toInt(),
                    edgeSolidArgb = 0xFF0F172A.toInt(),
                    edgeDashedArgb = 0xFF64748B.toInt(),
                    faceTintArgb = primary.toArgb(),
                    labelPillBgArgb = bgDark.copy(alpha = if (safeGlass.enabled) 0.78f else 0.92f).toArgb(),
                    labelTextArgb = 0xFF0F172A.toInt()
                )
            )
        }
    }

    private data class SurfaceTuple(
        val bgDeepest: Color,
        val bgDark: Color,
        val surface: Color,
        val border: Color,
        val muted: Color,
        val bgInner: Int,
        val bgOuter: Int,
        val gMinor: Int,
        val gMajor: Int
    )
}

/**
 * Reactive Compose snapshot holder for the active theme palette so every composable
 * and 2D/3D canvas updates seamlessly when the user switches Dark/Light, presets, accent colors,
 * or Liquid Glass optical parameters.
 */
object ActiveThemeState {
    var current by mutableStateOf(
        ThemePaletteResolver.resolve(AppThemeConfig(), systemDark = true)
    )
}
