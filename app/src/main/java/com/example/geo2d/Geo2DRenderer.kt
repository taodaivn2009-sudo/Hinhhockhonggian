package com.example.geo2d

import android.graphics.*
import com.example.ui.theme.ActiveThemeState
import kotlin.math.*

/**
 * High-precision 60fps Android Canvas renderer for the 2D Oxy Coordinate System,
 * Arbitrary Function Graphing, Key-Point Analysis, Tangent Trace, and 2D Geometric Constructions.
 */
object Geo2DRenderer {

    private class RenderScratch {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var cachedBgW = -1f
        var cachedBgH = -1f

        var cachedDensity = -1f
        var dash75: DashPathEffect? = null
        var dash96: DashPathEffect? = null
        var dash65: DashPathEffect? = null
        var dash54: DashPathEffect? = null

        val serifBoldItalic: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        val defaultBold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val minorGridPaint = Paint()
        val majorGridPaint = Paint()
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serifBoldItalic }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

        val curveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val curveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val curvePath = Path()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val dashedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val keyPtTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            color = 0xFFE2E8F0.toInt()
        }
        val keyPtBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0F172A.toInt() }

        val pointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
        }
        val pointTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = defaultBold
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }
        val pointBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val pointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9090D16.toInt() }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF334155.toInt()
        }

        val scratchPath = Path()
        val scratchRect = RectF()

        // Viewport-padded cache for key function points (roots, extrema, inflection, y-intercepts, intersections) & asymptotes
        var kpFuncSig: String = ""
        var kpXMin = Double.NaN
        var kpXMax = Double.NaN
        var kpYMin = Double.NaN
        var kpYMax = Double.NaN
        var kpXSpan = Double.NaN
        var cachedKeyPoints: List<GraphKeyPoint> = emptyList()
        var cachedAsymptotes: List<GraphAsymptote> = emptyList()

        fun ensureDensity(density: Float) {
            if (cachedDensity != density) {
                cachedDensity = density
                dash75 = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
                dash96 = DashPathEffect(floatArrayOf(9f * density, 6f * density), 0f)
                dash65 = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
                dash54 = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
            }
        }

        var cachedBgInner = 0
        var cachedBgOuter = 0

        fun ensureBackground(width: Float, height: Float, bgInner: Int, bgOuter: Int) {
            if (cachedBgW != width || cachedBgH != height || cachedBgInner != bgInner || cachedBgOuter != bgOuter) {
                cachedBgW = width
                cachedBgH = height
                cachedBgInner = bgInner
                cachedBgOuter = bgOuter
                bgPaint.shader = RadialGradient(
                    width * 0.5f, height * 0.5f, max(width, height) * 0.75f,
                    intArrayOf(bgInner, bgOuter),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private val scratchLocal = ThreadLocal.withInitial { RenderScratch() }

    private fun worldToScreenX(wx: Double, cam: Geo2DCamera, halfW: Double): Float =
        (halfW + (wx - cam.centerX) * cam.pixelsPerUnit).toFloat()

    private fun worldToScreenY(wy: Double, cam: Geo2DCamera, halfH: Double): Float =
        (halfH - (wy - cam.centerY) * cam.pixelsPerUnit).toFloat()

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
        exportTitle: String = "",
        transparentBackground: Boolean = false
    ) {
        if (width <= 2f || height <= 2f) return
        val canvasTheme = ActiveThemeState.current.canvas
        val useDarkInk = transparentBackground || canvasTheme.isLight
        val scratch = scratchLocal.get()!!
        scratch.ensureDensity(density)
        if (!transparentBackground) {
            scratch.ensureBackground(width, height, canvasTheme.bgInnerArgb, canvasTheme.bgOuterArgb)
        }

        val snap = uiState.snapshot
        val cam = snap.camera
        val paramM = snap.paramM

        // 1. Dynamic Canvas Background (skipped when transparent)
        if (!transparentBackground) {
            canvas.drawRect(0f, 0f, width, height, scratch.bgPaint)
        }

        val (xMin, yMin) = screenToWorldDouble(0f, height, cam, width, height)
        val (xMax, yMax) = screenToWorldDouble(width, 0f, cam, width, height)
        val majorStep = niceGridStep(cam.pixelsPerUnit, density)

        // 2. Adaptive Coordinate Grid (skipped when transparent)
        if (snap.showGrid && !transparentBackground) {
            drawGrid(canvas, width, height, density, cam, xMin, xMax, yMin, yMax, majorStep, canvasTheme.gridMinorArgb, canvasTheme.gridMajorArgb, scratch)
        }

        // 3. Oxy Coordinate Axes & Tick Labels
        if (snap.showAxes) {
            drawAxes(canvas, width, height, density, cam, xMin, xMax, yMin, yMax, majorStep, useDarkInk, scratch)
        }

        // 4. Compile (LRU-cached) & Draw Arbitrary Mathematical Function Graphs y = f(x, m) and optional Derivative y = f'(x, m)
        val compiledFunctions = ArrayList<Triple<GraphFunction, MathExprParser.Expr, MathExprParser.Expr?>>(snap.functions.size)
        val xSpan = max(1e-9, xMax - xMin)
        val ySpan = max(1e-9, yMax - yMin)
        snap.functions.forEach { fn ->
            if ((!fn.visible && !fn.showDerivative) || fn.expression.isBlank()) return@forEach
            val parsed = MathExprParser.parse(fn.expression)
            if (parsed is MathExprParser.ParseResult.Ok) {
                val symDeriv = if (fn.showDerivative || snap.showKeyGraphPoints) {
                    MathExprParser.derive(fn.expression)?.first
                } else null

                if (fn.visible) {
                    compiledFunctions.add(Triple(fn, parsed.expr, symDeriv))
                    drawFunctionCurve(
                        canvas = canvas,
                        width = width,
                        height = height,
                        density = density,
                        cam = cam,
                        evaluator = { x -> parsed.expr.eval(x, paramM) },
                        colorHex = fn.colorHex,
                        worldHeight = ySpan,
                        isDashedDerivative = false,
                        scratch = scratch
                    )
                }
                if (fn.showDerivative) {
                    val numStep = xSpan * 1e-5
                    drawFunctionCurve(
                        canvas = canvas,
                        width = width,
                        height = height,
                        density = density,
                        cam = cam,
                        evaluator = { x -> symDeriv?.eval(x, paramM) ?: parsed.expr.derivative(x, numStep, paramM) },
                        colorHex = fn.colorHex,
                        worldHeight = ySpan,
                        isDashedDerivative = true,
                        scratch = scratch
                    )
                }
            }
        }

        // 5. Key Function Points & Asymptotes with viewport-padded caching
        if ((snap.showKeyGraphPoints || snap.showAsymptotes) && compiledFunctions.isNotEmpty()) {
            val (keyPoints, asymptotes) = resolveGraphAnalysisCached(
                compiledFunctions = compiledFunctions,
                paramM = paramM,
                computeKeyPoints = snap.showKeyGraphPoints,
                computeAsymptotes = snap.showAsymptotes,
                xMin = xMin,
                xMax = xMax,
                yMin = yMin,
                yMax = yMax,
                xSpan = xSpan,
                ySpan = ySpan,
                scratch = scratch
            )
            if (snap.showAsymptotes && asymptotes.isNotEmpty()) {
                drawAsymptotes(canvas, width, height, density, cam, xMin, xMax, yMin, yMax, asymptotes, scratch)
            }
            if (snap.showKeyGraphPoints && keyPoints.isNotEmpty()) {
                drawKeyGraphPoints(canvas, width, height, density, cam, keyPoints, scratch)
            }
        }

        // 6. 2D Geometric Objects (Polygons -> Circles -> Lines/Rays -> Segments/Vectors -> Angles)
        val ptMap = snap.points.associateBy { it.id }

        // 6a. Polygons (filled first)
        for (obj in snap.objects) {
            if (obj is Geo2DObject.Polygon) {
                drawPolygon(canvas, width, height, density, cam, obj, ptMap, snap.showMeasurements, useDarkInk, scratch)
            }
        }

        // 6b. Circles
        for (obj in snap.objects) {
            if (obj is Geo2DObject.Circle) {
                drawCircle(canvas, width, height, density, cam, obj, ptMap, snap.showMeasurements, useDarkInk, scratch)
            }
        }

        // 6c. Infinite Lines & Rays
        for (obj in snap.objects) {
            when (obj) {
                is Geo2DObject.Line -> drawInfiniteLine(canvas, width, height, density, cam, obj, ptMap, snap.showMeasurements, useDarkInk, scratch)
                is Geo2DObject.Ray -> drawRay(canvas, width, height, density, cam, obj, ptMap, useDarkInk, scratch)
                else -> Unit
            }
        }

        // 6d. Segments & Vectors
        for (obj in snap.objects) {
            when (obj) {
                is Geo2DObject.Segment -> drawSegment(canvas, width, height, density, cam, obj, ptMap, snap.showMeasurements, useDarkInk, scratch)
                is Geo2DObject.VectorObj -> drawVector(canvas, width, height, density, cam, obj, ptMap, snap.showMeasurements, useDarkInk, scratch)
                else -> Unit
            }
        }

        // 6e. Angle Measurements
        for (obj in snap.objects) {
            if (obj is Geo2DObject.AngleMeasure) {
                drawAngleMeasure(canvas, width, height, density, cam, obj, ptMap, snap.showMeasurements, scratch)
            }
        }

        // 7. Interactive Trace Crosshair & Tangent Line
        uiState.traceX?.let { tx ->
            drawTraceOverlay(canvas, width, height, density, cam, tx, uiState.traceResults, snap.showTangentOnTrace, scratch)
        }

        // 8. Pending Polygon Preview Lines
        if (uiState.activeTool == Geo2DTool.POLYGON && uiState.pendingPointIds.size >= 2) {
            val previewPaint = scratch.dashedStrokePaint
            previewPaint.strokeWidth = 2f * density
            previewPaint.color = 0xFF818CF8.toInt()
            previewPaint.pathEffect = scratch.dash75
            val pendingPts = uiState.pendingPointIds.mapNotNull { ptMap[it] }
            for (i in 0 until pendingPts.size - 1) {
                val a = worldToScreen(pendingPts[i].x, pendingPts[i].y, cam, width, height)
                val b = worldToScreen(pendingPts[i + 1].x, pendingPts[i + 1].y, cam, width, height)
                canvas.drawLine(a.x, a.y, b.x, b.y, previewPaint)
            }
        }

        // 9. Named 2D Points & Coordinate Labels
        drawPoints(canvas, width, height, density, cam, uiState, scratch)

        // 10. Optional Export Summary Card (skipped for transparent export)
        if (includeExportCard && !transparentBackground) {
            drawExportInfoCard(canvas, width, height, density, uiState, exportTitle, scratch)
        }
    }

    private fun resolveGraphAnalysisCached(
        compiledFunctions: List<Triple<GraphFunction, MathExprParser.Expr, MathExprParser.Expr?>>,
        paramM: Double,
        computeKeyPoints: Boolean,
        computeAsymptotes: Boolean,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        xSpan: Double,
        ySpan: Double,
        scratch: RenderScratch
    ): Pair<List<GraphKeyPoint>, List<GraphAsymptote>> {
        val sig = buildString {
            append("m=").append(String.format(java.util.Locale.US, "%.4f", paramM))
            append("|kp=").append(computeKeyPoints).append("|as=").append(computeAsymptotes).append('|')
            for ((fn, _, _) in compiledFunctions) {
                append(fn.expression).append('|').append(fn.colorHex).append(';')
            }
        }
        val zoomRatio = if (scratch.kpXSpan.isFinite() && scratch.kpXSpan > 0.0) xSpan / scratch.kpXSpan else 0.0
        val cacheHit = sig == scratch.kpFuncSig &&
            scratch.kpXMin.isFinite() &&
            xMin >= scratch.kpXMin && xMax <= scratch.kpXMax &&
            yMin >= scratch.kpYMin && yMax <= scratch.kpYMax &&
            zoomRatio in 0.8..1.25

        if (cacheHit) return scratch.cachedKeyPoints to scratch.cachedAsymptotes

        val padX = xSpan * 0.20
        val padY = ySpan * 0.20
        val qXMin = xMin - padX
        val qXMax = xMax + padX
        val qYMin = yMin - padY
        val qYMax = yMax + padY

        val keyPoints = ArrayList<GraphKeyPoint>()
        val asymptotes = ArrayList<GraphAsymptote>()

        if (computeKeyPoints) {
            for ((fn, expr, symDeriv) in compiledFunctions) {
                keyPoints.addAll(MathExprParser.analyzeFunction(expr, qXMin, qXMax, qYMin, qYMax, fn.colorHex, symDeriv, paramM))
            }
            if (compiledFunctions.size >= 2) {
                for (i in compiledFunctions.indices) {
                    for (j in i + 1 until compiledFunctions.size) {
                        keyPoints.addAll(
                            MathExprParser.findIntersections(
                                compiledFunctions[i].second,
                                compiledFunctions[j].second,
                                qXMin, qXMax, qYMin, qYMax, paramM
                            )
                        )
                    }
                }
            }
        }
        if (computeAsymptotes) {
            for ((fn, expr, _) in compiledFunctions) {
                asymptotes.addAll(MathExprParser.findAsymptotes(expr, qXMin, qXMax, qYMin, qYMax, fn.colorHex, paramM))
            }
        }

        scratch.kpFuncSig = sig
        scratch.kpXMin = qXMin
        scratch.kpXMax = qXMax
        scratch.kpYMin = qYMin
        scratch.kpYMax = qYMax
        scratch.kpXSpan = xSpan
        scratch.cachedKeyPoints = keyPoints
        scratch.cachedAsymptotes = asymptotes
        return keyPoints to asymptotes
    }

    private fun drawAsymptotes(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        asymptotes: List<GraphAsymptote>,
        scratch: RenderScratch
    ) {
        val halfW = width * 0.5
        val halfH = height * 0.5
        val linePaint = scratch.dashedStrokePaint.apply {
            strokeWidth = 1.5f * density
            pathEffect = scratch.dash65
        }
        var labelRow = 0
        for (asymptote in asymptotes) {
            when (asymptote) {
                is GraphAsymptote.Vertical -> {
                    val sx = worldToScreenX(asymptote.x, cam, halfW)
                    if (sx in -10f..(width + 10f)) {
                        linePaint.color = 0xCCF43F5E.toInt()
                        canvas.drawLine(sx, 0f, sx, height, linePaint)
                        val badgeY = (36f + (labelRow % 4) * 20f) * density
                        drawSmallBadge(canvas, sx.coerceIn(54f * density, width - 54f * density), badgeY, asymptote.label, 0xFFFDA4AF.toInt(), density, scratch)
                        labelRow++
                    }
                }
                is GraphAsymptote.Horizontal -> {
                    val sy = worldToScreenY(asymptote.y, cam, halfH)
                    if (sy in -10f..(height + 10f)) {
                        linePaint.color = 0xCCF59E0B.toInt()
                        canvas.drawLine(0f, sy, width, sy, linePaint)
                        val badgeX = width - 64f * density
                        drawSmallBadge(canvas, badgeX, sy.coerceIn(28f * density, height - 28f * density) - 11f * density, asymptote.label, 0xFFFCD34D.toInt(), density, scratch)
                        labelRow++
                    }
                }
                is GraphAsymptote.Oblique -> {
                    val y1 = asymptote.a * xMin + asymptote.b
                    val y2 = asymptote.a * xMax + asymptote.b
                    val s1 = worldToScreen(xMin, y1, cam, width, height)
                    val s2 = worldToScreen(xMax, y2, cam, width, height)
                    linePaint.color = 0xCCC084FC.toInt()
                    canvas.drawLine(s1.x, s1.y, s2.x, s2.y, linePaint)
                    val mx = (xMin + xMax) * 0.5 + (xMax - xMin) * 0.18
                    val my = asymptote.a * mx + asymptote.b
                    if (my in yMin..yMax) {
                        val ms = worldToScreen(mx, my, cam, width, height)
                        drawSmallBadge(canvas, ms.x, ms.y - 12f * density, asymptote.label, 0xFFD8B4FE.toInt(), density, scratch)
                    }
                    labelRow++
                }
            }
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
        majorStep: Double,
        minorColor: Int,
        majorColor: Int,
        scratch: RenderScratch
    ) {
        val minorStep = majorStep / 4.0
        val halfW = width * 0.5
        val halfH = height * 0.5
        val minorPaint = scratch.minorGridPaint.apply {
            color = minorColor
            strokeWidth = 1f * density
        }
        val majorPaint = scratch.majorGridPaint.apply {
            color = majorColor
            strokeWidth = 1.25f * density
        }

        // Minor grid lines
        var mx = floor(xMin / minorStep) * minorStep
        var guard = 0
        while (mx <= xMax && guard++ < 400) {
            val sx = worldToScreenX(mx, cam, halfW)
            canvas.drawLine(sx, 0f, sx, height, minorPaint)
            mx += minorStep
        }
        var my = floor(yMin / minorStep) * minorStep
        guard = 0
        while (my <= yMax && guard++ < 400) {
            val sy = worldToScreenY(my, cam, halfH)
            canvas.drawLine(0f, sy, width, sy, minorPaint)
            my += minorStep
        }

        // Major grid lines
        var gx = floor(xMin / majorStep) * majorStep
        guard = 0
        while (gx <= xMax && guard++ < 150) {
            val sx = worldToScreenX(gx, cam, halfW)
            canvas.drawLine(sx, 0f, sx, height, majorPaint)
            gx += majorStep
        }
        var gy = floor(yMin / majorStep) * majorStep
        guard = 0
        while (gy <= yMax && guard++ < 150) {
            val sy = worldToScreenY(gy, cam, halfH)
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
        majorStep: Double,
        transparentBackground: Boolean,
        scratch: RenderScratch
    ) {
        val halfW = width * 0.5
        val halfH = height * 0.5
        val originX = worldToScreenX(0.0, cam, halfW)
        val originY = worldToScreenY(0.0, cam, halfH)

        val axisCol = if (transparentBackground) 0xFF1E293B.toInt() else 0xFF94A3B8.toInt()
        val arrowCol = if (transparentBackground) 0xFF0F172A.toInt() else 0xFFCBD5E1.toInt()
        val axisPaint = scratch.axisPaint.apply {
            color = axisCol
            strokeWidth = 2.0f * density
        }
        val tickPaint = scratch.tickPaint.apply {
            color = axisCol
            textSize = 10.5f * density
        }
        val lblPaint = scratch.axisLabelPaint.apply {
            textSize = 13.5f * density
        }

        // X Axis
        val clampedY = originY.coerceIn(22f * density, height - 22f * density)
        val xVisible = originY in -2f..height + 2f
        if (xVisible) {
            canvas.drawLine(0f, originY, width - 6f * density, originY, axisPaint)
            drawArrowHead(canvas, width - 20f * density, originY, width - 3f * density, originY, arrowCol, 9f * density, scratch)
            lblPaint.color = if (transparentBackground) 0xFFDC2626.toInt() else 0xFFF87171.toInt()
            canvas.drawText("x", width - 18f * density, originY - 10f * density, lblPaint)
        }

        // Y Axis
        val clampedX = originX.coerceIn(32f * density, width - 26f * density)
        val yVisible = originX in -2f..width + 2f
        if (yVisible) {
            canvas.drawLine(originX, height, originX, 6f * density, axisPaint)
            drawArrowHead(canvas, originX, 20f * density, originX, 3f * density, arrowCol, 9f * density, scratch)
            lblPaint.color = if (transparentBackground) 0xFF16A34A.toInt() else 0xFF4ADE80.toInt()
            canvas.drawText("y", originX + 10f * density, 20f * density, lblPaint)
        }

        // Origin "O"
        if (xVisible && yVisible) {
            tickPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("O", originX - 6f * density, originY + 14f * density, tickPaint)
        }

        // X tick numbers (with scientific/compact infinite-zoom formatting)
        tickPaint.textAlign = Paint.Align.CENTER
        var gx = floor(xMin / majorStep) * majorStep
        var guard = 0
        while (gx <= xMax && guard++ < 120) {
            if (abs(gx) > majorStep * 0.1) {
                val sx = worldToScreenX(gx, cam, halfW)
                if (sx in (24f * density)..(width - 26f * density)) {
                    if (xVisible) canvas.drawLine(sx, originY - 3.5f * density, sx, originY + 3.5f * density, axisPaint)
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
                val sy = worldToScreenY(gy, cam, halfH)
                if (sy in (24f * density)..(height - 20f * density)) {
                    if (yVisible) canvas.drawLine(originX - 3.5f * density, sy, originX + 3.5f * density, sy, axisPaint)
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
        isDashedDerivative: Boolean,
        scratch: RenderScratch
    ) {
        val rawColor = parseColor(colorHex, 0xFF38BDF8.toInt())
        val baseColor = if (isDashedDerivative) {
            Color.rgb(
                ((Color.red(rawColor) + 251) / 2).coerceIn(0, 255),
                ((Color.green(rawColor) + 191) / 2).coerceIn(0, 255),
                ((Color.blue(rawColor) + 60) / 2).coerceIn(0, 255)
            )
        } else rawColor

        val glowPaint = scratch.curveGlowPaint
        glowPaint.strokeWidth = (if (isDashedDerivative) 5f else 6.5f) * density
        glowPaint.color = Color.argb(if (isDashedDerivative) 34 else 42, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val strokePaint = scratch.curveStrokePaint
        strokePaint.strokeWidth = (if (isDashedDerivative) 2.2f else 2.5f) * density
        strokePaint.color = baseColor
        strokePaint.pathEffect = if (isDashedDerivative) scratch.dash96 else null

        val path = scratch.curvePath
        path.rewind()
        val stepPx = max(1.5f, 1.5f * density)
        val maxJumpWorld = max(1e-4, worldHeight * 1.15)
        val halfW = width * 0.5
        val halfH = height * 0.5
        val invPpu = 1.0 / cam.pixelsPerUnit
        val minSy = -height * 2f
        val maxSy = height * 3f
        val maxJumpSy = height * 1.2f

        var sx = 0f
        var penDown = false
        var prevWy = Double.NaN
        var prevSy = 0f

        while (sx <= width + stepPx) {
            val wx = cam.centerX + (sx - halfW) * invPpu
            val wy = evaluator(wx)
            if (wy.isFinite()) {
                val sy = (halfH - (wy - cam.centerY) * cam.pixelsPerUnit).toFloat()
                val isAsymptoteJump = penDown && prevWy.isFinite() &&
                    (abs(wy - prevWy) > maxJumpWorld || (abs(sy - prevSy) > maxJumpSy && (wy * prevWy < 0.0)))

                if (!penDown || isAsymptoteJump) {
                    if (sy in minSy..maxSy) {
                        path.moveTo(sx, sy)
                        penDown = true
                    } else {
                        penDown = false
                    }
                } else {
                    if (sy in minSy..maxSy) {
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
        keyPoints: List<GraphKeyPoint>,
        scratch: RenderScratch
    ) {
        val halfW = width * 0.5
        val halfH = height * 0.5
        val fillPaint = scratch.fillPaint
        val strokePaint = scratch.strokePaint.apply {
            strokeWidth = 1.6f * density
            pathEffect = null
        }
        val textPaint = scratch.keyPtTextPaint.apply {
            textSize = 9.5f * density
        }
        val bgPaint = scratch.keyPtBgPaint
        val rect = scratch.scratchRect

        fun priority(t: KeyPointType): Int = when (t) {
            KeyPointType.INTERSECTION -> 5
            KeyPointType.LOCAL_MAX, KeyPointType.LOCAL_MIN -> 4
            KeyPointType.INFLECTION -> 3
            KeyPointType.ROOT -> 2
            KeyPointType.Y_INTERCEPT -> 1
        }
        val drawnPts = ArrayList<Pair<Float, Float>>(keyPoints.size)
        val minSep = 14f * density

        for (kp in keyPoints.sortedByDescending { priority(it.type) }) {
            val px = worldToScreenX(kp.x, cam, halfW)
            val py = worldToScreenY(kp.y, cam, halfH)
            if (px !in 12f..(width - 12f) || py !in 12f..(height - 12f)) continue
            if (drawnPts.any { hypot(it.first - px, it.second - py) < minSep }) continue
            drawnPts.add(px to py)
            val c = parseColor(kp.colorHex, 0xFFFBBF24.toInt())

            if (kp.type == KeyPointType.INFLECTION) {
                val r = 5.0f * density
                val path = scratch.scratchPath
                path.rewind()
                path.moveTo(px, py - r)
                path.lineTo(px + r, py)
                path.lineTo(px, py + r)
                path.lineTo(px - r, py)
                path.close()
                fillPaint.color = 0xFFC084FC.toInt()
                strokePaint.color = Color.WHITE
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            } else {
                val r = if (kp.type == KeyPointType.INTERSECTION) 5.2f * density else 4.2f * density
                fillPaint.color = if (kp.type == KeyPointType.INTERSECTION) 0xFFFBBF24.toInt() else 0xFF090D16.toInt()
                strokePaint.color = c
                canvas.drawCircle(px, py, r, fillPaint)
                canvas.drawCircle(px, py, r, strokePaint)
            }

            val prefix = when (kp.type) {
                KeyPointType.LOCAL_MAX -> "CĐ "
                KeyPointType.LOCAL_MIN -> "CT "
                KeyPointType.INFLECTION -> "U "
                else -> ""
            }
            val label = "$prefix(${Geo2DMathHelpers.formatNum(kp.x)}; ${Geo2DMathHelpers.formatNum(kp.y)})"
            val tw = textPaint.measureText(label)
            rect.set(px + 6f * density, py - 16f * density, px + 14f * density + tw, py - 2f * density)
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
        showTangent: Boolean,
        scratch: RenderScratch
    ) {
        val sx = worldToScreenX(traceX, cam, width * 0.5)
        val guidePaint = scratch.dashedStrokePaint
        guidePaint.strokeWidth = 1.4f * density
        guidePaint.color = 0xAA94A3B8.toInt()
        guidePaint.pathEffect = scratch.dash65
        canvas.drawLine(sx, 0f, sx, height, guidePaint)

        val fillPaint = scratch.fillPaint
        val ringPaint = scratch.pointRingPaint.apply { strokeWidth = 1.8f * density }

        for (res in results) {
            if (!res.y.isFinite()) continue
            val pt = worldToScreen(res.x, res.y, cam, width, height)
            val curveColor = parseColor(res.colorHex, 0xFF38BDF8.toInt())

            if (showTangent && res.slope.isFinite()) {
                val spanWorld = (100.0 * density) / cam.pixelsPerUnit
                val x1 = res.x - spanWorld
                val y1 = res.y + res.slope * (x1 - res.x)
                val x2 = res.x + spanWorld
                val y2 = res.y + res.slope * (x2 - res.x)
                val p1 = worldToScreen(x1, y1, cam, width, height)
                val p2 = worldToScreen(x2, y2, cam, width, height)
                guidePaint.strokeWidth = 1.6f * density
                guidePaint.color = Color.argb(190, 251, 191, 36)
                guidePaint.pathEffect = scratch.dash54
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, guidePaint)
            }

            fillPaint.color = Color.argb(80, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor))
            canvas.drawCircle(pt.x, pt.y, 11f * density, fillPaint)
            fillPaint.color = curveColor
            canvas.drawCircle(pt.x, pt.y, 5.5f * density, fillPaint)
            canvas.drawCircle(pt.x, pt.y, 5.5f * density, ringPaint)
        }
    }

    private fun isNeutralColorHex(hex: String?): Boolean {
        val t = hex?.trim().orEmpty()
        return t.isEmpty() || t.equals("default", ignoreCase = true) || t.equals("neutral", ignoreCase = true)
    }

    private fun resolve2DColor(hex: String, fallback: Int, useDarkInk: Boolean, isDashed: Boolean = false): Int {
        if (isNeutralColorHex(hex)) {
            return if (useDarkInk) {
                if (isDashed) 0xFF475569.toInt() else 0xFF0F172A.toInt()
            } else {
                if (isDashed) 0xFF94A3B8.toInt() else 0xFFE2E8F0.toInt()
            }
        }
        val rawColor = parseColor(hex, fallback)
        return if (useDarkInk && rawColor == 0xFFF8FAFC.toInt()) 0xFF0F172A.toInt() else rawColor
    }

    private fun drawPolygon(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        poly: Geo2DObject.Polygon,
        ptMap: Map<String, Geo2DPoint>,
        showMeasurements: Boolean,
        useDarkInk: Boolean,
        scratch: RenderScratch
    ) {
        val pts = poly.pointIds.mapNotNull { ptMap[it] }
        if (pts.size < 3) return
        val sPts = pts.map { worldToScreen(it.x, it.y, cam, width, height) }
        val isNeutral = isNeutralColorHex(poly.colorHex)
        val baseColor = resolve2DColor(poly.colorHex, 0xFF6366F1.toInt(), useDarkInk)

        val path = scratch.scratchPath
        path.rewind()
        path.moveTo(sPts[0].x, sPts[0].y)
        for (i in 1 until sPts.size) path.lineTo(sPts[i].x, sPts[i].y)
        path.close()

        val fillPaint = scratch.fillPaint
        fillPaint.color = Color.argb(if (isNeutral) 18 else 56, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val borderPaint = scratch.strokePaint
        borderPaint.strokeWidth = (if (isNeutral) 1.8f else 2.1f) * density
        borderPaint.strokeJoin = Paint.Join.ROUND
        borderPaint.color = baseColor
        borderPaint.pathEffect = null

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, borderPaint)

        if (showMeasurements) {
            val worldPts = pts.map { Vec2(it.x, it.y) }
            val area = Geo2DMathHelpers.polygonArea(worldPts)
            var sumX = 0f
            var sumY = 0f
            for (sp in sPts) {
                sumX += sp.x
                sumY += sp.y
            }
            val cx = sumX / sPts.size
            val cy = sumY / sPts.size

            for (i in sPts.indices) {
                val p1 = sPts[i]
                val p2 = sPts[(i + 1) % sPts.size]
                val screenLen = hypot(p2.x - p1.x, p2.y - p1.y)
                if (screenLen >= 52f * density) {
                    val worldLen = Geo2DMathHelpers.distance(worldPts[i], worldPts[(i + 1) % worldPts.size])
                    val mx = (p1.x + p2.x) * 0.5f
                    val my = (p1.y + p2.y) * 0.5f
                    val ox = mx - cx
                    val oy = my - cy
                    val oLen = hypot(ox, oy).coerceAtLeast(1f)
                    val bx = mx + (ox / oLen) * 12f * density
                    val by = my + (oy / oLen) * 12f * density
                    drawSmallBadge(canvas, bx, by, Geo2DMathHelpers.formatNum(worldLen), 0xFFCBD5E1.toInt(), density, scratch)
                }
            }

            var maxSpan = 0f
            for (sp in sPts) {
                val d = hypot(sp.x - cx, sp.y - cy)
                if (d > maxSpan) maxSpan = d
            }
            if (maxSpan >= 28f * density) {
                drawSmallBadge(canvas, cx, cy, "S = ${Geo2DMathHelpers.formatNum(area)}", 0xFFA5B4FC.toInt(), density, scratch)
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
        showMeasurements: Boolean,
        useDarkInk: Boolean,
        scratch: RenderScratch
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
        val isNeutral = isNeutralColorHex(circle.colorHex)
        val baseColor = resolve2DColor(circle.colorHex, 0xFF10B981.toInt(), useDarkInk)

        val fill = scratch.fillPaint
        fill.color = Color.argb(if (isNeutral) 14 else 32, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val stroke = scratch.strokePaint
        stroke.strokeWidth = (if (isNeutral) 1.9f else 2.2f) * density
        stroke.color = baseColor
        stroke.pathEffect = null

        canvas.drawCircle(sc.x, sc.y, screenR, fill)
        canvas.drawCircle(sc.x, sc.y, screenR, stroke)

        if (showMeasurements && screenR >= 24f * density) {
            val radiusEnd = if (ePt != null) {
                worldToScreen(ePt.x, ePt.y, cam, width, height)
            } else {
                Vec2(sc.x + screenR, sc.y)
            }
            val rPaint = scratch.dashedStrokePaint
            rPaint.strokeWidth = 1.4f * density
            rPaint.color = baseColor
            rPaint.pathEffect = scratch.dash54
            canvas.drawLine(sc.x, sc.y, radiusEnd.x, radiusEnd.y, rPaint)
            if (screenR >= 44f * density) {
                drawSmallBadge(
                    canvas,
                    (sc.x + radiusEnd.x) * 0.5f,
                    (sc.y + radiusEnd.y) * 0.5f - 10f * density,
                    "R = ${Geo2DMathHelpers.formatNum(worldR)}",
                    baseColor,
                    density,
                    scratch
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
        showMeasurements: Boolean,
        useDarkInk: Boolean,
        scratch: RenderScratch
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

        val baseColor = resolve2DColor(line.colorHex, 0xFF38BDF8.toInt(), useDarkInk, line.dashed)
        val paint = if (line.dashed) scratch.dashedStrokePaint else scratch.strokePaint
        paint.strokeWidth = 2.0f * density
        paint.color = baseColor
        paint.pathEffect = if (line.dashed) scratch.dash75 else null
        canvas.drawLine(s1.x - ux * ext, s1.y - uy * ext, s1.x + ux * ext, s1.y + uy * ext, paint)

        if (showMeasurements) {
            val eq = Geo2DMathHelpers.lineEquationString(Vec2(p1.x, p1.y), Vec2(p2.x, p2.y))
            drawSmallBadge(canvas, (s1.x + s2.x) * 0.5f, (s1.y + s2.y) * 0.5f - 12f * density, eq, baseColor, density, scratch)
        }
    }

    private fun drawRay(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        ray: Geo2DObject.Ray,
        ptMap: Map<String, Geo2DPoint>,
        useDarkInk: Boolean,
        scratch: RenderScratch
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

        val paint = scratch.strokePaint
        paint.strokeWidth = 2.0f * density
        paint.color = resolve2DColor(ray.colorHex, 0xFFA78BFA.toInt(), useDarkInk)
        paint.pathEffect = null
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
        showMeasurements: Boolean,
        transparentBackground: Boolean,
        scratch: RenderScratch
    ) {
        val p1 = ptMap[seg.p1Id] ?: return
        val p2 = ptMap[seg.p2Id] ?: return
        val s1 = worldToScreen(p1.x, p1.y, cam, width, height)
        val s2 = worldToScreen(p2.x, p2.y, cam, width, height)
        val isNeutral = isNeutralColorHex(seg.colorHex)
        val baseColor = resolve2DColor(seg.colorHex, 0xFFF8FAFC.toInt(), transparentBackground, seg.dashed)
        val paint = if (seg.dashed) scratch.dashedStrokePaint else scratch.strokePaint
        paint.strokeWidth = (if (isNeutral) 1.9f else 2.4f) * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = baseColor
        paint.pathEffect = if (seg.dashed) scratch.dash75 else null
        canvas.drawLine(s1.x, s1.y, s2.x, s2.y, paint)

        if (showMeasurements && hypot(s2.x - s1.x, s2.y - s1.y) >= 40f * density) {
            val d = Geo2DMathHelpers.distance(Vec2(p1.x, p1.y), Vec2(p2.x, p2.y))
            drawSmallBadge(
                canvas,
                (s1.x + s2.x) * 0.5f,
                (s1.y + s2.y) * 0.5f - 10f * density,
                Geo2DMathHelpers.formatNum(d),
                0xFFCBD5E1.toInt(),
                density,
                scratch
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
        showMeasurements: Boolean,
        useDarkInk: Boolean,
        scratch: RenderScratch
    ) {
        val p1 = ptMap[vec.fromId] ?: return
        val p2 = ptMap[vec.toId] ?: return
        val s1 = worldToScreen(p1.x, p1.y, cam, width, height)
        val s2 = worldToScreen(p2.x, p2.y, cam, width, height)
        val isNeutral = isNeutralColorHex(vec.colorHex)
        val baseColor = resolve2DColor(vec.colorHex, 0xFFF43F5E.toInt(), useDarkInk, vec.dashed)
        val paint = if (vec.dashed) scratch.dashedStrokePaint else scratch.strokePaint
        paint.strokeWidth = (if (isNeutral) 2.1f else 2.6f) * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = baseColor
        paint.pathEffect = if (vec.dashed) scratch.dash75 else null
        canvas.drawLine(s1.x, s1.y, s2.x, s2.y, paint)
        drawArrowHead(canvas, s1.x, s1.y, s2.x, s2.y, baseColor, 12f * density, scratch)

        if (showMeasurements && hypot(s2.x - s1.x, s2.y - s1.y) >= 44f * density) {
            val dx = p2.x.toDouble() - p1.x.toDouble()
            val dy = p2.y.toDouble() - p1.y.toDouble()
            drawSmallBadge(
                canvas,
                (s1.x + s2.x) * 0.5f,
                (s1.y + s2.y) * 0.5f - 12f * density,
                "(${Geo2DMathHelpers.formatNum(dx)}; ${Geo2DMathHelpers.formatNum(dy)})",
                baseColor,
                density,
                scratch
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
        showMeasurements: Boolean,
        scratch: RenderScratch
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

        val armPaint = scratch.dashedStrokePaint
        armPaint.strokeWidth = 1.3f * density
        armPaint.color = Color.argb(135, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        armPaint.pathEffect = scratch.dash54
        canvas.drawLine(sV.x, sV.y, sA.x, sA.y, armPaint)
        canvas.drawLine(sV.x, sV.y, sC.x, sC.y, armPaint)

        val a1 = Math.toDegrees(atan2((sA.y - sV.y).toDouble(), (sA.x - sV.x).toDouble())).toFloat()
        val a2 = Math.toDegrees(atan2((sC.y - sV.y).toDouble(), (sC.x - sV.x).toDouble())).toFloat()
        var sweep = a2 - a1
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f

        val arcR = min(24f * density, min(lenVA, lenVC) * 0.42f).coerceAtLeast(8f * density)

        val fillPaint = scratch.fillPaint
        fillPaint.color = Color.argb(56, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val strokePaint = scratch.strokePaint
        strokePaint.strokeWidth = 1.9f * density
        strokePaint.color = baseColor
        strokePaint.pathEffect = null

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
            val sqPath = scratch.scratchPath
            sqPath.rewind()
            sqPath.moveTo(sV.x, sV.y); sqPath.lineTo(q1x, q1y); sqPath.lineTo(q3x, q3y); sqPath.lineTo(q2x, q2y); sqPath.close()
            canvas.drawPath(sqPath, fillPaint)
            canvas.drawLine(q1x, q1y, q3x, q3y, strokePaint)
            canvas.drawLine(q3x, q3y, q2x, q2y, strokePaint)
        } else {
            val oval = scratch.scratchRect
            oval.set(sV.x - arcR, sV.y - arcR, sV.x + arcR, sV.y + arcR)
            canvas.drawArc(oval, a1, sweep, true, fillPaint)
            canvas.drawArc(oval, a1, sweep, false, strokePaint)
        }

        if (showMeasurements) {
            val midRad = Math.toRadians((a1 + sweep * 0.5f).toDouble())
            val lx = sV.x + (arcR + 17f * density) * cos(midRad).toFloat()
            val ly = sV.y + (arcR + 17f * density) * sin(midRad).toFloat()
            drawSmallBadge(canvas, lx, ly, "${Geo2DMathHelpers.formatNum(deg)}°", baseColor, density, scratch)
        }
    }

    private fun drawPoints(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        cam: Geo2DCamera,
        uiState: Geo2DUiState,
        scratch: RenderScratch
    ) {
        val snap = uiState.snapshot
        val halfW = width * 0.5
        val halfH = height * 0.5
        val dotPaint = scratch.fillPaint
        val ringPaint = scratch.pointRingPaint.apply { strokeWidth = 1.8f * density }
        val textPaint = scratch.pointTextPaint.apply { textSize = 11f * density }
        val bgPaint = scratch.pointBgPaint
        val borderPaint = scratch.pointBorderPaint.apply { strokeWidth = 1.1f * density }
        val rect = scratch.scratchRect
        val fm = textPaint.fontMetrics
        val textCenterOffset = (fm.ascent + fm.descent) * 0.5f

        for (pt in snap.points) {
            val spx = worldToScreenX(pt.x.toDouble(), cam, halfW)
            val spy = worldToScreenY(pt.y.toDouble(), cam, halfH)
            if (spx !in -30f..(width + 30f) || spy !in -30f..(height + 30f)) continue

            val isDragging = uiState.draggingPointId == pt.id
            val isPending = uiState.pendingPointIds.contains(pt.id)
            val baseColor = when {
                isDragging -> 0xFFEC4899.toInt()
                isPending -> 0xFF10B981.toInt()
                else -> parseColor(pt.colorHex, 0xFF38BDF8.toInt())
            }
            val r = if (isDragging || isPending) 6.5f * density else 5f * density

            dotPaint.color = baseColor
            canvas.drawCircle(spx, spy, r, dotPaint)
            canvas.drawCircle(spx, spy, r, ringPaint)

            val label = if (snap.showPointCoords) {
                "${pt.name}(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})"
            } else {
                pt.name
            }
            val padX = 6f * density
            val boxH = 19f * density
            val boxW = max(22f * density, textPaint.measureText(label) + padX * 2f)
            rect.set(
                spx - boxW * 0.5f,
                spy - 16f * density - boxH * 0.5f,
                spx + boxW * 0.5f,
                spy - 16f * density + boxH * 0.5f
            )

            val canvasTheme = ActiveThemeState.current.canvas
            bgPaint.color = if (isDragging) 0xF0DB2777.toInt() else if (isPending) 0xF0059669.toInt() else canvasTheme.labelPillBgArgb
            textPaint.color = if (isDragging || isPending) Color.WHITE else canvasTheme.labelTextArgb
            borderPaint.color = baseColor
            canvas.drawRoundRect(rect, 5f * density, 5f * density, bgPaint)
            canvas.drawRoundRect(rect, 5f * density, 5f * density, borderPaint)
            canvas.drawText(label, spx, rect.centerY() - textCenterOffset, textPaint)
        }
    }

    private fun drawSmallBadge(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        text: String,
        textColor: Int,
        density: Float,
        scratch: RenderScratch
    ) {
        val paint = scratch.badgeTextPaint
        paint.textSize = 10f * density
        paint.color = textColor
        val tw = paint.measureText(text)
        val rect = scratch.scratchRect
        rect.set(cx - tw * 0.5f - 5f * density, cy - 8.5f * density, cx + tw * 0.5f + 5f * density, cy + 8.5f * density)
        val border = scratch.badgeBorderPaint
        border.strokeWidth = 1f * density
        canvas.drawRoundRect(rect, 4.5f * density, 4.5f * density, scratch.badgeBgPaint)
        canvas.drawRoundRect(rect, 4.5f * density, 4.5f * density, border)
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, rect.centerY() - (fm.ascent + fm.descent) * 0.5f, paint)
    }

    private fun drawArrowHead(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        colorArgb: Int,
        headSize: Float,
        scratch: RenderScratch
    ) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val spread = 0.42
        val lx = x2 - (headSize * cos(angle - spread)).toFloat()
        val ly = y2 - (headSize * sin(angle - spread)).toFloat()
        val rx = x2 - (headSize * cos(angle + spread)).toFloat()
        val ry = y2 - (headSize * sin(angle + spread)).toFloat()
        val path = scratch.scratchPath
        path.rewind()
        path.moveTo(x2, y2); path.lineTo(lx, ly); path.lineTo(rx, ry); path.close()
        val fill = scratch.fillPaint
        fill.color = colorArgb
        canvas.drawPath(path, fill)
    }

    private fun drawExportInfoCard(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        uiState: Geo2DUiState,
        exportTitle: String,
        scratch: RenderScratch
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
            typeface = scratch.defaultBold
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
        val rect = scratch.scratchRect
        rect.set(cardX, cardY, cardX + cardW, cardY + cardH)
        val bg = scratch.fillPaint.apply { color = 0xE60F172A.toInt() }
        val border = scratch.strokePaint.apply {
            strokeWidth = 1.2f * density
            color = 0xFF334155.toInt()
            pathEffect = null
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
        exportTitle: String,
        transparentBackground: Boolean = false
    ): Bitmap {
        val safeW = widthPx.coerceIn(720, 2160)
        val safeH = heightPx.coerceIn(720, 2160)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderScene(
            canvas = canvas,
            width = safeW.toFloat(),
            height = safeH.toFloat(),
            density = density,
            uiState = uiState,
            includeExportCard = !transparentBackground,
            exportTitle = exportTitle,
            transparentBackground = transparentBackground
        )
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
