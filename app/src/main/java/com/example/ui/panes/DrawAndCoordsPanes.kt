package com.example.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.*
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun DrawPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    val pointNames = remember(uiState.scene.pointRegistry) { uiState.scene.pointRegistry.keys.toList() }
    val pointOptions = remember(pointNames) { pointNames.map { it to it } }
    val optionalOptions = remember(pointNames) { listOf("" to "-- 3 điểm --") + pointOptions }

    // 1. Add custom point state
    val suggestedName = remember(uiState.scene.pointRegistry) { ShapeBuilder.nextAutoPointName(uiState.scene.pointRegistry) }
    var ptName by remember(suggestedName) { mutableStateOf(suggestedName) }
    var ptFrom by remember(pointNames) { mutableStateOf(pointNames.getOrElse(0) { "S" }) }
    var ptTo by remember(pointNames) { mutableStateOf(pointNames.getOrElse(1) { pointNames.firstOrNull() ?: "A" }) }
    var ptRatio by remember { mutableFloatStateOf(0.5f) }

    // 2. Add line / vector state
    var lineP1 by remember(pointNames) { mutableStateOf(pointNames.getOrElse(0) { "S" }) }
    var lineP2 by remember(pointNames) { mutableStateOf(pointNames.getOrElse(1) { pointNames.firstOrNull() ?: "A" }) }
    var lineStyle by remember { mutableStateOf(LineStyle.DASHED) }
    var lineColor by remember { mutableStateOf("#38bdf8") }

    // 3. Add custom plane state
    var pl1 by remember(pointNames) { mutableStateOf(pointNames.getOrElse(0) { "S" }) }
    var pl2 by remember(pointNames) { mutableStateOf(pointNames.getOrElse(1) { pointNames.firstOrNull() ?: "A" }) }
    var pl3 by remember(pointNames) { mutableStateOf(pointNames.getOrElse(2) { pointNames.firstOrNull() ?: "B" }) }
    var pl4 by remember(pointNames) { mutableStateOf("") }

    val colorChoices = remember {
        listOf(
            "#38bdf8" to "Xanh dương",
            "#f43f5e" to "Đỏ hồng",
            "#10b981" to "Xanh lá",
            "#fbbf24" to "Vàng cam",
            "#e879f9" to "Tím hồng"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Section 1: Point on segment
        SettingBox(title = "1. Lấy thêm điểm trên đoạn thẳng", titleColor = AmberAccent) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CompactTextField(
                    label = "Tên điểm",
                    value = ptName,
                    onValueChange = { if (it.length <= 3) ptName = it },
                    modifier = Modifier.weight(0.9f)
                )
                CompactDropdown("Từ điểm", ptFrom, pointOptions, { ptFrom = it }, Modifier.weight(1f))
                CompactDropdown("Đến điểm", ptTo, pointOptions, { ptTo = it }, Modifier.weight(1f))
            }

            val ratioNote = when {
                abs(ptRatio - 0.5f) < 0.02f -> " (Trung điểm)"
                abs(ptRatio - 0.333f) < 0.02f -> " (~1/3)"
                abs(ptRatio - 0.667f) < 0.02f -> " (~2/3)"
                else -> ""
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tỉ lệ vị trí (k):", style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
                Text(
                    "${String.format(Locale.US, "%.2f", ptRatio)}$ratioNote",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = IndigoSoft
                )
            }
            Slider(
                value = ptRatio,
                onValueChange = { ptRatio = it },
                valueRange = 0.05f..0.95f,
                colors = SliderDefaults.colors(thumbColor = IndigoBright, activeTrackColor = IndigoBright, inactiveTrackColor = SlateBorder),
                modifier = Modifier.height(28.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                RatioChip("k = 1/3", Modifier.weight(1f)) { ptRatio = 1f / 3f }
                RatioChip("Trung điểm", Modifier.weight(1f)) { ptRatio = 0.5f }
                RatioChip("k = 2/3", Modifier.weight(1f)) { ptRatio = 2f / 3f }
            }
            Button(
                onClick = {
                    val err = viewModel.addCustomEdgePoint(ptName, ptFrom, ptTo, ptRatio)
                    if (err != null) viewModel.showToast(err)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("add_custom_point_btn")
            ) {
                Text("+ Thêm điểm mới", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Section 2: Segment / Vector
        SettingBox(title = "2. Kẻ đoạn thẳng / Vectơ", titleColor = CyanAccent) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactDropdown("Điểm đầu (Gốc)", lineP1, pointOptions, { lineP1 = it }, Modifier.weight(1f))
                CompactDropdown("Điểm cuối (Ngọn)", lineP2, pointOptions, { lineP2 = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactDropdown(
                    label = "Kiểu đối tượng",
                    selected = lineStyle.key,
                    options = LineStyle.entries.map { it.key to it.label },
                    onSelect = { lineStyle = LineStyle.fromKey(it) },
                    modifier = Modifier.weight(1.2f)
                )
                CompactDropdown(
                    label = "Màu sắc",
                    selected = lineColor,
                    options = colorChoices,
                    onSelect = { lineColor = it },
                    modifier = Modifier.weight(0.9f)
                )
            }
            Button(
                onClick = {
                    val err = viewModel.addCustomLine(lineP1, lineP2, lineStyle, lineColor)
                    if (err != null) viewModel.showToast(err)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("add_custom_line_btn")
            ) {
                Text("+ Vẽ đoạn thẳng / Vectơ", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Section 3: Auxiliary Plane
        SettingBox(title = "3. Tô mặt phẳng phụ (3 - 4 điểm)", titleColor = EmeraldLight) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactDropdown("Điểm 1", pl1, pointOptions, { pl1 = it }, Modifier.weight(1f))
                CompactDropdown("Điểm 2", pl2, pointOptions, { pl2 = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactDropdown("Điểm 3", pl3, pointOptions, { pl3 = it }, Modifier.weight(1f))
                CompactDropdown("Điểm 4 (Tuỳ chọn)", pl4, optionalOptions, { pl4 = it }, Modifier.weight(1f))
            }
            Button(
                onClick = {
                    val err = viewModel.addCustomPlane(pl1, pl2, pl3, pl4)
                    if (err != null) viewModel.showToast(err)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("add_custom_plane_btn")
            ) {
                Text("+ Tô mặt phẳng", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Section 4: Created elements list
        val snap = uiState.snapshot
        val totalItems = snap.customPoints.size + snap.customXYZPoints.size + snap.customLines.size + snap.customPlanes.size
        SettingBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CÁC YẾU TỐ ĐÃ DỰNG THÊM", style = MaterialTheme.typography.labelSmall, color = IndigoLight)
                if (totalItems > 0 || snap.vertexOverrides.isNotEmpty()) {
                    Text(
                        text = "Xoá tất cả",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { viewModel.clearAllCustom() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (totalItems == 0) {
                Text(
                    text = "Chưa có đường, vectơ hoặc điểm kẻ thêm",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    snap.customPoints.forEach { p ->
                        CustomElementRow(
                            dotColor = AmberAccent,
                            text = "Điểm ${p.name} ∈ ${p.from}${p.to} (k=${String.format(Locale.US, "%.2f", p.ratio)})",
                            onDelete = { viewModel.removeCustomPoint(p.id) }
                        )
                    }
                    snap.customXYZPoints.forEach { p ->
                        CustomElementRow(
                            dotColor = AmberDeep,
                            text = "Điểm ${p.name}(${formatMathNumber(p.x)}; ${formatMathNumber(p.y)}; ${formatMathNumber(p.z)})",
                            onDelete = { viewModel.removeCustomXYZ(p.id) }
                        )
                    }
                    snap.customLines.forEach { l ->
                        val desc = if (l.style.isVector) {
                            "${if (l.style.isDashed) "Vectơ đứt" else "Vectơ"} ${l.p1}${l.p2}→"
                        } else {
                            "Đoạn ${l.p1}${l.p2} (${if (l.style.isDashed) "Nét đứt" else "Nét liền"})"
                        }
                        CustomElementRow(
                            dotColor = Color(GeometryRenderer.parseHexColor(l.colorHex, 0xFF38BDF8.toInt())),
                            text = desc,
                            onDelete = { viewModel.removeCustomLine(l.id) }
                        )
                    }
                    snap.customPlanes.forEach { pl ->
                        CustomElementRow(
                            dotColor = EmeraldAccent,
                            text = "Mặt phẳng (${pl.pts.joinToString("")})",
                            onDelete = { viewModel.removeCustomPlane(pl.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CoordsPane(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    val snap = uiState.snapshot
    val reg = uiState.scene.pointRegistry
    val originWorld = ShapeBuilder.getOriginWorld(reg, snap.originPoint)
    val pointNames = remember(reg) { reg.keys.toList() }
    val originOptions = remember(pointNames) { pointNames.map { it to "Điểm $it (0; 0; 0)" } }
    val pointOptions = remember(pointNames) { pointNames.map { it to it } }

    val suggestedName = remember(reg) { ShapeBuilder.nextAutoPointName(reg) }
    var xyzName by remember(suggestedName) { mutableStateOf(suggestedName) }
    var xyzX by remember { mutableStateOf("1") }
    var xyzY by remember { mutableStateOf("1") }
    var xyzZ by remember { mutableStateOf("2") }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Oxyz System Toggle Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BlueAxis.copy(alpha = 0.22f))
                .border(1.dp, BlueAxis, RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Hệ trục tọa độ Oxyz", style = MaterialTheme.typography.labelLarge, color = CyanAccent)
                Text("Hiển thị trục Ox, Oy, Oz trên hình 3D", style = MaterialTheme.typography.bodySmall, color = SlateTextLight, fontSize = 10.5.sp)
            }
            Box(
                modifier = Modifier
                    .testTag("toggle_axes_switch_btn")
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (snap.showAxes) BlueAxis else SlateBorder)
                    .border(1.dp, if (snap.showAxes) CyanAccent else SlateMuted, RoundedCornerShape(20.dp))
                    .clickable { viewModel.toggleAxes() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (snap.showAxes) "ĐANG BẬT" else "ĐANG TẮT",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextPrimary
                )
            }
        }

        // 1. Origin selector
        SettingBox(title = "1. Chọn điểm làm Gốc tọa độ (0; 0; 0)", titleColor = RoseLight) {
            CompactDropdown(
                label = "Đặt gốc tại điểm:",
                selected = snap.originPoint,
                options = originOptions,
                onSelect = { viewModel.changeOrigin(it) }
            )
            SettingCheckRow("Hiện tọa độ trên nhãn điểm", snap.showCoordsOnLabels) {
                viewModel.setShowCoordsOnLabels(it)
            }
        }

        // 2. Point & Vector Calculator
        SettingBox(title = "2. Tính tọa độ Điểm & Vectơ", titleColor = CyanAccent) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactDropdown("Điểm đầu (A)", uiState.calcPointA, pointOptions, { viewModel.setCalcPoints(it, uiState.calcPointB) }, Modifier.weight(1f))
                CompactDropdown("Điểm cuối (B)", uiState.calcPointB, pointOptions, { viewModel.setCalcPoints(uiState.calcPointA, it) }, Modifier.weight(1f))
            }

            val vA = reg[uiState.calcPointA]
            val vB = reg[uiState.calcPointB]
            if (vA != null && vB != null) {
                val cA = ShapeBuilder.worldToMath(vA, originWorld)
                val cB = ShapeBuilder.worldToMath(vB, originWorld)
                val vx = cB.x - cA.x
                val vy = cB.y - cA.y
                val vz = cB.z - cA.z
                val len = sqrt(vx * vx + vy * vy + vz * vz)
                val mx = (cA.x + cB.x) * 0.5f
                val my = (cA.y + cB.y) * 0.5f
                val mz = (cA.z + cB.z) * 0.5f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateDeepest)
                        .border(1.dp, SlateSurface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalcResultRow("Tọa độ ${uiState.calcPointA}:", "(${formatMathNumber(cA.x)}; ${formatMathNumber(cA.y)}; ${formatMathNumber(cA.z)})", CyanAccent)
                    CalcResultRow("Tọa độ ${uiState.calcPointB}:", "(${formatMathNumber(cB.x)}; ${formatMathNumber(cB.y)}; ${formatMathNumber(cB.z)})", CyanAccent)
                    CalcResultRow("Vectơ ${uiState.calcPointA}${uiState.calcPointB}→:", "(${formatMathNumber(vx)}; ${formatMathNumber(vy)}; ${formatMathNumber(vz)})", AmberAccent)
                    CalcResultRow("Độ dài |${uiState.calcPointA}${uiState.calcPointB}→|:", String.format(Locale.US, "%.2f", len), EmeraldLight)
                    CalcResultRow("Trung điểm I:", "(${formatMathNumber(mx)}; ${formatMathNumber(my)}; ${formatMathNumber(mz)})", PurpleAccent)
                }
            }

            Button(
                onClick = {
                    val err = viewModel.addCustomLine(uiState.calcPointA, uiState.calcPointB, LineStyle.VECTOR_SOLID, "#f43f5e")
                    if (err != null) viewModel.showToast(err)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("draw_calc_vector_btn")
            ) {
                Icon(Icons.Default.NorthEast, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Vẽ Vectơ này lên hình 3D", style = MaterialTheme.typography.labelMedium)
            }
        }

        // 3. Coordinates Table
        SettingBox(title = "3. Bảng tọa độ các điểm", titleColor = EmeraldLight) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateDeepest)
                    .border(1.dp, SlateSurface, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateSurface)
                        .padding(vertical = 5.dp, horizontal = 6.dp)
                ) {
                    Text("Điểm", style = MaterialTheme.typography.labelSmall, color = SlateTextLight, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center)
                    Text("x", style = MaterialTheme.typography.labelSmall, color = RoseLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("y", style = MaterialTheme.typography.labelSmall, color = EmeraldLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("z", style = MaterialTheme.typography.labelSmall, color = CyanAccent, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
                reg.forEach { (name, pos) ->
                    val c = ShapeBuilder.worldToMath(pos, originWorld)
                    val isOrigin = name == snap.originPoint
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isOrigin) RoseAccent.copy(alpha = 0.2f) else Color.Transparent)
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = if (isOrigin) "$name (Gốc)" else name,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = if (isOrigin) RoseLight else SlateTextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.Center
                        )
                        Text(formatMathNumber(c.x), style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = SlateTextLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(formatMathNumber(c.y), style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = SlateTextLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(formatMathNumber(c.z), style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = SlateTextLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                    HorizontalDivider(color = SlateSurface, thickness = 0.7.dp)
                }
            }
        }

        // 4. Add point by (x; y; z)
        SettingBox(title = "4. Chấm điểm theo tọa độ (x; y; z)", titleColor = AmberAccent) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                CompactTextField("Tên", xyzName, { if (it.length <= 3) xyzName = it }, Modifier.weight(1f))
                CompactTextField("x", xyzX, { xyzX = it }, Modifier.weight(1f), isNumeric = true)
                CompactTextField("y", xyzY, { xyzY = it }, Modifier.weight(1f), isNumeric = true)
                CompactTextField("z", xyzZ, { xyzZ = it }, Modifier.weight(1f), isNumeric = true)
            }
            Button(
                onClick = {
                    val x = xyzX.replace(',', '.').toFloatOrNull() ?: 0f
                    val y = xyzY.replace(',', '.').toFloatOrNull() ?: 0f
                    val z = xyzZ.replace(',', '.').toFloatOrNull() ?: 0f
                    val err = viewModel.addCustomXYZPoint(xyzName, x, y, z)
                    if (err != null) viewModel.showToast(err)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("add_xyz_point_btn")
            ) {
                Text("+ Thêm điểm (x; y; z)", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RatioChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SlateSurface)
            .border(1.dp, SlateMuted, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateTextLight)
    }
}

@Composable
private fun CustomElementRow(dotColor: Color, text: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(SlateDark)
            .border(1.dp, SlateSurface, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Text(text, style = MaterialTheme.typography.bodySmall, color = SlateTextPrimary)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Xóa $text", tint = RoseAccent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CalcResultRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CompactTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SlateTextSecondary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = SlateTextPrimary,
                fontFamily = if (isNumeric) MonoFontFamily else BodyFontFamily
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Decimal else KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SlateDark,
                unfocusedContainerColor = SlateDark,
                focusedBorderColor = IndigoBright,
                unfocusedBorderColor = SlateMuted,
                cursorColor = CyanAccent
            ),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        )
    }
}
