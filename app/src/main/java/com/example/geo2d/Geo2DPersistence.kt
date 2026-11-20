package com.example.geo2d

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Geo2DPersistence(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAutoSession(snapshot: Geo2DSnapshot) {
        prefs.edit().putString(KEY_SESSION, snapshotToJson(snapshot)).apply()
    }

    fun loadAutoSession(): Geo2DSnapshot? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { jsonToSnapshot(raw) }.getOrNull()
    }

    fun saveLastWorkspace(workspace: MathWorkspace) {
        prefs.edit().putString(KEY_WORKSPACE, workspace.name).apply()
    }

    fun loadLastWorkspace(): MathWorkspace {
        val name = prefs.getString(KEY_WORKSPACE, null)
        return runCatching { MathWorkspace.valueOf(name ?: "") }.getOrDefault(MathWorkspace.SPACE_3D)
    }

    companion object {
        private const val PREFS_NAME = "geo2d_oxy_storage_v1"
        private const val KEY_SESSION = "auto_session_2d_json"
        private const val KEY_WORKSPACE = "last_workspace_mode"

        fun snapshotToJson(s: Geo2DSnapshot): String {
            val root = JSONObject()
            root.put("type", "tool_toan_2d")
            root.put("version", 1)
            root.put("showAxes", s.showAxes)
            root.put("showGrid", s.showGrid)
            root.put("snapToGrid", s.snapToGrid)
            root.put("showPointCoords", s.showPointCoords)
            root.put("showMeasurements", s.showMeasurements)
            root.put("showKeyGraphPoints", s.showKeyGraphPoints)
            root.put("showTangentOnTrace", s.showTangentOnTrace)

            root.put("sensitivity", JSONObject().apply {
                put("pan", s.sensitivity.pan.toDouble())
                put("zoom", s.sensitivity.zoom.toDouble())
                put("touchRadius", s.sensitivity.touchRadius.toDouble())
            })
            root.put("camera", JSONObject().apply {
                put("centerX", s.camera.centerX.toDouble())
                put("centerY", s.camera.centerY.toDouble())
                put("pixelsPerUnit", s.camera.pixelsPerUnit.toDouble())
            })

            val ptsArr = JSONArray()
            s.points.forEach { p ->
                ptsArr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("x", p.x.toDouble())
                    put("y", p.y.toDouble())
                    put("color", p.colorHex)
                })
            }
            root.put("points", ptsArr)

            val objsArr = JSONArray()
            s.objects.forEach { obj ->
                val o = JSONObject()
                o.put("id", obj.id)
                o.put("color", obj.colorHex)
                when (obj) {
                    is Geo2DObject.Segment -> {
                        o.put("kind", "segment")
                        o.put("p1Id", obj.p1Id)
                        o.put("p2Id", obj.p2Id)
                        o.put("dashed", obj.dashed)
                    }
                    is Geo2DObject.Line -> {
                        o.put("kind", "line")
                        o.put("p1Id", obj.p1Id)
                        o.put("p2Id", obj.p2Id)
                        o.put("dashed", obj.dashed)
                    }
                    is Geo2DObject.Ray -> {
                        o.put("kind", "ray")
                        o.put("startId", obj.startId)
                        o.put("throughId", obj.throughId)
                    }
                    is Geo2DObject.VectorObj -> {
                        o.put("kind", "vector")
                        o.put("fromId", obj.fromId)
                        o.put("toId", obj.toId)
                    }
                    is Geo2DObject.Circle -> {
                        o.put("kind", "circle")
                        o.put("centerId", obj.centerId)
                        if (obj.edgePointId != null) o.put("edgePointId", obj.edgePointId)
                        if (obj.fixedRadius != null) o.put("fixedRadius", obj.fixedRadius.toDouble())
                    }
                    is Geo2DObject.Polygon -> {
                        o.put("kind", "polygon")
                        o.put("pointIds", JSONArray(obj.pointIds))
                    }
                    is Geo2DObject.AngleMeasure -> {
                        o.put("kind", "angle")
                        o.put("pAId", obj.pAId)
                        o.put("vertexId", obj.vertexId)
                        o.put("pCId", obj.pCId)
                    }
                }
                objsArr.put(o)
            }
            root.put("objects", objsArr)

            val fnArr = JSONArray()
            s.functions.forEach { fn ->
                fnArr.put(JSONObject().apply {
                    put("id", fn.id)
                    put("label", fn.label)
                    put("expression", fn.expression)
                    put("color", fn.colorHex)
                    put("visible", fn.visible)
                })
            }
            root.put("functions", fnArr)

            return root.toString(2)
        }

        fun jsonToSnapshot(jsonStr: String): Geo2DSnapshot {
            val o = JSONObject(jsonStr)
            val points = mutableListOf<Geo2DPoint>()
            o.optJSONArray("points")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    points.add(
                        Geo2DPoint(
                            id = p.optString("id", "p_$i"),
                            name = p.optString("name", "P$i"),
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            colorHex = p.optString("color", "#38BDF8")
                        )
                    )
                }
            }

            val objects = mutableListOf<Geo2DObject>()
            o.optJSONArray("objects")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("id", "obj_$i")
                    val color = item.optString("color", "#F8FAFC")
                    when (item.optString("kind")) {
                        "segment" -> objects.add(
                            Geo2DObject.Segment(id, item.getString("p1Id"), item.getString("p2Id"), item.optBoolean("dashed", false), color)
                        )
                        "line" -> objects.add(
                            Geo2DObject.Line(id, item.getString("p1Id"), item.getString("p2Id"), item.optBoolean("dashed", false), color)
                        )
                        "ray" -> objects.add(
                            Geo2DObject.Ray(id, item.getString("startId"), item.getString("throughId"), color)
                        )
                        "vector" -> objects.add(
                            Geo2DObject.VectorObj(id, item.getString("fromId"), item.getString("toId"), color)
                        )
                        "circle" -> objects.add(
                            Geo2DObject.Circle(
                                id = id,
                                centerId = item.getString("centerId"),
                                edgePointId = if (item.has("edgePointId")) item.optString("edgePointId") else null,
                                fixedRadius = if (item.has("fixedRadius")) item.optDouble("fixedRadius", 2.0).toFloat() else null,
                                colorHex = color
                            )
                        )
                        "polygon" -> {
                            val idsArr = item.optJSONArray("pointIds") ?: JSONArray()
                            val ids = (0 until idsArr.length()).map { idsArr.getString(it) }
                            if (ids.size >= 3) objects.add(Geo2DObject.Polygon(id, ids, color))
                        }
                        "angle" -> objects.add(
                            Geo2DObject.AngleMeasure(id, item.getString("pAId"), item.getString("vertexId"), item.getString("pCId"), color)
                        )
                    }
                }
            }

            val functions = mutableListOf<GraphFunction>()
            o.optJSONArray("functions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    functions.add(
                        GraphFunction(
                            id = f.optString("id", "fn_$i"),
                            label = f.optString("label", "f${i + 1}(x)"),
                            expression = f.optString("expression", ""),
                            colorHex = f.optString("color", "#38BDF8"),
                            visible = f.optBoolean("visible", true)
                        )
                    )
                }
            }

            val sens = o.optJSONObject("sensitivity")?.let {
                Geo2DSensitivity(
                    pan = it.optDouble("pan", 1.0).toFloat().coerceIn(0.4f, 2.2f),
                    zoom = it.optDouble("zoom", 1.0).toFloat().coerceIn(0.4f, 2.2f),
                    touchRadius = it.optDouble("touchRadius", 1.0).toFloat().coerceIn(0.7f, 1.7f)
                )
            } ?: Geo2DSensitivity()

            val cam = o.optJSONObject("camera")?.let {
                Geo2DCamera(
                    centerX = it.optDouble("centerX", 0.0).toFloat(),
                    centerY = it.optDouble("centerY", 0.0).toFloat(),
                    pixelsPerUnit = it.optDouble("pixelsPerUnit", 72.0).toFloat().coerceIn(14f, 360f)
                )
            } ?: Geo2DCamera()

            return Geo2DSnapshot(
                points = points,
                objects = objects,
                functions = functions,
                showAxes = o.optBoolean("showAxes", true),
                showGrid = o.optBoolean("showGrid", true),
                snapToGrid = o.optBoolean("snapToGrid", true),
                showPointCoords = o.optBoolean("showPointCoords", true),
                showMeasurements = o.optBoolean("showMeasurements", true),
                showKeyGraphPoints = o.optBoolean("showKeyGraphPoints", true),
                showTangentOnTrace = o.optBoolean("showTangentOnTrace", true),
                sensitivity = sens,
                camera = cam
            )
        }
    }
}
