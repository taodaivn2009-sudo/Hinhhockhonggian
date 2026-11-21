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
    private var autoSaveJob: Job? = null
    private var replayJob: Job? = null
    private var fullSnapshotBeforeReplay: GeometrySnapshot? = null
    private var dragHistoryRecorded = false
    private var idSeq = 1

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
        val savedFolder = persistence.loadExportFolder()?.takeIf { (uriStr, _) ->
            application.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == uriStr && it.isWritePermission
            }
        }
        if (savedFolder == null && persistence.loadExportFolder() != null) {
            persistence.clearExportFolder()
        }
        val restored = persistence.loadAutoSession()
        if (restored != null) {
            val scene = ShapeBuilder.build(restored)
            val pts = scene.pointRegistry.keys.toList()
            _uiState.value = _uiState.value.copy(
                snapshot = restored,
                scene = scene,
                savedSlots = slots,
                exportFolderUri = savedFolder?.first,
                exportFolderLabel = savedFolder?.second,
                openCategories = setOf(restored.shape.category),
                calcPointA = pts.getOrElse(0) { "S" },
                calcPointB = pts.getOrElse(1) { pts.firstOrNull() ?: "A" },
                calcPlaneP1 = pts.getOrElse(1) { "A" },
                calcPlaneP2 = pts.getOrElse(2) { "B" },
                calcPlaneP3 = pts.getOrElse(3) { pts.firstOrNull() ?: "C" }
            )
            showToast("Đã khôi phục bài vẽ lần trước!")
        } else {
            val initSnap = GeometrySnapshot()
            val scene = ShapeBuilder.build(initSnap)
            _uiState.value = _uiState.value.copy(
                snapshot = initSnap,
                scene = scene,
                savedSlots = slots,
                exportFolderUri = savedFolder?.first,
                exportFolderLabel = savedFolder?.second
            )
        }
    }

    // ================= HISTORY & SNAPSHOT APPLICATION =================
    private fun nextId(prefix: String): String = "${prefix}_${System.currentTimeMillis()}_${idSeq++}"

    private fun pushHistory(snap: GeometrySnapshot = _uiState.value.snapshot) {
        if (undoStack.lastOrNull() == snap) return
        undoStack.addLast(snap)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    /**
     * Never persist a truncated Replay step to disk. If Replay is active, mirror live view/camera
     * settings onto fullSnapshotBeforeReplay and save that full snapshot instead.
     */
    private fun snapshotForDisk(liveSnap: GeometrySnapshot): GeometrySnapshot {
        val full = fullSnapshotBeforeReplay ?: return liveSnap
        val merged = full.copy(
            camera = liveSnap.camera,
            sensitivity = liveSnap.sensitivity,
            dashed = liveSnap.dashed,
            altitude = liveSnap.altitude,
            labels = liveSnap.labels,
            showGrid = liveSnap.showGrid,
            showAxes = liveSnap.showAxes,
            showCoordsOnLabels = liveSnap.showCoordsOnLabels,
            snapEnabled = liveSnap.snapEnabled
        )
        fullSnapshotBeforeReplay = merged
        return merged
    }

    private fun persistSafeAutoSession(liveSnap: GeometrySnapshot) {
        autoSaveJob?.cancel()
        persistence.saveAutoSession(snapshotForDisk(liveSnap))
    }

    private fun scheduleDebouncedAutoSave(liveSnap: GeometrySnapshot) {
        val toSave = snapshotForDisk(liveSnap)
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(300)
            persistence.saveAutoSession(toSave)
        }
    }

    private fun applyResolvedCalcSelections(
        st: GeometryUiState,
        scene: BuiltScene,
        nextSnap: GeometrySnapshot
    ): GeometryUiState {
        val keys = scene.pointRegistry.keys.toList()
        return st.copy(
            snapshot = nextSnap,
            scene = scene,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            calcPointA = if (st.calcPointA in scene.pointRegistry) st.calcPointA else keys.getOrElse(0) { "S" },
            calcPointB = if (st.calcPointB in scene.pointRegistry) st.calcPointB else keys.getOrElse(1) { keys.firstOrNull() ?: "A" },
            calcPlaneP1 = if (st.calcPlaneP1 in scene.pointRegistry) st.calcPlaneP1 else keys.getOrElse(1) { keys.firstOrNull() ?: "A" },
            calcPlaneP2 = if (st.calcPlaneP2 in scene.pointRegistry) st.calcPlaneP2 else keys.getOrElse(2) { keys.firstOrNull() ?: "B" },
            calcPlaneP3 = if (st.calcPlaneP3 in scene.pointRegistry) st.calcPlaneP3 else keys.getOrElse(3) { keys.firstOrNull() ?: "C" }
        )
    }

    private fun updateSnapshot(
        recordHistory: Boolean = false,
        autoSave: Boolean = true,
        transform: (GeometrySnapshot) -> GeometrySnapshot
    ) {
        if (fullSnapshotBeforeReplay != null) {
            stopReplay(restoreFull = true)
        }
        val current = _uiState.value.snapshot
        if (recordHistory) pushHistory(current)
        var next = transform(current)
        val built = ShapeBuilder.build(next)
        val finalScene = if (!built.pointRegistry.containsKey(next.originPoint)) {
            val fallback = built.pointRegistry.keys.firstOrNull() ?: "O"
            next = next.copy(originPoint = fallback)
            ShapeBuilder.build(next)
        } else {
            built
        }

        _uiState.update { st -> applyResolvedCalcSelections(st, finalScene, next) }
        if (autoSave) {
            persistSafeAutoSession(next)
        }
    }

    /** Updates display/camera/sensitivity flags without rebuilding the 3D geometry mesh. */
    private fun updateViewSnapshot(
        autoSave: Boolean = true,
        transform: (GeometrySnapshot) -> GeometrySnapshot
    ) {
        val next = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = next) }
        if (autoSave) {
            persistSafeAutoSession(next)
        } else {
            snapshotForDisk(next)
        }
    }

    fun undo() {
        if (fullSnapshotBeforeReplay != null) {
            stopReplay(restoreFull = true)
        }
        if (undoStack.isEmpty()) return
        redoStack.addLast(_uiState.value.snapshot)
        val prev = undoStack.removeLast()
        val scene = ShapeBuilder.build(prev)
        _uiState.update { st ->
            applyResolvedCalcSelections(st, scene, prev).copy(selectedFirstPoint = null)
        }
        persistSafeAutoSession(prev)
    }

    fun redo() {
        if (fullSnapshotBeforeReplay != null) {
            stopReplay(restoreFull = true)
        }
        if (redoStack.isEmpty()) return
        undoStack.addLast(_uiState.value.snapshot)
        val next = redoStack.removeLast()
        val scene = ShapeBuilder.build(next)
        _uiState.update { st ->
            applyResolvedCalcSelections(st, scene, next).copy(selectedFirstPoint = null)
        }
        persistSafeAutoSession(next)
    }

    fun applyAiSnapshot(newSnapshot: GeometrySnapshot, title: String) {
        val currentSens = _uiState.value.snapshot.sensitivity
        updateSnapshot(recordHistory = true, autoSave = true) {
            newSnapshot.copy(sensitivity = currentSens)
        }
        _uiState.update {
            it.copy(
                activeLeftPane = null,
                selectedFirstPoint = null,
                autoRotate = false
            )
        }
        showToast("AI đã dựng hình 3D: $title")
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
        val cur = _uiState.value.snapshot.sensitivity
        val nextSens = cur.copy(
            rotation = (rotation ?: cur.rotation).coerceIn(0.2f, 2.5f),
            zoom = (zoom ?: cur.zoom).coerceIn(0.3f, 2.5f),
            drag = (drag ?: cur.drag).coerceIn(0.2f, 2.0f),
            touchRadius = (touchRadius ?: cur.touchRadius).coerceIn(0.6f, 1.8f)
        )
        val nextSnap = _uiState.value.snapshot.copy(sensitivity = nextSens)
        _uiState.update { it.copy(snapshot = nextSnap) }
        scheduleDebouncedAutoSave(nextSnap)
    }

    fun applySensitivityPreset(preset: String) {
        val next = when (preset) {
            "precise" -> SensitivityConfig(rotation = 0.5f, zoom = 0.7f, drag = 0.5f, touchRadius = 1.25f)
            "fast" -> SensitivityConfig(rotation = 1.7f, zoom = 1.5f, drag = 1.35f, touchRadius = 1.1f)
            else -> SensitivityConfig(rotation = 1.0f, zoom = 1.0f, drag = 1.0f, touchRadius = 1.0f)
        }
        updateViewSnapshot { it.copy(sensitivity = next) }
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
        updateViewSnapshot { it.copy(keepRelations = !it.keepRelations) }
    }

    fun toggleSnapEnabled() {
        updateViewSnapshot { it.copy(snapEnabled = !it.snapEnabled) }
        if (!_uiState.value.snapshot.snapEnabled) {
            _uiState.update { it.copy(snapVisual = SnapVisualState()) }
        }
    }

    fun toggleAxes() {
        updateViewSnapshot { it.copy(showAxes = !it.showAxes) }
    }

    fun openShapePreset(shape: ShapeType, showAxes: Boolean) {
        selectShape(shape)
        if (showAxes && !_uiState.value.snapshot.showAxes) {
            updateViewSnapshot { it.copy(showAxes = true) }
        }
    }

    fun changeOrigin(pointName: String) {
        updateSnapshot(recordHistory = true) {
            it.copy(originPoint = pointName, showAxes = true)
        }
    }

    fun setShowCoordsOnLabels(show: Boolean) {
        updateViewSnapshot { it.copy(showCoordsOnLabels = show) }
    }

    fun setCalcPoints(pointA: String, pointB: String) {
        _uiState.update { it.copy(calcPointA = pointA, calcPointB = pointB) }
    }

    fun setCalcPlanePoints(p1: String, p2: String, p3: String) {
        _uiState.update { it.copy(calcPlaneP1 = p1, calcPlaneP2 = p2, calcPlaneP3 = p3) }
    }

    fun toggleVerifyHideAnswers() {
        _uiState.update { it.copy(verifyHideAnswers = !it.verifyHideAnswers) }
    }

    fun toggleExportTransparentBg() {
        _uiState.update { it.copy(exportTransparentBg = !it.exportTransparentBg) }
    }

    // ================= DYNAMIC 3-POINT CROSS SECTION =================
    fun setDynamicSection(p1: String, p2: String, p3: String): String? {
        if (setOf(p1, p2, p3).size < 3) return "Vui lòng chọn 3 điểm phân biệt!"
        val current = fullSnapshotBeforeReplay ?: _uiState.value.snapshot
        val testSnap = current.copy(dynamicSection = DynamicSection3Points(p1, p2, p3))
        val testScene = ShapeBuilder.build(testSnap)
        if (testScene.dynamicSectionBorder == null) {
            return "Không tạo được thiết diện phẳng từ 3 điểm ($p1, $p2, $p3) trên khối này (3 điểm thẳng hàng hoặc khối tròn xoay)."
        }
        updateSnapshot(recordHistory = true) { it.copy(dynamicSection = DynamicSection3Points(p1, p2, p3)) }
        val nVerts = testScene.dynamicSectionBorder.size
        val areaStr = testScene.dynamicSectionArea?.let { String.format(Locale.US, "%.2f", it) } ?: "0"
        showToast("Đã dựng thiết diện $nVerts cạnh qua ($p1$p2$p3) • S = $areaStr")
        return null
    }

    fun clearDynamicSection() {
        if ((fullSnapshotBeforeReplay ?: _uiState.value.snapshot).dynamicSection == null) return
        updateSnapshot(recordHistory = true) { it.copy(dynamicSection = null) }
        showToast("Đã xóa thiết diện động")
    }

    // ================= CONSTRUCTION REPLAY (STEP-BY-STEP) =================
    private fun buildReplaySteps(full: GeometrySnapshot): List<Pair<String, GeometrySnapshot>> {
        val steps = ArrayList<Pair<String, GeometrySnapshot>>()
        var cur = full.copy(
            customPoints = emptyList(),
            customXYZPoints = emptyList(),
            customLines = emptyList(),
            customPlanes = emptyList(),
            dynamicSection = null
        )
        steps.add("Bước 0: Khối gốc ${full.shape.title}" to cur)

        full.customPoints.forEach { pt ->
            cur = cur.copy(customPoints = cur.customPoints + pt)
            steps.add("Chấm điểm ${pt.name} trên ${pt.from}${pt.to}" to cur)
        }
        full.customXYZPoints.forEach { pt ->
            cur = cur.copy(customXYZPoints = cur.customXYZPoints + pt)
            steps.add("Chấm điểm ${pt.name}(${formatMathNumber(pt.x)}; ${formatMathNumber(pt.y)}; ${formatMathNumber(pt.z)})" to cur)
        }
        full.customLines.forEach { ln ->
            cur = cur.copy(customLines = cur.customLines + ln)
            val kind = if (ln.style.isVector) "vectơ" else "đoạn"
            steps.add("Vẽ $kind ${ln.p1}${ln.p2}" to cur)
        }
        full.customPlanes.forEach { pl ->
            cur = cur.copy(customPlanes = cur.customPlanes + pl)
            steps.add("Tô mặt phẳng (${pl.pts.joinToString("")})" to cur)
        }
        full.dynamicSection?.let { ds ->
            cur = cur.copy(dynamicSection = ds)
            steps.add("Cắt thiết diện động (${ds.p1}${ds.p2}${ds.p3})" to cur)
        }
        return steps
    }

    fun startReplay() {
        val full = fullSnapshotBeforeReplay ?: _uiState.value.snapshot
        val steps = buildReplaySteps(full)
        if (steps.size <= 1) {
            showToast("Hãy dựng thêm điểm, đường hoặc thiết diện để tua lại quy trình!")
            return
        }
        fullSnapshotBeforeReplay = full
        applyReplayStep(0, steps)
        toggleReplayPlay()
    }

    private fun applyReplayStep(index: Int, steps: List<Pair<String, GeometrySnapshot>>) {
        val clamped = index.coerceIn(0, steps.lastIndex)
        val (label, snap) = steps[clamped]
        val cam = _uiState.value.snapshot.camera
        val stepSnap = snap.copy(camera = cam)
        val built = ShapeBuilder.build(stepSnap)
        _uiState.update { st ->
            st.copy(
                snapshot = stepSnap,
                scene = built,
                activeLeftPane = null,
                replayStep = clamped,
                replayTotalSteps = steps.lastIndex,
                replayStepLabel = label
            )
        }
    }

    fun stepReplay(delta: Int) {
        val full = fullSnapshotBeforeReplay ?: return
        val cur = _uiState.value.replayStep ?: return
        replayJob?.cancel()
        val steps = buildReplaySteps(full)
        _uiState.update { it.copy(replayPlaying = false) }
        applyReplayStep(cur + delta, steps)
    }

    fun toggleReplayPlay() {
        val full = fullSnapshotBeforeReplay ?: return
        val currentlyPlaying = _uiState.value.replayPlaying
        if (currentlyPlaying) {
            replayJob?.cancel()
            _uiState.update { it.copy(replayPlaying = false) }
            return
        }
        val steps = buildReplaySteps(full)
        if ((_uiState.value.replayStep ?: 0) >= steps.lastIndex) {
            applyReplayStep(0, steps)
        }
        _uiState.update { it.copy(replayPlaying = true) }
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            while (true) {
                delay(1150)
                val cur = _uiState.value.replayStep ?: break
                if (cur >= steps.lastIndex) {
                    _uiState.update { it.copy(replayPlaying = false) }
                    break
                }
                applyReplayStep(cur + 1, steps)
            }
        }
    }

    fun stopReplay(restoreFull: Boolean = true) {
        replayJob?.cancel()
        val full = fullSnapshotBeforeReplay
        fullSnapshotBeforeReplay = null
        if (restoreFull && full != null) {
            val cam = _uiState.value.snapshot.camera
            val restored = full.copy(camera = cam)
            val built = ShapeBuilder.build(restored)
            _uiState.update { st ->
                st.copy(
                    snapshot = restored,
                    scene = built,
                    replayStep = null,
                    replayTotalSteps = 0,
                    replayPlaying = false,
                    replayStepLabel = ""
                )
            }
        } else {
            _uiState.update { st ->
                st.copy(
                    replayStep = null,
                    replayTotalSteps = 0,
                    replayPlaying = false,
                    replayStepLabel = ""
                )
            }
        }
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
                customPlanes = emptyList(),
                dynamicSection = null
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

    fun toggleGrid(v: Boolean) = updateViewSnapshot { it.copy(showGrid = v) }
    fun toggleDashed(v: Boolean) = updateViewSnapshot { it.copy(dashed = v) }
    fun toggleLabels(v: Boolean) = updateViewSnapshot { it.copy(labels = v) }
    fun toggleAltitude(v: Boolean) = updateViewSnapshot { it.copy(altitude = v) }
    fun toggleFaces(v: Boolean) = updateSnapshot { it.copy(faces = v) }
    fun toggleSection(v: Boolean) = updateSnapshot { it.copy(showSection = v) }
    fun toggleAutoRotate(v: Boolean) { _uiState.update { it.copy(autoRotate = v) } }

    fun resetMovedVertices() {
        updateSnapshot(recordHistory = true) { it.copy(vertexOverrides = emptyMap()) }
        _uiState.update { it.copy(linkedPartners = emptySet()) }
        showToast("Đã đưa các đỉnh về vị trí chuẩn!")
    }

    // ================= CAMERA CONTROLS =================
    private fun wrapTheta(theta: Float): Float {
        val twoPi = (2.0 * PI).toFloat()
        var t = theta % twoPi
        if (t > PI.toFloat()) t -= twoPi
        if (t < -PI.toFloat()) t += twoPi
        return t
    }

    fun orbitCamera(deltaX: Float, deltaY: Float, density: Float = 1f) {
        val safeDensity = density.coerceAtLeast(1f)
        val dxDp = deltaX / safeDensity
        val dyDp = deltaY / safeDensity
        _uiState.update { st ->
            val cam = st.snapshot.camera
            val sens = st.snapshot.sensitivity.rotation
            // DPI-normalized orbit (~165° per 250dp drag at 1.0x sensitivity)
            val radPerDp = 0.0115f * sens
            val nextCam = cam.copy(
                theta = wrapTheta(cam.theta - dxDp * radPerDp),
                phi = (cam.phi - dyDp * radPerDp).coerceIn(0.08f, PI.toFloat() - 0.08f)
            )
            st.copy(
                autoRotate = false,
                tooltip = null,
                snapshot = st.snapshot.copy(camera = nextCam)
            )
        }
    }

    fun zoomAndPanCamera(
        zoomFactor: Float,
        panX: Float,
        panY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        density: Float = 1f
    ) {
        val safeDensity = density.coerceAtLeast(1f)
        _uiState.update { st ->
            val cam = st.snapshot.camera
            val zoomSens = st.snapshot.sensitivity.zoom
            val scaledZoom = (1f + (zoomFactor - 1f) * zoomSens).coerceIn(0.65f, 1.55f)
            val nextRadius = (cam.radius / scaledZoom).coerceIn(3.2f, 28f)
            val proj = CameraProjection(cam.cameraPosition(), cam.target, viewportWidth, viewportHeight)
            val panScale = nextRadius * 0.0026f * zoomSens / safeDensity
            val rawTarget = cam.target - proj.right * (panX * panScale) + proj.up * (panY * panScale)
            val clampedTarget = Vec3(
                x = rawTarget.x.coerceIn(-10f, 10f),
                y = rawTarget.y.coerceIn(-4f, 12f),
                z = rawTarget.z.coerceIn(-10f, 10f)
            )
            val nextCam = cam.copy(radius = nextRadius, target = clampedTarget)
            st.copy(
                autoRotate = false,
                tooltip = null,
                snapshot = st.snapshot.copy(camera = nextCam)
            )
        }
    }

    fun stepZoom(zoomIn: Boolean) {
        val snap = _uiState.value.snapshot
        val factor = if (zoomIn) (1f + 0.18f * snap.sensitivity.zoom) else 1f / (1f + 0.18f * snap.sensitivity.zoom)
        val nextRadius = (snap.camera.radius / factor).coerceIn(3.2f, 28f)
        val nextSnap = snap.copy(camera = snap.camera.copy(radius = nextRadius))
        _uiState.update { it.copy(snapshot = nextSnap) }
        scheduleDebouncedAutoSave(nextSnap)
    }

    fun tickAutoRotate(deltaSec: Float) {
        if (!_uiState.value.autoRotate) return
        _uiState.update { st ->
            val cam = st.snapshot.camera
            val rotSens = st.snapshot.sensitivity.rotation
            st.copy(snapshot = st.snapshot.copy(camera = cam.copy(theta = wrapTheta(cam.theta + deltaSec * 0.55f * rotSens))))
        }
    }

    fun commitCameraGesture() {
        scheduleDebouncedAutoSave(_uiState.value.snapshot)
    }

    fun setCameraPreset(preset: CameraViewPreset) {
        val baseRadius = _uiState.value.snapshot.camera.radius.coerceIn(7f, 18f)
        val target = Vec3(0f, 1.8f, 0f)
        val nextCam = when (preset) {
            CameraViewPreset.PERSPECTIVE -> CameraOrbitState.DEFAULT
            CameraViewPreset.FRONT -> CameraOrbitState(target = target, radius = baseRadius, theta = 0f, phi = (PI / 2.0).toFloat() - 0.08f)
            CameraViewPreset.TOP -> CameraOrbitState(target = target, radius = baseRadius, theta = 0f, phi = 0.10f)
            CameraViewPreset.SIDE -> CameraOrbitState(target = target, radius = baseRadius, theta = (PI / 2.0).toFloat(), phi = (PI / 2.0).toFloat() - 0.08f)
        }
        _uiState.update { it.copy(autoRotate = false, tooltip = null) }
        updateViewSnapshot { it.copy(camera = nextCam) }
    }

    fun resetCamera() {
        setCameraPreset(CameraViewPreset.PERSPECTIVE)
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
                    id = nextId("pt"),
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
                    id = nextId("xyz"),
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
                    id = nextId("ln"),
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
                    id = nextId("pl"),
                    pts = pts
                )
            )
        }
        showToast("Đã tô mặt phẳng (${pts.joinToString("")})")
        return null
    }

    /** Recursively removes a custom point and any dependent points/lines/planes/dynamic section. */
    private fun cascadeRemovePointNames(snap: GeometrySnapshot, initialNames: Set<String>): GeometrySnapshot {
        if (initialNames.isEmpty()) return snap
        val removed = initialNames.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            for (pt in snap.customPoints) {
                if (pt.name !in removed && (pt.from in removed || pt.to in removed)) {
                    removed.add(pt.name)
                    changed = true
                }
            }
        }
        val cleanedPlanes = snap.customPlanes.mapNotNull { pl ->
            val kept = pl.pts.filterNot { it in removed }
            if (kept.size >= 3) pl.copy(pts = kept) else null
        }
        val cleanedSec = snap.dynamicSection?.takeUnless { ds ->
            ds.p1 in removed || ds.p2 in removed || ds.p3 in removed
        }
        return snap.copy(
            customPoints = snap.customPoints.filterNot { it.name in removed },
            customXYZPoints = snap.customXYZPoints.filterNot { it.name in removed },
            customLines = snap.customLines.filterNot { it.p1 in removed || it.p2 in removed },
            customPlanes = cleanedPlanes,
            dynamicSection = cleanedSec
        )
    }

    fun removeCustomPoint(id: String) = updateSnapshot(recordHistory = true) { s ->
        val name = s.customPoints.firstOrNull { it.id == id }?.name
        if (name != null) cascadeRemovePointNames(s, setOf(name)) else s
    }

    fun removeCustomXYZ(id: String) = updateSnapshot(recordHistory = true) { s ->
        val name = s.customXYZPoints.firstOrNull { it.id == id }?.name
        if (name != null) cascadeRemovePointNames(s, setOf(name)) else s
    }

    fun removeCustomLine(id: String) = updateSnapshot(recordHistory = true) { s -> s.copy(customLines = s.customLines.filterNot { it.id == id }) }
    fun removeCustomPlane(id: String) = updateSnapshot(recordHistory = true) { s -> s.copy(customPlanes = s.customPlanes.filterNot { it.id == id }) }

    fun clearAllCustom() {
        updateSnapshot(recordHistory = true) { snap ->
            snap.copy(
                customPoints = emptyList(),
                customXYZPoints = emptyList(),
                customLines = emptyList(),
                customPlanes = emptyList(),
                dynamicSection = null,
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

        dragHistoryRecorded = false
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

            if (!dragHistoryRecorded) {
                pushHistory(snap)
                dragHistoryRecorded = true
            }

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

        if (!dragHistoryRecorded) {
            pushHistory(snap)
            dragHistoryRecorded = true
        }

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
            val hit = findNearestEdgeHit2D(sx, sy, proj, 48f * density * st.snapshot.sensitivity.touchRadius)
            if (hit != null) {
                val nextName = ShapeBuilder.nextAutoPointName(st.scene.pointRegistry)
                addCustomEdgePoint(nextName, hit.from, hit.to, hit.ratio)
            }
            _uiState.update { it.copy(previewDot = null, snapVisual = SnapVisualState(), tooltip = null) }
            return
        }
        if (activeDrag != null) {
            val didMove = dragHistoryRecorded
            dragHistoryRecorded = false
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
            if (didMove) persistence.saveAutoSession(_uiState.value.snapshot)
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
            if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = true)
            pushHistory()
            val scene = ShapeBuilder.build(loaded)
            _uiState.update { st ->
                applyResolvedCalcSelections(st, scene, loaded).copy(
                    customFileName = slot.name,
                    saveModalOpen = false,
                    selectedFirstPoint = null,
                    openCategories = setOf(loaded.shape.category)
                )
            }
            persistSafeAutoSession(loaded)
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

    fun setExportFolder(uriString: String, label: String) {
        persistence.saveExportFolder(uriString, label)
        _uiState.update { it.copy(exportFolderUri = uriString, exportFolderLabel = label) }
        showToast("Đã chọn vị trí lưu: $label")
    }

    fun clearExportFolder() {
        persistence.clearExportFolder()
        _uiState.update { it.copy(exportFolderUri = null, exportFolderLabel = null) }
        showToast("Đã chuyển sang chế độ hỏi vị trí mỗi lần lưu")
    }

    fun exportSnapshotJson(): String = GeometryPersistence.snapshotToJson(fullSnapshotBeforeReplay ?: _uiState.value.snapshot)

    fun exportObjModel(): String = MeshExporter.exportToObj(_uiState.value.snapshot, sanitizedFileName())

    fun importSnapshotJson(jsonContent: String, fileName: String? = null) {
        runCatching { GeometryPersistence.jsonToSnapshot(jsonContent) }.onSuccess { loaded ->
            if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = true)
            pushHistory()
            val scene = ShapeBuilder.build(loaded)
            val cleanName = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            _uiState.update { st ->
                applyResolvedCalcSelections(st, scene, loaded).copy(
                    customFileName = cleanName ?: st.customFileName,
                    saveModalOpen = false,
                    selectedFirstPoint = null,
                    openCategories = setOf(loaded.shape.category)
                )
            }
            persistSafeAutoSession(loaded)
            showToast("Đã mở file ${fileName ?: "JSON"} thành công!")
        }.onFailure {
            showToast("File JSON không hợp lệ hoặc bị lỗi!")
        }
    }

    fun renderExportBitmap(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        transparentBackground: Boolean = _uiState.value.exportTransparentBg
    ): Bitmap {
        return GeometryRenderer.renderToBitmap(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            uiState = _uiState.value,
            exportTitle = sanitizedFileName(),
            transparentBackground = transparentBackground
        )
    }

    fun exportSvgModel(
        widthPx: Int = 1280,
        heightPx: Int = 960,
        transparentBackground: Boolean = _uiState.value.exportTransparentBg
    ): String {
        return SvgExporter.export3DToSvg(
            width = widthPx,
            height = heightPx,
            uiState = _uiState.value,
            transparentBg = transparentBackground
        )
    }

    fun resetAllToDefault() {
        if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = false)
        autoSaveJob?.cancel()
        persistence.clearAutoSession()
        undoStack.clear()
        redoStack.clear()
        val defaultSnap = GeometrySnapshot()
        val scene = ShapeBuilder.build(defaultSnap)
        _uiState.update { st ->
            applyResolvedCalcSelections(st, scene, defaultSnap).copy(
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
