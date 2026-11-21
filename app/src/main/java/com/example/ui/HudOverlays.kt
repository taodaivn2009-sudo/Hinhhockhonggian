package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.geo2d.MathWorkspace
import com.example.geo2d.WorkspaceModeSwitch
import com.example.ui.panes.SensitivityControlsSection
import com.example.ui.panes.ShapePreviewThumbnail
import com.example.ui.theme.*
import com.example.update.AppUpdateUiState
import com.example.update.UpdateVersionPill
import com.example.viewmodel.GeometryViewModel
import java.util.Locale
import kotlin.math.abs

@Composable
fun TopStudioHeader(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    isCompactWidth: Boolean,
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    updateUiState: AppUpdateUiState = AppUpdateUiState(),
    onOpenUpdateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val m = uiState.scene.metrics
    val hide = uiState.verifyHideAnswers
    fun f2(v: Float) = if (hide) "???" else String.format(Locale.US, "%.2f", v)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Row 1: 3D/2D Mode Switcher + Current Shape Selector Pill + Quick Undo/Redo/Reset/Save Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WorkspaceModeSwitch(
                current = MathWorkspace.SPACE_3D,
                onSelect = onSwitchWorkspace
            )

            // Interactive Shape Title Pill (opens Shapes pane)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .testTag("top_shape_selector_pill")
                    .clip(RoundedCornerShape(11.dp))
                    .background(SlateDark.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = if (uiState.activeLeftPane == LeftPane.SHAPES) CyanAccent else SlateBorder.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(11.dp)
                    )
                    .clickable { viewModel.toggleLeftPane(LeftPane.SHAPES) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShapePreviewThumbnail(
                    shape = uiState.snapshot.shape,
                    accent = CyanAccent,
                    modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        text = uiState.snapshot.shape.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.snapshot.shape.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Chọn khối hình khác",
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Right Studio Action Strip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(SlateDark.copy(alpha = 0.88f))
                    .border(1.dp, SlateBorder.copy(alpha = 0.75f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                TopIconBtn(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Hoàn tác",
                    enabled = uiState.canUndo,
                    tint = SlateTextPrimary,
                    testTag = "undo_btn",
                    onClick = { viewModel.undo() }
                )
                TopIconBtn(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Làm lại",
                    enabled = uiState.canRedo,
                    tint = SlateTextPrimary,
                    testTag = "redo_btn",
                    onClick = { viewModel.redo() }
                )
                if (uiState.snapshot.vertexOverrides.isNotEmpty()) {
                    TopIconBtn(
                        icon = Icons.Default.RestartAlt,
                        contentDescription = "Đưa đỉnh về vị trí chuẩn",
                        tint = RoseLight,
                        testTag = "reset_vertices_button",
                        onClick = { viewModel.resetMovedVertices() }
                    )
                }
                TopIconBtn(
                    icon = Icons.Default.PlayCircleOutline,
                    contentDescription = "Tua lại từng bước dựng hình",
                    tint = AmberAccent,
                    testTag = "replay_3d_btn",
                    onClick = { viewModel.startReplay() }
                )
                TopIconBtn(
                    icon = Icons.Default.CenterFocusStrong,
                    contentDescription = "Đặt lại góc nhìn camera chuẩn",
                    tint = CyanAccent,
                    testTag = "reset_camera_button",
                    onClick = { viewModel.resetCamera() }
                )
                TopIconBtn(
                    icon = Icons.Default.Save,
                    contentDescription = "Lưu bài hoặc xuất PNG/SVG/OBJ/JSON",
                    tint = EmeraldLight,
                    testTag = "rail_save_btn",
                    onClick = { viewModel.openSaveModal() }
                )
            }
        }

        // Row 2: Live Metrics Bar (tapping opens Formulas pane) + Active Mode Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .testTag("live_metrics_pill")
                    .clip(RoundedCornerShape(9.dp))
                    .background(SlateDeepest.copy(alpha = 0.86f))
                    .border(1.dp, SlateBorder.copy(alpha = 0.85f), RoundedCornerShape(9.dp))
                    .clickable { viewModel.toggleLeftPane(LeftPane.FORMULAS) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MetricPill("h", f2(m.realH), PinkLight)
                VerticalDividerTiny()
                MetricPill("Sđ", f2(m.sDay), CyanAccent)
                if (!isCompactWidth) {
                    VerticalDividerTiny()
                    MetricPill("Sxq", f2(m.sXq), CyanAccent)
                    VerticalDividerTiny()
                    MetricPill("Stp", f2(m.sTp), CyanAccent)
                }
                VerticalDividerTiny()
                MetricPill("V", f2(m.vol), AmberAccent)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Active Sensitivity Indicator Pill (only when non-default)
                val s = uiState.snapshot.sensitivity
                val isCustomSens = abs(s.rotation - 1f) > 0.05f || abs(s.zoom - 1f) > 0.05f || abs(s.drag - 1f) > 0.05f || abs(s.touchRadius - 1f) > 0.05f
                if (isCustomSens) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(SkyBlue.copy(alpha = 0.24f))
                            .border(1.dp, CyanAccent.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                            .clickable { viewModel.toggleSensitivityPopover() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(13.dp))
                        Text(
                            text = String.format(Locale.US, "%.1fx", s.rotation),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = CyanAccent
                        )
                    }
                }

                UpdateVersionPill(
                    uiState = updateUiState,
                    onClick = onOpenUpdateDialog
                )
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
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
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
    modifier: Modifier = Modifier
) {
    val snap = uiState.snapshot

    Column(
        modifier = modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SlateDark.copy(alpha = 0.84f))
            .border(1.dp, SlateBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp, horizontal = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
            icon = Icons.Default.GpsFixed,
            label = "Hút",
            active = snap.snapEnabled,
            activeColor = EmeraldDark,
            testTag = "snap_toggle_btn",
            contentDescription = "Bật hoặc tắt nam châm hút tọa độ",
            onClick = { viewModel.toggleSnapEnabled() }
        )
        QuickCanvasButton(
            icon = Icons.Default.Speed,
            label = "Nhạy",
            active = uiState.sensitivityPopoverOpen,
            activeColor = SkyBlue,
            testTag = "quick_sensitivity_btn",
            contentDescription = "Mở tùy chỉnh nhanh độ nhạy xoay, zoom và kéo điểm",
            onClick = { viewModel.toggleSensitivityPopover() }
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
        HorizontalDivider(color = SlateBorder, modifier = Modifier.width(26.dp))
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
            .size(width = 44.dp, height = if (label != null) 44.dp else 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) activeColor.copy(alpha = 0.88f) else Color.Transparent)
            .border(1.dp, if (active) Color.White.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) SlateTextPrimary else SlateTextLight,
            modifier = Modifier.size(18.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) SlateTextPrimary else SlateTextSecondary,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SensitivityQuickPopover(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = uiState.sensitivityPopoverOpen) {
        viewModel.closeSensitivityPopover()
    }

    AnimatedVisibility(
        visible = uiState.sensitivityPopoverOpen,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.CenterVertically),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically),
        modifier = modifier
            .padding(end = 68.dp, start = 12.dp)
            .widthIn(max = 290.dp)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SlateDark.copy(alpha = 0.95f))
                .border(1.dp, CyanAccent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(17.dp))
                    Text("Tùy chỉnh độ nhạy", style = MaterialTheme.typography.titleSmall, color = SlateTextPrimary)
                }
                IconButton(
                    onClick = { viewModel.closeSensitivityPopover() },
                    modifier = Modifier.size(28.dp).testTag("close_sensitivity_popover_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng tùy chỉnh độ nhạy", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Text(
                text = "Thử xoay/zoom trực tiếp trên hình để chọn mức vừa tay nhất.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                fontSize = 10.5.sp
            )

            SensitivityControlsSection(
                sensitivity = uiState.snapshot.sensitivity,
                viewModel = viewModel,
                compact = true
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
            .padding(top = 98.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(EmeraldDark.copy(alpha = 0.95f))
                .border(1.dp, EmeraldLight, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Text(
                text = message ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BottomStudioDock(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Active Interactive Hint Pill + Quick "Xong" exit button
        val statusText = buildInteractiveStatusText(uiState)
        AnimatedVisibility(visible = statusText != null && uiState.activeLeftPane == null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(IndigoPrimary.copy(alpha = 0.95f))
                    .border(1.dp, IndigoLight, RoundedCornerShape(18.dp))
                    .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = statusText ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateDark.copy(alpha = 0.7f))
                        .clickable { viewModel.setInteractionMode(InteractionMode.VIEW) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Xong", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                }
            }
        }

        // 2. Drag Constraint Sub-bar (when Drag Point mode is active)
        AnimatedVisibility(
            visible = uiState.dragSubdrawerOpen && !uiState.bottomDockCollapsed && uiState.activeLeftPane == null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateDark.copy(alpha = 0.96f))
                    .border(1.dp, PinkAccent, RoundedCornerShape(12.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SubDragChip(
                    label = "↔ Ngang Oxy",
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
                VerticalDividerTiny()
                val relOn = uiState.snapshot.keepRelations
                Row(
                    modifier = Modifier
                        .testTag("relation_toggle_btn")
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (relOn) SkyBlue.copy(alpha = 0.28f) else SlateSurface)
                        .border(1.dp, if (relOn) SkyBlue else SlateMuted, RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleKeepRelations() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (relOn) Icons.Default.Link else Icons.Default.LinkOff,
                        contentDescription = "Bật hoặc tắt ràng buộc hình học",
                        tint = if (relOn) CyanAccent else SlateTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (relOn) "Ràng buộc" else "Tự do",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (relOn) CyanAccent else SlateTextSecondary
                    )
                }
            }
        }

        // 3. Tier 1: Direct 3D Interaction Mode Strip (Hidden while bottom sheet is open on compact screen)
        AnimatedVisibility(visible = uiState.activeLeftPane == null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(SlateDark.copy(alpha = 0.92f))
                    .border(1.dp, SlateBorder, RoundedCornerShape(13.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ModePillButton(
                    icon = Icons.Default.RotateRight,
                    label = "Xoay",
                    active = uiState.interactionMode == InteractionMode.VIEW,
                    activeBg = IndigoPrimary,
                    testTag = "mode_view_btn",
                    onClick = { viewModel.setInteractionMode(InteractionMode.VIEW) }
                )
                ModePillButton(
                    icon = Icons.Default.OpenWith,
                    label = "Kéo điểm",
                    active = uiState.interactionMode == InteractionMode.DRAG_POINT,
                    activeBg = PinkAccent,
                    testTag = "mode_drag_btn",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAG_POINT) }
                )
                ModePillButton(
                    icon = Icons.Default.AddLocationAlt,
                    label = "Điểm",
                    active = uiState.interactionMode == InteractionMode.ADD_POINT,
                    activeBg = AmberDeep,
                    testTag = "mode_add_point_btn",
                    onClick = { viewModel.setInteractionMode(InteractionMode.ADD_POINT) }
                )
                ModePillButton(
                    icon = Icons.Default.LinearScale,
                    label = "Nét đứt",
                    active = uiState.interactionMode == InteractionMode.DRAW_DASHED,
                    activeBg = SkyBlue,
                    testTag = "mode_dashed_btn",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAW_DASHED) }
                )
                ModePillButton(
                    icon = Icons.Default.Timeline,
                    label = "Nét liền",
                    active = uiState.interactionMode == InteractionMode.DRAW_SOLID,
                    activeBg = EmeraldDark,
                    testTag = "mode_solid_btn",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAW_SOLID) }
                )
                ModePillButton(
                    icon = Icons.Default.NorthEast,
                    label = "Vectơ",
                    active = uiState.interactionMode == InteractionMode.DRAW_VECTOR,
                    activeBg = RoseAccent,
                    testTag = "mode_vector_btn",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAW_VECTOR) }
                )
            }
        }

        // 4. Tier 2: Main 5-Tab Studio Navigation Bar (Always accessible, clear labels)
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark.copy(alpha = 0.96f))
                .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioBottomTab(
                icon = Icons.Default.Category,
                label = "Khối hình",
                active = uiState.activeLeftPane == LeftPane.SHAPES,
                testTag = "rail_shapes_btn",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleLeftPane(LeftPane.SHAPES) }
            )
            StudioBottomTab(
                icon = Icons.Default.Edit,
                label = "Kẻ hình",
                active = uiState.activeLeftPane == LeftPane.DRAW,
                testTag = "rail_draw_btn",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleLeftPane(LeftPane.DRAW) }
            )
            StudioBottomTab(
                icon = Icons.Default.Public,
                label = "Oxyz",
                active = uiState.activeLeftPane == LeftPane.COORDS,
                testTag = "rail_coords_btn",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleLeftPane(LeftPane.COORDS) }
            )
            StudioBottomTab(
                icon = Icons.Default.Tune,
                label = "Cài đặt",
                active = uiState.activeLeftPane == LeftPane.SETTINGS,
                testTag = "rail_settings_btn",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleLeftPane(LeftPane.SETTINGS) }
            )
            StudioBottomTab(
                icon = Icons.Default.Analytics,
                label = "Số liệu",
                active = uiState.activeLeftPane == LeftPane.FORMULAS,
                testTag = "rail_formulas_btn",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleLeftPane(LeftPane.FORMULAS) }
            )
        }
    }
}

@Composable
private fun ModePillButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeBg: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) activeBg else Color.Transparent)
            .border(1.dp, if (active) Color.White.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) SlateTextPrimary else SlateTextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) SlateTextPrimary else SlateTextLight,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StudioBottomTab(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .testTag(testTag)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) IndigoPrimary.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (active) IndigoPrimary else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) SlateTextPrimary else SlateTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CyanAccent else SlateTextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) PinkAccent else SlateSurface)
            .border(1.dp, if (active) PinkLight else SlateBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) SlateTextPrimary else SlateTextLight,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun buildInteractiveStatusText(uiState: GeometryUiState): String? {
    return when (uiState.interactionMode) {
        InteractionMode.VIEW -> null
        InteractionMode.DRAG_POINT -> {
            val rel = if (uiState.snapshot.keepRelations) " • Ràng buộc BẬT" else " • Tự do"
            when (uiState.dragConstraint) {
                DragConstraintMode.HORIZONTAL -> "Kéo ngang Oxy: chạm giữ đỉnh để trượt$rel"
                DragConstraintMode.VERTICAL -> "Kéo cao Oz: chạm giữ đỉnh để nâng/hạ$rel"
                DragConstraintMode.SEGMENT -> "Kéo trên đoạn: trượt điểm dọc theo cạnh"
            }
        }
        InteractionMode.ADD_POINT -> "Chạm lên một cạnh bất kỳ để chấm điểm mới"
        InteractionMode.DRAW_DASHED, InteractionMode.DRAW_SOLID, InteractionMode.DRAW_VECTOR -> {
            val kind = when (uiState.interactionMode) {
                InteractionMode.DRAW_VECTOR -> "Vectơ"
                InteractionMode.DRAW_DASHED -> "Nét đứt"
                else -> "Nét liền"
            }
            val first = uiState.selectedFirstPoint
            if (first == null) "[$kind] Chạm chọn điểm đầu (Gốc)"
            else "[$kind] Từ \"$first\" → Chạm chọn điểm cuối"
        }
    }
}
