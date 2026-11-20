package com.example

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BottomToolDock
import com.example.ui.GeometryCanvasView
import com.example.ui.LeftDockAndDrawer
import com.example.ui.ToastBanner
import com.example.ui.TopRightMetricsBar
import com.example.ui.panes.SaveExportModal
import com.example.ui.theme.Geo3DTheme
import com.example.viewmodel.GeometryViewModel
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            Geo3DTheme {
                GeometryStudioScreen()
            }
        }
    }
}

@Composable
fun GeometryStudioScreen(
    viewModel: GeometryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // SAF PNG Export Launcher (Zero permissions required)
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bmp = viewModel.renderExportBitmap(
                    widthPx = canvasSize.width.coerceAtLeast(1080),
                    heightPx = canvasSize.height.coerceAtLeast(1080),
                    density = density
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
            }.onSuccess {
                viewModel.closeSaveModal()
                viewModel.showToast("Đã xuất ảnh ${viewModel.sanitizedFileName()}.png!")
            }.onFailure {
                viewModel.showToast("Lỗi xuất ảnh PNG!")
            }
        }
    }

    // SAF JSON Export Launcher
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonText = viewModel.exportSnapshotJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                        writer.write(jsonText)
                    }
                }
            }.onSuccess {
                viewModel.closeSaveModal()
                viewModel.showToast("Đã xuất file ${viewModel.sanitizedFileName()}.json!")
            }.onFailure {
                viewModel.showToast("Lỗi xuất file JSON!")
            }
        }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactWidth = maxWidth < 600.dp
        val drawerMaxWidth = (maxWidth - 78.dp).coerceIn(250.dp, 348.dp)

        // 1. Full-bleed Interactive 3D Viewport
        GeometryCanvasView(
            uiState = uiState,
            viewModel = viewModel,
            onCanvasSizeChanged = { canvasSize = it },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top-Right Live Metrics & Camera Quick Actions
        TopRightMetricsBar(
            uiState = uiState,
            viewModel = viewModel,
            isCompactWidth = isCompactWidth,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // 3. Left Icon Rail & Slide-Out Drawer
        LeftDockAndDrawer(
            uiState = uiState,
            viewModel = viewModel,
            drawerMaxWidth = drawerMaxWidth,
            onImportJsonClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // 4. Bottom Floating Collapsible Dock & Drag Constraint Sub-Bar
        BottomToolDock(
            uiState = uiState,
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 5. Floating Toast Notification
        ToastBanner(
            message = uiState.toastMessage,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 6. Save / Export Modal Dialog
        SaveExportModal(
            uiState = uiState,
            viewModel = viewModel,
            onExportPngClick = { exportPngLauncher.launch("${viewModel.sanitizedFileName()}.png") },
            onExportJsonClick = { exportJsonLauncher.launch("${viewModel.sanitizedFileName()}.json") },
            onImportJsonClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
    }
}
