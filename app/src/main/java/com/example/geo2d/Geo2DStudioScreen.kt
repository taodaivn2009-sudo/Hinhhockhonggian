package com.example.geo2d

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ConstructionReplayBar
import com.example.ui.ParameterMSliderOverlay
import com.example.ui.ToastBanner
import com.example.ui.theme.*
import com.example.update.AppUpdateUiState
import com.example.update.UpdateVersionPill
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shared compact segmented switch between the 3D Space, 2D Plane, and Documents workspaces.
 */
@Composable
fun WorkspaceModeSwitch(
    current: MathWorkspace,
    onSelect: (MathWorkspace) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SlateDeepest.copy(alpha = 0.94f))
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkspaceTabChip(
            label = "3D",
            selected = current == MathWorkspace.SPACE_3D,
            activeColor = IndigoPrimary,
            testTag = "switch_workspace_3d",
            onClick = { onSelect(MathWorkspace.SPACE_3D) }
        )
        WorkspaceTabChip(
            label = "2D",
            selected = current == MathWorkspace.PLANE_2D,
            activeColor = EmeraldDark,
            testTag = "switch_workspace_2d",
            onClick = { onSelect(MathWorkspace.PLANE_2D) }
        )
        WorkspaceTabChip(
            label = "Tài liệu",
            selected = current == MathWorkspace.DOCUMENTS,
            activeColor = Color(0xFFD97706),
            testTag = "switch_workspace_docs",
            onClick = { onSelect(MathWorkspace.DOCUMENTS) }
        )
    }
}

@Composable
private fun WorkspaceTabChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) activeColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else SlateTextSecondary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun Geo2DStudioScreen(
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    pendingPlotExpr: String? = null,
    onConsumePendingPlot: () -> Unit = {},
    updateUiState: AppUpdateUiState = AppUpdateUiState(),
    onOpenUpdateDialog: () -> Unit = {},
    onOpenThemeDialog: () -> Unit = {},
    viewModel: Geo2DViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    LaunchedEffect(Unit) {
        viewModel.refreshExportFolder()
    }

    LaunchedEffect(pendingPlotExpr) {
        val expr = pendingPlotExpr?.trim()
        if (!expr.isNullOrEmpty()) {
            val existing = viewModel.uiState.value.snapshot.functions.firstOrNull {
                it.expression.replace(" ", "").equals(expr.replace(" ", ""), ignoreCase = true)
            }
            if (existing == null) {
                viewModel.addFunction(expr)
            } else if (!existing.visible) {
                viewModel.toggleFunctionVisibility(existing.id)
                viewModel.showToast("Đã bật lại đồ thị y = ${existing.expression}")
            } else {
                viewModel.showToast("Đồ thị y = ${existing.expression} đang hiển thị")
            }
            onConsumePendingPlot()
        }
    }

    BackHandler(enabled = uiState.replayStep != null) {
        viewModel.stopReplay(restoreFull = true)
    }

    fun writePngToUri(uri: Uri, locationSuffix: String = "") {
        runCatching {
            val bmp = viewModel.renderExportBitmap(
                widthPx = canvasSize.width.coerceAtLeast(1080),
                heightPx = canvasSize.height.coerceAtLeast(1080),
                density = density
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Không thể mở luồng ghi tệp")
            bmp.recycle()
        }.onSuccess {
            viewModel.closeSaveModal()
            val bgTag = if (uiState.exportTransparentBg) " (nền trong suốt)" else ""
            viewModel.showToast("Đã lưu ảnh ${viewModel.sanitizedFileName()}.png$bgTag$locationSuffix")
        }.onFailure {
            viewModel.showToast("Lỗi lưu ảnh PNG 2D!")
        }
    }

    fun writeSvgToUri(uri: Uri, locationSuffix: String = "") {
        runCatching {
            val svgText = viewModel.exportSvgModel(
                widthPx = canvasSize.width.coerceAtLeast(1080),
                heightPx = canvasSize.height.coerceAtLeast(1080)
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { it.write(svgText) }
            } ?: error("Không thể mở luồng ghi tệp")
        }.onSuccess {
            viewModel.closeSaveModal()
            viewModel.showToast("Đã lưu vector ${viewModel.sanitizedFileName()}.svg$locationSuffix")
        }.onFailure {
            viewModel.showToast("Lỗi lưu file Vector SVG 2D!")
        }
    }

    fun writeJsonToUri(uri: Uri, locationSuffix: String = "") {
        runCatching {
            val jsonText = viewModel.exportSnapshotJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { it.write(jsonText) }
            } ?: error("Không thể mở luồng ghi tệp")
        }.onSuccess {
            viewModel.closeSaveModal()
            viewModel.showToast("Đã lưu file ${viewModel.sanitizedFileName()}.json$locationSuffix")
        }.onFailure {
            viewModel.showToast("Lỗi lưu file 2D JSON!")
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            viewModel.setExportFolder(treeUri.toString(), formatGeo2DTreeLabel(treeUri))
        }
    }

    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> if (uri != null) writePngToUri(uri) }

    val exportSvgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri -> if (uri != null) writeSvgToUri(uri) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeJsonToUri(uri) }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                var displayName: String? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) displayName = c.getString(0)
                }
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text != null) viewModel.importSnapshotJson(text, displayName)
            }.onFailure {
                viewModel.showToast("Không thể đọc file 2D JSON!")
            }
        }
    }

    fun handleExport(
        forcePicker: Boolean,
        mimeType: String,
        ext: String,
        onDirect: (Uri, String) -> Unit,
        launchPicker: (String) -> Unit
    ) {
        val fileName = "${viewModel.sanitizedFileName()}.$ext"
        val folderUri = uiState.exportFolderUri
        val folderLabel = uiState.exportFolderLabel
        if (!forcePicker && !folderUri.isNullOrBlank()) {
            val created = runCatching {
                val tree = Uri.parse(folderUri)
                val parentId = DocumentsContract.getTreeDocumentId(tree)
                val parentDoc = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
                DocumentsContract.createDocument(context.contentResolver, parentDoc, mimeType, fileName)
            }.getOrNull()
            if (created != null) {
                onDirect(created, if (folderLabel != null) " vào $folderLabel" else "")
                return
            }
            viewModel.clearExportFolder()
        }
        launchPicker(fileName)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactWidth = maxWidth < 600.dp
        val maxSheetHeight = (maxHeight * 0.56f).coerceIn(280.dp, 480.dp)

        // 1. Interactive 2D Oxy + Function Graph Canvas
        Geo2DCanvasViewport(
            uiState = uiState,
            viewModel = viewModel,
            onSizeChanged = { canvasSize = it },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top 2D Studio Header with 3D/2D Switcher
        Geo2DTopHeader(
            uiState = uiState,
            viewModel = viewModel,
            onSwitchWorkspace = onSwitchWorkspace,
            updateUiState = updateUiState,
            onOpenUpdateDialog = onOpenUpdateDialog,
            onOpenThemeDialog = onOpenThemeDialog,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Right Quick Controls
        if (!isCompactWidth || uiState.activeTab == null) {
            Geo2DRightQuickControls(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // 3b. Tap anywhere on empty canvas area to quickly dismiss open panel
        if (uiState.activeTab != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.closeTab() }
                    .testTag("geo2d_dismiss_scrim")
            )
        }

        // 4. Adaptive Inspector Sheet (Functions / Presets / Measurements / Settings)
        Geo2DInspectorSheet(
            uiState = uiState,
            viewModel = viewModel,
            isCompactWidth = isCompactWidth,
            maxSheetHeight = maxSheetHeight,
            modifier = Modifier.align(if (isCompactWidth) Alignment.BottomCenter else Alignment.CenterStart)
        )

        // 4b. Floating Parameter m Slider Overlay (auto-shown when any active function contains parameter m)
        val hasActiveParamM = remember(uiState.snapshot.functions) {
            uiState.snapshot.functions.any {
                (it.visible || it.showDerivative) &&
                    (MathExprParser.parse(it.expression) as? MathExprParser.ParseResult.Ok)?.expr?.containsVarM() == true
            }
        }
        if (hasActiveParamM && uiState.activeTab == null && uiState.replayStep == null) {
            val topOffset = if (uiState.traceX != null && uiState.traceResults.isNotEmpty()) 130.dp else 60.dp
            ParameterMSliderOverlay(
                paramM = uiState.snapshot.paramM,
                minVal = uiState.snapshot.paramMMin,
                maxVal = uiState.snapshot.paramMMax,
                isAnimating = uiState.paramMAnimating,
                onValueChange = { viewModel.updateParamM(it) },
                onToggleAnimate = { viewModel.toggleParamMAnimate() },
                onReset = { viewModel.resetParamM() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = topOffset)
            )
        }

        // 5. Construction Replay Bar OR 2-Tier Bottom 2D Studio Dock
        if (uiState.replayStep != null) {
            ConstructionReplayBar(
                step = uiState.replayStep!!,
                totalSteps = uiState.replayTotalSteps,
                stepLabel = uiState.replayStepLabel,
                isPlaying = uiState.replayPlaying,
                onPrev = { viewModel.stepReplay(-1) },
                onTogglePlay = { viewModel.toggleReplayPlay() },
                onNext = { viewModel.stepReplay(1) },
                onClose = { viewModel.stopReplay() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        } else {
            Geo2DBottomDock(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 6. Floating Toast
        ToastBanner(
            message = uiState.toastMessage,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 7. 2D Save / Export Modal
        Geo2DSaveExportModal(
            uiState = uiState,
            viewModel = viewModel,
            onPickExportFolderClick = { pickFolderLauncher.launch(null) },
            onExportPngClick = { forcePicker ->
                handleExport(forcePicker, "image/png", "png", ::writePngToUri) { exportPngLauncher.launch(it) }
            },
            onExportSvgClick = { forcePicker ->
                handleExport(forcePicker, "image/svg+xml", "svg", ::writeSvgToUri) { exportSvgLauncher.launch(it) }
            },
            onExportJsonClick = { forcePicker ->
                handleExport(forcePicker, "application/json", "json", ::writeJsonToUri) { exportJsonLauncher.launch(it) }
            },
            onImportJsonClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )

        // 8. Automatic Variation Table (Bảng biến thiên) Dialog
        uiState.variationTargetFnId?.let { targetId ->
            val targetFn = uiState.snapshot.functions.firstOrNull { it.id == targetId }
            if (targetFn != null) {
                VariationTableDialog(
                    fn = targetFn,
                    paramM = uiState.snapshot.paramM,
                    paramMMin = uiState.snapshot.paramMMin,
                    paramMMax = uiState.snapshot.paramMMax,
                    onChangeParamM = { viewModel.updateParamM(it) },
                    onDismiss = { viewModel.closeVariationTable() }
                )
            }
        }
    }
}

@Composable
private fun Geo2DCanvasViewport(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val latestState by rememberUpdatedState(uiState)
    var sizePx by remember { mutableStateOf(IntSize(1080, 1920)) }

    Canvas(
        modifier = modifier
            .onSizeChanged {
                sizePx = it
                onSizeChanged(it)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val w = sizePx.width.toFloat().coerceAtLeast(1f)
                    val h = sizePx.height.toFloat().coerceAtLeast(1f)
                    var moved = false
                    var multiTouch = false
                    var cameraMoved = false
                    var draggingEntity = viewModel.onDragStart(downPos.x, downPos.y, w, h, density)

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            multiTouch = true
                            if (draggingEntity) {
                                viewModel.onDragEnd()
                                draggingEntity = false
                            }
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (abs(zoom - 1f) > 0.002f && centroid != Offset.Unspecified) {
                                viewModel.zoomCamera(zoom, centroid.x, centroid.y, w, h)
                                cameraMoved = true
                            }
                            if (pan != Offset.Zero) {
                                viewModel.panCamera(pan.x, pan.y)
                                cameraMoved = true
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            if (hypot(change.position.x - downPos.x, change.position.y - downPos.y) > 8f * density) {
                                moved = true
                            }
                            if (draggingEntity) {
                                viewModel.onDragMove(change.position.x, change.position.y, w, h, density)
                                if (change.positionChanged()) change.consume()
                            } else if (!multiTouch && delta != Offset.Zero && latestState.activeTool == Geo2DTool.PAN_SELECT) {
                                viewModel.panCamera(delta.x, delta.y)
                                cameraMoved = true
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    }

                    if (draggingEntity) {
                        viewModel.onDragEnd()
                    }
                    if (cameraMoved) {
                        viewModel.commitCameraGesture()
                    }
                    if (!moved && !multiTouch) {
                        viewModel.onCanvasTap(downPos.x, downPos.y, w, h, density)
                    }
                }
            }
            .testTag("geo2d_canvas")
    ) {
        // Observe active theme palette so Light/Dark/Preset/Accent changes redraw 2D canvas immediately
        @Suppress("UNUSED_VARIABLE")
        val themeVersion = ActiveThemeState.current
        Geo2DRenderer.renderScene(
            canvas = drawContext.canvas.nativeCanvas,
            width = size.width,
            height = size.height,
            density = density,
            uiState = uiState
        )
    }
}

@Composable
private fun Geo2DTopHeader(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    updateUiState: AppUpdateUiState = AppUpdateUiState(),
    onOpenUpdateDialog: () -> Unit = {},
    onOpenThemeDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkspaceModeSwitch(
                    current = MathWorkspace.PLANE_2D,
                    onSelect = onSwitchWorkspace
                )

                // Quick Functions Pill
                val visibleFnCount = uiState.snapshot.functions.count { (it.visible || it.showDerivative) && it.expression.isNotBlank() }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDark.copy(alpha = 0.9f))
                        .border(1.dp, if (uiState.activeTab == Geo2DTab.FUNCTIONS) EmeraldAccent else SlateBorder, RoundedCornerShape(10.dp))
                        .clickable { viewModel.toggleTab(Geo2DTab.FUNCTIONS) }
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (visibleFnCount > 0) "$visibleFnCount đồ thị" else "f(x)",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateTextPrimary,
                        maxLines = 1
                    )
                }
            }

            // Right Action Strip: Undo, Redo, Replay, Center O, Save
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateDark.copy(alpha = 0.9f))
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopIconBtn(Icons.AutoMirrored.Filled.Undo, "Hoàn tác", enabled = uiState.canUndo) { viewModel.undo() }
                TopIconBtn(Icons.AutoMirrored.Filled.Redo, "Làm lại", enabled = uiState.canRedo) { viewModel.redo() }
                TopIconBtn(Icons.Default.PlayCircleOutline, "Tua lại từng bước dựng", tint = AmberAccent, testTag = "replay_2d_btn") { viewModel.startReplay() }
                TopIconBtn(Icons.Default.MyLocation, "Về tâm O(0;0)") { viewModel.resetView() }
                TopIconBtn(Icons.Default.SaveAlt, "Lưu / Xuất 2D", tint = EmeraldAccent) { viewModel.openSaveModal() }
            }
        }

        // Compact Theme & GitHub version pills aligned neatly to the right below header
        if (uiState.traceX == null || uiState.traceResults.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemePickerButton(onClick = onOpenThemeDialog)
                UpdateVersionPill(
                    uiState = updateUiState,
                    onClick = onOpenUpdateDialog
                )
            }
        }

        // Live Trace Readout Pill (when tracing graph)
        if (uiState.traceX != null && uiState.traceResults.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateDeepest.copy(alpha = 0.92f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "x = ${Geo2DMathHelpers.formatNum(uiState.traceX)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = AmberAccent,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.traceResults.take(3).forEach { res ->
                        val c = try { Color(AndroidColor.parseColor(res.colorHex)) } catch (_: Exception) { CyanAccent }
                        val yText = if (uiState.verifyHideAnswers) "???" else if (res.y.isFinite()) Geo2DMathHelpers.formatNum(res.y) else "Không XĐ"
                        val slopeText = if (uiState.verifyHideAnswers) "" else if (res.slope.isFinite()) " • f'(x)=${Geo2DMathHelpers.formatNum(res.slope)}" else ""
                        Text(
                            text = "${res.functionLabel} = $yText$slopeText",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = c,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { viewModel.clearTrace() }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng dò đồ thị", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TopIconBtn(
    icon: ImageVector,
    contentDesc: String,
    enabled: Boolean = true,
    tint: Color = SlateTextPrimary,
    testTag: String? = null,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (enabled) tint else SlateTextSecondary.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun Geo2DRightQuickControls(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    modifier: Modifier = Modifier
) {
    val s = uiState.snapshot
    Column(
        modifier = modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SlateDark.copy(alpha = 0.84f))
            .border(1.dp, SlateBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        QuickChip2D("Oxy", Icons.Default.GridOn, s.showAxes) { viewModel.toggleAxes() }
        QuickChip2D("Hút", Icons.Default.Straighten, s.snapToGrid) { viewModel.toggleSnap() }
        QuickChip2D("Đo", Icons.Default.SquareFoot, s.showMeasurements) { viewModel.toggleMeasurements() }
        HorizontalDivider(color = SlateBorder.copy(alpha = 0.65f), modifier = Modifier.width(24.dp).padding(vertical = 2.dp))
        IconButton(onClick = { viewModel.stepZoom(true) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Phóng to", tint = SlateTextPrimary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { viewModel.stepZoom(false) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Thu nhỏ", tint = SlateTextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun QuickChip2D(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) EmeraldDark.copy(alpha = 0.36f) else Color.Transparent)
            .border(1.dp, if (active) EmeraldAccent.copy(alpha = 0.65f) else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (active) EmeraldAccent else SlateTextSecondary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.5.sp, color = if (active) SlateTextPrimary else SlateTextSecondary)
    }
}

@Composable
private fun Geo2DInspectorSheet(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    isCompactWidth: Boolean,
    maxSheetHeight: Dp,
    modifier: Modifier = Modifier
) {
    val activeTab = uiState.activeTab
    BackHandler(enabled = activeTab != null) {
        viewModel.closeTab()
    }

    AnimatedVisibility(
        visible = activeTab != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        if (activeTab == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(16.dp)
        val containerMod = if (isCompactWidth) {
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 72.dp)
                .heightIn(min = 220.dp, max = maxSheetHeight)
        } else {
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 74.dp, bottom = 78.dp)
                .width(356.dp)
                .fillMaxHeight()
        }

        Column(
            modifier = containerMod
                .clip(shape)
                .background(SlateDark.copy(alpha = 0.96f))
                .border(1.dp, SlateBorder.copy(alpha = 0.85f), shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            if (isCompactWidth) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDeepest)
                        .clickable { viewModel.closeTab() }
                        .padding(top = 7.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SlateMuted)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateDeepest)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(activeTab.title, style = MaterialTheme.typography.titleSmall, color = EmeraldAccent)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.closeTab() }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng bảng", tint = SlateTextLight, modifier = Modifier.size(14.dp))
                    Text("Đóng", style = MaterialTheme.typography.labelSmall, color = SlateTextLight)
                }
            }
            HorizontalDivider(color = SlateSurface)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                when (activeTab) {
                    Geo2DTab.FUNCTIONS -> Geo2DFunctionsPane(uiState, viewModel)
                    Geo2DTab.PRESETS -> Geo2DPresetsPane(viewModel)
                    Geo2DTab.OBJECTS -> Geo2DObjectsPane(uiState, viewModel)
                    Geo2DTab.SETTINGS -> Geo2DSettingsPane(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Geo2DBottomDock(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Active Tool Hint Pill + Finish Polygon Button
        if (uiState.activeTool != Geo2DTool.PAN_SELECT && uiState.activeTab == null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SlateDeepest.copy(alpha = 0.92f))
                    .border(1.dp, EmeraldAccent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.activeTool.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextLight,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (uiState.activeTool == Geo2DTool.POLYGON && uiState.pendingPointIds.size >= 3) {
                    Text(
                        text = "Khép (${uiState.pendingPointIds.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldDark)
                            .clickable { viewModel.finishPendingPolygon() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Xong",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { viewModel.setTool(Geo2DTool.PAN_SELECT) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Tier 1: Compact Scrollable 2D Tool Mode Strip (auto-hidden when an inspector tab is open to keep UI neat!)
        AnimatedVisibility(visible = uiState.activeTab == null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(SlateDark.copy(alpha = 0.9f))
                    .border(1.dp, SlateBorder.copy(alpha = 0.75f), RoundedCornerShape(13.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Geo2DTool.entries.forEach { tool ->
                    val selected = uiState.activeTool == tool
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) EmeraldDark else Color.Transparent)
                            .border(1.dp, if (selected) EmeraldAccent.copy(alpha = 0.8f) else Color.Transparent, RoundedCornerShape(9.dp))
                            .clickable { viewModel.setTool(tool) }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                            .testTag("geo2d_tool_${tool.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = iconFor2DTool(tool),
                            contentDescription = null,
                            tint = if (selected) Color.White else SlateTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = tool.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else SlateTextLight,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Tier 2: Sleek 4-Tab Navigation Bar (unified M3 pill style matching 3D Studio)
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark.copy(alpha = 0.95f))
                .border(1.dp, SlateBorder.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Geo2DTab.entries.forEach { tab ->
                val selected = uiState.activeTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) EmeraldDark.copy(alpha = 0.24f) else Color.Transparent)
                        .clickable { viewModel.toggleTab(tab) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("geo2d_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) EmeraldDark else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconFor2DTab(tab),
                            contentDescription = tab.shortLabel,
                            tint = if (selected) Color.White else SlateTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (selected) EmeraldAccent else SlateTextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun iconFor2DTool(tool: Geo2DTool): ImageVector = when (tool) {
    Geo2DTool.PAN_SELECT -> Icons.Default.PanTool
    Geo2DTool.TRACE_GRAPH -> Icons.Default.Timeline
    Geo2DTool.ADD_POINT -> Icons.Default.FiberManualRecord
    Geo2DTool.SEGMENT -> Icons.Default.HorizontalRule
    Geo2DTool.LINE -> Icons.Default.ShowChart
    Geo2DTool.RAY -> Icons.Default.CallMade
    Geo2DTool.VECTOR -> Icons.Default.NorthEast
    Geo2DTool.CIRCLE -> Icons.Default.RadioButtonUnchecked
    Geo2DTool.POLYGON -> Icons.Default.Pentagon
    Geo2DTool.MEASURE_ANGLE -> Icons.Default.Architecture
}

private fun iconFor2DTab(tab: Geo2DTab): ImageVector = when (tab) {
    Geo2DTab.FUNCTIONS -> Icons.AutoMirrored.Filled.ShowChart
    Geo2DTab.PRESETS -> Icons.Default.Category
    Geo2DTab.OBJECTS -> Icons.Default.Straighten
    Geo2DTab.SETTINGS -> Icons.Default.Tune
}

private fun formatGeo2DTreeLabel(treeUri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
    if (!docId.isNullOrBlank()) {
        val parts = docId.split(":", limit = 2)
        if (parts.size == 2) {
            val root = if (parts[0].equals("primary", ignoreCase = true)) "Bộ nhớ trong" else "Bộ nhớ ngoài (${parts[0]})"
            val sub = parts[1].trim('/').replace("/", " / ")
            return if (sub.isBlank()) root else "$root / $sub"
        }
        return docId
    }
    return Uri.decode(treeUri.lastPathSegment ?: "Thư mục đã chọn")
}
