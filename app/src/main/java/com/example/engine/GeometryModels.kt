package com.example.engine

import kotlin.math.atan2
import kotlin.math.acos

enum class ShapeCategory(val title: String, val badge: String) {
    PYRAMID("1. Nhóm Hình Chóp & Tứ Diện", "6 hình"),
    PRISM("2. Nhóm Hình Lăng Trụ", "3 hình"),
    BOX("3. Nhóm Hình Hộp & Đa Diện", "3 hình"),
    ROUND("4. Nhóm Khối Tròn Xoay", "3 hình")
}

enum class ShapeType(
    val key: String,
    val title: String,
    val subtitle: String,
    val category: ShapeCategory,
    val hasB: Boolean = false,
    val hasH: Boolean = true,
    val isRound: Boolean = false,
    val defaultOrigin: String = "O"
) {
    PYRAMID3("pyramid3", "Chóp tam giác đều", "S.ABC (SO ⊥ đáy)", ShapeCategory.PYRAMID),
    PYRAMID3_SA("pyramid3_sa", "Chóp SA ⊥ (ABC)", "Tam giác có SA ⊥ đáy", ShapeCategory.PYRAMID, defaultOrigin = "A"),
    TETRA_ORTHO("tetra_ortho", "Tứ diện vuông", "O.ABC (OA,OB,OC ⊥)", ShapeCategory.PYRAMID, hasB = true),
    PYRAMID4("pyramid4", "Chóp tứ giác đều", "S.ABCD (SO ⊥ đáy)", ShapeCategory.PYRAMID),
    PYRAMID4_SA("pyramid4_sa", "Chóp SA ⊥ (ABCD)", "Đáy vuông, SA ⊥ đáy", ShapeCategory.PYRAMID, defaultOrigin = "A"),
    PYRAMID_TRAP("pyramid_trap", "Chóp đáy hình thang", "Thang vuông tại A, B", ShapeCategory.PYRAMID, defaultOrigin = "A"),

    PRISM3("prism3", "Lăng trụ tam giác", "Đứng ABC.A'B'C'", ShapeCategory.PRISM),
    PRISM3_OBLIQUE("prism3_oblique", "Lăng trụ xiên", "A'O ⊥ (ABC)", ShapeCategory.PRISM),
    PRISM6("prism6", "Lăng trụ lục giác", "Lục giác đều 6 cạnh", ShapeCategory.PRISM),

    CUBE("cube", "Hình lập phương", "ABCD.A'B'C'D' (cạnh a)", ShapeCategory.BOX, hasH = false),
    BOX("box", "Hộp chữ nhật", "Kích thước a × b × h", ShapeCategory.BOX, hasB = true),
    OCTAHEDRON("octahedron", "Bát diện đều", "8 mặt tam giác đều", ShapeCategory.BOX, hasH = false),

    CYLINDER("cylinder", "Hình trụ tròn", "Trục OO', bán kính R", ShapeCategory.ROUND, isRound = true),
    CONE("cone", "Hình nón tròn", "Đỉnh S, đáy tâm O", ShapeCategory.ROUND, isRound = true),
    SPHERE("sphere", "Hình cầu", "Tâm O, bán kính R", ShapeCategory.ROUND, hasH = false, isRound = true);

    companion object {
        fun fromKey(key: String?): ShapeType = entries.firstOrNull { it.key == key } ?: PYRAMID3
    }
}

enum class LeftPane(val title: String) {
    SHAPES("Chọn Khối Hình Học"),
    DRAW("Kẻ Thêm Điểm / Đường / Mặt"),
    COORDS("Hệ Tọa Độ & Máy Tính Oxyz"),
    SETTINGS("Kích Thước & Cài Đặt Hiển Thị"),
    FORMULAS("Công Thức & Số Liệu Thực Tế")
}

enum class InteractionMode {
    VIEW,
    DRAG_POINT,
    ADD_POINT,
    DRAW_DASHED,
    DRAW_SOLID,
    DRAW_VECTOR
}

enum class DragConstraintMode {
    HORIZONTAL,
    VERTICAL,
    SEGMENT
}

enum class LineStyle(val key: String, val label: String) {
    DASHED("dashed", "Đoạn thẳng - Nét đứt"),
    SOLID("solid", "Đoạn thẳng - Nét liền"),
    VECTOR_SOLID("vector_solid", "Vectơ (Nét liền)"),
    VECTOR_DASHED("vector_dashed", "Vectơ (Nét đứt)");

    val isVector: Boolean get() = this == VECTOR_SOLID || this == VECTOR_DASHED
    val isDashed: Boolean get() = this == DASHED || this == VECTOR_DASHED

    companion object {
        fun fromKey(key: String?): LineStyle = entries.firstOrNull { it.key == key } ?: DASHED
    }
}

data class CustomEdgePoint(
    val id: String,
    val name: String,
    val from: String,
    val to: String,
    val ratio: Float
)

data class CustomXYZPoint(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float
)

data class CustomLine(
    val id: String,
    val p1: String,
    val p2: String,
    val style: LineStyle,
    val colorHex: String
)

data class CustomPlane(
    val id: String,
    val pts: List<String>
)

data class CameraOrbitState(
    val target: Vec3 = Vec3(0f, 1.8f, 0f),
    val radius: Float = 11.9f,
    val theta: Float = 0.72f, // azimuth around Y
    val phi: Float = 1.25f    // polar angle from +Y
) {
    fun cameraPosition(): Vec3 = CameraProjection.orbitCameraPos(target, radius, theta, phi)

    companion object {
        val DEFAULT = fromPositionAndTarget(Vec3(7.5f, 5.5f, 8.5f), Vec3(0f, 1.8f, 0f))

        fun fromPositionAndTarget(pos: Vec3, target: Vec3): CameraOrbitState {
            val rel = pos - target
            val r = rel.length().coerceIn(3f, 32f)
            val phi = acos((rel.y / r).coerceIn(-0.99f, 0.99f))
            val theta = atan2(rel.x, rel.z)
            return CameraOrbitState(target = target, radius = r, theta = theta, phi = phi)
        }
    }
}

data class RenderFace(
    val points: List<Vec3>,
    val normal: Vec3,
    val center: Vec3,
    val fillColorArgb: Int,
    val isSection: Boolean = false,
    val isCustomPlane: Boolean = false
)

data class SmartEdge(
    val p1: Vec3,
    val p2: Vec3,
    val normals: List<Vec3> = emptyList(),
    val centers: List<Vec3> = emptyList(),
    val forceStyle: EdgeRenderStyle = EdgeRenderStyle.SMART,
    val colorArgb: Int? = null
)

enum class EdgeRenderStyle {
    SMART,
    ALWAYS_SOLID,
    ALWAYS_DASHED
}

data class AltitudeVisual(
    val top: Vec3,
    val foot: Vec3,
    val ref: Vec3
)

data class SegmentRef(val n1: String, val n2: String)

data class GeometryMetrics(
    val title: String = "Chóp tam giác S.ABC",
    val realH: Float = 4.8f,
    val sDay: Float = 0f,
    val sXq: Float = 0f,
    val sTp: Float = 0f,
    val vol: Float = 0f,
    val dimensionRows: List<Pair<String, String>> = emptyList(),
    val formulaLines: List<String> = emptyList()
)

data class BuiltScene(
    val pointRegistry: LinkedHashMap<String, Vec3>,
    val customPointNames: Set<String>,
    val faces: List<RenderFace>,
    val edges: List<SmartEdge>,
    val altitude: AltitudeVisual?,
    val sectionBorder: List<Vec3>?,
    val customPlaneBorders: List<List<Vec3>>,
    val clickableSegments: List<SegmentRef>,
    val metrics: GeometryMetrics,
    val roundBodyHint: RoundBodyHint? = null
)

sealed class RoundBodyHint {
    data class Cylinder(val centerBottom: Vec3, val centerTop: Vec3, val radius: Float) : RoundBodyHint()
    data class Cone(val apex: Vec3, val centerBottom: Vec3, val radius: Float) : RoundBodyHint()
    data class Sphere(val center: Vec3, val radius: Float) : RoundBodyHint()
}

data class SavedSlot(
    val name: String,
    val savedAt: String,
    val shapeTitle: String,
    val snapshotJson: String
)

data class SnapVisualState(
    val pos: Vec3? = null,
    val label: String = ""
)

data class ConstraintRayVisual(
    val p1: Vec3,
    val p2: Vec3
)

data class HoverTooltip(
    val screenX: Float,
    val screenY: Float,
    val title: String,
    val subtitle: String = "",
    val snapBadge: String = "",
    val relationBadge: String = ""
)

data class GeometrySnapshot(
    val shape: ShapeType = ShapeType.PYRAMID3,
    val a: Float = 4.0f,
    val b: Float = 3.0f,
    val h: Float = 4.8f,
    val originPoint: String = "O",
    val showAxes: Boolean = false,
    val showCoordsOnLabels: Boolean = false,
    val showGrid: Boolean = true,
    val dashed: Boolean = true,
    val labels: Boolean = true,
    val altitude: Boolean = true,
    val faces: Boolean = true,
    val showSection: Boolean = false,
    val secRatio: Float = 0.5f,
    val keepRelations: Boolean = true,
    val snapEnabled: Boolean = true,
    val vertexOverrides: Map<String, Vec3> = emptyMap(),
    val customPoints: List<CustomEdgePoint> = emptyList(),
    val customXYZPoints: List<CustomXYZPoint> = emptyList(),
    val customLines: List<CustomLine> = emptyList(),
    val customPlanes: List<CustomPlane> = emptyList(),
    val camera: CameraOrbitState = CameraOrbitState.DEFAULT
)

data class GeometryUiState(
    val snapshot: GeometrySnapshot = GeometrySnapshot(),
    val scene: BuiltScene = ShapeBuilder.build(GeometrySnapshot()),
    val interactionMode: InteractionMode = InteractionMode.VIEW,
    val dragConstraint: DragConstraintMode = DragConstraintMode.HORIZONTAL,
    val selectedFirstPoint: String? = null,
    val draggingPointName: String? = null,
    val linkedPartners: Set<String> = emptySet(),
    val autoRotate: Boolean = false,
    val activeLeftPane: LeftPane? = null,
    val bottomDockCollapsed: Boolean = false,
    val dragSubdrawerOpen: Boolean = false,
    val openCategories: Set<ShapeCategory> = setOf(ShapeCategory.PYRAMID),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val previewDot: Vec3? = null,
    val snapVisual: SnapVisualState = SnapVisualState(),
    val constraintRay: ConstraintRayVisual? = null,
    val tooltip: HoverTooltip? = null,
    val toastMessage: String? = null,
    val saveModalOpen: Boolean = false,
    val customFileName: String = "Chop-SABC-Oxyz",
    val savedSlots: List<SavedSlot> = emptyList(),
    val calcPointA: String = "S",
    val calcPointB: String = "A"
)
