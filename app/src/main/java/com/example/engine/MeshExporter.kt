package com.example.engine

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Exports the current 3D geometry scene (polyhedra, curved cylinder/cone/sphere meshes,
 * section planes, custom planes, and custom segments) to standard Wavefront .OBJ format.
 */
object MeshExporter {

    fun exportToObj(snapshot: GeometrySnapshot, title: String): String {
        // Always ensure surface faces are generated for 3D mesh export even if hidden in preview
        val exportScene = ShapeBuilder.build(snapshot.copy(faces = true))
        val m = exportScene.metrics

        val sb = StringBuilder(8192)
        sb.appendLine("# Hinh Hoc 3D - Wavefront OBJ Export")
        sb.appendLine("# Shape: ${title.ifBlank { m.title }} (${snapshot.shape.key})")
        sb.appendLine(
            String.format(
                Locale.US,
                "# Metrics: h=%.2f | Sday=%.2f | Sxq=%.2f | Stp=%.2f | V=%.2f",
                m.realH, m.sDay, m.sXq, m.sTp, m.vol
            )
        )
        sb.appendLine()

        val vertexIndices = LinkedHashMap<String, Int>()
        val vertices = mutableListOf<Vec3>()

        fun getOrAddVertex(v: Vec3): Int {
            val key = "${(v.x * 10000f).roundToInt()}_${(v.y * 10000f).roundToInt()}_${(v.z * 10000f).roundToInt()}"
            return vertexIndices.getOrPut(key) {
                vertices.add(v)
                vertices.size // 1-based index for Wavefront OBJ
            }
        }

        val solidFaces = mutableListOf<List<Int>>()
        val sectionFaces = mutableListOf<List<Int>>()
        val customPlaneFaces = mutableListOf<List<Int>>()

        exportScene.faces.forEach { face ->
            if (face.points.size < 3) return@forEach
            // Ensure consistent outward winding relative to face.normal
            val pts = ensureOutwardWinding(face.points, face.normal)
            val indices = pts.map { getOrAddVertex(it) }.distinctConsecutive()
            if (indices.size >= 3) {
                when {
                    face.isSection -> sectionFaces.add(indices)
                    face.isCustomPlane -> customPlaneFaces.add(indices)
                    else -> solidFaces.add(indices)
                }
            }
        }

        val customLinesIndices = mutableListOf<Pair<Int, Int>>()
        snapshot.customLines.forEach { ln ->
            val p1 = exportScene.pointRegistry[ln.p1]
            val p2 = exportScene.pointRegistry[ln.p2]
            if (p1 != null && p2 != null) {
                customLinesIndices.add(getOrAddVertex(p1) to getOrAddVertex(p2))
            }
        }

        // Write vertices
        vertices.forEach { v ->
            sb.appendLine(String.format(Locale.US, "v %.5f %.5f %.5f", v.x, v.y, v.z))
        }
        sb.appendLine()

        // Write main 3D solid group
        sb.appendLine("o ${sanitizeObjName(title.ifBlank { snapshot.shape.key })}")
        sb.appendLine("g SolidMesh")
        solidFaces.forEach { idxs ->
            sb.append("f ")
            sb.appendLine(idxs.joinToString(" "))
        }

        if (sectionFaces.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("g SectionPlane")
            sectionFaces.forEach { idxs ->
                sb.append("f ")
                sb.appendLine(idxs.joinToString(" "))
            }
        }

        if (customPlaneFaces.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("g CustomPlanes")
            customPlaneFaces.forEach { idxs ->
                sb.append("f ")
                sb.appendLine(idxs.joinToString(" "))
            }
        }

        if (customLinesIndices.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("g CustomSegments")
            customLinesIndices.forEach { (i1, i2) ->
                sb.appendLine("l $i1 $i2")
            }
        }

        return sb.toString()
    }

    private fun ensureOutwardWinding(pts: List<Vec3>, expectedNormal: Vec3): List<Vec3> {
        if (pts.size < 3) return pts
        val computed = (pts[1] - pts[0]).cross(pts[2] - pts[0])
        return if (computed.dot(expectedNormal) < 0f) pts.asReversed() else pts
    }

    private fun List<Int>.distinctConsecutive(): List<Int> {
        if (isEmpty()) return this
        val out = mutableListOf(first())
        for (i in 1 until size) {
            if (this[i] != out.last()) out.add(this[i])
        }
        if (out.size > 1 && out.first() == out.last()) out.removeAt(out.lastIndex)
        return out
    }

    private fun sanitizeObjName(raw: String): String {
        val clean = raw.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return clean.ifBlank { "Shape3D" }
    }
}
