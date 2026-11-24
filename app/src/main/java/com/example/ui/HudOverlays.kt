package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.*
import com.example.ui.panes.ShapePreviewThumbnail
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale

/**
 * Minimal Canvas-first Top Floating Bar for 3D Space:
 * - Row 1: Shape Selector Pill (opens 15-shape Bottom Sheet) + Camera View Popover + Save/Export + Fullscreen
 * - Row 2 (micro-strip, hidden when sheet open): Undo/Redo + Live Metrics summary pill
 */
@Composable
fun TopStudioHeader(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    isCompactWidth: Boolean,
    isImmersive: Boolean,
    onToggleImmersive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = uiState.scene.metrics
    val hide = uiState.verifyHideAnswers
    fun f2(v: Float) = if (hide) "???" else String.format(Locale.US, "%.2f", v)
    var cameraMenuExpanded by remember { mutableStateOf(false) }

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
            // Row 1: Compact Translucent Floating Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Shape Selector Pill (opens 4-group 15-shape Bottom Sheet)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                        .testTag("top_shape_selector_pill")
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            fallbackBg = SlateDark.copy(alpha = 0.90f),
                            fallbackBorder = SlateBorder.copy(alpha = 0.8f),
                            accentBorder = if (uiState.activeLeftPane == LeftPane.SHAPES) CyanAccent else null
                        )
                        .clip(CircleShape)
                        .clickable { viewModel.toggleLeftPane(LeftPane.SHAPES) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShapePreviewThumbnail(
                        shape = uiState.snapshot.shape,
                        accent = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            text = uiState.snapshot.shape.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.snapshot.shape.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Chọn khối hình khác",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Right Floating Action Pill: Camera View Menu · Save/Export · Fullscreen
                Row(
                    modifier = Modifier
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            fallbackBg = SlateDark.copy(alpha = 0.90f),
                            fallbackBorder = SlateBorder.copy(alpha = 0.8f)
                        )
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // Quick Camera View Preset Menu (3D / Trước / Trên / Bên / Reset)
                    Box {
                        TopIconBtn(
                            icon = Icons.Default.CenterFocusStrong,
                            contentDescription = "Chuyển góc nhìn nhanh 3D",
                            tint = CyanAccent,
                            testTag = "camera_presets_menu_btn",
                            onClick = { cameraMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = cameraMenuExpanded,
                            onDismissRequest = { cameraMenuExpanded = false },
                            containerColor = SlateDark
                        ) {
                            CameraViewPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Góc nhìn: ${preset.label}",
                                            color = SlateTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("camera_preset_${preset.name.lowercase()}"),
                                    onClick = {
                                        cameraMenuExpanded = false
                                        viewModel.setCameraPreset(preset)
                                    }
                                )
                            }
                            HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f))
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Đặt lại góc nhìn chuẩn",
                                        color = IndigoBright,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = IndigoBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("reset_camera_button"),
                                onClick = {
                                    cameraMenuExpanded = false
                                    viewModel.resetCamera()
                                }
                            )
                        }
                    }

                    TopIconBtn(
                        icon = Icons.Default.SaveAlt,
                        contentDescription = "Lưu & Xuất file JSON/PNG/SVG/OBJ",
                        tint = EmeraldLight,
                        testTag = "rail_save_btn",
                        onClick = { viewModel.openSaveModal() }
                    )

                    TopIconBtn(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = "Chế độ toàn màn hình",
                        tint = SlateTextPrimary,
                        testTag = "geo3d_fullscreen_btn",
                        onClick = onToggleImmersive
                    )
                }
            }

            // Row 2: Ultra-compact Undo/Redo + Live Metrics Micro-Strip (hidden while editing sheet)
            AnimatedVisibility(visible = uiState.activeLeftPane == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Clickable Live Metrics Summary Pill -> opens Oxyz & Formulas Sheet
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("live_metrics_pill")
                            .liquidGlassSurface(
                                shape = CircleShape,
                                pillShape = CircleShape,
                                liveBackdrop = false,
                                fallbackBg = SlateDeepest.copy(alpha = 0.84f),
                                fallbackBorder = SlateBorder.copy(alpha = 0.75f)
                            )
                            .clip(CircleShape)
                            .clickable { viewModel.toggleLeftPane(LeftPane.FORMULAS) }
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        MetricPill("h", f2(m.realH), PinkLight)
                        VerticalDividerTiny()
                        MetricPill("Sđ", f2(m.sDay), CyanAccent)
                        if (!isCompactWidth) {
                            VerticalDividerTiny()
                            MetricPill("Stp", f2(m.sTp), CyanAccent)
                        }
                        VerticalDividerTiny()
                        MetricPill("V", f2(m.vol), AmberAccent)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Compact Undo / Redo / Reset Moved Vertices Pill
                    Row(
                        modifier = Modifier
                            .liquidGlassSurface(
                                shape = CircleShape,
                                pillShape = CircleShape,
                                liveBackdrop = false,
                                fallbackBg = SlateDeepest.copy(alpha = 0.84f),
                                fallbackBorder = SlateBorder.copy(alpha = 0.75f)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallMicroIconBtn(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Hoàn tác",
                            enabled = uiState.canUndo,
                            tint = SlateTextPrimary,
                            testTag = "undo_btn",
                            onClick = { viewModel.undo() }
                        )
                        SmallMicroIconBtn(
                            icon = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Làm lại",
                            enabled = uiState.canRedo,
                            tint = SlateTextPrimary,
                            testTag = "redo_btn",
                            onClick = { viewModel.redo() }
                        )
                        if (uiState.snapshot.vertexOverrides.isNotEmpty()) {
                            SmallMicroIconBtn(
                                icon = Icons.Default.RestartAlt,
                                contentDescription = "Đưa đỉnh về vị trí chuẩn",
                                tint = RoseLight,
                                testTag = "reset_vertices_button",
                                onClick = { viewModel.resetMovedVertices() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIconBtn(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else SlateTextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SmallMicroIconBtn(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else SlateTextSecondary.copy(alpha = 0.32f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MetricPill(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = "$label:", style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VerticalDividerTiny() {
    Box(modifier = Modifier.width(1.dp).height(13.dp).background(SlateBorder))
}

@Composable
fun RightCanvasQuickControls(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val snap = uiState.snapshot

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(end = 8.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    pillShape = RoundedCornerShape(24.dp),
                    liveBackdrop = false,
                    fallbackBg = SlateDark.copy(alpha = 0.84f),
                    fallbackBorder = SlateBorder.copy(alpha = 0.72f)
                )
                .padding(vertical = 5.dp, horizontal = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QuickCanvasButton(
                icon = Icons.Default.GridOn,
                label = "Oxyz",
                active = snap.showAxes,
                activeColor = BlueAxis,
                testTag = "quick_axes_btn",
                contentDescription = "Bật hoặc tắt hệ trục tọa độ Oxyz",
                onClick = { viewModel.toggleAxes() }
            )
            QuickCanvasButton(
                icon = if (uiState.autoRotate) Icons.Default.Pause else Icons.Default.Autorenew,
                label = "Xoay",
                active = uiState.autoRotate,
                activeColor = IndigoPrimary,
                testTag = "quick_autorotate_btn",
                contentDescription = "Bật hoặc tắt tự động xoay mô hình 3D",
                onClick = { viewModel.toggleAutoRotate(!uiState.autoRotate) }
            )
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.65f), modifier = Modifier.width(22.dp))
            QuickCanvasButton(
                icon = Icons.Default.Add,
                label = null,
                active = false,
                activeColor = IndigoPrimary,
                testTag = "quick_zoom_in_btn",
                contentDescription = "Phóng to mô hình 3D",
                onClick = { viewModel.stepZoom(zoomIn = true) }
            )
            QuickCanvasButton(
                icon = Icons.Default.Remove,
                label = null,
                active = false,
                activeColor = IndigoPrimary,
                testTag = "quick_zoom_out_btn",
                contentDescription = "Thu nhỏ mô hình 3D",
                onClick = { viewModel.stepZoom(zoomIn = false) }
            )
        }
    }
}

@Composable
private fun QuickCanvasButton(
    icon: ImageVector,
    label: String?,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .testTag(testTag)
            .size(width = 44.dp, height = if (label != null) 42.dp else 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) activeColor.copy(alpha = 0.88f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color.White else SlateTextLight,
            modifier = Modifier.size(17.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color.White else SlateTextSecondary,
                fontSize = 8.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ToastBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 104.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(EmeraldDark.copy(alpha = 0.95f))
                .border(1.dp, EmeraldLight, CircleShape)
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Text(
                text = message ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Collapsible Floating Dock for 3D Space (positioned neatly above the root Floating Bottom Nav Bar):
 * Consolidates all 3D tools into 3 crisp sheet triggers + a collapse toggle:
 * 1. Kích thước & Đỉnh (Dimensions, Direct Vertex Drag, Replay, Display)
 * 2. Dựng hình & Thiết diện (Edge/XYZ Points, Lines/Vectors, Planes, Dynamic 3-Point Section)
 * 3. Oxyz & Công thức (Axes/Origin/Angle-Distance Calc + Formulas & Verify Mode)
 */
@Composable
fun BottomStudioDock(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    isImmersive: Boolean,
    modifier: Modifier = Modifier
) {
    var dockCollapsed by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isImmersive,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Active Interactive Mode Hint Pill + Quick "Xong" exit
            val statusText = buildInteractiveStatusText(uiState)
            AnimatedVisibility(visible = statusText != null && uiState.activeLeftPane == null, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.94f))
                        .border(1.dp, IndigoLight, CircleShape)
                        .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = statusText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SlateDark.copy(alpha = 0.75f))
                            .clickable { viewModel.setInteractionMode(InteractionMode.VIEW) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Xong", style = MaterialTheme.typography.labelSmall, color = CyanAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Drag Constraint Sub-bar (shown automatically when Direct Drag Point mode is active)
            AnimatedVisibility(
                visible = uiState.interactionMode == InteractionMode.DRAG_POINT && uiState.activeLeftPane == null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Row(
                    modifier = Modifier
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            fallbackBg = SlateDark.copy(alpha = 0.95f),
                            accentBorder = PinkAccent
                        )
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SubDragChip(
                        label = "↔ Ngang",
                        active = uiState.dragConstraint == DragConstraintMode.HORIZONTAL,
                        testTag = "drag_horizontal_btn",
                        onClick = { viewModel.setDragConstraint(DragConstraintMode.HORIZONTAL) }
                    )
                    SubDragChip(
                        label = "↕ Cao Oz",
                        active = uiState.dragConstraint == DragConstraintMode.VERTICAL,
                        testTag = "drag_vertical_btn",
                        onClick = { viewModel.setDragConstraint(DragConstraintMode.VERTICAL) }
                    )
                    SubDragChip(
                        label = "Trên đoạn",
                        active = uiState.dragConstraint == DragConstraintMode.SEGMENT,
                        testTag = "drag_segment_btn",
                        onClick = { viewModel.setDragConstraint(DragConstraintMode.SEGMENT) }
                    )
                    val relOn = uiState.snapshot.keepRelations
                    SubDragChip(
                        label = if (relOn) "Khóa hình" else "Tự do",
                        active = relOn,
                        testTag = "relation_toggle_btn",
                        onClick = { viewModel.toggleKeepRelations() }
                    )
                }
            }

            // Collapsible 3-Action Floating Dock (hidden while an Inspector Bottom Sheet is expanded)
            AnimatedVisibility(visible = uiState.activeLeftPane == null) {
                Row(
                    modifier = Modifier
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            fallbackBg = SlateDark.copy(alpha = 0.94f),
                            fallbackBorder = SlateBorder
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("geo3d_collapsible_dock"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!dockCollapsed) {
                        DockSheetChip(
                            icon = Icons.Default.Tune,
                            label = "Kích thước & Đỉnh",
                            active = uiState.interactionMode == InteractionMode.DRAG_POINT,
                            accent = PinkAccent,
                            testTag = "rail_settings_btn",
                            onClick = { viewModel.toggleLeftPane(LeftPane.SETTINGS) }
                        )
                        DockSheetChip(
                            icon = Icons.Default.Edit,
                            label = "Dựng hình",
                            active = uiState.interactionMode in listOf(
                                InteractionMode.ADD_POINT,
                                InteractionMode.DRAW_DASHED,
                                InteractionMode.DRAW_SOLID,
                                InteractionMode.DRAW_VECTOR
                            ),
                            accent = CyanAccent,
                            testTag = "rail_draw_btn",
                            onClick = { viewModel.toggleLeftPane(LeftPane.DRAW) }
                        )
                        DockSheetChip(
                            icon = Icons.Default.Analytics,
                            label = "Oxyz & Công thức",
                            active = uiState.verifyHideAnswers,
                            accent = AmberAccent,
                            testTag = "rail_coords_btn",
                            onClick = { viewModel.toggleLeftPane(LeftPane.COORDS) }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { dockCollapsed = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Công cụ 3D",
                                style = MaterialTheme.typography.labelMedium,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Collapse / Expand Dock Handle
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SlateSurface.copy(alpha = 0.65f))
                            .clickable { dockCollapsed = !dockCollapsed }
                            .testTag("geo3d_dock_collapse_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (dockCollapsed) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                            contentDescription = if (dockCollapsed) "Mở rộng công cụ 3D" else "Thu gọn công cụ 3D",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockSheetChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    Row(
        modifier = Modifier
            .testTag(testTag)
            .defaultMinSize(minHeight = 40.dp)
            .liquidGlassPressPhysics(enabled = isGlass, scaleOnPress = 1.05f, bloomColor = accent)
            .then(
                if (active) {
                    Modifier.liquidGlassActiveBubble(
                        active = true,
                        shape = CircleShape,
                        fallbackActiveColor = accent.copy(alpha = 0.24f)
                    )
                } else {
                    Modifier.clip(CircleShape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) accent else SlateTextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accent else SlateTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SubDragChip(
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val isGlass = ActiveThemeState.current.liquidGlass.enabled
    Box(
        modifier = Modifier
            .testTag(testTag)
            .liquidGlassPressPhysics(enabled = isGlass, scaleOnPress = 1.06f, bloomColor = PinkAccent)
            .then(
                if (active) {
                    Modifier
                        .liquidGlassActiveBubble(
                            active = true,
                            shape = CircleShape,
                            fallbackActiveColor = PinkAccent
                        )
                } else {
                    Modifier.liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        liveBackdrop = false,
                        fallbackBg = SlateSurface,
                        fallbackBorder = SlateBorder
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else SlateTextLight,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun buildInteractiveStatusText(uiState: GeometryUiState): String? {
    return when (uiState.interactionMode) {
        InteractionMode.VIEW -> null
        InteractionMode.DRAG_POINT -> {
            val rel = if (uiState.snapshot.keepRelations) " • Khóa hình" else " • Tự do"
            when (uiState.dragConstraint) {
                DragConstraintMode.HORIZONTAL -> "Kéo ngang Oxy: giữ đỉnh để trượt$rel"
                DragConstraintMode.VERTICAL -> "Kéo cao Oz: giữ đỉnh để nâng/hạ$rel"
                DragConstraintMode.SEGMENT -> "Trượt điểm dọc theo cạnh"
            }
        }
        InteractionMode.ADD_POINT -> "Chạm lên cạnh bất kỳ để chấm điểm mới"
        InteractionMode.DRAW_DASHED, InteractionMode.DRAW_SOLID, InteractionMode.DRAW_VECTOR -> {
            val kind = when (uiState.interactionMode) {
                InteractionMode.DRAW_VECTOR -> "Vectơ"
                InteractionMode.DRAW_DASHED -> "Nét đứt"
                else -> "Nét liền"
            }
            val first = uiState.selectedFirstPoint
            if (first == null) "[$kind] Chạm chọn điểm đầu"
            else "[$kind] Từ \"$first\" → Chọn điểm cuối"
        }
    }
}
