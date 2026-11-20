package com.example.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.*
import com.example.geo2d.*
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Evaluates student-typed math answers like "4*pi/3", "sqrt(3)/2", "0,5", "60", or "12.5".
 */
object VerifyAnswerEvaluator {
    fun evaluate(rawInput: String): Double? {
        val normalized = rawInput.trim()
            .replace(',', '.')
            .replace("°", "")
            .replace("π", "pi")
            .replace("√", "sqrt")
        if (normalized.isEmpty()) return null
        val direct = normalized.toDoubleOrNull()
        if (direct != null && direct.isFinite()) return direct
        return when (val res = MathExprParser.parse(normalized)) {
            is MathExprParser.ParseResult.Ok -> {
                val v = res.expr.eval(0.0, 0.0)
                if (v.isFinite()) v else null
            }
            is MathExprParser.ParseResult.Error -> null
        }
    }

    fun isCorrect(expected: Double, userValue: Double, relTol: Double = 0.015, absTol: Double = 0.03): Boolean {
        if (!expected.isFinite() || !userValue.isFinite()) return false
        val diff = abs(expected - userValue)
        val tol = max(absTol, abs(expected) * relTol)
        return diff <= tol
    }
}

private enum class VerifyTarget3D(val label: String, val unit: String) {
    VOLUME("Thể tích V", ""),
    STP("S toàn phần", ""),
    SDAY("S đáy", ""),
    HEIGHT("Chiều cao h", ""),
    DIST_AB("Đoạn AB", ""),
    ANGLE_LP("Góc (AB,(P))", "°"),
    DIST_AP("d(A, (P))", ""),
    DYN_SEC("S thiết diện", "")
}

@Composable
fun VerifyModeCard3D(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    var selectedTarget by remember { mutableStateOf(VerifyTarget3D.VOLUME) }
    var answerInput by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var revealAnswer by remember { mutableStateOf(false) }

    val reg = uiState.scene.pointRegistry
    val origin = ShapeBuilder.getOriginWorld(reg, uiState.snapshot.originPoint)
    val metrics = uiState.scene.metrics

    val expectedValue: Double? = remember(
        selectedTarget, metrics, reg, uiState.calcPointA, uiState.calcPointB,
        uiState.calcPlaneP1, uiState.calcPlaneP2, uiState.calcPlaneP3, uiState.scene.dynamicSectionArea
    ) {
        when (selectedTarget) {
            VerifyTarget3D.VOLUME -> metrics.vol.toDouble()
            VerifyTarget3D.STP -> metrics.sTp.toDouble()
            VerifyTarget3D.SDAY -> metrics.sDay.toDouble()
            VerifyTarget3D.HEIGHT -> metrics.realH.toDouble()
            VerifyTarget3D.DIST_AB -> {
                val a = reg[uiState.calcPointA]
                val b = reg[uiState.calcPointB]
                if (a != null && b != null) a.distanceTo(b).toDouble() else null
            }
            VerifyTarget3D.ANGLE_LP, VerifyTarget3D.DIST_AP -> {
                val a = reg[uiState.calcPointA]
                val b = reg[uiState.calcPointB]
                val p1 = reg[uiState.calcPlaneP1]
                val p2 = reg[uiState.calcPlaneP2]
                val p3 = reg[uiState.calcPlaneP3]
                if (a != null && b != null && p1 != null && p2 != null && p3 != null) {
                    val analysis = Geo3DMathHelpers.analyzeLineAndPlane(a, b, p1, p2, p3, origin)
                    if (selectedTarget == VerifyTarget3D.ANGLE_LP) analysis?.angleDegrees?.toDouble()
                    else analysis?.distA?.toDouble()
                } else null
            }
            VerifyTarget3D.DYN_SEC -> uiState.scene.dynamicSectionArea?.toDouble()
        }
    }

    LaunchedEffect(selectedTarget, uiState.snapshot.shape, uiState.snapshot.a, uiState.snapshot.b, uiState.snapshot.h) {
        checkResult = null
        revealAnswer = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1629), RoundedCornerShape(12.dp))
            .border(1.dp, EmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                Text(
                    "CHẾ ĐỘ KIỂM TRA ĐÁP ÁN",
                    color = EmeraldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.verifyHideAnswers) AmberAccent.copy(alpha = 0.22f) else SlateSurface)
                    .border(1.dp, if (uiState.verifyHideAnswers) AmberAccent else SlateBorder, RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleVerifyHideAnswers() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("toggle_hide_answers_3d"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (uiState.verifyHideAnswers) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (uiState.verifyHideAnswers) AmberAccent else SlateTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    if (uiState.verifyHideAnswers) "Đang ẩn KQ" else "Ẩn đáp án",
                    color = if (uiState.verifyHideAnswers) AmberAccent else SlateTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            "Tự tính rồi nhập số hoặc biểu thức (vd: sqrt(3)/2, 4*pi/3, 45) để đối chiếu với mô hình 3D.",
            color = SlateTextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VerifyTarget3D.entries.forEach { target ->
                val active = selectedTarget == target
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) EmeraldAccent.copy(alpha = 0.22f) else SlateSurface)
                        .border(1.dp, if (active) EmeraldAccent else SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedTarget = target }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        target.label,
                        color = if (active) EmeraldAccent else SlateTextLight,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (expectedValue == null) {
            Text(
                when (selectedTarget) {
                    VerifyTarget3D.DYN_SEC -> "Hãy cắt một Thiết diện động ở tab Kẻ hình trước để kiểm tra diện tích."
                    else -> "Cần chọn đủ điểm/mặt phẳng hợp lệ ở tab Oxyz."
                },
                color = AmberAccent,
                fontSize = 11.sp
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = answerInput,
                    onValueChange = { answerInput = it; checkResult = null },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_answer_input_3d"),
                    singleLine = true,
                    placeholder = { Text("VD: 12.5 hoặc 4*sqrt(3)", fontSize = 11.sp, color = SlateTextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldAccent,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary,
                        focusedContainerColor = SlateDeepest,
                        unfocusedContainerColor = SlateDeepest
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
                Button(
                    onClick = {
                        val parsed = VerifyAnswerEvaluator.evaluate(answerInput)
                        if (parsed == null) {
                            checkResult = false to "Biểu thức không hợp lệ! Hãy nhập số hoặc công thức (vd: sqrt(2)/2)."
                        } else if (VerifyAnswerEvaluator.isCorrect(expectedValue, parsed)) {
                            checkResult = true to "Chính xác! Đáp án của bạn (${String.format(Locale.US, "%.2f", parsed)}${selectedTarget.unit}) khớp với mô hình."
                        } else {
                            val dir = if (parsed > expectedValue) "lớn hơn" else "nhỏ hơn"
                            checkResult = false to "Chưa đúng — kết quả bạn nhập ($dir đáp án chuẩn). Hãy kiểm tra lại công thức!"
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("verify_check_btn_3d"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Kiểm tra", color = SlateDeepest, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            checkResult?.let { (ok, msg) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ok) EmeraldAccent.copy(alpha = 0.16f) else RoseAccent.copy(alpha = 0.16f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (ok) EmeraldAccent.copy(alpha = 0.5f) else RoseAccent.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (ok) EmeraldAccent else RoseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        msg,
                        color = if (ok) Color(0xFFA7F3D0) else Color(0xFFFECDD3),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { revealAnswer = !revealAnswer },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (revealAnswer) "Đáp án chuẩn: ${String.format(Locale.US, "%.3f", expectedValue)}${selectedTarget.unit}" else "Xem đáp án chuẩn",
                        color = if (revealAnswer) CyanAccent else SlateTextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = if (revealAnswer) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }
        }
    }
}

private enum class VerifyTarget2D(val label: String, val unit: String) {
    FUNC_VAL("Giá trị f(x₀)", ""),
    DERIV_VAL("Đạo hàm f'(x₀)", ""),
    SEG_LEN("Độ dài AB", ""),
    POLY_AREA("Diện tích S", "")
}

@Composable
fun VerifyModeCard2D(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel
) {
    var selectedTarget by remember { mutableStateOf(VerifyTarget2D.FUNC_VAL) }
    var x0Input by remember { mutableStateOf("1") }
    var answerInput by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var revealAnswer by remember { mutableStateOf(false) }

    val snap = uiState.snapshot
    val ptMap = snap.points.associateBy { it.id }
    val firstFn = snap.functions.firstOrNull { it.visible && it.expression.isNotBlank() }

    val expectedValue: Double? = remember(selectedTarget, x0Input, snap.functions, snap.paramM, snap.points, snap.objects) {
        when (selectedTarget) {
            VerifyTarget2D.FUNC_VAL -> {
                val fn = firstFn ?: return@remember null
                val x0 = VerifyAnswerEvaluator.evaluate(x0Input) ?: return@remember null
                val p = MathExprParser.parse(fn.expression)
                if (p is MathExprParser.ParseResult.Ok) p.expr.eval(x0, snap.paramM).takeIf { it.isFinite() } else null
            }
            VerifyTarget2D.DERIV_VAL -> {
                val fn = firstFn ?: return@remember null
                val x0 = VerifyAnswerEvaluator.evaluate(x0Input) ?: return@remember null
                val p = MathExprParser.parse(fn.expression)
                if (p is MathExprParser.ParseResult.Ok) {
                    val sym = MathExprParser.derive(fn.expression)?.first
                    (sym?.eval(x0, snap.paramM) ?: p.expr.derivative(x0, 1e-5, snap.paramM)).takeIf { it.isFinite() }
                } else null
            }
            VerifyTarget2D.SEG_LEN -> {
                val p1 = snap.points.getOrNull(0) ?: return@remember null
                val p2 = snap.points.getOrNull(1) ?: return@remember null
                Geo2DMathHelpers.distance(Vec2(p1.x, p1.y), Vec2(p2.x, p2.y))
            }
            VerifyTarget2D.POLY_AREA -> {
                val poly = snap.objects.filterIsInstance<Geo2DObject.Polygon>().firstOrNull() ?: return@remember null
                val pts = poly.pointIds.mapNotNull { ptMap[it] }.map { Vec2(it.x, it.y) }
                if (pts.size >= 3) Geo2DMathHelpers.polygonArea(pts).toDouble() else null
            }
        }
    }

    LaunchedEffect(selectedTarget, x0Input) {
        checkResult = null
        revealAnswer = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1629), RoundedCornerShape(12.dp))
            .border(1.dp, EmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                Text("KIỂM TRA ĐÁP ÁN (VERIFY)", color = EmeraldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.verifyHideAnswers) AmberAccent.copy(alpha = 0.22f) else SlateSurface)
                    .border(1.dp, if (uiState.verifyHideAnswers) AmberAccent else SlateBorder, RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleVerifyHideAnswers() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("toggle_hide_answers_2d"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (uiState.verifyHideAnswers) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (uiState.verifyHideAnswers) AmberAccent else SlateTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    if (uiState.verifyHideAnswers) "Đang ẩn KQ" else "Ẩn đáp án",
                    color = if (uiState.verifyHideAnswers) AmberAccent else SlateTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VerifyTarget2D.entries.forEach { t ->
                val active = selectedTarget == t
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) EmeraldAccent.copy(alpha = 0.22f) else SlateSurface)
                        .border(1.dp, if (active) EmeraldAccent else SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedTarget = t }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        t.label,
                        color = if (active) EmeraldAccent else SlateTextLight,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (selectedTarget == VerifyTarget2D.FUNC_VAL || selectedTarget == VerifyTarget2D.DERIV_VAL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Tại x₀ =",
                    color = SlateTextLight,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = x0Input,
                    onValueChange = { x0Input = it },
                    modifier = Modifier.width(105.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary,
                        focusedContainerColor = SlateDeepest,
                        unfocusedContainerColor = SlateDeepest
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
                if (firstFn != null) {
                    Text(
                        "${firstFn.label} = ${firstFn.expression}",
                        color = CyanAccent,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (expectedValue == null) {
            Text(
                when (selectedTarget) {
                    VerifyTarget2D.FUNC_VAL, VerifyTarget2D.DERIV_VAL -> "Hãy nhập ít nhất 1 hàm số hợp lệ và giá trị x₀ thuộc tập xác định."
                    VerifyTarget2D.SEG_LEN -> "Cần ít nhất 2 điểm trên mặt phẳng Oxy để kiểm tra độ dài."
                    VerifyTarget2D.POLY_AREA -> "Hãy vẽ ít nhất 1 đa giác trên mặt phẳng Oxy để kiểm tra diện tích."
                },
                color = AmberAccent,
                fontSize = 11.sp
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = answerInput,
                    onValueChange = { answerInput = it; checkResult = null },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_answer_input_2d"),
                    singleLine = true,
                    placeholder = { Text("Nhập đáp án (vd: sqrt(2), -3/2)", fontSize = 11.sp, color = SlateTextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldAccent,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = SlateTextPrimary,
                        unfocusedTextColor = SlateTextPrimary,
                        focusedContainerColor = SlateDeepest,
                        unfocusedContainerColor = SlateDeepest
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
                Button(
                    onClick = {
                        val parsed = VerifyAnswerEvaluator.evaluate(answerInput)
                        if (parsed == null) {
                            checkResult = false to "Biểu thức không hợp lệ!"
                        } else if (VerifyAnswerEvaluator.isCorrect(expectedValue, parsed)) {
                            checkResult = true to "Chính xác! Đáp án của bạn khớp hoàn toàn với đồ thị/hình vẽ."
                        } else {
                            val dir = if (parsed > expectedValue) "lớn hơn" else "nhỏ hơn"
                            checkResult = false to "Chưa đúng (số bạn nhập $dir đáp án chuẩn)."
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("verify_check_btn_2d"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Kiểm tra", color = SlateDeepest, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            checkResult?.let { (ok, msg) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ok) EmeraldAccent.copy(alpha = 0.16f) else RoseAccent.copy(alpha = 0.16f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (ok) EmeraldAccent else RoseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(msg, color = if (ok) Color(0xFFA7F3D0) else Color(0xFFFECDD3), fontSize = 11.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { revealAnswer = !revealAnswer },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (revealAnswer) "Đáp án: ${Geo2DMathHelpers.formatNum(expectedValue)}${selectedTarget.unit}" else "Xem đáp án chuẩn",
                        color = if (revealAnswer) CyanAccent else SlateTextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = if (revealAnswer) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }
        }
    }
}

/**
 * Floating Step-by-Step Construction Replay Controller (shared by 3D & 2D studios).
 */
@Composable
fun ConstructionReplayBar(
    step: Int,
    totalSteps: Int,
    stepLabel: String,
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(0.92f)
            .testTag("construction_replay_bar"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF20D1426),
        border = BorderStroke(1.2.dp, CyanAccent.copy(alpha = 0.7f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(CyanAccent.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Bước $step / $totalSteps",
                            color = CyanAccent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        stepLabel,
                        color = SlateTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("replay_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Thoát tua lại", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onPrev,
                    enabled = step > 0,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("replay_prev_btn")
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Bước trước",
                        tint = if (step > 0) SlateTextPrimary else SlateMuted
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                        .clickable { onTogglePlay() }
                        .testTag("replay_play_pause_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát tiếp",
                        tint = SlateDeepest
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onNext,
                    enabled = step < totalSteps,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("replay_next_btn")
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Bước sau",
                        tint = if (step < totalSteps) SlateTextPrimary else SlateMuted
                    )
                }
            }
        }
    }
}

/**
 * Floating Parameter m Slider Overlay shown automatically on 2D canvas whenever a visible function uses `m`.
 */
@Composable
fun ParameterMSliderOverlay(
    paramM: Double,
    minVal: Double,
    maxVal: Double,
    isAnimating: Boolean,
    onValueChange: (Double) -> Unit,
    onToggleAnimate: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth(0.92f)
            .testTag("param_m_slider_overlay"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xEB0F172A),
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.65f)),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(15.dp))
                    Text(
                        "Tham số m = ${Geo2DMathHelpers.formatNum(paramM)}",
                        color = AmberAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAnimating) AmberAccent else SlateSurface)
                            .clickable { onToggleAnimate() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("param_m_play_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                if (isAnimating) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Chạy tự động m",
                                tint = if (isAnimating) SlateDeepest else AmberAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (isAnimating) "Dừng" else "Chạy m",
                                color = if (isAnimating) SlateDeepest else AmberAccent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateSurface)
                            .clickable { onReset() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("param_m_reset_btn")
                    ) {
                        Text("m=1", color = SlateTextLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Slider(
                value = paramM.toFloat().coerceIn(minVal.toFloat(), maxVal.toFloat()),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = minVal.toFloat()..maxVal.toFloat(),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("param_m_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = AmberAccent,
                    activeTrackColor = AmberAccent,
                    inactiveTrackColor = SlateBorder
                )
            )
        }
    }
}
