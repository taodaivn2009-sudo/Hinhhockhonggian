package com.example.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reusable inline Theme & Color Studio section used inside the App Settings tab.
 */
@Composable
fun ThemeStudioSection(
    config: AppThemeConfig,
    onSelectMode: (AppThemeMode) -> Unit,
    onSelectPreset: (AppThemePreset) -> Unit,
    onSelectAccentColor: (Long?) -> Unit,
    onResetDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePalette = ActiveThemeState.current

    val initialHue = remember(config.customAccentArgb, config.preset) {
        val c = config.customAccentArgb?.let { Color(it.toInt()) } ?: config.preset.previewAccent
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[0]
    }
    var sliderHue by remember(initialHue) { mutableFloatStateOf(initialHue) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("theme_studio_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Interactive Mini Preview Card (shows Liquid Glass lens in real time)
        ThemeLivePreviewCard(palette = activePalette)

        // 2. Brightness Mode Selector (Tối / Sáng / Theo máy)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Chế độ hiển thị",
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateDeepest.copy(alpha = if (activePalette.liquidGlass.enabled) 0.55f else 1f))
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val selected = config.mode == mode
                    val icon = when (mode) {
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) IndigoPrimary else Color.Transparent)
                            .clickable { onSelectMode(mode) }
                            .testTag("theme_mode_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else SlateTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else SlateTextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Curated Presets Grid (8 Light, Dark & Liquid Glass Suggestions)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Gợi ý chủ đề nổi bật",
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
            AppThemePreset.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { preset ->
                        ThemePresetCard(
                            preset = preset,
                            selected = config.preset == preset,
                            onClick = { onSelectPreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 4. Custom Accent Color Picker (Swatches + Free Hue Slider)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SlateDeepest.copy(alpha = if (activePalette.liquidGlass.enabled) 0.45f else 1f))
                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Màu nhấn chủ đạo",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (config.customAccentArgb != null) {
                    Text(
                        text = "Dùng màu mẫu",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onSelectAccentColor(null) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppAccentSwatches.all.forEach { swatch ->
                    val swatchArgb = swatch.color.toArgb().toLong()
                    val selected = config.customAccentArgb == swatchArgb
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatch.color)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) SlateTextPrimary else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .clickable { onSelectAccentColor(swatchArgb) }
                            .testTag("accent_swatch_${swatch.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = swatch.label,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tự pha sắc màu tùy ý",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
                    )
                    val previewHueColor = ThemePaletteResolver.hueToAccentColor(sliderHue, activePalette.isDark)
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(previewHueColor)
                            .border(1.dp, SlateBorder, CircleShape)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.hsv(0f, 0.8f, 0.9f),
                                    Color.hsv(60f, 0.8f, 0.9f),
                                    Color.hsv(120f, 0.8f, 0.85f),
                                    Color.hsv(180f, 0.8f, 0.85f),
                                    Color.hsv(240f, 0.8f, 0.9f),
                                    Color.hsv(300f, 0.8f, 0.9f),
                                    Color.hsv(360f, 0.8f, 0.9f)
                                )
                            )
                        )
                )

                Slider(
                    value = sliderHue,
                    onValueChange = { hue ->
                        sliderHue = hue
                        val custom = ThemePaletteResolver.hueToAccentColor(hue, activePalette.isDark)
                        onSelectAccentColor(custom.toArgb().toLong())
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = ThemePaletteResolver.hueToAccentColor(sliderHue, activePalette.isDark),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .testTag("custom_hue_slider")
                )
            }
        }

        OutlinedButton(
            onClick = onResetDefault,
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .testTag("reset_theme_default_btn")
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Khôi phục chủ đề mặc định", fontSize = 13.sp)
        }
    }
}

/**
 * Dedicated Liquid Glass optical calibration section inside the App Settings tab.
 */
@Composable
fun LiquidGlassSettingsSection(
    config: LiquidGlassConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onApplyPresetStyle: (LiquidGlassPresetStyle) -> Unit,
    onUpdateTranslucency: (Float) -> Unit,
    onUpdateSpecularGlow: (Float) -> Unit,
    onUpdateRefractionTint: (Float) -> Unit,
    onUpdateBorderIntensity: (Float) -> Unit,
    onToggleDynamicShimmer: (Boolean) -> Unit,
    onToggleHighContrastText: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("liquid_glass_settings_section"),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // Master Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (config.enabled) CyanAccent.copy(alpha = 0.14f)
                    else SlateDeepest.copy(alpha = 0.65f)
                )
                .border(
                    width = 1.dp,
                    color = if (config.enabled) CyanAccent.copy(alpha = 0.65f) else SlateBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onToggleEnabled(!config.enabled) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("liquid_glass_master_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Bật chất liệu Liquid Glass",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (config.enabled) {
                        "Đang bật kính lỏng trong suốt cho thanh nổi, bảng & thẻ"
                    } else {
                        "Có thể bật kết hợp cùng bất kỳ chủ đề sáng/tối nào"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyanAccent
                )
            )
        }

        AnimatedVisibility(visible = config.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick Liquid Glass Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Kiểu chất liệu kính nhanh",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LiquidGlassPresetStyle.entries.forEach { style ->
                            val isActive = abs(config.translucency - style.config.translucency) < 0.04f &&
                                abs(config.specularGlow - style.config.specularGlow) < 0.05f &&
                                config.highContrastText == style.config.highContrastText
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) CyanAccent.copy(alpha = 0.24f) else SlateDeepest.copy(alpha = 0.5f))
                                    .border(
                                        width = if (isActive) 1.4.dp else 1.dp,
                                        color = if (isActive) CyanAccent else SlateBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onApplyPresetStyle(style) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp)
                                    .testTag("lg_style_${style.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) CyanAccent else SlateTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 4 Real-Time Optical Sliders
                LiquidGlassSliderRow(
                    label = "Độ trong suốt kính",
                    value = config.translucency,
                    range = 0.25f..0.90f,
                    accent = CyanAccent,
                    testTag = "lg_slider_translucency",
                    onValueChange = onUpdateTranslucency
                )
                LiquidGlassSliderRow(
                    label = "Ánh phản quang bề mặt",
                    value = config.specularGlow,
                    range = 0f..1f,
                    accent = IndigoBright,
                    testTag = "lg_slider_specular",
                    onValueChange = onUpdateSpecularGlow
                )
                LiquidGlassSliderRow(
                    label = "Tán sắc màu & nền cực quang",
                    value = config.refractionTint,
                    range = 0f..1f,
                    accent = PurpleAccent,
                    testTag = "lg_slider_refraction",
                    onValueChange = onUpdateRefractionTint
                )
                LiquidGlassSliderRow(
                    label = "Độ sáng viền pha lê",
                    value = config.borderIntensity,
                    range = 0.2f..1f,
                    accent = EmeraldAccent,
                    testTag = "lg_slider_border",
                    onValueChange = onUpdateBorderIntensity
                )

                HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                // 2 Toggle Switches
                LiquidGlassToggleRow(
                    title = "Ánh sáng lướt động (Liquid Shimmer)",
                    subtitle = "Hiệu ứng vệt sáng chuyển động nhẹ qua bề mặt kính",
                    checked = config.dynamicShimmer,
                    testTag = "lg_toggle_shimmer",
                    onCheckedChange = onToggleDynamicShimmer
                )
                LiquidGlassToggleRow(
                    title = "Tăng tương phản chữ trên kính",
                    subtitle = "Làm đục nhẹ lớp kính nền để đọc công thức rõ hơn",
                    checked = config.highContrastText,
                    testTag = "lg_toggle_high_contrast",
                    onCheckedChange = onToggleHighContrastText
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextPrimary
            )
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = SlateBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun LiquidGlassToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                fontSize = 10.5.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IndigoBright
            )
        )
    }
}

@Composable
fun ThemeLivePreviewCard(palette: AppThemePalette) {
    val gridColor = Color(palette.canvas.gridMajorArgb)
    val axisColor = Color(palette.canvas.axisArgb)
    val curveColor = palette.primaryBright
    val bgTop = Color(palette.canvas.bgInnerArgb)
    val bgBottom = Color(palette.canvas.bgOuterArgb)
    val lg = palette.liquidGlass

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .border(1.dp, palette.border, RoundedCornerShape(15.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Luminous aurora spheres behind preview when Liquid Glass is active
            if (lg.enabled) {
                val glow = 0.32f + lg.refractionTint * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.primaryBright.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.25f),
                        radius = size.width * 0.42f
                    ),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.22f, size.height * 0.25f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.cyanAccent.copy(alpha = glow * 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.72f),
                        radius = size.width * 0.40f
                    ),
                    radius = size.width * 0.40f,
                    center = Offset(size.width * 0.78f, size.height * 0.72f)
                )
            }

            val cols = 8
            val rows = 4
            for (c in 1 until cols) {
                val x = size.width * c / cols
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.2f)
            }
            for (r in 1 until rows) {
                val y = size.height * r / rows
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
            }
            drawLine(axisColor, Offset(0f, size.height * 0.56f), Offset(size.width, size.height * 0.56f), strokeWidth = 2f)
            drawLine(axisColor, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.35f, size.height), strokeWidth = 2f)

            val path = Path()
            val steps = 70
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = t * size.width
                val y = size.height * 0.56f - sin(t * 3.2f * Math.PI.toFloat()) * size.height * 0.30f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = curveColor, style = Stroke(width = 4.2f, cap = StrokeCap.Round))
        }

        // Floating HUD preview pill rendered with active Liquid Glass surface!
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(9.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(10.dp),
                    elevated = false,
                    fallbackBg = palette.bgDark.copy(alpha = 0.92f),
                    fallbackBorder = palette.border
                )
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("3D", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("y = sin(x)", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (lg.enabled) {
                Text(
                    text = "Glass",
                    color = palette.cyanAccent,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    preset: AppThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGlass = ActiveThemeState.current.liquidGlass.enabled
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                when {
                    selected -> IndigoPrimary.copy(alpha = 0.18f)
                    activeGlass -> SlateDeepest.copy(alpha = 0.48f)
                    else -> SlateDeepest
                }
            )
            .border(
                width = if (selected) 1.6.dp else 1.dp,
                color = if (selected) IndigoPrimary else SlateBorder,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("theme_preset_${preset.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(preset.previewBg)
                        .border(1.dp, SlateBorder, CircleShape)
                )
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(preset.previewSurface)
                        .border(1.dp, SlateBorder, CircleShape)
                )
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(preset.previewAccent)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (preset.isLiquidGlass) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanAccent.copy(alpha = 0.22f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Glass",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (preset.isDark) Color(0xFF1E293B) else Color(0xFFFEF3C7)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (preset.isDark) "Tối" else "Sáng",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (preset.isDark) Color(0xFF93C5FD) else Color(0xFFB45309)
                    )
                }
            }
        }

        Text(
            text = preset.title,
            style = MaterialTheme.typography.labelMedium,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = preset.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
