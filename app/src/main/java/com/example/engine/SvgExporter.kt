package com.example.engine

import com.example.geo2d.*
import java.util.Locale
import kotlin.math.*

/**
 * Resolution-independent Vector SVG exporter for both 3D Spatial Geometry (Oxyz)
 * and 2D Plane Geometry / Function Graphs (Oxy), supporting transparent or dark backgrounds.
 */
object SvgExporter {

    private fun f(v: Float): String = String.format(Locale.US, "%.2f", v)
    private fun fd(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun argbToCssRgba(argb: Int): String {
        val a = ((argb ushr 24) and 0xFF) / 255.0
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", r, g, b, a)
    }

    private fun svgArrowPolygon(x1: Float, y1: Float, x2: Float, y2: Float, headSize: Float, fillCss: String): String {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val spread = 0.42
        val lx = x2 - (headSize * cos(angle - spread)).toFloat()
        val ly = y2 - (headSize * sin(angle - spread)).toFloat()
        val rx = x2 - (headSize * cos(angle + spread)).toFloat()
        val ry = y2 - (headSize * sin(angle + spread)).toFloat()
        return """  <polygon points="${f(x2)},${f(y2)} ${f(lx)},${f(ly)} ${f(rx)},${f(ry)}" fill="$fillCss"/>"""
    }

    /**
     * Exports the current 3D spatial geometry scene as a clean vector SVG.
     */
    fun export3DToSvg(
        width: Int,
        height: Int,
        uiState: GeometryUiState,
        transparentBg: Boolean = true
    ): String {
        val w = width.coerceIn(600, 2400).toFloat()
        val h = height.coerceIn(600, 2400).toFloat()
        val snap = uiState.snapshot
        val scene = uiState.scene
        val camPos = snap.camera.cameraPosition()
        val proj = CameraProjection(camPos, snap.camera.target, w, h)
        val lightDir = Vec3(10f, 18f, 12f).normalized()

        val sb = StringBuilder(8192)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${w.toInt()}" height="${h.toInt()}" viewBox="0 0 ${w.toInt()} ${h.toInt()}">""")

        if (!transparentBg) {
            sb.appendLine("""  <defs><radialGradient id="bg3d" cx="50%" cy="50%" r="72%"><stop offset="0%" stop-color="#1E293B"/><stop offset="100%" stop-color="#090D16"/></radialGradient></defs>""")
            sb.appendLine("""  <rect width="100%" height="100%" fill="url(#bg3d)"/>""")
        }

        // 1. Painter-sorted faces
        data class ProjFace(val face: RenderFace, val pts: List<ProjectedPoint>, val depth: Float)
        val sortedFaces = scene.faces.mapNotNull { face ->
            val pts = face.points.map { proj.project(it) }
            if (pts.isEmpty() || pts.any { !it.visible }) null
            else ProjFace(face, pts, pts.sumOf { it.viewDepth.toDouble() }.toFloat() / pts.size)
        }.sortedByDescending { it.depth }

        for (pf in sortedFaces) {
            val face = pf.face
            val pointsAttr = pf.pts.joinToString(" ") { "${f(it.x)},${f(it.y)}" }
            val fillCss = when {
                face.isSection || face.isCustomPlane -> argbToCssRgba(face.fillColorArgb)
                face.isCurvedMesh -> {
                    val viewDir = (camPos - face.center).normalized()
                    val isFront = face.normal.dot(viewDir) >= 0f
                    val effN = if (isFront) face.normal else -face.normal
                    val hl = ((effN.dot(lightDir) + 1f) * 0.5f).coerceIn(0f, 1f)
                    val a = (if (isFront) 0.19 + hl * 0.14 else 0.10 + hl * 0.08).coerceIn(0.08, 0.45)
                    val r = (74 + hl * 62).toInt().coerceIn(0, 255)
                    val g = (84 + hl * 68).toInt().coerceIn(0, 255)
                    val b = (186 + hl * 64).toInt().coerceIn(0, 255)
                    String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", r, g, b, a)
                }
                else -> {
                    val viewDir = (camPos - face.center).normalized()
                    val isFront = face.normal.dot(viewDir) >= 0f
                    val ndl = ((face.normal.dot(lightDir) + 1f) * 0.5f).coerceIn(0f, 1f)
                    val shade = (0.54f + 0.46f * ndl).coerceIn(0.45f, 1f)
                    val a = ((if (isFront) 0.24 else 0.14) * (0.85 + 0.25 * shade)).coerceIn(0.10, 0.36)
                    val r = (99 * shade).toInt().coerceIn(0, 255)
                    val g = (102 * shade).toInt().coerceIn(0, 255)
                    val b = (241 * shade).toInt().coerceIn(0, 255)
                    String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", r, g, b, a)
                }
            }
            sb.appendLine("""  <polygon points="$pointsAttr" fill="$fillCss"/>""")
        }

        // 2. Smart solid / dashed edges
        val solidColor = if (transparentBg) "#0F172A" else "#F8FAFC"
        val dashedColor = if (transparentBg) "#475569" else "#94A3B8"
        val customLineSegments = if (snap.customLines.isEmpty()) emptyList() else snap.customLines.mapNotNull { ln ->
            val v1 = scene.pointRegistry[ln.p1]
            val v2 = scene.pointRegistry[ln.p2]
            if (v1 != null && v2 != null) v1 to v2 else null
        }
        for (edge in scene.edges) {
            if (customLineSegments.any { (c1, c2) ->
                    (c1.distanceToSq(edge.p1) < 1e-4f && c2.distanceToSq(edge.p2) < 1e-4f) ||
                        (c1.distanceToSq(edge.p2) < 1e-4f && c2.distanceToSq(edge.p1) < 1e-4f)
                }
            ) continue
            val a = proj.project(edge.p1)
            val b = proj.project(edge.p2)
            if (!a.visible || !b.visible) continue
            val isDashed = when (edge.forceStyle) {
                EdgeRenderStyle.ALWAYS_SOLID -> false
                EdgeRenderStyle.ALWAYS_DASHED -> true
                EdgeRenderStyle.SMART -> if (!snap.dashed) false else {
                    var vis = false
                    for (i in edge.normals.indices) {
                        if (edge.normals[i].dot(camPos - edge.centers[i]) > 0f) {
                            vis = true; break
                        }
                    }
                    !vis
                }
            }
            val stroke = edge.colorArgb?.let { argbToCssRgba(it) } ?: if (isDashed) dashedColor else solidColor
            val sw = if (isDashed) "2.2" else "2.8"
            val dashAttr = if (isDashed) """ stroke-dasharray="10,7"""" else ""
            sb.appendLine("""  <line x1="${f(a.x)}" y1="${f(a.y)}" x2="${f(b.x)}" y2="${f(b.y)}" stroke="$stroke" stroke-width="$sw" stroke-linecap="round"$dashAttr/>""")
        }

        // 2b. Round Body Silhouette Outlines (Cylinder, Cone, Sphere)
        scene.roundBodyHint?.let { hint ->
            when (hint) {
                is RoundBodyHint.Cylinder -> {
                    val axisMid = (hint.centerBottom + hint.centerTop) * 0.5f
                    val axisVec = hint.centerTop - hint.centerBottom
                    val roots = findSilhouetteRoots { theta ->
                        val c = cos(theta); val s = sin(theta)
                        val botPt = Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius)
                        val midPt = botPt + axisVec * 0.5f
                        var n = Vec3(-s, 0f, c).cross(axisVec).normalized()
                        if (n.dot(midPt - axisMid) < 0f) n = -n
                        n.dot(camPos - midPt)
                    }
                    for (theta in roots) {
                        val c = cos(theta); val s = sin(theta)
                        val bPt = proj.project(Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius))
                        val tPt = proj.project(Vec3(hint.centerTop.x + c * hint.radius, hint.centerTop.y, hint.centerTop.z + s * hint.radius))
                        if (bPt.visible && tPt.visible) {
                            sb.appendLine("""  <line x1="${f(bPt.x)}" y1="${f(bPt.y)}" x2="${f(tPt.x)}" y2="${f(tPt.y)}" stroke="$solidColor" stroke-width="2.8" stroke-linecap="round"/>""")
                        }
                    }
                }
                is RoundBodyHint.Cone -> {
                    val axisMid = (hint.centerBottom + hint.apex) * 0.5f
                    val apex2d = proj.project(hint.apex)
                    if (apex2d.visible) {
                        val roots = findSilhouetteRoots { theta ->
                            val c = cos(theta); val s = sin(theta)
                            val basePt = Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius)
                            var n = Vec3(-s, 0f, c).cross(hint.apex - basePt).normalized()
                            val midPt = (basePt + hint.apex) * 0.5f
                            if (n.dot(midPt - axisMid) < 0f) n = -n
                            n.dot(camPos - midPt)
                        }
                        for (theta in roots) {
                            val c = cos(theta); val s = sin(theta)
                            val bPt = proj.project(Vec3(hint.centerBottom.x + c * hint.radius, hint.centerBottom.y, hint.centerBottom.z + s * hint.radius))
                            if (bPt.visible) {
                                sb.appendLine("""  <line x1="${f(apex2d.x)}" y1="${f(apex2d.y)}" x2="${f(bPt.x)}" y2="${f(bPt.y)}" stroke="$solidColor" stroke-width="2.8" stroke-linecap="round"/>""")
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
                        val ptsStr = (0 until segs).mapNotNull { i ->
                            val t = (i.toFloat() / segs) * (2.0 * PI).toFloat()
                            val p2 = proj.project(cSil + u * (cos(t) * rSil) + v * (sin(t) * rSil))
                            if (p2.visible) "${f(p2.x)},${f(p2.y)}" else null
                        }
                        if (ptsStr.size >= 16) {
                            sb.appendLine("""  <polygon points="${ptsStr.joinToString(" ")}" fill="none" stroke="$solidColor" stroke-width="2.8" stroke-linejoin="round"/>""")
                        }
                    }
                }
            }
        }

        // 3. Section & dynamic section borders
        fun appendClosedPoly(pts: List<Vec3>, stroke: String, sw: String, dash: String = "") {
            val p2 = pts.map { proj.project(it) }
            if (p2.size < 3 || p2.any { !it.visible }) return
            val attr = p2.joinToString(" ") { "${f(it.x)},${f(it.y)}" }
            val dashStr = if (dash.isNotEmpty()) """ stroke-dasharray="$dash"""" else ""
            sb.appendLine("""  <polygon points="$attr" fill="none" stroke="$stroke" stroke-width="$sw" stroke-linejoin="round"$dashStr/>""")
        }
        scene.sectionBorder?.let { appendClosedPoly(it, "#FBBF24", "3.2") }
        scene.dynamicSectionBorder?.let { appendClosedPoly(it, "#F97316", "3.8") }
        scene.customPlaneBorders.forEach { appendClosedPoly(it, "#34D399", "2.6", "9,6") }

        // 4. Altitude
        if (snap.altitude) {
            scene.altitude?.let { alt ->
                val t2 = proj.project(alt.top)
                val f2 = proj.project(alt.foot)
                if (t2.visible && f2.visible) {
                    sb.appendLine("""  <line x1="${f(t2.x)}" y1="${f(t2.y)}" x2="${f(f2.x)}" y2="${f(f2.y)}" stroke="#F43F5E" stroke-width="2.5" stroke-dasharray="9,6"/>""")
                }
            }
        }

        // 5. Oxyz axes
        if (snap.showAxes) {
            val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)
            val axes = listOf(
                Triple(origin + Vec3(-2f, 0f, 0f), origin + Vec3(6.2f, 0f, 0f), "#EF4444" to "x"),
                Triple(origin + Vec3(0f, 0f, -2f), origin + Vec3(0f, 0f, 6.2f), "#22C55E" to "y"),
                Triple(origin + Vec3(0f, -1.2f, 0f), origin + Vec3(0f, 7.0f, 0f), "#3B82F6" to "z")
            )
            for ((st, en, meta) in axes) {
                val p1 = proj.project(st)
                val p2 = proj.project(en)
                if (p1.visible && p2.visible) {
                    sb.appendLine("""  <line x1="${f(p1.x)}" y1="${f(p1.y)}" x2="${f(p2.x)}" y2="${f(p2.y)}" stroke="${meta.first}" stroke-width="2.6"/>""")
                    sb.appendLine(svgArrowPolygon(p1.x, p1.y, p2.x, p2.y, 14f, meta.first))
                    if (snap.labels) {
                        sb.appendLine("""  <text x="${f(p2.x + 12f)}" y="${f(p2.y + 4f)}" fill="${meta.first}" font-family="serif" font-style="italic" font-weight="bold" font-size="22">${meta.second}</text>""")
                    }
                }
            }
        }

        // 6. Custom lines & vectors (with real 3D hidden-line/vector occlusion)
        for (ln in snap.customLines) {
            val v1 = scene.pointRegistry[ln.p1] ?: continue
            val v2 = scene.pointRegistry[ln.p2] ?: continue
            val a = proj.project(v1)
            val b = proj.project(v2)
            if (!a.visible || !b.visible) continue
            val effectiveDashed = ln.style.isDashed || (snap.dashed && Geo3DMathHelpers.isSegmentOccludedBySolid(
                p1 = v1,
                p2 = v2,
                camPos = camPos,
                occluderFaces = scene.occluderFaces,
                roundBodyHint = scene.roundBodyHint
            ))
            val isNeutral = Geo3DMathHelpers.isNeutralColorHex(ln.colorHex)
            val strokeCss = if (isNeutral) {
                if (effectiveDashed) dashedColor else solidColor
            } else {
                escapeXml(ln.colorHex)
            }
            val sw = if (isNeutral) (if (effectiveDashed) "2.2" else "2.8") else "3.2"
            val dashAttr = if (effectiveDashed) """ stroke-dasharray="10,7"""" else ""
            sb.appendLine("""  <line x1="${f(a.x)}" y1="${f(a.y)}" x2="${f(b.x)}" y2="${f(b.y)}" stroke="$strokeCss" stroke-width="$sw" stroke-linecap="round"$dashAttr/>""")
            if (ln.style.isVector) {
                sb.appendLine(svgArrowPolygon(a.x, a.y, b.x, b.y, 15f, strokeCss))
            }
        }

        // 7. Vertices & Labels
        val origin = ShapeBuilder.getOriginWorld(scene.pointRegistry, snap.originPoint)
        for ((name, pos) in scene.pointRegistry) {
            val p = proj.project(pos)
            if (!p.visible) continue
            val dotFill = when {
                snap.showAxes && snap.originPoint == name -> "#EF4444"
                scene.customPointNames.contains(name) -> "#FBBF24"
                transparentBg -> "#0F172A"
                else -> "#CBD5E1"
            }
            sb.appendLine("""  <circle cx="${f(p.x)}" cy="${f(p.y)}" r="5" fill="$dotFill"/>""")
            if (snap.labels) {
                val lbl = if (snap.showCoordsOnLabels) {
                    val m = ShapeBuilder.worldToMath(pos, origin)
                    "$name(${formatMathNumber(m.x)}; ${formatMathNumber(m.y)}; ${formatMathNumber(m.z)})"
                } else name
                val txtCol = if (transparentBg) "#0F172A" else "#F8FAFC"
                sb.appendLine("""  <text x="${f(p.x)}" y="${f(p.y - 12f)}" text-anchor="middle" fill="$txtCol" font-family="sans-serif" font-weight="bold" font-size="18">${escapeXml(lbl)}</text>""")
            }
        }

        sb.appendLine("</svg>")
        return sb.toString()
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
                    if ((loV <= 0f && midV > 0f) || (loV >= 0f && midV < 0f)) hi = mid else { lo = mid; loV = midV }
                }
                roots.add((lo + hi) * 0.5f)
            }
            prevT = curT
            prevV = curV
        }
        return roots
    }

    /**
     * Exports the current 2D Oxy coordinate plane, function graphs, asymptotes, key points,
     * and geometric constructions as a crisp vector SVG.
     */
    fun export2DToSvg(
        width: Int,
        height: Int,
        uiState: Geo2DUiState,
        transparentBg: Boolean = true
    ): String {
        val w = width.coerceIn(600, 2400).toFloat()
        val h = height.coerceIn(600, 2400).toFloat()
        val snap = uiState.snapshot
        val cam = snap.camera
        val m = snap.paramM
        val (xMin, yMin) = Geo2DRenderer.screenToWorldDouble(0f, h, cam, w, h)
        val (xMax, yMax) = Geo2DRenderer.screenToWorldDouble(w, 0f, cam, w, h)
        val majorStep = Geo2DRenderer.niceGridStep(cam.pixelsPerUnit, 1.5f)
        val xSpan = max(1e-9, xMax - xMin)
        val ySpan = max(1e-9, yMax - yMin)

        fun toSx(wx: Double): Float = (w * 0.5 + (wx - cam.centerX) * cam.pixelsPerUnit).toFloat()
        fun toSy(wy: Double): Float = (h * 0.5 - (wy - cam.centerY) * cam.pixelsPerUnit).toFloat()

        val sb = StringBuilder(12288)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${w.toInt()}" height="${h.toInt()}" viewBox="0 0 ${w.toInt()} ${h.toInt()}">""")

        if (!transparentBg) {
            sb.appendLine("""  <rect width="100%" height="100%" fill="#0D1322"/>""")
            if (snap.showGrid) {
                var gx = floor(xMin / majorStep) * majorStep
                var gCount = 0
                while (gx <= xMax && gCount++ < 140) {
                    val sx = toSx(gx)
                    sb.appendLine("""  <line x1="${f(sx)}" y1="0" x2="${f(sx)}" y2="${f(h)}" stroke="#1E293B" stroke-width="1"/>""")
                    gx += majorStep
                }
                var gy = floor(yMin / majorStep) * majorStep
                gCount = 0
                while (gy <= yMax && gCount++ < 140) {
                    val sy = toSy(gy)
                    sb.appendLine("""  <line x1="0" y1="${f(sy)}" x2="${f(w)}" y2="${f(sy)}" stroke="#1E293B" stroke-width="1"/>""")
                    gy += majorStep
                }
            }
        }

        // Axes
        val axisCol = if (transparentBg) "#1E293B" else "#94A3B8"
        val textCol = if (transparentBg) "#0F172A" else "#E2E8F0"
        if (snap.showAxes) {
            val ox = toSx(0.0)
            val oy = toSy(0.0)
            if (oy in 0f..h) {
                sb.appendLine("""  <line x1="0" y1="${f(oy)}" x2="${f(w - 6f)}" y2="${f(oy)}" stroke="$axisCol" stroke-width="2.2"/>""")
                sb.appendLine(svgArrowPolygon(w - 24f, oy, w - 4f, oy, 12f, axisCol))
                sb.appendLine("""  <text x="${f(w - 22f)}" y="${f(oy - 12f)}" fill="#DC2626" font-family="serif" font-style="italic" font-weight="bold" font-size="20">x</text>""")
            }
            if (ox in 0f..w) {
                sb.appendLine("""  <line x1="${f(ox)}" y1="${f(h)}" x2="${f(ox)}" y2="6" stroke="$axisCol" stroke-width="2.2"/>""")
                sb.appendLine(svgArrowPolygon(ox, 24f, ox, 4f, 12f, axisCol))
                sb.appendLine("""  <text x="${f(ox + 12f)}" y="26" fill="#16A34A" font-family="serif" font-style="italic" font-weight="bold" font-size="20">y</text>""")
            }
            var gx = floor(xMin / majorStep) * majorStep
            var guard = 0
            val clampedY = oy.coerceIn(28f, h - 24f)
            while (gx <= xMax && guard++ < 100) {
                if (abs(gx) > majorStep * 0.1) {
                    val sx = toSx(gx)
                    if (sx in 24f..(w - 28f)) {
                        if (oy in 0f..h) sb.appendLine("""  <line x1="${f(sx)}" y1="${f(oy - 4f)}" x2="${f(sx)}" y2="${f(oy + 4f)}" stroke="$axisCol" stroke-width="1.6"/>""")
                        sb.appendLine("""  <text x="${f(sx)}" y="${f(clampedY + 18f)}" text-anchor="middle" fill="$axisCol" font-family="monospace" font-size="14">${escapeXml(Geo2DMathHelpers.formatAxisTick(gx))}</text>""")
                    }
                }
                gx += majorStep
            }
            var gy = floor(yMin / majorStep) * majorStep
            guard = 0
            val clampedX = ox.coerceIn(36f, w - 24f)
            while (gy <= yMax && guard++ < 100) {
                if (abs(gy) > majorStep * 0.1) {
                    val sy = toSy(gy)
                    if (sy in 24f..(h - 20f)) {
                        if (ox in 0f..w) sb.appendLine("""  <line x1="${f(ox - 4f)}" y1="${f(sy)}" x2="${f(ox + 4f)}" y2="${f(sy)}" stroke="$axisCol" stroke-width="1.6"/>""")
                        sb.appendLine("""  <text x="${f(clampedX - 8f)}" y="${f(sy + 5f)}" text-anchor="end" fill="$axisCol" font-family="monospace" font-size="14">${escapeXml(Geo2DMathHelpers.formatAxisTick(gy))}</text>""")
                    }
                }
                gy += majorStep
            }
        }

        // Functions & Derivatives & Asymptotes
        val compiled = ArrayList<Triple<GraphFunction, MathExprParser.Expr, MathExprParser.Expr?>>()
        fun appendCurve(evalFn: (Double) -> Double, strokeHex: String, dashed: Boolean) {
            val d = StringBuilder()
            val stepPx = 2.0
            val maxJumpWorld = max(1e-4, ySpan * 1.15)
            var sx = 0.0
            var penDown = false
            var prevWy = Double.NaN
            while (sx <= w + stepPx) {
                val wx = cam.centerX + (sx - w * 0.5) / cam.pixelsPerUnit
                val wy = evalFn(wx)
                if (wy.isFinite()) {
                    val sy = (h * 0.5 - (wy - cam.centerY) * cam.pixelsPerUnit)
                    val jump = penDown && prevWy.isFinite() && abs(wy - prevWy) > maxJumpWorld
                    if (sy.isFinite() && sy in (-h * 1.5)..(h * 2.5)) {
                        if (!penDown || jump) {
                            d.append("M ").append(fd(sx)).append(' ').append(fd(sy)).append(' ')
                            penDown = true
                        } else {
                            d.append("L ").append(fd(sx)).append(' ').append(fd(sy)).append(' ')
                        }
                    } else penDown = false
                    prevWy = wy
                } else {
                    penDown = false
                    prevWy = Double.NaN
                }
                sx += stepPx
            }
            if (d.isNotEmpty()) {
                val dashAttr = if (dashed) """ stroke-dasharray="10,7"""" else ""
                sb.appendLine("""  <path d="${d.toString().trim()}" fill="none" stroke="${escapeXml(strokeHex)}" stroke-width="${if (dashed) "2.4" else "3.0"}" stroke-linecap="round" stroke-linejoin="round"$dashAttr/>""")
            }
        }

        for (fn in snap.functions) {
            if ((!fn.visible && !fn.showDerivative) || fn.expression.isBlank()) continue
            val parsed = MathExprParser.parse(fn.expression)
            if (parsed is MathExprParser.ParseResult.Ok) {
                val symDeriv = MathExprParser.derive(fn.expression)?.first
                if (fn.visible) {
                    compiled.add(Triple(fn, parsed.expr, symDeriv))
                    appendCurve({ x -> parsed.expr.eval(x, m) }, fn.colorHex, false)
                }
                if (fn.showDerivative) {
                    val numStep = xSpan * 1e-5
                    appendCurve({ x -> symDeriv?.eval(x, m) ?: parsed.expr.derivative(x, numStep, m) }, "#FBBF24", true)
                }
            }
        }

        if (snap.showAsymptotes) {
            for ((fn, expr, _) in compiled) {
                val asyms = MathExprParser.findAsymptotes(expr, xMin, xMax, yMin, yMax, fn.colorHex, m)
                for (a in asyms) {
                    when (a) {
                        is GraphAsymptote.Vertical -> {
                            val sx = toSx(a.x)
                            if (sx.isFinite() && sx in -20f..(w + 20f)) {
                                sb.appendLine("""  <line x1="${f(sx)}" y1="0" x2="${f(sx)}" y2="${f(h)}" stroke="#F43F5E" stroke-width="2" stroke-dasharray="8,6"/>""")
                            }
                        }
                        is GraphAsymptote.Horizontal -> {
                            val sy = toSy(a.y)
                            if (sy.isFinite() && sy in -20f..(h + 20f)) {
                                sb.appendLine("""  <line x1="0" y1="${f(sy)}" x2="${f(w)}" y2="${f(sy)}" stroke="#F59E0B" stroke-width="2" stroke-dasharray="8,6"/>""")
                            }
                        }
                        is GraphAsymptote.Oblique -> {
                            val s1y = toSy(a.a * xMin + a.b)
                            val s2y = toSy(a.a * xMax + a.b)
                            if (s1y.isFinite() && s2y.isFinite()) {
                                sb.appendLine("""  <line x1="0" y1="${f(s1y.coerceIn(-h * 2f, h * 3f))}" x2="${f(w)}" y2="${f(s2y.coerceIn(-h * 2f, h * 3f))}" stroke="#A855F7" stroke-width="2" stroke-dasharray="8,6"/>""")
                            }
                        }
                    }
                }
            }
        }

        // Geometric Objects
        val ptMap = snap.points.associateBy { it.id }
        val neutralSolid2D = if (transparentBg) "#0F172A" else "#E2E8F0"
        val neutralDashed2D = if (transparentBg) "#475569" else "#94A3B8"
        fun resolveSvgColor2D(rawHex: String, dashed: Boolean = false): String = when {
            Geo3DMathHelpers.isNeutralColorHex(rawHex) -> if (dashed) neutralDashed2D else neutralSolid2D
            transparentBg && rawHex.equals("#F8FAFC", true) -> "#0F172A"
            else -> escapeXml(rawHex)
        }
        for (obj in snap.objects) {
            when (obj) {
                is Geo2DObject.Polygon -> {
                    val pts = obj.pointIds.mapNotNull { ptMap[it] }
                    if (pts.size >= 3) {
                        val isNeutral = Geo3DMathHelpers.isNeutralColorHex(obj.colorHex)
                        val col = resolveSvgColor2D(obj.colorHex)
                        val attr = pts.joinToString(" ") { "${f(toSx(it.x.toDouble()))},${f(toSy(it.y.toDouble()))}" }
                        sb.appendLine("""  <polygon points="$attr" fill="$col" fill-opacity="${if (isNeutral) "0.07" else "0.20"}" stroke="$col" stroke-width="${if (isNeutral) "2.2" else "2.6"}"/>""")
                    }
                }
                is Geo2DObject.Circle -> {
                    val c = ptMap[obj.centerId] ?: continue
                    val e = obj.edgePointId?.let { ptMap[it] }
                    val r = when {
                        e != null -> hypot(e.x - c.x, e.y - c.y)
                        obj.fixedRadius != null -> obj.fixedRadius
                        else -> continue
                    }
                    val screenR = (r * cam.pixelsPerUnit).toFloat()
                    if (screenR.isFinite() && screenR > 0.5f && screenR < max(w, h) * 8f) {
                        val isNeutral = Geo3DMathHelpers.isNeutralColorHex(obj.colorHex)
                        val col = resolveSvgColor2D(obj.colorHex)
                        sb.appendLine("""  <circle cx="${f(toSx(c.x.toDouble()))}" cy="${f(toSy(c.y.toDouble()))}" r="${f(screenR)}" fill="$col" fill-opacity="${if (isNeutral) "0.05" else "0.12"}" stroke="$col" stroke-width="${if (isNeutral) "2.2" else "2.6"}"/>""")
                    }
                }
                is Geo2DObject.Segment -> {
                    val p1 = ptMap[obj.p1Id] ?: continue
                    val p2 = ptMap[obj.p2Id] ?: continue
                    val col = resolveSvgColor2D(obj.colorHex, obj.dashed)
                    val dash = if (obj.dashed) """ stroke-dasharray="9,6"""" else ""
                    sb.appendLine("""  <line x1="${f(toSx(p1.x.toDouble()))}" y1="${f(toSy(p1.y.toDouble()))}" x2="${f(toSx(p2.x.toDouble()))}" y2="${f(toSy(p2.y.toDouble()))}" stroke="$col" stroke-width="2.6" stroke-linecap="round"$dash/>""")
                }
                is Geo2DObject.Line -> {
                    val p1 = ptMap[obj.p1Id] ?: continue
                    val p2 = ptMap[obj.p2Id] ?: continue
                    val sx1 = toSx(p1.x.toDouble()); val sy1 = toSy(p1.y.toDouble())
                    val sx2 = toSx(p2.x.toDouble()); val sy2 = toSy(p2.y.toDouble())
                    val len = hypot(sx2 - sx1, sy2 - sy1)
                    if (len > 1f) {
                        val ux = (sx2 - sx1) / len; val uy = (sy2 - sy1) / len; val ext = max(w, h) * 1.5f
                        val col = resolveSvgColor2D(obj.colorHex, obj.dashed)
                        val dash = if (obj.dashed) """ stroke-dasharray="9,6"""" else ""
                        sb.appendLine("""  <line x1="${f(sx1 - ux * ext)}" y1="${f(sy1 - uy * ext)}" x2="${f(sx1 + ux * ext)}" y2="${f(sy1 + uy * ext)}" stroke="$col" stroke-width="2.4"$dash/>""")
                    }
                }
                is Geo2DObject.Ray -> {
                    val p1 = ptMap[obj.startId] ?: continue
                    val p2 = ptMap[obj.throughId] ?: continue
                    val sx1 = toSx(p1.x.toDouble()); val sy1 = toSy(p1.y.toDouble())
                    val sx2 = toSx(p2.x.toDouble()); val sy2 = toSy(p2.y.toDouble())
                    val len = hypot(sx2 - sx1, sy2 - sy1)
                    if (len > 1f) {
                        val ux = (sx2 - sx1) / len; val uy = (sy2 - sy1) / len; val ext = max(w, h) * 1.5f
                        val col = resolveSvgColor2D(obj.colorHex)
                        sb.appendLine("""  <line x1="${f(sx1)}" y1="${f(sy1)}" x2="${f(sx1 + ux * ext)}" y2="${f(sy1 + uy * ext)}" stroke="$col" stroke-width="2.4"/>""")
                    }
                }
                is Geo2DObject.VectorObj -> {
                    val p1 = ptMap[obj.fromId] ?: continue
                    val p2 = ptMap[obj.toId] ?: continue
                    val sx1 = toSx(p1.x.toDouble()); val sy1 = toSy(p1.y.toDouble())
                    val sx2 = toSx(p2.x.toDouble()); val sy2 = toSy(p2.y.toDouble())
                    val col = resolveSvgColor2D(obj.colorHex, obj.dashed)
                    val dash = if (obj.dashed) """ stroke-dasharray="9,6"""" else ""
                    sb.appendLine("""  <line x1="${f(sx1)}" y1="${f(sy1)}" x2="${f(sx2)}" y2="${f(sy2)}" stroke="$col" stroke-width="3" stroke-linecap="round"$dash/>""")
                    sb.appendLine(svgArrowPolygon(sx1, sy1, sx2, sy2, 14f, col))
                }
                is Geo2DObject.AngleMeasure -> {
                    val pA = ptMap[obj.pAId] ?: continue
                    val pV = ptMap[obj.vertexId] ?: continue
                    val pC = ptMap[obj.pCId] ?: continue
                    val deg = Geo2DMathHelpers.angleDegrees(Vec2(pA.x, pA.y), Vec2(pV.x, pV.y), Vec2(pC.x, pC.y))
                    val svx = toSx(pV.x.toDouble()); val svy = toSy(pV.y.toDouble())
                    val col = resolveSvgColor2D(obj.colorHex)
                    sb.appendLine("""  <text x="${f(svx + 16f)}" y="${f(svy - 10f)}" fill="$col" font-family="monospace" font-weight="bold" font-size="14">${Geo2DMathHelpers.formatNum(deg)}°</text>""")
                }
            }
        }

        // Points
        for (pt in snap.points) {
            val sx = toSx(pt.x.toDouble())
            val sy = toSy(pt.y.toDouble())
            if (sx !in -20f..(w + 20f) || sy !in -20f..(h + 20f)) continue
            sb.appendLine("""  <circle cx="${f(sx)}" cy="${f(sy)}" r="5.5" fill="${escapeXml(pt.colorHex)}" stroke="#FFFFFF" stroke-width="1.8"/>""")
            val lbl = if (snap.showPointCoords) {
                "${pt.name}(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})"
            } else pt.name
            sb.appendLine("""  <text x="${f(sx)}" y="${f(sy - 12f)}" text-anchor="middle" fill="$textCol" font-family="sans-serif" font-weight="bold" font-size="16">${escapeXml(lbl)}</text>""")
        }

        sb.appendLine("</svg>")
        return sb.toString()
    }
}
