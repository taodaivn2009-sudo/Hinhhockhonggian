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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.GeometryUiState
import com.example.engine.LeftPane
import com.example.ui.panes.*
import com.example.ui.theme.*
import com.example.viewmodel.GeometryViewModel

/**
 * Adaptive Inspector Sheet:
 * - On compact/portrait phones: opens as a roomy translucent Bottom Sheet anchored above the Bottom Tab Bar
 *   so the 3D shape stays visible in the upper half of the screen while editing.
 * - On medium/expanded tablets: opens as a crisp left-side Inspector Panel alongside the 3D canvas.
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
                .padding(start = 8.dp, end = 8.dp, bottom = 72.dp)
                .heightIn(max = maxSheetHeight)
        } else {
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 102.dp, bottom = 78.dp)
                .width(356.dp)
                .fillMaxHeight()
        }

        val isGlass = ActiveThemeState.current.liquidGlass.enabled
        val headerBg = if (isGlass) SlateDeepest.copy(alpha = 0.42f) else SlateDeepest

        Column(
            modifier = containerModifier
                .liquidGlassSurface(
                    shape = RoundedCornerShape(16.dp),
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
                    text = activePane.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .testTag("close_left_drawer_btn")
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.closeLeftPane() }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
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
                    LeftPane.DRAW -> DrawPane(uiState, viewModel)
                    LeftPane.COORDS -> CoordsPane(uiState, viewModel)
                    LeftPane.SETTINGS -> SettingsPane(uiState, viewModel, onImportJsonClick)
                    LeftPane.FORMULAS -> FormulasPane(uiState, viewModel)
                }
            }
        }
    }
}
