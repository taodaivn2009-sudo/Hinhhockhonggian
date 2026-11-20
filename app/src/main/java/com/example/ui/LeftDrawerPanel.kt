package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.engine.LeftPane
import com.example.ui.panes.*
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel

@Composable
fun LeftDockAndDrawer(
    uiState: GeometryUiState,
    viewModel: GeometryViewModel,
    drawerMaxWidth: Dp,
    onImportJsonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Handle system Back button to close open left drawer cleanly
    BackHandler(enabled = uiState.activeLeftPane != null) {
        viewModel.closeLeftPane()
    }

    Row(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 8.dp, top = 56.dp, bottom = 78.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical Left Icon Rail
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SlateDark.copy(alpha = 0.94f))
                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RailButton(
                icon = Icons.Default.Category,
                label = "Khối",
                active = uiState.activeLeftPane == LeftPane.SHAPES,
                testTag = "rail_shapes_btn",
                contentDescription = "Chọn khối hình 3D",
                onClick = { viewModel.toggleLeftPane(LeftPane.SHAPES) }
            )
            RailButton(
                icon = Icons.Default.Edit,
                label = "Kẻ hình",
                active = uiState.activeLeftPane == LeftPane.DRAW,
                testTag = "rail_draw_btn",
                contentDescription = "Kẻ thêm điểm, đường, vectơ, mặt phẳng",
                onClick = { viewModel.toggleLeftPane(LeftPane.DRAW) }
            )
            RailButton(
                icon = Icons.Default.Public,
                label = "Oxyz",
                active = uiState.activeLeftPane == LeftPane.COORDS,
                testTag = "rail_coords_btn",
                contentDescription = "Hệ trục tọa độ Oxyz và máy tính vectơ",
                onClick = { viewModel.toggleLeftPane(LeftPane.COORDS) }
            )
            RailButton(
                icon = Icons.Default.Tune,
                label = "Cài đặt",
                active = uiState.activeLeftPane == LeftPane.SETTINGS,
                testTag = "rail_settings_btn",
                contentDescription = "Kích thước và cài đặt hiển thị",
                onClick = { viewModel.toggleLeftPane(LeftPane.SETTINGS) }
            )
            RailButton(
                icon = Icons.Default.Analytics,
                label = "Số liệu",
                active = uiState.activeLeftPane == LeftPane.FORMULAS,
                testTag = "rail_formulas_btn",
                contentDescription = "Công thức và số liệu đo đạc thực tế",
                onClick = { viewModel.toggleLeftPane(LeftPane.FORMULAS) }
            )
            HorizontalDivider(
                color = SlateBorder,
                modifier = Modifier.width(32.dp).padding(vertical = 2.dp)
            )
            RailButton(
                icon = Icons.Default.Save,
                label = "Lưu",
                active = false,
                tintOverride = EmeraldLight,
                testTag = "rail_save_btn",
                contentDescription = "Đặt tên file, lưu bài hoặc xuất PNG/JSON",
                onClick = { viewModel.openSaveModal() }
            )
        }

        // Slide-out Drawer Panel
        AnimatedVisibility(
            visible = uiState.activeLeftPane != null,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            val activePane = uiState.activeLeftPane ?: LeftPane.SHAPES
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .width(drawerMaxWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SlateDark.copy(alpha = 0.96f))
                    .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
            ) {
                // Drawer Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDeepest)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activePane.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = IndigoSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .testTag("close_left_drawer_btn")
                            .clip(RoundedCornerShape(6.dp))
                            .background(SlateSurface)
                            .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
                            .clickable { viewModel.closeLeftPane() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Thu gọn ngăn kéo",
                            tint = SlateTextLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Thu gọn", style = MaterialTheme.typography.labelSmall, color = SlateTextLight)
                    }
                }
                HorizontalDivider(color = SlateSurface)

                // Drawer Scrollable Body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(11.dp)
                ) {
                    when (activePane) {
                        LeftPane.SHAPES -> ShapesPane(uiState, viewModel)
                        LeftPane.DRAW -> DrawPane(uiState, viewModel)
                        LeftPane.COORDS -> CoordsPane(uiState, viewModel)
                        LeftPane.SETTINGS -> SettingsPane(uiState, viewModel, onImportJsonClick)
                        LeftPane.FORMULAS -> FormulasPane(uiState)
                    }
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    tintOverride: Color? = null,
    testTag: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    val bg = if (active) IndigoPrimary else Color.Transparent
    val border = if (active) IndigoLight else Color.Transparent
    val fg = when {
        active -> SlateTextPrimary
        tintOverride != null -> tintOverride
        else -> SlateTextSecondary
    }

    Column(
        modifier = Modifier
            .testTag(testTag)
            .size(width = 48.dp, height = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
