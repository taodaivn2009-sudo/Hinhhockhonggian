package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object GeometryRenderer {

    private val lightDir = Vec3(10f, 18f, 12f).normalized()

    fun renderScene(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        uiState: GeometryUiState,
        includeExportCard: Boolean = false,
        exportTitle: String = ""
    ) {
        if (width <= 2f || height <= 2f) return
        val snap = uiState.snapshot
        val scene = uiState.scene
        val camPos = snap.camera.cameraPosition()
        val proj = CameraProjection(camPos, snap.camera.target, width, height)

        // 1. Deep Observatory radial background gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.5f, max(width, height) * 0.72f,
                intArrayOf(0xFF1E293B.toInt(), 0xFF090D16.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // 2. Ground Grid (14x14 at y = -0.01)
        if (snap.showGrid) {
            drawGrid(canvas, proj, density)
        }

        // 3. Round Body Translucent Surface (Cylinder / Cone / Sphere)
        if (snap.faces) {
            scene.roundBodyHint?.let { drawRoundBodySurface(canvas, proj, it, density) }
        }

        // 4. Translucent Polyhedron, Section, and Custom Plane Faces (Painter's Algorithm)
        val sortedFaces = scene.faces.mapNotNull { face ->
            val projected = face.points.map { proj.project(it) }
            if (projected.any { !it.visible }) return@mapNotNull null
            val avgDepth = projected.map { it.viewDepth }.average().toFloat()
            Triple(face, projected, avgDepth)
        }.sortedByDescending { it.third }

        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for ((face, pts2d, _) in sortedFaces) {
            val path = Path().apply {
                moveTo(pts2d[0].x, pts2d[0].y)
                for (i in 1 until pts2d.size) lineTo(pts2d[i].x, pts2d[i].y)
                close()
            }
            if (face.isSection || face.isCustomPlane) {
                facePaint.color = face.fillColorArgb
            } else {
                // Subtle diffuse directional shading
                val shade = (0.65f + 0.35f * abs(face.normal.dot(lightDir))).coerceIn(0.45f, 1f)
                val baseA = (0x36 * (0.85f + 0.25f * shade)).toInt().coerceIn(28, 80)
                val r = (99 * shade).toInt().coerceIn(0, 255)
                val g = (102 * shade).toInt().coerceIn(0, 255)
                val b = (241 * shade).toInt().coerceIn(0, 255)
                facePaint.color = Color.argb(baseA, r, g, b)
            }
            canvas.drawPath(path, facePaint)
        }

        // 5. Smart Hidden-Edge Dashing for Solid Edges
        val solidEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.7f * density
            strokeCap = Paint.Cap.ROUND
            color = 0xFFF8FAFC.toInt()
        }
        val dashedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.35f * density
            strokeCap = Paint.Cap.ROUND
            color = 0xFF94A3B8.toInt()
            pathEffect = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
        }

        for (edge in scene.edges) {
            val a = proj.project(edge.p1)
            val b = proj.project(edge.p2)
            if (!a.visible || !b.visible) continue

            val isDashed = when (edge.forceStyle) {
                EdgeRenderStyle.ALWAYS_SOLID -> false
                EdgeRenderStyle.ALWAYS_DASHED -> true
                EdgeRenderStyle.SMART -> {
                    if (!snap.dashed) false
                    else {
                        var visible = false
                        for (i in edge.normals.indices) {
                            val dirToCam = camPos - edge.centers[i]
                            if (edge.normals[i].dot(dirToCam) > 0f) {
                                visible = true
                                break
                            }
                        }
                        !visible
                    }
                }
            }

            val paint = if (isDashed) dashedEdgePaint else solidEdgePaint
            val prevColor = paint.color
            if (edge.colorArgb != null) paint.color = edge.colorArgb
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            paint.color = prevColor
        }

        // 5b. Round Body Silhouette Generators (Dynamic tangent edges for cylinder/cone/sphere)
        scene.roundBodyHint?.let { drawRoundBodySilhouettes(canvas, proj, it, solidEdgePaint) }

        // 6. Section & Custom Plane Borders
        scene.sectionBorder?.let { pts ->
            val secBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.0f * density
                color = 0xFFFBBF24.toInt()
            }
            drawClosedPolyline(canvas, proj, pts, secBorderPaint)
        }

        if (scene.customPlaneBorders.isNotEmpty()) {
            val planeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * density
                color = 0xFF34D399.toInt()
                pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
            }
            scene.customPlaneBorders.forEach { pts ->
                drawClosedPolyline(canvas, proj, pts, planeBorderPaint)
            }
        }

        // 7. Altitude & Right-Angle Symbol
        if (snap.altitude) {
            scene.altitude?.let { drawAltitude(canvas, proj, it, scene.pointRegistry, density) }
        }

        // 8. Oxyz Coordinate Axes
        if (snap.showAxes) {
            val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)
            drawCoordinateAxes(canvas, proj, origin, density, snap.labels)
        }

        // 9. Custom Lines & 3D Vectors
        snap.customLines.forEach { ln ->
            val p1 = scene.pointRegistry[ln.p1] ?: return@forEach
            val p2 = scene.pointRegistry[ln.p2] ?: return@forEach
            val colorInt = parseHexColor(ln.colorHex, 0xFF38BDF8.toInt())
            drawCustomLineOrVector(canvas, proj, p1, p2, colorInt, ln.style, density)
        }

        // 10. Pink Constraint Rail (when dragging along vertical Oz or segment AB)
        uiState.constraintRay?.let { ray ->
            val a = proj.project(ray.p1)
            val b = proj.project(ray.p2)
            if (a.visible && b.visible) {
                val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.2f * density
                    color = 0xFFF472B6.toInt()
                }
                canvas.drawLine(a.x, a.y, b.x, b.y, railPaint)
            }
        }

        // 11. Magnetic Snap Visuals (Emerald Ring + 3 Coordinate Projection Guides)
        uiState.snapVisual.pos?.let { snapPos ->
            val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)
            drawSnapGuides(canvas, proj, snapPos, origin, density)
        }

        // 12. Edge Preview Dot (when adding point)
        uiState.previewDot?.let { dotPos ->
            val p = proj.project(dotPos)
            if (p.visible) {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FBBF24 }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBBF24.toInt() }
                canvas.drawCircle(p.x, p.y, 10f * density, glow)
                canvas.drawCircle(p.x, p.y, 5.5f * density, fill)
            }
        }

        // 13. Vertex Nodes
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        scene.pointRegistry.forEach { (name, pos) ->
            val p = proj.project(pos)
            if (!p.visible) return@forEach
            val isOrigin = snap.showAxes && snap.originPoint == name
            val isMoved = snap.vertexOverrides.containsKey(name)
            val isCustom = scene.customPointNames.contains(name)
            val isDragging = uiState.draggingPointName == name
            val isSelected = uiState.selectedFirstPoint == name

            val radius = when {
                isDragging -> 6.5f * density
                isOrigin || isSelected -> 5.5f * density
                isMoved || isCustom -> 4.8f * density
                else -> 3.6f * density
            }
            dotPaint.color = when {
                isDragging -> 0xFFEC4899.toInt()
                isSelected -> 0xFF34D399.toInt()
                isOrigin -> 0xFFEF4444.toInt()
                isMoved -> 0xFFF472B6.toInt()
                isCustom -> 0xFFFBBF24.toInt()
                else -> 0xFFCBD5E1.toInt()
            }
            canvas.drawCircle(p.x, p.y, radius, dotPaint)
        }

        // 14. Vertex Pill Labels
        if (snap.labels) {
            drawVertexLabels(canvas, proj, uiState, density)
        }

        // 15. Optional Export Metrics Card (Bottom-Left for PNG Export)
        if (includeExportCard) {
            drawExportInfoCard(canvas, width, height, density, uiState, exportTitle)
        }
    }

    private fun drawGrid(canvas: Canvas, proj: CameraProjection, density: Float) {
        val minorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
            strokeWidth = 1f * density
        }
        val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            strokeWidth = 1.3f * density
        }
        val half = 7
        val y = -0.01f
        for (i in -half..half) {
            val paint = if (i == 0) majorPaint else minorPaint
            val a1 = proj.project(Vec3(i.toFloat(), y, -half.toFloat()))
            val b1 = proj.project(Vec3(i.toFloat(), y, half.toFloat()))
            if (a1.visible && b1.visible) canvas.drawLine(a1.x, a1.y, b1.x, b1.y, paint)

            val a2 = proj.project(Vec3(-half.toFloat(), y, i.toFloat()))
            val b2 = proj.project(Vec3(half.toFloat(), y, i.toFloat()))
            if (a2.visible && b2.visible) canvas.drawLine(a2.x, a2.y, b2.x, b2.y, paint)
        }
    }

    private fun drawRoundBodySurface(canvas: Canvas, proj: CameraProjection, hint: RoundBodyHint, density: Float) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x2E6366F1
        }
        when (hint) {
            is RoundBodyHint.Cylinder -> {
                val side = proj.right.let { Vec3(it.x, 0f, it.z) }.let {
                    if (it.lengthSq() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
                }
                val bL = proj.project(hint.centerBottom - side * hint.radius)
                val bR = proj.project(hint.centerBottom + side * hint.radius)
                val tR = proj.project(hint.centerTop + side * hint.radius)
                val tL = proj.project(hint.centerTop - side * hint.radius)
                if (bL.visible && bR.visible && tR.visible && tL.visible) {
                    val path = Path().apply {
                        moveTo(bL.x, bL.y); lineTo(bR.x, bR.y); lineTo(tR.x, tR.y); lineTo(tL.x, tL.y); close()
                    }
                    canvas.drawPath(path, fillPaint)
                }
            }
            is RoundBodyHint.Cone -> {
                val side = proj.right.let { Vec3(it.x, 0f, it.z) }.let {
                    if (it.lengthSq() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
                }
                val apex = proj.project(hint.apex)
                val bL = proj.project(hint.centerBottom - side * hint.radius)
                val bR = proj.project(hint.centerBottom + side * hint.radius)
                if (apex.visible && bL.visible && bR.visible) {
                    val path = Path().apply {
                        moveTo(apex.x, apex.y); lineTo(bL.x, bL.y); lineTo(bR.x, bR.y); close()
                    }
                    canvas.drawPath(path, fillPaint)
                }
            }
            is RoundBodyHint.Sphere -> {
                val c = proj.project(hint.center)
                val edge = proj.project(hint.center + proj.right * hint.radius)
                if (c.visible && edge.visible) {
                    val screenR = hypot(edge.x - c.x, edge.y - c.y)
                    val sphereGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            c.x - screenR * 0.28f, c.y - screenR * 0.28f, max(1f, screenR * 1.15f),
                            intArrayOf(0x52818CF8, 0x264F46E5, 0x181E293B),
                            floatArrayOf(0f, 0.65f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawCircle(c.x, c.y, screenR, sphereGrad)
                }
            }
        }
    }

    private fun drawRoundBodySilhouettes(canvas: Canvas, proj: CameraProjection, hint: RoundBodyHint, paint: Paint) {
        when (hint) {
            is RoundBodyHint.Cylinder -> {
                val side = proj.right.let { Vec3(it.x, 0f, it.z) }.let {
                    if (it.lengthSq() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
                }
                val bL = proj.project(hint.centerBottom - side * hint.radius)
                val tL = proj.project(hint.centerTop - side * hint.radius)
                val bR = proj.project(hint.centerBottom + side * hint.radius)
                val tR = proj.project(hint.centerTop + side * hint.radius)
                if (bL.visible && tL.visible) canvas.drawLine(bL.x, bL.y, tL.x, tL.y, paint)
                if (bR.visible && tR.visible) canvas.drawLine(bR.x, bR.y, tR.x, tR.y, paint)
            }
            is RoundBodyHint.Cone -> {
                val side = proj.right.let { Vec3(it.x, 0f, it.z) }.let {
                    if (it.lengthSq() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
                }
                val apex = proj.project(hint.apex)
                val bL = proj.project(hint.centerBottom - side * hint.radius)
                val bR = proj.project(hint.centerBottom + side * hint.radius)
                if (apex.visible && bL.visible) canvas.drawLine(apex.x, apex.y, bL.x, bL.y, paint)
                if (apex.visible && bR.visible) canvas.drawLine(apex.x, apex.y, bR.x, bR.y, paint)
            }
            is RoundBodyHint.Sphere -> {
                val c = proj.project(hint.center)
                val edge = proj.project(hint.center + proj.right * hint.radius)
                if (c.visible && edge.visible) {
                    val screenR = hypot(edge.x - c.x, edge.y - c.y)
                    canvas.drawCircle(c.x, c.y, screenR, paint)
                }
            }
        }
    }

    private fun drawClosedPolyline(canvas: Canvas, proj: CameraProjection, pts: List<Vec3>, paint: Paint) {
        val p2d = pts.map { proj.project(it) }
        if (p2d.size < 3 || p2d.any { !it.visible }) return
        val path = Path().apply {
            moveTo(p2d[0].x, p2d[0].y)
            for (i in 1 until p2d.size) lineTo(p2d[i].x, p2d[i].y)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawAltitude(
        canvas: Canvas,
        proj: CameraProjection,
        alt: AltitudeVisual,
        registry: Map<String, Vec3>,
        density: Float
    ) {
        val top2d = proj.project(alt.top)
        val foot2d = proj.project(alt.foot)
        if (!top2d.visible || !foot2d.visible) return

        val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            color = 0xFFF43F5E.toInt()
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
        }
        canvas.drawLine(top2d.x, top2d.y, foot2d.x, foot2d.y, altPaint)

        // Right-angle L symbol at foot
        val dUpRaw = alt.top - alt.foot
        if (dUpRaw.length() < 0.05f) return
        var dRefRaw = alt.ref - alt.foot
        if (dRefRaw.length() < 0.08f) {
            dRefRaw = (registry["B"] ?: (alt.foot + Vec3(1f, 0f, 0f))) - alt.foot
        }
        if (dRefRaw.length() < 0.05f) dRefRaw = Vec3(1f, 0f, 0f)
        val s = 0.28f
        val dUp = dUpRaw.normalized()
        val dRef = dRefRaw.normalized()
        val p1 = proj.project(alt.foot + dUp * s)
        val p2 = proj.project(alt.foot + dUp * s + dRef * s)
        val p3 = proj.project(alt.foot + dRef * s)
        if (p1.visible && p2.visible && p3.visible) {
            val raPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = 0xFFF43F5E.toInt()
            }
            val path = Path().apply {
                moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
            }
            canvas.drawPath(path, raPaint)
        }
    }

    private fun drawCoordinateAxes(
        canvas: Canvas,
        proj: CameraProjection,
        origin: Vec3,
        density: Float,
        showLabels: Boolean
    ) {
        val axisLen = 6.2f
        drawSingleAxis(canvas, proj, origin + Vec3(-2f, 0f, 0f), origin + Vec3(axisLen, 0f, 0f), origin, Vec3(1f, 0f, 0f), 0xFFEF4444.toInt(), "x", density, showLabels)
        drawSingleAxis(canvas, proj, origin + Vec3(0f, 0f, -2f), origin + Vec3(0f, 0f, axisLen), origin, Vec3(0f, 0f, 1f), 0xFF22C55E.toInt(), "y", density, showLabels)
        drawSingleAxis(canvas, proj, origin + Vec3(0f, -1.2f, 0f), origin + Vec3(0f, axisLen + 0.8f, 0f), origin, Vec3(0f, 1f, 0f), 0xFF3B82F6.toInt(), "z", density, showLabels)
    }

    private fun drawSingleAxis(
        canvas: Canvas,
        proj: CameraProjection,
        start: Vec3,
        end: Vec3,
        origin: Vec3,
        unitDir: Vec3,
        colorArgb: Int,
        axisName: String,
        density: Float,
        showLabels: Boolean
    ) {
        val s2 = proj.project(start)
        val e2 = proj.project(end)
        if (!s2.visible || !e2.visible) return

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * density
            color = colorArgb
        }
        canvas.drawLine(s2.x, s2.y, e2.x, e2.y, linePaint)

        // Arrowhead
        drawArrowHead2D(canvas, s2.x, s2.y, e2.x, e2.y, colorArgb, 10f * density)

        // Unit ticks 1..5
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
        for (i in 1..5) {
            val t = proj.project(origin + unitDir * i.toFloat())
            if (t.visible) canvas.drawCircle(t.x, t.y, 2.5f * density, tickPaint)
        }

        // Axis label pill
        if (showLabels) {
            val lblPt = proj.project(end + unitDir * 0.42f)
            if (lblPt.visible) {
                val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colorArgb
                    textSize = 13f * density
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                    textAlign = Paint.Align.CENTER
                }
                val boxW = 22f * density
                val boxH = 20f * density
                val rect = RectF(lblPt.x - boxW / 2f, lblPt.y - boxH / 2f, lblPt.x + boxW / 2f, lblPt.y + boxH / 2f)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEB090D16.toInt() }
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f * density
                    color = colorArgb
                }
                canvas.drawRoundRect(rect, 5f * density, 5f * density, bg)
                canvas.drawRoundRect(rect, 5f * density, 5f * density, border)
                val fm = txtPaint.fontMetrics
                canvas.drawText(axisName, lblPt.x, rect.centerY() - (fm.ascent + fm.descent) / 2f, txtPaint)
            }
        }
    }

    private fun drawCustomLineOrVector(
        canvas: Canvas,
        proj: CameraProjection,
        p1: Vec3,
        p2: Vec3,
        colorArgb: Int,
        style: LineStyle,
        density: Float
    ) {
        val a = proj.project(p1)
        val b = proj.project(p2)
        if (!a.visible || !b.visible) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2.3f * density
            strokeCap = Paint.Cap.ROUND
            color = colorArgb
            if (style.isDashed) {
                pathEffect = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
            }
        }
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        if (style.isVector) {
            drawArrowHead2D(canvas, a.x, a.y, b.x, b.y, colorArgb, 11.5f * density)
        }
    }

    private fun drawArrowHead2D(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, colorArgb: Int, headSize: Float) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val spread = 0.42
        val lx = x2 - (headSize * cos(angle - spread)).toFloat()
        val ly = y2 - (headSize * sin(angle - spread)).toFloat()
        val rx = x2 - (headSize * cos(angle + spread)).toFloat()
        val ry = y2 - (headSize * sin(angle + spread)).toFloat()
        val path = Path().apply {
            moveTo(x2, y2); lineTo(lx, ly); lineTo(rx, ry); close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colorArgb
        }
        canvas.drawPath(path, fill)
    }

    private fun drawSnapGuides(canvas: Canvas, proj: CameraProjection, pos: Vec3, origin: Vec3, density: Float) {
        val p = proj.project(pos)
        if (p.visible) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.4f * density
                color = 0xFF10B981.toInt()
            }
            canvas.drawCircle(p.x, p.y, 12f * density, ringPaint)
        }

        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
            color = 0xFF10B981.toInt()
            pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
        }
        val footXY = Vec3(pos.x, origin.y, pos.z)
        val footX = Vec3(pos.x, origin.y, origin.z)
        val footY = Vec3(origin.x, origin.y, pos.z)

        fun drawGuide(u: Vec3, v: Vec3) {
            if (u.distanceToSq(v) < 0.0025f) return
            val pu = proj.project(u)
            val pv = proj.project(v)
            if (pu.visible && pv.visible) canvas.drawLine(pu.x, pu.y, pv.x, pv.y, guidePaint)
        }
        drawGuide(pos, footXY)
        drawGuide(footXY, footX)
        drawGuide(footXY, footY)
    }

    private fun drawVertexLabels(canvas: Canvas, proj: CameraProjection, uiState: GeometryUiState, density: Float) {
        val snap = uiState.snapshot
        val scene = uiState.scene
        val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.15f * density
        }

        scene.pointRegistry.forEach { (name, pos) ->
            val p = proj.project(pos)
            if (!p.visible) return@forEach

            val isOrigin = snap.showAxes && snap.originPoint == name
            val isCustom = scene.customPointNames.contains(name)
            val isSelected = uiState.selectedFirstPoint == name
            val isDragging = uiState.draggingPointName == name
            val isLinked = uiState.draggingPointName != null && uiState.linkedPartners.contains(name)

            val labelText = if (snap.showCoordsOnLabels) {
                val m = ShapeBuilder.worldToMath(pos, origin)
                "$name(${formatMathNumber(m.x)}; ${formatMathNumber(m.y)}; ${formatMathNumber(m.z)})"
            } else name

            val scale = when {
                isDragging -> 1.18f
                isLinked || isSelected -> 1.10f
                else -> 1f
            }
            textPaint.textSize = 11.5f * density * scale

            val padX = 6.5f * density * scale
            val boxH = 20f * density * scale
            val boxW = max(22f * density * scale, textPaint.measureText(labelText) + padX * 2f)
            val cx = p.x
            val cy = p.y - 17f * density

            val rect = RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)

            when {
                isDragging -> {
                    bgPaint.color = 0xF0DB2777.toInt(); borderPaint.color = 0xFFF472B6.toInt(); textPaint.color = Color.WHITE
                }
                isLinked -> {
                    bgPaint.color = 0xF00284C7.toInt(); borderPaint.color = 0xFF38BDF8.toInt(); textPaint.color = Color.WHITE
                }
                isSelected -> {
                    bgPaint.color = 0xF0059669.toInt(); borderPaint.color = 0xFF34D399.toInt(); textPaint.color = Color.WHITE
                }
                isOrigin -> {
                    bgPaint.color = 0xEBDC2626.toInt(); borderPaint.color = 0xFFFCA5A5.toInt(); textPaint.color = Color.WHITE
                }
                isCustom -> {
                    bgPaint.color = 0xEBB45309.toInt(); borderPaint.color = 0xFFFBBF24.toInt(); textPaint.color = 0xFFFEF3C7.toInt()
                }
                else -> {
                    bgPaint.color = 0xE00F172A.toInt(); borderPaint.color = 0xFF64748B.toInt(); textPaint.color = Color.WHITE
                }
            }

            val r = 5.5f * density
            canvas.drawRoundRect(rect, r, r, bgPaint)
            canvas.drawRoundRect(rect, r, r, borderPaint)
            val fm = textPaint.fontMetrics
            canvas.drawText(labelText, cx, rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
        }
    }

    private fun drawExportInfoCard(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        uiState: GeometryUiState,
        exportTitle: String
    ) {
        val m = uiState.scene.metrics
        fun f2(v: Float) = String.format(java.util.Locale.US, "%.2f", v)
        val infoLine = "h = ${f2(m.realH)}  |  S(đáy) = ${f2(m.sDay)}  |  S(xq) = ${f2(m.sXq)}  |  S(tp) = ${f2(m.sTp)}  |  V = ${f2(m.vol)}"

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

        val cardW = min(width - 32f * density, max(infoPaint.measureText(infoLine) + 28f * density, 340f * density))
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

        val titleText = "${exportTitle.ifBlank { m.title }} (${m.title})"
        canvas.drawText(titleText, cardX + 14f * density, cardY + 21f * density, titlePaint)
        canvas.drawText(infoLine, cardX + 14f * density, cardY + 41f * density, infoPaint)
    }

    fun renderToBitmap(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        uiState: GeometryUiState,
        exportTitle: String
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
            includeExportCard = true,
            exportTitle = exportTitle
        )
        return bitmap
    }

    fun parseHexColor(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
