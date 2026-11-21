package com.example.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Spatial Oxyz analytical geometry helpers:
 * - Dynamic 3-point cross-section polygon clipping against polyhedron edges
 * - Point-to-plane distance d(M, (P1P2P3))
 * - Line-to-plane angle phi(AB, (P1P2P3)) in degrees
 * - Oxyz plane equation ax + by + cz + d = 0 formatting
 */
object Geo3DMathHelpers {

    data class DynamicSectionResult(
        val polygon: List<Vec3>,
        val area: Float,
        val normal: Vec3,
        val center: Vec3
    )

    data class LinePlaneAnalysis(
        val planeEquation: String,
        val distA: Float,
        val distB: Float,
        val angleDegrees: Float,
        val lineLength: Float,
        val normalMath: Vec3
    )

    /**
     * Intersects the infinite plane passing through [p1], [p2], [p3] with all polyhedron edges.
     * Returns the ordered convex cross-section polygon and its exact 3D area, or null if degenerate.
     */
    fun computeDynamicCrossSection(
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        edges: List<Pair<Vec3, Vec3>>
    ): DynamicSectionResult? {
        val rawN = (p2 - p1).cross(p3 - p1)
        val nLen = rawN.length()
        if (nLen < 1e-4f) return null
        val n = rawN / nLen

        val hits = mutableListOf<Vec3>()

        fun addUnique(pt: Vec3) {
            if (hits.none { it.distanceTo(pt) < 1.5e-3f }) {
                hits.add(pt)
            }
        }

        val eps = 1e-4f
        for ((u, v) in edges) {
            val du = (u - p1).dot(n)
            val dv = (v - p1).dot(n)
            when {
                abs(du) < eps && abs(dv) < eps -> {
                    addUnique(u)
                    addUnique(v)
                }
                abs(du) < eps -> addUnique(u)
                abs(dv) < eps -> addUnique(v)
                (du > 0f && dv < 0f) || (du < 0f && dv > 0f) -> {
                    val t = du / (du - dv)
                    if (t in -1e-3f..1.001f) {
                        addUnique(Vec3.lerp(u, v, t.coerceIn(0f, 1f)))
                    }
                }
            }
        }

        // Fallback for non-polyhedral solids (e.g. cylinder/cone/sphere) or degenerate clips
        if (hits.size < 3) {
            addUnique(p1)
            addUnique(p2)
            addUnique(p3)
        }
        if (hits.size < 3) return null

        var center = Vec3.ZERO
        hits.forEach { center += it }
        center /= hits.size.toFloat()

        // Orthonormal basis (uAxis, vAxis) on the section plane
        val ref = if (abs(n.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        val uAxis = n.cross(ref).normalized()
        val vAxis = n.cross(uAxis).normalized()

        val ordered = hits.sortedBy { pt ->
            val rel = pt - center
            atan2(rel.dot(vAxis).toDouble(), rel.dot(uAxis).toDouble())
        }

        val area = polygonArea3D(ordered)
        if (area < 1e-4f) return null

        return DynamicSectionResult(
            polygon = ordered,
            area = area,
            normal = n,
            center = center
        )
    }

    fun polygonArea3D(pts: List<Vec3>): Float {
        if (pts.size < 3) return 0f
        var area = 0f
        val p0 = pts[0]
        for (i in 1 until pts.size - 1) {
            area += 0.5f * (pts[i] - p0).cross(pts[i + 1] - p0).length()
        }
        return area
    }

    fun pointToPlaneDistance(point: Vec3, p1: Vec3, p2: Vec3, p3: Vec3): Float? {
        val normal = (p2 - p1).cross(p3 - p1)
        val nLen = normal.length()
        if (nLen < 1e-4f) return null
        return abs((point - p1).dot(normal)) / nLen
    }

    fun linePlaneAngleDegrees(lineA: Vec3, lineB: Vec3, p1: Vec3, p2: Vec3, p3: Vec3): Float? {
        val u = lineB - lineA
        val uLen = u.length()
        if (uLen < 1e-4f) return null
        val n = (p2 - p1).cross(p3 - p1)
        val nLen = n.length()
        if (nLen < 1e-4f) return null
        val sinPhi = (abs(u.dot(n)) / (uLen * nLen)).coerceIn(0f, 1f)
        return Math.toDegrees(asin(sinPhi.toDouble())).toFloat()
    }

    fun analyzeLineAndPlane(
        lineAWorld: Vec3,
        lineBWorld: Vec3,
        p1World: Vec3,
        p2World: Vec3,
        p3World: Vec3,
        originWorld: Vec3
    ): LinePlaneAnalysis? {
        val m1 = ShapeBuilder.worldToMath(p1World, originWorld)
        val m2 = ShapeBuilder.worldToMath(p2World, originWorld)
        val m3 = ShapeBuilder.worldToMath(p3World, originWorld)
        val rawN = (m2 - m1).cross(m3 - m1)
        val nLen = rawN.length()
        if (nLen < 1e-4f) return null

        val distA = pointToPlaneDistance(lineAWorld, p1World, p2World, p3World) ?: return null
        val distB = pointToPlaneDistance(lineBWorld, p1World, p2World, p3World) ?: return null
        val lineLen = lineAWorld.distanceTo(lineBWorld)
        val angle = if (lineLen > 1e-4f) {
            linePlaneAngleDegrees(lineAWorld, lineBWorld, p1World, p2World, p3World) ?: 0f
        } else 0f

        val eq = formatPlaneEquation(m1, m2, m3)
        return LinePlaneAnalysis(
            planeEquation = eq,
            distA = distA,
            distB = distB,
            angleDegrees = angle,
            lineLength = lineLen,
            normalMath = rawN / nLen
        )
    }

    fun formatPlaneEquation(m1: Vec3, m2: Vec3, m3: Vec3): String {
        val rawN = (m2 - m1).cross(m3 - m1)
        val maxAbs = max(abs(rawN.x), max(abs(rawN.y), abs(rawN.z)))
        if (maxAbs < 1e-4f) return "3 điểm thẳng hàng"

        // Normalize so largest component has magnitude 1 (or clean integer-like scale)
        var a = rawN.x / maxAbs
        var b = rawN.y / maxAbs
        var c = rawN.z / maxAbs
        var d = -(a * m1.x + b * m1.y + c * m1.z)

        // Make leading non-zero coefficient positive
        val firstSign = when {
            abs(a) > 1e-3f -> if (a < 0f) -1f else 1f
            abs(b) > 1e-3f -> if (b < 0f) -1f else 1f
            else -> if (c < 0f) -1f else 1f
        }
        a *= firstSign
        b *= firstSign
        c *= firstSign
        d *= firstSign

        val sb = StringBuilder()
        fun appendTerm(coef: Float, varName: String) {
            if (abs(coef) < 1e-3f) return
            val isNeg = coef < 0f
            val absC = abs(coef)
            if (sb.isNotEmpty()) {
                sb.append(if (isNeg) " - " else " + ")
            } else if (isNeg) {
                sb.append("-")
            }
            if (varName.isEmpty()) {
                sb.append(formatMathNumber(absC))
            } else if (abs(absC - 1f) < 1e-3f) {
                sb.append(varName)
            } else {
                sb.append(formatMathNumber(absC)).append(varName)
            }
        }

        appendTerm(a, "x")
        appendTerm(b, "y")
        appendTerm(c, "z")
        appendTerm(d, "")
        if (sb.isEmpty()) return "0 = 0"
        sb.append(" = 0")
        return sb.toString()
    }

    fun formatDeg(deg: Float): String = String.format(Locale.US, "%.1f°", deg)

    /**
     * Returns true when a color string indicates neutral/uncolored ink (blank, "default", or "neutral").
     */
    fun isNeutralColorHex(hex: String?): Boolean {
        val t = hex?.trim().orEmpty()
        return t.isEmpty() || t.equals("default", ignoreCase = true) || t.equals("neutral", ignoreCase = true)
    }

    /**
     * Determines whether a 3D segment or vector [p1] -> [p2] is hidden/occluded behind the solid
     * from [camPos]. Handles interior segments (diagonals, altitudes, medians), back-facing edges,
     * and segments behind polyhedra, cylinders, cones, and spheres.
     */
    fun isSegmentOccludedBySolid(
        p1: Vec3,
        p2: Vec3,
        camPos: Vec3,
        occluderFaces: List<RenderFace>,
        roundBodyHint: RoundBodyHint?
    ): Boolean {
        if (occluderFaces.isEmpty() && roundBodyHint == null) return false
        if (p1.distanceToSq(p2) < 1e-6f) return false

        val qMid = Vec3.lerp(p1, p2, 0.5f)
        if (isPointOccludedBySolid(qMid, camPos, occluderFaces, roundBodyHint)) return true

        val q1 = Vec3.lerp(p1, p2, 0.25f)
        val q3 = Vec3.lerp(p1, p2, 0.75f)
        return isPointOccludedBySolid(q1, camPos, occluderFaces, roundBodyHint) &&
            isPointOccludedBySolid(q3, camPos, occluderFaces, roundBodyHint)
    }

    private fun isPointOccludedBySolid(
        q: Vec3,
        camPos: Vec3,
        occluderFaces: List<RenderFace>,
        roundBodyHint: RoundBodyHint?
    ): Boolean {
        if (occluderFaces.isNotEmpty()) {
            var onFrontFace = false
            var onBackFace = false
            for (face in occluderFaces) {
                if (face.points.size < 3) continue
                val planeDist = abs((q - face.points[0]).dot(face.normal))
                if (planeDist < 1.5e-3f && isPointInConvexPolygon3D(q, face.points, face.normal)) {
                    if (face.normal.dot(camPos - face.center) > 1e-4f) {
                        onFrontFace = true
                    } else {
                        onBackFace = true
                    }
                }
            }
            if (onBackFace && !onFrontFace) return true

            val dir = q - camPos
            for (face in occluderFaces) {
                if (face.points.size < 3) continue
                // Only front-facing faces can occlude points behind them
                if (face.normal.dot(camPos - face.center) <= 1e-4f) continue
                val denom = dir.dot(face.normal)
                if (denom >= -1e-5f) continue
                val u = (face.points[0] - camPos).dot(face.normal) / denom
                if (u > 0.005f && u < 0.985f) {
                    val hit = camPos + dir * u
                    if (isPointInConvexPolygon3D(hit, face.points, face.normal)) {
                        return true
                    }
                }
            }
        }

        if (roundBodyHint != null) {
            return isPointOccludedByRoundBody(q, camPos, roundBodyHint)
        }
        return false
    }

    private fun isPointInConvexPolygon3D(pt: Vec3, poly: List<Vec3>, normal: Vec3): Boolean {
        var hasPos = false
        var hasNeg = false
        val n = poly.size
        for (i in 0 until n) {
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val d = (b - a).cross(pt - a).dot(normal)
            if (d > 1.5e-4f) hasPos = true
            else if (d < -1.5e-4f) hasNeg = true
            if (hasPos && hasNeg) return false
        }
        return true
    }

    private fun isPointOccludedByRoundBody(q: Vec3, camPos: Vec3, hint: RoundBodyHint): Boolean {
        val dir = q - camPos
        val uMin = 0.005f
        val uMax = 0.985f
        return when (hint) {
            is RoundBodyHint.Sphere -> {
                val oc = camPos - hint.center
                val a = dir.dot(dir)
                if (a < 1e-6f) return false
                val b = 2f * oc.dot(dir)
                val c = oc.dot(oc) - hint.radius * hint.radius
                val disc = b * b - 4f * a * c
                if (disc <= 0f) return false
                val sqrtD = sqrt(disc)
                val u1 = (-b - sqrtD) / (2f * a)
                val u2 = (-b + sqrtD) / (2f * a)
                (u1 in uMin..uMax) || (u2 in uMin..uMax)
            }
            is RoundBodyHint.Cylinder -> {
                val cx = hint.centerBottom.x
                val cz = hint.centerBottom.z
                val yMin = kotlin.math.min(hint.centerBottom.y, hint.centerTop.y)
                val yMax = kotlin.math.max(hint.centerBottom.y, hint.centerTop.y)
                val rSq = hint.radius * hint.radius
                // Check caps
                if (abs(dir.y) > 1e-5f) {
                    val uTop = (yMax - camPos.y) / dir.y
                    if (uTop in uMin..uMax) {
                        val hx = camPos.x + dir.x * uTop - cx
                        val hz = camPos.z + dir.z * uTop - cz
                        if (hx * hx + hz * hz <= rSq + 1e-3f) return true
                    }
                    val uBot = (yMin - camPos.y) / dir.y
                    if (uBot in uMin..uMax) {
                        val hx = camPos.x + dir.x * uBot - cx
                        val hz = camPos.z + dir.z * uBot - cz
                        if (hx * hx + hz * hz <= rSq + 1e-3f) return true
                    }
                }
                // Check lateral cylinder surface
                val ox = camPos.x - cx
                val oz = camPos.z - cz
                val a = dir.x * dir.x + dir.z * dir.z
                if (a > 1e-6f) {
                    val b = 2f * (ox * dir.x + oz * dir.z)
                    val c = ox * ox + oz * oz - rSq
                    val disc = b * b - 4f * a * c
                    if (disc > 0f) {
                        val sqrtD = sqrt(disc)
                        val u1 = (-b - sqrtD) / (2f * a)
                        if (u1 in uMin..uMax) {
                            val hy = camPos.y + dir.y * u1
                            if (hy in (yMin - 1e-3f)..(yMax + 1e-3f)) return true
                        }
                        val u2 = (-b + sqrtD) / (2f * a)
                        if (u2 in uMin..uMax) {
                            val hy = camPos.y + dir.y * u2
                            if (hy in (yMin - 1e-3f)..(yMax + 1e-3f)) return true
                        }
                    }
                }
                false
            }
            is RoundBodyHint.Cone -> {
                val cx = hint.centerBottom.x
                val cz = hint.centerBottom.z
                val yBase = hint.centerBottom.y
                val yApex = hint.apex.y
                val h = yApex - yBase
                if (abs(h) < 1e-4f) return false
                val rSq = hint.radius * hint.radius
                // Check base disk
                if (abs(dir.y) > 1e-5f) {
                    val uBot = (yBase - camPos.y) / dir.y
                    if (uBot in uMin..uMax) {
                        val hx = camPos.x + dir.x * uBot - cx
                        val hz = camPos.z + dir.z * uBot - cz
                        if (hx * hx + hz * hz <= rSq + 1e-3f) return true
                    }
                }
                // Check lateral cone surface: (x-ax)^2 + (z-az)^2 = k^2 * (yApex - y)^2
                val k = hint.radius / h
                val k2 = k * k
                val ox = camPos.x - hint.apex.x
                val oy = camPos.y - hint.apex.y
                val oz = camPos.z - hint.apex.z
                val a = dir.x * dir.x + dir.z * dir.z - k2 * dir.y * dir.y
                val b = 2f * (ox * dir.x + oz * dir.z - k2 * oy * dir.y)
                val c = ox * ox + oz * oz - k2 * oy * oy
                val yLo = kotlin.math.min(yBase, yApex) - 1e-3f
                val yHi = kotlin.math.max(yBase, yApex) + 1e-3f
                if (abs(a) > 1e-6f) {
                    val disc = b * b - 4f * a * c
                    if (disc > 0f) {
                        val sqrtD = sqrt(disc)
                        val u1 = (-b - sqrtD) / (2f * a)
                        if (u1 in uMin..uMax) {
                            val hy = camPos.y + dir.y * u1
                            if (hy in yLo..yHi) return true
                        }
                        val u2 = (-b + sqrtD) / (2f * a)
                        if (u2 in uMin..uMax) {
                            val hy = camPos.y + dir.y * u2
                            if (hy in yLo..yHi) return true
                        }
                    }
                }
                false
            }
        }
    }
}
