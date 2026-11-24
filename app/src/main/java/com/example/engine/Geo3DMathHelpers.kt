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
                if (planeDist < 1.5e-2f && isPointInConvexPolygon3D(q, face.points, face.normal, edgeTolFactor = 1e-2f)) {
                    if (face.normal.dot(camPos - face.center) > 0f) {
                        onFrontFace = true
                    } else {
                        onBackFace = true
                    }
                }
            }
            // On any convex polyhedron, a point lying on a front-facing face or silhouette edge is always visible
            if (onFrontFace) return false
            if (onBackFace) return true

            val dir = q - camPos
            for (face in occluderFaces) {
                if (face.points.size < 3) continue
                // Only front-facing faces can occlude points strictly behind them
                if (face.normal.dot(camPos - face.center) <= 0f) continue
                val denom = dir.dot(face.normal)
                if (denom >= -1e-5f) continue
                val u = (face.points[0] - camPos).dot(face.normal) / denom
                if (u > 0.005f && u < 0.985f) {
                    val hit = camPos + dir * u
                    if (isPointInConvexPolygon3D(hit, face.points, face.normal, edgeTolFactor = 2e-3f)) {
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

    private fun isPointInConvexPolygon3D(
        pt: Vec3,
        poly: List<Vec3>,
        normal: Vec3,
        edgeTolFactor: Float
    ): Boolean {
        var hasPos = false
        var hasNeg = false
        val n = poly.size
        for (i in 0 until n) {
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val edge = b - a
            val edgeLen = edge.length()
            if (edgeLen < 1e-5f) continue
            val tol = edgeLen * edgeTolFactor
            val d = edge.cross(pt - a).dot(normal)
            if (d > tol) hasPos = true
            else if (d < -tol) hasNeg = true
            if (hasPos && hasNeg) return false
        }
        return true
    }

    private fun isPointOccludedByRoundBody(q: Vec3, camPos: Vec3, hint: RoundBodyHint): Boolean {
        val dir = q - camPos
        val uMin = 0.005f
        val uMax = 0.98f
        return when (hint) {
            is RoundBodyHint.Sphere -> {
                // If q lies on the front-facing hemisphere surface, it is visible
                val fromCenter = q - hint.center
                val distC = fromCenter.length()
                if (abs(distC - hint.radius) < 0.03f && fromCenter.dot(camPos - q) >= -0.02f) {
                    return false
                }
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
                // If q lies on a front-facing cap or front lateral surface, it is visible
                val qRad = kotlin.math.hypot(q.x - cx, q.z - cz)
                if (abs(q.y - yMax) < 0.03f && qRad <= hint.radius + 0.03f && camPos.y > yMax) return false
                if (abs(q.y - yMin) < 0.03f && qRad <= hint.radius + 0.03f && camPos.y < yMin) return false
                if (q.y in (yMin - 0.03f)..(yMax + 0.03f) && abs(qRad - hint.radius) < 0.03f) {
                    val outN = Vec3(q.x - cx, 0f, q.z - cz)
                    if (outN.dot(camPos - q) >= -0.02f) return false
                }
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
                // If q lies on the front-facing base or front lateral surface of the cone, it is visible
                val qRad = kotlin.math.hypot(q.x - cx, q.z - cz)
                if (abs(q.y - yBase) < 0.03f && qRad <= hint.radius + 0.03f && camPos.y < yBase) return false
                val expectedR = hint.radius * ((yApex - q.y) / h)
                if (expectedR >= -0.02f && abs(qRad - expectedR) < 0.03f && qRad > 1e-4f) {
                    val outN = Vec3((q.x - cx) / qRad, hint.radius / h, (q.z - cz) / qRad)
                    if (outN.dot(camPos - q) >= -0.02f) return false
                }
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

    // ================= ADVANCED 3D ANALYSIS (SPHERES, DIHEDRAL, SKEW LINES, UNFOLDING) =================

    data class SphereResult3D(
        val isCircumscribed: Boolean,
        val centerWorld: Vec3,
        val centerMath: Vec3,
        val radius: Float,
        val surfaceArea: Float,
        val volume: Float,
        val formulaHint: String
    )

    data class DihedralAngleResult3D(
        val angleDegrees: Float,
        val plane1Eq: String,
        val plane2Eq: String,
        val cosPhi: Float
    )

    data class SkewLinesResult3D(
        val distance: Float,
        val isParallel: Boolean,
        val isIntersecting: Boolean,
        val closestOnLine1: Vec3,
        val closestOnLine2: Vec3
    )

    data class UnfoldedFace3D(
        val points: List<Vec3>,
        val isBase: Boolean
    )

    fun computeCircumSphere(
        scene: BuiltScene,
        snap: GeometrySnapshot,
        originWorld: Vec3
    ): SphereResult3D? {
        val safeA = max(0.5f, snap.a)
        val safeB = max(0.5f, snap.b)
        val safeH = max(0.5f, snap.h)
        val sqrt2 = sqrt(2f)
        val sqrt3 = sqrt(3f)

        var center: Vec3
        var radius: Float
        var formulaHint: String

        when (snap.shape) {
            ShapeType.SPHERE -> {
                val hint = scene.roundBodyHint as? RoundBodyHint.Sphere
                val r = hint?.radius ?: (safeA * 0.5f)
                center = hint?.center ?: Vec3(0f, r, 0f)
                radius = r
                formulaHint = "Mặt cầu tâm O, bán kính R"
            }
            ShapeType.CUBE -> {
                center = Vec3(0f, safeA * 0.5f, 0f)
                radius = safeA * sqrt3 * 0.5f
                formulaHint = "R = a√3 / 2"
            }
            ShapeType.BOX -> {
                center = Vec3(0f, safeH * 0.5f, 0f)
                radius = 0.5f * sqrt(safeA * safeA + safeB * safeB + safeH * safeH)
                formulaHint = "R = ½√(a² + b² + h²)"
            }
            ShapeType.TETRA_ORTHO -> {
                val pO = scene.pointRegistry["O"] ?: Vec3.ZERO
                val pA = scene.pointRegistry["A"] ?: Vec3(safeA, 0f, 0f)
                val pB = scene.pointRegistry["B"] ?: Vec3(0f, 0f, safeB)
                val pC = scene.pointRegistry["C"] ?: Vec3(0f, safeH, 0f)
                center = Vec3((pO.x + pA.x) * 0.5f, (pO.y + pC.y) * 0.5f, (pO.z + pB.z) * 0.5f)
                radius = center.distanceTo(pO)
                formulaHint = "R = ½√(OA² + OB² + OC²)"
            }
            ShapeType.OCTAHEDRON -> {
                val r = safeA / sqrt2
                center = Vec3(0f, r, 0f)
                radius = r
                formulaHint = "R = a√2 / 2"
            }
            ShapeType.PYRAMID3 -> {
                val rBase = safeA / sqrt3
                val rVal = (safeH * safeH + rBase * rBase) / (2f * safeH)
                center = Vec3(0f, safeH - rVal, 0f)
                radius = rVal
                formulaHint = "R = (h² + R_đáy²) / (2h)"
            }
            ShapeType.PYRAMID3_SA -> {
                val rBase = safeA / sqrt3
                val cBase = scene.pointRegistry["O"] ?: Vec3.ZERO
                center = Vec3(cBase.x, safeH * 0.5f, cBase.z)
                radius = kotlin.math.hypot(rBase, safeH * 0.5f)
                formulaHint = "R = √(R_đáy² + (SA/2)²)"
            }
            ShapeType.PYRAMID4 -> {
                val rBase = safeA / sqrt2
                val rVal = (safeH * safeH + rBase * rBase) / (2f * safeH)
                center = Vec3(0f, safeH - rVal, 0f)
                radius = rVal
                formulaHint = "R = (h² + (a√2/2)²) / (2h)"
            }
            ShapeType.PYRAMID4_SA -> {
                val rBase = safeA / sqrt2
                val cBase = scene.pointRegistry["O"] ?: Vec3.ZERO
                center = Vec3(cBase.x, safeH * 0.5f, cBase.z)
                radius = kotlin.math.hypot(rBase, safeH * 0.5f)
                formulaHint = "R = ½√(SA² + AC²)"
            }
            ShapeType.PRISM3, ShapeType.PRISM3_OBLIQUE -> {
                val pts = scene.pointRegistry.values
                if (pts.isEmpty()) return null
                var sum = Vec3.ZERO
                pts.forEach { sum += it }
                center = sum / pts.size.toFloat()
                radius = pts.maxOf { it.distanceTo(center) }
                formulaHint = "R = √(R_đáy² + (h/2)²)"
            }
            ShapeType.PRISM6 -> {
                center = Vec3(0f, safeH * 0.5f, 0f)
                radius = kotlin.math.hypot(safeA, safeH * 0.5f)
                formulaHint = "R = √(a² + (h/2)²)"
            }
            ShapeType.CYLINDER -> {
                val hint = scene.roundBodyHint as? RoundBodyHint.Cylinder
                val r = hint?.radius ?: (safeA * 0.5f)
                val cy = hint?.let { (it.centerBottom.y + it.centerTop.y) * 0.5f } ?: (safeH * 0.5f)
                center = Vec3(0f, cy, 0f)
                radius = kotlin.math.hypot(r, safeH * 0.5f)
                formulaHint = "R_mc = √(R_đáy² + (h/2)²)"
            }
            ShapeType.CONE -> {
                val hint = scene.roundBodyHint as? RoundBodyHint.Cone
                val r = hint?.radius ?: (safeA * 0.5f)
                val rVal = (safeH * safeH + r * r) / (2f * safeH)
                center = Vec3(0f, safeH - rVal, 0f)
                radius = rVal
                formulaHint = "R_mc = l² / (2h)"
            }
            else -> {
                val pts = scene.pointRegistry.values
                if (pts.isEmpty()) return null
                var sum = Vec3.ZERO
                pts.forEach { sum += it }
                center = sum / pts.size.toFloat()
                radius = pts.maxOf { it.distanceTo(center) }
                formulaHint = "Mặt cầu ngoại tiếp gần đúng qua các đỉnh"
            }
        }

        val piF = kotlin.math.PI.toFloat()
        return SphereResult3D(
            isCircumscribed = true,
            centerWorld = center,
            centerMath = ShapeBuilder.worldToMath(center, originWorld),
            radius = radius,
            surfaceArea = 4f * piF * radius * radius,
            volume = (4f / 3f) * piF * radius * radius * radius,
            formulaHint = formulaHint
        )
    }

    fun computeInSphere(
        scene: BuiltScene,
        snap: GeometrySnapshot,
        originWorld: Vec3
    ): SphereResult3D? {
        val safeA = max(0.5f, snap.a)
        val safeB = max(0.5f, snap.b)
        val safeH = max(0.5f, snap.h)
        val sqrt2 = sqrt(2f)
        val sqrt3 = sqrt(3f)

        val center: Vec3
        val radius: Float
        val formulaHint: String

        when (snap.shape) {
            ShapeType.SPHERE -> {
                val hint = scene.roundBodyHint as? RoundBodyHint.Sphere
                val r = hint?.radius ?: (safeA * 0.5f)
                center = hint?.center ?: Vec3(0f, r, 0f)
                radius = r
                formulaHint = "Mặt cầu nội tiếp trùng chính nó"
            }
            ShapeType.CUBE -> {
                radius = safeA * 0.5f
                center = Vec3(0f, radius, 0f)
                formulaHint = "r = a / 2"
            }
            ShapeType.BOX -> {
                radius = minOf(safeA, safeB, safeH) * 0.5f
                center = Vec3(0f, safeH * 0.5f, 0f)
                formulaHint = "r = min(a, b, h) / 2"
            }
            ShapeType.OCTAHEDRON -> {
                radius = safeA * sqrt(6f) / 6f
                center = Vec3(0f, safeA / sqrt2, 0f)
                formulaHint = "r = a√6 / 6"
            }
            ShapeType.CYLINDER -> {
                val hint = scene.roundBodyHint as? RoundBodyHint.Cylinder
                val r = hint?.radius ?: (safeA * 0.5f)
                radius = minOf(r, safeH * 0.5f)
                center = Vec3(0f, safeH * 0.5f, 0f)
                formulaHint = "r = min(R_đáy, h/2)"
            }
            ShapeType.CONE -> {
                val hint = scene.roundBodyHint as? RoundBodyHint.Cone
                val r = hint?.radius ?: (safeA * 0.5f)
                val l = kotlin.math.hypot(r, safeH)
                radius = (r * safeH) / (r + l)
                center = Vec3(0f, radius, 0f)
                formulaHint = "r = (R·h) / (R + l)"
            }
            ShapeType.PRISM3, ShapeType.PRISM3_OBLIQUE -> {
                val rInBase = safeA / (2f * sqrt3)
                radius = minOf(rInBase, safeH * 0.5f)
                val pts = scene.pointRegistry.values
                val avgX = if (pts.isNotEmpty()) pts.map { it.x }.average().toFloat() else 0f
                val avgZ = if (pts.isNotEmpty()) pts.map { it.z }.average().toFloat() else 0f
                center = Vec3(avgX, safeH * 0.5f, avgZ)
                formulaHint = "r = min(r_đáy, h/2)"
            }
            ShapeType.PRISM6 -> {
                val rInBase = safeA * sqrt3 * 0.5f
                radius = minOf(rInBase, safeH * 0.5f)
                center = Vec3(0f, safeH * 0.5f, 0f)
                formulaHint = "r = min(a√3/2, h/2)"
            }
            else -> {
                val sTp = scene.metrics.sTp
                if (sTp < 1e-4f) return null
                radius = (3f * scene.metrics.vol) / sTp
                center = when (snap.shape) {
                    ShapeType.TETRA_ORTHO -> {
                        val pO = scene.pointRegistry["O"] ?: Vec3.ZERO
                        Vec3(pO.x + radius, pO.y + radius, pO.z + radius)
                    }
                    ShapeType.PYRAMID3, ShapeType.PYRAMID4 -> Vec3(0f, radius, 0f)
                    else -> {
                        val basePts = scene.pointRegistry.values.filter { abs(it.y) < 1e-3f }
                        val bx = if (basePts.isNotEmpty()) basePts.map { it.x }.average().toFloat() else 0f
                        val bz = if (basePts.isNotEmpty()) basePts.map { it.z }.average().toFloat() else 0f
                        Vec3(bx, radius, bz)
                    }
                }
                formulaHint = "r = 3V / S_tp"
            }
        }

        val piF = kotlin.math.PI.toFloat()
        return SphereResult3D(
            isCircumscribed = false,
            centerWorld = center,
            centerMath = ShapeBuilder.worldToMath(center, originWorld),
            radius = radius,
            surfaceArea = 4f * piF * radius * radius,
            volume = (4f / 3f) * piF * radius * radius * radius,
            formulaHint = formulaHint
        )
    }

    fun computeDihedralAngle(
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        q1: Vec3,
        q2: Vec3,
        q3: Vec3,
        originWorld: Vec3
    ): DihedralAngleResult3D? {
        val n1 = (p2 - p1).cross(p3 - p1)
        val n2 = (q2 - q1).cross(q3 - q1)
        val l1 = n1.length()
        val l2 = n2.length()
        if (l1 < 1e-4f || l2 < 1e-4f) return null

        val cosPhi = (abs(n1.dot(n2)) / (l1 * l2)).coerceIn(0f, 1f)
        val angleDeg = Math.toDegrees(kotlin.math.acos(cosPhi.toDouble())).toFloat()
        val p1Eq = formatPlaneEquation(
            ShapeBuilder.worldToMath(p1, originWorld),
            ShapeBuilder.worldToMath(p2, originWorld),
            ShapeBuilder.worldToMath(p3, originWorld)
        )
        val p2Eq = formatPlaneEquation(
            ShapeBuilder.worldToMath(q1, originWorld),
            ShapeBuilder.worldToMath(q2, originWorld),
            ShapeBuilder.worldToMath(q3, originWorld)
        )
        return DihedralAngleResult3D(angleDeg, p1Eq, p2Eq, cosPhi)
    }

    fun computeSkewLinesDistance(a1: Vec3, a2: Vec3, b1: Vec3, b2: Vec3): SkewLinesResult3D? {
        val u = a2 - a1
        val v = b2 - b1
        val w0 = a1 - b1
        val a = u.dot(u)
        val b = u.dot(v)
        val c = v.dot(v)
        if (a < 1e-6f || c < 1e-6f) return null

        val d = u.dot(w0)
        val e = v.dot(w0)
        val denom = a * c - b * b

        if (abs(denom) < 1e-5f) {
            val s = -d / a
            val h1 = a1 + u * s
            val dist = h1.distanceTo(b1)
            return SkewLinesResult3D(
                distance = dist,
                isParallel = true,
                isIntersecting = dist < 1e-3f,
                closestOnLine1 = h1,
                closestOnLine2 = b1
            )
        }

        val sc = (b * e - c * d) / denom
        val tc = (a * e - b * d) / denom
        val h1 = a1 + u * sc
        val h2 = b1 + v * tc
        val dist = h1.distanceTo(h2)
        return SkewLinesResult3D(
            distance = dist,
            isParallel = false,
            isIntersecting = dist < 1e-3f,
            closestOnLine1 = h1,
            closestOnLine2 = h2
        )
    }

    private fun rotateAroundAxis(pt: Vec3, origin: Vec3, axisUnit: Vec3, angleRad: Float): Vec3 {
        val p = pt - origin
        val cosA = kotlin.math.cos(angleRad)
        val sinA = kotlin.math.sin(angleRad)
        val kDotP = axisUnit.dot(p)
        val kCrossP = axisUnit.cross(p)
        return origin + Vec3(
            x = p.x * cosA + kCrossP.x * sinA + axisUnit.x * kDotP * (1f - cosA),
            y = p.y * cosA + kCrossP.y * sinA + axisUnit.y * kDotP * (1f - cosA),
            z = p.z * cosA + kCrossP.z * sinA + axisUnit.z * kDotP * (1f - cosA)
        )
    }

    fun buildUnfoldedFaces(
        scene: BuiltScene,
        shape: ShapeType,
        unfoldRatio: Float
    ): List<UnfoldedFace3D>? {
        if (shape.isRound) return null
        val t = unfoldRatio.coerceIn(0f, 1f)
        if (t <= 0.002f) return null
        val occluders = scene.occluderFaces
        if (occluders.isEmpty()) return null

        var baseIdx = 0
        var minY = Float.POSITIVE_INFINITY
        occluders.forEachIndexed { idx, f ->
            if (f.center.y < minY) {
                minY = f.center.y
                baseIdx = idx
            }
        }
        val baseFace = occluders[baseIdx]
        val baseNormal = baseFace.normal
        val result = ArrayList<UnfoldedFace3D>(occluders.size)
        result.add(UnfoldedFace3D(baseFace.points, isBase = true))

        fun findSharedEdge(fA: List<Vec3>, fB: List<Vec3>): Pair<Vec3, Vec3>? {
            val shared = ArrayList<Vec3>(2)
            for (p in fA) {
                if (fB.any { it.distanceTo(p) < 1e-3f }) shared.add(p)
            }
            return if (shared.size >= 2) shared[0] to shared[1] else null
        }

        var firstSideOrig: RenderFace? = null
        var firstSideHinge: Pair<Vec3, Vec3>? = null
        var firstSideAngle = 0f
        var firstSideAxis = Vec3(1f, 0f, 0f)
        val remaining = ArrayList<RenderFace>()

        for (i in occluders.indices) {
            if (i == baseIdx) continue
            val face = occluders[i]
            val hinge = findSharedEdge(baseFace.points, face.points)
            if (hinge == null) {
                remaining.add(face)
                continue
            }
            val (h0, h1) = hinge
            val axis = (h1 - h0).normalized()
            val cosAngle = face.normal.dot(baseNormal).coerceIn(-1f, 1f)
            val fullAngle = kotlin.math.acos(cosAngle.toDouble()).toFloat()
            val testPos = rotateAroundAxis(face.center, h0, axis, fullAngle * 0.25f)
            val testNeg = rotateAroundAxis(face.center, h0, axis, -fullAngle * 0.25f)
            val sign = if (testPos.distanceTo(baseFace.center) >= testNeg.distanceTo(baseFace.center)) 1f else -1f
            val angle = sign * fullAngle * t
            val unfoldedPts = face.points.map { rotateAroundAxis(it, h0, axis, angle) }
            result.add(UnfoldedFace3D(unfoldedPts, isBase = false))

            if (firstSideOrig == null) {
                firstSideOrig = face
                firstSideHinge = hinge
                firstSideAngle = angle
                firstSideAxis = axis
            }
        }

        for (remFace in remaining) {
            val sideOrig = firstSideOrig
            val sideHinge = firstSideHinge
            if (sideOrig != null && sideHinge != null) {
                val topHinge = findSharedEdge(sideOrig.points, remFace.points)
                if (topHinge != null) {
                    val (th0, th1) = topHinge
                    val tAxis = (th1 - th0).normalized()
                    val cosA = remFace.normal.dot(sideOrig.normal).coerceIn(-1f, 1f)
                    val fullA = kotlin.math.acos(cosA.toDouble()).toFloat()
                    val testP = rotateAroundAxis(remFace.center, th0, tAxis, fullA * 0.25f)
                    val testN = rotateAroundAxis(remFace.center, th0, tAxis, -fullA * 0.25f)
                    val sgn = if (testP.distanceTo(sideOrig.center) >= testN.distanceTo(sideOrig.center)) 1f else -1f
                    val step1 = remFace.points.map { rotateAroundAxis(it, th0, tAxis, sgn * fullA * t) }
                    val step2 = step1.map { rotateAroundAxis(it, sideHinge.first, firstSideAxis, firstSideAngle) }
                    result.add(UnfoldedFace3D(step2, isBase = false))
                    continue
                }
            }
            result.add(UnfoldedFace3D(remFace.points, isBase = false))
        }

        return result
    }
}
