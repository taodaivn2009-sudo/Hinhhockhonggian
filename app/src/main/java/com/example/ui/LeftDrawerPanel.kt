package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GeometryUiState
import com.example.engine.InteractionMode
import com.example.engine.LeftPane
import com.example.ui.panes.*
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel

/**
 * Adaptive Modal Bottom Sheet / Side Inspector for 3D Space:
 * Occupies <= 52% height on mobile so the 3D shape stays live & visible above while editing.
 * Consolidates:
 * - LeftPane.SHAPES -> 15 shapes in 4 groups
 * - LeftPane.SETTINGS -> Kích thước & Đỉnh (Dimensions, Direct Vertex Drag toggle, Replay trigger, SGK Display)
 * - LeftPane.DRAW -> Dựng hình & Thiết diện (Quick canvas draw mode chips + DrawPane)
 * - LeftPane.COORDS / FORMULAS -> Unified "Oxyz & Công thức" sheet with clean 2-tab switch
 */
@Composable
fun StudioInspectorSheet(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    isCompactWidth: Boolean,
    maxSheetHeight: Dp,
    onImportJsonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = uiState.activeLeftPane != null) {
        viewModel.closeLeftPane()
    }

    val enterAnim = if (isCompactWidth) {
        fadeIn() + expandVertically(expandFrom = Alignment.Bottom)
    } else {
        fadeIn() + expandHorizontally(expandFrom = Alignment.Start)
    }
    val exitAnim = if (isCompactWidth) {
        fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
    } else {
        fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
    }

    AnimatedVisibility(
        visible = uiState.activeLeftPane != null,
        enter = enterAnim,
        exit = exitAnim,
        modifier = modifier
    ) {
        val activePane = uiState.activeLeftPane ?: LeftPane.SHAPES

        val containerModifier = if (isCompactWidth) {
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 78.dp)
                .heightIn(max = maxSheetHeight)
        } else {
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 74.dp, bottom = 84.dp)
                .width(360.dp)
                .fillMaxHeight()
        }

        val isGlass = ActiveThemeState.current.liquidGlass.enabled
        val headerBg = if (isGlass) SlateDeepest.copy(alpha = 0.42f) else SlateDeepest

        val sheetTitle = when (activePane) {
            LeftPane.SHAPES -> "Chọn Khối Hình (15 khối)"
            LeftPane.SETTINGS -> "Kích thước & Đỉnh"
            LeftPane.DRAW -> "Dựng hình & Thiết diện"
            LeftPane.COORDS, LeftPane.FORMULAS -> "Oxyz & Công thức"
        }

        Column(
            modifier = containerModifier
                .liquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    elevated = true,
                    fallbackBg = SlateDark.copy(alpha = 0.96f),
                    fallbackBorder = SlateBorder
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            // Drag Handle Indicator on mobile bottom sheet
            if (isCompactWidth) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .clickable { viewModel.closeLeftPane() }
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

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .testTag("close_left_drawer_btn")
                        .clip(CircleShape)
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, CircleShape)
                        .clickable { viewModel.closeLeftPane() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Đóng bảng điều khiển",
                        tint = SlateTextLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Đóng", style = MaterialTheme.typography.labelSmall, color = SlateTextLight)
                }
            }

            // Unified 2-Tab Switch when inside Oxyz & Formulas Sheet
            if (activePane == LeftPane.COORDS || activePane == LeftPane.FORMULAS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SheetSubTabChip(
                        label = "Hệ trục & Máy tính Oxyz",
                        selected = activePane == LeftPane.COORDS,
                        accent = CyanAccent,
                        testTag = "sheet_tab_oxyz",
                        modifier = Modifier.weight(1f),
                        onClick = { if (activePane != LeftPane.COORDS) viewModel.toggleLeftPane(LeftPane.COORDS) }
                    )
                    SheetSubTabChip(
                        label = "Công thức & Tự kiểm tra",
                        selected = activePane == LeftPane.FORMULAS,
                        accent = AmberAccent,
                        testTag = "rail_formulas_btn",
                        modifier = Modifier.weight(1f),
                        onClick = { if (activePane != LeftPane.FORMULAS) viewModel.toggleLeftPane(LeftPane.FORMULAS) }
                    )
                }
            }

            HorizontalDivider(color = SlateSurface)

            // Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                when (activePane) {
                    LeftPane.SHAPES -> ShapesPane(uiState, viewModel)
                    LeftPane.DRAW -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickDirectDrawBar(uiState = uiState, viewModel = viewModel)
                        DrawPane(uiState, viewModel)
                    }
                    LeftPane.COORDS -> CoordsPane(uiState, viewModel)
                    LeftPane.SETTINGS -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VertexDragAndReplayCard(uiState = uiState, viewModel = viewModel)
                        SettingsPane(uiState, viewModel, onImportJsonClick)
                    }
                    LeftPane.FORMULAS -> FormulasPane(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SheetSubTabChip(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.22f) else SlateSurface.copy(alpha = 0.7f))
            .border(1.dp, if (selected) accent else SlateBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else SlateTextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VertexDragAndReplayCard(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    val isDragOn = uiState.interactionMode == InteractionMode.DRAG_POINT
    SettingBox(
        title = "Tương tác Đỉnh & Phát lại dựng hình",
        titleColor = PinkLight,
        borderColor = PinkAccent.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kéo trực tiếp đỉnh trên khối 3D",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Chạm và trượt đỉnh theo phương ngang, dọc hoặc trên cạnh",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = isDragOn,
                onCheckedChange = { enabled ->
                    viewModel.setInteractionMode(if (enabled) InteractionMode.DRAG_POINT else InteractionMode.VIEW)
                    if (enabled) viewModel.closeLeftPane()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PinkAccent
                ),
                modifier = Modifier.testTag("mode_drag_btn")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.closeLeftPane()
                    viewModel.startReplay()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberDeep, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .testTag("replay_3d_btn")
            ) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Phát lại từng bước", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            if (uiState.snapshot.vertexOverrides.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.resetMovedVertices() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 42.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = RoseLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Đỉnh chuẩn", color = RoseLight, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun QuickDirectDrawBar(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel
) {
    SettingBox(
        title = "Vẽ trực tiếp trên Canvas 3D",
        titleColor = CyanAccent,
        borderColor = CyanAccent.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickDrawModePill("Chấm điểm", Icons.Default.AddLocationAlt, uiState.interactionMode == InteractionMode.ADD_POINT, AmberDeep, "mode_add_point_btn") {
                viewModel.setInteractionMode(InteractionMode.ADD_POINT)
                viewModel.closeLeftPane()
            }
            QuickDrawModePill("Nét đứt", Icons.Default.LinearScale, uiState.interactionMode == InteractionMode.DRAW_DASHED, SkyBlue, "mode_dashed_btn") {
                viewModel.setInteractionMode(InteractionMode.DRAW_DASHED)
                viewModel.closeLeftPane()
            }
            QuickDrawModePill("Nét liền", Icons.Default.Timeline, uiState.interactionMode == InteractionMode.DRAW_SOLID, EmeraldDark, "mode_solid_btn") {
                viewModel.setInteractionMode(InteractionMode.DRAW_SOLID)
                viewModel.closeLeftPane()
            }
            QuickDrawModePill("Vectơ", Icons.Default.NorthEast, uiState.interactionMode == InteractionMode.DRAW_VECTOR, RoseAccent, "mode_vector_btn") {
                viewModel.setInteractionMode(InteractionMode.DRAW_VECTOR)
                viewModel.closeLeftPane()
            }
        }
    }
}

@Composable
private fun QuickDrawModePill(
    label: String,
    icon: ImageVector,
    active: Boolean,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .clip(CircleShape)
            .background(if (active) color else SlateDark)
            .border(1.dp, if (active) Color.White.copy(alpha = 0.4f) else color.copy(alpha = 0.55f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color.White else color, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else SlateTextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
