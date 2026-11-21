package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.*
import com.example.data.GeometryPersistence
import com.example.docs.*
import com.example.engine.*
import com.example.geo2d.*
import com.example.ui.theme.*
import com.example.update.GitHubUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `3d dynamic cross section and oxyz line plane angle and distance are accurate`() {
        // 1. Dynamic cross-section through midpoints of edges on a 4x4x4 cube (area = 4*4 = 16 for horizontal mid-plane)
        val snapCube = GeometrySnapshot(
            shape = ShapeType.CUBE,
            a = 4f,
            customPoints = listOf(
                CustomEdgePoint("p1", "M", "A", "A'", 0.5f),
                CustomEdgePoint("p2", "N", "B", "B'", 0.5f),
                CustomEdgePoint("p3", "P", "C", "C'", 0.5f)
            ),
            dynamicSection = DynamicSection3Points("M", "N", "P")
        )
        val builtCube = ShapeBuilder.build(snapCube)
        assertTrue("Dynamic section polygon should exist", (builtCube.dynamicSectionBorder?.size ?: 0) == 4)
        assertEquals(16.0f, builtCube.dynamicSectionArea ?: 0f, 0.05f)

        // 2. Oxyz Line-Plane Angle & Point-Plane Distance on S.ABCD with SA perpendicular to base (ABCD)
        val snapPyr = GeometrySnapshot(shape = ShapeType.PYRAMID4_SA, a = 4f, h = 6f, originPoint = "A")
        val builtPyr = ShapeBuilder.build(snapPyr)
        val reg = builtPyr.pointRegistry
        val origin = ShapeBuilder.getOriginWorld(reg, "A")

        val saPerp = Geo3DMathHelpers.analyzeLineAndPlane(
            lineAWorld = reg["S"]!!,
            lineBWorld = reg["A"]!!,
            p1World = reg["A"]!!,
            p2World = reg["B"]!!,
            p3World = reg["C"]!!,
            originWorld = origin
        )
        assertTrue(saPerp != null)
        assertEquals(90.0f, saPerp!!.angleDegrees, 0.1f)
        assertEquals(6.0f, saPerp.distA, 0.05f)
        assertEquals(0.0f, saPerp.distB, 0.05f)

        // 3. Space diagonal of cube makes arcsin(1/sqrt(3)) ≈ 35.264° with base
        val cReg = builtCube.pointRegistry
        val diagAnalysis = Geo3DMathHelpers.analyzeLineAndPlane(
            lineAWorld = cReg["A"]!!,
            lineBWorld = cReg["C'"]!!,
            p1World = cReg["A"]!!,
            p2World = cReg["B"]!!,
            p3World = cReg["C"]!!,
            originWorld = ShapeBuilder.getOriginWorld(cReg, "A")
        )
        assertTrue(diagAnalysis != null)
        assertEquals(35.264f, diagAnalysis!!.angleDegrees, 0.15f)
    }

    @Test
    fun `2d parameter m inflection points asymptotes and variation table work accurately`() {
        // 1. Parameter m evaluation & symbolic derivative
        val parsedM = MathExprParser.parse("x^3 - 3*m*x + 1") as MathExprParser.ParseResult.Ok
        assertTrue(parsedM.expr.containsVarM())
        assertEquals(1.0 - 6.0 + 8.0, parsedM.expr.eval(2.0, m = 1.0), 1e-6)
        assertEquals(8.0 - 12.0 + 1.0, parsedM.expr.eval(2.0, m = 2.0), 1e-6)

        val derivM = MathExprParser.derive("x^3 - 3*m*x + 1")!!
        // f'(x) = 3x^2 - 3m => at x=2, m=2 => 12 - 6 = 6
        assertEquals(6.0, derivM.first.eval(2.0, m = 2.0), 1e-6)

        // 2. Inflection point on y = x^3 - 3x is at (0, 0)
        val cubic = (MathExprParser.parse("x^3 - 3x") as MathExprParser.ParseResult.Ok).expr
        val keyPts = MathExprParser.analyzeFunction(cubic, -5.0, 5.0, -10.0, 10.0, "#38BDF8")
        val inflection = keyPts.firstOrNull { it.type == KeyPointType.INFLECTION }
        assertTrue("Cubic should detect inflection point", inflection != null)
        assertEquals(0.0, inflection!!.x, 0.05)
        assertEquals(0.0, inflection.y, 0.05)

        // 3. Asymptotes on (2x - 1)/(x - 1): vertical x=1, horizontal y=2
        val rational = (MathExprParser.parse("(2x - 1)/(x - 1)") as MathExprParser.ParseResult.Ok).expr
        val asym1 = MathExprParser.findAsymptotes(rational, -10.0, 10.0, -10.0, 10.0, "#FBBF24")
        assertTrue(asym1.filterIsInstance<GraphAsymptote.Vertical>().any { kotlin.math.abs(it.x - 1.0) < 0.08 })
        assertTrue(asym1.filterIsInstance<GraphAsymptote.Horizontal>().any { kotlin.math.abs(it.y - 2.0) < 0.08 })

        // 4. Oblique asymptote on (x^2 + 1)/x: vertical x=0, oblique y = x
        val obliqueFn = (MathExprParser.parse("(x^2 + 1)/x") as MathExprParser.ParseResult.Ok).expr
        val asym2 = MathExprParser.findAsymptotes(obliqueFn, -10.0, 10.0, -10.0, 10.0, "#FBBF24")
        assertTrue(asym2.filterIsInstance<GraphAsymptote.Vertical>().any { kotlin.math.abs(it.x) < 0.08 })
        assertTrue(asym2.filterIsInstance<GraphAsymptote.Oblique>().any { kotlin.math.abs(it.a - 1.0) < 0.08 && kotlin.math.abs(it.b) < 0.08 })

        // 5. Variation Table analysis for cubic and rational functions
        val bbtCubic = VariationTableEngine.analyze("x^3 - 3x", m = 0.0)
        assertTrue(bbtCubic != null)
        assertTrue(bbtCubic!!.extremaLines.size >= 2)
        assertEquals(3, bbtCubic.intervals.size) // (-inf,-1) +, (-1,1) -, (1,+inf) +

        val bbtRat = VariationTableEngine.analyze("(2x - 1)/(x - 1)", m = 0.0)
        assertTrue(bbtRat != null)
        assertTrue(bbtRat!!.nodes.any { it.kind == VariationTableEngine.NodeKind.DISCONTINUITY })
        assertTrue(bbtRat.asymptoteLines.size >= 2)
    }

    @Test
    fun `svg exporter and verify answer evaluator work accurately`() {
        // 1. 3D & 2D Vector SVG export
        val snap3D = GeometrySnapshot(shape = ShapeType.PYRAMID4_SA, a = 4f, h = 5f)
        val ui3D = GeometryUiState(snapshot = snap3D, scene = ShapeBuilder.build(snap3D))
        val svg3D = SvgExporter.export3DToSvg(1080, 1080, ui3D, transparentBg = true)
        assertTrue(svg3D.contains("<svg") && svg3D.contains("</svg>"))
        assertTrue(!svg3D.contains("url(#bg3d)"))

        val ui2D = Geo2DUiState(
            snapshot = Geo2DSnapshot(
                functions = listOf(GraphFunction("f1", "f1(x)", "x^2 - 2", "#38BDF8", true))
            )
        )
        val svg2D = SvgExporter.export2DToSvg(1080, 1080, ui2D, transparentBg = true)
        assertTrue(svg2D.contains("<svg") && svg2D.contains("<path"))

        // 2. Verify Answer Evaluator accepts formulas, Vietnamese comma decimals, and degree signs
        val vSqrt = com.example.ui.VerifyAnswerEvaluator.evaluate("sqrt(3)/2")!!
        assertTrue(com.example.ui.VerifyAnswerEvaluator.isCorrect(kotlin.math.sqrt(3.0) / 2.0, vSqrt))
        val vPi = com.example.ui.VerifyAnswerEvaluator.evaluate("4*pi/3")!!
        assertTrue(com.example.ui.VerifyAnswerEvaluator.isCorrect(4.0 * PI / 3.0, vPi))
        val vComma = com.example.ui.VerifyAnswerEvaluator.evaluate("35,26°")!!
        assertTrue(com.example.ui.VerifyAnswerEvaluator.isCorrect(35.264, vComma))

        // 3. Verify Answer Evaluator must reject free variables x or m
        assertEquals(null, com.example.ui.VerifyAnswerEvaluator.evaluate("x"))
        assertEquals(null, com.example.ui.VerifyAnswerEvaluator.evaluate("2*x + 1"))
        assertEquals(null, com.example.ui.VerifyAnswerEvaluator.evaluate("m"))
    }

    @Test
    fun `regression fixes for exp log variation table paramM bounds and svg round bodies`() {
        // 1. Exponential e^x and 2^x must report D = R, lim(-inf)=0, lim(+inf)=+inf (no IEEE overflow bug)
        val bbtExp = VariationTableEngine.analyze("e^x", m = 0.0)!!
        assertEquals("D = ℝ", bbtExp.domainText)
        assertEquals("0", bbtExp.nodes.first().yCenterLabel)
        assertEquals("+∞", bbtExp.nodes.last().yCenterLabel)

        val bbtPow2 = VariationTableEngine.analyze("2^x", m = 0.0)!!
        assertEquals("D = ℝ", bbtPow2.domainText)
        assertEquals("0", bbtPow2.nodes.first().yCenterLabel)
        assertEquals("+∞", bbtPow2.nodes.last().yCenterLabel)

        // 2. Natural log ln(x) must report D = (0; +inf), lim(0+)=-inf, lim(+inf)=+inf
        val bbtLn = VariationTableEngine.analyze("ln(x)", m = 0.0)!!
        assertEquals("D = (0; +∞)", bbtLn.domainText)
        assertEquals("+∞", bbtLn.nodes.last().yCenterLabel)
        val zeroNode = bbtLn.nodes.firstOrNull { kotlin.math.abs(it.x) < 0.05 }
        assertTrue("ln(x) should have boundary node at 0", zeroNode != null)
        assertEquals("-∞", zeroNode!!.yRightLabel)

        // 3. Inverted or equal paramMMin/paramMMax in imported JSON must be sanitized safely
        val badRangeJson = """{"paramM": 99, "paramMMin": 5, "paramMMax": 5}"""
        val safeSnap = Geo2DPersistence.jsonToSnapshot(badRangeJson)
        assertTrue(safeSnap.paramMMax > safeSnap.paramMMin)
        assertTrue(safeSnap.paramM in safeSnap.paramMMin..safeSnap.paramMMax)

        // 4. 3D SVG export includes round-body silhouettes and never emits NaN/Infinity
        listOf(ShapeType.CYLINDER, ShapeType.CONE, ShapeType.SPHERE).forEach { shape ->
            val snap = GeometrySnapshot(shape = shape, a = 4f, h = 5f)
            val ui = GeometryUiState(snapshot = snap, scene = ShapeBuilder.build(snap))
            val svg = SvgExporter.export3DToSvg(1080, 1080, ui, transparentBg = true)
            assertTrue("${shape.key} SVG must not contain NaN", !svg.contains("NaN"))
            assertTrue("${shape.key} SVG must not contain Infinity", !svg.contains("Infinity"))
            assertTrue("${shape.key} SVG should contain silhouette geometry", svg.contains("<line") || svg.contains("<polygon"))
        }

        // 5. 2D SVG export stays free of NaN/Infinity even under extreme zoom
        val extremeUi2D = Geo2DUiState(
            snapshot = Geo2DSnapshot(
                functions = listOf(GraphFunction("f1", "f1(x)", "e^x", "#38BDF8", true)),
                camera = Geo2DCamera(centerX = 0.0, centerY = 0.0, pixelsPerUnit = 1e6)
            )
        )
        val svg2D = SvgExporter.export2DToSvg(1080, 1080, extremeUi2D, transparentBg = true)
        assertTrue(!svg2D.contains("NaN"))
        assertTrue(!svg2D.contains("Infinity"))
    }

    @Test
    fun `github update checker parses releases normalizes repos and compares versions accurately`() {
        // 1. Repo slug normalization from raw slug or full GitHub URLs
        assertEquals("user/tool-toan", GitHubUpdateChecker.normalizeRepoSlug("user/tool-toan"))
        assertEquals("user/tool-toan", GitHubUpdateChecker.normalizeRepoSlug("https://github.com/user/tool-toan/releases/latest"))
        assertEquals("user/tool-toan", GitHubUpdateChecker.normalizeRepoSlug("github.com/user/tool-toan.git"))
        assertEquals(null, GitHubUpdateChecker.normalizeRepoSlug("not a valid repo"))

        // 2. Single-number (v1 -> v2 -> v10) and semantic version comparisons
        assertTrue(GitHubUpdateChecker.isNewerVersion("v2", "v1", 1))
        assertTrue(GitHubUpdateChecker.isNewerVersion("v10", "v9", 9))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v3", "v3", 3))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v2", "v3", 3))
        assertTrue(GitHubUpdateChecker.isNewerVersion("v1.2.0", "v1.1.9", 1))

        // 3. GitHub Releases API JSON parsing picks the tool-toan APK asset cleanly
        val sampleJson = """
            {
              "tag_name": "v5",
              "name": "Tool Toán v5",
              "body": "## Điểm mới\n- Thêm thông báo cập nhật GitHub",
              "html_url": "https://github.com/demo/tool-toan/releases/tag/v5",
              "published_at": "2026-11-20T12:30:00Z",
              "assets": [
                {
                  "name": "notes.txt",
                  "size": 1024,
                  "browser_download_url": "https://github.com/demo/tool-toan/releases/download/v5/notes.txt"
                },
                {
                  "name": "tool-toan-v5.apk",
                  "size": 15728640,
                  "browser_download_url": "https://github.com/demo/tool-toan/releases/download/v5/tool-toan-v5.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = GitHubUpdateChecker.parseReleaseJson(sampleJson)
        assertTrue(parsed != null)
        assertEquals("v5", parsed!!.tagName)
        assertEquals("Tool Toán v5", parsed.releaseName)
        assertEquals("tool-toan-v5.apk", parsed.apkAssetName)
        assertEquals("https://github.com/demo/tool-toan/releases/download/v5/tool-toan-v5.apk", parsed.preferredInstallUrl)
        assertEquals("15.0 MB", GitHubUpdateChecker.formatFileSize(parsed.apkSizeBytes))
        assertEquals("20/11/2026", GitHubUpdateChecker.formatPublishedDate(parsed.publishedAt))
    }

    @Test
    fun `theme palette resolver presets custom accent and preferences persistence work cleanly`() {
        // 1. All 8 curated presets (including Liquid Glass Light & Dark) resolve cleanly
        assertEquals(8, AppThemePreset.entries.size)
        AppThemePreset.entries.forEach { preset ->
            val mode = if (preset.isDark) AppThemeMode.DARK else AppThemeMode.LIGHT
            val palette = ThemePaletteResolver.resolve(AppThemeConfig(mode = mode, preset = preset), systemDark = true)
            assertEquals(preset.isDark, palette.isDark)
            assertEquals(!preset.isDark, palette.canvas.isLight)
        }

        // 2. System mode follows OS brightness
        val sysLight = ThemePaletteResolver.resolve(AppThemeConfig(mode = AppThemeMode.SYSTEM), systemDark = false)
        assertFalse(sysLight.isDark)
        assertTrue(sysLight.canvas.isLight)
        val sysDark = ThemePaletteResolver.resolve(AppThemeConfig(mode = AppThemeMode.SYSTEM), systemDark = true)
        assertTrue(sysDark.isDark)
        assertFalse(sysDark.canvas.isLight)

        // 3. Custom accent color & freeform Hue override primary and 3D face tint
        val roseAccent = 0xFFE11D48L
        val customPalette = ThemePaletteResolver.resolve(
            AppThemeConfig(mode = AppThemeMode.DARK, preset = AppThemePreset.MIDNIGHT_OBSERVATORY, customAccentArgb = roseAccent),
            systemDark = true
        )
        assertEquals(roseAccent.toInt(), customPalette.primary.hashCode().let { android.graphics.Color.argb(
            (customPalette.primary.alpha * 255).toInt(),
            (customPalette.primary.red * 255).toInt(),
            (customPalette.primary.green * 255).toInt(),
            (customPalette.primary.blue * 255).toInt()
        ) })

        // 4. SharedPreferences round-trip preserves theme mode, preset, and custom accent
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ThemePreferences(context)
        val savedCfg = AppThemeConfig(
            mode = AppThemeMode.LIGHT,
            preset = AppThemePreset.WARM_SAND_LIGHT,
            customAccentArgb = roseAccent
        )
        prefs.save(savedCfg)
        val loadedCfg = prefs.load()
        assertEquals(AppThemeMode.LIGHT, loadedCfg.mode)
        assertEquals(AppThemePreset.WARM_SAND_LIGHT, loadedCfg.preset)
        assertEquals(roseAccent, loadedCfg.customAccentArgb)
    }

    @Test
    fun `3d rotate and view mode normalizes dpi pauses autorotate clamps pan and switches presets`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.GeometryViewModel(app)

        // 1. DPI-normalized orbit: 100px at 1x density equals 300px at 3x density
        vm.resetCamera()
        val baseCam = vm.uiState.value.snapshot.camera
        vm.orbitCamera(deltaX = 100f, deltaY = 40f, density = 1f)
        val after1x = vm.uiState.value.snapshot.camera

        vm.resetCamera()
        vm.orbitCamera(deltaX = 300f, deltaY = 120f, density = 3f)
        val after3x = vm.uiState.value.snapshot.camera
        assertEquals(after1x.theta, after3x.theta, 1e-4f)
        assertEquals(after1x.phi, after3x.phi, 1e-4f)
        assertTrue("Dragging horizontally must rotate camera theta", kotlin.math.abs(after1x.theta - baseCam.theta) > 0.5f)

        // 2. Manual orbit automatically pauses autoRotate so it never fights user touch
        vm.toggleAutoRotate(true)
        assertTrue(vm.uiState.value.autoRotate)
        vm.orbitCamera(deltaX = 24f, deltaY = 0f, density = 2f)
        assertFalse("Manual orbit should auto-pause autoRotate", vm.uiState.value.autoRotate)

        // 3. Two-finger pan clamps camera target inside safe bounds
        vm.toggleAutoRotate(true)
        vm.zoomAndPanCamera(
            zoomFactor = 1f,
            panX = 50000f,
            panY = 50000f,
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            density = 2f
        )
        val clamped = vm.uiState.value.snapshot.camera.target
        assertTrue(clamped.x in -10f..10f)
        assertTrue(clamped.y in -4f..12f)
        assertTrue(clamped.z in -10f..10f)
        assertFalse("Manual zoom/pan should auto-pause autoRotate", vm.uiState.value.autoRotate)

        // 4. Quick CameraViewPreset switches angles cleanly
        vm.setCameraPreset(CameraViewPreset.TOP)
        assertEquals(0.10f, vm.uiState.value.snapshot.camera.phi, 0.01f)
        vm.setCameraPreset(CameraViewPreset.FRONT)
        assertEquals((PI / 2.0).toFloat() - 0.08f, vm.uiState.value.snapshot.camera.phi, 0.01f)
        vm.setCameraPreset(CameraViewPreset.SIDE)
        assertEquals((PI / 2.0).toFloat(), vm.uiState.value.snapshot.camera.theta, 0.01f)

        // 5. Dedicated App Settings workspace persists cleanly
        val wsPrefs = Geo2DPersistence(app)
        wsPrefs.saveLastWorkspace(MathWorkspace.SETTINGS)
        assertEquals(MathWorkspace.SETTINGS, wsPrefs.loadLastWorkspace())
    }

    @Test
    fun `liquid glass presets optical math viewmodel controls and persistence work cleanly`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = AppThemeViewModel(app)

        // 1. Selecting Liquid Glass Dark enables Liquid Glass and resolves glass palette
        vm.selectPreset(AppThemePreset.LIQUID_GLASS_DARK)
        assertTrue(vm.uiState.value.config.liquidGlass.enabled)
        assertEquals(AppThemeMode.DARK, vm.uiState.value.config.mode)

        // Switching to Light mode keeps Liquid Glass paired to LIQUID_GLASS_LIGHT
        vm.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemePreset.LIQUID_GLASS_LIGHT, vm.uiState.value.config.preset)
        assertTrue(vm.uiState.value.config.liquidGlass.enabled)

        // 2. Optical math: higher translucency lowers core alpha, highContrastText boosts alpha
        val crystalAlpha = LiquidGlassMath.coreAlpha(LiquidGlassPresetStyle.CRYSTAL.config, isDark = true, elevated = false)
        val softAlpha = LiquidGlassMath.coreAlpha(LiquidGlassPresetStyle.SOFT.config, isDark = true, elevated = false)
        assertTrue("Crystal preset should be more transparent than Soft Frost", crystalAlpha < softAlpha)

        // 3. Updating Liquid Glass GPU optical sliders clamps safely and persists across ThemePreferences reload
        vm.updateLiquidGlass(
            blurRadius = 24f,
            refraction = 0.86f,
            chromaticDispersion = 0.64f,
            translucency = 0.85f,
            specularGlow = 0.90f,
            refractionTint = 0.72f,
            borderIntensity = 0.88f,
            dynamicShimmer = false,
            highContrastText = true
        )
        val reloaded = ThemePreferences(app).load()
        assertTrue(reloaded.liquidGlass.enabled)
        assertEquals(24f, reloaded.liquidGlass.blurRadius, 1e-2f)
        assertEquals(0.86f, reloaded.liquidGlass.refraction, 1e-3f)
        assertEquals(0.64f, reloaded.liquidGlass.chromaticDispersion, 1e-3f)
        assertEquals(0.85f, reloaded.liquidGlass.translucency, 1e-3f)
        assertEquals(0.90f, reloaded.liquidGlass.specularGlow, 1e-3f)
        assertEquals(0.72f, reloaded.liquidGlass.refractionTint, 1e-3f)
        assertEquals(0.88f, reloaded.liquidGlass.borderIntensity, 1e-3f)
        assertFalse(reloaded.liquidGlass.dynamicShimmer)
        assertTrue(reloaded.liquidGlass.highContrastText)

        // Clamping out-of-range optical values stays safe
        val clamped = LiquidGlassConfig(
            enabled = true,
            blurRadius = 999f,
            refraction = -2f,
            chromaticDispersion = 5f
        ).sanitized()
        assertEquals(36f, clamped.blurRadius, 1e-3f)
        assertEquals(0f, clamped.refraction, 1e-3f)
        assertEquals(1f, clamped.chromaticDispersion, 1e-3f)
        assertTrue(LiquidGlassMath.LIQUID_GLASS_AGSL_SRC.contains("sdRoundedBox"))

        // 4. Liquid Glass can also be toggled cleanly on non-glass themes
        vm.selectPreset(AppThemePreset.VIOLET_NEBULA)
        assertFalse(vm.uiState.value.config.liquidGlass.enabled)
        vm.toggleLiquidGlass(true)
        assertTrue(vm.uiState.value.config.liquidGlass.enabled)
    }

    @Test
    fun `bundled 388 page dpad pdf toc search page lookup and preferences work cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. All 3 grades have complete DPAD topic tables with globally unique IDs and valid 1..388 page bounds
        val g10 = PdfCurriculumRepository.topicsFor(PdfGrade.GRADE_10)
        val g11 = PdfCurriculumRepository.topicsFor(PdfGrade.GRADE_11)
        val g12 = PdfCurriculumRepository.topicsFor(PdfGrade.GRADE_12)
        assertEquals(22, g10.size)
        assertEquals(19, g11.size)
        assertEquals(18, g12.size)
        assertEquals(59, PdfCurriculumRepository.allTopics.size)

        val allIds = PdfCurriculumRepository.allTopics.map { it.id }
        assertEquals("PDF topic IDs must be unique", allIds.size, allIds.toSet().size)
        PdfCurriculumRepository.allTopics.forEach { topic ->
            assertTrue("startPage valid for ${topic.id}", topic.startPage in 1..PDF_TOTAL_PAGES)
            assertTrue("endPage valid for ${topic.id}", topic.endPage in topic.startPage..PDF_TOTAL_PAGES)
            topic.subTopics.forEach { sub ->
                assertTrue("subtopic page valid for ${topic.id}", sub.page in topic.startPage..topic.endPage)
            }
        }

        // 2. Exact page-to-topic resolution matches the 388-page DPAD PDF structure
        assertEquals("g10_t01", PdfCurriculumRepository.topicForPage(18)?.id)
        assertEquals("g11_t01", PdfCurriculumRepository.topicForPage(121)?.id)
        assertEquals("g12_t07", PdfCurriculumRepository.topicForPage(314)?.id)
        assertEquals("g12_t18", PdfCurriculumRepository.topicForPage(386)?.id)
        assertEquals("Mục lục chi tiết PDF", PdfCurriculumRepository.sectionLabelForPage(5))

        // 3. Diacritic-insensitive search across all 3 grades jumps to accurate target pages
        val vietHits = PdfCurriculumRepository.searchTopics("vi-et")
        assertTrue("Should find Grade 10 Vi-ét", vietHits.any { it.topic.id == "g10_t05" && it.targetPage == 34 })

        val trigHits = PdfCurriculumRepository.searchTopics("luong giac")
        assertTrue("Should find Grade 11 Trigonometry", trigHits.any { it.topic.id == "g11_t01" })

        val integralHits = PdfCurriculumRepository.searchTopics("tung phan")
        assertTrue("Should find Grade 12 Integration by Parts", integralHits.any { it.topic.id == "g12_t07" && it.targetPage == 314 })

        val bayesHits = PdfCurriculumRepository.searchTopics("bayes")
        assertTrue("Should find Grade 12 Bayes on page 386", bayesHits.any { it.topic.id == "g12_t18" && it.targetPage == 386 })

        // 4. Bundled PDF asset exists and is readable
        context.assets.open(PDF_ASSET_PATH).use { stream ->
            val header = ByteArray(4)
            assertEquals(4, stream.read(header))
            assertEquals("%PDF", String(header, Charsets.US_ASCII))
        }

        // 5. PdfDocsPreferences persists last page, bookmarks, grade tab, and night filter cleanly
        val prefs = PdfDocsPreferences(context)
        prefs.saveLastPage(256)
        assertEquals(256, prefs.loadLastPage())

        prefs.saveBookmarks(emptySet())
        val afterAdd = prefs.toggleBookmark(121)
        assertTrue(121 in afterAdd)
        assertEquals(setOf(121), prefs.loadBookmarks())
        val afterRemove = prefs.toggleBookmark(121)
        assertFalse(121 in afterRemove)

        prefs.saveLastTab(PdfDocsTab.GRADE_11)
        assertEquals(PdfDocsTab.GRADE_11, prefs.loadLastTab())

        prefs.saveNightFilter(true)
        assertTrue(prefs.loadNightFilter())
    }

    @Test
    fun `ai key persistence docs qa parser and problem to 3d and 2d drawing builders work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. User API key & model preferences persist and mask cleanly
        val keyPrefs = AiKeyPreferences(context)
        keyPrefs.clearKey()
        assertFalse(keyPrefs.load().hasKey)

        keyPrefs.save(AiKeyConfig(apiKey = "AIzaSyDemoKey1234567890", modelId = AiModelPreset.GEMINI_4_ARGON.modelId))
        val loaded = keyPrefs.load()
        assertTrue(loaded.hasKey)
        assertEquals("AIza••••••7890", loaded.maskedKey)
        assertEquals(AiModelPreset.GEMINI_4_ARGON.modelId, loaded.effectiveModelId)
        keyPrefs.clearKey()
        assertFalse(keyPrefs.load().hasKey)

        // 2. Document Q&A structured JSON parsing handles markdown fences and bounds page numbers safely
        val rawDocsJson = """
            ```json
            {
              "answer": "Khoảng cách giữa 2 đường thẳng chéo nhau trong Oxyz dùng tích hỗn tạp.",
              "suggestedPages": [
                {"page": 376, "title": "Tổng hợp khoảng cách Oxyz", "reason": "Công thức khoảng cách 2 đường chéo nhau"},
                {"page": 999, "title": "Trang không hợp lệ", "reason": "Bị lọc bỏ"}
              ]
            }
            ```
        """.trimIndent()
        val docsReply = AiResponseParser.parseDocsReply(rawDocsJson)
        assertTrue(docsReply.answer.contains("Oxyz"))
        assertEquals(1, docsReply.suggestedPages.size)
        assertEquals(376, docsReply.suggestedPages.first().page)

        // 3. Problem-to-3D JSON converts to a real buildable 3D scene with edge points & dynamic cross section
        val raw3DJson = """
            {
              "target": "3D",
              "title": "Chóp S.ABCD có SA vuông đáy & thiết diện AMN",
              "problemText": "Cho hình chóp S.ABCD đáy vuông cạnh 4, SA vuông góc đáy, SA = 5. M, N là trung điểm SC, SD.",
              "drawingSummary": "Dựng chóp S.ABCD, trung điểm M trên SC, N trên SD và thiết diện (AMN).",
              "solutionHint": "V = 1/3 * S_đáy * SA",
              "scene3D": {
                "shapeKey": "pyramid4_sa",
                "a": 4.0,
                "h": 5.0,
                "showAxes": true,
                "originPoint": "A",
                "edgePoints": [
                  {"name": "M", "from": "S", "to": "C", "ratio": 0.5},
                  {"name": "N", "from": "S", "to": "D", "ratio": 0.5}
                ],
                "lines": [
                  {"p1": "A", "p2": "M", "style": "solid", "colorHex": "#F43F5E"}
                ],
                "planes": [["A", "M", "N"]],
                "dynamicSection": {"p1": "A", "p2": "M", "p3": "N"}
              },
              "relatedPages": [{"page": 235, "title": "Thể tích đa diện", "reason": "Thể tích khối chóp"}]
            }
        """.trimIndent()
        val plan3D = AiResponseParser.parseDrawingPlan(raw3DJson)
        assertTrue(plan3D != null && plan3D.target == AiDrawTarget.SPACE_3D)
        val snap3D = AiResponseParser.buildGeometrySnapshot(plan3D!!.scene3D!!)
        assertEquals(ShapeType.PYRAMID4_SA, snap3D.shape)
        assertTrue(snap3D.showAxes)
        assertEquals(2, snap3D.customPoints.size)
        assertEquals(1, snap3D.customLines.size)
        assertTrue(snap3D.dynamicSection != null)
        val built3D = ShapeBuilder.build(snap3D)
        assertTrue("AI-built 3D pyramid must have valid positive volume", built3D.metrics.vol > 0f)
        assertTrue("AI-built 3D scene must contain M and N vertices", "M" in built3D.pointRegistry && "N" in built3D.pointRegistry)

        // 4. Problem-to-2D JSON converts to real Oxy points, polygon, circle, and validated graph function
        val raw2DJson = """
            {
              "target": "2D",
              "title": "Tam giác ABC, đường tròn tâm I & đồ thị bậc 3",
              "problemText": "Cho A(0;0), B(4;0), C(1;3), đường tròn tâm I(2;1) R=2 và đồ thị y = x^3 - 3x + 1.",
              "drawingSummary": "Dựng tam giác ABC, đường tròn (I; 2) và đồ thị y = x^3 - 3x + 1.",
              "solutionHint": "Tính đạo hàm y' = 3x^2 - 3.",
              "scene2D": {
                "points": [
                  {"name": "A", "x": 0, "y": 0},
                  {"name": "B", "x": 4, "y": 0},
                  {"name": "C", "x": 1, "y": 3},
                  {"name": "I", "x": 2, "y": 1}
                ],
                "objects": [
                  {"kind": "polygon", "pointNames": ["A", "B", "C"]},
                  {"kind": "circle", "p1": "I", "radius": 2.0}
                ],
                "functions": [
                  {"expression": "y = x^3 - 3x + 1", "showDerivative": true}
                ]
              }
            }
        """.trimIndent()
        val plan2D = AiResponseParser.parseDrawingPlan(raw2DJson)
        assertTrue(plan2D != null && plan2D.target == AiDrawTarget.PLANE_2D)
        val snap2D = AiResponseParser.buildGeo2DSnapshot(plan2D!!.scene2D!!)
        assertEquals(4, snap2D.points.size)
        assertEquals(2, snap2D.objects.size)
        assertEquals(1, snap2D.functions.size)
        assertEquals("x^3 - 3x + 1", snap2D.functions.first().expression)
        assertTrue(snap2D.functions.first().showDerivative)
    }

    @Test
    fun `google sign in unlocks keyless gemini readiness and formats user profile cleanly`() {
        // 1. Neither Google user nor API key -> not ready
        val disconnected = AiAssistantUiState()
        assertFalse(disconnected.isSignedInWithGoogle)
        assertFalse(disconnected.isAiReady)
        assertTrue(disconnected.activeProviderLabel.contains("Chưa đăng nhập"))

        // 2. Signed in with Google and NO API key -> ready immediately with keyless Google Gemini
        val profile = GoogleUserProfile(
            uid = "uid_google_123",
            displayName = "Nguyen Van An",
            email = "an.nguyen@gmail.com"
        )
        assertEquals("NA", profile.initials)

        val googleReady = AiAssistantUiState(
            googleUser = profile,
            keyConfig = AiKeyConfig(apiKey = "")
        )
        assertTrue(googleReady.isSignedInWithGoogle)
        assertTrue(googleReady.isAiReady)
        assertTrue(googleReady.activeProviderLabel.contains("Không cần API Key"))

        // 3. Fallback API key without Google Sign-In -> still ready
        val keyOnlyReady = AiAssistantUiState(
            googleUser = null,
            keyConfig = AiKeyConfig(apiKey = "AIzaSyManualFallbackKey9999")
        )
        assertFalse(keyOnlyReady.isSignedInWithGoogle)
        assertTrue(keyOnlyReady.isAiReady)
        assertTrue(keyOnlyReady.activeProviderLabel.contains("API Key riêng"))
    }
}



