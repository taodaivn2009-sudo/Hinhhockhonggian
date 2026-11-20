package com.example.geo2d

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeometryPersistence
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sqrt

class Geo2DViewModel(application: Application) : AndroidViewModel(application) {

    private val persistence = Geo2DPersistence(application.applicationContext)
    private val sharedFolderPrefs = GeometryPersistence(application.applicationContext)

    private val _uiState = MutableStateFlow(Geo2DUiState())
    val uiState: StateFlow<Geo2DUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<Geo2DSnapshot>()
    private val redoStack = ArrayDeque<Geo2DSnapshot>()
    private val maxHistory = 40
    private var toastJob: Job? = null
    private var autoSaveJob: Job? = null
    private var paramMAnimJob: Job? = null
    private var replayJob: Job? = null
    private var fullSnapshotBeforeReplay: Geo2DSnapshot? = null
    private var dragHistoryRecorded = false
    private var idSeq = 100

    private val palette = listOf(
        "#38BDF8", "#10B981", "#F43F5E", "#FBBF24", "#A78BFA", "#FB923C", "#2DD4BF"
    )

    init {
        val restored = persistence.loadAutoSession()
        val savedFolder = loadValidExportFolder()
        _uiState.value = _uiState.value.copy(
            snapshot = restored ?: Geo2DSnapshot(),
            exportFolderUri = savedFolder?.first,
            exportFolderLabel = savedFolder?.second
        )
    }

    private fun loadValidExportFolder(): Pair<String, String>? {
        val saved = sharedFolderPrefs.loadExportFolder() ?: return null
        val valid = getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri.toString() == saved.first && it.isWritePermission
        }
        return if (valid) saved else null
    }

    fun refreshExportFolder() {
        val folder = loadValidExportFolder()
        _uiState.update { it.copy(exportFolderUri = folder?.first, exportFolderLabel = folder?.second) }
    }

    fun setExportFolder(uriString: String, label: String) {
        sharedFolderPrefs.saveExportFolder(uriString, label)
        _uiState.update { it.copy(exportFolderUri = uriString, exportFolderLabel = label) }
        showToast("Đã chọn vị trí lưu: $label")
    }

    fun clearExportFolder() {
        sharedFolderPrefs.clearExportFolder()
        _uiState.update { it.copy(exportFolderUri = null, exportFolderLabel = null) }
        showToast("Đã chuyển sang chế độ hỏi vị trí mỗi lần lưu")
    }

    private fun nextId(prefix: String): String = "${prefix}_${System.currentTimeMillis()}_${idSeq++}"

    private fun nextPointName(existing: List<Geo2DPoint>): String {
        val used = existing.map { it.name }.toSet()
        for (c in 'A'..'Z') {
            if (c != 'O' && c.toString() !in used) return c.toString()
        }
        var idx = 1
        while ("P$idx" in used) idx++
        return "P$idx"
    }

    private fun pushHistory(snap: Geo2DSnapshot = _uiState.value.snapshot) {
        if (undoStack.lastOrNull() == snap) return
        undoStack.addLast(snap)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun snapshotForDisk(liveSnap: Geo2DSnapshot): Geo2DSnapshot {
        val full = fullSnapshotBeforeReplay ?: return liveSnap
        val merged = full.copy(
            camera = liveSnap.camera,
            sensitivity = liveSnap.sensitivity,
            showAxes = liveSnap.showAxes,
            showGrid = liveSnap.showGrid,
            snapToGrid = liveSnap.snapToGrid,
            showPointCoords = liveSnap.showPointCoords,
            showMeasurements = liveSnap.showMeasurements,
            showKeyGraphPoints = liveSnap.showKeyGraphPoints,
            showAsymptotes = liveSnap.showAsymptotes,
            showTangentOnTrace = liveSnap.showTangentOnTrace
        )
        fullSnapshotBeforeReplay = merged
        return merged
    }

    private fun stopParamMAnimIfRunning() {
        if (paramMAnimJob != null || _uiState.value.paramMAnimating) {
            paramMAnimJob?.cancel()
            paramMAnimJob = null
            _uiState.update { it.copy(paramMAnimating = false) }
        }
    }

    private fun scheduleDebouncedAutoSave(liveSnap: Geo2DSnapshot) {
        val toSave = snapshotForDisk(liveSnap)
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(350)
            persistence.saveAutoSession(toSave)
        }
    }

    /** Updates camera/view-only settings without cancelling an active Construction Replay. */
    private fun updateViewOnly(
        autoSave: Boolean = true,
        transform: (Geo2DSnapshot) -> Geo2DSnapshot
    ) {
        val next = transform(_uiState.value.snapshot)
        _uiState.update { it.copy(snapshot = next) }
        if (autoSave) {
            autoSaveJob?.cancel()
            persistence.saveAutoSession(snapshotForDisk(next))
        } else {
            snapshotForDisk(next)
        }
    }

    private fun updateSnapshot(
        recordHistory: Boolean = false,
        autoSave: Boolean = true,
        transform: (Geo2DSnapshot) -> Geo2DSnapshot
    ) {
        if (fullSnapshotBeforeReplay != null) {
            stopReplay(restoreFull = true)
        }
        val current = _uiState.value.snapshot
        if (recordHistory) pushHistory(current)
        val next = transform(current)
        _uiState.update { st ->
            val updatedTrace = if (st.traceX != null && (next.functions !== current.functions || next.paramM != current.paramM)) {
                evaluateTrace(next, st.traceX)
            } else {
                st.traceResults
            }
            val validVarId = st.variationTargetFnId?.takeIf { id -> next.functions.any { it.id == id } }
            st.copy(
                snapshot = next,
                traceResults = updatedTrace,
                variationTargetFnId = validVarId,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        if (autoSave) {
            autoSaveJob?.cancel()
            persistence.saveAutoSession(next)
        }
    }

    fun undo() {
        stopParamMAnimIfRunning()
        if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = true)
        if (undoStack.isEmpty()) return
        redoStack.addLast(_uiState.value.snapshot)
        val prev = undoStack.removeLast()
        _uiState.update { st ->
            val validVarId = st.variationTargetFnId?.takeIf { id -> prev.functions.any { it.id == id } }
            st.copy(
                snapshot = prev,
                pendingPointIds = emptyList(),
                variationTargetFnId = validVarId,
                traceResults = if (st.traceX != null) evaluateTrace(prev, st.traceX) else st.traceResults,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        autoSaveJob?.cancel()
        persistence.saveAutoSession(prev)
    }

    fun redo() {
        stopParamMAnimIfRunning()
        if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = true)
        if (redoStack.isEmpty()) return
        undoStack.addLast(_uiState.value.snapshot)
        val next = redoStack.removeLast()
        _uiState.update { st ->
            val validVarId = st.variationTargetFnId?.takeIf { id -> next.functions.any { it.id == id } }
            st.copy(
                snapshot = next,
                pendingPointIds = emptyList(),
                variationTargetFnId = validVarId,
                traceResults = if (st.traceX != null) evaluateTrace(next, st.traceX) else st.traceResults,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        autoSaveJob?.cancel()
        persistence.saveAutoSession(next)
    }

    fun showToast(msg: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = msg) }
        toastJob = viewModelScope.launch {
            delay(2400)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    // ================= UI TABS & TOOL SELECTION =================
    fun toggleTab(tab: Geo2DTab) {
        _uiState.update { st ->
            st.copy(activeTab = if (st.activeTab == tab) null else tab)
        }
    }

    fun closeTab() {
        _uiState.update { it.copy(activeTab = null) }
    }

    fun setTool(tool: Geo2DTool) {
        _uiState.update { st ->
            st.copy(
                activeTool = tool,
                pendingPointIds = emptyList(),
                draggingPointId = null,
                traceX = if (tool == Geo2DTool.TRACE_GRAPH) st.traceX ?: st.snapshot.camera.centerX.toDouble() else null,
                traceResults = if (tool == Geo2DTool.TRACE_GRAPH) evaluateTrace(st.snapshot, st.traceX ?: st.snapshot.camera.centerX.toDouble()) else emptyList()
            )
        }
    }

    fun clearTrace() {
        _uiState.update { it.copy(traceX = null, traceResults = emptyList()) }
    }

    // ================= CAMERA & DISPLAY TOGGLES =================
    fun panCamera(dxPx: Float, dyPx: Float) {
        val sens = _uiState.value.snapshot.sensitivity.pan.toDouble()
        updateViewOnly(autoSave = false) { s ->
            val ppu = s.camera.pixelsPerUnit
            s.copy(
                camera = s.camera.copy(
                    centerX = s.camera.centerX - (dxPx * sens) / ppu,
                    centerY = s.camera.centerY + (dyPx * sens) / ppu
                )
            )
        }
    }

    fun zoomCamera(zoomFactor: Float, focalX: Float, focalY: Float, width: Float, height: Float) {
        val sens = _uiState.value.snapshot.sensitivity.zoom.toDouble()
        val adjustedZoom = 1.0 + (zoomFactor - 1.0) * sens
        if (adjustedZoom <= 0.05) return
        updateViewOnly(autoSave = false) { s ->
            val oldCam = s.camera
            val (beforeX, beforeY) = Geo2DRenderer.screenToWorldDouble(focalX, focalY, oldCam, width, height)
            // Effectively infinite zoom: supports viewing spans from 1e-4 up to 1e12 units!
            val newPpu = (oldCam.pixelsPerUnit * adjustedZoom).coerceIn(1e-9, 1e7)
            val newCenterX = beforeX - (focalX - width * 0.5) / newPpu
            val newCenterY = beforeY + (focalY - height * 0.5) / newPpu
            s.copy(camera = Geo2DCamera(newCenterX, newCenterY, newPpu))
        }
    }

    fun commitCameraGesture() {
        scheduleDebouncedAutoSave(_uiState.value.snapshot)
    }

    fun stepZoom(zoomIn: Boolean) {
        val sens = _uiState.value.snapshot.sensitivity.zoom.toDouble()
        val factor = if (zoomIn) (1.0 + 0.45 * sens) else (1.0 / (1.0 + 0.45 * sens))
        updateViewOnly(autoSave = true) { s ->
            s.copy(camera = s.camera.copy(pixelsPerUnit = (s.camera.pixelsPerUnit * factor).coerceIn(1e-9, 1e7)))
        }
    }

    fun resetView() {
        updateViewOnly(autoSave = true) { s ->
            s.copy(camera = Geo2DCamera())
        }
        showToast("Đã đưa hệ trục về tâm O(0; 0)")
    }

    fun toggleAxes() = updateViewOnly { it.copy(showAxes = !it.showAxes) }
    fun toggleGrid() = updateViewOnly { it.copy(showGrid = !it.showGrid) }
    fun toggleSnap() = updateViewOnly { it.copy(snapToGrid = !it.snapToGrid) }
    fun togglePointCoords() = updateViewOnly { it.copy(showPointCoords = !it.showPointCoords) }
    fun toggleMeasurements() = updateViewOnly { it.copy(showMeasurements = !it.showMeasurements) }
    fun toggleKeyGraphPoints() = updateViewOnly { it.copy(showKeyGraphPoints = !it.showKeyGraphPoints) }
    fun toggleTangentOnTrace() = updateViewOnly { it.copy(showTangentOnTrace = !it.showTangentOnTrace) }
    fun toggleAsymptotes() = updateViewOnly { it.copy(showAsymptotes = !it.showAsymptotes) }

    fun toggleVerifyHideAnswers() {
        _uiState.update { it.copy(verifyHideAnswers = !it.verifyHideAnswers) }
    }

    fun toggleExportTransparentBg() {
        _uiState.update { it.copy(exportTransparentBg = !it.exportTransparentBg) }
    }

    // ================= PARAMETER m & VARIATION TABLE =================
    fun updateParamM(value: Double) {
        val rounded = round(value * 100.0) / 100.0
        updateSnapshot(recordHistory = false, autoSave = false) { s ->
            s.copy(paramM = rounded.coerceIn(s.paramMMin, s.paramMMax))
        }
        scheduleDebouncedAutoSave(_uiState.value.snapshot)
    }

    fun resetParamM() {
        paramMAnimJob?.cancel()
        _uiState.update { it.copy(paramMAnimating = false) }
        updateSnapshot(recordHistory = false, autoSave = true) { it.copy(paramM = 1.0) }
    }

    fun toggleParamMAnimate() {
        val playing = _uiState.value.paramMAnimating
        if (playing) {
            paramMAnimJob?.cancel()
            _uiState.update { it.copy(paramMAnimating = false) }
            persistence.saveAutoSession(_uiState.value.snapshot)
            return
        }
        _uiState.update { it.copy(paramMAnimating = true) }
        paramMAnimJob?.cancel()
        paramMAnimJob = viewModelScope.launch {
            var dir = 1.0
            while (true) {
                delay(40)
                val snap = _uiState.value.snapshot
                val span = (snap.paramMMax - snap.paramMMin).coerceAtLeast(1.0)
                val step = span * 0.012
                var nextM = snap.paramM + dir * step
                if (nextM >= snap.paramMMax) {
                    nextM = snap.paramMMax
                    dir = -1.0
                } else if (nextM <= snap.paramMMin) {
                    nextM = snap.paramMMin
                    dir = 1.0
                }
                val rounded = round(nextM * 100.0) / 100.0
                _uiState.update { st ->
                    val ns = st.snapshot.copy(paramM = rounded)
                    st.copy(
                        snapshot = ns,
                        traceResults = if (st.traceX != null) evaluateTrace(ns, st.traceX) else st.traceResults
                    )
                }
            }
        }
    }

    fun openVariationTable(fnId: String) {
        _uiState.update { it.copy(variationTargetFnId = fnId) }
    }

    fun closeVariationTable() {
        _uiState.update { it.copy(variationTargetFnId = null) }
    }

    // ================= CONSTRUCTION REPLAY (2D STEP-BY-STEP) =================
    private fun buildReplaySteps(full: Geo2DSnapshot): List<Pair<String, Geo2DSnapshot>> {
        val steps = ArrayList<Pair<String, Geo2DSnapshot>>()
        val ptMap = full.points.associateBy { it.id }
        var cur = full.copy(points = emptyList(), objects = emptyList(), functions = emptyList())
        steps.add("Bước 0: Mặt phẳng tọa độ Oxy" to cur)

        full.points.forEach { pt ->
            cur = cur.copy(points = cur.points + pt)
            steps.add("Đặt điểm ${pt.name}(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})" to cur)
        }
        full.objects.forEach { obj ->
            cur = cur.copy(objects = cur.objects + obj)
            val label = when (obj) {
                is Geo2DObject.Segment -> "Vẽ đoạn ${ptMap[obj.p1Id]?.name ?: ""}${ptMap[obj.p2Id]?.name ?: ""}"
                is Geo2DObject.Line -> "Vẽ đường thẳng ${ptMap[obj.p1Id]?.name ?: ""}${ptMap[obj.p2Id]?.name ?: ""}"
                is Geo2DObject.Ray -> "Vẽ tia ${ptMap[obj.startId]?.name ?: ""}${ptMap[obj.throughId]?.name ?: ""}"
                is Geo2DObject.VectorObj -> "Vẽ vectơ ${ptMap[obj.fromId]?.name ?: ""}${ptMap[obj.toId]?.name ?: ""}"
                is Geo2DObject.Circle -> "Vẽ đường tròn tâm ${ptMap[obj.centerId]?.name ?: ""}"
                is Geo2DObject.Polygon -> "Vẽ đa giác ${obj.pointIds.mapNotNull { ptMap[it]?.name }.joinToString("")}"
                is Geo2DObject.AngleMeasure -> "Đo góc ∠${ptMap[obj.pAId]?.name ?: ""}${ptMap[obj.vertexId]?.name ?: ""}${ptMap[obj.pCId]?.name ?: ""}"
            }
            steps.add(label to cur)
        }
        full.functions.forEach { fn ->
            cur = cur.copy(functions = cur.functions + fn)
            steps.add("Vẽ đồ thị ${fn.label} = ${fn.expression}" to cur)
        }
        return steps
    }

    fun startReplay() {
        stopParamMAnimIfRunning()
        val full = fullSnapshotBeforeReplay ?: _uiState.value.snapshot
        val steps = buildReplaySteps(full)
        if (steps.size <= 1) {
            showToast("Hãy vẽ thêm điểm, hình hoặc đồ thị để tua lại từng bước!")
            return
        }
        fullSnapshotBeforeReplay = full
        applyReplayStep(0, steps)
        toggleReplayPlay()
    }

    private fun applyReplayStep(index: Int, steps: List<Pair<String, Geo2DSnapshot>>) {
        val clamped = index.coerceIn(0, steps.lastIndex)
        val (label, snap) = steps[clamped]
        val cam = _uiState.value.snapshot.camera
        _uiState.update { st ->
            st.copy(
                snapshot = snap.copy(camera = cam),
                activeTab = null,
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
        if (_uiState.value.replayPlaying) {
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
                delay(1050)
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
            _uiState.update { st ->
                st.copy(
                    snapshot = full.copy(camera = cam),
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

    fun updateSensitivity(
        pan: Float = _uiState.value.snapshot.sensitivity.pan,
        zoom: Float = _uiState.value.snapshot.sensitivity.zoom,
        touchRadius: Float = _uiState.value.snapshot.sensitivity.touchRadius
    ) {
        updateViewOnly(autoSave = false) { s ->
            s.copy(
                sensitivity = Geo2DSensitivity(
                    pan = pan.coerceIn(0.4f, 2.2f),
                    zoom = zoom.coerceIn(0.4f, 2.2f),
                    touchRadius = touchRadius.coerceIn(0.7f, 1.7f)
                )
            )
        }
        scheduleDebouncedAutoSave(_uiState.value.snapshot)
    }

    // ================= CANVAS GESTURES & INTERACTIVE DRAWING =================
    private fun findPointAtScreen(
        sx: Float,
        sy: Float,
        width: Float,
        height: Float,
        density: Float
    ): Geo2DPoint? {
        val snap = _uiState.value.snapshot
        val hitRadius = 26f * density * snap.sensitivity.touchRadius
        var best: Geo2DPoint? = null
        var bestDist = hitRadius
        snap.points.forEach { pt ->
            val sp = Geo2DRenderer.worldToScreen(pt.x, pt.y, snap.camera, width, height)
            val d = hypot(sp.x - sx, sp.y - sy)
            if (d < bestDist) {
                bestDist = d
                best = pt
            }
        }
        return best
    }

    private fun snapCoord(raw: Vec2, density: Float): Vec2 {
        val snap = _uiState.value.snapshot
        val major = Geo2DRenderer.niceGridStep(snap.camera.pixelsPerUnit, density).coerceAtLeast(1e-6)
        val snapStep = (major / 2.0).coerceAtLeast(1e-6)
        val fineStep = (major / 20.0).coerceAtLeast(1e-6)

        fun roundDynamic(v: Double): Float {
            if (!v.isFinite()) return 0f
            val r = round(v / fineStep) * fineStep
            return (if (abs(r) < fineStep * 1e-3) 0.0 else r).toFloat()
        }

        if (!snap.snapToGrid) {
            return Vec2(roundDynamic(raw.x.toDouble()), roundDynamic(raw.y.toDouble()))
        }

        val rx = raw.x.toDouble()
        val ry = raw.y.toDouble()
        val sx = round(rx / snapStep) * snapStep
        val sy = round(ry / snapStep) * snapStep
        val tol = snapStep * 0.35
        return Vec2(
            x = if (abs(rx - sx) <= tol) sx.toFloat() else roundDynamic(rx),
            y = if (abs(ry - sy) <= tol) sy.toFloat() else roundDynamic(ry)
        )
    }

    fun onCanvasTap(sx: Float, sy: Float, width: Float, height: Float, density: Float) {
        val st = _uiState.value
        val snap = st.snapshot
        val rawWorld = Geo2DRenderer.screenToWorld(sx, sy, snap.camera, width, height)

        if (st.activeTool == Geo2DTool.TRACE_GRAPH) {
            updateTraceAtScreenX(sx, width)
            return
        }

        if (st.activeTool == Geo2DTool.PAN_SELECT) {
            // If user taps near a graphed curve when no point hit, show quick trace!
            val hitPt = findPointAtScreen(sx, sy, width, height, density)
            if (hitPt != null) {
                showToast("${hitPt.name}(${Geo2DMathHelpers.formatNum(hitPt.x)}; ${Geo2DMathHelpers.formatNum(hitPt.y)})")
                clearTrace()
            } else {
                val results = evaluateTrace(snap, rawWorld.x.toDouble())
                val nearCurve = results.any { res ->
                    res.y.isFinite() && abs(Geo2DRenderer.worldToScreen(res.x.toFloat(), res.y.toFloat(), snap.camera, width, height).y - sy) < 28f * density
                }
                if (nearCurve) {
                    _uiState.update { it.copy(traceX = rawWorld.x.toDouble(), traceResults = results) }
                } else {
                    clearTrace()
                }
            }
            return
        }

        // Helper: resolve clicked existing point OR create a new point at tapped position
        fun resolveOrCreatePoint(): String {
            val existing = findPointAtScreen(sx, sy, width, height, density)
            if (existing != null) return existing.id
            val snapped = snapCoord(rawWorld, density)
            val newPt = Geo2DPoint(
                id = nextId("pt"),
                name = nextPointName(_uiState.value.snapshot.points),
                x = snapped.x,
                y = snapped.y
            )
            updateSnapshot(recordHistory = true) { s -> s.copy(points = s.points + newPt) }
            return newPt.id
        }

        when (st.activeTool) {
            Geo2DTool.ADD_POINT -> {
                val snapped = snapCoord(rawWorld, density)
                val name = nextPointName(snap.points)
                val pt = Geo2DPoint(nextId("pt"), name, snapped.x, snapped.y)
                updateSnapshot(recordHistory = true) { it.copy(points = it.points + pt) }
                showToast("Đã đặt điểm $name(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})")
            }

            Geo2DTool.SEGMENT, Geo2DTool.LINE, Geo2DTool.RAY, Geo2DTool.VECTOR, Geo2DTool.CIRCLE -> {
                val ptId = resolveOrCreatePoint()
                val pending = _uiState.value.pendingPointIds
                if (pending.isEmpty()) {
                    _uiState.update { it.copy(pendingPointIds = listOf(ptId)) }
                } else {
                    val firstId = pending.first()
                    if (firstId == ptId) {
                        _uiState.update { it.copy(pendingPointIds = emptyList()) }
                        return
                    }
                    val newObj: Geo2DObject = when (st.activeTool) {
                        Geo2DTool.SEGMENT -> Geo2DObject.Segment(nextId("seg"), firstId, ptId)
                        Geo2DTool.LINE -> Geo2DObject.Line(nextId("ln"), firstId, ptId)
                        Geo2DTool.RAY -> Geo2DObject.Ray(nextId("ray"), firstId, ptId)
                        Geo2DTool.VECTOR -> Geo2DObject.VectorObj(nextId("vec"), firstId, ptId)
                        else -> Geo2DObject.Circle(nextId("cir"), firstId, edgePointId = ptId)
                    }
                    updateSnapshot(recordHistory = true) { s -> s.copy(objects = s.objects + newObj) }
                    _uiState.update { it.copy(pendingPointIds = emptyList()) }
                }
            }

            Geo2DTool.POLYGON -> {
                val hitExisting = findPointAtScreen(sx, sy, width, height, density)
                val pending = _uiState.value.pendingPointIds
                if (hitExisting != null && pending.size >= 3 && hitExisting.id == pending.first()) {
                    finishPendingPolygon()
                    return
                }
                val ptId = hitExisting?.id ?: resolveOrCreatePoint()
                if (ptId !in pending) {
                    _uiState.update { it.copy(pendingPointIds = pending + ptId) }
                }
            }

            Geo2DTool.MEASURE_ANGLE -> {
                val ptId = resolveOrCreatePoint()
                val pending = _uiState.value.pendingPointIds
                if (ptId in pending) {
                    showToast("Hãy chọn điểm khác để tạo góc gồm 3 điểm phân biệt")
                    return
                }
                val updated = pending + ptId
                val ptMap = _uiState.value.snapshot.points.associateBy { it.id }
                if (updated.size == 1) {
                    _uiState.update { it.copy(pendingPointIds = updated) }
                    showToast("Đã chọn ${ptMap[updated[0]]?.name ?: "A"} → Chạm chọn đỉnh góc")
                } else if (updated.size == 2) {
                    _uiState.update { it.copy(pendingPointIds = updated) }
                    showToast("Đỉnh ${ptMap[updated[1]]?.name ?: "B"} → Chạm chọn điểm thứ 3")
                } else {
                    addAngleMeasure(updated[0], updated[1], updated[2])
                    _uiState.update { it.copy(pendingPointIds = emptyList()) }
                }
            }

            else -> Unit
        }
    }

    fun finishPendingPolygon() {
        val pending = _uiState.value.pendingPointIds
        if (pending.size < 3) {
            showToast("Cần ít nhất 3 đỉnh để tạo đa giác")
            return
        }
        val poly = Geo2DObject.Polygon(nextId("poly"), pending)
        updateSnapshot(recordHistory = true) { s -> s.copy(objects = s.objects + poly) }
        _uiState.update { it.copy(pendingPointIds = emptyList()) }
        showToast("Đã tạo đa giác ${pending.size} đỉnh!")
    }

    fun onDragStart(sx: Float, sy: Float, width: Float, height: Float, density: Float): Boolean {
        val st = _uiState.value
        if (st.activeTool == Geo2DTool.TRACE_GRAPH) {
            updateTraceAtScreenX(sx, width)
            return true
        }
        if (st.activeTool == Geo2DTool.PAN_SELECT) {
            val hit = findPointAtScreen(sx, sy, width, height, density)
            if (hit != null) {
                dragHistoryRecorded = false
                _uiState.update { it.copy(draggingPointId = hit.id) }
                return true
            }
        }
        return false
    }

    fun onDragMove(sx: Float, sy: Float, width: Float, height: Float, density: Float) {
        val st = _uiState.value
        if (st.activeTool == Geo2DTool.TRACE_GRAPH) {
            updateTraceAtScreenX(sx, width)
            return
        }
        val dragId = st.draggingPointId ?: return
        val rawWorld = Geo2DRenderer.screenToWorld(sx, sy, st.snapshot.camera, width, height)
        val snapped = snapCoord(rawWorld, density)
        val existing = st.snapshot.points.firstOrNull { it.id == dragId } ?: return
        if (existing.x == snapped.x && existing.y == snapped.y) return
        if (!dragHistoryRecorded) {
            pushHistory(st.snapshot)
            dragHistoryRecorded = true
        }
        updateSnapshot(recordHistory = false, autoSave = false) { s ->
            s.copy(
                points = s.points.map { p ->
                    if (p.id == dragId) p.copy(x = snapped.x, y = snapped.y) else p
                }
            )
        }
    }

    fun onDragEnd() {
        if (_uiState.value.draggingPointId != null) {
            val didMove = dragHistoryRecorded
            dragHistoryRecorded = false
            _uiState.update { it.copy(draggingPointId = null) }
            if (didMove) persistence.saveAutoSession(_uiState.value.snapshot)
        }
    }

    private fun updateTraceAtScreenX(sx: Float, width: Float) {
        val snap = _uiState.value.snapshot
        val (wx, _) = Geo2DRenderer.screenToWorldDouble(sx, 0f, snap.camera, width, 1000f)
        val snapStep = Geo2DRenderer.niceGridStep(snap.camera.pixelsPerUnit, 2.5f) / 10.0
        val snappedX = if (snap.snapToGrid && snapStep > 0.0) round(wx / snapStep) * snapStep else wx
        _uiState.update { it.copy(traceX = snappedX, traceResults = evaluateTrace(snap, snappedX)) }
    }

    private fun evaluateTrace(snap: Geo2DSnapshot, x: Double): List<TraceResult> {
        val step = kotlin.math.max(1e-7, (1.0 / snap.camera.pixelsPerUnit) * 1e-3)
        val m = snap.paramM
        return snap.functions.mapNotNull { fn ->
            if ((!fn.visible && !fn.showDerivative) || fn.expression.isBlank()) return@mapNotNull null
            val parsed = MathExprParser.parse(fn.expression)
            if (parsed is MathExprParser.ParseResult.Ok) {
                val y = parsed.expr.eval(x, m)
                val symDeriv = MathExprParser.derive(fn.expression)?.first
                val slope = symDeriv?.eval(x, m) ?: parsed.expr.derivative(x, step, m)
                TraceResult(fn.id, fn.label, fn.expression, fn.colorHex, x, y, slope)
            } else null
        }
    }

    // ================= FUNCTIONS MANAGEMENT (ARBITRARY f(x)) =================
    private fun nextFunctionNumber(existing: List<GraphFunction>): Int {
        val usedLabels = existing.map { it.label }.toSet()
        var n = 1
        while ("f$n(x)" in usedLabels) n++
        return n
    }

    fun addFunction(expression: String, colorHex: String? = null) {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) {
            showToast("Vui lòng nhập biểu thức hàm số!")
            return
        }
        val parsed = MathExprParser.parse(trimmed)
        if (parsed is MathExprParser.ParseResult.Error) {
            showToast("Lỗi hàm số: ${parsed.message}")
            return
        }
        val existing = _uiState.value.snapshot.functions
        val nextNum = nextFunctionNumber(existing)
        val color = colorHex ?: palette[(nextNum - 1) % palette.size]
        val fn = GraphFunction(
            id = nextId("fn"),
            label = "f$nextNum(x)",
            expression = trimmed,
            colorHex = color,
            visible = true
        )
        updateSnapshot(recordHistory = true) { s -> s.copy(functions = s.functions + fn) }
        showToast("Đã vẽ đồ thị y = $trimmed")
    }

    fun updateFunctionExpression(id: String, newExpr: String) {
        updateSnapshot(recordHistory = false, autoSave = false) { s ->
            s.copy(
                functions = s.functions.map { if (it.id == id) it.copy(expression = newExpr) else it }
            )
        }
        scheduleDebouncedAutoSave(_uiState.value.snapshot)
    }

    fun toggleFunctionVisibility(id: String) {
        updateSnapshot(recordHistory = true) { s ->
            s.copy(
                functions = s.functions.map { if (it.id == id) it.copy(visible = !it.visible) else it }
            )
        }
    }

    fun toggleFunctionDerivative(id: String) {
        val target = _uiState.value.snapshot.functions.firstOrNull { it.id == id } ?: return
        val turningOn = !target.showDerivative
        updateSnapshot(recordHistory = true) { s ->
            s.copy(
                functions = s.functions.map { if (it.id == id) it.copy(showDerivative = turningOn) else it }
            )
        }
        if (turningOn) {
            val formula = MathExprParser.derive(target.expression)?.second
            showToast(if (formula != null) "Đã bật đạo hàm: y' = $formula" else "Đã bật đồ thị đạo hàm ${target.label}'")
        } else {
            showToast("Đã tắt đồ thị đạo hàm ${target.label}'")
        }
    }

    fun cycleFunctionColor(id: String) {
        updateSnapshot(recordHistory = false) { s ->
            s.copy(
                functions = s.functions.map { fn ->
                    if (fn.id == id) {
                        val idx = palette.indexOf(fn.colorHex)
                        fn.copy(colorHex = palette[(idx + 1) % palette.size])
                    } else fn
                }
            )
        }
    }

    fun deleteFunction(id: String) {
        updateSnapshot(recordHistory = true) { s ->
            s.copy(functions = s.functions.filterNot { it.id == id })
        }
    }

    // ================= MANUAL COORDS, CIRCLES, & 2D PRESETS =================
    fun addManualPoint(nameRaw: String, x: Float, y: Float) {
        val name = nameRaw.trim().ifEmpty { nextPointName(_uiState.value.snapshot.points) }
        val pt = Geo2DPoint(nextId("pt"), name, round2(x), round2(y))
        updateSnapshot(recordHistory = true) { s -> s.copy(points = s.points + pt) }
        showToast("Đã thêm điểm $name(${Geo2DMathHelpers.formatNum(pt.x)}; ${Geo2DMathHelpers.formatNum(pt.y)})")
    }

    fun addManualCircle(cx: Float, cy: Float, radius: Float) {
        if (radius <= 0.05f) {
            showToast("Bán kính R phải lớn hơn 0")
            return
        }
        val centerName = if (_uiState.value.snapshot.points.none { it.name == "I" }) "I" else nextPointName(_uiState.value.snapshot.points)
        val center = Geo2DPoint(nextId("pt"), centerName, round2(cx), round2(cy), "#10B981")
        val circle = Geo2DObject.Circle(nextId("cir"), center.id, edgePointId = null, fixedRadius = round2(radius))
        updateSnapshot(recordHistory = true) { s ->
            s.copy(points = s.points + center, objects = s.objects + circle)
        }
        showToast("Đã vẽ đường tròn tâm $centerName, R = ${Geo2DMathHelpers.formatNum(radius)}")
    }

    fun addSegmentBetween(p1Id: String, p2Id: String) {
        if (p1Id == p2Id) {
            showToast("Hãy chọn 2 điểm khác nhau")
            return
        }
        val exists = _uiState.value.snapshot.objects.any { obj ->
            obj is Geo2DObject.Segment && ((obj.p1Id == p1Id && obj.p2Id == p2Id) || (obj.p1Id == p2Id && obj.p2Id == p1Id))
        }
        if (exists) {
            showToast("Đoạn thẳng này đã tồn tại trên hình")
            return
        }
        val ptMap = _uiState.value.snapshot.points.associateBy { it.id }
        val p1 = ptMap[p1Id] ?: return
        val p2 = ptMap[p2Id] ?: return
        val d = Geo2DMathHelpers.distance(Vec2(p1.x, p1.y), Vec2(p2.x, p2.y))
        updateSnapshot(recordHistory = true) { s ->
            s.copy(objects = s.objects + Geo2DObject.Segment(nextId("seg"), p1Id, p2Id))
        }
        showToast("Đã nối ${p1.name}${p2.name} = ${Geo2DMathHelpers.formatNum(d)}")
    }

    fun addAngleMeasure(pAId: String, vertexId: String, pCId: String) {
        if (pAId == vertexId || pCId == vertexId || pAId == pCId) {
            showToast("Cần 3 điểm phân biệt để đo góc")
            return
        }
        val ptMap = _uiState.value.snapshot.points.associateBy { it.id }
        val pA = ptMap[pAId] ?: return
        val pV = ptMap[vertexId] ?: return
        val pC = ptMap[pCId] ?: return
        if (Geo2DMathHelpers.distance(Vec2(pA.x, pA.y), Vec2(pV.x, pV.y)) < 1e-5 ||
            Geo2DMathHelpers.distance(Vec2(pC.x, pC.y), Vec2(pV.x, pV.y)) < 1e-5
        ) {
            showToast("Hai điểm trùng nhau, không thể đo góc")
            return
        }
        val duplicate = _uiState.value.snapshot.objects.any { obj ->
            obj is Geo2DObject.AngleMeasure && obj.vertexId == vertexId &&
                ((obj.pAId == pAId && obj.pCId == pCId) || (obj.pAId == pCId && obj.pCId == pAId))
        }
        val deg = Geo2DMathHelpers.angleDegrees(Vec2(pA.x, pA.y), Vec2(pV.x, pV.y), Vec2(pC.x, pC.y))
        if (!duplicate) {
            val ang = Geo2DObject.AngleMeasure(nextId("ang"), pAId, vertexId, pCId)
            updateSnapshot(recordHistory = true) { s -> s.copy(objects = s.objects + ang) }
        }
        showToast("Góc ∠${pA.name}${pV.name}${pC.name} = ${Geo2DMathHelpers.formatNum(deg)}°")
    }

    fun deletePoint(pointId: String) {
        updateSnapshot(recordHistory = true) { s ->
            s.copy(
                points = s.points.filterNot { it.id == pointId },
                objects = s.objects.filterNot { obj ->
                    when (obj) {
                        is Geo2DObject.Segment -> obj.p1Id == pointId || obj.p2Id == pointId
                        is Geo2DObject.Line -> obj.p1Id == pointId || obj.p2Id == pointId
                        is Geo2DObject.Ray -> obj.startId == pointId || obj.throughId == pointId
                        is Geo2DObject.VectorObj -> obj.fromId == pointId || obj.toId == pointId
                        is Geo2DObject.Circle -> obj.centerId == pointId || obj.edgePointId == pointId
                        is Geo2DObject.Polygon -> pointId in obj.pointIds
                        is Geo2DObject.AngleMeasure -> obj.pAId == pointId || obj.vertexId == pointId || obj.pCId == pointId
                    }
                }
            )
        }
    }

    fun deleteObject(objectId: String) {
        updateSnapshot(recordHistory = true) { s ->
            s.copy(objects = s.objects.filterNot { it.id == objectId })
        }
    }

    fun clearAllGeometryAndGraphs() {
        stopParamMAnimIfRunning()
        updateSnapshot(recordHistory = true) { s ->
            s.copy(points = emptyList(), objects = emptyList(), functions = emptyList())
        }
        clearTrace()
        showToast("Đã xóa trắng mặt phẳng Oxy")
    }

    fun applyPreset(preset: Geo2DPreset) {
        stopParamMAnimIfRunning()
        if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = true)
        val pts = mutableListOf<Geo2DPoint>()
        val objs = mutableListOf<Geo2DObject>()
        var keepFuncs = _uiState.value.snapshot.functions

        when (preset) {
            Geo2DPreset.EMPTY_OXY -> {
                keepFuncs = emptyList()
            }
            Geo2DPreset.EQUILATERAL_TRIANGLE -> {
                val h = (2.0 * sqrt(3.0))
                val a = Geo2DPoint(nextId("p"), "A", 0f, (h * 2.0 / 3.0).toFloat())
                val b = Geo2DPoint(nextId("p"), "B", -2f, (-h / 3.0).toFloat())
                val c = Geo2DPoint(nextId("p"), "C", 2f, (-h / 3.0).toFloat())
                pts.addAll(listOf(a, b, c))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id)))
                objs.add(Geo2DObject.AngleMeasure(nextId("ang"), b.id, a.id, c.id))
            }
            Geo2DPreset.RIGHT_TRIANGLE -> {
                val a = Geo2DPoint(nextId("p"), "A", -2f, -1f)
                val b = Geo2DPoint(nextId("p"), "B", -2f, 2f)
                val c = Geo2DPoint(nextId("p"), "C", 2f, -1f)
                pts.addAll(listOf(a, b, c))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id)))
                objs.add(Geo2DObject.AngleMeasure(nextId("ang"), b.id, a.id, c.id))
            }
            Geo2DPreset.SQUARE -> {
                val a = Geo2DPoint(nextId("p"), "A", -2f, -2f)
                val b = Geo2DPoint(nextId("p"), "B", 2f, -2f)
                val c = Geo2DPoint(nextId("p"), "C", 2f, 2f)
                val d = Geo2DPoint(nextId("p"), "D", -2f, 2f)
                pts.addAll(listOf(a, b, c, d))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id, d.id)))
                objs.add(Geo2DObject.Segment(nextId("seg"), a.id, c.id, dashed = true, colorHex = "#94A3B8"))
                objs.add(Geo2DObject.Segment(nextId("seg"), b.id, d.id, dashed = true, colorHex = "#94A3B8"))
            }
            Geo2DPreset.RECTANGLE -> {
                val a = Geo2DPoint(nextId("p"), "A", -3f, -1.5f)
                val b = Geo2DPoint(nextId("p"), "B", 3f, -1.5f)
                val c = Geo2DPoint(nextId("p"), "C", 3f, 1.5f)
                val d = Geo2DPoint(nextId("p"), "D", -3f, 1.5f)
                pts.addAll(listOf(a, b, c, d))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id, d.id)))
            }
            Geo2DPreset.PARALLELOGRAM -> {
                val a = Geo2DPoint(nextId("p"), "A", -3f, -1.5f)
                val b = Geo2DPoint(nextId("p"), "B", 1.5f, -1.5f)
                val c = Geo2DPoint(nextId("p"), "C", 3f, 1.5f)
                val d = Geo2DPoint(nextId("p"), "D", -1.5f, 1.5f)
                pts.addAll(listOf(a, b, c, d))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id, d.id)))
            }
            Geo2DPreset.RHOMBUS -> {
                val a = Geo2DPoint(nextId("p"), "A", 0f, 2.5f)
                val b = Geo2DPoint(nextId("p"), "B", 3.5f, 0f)
                val c = Geo2DPoint(nextId("p"), "C", 0f, -2.5f)
                val d = Geo2DPoint(nextId("p"), "D", -3.5f, 0f)
                pts.addAll(listOf(a, b, c, d))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id, d.id)))
                objs.add(Geo2DObject.Segment(nextId("seg"), a.id, c.id, dashed = true, colorHex = "#94A3B8"))
                objs.add(Geo2DObject.Segment(nextId("seg"), b.id, d.id, dashed = true, colorHex = "#94A3B8"))
            }
            Geo2DPreset.ISOSCELES_TRAPEZOID -> {
                val a = Geo2DPoint(nextId("p"), "A", -3f, -1.5f)
                val b = Geo2DPoint(nextId("p"), "B", 3f, -1.5f)
                val c = Geo2DPoint(nextId("p"), "C", 1.5f, 1.5f)
                val d = Geo2DPoint(nextId("p"), "D", -1.5f, 1.5f)
                pts.addAll(listOf(a, b, c, d))
                objs.add(Geo2DObject.Polygon(nextId("poly"), listOf(a.id, b.id, c.id, d.id)))
            }
            Geo2DPreset.CIRCLE_TANGENT -> {
                val iPt = Geo2DPoint(nextId("p"), "I", 0f, 0f, "#10B981")
                val mPt = Geo2DPoint(nextId("p"), "M", 3f, 0f, "#38BDF8")
                pts.addAll(listOf(iPt, mPt))
                objs.add(Geo2DObject.Circle(nextId("cir"), iPt.id, edgePointId = mPt.id))
            }
        }

        updateSnapshot(recordHistory = true) { s ->
            s.copy(
                points = pts,
                objects = objs,
                functions = keepFuncs,
                camera = Geo2DCamera()
            )
        }
        _uiState.update { it.copy(pendingPointIds = emptyList(), activeTab = null) }
        showToast("Đã dựng: ${preset.title}")
    }

    // ================= SAVE / EXPORT / IMPORT (2D) =================
    fun openSaveModal() {
        refreshExportFolder()
        _uiState.update { it.copy(saveModalOpen = true) }
    }

    fun closeSaveModal() {
        _uiState.update { it.copy(saveModalOpen = false) }
    }

    fun updateCustomFileName(name: String) {
        _uiState.update { it.copy(customFileName = name) }
    }

    fun sanitizedFileName(): String {
        val raw = _uiState.value.customFileName.trim()
        return if (raw.isEmpty()) "ToolToan-2D-Oxy" else raw.replace(Regex("[\\\\/:*?\"<>|]"), "-")
    }

    fun exportSnapshotJson(): String = Geo2DPersistence.snapshotToJson(fullSnapshotBeforeReplay ?: _uiState.value.snapshot)

    fun importSnapshotJson(jsonContent: String, fileName: String? = null) {
        runCatching { Geo2DPersistence.jsonToSnapshot(jsonContent) }.onSuccess { loaded ->
            stopParamMAnimIfRunning()
            if (fullSnapshotBeforeReplay != null) stopReplay(restoreFull = true)
            pushHistory()
            val cleanName = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            _uiState.update { st ->
                st.copy(
                    snapshot = loaded,
                    customFileName = cleanName ?: st.customFileName,
                    saveModalOpen = false,
                    pendingPointIds = emptyList(),
                    variationTargetFnId = null,
                    traceX = null,
                    traceResults = emptyList(),
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            autoSaveJob?.cancel()
            persistence.saveAutoSession(loaded)
            showToast("Đã mở file 2D ${fileName ?: "JSON"}!")
        }.onFailure {
            showToast("File JSON 2D không hợp lệ!")
        }
    }

    fun renderExportBitmap(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        transparentBackground: Boolean = _uiState.value.exportTransparentBg
    ): Bitmap {
        return Geo2DRenderer.renderToBitmap(
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
        return com.example.engine.SvgExporter.export2DToSvg(
            width = widthPx,
            height = heightPx,
            uiState = _uiState.value,
            transparentBg = transparentBackground
        )
    }

    private fun round2(v: Float): Float = round(v * 100f) / 100f
}
