package com.example.data

import android.content.Context
import com.example.engine.*
import org.json.JSONArray
import org.json.JSONObject

class GeometryPersistence(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAutoSession(snapshot: GeometrySnapshot) {
        prefs.edit().putString(KEY_SESSION, snapshotToJson(snapshot)).apply()
    }

    fun loadAutoSession(): GeometrySnapshot? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { jsonToSnapshot(raw) }.getOrNull()
    }

    fun clearAutoSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun loadNamedSlots(): List<SavedSlot> {
        val raw = prefs.getString(KEY_SLOTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                SavedSlot(
                    name = obj.optString("name", "Bài vẽ"),
                    savedAt = obj.optString("savedAt", ""),
                    shapeTitle = obj.optString("shapeTitle", ""),
                    snapshotJson = obj.optString("snapshotJson", "{}")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveNamedSlot(slot: SavedSlot): List<SavedSlot> {
        val list = loadNamedSlots().filterNot { it.name == slot.name }.toMutableList()
        list.add(0, slot)
        persistSlots(list)
        return list
    }

    fun deleteNamedSlot(name: String): List<SavedSlot> {
        val list = loadNamedSlots().filterNot { it.name == name }
        persistSlots(list)
        return list
    }

    private fun persistSlots(slots: List<SavedSlot>) {
        val arr = JSONArray()
        slots.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("savedAt", s.savedAt)
                put("shapeTitle", s.shapeTitle)
                put("snapshotJson", s.snapshotJson)
            })
        }
        prefs.edit().putString(KEY_SLOTS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "geo3d_oxyz_storage_v1"
        private const val KEY_SESSION = "auto_session_json"
        private const val KEY_SLOTS = "named_slots_json"

        /**
         * 100% schema-compatible with the HTML tool's JSON export/import format.
         */
        fun snapshotToJson(s: GeometrySnapshot): String {
            val root = JSONObject()
            root.put("version", 1)
            root.put("shape", s.shape.key)
            root.put("a", s.a.toDouble())
            root.put("b", s.b.toDouble())
            root.put("h", s.h.toDouble())
            root.put("originPoint", s.originPoint)
            root.put("showAxes", s.showAxes)
            root.put("showCoordsOnLabels", s.showCoordsOnLabels)
            root.put("showGrid", s.showGrid)
            root.put("dashed", s.dashed)
            root.put("labels", s.labels)
            root.put("altitude", s.altitude)
            root.put("faces", s.faces)
            root.put("showSection", s.showSection)
            root.put("secRatio", s.secRatio.toDouble())
            root.put("keepRelations", s.keepRelations)
            root.put("snapEnabled", s.snapEnabled)

            val camPos = s.camera.cameraPosition()
            root.put("cameraPos", JSONObject().apply {
                put("x", camPos.x.toDouble()); put("y", camPos.y.toDouble()); put("z", camPos.z.toDouble())
            })
            root.put("controlsTarget", JSONObject().apply {
                put("x", s.camera.target.x.toDouble()); put("y", s.camera.target.y.toDouble()); put("z", s.camera.target.z.toDouble())
            })

            val ovObj = JSONObject()
            s.vertexOverrides.forEach { (k, v) ->
                ovObj.put(k, JSONObject().apply {
                    put("x", v.x.toDouble()); put("y", v.y.toDouble()); put("z", v.z.toDouble())
                })
            }
            root.put("vertexOverrides", ovObj)

            val cpArr = JSONArray()
            s.customPoints.forEach { p ->
                cpArr.put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("from", p.from); put("to", p.to); put("ratio", p.ratio.toDouble())
                })
            }
            root.put("customPoints", cpArr)

            val xyzArr = JSONArray()
            s.customXYZPoints.forEach { p ->
                xyzArr.put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("z", p.z.toDouble())
                })
            }
            root.put("customXYZPoints", xyzArr)

            val lnArr = JSONArray()
            s.customLines.forEach { l ->
                lnArr.put(JSONObject().apply {
                    put("id", l.id); put("p1", l.p1); put("p2", l.p2); put("style", l.style.key); put("color", l.colorHex)
                })
            }
            root.put("customLines", lnArr)

            val plArr = JSONArray()
            s.customPlanes.forEach { pl ->
                plArr.put(JSONObject().apply {
                    put("id", pl.id)
                    put("pts", JSONArray(pl.pts))
                })
            }
            root.put("customPlanes", plArr)

            return root.toString(2)
        }

        fun jsonToSnapshot(jsonStr: String): GeometrySnapshot {
            val o = JSONObject(jsonStr)
            val overrides = mutableMapOf<String, Vec3>()
            o.optJSONObject("vertexOverrides")?.let { ov ->
                val keys = ov.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = ov.getJSONObject(k)
                    overrides[k] = Vec3(v.optDouble("x", 0.0).toFloat(), v.optDouble("y", 0.0).toFloat(), v.optDouble("z", 0.0).toFloat())
                }
            }

            val customPts = mutableListOf<CustomEdgePoint>()
            o.optJSONArray("customPoints")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    customPts.add(
                        CustomEdgePoint(
                            id = p.optString("id", "pt_$i"),
                            name = p.optString("name", "M"),
                            from = p.optString("from", "S"),
                            to = p.optString("to", "A"),
                            ratio = p.optDouble("ratio", 0.5).toFloat()
                        )
                    )
                }
            }

            val xyzPts = mutableListOf<CustomXYZPoint>()
            o.optJSONArray("customXYZPoints")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    xyzPts.add(
                        CustomXYZPoint(
                            id = p.optString("id", "xyz_$i"),
                            name = p.optString("name", "K"),
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            z = p.optDouble("z", 0.0).toFloat()
                        )
                    )
                }
            }

            val lines = mutableListOf<CustomLine>()
            o.optJSONArray("customLines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val l = arr.getJSONObject(i)
                    lines.add(
                        CustomLine(
                            id = l.optString("id", "ln_$i"),
                            p1 = l.optString("p1", "S"),
                            p2 = l.optString("p2", "A"),
                            style = LineStyle.fromKey(l.optString("style", "dashed")),
                            colorHex = l.optString("color", "#38bdf8")
                        )
                    )
                }
            }

            val planes = mutableListOf<CustomPlane>()
            o.optJSONArray("customPlanes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pl = arr.getJSONObject(i)
                    val ptsArr = pl.optJSONArray("pts") ?: JSONArray()
                    val pts = (0 until ptsArr.length()).map { ptsArr.getString(it) }
                    planes.add(CustomPlane(pl.optString("id", "pl_$i"), pts))
                }
            }

            val cam = run {
                val cPos = o.optJSONObject("cameraPos")
                val cTgt = o.optJSONObject("controlsTarget")
                if (cPos != null && cTgt != null) {
                    CameraOrbitState.fromPositionAndTarget(
                        Vec3(cPos.optDouble("x", 7.5).toFloat(), cPos.optDouble("y", 5.5).toFloat(), cPos.optDouble("z", 8.5).toFloat()),
                        Vec3(cTgt.optDouble("x", 0.0).toFloat(), cTgt.optDouble("y", 1.8).toFloat(), cTgt.optDouble("z", 0.0).toFloat())
                    )
                } else CameraOrbitState.DEFAULT
            }

            return GeometrySnapshot(
                shape = ShapeType.fromKey(o.optString("shape", "pyramid3")),
                a = o.optDouble("a", 4.0).toFloat(),
                b = o.optDouble("b", 3.0).toFloat(),
                h = o.optDouble("h", 4.8).toFloat(),
                originPoint = o.optString("originPoint", "O"),
                showAxes = o.optBoolean("showAxes", false),
                showCoordsOnLabels = o.optBoolean("showCoordsOnLabels", false),
                showGrid = o.optBoolean("showGrid", true),
                dashed = o.optBoolean("dashed", true),
                labels = o.optBoolean("labels", true),
                altitude = o.optBoolean("altitude", true),
                faces = o.optBoolean("faces", true),
                showSection = o.optBoolean("showSection", false),
                secRatio = o.optDouble("secRatio", 0.5).toFloat(),
                keepRelations = o.optBoolean("keepRelations", true),
                snapEnabled = o.optBoolean("snapEnabled", true),
                vertexOverrides = overrides,
                customPoints = customPts,
                customXYZPoints = xyzPts,
                customLines = lines,
                customPlanes = planes,
                camera = cam
            )
        }
    }
}
