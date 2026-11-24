package com.example.data

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.example.engine.GeometrySnapshot
import com.example.geo2d.Geo2DObject
import com.example.geo2d.Geo2DSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes Android 3D and 2D workspaces into URL-safe Base64 hash links
 * compatible 100% with the Tool Toán Web app (`https://tooltoan.pages.dev/#share3d=...` and `#share2d=...`).
 */
object WebShareUrlEncoder {

    private const val WEB_BASE_URL = "https://tooltoan.pages.dev/"

    private fun toBase64Url(json: String): String {
        val bytes = json.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun buildUrl3D(snapshot: GeometrySnapshot): String {
        val root = JSONObject().apply {
            put("shapeKey", snapshot.shape.key)
            put("a", snapshot.a.toDouble())
            put("b", snapshot.b.toDouble())
            put("h", snapshot.h.toDouble())
            put("originPoint", snapshot.originPoint)

            if (snapshot.vertexOverrides.isNotEmpty()) {
                val ov = JSONObject()
                snapshot.vertexOverrides.forEach { (k, v) ->
                    ov.put(
                        k,
                        JSONObject().apply {
                            put("x", v.x.toDouble())
                            put("y", v.y.toDouble())
                            put("z", v.z.toDouble())
                        }
                    )
                }
                put("vertexOverrides", ov)
            }

            val customPts = JSONArray()
            snapshot.customPoints.forEach { p ->
                customPts.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("from", p.from)
                        put("to", p.to)
                        put("ratio", p.ratio.toDouble())
                    }
                )
            }
            put("customPoints", customPts)

            val xyzPts = JSONArray()
            snapshot.customXYZPoints.forEach { p ->
                xyzPts.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("z", p.z.toDouble())
                    }
                )
            }
            put("customXYZPoints", xyzPts)

            val lines = JSONArray()
            snapshot.customLines.forEach { ln ->
                lines.put(
                    JSONObject().apply {
                        put("id", ln.id)
                        put("p1", ln.p1)
                        put("p2", ln.p2)
                        put("style", ln.style.key)
                        put("color", ln.colorHex)
                    }
                )
            }
            put("customLines", lines)

            val planes = JSONArray()
            snapshot.customPlanes.forEach { pl ->
                planes.put(
                    JSONObject().apply {
                        put("id", pl.id)
                        put("pts", JSONArray(pl.pts))
                        put("color", "#10b981")
                    }
                )
            }
            put("customPlanes", planes)

            val ds = snapshot.dynamicSection
            if (ds != null) {
                put(
                    "dynamicSection",
                    JSONObject().apply {
                        put("p1", ds.p1)
                        put("p2", ds.p2)
                        put("p3", ds.p3)
                    }
                )
            } else {
                put("dynamicSection", JSONObject.NULL)
            }
        }
        return "${WEB_BASE_URL}#share3d=${toBase64Url(root.toString())}"
    }

    fun buildUrl2D(snapshot: Geo2DSnapshot): String {
        val root = JSONObject().apply {
            val ptsArr = JSONArray()
            snapshot.points.forEach { p ->
                ptsArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("color", p.colorHex)
                    }
                )
            }
            put("points", ptsArr)

            val objArr = JSONArray()
            snapshot.objects.forEach { obj ->
                val jo = JSONObject().apply {
                    put("id", obj.id)
                    put("color", obj.colorHex)
                }
                when (obj) {
                    is Geo2DObject.Segment -> {
                        jo.put("kind", "segment")
                        jo.put("p1Id", obj.p1Id)
                        jo.put("p2Id", obj.p2Id)
                        if (obj.dashed) jo.put("dashed", true)
                    }
                    is Geo2DObject.Line -> {
                        jo.put("kind", "line")
                        jo.put("p1Id", obj.p1Id)
                        jo.put("p2Id", obj.p2Id)
                    }
                    is Geo2DObject.Ray -> {
                        jo.put("kind", "ray")
                        jo.put("startId", obj.startId)
                        jo.put("throughId", obj.throughId)
                    }
                    is Geo2DObject.VectorObj -> {
                        jo.put("kind", "vector")
                        jo.put("fromId", obj.fromId)
                        jo.put("toId", obj.toId)
                    }
                    is Geo2DObject.Circle -> {
                        jo.put("kind", "circle")
                        jo.put("centerId", obj.centerId)
                        obj.edgePointId?.let { jo.put("edgePointId", it) }
                        obj.fixedRadius?.let { jo.put("fixedRadius", it.toDouble()) }
                    }
                    is Geo2DObject.Polygon -> {
                        jo.put("kind", "polygon")
                        jo.put("pointIds", JSONArray(obj.pointIds))
                    }
                    is Geo2DObject.AngleMeasure -> {
                        jo.put("kind", "angle")
                        jo.put("pAId", obj.pAId)
                        jo.put("vertexId", obj.vertexId)
                        jo.put("pCId", obj.pCId)
                    }
                }
                objArr.put(jo)
            }
            put("objects", objArr)

            val fnArr = JSONArray()
            snapshot.functions.forEach { fn ->
                fnArr.put(
                    JSONObject().apply {
                        put("id", fn.id)
                        put("label", fn.label)
                        put("expression", fn.expression)
                        put("color", fn.colorHex)
                        put("visible", fn.visible)
                        put("showDerivative", fn.showDerivative)
                    }
                )
            }
            put("functions", fnArr)
            put("paramM", snapshot.paramM)
        }
        return "${WEB_BASE_URL}#share2d=${toBase64Url(root.toString())}"
    }

    fun share3D(context: Context, snapshot: GeometrySnapshot) {
        val url = buildUrl3D(snapshot)
        launchShareSheet(
            context = context,
            title = "Chia sẻ hình 3D Tool Toán",
            body = "Xem trực tiếp khối hình 3D (${snapshot.shape.title}) trên Tool Toán Web:\n$url"
        )
    }

    fun share2D(context: Context, snapshot: Geo2DSnapshot) {
        val url = buildUrl2D(snapshot)
        launchShareSheet(
            context = context,
            title = "Chia sẻ đồ thị & hình 2D Tool Toán",
            body = "Xem trực tiếp hình phẳng 2D & đồ thị hàm số trên Tool Toán Web:\n$url"
        )
    }

    private fun launchShareSheet(context: Context, title: String, body: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
