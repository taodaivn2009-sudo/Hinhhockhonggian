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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeometryRenderer {

    private val lightDir = Vec3(10f, 18f, 12f).normalized()

    private class ProjectedFace(
        val face: RenderFace,
        val pts2d: List<ProjectedPoint>,
        val avgDepth: Float
    )

    private class RenderScratch3D {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var cachedBgW = -1f
        var cachedBgH = -1f

        var cachedDensity = -1f
        var dash75: DashPathEffect? = null
        var dash64: DashPathEffect? = null
        var dash54: DashPathEffect? = null

        val defaultBold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val serifBoldItalic: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)

        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // Contiguous tessellated curved strips use non-AA fill to prevent hairline alpha cracks between adjacent quads
        val curvedMeshPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }

        val solidEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0xFFF8FAFC.toInt()
        }
        val dashedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0xFF94A3B8.toInt()
        }
        val minorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() }
        val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt() }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val dashedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serifBoldItalic
            textAlign = Paint.Align.CENTER
        }
        val vertexTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = defaultBold
            textAlign = Paint.Align.CENTER
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val scratchPath = Path()
        val scratchRect = RectF()
        val sortedFaces = ArrayList<ProjectedFace>(128)

        fun ensureDensity(density: Float) {
            if (cachedDensity != density) {
                cachedDensity = density
                dash75 = DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
                dash64 = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
                dash54 = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
            }
        }

        fun ensureBackground(width: Float, height: Float) {
            if (cachedBgW != width || cachedBgH != height) {
                cachedBgW = width
                cachedBgH = height
                bgPaint.shader = RadialGradient(
                    width * 0.5f, height * 0.5f, max(width, height) * 0.72f,
                    intArrayOf(0xFF1E293B.toInt(), 0xFF090D16.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private val scratchLocal = ThreadLocal.withInitial { RenderScratch3D() }

    fun renderScene(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        uiState: GeometryUiState,
        includeExportCard: Boolean = false,
        exportTitle: String = "",
        transparentBackground: Boolean = false
    ) {
        if (width <= 2f || height <= 2f) return
        val scratch = scratchLocal.get()!!
        scratch.ensureDensity(density)
        if (!transparentBackground) {
            scratch.ensureBackground(width, height)
        }

        val snap = uiState.snapshot
        val scene = uiState.scene
        val camPos = snap.camera.cameraPosition()
        val proj = CameraProjection(camPos, snap.camera.target, width, height)

        // 1. Deep Observatory radial background gradient (skipped for transparent export)
        if (!transparentBackground) {
            canvas.drawRect(0f, 0f, width, height, scratch.bgPaint)
        }

        // 2. Ground Grid (14x14 at y = -0.01) — skipped for transparent export
        if (snap.showGrid && !transparentBackground) {
            drawGrid(canvas, proj, density, scratch)
        }

        // 3. Translucent 3D Surfaces: Polyhedra, Curved Meshes (Cylinder/Cone/Sphere), Sections & Custom Planes
        val sortedFaces = scratch.sortedFaces
        sortedFaces.clear()
        for (face in scene.faces) {
            val pts = face.points
            val projected = ArrayList<ProjectedPoint>(pts.size)
            var allVisible = true
            var depthSum = 0f
            for (i in pts.indices) {
                val p2 = proj.project(pts[i])
                if (!p2.visible) {
                    allVisible = false
                    break
                }
                projected.add(p2)
                depthSum += p2.viewDepth
            }
            if (allVisible && projected.isNotEmpty()) {
                sortedFaces.add(ProjectedFace(face, projected, depthSum / projected.size))
            }
        }
        sortedFaces.sortByDescending { it.avgDepth }

        val facePaint = scratch.facePaint
        val curvedMeshPaint = scratch.curvedMeshPaint
        val path = scratch.scratchPath

        for (pf in sortedFaces) {
            val face = pf.face
            val pts2d = pf.pts2d
            path.rewind()
            path.moveTo(pts2d[0].x, pts2d[0].y)
            for (i in 1 until pts2d.size) path.lineTo(pts2d[i].x, pts2d[i].y)
            path.close()

            if (face.isSection || face.isCustomPlane) {
                facePaint.color = face.fillColorArgb
                canvas.drawPath(path, facePaint)
            } else if (face.isCurvedMesh) {
                val viewDir = (camPos - face.center).normalized()
                val facing = face.normal.dot(viewDir)
                val isFront = facing >= 0f
                val effectiveNormal = if (isFront) face.normal else -face.normal
                val halfLambert = ((effectiveNormal.dot(lightDir) + 1f) * 0.5f).coerceIn(0f, 1f)
                val halfVec = (lightDir + viewDir).normalized()
                val spec = if (isFront) max(0f, effectiveNormal.dot(halfVec)).pow(10f) else 0f
                val rim = if (isFront) (1f - abs(facing)).pow(2.4f) * 0.22f else 0f

                val baseAlpha = if (isFront) (44 + halfLambert * 34 + spec * 45 + rim * 38) else (24 + halfLambert * 18)
                val aInt = baseAlpha.toInt().coerceIn(20, 128)
                val rInt = (74 + halfLambert * 58 + spec * 115 + rim * 40).toInt().coerceIn(0, 255)
                val gInt = (84 + halfLambert * 62 + spec * 120 + rim * 55).toInt().coerceIn(0, 255)
                val bInt = (186 + halfLambert * 64 + spec * 50 + rim * 30).toInt().coerceIn(0, 255)
                curvedMeshPaint.color = Color.argb(aInt, rInt, gInt, bInt)
                canvas.drawPath(path, curvedMeshPaint)
            } else {
                val viewDir = (camPos - face.center).normalized()
                val isFront = face.normal.dot(viewDir) >= 0f
                val ndl = ((face.normal.dot(lightDir) + 1f) * 0.5f).coerceIn(0f, 1f)
                val shade = (0.54f + 0.46f * ndl).coerceIn(0.45f, 1f)
                val baseA = ((if (isFront) 62f else 36f) * (0.85f + 0.25f * shade)).toInt().coerceIn(24, 92)
                val r = (99 * shade).toInt().coerceIn(0, 255)
                val g = (102 * shade).toInt().coerceIn(0, 255)
                val b = (241 * shade).toInt().coerceIn(0, 255)
                facePaint.color = Color.argb(baseA, r, g, b)
                canvas.drawPath(path, facePaint)
            }
        }
        sortedFaces.clear()

        // 5. Smart Hidden-Edge Dashing for Solid Edges
        val solidEdgePaint = scratch.solidEdgePaint.apply {
            strokeWidth = 1.7f * density
            color = if (transparentBackground) 0xFF0F172A.toInt() else 0xFFF8FAFC.toInt()
        }
        val dashedEdgePaint = scratch.dashedEdgePaint.apply {
            strokeWidth = 1.35f * density
            color = if (transparentBackground) 0xFF475569.toInt() else 0xFF94A3B8.toInt()
            pathEffect = scratch.dash75
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

        // 5b. Round Body Silhouette Generators (Exact 3D perspective tangent outlines for cylinder/cone/sphere)
        scene.roundBodyHint?.let { drawRoundBodySilhouettes(canvas, proj, camPos, it, solidEdgePaint, scratch) }

        // 6. Section, Dynamic 3-Point Section & Custom Plane Borders
        scene.sectionBorder?.let { pts ->
            val secBorderPaint = scratch.strokePaint.apply {
                strokeWidth = 2.0f * density
                color = 0xFFFBBF24.toInt()
                pathEffect = null
            }
            drawClosedPolyline(canvas, proj, pts, secBorderPaint, scratch)
        }

        scene.dynamicSectionBorder?.let { pts ->
            val dynBorderPaint = scratch.strokePaint.apply {
                strokeWidth = 2.4f * density
                color = 0xFFF97316.toInt()
                pathEffect = null
            }
            drawClosedPolyline(canvas, proj, pts, dynBorderPaint, scratch)
        }

        if (scene.customPlaneBorders.isNotEmpty()) {
            val planeBorderPaint = scratch.dashedStrokePaint.apply {
                strokeWidth = 1.6f * density
                color = 0xFF34D399.toInt()
                pathEffect = scratch.dash64
            }
            scene.customPlaneBorders.forEach { pts ->
                drawClosedPolyline(canvas, proj, pts, planeBorderPaint, scratch)
            }
        }

        // 7. Altitude & Right-Angle Symbol
        if (snap.altitude) {
            scene.altitude?.let { drawAltitude(canvas, proj, it, scene.pointRegistry, density, scratch) }
        }

        // 8. Oxyz Coordinate Axes
        if (snap.showAxes) {
            val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)
            drawCoordinateAxes(canvas, proj, origin, density, snap.labels, scratch)
        }

        // 9. Custom Lines & 3D Vectors
        for (ln in snap.customLines) {
            val p1 = scene.pointRegistry[ln.p1] ?: continue
            val p2 = scene.pointRegistry[ln.p2] ?: continue
            val colorInt = parseHexColor(ln.colorHex, 0xFF38BDF8.toInt())
            drawCustomLineOrVector(canvas, proj, p1, p2, colorInt, ln.style, density, scratch)
        }

        // 10. Pink Constraint Rail (when dragging along vertical Oz or segment AB)
        uiState.constraintRay?.let { ray ->
            val a = proj.project(ray.p1)
            val b = proj.project(ray.p2)
            if (a.visible && b.visible) {
                val railPaint = scratch.strokePaint.apply {
                    strokeWidth = 2.2f * density
                    color = 0xFFF472B6.toInt()
                    pathEffect = null
                }
                canvas.drawLine(a.x, a.y, b.x, b.y, railPaint)
            }
        }

        // 11. Magnetic Snap Visuals (Emerald Ring + 3 Coordinate Projection Guides)
        uiState.snapVisual.pos?.let { snapPos ->
            val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)
            drawSnapGuides(canvas, proj, snapPos, origin, density, scratch)
        }

        // 12. Edge Preview Dot (when adding point)
        uiState.previewDot?.let { dotPos ->
            val p = proj.project(dotPos)
            if (p.visible) {
                val fill = scratch.fillPaint
                fill.color = 0x55FBBF24
                canvas.drawCircle(p.x, p.y, 10f * density, fill)
                fill.color = 0xFFFBBF24.toInt()
                canvas.drawCircle(p.x, p.y, 5.5f * density, fill)
            }
        }

        // 13. Vertex Nodes
        val dotPaint = scratch.fillPaint
        for ((name, pos) in scene.pointRegistry) {
            val p = proj.project(pos)
            if (!p.visible) continue
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
                transparentBackground -> 0xFF0F172A.toInt()
                else -> 0xFFCBD5E1.toInt()
            }
            canvas.drawCircle(p.x, p.y, radius, dotPaint)
        }

        // 14. Vertex Pill Labels
        if (snap.labels) {
            drawVertexLabels(canvas, proj, uiState, density, scratch)
        }

        // 15. Optional Export Metrics Card (Bottom-Left for opaque PNG Export)
        if (includeExportCard && !transparentBackground) {
            drawExportInfoCard(canvas, width, height, density, uiState, exportTitle, scratch)
        }
    }

    private fun drawGrid(canvas: Canvas, proj: CameraProjection, density: Float, scratch: RenderScratch3D) {
        val minorPaint = scratch.minorGridPaint.apply { strokeWidth = 1f * density }
        val majorPaint = scratch.majorGridPaint.apply { strokeWidth = 1.3f * density }
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

    private fun drawRoundBodySilhouettes(
        canvas: Canvas,
        proj: CameraProjection,
        camPos: Vec3,
        hint: RoundBodyHint,
        paint: Paint,
        scratch: RenderScratch3D
    ) {
        when (hint) {
            is RoundBodyHint.Cylinder -> {
                val axisMid = (hint.centerBottom + hint.centerTop) * 0.5f
                val axisVec = hint.centerTop - hint.centerBottom
                fun evalFacing(theta: Float): Float {
                    val c = cos(theta)
                    val s = sin(theta)
                    val botPt = Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius)
                    val midPt = botPt + axisVec * 0.5f
                    val tangent = Vec3(-s, 0f, c)
                    var n = tangent.cross(axisVec).normalized()
                    if (n.dot(midPt - axisMid) < 0f) n = -n
                    return n.dot(camPos - midPt)
                }

                val roots = findSilhouetteRoots(::evalFacing)
                for (theta in roots) {
                    val c = cos(theta)
                    val s = sin(theta)
                    val bPt = proj.project(Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius))
                    val tPt = proj.project(Vec3(hint.centerTop.x + c * hint.radius, hint.centerTop.y, hint.centerTop.z + s * hint.radius))
                    if (bPt.visible && tPt.visible) {
                        canvas.drawLine(bPt.x, bPt.y, tPt.x, tPt.y, paint)
                    }
                }
            }
            is RoundBodyHint.Cone -> {
                val axisMid = (hint.centerBottom + hint.apex) * 0.5f
                fun evalFacing(theta: Float): Float {
                    val c = cos(theta)
                    val s = sin(theta)
                    val basePt = Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius)
                    val genVec = hint.apex - basePt
                    val tangent = Vec3(-s, 0f, c)
                    var n = tangent.cross(genVec).normalized()
                    val midPt = (basePt + hint.apex) * 0.5f
                    if (n.dot(midPt - axisMid) < 0f) n = -n
                    return n.dot(camPos - midPt)
                }

                val apex2d = proj.project(hint.apex)
                if (apex2d.visible) {
                    val roots = findSilhouetteRoots(::evalFacing)
                    for (theta in roots) {
                        val c = cos(theta)
                        val s = sin(theta)
                        val bPt = proj.project(Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius))
                        if (bPt.visible) {
                            canvas.drawLine(apex2d.x, apex2d.y, bPt.x, bPt.y, paint)
                        }
                    }
                }
            }
            is RoundBodyHint.Sphere -> {
                val toCam = camPos - hint.center
                val d = toCam.length()
                val r = hint.radius
                if (d > r + 0.02f) {
                    val dir = toCam / d
                    val cSil = hint.center + dir * ((r * r) / d)
                    val ratio = (r / d).coerceIn(0f, 0.999f)
                    val rSil = r * sqrt(1f - ratio * ratio)
                    val refUp = if (abs(dir.y) > 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
                    val u = refUp.cross(dir).normalized()
                    val v = dir.cross(u).normalized()
                    val segs = 64
                    val path = scratch.scratchPath
                    path.rewind()
                    var started = false
                    for (i in 0..segs) {
                        val t = (i.toFloat() / segs) * (2.0 * PI).toFloat()
                        val pt3 = cSil + u * (cos(t) * rSil) + v * (sin(t) * rSil)
                        val p2 = proj.project(pt3)
                        if (p2.visible) {
                            if (!started) {
                                path.moveTo(p2.x, p2.y)
                                started = true
                            } else {
                                path.lineTo(p2.x, p2.y)
                            }
                        }
                    }
                    if (started) {
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
            }
        }
    }

    private fun findSilhouetteRoots(evalFacing: (Float) -> Float): List<Float> {
        val steps = 72
        val stepSize = (2.0 * PI / steps).toFloat()
        val roots = ArrayList<Float>(4)
        var prevT = 0f
        var prevV = evalFacing(0f)
        for (i in 1..steps) {
            val curT = i * stepSize
            val curV = evalFacing(curT)
            if ((prevV <= 0f && curV > 0f) || (prevV >= 0f && curV < 0f)) {
                var lo = prevT
                var hi = curT
                var loV = prevV
                repeat(10) {
                    val mid = (lo + hi) * 0.5f
                    val midV = evalFacing(mid)
                    if ((loV <= 0f && midV > 0f) || (loV >= 0f && midV < 0f)) {
                        hi = mid
                    } else {
                        lo = mid
                        loV = midV
                    }
                }
                roots.add((lo + hi) * 0.5f)
            }
            prevT = curT
            prevV = curV
        }
        return roots
    }

    private fun drawClosedPolyline(
        canvas: Canvas,
        proj: CameraProjection,
        pts: List<Vec3>,
        paint: Paint,
        scratch: RenderScratch3D
    ) {
        if (pts.size < 3) return
        val path = scratch.scratchPath
        path.rewind()
        for (i in pts.indices) {
            val p2 = proj.project(pts[i])
            if (!p2.visible) return
            if (i == 0) path.moveTo(p2.x, p2.y) else path.lineTo(p2.x, p2.y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawAltitude(
        canvas: Canvas,
        proj: CameraProjection,
        alt: AltitudeVisual,
        registry: Map<String, Vec3>,
        density: Float,
        scratch: RenderScratch3D
    ) {
        val top2d = proj.project(alt.top)
        val foot2d = proj.project(alt.foot)
        if (!top2d.visible || !foot2d.visible) return

        val altPaint = scratch.dashedStrokePaint.apply {
            strokeWidth = 1.6f * density
            color = 0xFFF43F5E.toInt()
            pathEffect = scratch.dash64
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
            val raPaint = scratch.strokePaint.apply {
                strokeWidth = 1.5f * density
                color = 0xFFF43F5E.toInt()
                pathEffect = null
            }
            val path = scratch.scratchPath
            path.rewind()
            path.moveTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y)
            canvas.drawPath(path, raPaint)
        }
    }

    private fun drawCoordinateAxes(
        canvas: Canvas,
        proj: CameraProjection,
        origin: Vec3,
        density: Float,
        showLabels: Boolean,
        scratch: RenderScratch3D
    ) {
        val axisLen = 6.2f
        drawSingleAxis(canvas, proj, origin + Vec3(-2f, 0f, 0f), origin + Vec3(axisLen, 0f, 0f), origin, Vec3(1f, 0f, 0f), 0xFFEF4444.toInt(), "x", density, showLabels, scratch)
        drawSingleAxis(canvas, proj, origin + Vec3(0f, 0f, -2f), origin + Vec3(0f, 0f, axisLen), origin, Vec3(0f, 0f, 1f), 0xFF22C55E.toInt(), "y", density, showLabels, scratch)
        drawSingleAxis(canvas, proj, origin + Vec3(0f, -1.2f, 0f), origin + Vec3(0f, axisLen + 0.8f, 0f), origin, Vec3(0f, 1f, 0f), 0xFF3B82F6.toInt(), "z", density, showLabels, scratch)
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
        showLabels: Boolean,
        scratch: RenderScratch3D
    ) {
        val s2 = proj.project(start)
        val e2 = proj.project(end)
        if (!s2.visible || !e2.visible) return

        val linePaint = scratch.strokePaint.apply {
            strokeWidth = 1.9f * density
            color = colorArgb
            pathEffect = null
        }
        canvas.drawLine(s2.x, s2.y, e2.x, e2.y, linePaint)

        // Arrowhead
        drawArrowHead2D(canvas, s2.x, s2.y, e2.x, e2.y, colorArgb, 10f * density, scratch)

        // Unit ticks 1..5
        val tickPaint = scratch.fillPaint
        tickPaint.color = colorArgb
        for (i in 1..5) {
            val t = proj.project(origin + unitDir * i.toFloat())
            if (t.visible) canvas.drawCircle(t.x, t.y, 2.5f * density, tickPaint)
        }

        // Axis label pill
        if (showLabels) {
            val lblPt = proj.project(end + unitDir * 0.42f)
            if (lblPt.visible) {
                val txtPaint = scratch.axisLabelPaint.apply {
                    color = colorArgb
                    textSize = 13f * density
                }
                val boxW = 22f * density
                val boxH = 20f * density
                val rect = scratch.scratchRect
                rect.set(lblPt.x - boxW / 2f, lblPt.y - boxH / 2f, lblPt.x + boxW / 2f, lblPt.y + boxH / 2f)
                val bg = scratch.pillBgPaint.apply { color = 0xEB090D16.toInt() }
                val border = scratch.pillBorderPaint.apply {
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
        density: Float,
        scratch: RenderScratch3D
    ) {
        val a = proj.project(p1)
        val b = proj.project(p2)
        if (!a.visible || !b.visible) return

        val paint = if (style.isDashed) scratch.dashedStrokePaint else scratch.strokePaint
        paint.strokeWidth = 2.3f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = colorArgb
        paint.pathEffect = if (style.isDashed) scratch.dash75 else null
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        if (style.isVector) {
            drawArrowHead2D(canvas, a.x, a.y, b.x, b.y, colorArgb, 11.5f * density, scratch)
        }
    }

    private fun drawArrowHead2D(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        colorArgb: Int,
        headSize: Float,
        scratch: RenderScratch3D
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

    private fun drawSnapGuides(
        canvas: Canvas,
        proj: CameraProjection,
        pos: Vec3,
        origin: Vec3,
        density: Float,
        scratch: RenderScratch3D
    ) {
        val p = proj.project(pos)
        if (p.visible) {
            val ringPaint = scratch.strokePaint.apply {
                strokeWidth = 2.4f * density
                color = 0xFF10B981.toInt()
                pathEffect = null
            }
            canvas.drawCircle(p.x, p.y, 12f * density, ringPaint)
        }

        val guidePaint = scratch.dashedStrokePaint.apply {
            strokeWidth = 1.4f * density
            color = 0xFF10B981.toInt()
            pathEffect = scratch.dash54
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

    private fun drawVertexLabels(
        canvas: Canvas,
        proj: CameraProjection,
        uiState: GeometryUiState,
        density: Float,
        scratch: RenderScratch3D
    ) {
        val snap = uiState.snapshot
        val scene = uiState.scene
        val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)

        val textPaint = scratch.vertexTextPaint
        val bgPaint = scratch.pillBgPaint
        val borderPaint = scratch.pillBorderPaint.apply { strokeWidth = 1.15f * density }
        val rect = scratch.scratchRect
        val cornerR = 5.5f * density

        for ((name, pos) in scene.pointRegistry) {
            val p = proj.project(pos)
            if (!p.visible) continue

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

            rect.set(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)

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

            canvas.drawRoundRect(rect, cornerR, cornerR, bgPaint)
            canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)
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
        exportTitle: String,
        scratch: RenderScratch3D
    ) {
        val m = uiState.scene.metrics
        fun f2(v: Float) = String.format(java.util.Locale.US, "%.2f", v)
        val infoLine = "h = ${f2(m.realH)}  |  S(đáy) = ${f2(m.sDay)}  |  S(xq) = ${f2(m.sXq)}  |  S(tp) = ${f2(m.sTp)}  |  V = ${f2(m.vol)}"

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

        val cardW = min(width - 32f * density, max(infoPaint.measureText(infoLine) + 28f * density, 340f * density))
        val cardH = 54f * density
        val cardX = 16f * density
        val cardY = height - cardH - 16f * density
        val rect = scratch.scratchRect
        rect.set(cardX, cardY, cardX + cardW, cardY + cardH)

        val bg = scratch.pillBgPaint.apply { color = 0xE60F172A.toInt() }
        val border = scratch.pillBorderPaint.apply {
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

    fun parseHexColor(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
