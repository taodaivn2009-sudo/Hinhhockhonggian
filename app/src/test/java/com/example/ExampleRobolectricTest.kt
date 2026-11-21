package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeometryPersistence
import com.example.docs.TrigDocCategory
import com.example.docs.TrigReferenceRepository
import com.example.engine.*
import com.example.geo2d.*
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
    fun `trigonometry documents repository has complete poster formulas and accurate converters`() {
        // 1. All 10 formula groups, 16 unit-circle angles, and 4 quadrant sign rows exist
        assertEquals(10, TrigReferenceRepository.formulaGroups.size)
        assertEquals(16, TrigReferenceRepository.specialCircleAngles.size)
        assertEquals(4, TrigReferenceRepository.quadrantSignRows.size)

        // 2. Category and search filtering work accurately
        val specialOnly = TrigReferenceRepository.filterGroups(TrigDocCategory.SPECIAL_ANGLES, "")
        assertEquals(4, specialOnly.size)

        val searchDouble = TrigReferenceRepository.filterGroups(TrigDocCategory.ALL, "nhân đôi")
        assertTrue("Search 'nhân đôi' should match double-angle group", searchDouble.any { it.id == "double_triple" })

        val searchCosDiff = TrigReferenceRepository.filterGroups(TrigDocCategory.ALL, "cos u - cos v")
        assertTrue("Search should match sum-to-product group", searchCosDiff.any { it.id == "sum_to_product" })

        // 3. Degree <-> Radian conversion & exact pi-fraction formatting
        assertEquals(PI, TrigReferenceRepository.degreesToRadians(180.0), 1e-9)
        assertEquals(180.0, TrigReferenceRepository.radiansToDegrees(PI), 1e-9)
        assertEquals("5π/6", TrigReferenceRepository.formatDegreesAsPiFraction(150.0))
        assertEquals("π/2", TrigReferenceRepository.formatDegreesAsPiFraction(90.0))
        assertEquals("0", TrigReferenceRepository.formatDegreesAsPiFraction(0.0))
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
}
