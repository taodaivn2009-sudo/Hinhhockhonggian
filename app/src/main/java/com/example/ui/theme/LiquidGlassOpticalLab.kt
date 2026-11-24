package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class OpticalLabTab(val label: String, val icon: ImageVector) {
    PIN_PAD("PIN Pad 0–9", Icons.Default.Dialpad),
    FLOATING_NAV("Thanh nổi", Icons.Default.Home),
    CONTROLS("Công tắc", Icons.Default.ToggleOn)
}

/**
 * Interactive iOS 26 Liquid Glass Optical Lab & Demo Stage inside Settings -> Hiệu ứng Liquid Glass.
 * Showcases real-time AGSL optical refraction, bevel thickness, RGB chromatic dispersion,
 * dynamic scrolling backdrop sampling, elastic spring + radial bloom physics across:
 *   1. Liquid Glass PIN Pad (0–9 circular lenses)
 *   2. Floating Liquid Navigation Bar over colorful scrolling rows
 *   3. Liquid Glass Toggle Switch & Liquid Glass Slider
 * Paired with a live tuning strip for Refraction, Bevel Thickness, Dispersion, and Blur Radius.
 */
@Composable
fun LiquidGlassOpticalLabCard(
    config: LiquidGlassConfig,
    onUpdateRefraction: (Float) -> Unit,
    onUpdateBevelThickness: (Float) -> Unit,
    onUpdateChromaticDispersion: (Float) -> Unit,
    onUpdateBlurRadius: (Float) -> Unit,
    onUpdateVibrancy: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(OpticalLabTab.PIN_PAD) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("liquid_glass_optical_lab"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mode switcher for the 3 interactive optical demos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(SlateDark.copy(alpha = 0.85f))
                .border(1.dp, SlateBorder, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OpticalLabTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(CircleShape)
                        .background(if (selected) CyanAccent.copy(alpha = 0.22f) else Color.Transparent)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) CyanAccent.copy(alpha = 0.65f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { activeTab = tab }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("lg_lab_tab_${tab.name.lowercase()}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) CyanAccent else SlateTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) CyanAccent else SlateTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        // High-Contrast Interactive Optical Demo Stage
        val stageBackdrop = rememberLiquidGlassBackdropState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(322.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.2.dp, SlateBorder.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                .testTag("liquid_glass_live_preview")
        ) {
            when (activeTab) {
                OpticalLabTab.PIN_PAD -> {
                    val panKey = (panOffset.x.roundToInt().toLong() shl 20) xor panOffset.y.roundToInt().toLong()
                    // Draggable high-contrast abstract wallpaper: cached via captureKey so it ONLY re-records while dragging!
                    AbstractLiquidWallpaper(
                        panOffset = panOffset,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    panOffset = Offset(
                                        x = (panOffset.x + dragAmount.x).coerceIn(-160f, 160f),
                                        y = (panOffset.y + dragAmount.y).coerceIn(-140f, 140f)
                                    )
                                }
                            }
                            .liquidGlassBackdropSource(
                                state = stageBackdrop,
                                staticCapture = true,
                                alwaysCapture = true,
                                captureKey = panKey
                            )
                    )
                    CompositionLocalProvider(LocalLiquidGlassBackdrop provides stageBackdrop) {
                        LiquidGlassPinPadDemo(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                OpticalLabTab.FLOATING_NAV -> {
                    // Live scrolling colorful list behind MeeTube floating frosted-crystal nav pill!
                    LiquidNavScrollingDemo(
                        backdropState = stageBackdrop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                OpticalLabTab.CONTROLS -> {
                    val panKey = (panOffset.x.roundToInt().toLong() shl 20) xor panOffset.y.roundToInt().toLong()
                    AbstractLiquidWallpaper(
                        panOffset = panOffset,
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidGlassBackdropSource(
                                state = stageBackdrop,
                                staticCapture = true,
                                alwaysCapture = true,
                                captureKey = panKey
                            )
                    )
                    CompositionLocalProvider(LocalLiquidGlassBackdrop provides stageBackdrop) {
                        LiquidControlsStageDemo(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        // Compact Floating Real-Time Optical Calibration Panel right under the live stage
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    liveBackdrop = false,
                    fallbackBg = SlateDark.copy(alpha = 0.78f),
                    accentBorder = CyanAccent.copy(alpha = 0.45f)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("lg_quick_tuning_panel"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tinh chỉnh quang học MeeTube tức thời",
                        style = MaterialTheme.typography.labelLarge,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (config.enabled) "Đang bật" else "Xem thử",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (config.enabled) EmeraldAccent else AmberAccent
                )
            }

            QuickOpticalSlider(
                label = "Blur Radius (Độ mờ kính)",
                value = config.blurRadius,
                range = 0f..36f,
                valueLabel = "${config.blurRadius.roundToInt()} dp",
                accent = SkyBlue,
                testTag = "lg_lab_slider_blur",
                onValueChange = onUpdateBlurRadius
            )
            QuickOpticalSlider(
                label = "Vibrancy (Độ rực màu nền MeeTube)",
                value = config.vibrancy,
                range = 0f..1f,
                valueLabel = "${(config.vibrancy * 100f).roundToInt()}%",
                accent = EmeraldAccent,
                testTag = "lg_lab_slider_vibrancy",
                onValueChange = onUpdateVibrancy
            )
            QuickOpticalSlider(
                label = "Refraction Intensity (Khúc xạ viền)",
                value = config.refraction,
                range = 0f..1f,
                valueLabel = "${(config.refraction * 100f).roundToInt()}%",
                accent = CyanAccent,
                testTag = "lg_lab_slider_refraction",
                onValueChange = onUpdateRefraction
            )
            QuickOpticalSlider(
                label = "Bevel Thickness (Độ dày vát)",
                value = config.bevelThickness,
                range = 0.15f..1f,
                valueLabel = "${(config.bevelThickness * 100f).roundToInt()}%",
                accent = IndigoBright,
                testTag = "lg_lab_slider_bevel",
                onValueChange = onUpdateBevelThickness
            )
            QuickOpticalSlider(
                label = "Chromatic Dispersion (Tán sắc RGB)",
                value = config.chromaticDispersion,
                range = 0f..1f,
                valueLabel = "${(config.chromaticDispersion * 100f).roundToInt()}%",
                accent = PinkAccent,
                testTag = "lg_lab_slider_dispersion",
                onValueChange = onUpdateChromaticDispersion
            )
        }
    }
}

@Composable
private fun QuickOpticalSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    accent: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueLabel,
                fontSize = 11.5.sp,
                fontFamily = MonoFontFamily,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        LiquidGlassSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            accentColor = accent,
            testTag = testTag,
            forceCrystalStyle = true
        )
    }
}

/**
 * High-contrast abstract curved-ribbon & luminous-orb wallpaper inspired by iOS 26 glass demos
 * so convex refraction, bevel curvature, and RGB prism dispersion pop unmistakably.
 */
@Composable
private fun AbstractLiquidWallpaper(
    panOffset: Offset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ox = panOffset.x
        val oy = panOffset.y

        // Deep rich emerald-obsidian base
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0A1D24),
                    Color(0xFF15383E),
                    Color(0xFF0D1426)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        // Luminous overlapping glass orbs (olive-gold, turquoise, electric cobalt, magenta)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE7DF74), Color(0xFF7B9E44), Color.Transparent),
                center = Offset(w * 0.24f + ox * 0.6f, h * 0.62f + oy * 0.6f),
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = Offset(w * 0.24f + ox * 0.6f, h * 0.62f + oy * 0.6f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF56E0E0), Color(0xFF0E7490), Color.Transparent),
                center = Offset(w * 0.78f + ox * 0.8f, h * 0.28f + oy * 0.8f),
                radius = w * 0.42f
            ),
            radius = w * 0.42f,
            center = Offset(w * 0.78f + ox * 0.8f, h * 0.28f + oy * 0.8f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF472B6), Color(0xFF6366F1), Color.Transparent),
                center = Offset(w * 0.82f + ox * 0.5f, h * 0.82f + oy * 0.5f),
                radius = w * 0.38f
            ),
            radius = w * 0.38f,
            center = Offset(w * 0.82f + ox * 0.5f, h * 0.82f + oy * 0.5f)
        )

        // High-contrast crisp curved glass arcs & geometric lines so refraction bending is crystal clear!
        val arcPath1 = Path().apply {
            moveTo(-w * 0.1f + ox, h * 0.18f + oy)
            cubicTo(
                w * 0.28f + ox, h * 0.02f + oy,
                w * 0.52f + ox, h * 0.86f + oy,
                w * 1.1f + ox, h * 0.46f + oy
            )
        }
        drawPath(
            path = arcPath1,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFFEF08A), Color(0xFF38BDF8), Color(0xFFF43F5E))
            ),
            style = Stroke(width = 24f, cap = StrokeCap.Round)
        )
        drawPath(
            path = arcPath1,
            color = Color.White.copy(alpha = 0.88f),
            style = Stroke(width = 5.5f, cap = StrokeCap.Round)
        )

        val arcPath2 = Path().apply {
            moveTo(-w * 0.08f + ox * 0.7f, h * 0.78f + oy * 0.7f)
            cubicTo(
                w * 0.34f + ox * 0.7f, h * 0.96f + oy * 0.7f,
                w * 0.62f + ox * 0.7f, h * 0.14f + oy * 0.7f,
                w * 1.08f + ox * 0.7f, h * 0.24f + oy * 0.7f
            )
        }
        drawPath(
            path = arcPath2,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFA78BFA))
            ),
            style = Stroke(width = 18f, cap = StrokeCap.Round)
        )
        drawPath(
            path = arcPath2,
            color = Color(0xFF06101B).copy(alpha = 0.75f),
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )

        // Crisp subtle diagonal reference lines to showcase lens edge distortion
        for (i in 1..4) {
            val y = h * (i / 5f) + oy * 0.4f
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(w, y - h * 0.14f),
                strokeWidth = 2.2f
            )
        }
    }
}

/**
 * 1. Liquid Glass PIN Pad (0–9 circular refractive lens keycaps with elastic spring + radial bloom).
 */
@Composable
private fun LiquidGlassPinPadDemo(modifier: Modifier = Modifier) {
    var pinDigits by remember { mutableStateOf("") }
    val rows = remember {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // PIN indicator dots + hint
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "Kéo nền hoặc chạm phím tròn để xem khúc xạ & tán sắc",
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { idx ->
                    val filled = idx < pinDigits.length
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (filled) Color.White else Color.Transparent)
                            .border(1.2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                }
            }
        }

        // 3x4 Circular Crystal Lens Keycaps
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .liquidGlassPressPhysics(scaleOnPress = 1.13f, bloomColor = Color.White)
                                .liquidGlassSurface(
                                    shape = CircleShape,
                                    pillShape = CircleShape,
                                    elevated = false,
                                    lensZoom = 1.35f,
                                    crystalClarity = true,
                                    forceLiveOptical = true
                                )
                                .clickable {
                                    when (key) {
                                        "C" -> pinDigits = ""
                                        "⌫" -> if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                                        else -> {
                                            pinDigits = if (pinDigits.length >= 6) key else pinDigits + key
                                        }
                                    }
                                }
                                .testTag("lg_pin_key_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "⌫") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Xóa",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Floating Liquid Navigation Bar Demo floating over colorful scrolling icon tiles
 *    (directly reproduces the live scrolling refraction & chromatic rim seen in the TikTok video!).
 */
@Composable
private fun LiquidNavScrollingDemo(
    backdropState: LiquidGlassBackdropState,
    modifier: Modifier = Modifier
) {
    val demoRows = remember {
        listOf(
            Triple("Toggles & Sliders", "Công tắc và thanh trượt pha lê iOS 26", Color(0xFFF59E0B) to Icons.Default.Tune),
            Triple("LockScreen Glass", "Khúc xạ thấu kính, viền vát & tán sắc RGB", Color(0xFF0EA5E9) to Icons.Default.Lock),
            Triple("System Physics", "Vật lý lò xo đàn hồi & Radial Bloom", Color(0xFF6366F1) to Icons.Default.AutoAwesome),
            Triple("Notification Centre", "Lấy mẫu nội dung cuộn sống động 60fps", Color(0xFFEF4444) to Icons.Default.Notifications),
            Triple("Floating Nav Bar", "Giọt kính phóng đại trượt mượt theo tay", Color(0xFF10B981) to Icons.Default.Category),
            Triple("Color Palette Studio", "Tự pha màu & tùy biến kính theo thời gian thực", Color(0xFFEC4899) to Icons.Default.Palette),
            Triple("Haptic & Motion", "Độ nảy hữu cơ cho mọi thao tác chạm", Color(0xFF8B5CF6) to Icons.Default.Vibration),
            Triple("Math Engine 3D/2D", "Vẽ hình không gian & đồ thị sắc nét", Color(0xFF06B6D4) to Icons.Default.Functions)
        )
    }
    var selectedNav by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.background(Color(0xFF05070B))) {
        // Scrolling colorful icon rows captured live as backdrop!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(
                    state = backdropState,
                    staticCapture = false,
                    alwaysCapture = true
                )
                .verticalScroll(scrollState)
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Cuộn danh sách này để thấy icon biến dạng qua thanh kính dưới",
                fontSize = 11.sp,
                color = CyanAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            demoRows.forEach { (title, sub, pair) ->
                val (badgeColor, icon) = pair
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF131823))
                        .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(sub, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // Floating Refractive Navigation Bar with Fluid Morphing Pill directly over the scrolling list!
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdropState) {
            val items = remember {
                listOf(
                    "Home" to Icons.Default.Home,
                    "Settings" to Icons.Default.Settings,
                    "About" to Icons.Default.Info
                )
            }
            val animatedIdx by animateFloatAsState(
                targetValue = selectedNav.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "lab_nav_pill_idx"
            )
            val velocityStretch = (abs(animatedIdx - selectedNav.toFloat()) * 0.22f).coerceIn(0f, 0.18f)

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        elevated = true,
                        lensZoom = 1.2f,
                        crystalClarity = true,
                        forceLiveOptical = true
                    )
                    .padding(4.dp)
            ) {
                val slotWidth = maxWidth / items.size
                val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }

                // Sliding Magnified Refractive Active Droplet
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = (animatedIdx * slotWidthPx).roundToInt(), y = 0) }
                        .width(slotWidth)
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f + velocityStretch
                            scaleY = 1f - velocityStretch * 0.35f
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    Color.White.copy(alpha = 0.18f),
                                    Color(0xFFCFFAFE).copy(alpha = 0.65f)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEachIndexed { idx, (label, icon) ->
                        val active = selectedNav == idx
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(CircleShape)
                                .clickable { selectedNav = idx },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. Live Liquid Glass Toggle Switch & Slider showcase over the colorful refractive backdrop.
 */
@Composable
private fun LiquidControlsStageDemo(modifier: Modifier = Modifier) {
    var toggleA by remember { mutableStateOf(true) }
    var toggleB by remember { mutableStateOf(false) }
    var sliderVal by remember { mutableFloatStateOf(0.65f) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassPressPhysics(scaleOnPress = 1.03f)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    cornerRadiusDp = 20.dp,
                    elevated = true,
                    crystalClarity = true,
                    forceLiveOptical = true
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Liquid Glass Toggle", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Nút kính co giãn đàn hồi khi chạm", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
            }
            LiquidGlassSwitch(
                checked = toggleA,
                onCheckedChange = { toggleA = it },
                checkedTrackColor = Color(0xFF30D158),
                forceCrystalStyle = true
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassPressPhysics(scaleOnPress = 1.03f)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    cornerRadiusDp = 20.dp,
                    elevated = true,
                    crystalClarity = true,
                    forceLiveOptical = true
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Radial Bloom & Prism", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Quầng sáng tỏa theo vị trí ngón tay", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
            }
            LiquidGlassSwitch(
                checked = toggleB,
                onCheckedChange = { toggleB = it },
                checkedTrackColor = CyanAccent,
                forceCrystalStyle = true
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    cornerRadiusDp = 20.dp,
                    elevated = true,
                    crystalClarity = true,
                    forceLiveOptical = true
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Liquid Glass Slider", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${(sliderVal * 100).roundToInt()}%", color = CyanAccent, fontSize = 12.sp, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
            }
            LiquidGlassSlider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                accentColor = CyanAccent,
                forceCrystalStyle = true
            )
        }
    }
}
