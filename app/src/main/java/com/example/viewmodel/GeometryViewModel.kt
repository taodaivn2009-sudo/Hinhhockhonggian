package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeometryPersistence
import com.example.engine.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.round

class GeometryViewModel(application: Application) : AndroidViewModel(application) {

    private val persistence = GeometryPersistence(application.applicationContext)

    private val _uiState = MutableStateFlow(GeometryUiState())
    val uiState: StateFlow<GeometryUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<GeometrySnapshot>()
    private val redoStack = ArrayDeque<GeometrySnapshot>()
    private val maxHistory = 40
    private var toastJob: Job? = null

    private data class ActiveDrag(
        val name: String,
        val type: DragPointType,
        val lockedRail: LockedRail?,
        var startWorldPos: Vec3,
        val anchorScreenX: Float,
        val anchorScreenY: Float
    )

    private enum class DragPointType { EDGE, XYZ, BASE }
    private data class LockedRail(val from: String, val to: String, val v1: Vec3, val v2: Vec3)

    private var activeDrag: ActiveDrag? = null

    init {
        val slots = persistence.loadNamedSlots()
        val restored = persistence.loadAutoSession()
        if (restored != null) {
            val scene = ShapeBuilder.build(restored)
            val pts = scene.pointRegistry.keys.toList()
            _uiState.value = _uiState.value.copy(
                snapshot = restored,
                scene = scene,
                savedSlots = slots,
                openCategories = setOf(restored.shape.category),
                calcPointA = pts.getOrElse(0) { "S" },
                calcPointB = pts.getOrElse(1) { pts.firstOrNull() ?: "A" }
            )
            showToast("Đã khôi phục bài vẽ lần trước!")
        } else {
            val initSnap = GeometrySnapshot()
            val scene = ShapeBuilder.build(initSnap)
            _uiState.value = _uiState.value.copy(
                snapshot = initSnap,
                scene = scene,
                savedSlots = slots
            )
        }
    }

    // ================= HISTORY & SNAPSHOT APPLICATION =================
    private fun pushHistory(snap: GeometrySnapshot = _uiState.value.snapshot) {
        if (undoStack.lastOrNull() == snap) return
        undoStack.addLast(snap)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun updateSnapshot(
        recordHistory: Boolean = false,
        autoSave: Boolean = true,
        transform: (GeometrySnapshot) -> GeometrySnapshot
    ) {
        val current = _uiState.value.snapshot
        if (recordHistory) pushHistory(current)
        var next = transform(current)
        val built = ShapeBuilder.build(next)
        if (!built.pointRegistry.containsKey(next.originPoint)) {
            val fallback = built.pointRegistry.keys.firstOrNull() ?: "O"
            next = next.copy(originPoint = fallback)
        }
        val finalScene = ShapeBuilder.build(next)
        val keys = finalScene.pointRegistry.keys.toList()

        _uiState.update { st ->
            st.copy(
                snapshot = next,
                scene = finalScene,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                calcPointA = if (st.calcPointA in finalScene.pointRegistry) st.calcPointA else keys.getOrElse(0) { "S" },
                calcPointB = if (st.calcPointB in finalScene.pointRegistry) st.calcPointB else keys.getOrElse(1) { keys.firstOrNull() ?: "A" }
            )
        }
        if (autoSave) persistence.saveAutoSession(next)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_uiState.value.snapshot)
        val prev = undoStack.removeLast()
        val scene = ShapeBuilder.build(prev)
        _uiState.update { st ->
            st.copy(
                snapshot = prev,
                scene = scene,
                selectedFirstPoint = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        persistence.saveAutoSession(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_uiState.value.snapshot)
        val next = redoStack.removeLast()
        val scene = ShapeBuilder.build(next)
        _uiState.update { st ->
            st.copy(
                snapshot = next,
                scene = scene,
                selectedFirstPoint = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        persistence.saveAutoSession(next)
    }

    // ================= TOAST & UI PANELS =================
    fun showToast(msg: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = msg) }
        toastJob = viewModelScope.launch {
            delay(2400)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun toggleLeftPane(pane: LeftPane) {
        _uiState.update { st ->
            st.copy(
                activeLeftPane = if (st.activeLeftPane == pane) null else pane,
                sensitivityPopoverOpen = false
            )
        }
    }

    fun closeLeftPane() {
        _uiState.update { it.copy(activeLeftPane = null) }
    }

    fun toggleSensitivityPopover() {
        _uiState.update { st ->
            st.copy(
                sensitivityPopoverOpen = !st.sensitivityPopoverOpen,
                activeLeftPane = if (!st.sensitivityPopoverOpen) null else st.activeLeftPane
            )
        }
    }

    fun closeSensitivityPopover() {
        _uiState.update { it.copy(sensitivityPopoverOpen = false) }
    }

    fun updateSensitivity(
        rotation: Float? = null,
        zoom: Float? = null,
        drag: Float? = null,
        touchRadius: Float? = null
    ) {
        updateSnapshot(autoSave = false) { snap ->
            val cur = snap.sensitivity
            snap.copy(
                sensitivity = cur.copy(
                    rotation = (rotation ?: cur.rotation).coerceIn(0.2f, 2.5f),
                    zoom = (zoom ?: cur.zoom).coerceIn(0.3f, 2.5f),
                    drag = (drag ?: cur.drag).coerceIn(0.2f, 2.0f),
                    touchRadius = (touchRadius ?: cur.touchRadius).coerceIn(0.6f, 1.8f)
                )
            )
        }
    }

    fun applySensitivityPreset(preset: String) {
        val next = when (preset) {
            "precise" -> SensitivityConfig(rotation = 0.5f, zoom = 0.7f, drag = 0.5f, touchRadius = 1.25f)
            "fast" -> SensitivityConfig(rotation = 1.7f, zoom = 1.5f, drag = 1.35f, touchRadius = 1.1f)
            else -> SensitivityConfig(rotation = 1.0f, zoom = 1.0f, drag = 1.0f, touchRadius = 1.0f)
        }
        updateSnapshot { it.copy(sensitivity = next) }
        val label = when (preset) {
            "precise" -> "Chính xác (Chậm & êm)"
            "fast" -> "Nhanh & linh hoạt"
            else -> "Cân bằng (Mặc định)"
        }
        showToast("Độ nhạy: $label")
    }

    fun toggleShapeCategory(category: ShapeCategory) {
        _uiState.update { st ->
            val next = st.openCategories.toMutableSet()
            if (!next.add(category)) next.remove(category)
            st.copy(openCategories = next)
        }
    }

    fun toggleBottomDockCollapse() {
        _uiState.update { st ->
            val collapsed = !st.bottomDockCollapsed
            st.copy(
                bottomDockCollapsed = collapsed,
                dragSubdrawerOpen = if (collapsed) false else (st.interactionMode == InteractionMode.DRAG_POINT)
            )
        }
    }

    fun setInteractionMode(mode: InteractionMode) {
        _uiState.update { st ->
            if (mode == InteractionMode.DRAG_POINT && st.interactionMode == InteractionMode.DRAG_POINT) {
                return@update st.copy(dragSubdrawerOpen = !st.dragSubdrawerOpen)
            }
            activeDrag = null
            st.copy(
                interactionMode = mode,
                selectedFirstPoint = null,
                draggingPointName = null,
                linkedPartners = emptySet(),
                dragSubdrawerOpen = mode == InteractionMode.DRAG_POINT,
                previewDot = null,
                snapVisual = SnapVisualState(),
                constraintRay = null,
                tooltip = null
            )
        }
    }

    fun setDragConstraint(constraint: DragConstraintMode) {
        _uiState.update { it.copy(dragConstraint = constraint) }
    }

    fun toggleKeepRelations() {
        updateSnapshot { it.copy(keepRelations = !it.keepRelations) }
    }

    fun toggleSnapEnabled() {
        updateSnapshot { it.copy(snapEnabled = !it.snapEnabled) }
        if (!_uiState.value.snapshot.snapEnabled) {
            _uiState.update { it.copy(snapVisual = SnapVisualState()) }
        }
    }

    fun toggleAxes() {
        updateSnapshot { it.copy(showAxes = !it.showAxes) }
    }

    fun changeOrigin(pointName: String) {
        updateSnapshot(recordHistory = true) {
            it.copy(originPoint = pointName, showAxes = true)
        }
    }

    fun setShowCoordsOnLabels(show: Boolean) {
        updateSnapshot { it.copy(showCoordsOnLabels = show) }
    }

    fun setCalcPoints(pointA: String, pointB: String) {
        _uiState.update { it.copy(calcPointA = pointA, calcPointB = pointB) }
    }

    // ================= SHAPE & PARAMETERS =================
    fun selectShape(shape: ShapeType) {
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                shape = shape,
                originPoint = shape.defaultOrigin,
                vertexOverrides = emptyMap(),
                customPoints = emptyList(),
                customXYZPoints = emptyList(),
                customLines = emptyList(),
                customPlanes = emptyList()
            )
        }
        _uiState.update { st ->
            st.copy(
                selectedFirstPoint = null,
                linkedPartners = emptySet(),
                openCategories = st.openCategories + shape.category
            )
        }
    }

    fun recordSliderDragStart() {
        pushHistory()
    }

    fun updateParamA(value: Float) {
        val baseKeys = setOf("A", "B", "C", "D", "E", "F", "A'", "B'", "C'", "D'", "E'", "F'")
        updateSnapshot(autoSave = false) { snap ->
            snap.copy(
                a = value,
                vertexOverrides = snap.vertexOverrides.filterKeys { it !in baseKeys }
            )
        }
    }

    fun updateParamB(value: Float) {
        val baseKeys = setOf("A", "B", "C", "D", "A'", "B'", "C'", "D'")
        updateSnapshot(autoSave = false) { snap ->
            snap.copy(
                b = value,
                vertexOverrides = snap.vertexOverrides.filterKeys { it !in baseKeys }
            )
        }
    }

    fun updateParamH(value: Float) {
        val topKeys = setOf("S", "O'", "A'", "B'", "C'", "D'", "E'", "F'")
        updateSnapshot(autoSave = false) { snap ->
            val updated = snap.vertexOverrides.mapValues { (k, v) ->
                if (k in topKeys) Vec3(v.x, value, v.z) else v
            }
            snap.copy(h = value, vertexOverrides = updated)
        }
    }

    fun updateSectionRatio(value: Float) {
        updateSnapshot(autoSave = false) { it.copy(secRatio = value) }
    }

    fun commitSliderChange() {
        persistence.saveAutoSession(_uiState.value.snapshot)
    }

    fun toggleGrid(v: Boolean) = updateSnapshot { it.copy(showGrid = v) }
    fun toggleDashed(v: Boolean) = updateSnapshot { it.copy(dashed = v) }
    fun toggleLabels(v: Boolean) = updateSnapshot { it.copy(labels = v) }
    fun toggleAltitude(v: Boolean) = updateSnapshot { it.copy(altitude = v) }
    fun toggleFaces(v: Boolean) = updateSnapshot { it.copy(faces = v) }
    fun toggleSection(v: Boolean) = updateSnapshot { it.copy(showSection = v) }
    fun toggleAutoRotate(v: Boolean) { _uiState.update { it.copy(autoRotate = v) } }

    fun resetMovedVertices() {
        updateSnapshot(recordHistory = true) { it.copy(vertexOverrides = emptyMap()) }
        _uiState.update { it.copy(linkedPartners = emptySet()) }
        showToast("Đã đưa các đỉnh về vị trí chuẩn!")
    }

    // ================= CAMERA CONTROLS =================
    fun orbitCamera(deltaX: Float, deltaY: Float) {
        _uiState.update { st ->
            val cam = st.snapshot.camera
            val sens = st.snapshot.sensitivity.rotation
            val nextCam = cam.copy(
                theta = cam.theta - deltaX * 0.0085f * sens,
                phi = (cam.phi - deltaY * 0.0085f * sens).coerceIn(0.12f, PI.toFloat() - 0.12f)
            )
            st.copy(snapshot = st.snapshot.copy(camera = nextCam))
        }
    }

    fun zoomAndPanCamera(zoomFactor: Float, panX: Float, panY: Float, viewportHeight: Float) {
        _uiState.update { st ->
            val cam = st.snapshot.camera
            val zoomSens = st.snapshot.sensitivity.zoom
            val scaledZoom = (1f + (zoomFactor - 1f) * zoomSens).coerceIn(0.55f, 1.85f)
            val nextRadius = (cam.radius / scaledZoom).coerceIn(3.5f, 28f)
            val proj = CameraProjection(cam.cameraPosition(), cam.target, viewportHeight, viewportHeight)
            val panScale = nextRadius * 0.0014f * zoomSens
            val nextTarget = cam.target - proj.right * (panX * panScale) + proj.up * (panY * panScale)
            val nextCam = cam.copy(radius = nextRadius, target = nextTarget)
            st.copy(snapshot = st.snapshot.copy(camera = nextCam))
        }
    }

    fun stepZoom(zoomIn: Boolean) {
        updateSnapshot { snap ->
            val factor = if (zoomIn) (1f + 0.18f * snap.sensitivity.zoom) else 1f / (1f + 0.18f * snap.sensitivity.zoom)
            val nextRadius = (snap.camera.radius / factor).coerceIn(3.5f, 28f)
            snap.copy(camera = snap.camera.copy(radius = nextRadius))
        }
    }

    fun tickAutoRotate(deltaSec: Float) {
        if (!_uiState.value.autoRotate) return
        _uiState.update { st ->
            val cam = st.snapshot.camera
            val rotSens = st.snapshot.sensitivity.rotation
            st.copy(snapshot = st.snapshot.copy(camera = cam.copy(theta = cam.theta + deltaSec * 0.55f * rotSens)))
        }
    }

    fun commitCameraGesture() {
        persistence.saveAutoSession(_uiState.value.snapshot)
    }

    fun resetCamera() {
        updateSnapshot { it.copy(camera = CameraOrbitState.DEFAULT) }
    }

    // ================= CUSTOM ELEMENTS (DRAW & COORDS) =================
    fun addCustomEdgePoint(rawName: String, from: String, to: String, ratio: Float): String? {
        val name = rawName.trim()
        if (name.isEmpty()) return "Vui lòng nhập tên điểm!"
        if (from == to) return "Điểm đầu và điểm cuối phải khác nhau!"
        if (_uiState.value.scene.pointRegistry.containsKey(name)) return "Điểm \"$name\" đã tồn tại trên hình!"
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                customPoints = snap.customPoints + CustomEdgePoint(
                    id = "pt_${System.currentTimeMillis()}",
                    name = name,
                    from = from,
                    to = to,
                    ratio = ratio
                )
            )
        }
        showToast("Đã thêm điểm $name trên $from$to")
        return null
    }

    fun addCustomXYZPoint(rawName: String, x: Float, y: Float, z: Float): String? {
        val name = rawName.trim()
        if (name.isEmpty()) return "Vui lòng nhập tên điểm!"
        if (_uiState.value.scene.pointRegistry.containsKey(name)) return "Điểm \"$name\" đã tồn tại trên hình!"
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                showAxes = true,
                customXYZPoints = snap.customXYZPoints + CustomXYZPoint(
                    id = "xyz_${System.currentTimeMillis()}",
                    name = name,
                    x = x,
                    y = y,
                    z = z
                )
            )
        }
        showToast("Đã chấm điểm $name(${formatMathNumber(x)}; ${formatMathNumber(y)}; ${formatMathNumber(z)})")
        return null
    }

    fun addCustomLine(p1: String, p2: String, style: LineStyle, colorHex: String): String? {
        if (p1 == p2) return "Vui lòng chọn 2 điểm khác nhau!"
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                customLines = snap.customLines + CustomLine(
                    id = "ln_${System.currentTimeMillis()}",
                    p1 = p1,
                    p2 = p2,
                    style = style,
                    colorHex = colorHex
                )
            )
        }
        val label = if (style.isVector) "vectơ $p1$p2→" else "đoạn $p1$p2"
        showToast("Đã vẽ $label")
        return null
    }

    fun addCustomPlane(p1: String, p2: String, p3: String, p4: String?): String? {
        val pts = listOfNotNull(p1, p2, p3, p4?.takeIf { it.isNotBlank() }).distinct()
        if (pts.size < 3) return "Vui lòng chọn ít nhất 3 điểm phân biệt!"
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                customPlanes = snap.customPlanes + CustomPlane(
                    id = "pl_${System.currentTimeMillis()}",
                    pts = pts
                )
            )
        }
        showToast("Đã tô mặt phẳng (${pts.joinToString("")})")
        return null
    }

    fun removeCustomPoint(id: String) = updateSnapshot(recordHistory = true) { s -> s.copy(customPoints = s.customPoints.filterNot { it.id == id }) }
    fun removeCustomXYZ(id: String) = updateSnapshot(recordHistory = true) { s -> s.copy(customXYZPoints = s.customXYZPoints.filterNot { it.id == id }) }
    fun removeCustomLine(id: String) = updateSnapshot(recordHistory = true) { s -> s.copy(customLines = s.customLines.filterNot { it.id == id }) }
    fun removeCustomPlane(id: String) = updateSnapshot(recordHistory = true) { s -> s.copy(customPlanes = s.customPlanes.filterNot { it.id == id }) }

    fun clearAllCustom() {
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                customPoints = emptyList(),
                customXYZPoints = emptyList(),
                customLines = emptyList(),
                customPlanes = emptyList(),
                vertexOverrides = emptyMap()
            )
        }
        _uiState.update { it.copy(selectedFirstPoint = null, linkedPartners = emptySet()) }
        showToast("Đã xóa toàn bộ yếu tố dựng thêm")
    }

    // ================= DIRECT 3D CANVAS TOUCH INTERACTIONS =================
    private fun makeProjection(width: Float, height: Float): CameraProjection {
        val cam = _uiState.value.snapshot.camera
        return CameraProjection(cam.cameraPosition(), cam.target, width, height)
    }

    private fun findNearestVertex2D(sx: Float, sy: Float, proj: CameraProjection, thresholdPx: Float): String? {
        var best: String? = null
        var minDist = thresholdPx
        _uiState.value.scene.pointRegistry.forEach { (name, pos) ->
            val p = proj.project(pos)
            if (!p.visible) return@forEach
            val d = hypot(sx - p.x, sy - p.y)
            if (d < minDist) {
                minDist = d
                best = name
            }
        }
        return best
    }

    private data class EdgeHit(val from: String, val to: String, val ratio: Float, val pos3D: Vec3, val snapLabel: String, val v1: Vec3, val v2: Vec3)

    private fun projectOntoSegment(sx: Float, sy: Float, rail: LockedRail, proj: CameraProjection, snapEnabled: Boolean): EdgeHit? {
        val a = proj.project(rail.v1)
        val b = proj.project(rail.v2)
        if (!a.visible || !b.visible) return null
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1f) return null
        var t = (((sx - a.x) * dx + (sy - a.y) * dy) / len2).coerceIn(0.02f, 0.98f)
        var snapLabel = ""
        if (snapEnabled) {
            for (sp in ShapeBuilder.SPECIAL_EDGE_RATIOS) {
                if (kotlin.math.abs(t - sp.value) < 0.055f) {
                    t = sp.value
                    snapLabel = "Hút: ${sp.label}"
                    break
                }
            }
        }
        return EdgeHit(rail.from, rail.to, t, Vec3.lerp(rail.v1, rail.v2, t), snapLabel, rail.v1, rail.v2)
    }

    private fun findNearestEdgeHit2D(sx: Float, sy: Float, proj: CameraProjection, thresholdPx: Float): EdgeHit? {
        val st = _uiState.value
        val reg = st.scene.pointRegistry
        var best: EdgeHit? = null
        var minDist = thresholdPx
        for (seg in st.scene.clickableSegments) {
            val v1 = reg[seg.n1] ?: continue
            val v2 = reg[seg.n2] ?: continue
            val a = proj.project(v1)
            val b = proj.project(v2)
            if (!a.visible || !b.visible) continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            if (len2 < 1f) continue
            var t = (((sx - a.x) * dx + (sy - a.y) * dy) / len2).coerceIn(0.05f, 0.95f)
            var snapLabel = ""
            if (st.snapshot.snapEnabled) {
                for (sp in ShapeBuilder.SPECIAL_EDGE_RATIOS) {
                    if (kotlin.math.abs(t - sp.value) < 0.055f) {
                        t = sp.value
                        snapLabel = "Hút: ${sp.label}"
                        break
                    }
                }
            }
            val px = a.x + t * dx
            val py = a.y + t * dy
            val dist = hypot(sx - px, sy - py)
            if (dist < minDist) {
                minDist = dist
                best = EdgeHit(seg.n1, seg.n2, t, Vec3.lerp(v1, v2, t), snapLabel, v1, v2)
            }
        }
        return best
    }

    private fun findBestRailForPoint(targetName: String): LockedRail? {
        val st = _uiState.value
        val reg = st.scene.pointRegistry
        val edgePt = st.snapshot.customPoints.firstOrNull { it.name == targetName }
        if (edgePt != null) {
            val v1 = reg[edgePt.from]
            val v2 = reg[edgePt.to]
            if (v1 != null && v2 != null) return LockedRail(edgePt.from, edgePt.to, v1, v2)
        }
        val ptPos = reg[targetName] ?: return null
        var best: LockedRail? = null
        var minDist = Float.MAX_VALUE
        for (seg in st.scene.clickableSegments) {
            if (seg.n1 == targetName || seg.n2 == targetName) continue
            val v1 = reg[seg.n1] ?: continue
            val v2 = reg[seg.n2] ?: continue
            val closest = Vec3.closestPointOnSegment(ptPos, v1, v2)
            val d = ptPos.distanceTo(closest)
            if (d < minDist) {
                minDist = d
                best = LockedRail(seg.n1, seg.n2, v1, v2)
            }
        }
        return best
    }

    fun onCanvasTap(sx: Float, sy: Float, width: Float, height: Float, density: Float) {
        val st = _uiState.value
        val proj = makeProjection(width, height)
        val hitThreshold = 40f * density * st.snapshot.sensitivity.touchRadius

        when (st.interactionMode) {
            InteractionMode.VIEW -> {
                val tapped = findNearestVertex2D(sx, sy, proj, hitThreshold)
                if (tapped != null) {
                    val pos = st.scene.pointRegistry[tapped] ?: return
                    val origin = ShapeBuilder.getOriginWorld(st.scene.pointRegistry, st.snapshot.originPoint)
                    val m = ShapeBuilder.worldToMath(pos, origin)
                    _uiState.update {
                        it.copy(
                            tooltip = HoverTooltip(
                                screenX = sx,
                                screenY = sy,
                                title = "Điểm $tapped (${formatMathNumber(m.x)}; ${formatMathNumber(m.y)}; ${formatMathNumber(m.z)})",
                                subtitle = "Gốc ${st.snapshot.originPoint}"
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(tooltip = null) }
                }
            }
            InteractionMode.ADD_POINT -> {
                val hit = findNearestEdgeHit2D(sx, sy, proj, hitThreshold) ?: return
                val nextName = ShapeBuilder.nextAutoPointName(st.scene.pointRegistry)
                addCustomEdgePoint(nextName, hit.from, hit.to, hit.ratio)
                _uiState.update { it.copy(previewDot = null, snapVisual = SnapVisualState(), tooltip = null) }
            }
            InteractionMode.DRAW_DASHED, InteractionMode.DRAW_SOLID, InteractionMode.DRAW_VECTOR -> {
                val clicked = findNearestVertex2D(sx, sy, proj, hitThreshold) ?: return
                val first = st.selectedFirstPoint
                if (first == null) {
                    _uiState.update { it.copy(selectedFirstPoint = clicked) }
                } else if (first == clicked) {
                    _uiState.update { it.copy(selectedFirstPoint = null) }
                } else {
                    val (style, color) = when (st.interactionMode) {
                        InteractionMode.DRAW_DASHED -> LineStyle.DASHED to "#38bdf8"
                        InteractionMode.DRAW_SOLID -> LineStyle.SOLID to "#10b981"
                        else -> LineStyle.VECTOR_SOLID to "#f43f5e"
                    }
                    addCustomLine(first, clicked, style, color)
                    _uiState.update { it.copy(selectedFirstPoint = null) }
                }
            }
            InteractionMode.DRAG_POINT -> Unit
        }
    }

    fun onDragStart(sx: Float, sy: Float, width: Float, height: Float, density: Float): Boolean {
        val st = _uiState.value
        val proj = makeProjection(width, height)
        val hitThreshold = 42f * density * st.snapshot.sensitivity.touchRadius

        if (st.interactionMode == InteractionMode.ADD_POINT) {
            val hit = findNearestEdgeHit2D(sx, sy, proj, hitThreshold)
            if (hit != null) {
                _uiState.update {
                    it.copy(
                        previewDot = hit.pos3D,
                        snapVisual = if (hit.snapLabel.isNotEmpty()) SnapVisualState(hit.pos3D, hit.snapLabel) else SnapVisualState(),
                        tooltip = HoverTooltip(sx, sy, "Chấm điểm trên ${hit.from}${hit.to} (k = ${String.format(Locale.US, "%.2f", hit.ratio)})", snapBadge = hit.snapLabel)
                    )
                }
                return true
            }
            return false
        }

        if (st.interactionMode != InteractionMode.DRAG_POINT) return false
        val targetName = findNearestVertex2D(sx, sy, proj, hitThreshold) ?: return false
        val startPos = st.scene.pointRegistry[targetName] ?: return false

        pushHistory()
        val type = when {
            st.snapshot.customPoints.any { it.name == targetName } -> DragPointType.EDGE
            st.snapshot.customXYZPoints.any { it.name == targetName } -> DragPointType.XYZ
            else -> DragPointType.BASE
        }
        val rail = findBestRailForPoint(targetName)
        activeDrag = ActiveDrag(targetName, type, rail, startPos, sx, sy)

        _uiState.update {
            it.copy(
                draggingPointName = targetName,
                constraintRay = if ((st.dragConstraint == DragConstraintMode.SEGMENT || type == DragPointType.EDGE) && rail != null) {
                    ConstraintRayVisual(rail.v1, rail.v2)
                } else null
            )
        }
        return true
    }

    fun onDragMove(sx: Float, sy: Float, width: Float, height: Float, density: Float) {
        val st = _uiState.value
        val proj = makeProjection(width, height)
        val touchRadius = st.snapshot.sensitivity.touchRadius

        if (st.interactionMode == InteractionMode.ADD_POINT) {
            val hit = findNearestEdgeHit2D(sx, sy, proj, 48f * density * touchRadius)
            _uiState.update {
                if (hit != null) {
                    it.copy(
                        previewDot = hit.pos3D,
                        snapVisual = if (hit.snapLabel.isNotEmpty()) SnapVisualState(hit.pos3D, hit.snapLabel) else SnapVisualState(),
                        tooltip = HoverTooltip(sx, sy, "Chấm điểm trên ${hit.from}${hit.to} (k = ${String.format(Locale.US, "%.2f", hit.ratio)})", snapBadge = hit.snapLabel)
                    )
                } else {
                    it.copy(previewDot = null, snapVisual = SnapVisualState(), tooltip = null)
                }
            }
            return
        }

        val drag = activeDrag ?: return
        val snap = st.snapshot
        val dragSens = snap.sensitivity.drag
        val effX = drag.anchorScreenX + (sx - drag.anchorScreenX) * dragSens
        val effY = drag.anchorScreenY + (sy - drag.anchorScreenY) * dragSens
        val origin = ShapeBuilder.getOriginWorld(st.scene.pointRegistry, snap.originPoint)

        // Mode 1: Locked on Segment (or Custom Edge Point)
        if (st.dragConstraint == DragConstraintMode.SEGMENT || drag.type == DragPointType.EDGE) {
            val rail = drag.lockedRail ?: return
            val hit = projectOntoSegment(effX, effY, rail, proj, snap.snapEnabled) ?: return
            var relMsg = ""
            var linked = emptySet<String>()

            when (drag.type) {
                DragPointType.EDGE -> {
                    updateSnapshot(autoSave = false) { s ->
                        s.copy(customPoints = s.customPoints.map { p ->
                            if (p.name == drag.name) p.copy(from = hit.from, to = hit.to, ratio = hit.ratio) else p
                        })
                    }
                }
                DragPointType.XYZ -> {
                    val m = ShapeBuilder.worldToMath(hit.pos3D, origin)
                    updateSnapshot(autoSave = false) { s ->
                        s.copy(customXYZPoints = s.customXYZPoints.map { p ->
                            if (p.name == drag.name) p.copy(x = round2(m.x), y = round2(m.y), z = round2(m.z)) else p
                        })
                    }
                }
                DragPointType.BASE -> {
                    val res = ShapeBuilder.applyGeometricRelationships(snap, st.scene.pointRegistry, drag.name, hit.pos3D)
                    relMsg = res.message
                    linked = res.linkedPartners
                    updateSnapshot(autoSave = false) { s -> s.copy(vertexOverrides = res.updatedOverrides) }
                }
            }

            val updatedPos = _uiState.value.scene.pointRegistry[drag.name] ?: hit.pos3D
            val m = ShapeBuilder.worldToMath(updatedPos, origin)
            _uiState.update {
                it.copy(
                    linkedPartners = linked,
                    constraintRay = ConstraintRayVisual(hit.v1, hit.v2),
                    snapVisual = if (hit.snapLabel.isNotEmpty()) SnapVisualState(hit.pos3D, hit.snapLabel) else SnapVisualState(),
                    tooltip = HoverTooltip(
                        screenX = sx,
                        screenY = sy,
                        title = "[Trên đoạn ${hit.from}${hit.to}] ${drag.name} (k = ${String.format(Locale.US, "%.2f", hit.ratio)})",
                        subtitle = "Tọa độ: (${formatMathNumber(m.x)}; ${formatMathNumber(m.y)}; ${formatMathNumber(m.z)})",
                        snapBadge = hit.snapLabel,
                        relationBadge = relMsg
                    )
                )
            }
            return
        }

        // Mode 2 & 3: Horizontal (Oxy) or Vertical (Oz)
        val isVertical = st.dragConstraint == DragConstraintMode.VERTICAL
        val ray = proj.screenToRay(effX, effY)
        val rawPos = if (isVertical) {
            val horizCam = Vec3(proj.forward.x, 0f, proj.forward.z).let {
                if (it.lengthSq() < 1e-4f) Vec3(0f, 0f, 1f) else it.normalized()
            }
            val hitPt = ray.intersectPlane(horizCam, drag.startWorldPos)
            if (hitPt != null) Vec3(drag.startWorldPos.x, hitPt.y.coerceIn(-4f, 9.5f), drag.startWorldPos.z)
            else drag.startWorldPos
        } else {
            val hitPt = ray.intersectPlane(Vec3.UP, drag.startWorldPos)
            if (hitPt != null) Vec3(hitPt.x.coerceIn(-7.5f, 7.5f), drag.startWorldPos.y, hitPt.z.coerceIn(-7.5f, 7.5f))
            else drag.startWorldPos
        }

        val specialSnap = if (!isVertical && (drag.name == "S" || drag.name == "O")) {
            ShapeBuilder.snapToSpecialBaseTargets(rawPos, drag.name, st.scene.pointRegistry, snap.snapEnabled)
        } else null

        val finalPos: Vec3
        val snapReason: String
        val isSnapped: Boolean

        if (specialSnap != null) {
            finalPos = specialSnap.worldPos
            snapReason = specialSnap.reason
            isSnapped = true
        } else {
            val mRaw = ShapeBuilder.worldToMath(rawPos, origin)
            val mStart = ShapeBuilder.worldToMath(drag.startWorldPos, origin)
            val targetX = if (isVertical) mStart.x else mRaw.x
            val targetY = if (isVertical) mStart.y else mRaw.y
            val targetZ = if (isVertical) mRaw.z else mStart.z
            val sn = ShapeBuilder.snapMathCoordinates(targetX, targetY, targetZ, snap.snapEnabled)
            val fx = if (isVertical) mStart.x else sn.x
            val fy = if (isVertical) mStart.y else sn.y
            val fz = if (isVertical) sn.z else mStart.z
            finalPos = ShapeBuilder.mathToWorld(fx, fy, fz, origin)
            snapReason = sn.reason
            isSnapped = sn.snapped
        }

        drag.startWorldPos = finalPos
        var relMsg = ""
        var linked = emptySet<String>()

        if (drag.type == DragPointType.XYZ) {
            val mFinal = ShapeBuilder.worldToMath(finalPos, origin)
            updateSnapshot(autoSave = false) { s ->
                s.copy(customXYZPoints = s.customXYZPoints.map { p ->
                    if (p.name == drag.name) p.copy(x = round2(mFinal.x), y = round2(mFinal.y), z = round2(mFinal.z)) else p
                })
            }
        } else {
            val res = ShapeBuilder.applyGeometricRelationships(snap, st.scene.pointRegistry, drag.name, finalPos)
            relMsg = res.message
            linked = res.linkedPartners
            updateSnapshot(autoSave = false) { s -> s.copy(vertexOverrides = res.updatedOverrides) }
        }

        val mCur = ShapeBuilder.worldToMath(finalPos, origin)
        val modeTitle = if (isVertical) "[Kéo cao Oz]" else "[Kéo ngang Oxy]"
        _uiState.update {
            it.copy(
                linkedPartners = linked,
                constraintRay = if (isVertical) ConstraintRayVisual(Vec3(finalPos.x, -2f, finalPos.z), Vec3(finalPos.x, 8f, finalPos.z)) else null,
                snapVisual = if (isSnapped) SnapVisualState(finalPos, snapReason) else SnapVisualState(),
                tooltip = HoverTooltip(
                    screenX = sx,
                    screenY = sy,
                    title = "$modeTitle Điểm ${drag.name} (${formatMathNumber(mCur.x)}; ${formatMathNumber(mCur.y)}; ${formatMathNumber(mCur.z)})",
                    snapBadge = snapReason,
                    relationBadge = relMsg
                )
            )
        }
    }

    fun onDragEnd(sx: Float, sy: Float, width: Float, height: Float, density: Float) {
        val st = _uiState.value
        if (st.interactionMode == InteractionMode.ADD_POINT) {
            val proj = makeProjection(width, height)
            val hit = findNearestEdgeHit2D(sx, sy, proj, 48f * density)
            if (hit != null) {
                val nextName = ShapeBuilder.nextAutoPointName(st.scene.pointRegistry)
                addCustomEdgePoint(nextName, hit.from, hit.to, hit.ratio)
            }
            _uiState.update { it.copy(previewDot = null, snapVisual = SnapVisualState(), tooltip = null) }
            return
        }
        if (activeDrag != null) {
            activeDrag = null
            _uiState.update {
                it.copy(
                    draggingPointName = null,
                    linkedPartners = emptySet(),
                    snapVisual = SnapVisualState(),
                    constraintRay = null,
                    tooltip = null
                )
            }
            persistence.saveAutoSession(_uiState.value.snapshot)
        }
    }

    // ================= SAVE / EXPORT / IMPORT =================
    fun openSaveModal() {
        _uiState.update { st ->
            val suggested = st.scene.metrics.title.replace(Regex("\\s+"), "-").replace(Regex("[\\\\/:*?\"<>|]"), "")
            st.copy(
                saveModalOpen = true,
                customFileName = st.customFileName.ifBlank { suggested },
                savedSlots = persistence.loadNamedSlots()
            )
        }
    }

    fun closeSaveModal() {
        _uiState.update { it.copy(saveModalOpen = false) }
    }

    fun updateCustomFileName(name: String) {
        _uiState.update { it.copy(customFileName = name) }
    }

    fun sanitizedFileName(): String {
        val raw = _uiState.value.customFileName.trim()
        return if (raw.isEmpty()) "Hinh-3D-${_uiState.value.snapshot.shape.key}"
        else raw.replace(Regex("[\\\\/:*?\"<>|]"), "-")
    }

    fun saveNamedSlot() {
        val st = _uiState.value
        val name = st.customFileName.trim().ifEmpty { "Bài vẽ ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}" }
        val timeStr = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN")).format(Date())
        val slot = SavedSlot(
            name = name,
            savedAt = timeStr,
            shapeTitle = st.scene.metrics.title,
            snapshotJson = GeometryPersistence.snapshotToJson(st.snapshot)
        )
        val updated = persistence.saveNamedSlot(slot)
        _uiState.update { it.copy(savedSlots = updated) }
        showToast("Đã lưu \"$name\" trong ứng dụng!")
    }

    fun loadNamedSlot(slot: SavedSlot) {
        runCatching { GeometryPersistence.jsonToSnapshot(slot.snapshotJson) }.onSuccess { loaded ->
            pushHistory()
            val scene = ShapeBuilder.build(loaded)
            _uiState.update { st ->
                st.copy(
                    snapshot = loaded,
                    scene = scene,
                    customFileName = slot.name,
                    saveModalOpen = false,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            persistence.saveAutoSession(loaded)
            showToast("Đã mở bài \"${slot.name}\"!")
        }.onFailure {
            showToast("Không thể mở bài vẽ này!")
        }
    }

    fun deleteNamedSlot(name: String) {
        val updated = persistence.deleteNamedSlot(name)
        _uiState.update { it.copy(savedSlots = updated) }
        showToast("Đã xóa \"$name\"")
    }

    fun exportSnapshotJson(): String = GeometryPersistence.snapshotToJson(_uiState.value.snapshot)

    fun importSnapshotJson(jsonContent: String, fileName: String? = null) {
        runCatching { GeometryPersistence.jsonToSnapshot(jsonContent) }.onSuccess { loaded ->
            pushHistory()
            val scene = ShapeBuilder.build(loaded)
            val cleanName = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            _uiState.update { st ->
                st.copy(
                    snapshot = loaded,
                    scene = scene,
                    customFileName = cleanName ?: st.customFileName,
                    saveModalOpen = false,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            persistence.saveAutoSession(loaded)
            showToast("Đã mở file ${fileName ?: "JSON"} thành công!")
        }.onFailure {
            showToast("File JSON không hợp lệ hoặc bị lỗi!")
        }
    }

    fun renderExportBitmap(widthPx: Int, heightPx: Int, density: Float): Bitmap {
        return GeometryRenderer.renderToBitmap(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            uiState = _uiState.value,
            exportTitle = sanitizedFileName()
        )
    }

    fun resetAllToDefault() {
        persistence.clearAutoSession()
        undoStack.clear()
        redoStack.clear()
        val defaultSnap = GeometrySnapshot()
        val scene = ShapeBuilder.build(defaultSnap)
        _uiState.update { st ->
            st.copy(
                snapshot = defaultSnap,
                scene = scene,
                selectedFirstPoint = null,
                draggingPointName = null,
                linkedPartners = emptySet(),
                canUndo = false,
                canRedo = false,
                openCategories = setOf(ShapeCategory.PYRAMID)
            )
        }
        showToast("Đã đặt lại về mặc định ban đầu!")
    }

    private fun round2(v: Float): Float = round(v * 100f) / 100f
}
