package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AiAssistantViewModel
import com.example.ai.AiDrawTarget
import com.example.ai.AiResponseParser
import com.example.ai.AiWorkspaceScreen
import com.example.ai.AuthToastBanner
import com.example.ai.configureAppCheck
import com.example.ai.findActivity
import com.example.docs.MathDocumentsScreen
import com.example.docs.PdfBookRenderer
import com.example.geo2d.Geo2DPersistence
import com.example.geo2d.Geo2DStudioScreen
import com.example.geo2d.Geo2DViewModel
import com.example.geo2d.MathWorkspace
import com.example.settings.AppSettingsScreen
import com.example.settings.SettingsSubPage
import com.example.ui.BottomStudioDock
import com.example.ui.ConstructionReplayBar
import com.example.ui.FloatingPillBottomNavBar
import com.example.ui.GeometryCanvasView
import com.example.ui.ImmersiveRestorePill
import com.example.ui.RightCanvasQuickControls
import com.example.ui.StudioInspectorSheet
import com.example.ui.ToastBanner
import com.example.ui.TopStudioHeader
import com.example.ui.panes.SaveExportModal
import com.example.ui.theme.AppThemeUiState
import com.example.ui.theme.AppThemeViewModel
import com.example.ui.theme.Geo3DTheme
import com.example.ui.theme.LocalLiquidGlassBackdrop
import com.example.ui.theme.ThemePaletteResolver
import com.example.ui.theme.liquidGlassBackdropSource
import com.example.ui.theme.rememberLiquidGlassBackdropState
import com.example.update.AppUpdateViewModel
import com.example.update.UpdateAvailableBanner
import com.example.update.UpdateDetailsDialog
import com.example.viewmodel.GeometryViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val themeViewModel: AppThemeViewModel = viewModel()
            val themeUiState by themeViewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDark = ThemePaletteResolver.resolveIsDark(themeUiState.config, systemDark)

            DisposableEffect(useDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (useDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            Geo3DTheme(config = themeUiState.config) {
                ToolToanRootScreen(
                    themeViewModel = themeViewModel,
                    themeUiState = themeUiState
                )
            }
        }
    }
}

@Composable
fun ToolToanRootScreen(
    themeViewModel: AppThemeViewModel,
    themeUiState: AppThemeUiState,
    geometryViewModel: GeometryViewModel = viewModel(),
    geo2DViewModel: Geo2DViewModel = viewModel(),
    updateViewModel: AppUpdateViewModel = viewModel(),
    aiViewModel: AiAssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val workspacePrefs = remember(appContext) { Geo2DPersistence(appContext) }
    var activeWorkspace by remember { mutableStateOf(workspacePrefs.loadLastWorkspace()) }
    var rememberLastWorkspace by remember { mutableStateOf(workspacePrefs.isRememberLastWorkspaceEnabled()) }
    var isImmersive by remember { mutableStateOf(false) }
    var settingsSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }
    var pending2DExpr by remember { mutableStateOf<String?>(null) }
    var pendingPdfPage by remember { mutableStateOf<Int?>(null) }

    // Retain the 388-page PDF renderer + 36MB RGB_565 page cache across tab switches for 0ms instant return!
    val sharedPdfRenderer = remember(appContext) { PdfBookRenderer(appContext) }
    DisposableEffect(sharedPdfRenderer) {
        onDispose { sharedPdfRenderer.close() }
    }

    // Collect ONLY the lightweight update badge boolean here — NEVER collect heavy 3D/2D/AI states at root!
    val hasUpdateBadge by remember(updateViewModel) {
        updateViewModel.uiState.map { it.hasUpdate }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(Unit) {
        aiViewModel.attemptSilentGoogleSignIn(context)
    }

    val onSelectWorkspace: (MathWorkspace) -> Unit = remember(workspacePrefs) {
        { target ->
            if (target != activeWorkspace) {
                activeWorkspace = target
                isImmersive = false
                if (target != MathWorkspace.SETTINGS) {
                    settingsSubPage = null
                }
                workspacePrefs.saveLastWorkspace(target)
            } else if (target == MathWorkspace.SETTINGS && settingsSubPage != null) {
                settingsSubPage = null
            }
        }
    }

    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val supportsImmersive = activeWorkspace == MathWorkspace.SPACE_3D ||
        activeWorkspace == MathWorkspace.PLANE_2D ||
        activeWorkspace == MathWorkspace.DOCUMENTS

    val showBottomNav = !isImmersive &&
        !(activeWorkspace == MathWorkspace.SETTINGS && settingsSubPage != null) &&
        !isImeVisible

    // Single shared non-nested GPU backdrop: each workspace's raw Canvas or static Aurora background
    // records into this layer ONCE (never wrapping floating glass chrome -> zero draw feedback loops!)
    val sharedBackdrop = rememberLiquidGlassBackdropState()
    LaunchedEffect(activeWorkspace) {
        sharedBackdrop.resetStaticCache()
    }

    CompositionLocalProvider(LocalLiquidGlassBackdrop provides sharedBackdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: Active Workspace Screen (state-isolated per tab!)
            when (activeWorkspace) {
                MathWorkspace.SPACE_3D -> GeometryStudioScreen(
                    isImmersive = isImmersive,
                    onToggleImmersive = { isImmersive = !isImmersive },
                    viewModel = geometryViewModel
                )
                MathWorkspace.PLANE_2D -> Geo2DStudioScreen(
                    isImmersive = isImmersive,
                    onToggleImmersive = { isImmersive = !isImmersive },
                    pendingPlotExpr = pending2DExpr,
                    onConsumePendingPlot = { pending2DExpr = null },
                    viewModel = geo2DViewModel
                )
                MathWorkspace.DOCUMENTS -> MathDocumentsScreen(
                    isImmersive = isImmersive,
                    onToggleImmersive = { isImmersive = !isImmersive },
                    onSwitchWorkspace = onSelectWorkspace,
                    pendingOpenPage = pendingPdfPage,
                    onConsumePendingPage = { pendingPdfPage = null },
                    onAskAiAboutPage = { pageNum ->
                        aiViewModel.prepareAskAboutPdfPage(pageNum)
                        onSelectWorkspace(MathWorkspace.AI)
                    },
                    sharedPdfRenderer = sharedPdfRenderer
                )
                MathWorkspace.AI -> AiWorkspaceScreen(
                    aiViewModel = aiViewModel,
                    onSwitchWorkspace = onSelectWorkspace,
                    onOpenPdfPage = { pageNum ->
                        pendingPdfPage = pageNum
                        onSelectWorkspace(MathWorkspace.DOCUMENTS)
                    },
                    onApplyDrawingPlan = { plan ->
                        when (plan.target) {
                            AiDrawTarget.SPACE_3D -> {
                                plan.scene3D?.let { spec ->
                                    val snap3D = AiResponseParser.buildGeometrySnapshot(spec)
                                    geometryViewModel.applyAiSnapshot(snap3D, plan.title)
                                    onSelectWorkspace(MathWorkspace.SPACE_3D)
                                }
                            }
                            AiDrawTarget.PLANE_2D -> {
                                plan.scene2D?.let { spec ->
                                    val snap2D = AiResponseParser.buildGeo2DSnapshot(spec)
                                    geo2DViewModel.applyAiSnapshot(snap2D, plan.title)
                                    onSelectWorkspace(MathWorkspace.PLANE_2D)
                                }
                            }
                        }
                    },
                    onOpenAiSettings = {
                        activeWorkspace = MathWorkspace.SETTINGS
                        settingsSubPage = SettingsSubPage.AI_PROVIDERS
                        isImmersive = false
                        workspacePrefs.saveLastWorkspace(MathWorkspace.SETTINGS)
                    }
                )
                MathWorkspace.SETTINGS -> SettingsWorkspaceHost(
                    activeSubPage = settingsSubPage,
                    onSelectSubPage = { settingsSubPage = it },
                    themeViewModel = themeViewModel,
                    themeUiState = themeUiState,
                    geometryViewModel = geometryViewModel,
                    geo2DViewModel = geo2DViewModel,
                    updateViewModel = updateViewModel,
                    aiViewModel = aiViewModel,
                    rememberLastWorkspace = rememberLastWorkspace,
                    onToggleRememberLastWorkspace = { enabled ->
                        rememberLastWorkspace = enabled
                        workspacePrefs.setRememberLastWorkspace(enabled)
                    },
                    onSwitchWorkspace = onSelectWorkspace
                )
            }

            // Layer 2: Unified Floating Pill Bottom Navigation Bar & Immersive Restore Pill
            FloatingPillBottomNavBar(
                current = activeWorkspace,
                hasUpdateBadge = hasUpdateBadge,
                visible = showBottomNav,
                onSelect = onSelectWorkspace,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            ImmersiveRestorePill(
                visible = isImmersive && supportsImmersive,
                onExitImmersive = { isImmersive = false },
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            // Layer 3: Self-isolated Global Overlays (never recompose active workspace!)
            RootAuthToastHost(
                aiViewModel = aiViewModel,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            RootUpdateOverlayHost(
                updateViewModel = updateViewModel,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun SettingsWorkspaceHost(
    activeSubPage: SettingsSubPage?,
    onSelectSubPage: (SettingsSubPage?) -> Unit,
    themeViewModel: AppThemeViewModel,
    themeUiState: AppThemeUiState,
    geometryViewModel: GeometryViewModel,
    geo2DViewModel: Geo2DViewModel,
    updateViewModel: AppUpdateViewModel,
    aiViewModel: AiAssistantViewModel,
    rememberLastWorkspace: Boolean,
    onToggleRememberLastWorkspace: (Boolean) -> Unit,
    onSwitchWorkspace: (MathWorkspace) -> Unit
) {
    val context = LocalContext.current
    val geoUiState by geometryViewModel.uiState.collectAsStateWithLifecycle()
    val geo2DUiState by geo2DViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val aiUiState by aiViewModel.uiState.collectAsStateWithLifecycle()

    AppSettingsScreen(
        activeSubPage = activeSubPage,
        onSelectSubPage = onSelectSubPage,
        themeConfig = themeUiState.config,
        onSelectThemeMode = { themeViewModel.setThemeMode(it) },
        onSelectThemePreset = { themeViewModel.selectPreset(it) },
        onSelectAccentColor = { themeViewModel.selectAccentColor(it) },
        onToggleLiquidGlass = { themeViewModel.toggleLiquidGlass(it) },
        onApplyLiquidGlassStyle = { themeViewModel.applyLiquidGlassStyle(it) },
        onUpdateLiquidGlass = { blur, ref, disp, t, s, r, b, sh, hc ->
            themeViewModel.updateLiquidGlass(
                blurRadius = blur,
                refraction = ref,
                chromaticDispersion = disp,
                translucency = t,
                specularGlow = s,
                refractionTint = r,
                borderIntensity = b,
                dynamicShimmer = sh,
                highContrastText = hc
            )
        },
        onResetThemeDefault = { themeViewModel.resetToDefault() },
        sensitivity = geoUiState.snapshot.sensitivity,
        geometryViewModel = geometryViewModel,
        sensitivity2D = geo2DUiState.snapshot.sensitivity,
        geo2DViewModel = geo2DViewModel,
        rememberLastWorkspace = rememberLastWorkspace,
        onToggleRememberLastWorkspace = onToggleRememberLastWorkspace,
        exportFolderUri = geoUiState.exportFolderUri ?: geo2DUiState.exportFolderUri,
        exportFolderLabel = geoUiState.exportFolderLabel ?: geo2DUiState.exportFolderLabel,
        exportTransparentPng = geoUiState.exportTransparentBg,
        onSetExportFolder = { uri, label ->
            geometryViewModel.setExportFolder(uri, label)
            geo2DViewModel.setExportFolder(uri, label)
        },
        onClearExportFolder = {
            geometryViewModel.clearExportFolder()
            geo2DViewModel.clearExportFolder()
        },
        onToggleExportTransparentPng = { desired ->
            if (geoUiState.exportTransparentBg != desired) {
                geometryViewModel.toggleExportTransparentBg()
            }
            if (geo2DUiState.exportTransparentBg != desired) {
                geo2DViewModel.toggleExportTransparentBg()
            }
        },
        updateUiState = updateUiState,
        onCheckUpdateNow = { updateViewModel.checkForUpdates(isAutoStartup = false) },
        onSaveUpdateRepoAndCheck = { updateViewModel.saveRepoAndCheck(it) },
        onSkipUpdateVersion = { updateViewModel.skipCurrentAvailableVersion() },
        aiUiState = aiUiState,
        onSelectAiProvider = { aiViewModel.selectProvider(it) },
        onSelectCustomAiEndpoint = { aiViewModel.selectCustomEndpoint(it) },
        onCreateCustomAiEndpoint = { name, baseUrl, key, verify ->
            aiViewModel.createCustomEndpoint(name, baseUrl, key, verify)
        },
        onUpdateCustomAiEndpoint = { id, name, baseUrl, key, verify ->
            aiViewModel.updateCustomEndpoint(id, name, baseUrl, key, verify)
        },
        onDeleteCustomAiEndpoint = { aiViewModel.deleteCustomEndpoint(it) },
        onSignInWithGoogle = {
            context.findActivity()?.let { aiViewModel.signInWithGoogle(it) }
        },
        onSignOutGoogle = { aiViewModel.signOutGoogle(context) },
        onTestGoogleAi = { aiViewModel.testGoogleGeminiConnection() },
        onSaveAndVerifyAiKey = { key, model, baseUrl ->
            aiViewModel.saveAndVerifyApiKey(key, model, baseUrl)
        },
        onSaveAiKeyOnly = { key, model, baseUrl ->
            aiViewModel.saveApiKey(key, model, baseUrl)
        },
        onSelectAiModel = { aiViewModel.selectModel(it) },
        onSyncAiModels = { overrideKey, overrideUrl ->
            aiViewModel.syncAccountModels(overrideKey, overrideUrl)
        },
        onProbeAllAiModels = { overrideKey, overrideUrl, subset ->
            aiViewModel.probeAllModels(overrideKey, overrideUrl, subset)
        },
        onRefreshAiTokens = { aiViewModel.refreshTokenSync() },
        onAddCustomAiModel = { aiViewModel.addCustomModel(it) },
        onRemoveCustomAiModel = { aiViewModel.removeCustomModel(it) },
        onClearAiKey = { aiViewModel.clearApiKey() },
        onSwitchWorkspace = onSwitchWorkspace
    )
}

@Composable
private fun RootAuthToastHost(
    aiViewModel: AiAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val authToast by remember(aiViewModel) {
        aiViewModel.uiState.map { it.authToast }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    AuthToastBanner(
        event = authToast,
        onDismiss = { aiViewModel.dismissAuthToast() },
        modifier = modifier
    )
}

@Composable
private fun RootUpdateOverlayHost(
    updateViewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier
) {
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()

    UpdateAvailableBanner(
        uiState = updateUiState,
        onOpenDetails = { updateViewModel.openUpdateDialog() },
        onDismiss = { updateViewModel.dismissBannerForNow() },
        modifier = modifier
    )

    UpdateDetailsDialog(
        uiState = updateUiState,
        onCheckNow = { updateViewModel.checkForUpdates(isAutoStartup = false) },
        onSaveRepoAndCheck = { updateViewModel.saveRepoAndCheck(it) },
        onSkipVersion = { updateViewModel.skipCurrentAvailableVersion() },
        onDismiss = { updateViewModel.closeUpdateDialog() }
    )
}

@Composable
fun GeometryStudioScreen(
    isImmersive: Boolean = false,
    onToggleImmersive: () -> Unit = {},
    viewModel: GeometryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    BackHandler(enabled = uiState.replayStep != null || uiState.activeLeftPane != null || isImmersive) {
        when {
            uiState.replayStep != null -> viewModel.stopReplay(restoreFull = true)
            uiState.activeLeftPane != null -> viewModel.closeLeftPane()
            isImmersive -> onToggleImmersive()
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
            viewModel.showToast("Lỗi lưu ảnh PNG!")
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
            viewModel.showToast("Lỗi lưu ảnh Vector (.SVG)!")
        }
    }

    fun writeObjToUri(uri: Uri, locationSuffix: String = "") {
        runCatching {
            val objText = viewModel.exportObjModel()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write(objText)
                }
            } ?: error("Không thể mở luồng ghi tệp")
        }.onSuccess {
            viewModel.closeSaveModal()
            viewModel.showToast("Đã lưu mô hình ${viewModel.sanitizedFileName()}.obj$locationSuffix")
        }.onFailure {
            viewModel.showToast("Lỗi lưu mô hình 3D (.OBJ)!")
        }
    }

    fun writeJsonToUri(uri: Uri, locationSuffix: String = "") {
        runCatching {
            val jsonText = viewModel.exportSnapshotJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write(jsonText)
                }
            } ?: error("Không thể mở luồng ghi tệp")
        }.onSuccess {
            viewModel.closeSaveModal()
            viewModel.showToast("Đã lưu file ${viewModel.sanitizedFileName()}.json$locationSuffix")
        }.onFailure {
            viewModel.showToast("Lỗi lưu file dự án (.JSON)!")
        }
    }

    // SAF Folder Picker (Pick default external save directory — zero storage permissions required)
    val pickExportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            val label = formatTreeUriLabel(treeUri)
            viewModel.setExportFolder(treeUri.toString(), label)
        }
    }

    // SAF PNG Export Launcher (Pick location per file)
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) writePngToUri(uri)
    }

    // SAF Vector SVG Export Launcher
    val exportSvgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        if (uri != null) writeSvgToUri(uri)
    }

    // SAF 3D Model (.OBJ) Export Launcher (Pick location per file)
    val exportObjLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) writeObjToUri(uri)
    }

    // SAF JSON Project Export Launcher (Pick location per file)
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) writeJsonToUri(uri)
    }

    // SAF JSON Import Launcher
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                var displayName: String? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) displayName = cursor.getString(0)
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (content != null) {
                    viewModel.importSnapshotJson(content, displayName)
                }
            }.onFailure {
                viewModel.showToast("Không thể đọc file JSON!")
            }
        }
    }

    fun handleExport(
        forcePicker: Boolean,
        mimeType: String,
        extension: String,
        onDirectUri: (Uri, String) -> Unit,
        launchPicker: (String) -> Unit
    ) {
        val fileName = "${viewModel.sanitizedFileName()}.$extension"
        val folderUri = uiState.exportFolderUri
        val folderLabel = uiState.exportFolderLabel
        if (!forcePicker && !folderUri.isNullOrBlank()) {
            val createdUri = createDocumentInTree(context, folderUri, mimeType, fileName)
            if (createdUri != null) {
                onDirectUri(createdUri, if (folderLabel != null) " vào $folderLabel" else "")
                return
            }
            // Persisted folder permission expired or folder deleted -> fall back to system picker
            viewModel.clearExportFolder()
        }
        launchPicker(fileName)
    }

    val sharedBackdrop = LocalLiquidGlassBackdrop.current
    val fallbackBackdrop = rememberLiquidGlassBackdropState()
    val backdropState = sharedBackdrop ?: fallbackBackdrop

    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdropState) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactWidth = maxWidth < 600.dp
            val maxSheetHeight = (maxHeight * 0.52f).coerceIn(260.dp, 460.dp)

            // 1. Full-bleed Interactive 3D Viewport (single GPU backdrop source for 3D HUD & root Bottom Nav!)
            GeometryCanvasView(
                uiState = uiState,
                viewModel = viewModel,
                onCanvasSizeChanged = { canvasSize = it },
                onEmptyCanvasTap = {
                    if (uiState.activeLeftPane == null) {
                        onToggleImmersive()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropSource(backdropState)
            )

            // 2. Canvas-First Minimal Top Studio Header (Shape Pill + Camera Presets + Save + Fullscreen)
            TopStudioHeader(
                uiState = uiState,
                viewModel = viewModel,
                isCompactWidth = isCompactWidth,
                isImmersive = isImmersive,
                onToggleImmersive = onToggleImmersive,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 3. Minimal Right Floating Quick Controls (Oxyz, Snap, Auto-Rotate, Zoom +/-)
            RightCanvasQuickControls(
                uiState = uiState,
                viewModel = viewModel,
                visible = !isImmersive && (!isCompactWidth || uiState.activeLeftPane == null),
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // 3b. Tap anywhere on empty area outside open panel to close quickly
            if (uiState.activeLeftPane != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.closeLeftPane()
                        }
                        .testTag("geo3d_dismiss_scrim")
                )
            }

            // 5. Adaptive Inspector Sheet (Bottom Sheet on mobile, Left Side Panel on tablet)
            StudioInspectorSheet(
                uiState = uiState,
                viewModel = viewModel,
                isCompactWidth = isCompactWidth,
                maxSheetHeight = maxSheetHeight,
                onImportJsonClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.align(if (isCompactWidth) Alignment.BottomCenter else Alignment.CenterStart)
            )

            // 6. Collapsible 3-Action Bottom Dock OR Step-by-Step Construction Replay Bar
            val replayStep = uiState.replayStep
            if (replayStep != null) {
                ConstructionReplayBar(
                    step = replayStep,
                    totalSteps = uiState.replayTotalSteps,
                    stepLabel = uiState.replayStepLabel,
                    isPlaying = uiState.replayPlaying,
                    onPrev = { viewModel.stepReplay(-1) },
                    onTogglePlay = { viewModel.toggleReplayPlay() },
                    onNext = { viewModel.stepReplay(1) },
                    onClose = { viewModel.stopReplay(restoreFull = true) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = if (isImmersive) 16.dp else 82.dp)
                )
            } else {
                BottomStudioDock(
                    uiState = uiState,
                    viewModel = viewModel,
                    isImmersive = isImmersive,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 7. Floating Toast Notification
            ToastBanner(
                message = uiState.toastMessage,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 8. Save / Export Modal Dialog
            SaveExportModal(
                uiState = uiState,
                viewModel = viewModel,
                onPickExportFolderClick = { pickExportFolderLauncher.launch(null) },
                onExportPngClick = { forcePicker ->
                    handleExport(
                        forcePicker = forcePicker,
                        mimeType = "image/png",
                        extension = "png",
                        onDirectUri = ::writePngToUri,
                        launchPicker = { exportPngLauncher.launch(it) }
                    )
                },
                onExportSvgClick = { forcePicker ->
                    handleExport(
                        forcePicker = forcePicker,
                        mimeType = "image/svg+xml",
                        extension = "svg",
                        onDirectUri = ::writeSvgToUri,
                        launchPicker = { exportSvgLauncher.launch(it) }
                    )
                },
                onExportObjClick = { forcePicker ->
                    handleExport(
                        forcePicker = forcePicker,
                        mimeType = "application/octet-stream",
                        extension = "obj",
                        onDirectUri = ::writeObjToUri,
                        launchPicker = { exportObjLauncher.launch(it) }
                    )
                },
                onExportJsonClick = { forcePicker ->
                    handleExport(
                        forcePicker = forcePicker,
                        mimeType = "application/json",
                        extension = "json",
                        onDirectUri = ::writeJsonToUri,
                        launchPicker = { exportJsonLauncher.launch(it) }
                    )
                },
                onImportJsonClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
        }
    }
}

private fun createDocumentInTree(
    context: Context,
    treeUriString: String,
    mimeType: String,
    fileName: String
): Uri? {
    return runCatching {
        val treeUri = Uri.parse(treeUriString)
        val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        DocumentsContract.createDocument(context.contentResolver, parentDocUri, mimeType, fileName)
    }.getOrNull()
}

private fun formatTreeUriLabel(treeUri: Uri): String {
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
