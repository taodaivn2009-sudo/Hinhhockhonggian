package com.example.geo2d

import android.graphics.*
import kotlin.math.*

/**
 * High-precision 60fps Android Canvas renderer for the 2D Oxy Coordinate System,
 * Arbitrary Function Graphing, Key-Point Analysis, Tangent Trace, and 2D Geometric Constructions.
 */
object Geo2DRenderer {

    fun worldToScreen(wx: Double, wy: Double, cam: Geo2DCamera, width: Float, height: Float): Vec2 {
        val sx = (width * 0.5 + (wx - cam.centerX) * cam.pixelsPerUnit).toFloat()
        val sy = (height * 0.5 - (wy - cam.centerY) * cam.pixelsPerUnit).toFloat()
        return Vec2(sx, sy)
    }

    fun worldToScreen(wx: Float, wy: Float, cam: Geo2DCamera, width: Float, height: Float): Vec2 =
        worldToScreen(wx.toDouble(), wy.toDouble(), cam, width, height)

    fun screenToWorldDouble(sx: Float, sy: Float, cam: Geo2DCamera, width: Float, height: Float): Pair<Double, Double> {
        val wx = cam.centerX + (sx - width * 0.5) / cam.pixelsPerUnit
        val wy = cam.centerY - (sy - height * 0.5) / cam.pixelsPerUnit
        return wx to wy
    }

    fun screenToWorld(sx: Float, sy: Float, cam: Geo2DCamera, width: Float, height: Float): Vec2 {
        val (wx, wy) = screenToWorldDouble(sx, sy, cam, width, height)
        return Vec2(wx.toFloat(), wy.toFloat())
    }

    fun niceGridStep(pixelsPerUnit: Double, density: Float): Double {
        val targetPx = 72.0 * density
        val rawStep = targetPx / max(1e-12, pixelsPerUnit)
        val exp = floor(log10(max(1e-12, rawStep)))
        val base = 10.0.pow(exp)
        val frac = rawStep / base
        return when {
            frac < 1.5 -> 1.0 * base
            frac < 3.5 -> 2.0 * base
            frac < 7.5 -> 5.0 * base
            else -> 10.0 * base
        }
    }

    fun renderScene(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        uiState: Geo2DUiState,
        includeExportCard: Boolean = false,
        exportTitle: String = ""
    ) {
        if (width <= 2f || height <= 2f) return
        val snap = uiState.snapshot
        val cam = snap.camera

        // 1. Deep Observatory Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.5f, max(width, height) * 0.75f,
                intArrayOf(0xFF162033.toInt(), 0xFF090D16.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        val (xMin, yMin) = screenToWorldDouble(0f, height, cam, width, height)
        val (xMax, yMax) = screenToWorldDouble(width, 0f, cam, width, height)
        val majorStep = niceGridStep(cam.pixelsPerUnit, density)

        // 2. Adaptive Coordinate Grid
        if (snap.showGrid) {
            drawGrid(canvas, width, height, density, cam, xMin, xMax, yMin, yMax, majorStep)
        }

        // 3. Oxy Coordinate Axes & Tick Labels
        if (snap.showAxes) {
            drawAxes(canvas, width, height, density, cam, xMin, xMax, yMin, yMax, majorStep)
        }

        // 4. Compile & Draw Arbitrary Mathematical Function Graphs y = f(x) and optional Derivative y = f'(x)
        val compiledFunctions = mutableListOf<Pair<GraphFunction, MathExprParser.Expr>>()
        val xSpan = max(1e-9, xMax - xMin)
        val ySpan = max(1e-9, yMax - yMin)
        snap.functions.forEach { fn ->
            if ((!fn.visible && !fn.showDerivative) || fn.expression.isBlank()) return@forEach
            val parsed = MathExprParser.parse(fn.expression)
            if (parsed is MathExprParser.ParseResult.Ok) {
                if (fn.visible) {
                    compiledFunctions.add(fn to parsed.expr)
                    drawFunctionCurve(
                        canvas = canvas,
                        width = width,
                        height = height,
                        density = density,
                        cam = cam,
                        evaluator = { x -> parsed.expr.eval(x) },
                        colorHex = fn.colorHex,
                        worldHeight = ySpan,
                        isDashedDerivative = false
                    )
                }
                if (fn.showDerivative) {
                    val symDeriv = parsed.expr.symbolicDerivative()?.simplify()
                    val numStep = xSpan * 1e-5
                    drawFunctionCurve(
                        canvas = canvas,
                        width = width,
                        height = height,
                        density = density,
                        cam = cam,
                        evaluator = { x -> symDeriv?.eval(x) ?: parsed.expr.derivative(x, numStep) },
                        colorHex = fn.colorHex,
                        worldHeight = ySpan,
                        isDashedDerivative = true
                    )
                }
            }
        }

        // 5. Key Function Points (Roots, Y-Intercept, Extrema, Intersections)
        if (snap.showKeyGraphPoints && compiledFunctions.isNotEmpty()) {
            val keyPoints = mutableListOf<GraphKeyPoint>()
            compiledFunctions.forEach { (fn, expr) ->
                keyPoints.addAll(MathExprParser.analyzeFunction(expr, xMin, xMax, yMin, yMax, fn.colorHex))
            }
            if (compiledFunctions.size >= 2) {
                for (i in 0 until compiledFunctions.size) {
                    for (j in i + 1 until compiledFunctions.size) {
                        keyPoints.addAll(
                            MathExprParser.findIntersections(
                                compiledFunctions[i].second,
                                compiledFunctions[j].second,
                                xMin, xMax, yMin, yMax
                            )
                        )
                    }
                }
            }
            drawKeyGraphPoints(canvas, width, height, density, cam, keyPoints)
        }

        // 6. 2D Geometric Objects (Polygons -> Circles -> Lines/Rays -> Segments/Vectors -> Angles)
        val ptMap = snap.points.associateBy { it.id }

        // 6a. Polygons (filled first)
        snap.objects.filterIsInstance<Geo2DObject.Polygon>().forEach { poly ->
            drawPolygon(canvas, width, height, density, cam, poly, ptMap, snap.showMeasurements)
        }

        // 6b. Circles
        snap.objects.filterIsInstance<Geo2DObject.Circle>().forEach { circle ->
            drawCircle(canvas, width, height, density, cam, circle, ptMap, snap.showMeasurements)
        }

        // 6c. Infinite Lines & Rays
        snap.objects.filterIsInstance<Geo2DObject.Line>().forEach { line ->
            drawInfiniteLine(canvas, width, height, density, cam, line, ptMap, snap.showMeasurements)
        }
        snap.objects.filterIsInstance<Geo2DObject.Ray>().forEach { ray ->
            drawRay(canvas, width, height, density, cam, ray, ptMap)
        }

        // 6d. Segments & Vectors
        snap.objects.filterIsInstance<Geo2DObject.Segment>().forEach { seg ->
            drawSegment(canvas, width, height, density, cam, seg, ptMap, snap.showMeasurements)
        }
        snap.objects.filterIsInstance<Geo2DObject.VectorObj>().forEach { vec ->
            drawVector(canvas, width, height, density, cam, vec, ptMap, snap.showMeasurements)
        }

        // 6e. Angle Measurements
        snap.objects.filterIsInstance<Geo2DObject.AngleMeasure>().forEach { ang ->
            drawAngleMeasure(canvas, width, height, density, cam, ang, ptMap, snap.showMeasurements)
        }

        // 7. Interactive Trace Crosshair & Tangent Line
        uiState.traceX?.let { tx ->
            drawTraceOverlay(canvas, width, height, density, cam, tx, uiState.traceResults, snap.showTangentOnTrace)
        }

        // 8. Pending Polygon Preview Lines
        if (uiState.activeTool == Geo2DTool.POLYGON && uiState.pendingPointIds.size >= 2) {
            val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                color = 0xFF818CF8.toInt()
                pathEffect = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
            }
            val pendingPts = uiState.pendingPointIds.mapNotNull { ptMap[it] }
            for (i in 0 until pendingPts.size - 1) {
                val a = worldToScreen(pendingPts[i].x, pendingPts[i].y, cam, width, height)
                val b = worldToScreen(pendingPts[i + 1].x, pendingPts[i + 1].y, cam, width, height)
                canvas.drawLine(a.x, a.y, b.x, b.y, previewPaint)
            }
        }

        // 9. Named 2D Points & Coordinate Labels
        drawPoints(canvas, width, height, density, cam, uiState)

        // 10. Optional Export Summary Card
        if (includeExportCard) {
            drawExportInfoCard(canvas, width, height, density, uiState, exportTitle)
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        majorStep: Double
    ) {
        val minorStep = majorStep / 4.0
        val minorPaint = Paint().apply {
            color = 0xFF152034.toInt()
            strokeWidth = 1f * density
        }
        val majorPaint = Paint().apply {
            color = 0xFF23324C.toInt()
            strokeWidth = 1.25f * density
        }

        // Minor grid lines
        var mx = floor(xMin / minorStep) * minorStep
        var guard = 0
        while (mx <= xMax && guard++ < 400) {
            val sx = worldToScreen(mx, 0.0, cam, width, height).x
            canvas.drawLine(sx, 0f, sx, height, minorPaint)
            mx += minorStep
        }
        var my = floor(yMin / minorStep) * minorStep
        guard = 0
        while (my <= yMax && guard++ < 400) {
            val sy = worldToScreen(0.0, my, cam, width, height).y
            canvas.drawLine(0f, sy, width, sy, minorPaint)
            my += minorStep
        }

        // Major grid lines
        var gx = floor(xMin / majorStep) * majorStep
        guard = 0
        while (gx <= xMax && guard++ < 150) {
            val sx = worldToScreen(gx, 0.0, cam, width, height).x
            canvas.drawLine(sx, 0f, sx, height, majorPaint)
            gx += majorStep
        }
        var gy = floor(yMin / majorStep) * majorStep
        guard = 0
        while (gy <= yMax && guard++ < 150) {
            val sy = worldToScreen(0.0, gy, cam, width, height).y
            canvas.drawLine(0f, sy, width, sy, majorPaint)
            gy += majorStep
        }
    }

    private fun drawAxes(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        majorStep: Double
    ) {
        val origin = worldToScreen(0.0, 0.0, cam, width, height)
        val axisXPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            strokeWidth = 2.0f * density
        }
        val axisYPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            strokeWidth = 2.0f * density
        }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 10.5f * density
            typeface = Typeface.MONOSPACE
        }

        // X Axis
        val clampedY = origin.y.coerceIn(22f * density, height - 22f * density)
        val xVisible = origin.y in -2f..height + 2f
        if (xVisible) {
            canvas.drawLine(0f, origin.y, width - 6f * density, origin.y, axisXPaint)
            drawArrowHead(canvas, width - 20f * density, origin.y, width - 3f * density, origin.y, 0xFFCBD5E1.toInt(), 9f * density)
            val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF87171.toInt()
                textSize = 13.5f * density
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            }
            canvas.drawText("x", width - 18f * density, origin.y - 10f * density, lblPaint)
        }

        // Y Axis
        val clampedX = origin.x.coerceIn(32f * density, width - 26f * density)
        val yVisible = origin.x in -2f..width + 2f
        if (yVisible) {
            canvas.drawLine(origin.x, height, origin.x, 6f * density, axisYPaint)
            drawArrowHead(canvas, origin.x, 20f * density, origin.x, 3f * density, 0xFFCBD5E1.toInt(), 9f * density)
            val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF4ADE80.toInt()
                textSize = 13.5f * density
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            }
            canvas.drawText("y", origin.x + 10f * density, 20f * density, lblPaint)
        }

        // Origin "O"
        if (xVisible && yVisible) {
            tickPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("O", origin.x - 6f * density, origin.y + 14f * density, tickPaint)
        }

        // X tick numbers (with scientific/compact infinite-zoom formatting)
        tickPaint.textAlign = Paint.Align.CENTER
        var gx = floor(xMin / majorStep) * majorStep
        var guard = 0
        while (gx <= xMax && guard++ < 120) {
            if (abs(gx) > majorStep * 0.1) {
                val sx = worldToScreen(gx, 0.0, cam, width, height).x
                if (sx in (24f * density)..(width - 26f * density)) {
                    if (xVisible) canvas.drawLine(sx, origin.y - 3.5f * density, sx, origin.y + 3.5f * density, axisXPaint)
                    canvas.drawText(Geo2DMathHelpers.formatAxisTick(gx), sx, clampedY + 14f * density, tickPaint)
                }
            }
            gx += majorStep
        }

        // Y tick numbers
        tickPaint.textAlign = Paint.Align.RIGHT
        var gy = floor(yMin / majorStep) * majorStep
        guard = 0
        while (gy <= yMax && guard++ < 120) {
            if (abs(gy) > majorStep * 0.1) {
                val sy = worldToScreen(0.0, gy, cam, width, height).y
                if (sy in (24f * density)..(height - 20f * density)) {
                    if (yVisible) canvas.drawLine(origin.x - 3.5f * density, sy, origin.x + 3.5f * density, sy, axisYPaint)
                    canvas.drawText(Geo2DMathHelpers.formatAxisTick(gy), clampedX - 7f * density, sy + 4f * density, tickPaint)
                }
            }
            gy += majorStep
        }
    }

    private fun drawFunctionCurve(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        evaluator: (Double) -> Double,
        colorHex: String,
        worldHeight: Double,
        isDashedDerivative: Boolean
    ) {
        val rawColor = parseColor(colorHex, 0xFF38BDF8.toInt())
        // Lighten/warm slightly for dashed derivative so both f(x) and f'(x) pop cleanly together
        val baseColor = if (isDashedDerivative) {
            Color.rgb(
                ((Color.red(rawColor) + 251) / 2).coerceIn(0, 255),
                ((Color.green(rawColor) + 191) / 2).coerceIn(0, 255),
                ((Color.blue(rawColor) + 60) / 2).coerceIn(0, 255)
            )
        } else rawColor

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (isDashedDerivative) 5f else 6.5f) * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(if (isDashedDerivative) 34 else 42, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (isDashedDerivative) 2.2f else 2.5f) * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = baseColor
            if (isDashedDerivative) {
                pathEffect = DashPathEffect(floatArrayOf(9f * density, 6f * density), 0f)
            }
        }

        val path = Path()
        val stepPx = max(1.5f, 1.5f * density)
        val maxJumpWorld = max(1e-4, worldHeight * 1.15)
        var sx = 0f
        var penDown = false
        var prevWy = Double.NaN
        var prevSy = 0f

        while (sx <= width + stepPx) {
            val (wx, _) = screenToWorldDouble(sx, 0f, cam, width, height)
            val wy = evaluator(wx)
            if (wy.isFinite()) {
                val sy = worldToScreen(wx, wy, cam, width, height).y
                val isAsymptoteJump = penDown && prevWy.isFinite() &&
                    (abs(wy - prevWy) > maxJumpWorld || (abs(sy - prevSy) > height * 1.2f && (wy * prevWy < 0.0)))

                if (!penDown || isAsymptoteJump) {
                    if (sy in (-height * 2f)..(height * 3f)) {
                        path.moveTo(sx, sy)
                        penDown = true
                    } else {
                        penDown = false
                    }
                } else {
                    if (sy in (-height * 2f)..(height * 3f)) {
                        path.lineTo(sx, sy)
                    } else {
                        penDown = false
                    }
                }
                prevWy = wy
                prevSy = sy
            } else {
                penDown = false
                prevWy = Double.NaN
            }
            sx += stepPx
        }

        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawKeyGraphPoints(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        keyPoints: List<GraphKeyPoint>
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f * density
            typeface = Typeface.MONOSPACE
            color = 0xFFE2E8F0.toInt()
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0F172A.toInt() }

        keyPoints.forEach { kp ->
            val p = worldToScreen(kp.x, kp.y, cam, width, height)
            if (p.x !in 12f..(width - 12f) || p.y !in 12f..(height - 12f)) return@forEach
            val c = parseColor(kp.colorHex, 0xFFFBBF24.toInt())
            val r = if (kp.type == KeyPointType.INTERSECTION) 5.2f * density else 4.2f * density
            fillPaint.color = if (kp.type == KeyPointType.INTERSECTION) 0xFFFBBF24.toInt() else 0xFF090D16.toInt()
            strokePaint.color = c
            canvas.drawCircle(p.x, p.y, r, fillPaint)
            canvas.drawCircle(p.x, p.y, r, strokePaint)

            val label = "(${Geo2DMathHelpers.formatNum(kp.x)}; ${Geo2DMathHelpers.formatNum(kp.y)})"
            val tw = textPaint.measureText(label)
            val rect = RectF(p.x + 6f * density, p.y - 16f * density, p.x + 14f * density + tw, p.y - 2f * density)
            canvas.drawRoundRect(rect, 4f * density, 4f * density, bgPaint)
            canvas.drawText(label, rect.left + 4f * density, rect.bottom - 3.5f * density, textPaint)
        }
    }

    private fun drawTraceOverlay(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        traceX: Double,
        results: List<TraceResult>,
        showTangent: Boolean
    ) {
        val sx = worldToScreen(traceX, 0.0, cam, width, height).x
        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
            color = 0xAA94A3B8.toInt()
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
        }
        canvas.drawLine(sx, 0f, sx, height, guidePaint)

        results.forEach { res ->
            if (!res.y.isFinite()) return@forEach
            val pt = worldToScreen(res.x, res.y, cam, width, height)
            val curveColor = parseColor(res.colorHex, 0xFF38BDF8.toInt())

            // Tangent line at traced point
            if (showTangent && res.slope.isFinite()) {
                val spanWorld = (100.0 * density) / cam.pixelsPerUnit
                val x1 = res.x - spanWorld
                val y1 = res.y + res.slope * (x1 - res.x)
                val x2 = res.x + spanWorld
                val y2 = res.y + res.slope * (x2 - res.x)
                val p1 = worldToScreen(x1, y1, cam, width, height)
                val p2 = worldToScreen(x2, y2, cam, width, height)
                val tanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.6f * density
                    color = Color.argb(190, 251, 191, 36)
                    pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
                }
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, tanPaint)
            }

            // Glowing traced dot
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor))
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = curveColor }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.8f * density
                color = Color.WHITE
            }
            canvas.drawCircle(pt.x, pt.y, 11f * density, glow)
            canvas.drawCircle(pt.x, pt.y, 5.5f * density, dot)
            canvas.drawCircle(pt.x, pt.y, 5.5f * density, ring)
        }
    }

    private fun drawPolygon(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        poly: Geo2DObject.Polygon,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean
    ) {
        val pts = poly.pointIds.mapNotNull { ptMap[it] }
        if (pts.size < 3) return
        val sPts = pts.map { worldToScreen(it.x, it.y, cam, width, height) }
        val baseColor = parseColor(poly.colorHex, 0xFF6366F1.toInt())

        val path = Path().apply {
            moveTo(sPts[0].x, sPts[0].y)
            for (i in 1 until sPts.size) lineTo(sPts[i].x, sPts[i].y)
            close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(56, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.1f * density
            strokeJoin = Paint.Join.ROUND
            color = baseColor
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, borderPaint)

        if (showMeasurements) {
            val worldPts = pts.map { Vec2(it.x, it.y) }
            val area = Geo2DMathHelpers.polygonArea(worldPts)
            val cx = sPts.map { it.x }.average().toFloat()
            val cy = sPts.map { it.y }.average().toFloat()

            // Side length badges along each edge (when edge is long enough on screen to avoid overlap)
            for (i in sPts.indices) {
                val p1 = sPts[i]
                val p2 = sPts[(i + 1) % sPts.size]
                val screenLen = hypot(p2.x - p1.x, p2.y - p1.y)
                if (screenLen >= 52f * density) {
                    val worldLen = Geo2DMathHelpers.distance(worldPts[i], worldPts[(i + 1) % worldPts.size])
                    val mx = (p1.x + p2.x) * 0.5f
                    val my = (p1.y + p2.y) * 0.5f
                    // Offset slightly outward from polygon centroid
                    val ox = mx - cx
                    val oy = my - cy
                    val oLen = hypot(ox, oy).coerceAtLeast(1f)
                    val bx = mx + (ox / oLen) * 12f * density
                    val by = my + (oy / oLen) * 12f * density
                    drawSmallBadge(canvas, bx, by, Geo2DMathHelpers.formatNum(worldLen), 0xFFCBD5E1.toInt(), density)
                }
            }

            // Area badge at polygon centroid (only if polygon spans enough screen space)
            val maxSpan = sPts.maxOf { hypot(it.x - cx, it.y - cy) }
            if (maxSpan >= 28f * density) {
                drawSmallBadge(canvas, cx, cy, "S = ${Geo2DMathHelpers.formatNum(area)}", 0xFFA5B4FC.toInt(), density)
            }
        }
    }

    private fun drawCircle(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        circle: Geo2DObject.Circle,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean
    ) {
        val cPt = ptMap[circle.centerId] ?: return
        val ePt = circle.edgePointId?.let { ptMap[it] }
        val worldR = when {
            ePt != null -> hypot(ePt.x - cPt.x, ePt.y - cPt.y)
            circle.fixedRadius != null -> circle.fixedRadius
            else -> return
        }
        if (worldR < 1e-4f) return

        val sc = worldToScreen(cPt.x, cPt.y, cam, width, height)
        val screenR = (worldR * cam.pixelsPerUnit).toFloat()
        val baseColor = parseColor(circle.colorHex, 0xFF10B981.toInt())

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(32, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            color = baseColor
        }
        canvas.drawCircle(sc.x, sc.y, screenR, fill)
        canvas.drawCircle(sc.x, sc.y, screenR, stroke)

        if (showMeasurements && screenR >= 24f * density) {
            val radiusEnd = if (ePt != null) {
                worldToScreen(ePt.x, ePt.y, cam, width, height)
            } else {
                Vec2(sc.x + screenR, sc.y)
            }
            val rPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.4f * density
                color = baseColor
                pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
            }
            canvas.drawLine(sc.x, sc.y, radiusEnd.x, radiusEnd.y, rPaint)
            if (screenR >= 44f * density) {
                drawSmallBadge(
                    canvas,
                    (sc.x + radiusEnd.x) * 0.5f,
                    (sc.y + radiusEnd.y) * 0.5f - 10f * density,
                    "R = ${Geo2DMathHelpers.formatNum(worldR)}",
                    baseColor,
                    density
                )
            }
        }
    }

    private fun drawInfiniteLine(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        line: Geo2DObject.Line,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean
    ) {
        val p1 = ptMap[line.p1Id] ?: return
        val p2 = ptMap[line.p2Id] ?: return
        val s1 = worldToScreen(p1.x, p1.y, cam, width, height)
        val s2 = worldToScreen(p2.x, p2.y, cam, width, height)
        val dx = s2.x - s1.x
        val dy = s2.y - s1.y
        val len = hypot(dx, dy)
        if (len < 1e-3f) return
        val ux = dx / len
        val uy = dy / len
        val ext = max(width, height) * 2f

        val baseColor = parseColor(line.colorHex, 0xFF38BDF8.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
            color = baseColor
            if (line.dashed) pathEffect = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
        }
        canvas.drawLine(s1.x - ux * ext, s1.y - uy * ext, s1.x + ux * ext, s1.y + uy * ext, paint)

        if (showMeasurements) {
            val eq = Geo2DMathHelpers.lineEquationString(Vec2(p1.x, p1.y), Vec2(p2.x, p2.y))
            drawSmallBadge(canvas, (s1.x + s2.x) * 0.5f, (s1.y + s2.y) * 0.5f - 12f * density, eq, baseColor, density)
        }
    }

    private fun drawRay(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        ray: Geo2DObject.Ray,
        ptMap: Map<String, Geo2DPoint>
    ) {
        val p1 = ptMap[ray.startId] ?: return
        val p2 = ptMap[ray.throughId] ?: return
        val s1 = worldToScreen(p1.x, p1.y, cam, width, height)
        val s2 = worldToScreen(p2.x, p2.y, cam, width, height)
        val dx = s2.x - s1.x
        val dy = s2.y - s1.y
        val len = hypot(dx, dy)
        if (len < 1e-3f) return
        val ux = dx / len
        val uy = dy / len
        val ext = max(width, height) * 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
            color = parseColor(ray.colorHex, 0xFFA78BFA.toInt())
        }
        canvas.drawLine(s1.x, s1.y, s1.x + ux * ext, s1.y + uy * ext, paint)
    }

    private fun drawSegment(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        seg: Geo2DObject.Segment,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean
    ) {
        val p1 = ptMap[seg.p1Id] ?: return
        val p2 = ptMap[seg.p2Id] ?: return
        val s1 = worldToScreen(p1.x, p1.y, cam, width, height)
        val s2 = worldToScreen(p2.x, p2.y, cam, width, height)
        val baseColor = parseColor(seg.colorHex, 0xFFF8FAFC.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
            color = baseColor
            if (seg.dashed) pathEffect = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
        }
        canvas.drawLine(s1.x, s1.y, s2.x, s2.y, paint)

        if (showMeasurements && hypot(s2.x - s1.x, s2.y - s1.y) >= 40f * density) {
            val d = Geo2DMathHelpers.distance(Vec2(p1.x, p1.y), Vec2(p2.x, p2.y))
            drawSmallBadge(
                canvas,
                (s1.x + s2.x) * 0.5f,
                (s1.y + s2.y) * 0.5f - 10f * density,
                Geo2DMathHelpers.formatNum(d),
                0xFFCBD5E1.toInt(),
                density
            )
        }
    }

    private fun drawVector(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        vec: Geo2DObject.VectorObj,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean
    ) {
        val p1 = ptMap[vec.fromId] ?: return
        val p2 = ptMap[vec.toId] ?: return
        val s1 = worldToScreen(p1.x, p1.y, cam, width, height)
        val s2 = worldToScreen(p2.x, p2.y, cam, width, height)
        val baseColor = parseColor(vec.colorHex, 0xFFF43F5E.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
            color = baseColor
        }
        canvas.drawLine(s1.x, s1.y, s2.x, s2.y, paint)
        drawArrowHead(canvas, s1.x, s1.y, s2.x, s2.y, baseColor, 12f * density)

        if (showMeasurements && hypot(s2.x - s1.x, s2.y - s1.y) >= 44f * density) {
            val dx = p2.x.toDouble() - p1.x.toDouble()
            val dy = p2.y.toDouble() - p1.y.toDouble()
            drawSmallBadge(
                canvas,
                (s1.x + s2.x) * 0.5f,
                (s1.y + s2.y) * 0.5f - 12f * density,
                "(${Geo2DMathHelpers.formatNum(dx)}; ${Geo2DMathHelpers.formatNum(dy)})",
                baseColor,
                density
            )
        }
    }

    private fun drawAngleMeasure(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        ang: Geo2DObject.AngleMeasure,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean
    ) {
        val pA = ptMap[ang.pAId] ?: return
        val pV = ptMap[ang.vertexId] ?: return
        val pC = ptMap[ang.pCId] ?: return

        val sA = worldToScreen(pA.x, pA.y, cam, width, height)
        val sV = worldToScreen(pV.x, pV.y, cam, width, height)
        val sC = worldToScreen(pC.x, pC.y, cam, width, height)

        val lenVA = hypot(sA.x - sV.x, sA.y - sV.y)
        val lenVC = hypot(sC.x - sV.x, sC.y - sV.y)
        if (lenVA < 4f || lenVC < 4f) return

        val deg = Geo2DMathHelpers.angleDegrees(Vec2(pA.x, pA.y), Vec2(pV.x, pV.y), Vec2(pC.x, pC.y))
        val baseColor = parseColor(ang.colorHex, 0xFFFBBF24.toInt())

        // Draw subtle dashed arms V->A and V->C so standalone angle measurements clearly show both rays
        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.3f * density
            color = Color.argb(135, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
        }
        canvas.drawLine(sV.x, sV.y, sA.x, sA.y, armPaint)
        canvas.drawLine(sV.x, sV.y, sC.x, sC.y, armPaint)

        val a1 = Math.toDegrees(atan2((sA.y - sV.y).toDouble(), (sA.x - sV.x).toDouble())).toFloat()
        val a2 = Math.toDegrees(atan2((sC.y - sV.y).toDouble(), (sC.x - sV.x).toDouble())).toFloat()
        var sweep = a2 - a1
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f

        val arcR = min(24f * density, min(lenVA, lenVC) * 0.42f).coerceAtLeast(8f * density)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(56, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * density
            color = baseColor
        }

        val isRightAngle = abs(deg - 90f) < 0.35f
        if (isRightAngle) {
            val sq = min(15f * density, min(lenVA, lenVC) * 0.35f)
            val ux1 = (sA.x - sV.x) / lenVA
            val uy1 = (sA.y - sV.y) / lenVA
            val ux2 = (sC.x - sV.x) / lenVC
            val uy2 = (sC.y - sV.y) / lenVC
            val q1x = sV.x + ux1 * sq
            val q1y = sV.y + uy1 * sq
            val q2x = sV.x + ux2 * sq
            val q2y = sV.y + uy2 * sq
            val q3x = q1x + ux2 * sq
            val q3y = q1y + uy2 * sq
            val sqPath = Path().apply {
                moveTo(sV.x, sV.y); lineTo(q1x, q1y); lineTo(q3x, q3y); lineTo(q2x, q2y); close()
            }
            canvas.drawPath(sqPath, fillPaint)
            canvas.drawLine(q1x, q1y, q3x, q3y, strokePaint)
            canvas.drawLine(q3x, q3y, q2x, q2y, strokePaint)
        } else {
            val oval = RectF(sV.x - arcR, sV.y - arcR, sV.x + arcR, sV.y + arcR)
            canvas.drawArc(oval, a1, sweep, true, fillPaint)
            canvas.drawArc(oval, a1, sweep, false, strokePaint)
        }

        if (showMeasurements) {
            val midRad = Math.toRadians((a1 + sweep * 0.5f).toDouble())
            val lx = sV.x + (arcR + 17f * density) * cos(midRad).toFloat()
            val ly = sV.y + (arcR + 17f * density) * sin(midRad).toFloat()
            drawSmallBadge(canvas, lx, ly, "${Geo2DMathHelpers.formatNum(deg)}°", baseColor, density)
        }
    }

    private fun drawPoints(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        uiState: Geo2DUiState
    ) {
        val snap = uiState.snapshot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            color = Color.WHITE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * density
        }

        snap.points.forEach { pt ->
            val sp = worldToScreen(pt.x, pt.y, cam, width, height)
            if (sp.x !in -30f..(width + 30f) || sp.y !in -30f..(height + 30f)) return@forEach

            val isDragging = uiState.draggingPointId == pt.id
            val isPending = uiState.pendingPointIds.contains(pt.id)
            val baseColor = when {
                isDragging -> 0xFFEC4899.toInt()
                isPending -> 0xFF10B981.toInt()
                else -> parseColor(pt.colorHex, 0xFF38BDF8.toInt())
            }
            val r = if (isDragging || isPending) 6.5f * density else 5f * density

            dotPaint.color = baseColor
            canvas.drawCircle(sp.x, sp.y, r, dotPaint)
            canvas.drawCircle(sp.x, sp.y, r, ringPaint)

            val label = if (snap.showPointCoords) {
                "${pt.name}(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})"
            } else {
                pt.name
            }
            val padX = 6f * density
            val boxH = 19f * density
            val boxW = max(22f * density, textPaint.measureText(label) + padX * 2f)
            val rect = RectF(
                sp.x - boxW * 0.5f,
                sp.y - 16f * density - boxH * 0.5f,
                sp.x + boxW * 0.5f,
                sp.y - 16f * density + boxH * 0.5f
            )

            bgPaint.color = if (isDragging) 0xF0DB2777.toInt() else if (isPending) 0xF0059669.toInt() else 0xE60F172A.toInt()
            borderPaint.color = baseColor
            textPaint.color = Color.WHITE
            canvas.drawRoundRect(rect, 5f * density, 5f * density, bgPaint)
            canvas.drawRoundRect(rect, 5f * density, 5f * density, borderPaint)
            val fm = textPaint.fontMetrics
            canvas.drawText(label, sp.x, rect.centerY() - (fm.ascent + fm.descent) * 0.5f, textPaint)
        }
    }

    private fun drawSmallBadge(canvas: Canvas, cx: Float, cy: Float, text: String, textColor: Int, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f * density
            typeface = Typeface.MONOSPACE
            color = textColor
            textAlign = Paint.Align.CENTER
        }
        val tw = paint.measureText(text)
        val rect = RectF(cx - tw * 0.5f - 5f * density, cy - 8.5f * density, cx + tw * 0.5f + 5f * density, cy + 8.5f * density)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9090D16.toInt() }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = 0xFF334155.toInt()
        }
        canvas.drawRoundRect(rect, 4.5f * density, 4.5f * density, bg)
        canvas.drawRoundRect(rect, 4.5f * density, 4.5f * density, border)
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, rect.centerY() - (fm.ascent + fm.descent) * 0.5f, paint)
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, colorArgb: Int, headSize: Float) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val spread = 0.42
        val lx = x2 - (headSize * cos(angle - spread)).toFloat()
        val ly = y2 - (headSize * sin(angle - spread)).toFloat()
        val rx = x2 - (headSize * cos(angle + spread)).toFloat()
        val ry = y2 - (headSize * sin(angle + spread)).toFloat()
        val path = Path().apply { moveTo(x2, y2); lineTo(lx, ly); lineTo(rx, ry); close() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = colorArgb }
        canvas.drawPath(path, fill)
    }

    private fun drawExportInfoCard(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        uiState: Geo2DUiState,
        exportTitle: String
    ) {
        val activeFns = uiState.snapshot.functions.filter { it.visible && it.expression.isNotBlank() }
        val summary = if (activeFns.isNotEmpty()) {
            activeFns.take(3).joinToString("   |   ") { "${it.label} = ${it.expression}" }
        } else {
            "Điểm: ${uiState.snapshot.points.size}  |  Đối tượng 2D: ${uiState.snapshot.objects.size}"
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA5B4FC.toInt()
            textSize = 13f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF38BDF8.toInt()
            textSize = 11.5f * density
            typeface = Typeface.MONOSPACE
        }

        val cardW = min(width - 32f * density, max(infoPaint.measureText(summary) + 28f * density, 320f * density))
        val cardH = 54f * density
        val cardX = 16f * density
        val cardY = height - cardH - 16f * density
        val rect = RectF(cardX, cardY, cardX + cardW, cardY + cardH)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60F172A.toInt() }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = 0xFF334155.toInt()
        }
        canvas.drawRoundRect(rect, 10f * density, 10f * density, bg)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, border)

        canvas.drawText("Tool Toán 2D • ${exportTitle.ifBlank { "Oxy" }}", cardX + 14f * density, cardY + 21f * density, titlePaint)
        canvas.drawText(summary, cardX + 14f * density, cardY + 41f * density, infoPaint)
    }

    fun renderToBitmap(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        uiState: Geo2DUiState,
        exportTitle: String
    ): Bitmap {
        val safeW = widthPx.coerceIn(720, 2160)
        val safeH = heightPx.coerceIn(720, 2160)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderScene(canvas, safeW.toFloat(), safeH.toFloat(), density, uiState, true, exportTitle)
        return bitmap
    }

    private fun parseColor(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
