package com.example.ui.panes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Geo3DMathHelpers
import com.example.engine.GeometryUiState
import com.example.engine.SensitivityConfig
import com.example.engine.ShapeBuilder
import com.example.engine.ShapeCategory
import com.example.engine.ShapeType
import com.example.engine.formatMathNumber
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SettingBox(
    title: String? = null,
    titleColor: Color = IndigoLight,
    borderColor: Color = SlateBorder,
    containerColor: Color = SlateSurface.copy(alpha = 0.55f),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = titleColor,
                letterSpacing = 0.5.sp
            )
        }
        content()
    }
}

@Composable
fun CompactDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == selected }?.second ?: selected

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateDark)
                    .border(1.dp, SlateMuted, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Chọn $label",
                    tint = SlateTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = SlateSurface
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (key == selected) CyanAccent else SlateTextPrimary
                            )
                        },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShapePreviewThumbnail(
    shape: ShapeType,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(34.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.2.dp.toPx()))
        val hiddenColor = SlateTextSecondary.copy(alpha = 0.7f)

        fun seg(x1: Float, y1: Float, x2: Float, y2: Float, dashed: Boolean = false, col: Color = accent) {
            drawLine(
                color = if (dashed) hiddenColor else col,
                start = Offset(w * x1, h * y1),
                end = Offset(w * x2, h * y2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = if (dashed) dash else null
            )
        }

        when (shape) {
            ShapeType.PYRAMID3 -> {
                seg(0.5f, 0.1f, 0.14f, 0.82f)
                seg(0.5f, 0.1f, 0.86f, 0.82f)
                seg(0.5f, 0.1f, 0.56f, 0.90f)
                seg(0.14f, 0.82f, 0.56f, 0.90f)
                seg(0.56f, 0.90f, 0.86f, 0.82f)
                seg(0.14f, 0.82f, 0.86f, 0.82f, dashed = true)
                seg(0.5f, 0.1f, 0.5f, 0.83f, dashed = true, col = RoseAccent)
            }
            ShapeType.PYRAMID3_SA, ShapeType.TETRA_ORTHO -> {
                seg(0.24f, 0.12f, 0.24f, 0.84f, col = RoseAccent)
                seg(0.24f, 0.12f, 0.88f, 0.78f)
                seg(0.24f, 0.12f, 0.52f, 0.90f)
                seg(0.24f, 0.84f, 0.52f, 0.90f)
                seg(0.52f, 0.90f, 0.88f, 0.78f)
                seg(0.24f, 0.84f, 0.88f, 0.78f, dashed = true)
            }
            ShapeType.PYRAMID4 -> {
                seg(0.5f, 0.10f, 0.14f, 0.86f)
                seg(0.5f, 0.10f, 0.68f, 0.88f)
                seg(0.5f, 0.10f, 0.88f, 0.68f)
                seg(0.5f, 0.10f, 0.34f, 0.66f, dashed = true)
                seg(0.14f, 0.86f, 0.68f, 0.88f)
                seg(0.68f, 0.88f, 0.88f, 0.68f)
                seg(0.14f, 0.86f, 0.34f, 0.66f, dashed = true)
                seg(0.34f, 0.66f, 0.88f, 0.68f, dashed = true)
                seg(0.5f, 0.10f, 0.5f, 0.77f, dashed = true, col = RoseAccent)
            }
            ShapeType.PYRAMID4_SA, ShapeType.PYRAMID_TRAP -> {
                seg(0.22f, 0.12f, 0.22f, 0.70f, col = RoseAccent)
                seg(0.22f, 0.12f, 0.14f, 0.88f)
                seg(0.22f, 0.12f, 0.72f, 0.88f)
                seg(0.22f, 0.12f, 0.88f, 0.70f)
                seg(0.14f, 0.88f, 0.72f, 0.88f)
                seg(0.72f, 0.88f, 0.88f, 0.70f)
                seg(0.22f, 0.70f, 0.14f, 0.88f, dashed = true)
                seg(0.22f, 0.70f, 0.88f, 0.70f, dashed = true)
            }
            ShapeType.PRISM3 -> {
                seg(0.20f, 0.22f, 0.80f, 0.22f)
                seg(0.20f, 0.22f, 0.52f, 0.36f)
                seg(0.80f, 0.22f, 0.52f, 0.36f)
                seg(0.20f, 0.22f, 0.20f, 0.76f)
                seg(0.80f, 0.22f, 0.80f, 0.76f)
                seg(0.52f, 0.36f, 0.52f, 0.90f)
                seg(0.20f, 0.76f, 0.52f, 0.90f)
                seg(0.80f, 0.76f, 0.52f, 0.90f)
                seg(0.20f, 0.76f, 0.80f, 0.76f, dashed = true)
            }
            ShapeType.PRISM3_OBLIQUE -> {
                seg(0.32f, 0.18f, 0.86f, 0.18f)
                seg(0.32f, 0.18f, 0.62f, 0.32f)
                seg(0.86f, 0.18f, 0.62f, 0.32f)
                seg(0.32f, 0.18f, 0.14f, 0.78f)
                seg(0.86f, 0.18f, 0.68f, 0.78f)
                seg(0.62f, 0.32f, 0.44f, 0.90f)
                seg(0.14f, 0.78f, 0.44f, 0.90f)
                seg(0.68f, 0.78f, 0.44f, 0.90f)
                seg(0.14f, 0.78f, 0.68f, 0.78f, dashed = true)
            }
            ShapeType.PRISM6 -> {
                val top = (0 until 6).map { i ->
                    val a = i * PI / 3.0
                    Offset(w * (0.5f + 0.34f * cos(a).toFloat()), h * (0.24f + 0.12f * sin(a).toFloat()))
                }
                val bot = (0 until 6).map { i ->
                    val a = i * PI / 3.0
                    Offset(w * (0.5f + 0.34f * cos(a).toFloat()), h * (0.76f + 0.12f * sin(a).toFloat()))
                }
                for (i in 0 until 6) {
                    val j = (i + 1) % 6
                    drawLine(accent, top[i], top[j], stroke)
                    val isBack = i == 3 || i == 4
                    drawLine(if (isBack) hiddenColor else accent, bot[i], bot[j], stroke, pathEffect = if (isBack) dash else null)
                    drawLine(if (i == 4) hiddenColor else accent, top[i], bot[i], stroke, pathEffect = if (i == 4) dash else null)
                }
            }
            ShapeType.CUBE, ShapeType.BOX -> {
                val dx = if (shape == ShapeType.BOX) 0.16f else 0.18f
                val topY = if (shape == ShapeType.BOX) 0.24f else 0.18f
                seg(0.16f, topY + 0.14f, 0.66f, topY + 0.14f)
                seg(0.16f, topY + 0.14f, 0.16f + dx, topY)
                seg(0.66f, topY + 0.14f, 0.66f + dx, topY)
                seg(0.16f + dx, topY, 0.66f + dx, topY)
                seg(0.16f, topY + 0.14f, 0.16f, 0.86f)
                seg(0.66f, topY + 0.14f, 0.66f, 0.86f)
                seg(0.66f + dx, topY, 0.66f + dx, 0.72f)
                seg(0.16f, 0.86f, 0.66f, 0.86f)
                seg(0.66f, 0.86f, 0.66f + dx, 0.72f)
                seg(0.16f + dx, topY, 0.16f + dx, 0.72f, dashed = true)
                seg(0.16f, 0.86f, 0.16f + dx, 0.72f, dashed = true)
                seg(0.16f + dx, 0.72f, 0.66f + dx, 0.72f, dashed = true)
            }
            ShapeType.OCTAHEDRON -> {
                seg(0.5f, 0.08f, 0.14f, 0.5f)
                seg(0.5f, 0.08f, 0.5f, 0.62f)
                seg(0.5f, 0.08f, 0.86f, 0.5f)
                seg(0.5f, 0.92f, 0.14f, 0.5f)
                seg(0.5f, 0.92f, 0.5f, 0.62f)
                seg(0.5f, 0.92f, 0.86f, 0.5f)
                seg(0.14f, 0.5f, 0.5f, 0.62f)
                seg(0.5f, 0.62f, 0.86f, 0.5f)
                seg(0.14f, 0.5f, 0.86f, 0.5f, dashed = true)
            }
            ShapeType.CYLINDER -> {
                drawOval(accent, topLeft = Offset(w * 0.2f, h * 0.14f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.2f), style = Stroke(stroke))
                drawOval(hiddenColor, topLeft = Offset(w * 0.2f, h * 0.68f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.2f), style = Stroke(stroke, pathEffect = dash))
                drawArc(accent, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.2f, h * 0.68f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.2f), style = Stroke(stroke))
                seg(0.2f, 0.24f, 0.2f, 0.78f)
                seg(0.8f, 0.24f, 0.8f, 0.78f)
            }
            ShapeType.CONE -> {
                seg(0.5f, 0.12f, 0.18f, 0.78f)
                seg(0.5f, 0.12f, 0.82f, 0.78f)
                seg(0.5f, 0.12f, 0.5f, 0.78f, dashed = true, col = RoseAccent)
                drawOval(hiddenColor, topLeft = Offset(w * 0.18f, h * 0.68f), size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.2f), style = Stroke(stroke, pathEffect = dash))
                drawArc(accent, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.18f, h * 0.68f), size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.2f), style = Stroke(stroke))
            }
            ShapeType.SPHERE -> {
                drawCircle(accent, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(stroke))
                drawOval(hiddenColor, topLeft = Offset(w * 0.14f, h * 0.40f), size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.20f), style = Stroke(stroke, pathEffect = dash))
                drawArc(accent, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.14f, h * 0.40f), size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.20f), style = Stroke(stroke))
            }
        }
    }
}

@Composable
fun ShapesPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ShapeCategory.entries.forEach { cat ->
            val isOpen = cat in uiState.openCategories
            val shapesInCat = ShapeType.entries.filter { it.category == cat }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateSurface.copy(alpha = 0.45f))
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_${cat.name.lowercase()}")
                        .background(SlateDark.copy(alpha = 0.85f))
                        .clickable { viewModel.toggleShapeCategory(cat) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = "Mở rộng nhóm ${cat.title}",
                            tint = if (isOpen) CyanAccent else SlateTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isOpen) SlateTextPrimary else SlateTextLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = cat.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOpen) CyanAccent else SlateTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurface)
                            .border(1.dp, if (isOpen) CyanAccent.copy(alpha = 0.5f) else SlateBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                AnimatedVisibility(visible = isOpen) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        shapesInCat.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                pair.forEach { shape ->
                                    val active = uiState.snapshot.shape == shape
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("shape_${shape.key}")
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (active) IndigoPrimary.copy(alpha = 0.34f) else SlateDark.copy(alpha = 0.85f))
                                            .border(
                                                width = if (active) 1.5.dp else 1.dp,
                                                color = if (active) CyanAccent else SlateBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.selectShape(shape) }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        ShapePreviewThumbnail(
                                            shape = shape,
                                            accent = if (active) CyanAccent else IndigoSoft
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = shape.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (active) SlateTextPrimary else SlateTextLight,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = shape.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SlateTextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensitivityControlsSection(
    sensitivity: SensitivityConfig,
    viewModel: GeometryViewModel,
    compact: Boolean = false
) {
    val isPrecise = abs(sensitivity.rotation - 0.5f) < 0.08f && abs(sensitivity.drag - 0.5f) < 0.08f
    val isBalanced = abs(sensitivity.rotation - 1.0f) < 0.08f && abs(sensitivity.drag - 1.0f) < 0.08f && abs(sensitivity.zoom - 1.0f) < 0.08f
    val isFast = abs(sensitivity.rotation - 1.7f) < 0.1f && abs(sensitivity.zoom - 1.5f) < 0.1f

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
        // Quick Presets Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetChip(
                label = "Chính xác",
                selected = isPrecise,
                testTag = "sens_preset_precise",
                modifier = Modifier.weight(1f)
            ) { viewModel.applySensitivityPreset("precise") }
            PresetChip(
                label = "Cân bằng",
                selected = isBalanced,
                testTag = "sens_preset_balanced",
                modifier = Modifier.weight(1f)
            ) { viewModel.applySensitivityPreset("balanced") }
            PresetChip(
                label = "Nhanh",
                selected = isFast,
                testTag = "sens_preset_fast",
                modifier = Modifier.weight(1f)
            ) { viewModel.applySensitivityPreset("fast") }
        }

        SensitivitySliderRow(
            label = "Xoay hình 3D",
            value = sensitivity.rotation,
            range = 0.2f..2.5f,
            accent = CyanAccent,
            testTag = "slider_sens_rotation",
            onValueChange = { viewModel.updateSensitivity(rotation = it) },
            onFinished = { viewModel.commitSliderChange() }
        )
        SensitivitySliderRow(
            label = "Phóng to / Thu nhỏ",
            value = sensitivity.zoom,
            range = 0.3f..2.5f,
            accent = IndigoSoft,
            testTag = "slider_sens_zoom",
            onValueChange = { viewModel.updateSensitivity(zoom = it) },
            onFinished = { viewModel.commitSliderChange() }
        )
        SensitivitySliderRow(
            label = "Kéo đỉnh / điểm",
            value = sensitivity.drag,
            range = 0.2f..2.0f,
            accent = PinkLight,
            testTag = "slider_sens_drag",
            onValueChange = { viewModel.updateSensitivity(drag = it) },
            onFinished = { viewModel.commitSliderChange() }
        )
        SensitivitySliderRow(
            label = "Vùng chạm & bắt điểm",
            value = sensitivity.touchRadius,
            range = 0.6f..1.8f,
            accent = EmeraldLight,
            testTag = "slider_sens_touch",
            onValueChange = { viewModel.updateSensitivity(touchRadius = it) },
            onFinished = { viewModel.commitSliderChange() }
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) IndigoPrimary else SlateDark)
            .border(1.dp, if (selected) CyanAccent else SlateMuted, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) SlateTextPrimary else SlateTextLight,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SensitivitySliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    testTag: String,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
            Text(
                text = String.format(Locale.US, "%.2fx", value),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        LiquidGlassSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            accentColor = accent,
            testTag = testTag
        )
    }
}

@Composable
fun SettingsPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    onImportJsonClick: () -> Unit
) {
    val snap = uiState.snapshot
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Dimensions Sliders
        SettingBox(title = "Kích thước chuẩn ban đầu") {
            DimensionSliderRow(
                label = if (snap.shape.isRound) "Đường kính (2R):" else "Cạnh đáy (a):",
                value = snap.a,
                range = 1.5f..6.5f,
                onValueChange = { viewModel.updateParamA(it) },
                onValueChangeFinished = { viewModel.commitSliderChange() }
            )
            if (snap.shape.hasB) {
                DimensionSliderRow(
                    label = "Chiều rộng đáy (b):",
                    value = snap.b,
                    range = 1.5f..6.5f,
                    onValueChange = { viewModel.updateParamB(it) },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
            if (snap.shape.hasH) {
                DimensionSliderRow(
                    label = "Chiều cao (h):",
                    value = snap.h,
                    range = 2.0f..7.5f,
                    onValueChange = { viewModel.updateParamH(it) },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
        }

        // 3. Display Switches (SGK standard)
        SettingBox(title = "Hiển thị chuẩn SGK") {
            SettingCheckRow("Hiện lưới mặt phẳng đáy", snap.showGrid) { viewModel.toggleGrid(it) }
            SettingCheckRow("Nét đứt tự động cho cạnh khuất", snap.dashed) { viewModel.toggleDashed(it) }
            SettingCheckRow("Hiện tên các điểm (S, A, B, M...)", snap.labels) { viewModel.toggleLabels(it) }
            SettingCheckRow("Hiện đường cao & góc vuông", snap.altitude) { viewModel.toggleAltitude(it) }
            SettingCheckRow("Tô màu bề mặt khối 3D", snap.faces) { viewModel.toggleFaces(it) }
            SettingCheckRow("Tự động xoay hình", uiState.autoRotate) { viewModel.toggleAutoRotate(it) }
        }

        // 4. Cross-section plane
        SettingBox(title = "Mặt cắt ngang song song đáy", titleColor = AmberAccent) {
            SettingCheckRow("Bật mặt cắt ngang", snap.showSection) { viewModel.toggleSection(it) }
            AnimatedVisibility(visible = snap.showSection) {
                DimensionSliderRow(
                    label = "Vị trí mặt cắt (k = h'/h):",
                    value = snap.secRatio,
                    range = 0.15f..0.85f,
                    decimals = 2,
                    onValueChange = { viewModel.updateSectionRatio(it) },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
        }

        // 5. Save / Export / Reset
        SettingBox(
            title = "Lưu bài & Xuất File",
            titleColor = EmeraldLight,
            borderColor = EmeraldDark,
            containerColor = EmeraldDark.copy(alpha = 0.14f)
        ) {
            Button(
                onClick = { viewModel.openSaveModal() },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 9.dp, horizontal = 10.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_open_save_modal_btn")
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đặt tên File / Tải PNG / Lưu JSON", style = MaterialTheme.typography.labelMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onImportJsonClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateMuted),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Mở .JSON", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseAccent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = RoseAccent.copy(alpha = 0.16f),
                        contentColor = RoseLight
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Mặc định", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = SlateDark,
            title = { Text("Đặt lại mặc định?", color = SlateTextPrimary) },
            text = { Text("Toàn bộ đỉnh đã kéo, đường kẻ thêm và cài đặt hiện tại sẽ được đưa về trạng thái mặc định ban đầu.", color = SlateTextLight) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    viewModel.resetAllToDefault()
                }) {
                    Text("Đặt lại", color = RoseAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Hủy", color = SlateTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DimensionSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 1,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
            Text(
                text = String.format(Locale.US, "%.${decimals}f", value),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = IndigoSoft,
                fontWeight = FontWeight.Bold
            )
        }
        LiquidGlassSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            accentColor = IndigoBright
        )
    }
}

@Composable
fun SettingCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextPrimary,
            modifier = Modifier.weight(1f)
        )
        LiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedTrackColor = IndigoBright
        )
    }
}
