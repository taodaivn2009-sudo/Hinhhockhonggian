package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo2d.MathWorkspace
import com.example.ui.theme.*

/**
 * Unified 5-tab Floating Pill Bottom Navigation Bar compatible with Material 3 and Liquid Glass.
 * Automatically slides away smoothly when [visible] is false (Immersive / Fullscreen mode or Settings sub-screens).
 */
@Composable
fun FloatingPillBottomNavBar(
    current: MathWorkspace,
    visible: Boolean,
    hasUpdateBadge: Boolean,
    onSelect: (MathWorkspace) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        elevated = true,
                        fallbackBg = SlateDark.copy(alpha = 0.96f),
                        fallbackBorder = SlateBorder.copy(alpha = 0.85f)
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("floating_bottom_nav_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BottomNavTabItem(
                    label = "3D",
                    selectedIcon = Icons.Default.Category,
                    unselectedIcon = Icons.Outlined.Category,
                    selected = current == MathWorkspace.SPACE_3D,
                    activeColor = IndigoPrimary,
                    testTag = "switch_workspace_3d",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(MathWorkspace.SPACE_3D) }
                )
                BottomNavTabItem(
                    label = "2D",
                    selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
                    unselectedIcon = Icons.AutoMirrored.Filled.ShowChart,
                    selected = current == MathWorkspace.PLANE_2D,
                    activeColor = EmeraldDark,
                    testTag = "switch_workspace_2d",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(MathWorkspace.PLANE_2D) }
                )
                BottomNavTabItem(
                    label = "Tài liệu",
                    selectedIcon = Icons.Default.MenuBook,
                    unselectedIcon = Icons.Outlined.MenuBook,
                    selected = current == MathWorkspace.DOCUMENTS,
                    activeColor = AmberDeep,
                    testTag = "switch_workspace_docs",
                    modifier = Modifier.weight(1.08f),
                    onClick = { onSelect(MathWorkspace.DOCUMENTS) }
                )
                BottomNavTabItem(
                    label = "AI",
                    selectedIcon = Icons.Default.AutoAwesome,
                    unselectedIcon = Icons.Outlined.AutoAwesome,
                    selected = current == MathWorkspace.AI,
                    activeColor = PurpleAccent,
                    testTag = "switch_workspace_ai",
                    modifier = Modifier.weight(0.92f),
                    onClick = { onSelect(MathWorkspace.AI) }
                )
                BottomNavTabItem(
                    label = "Cài đặt",
                    selectedIcon = Icons.Default.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    selected = current == MathWorkspace.SETTINGS,
                    activeColor = SkyBlue,
                    showBadge = hasUpdateBadge,
                    testTag = "switch_workspace_settings",
                    modifier = Modifier.weight(1.06f),
                    onClick = { onSelect(MathWorkspace.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavTabItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    val palette = ActiveThemeState.current
    val isGlass = palette.liquidGlass.enabled
    val iconTint = when {
        selected && isGlass && !palette.isDark -> SlateTextPrimary
        selected -> Color.White
        isGlass && palette.isDark -> Color.White.copy(alpha = 0.80f)
        else -> SlateTextSecondary
    }
    val labelTint = when {
        selected && isGlass && !palette.isDark -> SlateTextPrimary
        selected -> Color.White
        isGlass && palette.isDark -> Color.White.copy(alpha = 0.74f)
        else -> SlateTextSecondary
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .liquidGlassActiveBubble(
                active = selected,
                shape = CircleShape,
                fallbackActiveColor = activeColor
            )
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp, y = (-2).dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EmeraldLight)
                            .border(1.dp, SlateDark, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelTint,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Unobtrusive floating button shown in Immersive Fullscreen mode on 3D/2D/PDF
 * so users can always restore navigation and HUD controls in 1 tap.
 */
@Composable
fun ImmersiveRestorePill(
    visible: Boolean,
    onExitImmersive: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .defaultMinSize(minHeight = 44.dp)
                .liquidGlassSurface(
                    shape = CircleShape,
                    pillShape = CircleShape,
                    elevated = true,
                    fallbackBg = SlateDark.copy(alpha = 0.88f),
                    fallbackBorder = SlateBorder.copy(alpha = 0.8f)
                )
                .clip(CircleShape)
                .clickable(onClick = onExitImmersive)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("exit_immersive_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "Hiện thanh điều hướng",
                tint = CyanAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Hiện công cụ",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}
