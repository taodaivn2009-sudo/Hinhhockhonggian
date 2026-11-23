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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.hypot

private enum class Geo2DSubMode(val label: String) {
    GEOMETRY("Hình học 2D"),
    GRAPH("Đồ thị hàm số")
}

@Composable
fun Geo2DStudioScreen(
    isImmersive: Boolean = false,
    onToggleImmersive: () -> Unit = {},
    pendingPlotExpr: String? = null,
    onConsumePendingPlot: () -> Unit = {},
    viewModel: Geo2DViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // Default to Graph mode when functions exist and no geometry objects, else Geometry mode
    var subMode by remember {
        mutableStateOf(
            if (uiState.snapshot.functions.isNotEmpty() && uiState.snapshot.objects.isEmpty()) {
                Geo2DSubMode.GRAPH
            } else {
                Geo2DSubMode.GEOMETRY
            }
        )
    }
    var graphSheetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshExportFolder()
    }

    LaunchedEffect(pendingPlotExpr) {
        val expr = pendingPlotExpr?.trim()
        if (!expr.isNullOrEmpty()) {
            subMode = Geo2DSubMode.GRAPH
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

    BackHandler(enabled = uiState.replayStep != null || graphSheetExpanded || uiState.activeTab != null) {
        when {
            uiState.replayStep != null -> viewModel.stopReplay()
            uiState.activeTab != null -> viewModel.closeTab()
            graphSheetExpanded -> graphSheetExpanded = false
        }
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

    val backdropState = rememberLiquidGlassBackdropState()

    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdropState) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactWidth = maxWidth < 600.dp
            val maxSheetHeight = (maxHeight * 0.56f).coerceIn(280.dp, 490.dp)
            val anySheetOpen = uiState.activeTab != null || (subMode == Geo2DSubMode.GRAPH && graphSheetExpanded)

            // 1. Full-bleed Interactive 2D Oxy + Function Graph Canvas
            Geo2DCanvasViewport(
                uiState = uiState,
                viewModel = viewModel,
                onSizeChanged = { canvasSize = it },
                onEmptyCanvasTap = {
                    if (!anySheetOpen) onToggleImmersive()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropSource(backdropState)
            )

            // 2. Top Floating Bar: Segmented Mode Switch (Hình học 2D | Đồ thị hàm số) + Compact Actions
            Geo2DTopHeader(
                uiState = uiState,
                subMode = subMode,
                isImmersive = isImmersive,
                onSelectSubMode = { next ->
                    subMode = next
                    viewModel.closeTab()
                    if (next == Geo2DSubMode.GEOMETRY) {
                        graphSheetExpanded = false
                    } else if (uiState.activeTool != Geo2DTool.TRACE_GRAPH) {
                        viewModel.setTool(Geo2DTool.PAN_SELECT)
                    }
                },
                onToggleImmersive = onToggleImmersive,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 3. Minimal Right Quick Controls (hidden in immersive or while editing sheet)
            AnimatedVisibility(
                visible = !isImmersive && (!isCompactWidth || !anySheetOpen),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Geo2DRightQuickControls(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }

            // 3b. Tap outside scrim to close open inspector/expanded graph sheet cleanly
            if (anySheetOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.closeTab()
                            graphSheetExpanded = false
                        }
                        .testTag("geo2d_dismiss_scrim")
                )
            }

            // 4. Floating Parameter m Slider Overlay (when active function uses m and sheet is collapsed)
            val hasActiveParamM = remember(uiState.snapshot.functions) {
                uiState.snapshot.functions.any {
                    (it.visible || it.showDerivative) &&
                        (MathExprParser.parse(it.expression) as? MathExprParser.ParseResult.Ok)?.expr?.containsVarM() == true
                }
            }
            if (hasActiveParamM && !anySheetOpen && uiState.replayStep == null && !isImmersive) {
                val topOffset = if (uiState.traceX != null && uiState.traceResults.isNotEmpty()) 136.dp else 66.dp
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

            // 5. Modal Bottom Sheet for Geometry helpers (Hình mẫu / Đo đạc / Cài đặt Oxy)
            Geo2DInspectorSheet(
                uiState = uiState,
                viewModel = viewModel,
                isCompactWidth = isCompactWidth,
                maxSheetHeight = maxSheetHeight,
                modifier = Modifier.align(if (isCompactWidth) Alignment.BottomCenter else Alignment.CenterStart)
            )

            // 6. Bottom Contextual Area: Replay Bar OR Geometry Tool Strip OR Expandable Graph Sheet
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
                        .padding(bottom = if (isImmersive) 14.dp else 82.dp)
                )
            } else if (!isImmersive) {
                if (subMode == Geo2DSubMode.GEOMETRY) {
                    Geo2DGeometryToolStrip(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    Geo2DExpandableGraphSheet(
                        uiState = uiState,
                        viewModel = viewModel,
                        expanded = graphSheetExpanded,
                        maxExpandedHeight = maxSheetHeight,
                        onToggleExpanded = { graphSheetExpanded = !graphSheetExpanded },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // 7. Floating Toast
            ToastBanner(
                message = uiState.toastMessage,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 8. 2D Save / Export Modal
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

            // 9. Automatic Variation Table (Bảng biến thiên) Dialog
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
}

@Composable
private fun Geo2DCanvasViewport(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    onSizeChanged: (IntSize) -> Unit,
    onEmptyCanvasTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val latestState by rememberUpdatedState(uiState)
    val latestEmptyTap by rememberUpdatedState(onEmptyCanvasTap)
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
                        val prevToast = viewModel.uiState.value.toastMessage
                        val prevTrace = viewModel.uiState.value.traceX
                        viewModel.onCanvasTap(downPos.x, downPos.y, w, h, density)
                        val after = viewModel.uiState.value
                        if (
                            latestState.activeTool == Geo2DTool.PAN_SELECT &&
                            prevTrace == null &&
                            after.traceX == null &&
                            after.toastMessage == prevToast
                        ) {
                            latestEmptyTap()
                        }
                    }
                }
            }
            .testTag("geo2d_canvas")
    ) {
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
    subMode: Geo2DSubMode,
    isImmersive: Boolean,
    onSelectSubMode: (Geo2DSubMode) -> Unit,
    onToggleImmersive: () -> Unit,
    viewModel: Geo2DViewModel,
    modifier: Modifier = Modifier
) {
    val palette = ActiveThemeState.current
    val isGlass = palette.liquidGlass.enabled

    AnimatedVisibility(
        visible = !isImmersive,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compact Segmented Control: Hình học 2D | Đồ thị hàm số
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            fallbackBg = SlateDark.copy(alpha = 0.92f),
                            fallbackBorder = SlateBorder.copy(alpha = 0.8f)
                        )
                        .padding(3.dp)
                        .testTag("geo2d_mode_segmented"),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Geo2DSubMode.entries.forEach { mode ->
                        val selected = subMode == mode
                        val tint = when {
                            selected && isGlass && !palette.isDark -> SlateTextPrimary
                            selected -> Color.White
                            else -> SlateTextSecondary
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 36.dp)
                                .liquidGlassActiveBubble(
                                    active = selected,
                                    shape = CircleShape,
                                    fallbackActiveColor = if (mode == Geo2DSubMode.GRAPH) CyanAccent else EmeraldDark
                                )
                                .clip(CircleShape)
                                .clickable { onSelectSubMode(mode) }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                .testTag("geo2d_submode_${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Compact Action Pill: Undo, Redo, Reset, Save, Fullscreen
                Row(
                    modifier = Modifier
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            fallbackBg = SlateDark.copy(alpha = 0.92f),
                            fallbackBorder = SlateBorder.copy(alpha = 0.8f)
                        )
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopIconBtn(Icons.AutoMirrored.Filled.Undo, "Hoàn tác", enabled = uiState.canUndo) { viewModel.undo() }
                    TopIconBtn(Icons.AutoMirrored.Filled.Redo, "Làm lại", enabled = uiState.canRedo) { viewModel.redo() }
                    TopIconBtn(Icons.Default.MyLocation, "Về tâm O(0;0)") { viewModel.resetView() }
                    TopIconBtn(Icons.Default.SaveAlt, "Lưu / Xuất 2D", tint = EmeraldAccent) { viewModel.openSaveModal() }
                    TopIconBtn(Icons.Default.Fullscreen, "Toàn màn hình", testTag = "geo2d_fullscreen_btn") { onToggleImmersive() }
                }
            }

            // Live Trace Readout Pill (when tracing graph)
            if (uiState.traceX != null && uiState.traceResults.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(14.dp),
                            fallbackBg = SlateDeepest.copy(alpha = 0.92f),
                            accentBorder = CyanAccent.copy(alpha = 0.6f)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
            .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                pillShape = RoundedCornerShape(24.dp),
                fallbackBg = SlateDark.copy(alpha = 0.84f),
                fallbackBorder = SlateBorder.copy(alpha = 0.7f)
            )
            .padding(vertical = 5.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        QuickChip2D("Oxy", Icons.Default.GridOn, s.showAxes) { viewModel.toggleAxes() }
        QuickChip2D("Hút", Icons.Default.Straighten, s.snapToGrid) { viewModel.toggleSnap() }
        QuickChip2D("Đo", Icons.Default.SquareFoot, s.showMeasurements) { viewModel.toggleMeasurements() }
        HorizontalDivider(color = SlateBorder.copy(alpha = 0.65f), modifier = Modifier.width(22.dp).padding(vertical = 2.dp))
        IconButton(onClick = { viewModel.stepZoom(true) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Phóng to", tint = SlateTextPrimary, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = { viewModel.stepZoom(false) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Thu nhỏ", tint = SlateTextPrimary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun QuickChip2D(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) EmeraldDark.copy(alpha = 0.36f) else Color.Transparent)
            .border(1.dp, if (active) EmeraldAccent.copy(alpha = 0.65f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (active) EmeraldAccent else SlateTextSecondary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.5.sp, color = if (active) SlateTextPrimary else SlateTextSecondary)
    }
}

/**
 * Compact horizontal Tool Strip for 2D Geometry Mode (floating cleanly above the Bottom Navigation Bar).
 */
@Composable
private fun Geo2DGeometryToolStrip(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    modifier: Modifier = Modifier
) {
    val palette = ActiveThemeState.current
    val isGlass = palette.liquidGlass.enabled
    val geometryTools = remember {
        Geo2DTool.entries.filter { it != Geo2DTool.TRACE_GRAPH }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 78.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Active tool hint pill
        AnimatedVisibility(visible = uiState.activeTool != Geo2DTool.PAN_SELECT && uiState.activeTab == null) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SlateDeepest.copy(alpha = 0.92f))
                    .border(1.dp, EmeraldAccent.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
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
                            .clip(CircleShape)
                            .background(EmeraldDark)
                            .clickable { viewModel.finishPendingPolygon() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
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

        // Scrollable Tool Strip + Compact Sheet Triggers (Hình mẫu, Đo đạc, Cài đặt Oxy)
        AnimatedVisibility(visible = uiState.activeTab == null) {
            Row(
                modifier = Modifier
                    .liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        elevated = true,
                        fallbackBg = SlateDark.copy(alpha = 0.94f),
                        fallbackBorder = SlateBorder.copy(alpha = 0.8f)
                    )
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("geo2d_geometry_tool_strip"),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                geometryTools.forEach { tool ->
                    val selected = uiState.activeTool == tool
                    val tint = when {
                        selected && isGlass && !palette.isDark -> SlateTextPrimary
                        selected -> Color.White
                        isGlass && palette.isDark -> Color.White.copy(alpha = 0.84f)
                        else -> SlateTextLight
                    }
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .liquidGlassActiveBubble(
                                active = selected,
                                shape = CircleShape,
                                fallbackActiveColor = EmeraldDark
                            )
                            .clip(CircleShape)
                            .clickable { viewModel.setTool(tool) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("geo2d_tool_${tool.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = iconFor2DTool(tool),
                            contentDescription = tool.shortLabel,
                            tint = tint,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = tool.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(modifier = Modifier.width(1.dp).height(18.dp).background(SlateBorder.copy(alpha = 0.7f)))

                // Contextual sheet openers (Hình mẫu / Đo đạc / Cài đặt / Phát lại)
                Geo2DMiniSheetChip("Hình mẫu", Icons.Default.Category, CyanAccent, "geo2d_tab_presets") {
                    viewModel.toggleTab(Geo2DTab.PRESETS)
                }
                Geo2DMiniSheetChip("Đo đạc", Icons.Default.Straighten, AmberAccent, "geo2d_tab_objects") {
                    viewModel.toggleTab(Geo2DTab.OBJECTS)
                }
                Geo2DMiniSheetChip("Phát lại", Icons.Default.PlayCircleOutline, IndigoBright, "replay_2d_btn") {
                    viewModel.startReplay()
                }
                Geo2DMiniSheetChip("Tùy chọn", Icons.Default.Tune, SlateTextPrimary, "geo2d_tab_settings") {
                    viewModel.toggleTab(Geo2DTab.SETTINGS)
                }
            }
        }
    }
}

@Composable
private fun Geo2DMiniSheetChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Expandable Bottom Sheet for 2D Function Graphing & Analysis Mode:
 * - Collapsed (Peek) state: Compact 1-line quick y = f(x) input + Trace toggle + Expand pill (keeps graph wide open!)
 * - Expanded state: Full Geo2DFunctionsPane (function list, library, parameter m, derivative f'(x), key points, asymptotes, BBT)
 */
@Composable
private fun Geo2DExpandableGraphSheet(
    uiState: Geo2DUiState,
    viewModel: Geo2DViewModel,
    expanded: Boolean,
    maxExpandedHeight: Dp,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quickExpr by remember { mutableStateOf("") }
    val fnCount = uiState.snapshot.functions.count { (it.visible || it.showDerivative) && it.expression.isNotBlank() }
    val isTracing = uiState.activeTool == Geo2DTool.TRACE_GRAPH
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    val headerBg = if (isGlass) SlateDeepest.copy(alpha = 0.42f) else SlateDeepest

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 78.dp)
            .animateContentSize()
            .then(if (expanded) Modifier.heightIn(max = maxExpandedHeight) else Modifier)
            .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                elevated = true,
                fallbackBg = SlateDark.copy(alpha = 0.96f),
                fallbackBorder = SlateBorder.copy(alpha = 0.85f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .testTag("geo2d_graph_expandable_sheet")
    ) {
        // Drag Handle + Header Toggle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SlateMuted)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (expanded) "Khảo sát hàm số & Bảng biến thiên" else "Đồ thị ($fnCount hàm) · Khảo sát & BBT",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Quick Trace toggle right in the header
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isTracing) CyanAccent.copy(alpha = 0.22f) else SlateSurface)
                        .border(1.dp, if (isTracing) CyanAccent else SlateBorder, CircleShape)
                        .clickable {
                            viewModel.setTool(if (isTracing) Geo2DTool.PAN_SELECT else Geo2DTool.TRACE_GRAPH)
                            if (!isTracing && expanded) onToggleExpanded()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("geo2d_tool_trace_graph")
                ) {
                    Text(
                        text = if (isTracing) "Đang dò" else "Dò x",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTracing) CyanAccent else SlateTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Thu gọn bảng đồ thị" else "Mở rộng bảng khảo sát hàm số",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (!expanded) {
            // Peek State: Slim 1-line quick y = f(x) composer so graph stays unobstructed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = quickExpr,
                    onValueChange = { quickExpr = it },
                    placeholder = { Text("Nhập nhanh: x^3 - 3x, e^x, sin(x)...", color = SlateTextSecondary, fontSize = 11.5.sp) },
                    prefix = { Text("y = ", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, color = SlateTextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (quickExpr.isNotBlank()) {
                                viewModel.addFunction(quickExpr)
                                quickExpr = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SlateDeepest.copy(alpha = 0.7f),
                        unfocusedContainerColor = SlateDeepest.copy(alpha = 0.6f),
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SlateBorder
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("geo2d_quick_func_input")
                )
                Button(
                    onClick = {
                        if (quickExpr.isNotBlank()) {
                            viewModel.addFunction(quickExpr)
                            quickExpr = ""
                        } else {
                            onToggleExpanded()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(if (quickExpr.isNotBlank()) Icons.Default.Add else Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (quickExpr.isNotBlank()) "Vẽ" else "Mở", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            HorizontalDivider(color = SlateSurface)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Analysis Display Chips (Điểm đặc biệt, Tiệm cận, Tiếp tuyến)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GraphFeatureChip("Nghiệm & Cực trị", uiState.snapshot.showKeyGraphPoints) {
                        viewModel.toggleKeyGraphPoints()
                    }
                    GraphFeatureChip("Tiệm cận", uiState.snapshot.showAsymptotes) {
                        viewModel.toggleAsymptotes()
                    }
                    GraphFeatureChip("Tiếp tuyến khi dò", uiState.snapshot.showTangentOnTrace) {
                        viewModel.toggleTangentOnTrace()
                    }
                }
                Geo2DFunctionsPane(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun GraphFeatureChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) CyanAccent.copy(alpha = 0.2f) else SlateSurface)
            .border(1.dp, if (active) CyanAccent else SlateBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Check else Icons.Default.VisibilityOff,
            contentDescription = null,
            tint = if (active) CyanAccent else SlateTextSecondary,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CyanAccent else SlateTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
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

    AnimatedVisibility(
        visible = activeTab != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        if (activeTab == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(22.dp)
        val containerMod = if (isCompactWidth) {
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 78.dp)
                .heightIn(min = 220.dp, max = maxSheetHeight)
        } else {
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 66.dp, bottom = 84.dp)
                .width(360.dp)
                .fillMaxHeight()
        }

        val isGlass2D = ActiveThemeState.current.liquidGlass.enabled
        val headerBg2D = if (isGlass2D) SlateDeepest.copy(alpha = 0.42f) else SlateDeepest

        Column(
            modifier = containerMod
                .liquidGlassSurface(
                    shape = shape,
                    elevated = true,
                    fallbackBg = SlateDark.copy(alpha = 0.96f),
                    fallbackBorder = SlateBorder.copy(alpha = 0.85f)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            if (isCompactWidth) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg2D)
                        .clickable { viewModel.closeTab() }
                        .padding(top = 8.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(SlateMuted)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg2D)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(activeTab.title, style = MaterialTheme.typography.titleSmall, color = EmeraldAccent, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, CircleShape)
                        .clickable { viewModel.closeTab() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
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
