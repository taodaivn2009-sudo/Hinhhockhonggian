package com.example.geo2d

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

enum class MathWorkspace {
    SPACE_3D,
    PLANE_2D
}

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun length(): Float = hypot(x, y)
    fun distanceTo(o: Vec2): Float = hypot(x - o.x, y - o.y)
    fun dot(o: Vec2): Float = x * o.x + y * o.y
    fun cross(o: Vec2): Float = x * o.y - y * o.x
}

enum class Geo2DTool(val shortLabel: String, val hint: String) {
    PAN_SELECT("Di chuyển", "Kéo nền để trượt Oxy • Chụm 2 ngón thu phóng • Kéo trực tiếp điểm để dời"),
    TRACE_GRAPH("Dò đồ thị", "Chạm & kéo ngang trên màn hình để dò tọa độ (x; f(x)) và tiếp tuyến"),
    ADD_POINT("Điểm", "Chạm vào mặt phẳng Oxy để đặt điểm mới"),
    SEGMENT("Đoạn thẳng", "Chọn 2 điểm (hoặc chạm vị trí mới) để vẽ đoạn thẳng"),
    LINE("Đường thẳng", "Chọn 2 điểm để vẽ đường thẳng vô hạn và xem phương trình"),
    RAY("Tia", "Chọn điểm gốc rồi chọn điểm định hướng để vẽ tia"),
    VECTOR("Vectơ", "Chọn điểm đầu và điểm cuối để vẽ vectơ"),
    CIRCLE("Đường tròn", "Chọn tâm đường tròn rồi chọn 1 điểm trên đường tròn"),
    POLYGON("Đa giác", "Chạm lần lượt các đỉnh, chạm lại đỉnh đầu tiên để khép kín đa giác"),
    MEASURE_ANGLE("Đo góc", "Chọn lần lượt 3 điểm A → B (đỉnh góc) → C để đo góc ∠ABC")
}

enum class Geo2DTab(val title: String, val shortLabel: String) {
    FUNCTIONS("Đồ Thị Hàm Số y = f(x)", "Hàm số"),
    PRESETS("Hình Mẫu & Nhập Tọa Độ", "Hình mẫu"),
    OBJECTS("Đối Tượng & Đo Đạc", "Đo đạc"),
    SETTINGS("Cài Đặt Mặt Phẳng Oxy", "Cài đặt")
}

enum class Geo2DPreset(val title: String, val subtitle: String) {
    EMPTY_OXY("Hệ trục Oxy trống", "Làm mới để vẽ đồ thị & hình tự do"),
    EQUILATERAL_TRIANGLE("Tam giác đều ABC", "3 cạnh bằng nhau, các góc 60°"),
    RIGHT_TRIANGLE("Tam giác vuông ABC", "Vuông tại A, kèm đo góc 90°"),
    SQUARE("Hình vuông ABCD", "4 cạnh bằng nhau, diện tích & chu vi"),
    RECTANGLE("Hình chữ nhật ABCD", "Chiều dài 6, chiều rộng 3"),
    PARALLELOGRAM("Hình bình hành ABCD", "Cặp cạnh đối song song bằng nhau"),
    RHOMBUS("Hình thoi ABCD", "2 đường chéo vuông góc tại O"),
    ISOSCELES_TRAPEZOID("Hình thang cân ABCD", "2 đáy song song, 2 cạnh bên bằng nhau"),
    CIRCLE_TANGENT("Đường tròn tâm I", "Tâm I(0; 0), bán kính R = 3")
}

data class Geo2DPoint(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val colorHex: String = "#38BDF8"
)

sealed class Geo2DObject {
    abstract val id: String
    abstract val colorHex: String

    data class Segment(
        override val id: String,
        val p1Id: String,
        val p2Id: String,
        val dashed: Boolean = false,
        override val colorHex: String = "#F8FAFC"
    ) : Geo2DObject()

    data class Line(
        override val id: String,
        val p1Id: String,
        val p2Id: String,
        val dashed: Boolean = false,
        override val colorHex: String = "#38BDF8"
    ) : Geo2DObject()

    data class Ray(
        override val id: String,
        val startId: String,
        val throughId: String,
        override val colorHex: String = "#A78BFA"
    ) : Geo2DObject()

    data class VectorObj(
        override val id: String,
        val fromId: String,
        val toId: String,
        override val colorHex: String = "#F43F5E"
    ) : Geo2DObject()

    data class Circle(
        override val id: String,
        val centerId: String,
        val edgePointId: String? = null,
        val fixedRadius: Float? = null,
        override val colorHex: String = "#10B981"
    ) : Geo2DObject()

    data class Polygon(
        override val id: String,
        val pointIds: List<String>,
        override val colorHex: String = "#6366F1"
    ) : Geo2DObject()

    data class AngleMeasure(
        override val id: String,
        val pAId: String,
        val vertexId: String,
        val pCId: String,
        override val colorHex: String = "#FBBF24"
    ) : Geo2DObject()
}

data class GraphFunction(
    val id: String,
    val label: String,
    val expression: String,
    val colorHex: String,
    val visible: Boolean = true,
    val showDerivative: Boolean = false
)

enum class FunctionCategory(val title: String) {
    EXP_LOG("Mũ & Loga"),
    POLY("Đa thức"),
    RATIONAL_RADICAL("Phân thức & Căn"),
    TRIG("Lượng giác"),
    SPECIAL("Đặc biệt")
}

data class FunctionTemplate(
    val expr: String,
    val note: String,
    val category: FunctionCategory
)

object FunctionLibrary {
    val templates: List<FunctionTemplate> = listOf(
        // Mũ & Logarit
        FunctionTemplate("e^x", "Hàm mũ tự nhiên", FunctionCategory.EXP_LOG),
        FunctionTemplate("2^x", "Hàm mũ cơ số 2", FunctionCategory.EXP_LOG),
        FunctionTemplate("(1/2)^x", "Mũ nghịch biến", FunctionCategory.EXP_LOG),
        FunctionTemplate("ln(x)", "Logarit tự nhiên", FunctionCategory.EXP_LOG),
        FunctionTemplate("log(x)", "Logarit thập phân", FunctionCategory.EXP_LOG),
        FunctionTemplate("log2(x)", "Logarit cơ số 2", FunctionCategory.EXP_LOG),
        FunctionTemplate("log(3; x)", "Logarit cơ số 3", FunctionCategory.EXP_LOG),
        FunctionTemplate("x*e^(-x)", "Hàm mũ tắt dần", FunctionCategory.EXP_LOG),

        // Đa thức
        FunctionTemplate("x^2 - 2x - 1", "Parabol bậc 2", FunctionCategory.POLY),
        FunctionTemplate("x^3 - 3x", "Hàm bậc 3 có 2 cực trị", FunctionCategory.POLY),
        FunctionTemplate("x^4 - 4x^2 + 1", "Hàm trùng phương", FunctionCategory.POLY),
        FunctionTemplate("2x + 1", "Đường thẳng bậc 1", FunctionCategory.POLY),

        // Phân thức & Căn thức
        FunctionTemplate("1/x", "Hypebol cơ bản", FunctionCategory.RATIONAL_RADICAL),
        FunctionTemplate("(2x - 1)/(x - 1)", "Hàm nhất biến", FunctionCategory.RATIONAL_RADICAL),
        FunctionTemplate("(x^2 + 1)/x", "Tiệm cận xiên", FunctionCategory.RATIONAL_RADICAL),
        FunctionTemplate("sqrt(x + 2)", "Căn bậc 2", FunctionCategory.RATIONAL_RADICAL),
        FunctionTemplate("cbrt(x)", "Căn bậc 3", FunctionCategory.RATIONAL_RADICAL),

        // Lượng giác
        FunctionTemplate("sin(x)", "Sóng sin cơ bản", FunctionCategory.TRIG),
        FunctionTemplate("cos(x)", "Sóng cos cơ bản", FunctionCategory.TRIG),
        FunctionTemplate("tan(x)", "Hàm tang tuần hoàn", FunctionCategory.TRIG),
        FunctionTemplate("sin(2x) + cos(x)", "Tổng hợp điều hòa", FunctionCategory.TRIG),

        // Trị tuyệt đối & Đặc biệt
        FunctionTemplate("|x - 1| - 2", "Trị tuyệt đối chữ V", FunctionCategory.SPECIAL),
        FunctionTemplate("|x^2 - 4|", "Trị tuyệt đối parabol", FunctionCategory.SPECIAL),
        FunctionTemplate("e^(-x^2)", "Đường cong Gauss", FunctionCategory.SPECIAL),
        FunctionTemplate("sinh(x)", "Sin hyperbolic", FunctionCategory.SPECIAL)
    )

    fun byCategory(cat: FunctionCategory): List<FunctionTemplate> = templates.filter { it.category == cat }
}

enum class KeyPointType(val vnLabel: String) {
    ROOT("Giao Ox"),
    Y_INTERCEPT("Giao Oy"),
    LOCAL_MAX("Cực đại"),
    LOCAL_MIN("Cực tiểu"),
    INTERSECTION("Giao điểm")
}

data class GraphKeyPoint(
    val x: Double,
    val y: Double,
    val type: KeyPointType,
    val colorHex: String
)

data class TraceResult(
    val functionId: String,
    val functionLabel: String,
    val expression: String,
    val colorHex: String,
    val x: Double,
    val y: Double,
    val slope: Double
)

data class Geo2DCamera(
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val pixelsPerUnit: Double = 72.0
)

data class Geo2DSensitivity(
    val pan: Float = 1.0f,
    val zoom: Float = 1.0f,
    val touchRadius: Float = 1.0f
)

data class Geo2DSnapshot(
    val points: List<Geo2DPoint> = emptyList(),
    val objects: List<Geo2DObject> = emptyList(),
    val functions: List<GraphFunction> = listOf(
        GraphFunction("fn_1", "f₁(x)", "x^2 - 2x - 1", "#38BDF8", true),
        GraphFunction("fn_2", "f₂(x)", "sin(x) + 1", "#10B981", true)
    ),
    val showAxes: Boolean = true,
    val showGrid: Boolean = true,
    val snapToGrid: Boolean = true,
    val showPointCoords: Boolean = true,
    val showMeasurements: Boolean = true,
    val showKeyGraphPoints: Boolean = true,
    val showTangentOnTrace: Boolean = true,
    val sensitivity: Geo2DSensitivity = Geo2DSensitivity(),
    val camera: Geo2DCamera = Geo2DCamera()
)

data class Geo2DUiState(
    val snapshot: Geo2DSnapshot = Geo2DSnapshot(),
    val activeTool: Geo2DTool = Geo2DTool.PAN_SELECT,
    val activeTab: Geo2DTab? = null,
    val pendingPointIds: List<String> = emptyList(),
    val draggingPointId: String? = null,
    val traceX: Double? = null,
    val traceResults: List<TraceResult> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val toastMessage: String? = null,
    val saveModalOpen: Boolean = false,
    val customFileName: String = "DoThi-Hinh2D-Oxy",
    val exportFolderUri: String? = null,
    val exportFolderLabel: String? = null
)

object Geo2DMathHelpers {
    fun formatNum(v: Double): String {
        val clean = if (kotlin.math.abs(v) < 1e-9) 0.0 else v
        val absV = kotlin.math.abs(clean)
        if (absV >= 1e6 || (absV > 0.0 && absV < 1e-3)) {
            return String.format(java.util.Locale.US, "%.2e", clean)
                .replace("e+0", "e")
                .replace("e+", "e")
                .replace("e-0", "e-")
        }
        val rounded = kotlin.math.round(clean * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    fun formatAxisTick(v: Double): String {
        val absV = kotlin.math.abs(v)
        if (absV < 1e-12) return "0"
        return when {
            absV >= 1e6 || absV < 1e-3 -> String.format(java.util.Locale.US, "%.1e", v)
                .replace(".0e", "e")
                .replace("e+0", "e")
                .replace("e+", "e")
                .replace("e-0", "e-")
            absV >= 10000.0 -> {
                val k = v / 1000.0
                val rounded = kotlin.math.round(k * 10.0) / 10.0
                if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}k" else "${rounded}k"
            }
            absV < 0.1 -> String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
            else -> formatNum(v)
        }
    }

    fun formatNum(v: Float): String = formatNum(v.toDouble())

    fun polygonArea(pts: List<Vec2>): Float {
        if (pts.size < 3) return 0f
        var sum = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += (a.x * b.y - b.x * a.y)
        }
        return kotlin.math.abs(sum) * 0.5f
    }

    fun polygonPerimeter(pts: List<Vec2>): Float {
        if (pts.size < 2) return 0f
        var p = 0f
        for (i in pts.indices) {
            p += pts[i].distanceTo(pts[(i + 1) % pts.size])
        }
        return p
    }

    fun angleDegrees(a: Vec2, vertex: Vec2, c: Vec2): Float {
        val v1 = a - vertex
        val v2 = c - vertex
        val len = v1.length() * v2.length()
        if (len < 1e-5f) return 0f
        val cosA = (v1.dot(v2) / len).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosA.toDouble())).toFloat()
    }

    fun lineEquationString(p1: Vec2, p2: Vec2): String {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        if (hypot(dx, dy) < 1e-5f) return "—"
        if (kotlin.math.abs(dx) < 1e-4f) return "x = ${formatNum(p1.x)}"
        val m = dy / dx
        val b = p1.y - m * p1.x
        val mStr = when {
            kotlin.math.abs(m) < 1e-4f -> ""
            kotlin.math.abs(m - 1f) < 1e-4f -> "x"
            kotlin.math.abs(m + 1f) < 1e-4f -> "-x"
            else -> "${formatNum(m)}x"
        }
        val bStr = when {
            kotlin.math.abs(b) < 1e-4f -> if (mStr.isEmpty()) "0" else ""
            b > 0f -> if (mStr.isEmpty()) formatNum(b) else " + ${formatNum(b)}"
            else -> if (mStr.isEmpty()) formatNum(b) else " - ${formatNum(-b)}"
        }
        return "y = $mStr$bStr"
    }

    fun circleEquationString(center: Vec2, radius: Float): String {
        fun term(v: Float, axis: String): String = when {
            kotlin.math.abs(v) < 1e-4f -> "$axis²"
            v > 0f -> "($axis - ${formatNum(v)})²"
            else -> "($axis + ${formatNum(-v)})²"
        }
        return "${term(center.x, "x")} + ${term(center.y, "y")} = ${formatNum(radius * radius)}"
    }

    fun angleRad(from: Vec2, to: Vec2): Double = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
}
