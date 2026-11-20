package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeometryPersistence
import com.example.engine.*
import com.example.geo2d.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tool Toán", appName)
    }

    @Test
    fun `all 15 shapes build with valid positive volume`() {
        ShapeType.entries.forEach { shape ->
            val snap = GeometrySnapshot(shape = shape, a = 4f, b = 3f, h = 5f)
            val built = ShapeBuilder.build(snap)
            assertTrue("Shape ${shape.key} should have vertices", built.pointRegistry.isNotEmpty())
            assertTrue("Shape ${shape.key} should have positive volume", built.metrics.vol > 0f)
        }
    }

    @Test
    fun `cube and sphere exact volume calculations`() {
        val cube = ShapeBuilder.build(GeometrySnapshot(shape = ShapeType.CUBE, a = 3f))
        assertEquals(27f, cube.metrics.vol, 0.05f)
        assertEquals(54f, cube.metrics.sTp, 0.05f)

        val sphere = ShapeBuilder.build(GeometrySnapshot(shape = ShapeType.SPHERE, a = 4f))
        val expectedVol = ((4.0 / 3.0) * PI * 8.0).toFloat()
        assertEquals(expectedVol, sphere.metrics.vol, 0.05f)
    }

    @Test
    fun `json serialization round trip matches HTML format`() {
        val snap = GeometrySnapshot(
            shape = ShapeType.PYRAMID4_SA,
            a = 4.5f,
            h = 5.2f,
            showAxes = true,
            originPoint = "A",
            customPoints = listOf(CustomEdgePoint("pt_1", "M", "S", "C", 0.5f)),
            customLines = listOf(CustomLine("ln_1", "A", "M", LineStyle.VECTOR_SOLID, "#f43f5e")),
            customPlanes = listOf(CustomPlane("pl_1", listOf("A", "B", "M")))
        )
        val json = GeometryPersistence.snapshotToJson(snap)
        val restored = GeometryPersistence.jsonToSnapshot(json)
        assertEquals(snap.shape, restored.shape)
        assertEquals(snap.a, restored.a, 0.01f)
        assertEquals(snap.h, restored.h, 0.01f)
        assertEquals(snap.showAxes, restored.showAxes)
        assertEquals(1, restored.customPoints.size)
        assertEquals(1, restored.customLines.size)
        assertEquals(1, restored.customPlanes.size)
    }

    @Test
    fun `sensitivity config persists accurately in json round trip`() {
        val customSens = SensitivityConfig(
            rotation = 0.55f,
            zoom = 1.45f,
            drag = 0.65f,
            touchRadius = 1.3f
        )
        val snap = GeometrySnapshot(sensitivity = customSens)
        val json = GeometryPersistence.snapshotToJson(snap)
        val restored = GeometryPersistence.jsonToSnapshot(json)

        assertEquals(0.55f, restored.sensitivity.rotation, 0.01f)
        assertEquals(1.45f, restored.sensitivity.zoom, 0.01f)
        assertEquals(0.65f, restored.sensitivity.drag, 0.01f)
        assertEquals(1.30f, restored.sensitivity.touchRadius, 0.01f)
    }

    @Test
    fun `round shapes generate full 3D curved surface meshes and valid OBJ export`() {
        val roundShapes = listOf(ShapeType.CYLINDER, ShapeType.CONE, ShapeType.SPHERE)
        roundShapes.forEach { shape ->
            val snap = GeometrySnapshot(shape = shape, a = 4f, h = 5f, faces = true)
            val built = ShapeBuilder.build(snap)
            assertTrue("${shape.key} should build 3D curved faces", built.faces.size >= 40)
            assertTrue("${shape.key} should mark curved mesh faces", built.faces.any { it.isCurvedMesh })

            // Verify Wavefront OBJ export works even when preview faces toggle is off
            val obj = MeshExporter.exportToObj(snap.copy(faces = false), "Test-${shape.key}")
            assertTrue("${shape.key} OBJ should contain vertices", obj.contains("\nv "))
            assertTrue("${shape.key} OBJ should contain faces", obj.contains("\nf "))
        }
    }

    @Test
    fun `2d arbitrary function parser evaluates complex expressions and finds roots`() {
        val r1 = MathExprParser.parse("2x^2 - 8")
        assertTrue(r1 is MathExprParser.ParseResult.Ok)
        val e1 = (r1 as MathExprParser.ParseResult.Ok).expr
        assertEquals(0.0, e1.eval(2.0), 1e-6)
        assertEquals(0.0, e1.eval(-2.0), 1e-6)
        assertEquals(-8.0, e1.eval(0.0), 1e-6)

        val r2 = MathExprParser.parse("y = 2sin(pi/2) + sqrt(9) - | -4 |")
        assertTrue(r2 is MathExprParser.ParseResult.Ok)
        val e2 = (r2 as MathExprParser.ParseResult.Ok).expr
        assertEquals(1.0, e2.eval(0.0), 1e-6)

        val keyPoints = MathExprParser.analyzeFunction(e1, -5.0, 5.0, -10.0, 10.0, "#38BDF8")
        val roots = keyPoints.filter { it.type == KeyPointType.ROOT }
        assertEquals(2, roots.size)
        assertTrue(keyPoints.any { it.type == KeyPointType.LOCAL_MIN })
    }

    @Test
    fun `2d snapshot json serialization round trip preserves points objects and functions`() {
        val p1 = Geo2DPoint("p1", "A", 1f, 2f)
        val p2 = Geo2DPoint("p2", "B", 4f, 6f)
        val snap = Geo2DSnapshot(
            points = listOf(p1, p2),
            objects = listOf(
                Geo2DObject.Segment("s1", "p1", "p2"),
                Geo2DObject.Circle("c1", "p1", fixedRadius = 3f)
            ),
            functions = listOf(GraphFunction("f1", "f1(x)", "x^3 - 3x", "#38BDF8", true))
        )
        val json = Geo2DPersistence.snapshotToJson(snap)
        val restored = Geo2DPersistence.jsonToSnapshot(json)
        assertEquals(2, restored.points.size)
        assertEquals(2, restored.objects.size)
        assertEquals(1, restored.functions.size)
        assertEquals("x^3 - 3x", restored.functions.first().expression)
    }

    @Test
    fun `2d exp log functions symbolic derivative and infinite zoom persistence work`() {
        // 1. Exponential & arbitrary-base logarithmic functions
        val log2Res = MathExprParser.parse("log(2; 8)") as MathExprParser.ParseResult.Ok
        assertEquals(3.0, log2Res.expr.eval(0.0), 1e-6)

        val logSubRes = MathExprParser.parse("log_3(x)") as MathExprParser.ParseResult.Ok
        assertEquals(2.0, logSubRes.expr.eval(9.0), 1e-6)

        val expRes = MathExprParser.parse("e^x") as MathExprParser.ParseResult.Ok
        assertEquals(kotlin.math.E, expRes.expr.eval(1.0), 1e-6)

        // 2. Symbolic derivative evaluation
        val derivPoly = MathExprParser.derive("x^3 - 3x")
        assertTrue(derivPoly != null)
        assertEquals(9.0, derivPoly!!.first.eval(2.0), 1e-6)
        assertEquals(-3.0, derivPoly.first.eval(0.0), 1e-6)

        val derivLn = MathExprParser.derive("ln(x)")
        assertTrue(derivLn != null)
        assertEquals(0.25, derivLn!!.first.eval(4.0), 1e-6)

        // 3. Grouped FunctionLibrary covers every category including EXP_LOG
        FunctionCategory.entries.forEach { cat ->
            assertTrue("Category $cat should have templates", FunctionLibrary.byCategory(cat).isNotEmpty())
        }

        // 4. Infinite zoom Double camera & showDerivative round trip
        val extremeSnap = Geo2DSnapshot(
            functions = listOf(
                GraphFunction("f1", "f₁(x)", "e^x", "#38BDF8", visible = false, showDerivative = true)
            ),
            camera = Geo2DCamera(centerX = 12500.0, centerY = -8400.0, pixelsPerUnit = 0.001)
        )
        val restored = Geo2DPersistence.jsonToSnapshot(Geo2DPersistence.snapshotToJson(extremeSnap))
        assertEquals(false, restored.functions.first().visible)
        assertEquals(true, restored.functions.first().showDerivative)
        assertEquals(12500.0, restored.camera.centerX, 1e-3)
        assertEquals(0.001, restored.camera.pixelsPerUnit, 1e-6)
    }

    @Test
    fun `2d and 3d measurement accuracy fixes verified`() {
        // 1. Exact equilateral triangle: side = 4, angle = 60 deg
        val hEq = 2.0 * kotlin.math.sqrt(3.0)
        val a = Vec2(0f, (hEq * 2.0 / 3.0).toFloat())
        val b = Vec2(-2f, (-hEq / 3.0).toFloat())
        val c = Vec2(2f, (-hEq / 3.0).toFloat())
        assertEquals(4.0, Geo2DMathHelpers.distance(a, b), 1e-4)
        assertEquals(4.0, Geo2DMathHelpers.distance(b, c), 1e-4)
        assertEquals(60.0f, Geo2DMathHelpers.angleDegrees(b, a, c), 0.01f)
        assertEquals(12.0f, Geo2DMathHelpers.polygonPerimeter(listOf(a, b, c)), 0.01f)
        assertEquals((4.0 * kotlin.math.sqrt(3.0)).toFloat(), Geo2DMathHelpers.polygonArea(listOf(a, b, c)), 0.01f)

        // 2. 3D true perpendicular altitude stays exact even when apex S shifts sideways
        val shiftedPyramid = GeometrySnapshot(
            shape = ShapeType.PYRAMID4,
            a = 4f,
            h = 6f,
            vertexOverrides = mapOf("S" to Vec3(5f, 6f, -3f))
        )
        val built = ShapeBuilder.build(shiftedPyramid)
        assertEquals(6.0f, built.metrics.realH, 0.02f)
    }

    @Test
    fun `parser handles vietnamese decimal commas log shorthand precedence and safe json nulls`() {
        // 1. Vietnamese decimal comma syntax
        val decPoly = MathExprParser.parse("0,5x^2 + 1,5x - 2,25") as MathExprParser.ParseResult.Ok
        assertEquals(0.5 * 4.0 + 1.5 * 2.0 - 2.25, decPoly.expr.eval(2.0), 1e-6)

        // 2. Arbitrary-base log with decimal comma, comma separator, and log_(expr)(x)
        val logDec = MathExprParser.parse("log(0,5; x)") as MathExprParser.ParseResult.Ok
        assertEquals(-3.0, logDec.expr.eval(8.0), 1e-6)

        val logCommaSep = MathExprParser.parse("log(2, 8)") as MathExprParser.ParseResult.Ok
        assertEquals(3.0, logCommaSep.expr.eval(0.0), 1e-6)

        val logFracBase = MathExprParser.parse("log_(1/2)(x)") as MathExprParser.ParseResult.Ok
        assertEquals(-2.0, logFracBase.expr.eval(4.0), 1e-6)

        // 3. UnaryNeg precedence inside power pretty-printing: (-x)^2 must keep parentheses
        val negSquare = MathExprParser.Expr.Binary('^', MathExprParser.Expr.UnaryNeg(MathExprParser.Expr.VarX), MathExprParser.Expr.Num(2.0))
        assertEquals("(-x)^2", negSquare.toPrettyString())

        // 4. Geo2DPersistence safely handles explicit JSON null fields without turning them into "null"
        val jsonWithNulls = """
            {
              "points": [{"id":"p1","name":"A","x":0,"y":0}],
              "objects": [
                {"id":"c1","kind":"circle","centerId":"p1","edgePointId":null,"fixedRadius":2.5},
                {"id":"bad","kind":"segment","p1Id":"","p2Id":"p1"}
              ]
            }
        """.trimIndent()
        val parsedSnap = Geo2DPersistence.jsonToSnapshot(jsonWithNulls)
        assertEquals(1, parsedSnap.objects.size)
        val circle = parsedSnap.objects.first() as Geo2DObject.Circle
        assertEquals(null, circle.edgePointId)
        assertEquals(2.5f, circle.fixedRadius!!, 1e-4f)
    }
}
