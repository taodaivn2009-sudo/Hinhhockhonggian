package com.example.geo2d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Automatic Vietnamese Variation Table (Bảng biến thiên) generator & renderer.
 * Analyzes domain, vertical/horizontal/oblique asymptotes, critical points (y' = 0),
 * inflection points, monotony intervals (+ / -), and limits at ±∞ / poles.
 */
object VariationTableEngine {

    enum class NodeKind {
        NEG_INF,
        POS_INF,
        CRITICAL,
        DISCONTINUITY,
        DOMAIN_BOUNDARY
    }

    enum class Monotony {
        INCREASING,
        DECREASING,
        CONSTANT,
        UNDEFINED
    }

    data class TableNode(
        val x: Double,
        val xLabel: String,
        val kind: NodeKind,
        val yPrimeLabel: String,
        val yLeftLabel: String,
        val yCenterLabel: String,
        val yRightLabel: String
    )

    data class TableInterval(
        val fromX: Double,
        val toX: Double,
        val signLabel: String,
        val monotony: Monotony
    )

    data class VariationTableData(
        val expression: String,
        val derivativeFormula: String,
        val domainText: String,
        val nodes: List<TableNode>,
        val intervals: List<TableInterval>,
        val asymptoteLines: List<String>,
        val extremaLines: List<String>,
        val inflectionLines: List<String>,
        val hasParamM: Boolean
    )

    fun analyze(expression: String, m: Double = 0.0): VariationTableData? {
        val parsed = MathExprParser.parse(expression) as? MathExprParser.ParseResult.Ok ?: return null
        val expr = parsed.expr
        val symDeriv = MathExprParser.derive(expression)?.first ?: expr.symbolicDerivative()?.simplify()
        val derivFormula = symDeriv?.toPrettyString() ?: "Tính đạo hàm số"

        val scanMin = -18.0
        val scanMax = 18.0

        val asymptotes = MathExprParser.findAsymptotes(expr, scanMin, scanMax, -40.0, 40.0, "#FBBF24", m)
        val verticalPoles = asymptotes.filterIsInstance<GraphAsymptote.Vertical>().map { it.x }.sorted()

        val keyPts = MathExprParser.analyzeFunction(expr, scanMin, scanMax, -120.0, 120.0, "#38BDF8", symDeriv, m)
        val extremaPts = keyPts.filter { it.type == KeyPointType.LOCAL_MAX || it.type == KeyPointType.LOCAL_MIN }
        val inflectionPts = keyPts.filter { it.type == KeyPointType.INFLECTION }

        // Detect domain boundaries (e.g., sqrt(x + 2) starts at x = -2, ln(x) starts at x = 0)
        val domainBoundaries = mutableListOf<Double>()
        val probeSteps = 360
        val pDx = (scanMax - scanMin) / probeSteps
        var prevDef = expr.eval(scanMin, m).isFinite()
        for (i in 1..probeSteps) {
            val curX = scanMin + i * pDx
            val curDef = expr.eval(curX, m).isFinite()
            if (curDef != prevDef) {
                var lo = curX - pDx
                var hi = curX
                repeat(24) {
                    val mid = (lo + hi) * 0.5
                    if (expr.eval(mid, m).isFinite() == prevDef) lo = mid else hi = mid
                }
                val b = snapVal((lo + hi) * 0.5)
                if (verticalPoles.none { abs(it - b) < 0.08 } && domainBoundaries.none { abs(it - b) < 0.08 }) {
                    domainBoundaries.add(b)
                }
            }
            prevDef = curDef
        }

        // Also detect stationary inflection points / roots of y' where y' = 0 even without sign change (e.g. x^3 at x=0)
        val extraCritXs = mutableListOf<Double>()
        fun evalD(x: Double): Double = symDeriv?.eval(x, m) ?: expr.derivative(x, 1e-4, m)
        for (i in 0..probeSteps) {
            val cx = scanMin + i * pDx
            val d = evalD(cx)
            val y = expr.eval(cx, m)
            if (d.isFinite() && y.isFinite() && abs(d) < 0.015) {
                val refined = snapVal(cx)
                val yR = expr.eval(refined, m)
                val dR = evalD(refined)
                if (yR.isFinite() && dR.isFinite() && abs(dR) < 1e-3 &&
                    extremaPts.none { abs(it.x - refined) < 0.12 } &&
                    verticalPoles.none { abs(it - refined) < 0.12 } &&
                    extraCritXs.none { abs(it - refined) < 0.12 }
                ) {
                    extraCritXs.add(refined)
                }
            }
        }

        // Collect all interior special x-values sorted ascending
        data class RawNode(val x: Double, val kind: NodeKind)
        val rawNodes = mutableListOf<RawNode>()
        verticalPoles.forEach { rawNodes.add(RawNode(it, NodeKind.DISCONTINUITY)) }
        domainBoundaries.forEach { rawNodes.add(RawNode(it, NodeKind.DOMAIN_BOUNDARY)) }
        extremaPts.forEach { pt ->
            val sx = snapVal(pt.x)
            if (rawNodes.none { abs(it.x - sx) < 0.08 }) rawNodes.add(RawNode(sx, NodeKind.CRITICAL))
        }
        extraCritXs.forEach { cx ->
            if (rawNodes.none { abs(it.x - cx) < 0.08 }) rawNodes.add(RawNode(cx, NodeKind.CRITICAL))
        }
        rawNodes.sortBy { it.x }

        // Keep the most relevant central nodes (max 7 interior points) so table remains crisp and readable
        val interiorNodes = if (rawNodes.size > 7) {
            rawNodes.sortedBy { abs(it.x) }.take(7).sortedBy { it.x }
        } else {
            rawNodes
        }

        // Determine if function is defined toward -∞ and +∞
        val defNegInf = expr.eval(-1e4, m).isFinite()
        val defPosInf = expr.eval(1e4, m).isFinite()

        val nodes = mutableListOf<TableNode>()

        // Left endpoint (-∞) if defined at -∞ or no domain boundary
        if (defNegInf || interiorNodes.isEmpty()) {
            val limNeg = formatLimitAtInfinity(expr, -1.0, m)
            nodes.add(
                TableNode(
                    x = Double.NEGATIVE_INFINITY,
                    xLabel = "-∞",
                    kind = NodeKind.NEG_INF,
                    yPrimeLabel = "",
                    yLeftLabel = "",
                    yCenterLabel = limNeg,
                    yRightLabel = limNeg
                )
            )
        }

        for (rn in interiorNodes) {
            val xLabel = Geo2DMathHelpers.formatNum(rn.x)
            when (rn.kind) {
                NodeKind.DISCONTINUITY -> {
                    val leftLim = formatOneSidedLimit(expr, rn.x, -1.0, m)
                    val rightLim = formatOneSidedLimit(expr, rn.x, 1.0, m)
                    nodes.add(
                        TableNode(
                            x = rn.x,
                            xLabel = xLabel,
                            kind = NodeKind.DISCONTINUITY,
                            yPrimeLabel = "||",
                            yLeftLabel = leftLim,
                            yCenterLabel = "||",
                            yRightLabel = rightLim
                        )
                    )
                }
                NodeKind.DOMAIN_BOUNDARY -> {
                    val leftDef = expr.eval(rn.x - 0.02, m).isFinite()
                    val rightDef = expr.eval(rn.x + 0.02, m).isFinite()
                    val valAt = expr.eval(rn.x, m)
                    val centerStr = if (valAt.isFinite()) Geo2DMathHelpers.formatNum(valAt) else "||"
                    val leftStr = if (leftDef) formatOneSidedLimit(expr, rn.x, -1.0, m) else "✕"
                    val rightStr = if (rightDef) formatOneSidedLimit(expr, rn.x, 1.0, m) else "✕"
                    nodes.add(
                        TableNode(
                            x = rn.x,
                            xLabel = xLabel,
                            kind = NodeKind.DOMAIN_BOUNDARY,
                            yPrimeLabel = if (valAt.isFinite()) "|" else "||",
                            yLeftLabel = leftStr,
                            yCenterLabel = centerStr,
                            yRightLabel = rightStr
                        )
                    )
                }
                NodeKind.CRITICAL -> {
                    val yVal = expr.eval(rn.x, m)
                    val yStr = if (yVal.isFinite()) Geo2DMathHelpers.formatNum(yVal) else "—"
                    val dVal = evalD(rn.x)
                    val ypStr = if (dVal.isFinite() && abs(dVal) < 0.15) "0" else "||"
                    nodes.add(
                        TableNode(
                            x = rn.x,
                            xLabel = xLabel,
                            kind = NodeKind.CRITICAL,
                            yPrimeLabel = ypStr,
                            yLeftLabel = yStr,
                            yCenterLabel = yStr,
                            yRightLabel = yStr
                        )
                    )
                }
                else -> Unit
            }
        }

        if (defPosInf || nodes.size < 2) {
            val limPos = formatLimitAtInfinity(expr, 1.0, m)
            nodes.add(
                TableNode(
                    x = Double.POSITIVE_INFINITY,
                    xLabel = "+∞",
                    kind = NodeKind.POS_INF,
                    yPrimeLabel = "",
                    yLeftLabel = limPos,
                    yCenterLabel = limPos,
                    yRightLabel = ""
                )
            )
        }

        // Build intervals between consecutive nodes
        val intervals = mutableListOf<TableInterval>()
        for (i in 0 until nodes.size - 1) {
            val a = nodes[i].x
            val b = nodes[i + 1].x
            val sampleX = when {
                a.isInfinite() && b.isInfinite() -> 0.0
                a.isInfinite() -> b - 1.5
                b.isInfinite() -> a + 1.5
                else -> (a + b) * 0.5
            }
            val ySample = expr.eval(sampleX, m)
            if (!ySample.isFinite()) {
                intervals.add(TableInterval(a, b, "////", Monotony.UNDEFINED))
            } else {
                val dSample = evalD(sampleX)
                val (signLbl, mono) = when {
                    !dSample.isFinite() -> "////" to Monotony.UNDEFINED
                    dSample > 1e-5 -> "+" to Monotony.INCREASING
                    dSample < -1e-5 -> "−" to Monotony.DECREASING
                    else -> "0" to Monotony.CONSTANT
                }
                intervals.add(TableInterval(a, b, signLbl, mono))
            }
        }

        // Build domain text
        val domainText = buildDomainDescription(defNegInf, defPosInf, verticalPoles, domainBoundaries, expr, m)

        val extremaLines = extremaPts.sortedBy { it.x }.map { pt ->
            val tag = if (pt.type == KeyPointType.LOCAL_MAX) "Cực đại" else "Cực tiểu"
            "$tag: x = ${Geo2DMathHelpers.formatNum(snapVal(pt.x))}, y = ${Geo2DMathHelpers.formatNum(snapVal(pt.y))}"
        }
        val inflectionLines = inflectionPts.sortedBy { it.x }.map { pt ->
            "Điểm uốn I(${Geo2DMathHelpers.formatNum(snapVal(pt.x))}; ${Geo2DMathHelpers.formatNum(snapVal(pt.y))})"
        }
        val asymLines = asymptotes.map { it.label }

        return VariationTableData(
            expression = expression,
            derivativeFormula = derivFormula,
            domainText = domainText,
            nodes = nodes,
            intervals = intervals,
            asymptoteLines = asymLines,
            extremaLines = extremaLines,
            inflectionLines = inflectionLines,
            hasParamM = expr.containsVarM()
        )
    }

    private fun buildDomainDescription(
        defNegInf: Boolean,
        defPosInf: Boolean,
        poles: List<Double>,
        boundaries: List<Double>,
        expr: MathExprParser.Expr,
        m: Double
    ): String {
        if (defNegInf && defPosInf && boundaries.isEmpty()) {
            return if (poles.isEmpty()) {
                "D = ℝ"
            } else {
                val list = poles.take(5).joinToString("; ") { Geo2DMathHelpers.formatNum(it) }
                "D = ℝ \\ {$list}"
            }
        }
        if (!defNegInf && defPosInf && boundaries.size == 1 && poles.isEmpty()) {
            val b = boundaries.first()
            val closed = expr.eval(b, m).isFinite()
            val br = if (closed) "[" else "("
            return "D = $br${Geo2DMathHelpers.formatNum(b)}; +∞)"
        }
        if (defNegInf && !defPosInf && boundaries.size == 1 && poles.isEmpty()) {
            val b = boundaries.first()
            val closed = expr.eval(b, m).isFinite()
            val br = if (closed) "]" else ")"
            return "D = (-∞; ${Geo2DMathHelpers.formatNum(b)}$br"
        }
        if (!defNegInf && !defPosInf && boundaries.size == 2) {
            val b1 = boundaries[0]
            val b2 = boundaries[1]
            val lBr = if (expr.eval(b1, m).isFinite()) "[" else "("
            val rBr = if (expr.eval(b2, m).isFinite()) "]" else ")"
            return "D = $lBr${Geo2DMathHelpers.formatNum(b1)}; ${Geo2DMathHelpers.formatNum(b2)}$rBr"
        }
        return if (poles.isEmpty()) "Tập xác định trên miền vẽ" else "D loại các điểm {${poles.take(4).joinToString("; ") { Geo2DMathHelpers.formatNum(it) }}}"
    }

    private fun formatLimitAtInfinity(expr: MathExprParser.Expr, sign: Double, m: Double): String {
        val y1 = expr.eval(sign * 1e3, m)
        val y2 = expr.eval(sign * 1e4, m)
        val y3 = expr.eval(sign * 1e5, m)
        if (!y2.isFinite() && !y1.isFinite()) return "✕"
        val yRef = if (y3.isFinite()) y3 else if (y2.isFinite()) y2 else y1
        if (y1.isFinite() && y2.isFinite() && y3.isFinite()) {
            if (abs(y3 - y2) < 5e-3 && abs(y3) < 1e4) {
                val yCheck = expr.eval(sign * 1e5 + 1.9, m)
                if (yCheck.isFinite() && abs(yCheck - y3) < 5e-3) {
                    return Geo2DMathHelpers.formatNum(snapVal(y3))
                }
                return "dao động"
            }
            if (abs(y3) > abs(y2) * 1.15 && abs(y3) > 15.0) {
                return if (y3 > 0) "+∞" else "-∞"
            }
        }
        return when {
            !yRef.isFinite() -> if (y1 > 0) "+∞" else "-∞"
            yRef > 500.0 -> "+∞"
            yRef < -500.0 -> "-∞"
            else -> Geo2DMathHelpers.formatNum(snapVal(yRef))
        }
    }

    private fun formatOneSidedLimit(expr: MathExprParser.Expr, x0: Double, side: Double, m: Double): String {
        val yNear = expr.eval(x0 + side * 1e-5, m)
        val yMid = expr.eval(x0 + side * 1e-3, m)
        val yFar = expr.eval(x0 + side * 1e-2, m)
        if (!yNear.isFinite() && !yMid.isFinite()) return "✕"
        val ref = if (yNear.isFinite()) yNear else yMid
        if (yMid.isFinite() && yFar.isFinite() && abs(ref) > max(abs(yFar) * 2.0, 12.0)) {
            return if (ref > 0) "+∞" else "-∞"
        }
        return if (ref.isFinite()) Geo2DMathHelpers.formatNum(snapVal(ref)) else "✕"
    }

    private fun snapVal(v: Double): Double {
        if (!v.isFinite()) return v
        val nearHalf = kotlin.math.round(v * 2.0) / 2.0
        if (abs(v - nearHalf) < 1e-3) return if (abs(nearHalf) < 1e-9) 0.0 else nearHalf
        return v
    }
}

@Composable
fun VariationTableDialog(
    fn: GraphFunction,
    paramM: Double,
    paramMMin: Double,
    paramMMax: Double,
    onChangeParamM: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val data = remember(fn.expression, paramM) {
        VariationTableEngine.analyze(fn.expression, paramM)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 18.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark)
                .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .testTag("variation_table_dialog")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateDeepest)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Bảng Biến Thiên Tự Động", style = MaterialTheme.typography.titleSmall, color = SlateTextPrimary)
                        Text(
                            text = "${fn.label} = ${fn.expression}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = CyanAccent
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).testTag("close_bbt_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng bảng biến thiên", tint = SlateTextSecondary)
                }
            }

            if (data == null) {
                Text(
                    text = "Không thể lập bảng biến thiên cho biểu thức này. Vui lòng kiểm tra cú pháp hàm số.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoseLight,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live m parameter mini-slider inside BBT dialog if function uses m
                if (data.hasParamM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndigoPrimary.copy(alpha = 0.16f))
                            .border(1.dp, IndigoBright.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tham số m (cập nhật BBT trực tiếp)", style = MaterialTheme.typography.labelSmall, color = IndigoSoft)
                            Text(
                                text = "m = ${Geo2DMathHelpers.formatNum(paramM)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                color = AmberAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = paramM.toFloat().coerceIn(paramMMin.toFloat(), paramMMax.toFloat()),
                            onValueChange = { onChangeParamM(it.toDouble()) },
                            valueRange = paramMMin.toFloat()..paramMMax.toFloat(),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                // Domain & Derivative Summary Strip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDeepest)
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("• Tập xác định: ${data.domainText}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = EmeraldLight)
                    Text("• Đạo hàm: y' = ${data.derivativeFormula}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = AmberAccent)
                }

                // Textbook 3-Row Variation Table (horizontally scrollable for many critical points)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDeepest)
                        .border(1.dp, CyanAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())
                ) {
                    VariationTableGrid(data)
                }

                // Key Findings: Extrema, Inflection Points, Asymptotes
                if (data.extremaLines.isNotEmpty() || data.inflectionLines.isNotEmpty() || data.asymptoteLines.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurface.copy(alpha = 0.55f))
                            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("NHẬN XÉT KHẢO SÁT", style = MaterialTheme.typography.labelSmall, color = IndigoSoft, fontWeight = FontWeight.Bold)
                        data.extremaLines.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = CyanAccent)
                        }
                        data.inflectionLines.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = PurpleAccent)
                        }
                        data.asymptoteLines.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = AmberAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VariationTableGrid(data: VariationTableEngine.VariationTableData) {
    val labelColW = 46.dp
    val nodeColW = 54.dp
    val intervalColW = 74.dp

    Column {
        // Row 1: x
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(38.dp)) {
            TableHeaderCell("x", labelColW)
            data.nodes.forEachIndexed { idx, node ->
                Box(modifier = Modifier.width(nodeColW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = node.xLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                if (idx < data.intervals.size) {
                    Spacer(modifier = Modifier.width(intervalColW))
                }
            }
        }
        HorizontalDivider(color = SlateMuted, thickness = 1.dp)

        // Row 2: y'
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(38.dp)) {
            TableHeaderCell("y'", labelColW)
            data.nodes.forEachIndexed { idx, node ->
                Box(modifier = Modifier.width(nodeColW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (node.yPrimeLabel == "||") {
                        DoubleVerticalBar()
                    } else if (node.yPrimeLabel.isNotEmpty()) {
                        Text(
                            text = node.yPrimeLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = AmberAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (idx < data.intervals.size) {
                    val seg = data.intervals[idx]
                    val signColor = when (seg.monotony) {
                        VariationTableEngine.Monotony.INCREASING -> EmeraldLight
                        VariationTableEngine.Monotony.DECREASING -> RoseLight
                        VariationTableEngine.Monotony.CONSTANT -> CyanAccent
                        VariationTableEngine.Monotony.UNDEFINED -> SlateTextSecondary
                    }
                    Box(modifier = Modifier.width(intervalColW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            text = seg.signLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                            color = signColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = SlateMuted, thickness = 1.dp)

        // Row 3: y (with rising/falling arrows)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(92.dp)) {
            TableHeaderCell("y", labelColW)
            data.nodes.forEachIndexed { idx, node ->
                val leftSeg = if (idx > 0) data.intervals[idx - 1] else null
                val rightSeg = if (idx < data.intervals.size) data.intervals[idx] else null

                Box(modifier = Modifier.width(nodeColW).fillMaxHeight()) {
                    if (node.kind == VariationTableEngine.NodeKind.DISCONTINUITY) {
                        DoubleVerticalBar(modifier = Modifier.align(Alignment.Center))
                        // Left limit near top or bottom depending on incoming arrow
                        val leftHigh = leftSeg?.monotony == VariationTableEngine.Monotony.INCREASING
                        Text(
                            text = node.yLeftLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = CyanAccent,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(if (leftHigh) Alignment.TopStart else Alignment.BottomStart)
                                .padding(start = 2.dp, top = 6.dp, bottom = 6.dp)
                        )
                        val rightHigh = rightSeg?.monotony == VariationTableEngine.Monotony.DECREASING
                        Text(
                            text = node.yRightLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = CyanAccent,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(if (rightHigh) Alignment.TopEnd else Alignment.BottomEnd)
                                .padding(end = 2.dp, top = 6.dp, bottom = 6.dp)
                        )
                    } else {
                        val isHigh = when {
                            leftSeg?.monotony == VariationTableEngine.Monotony.INCREASING && rightSeg?.monotony == VariationTableEngine.Monotony.DECREASING -> true
                            leftSeg?.monotony == VariationTableEngine.Monotony.DECREASING && rightSeg?.monotony == VariationTableEngine.Monotony.INCREASING -> false
                            rightSeg?.monotony == VariationTableEngine.Monotony.DECREASING -> true
                            leftSeg?.monotony == VariationTableEngine.Monotony.INCREASING -> true
                            else -> false
                        }
                        val align = if (isHigh) Alignment.TopCenter else Alignment.BottomCenter
                        Text(
                            text = node.yCenterLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = if (node.kind == VariationTableEngine.NodeKind.CRITICAL) AmberAccent else CyanAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(align)
                                .padding(vertical = 7.dp, horizontal = 2.dp)
                        )
                    }
                }

                if (idx < data.intervals.size) {
                    val seg = data.intervals[idx]
                    VariationArrowCell(monotony = seg.monotony, modifier = Modifier.width(intervalColW).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(label: String, width: androidx.compose.ui.unit.Dp) {
    Row(modifier = Modifier.width(width).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                color = IndigoSoft,
                fontWeight = FontWeight.Bold
            )
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(SlateMuted))
    }
}

@Composable
private fun DoubleVerticalBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(1.5.dp).fillMaxHeight().background(RoseAccent))
        Box(Modifier.width(1.5.dp).fillMaxHeight().background(RoseAccent))
    }
}

@Composable
private fun VariationArrowCell(
    monotony: VariationTableEngine.Monotony,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = w * 0.12f
        val padY = h * 0.22f
        when (monotony) {
            VariationTableEngine.Monotony.INCREASING -> {
                drawVariationArrow(Offset(padX, h - padY), Offset(w - padX, padY), Color(0xFF34D399))
            }
            VariationTableEngine.Monotony.DECREASING -> {
                drawVariationArrow(Offset(padX, padY), Offset(w - padX, h - padY), Color(0xFFFB7185))
            }
            VariationTableEngine.Monotony.CONSTANT -> {
                drawVariationArrow(Offset(padX, h * 0.5f), Offset(w - padX, h * 0.5f), Color(0xFF38BDF8))
            }
            VariationTableEngine.Monotony.UNDEFINED -> {
                val hatchColor = Color(0xFF475569)
                var x = 6f
                while (x < w + h) {
                    drawLine(hatchColor, Offset(x, 0f), Offset(x - h * 0.4f, h), strokeWidth = 2f)
                    x += 12f
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVariationArrow(
    start: Offset,
    end: Offset,
    color: Color
) {
    drawLine(color = color, start = start, end = end, strokeWidth = 4.2f, cap = StrokeCap.Round)
    val ang = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val head = 15f
    val spread = 0.48
    val p1 = Offset(end.x - (head * cos(ang - spread)).toFloat(), end.y - (head * sin(ang - spread)).toFloat())
    val p2 = Offset(end.x - (head * cos(ang + spread)).toFloat(), end.y - (head * sin(ang + spread)).toFloat())
    drawLine(color = color, start = end, end = p1, strokeWidth = 4.2f, cap = StrokeCap.Round)
    drawLine(color = color, start = end, end = p2, strokeWidth = 4.2f, cap = StrokeCap.Round)
}
