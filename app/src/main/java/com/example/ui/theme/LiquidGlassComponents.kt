package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive iOS 26 Liquid Glass Spring & Radial Bloom Physics modifier.
 * - Observes touch down/move/up in [PointerEventPass.Initial] so parent/sibling clickables keep working seamlessly.
 * - Applies elastic liquid scale-up & slight organic squash/stretch with spring damping.
 * - Radiates a subtle internal light bloom from the exact touch coordinate [Offset].
 */
fun Modifier.liquidGlassPressPhysics(
    enabled: Boolean = true,
    scaleOnPress: Float = 1.07f,
    bloomColor: Color = Color.White
): Modifier = composed {
    if (!enabled) return@composed this

    var isPressed by remember { mutableStateOf(false) }
    var touchPoint by remember { mutableStateOf(Offset.Unspecified) }

    val scaleX by animateFloatAsState(
        targetValue = if (isPressed) scaleOnPress else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lg_press_scale_x"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (isPressed) (1f + (scaleOnPress - 1f) * 0.84f) else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lg_press_scale_y"
    )
    val bloomAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.42f else 0f,
        animationSpec = if (isPressed) tween(90) else tween(320),
        label = "lg_press_bloom"
    )

    this
        .pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                isPressed = true
                touchPoint = down.position
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (up != null) {
                    touchPoint = up.position
                }
                isPressed = false
            }
        }
        .graphicsLayer {
            this.scaleX = scaleX
            this.scaleY = scaleY
        }
        .drawWithContent {
            drawContent()
            if (bloomAlpha > 0.005f) {
                val center = if (touchPoint != Offset.Unspecified) {
                    touchPoint
                } else {
                    Offset(size.width * 0.5f, size.height * 0.5f)
                }
                val radius = maxOf(size.width, size.height).coerceAtLeast(24f) * 0.92f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bloomColor.copy(alpha = bloomAlpha),
                            bloomColor.copy(alpha = bloomAlpha * 0.28f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    )
                )
            }
        }
}

/**
 * Authentic iOS 26 Liquid Glass Toggle Switch:
 * - Glossy translucent pill track with vibrant internal glow when ON.
 * - Thick refractive crystal glass droplet thumb that elastically stretches horizontally
 *   with liquid inertia during press and state transitions.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedTrackColor: Color = Color(0xFF30D158),
    enabled: Boolean = true,
    forceCrystalStyle: Boolean = false
) {
    val palette = ActiveThemeState.current
    val lg = palette.liquidGlass
    val useGlass = lg.enabled || forceCrystalStyle
    val density = LocalDensity.current

    var isPressed by remember { mutableStateOf(false) }
    var isMorphing by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        isMorphing = true
        delay(180)
        isMorphing = false
    }

    val trackWidth = 58.dp
    val trackHeight = 32.dp
    val thumbPadding = 3.dp
    val baseThumbW = 26.dp
    val stretchedThumbW = 34.dp
    val thumbH = 26.dp

    val animatedThumbW by animateDpAsState(
        targetValue = if (isPressed || isMorphing) stretchedThumbW else baseThumbW,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lg_switch_thumb_w"
    )
    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lg_switch_progress"
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> checkedTrackColor
            palette.isDark -> Color(0xFF334155).copy(alpha = if (useGlass) 0.58f else 0.90f)
            else -> Color(0xFFCBD5E1).copy(alpha = if (useGlass) 0.72f else 0.95f)
        },
        animationSpec = tween(220),
        label = "lg_switch_track_color"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isPressed) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lg_switch_thumb_scale"
    )

    val maxTravelPx = with(density) { (trackWidth - thumbPadding * 2 - animatedThumbW).coerceAtLeast(0.dp).toPx() }
    val thumbOffsetPx = (thumbProgress * maxTravelPx).roundToInt()

    val disp = if (useGlass) lg.chromaticDispersion.coerceAtLeast(0.45f) else 0f
    val thumbBorderBrush = remember(useGlass, disp, isPressed, checked) {
        if (useGlass) {
            Brush.linearGradient(
                colors = listOf(
                    lerp(Color.White, Color(0xFFD8F6FF), disp * 0.45f).copy(alpha = 0.96f),
                    Color.White.copy(alpha = 0.36f),
                    lerp(Color.White, Color(0xFFFFD8EC), disp * 0.35f).copy(alpha = 0.85f)
                )
            )
        } else {
            Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.8f)))
        }
    }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        trackColor.copy(alpha = if (checked) 0.95f else trackColor.alpha),
                        lerp(trackColor, Color.Black, 0.14f).copy(alpha = if (checked) 0.92f else trackColor.alpha)
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (checked) 0.48f else 0.28f),
                        Color.White.copy(alpha = if (checked) 0.14f else 0.10f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(enabled, checked) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) {
                            onCheckedChange(!checked)
                        }
                    }
                )
            }
            .padding(thumbPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        // Subtle glossy top-edge highlight inside the pill track
        if (useGlass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.46f)
                    .align(Alignment.TopCenter)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (checked) 0.24f else 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Thick refractive crystal glass droplet thumb (stretches into clear refractive lens while pressed/moving!)
        val crystalActive = useGlass && (isPressed || isMorphing)
        Box(
            modifier = Modifier
                .offset { IntOffset(x = thumbOffsetPx, y = 0) }
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = if (isPressed) thumbScale * 0.96f else thumbScale
                }
                .width(animatedThumbW)
                .height(thumbH)
                .shadow(
                    elevation = if (isPressed) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(
                    brush = if (crystalActive) {
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.34f),
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.56f)
                            )
                        )
                    } else if (useGlass) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.96f),
                                Color(0xFFE6F3FF).copy(alpha = 0.88f),
                                Color.White.copy(alpha = 0.95f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                    },
                    shape = CircleShape
                )
                .border(
                    width = if (crystalActive) 1.4.dp else 1.dp,
                    brush = thumbBorderBrush,
                    shape = CircleShape
                )
        ) {
            // Curved meniscus specular highlight on top-left of glass droplet
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.68f)
                    .height(8.dp)
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (crystalActive) 0.88f else 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Authentic iOS 26 Liquid Glass Slider:
 * - Smooth translucent pill track with vibrant glowing active segment.
 * - Crystal pill-shaped glass lens thumb that fluidly expands and turns crystal-refractive
 *   while dragging, magnifying the track underneath with crisp specular Fresnel edges.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    accentColor: Color = CyanAccent,
    testTag: String? = null,
    forceCrystalStyle: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val palette = ActiveThemeState.current
    val lg = palette.liquidGlass
    val useGlass = lg.enabled || forceCrystalStyle
    val density = LocalDensity.current

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val thumbW by animateDpAsState(
        targetValue = if (isDragging) 40.dp else 30.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lg_slider_thumb_w"
    )
    val thumbH by animateDpAsState(
        targetValue = if (isDragging) 24.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lg_slider_thumb_h"
    )
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 7.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "lg_slider_track_h"
    )

    fun updateFromX(xPx: Float) {
        val norm = (xPx / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val newVal = valueRange.start + norm * span
        if (abs(newVal - value) > 1e-5f) {
            onValueChange(newVal)
        }
    }

    val taggedMod = if (testTag != null) modifier.testTag(testTag) else modifier

    BoxWithConstraints(
        modifier = taggedMod
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        updateFromX(offset.x)
                        tryAwaitRelease()
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        updateFromX(offset.x)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbWPx = with(density) { thumbW.toPx() }
        val usableTravel = (trackWidthPx - thumbWPx).coerceAtLeast(0f)
        val thumbX = (fraction * usableTravel).roundToInt()

        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(
                    if (palette.isDark) Color.White.copy(alpha = 0.16f)
                    else Color.Black.copy(alpha = 0.12f)
                )
                .border(
                    width = 0.75.dp,
                    color = Color.White.copy(alpha = if (palette.isDark) 0.18f else 0.35f),
                    shape = CircleShape
                )
        ) {
            // Luminous active progress fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.85f),
                                lerp(accentColor, Color.White, 0.28f)
                            )
                        )
                    )
            )
        }

        // Translucent refractive crystal pill thumb
        val disp = if (useGlass) lg.chromaticDispersion.coerceAtLeast(0.45f) else 0f
        Box(
            modifier = Modifier
                .offset { IntOffset(x = thumbX, y = 0) }
                .size(width = thumbW, height = thumbH)
                .shadow(
                    elevation = if (isDragging) 10.dp else 5.dp,
                    shape = CircleShape,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(
                    brush = if (useGlass && isDragging) {
                        // Crystal clear magnifying glass lens while dragging!
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.46f),
                                Color.White.copy(alpha = 0.14f),
                                Color.White.copy(alpha = 0.36f)
                            )
                        )
                    } else if (useGlass) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.94f),
                                Color(0xFFE0F2FE).copy(alpha = 0.78f),
                                Color.White.copy(alpha = 0.90f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                    },
                    shape = CircleShape
                )
                .border(
                    width = if (isDragging) 1.5.dp else 1.1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            lerp(Color.White, Color(0xFFCFFAFE), disp * 0.5f).copy(alpha = 0.98f),
                            Color.White.copy(alpha = 0.34f),
                            lerp(Color.White, Color(0xFFFFD6E8), disp * 0.4f).copy(alpha = 0.88f)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            // Top curved glass specular highlight
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.72f)
                    .height(6.dp)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )
        }
    }
}
