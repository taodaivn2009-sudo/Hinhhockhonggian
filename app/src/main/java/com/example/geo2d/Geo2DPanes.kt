package com.example.geo2d

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.panes.SettingBox
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.hypot

@Composable
fun Geo2DFunctionsPane(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel
) {
    var draftExpr by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FunctionCategory.EXP_LOG) }
    val draftCheck = remember(draftExpr) {
        if (draftExpr.isBlank()) null else MathExprParser.parse(draftExpr)
    }

    val quickSymbols = listOf(
        "x", "^2", "^3", "^", "e^", "2^x", "ln(", "log(", "log2(", "log(3; ",
        "sqrt(", "sin(", "cos(", "tan(", "abs(", "pi", "(", ")"
    )
    val categoryTemplates = remember(selectedCategory) {
        FunctionLibrary.byCategory(selectedCategory)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Add New Arbitrary Function Card
        SettingBox(
            title = "Nhập hàm số y = f(x) bất kỳ",
            titleColor = CyanAccent,
            borderColor = SkyBlue.copy(alpha = 0.65f),
            containerColor = SkyBlue.copy(alpha = 0.1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draftExpr,
                    onValueChange = { draftExpr = it },
                    placeholder = { Text("VD: e^x, log(2; x), x^3 - 3x...", color = SlateTextSecondary, fontSize = 12.sp) },
                    prefix = { Text("y = ", color = CyanAccent, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MonoFontFamily,
                        color = SlateTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SlateDark,
                        unfocusedContainerColor = SlateDark,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SlateMuted
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("geo2d_func_input")
                )
                Button(
                    onClick = {
                        viewModel.addFunction(draftExpr)
                        if (draftCheck is MathExprParser.ParseResult.Ok) draftExpr = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("geo2d_add_func_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Vẽ hàm số", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Vẽ", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (draftCheck is MathExprParser.ParseResult.Error) {
                Text(
                    text = draftCheck.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoseLight
                )
            }

            // Quick math symbol keypad (including exp & log)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                quickSymbols.forEach { sym ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(SlateDark)
                            .border(1.dp, SlateBorder, RoundedCornerShape(7.dp))
                            .clickable { draftExpr += sym }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = sym.trim(),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = IndigoSoft
                        )
                    }
                }
            }
        }

        // 2. Categorized Function Library (Mũ & Loga, Đa thức, Phân thức & Căn, Lượng giác, Đặc biệt)
        SettingBox(
            title = "Thư viện hàm số theo nhóm (chạm để vẽ)",
            titleColor = EmeraldAccent,
            borderColor = EmeraldAccent.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FunctionCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) EmeraldDark else SlateDeepest)
                            .border(
                                1.dp,
                                if (isSelected) EmeraldAccent else SlateBorder,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("geo2d_fn_cat_${cat.name.lowercase()}")
                    ) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color.White else SlateTextLight,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryTemplates.chunked(2).forEach { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pair.forEach { tpl ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(SlateDeepest)
                                    .border(1.dp, SlateMuted.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                                    .clickable { viewModel.addFunction(tpl.expr) }
                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "y = ${tpl.expr}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                    color = CyanAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tpl.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // 3. Active Functions List with independent Graph Toggle & Derivative Button
        SettingBox(
            title = "Danh sách đồ thị (${uiState.snapshot.functions.size})",
            titleColor = IndigoSoft
        ) {
            if (uiState.snapshot.functions.isEmpty()) {
                Text(
                    text = "Chưa có hàm số nào. Nhập công thức ở trên hoặc chọn từ thư viện nhóm hàm để vẽ ngay!",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )
            } else {
                uiState.snapshot.functions.forEach { fn ->
                    val parsed = remember(fn.expression) { MathExprParser.parse(fn.expression) }
                    val derived = remember(fn.expression) { MathExprParser.derive(fn.expression) }
                    val fnColor = remember(fn.colorHex) {
                        try {
                            Color(AndroidColor.parseColor(fn.colorHex))
                        } catch (_: Exception) {
                            CyanAccent
                        }
                    }
                    val anyCurveVisible = fn.visible || fn.showDerivative

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateDeepest)
                            .border(
                                1.dp,
                                if (anyCurveVisible) fnColor.copy(alpha = 0.55f) else SlateBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // Row 1: Color swatch, label, expression input, delete button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (fn.visible) fnColor else fnColor.copy(alpha = 0.35f))
                                    .border(1.5.dp, Color.White, CircleShape)
                                    .clickable { viewModel.cycleFunctionColor(fn.id) }
                            )
                            Text(
                                text = "${fn.label} =",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                color = fnColor,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = fn.expression,
                                onValueChange = { viewModel.updateFunctionExpression(fn.id, it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = MonoFontFamily,
                                    color = SlateTextPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark,
                                    focusedBorderColor = fnColor,
                                    unfocusedBorderColor = SlateBorder
                                ),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.deleteFunction(fn.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Xóa hàm ${fn.label}",
                                    tint = RoseLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Row 2: Independent Graph f(x) Toggle + Derivative f'(x) Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (fn.visible) fnColor.copy(alpha = 0.2f) else SlateSurface)
                                    .border(
                                        1.dp,
                                        if (fn.visible) fnColor.copy(alpha = 0.75f) else SlateBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.toggleFunctionVisibility(fn.id) }
                                    .padding(horizontal = 9.dp, vertical = 7.dp)
                                    .testTag("geo2d_toggle_fn_${fn.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (fn.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Bật/tắt đồ thị ${fn.label}",
                                    tint = if (fn.visible) fnColor else SlateTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (fn.visible) "Đang bật đồ thị" else "Đang tắt đồ thị",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fn.visible) SlateTextPrimary else SlateTextSecondary,
                                    fontWeight = if (fn.visible) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (fn.showDerivative) AmberAccent.copy(alpha = 0.22f) else SlateSurface)
                                    .border(
                                        1.dp,
                                        if (fn.showDerivative) AmberAccent else SlateBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.toggleFunctionDerivative(fn.id) }
                                    .padding(horizontal = 9.dp, vertical = 7.dp)
                                    .testTag("geo2d_toggle_deriv_${fn.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = "Bật/tắt đạo hàm ${fn.label}",
                                    tint = if (fn.showDerivative) AmberAccent else SlateTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (fn.showDerivative) "Đạo hàm f'(x)" else "Đạo hàm f'(x)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fn.showDerivative) AmberAccent else SlateTextLight,
                                    fontWeight = if (fn.showDerivative) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Symbolic Derivative Formula Strip (when derivative is ON)
                        if (fn.showDerivative && parsed is MathExprParser.ParseResult.Ok) {
                            val derivLabel = fn.label.replace("(x)", "'(x)")
                            val formulaText = derived?.second ?: "Tính đạo hàm số"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AmberAccent.copy(alpha = 0.11f))
                                    .border(1.dp, AmberAccent.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "- -",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = AmberAccent,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "$derivLabel = $formulaText",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = SlateTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (parsed is MathExprParser.ParseResult.Error) {
                            Text(
                                text = parsed.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = RoseLight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Geo2DPresetsPane(
    viewModel: Geo2DViewModel
) {
    var ptName by remember { mutableStateOf("") }
    var ptX by remember { mutableStateOf("2") }
    var ptY by remember { mutableStateOf("3") }

    var cirX by remember { mutableStateOf("0") }
    var cirY by remember { mutableStateOf("0") }
    var cirR by remember { mutableStateOf("3") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. 2D Classic Geometry Presets
        SettingBox(title = "Dựng nhanh hình phẳng 2D mẫu", titleColor = IndigoSoft) {
            val presets = Geo2DPreset.entries
            presets.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { preset ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SlateDeepest)
                                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.applyPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 2. Add Point by Exact Coordinates
        SettingBox(title = "Thêm điểm theo tọa độ Oxy", titleColor = CyanAccent) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactCoordInput("Tên", ptName, { ptName = it }, Modifier.width(62.dp), isNumeric = false)
                CompactCoordInput("x", ptX, { ptX = it }, Modifier.weight(1f))
                CompactCoordInput("y", ptY, { ptY = it }, Modifier.weight(1f))
                Button(
                    onClick = {
                        val x = ptX.replace(",", ".").toFloatOrNull()
                        val y = ptY.replace(",", ".").toFloatOrNull()
                        if (x != null && y != null) {
                            viewModel.addManualPoint(ptName, x, y)
                            ptName = ""
                        } else {
                            viewModel.showToast("Tọa độ x, y chưa hợp lệ")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Thêm", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Add Circle by Center & Radius
        SettingBox(title = "Vẽ đường tròn tâm I(a; b), bán kính R", titleColor = EmeraldAccent) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactCoordInput("a", cirX, { cirX = it }, Modifier.weight(1f))
                CompactCoordInput("b", cirY, { cirY = it }, Modifier.weight(1f))
                CompactCoordInput("R", cirR, { cirR = it }, Modifier.weight(1f))
                Button(
                    onClick = {
                        val cx = cirX.replace(",", ".").toFloatOrNull()
                        val cy = cirY.replace(",", ".").toFloatOrNull()
                        val r = cirR.replace(",", ".").toFloatOrNull()
                        if (cx != null && cy != null && r != null) {
                            viewModel.addManualCircle(cx, cy, r)
                        } else {
                            viewModel.showToast("Thông số đường tròn chưa hợp lệ")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Vẽ", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CompactCoordInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 10.5.sp) },
        singleLine = true,
        keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, color = SlateTextPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SlateDark,
            unfocusedContainerColor = SlateDark,
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = SlateMuted
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}

@Composable
fun Geo2DObjectsPane(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel
) {
    val snap = uiState.snapshot
    val ptMap = remember(snap.points) { snap.points.associateBy { it.id } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Points list
        SettingBox(title = "Các điểm trên mặt phẳng (${snap.points.size})", titleColor = CyanAccent) {
            if (snap.points.isEmpty()) {
                Text("Chưa có điểm nào. Dùng công cụ Điểm hoặc Hình mẫu để thêm.", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
            } else {
                snap.points.forEach { pt ->
                    ObjectRowItem(
                        title = "Điểm ${pt.name}",
                        detail = "(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})",
                        accent = CyanAccent,
                        onDelete = { viewModel.deletePoint(pt.id) }
                    )
                }
            }
        }

        // Objects list with live measurements & equations
        SettingBox(title = "Hình vẽ & Đo đạc (${snap.objects.size})", titleColor = IndigoSoft) {
            if (snap.objects.isEmpty()) {
                Text("Chưa có đoạn thẳng, đường thẳng, đường tròn hay đa giác nào.", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
            } else {
                snap.objects.forEach { obj ->
                    val (title, detail, accent) = when (obj) {
                        is Geo2DObject.Segment -> {
                            val a = ptMap[obj.p1Id]; val b = ptMap[obj.p2Id]
                            val d = if (a != null && b != null) hypot(b.x - a.x, b.y - a.y) else 0f
                            Triple("Đoạn ${a?.name ?: "?"}${b?.name ?: "?"}", "Độ dài = ${Geo2DMathHelpers.formatNum(d)}", SlateTextPrimary)
                        }
                        is Geo2DObject.Line -> {
                            val a = ptMap[obj.p1Id]; val b = ptMap[obj.p2Id]
                            val eq = if (a != null && b != null) Geo2DMathHelpers.lineEquationString(Vec2(a.x, a.y), Vec2(b.x, b.y)) else "—"
                            Triple("Đường ${a?.name ?: "?"}${b?.name ?: "?"}", eq, CyanAccent)
                        }
                        is Geo2DObject.Ray -> {
                            val a = ptMap[obj.startId]; val b = ptMap[obj.throughId]
                            Triple("Tia ${a?.name ?: "?"}${b?.name ?: "?"}", "Gốc ${a?.name ?: "?"}", IndigoSoft)
                        }
                        is Geo2DObject.VectorObj -> {
                            val a = ptMap[obj.fromId]; val b = ptMap[obj.toId]
                            val dx = if (a != null && b != null) b.x - a.x else 0f
                            val dy = if (a != null && b != null) b.y - a.y else 0f
                            val len = hypot(dx, dy)
                            Triple(
                                "Vectơ ${a?.name ?: "?"}${b?.name ?: "?"}",
                                "(${Geo2DMathHelpers.formatNum(dx)}; ${Geo2DMathHelpers.formatNum(dy)}) • |u| = ${Geo2DMathHelpers.formatNum(len)}",
                                RoseLight
                            )
                        }
                        is Geo2DObject.Circle -> {
                            val c = ptMap[obj.centerId]
                            val e = obj.edgePointId?.let { ptMap[it] }
                            val r = when {
                                c != null && e != null -> hypot(e.x - c.x, e.y - c.y)
                                obj.fixedRadius != null -> obj.fixedRadius
                                else -> 0f
                            }
                            val eq = if (c != null) Geo2DMathHelpers.circleEquationString(Vec2(c.x, c.y), r) else "—"
                            val area = (PI * r * r).toFloat()
                            Triple(
                                "Đường tròn (${c?.name ?: "I"}, R=${Geo2DMathHelpers.formatNum(r)})",
                                "$eq • S = ${Geo2DMathHelpers.formatNum(area)}",
                                EmeraldAccent
                            )
                        }
                        is Geo2DObject.Polygon -> {
                            val pts = obj.pointIds.mapNotNull { ptMap[it] }
                            val name = pts.joinToString("") { it.name }
                            val vecs = pts.map { Vec2(it.x, it.y) }
                            val p = Geo2DMathHelpers.polygonPerimeter(vecs)
                            val s = Geo2DMathHelpers.polygonArea(vecs)
                            Triple(
                                "Đa giác $name",
                                "Chu vi P = ${Geo2DMathHelpers.formatNum(p)} • Diện tích S = ${Geo2DMathHelpers.formatNum(s)}",
                                IndigoSoft
                            )
                        }
                        is Geo2DObject.AngleMeasure -> {
                            val a = ptMap[obj.pAId]; val v = ptMap[obj.vertexId]; val c = ptMap[obj.pCId]
                            val deg = if (a != null && v != null && c != null) {
                                Geo2DMathHelpers.angleDegrees(Vec2(a.x, a.y), Vec2(v.x, v.y), Vec2(c.x, c.y))
                            } else 0f
                            Triple("Góc ∠${a?.name ?: "?"}${v?.name ?: "?"}${c?.name ?: "?"}", "${Geo2DMathHelpers.formatNum(deg)}°", AmberAccent)
                        }
                    }
                    ObjectRowItem(title = title, detail = detail, accent = accent, onDelete = { viewModel.deleteObject(obj.id) })
                }
            }
        }
    }
}

@Composable
private fun ObjectRowItem(title: String, detail: String, accent: Color, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateDeepest)
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = SlateTextLight, fontSize = 11.sp)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Xóa $title", tint = RoseLight, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun Geo2DSettingsPane(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel
) {
    val s = uiState.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingBox(title = "Hiển thị hệ trục Oxy & Đồ thị", titleColor = CyanAccent) {
            Geo2DSwitchRow("Hệ trục tọa độ Oxy", s.showAxes) { viewModel.toggleAxes() }
            Geo2DSwitchRow("Lưới tọa độ tự động", s.showGrid) { viewModel.toggleGrid() }
            Geo2DSwitchRow("Hút lưới khi chấm/kéo điểm", s.snapToGrid) { viewModel.toggleSnap() }
            Geo2DSwitchRow("Hiển thị tọa độ cạnh tên điểm", s.showPointCoords) { viewModel.togglePointCoords() }
            Geo2DSwitchRow("Hiển thị độ dài, diện tích & PT", s.showMeasurements) { viewModel.toggleMeasurements() }
            Geo2DSwitchRow("Đánh dấu nghiệm, cực trị, giao điểm", s.showKeyGraphPoints) { viewModel.toggleKeyGraphPoints() }
            Geo2DSwitchRow("Vẽ tiếp tuyến khi dò đồ thị", s.showTangentOnTrace) { viewModel.toggleTangentOnTrace() }
        }

        SettingBox(title = "Độ nhạy thao tác 2D", titleColor = IndigoSoft) {
            Geo2DSliderRow("Tốc độ trượt mặt phẳng", s.sensitivity.pan, 0.4f..2.2f) {
                viewModel.updateSensitivity(pan = it)
            }
            Geo2DSliderRow("Tốc độ thu phóng", s.sensitivity.zoom, 0.4f..2.2f) {
                viewModel.updateSensitivity(zoom = it)
            }
            Geo2DSliderRow("Vùng bắt điểm chạm", s.sensitivity.touchRadius, 0.7f..1.7f) {
                viewModel.updateSensitivity(touchRadius = it)
            }
        }

        Button(
            onClick = { viewModel.clearAllGeometryAndGraphs() },
            colors = ButtonDefaults.buttonColors(containerColor = RoseAccent.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Xóa toàn bộ hình & đồ thị 2D", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Geo2DSwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SlateTextLight, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun Geo2DSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SlateTextLight)
            Text(
                text = String.format(java.util.Locale.US, "%.1f×", value),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = CyanAccent
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun Geo2DSaveExportModal(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    onPickExportFolderClick: () -> Unit,
    onExportPngClick: (forcePicker: Boolean) -> Unit,
    onExportJsonClick: (forcePicker: Boolean) -> Unit,
    onImportJsonClick: () -> Unit
) {
    if (!uiState.saveModalOpen) return
    val hasPinnedFolder = !uiState.exportFolderUri.isNullOrBlank()

    Dialog(
        onDismissRequest = { viewModel.closeSaveModal() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark)
                .border(1.dp, SlateMuted, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurface)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(19.dp))
                    Text("Lưu & Xuất File 2D / Đồ Thị", style = MaterialTheme.typography.titleSmall, color = SlateTextPrimary)
                }
                IconButton(onClick = { viewModel.closeSaveModal() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = SlateTextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingBox(title = "1. Tên File & Vị Trí Lưu", titleColor = CyanAccent) {
                    OutlinedTextField(
                        value = uiState.customFileName,
                        onValueChange = { viewModel.updateCustomFileName(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = SlateTextPrimary, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SlateDark,
                            unfocusedContainerColor = SlateDark,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = SlateMuted
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateDeepest)
                            .border(1.dp, if (hasPinnedFolder) EmeraldAccent.copy(alpha = 0.5f) else SkyBlue.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.exportFolderLabel ?: "Chế độ: Chọn vị trí thư mục mỗi khi bấm lưu",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPinnedFolder) EmeraldAccent else CyanAccent
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onPickExportFolderClick,
                                colors = ButtonDefaults.buttonColors(containerColor = if (hasPinnedFolder) SlateMuted else SkyBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (hasPinnedFolder) "Đổi thư mục" else "Chọn thư mục lưu", style = MaterialTheme.typography.labelMedium)
                            }
                            if (hasPinnedFolder) {
                                OutlinedButton(
                                    onClick = { viewModel.clearExportFolder() },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SlateMuted),
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text("Hỏi mỗi lần", style = MaterialTheme.typography.labelSmall, color = SlateTextLight)
                                }
                            }
                        }
                    }
                }

                SettingBox(title = "2. Xuất & Mở File Ngoài", titleColor = EmeraldAccent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onExportPngClick(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lưu Ảnh .PNG", style = MaterialTheme.typography.labelLarge)
                        }
                        if (hasPinnedFolder) {
                            IconButton(
                                onClick = { onExportPngClick(true) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SlateSurface)
                                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Chọn vị trí khác cho PNG", tint = SlateTextLight)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onExportJsonClick(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lưu File 2D .JSON", style = MaterialTheme.typography.labelLarge)
                        }
                        if (hasPinnedFolder) {
                            IconButton(
                                onClick = { onExportJsonClick(true) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SlateSurface)
                                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Chọn vị trí khác cho JSON", tint = SlateTextLight)
                            }
                        }
                    }

                    Button(
                        onClick = onImportJsonClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateMuted),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mở file 2D .JSON từ máy", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
