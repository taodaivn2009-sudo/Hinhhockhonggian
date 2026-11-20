package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.engine.DragConstraintMode
import com.example.engine.GeometryUiState
import com.example.engine.InteractionMode
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel
import java.util.Locale

@Composable
fun TopRightMetricsBar(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    isCompactWidth: Boolean,
    modifier: Modifier = Modifier
) {
    val m = uiState.scene.metrics
    fun f2(v: Float) = String.format(Locale.US, "%.2f", v)

    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp, end = 10.dp, start = 70.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Live metrics pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SlateDark.copy(alpha = 0.90f))
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricPill("h", f2(m.realH), PinkLight)
            if (!isCompactWidth) {
                VerticalDividerTiny()
                MetricPill("Sđ", f2(m.sDay), CyanAccent)
                VerticalDividerTiny()
                MetricPill("Sxq", f2(m.sXq), CyanAccent)
                VerticalDividerTiny()
                MetricPill("Stp", f2(m.sTp), CyanAccent)
            }
            VerticalDividerTiny()
            MetricPill("V", f2(m.vol), AmberAccent)
        }

        // Reset moved vertices button (visible only when vertices moved)
        if (uiState.snapshot.vertexOverrides.isNotEmpty()) {
            SmallHudActionButton(
                icon = Icons.Default.RestartAlt,
                label = if (isCompactWidth) null else "Đỉnh chuẩn",
                tint = RoseLight,
                borderColor = RoseAccent,
                testTag = "reset_vertices_button",
                contentDescription = "Đưa các đỉnh đã kéo về vị trí chuẩn",
                onClick = { viewModel.resetMovedVertices() }
            )
        }

        // Reset camera button
        SmallHudActionButton(
            icon = Icons.Default.Videocam,
            label = if (isCompactWidth) null else "Góc chuẩn",
            tint = SlateTextPrimary,
            borderColor = SlateMuted,
            testTag = "reset_camera_button",
            contentDescription = "Đặt lại góc nhìn camera chuẩn",
            onClick = { viewModel.resetCamera() }
        )
    }
}

@Composable
private fun MetricPill(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = SlateTextSecondary
        )
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
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(SlateBorder)
    )
}

@Composable
private fun SmallHudActionButton(
    icon: ImageVector,
    label: String?,
    tint: Color,
    borderColor: Color,
    testTag: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(SlateSurface.copy(alpha = 0.92f))
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (label != null) 10.dp else 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
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
            .padding(top = 52.dp)
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
fun BottomToolDock(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Interactive status banner
        val statusText = buildInteractiveStatusText(uiState)
        AnimatedVisibility(visible = statusText != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(IndigoPrimary.copy(alpha = 0.94f))
                    .border(1.dp, IndigoLight, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = statusText ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2. Upward-expanding Drag Mode Subdrawer
        AnimatedVisibility(
            visible = uiState.dragSubdrawerOpen && !uiState.bottomDockCollapsed,
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
                Text("Kéo:", style = MaterialTheme.typography.labelSmall, color = PinkLight)
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
                    label = "📏 Trên đoạn",
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
                        .padding(horizontal = 9.dp, vertical = 6.dp),
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
                        text = if (relOn) "Ràng buộc: BẬT" else "Ràng buộc: TẮT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (relOn) CyanAccent else SlateTextSecondary
                    )
                }
            }
        }

        // 3. Main Bottom Dock Bar
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SlateDark.copy(alpha = 0.94f))
                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Collapse/Expand button
            DockToolButton(
                icon = if (uiState.bottomDockCollapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                label = if (uiState.bottomDockCollapsed) "Công cụ" else null,
                active = false,
                testTag = "toggle_bottom_dock_btn",
                contentDescription = "Thu gọn hoặc mở rộng thanh công cụ dưới",
                onClick = { viewModel.toggleBottomDockCollapse() }
            )

            if (!uiState.bottomDockCollapsed) {
                VerticalDividerTiny()
                DockToolButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = null,
                    active = false,
                    enabled = uiState.canUndo,
                    testTag = "undo_btn",
                    contentDescription = "Hoàn tác",
                    onClick = { viewModel.undo() }
                )
                DockToolButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = null,
                    active = false,
                    enabled = uiState.canRedo,
                    testTag = "redo_btn",
                    contentDescription = "Làm lại",
                    onClick = { viewModel.redo() }
                )
                VerticalDividerTiny()

                DockToolButton(
                    icon = Icons.Default.RotateRight,
                    label = "Xoay",
                    active = uiState.interactionMode == InteractionMode.VIEW,
                    testTag = "mode_view_btn",
                    contentDescription = "Chế độ xoay hình 3D",
                    onClick = { viewModel.setInteractionMode(InteractionMode.VIEW) }
                )
                DockToolButton(
                    icon = Icons.Default.OpenWith,
                    label = "Kéo điểm",
                    active = uiState.interactionMode == InteractionMode.DRAG_POINT,
                    testTag = "mode_drag_btn",
                    contentDescription = "Chế độ kéo điểm trên hình",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAG_POINT) }
                )
                DockToolButton(
                    icon = Icons.Default.GpsFixed,
                    label = if (uiState.snapshot.snapEnabled) "Hút" else "Tắt hút",
                    active = false,
                    highlightGreen = uiState.snapshot.snapEnabled,
                    testTag = "snap_toggle_btn",
                    contentDescription = "Bật tắt nam châm hút tọa độ và trung điểm",
                    onClick = { viewModel.toggleSnapEnabled() }
                )
                VerticalDividerTiny()

                DockToolButton(
                    icon = Icons.Default.AddLocationAlt,
                    label = "Điểm",
                    active = uiState.interactionMode == InteractionMode.ADD_POINT,
                    testTag = "mode_add_point_btn",
                    contentDescription = "Chấm điểm mới trực tiếp lên cạnh",
                    onClick = { viewModel.setInteractionMode(InteractionMode.ADD_POINT) }
                )
                DockToolButton(
                    icon = Icons.Default.LinearScale,
                    label = "Nét đứt",
                    active = uiState.interactionMode == InteractionMode.DRAW_DASHED,
                    testTag = "mode_dashed_btn",
                    contentDescription = "Kẻ đoạn thẳng nét đứt giữa 2 điểm",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAW_DASHED) }
                )
                DockToolButton(
                    icon = Icons.Default.Timeline,
                    label = "Nét liền",
                    active = uiState.interactionMode == InteractionMode.DRAW_SOLID,
                    testTag = "mode_solid_btn",
                    contentDescription = "Kẻ đoạn thẳng nét liền giữa 2 điểm",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAW_SOLID) }
                )
                DockToolButton(
                    icon = Icons.Default.NorthEast,
                    label = "Vectơ",
                    active = uiState.interactionMode == InteractionMode.DRAW_VECTOR,
                    testTag = "mode_vector_btn",
                    contentDescription = "Vẽ vectơ giữa 2 điểm",
                    onClick = { viewModel.setInteractionMode(InteractionMode.DRAW_VECTOR) }
                )
                VerticalDividerTiny()

                DockToolButton(
                    icon = Icons.Default.GridOn,
                    label = "Oxyz",
                    active = uiState.snapshot.showAxes,
                    testTag = "quick_axes_btn",
                    contentDescription = "Bật hoặc tắt hệ trục tọa độ Oxyz",
                    onClick = { viewModel.toggleAxes() }
                )
            }
        }
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

@Composable
private fun DockToolButton(
    icon: ImageVector,
    label: String?,
    active: Boolean,
    enabled: Boolean = true,
    highlightGreen: Boolean = false,
    testTag: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    val bg = when {
        active -> IndigoPrimary
        highlightGreen -> EmeraldAccent.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val border = when {
        active -> IndigoBright
        highlightGreen -> EmeraldDark
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> SlateTextSecondary.copy(alpha = 0.35f)
        active -> SlateTextPrimary
        highlightGreen -> EmeraldLight
        else -> SlateTextLight
    }

    Row(
        modifier = Modifier
            .testTag(testTag)
            .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (label != null) 9.dp else 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontSize = 11.5.sp
            )
        }
    }
}

private fun buildInteractiveStatusText(uiState: GeometryUiState): String? {
    return when (uiState.interactionMode) {
        InteractionMode.VIEW -> null
        InteractionMode.DRAG_POINT -> {
            val rel = if (uiState.snapshot.keepRelations) " | Ràng buộc BẬT" else " | Ràng buộc TẮT"
            when (uiState.dragConstraint) {
                DragConstraintMode.HORIZONTAL -> "↔ [Kéo ngang Oxy]: Chạm & kéo điểm trên mặt ngang Oxy$rel"
                DragConstraintMode.VERTICAL -> "↕ [Kéo cao Oz]: Chạm & kéo điểm nâng/hạ độ cao Oz$rel"
                DragConstraintMode.SEGMENT -> "📏 [Trên đoạn AB]: Chạm & kéo điểm trượt dọc theo đoạn thẳng"
            }
        }
        InteractionMode.ADD_POINT -> "📍 Chạm trực tiếp lên một cạnh bất kỳ để lấy điểm (tự hút Trung điểm, Trọng tâm)"
        InteractionMode.DRAW_DASHED, InteractionMode.DRAW_SOLID, InteractionMode.DRAW_VECTOR -> {
            val kind = when (uiState.interactionMode) {
                InteractionMode.DRAW_VECTOR -> "Vectơ"
                InteractionMode.DRAW_DASHED -> "Nét đứt"
                else -> "Nét liền"
            }
            val first = uiState.selectedFirstPoint
            if (first == null) "✏️ [$kind] Chạm vào điểm đầu (Gốc) trên hình 3D"
            else "✏️ [$kind] Đã chọn \"$first\" → Chạm vào điểm cuối (Ngọn)"
        }
    }
}
