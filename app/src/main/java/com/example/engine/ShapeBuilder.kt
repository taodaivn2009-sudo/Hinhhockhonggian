package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object ShapeBuilder {

    val SUGGESTED_NAMES = listOf("M", "N", "P", "Q", "I", "J", "K", "H", "E", "F", "G")

    data class SpecialRatio(val value: Float, val label: String)

    val SPECIAL_EDGE_RATIOS = listOf(
        SpecialRatio(0.25f, "1/4 đoạn"),
        SpecialRatio(1f / 3f, "1/3 (Trọng tâm)"),
        SpecialRatio(0.5f, "1/2 (Trung điểm)"),
        SpecialRatio(2f / 3f, "2/3 (Trọng tâm)"),
        SpecialRatio(0.75f, "3/4 đoạn")
    )

    private const val COLOR_FACE_DEFAULT = 0x386366F1 // translucent indigo
    private const val COLOR_FACE_SECTION = 0x73F59E0B // amber section
    private const val COLOR_FACE_CUSTOM_PLANE = 0x6110B981 // emerald plane
    private const val COLOR_DIAGONAL = 0xFF64748B.toInt()

    fun getOriginWorld(registry: Map<String, Vec3>, originPoint: String): Vec3 {
        return registry[originPoint] ?: Vec3.ZERO
    }

    fun worldToMath(world: Vec3, origin: Vec3): Vec3 {
        return Vec3(
            x = world.x - origin.x,
            y = world.z - origin.z,
            z = world.y - origin.y
        )
    }

    fun mathToWorld(mx: Float, my: Float, mz: Float, origin: Vec3): Vec3 {
        return Vec3(
            x = origin.x + mx,
            y = origin.y + mz,
            z = origin.z + my
        )
    }

    fun build(snap: GeometrySnapshot): BuiltScene {
        val registry = LinkedHashMap<String, Vec3>()
        val faces = mutableListOf<RenderFace>()
        val edges = mutableListOf<SmartEdge>()
        val clickable = mutableListOf<SegmentRef>()
        var altitude: AltitudeVisual? = null
        var sectionBorder: List<Vec3>? = null
        var roundHint: RoundBodyHint? = null

        val a = snap.a
        val b = snap.b
        val h = snap.h
        val secRatio = snap.secRatio
        val showSection = snap.showSection
        val overrides = snap.vertexOverrides

        fun ov(name: String, default: Vec3): Vec3 = overrides[name] ?: default

        fun addSegment(n1: String, n2: String) {
            if (clickable.none { (it.n1 == n1 && it.n2 == n2) || (it.n1 == n2 && it.n2 == n1) }) {
                clickable.add(SegmentRef(n1, n2))
            }
        }

        fun addPolyhedron(
            verts: List<Vec3>,
            vertNames: List<String>,
            facesIdx: List<List<Int>>,
            baseFaceIndices: List<Int>
        ): PolyMetrics {
            var centerSolid = Vec3.ZERO
            verts.forEach { centerSolid += it }
            if (verts.isNotEmpty()) centerSolid /= verts.size.toFloat()

            data class FaceInfo(val normal: Vec3, val center: Vec3)
            val faceInfos = facesIdx.map { indices ->
                val pts = indices.map { verts[it] }
                var fc = Vec3.ZERO
                pts.forEach { fc += it }
                fc /= pts.size.toFloat()

                val v1 = pts[1] - pts[0]
                val v2 = pts[2] - pts[0]
                var normal = v1.cross(v2).normalized()
                if (normal.dot(fc - centerSolid) < 0f) normal = -normal

                if (snap.faces) {
                    faces.add(RenderFace(pts, normal, fc, COLOR_FACE_DEFAULT))
                }
                FaceInfo(normal, fc)
            }

            val edgeMap = LinkedHashMap<String, Triple<Int, Int, MutableList<Int>>>()
            facesIdx.forEachIndexed { fIdx, indices ->
                for (i in indices.indices) {
                    val u = indices[i]
                    val v = indices[(i + 1) % indices.size]
                    val key = if (u < v) "${u}_$v" else "${v}_$u"
                    val entry = edgeMap.getOrPut(key) { Triple(u, v, mutableListOf()) }
                    entry.third.add(fIdx)
                }
            }

            edgeMap.values.forEach { (u, v, adjFaces) ->
                val normals = adjFaces.map { faceInfos[it].normal }
                val centers = adjFaces.map { faceInfos[it].center }
                edges.add(SmartEdge(verts[u], verts[v], normals, centers))
                if (u < vertNames.size && v < vertNames.size) {
                    addSegment(vertNames[u], vertNames[v])
                }
            }

            return computePolyhedronMetrics(verts, facesIdx, baseFaceIndices)
        }

        fun addSectionPolygon(pts: List<Vec3>) {
            if (pts.size < 3) return
            var fc = Vec3.ZERO
            pts.forEach { fc += it }
            fc /= pts.size.toFloat()
            val normal = (pts[1] - pts[0]).cross(pts[2] - pts[0]).normalized()
            faces.add(RenderFace(pts, normal, fc, COLOR_FACE_SECTION, isSection = true))
            sectionBorder = pts
        }

        fun addCircleEdges(
            radius: Float,
            yPos: Float,
            smartHidden: Boolean,
            cx: Float = 0f,
            cz: Float = 0f,
            segments: Int = 56,
            capNormalY: Float? = -1f
        ) {
            for (i in 0 until segments) {
                val t1 = (i.toFloat() / segments) * (2.0 * PI).toFloat()
                val t2 = ((i + 1).toFloat() / segments) * (2.0 * PI).toFloat()
                val p1 = Vec3(cx + cos(t1) * radius, yPos, cz + sin(t1) * radius)
                val p2 = Vec3(cx + cos(t2) * radius, yPos, cz + sin(t2) * radius)
                if (smartHidden) {
                    val mid = (p1 + p2) * 0.5f
                    val n = Vec3(mid.x - cx, 0f, mid.z - cz).normalized()
                    if (capNormalY != null) {
                        edges.add(SmartEdge(p1, p2, listOf(n, Vec3(0f, capNormalY, 0f)), listOf(mid, Vec3(cx, yPos, cz))))
                    } else {
                        edges.add(SmartEdge(p1, p2, listOf(n), listOf(mid)))
                    }
                } else {
                    edges.add(SmartEdge(p1, p2, forceStyle = EdgeRenderStyle.ALWAYS_SOLID))
                }
            }
        }

        fun addCircleSection(radius: Float, yPos: Float, cx: Float = 0f, cz: Float = 0f) {
            val pts = (0 until 40).map { i ->
                val t = (i.toFloat() / 40f) * (2.0 * PI).toFloat()
                Vec3(cx + cos(t) * radius, yPos, cz + sin(t) * radius)
            }
            addSectionPolygon(pts)
        }

        var polyMetrics: PolyMetrics? = null

        when (snap.shape) {
            ShapeType.PYRAMID3 -> {
                val r = a / sqrt(3f)
                val vA = ov("A", Vec3(0f, 0f, r))
                val vB = ov("B", Vec3(-a / 2f, 0f, -r / 2f))
                val vC = ov("C", Vec3(a / 2f, 0f, -r / 2f))
                val vO = ov("O", Vec3(0f, 0f, 0f))
                val vS = ov("S", Vec3(0f, h, 0f))
                registry["S"] = vS; registry["A"] = vA; registry["B"] = vB; registry["C"] = vC; registry["O"] = vO
                polyMetrics = addPolyhedron(
                    listOf(vS, vA, vB, vC), listOf("S", "A", "B", "C"),
                    listOf(listOf(1, 2, 3), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 3, 1)), listOf(0)
                )
                altitude = AltitudeVisual(vS, vO, vA); addSegment("S", "O")
                if (showSection) addSectionPolygon(listOf(Vec3.lerp(vS, vA, secRatio), Vec3.lerp(vS, vB, secRatio), Vec3.lerp(vS, vC, secRatio)))
            }

            ShapeType.PYRAMID3_SA -> {
                val r = a / sqrt(3f)
                val vA = ov("A", Vec3(-a / 2f, 0f, -r / 2f))
                val vB = ov("B", Vec3(a / 2f, 0f, -r / 2f))
                val vC = ov("C", Vec3(0f, 0f, r))
                val vO = ov("O", vA)
                val vS = ov("S", Vec3(vA.x, h, vA.z))
                registry["S"] = vS; registry["A"] = vA; registry["B"] = vB; registry["C"] = vC; registry["O"] = vO
                polyMetrics = addPolyhedron(
                    listOf(vS, vA, vB, vC), listOf("S", "A", "B", "C"),
                    listOf(listOf(1, 2, 3), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 3, 1)), listOf(0)
                )
                altitude = AltitudeVisual(vS, vO, vB); addSegment("S", "O")
                if (showSection) addSectionPolygon(listOf(Vec3.lerp(vS, vA, secRatio), Vec3.lerp(vS, vB, secRatio), Vec3.lerp(vS, vC, secRatio)))
            }

            ShapeType.TETRA_ORTHO -> {
                val vO = ov("O", Vec3(-a * 0.35f, 0f, -a * 0.35f))
                val vA = ov("A", Vec3(vO.x + a, 0f, vO.z))
                val vB = ov("B", Vec3(vO.x, 0f, vO.z + b))
                val vC = ov("C", Vec3(vO.x, h, vO.z))
                registry["O"] = vO; registry["A"] = vA; registry["B"] = vB; registry["C"] = vC
                polyMetrics = addPolyhedron(
                    listOf(vC, vO, vA, vB), listOf("C", "O", "A", "B"),
                    listOf(listOf(1, 2, 3), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 3, 1)), listOf(0)
                )
                altitude = AltitudeVisual(vC, vO, vA); addSegment("C", "O")
                if (showSection) addSectionPolygon(listOf(Vec3.lerp(vC, vO, secRatio), Vec3.lerp(vC, vA, secRatio), Vec3.lerp(vC, vB, secRatio)))
            }

            ShapeType.PYRAMID4 -> {
                val hf = a / 2f
                val vA = ov("A", Vec3(-hf, 0f, hf))
                val vB = ov("B", Vec3(hf, 0f, hf))
                val vC = ov("C", Vec3(hf, 0f, -hf))
                val vD = ov("D", Vec3(-hf, 0f, -hf))
                val vO = ov("O", Vec3(0f, 0f, 0f))
                val vS = ov("S", Vec3(0f, h, 0f))
                registry["S"] = vS; registry["A"] = vA; registry["B"] = vB; registry["C"] = vC; registry["D"] = vD; registry["O"] = vO
                polyMetrics = addPolyhedron(
                    listOf(vS, vA, vB, vC, vD), listOf("S", "A", "B", "C", "D"),
                    listOf(listOf(1, 2, 3, 4), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 3, 4), listOf(0, 4, 1)), listOf(0)
                )
                edges.add(SmartEdge(vA, vC, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                edges.add(SmartEdge(vB, vD, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                addSegment("A", "C"); addSegment("B", "D")
                altitude = AltitudeVisual(vS, vO, vB); addSegment("S", "O")
                if (showSection) addSectionPolygon(listOf(Vec3.lerp(vS, vA, secRatio), Vec3.lerp(vS, vB, secRatio), Vec3.lerp(vS, vC, secRatio), Vec3.lerp(vS, vD, secRatio)))
            }

            ShapeType.PYRAMID4_SA -> {
                val hf = a / 2f
                val vA = ov("A", Vec3(-hf, 0f, -hf))
                val vB = ov("B", Vec3(hf, 0f, -hf))
                val vC = ov("C", Vec3(hf, 0f, hf))
                val vD = ov("D", Vec3(-hf, 0f, hf))
                val vO = ov("O", vA)
                val vS = ov("S", Vec3(vA.x, h, vA.z))
                registry["S"] = vS; registry["A"] = vA; registry["B"] = vB; registry["C"] = vC; registry["D"] = vD; registry["O"] = vO
                polyMetrics = addPolyhedron(
                    listOf(vS, vA, vB, vC, vD), listOf("S", "A", "B", "C", "D"),
                    listOf(listOf(1, 2, 3, 4), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 3, 4), listOf(0, 4, 1)), listOf(0)
                )
                edges.add(SmartEdge(vA, vC, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                edges.add(SmartEdge(vB, vD, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                addSegment("A", "C"); addSegment("B", "D")
                altitude = AltitudeVisual(vS, vO, vB); addSegment("S", "O")
                if (showSection) addSectionPolygon(listOf(Vec3.lerp(vS, vA, secRatio), Vec3.lerp(vS, vB, secRatio), Vec3.lerp(vS, vC, secRatio), Vec3.lerp(vS, vD, secRatio)))
            }

            ShapeType.PYRAMID_TRAP -> {
                val hf = a / 2f
                val vA = ov("A", Vec3(-hf, 0f, -hf))
                val vB = ov("B", Vec3(-hf, 0f, hf))
                val vC = ov("C", Vec3(0.15f * a, 0f, hf))
                val vD = ov("D", Vec3(0.8f * a, 0f, -hf))
                val vO = ov("O", vA)
                val vS = ov("S", Vec3(vA.x, h, vA.z))
                registry["S"] = vS; registry["A"] = vA; registry["B"] = vB; registry["C"] = vC; registry["D"] = vD; registry["O"] = vO
                polyMetrics = addPolyhedron(
                    listOf(vS, vA, vB, vC, vD), listOf("S", "A", "B", "C", "D"),
                    listOf(listOf(1, 2, 3, 4), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 3, 4), listOf(0, 4, 1)), listOf(0)
                )
                altitude = AltitudeVisual(vS, vO, vD); addSegment("S", "O")
                if (showSection) addSectionPolygon(listOf(Vec3.lerp(vS, vA, secRatio), Vec3.lerp(vS, vB, secRatio), Vec3.lerp(vS, vC, secRatio), Vec3.lerp(vS, vD, secRatio)))
            }

            ShapeType.CUBE, ShapeType.BOX -> {
                val la = a
                val lb = if (snap.shape == ShapeType.CUBE) a else b
                val lh = if (snap.shape == ShapeType.CUBE) a else h
                val hx = la / 2f
                val hz = lb / 2f
                val names = listOf("A", "B", "C", "D", "A'", "B'", "C'", "D'")
                val raw = listOf(
                    Vec3(-hx, 0f, hz), Vec3(hx, 0f, hz), Vec3(hx, 0f, -hz), Vec3(-hx, 0f, -hz),
                    Vec3(-hx, lh, hz), Vec3(hx, lh, hz), Vec3(hx, lh, -hz), Vec3(-hx, lh, -hz)
                )
                val verts = raw.mapIndexed { idx, vec -> ov(names[idx], vec) }
                val vO = ov("O", Vec3(0f, 0f, 0f))
                names.forEachIndexed { idx, n -> registry[n] = verts[idx] }
                registry["O"] = vO
                polyMetrics = addPolyhedron(
                    verts, names,
                    listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6, 7), listOf(0, 1, 5, 4), listOf(1, 2, 6, 5), listOf(2, 3, 7, 6), listOf(3, 0, 4, 7)),
                    listOf(0, 1)
                )
                altitude = AltitudeVisual(verts[4], verts[0], verts[1]); addSegment("A'", "A")
                if (showSection) {
                    val t = 1f - secRatio
                    addSectionPolygon(listOf(
                        Vec3.lerp(verts[0], verts[4], t),
                        Vec3.lerp(verts[1], verts[5], t),
                        Vec3.lerp(verts[2], verts[6], t),
                        Vec3.lerp(verts[3], verts[7], t)
                    ))
                }
            }

            ShapeType.OCTAHEDRON -> {
                val hf = a / 2f
                val hHalf = a / sqrt(2f)
                val vS = ov("S", Vec3(0f, hHalf, 0f))
                val vS1 = ov("S'", Vec3(0f, -hHalf, 0f))
                val vA = ov("A", Vec3(-hf, 0f, hf))
                val vB = ov("B", Vec3(hf, 0f, hf))
                val vC = ov("C", Vec3(hf, 0f, -hf))
                val vD = ov("D", Vec3(-hf, 0f, -hf))
                val vO = ov("O", Vec3(0f, 0f, 0f))
                val verts = listOf(vS, vS1, vA, vB, vC, vD)
                val names = listOf("S", "S'", "A", "B", "C", "D")
                names.forEachIndexed { idx, n -> registry[n] = verts[idx] }
                registry["O"] = vO
                polyMetrics = addPolyhedron(
                    verts, names,
                    listOf(
                        listOf(0, 2, 3), listOf(0, 3, 4), listOf(0, 4, 5), listOf(0, 5, 2),
                        listOf(1, 3, 2), listOf(1, 4, 3), listOf(1, 5, 4), listOf(1, 2, 5)
                    ),
                    emptyList()
                )
                altitude = AltitudeVisual(vS, vO, vB); addSegment("S", "O")
                if (showSection) {
                    addSectionPolygon(listOf(
                        Vec3.lerp(vS, vA, secRatio),
                        Vec3.lerp(vS, vB, secRatio),
                        Vec3.lerp(vS, vC, secRatio),
                        Vec3.lerp(vS, vD, secRatio)
                    ))
                }
            }

            ShapeType.PRISM3 -> {
                val r = a / sqrt(3f)
                val names = listOf("A", "B", "C", "A'", "B'", "C'")
                val raw = listOf(
                    Vec3(0f, 0f, r), Vec3(-a / 2f, 0f, -r / 2f), Vec3(a / 2f, 0f, -r / 2f),
                    Vec3(0f, h, r), Vec3(-a / 2f, h, -r / 2f), Vec3(a / 2f, h, -r / 2f)
                )
                val verts = raw.mapIndexed { idx, vec -> ov(names[idx], vec) }
                val vO = ov("O", Vec3(0f, 0f, 0f))
                names.forEachIndexed { idx, n -> registry[n] = verts[idx] }
                registry["O"] = vO
                polyMetrics = addPolyhedron(
                    verts, names,
                    listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(0, 1, 4, 3), listOf(1, 2, 5, 4), listOf(2, 0, 3, 5)),
                    listOf(0, 1)
                )
                altitude = AltitudeVisual(verts[3], verts[0], verts[2]); addSegment("A'", "A")
                if (showSection) {
                    val t = 1f - secRatio
                    addSectionPolygon(listOf(Vec3.lerp(verts[0], verts[3], t), Vec3.lerp(verts[1], verts[4], t), Vec3.lerp(verts[2], verts[5], t)))
                }
            }

            ShapeType.PRISM3_OBLIQUE -> {
                val r = a / sqrt(3f)
                val names = listOf("A", "B", "C", "A'", "B'", "C'")
                val a0 = Vec3(0f, 0f, r)
                val b0 = Vec3(-a / 2f, 0f, -r / 2f)
                val c0 = Vec3(a / 2f, 0f, -r / 2f)
                val shift = Vec3(-a0.x, h, -a0.z)
                val raw = listOf(a0, b0, c0, a0 + shift, b0 + shift, c0 + shift)
                val verts = raw.mapIndexed { idx, vec -> ov(names[idx], vec) }
                val vO = ov("O", Vec3(0f, 0f, 0f))
                names.forEachIndexed { idx, n -> registry[n] = verts[idx] }
                registry["O"] = vO
                polyMetrics = addPolyhedron(
                    verts, names,
                    listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(0, 1, 4, 3), listOf(1, 2, 5, 4), listOf(2, 0, 3, 5)),
                    listOf(0, 1)
                )
                altitude = AltitudeVisual(verts[3], vO, verts[0]); addSegment("A'", "O")
                if (showSection) {
                    val t = 1f - secRatio
                    addSectionPolygon(listOf(Vec3.lerp(verts[0], verts[3], t), Vec3.lerp(verts[1], verts[4], t), Vec3.lerp(verts[2], verts[5], t)))
                }
            }

            ShapeType.PRISM6 -> {
                val r = a * 0.65f
                val names = listOf("A", "B", "C", "D", "E", "F", "A'", "B'", "C'", "D'", "E'", "F'")
                val raw = mutableListOf<Vec3>()
                for (i in 0 until 6) {
                    val ang = (i * PI / 3.0).toFloat()
                    raw.add(Vec3(cos(ang) * r, 0f, sin(ang) * r))
                }
                for (i in 0 until 6) {
                    val ang = (i * PI / 3.0).toFloat()
                    raw.add(Vec3(cos(ang) * r, h, sin(ang) * r))
                }
                val verts = raw.mapIndexed { idx, vec -> ov(names[idx], vec) }
                val vO = ov("O", Vec3(0f, 0f, 0f))
                names.forEachIndexed { idx, n -> registry[n] = verts[idx] }
                registry["O"] = vO
                polyMetrics = addPolyhedron(
                    verts, names,
                    listOf(
                        listOf(0, 1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 10, 11),
                        listOf(0, 1, 7, 6), listOf(1, 2, 8, 7), listOf(2, 3, 9, 8),
                        listOf(3, 4, 10, 9), listOf(4, 5, 11, 10), listOf(5, 0, 6, 11)
                    ),
                    listOf(0, 1)
                )
                altitude = AltitudeVisual(verts[6], verts[0], vO); addSegment("A'", "A")
                if (showSection) {
                    val t = 1f - secRatio
                    addSectionPolygon((0 until 6).map { i -> Vec3.lerp(verts[i], verts[i + 6], t) })
                }
            }

            ShapeType.CYLINDER -> {
                val defaultR = a / 2f
                val vO = ov("O", Vec3(0f, 0f, 0f))
                val vO1 = ov("O'", Vec3(0f, h, 0f))
                val vA = ov("A", Vec3(vO.x + defaultR, vO.y, vO.z))
                val vB = ov("B", Vec3(vO.x - defaultR, vO.y, vO.z))
                val actualR = max(0.2f, vA.distanceTo(vO))
                val segments = 40
                val botPts = (0 until segments).map { i ->
                    val t = (i.toFloat() / segments) * (2.0 * PI).toFloat()
                    Vec3(vO.x + cos(t) * actualR, vO.y, vO.z + sin(t) * actualR)
                }
                val topPts = (0 until segments).map { i ->
                    val t = (i.toFloat() / segments) * (2.0 * PI).toFloat()
                    Vec3(vO1.x + cos(t) * actualR, vO1.y, vO1.z + sin(t) * actualR)
                }
                val axisMid = (vO + vO1) * 0.5f
                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    val b1 = botPts[i]
                    val b2 = botPts[next]
                    val t1 = topPts[i]
                    val t2 = topPts[next]
                    val fc = (b1 + b2 + t2 + t1) * 0.25f
                    var n = (b2 - b1).cross(t1 - b1).normalized()
                    if (n.dot(fc - axisMid) < 0f) n = -n

                    if (snap.faces) {
                        faces.add(RenderFace(listOf(b1, b2, t2, t1), n, fc, COLOR_FACE_DEFAULT, isCurvedMesh = true))
                    }
                    // Smart hidden-edge visibility on BOTH bottom and top rims (works from above and below)
                    edges.add(SmartEdge(b1, b2, listOf(n, Vec3(0f, -1f, 0f)), listOf(fc, vO)))
                    edges.add(SmartEdge(t1, t2, listOf(n, Vec3(0f, 1f, 0f)), listOf(fc, vO1)))
                }
                if (snap.faces) {
                    faces.add(RenderFace(botPts, Vec3(0f, -1f, 0f), vO, COLOR_FACE_DEFAULT, isCurvedMesh = true))
                    faces.add(RenderFace(topPts, Vec3(0f, 1f, 0f), vO1, COLOR_FACE_DEFAULT, isCurvedMesh = true))
                }
                edges.add(SmartEdge(vA, vB, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                registry["O"] = vO; registry["O'"] = vO1; registry["A"] = vA; registry["B"] = vB
                altitude = AltitudeVisual(vO1, vO, vA); addSegment("O'", "O"); addSegment("A", "B")
                roundHint = RoundBodyHint.Cylinder(vO, vO1, actualR)
                if (showSection) {
                    val secCenter = Vec3.lerp(vO, vO1, 1f - secRatio)
                    addCircleSection(actualR, secCenter.y, secCenter.x, secCenter.z)
                }
            }

            ShapeType.CONE -> {
                val defaultR = a / 2f
                val vS = ov("S", Vec3(0f, h, 0f))
                val vO = ov("O", Vec3(0f, 0f, 0f))
                val vA = ov("A", Vec3(vO.x + defaultR, vO.y, vO.z))
                val vB = ov("B", Vec3(vO.x - defaultR, vO.y, vO.z))
                val actualR = max(0.2f, vA.distanceTo(vO))
                val segments = 40
                val basePts = (0 until segments).map { i ->
                    val t = (i.toFloat() / segments) * (2.0 * PI).toFloat()
                    Vec3(vO.x + cos(t) * actualR, vO.y, vO.z + sin(t) * actualR)
                }
                val axisMid = (vO + vS) * 0.5f
                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    val p1 = basePts[i]
                    val p2 = basePts[next]
                    val fc = (vS + p1 + p2) / 3f
                    var n = (p2 - p1).cross(vS - p1).normalized()
                    if (n.dot(fc - axisMid) < 0f) n = -n

                    if (snap.faces) {
                        faces.add(RenderFace(listOf(vS, p1, p2), n, fc, COLOR_FACE_DEFAULT, isCurvedMesh = true))
                    }
                    edges.add(SmartEdge(p1, p2, listOf(n, Vec3(0f, -1f, 0f)), listOf(fc, vO)))
                }
                if (snap.faces) {
                    faces.add(RenderFace(basePts, Vec3(0f, -1f, 0f), vO, COLOR_FACE_DEFAULT, isCurvedMesh = true))
                }
                edges.add(SmartEdge(vA, vB, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                registry["S"] = vS; registry["O"] = vO; registry["A"] = vA; registry["B"] = vB
                altitude = AltitudeVisual(vS, vO, vA)
                addSegment("S", "O"); addSegment("S", "A"); addSegment("S", "B"); addSegment("A", "B")
                roundHint = RoundBodyHint.Cone(vS, vO, actualR)
                if (showSection) {
                    val secCenter = Vec3.lerp(vS, vO, secRatio)
                    addCircleSection(actualR * secRatio, secCenter.y, secCenter.x, secCenter.z)
                }
            }

            ShapeType.SPHERE -> {
                val defaultR = a / 2f
                val vO = ov("O", Vec3(0f, defaultR, 0f))
                val vA = ov("A", Vec3(vO.x + defaultR, vO.y, vO.z))
                val actualR = max(0.2f, vA.distanceTo(vO))
                val vB = ov("B", Vec3(vO.x - (vA.x - vO.x), vO.y, vO.z - (vA.z - vO.z)))
                val vN = ov("N", Vec3(vO.x, vO.y + actualR, vO.z))

                // True 3D UV Sphere mesh for volumetric lighting & proper depth sorting with sections
                if (snap.faces) {
                    val latSteps = 14
                    val lonSteps = 28
                    for (lat in 0 until latSteps) {
                        val phi1 = (-PI / 2.0 + PI * lat / latSteps).toFloat()
                        val phi2 = (-PI / 2.0 + PI * (lat + 1) / latSteps).toFloat()
                        val y1 = sin(phi1) * actualR
                        val r1 = cos(phi1) * actualR
                        val y2 = sin(phi2) * actualR
                        val r2 = cos(phi2) * actualR
                        for (lon in 0 until lonSteps) {
                            val t1 = (2.0 * PI * lon / lonSteps).toFloat()
                            val t2 = (2.0 * PI * (lon + 1) / lonSteps).toFloat()
                            val p00 = Vec3(vO.x + cos(t1) * r1, vO.y + y1, vO.z + sin(t1) * r1)
                            val p10 = Vec3(vO.x + cos(t2) * r1, vO.y + y1, vO.z + sin(t2) * r1)
                            val p11 = Vec3(vO.x + cos(t2) * r2, vO.y + y2, vO.z + sin(t2) * r2)
                            val p01 = Vec3(vO.x + cos(t1) * r2, vO.y + y2, vO.z + sin(t1) * r2)
                            val pts = when (lat) {
                                0 -> listOf(p00, p11, p01)
                                latSteps - 1 -> listOf(p00, p10, p11)
                                else -> listOf(p00, p10, p11, p01)
                            }
                            var fc = Vec3.ZERO
                            pts.forEach { fc += it }
                            fc /= pts.size.toFloat()
                            val n = (fc - vO).normalized()
                            faces.add(RenderFace(pts, n, fc, COLOR_FACE_DEFAULT, isCurvedMesh = true))
                        }
                    }
                }

                // Equator great circle (exact spherical horizon visibility from all angles)
                addCircleEdges(actualR, vO.y, true, vO.x, vO.z, segments = 56, capNormalY = null)

                // Prime Meridian great circle through N, A, B so horizontal & vertical rotation feels unmistakably 3D
                val uDir = Vec3(vA.x - vO.x, 0f, vA.z - vO.z).let {
                    if (it.lengthSq() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
                }
                val meridianSegs = 48
                for (i in 0 until meridianSegs) {
                    val t1 = (i.toFloat() / meridianSegs) * (2.0 * PI).toFloat()
                    val t2 = ((i + 1).toFloat() / meridianSegs) * (2.0 * PI).toFloat()
                    val p1 = vO + uDir * (cos(t1) * actualR) + Vec3(0f, sin(t1) * actualR, 0f)
                    val p2 = vO + uDir * (cos(t2) * actualR) + Vec3(0f, sin(t2) * actualR, 0f)
                    val mid = (p1 + p2) * 0.5f
                    val n = (mid - vO).normalized()
                    edges.add(SmartEdge(p1, p2, listOf(n), listOf(mid), colorArgb = 0xAA94A3B8.toInt()))
                }

                edges.add(SmartEdge(vA, vB, forceStyle = EdgeRenderStyle.ALWAYS_DASHED, colorArgb = COLOR_DIAGONAL))
                registry["O"] = vO; registry["A"] = vA; registry["B"] = vB; registry["N"] = vN
                altitude = AltitudeVisual(vN, vO, vA); addSegment("N", "O"); addSegment("A", "B")
                roundHint = RoundBodyHint.Sphere(vO, actualR)
                if (showSection) {
                    val dy = (0.5f - secRatio) * 1.7f * actualR
                    val rSec = sqrt(max(0.01f, actualR * actualR - dy * dy))
                    addCircleSection(rSec, vO.y + dy, vO.x, vO.z)
                }
            }
        }

        // Custom points on edges
        val customNames = mutableSetOf<String>()
        snap.customPoints.forEach { pt ->
            val vFrom = registry[pt.from]
            val vTo = registry[pt.to]
            if (vFrom != null && vTo != null) {
                registry[pt.name] = Vec3.lerp(vFrom, vTo, pt.ratio)
                customNames.add(pt.name)
            }
        }

        // Custom XYZ points
        val origin = getOriginWorld(registry, snap.originPoint)
        snap.customXYZPoints.forEach { pt ->
            registry[pt.name] = mathToWorld(pt.x, pt.y, pt.z, origin)
            customNames.add(pt.name)
        }

        // Custom lines clickable registration
        snap.customLines.forEach { ln ->
            if (registry.containsKey(ln.p1) && registry.containsKey(ln.p2)) {
                addSegment(ln.p1, ln.p2)
            }
        }

        // Custom planes
        val customPlaneBorders = mutableListOf<List<Vec3>>()
        snap.customPlanes.forEach { pl ->
            val pts = pl.pts.mapNotNull { registry[it] }
            if (pts.size >= 3) {
                var fc = Vec3.ZERO
                pts.forEach { fc += it }
                fc /= pts.size.toFloat()
                val normal = (pts[1] - pts[0]).cross(pts[2] - pts[0]).normalized()
                faces.add(RenderFace(pts, normal, fc, COLOR_FACE_CUSTOM_PLANE, isCustomPlane = true))
                customPlaneBorders.add(pts)
            }
        }

        val metrics = computeMetrics(snap, registry, polyMetrics)

        return BuiltScene(
            pointRegistry = registry,
            customPointNames = customNames,
            faces = faces,
            edges = edges,
            altitude = altitude,
            sectionBorder = sectionBorder,
            customPlaneBorders = customPlaneBorders,
            clickableSegments = clickable,
            metrics = metrics,
            roundBodyHint = roundHint
        )
    }

    private data class PolyMetrics(val sDay: Float, val sXq: Float, val sTp: Float, val vol: Float)

    private fun computePolygonArea3D(pts: List<Vec3>): Float {
        if (pts.size < 3) return 0f
        var area = 0f
        val p0 = pts[0]
        for (i in 1 until pts.size - 1) {
            val cross = (pts[i] - p0).cross(pts[i + 1] - p0)
            area += 0.5f * cross.length()
        }
        return area
    }

    private fun computePolyhedronMetrics(verts: List<Vec3>, facesIdx: List<List<Int>>, baseFaceIndices: List<Int>): PolyMetrics {
        var sDay = 0f
        var sXq = 0f
        var vol = 0f
        var center = Vec3.ZERO
        verts.forEach { center += it }
        if (verts.isNotEmpty()) center /= verts.size.toFloat()

        var sTp = 0f
        facesIdx.forEachIndexed { fIdx, indices ->
            val facePts = indices.map { verts[it] }
            val fArea = computePolygonArea3D(facePts)
            sTp += fArea
            if (baseFaceIndices.contains(fIdx)) {
                if (fIdx == baseFaceIndices.first()) sDay = fArea
            } else {
                sXq += fArea
            }
            val p0 = facePts[0]
            for (i in 1 until facePts.size - 1) {
                val cross = (facePts[i] - p0).cross(facePts[i + 1] - p0)
                val ref = p0 - center
                vol += abs(cross.dot(ref)) / 6f
            }
        }
        if (baseFaceIndices.isEmpty()) {
            sDay = 0f
            sXq = sTp
        }
        return PolyMetrics(sDay, sXq, sTp, vol)
    }

    private fun computeMetrics(snap: GeometrySnapshot, reg: Map<String, Vec3>, poly: PolyMetrics?): GeometryMetrics {
        fun dist(n1: String, n2: String): Float {
            val p1 = reg[n1] ?: return 0f
            val p2 = reg[n2] ?: return 0f
            return p1.distanceTo(p2)
        }
        fun f2(v: Float) = String.format(java.util.Locale.US, "%.2f", v)

        var sDay = poly?.sDay ?: 0f
        var sXq = poly?.sXq ?: 0f
        var sTp = poly?.sTp ?: 0f
        var vol = poly?.vol ?: 0f
        var realH = snap.h
        val title: String
        val dims: List<Pair<String, String>>
        val formulas: List<String>

        when (snap.shape) {
            ShapeType.PYRAMID3, ShapeType.PYRAMID3_SA -> {
                title = if (snap.shape == ShapeType.PYRAMID3) "Chóp tam giác S.ABC" else "Chóp S.ABC (SA ⊥ đáy)"
                realH = dist("S", "O")
                dims = listOf(
                    "Đường cao thực (SO)" to f2(realH),
                    "Cạnh đáy (AB | BC | CA)" to "${f2(dist("A", "B"))} | ${f2(dist("B", "C"))} | ${f2(dist("C", "A"))}",
                    "Cạnh bên (SA | SB | SC)" to "${f2(dist("S", "A"))} | ${f2(dist("S", "B"))} | ${f2(dist("S", "C"))}"
                )
                formulas = listOf(
                    "• Sđáy = ½ |[AB→, AC→]| (tính theo tọa độ 3D thực)",
                    "• Thể tích V = ⅓ · S(ABC) · d(S, (ABC))"
                )
            }
            ShapeType.TETRA_ORTHO -> {
                title = "Tứ diện vuông O.ABC"
                realH = dist("C", "O")
                dims = listOf(
                    "Độ dài OA | OB | OC" to "${f2(dist("O", "A"))} | ${f2(dist("O", "B"))} | ${f2(dist("O", "C"))}",
                    "Cạnh đáy AB | BC | CA" to "${f2(dist("A", "B"))} | ${f2(dist("B", "C"))} | ${f2(dist("C", "A"))}"
                )
                formulas = listOf("• Thể tích thực V = ⅙ |[OA→, OB→] · OC→|")
            }
            ShapeType.PYRAMID4, ShapeType.PYRAMID4_SA, ShapeType.PYRAMID_TRAP -> {
                title = when (snap.shape) {
                    ShapeType.PYRAMID4 -> "Chóp tứ giác S.ABCD"
                    ShapeType.PYRAMID4_SA -> "Chóp S.ABCD (SA ⊥ đáy)"
                    else -> "Chóp đáy hình thang S.ABCD"
                }
                realH = dist("S", "O")
                dims = listOf(
                    "Đường cao thực (SO)" to f2(realH),
                    "Cạnh đáy AB|BC|CD|DA" to "${f2(dist("A", "B"))} | ${f2(dist("B", "C"))} | ${f2(dist("C", "D"))} | ${f2(dist("D", "A"))}",
                    "Cạnh bên SA|SB|SC|SD" to "${f2(dist("S", "A"))} | ${f2(dist("S", "B"))} | ${f2(dist("S", "C"))} | ${f2(dist("S", "D"))}"
                )
                formulas = listOf(
                    "• Sxq = S(SAB) + S(SBC) + S(SCD) + S(SDA)",
                    "• Thể tích V = ⅓ · S(ABCD) · d(S, đáy)"
                )
            }
            ShapeType.CUBE, ShapeType.BOX -> {
                title = if (snap.shape == ShapeType.CUBE) "Hình lập phương" else "Hình hộp chữ nhật"
                realH = dist("A", "A'")
                dims = listOf(
                    "Chiều cao thực (AA')" to f2(realH),
                    "Cạnh đáy AB | AD" to "${f2(dist("A", "B"))} | ${f2(dist("A", "D"))}",
                    "Đường chéo khối AC'" to f2(dist("A", "C'"))
                )
                formulas = listOf(
                    "• Stp = Tổng diện tích 6 mặt thực tế",
                    "• Thể tích V = S(ABCD) · h"
                )
            }
            ShapeType.OCTAHEDRON -> {
                title = "Khối bát diện S.ABCD.S'"
                realH = dist("S", "S'")
                sDay = computePolygonArea3D(listOfNotNull(reg["A"], reg["B"], reg["C"], reg["D"]))
                dims = listOf(
                    "Trục đứng SS'" to f2(realH),
                    "Thiết diện giữa S(ABCD)" to f2(sDay),
                    "Cạnh bên SA | AB" to "${f2(dist("S", "A"))} | ${f2(dist("A", "B"))}"
                )
                formulas = listOf(
                    "• Stp = Tổng diện tích 8 mặt tam giác",
                    "• Thể tích V = V(S.ABCD) + V(S'.ABCD)"
                )
            }
            ShapeType.PRISM3, ShapeType.PRISM3_OBLIQUE -> {
                title = if (snap.shape == ShapeType.PRISM3) "Lăng trụ tam giác ABC.A'B'C'" else "Lăng trụ xiên ABC.A'B'C'"
                realH = if (snap.shape == ShapeType.PRISM3) dist("A", "A'") else dist("A'", "O")
                dims = listOf(
                    "Chiều cao thực (h)" to f2(realH),
                    "Cạnh bên AA'" to f2(dist("A", "A'")),
                    "Cạnh đáy AB | BC | CA" to "${f2(dist("A", "B"))} | ${f2(dist("B", "C"))} | ${f2(dist("C", "A"))}"
                )
                formulas = listOf(
                    "• Thể tích V = S(ABC) · d(A', (ABC))",
                    "• Tự động cập nhật khi kéo nghiêng lăng trụ!"
                )
            }
            ShapeType.PRISM6 -> {
                title = "Lăng trụ lục giác ABCDEF.A'..F'"
                realH = dist("A", "A'")
                dims = listOf(
                    "Chiều cao thực (AA')" to f2(realH),
                    "Cạnh đáy AB | BC" to "${f2(dist("A", "B"))} | ${f2(dist("B", "C"))}"
                )
                formulas = listOf(
                    "• Sđáy = Diện tích lục giác ABCDEF",
                    "• Thể tích V = Sđáy · AA'"
                )
            }
            ShapeType.CYLINDER -> {
                val r = max(0.2f, dist("O", "A"))
                realH = max(0.2f, dist("O", "O'"))
                title = "Hình trụ tròn (R = ${f2(r)})"
                sDay = (PI * r * r).toFloat()
                sXq = (2.0 * PI * r * realH).toFloat()
                sTp = sXq + 2f * sDay
                vol = sDay * realH
                dims = listOf("Bán kính thực (R = OA)" to f2(r), "Chiều cao thực (h = OO')" to f2(realH))
                formulas = listOf(
                    "• Kéo điểm A hoặc B để đổi bán kính R",
                    "• Kéo điểm O' theo chiều cao để đổi h"
                )
            }
            ShapeType.CONE -> {
                val r = max(0.2f, dist("O", "A"))
                realH = max(0.2f, dist("S", "O"))
                val l = dist("S", "A")
                title = "Hình nón (R = ${f2(r)})"
                sDay = (PI * r * r).toFloat()
                sXq = (PI * r * l).toFloat()
                sTp = sXq + sDay
                vol = (sDay * realH) / 3f
                dims = listOf(
                    "Bán kính thực (R = OA)" to f2(r),
                    "Chiều cao thực (h = SO)" to f2(realH),
                    "Đường sinh thực (l = SA)" to f2(l)
                )
                formulas = listOf(
                    "• Kéo điểm A để đổi bán kính đáy R",
                    "• Kéo đỉnh S để đổi chiều cao SO và đường sinh SA"
                )
            }
            ShapeType.SPHERE -> {
                val r = max(0.2f, dist("O", "A"))
                realH = 2f * r
                title = "Hình cầu (R = ${f2(r)})"
                sDay = (PI * r * r).toFloat()
                sXq = (4.0 * PI * r * r).toFloat()
                sTp = sXq
                vol = ((4.0 / 3.0) * PI * r.toDouble().pow(3.0)).toFloat()
                dims = listOf("Bán kính thực (R = OA)" to f2(r), "Đường kính (2R = AB)" to f2(2f * r))
                formulas = listOf("• Kéo điểm A hoặc B để thay đổi trực tiếp bán kính mặt cầu R!")
            }
        }

        return GeometryMetrics(title, realH, sDay, sXq, sTp, vol, dims, formulas)
    }

    // ================= RÀNG BUỘC HÌNH HỌC KHI KÉO ĐỈNH =================
    data class ConstraintResult(
        val updatedOverrides: Map<String, Vec3>,
        val linkedPartners: Set<String>,
        val message: String
    )

    fun applyGeometricRelationships(
        snap: GeometrySnapshot,
        registry: Map<String, Vec3>,
        draggedName: String,
        newPos: Vec3
    ): ConstraintResult {
        val nextOverrides = snap.vertexOverrides.toMutableMap()
        val linked = mutableSetOf<String>()
        nextOverrides[draggedName] = newPos

        if (!snap.keepRelations) return ConstraintResult(nextOverrides, emptySet(), "")

        val sh = snap.shape
        val pyramidOrCone = sh in setOf(
            ShapeType.PYRAMID3, ShapeType.PYRAMID3_SA, ShapeType.PYRAMID4,
            ShapeType.PYRAMID4_SA, ShapeType.PYRAMID_TRAP, ShapeType.CONE
        )

        if (pyramidOrCone) {
            if (draggedName == "S") {
                val curO = registry["O"] ?: Vec3.ZERO
                nextOverrides["O"] = Vec3(newPos.x, curO.y, newPos.z)
                linked.add("O")
                return ConstraintResult(nextOverrides, linked, "O đi theo S (SO ⊥ đáy)")
            }
            if (draggedName == "O") {
                val curS = registry["S"] ?: Vec3(0f, snap.h, 0f)
                nextOverrides["S"] = Vec3(newPos.x, curS.y, newPos.z)
                linked.add("S")
                return ConstraintResult(nextOverrides, linked, "S đi theo O (SO ⊥ đáy)")
            }
        }

        if (sh == ShapeType.CYLINDER) {
            if (draggedName == "O'") {
                val curO = registry["O"] ?: Vec3.ZERO
                val dx = newPos.x - curO.x
                val dz = newPos.z - curO.z
                nextOverrides["O"] = Vec3(newPos.x, curO.y, newPos.z)
                registry["A"]?.let { nextOverrides["A"] = it + Vec3(dx, 0f, dz) }
                registry["B"]?.let { nextOverrides["B"] = it + Vec3(dx, 0f, dz) }
                linked.addAll(listOf("O", "A", "B"))
                return ConstraintResult(nextOverrides, linked, "O, A, B đi theo O' (OO' ⊥ đáy)")
            }
            if (draggedName == "O") {
                val prevO = registry["O"] ?: Vec3.ZERO
                val dx = newPos.x - prevO.x
                val dz = newPos.z - prevO.z
                val curO1 = registry["O'"] ?: Vec3(0f, snap.h, 0f)
                nextOverrides["O'"] = Vec3(newPos.x, curO1.y, newPos.z)
                registry["A"]?.let { nextOverrides["A"] = it + Vec3(dx, 0f, dz) }
                registry["B"]?.let { nextOverrides["B"] = it + Vec3(dx, 0f, dz) }
                linked.addAll(listOf("O'", "A", "B"))
                return ConstraintResult(nextOverrides, linked, "O', A, B đi theo O")
            }
            if (draggedName == "A" || draggedName == "B") {
                val curO = registry["O"] ?: Vec3.ZERO
                val newR = max(0.5f, hypot(newPos.x - curO.x, newPos.z - curO.z))
                val dirX = (newPos.x - curO.x) / newR
                val dirZ = (newPos.z - curO.z) / newR
                val opp = if (draggedName == "A") "B" else "A"
                nextOverrides[opp] = Vec3(curO.x - dirX * newR, curO.y, curO.z - dirZ * newR)
                linked.add(opp)
                return ConstraintResult(nextOverrides, linked, "Bán kính R = ${String.format(java.util.Locale.US, "%.2f", newR)}")
            }
        }

        if (sh == ShapeType.CONE || sh == ShapeType.SPHERE) {
            if (draggedName == "A" || draggedName == "B") {
                val curO = registry["O"] ?: Vec3.ZERO
                val newR = max(0.5f, hypot(newPos.x - curO.x, newPos.z - curO.z))
                val dirX = (newPos.x - curO.x) / newR
                val dirZ = (newPos.z - curO.z) / newR
                val opp = if (draggedName == "A") "B" else "A"
                nextOverrides[opp] = Vec3(curO.x - dirX * newR, curO.y, curO.z - dirZ * newR)
                linked.add(opp)
                if (sh == ShapeType.SPHERE && registry.containsKey("N")) {
                    nextOverrides["N"] = Vec3(curO.x, curO.y + newR, curO.z)
                    linked.add("N")
                }
                return ConstraintResult(nextOverrides, linked, "Bán kính thực R = ${String.format(java.util.Locale.US, "%.2f", newR)}")
            }
        }

        if (sh in setOf(ShapeType.PRISM3, ShapeType.PRISM6, ShapeType.CUBE, ShapeType.BOX)) {
            val baseList = when (sh) {
                ShapeType.PRISM3 -> listOf("A", "B", "C")
                ShapeType.PRISM6 -> listOf("A", "B", "C", "D", "E", "F")
                else -> listOf("A", "B", "C", "D")
            }
            val topList = baseList.map { "$it'" }

            fun updateCenterO() {
                var sum = Vec3.ZERO
                var cnt = 0
                baseList.forEach { n ->
                    val v = nextOverrides[n] ?: registry[n]
                    if (v != null) {
                        sum += v
                        cnt++
                    }
                }
                if (cnt > 0) {
                    nextOverrides["O"] = sum / cnt.toFloat()
                    linked.add("O")
                }
            }

            val bIdx = baseList.indexOf(draggedName)
            val tIdx = topList.indexOf(draggedName)
            if (bIdx != -1) {
                val partnerTop = topList[bIdx]
                val curTop = registry[partnerTop] ?: Vec3(0f, snap.h, 0f)
                nextOverrides[partnerTop] = Vec3(newPos.x, curTop.y, newPos.z)
                linked.add(partnerTop)
                updateCenterO()
                return ConstraintResult(nextOverrides, linked, "$partnerTop đi theo $draggedName ($draggedName$partnerTop ⊥ đáy)")
            }
            if (tIdx != -1) {
                val partnerBase = baseList[tIdx]
                val curBase = registry[partnerBase] ?: Vec3.ZERO
                nextOverrides[partnerBase] = Vec3(newPos.x, curBase.y, newPos.z)
                linked.add(partnerBase)
                topList.forEach { tName ->
                    if (tName != draggedName) {
                        registry[tName]?.let { p ->
                            nextOverrides[tName] = Vec3(p.x, newPos.y, p.z)
                            linked.add(tName)
                        }
                    }
                }
                updateCenterO()
                return ConstraintResult(nextOverrides, linked, "$partnerBase & mặt đáy trên đi theo $draggedName")
            }
            if (draggedName == "O") {
                val prevO = registry["O"] ?: Vec3.ZERO
                val delta = newPos - prevO
                (baseList + topList).forEach { vName ->
                    registry[vName]?.let {
                        nextOverrides[vName] = it + delta
                        linked.add(vName)
                    }
                }
                return ConstraintResult(nextOverrides, linked, "Cả khối tịnh tiến theo tâm O")
            }
        }

        if (sh == ShapeType.PRISM3_OBLIQUE && draggedName == "A'") {
            val curO = registry["O"] ?: Vec3.ZERO
            nextOverrides["O"] = Vec3(newPos.x, curO.y, newPos.z)
            val vA = registry["A"] ?: Vec3.ZERO
            val vAA1 = newPos - vA
            listOf("B" to "B'", "C" to "C'").forEach { (bName, tName) ->
                registry[bName]?.let {
                    nextOverrides[tName] = it + vAA1
                    linked.add(tName)
                }
            }
            linked.add("O")
            return ConstraintResult(nextOverrides, linked, "O, B', C' đi theo A' (giữ cạnh bên song song)")
        }

        if (sh in setOf(ShapeType.PYRAMID3, ShapeType.PYRAMID3_SA, ShapeType.PYRAMID4, ShapeType.PYRAMID4_SA, ShapeType.PYRAMID_TRAP) &&
            draggedName in listOf("A", "B", "C", "D")
        ) {
            val curO = registry["O"]
            val prevDragged = registry[draggedName]
            if (curO != null && prevDragged != null && hypot(curO.x - prevDragged.x, curO.z - prevDragged.z) < 0.05f) {
                val curS = registry["S"] ?: Vec3(0f, snap.h, 0f)
                nextOverrides["O"] = Vec3(newPos.x, curO.y, newPos.z)
                nextOverrides["S"] = Vec3(newPos.x, curS.y, newPos.z)
                linked.addAll(listOf("O", "S"))
                return ConstraintResult(nextOverrides, linked, "SO đi theo $draggedName (do S$draggedName ⊥ đáy)")
            }
        }

        return ConstraintResult(nextOverrides, linked, "")
    }

    // ================= NAM CHÂM HÚT THÔNG MINH =================
    data class SnapTargetResult(val worldPos: Vec3, val reason: String)

    fun snapToSpecialBaseTargets(
        rawWorldPos: Vec3,
        draggedName: String,
        registry: Map<String, Vec3>,
        snapEnabled: Boolean
    ): SnapTargetResult? {
        if (!snapEnabled) return null
        val baseNames = listOf("A", "B", "C", "D", "E", "F").filter { it != draggedName && registry.containsKey(it) }
        val snapDist = 0.35f

        for (bName in baseNames) {
            val bPos = registry[bName] ?: continue
            if (hypot(rawWorldPos.x - bPos.x, rawWorldPos.z - bPos.z) < snapDist) {
                val reason = if (draggedName == "S") "Hút: S thẳng đứng trên $bName (S$bName ⊥ đáy)"
                else "Hút: Trùng đỉnh $bName"
                return SnapTargetResult(Vec3(bPos.x, rawWorldPos.y, bPos.z), reason)
            }
        }

        if (hypot(rawWorldPos.x, rawWorldPos.z) < snapDist) {
            return SnapTargetResult(Vec3(0f, rawWorldPos.y, 0f), "Hút: Tâm đáy (Chóp đều)")
        }

        for (i in baseNames.indices) {
            val n1 = baseNames[i]
            val n2 = baseNames[(i + 1) % baseNames.size]
            val p1 = registry[n1] ?: continue
            val p2 = registry[n2] ?: continue
            val midX = (p1.x + p2.x) * 0.5f
            val midZ = (p1.z + p2.z) * 0.5f
            if (hypot(rawWorldPos.x - midX, rawWorldPos.z - midZ) < snapDist * 0.85f) {
                return SnapTargetResult(Vec3(midX, rawWorldPos.y, midZ), "Hút: Trung điểm $n1$n2 ((S$n1$n2) ⊥ đáy)")
            }
        }
        return null
    }

    data class MathSnapResult(val x: Float, val y: Float, val z: Float, val snapped: Boolean, val reason: String)

    fun snapMathCoordinates(mx: Float, my: Float, mz: Float, snapEnabled: Boolean): MathSnapResult {
        if (!snapEnabled) return MathSnapResult(mx, my, mz, false, "")
        val reasons = mutableListOf<String>()
        val axisThreshold = 0.24f
        val gridThreshold = 0.16f

        fun snapSingle(valIn: Float, axisLabel: String): Float {
            if (abs(valIn) < axisThreshold) {
                reasons.add("$axisLabel=0")
                return 0f
            }
            val nearestHalf = round(valIn * 2f) / 2f
            if (abs(valIn - nearestHalf) < gridThreshold) {
                reasons.add("$axisLabel=${formatMathNumber(nearestHalf)}")
                return nearestHalf
            }
            return valIn
        }

        val sx = snapSingle(mx, "x")
        val sy = snapSingle(my, "y")
        val sz = snapSingle(mz, "z")

        val special = when {
            sx == 0f && sy == 0f && sz == 0f -> "Hút: Gốc tọa độ (0;0;0)"
            sy == 0f && sz == 0f -> "Hút: Trục Ox"
            sx == 0f && sz == 0f -> "Hút: Trục Oy"
            sx == 0f && sy == 0f -> "Hút: Trục Oz"
            sz == 0f && reasons.contains("z=0") -> "Hút: Mặt đáy (Oxy)"
            reasons.isNotEmpty() -> "Hút lưới (${reasons.joinToString(", ")})"
            else -> ""
        }
        return MathSnapResult(sx, sy, sz, reasons.isNotEmpty(), special)
    }

    fun nextAutoPointName(registry: Map<String, Vec3>): String {
        for (ch in SUGGESTED_NAMES) {
            if (!registry.containsKey(ch)) return ch
        }
        var idx = 1
        while (registry.containsKey("M$idx")) idx++
        return "M$idx"
    }
}
