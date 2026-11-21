package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GeometryRenderer
import com.example.engine.GeometryUiState
import com.example.engine.InteractionMode
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GeometryCanvasView(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    onCanvasSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1920)) }
    val currentUiState by rememberUpdatedState(uiState)

    // Smooth auto-rotate ticker
    LaunchedEffect(uiState.autoRotate) {
        if (!uiState.autoRotate) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    viewModel.tickAutoRotate(dt)
                }
                lastNanos = nanos
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasSize = it
                onCanvasSizeChanged(it)
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("geometry_3d_canvas")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        var movedDistance = 0f
                        var isMultiTouch = false
                        var dragCaptured = false
                        val mode = currentUiState.interactionMode

                        if (mode == InteractionMode.DRAG_POINT || mode == InteractionMode.ADD_POINT) {
                            dragCaptured = viewModel.onDragStart(
                                sx = startPos.x,
                                sy = startPos.y,
                                width = canvasSize.width.toFloat(),
                                height = canvasSize.height.toFloat(),
                                density = density
                            )
                        }

                        var lastPos = startPos
                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) break

                            if (activePointers.size >= 2) {
                                // Two-finger pinch zoom + pan works in ALL modes
                                if (dragCaptured) {
                                    viewModel.onDragEnd(lastPos.x, lastPos.y, canvasSize.width.toFloat(), canvasSize.height.toFloat(), density)
                                    dragCaptured = false
                                }
                                isMultiTouch = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan.x != 0f || pan.y != 0f) {
                                    viewModel.zoomAndPanCamera(
                                        zoomFactor = zoom,
                                        panX = pan.x,
                                        panY = pan.y,
                                        viewportWidth = canvasSize.width.toFloat().coerceAtLeast(1f),
                                        viewportHeight = canvasSize.height.toFloat().coerceAtLeast(1f)
                                    )
                                }
                                activePointers.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!isMultiTouch) {
                                val change = activePointers.first()
                                val curPos = change.position
                                val dx = curPos.x - lastPos.x
                                val dy = curPos.y - lastPos.y
                                movedDistance += hypot(dx, dy)
                                lastPos = curPos

                                if (dragCaptured) {
                                    viewModel.onDragMove(
                                        sx = curPos.x,
                                        sy = curPos.y,
                                        width = canvasSize.width.toFloat(),
                                        height = canvasSize.height.toFloat(),
                                        density = density
                                    )
                                    change.consume()
                                } else if (mode == InteractionMode.VIEW || mode == InteractionMode.DRAG_POINT) {
                                    // Orbit camera when dragging empty canvas
                                    if (movedDistance > 3f) {
                                        viewModel.orbitCamera(dx, dy)
                                        change.consume()
                                    }
                                }
                            }
                        }

                        if (dragCaptured) {
                            viewModel.onDragEnd(lastPos.x, lastPos.y, canvasSize.width.toFloat(), canvasSize.height.toFloat(), density)
                        } else if (!isMultiTouch && movedDistance < 14f * density) {
                            viewModel.onCanvasTap(
                                sx = startPos.x,
                                sy = startPos.y,
                                width = canvasSize.width.toFloat(),
                                height = canvasSize.height.toFloat(),
                                density = density
                            )
                        } else if (isMultiTouch || movedDistance >= 3f) {
                            viewModel.commitCameraGesture()
                        }
                    }
                }
        ) {
            // Observe active theme palette so Light/Dark/Preset/Accent changes redraw 3D immediately
            @Suppress("UNUSED_VARIABLE")
            val themeVersion = ActiveThemeState.current
            drawIntoCanvas { canvas ->
                GeometryRenderer.renderScene(
                    canvas = canvas.nativeCanvas,
                    width = size.width,
                    height = size.height,
                    density = density,
                    uiState = uiState
                )
            }
        }

        // Floating Coordinate / Snap Tooltip
        uiState.tooltip?.let { tip ->
            val maxOffsetX = (canvasSize.width - (260 * density)).coerceAtLeast(16f)
            val offsetX = (tip.screenX + 16f * density).coerceIn(12f * density, maxOffsetX).roundToInt()
            val offsetY = (tip.screenY - 76f * density).coerceIn(64f * density, (canvasSize.height - 140f * density).coerceAtLeast(64f)).roundToInt()

            Column(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .widthIn(max = 260.dp)
                    .background(SlateDeepest.copy(alpha = 0.94f), RoundedCornerShape(10.dp))
                    .border(1.dp, CyanAccent.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tip.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (tip.subtitle.isNotEmpty()) {
                    Text(
                        text = tip.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = CyanAccent,
                        fontSize = 11.sp
                    )
                }
                if (tip.snapBadge.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tip.snapBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextPrimary,
                        modifier = Modifier
                            .background(EmeraldDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (tip.relationBadge.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tip.relationBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextPrimary,
                        modifier = Modifier
                            .background(SkyBlue, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
