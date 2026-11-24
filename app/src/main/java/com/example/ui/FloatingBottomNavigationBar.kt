package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geo2d.MathWorkspace
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

private data class NavTabSpec(
    val workspace: MathWorkspace,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val activeColor: Color,
    val testTag: String
)

private val NAV_TAB_SPECS = listOf(
    NavTabSpec(
        workspace = MathWorkspace.SPACE_3D,
        label = "3D",
        selectedIcon = Icons.Default.Category,
        unselectedIcon = Icons.Outlined.Category,
        activeColor = IndigoPrimary,
        testTag = "switch_workspace_3d"
    ),
    NavTabSpec(
        workspace = MathWorkspace.PLANE_2D,
        label = "2D",
        selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Filled.ShowChart,
        activeColor = EmeraldDark,
        testTag = "switch_workspace_2d"
    ),
    NavTabSpec(
        workspace = MathWorkspace.DOCUMENTS,
        label = "Tài liệu",
        selectedIcon = Icons.Default.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook,
        activeColor = AmberDeep,
        testTag = "switch_workspace_docs"
    ),
    NavTabSpec(
        workspace = MathWorkspace.AI,
        label = "AI",
        selectedIcon = Icons.Default.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        activeColor = PurpleAccent,
        testTag = "switch_workspace_ai"
    ),
    NavTabSpec(
        workspace = MathWorkspace.SETTINGS,
        label = "Cài đặt",
        selectedIcon = Icons.Default.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        activeColor = SkyBlue,
        testTag = "switch_workspace_settings"
    )
)

/**
 * Authentic iOS 26 Floating Liquid Navigation Bar:
 * - Outer crystal capsule refracts live scrolling content behind it at 60fps via AGSL RuntimeShader.
 * - Single fluid-morphing active-tab droplet lens glides seamlessly across all 5 tabs with spring
 *   inertia, velocity-coupled elastic stretch, stronger lens magnification, and RGB chromatic rim.
 * - Supports BOTH direct tap AND horizontal finger drag-to-scrub across tabs!
 */
@Composable
fun FloatingPillBottomNavBar(
    current: MathWorkspace,
    visible: Boolean,
    hasUpdateBadge: Boolean,
    onSelect: (MathWorkspace) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ActiveThemeState.current
    val isGlass = palette.liquidGlass.enabled
    val lg = palette.liquidGlass
    val density = LocalDensity.current

    val currentIndex = NAV_TAB_SPECS.indexOfFirst { it.workspace == current }.coerceAtLeast(0)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubSlotPos by remember { mutableFloatStateOf(currentIndex.toFloat()) }

    LaunchedEffect(currentIndex) {
        if (!isScrubbing) {
            scrubSlotPos = currentIndex.toFloat()
        }
    }

    val targetSlot = if (isScrubbing) scrubSlotPos else currentIndex.toFloat()
    val animatedSlot by animateFloatAsState(
        targetValue = targetSlot,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = if (isScrubbing) Spring.StiffnessHigh else Spring.StiffnessMediumLow
        ),
        label = "floating_nav_droplet_slot"
    )

    // Elastic liquid stretch proportional to travel velocity or active finger scrubbing
    val velocityDelta = abs(targetSlot - animatedSlot)
    val stretchFactor = (velocityDelta * 0.24f + if (isScrubbing) 0.08f else 0f).coerceIn(0f, 0.28f)
    val liftScale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.09f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "floating_nav_droplet_lift"
    )

    val activeSpec = NAV_TAB_SPECS[animatedSlot.roundToInt().coerceIn(0, NAV_TAB_SPECS.lastIndex)]

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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        elevated = true,
                        lensZoom = 1.04f,
                        crystalClarity = true,
                        fallbackBg = SlateDark.copy(alpha = 0.96f),
                        fallbackBorder = SlateBorder.copy(alpha = 0.85f)
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("floating_bottom_nav_bar")
            ) {
                val tabCount = NAV_TAB_SPECS.size
                val innerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
                val slotWidthDp = maxWidth / tabCount
                val slotWidthPx = innerWidthPx / tabCount
                val pillOffsetPx = (animatedSlot * slotWidthPx).roundToInt()

                // 1. Sliding Fluid-Morphing Magnified Liquid Glass Droplet Pill
                val dropletModifier = if (isGlass) {
                    val disp = lg.chromaticDispersion
                    val specular = lg.specularGlow
                    val rimBrush = Brush.linearGradient(
                        colors = listOf(
                            lerp(Color.White, Color(0xFFCFFAFE), disp * 0.45f).copy(alpha = (0.76f + specular * 0.22f).coerceIn(0f, 1f)),
                            Color.White.copy(alpha = 0.16f),
                            lerp(Color.White, Color(0xFFFFD4E8), disp * 0.35f).copy(alpha = (0.48f + specular * 0.25f).coerceIn(0f, 1f))
                        )
                    )
                    Modifier
                        .offset { IntOffset(x = pillOffsetPx, y = 0) }
                        .width(slotWidthDp)
                        .height(48.dp)
                        .graphicsLayer {
                            scaleX = liftScale * (1f + stretchFactor)
                            scaleY = liftScale * (1f - stretchFactor * 0.26f)
                        }
                        .liquidGlassSurface(
                            shape = CircleShape,
                            pillShape = CircleShape,
                            elevated = true,
                            lensZoom = if (isScrubbing) 1.48f else 1.32f,
                            crystalClarity = true,
                            fallbackBg = activeSpec.activeColor.copy(alpha = 0.28f),
                            fallbackBorder = Color.White.copy(alpha = 0.65f)
                        )
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (palette.isDark) {
                                    listOf(
                                        Color.White.copy(alpha = if (isScrubbing) 0.26f else 0.18f),
                                        activeSpec.activeColor.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = if (isScrubbing) 0.56f else 0.44f),
                                        activeSpec.activeColor.copy(alpha = 0.12f),
                                        Color.White.copy(alpha = 0.24f)
                                    )
                                }
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = if (isScrubbing) 1.5.dp else 1.2.dp,
                            brush = rimBrush,
                            shape = CircleShape
                        )
                } else {
                    Modifier
                        .offset { IntOffset(x = pillOffsetPx, y = 0) }
                        .width(slotWidthDp)
                        .height(48.dp)
                        .graphicsLayer {
                            scaleX = liftScale * (1f + stretchFactor)
                            scaleY = liftScale * (1f - stretchFactor * 0.24f)
                        }
                        .clip(CircleShape)
                        .background(activeSpec.activeColor, CircleShape)
                }

                Box(modifier = dropletModifier)

                // 2. Interactive 5-Tab Row with Direct Finger Drag-to-Scrub + Tap Physics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(tabCount, innerWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isScrubbing = true
                                    val idxF = ((offset.x / slotWidthPx) - 0.5f).coerceIn(0f, (tabCount - 1).toFloat())
                                    scrubSlotPos = idxF
                                    val snapped = idxF.roundToInt().coerceIn(0, tabCount - 1)
                                    if (NAV_TAB_SPECS[snapped].workspace != current) {
                                        onSelect(NAV_TAB_SPECS[snapped].workspace)
                                    }
                                },
                                onDragEnd = {
                                    val snapped = scrubSlotPos.roundToInt().coerceIn(0, tabCount - 1)
                                    isScrubbing = false
                                    scrubSlotPos = snapped.toFloat()
                                    onSelect(NAV_TAB_SPECS[snapped].workspace)
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                    scrubSlotPos = currentIndex.toFloat()
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val idxF = ((change.position.x / slotWidthPx) - 0.5f).coerceIn(0f, (tabCount - 1).toFloat())
                                    scrubSlotPos = idxF
                                    val hovered = idxF.roundToInt().coerceIn(0, tabCount - 1)
                                    if (NAV_TAB_SPECS[hovered].workspace != current) {
                                        onSelect(NAV_TAB_SPECS[hovered].workspace)
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NAV_TAB_SPECS.forEachIndexed { idx, spec ->
                        val selected = if (isScrubbing) {
                            animatedSlot.roundToInt() == idx
                        } else {
                            current == spec.workspace
                        }
                        BottomNavTabItem(
                            label = spec.label,
                            selectedIcon = spec.selectedIcon,
                            unselectedIcon = spec.unselectedIcon,
                            selected = selected,
                            activeColor = spec.activeColor,
                            showBadge = spec.workspace == MathWorkspace.SETTINGS && hasUpdateBadge,
                            testTag = spec.testTag,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(spec.workspace) }
                        )
                    }
                }
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
        selected && isGlass && !palette.isDark -> lerp(activeColor, SlateTextPrimary, 0.35f)
        selected && isGlass -> lerp(activeColor, Color.White, 0.55f)
        selected -> Color.White
        isGlass && palette.isDark -> Color.White.copy(alpha = 0.82f)
        else -> SlateTextSecondary
    }
    val labelTint = when {
        selected && isGlass && !palette.isDark -> SlateTextPrimary
        selected -> Color.White
        isGlass && palette.isDark -> Color.White.copy(alpha = 0.76f)
        else -> SlateTextSecondary
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .liquidGlassPressPhysics(
                enabled = isGlass,
                scaleOnPress = 1.07f,
                bloomColor = if (selected) activeColor else Color.White
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 3.dp, vertical = 5.dp)
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
    val isGlass = ActiveThemeState.current.liquidGlass.enabled

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
                .defaultMinSize(minHeight = 48.dp)
                .liquidGlassPressPhysics(enabled = isGlass, scaleOnPress = 1.08f, bloomColor = CyanAccent)
                .liquidGlassSurface(
                    shape = CircleShape,
                    pillShape = CircleShape,
                    elevated = true,
                    lensZoom = 1.12f,
                    crystalClarity = true,
                    fallbackBg = SlateDark.copy(alpha = 0.88f),
                    fallbackBorder = SlateBorder.copy(alpha = 0.8f)
                )
                .clip(CircleShape)
                .clickable(onClick = onExitImmersive)
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
