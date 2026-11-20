package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun lengthSq(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSq())

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    fun distanceTo(o: Vec3): Float = (this - o).length()
    fun distanceToSq(o: Vec3): Float = (this - o).lengthSq()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)

        fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 = Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        )

        fun closestPointOnSegment(p: Vec3, a: Vec3, b: Vec3): Vec3 {
            val ab = b - a
            val lenSq = ab.lengthSq()
            if (lenSq < 1e-6f) return a
            val t = ((p - a).dot(ab) / lenSq).coerceIn(0f, 1f)
            return a + ab * t
        }
    }
}

data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val ndcZ: Float,
    val viewDepth: Float,
    val visible: Boolean
)

data class Ray3D(val origin: Vec3, val direction: Vec3) {
    fun intersectPlane(planeNormal: Vec3, planePoint: Vec3): Vec3? {
        val denom = planeNormal.dot(direction)
        if (abs(denom) < 1e-5f) return null
        val t = (planePoint - origin).dot(planeNormal) / denom
        if (t < 0f) return null
        return origin + direction * t
    }
}

/**
 * Right-handed perspective camera matching Three.js PerspectiveCamera + OrbitControls
 * (Y-up in Three.js world space, where math Oxyz maps X->x, Y->z, Z->y).
 */
class CameraProjection(
    val cameraPos: Vec3,
    val target: Vec3,
    val width: Float,
    val height: Float,
    val fovDeg: Float = 45f,
    val near: Float = 0.1f,
    val far: Float = 1000f
) {
    val forward: Vec3
    val right: Vec3
    val up: Vec3
    private val tanHalfFov: Float
    private val aspect: Float

    init {
        val f = (target - cameraPos).normalized()
        forward = f
        val r = f.cross(Vec3.UP).let {
            if (it.lengthSq() < 1e-6f) Vec3(1f, 0f, 0f) else it.normalized()
        }
        right = r
        up = r.cross(f).normalized()
        tanHalfFov = tan((fovDeg * PI / 360.0)).toFloat()
        aspect = if (height > 1f) width / height else 1f
    }

    fun project(world: Vec3): ProjectedPoint {
        val rel = world - cameraPos
        val vz = rel.dot(forward) // positive in front of camera
        val vx = rel.dot(right)
        val vy = rel.dot(up)

        if (vz <= near) {
            return ProjectedPoint(-10000f, -10000f, 2f, vz, false)
        }

        val ndcX = vx / (vz * tanHalfFov * aspect)
        val ndcY = vy / (vz * tanHalfFov)
        val screenX = (ndcX * 0.5f + 0.5f) * width
        val screenY = (0.5f - ndcY * 0.5f) * height
        val ndcZ = (far * (vz - near)) / (vz * (far - near))
        return ProjectedPoint(screenX, screenY, ndcZ, vz, vz > near && vz < far)
    }

    fun screenToRay(screenX: Float, screenY: Float): Ray3D {
        val ndcX = (screenX / width) * 2f - 1f
        val ndcY = 1f - (screenY / height) * 2f
        val dir = (forward + right * (ndcX * tanHalfFov * aspect) + up * (ndcY * tanHalfFov)).normalized()
        return Ray3D(cameraPos, dir)
    }

    companion object {
        fun orbitCameraPos(target: Vec3, radius: Float, theta: Float, phi: Float): Vec3 {
            val clampedPhi = phi.coerceIn(0.08f, PI.toFloat() - 0.08f)
            val sinPhi = sin(clampedPhi)
            return Vec3(
                target.x + radius * sinPhi * sin(theta),
                target.y + radius * cos(clampedPhi),
                target.z + radius * sinPhi * cos(theta)
            )
        }
    }
}

fun formatMathNumber(value: Float): String {
    val clean = if (abs(value) < 1e-4f) 0f else value
    val rounded = kotlin.math.round(clean * 100f) / 100f
    return if (abs(rounded - rounded.toInt().toFloat()) < 1e-4f) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}
