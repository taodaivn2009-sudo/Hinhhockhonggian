package com.example.ai

import android.content.Context
import com.example.docs.PDF_TOTAL_PAGES
import com.example.engine.*
import com.example.geo2d.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

enum class AiModelPreset(
    val modelId: String,
    val title: String,
    val subtitle: String
) {
    GEMINI_3_8_FLASH(
        modelId = "gemini-3.8-flash",
        title = "Gemini 3.8 Flash",
        subtitle = "Mặc định · Nhanh & chính xác cho ảnh đề bài + tài liệu"
    ),
    GEMINI_3_5_FLASH_LITE(
        modelId = "gemini-3.5-flash-lite",
        title = "Gemini 3.5 Flash-Lite",
        subtitle = "Siêu nhẹ · Tra cứu nhanh mục lục & công thức"
    ),
    GEMINI_4_ARGON(
        modelId = "gemini-4-argon",
        title = "Gemini 4 Argon",
        subtitle = "Chuyên sâu · Lập luận toán học & dựng hình phức tạp"
    );

    companion object {
        fun fromId(id: String?): AiModelPreset? = entries.firstOrNull { it.modelId.equals(id?.trim(), ignoreCase = true) }
    }
}

enum class AiSubTab(val key: String, val label: String) {
    DOCS_QA("docs_qa", "Hỏi tài liệu"),
    PROBLEM_DRAW("problem_draw", "Chụp đề vẽ hình"),
    API_KEY("api_key", "Tài khoản / Key")
}

data class AiKeyConfig(
    val apiKey: String = "",
    val modelId: String = AiModelPreset.GEMINI_3_8_FLASH.modelId
) {
    val trimmedKey: String get() = apiKey.trim()
    val hasKey: Boolean get() = trimmedKey.isNotEmpty()
    val effectiveModelId: String get() = modelId.trim().ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId }

    val maskedKey: String
        get() {
            val k = trimmedKey
            return when {
                k.isEmpty() -> "Chưa nhập API Key"
                k.length <= 8 -> "•".repeat(k.length)
                else -> "${k.take(4)}${"•".repeat(6)}${k.takeLast(4)}"
            }
        }
}

class AiKeyPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tool_toan_ai_config_v1", Context.MODE_PRIVATE)

    fun load(): AiKeyConfig = AiKeyConfig(
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        modelId = prefs.getString(KEY_MODEL_ID, AiModelPreset.GEMINI_3_8_FLASH.modelId)
            ?: AiModelPreset.GEMINI_3_8_FLASH.modelId
    )

    fun save(config: AiKeyConfig) {
        prefs.edit()
            .putString(KEY_API_KEY, config.trimmedKey)
            .putString(KEY_MODEL_ID, config.effectiveModelId)
            .apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val KEY_API_KEY = "gemini_user_api_key"
        private const val KEY_MODEL_ID = "gemini_model_id"
    }
}

data class AiPdfPageSuggestion(
    val page: Int,
    val title: String,
    val reason: String
)

data class AiDocsReply(
    val answer: String,
    val suggestedPages: List<AiPdfPageSuggestion> = emptyList()
)

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val suggestedPages: List<AiPdfPageSuggestion> = emptyList(),
    val attachedPdfPage: Int? = null,
    val isError: Boolean = false
)

enum class AiDrawTarget {
    SPACE_3D,
    PLANE_2D
}

data class Ai3DEdgePointSpec(
    val name: String,
    val from: String,
    val to: String,
    val ratio: Float
)

data class Ai3DXyzPointSpec(
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float
)

data class Ai3DLineSpec(
    val p1: String,
    val p2: String,
    val style: LineStyle = LineStyle.DASHED,
    val colorHex: String = "",
    val highlighted: Boolean = false
)

data class Ai3DSceneSpec(
    val shapeKey: String,
    val a: Float = 4f,
    val b: Float = 3f,
    val h: Float = 4.8f,
    val showAxes: Boolean = false,
    val showAltitude: Boolean = false,
    val originPoint: String? = null,
    val edgePoints: List<Ai3DEdgePointSpec> = emptyList(),
    val xyzPoints: List<Ai3DXyzPointSpec> = emptyList(),
    val lines: List<Ai3DLineSpec> = emptyList(),
    val planes: List<List<String>> = emptyList(),
    val dynamicSection: Triple<String, String, String>? = null
)

data class Ai2DPointSpec(
    val name: String,
    val x: Float,
    val y: Float,
    val colorHex: String = ""
)

data class Ai2DObjectSpec(
    val kind: String, // segment, line, ray, vector, circle, polygon, angle
    val p1: String? = null,
    val p2: String? = null,
    val p3: String? = null,
    val radius: Float? = null,
    val pointNames: List<String> = emptyList(),
    val dashed: Boolean = false,
    val colorHex: String = "",
    val highlighted: Boolean = false
)

data class Ai2DFunctionSpec(
    val expression: String,
    val showDerivative: Boolean = false,
    val colorHex: String = "#38BDF8"
)

data class Ai2DSceneSpec(
    val points: List<Ai2DPointSpec> = emptyList(),
    val objects: List<Ai2DObjectSpec> = emptyList(),
    val functions: List<Ai2DFunctionSpec> = emptyList(),
    val paramM: Double = 1.0
)

data class AiDrawingPlan(
    val target: AiDrawTarget,
    val title: String,
    val problemText: String,
    val drawingSummary: String,
    val solutionHint: String,
    val scene3D: Ai3DSceneSpec? = null,
    val scene2D: Ai2DSceneSpec? = null,
    val relatedPages: List<AiPdfPageSuggestion> = emptyList()
)

/**
 * Robust JSON parsing & scene conversion between Gemini structured output and the app's real
 * 3D ([GeometrySnapshot]) and 2D ([Geo2DSnapshot]) engines.
 */
object AiResponseParser {

    private val defaultColors = listOf("#F43F5E", "#38BDF8", "#10B981", "#FBBF24", "#A78BFA", "#FB923C")

    fun extractJsonObject(rawText: String): JSONObject? {
        val trimmed = rawText.trim()
        val unwrapped = trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        runCatching { return JSONObject(unwrapped) }
        val start = unwrapped.indexOf('{')
        val end = unwrapped.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { return JSONObject(unwrapped.substring(start, end + 1)) }
        }
        return null
    }

    fun parseDocsReply(rawText: String): AiDocsReply {
        val obj = extractJsonObject(rawText) ?: return AiDocsReply(answer = rawText.trim())
        val answer = obj.optString("answer").trim().ifEmpty { rawText.trim() }
        val pages = parsePageSuggestions(obj.optJSONArray("suggestedPages"))
        return AiDocsReply(answer = answer, suggestedPages = pages)
    }

    fun parseDrawingPlan(rawText: String): AiDrawingPlan? {
        val obj = extractJsonObject(rawText) ?: return null
        val targetStr = obj.optString("target", "3D").trim().uppercase()
        val target = if (targetStr.contains("2D")) AiDrawTarget.PLANE_2D else AiDrawTarget.SPACE_3D
        val title = obj.optString("title").trim().ifEmpty {
            if (target == AiDrawTarget.SPACE_3D) "Hình không gian 3D từ đề bài" else "Hình phẳng & Đồ thị 2D từ đề bài"
        }
        val problemText = obj.optString("problemText").trim()
        val drawingSummary = obj.optString("drawingSummary").trim()
        val solutionHint = obj.optString("solutionHint").trim()
        val relatedPages = parsePageSuggestions(obj.optJSONArray("relatedPages"))

        val scene3D = if (target == AiDrawTarget.SPACE_3D) {
            obj.optJSONObject("scene3D")?.let { parse3DSpec(it) }
        } else null

        val scene2D = if (target == AiDrawTarget.PLANE_2D) {
            obj.optJSONObject("scene2D")?.let { parse2DSpec(it) }
        } else null

        if (target == AiDrawTarget.SPACE_3D && scene3D == null) return null
        if (target == AiDrawTarget.PLANE_2D && scene2D == null) return null

        return AiDrawingPlan(
            target = target,
            title = title,
            problemText = problemText,
            drawingSummary = drawingSummary,
            solutionHint = solutionHint,
            scene3D = scene3D,
            scene2D = scene2D,
            relatedPages = relatedPages
        )
    }

    fun buildGeometrySnapshot(spec: Ai3DSceneSpec): GeometrySnapshot {
        val shape = ShapeType.fromKey(spec.shapeKey)
        val safeA = spec.a.takeIf { it.isFinite() }?.coerceIn(1.5f, 9f) ?: 4f
        val safeB = spec.b.takeIf { it.isFinite() }?.coerceIn(1.5f, 9f) ?: 3f
        val safeH = spec.h.takeIf { it.isFinite() }?.coerceIn(1.5f, 9f) ?: 4.8f

        val edgePoints = spec.edgePoints.mapIndexedNotNull { idx, ep ->
            val name = ep.name.trim().take(4)
            val from = ep.from.trim()
            val to = ep.to.trim()
            if (name.isEmpty() || from.isEmpty() || to.isEmpty()) null
            else CustomEdgePoint(
                id = "ai_ep_$idx",
                name = name,
                from = from,
                to = to,
                ratio = ep.ratio.takeIf { it.isFinite() }?.coerceIn(0.05f, 0.95f) ?: 0.5f
            )
        }

        val xyzPoints = spec.xyzPoints.mapIndexedNotNull { idx, pt ->
            val name = pt.name.trim().take(4)
            if (name.isEmpty() || !pt.x.isFinite() || !pt.y.isFinite() || !pt.z.isFinite()) null
            else CustomXYZPoint(
                id = "ai_xyz_$idx",
                name = name,
                x = pt.x.coerceIn(-12f, 12f),
                y = pt.y.coerceIn(-12f, 12f),
                z = pt.z.coerceIn(-12f, 12f)
            )
        }

        var highlightIdx3D = 0
        val lines = spec.lines.mapIndexedNotNull { idx, ln ->
            val p1 = ln.p1.trim()
            val p2 = ln.p2.trim()
            if (p1.isEmpty() || p2.isEmpty() || p1 == p2) null
            else {
                val resolvedColor = if (ln.highlighted) {
                    sanitizeHex(ln.colorHex, defaultColors[highlightIdx3D++ % defaultColors.size])
                } else {
                    ""
                }
                CustomLine(
                    id = "ai_ln_$idx",
                    p1 = p1,
                    p2 = p2,
                    style = ln.style,
                    colorHex = resolvedColor
                )
            }
        }

        val planes = spec.planes.mapIndexedNotNull { idx, pts ->
            val clean = pts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.size < 3) null else CustomPlane(id = "ai_pl_$idx", pts = clean)
        }

        val dynSection = spec.dynamicSection?.let { (p1, p2, p3) ->
            val a1 = p1.trim(); val a2 = p2.trim(); val a3 = p3.trim()
            if (a1.isNotEmpty() && a2.isNotEmpty() && a3.isNotEmpty() && setOf(a1, a2, a3).size == 3) {
                DynamicSection3Points(a1, a2, a3)
            } else null
        }

        return GeometrySnapshot(
            shape = shape,
            a = safeA,
            b = safeB,
            h = safeH,
            originPoint = spec.originPoint?.trim()?.ifEmpty { null } ?: shape.defaultOrigin,
            showAxes = spec.showAxes || xyzPoints.isNotEmpty(),
            showCoordsOnLabels = spec.showAxes || xyzPoints.isNotEmpty(),
            showGrid = true,
            dashed = true,
            labels = true,
            altitude = spec.showAltitude,
            faces = true,
            customPoints = edgePoints,
            customXYZPoints = xyzPoints,
            customLines = lines,
            customPlanes = planes,
            dynamicSection = dynSection,
            camera = CameraOrbitState.DEFAULT
        )
    }

    fun buildGeo2DSnapshot(spec: Ai2DSceneSpec): Geo2DSnapshot {
        val points = mutableListOf<Geo2DPoint>()
        val nameToId = LinkedHashMap<String, String>()

        spec.points.forEachIndexed { idx, pt ->
            val cleanName = pt.name.trim().take(6).ifEmpty { "P${idx + 1}" }
            if (pt.x.isFinite() && pt.y.isFinite() && cleanName !in nameToId) {
                val id = "ai_pt_$idx"
                nameToId[cleanName] = id
                points.add(
                    Geo2DPoint(
                        id = id,
                        name = cleanName,
                        x = pt.x.coerceIn(-50f, 50f),
                        y = pt.y.coerceIn(-50f, 50f),
                        colorHex = sanitizeHex(pt.colorHex, "#94A3B8")
                    )
                )
            }
        }

        val objects = mutableListOf<Geo2DObject>()
        var highlightIdx2D = 0
        spec.objects.forEachIndexed { idx, raw ->
            val kind = raw.kind.trim().lowercase()
            val color = if (raw.highlighted || kind == "angle") {
                sanitizeHex(raw.colorHex, defaultColors[highlightIdx2D++ % defaultColors.size])
            } else {
                ""
            }
            val id = "ai_obj_$idx"
            when (kind) {
                "segment" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.Segment(id, i1, i2, dashed = raw.dashed, colorHex = color))
                    }
                }
                "line" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.Line(id, i1, i2, dashed = raw.dashed, colorHex = color))
                    }
                }
                "ray" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.Ray(id, i1, i2, colorHex = color))
                    }
                }
                "vector" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.VectorObj(id, i1, i2, colorHex = color, dashed = raw.dashed))
                    }
                }
                "circle" -> {
                    val cId = nameToId[raw.p1?.trim()]
                    val eId = nameToId[raw.p2?.trim()]
                    val rad = raw.radius?.takeIf { it.isFinite() && it > 0.05f }?.coerceAtMost(40f)
                    if (cId != null && (eId != null || rad != null)) {
                        objects.add(Geo2DObject.Circle(id, cId, edgePointId = eId, fixedRadius = if (eId == null) rad else null, colorHex = color))
                    }
                }
                "polygon" -> {
                    val ids = raw.pointNames.mapNotNull { nameToId[it.trim()] }.distinct()
                    if (ids.size >= 3) {
                        objects.add(Geo2DObject.Polygon(id, ids, colorHex = color))
                    }
                }
                "angle" -> {
                    val aId = nameToId[raw.p1?.trim()]
                    val vId = nameToId[raw.p2?.trim()]
                    val cId = nameToId[raw.p3?.trim()]
                    if (aId != null && vId != null && cId != null && setOf(aId, vId, cId).size == 3) {
                        objects.add(Geo2DObject.AngleMeasure(id, aId, vId, cId, colorHex = color))
                    }
                }
            }
        }

        val functions = spec.functions.mapIndexedNotNull { idx, fn ->
            val cleanExpr = fn.expression.trim()
                .removePrefix("y=").removePrefix("y =")
                .removePrefix("f(x)=").removePrefix("f(x) =")
                .trim()
            if (cleanExpr.isEmpty()) return@mapIndexedNotNull null
            val parsed = MathExprParser.parse(cleanExpr)
            if (parsed !is MathExprParser.ParseResult.Ok) return@mapIndexedNotNull null
            GraphFunction(
                id = "ai_fn_$idx",
                label = "f${idx + 1}(x)",
                expression = cleanExpr,
                colorHex = sanitizeHex(fn.colorHex, defaultColors[idx % defaultColors.size]),
                visible = true,
                showDerivative = fn.showDerivative
            )
        }

        // Compute balanced camera framing around any drawn 2D points
        val camera = if (points.isNotEmpty()) {
            val minX = points.minOf { it.x }.toDouble()
            val maxX = points.maxOf { it.x }.toDouble()
            val minY = points.minOf { it.y }.toDouble()
            val maxY = points.maxOf { it.y }.toDouble()
            val cx = (minX + maxX) / 2.0
            val cy = (minY + maxY) / 2.0
            val span = max(abs(maxX - minX), abs(maxY - minY)).coerceAtLeast(4.0)
            val ppu = (420.0 / (span * 1.45)).coerceIn(28.0, 110.0)
            Geo2DCamera(centerX = cx, centerY = cy, pixelsPerUnit = ppu)
        } else {
            Geo2DCamera(centerX = 0.0, centerY = 0.0, pixelsPerUnit = 68.0)
        }

        return Geo2DSnapshot(
            points = points,
            objects = objects,
            functions = functions,
            camera = camera,
            paramM = spec.paramM.takeIf { it.isFinite() }?.coerceIn(-10.0, 10.0) ?: 1.0,
            showAxes = true,
            showGrid = true,
            showPointCoords = points.isNotEmpty(),
            showMeasurements = points.isNotEmpty(),
            showKeyGraphPoints = functions.isNotEmpty(),
            showAsymptotes = functions.isNotEmpty()
        )
    }

    private fun parsePageSuggestions(arr: JSONArray?): List<AiPdfPageSuggestion> {
        if (arr == null) return emptyList()
        val out = mutableListOf<AiPdfPageSuggestion>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val page = item.optInt("page", -1)
            if (page in 1..PDF_TOTAL_PAGES) {
                val title = item.optString("title").trim().ifEmpty { "Trang $page" }
                val reason = item.optString("reason").trim()
                if (out.none { it.page == page }) {
                    out.add(AiPdfPageSuggestion(page = page, title = title, reason = reason))
                }
            }
        }
        return out
    }

    private fun parse3DSpec(o: JSONObject): Ai3DSceneSpec {
        val epArr = o.optJSONArray("edgePoints")
        val edgePoints = buildList {
            if (epArr != null) {
                for (i in 0 until epArr.length()) {
                    val e = epArr.optJSONObject(i) ?: continue
                    add(
                        Ai3DEdgePointSpec(
                            name = e.optString("name"),
                            from = e.optString("from"),
                            to = e.optString("to"),
                            ratio = e.optDouble("ratio", 0.5).toFloat()
                        )
                    )
                }
            }
        }

        val xyzArr = o.optJSONArray("xyzPoints")
        val xyzPoints = buildList {
            if (xyzArr != null) {
                for (i in 0 until xyzArr.length()) {
                    val p = xyzArr.optJSONObject(i) ?: continue
                    add(
                        Ai3DXyzPointSpec(
                            name = p.optString("name"),
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            z = p.optDouble("z", 0.0).toFloat()
                        )
                    )
                }
            }
        }

        val lnArr = o.optJSONArray("lines")
        val lines = buildList {
            if (lnArr != null) {
                for (i in 0 until lnArr.length()) {
                    val l = lnArr.optJSONObject(i) ?: continue
                    val rawColor = l.optString("colorHex", "").trim()
                    val highlighted = if (l.has("highlighted") && !l.isNull("highlighted")) {
                        l.optBoolean("highlighted", false)
                    } else {
                        isValidHex(rawColor)
                    }
                    add(
                        Ai3DLineSpec(
                            p1 = l.optString("p1"),
                            p2 = l.optString("p2"),
                            style = LineStyle.fromKey(l.optString("style", "dashed")),
                            colorHex = rawColor,
                            highlighted = highlighted
                        )
                    )
                }
            }
        }

        val plArr = o.optJSONArray("planes")
        val planes = buildList {
            if (plArr != null) {
                for (i in 0 until plArr.length()) {
                    val ptsArr = plArr.optJSONArray(i) ?: plArr.optJSONObject(i)?.optJSONArray("pts") ?: continue
                    val list = (0 until ptsArr.length()).mapNotNull { j -> ptsArr.optString(j).takeIf { it.isNotBlank() } }
                    if (list.size >= 3) add(list)
                }
            }
        }

        val secObj = o.optJSONObject("dynamicSection")
        val dynSec = secObj?.let { s ->
            val p1 = s.optString("p1"); val p2 = s.optString("p2"); val p3 = s.optString("p3")
            if (p1.isNotBlank() && p2.isNotBlank() && p3.isNotBlank()) Triple(p1, p2, p3) else null
        }

        return Ai3DSceneSpec(
            shapeKey = o.optString("shapeKey", "pyramid4_sa"),
            a = o.optDouble("a", 4.0).toFloat(),
            b = o.optDouble("b", 3.0).toFloat(),
            h = o.optDouble("h", 4.8).toFloat(),
            showAxes = o.optBoolean("showAxes", false),
            showAltitude = o.optBoolean("showAltitude", false),
            originPoint = o.optString("originPoint").takeIf { it.isNotBlank() },
            edgePoints = edgePoints,
            xyzPoints = xyzPoints,
            lines = lines,
            planes = planes,
            dynamicSection = dynSec
        )
    }

    private fun parse2DSpec(o: JSONObject): Ai2DSceneSpec {
        val ptArr = o.optJSONArray("points")
        val points = buildList {
            if (ptArr != null) {
                for (i in 0 until ptArr.length()) {
                    val p = ptArr.optJSONObject(i) ?: continue
                    add(
                        Ai2DPointSpec(
                            name = p.optString("name"),
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            colorHex = p.optString("colorHex", "")
                        )
                    )
                }
            }
        }

        val objArr = o.optJSONArray("objects")
        val objects = buildList {
            if (objArr != null) {
                for (i in 0 until objArr.length()) {
                    val item = objArr.optJSONObject(i) ?: continue
                    val pnArr = item.optJSONArray("pointNames")
                    val pNames = buildList {
                        if (pnArr != null) {
                            for (j in 0 until pnArr.length()) {
                                pnArr.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    val rawColor = item.optString("colorHex", "").trim()
                    val highlighted = if (item.has("highlighted") && !item.isNull("highlighted")) {
                        item.optBoolean("highlighted", false)
                    } else {
                        isValidHex(rawColor)
                    }
                    add(
                        Ai2DObjectSpec(
                            kind = item.optString("kind", "segment"),
                            p1 = item.optString("p1").takeIf { it.isNotBlank() },
                            p2 = item.optString("p2").takeIf { it.isNotBlank() },
                            p3 = item.optString("p3").takeIf { it.isNotBlank() },
                            radius = if (item.has("radius") && !item.isNull("radius")) item.optDouble("radius").toFloat() else null,
                            pointNames = pNames,
                            dashed = item.optBoolean("dashed", false),
                            colorHex = rawColor,
                            highlighted = highlighted
                        )
                    )
                }
            }
        }

        val fnArr = o.optJSONArray("functions")
        val functions = buildList {
            if (fnArr != null) {
                for (i in 0 until fnArr.length()) {
                    val f = fnArr.optJSONObject(i) ?: continue
                    add(
                        Ai2DFunctionSpec(
                            expression = f.optString("expression"),
                            showDerivative = f.optBoolean("showDerivative", false),
                            colorHex = f.optString("colorHex", "#38BDF8")
                        )
                    )
                }
            }
        }

        return Ai2DSceneSpec(
            points = points,
            objects = objects,
            functions = functions,
            paramM = o.optDouble("paramM", 1.0)
        )
    }

    private fun isValidHex(raw: String?): Boolean {
        val t = raw?.trim().orEmpty()
        return Regex("^#[0-9A-Fa-f]{6}$").matches(t)
    }

    private fun sanitizeHex(raw: String?, fallback: String): String {
        val t = raw?.trim().orEmpty()
        return if (isValidHex(t)) t else fallback
    }
}
