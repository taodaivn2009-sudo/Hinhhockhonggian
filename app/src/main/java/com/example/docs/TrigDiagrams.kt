package com.example.docs

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.*

/**
 * Interactive Unit Circle ("Đường tròn lượng giác") matching the reference poster:
 * shows axes cos (Ox) / sin (Oy), positive/negative orientation arrows, all 16 special angles,
 * dashed cos/sin projections, and exact trigonometric values on tap.
 */
@Composable
fun InteractiveUnitCircleCard(
    modifier: Modifier = Modifier
) {
    val angles = TrigReferenceRepository.specialCircleAngles
    var selectedIdx by remember { mutableIntStateOf(1) } // Default: π/6 (30°)
    val selected = angles[selectedIdx]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateDark)
            .border(1.dp, CyanAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("trig_unit_circle_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "ĐƯỜNG TRÒN LƯỢNG GIÁC TƯƠNG TÁC",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Chạm trực tiếp vào điểm trên đường tròn hoặc chọn góc để xem sin, cos, tan, cot",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanAccent.copy(alpha = 0.16f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${selected.radLabel} (${selected.degrees}°)",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Interactive Circle Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(275.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateDeepest)
                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(angles) {
                        detectTapGestures { tap ->
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            val r = min(size.width, size.height) * 0.34f
                            var bestIdx = selectedIdx
                            var bestDist = Float.MAX_VALUE
                            angles.forEachIndexed { i, ang ->
                                val px = cx + (cos(ang.radians) * r).toFloat()
                                val py = cy - (sin(ang.radians) * r).toFloat()
                                val d = hypot(tap.x - px, tap.y - py)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestIdx = i
                                }
                            }
                            if (bestDist < r * 0.55f) {
                                selectedIdx = bestIdx
                            }
                        }
                    }
                    .testTag("trig_unit_circle_canvas")
            ) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val r = min(size.width, size.height) * 0.34f
                val axisExt = r * 1.28f
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)

                // Subtle quadrant fill tints
                drawArc(
                    color = Color(0xFF10B981).copy(alpha = 0.06f),
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f)
                )
                drawArc(
                    color = Color(0xFF38BDF8).copy(alpha = 0.06f),
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f)
                )

                // Coordinate Axes (cos horizontal, sin vertical)
                drawLine(Color(0xFF64748B), Offset(cx - axisExt, cy), Offset(cx + axisExt, cy), strokeWidth = 2.5f)
                drawLine(Color(0xFF64748B), Offset(cx, cy + axisExt), Offset(cx, cy - axisExt), strokeWidth = 2.5f)

                // Axis arrowheads
                drawLine(Color(0xFF94A3B8), Offset(cx + axisExt, cy), Offset(cx + axisExt - 11f, cy - 6f), strokeWidth = 2.5f)
                drawLine(Color(0xFF94A3B8), Offset(cx + axisExt, cy), Offset(cx + axisExt - 11f, cy + 6f), strokeWidth = 2.5f)
                drawLine(Color(0xFF94A3B8), Offset(cx, cy - axisExt), Offset(cx - 6f, cy - axisExt + 11f), strokeWidth = 2.5f)
                drawLine(Color(0xFF94A3B8), Offset(cx, cy - axisExt), Offset(cx + 6f, cy - axisExt + 11f), strokeWidth = 2.5f)

                // Unit Circle outline
                drawCircle(Color(0xFF94A3B8), radius = r, center = Offset(cx, cy), style = Stroke(width = 3f))

                // Positive (+) / Negative (-) direction indicator arcs at right side
                val orientR = r * 1.15f
                drawArc(
                    color = Color(0xFF34D399),
                    startAngle = -14f,
                    sweepAngle = -26f,
                    useCenter = false,
                    topLeft = Offset(cx - orientR, cy - orientR),
                    size = Size(orientR * 2f, orientR * 2f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFFB7185),
                    startAngle = 14f,
                    sweepAngle = 26f,
                    useCenter = false,
                    topLeft = Offset(cx - orientR, cy - orientR),
                    size = Size(orientR * 2f, orientR * 2f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )

                // Highlighted angle sector & projections
                val selX = cx + (selected.cosVal * r).toFloat()
                val selY = cy - (selected.sinVal * r).toFloat()
                if (selected.degrees > 0) {
                    val arcR = r * 0.25f
                    drawArc(
                        color = Color(0xFFFBBF24).copy(alpha = 0.25f),
                        startAngle = 0f,
                        sweepAngle = -selected.degrees.toFloat(),
                        useCenter = true,
                        topLeft = Offset(cx - arcR, cy - arcR),
                        size = Size(arcR * 2f, arcR * 2f)
                    )
                    drawArc(
                        color = Color(0xFFFBBF24),
                        startAngle = 0f,
                        sweepAngle = -selected.degrees.toFloat(),
                        useCenter = false,
                        topLeft = Offset(cx - arcR, cy - arcR),
                        size = Size(arcR * 2f, arcR * 2f),
                        style = Stroke(width = 2.8f)
                    )
                }

                // Dashed cos & sin projection lines
                drawLine(Color(0xFF38BDF8), Offset(selX, selY), Offset(selX, cy), strokeWidth = 2.4f, pathEffect = dashEffect)
                drawLine(Color(0xFFF43F5E), Offset(selX, selY), Offset(cx, selY), strokeWidth = 2.4f, pathEffect = dashEffect)
                // Solid segments on cos and sin axes
                drawLine(Color(0xFF38BDF8), Offset(cx, cy), Offset(selX, cy), strokeWidth = 4.5f, cap = StrokeCap.Round)
                drawLine(Color(0xFFF43F5E), Offset(cx, cy), Offset(cx, selY), strokeWidth = 4.5f, cap = StrokeCap.Round)
                // Radius vector OM
                drawLine(Color(0xFFFBBF24), Offset(cx, cy), Offset(selX, selY), strokeWidth = 3.5f, cap = StrokeCap.Round)

                // All 16 special angle dots + labels
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 24f
                    typeface = Typeface.MONOSPACE
                }
                val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 28f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                }

                axisPaint.color = 0xFF38BDF8.toInt()
                drawContext.canvas.nativeCanvas.drawText("cos", cx + axisExt - 6f, cy + 28f, axisPaint)
                axisPaint.color = 0xFFF43F5E.toInt()
                drawContext.canvas.nativeCanvas.drawText("sin", cx + 26f, cy - axisExt + 16f, axisPaint)

                // (+) and (-) labels
                textPaint.textSize = 22f
                textPaint.color = 0xFF34D399.toInt()
                drawContext.canvas.nativeCanvas.drawText("(+)", cx + orientR * 0.94f, cy - orientR * 0.45f, textPaint)
                textPaint.color = 0xFFFB7185.toInt()
                drawContext.canvas.nativeCanvas.drawText("(−)", cx + orientR * 0.94f, cy + orientR * 0.56f, textPaint)

                // Quadrant roman numerals
                textPaint.textSize = 24f
                textPaint.color = 0xFF475569.toInt()
                drawContext.canvas.nativeCanvas.drawText("I", cx + r * 0.52f, cy - r * 0.46f, textPaint)
                drawContext.canvas.nativeCanvas.drawText("II", cx - r * 0.52f, cy - r * 0.46f, textPaint)
                drawContext.canvas.nativeCanvas.drawText("III", cx - r * 0.52f, cy + r * 0.56f, textPaint)
                drawContext.canvas.nativeCanvas.drawText("IV", cx + r * 0.52f, cy + r * 0.56f, textPaint)

                textPaint.textSize = 21f
                angles.forEachIndexed { idx, ang ->
                    val px = cx + (ang.cosVal * r).toFloat()
                    val py = cy - (ang.sinVal * r).toFloat()
                    val isSel = idx == selectedIdx
                    drawCircle(
                        color = if (isSel) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                        radius = if (isSel) 9.5f else 4.8f,
                        center = Offset(px, py)
                    )
                    if (isSel) {
                        drawCircle(Color.White, radius = 9.5f, center = Offset(px, py), style = Stroke(width = 2.5f))
                    }
                    val lx = cx + (ang.cosVal * r * 1.19f).toFloat()
                    val ly = cy - (ang.sinVal * r * 1.19f).toFloat() + 7f
                    // Avoid colliding with axis labels at 0 and π/2
                    val drawLabel = !(ang.degrees == 0 || ang.degrees == 90) || isSel
                    if (drawLabel) {
                        textPaint.color = if (isSel) 0xFFFBBF24.toInt() else 0xFF94A3B8.toInt()
                        textPaint.isFakeBoldText = isSel
                        drawContext.canvas.nativeCanvas.drawText(ang.radLabel, lx, ly, textPaint)
                    }
                }
            }
        }

        // Scrollable quick-select chips for all 16 special angles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            angles.forEachIndexed { idx, ang ->
                val active = idx == selectedIdx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) AmberAccent.copy(alpha = 0.22f) else SlateSurface)
                        .border(1.dp, if (active) AmberAccent else SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedIdx = idx }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = ang.radLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = if (active) AmberAccent else SlateTextLight,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Exact trig values bar for selected angle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TrigValueBadge("sin α", selected.sinLabel, RoseLight, Modifier.weight(1f))
            TrigValueBadge("cos α", selected.cosLabel, CyanAccent, Modifier.weight(1f))
            TrigValueBadge("tan α", selected.tanLabel, EmeraldLight, Modifier.weight(1f))
            TrigValueBadge("cot α", selected.cotLabel, PurpleAccent, Modifier.weight(1f))
        }
        Text(
            text = "Vị trí: ${selected.quadrantLabel} • M(${selected.cosLabel}; ${selected.sinLabel})",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = SlateTextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TrigValueBadge(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(SlateDeepest)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Quadrant Sign Table ("Bảng xét dấu các giá trị lượng giác theo 4 góc phần tư I, II, III, IV").
 */
@Composable
fun QuadrantSignTableCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateDark)
            .border(1.dp, IndigoBright.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("trig_quadrant_sign_table"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "BẢNG XÉT DẤU GIÁ TRỊ LƯỢNG GIÁC",
            style = MaterialTheme.typography.labelLarge,
            color = IndigoSoft,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Mẹo nhớ nhanh: Nhất cả (I: tất cả +), Nhị sin (II: sin +), Tam tang (III: tan/cot +), Tứ cos (IV: cos +)",
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            fontSize = 11.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateDeepest)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
        ) {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurface)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Góc phần tư", style = MaterialTheme.typography.labelSmall, color = SlateTextLight, modifier = Modifier.weight(1.35f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                listOf("I", "II", "III", "IV").forEach { q ->
                    Text(q, style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily), color = AmberAccent, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            }
            TrigReferenceRepository.quadrantSignRows.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (idx % 2 == 0) Color.Transparent else SlateSurface.copy(alpha = 0.25f))
                        .padding(vertical = 7.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.fnName,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.35f),
                        textAlign = TextAlign.Center
                    )
                    listOf(row.q1, row.q2, row.q3, row.q4).forEach { sign ->
                        val isPos = sign == "+"
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isPos) EmeraldAccent.copy(alpha = 0.2f) else RoseAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sign,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                    color = if (isPos) EmeraldLight else RoseLight,
                                    fontWeight = FontWeight.ExtraBold
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
 * Mini Unit-Circle Diagram illustrating the 4 special angle pairs on the poster's right column:
 * Opposite (-α), Supplementary (π - α), Complementary (π/2 - α), and Differ by π (π + α).
 */
@Composable
fun SpecialAngleMiniDiagram(
    kind: SpecialAngleKind,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(106.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SlateDeepest)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val r = size.minDimension * 0.34f
        val ext = r * 1.32f
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)

        // Axes
        drawLine(Color(0xFF475569), Offset(cx - ext, cy), Offset(cx + ext, cy), strokeWidth = 1.8f)
        drawLine(Color(0xFF475569), Offset(cx, cy + ext), Offset(cx, cy - ext), strokeWidth = 1.8f)
        // Circle
        drawCircle(Color(0xFF64748B), radius = r, center = Offset(cx, cy), style = Stroke(width = 2f))

        // Base angle α = 32°
        val alphaDeg = 32.0
        val betaDeg = when (kind) {
            SpecialAngleKind.OPPOSITE -> -alphaDeg
            SpecialAngleKind.SUPPLEMENTARY -> 180.0 - alphaDeg
            SpecialAngleKind.COMPLEMENTARY -> 90.0 - alphaDeg
            SpecialAngleKind.PLUS_PI -> 180.0 + alphaDeg
        }
        val aRad = Math.toRadians(alphaDeg)
        val bRad = Math.toRadians(betaDeg)
        val mx = cx + (cos(aRad) * r).toFloat()
        val my = cy - (sin(aRad) * r).toFloat()
        val nx = cx + (cos(bRad) * r).toFloat()
        val ny = cy - (sin(bRad) * r).toFloat()

        // Symmetry guide line
        if (kind == SpecialAngleKind.COMPLEMENTARY) {
            drawLine(Color(0xFF64748B), Offset(cx - r * 0.9f, cy + r * 0.9f), Offset(cx + r * 1.05f, cy - r * 1.05f), strokeWidth = 1.4f, pathEffect = dash)
        }
        drawLine(accent.copy(alpha = 0.65f), Offset(mx, my), Offset(nx, ny), strokeWidth = 1.7f, pathEffect = dash)

        // Radii OM and ON
        drawLine(Color(0xFF38BDF8), Offset(cx, cy), Offset(mx, my), strokeWidth = 2.6f, cap = StrokeCap.Round)
        drawLine(accent, Offset(cx, cy), Offset(nx, ny), strokeWidth = 2.6f, cap = StrokeCap.Round)

        // Projection dashes
        drawLine(Color(0xFF38BDF8).copy(alpha = 0.45f), Offset(mx, my), Offset(mx, cy), strokeWidth = 1.3f, pathEffect = dash)
        drawLine(accent.copy(alpha = 0.45f), Offset(nx, ny), Offset(nx, cy), strokeWidth = 1.3f, pathEffect = dash)

        // Points M and N
        drawCircle(Color(0xFF38BDF8), radius = 4.5f, center = Offset(mx, my))
        drawCircle(accent, radius = 4.5f, center = Offset(nx, ny))

        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 20f
            isFakeBoldText = true
        }
        lblPaint.color = 0xFF38BDF8.toInt()
        drawContext.canvas.nativeCanvas.drawText("M", mx + 12f, my - 6f, lblPaint)
        lblPaint.color = android.graphics.Color.WHITE
        val nOffX = if (nx < cx) -12f else 12f
        val nOffY = if (ny < cy) -6f else 16f
        drawContext.canvas.nativeCanvas.drawText("N", nx + nOffX, ny + nOffY, lblPaint)
    }
}

/**
 * Poster-inspired Mindmap Wheel Header ("CÁC CÔNG THỨC LƯỢNG GIÁC") with 5 clickable petals
 * that jump/filter directly to each formula family.
 */
@Composable
fun TrigMindmapOverviewCard(
    selectedCategory: TrigDocCategory,
    onSelectCategory: (TrigDocCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val petals = remember {
        listOf(
            Triple(TrigDocCategory.ADDITION, "Công thức cộng", Color(0xFF10B981)),
            Triple(TrigDocCategory.DOUBLE_TRIPLE, "Nhân đôi – Ba", Color(0xFFF59E0B)),
            Triple(TrigDocCategory.POWER_REDUCE, "Hạ bậc", Color(0xFFFB7185)),
            Triple(TrigDocCategory.TRANSFORM, "Tổng ↔ Tích", Color(0xFF2DD4BF)),
            Triple(TrigDocCategory.SPECIAL_ANGLES, "Góc liên quan", Color(0xFFA855F7))
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateDark)
            .border(1.dp, IndigoBright.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "CÁC CÔNG THỨC LƯỢNG GIÁC",
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Chạm nhánh sơ đồ tư duy để lọc nhanh nhóm công thức",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberAccent.copy(alpha = 0.2f))
                    .border(1.dp, AmberAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = TrigReferenceRepository.CHAPTER_BADGE,
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Visual 5-petal wheel indicator + quick-tap buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            petals.take(3).forEach { (cat, label, col) ->
                MindmapPetalChip(
                    label = label,
                    color = col,
                    active = selectedCategory == cat,
                    onClick = { onSelectCategory(if (selectedCategory == cat) TrigDocCategory.ALL else cat) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            petals.drop(3).forEach { (cat, label, col) ->
                MindmapPetalChip(
                    label = label,
                    color = col,
                    active = selectedCategory == cat,
                    onClick = { onSelectCategory(if (selectedCategory == cat) TrigDocCategory.ALL else cat) },
                    modifier = Modifier.weight(1f)
                )
            }
            MindmapPetalChip(
                label = "Cơ bản & ĐTLG",
                color = CyanAccent,
                active = selectedCategory == TrigDocCategory.BASIC || selectedCategory == TrigDocCategory.UNIT_CIRCLE,
                onClick = {
                    onSelectCategory(
                        if (selectedCategory == TrigDocCategory.BASIC) TrigDocCategory.ALL else TrigDocCategory.BASIC
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MindmapPetalChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) color.copy(alpha = 0.28f) else SlateDeepest)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) color else color.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
