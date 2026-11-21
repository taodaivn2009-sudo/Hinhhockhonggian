package com.example.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared live backdrop capture container. Any sibling content decorated with
 * [Modifier.liquidGlassBackdropSource] records its GPU output into [backdropLayer] so floating
 * [Modifier.liquidGlassSurface] capsules sample, blur, and optically refract the real live scene
 * directly underneath them at 60fps.
 */
@Stable
class LiquidGlassBackdropState(val backdropLayer: GraphicsLayer) {
    var rootPositionInWindow by mutableStateOf(Offset.Zero)
    var hasRecorded by mutableStateOf(false)
    private var rawFrameCounter = 0L
    var frameVersion by mutableLongStateOf(0L)
        private set

    fun notifyRecorded() {
        hasRecorded = true
        rawFrameCounter += 1L
        frameVersion = rawFrameCounter
    }
}

val LocalLiquidGlassBackdrop = compositionLocalOf<LiquidGlassBackdropState?> { null }

@Composable
fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdropState(layer) }
}

/**
 * Marks a background layer (3D canvas, 2D canvas, or scrolling screen content) as the live
 * optical source sampled by floating Liquid Glass pills above it.
 */
fun Modifier.liquidGlassBackdropSource(state: LiquidGlassBackdropState): Modifier = this
    .onGloballyPositioned { coords ->
        state.rootPositionInWindow = coords.positionInWindow()
    }
    .drawWithContent {
        val lg = ActiveThemeState.current.liquidGlass
        if (lg.enabled && size.width > 1f && size.height > 1f) {
            state.backdropLayer.record {
                this@drawWithContent.drawContent()
            }
            state.notifyRecorded()
            drawLayer(state.backdropLayer)
        } else {
            drawContent()
        }
    }

/**
 * Optical math helpers for Liquid Glass frosted alpha, bevel thickness, and AGSL shader generation.
 */
object LiquidGlassMath {
    fun coreAlpha(config: LiquidGlassConfig, isDark: Boolean, elevated: Boolean): Float {
        val safe = config.sanitized()
        // When real backdrop blur is active, keep the frosted tint veil much clearer so
        // live colors and curved edge refractions shine right through like real crystal glass!
        val baseOpaque = if (isDark) {
            if (elevated) 0.72f else 0.64f
        } else {
            if (elevated) 0.76f else 0.68f
        }
        val minAlpha = if (isDark) {
            if (elevated) 0.14f else 0.10f
        } else {
            if (elevated) 0.20f else 0.14f
        }
        val raw = baseOpaque - (safe.translucency * (baseOpaque - minAlpha))
        val contrastBoost = if (safe.highContrastText) 0.18f else 0f
        return (raw + contrastBoost).coerceIn(0.10f, 0.90f)
    }

    fun borderWidthDp(config: LiquidGlassConfig): Dp {
        val safe = config.sanitized()
        return (1.0f + safe.borderIntensity * 0.6f).dp
    }

    /**
     * AGSL (Android 13+) optical Liquid Glass lens shader:
     * Computes exact rounded-capsule Signed Distance Field (SDF), surface normal,
     * curved convex bevel refraction (bending background pixels inward near the glass rim),
     * subtle RGB prismatic chromatic dispersion along the bevel, inner glass thickness shadow,
     * and bright directional Fresnel specular reflections.
     */
    const val LIQUID_GLASS_AGSL_SRC: String = """
        uniform shader content;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float refractionStrength;
        uniform float dispersion;
        uniform float specularStrength;
        uniform float isDark;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        float2 sdfNormal(float2 p, float2 b, float r) {
            float eps = 1.5;
            float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
            float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
            float len = max(length(float2(dx, dy)), 0.0001);
            return float2(dx, dy) / len;
        }

        half4 main(float2 fragCoord) {
            float2 halfRes = resolution * 0.5;
            float r = min(cornerRadius, min(halfRes.x, halfRes.y));
            float2 p = fragCoord - halfRes;
            float d = sdRoundedBox(p, halfRes, r);

            // Distance inward from outer glass perimeter (in pixels)
            float edgeDist = max(-d, 0.0);
            float minDim = min(resolution.x, resolution.y);
            float bevelWidth = clamp(minDim * 0.36, 14.0, 44.0);
            float t = clamp(1.0 - edgeDist / bevelWidth, 0.0, 1.0);
            float2 n = sdfNormal(p, halfRes, r);

            // Convex water-droplet / lens edge refraction bends rays inward near the curved rim
            float bend = pow(t, 2.1) * refractionStrength * bevelWidth * 0.72;
            // Gentle optical lens magnification in the center
            float2 centerZoom = (fragCoord - halfRes) * (1.0 - 0.025 * refractionStrength) + halfRes;
            float2 sampleBase = centerZoom - n * bend;

            // Prismatic chromatic dispersion along the curved bevel edge
            float dispPx = pow(t, 1.8) * dispersion * refractionStrength * 6.5;
            float2 minUv = float2(1.0);
            float2 maxUv = max(resolution - float2(1.0), float2(2.0));

            half4 colR = content.eval(clamp(sampleBase - n * dispPx, minUv, maxUv));
            half4 colG = content.eval(clamp(sampleBase, minUv, maxUv));
            half4 colB = content.eval(clamp(sampleBase + n * dispPx, minUv, maxUv));
            half3 rgb = half3(colR.r, colG.g, colB.b);

            // Subtle internal glass thickness absorption right behind the outer bevel (gives 3D depth)
            float innerDepthRing = exp(-pow((edgeDist - bevelWidth * 0.22) / max(bevelWidth * 0.24, 3.0), 2.0));
            float depthDarken = innerDepthRing * (isDark > 0.5 ? 0.14 : 0.08) * refractionStrength;
            rgb *= half(1.0 - depthDarken);

            // Directional Fresnel specular highlight along curved rim (top-left & bottom-right glints)
            float rimBand = pow(t, 3.2);
            float lightTopLeft = max(dot(n, normalize(float2(-0.55, -0.84))), 0.0);
            float lightBottomRight = max(dot(n, normalize(float2(0.55, 0.84))), 0.0);
            float spec = rimBand * (0.28 + 0.72 * lightTopLeft + 0.42 * lightBottomRight) * specularStrength;
            rgb += half3(spec * (isDark > 0.5 ? 0.55 : 0.72));

            return half4(clamp(rgb, half3(0.0), half3(1.0)), colG.a);
        }
    """

    @RequiresApi(Build.VERSION_CODES.S)
    fun buildGlassRenderEffect(
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        blurPx: Float,
        config: LiquidGlassConfig,
        isDark: Boolean
    ): RenderEffect? {
        if (widthPx < 4f || heightPx < 4f) return null
        val safeBlur = blurPx.coerceIn(1f, 96f)
        val blurEffect = AndroidRenderEffect.createBlurEffect(
            safeBlur,
            safeBlur,
            Shader.TileMode.CLAMP
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && config.refraction > 0.02f) {
            val chained = runCatching {
                val shader = RuntimeShader(LIQUID_GLASS_AGSL_SRC)
                shader.setFloatUniform("resolution", widthPx, heightPx)
                shader.setFloatUniform("cornerRadius", cornerRadiusPx.coerceAtLeast(1f))
                shader.setFloatUniform("refractionStrength", config.refraction)
                shader.setFloatUniform("dispersion", config.chromaticDispersion)
                shader.setFloatUniform("specularStrength", config.specularGlow)
                shader.setFloatUniform("isDark", if (isDark) 1f else 0f)
                val lensEffect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                AndroidRenderEffect.createChainEffect(lensEffect, blurEffect)
            }.getOrNull()
            if (chained != null) return chained.asComposeRenderEffect()
        }

        return blurEffect.asComposeRenderEffect()
    }
}

/**
 * Authentic real-time Liquid Glass lens modifier!
 * - Samples the live [LocalLiquidGlassBackdrop] underneath this element at 60fps.
 * - Applies GPU Gaussian frosted blur (Android 12+) chained with an AGSL convex lens edge
 *   refraction + chromatic dispersion shader (Android 13+).
 * - Adds floating drop shadow, subtle neutral frosted veil, gliding shimmer highlight,
 *   and crisp prismatic specular Fresnel rim border.
 * - When Liquid Glass is disabled, renders the clean fallback Material surface & border.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(14.dp),
    pillShape: Shape? = null,
    cornerRadiusDp: Dp = 20.dp,
    elevated: Boolean = false,
    fallbackBg: Color? = null,
    fallbackBorder: Color? = null,
    accentBorder: Color? = null
): Modifier = composed {
    val palette = ActiveThemeState.current
    val lg = palette.liquidGlass
    val resolvedFallbackBg = fallbackBg ?: palette.bgDark.copy(alpha = if (elevated) 0.95f else 0.90f)
    val resolvedFallbackBorder = accentBorder ?: fallbackBorder ?: palette.border.copy(alpha = 0.85f)

    if (!lg.enabled) {
        this
            .clip(shape)
            .background(resolvedFallbackBg, shape)
            .border(1.dp, resolvedFallbackBorder, shape)
    } else {
        val activeShape = pillShape ?: shape
        val backdropState = LocalLiquidGlassBackdrop.current
        val density = LocalDensity.current
        val glassLayer = rememberGraphicsLayer()
        var elementPosInWindow by remember { mutableStateOf(Offset.Zero) }
        var elementSize by remember { mutableStateOf(IntSize.Zero) }

        val shimmerPhase = if (lg.dynamicShimmer && lg.specularGlow > 0.05f) {
            val transition = rememberInfiniteTransition(label = "liquid_glass_shimmer")
            val phase by transition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shimmer_phase"
            )
            phase
        } else {
            0.22f
        }

        val blurPx = with(density) { lg.blurRadius.dp.toPx() }
        val cornerPx = with(density) {
            if (pillShape != null) {
                (minOf(elementSize.width, elementSize.height) * 0.5f).coerceAtLeast(cornerRadiusDp.toPx())
            } else {
                cornerRadiusDp.toPx()
            }
        }

        val cachedRenderEffect = remember(
            elementSize,
            cornerPx,
            blurPx,
            lg.refraction,
            lg.chromaticDispersion,
            lg.specularGlow,
            palette.isDark
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && elementSize.width > 0 && elementSize.height > 0) {
                LiquidGlassMath.buildGlassRenderEffect(
                    widthPx = elementSize.width.toFloat(),
                    heightPx = elementSize.height.toFloat(),
                    cornerRadiusPx = cornerPx,
                    blurPx = blurPx,
                    config = lg,
                    isDark = palette.isDark
                )
            } else {
                null
            }
        }

        val hasLiveBackdrop = backdropState != null && backdropState.hasRecorded
        // Keep the tint veil ultra-clear when real backdrop blur is active, slightly denser if no backdrop
        val coreAlpha = LiquidGlassMath.coreAlpha(lg, palette.isDark, elevated).let { a ->
            if (hasLiveBackdrop) (a * 0.62f).coerceIn(0.08f, 0.72f) else a
        }
        val tintStrength = lg.refractionTint
        val specStrength = lg.specularGlow
        val rimStrength = lg.borderIntensity

        val glassNeutral = if (palette.isDark) Color(0xFF141A24) else Color(0xFFFFFFFF)
        val tintPrimary = lerp(palette.primaryBright, palette.cyanAccent, 0.45f)

        val topCoreColor = lerp(glassNeutral, tintPrimary, tintStrength * (if (palette.isDark) 0.30f else 0.16f))
            .copy(alpha = (coreAlpha + 0.05f).coerceAtMost(0.88f))
        val bottomCoreColor = lerp(glassNeutral, palette.bgDeepest, if (palette.isDark) 0.25f else 0.06f)
            .copy(alpha = (coreAlpha - 0.03f).coerceAtLeast(0.06f))

        // Crisp Liquid Glass outer Fresnel rim border (bright white specular top-left & bottom-right)
        val rimTopLeft = if (accentBorder != null) {
            lerp(Color.White, accentBorder, 0.35f).copy(alpha = (0.48f + rimStrength * 0.50f).coerceAtMost(0.98f))
        } else {
            Color.White.copy(alpha = (0.34f + rimStrength * 0.56f).coerceAtMost(0.95f))
        }
        val rimMid = if (palette.isDark) {
            lerp(Color.White, tintPrimary, tintStrength).copy(alpha = 0.14f + rimStrength * 0.18f)
        } else {
            lerp(Color.White, palette.primary, tintStrength * 0.5f).copy(alpha = 0.32f + rimStrength * 0.28f)
        }
        val rimBottomRight = if (palette.isDark) {
            Color.White.copy(alpha = 0.22f + rimStrength * 0.44f)
        } else {
            Color.White.copy(alpha = 0.45f + rimStrength * 0.42f)
        }

        val borderWidth = LiquidGlassMath.borderWidthDp(lg)

        this
            .shadow(
                elevation = if (elevated) 14.dp else 8.dp,
                shape = activeShape,
                ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.55f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.65f else 0.22f)
            )
            .clip(activeShape)
            .onGloballyPositioned { coords ->
                elementPosInWindow = coords.positionInWindow()
                elementSize = coords.size
            }
            .drawWithContent {
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()

                // 1. REAL LIVE BACKDROP BLUR + AGSL LENS REFRACTION LAYER
                if (backdropState != null && backdropState.hasRecorded && w > 0 && h > 0) {
                    // Subscribe draw scope (without recomposing) to 60fps backdrop updates
                    @Suppress("UNUSED_VARIABLE")
                    val frameTick = backdropState.frameVersion
                    val relX = elementPosInWindow.x - backdropState.rootPositionInWindow.x
                    val relY = elementPosInWindow.y - backdropState.rootPositionInWindow.y
                    glassLayer.renderEffect = cachedRenderEffect
                    glassLayer.record(size = IntSize(w, h)) {
                        translate(left = -relX, top = -relY) {
                            drawLayer(backdropState.backdropLayer)
                        }
                    }
                    drawLayer(glassLayer)
                }

                // 2. Subtle frosted glass veil + optional chromatic tint
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topCoreColor, bottomCoreColor)
                    )
                )

                // 3. Convex upper-edge liquid gloss + gentle gliding specular sheen
                if (specStrength > 0.03f) {
                    val topGlossAlpha = specStrength * (if (palette.isDark) 0.20f else 0.42f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = topGlossAlpha),
                                0.26f to Color.White.copy(alpha = topGlossAlpha * 0.18f),
                                0.55f to Color.Transparent,
                                1.0f to Color.White.copy(alpha = topGlossAlpha * 0.22f)
                            )
                        )
                    )

                    if (lg.dynamicShimmer) {
                        val sx = size.width * shimmerPhase
                        val ribbonSpan = (size.width * 0.38f).coerceAtLeast(70f)
                        val sheenAlpha = specStrength * (if (palette.isDark) 0.14f else 0.26f)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = sheenAlpha),
                                    Color.Transparent
                                ),
                                start = Offset(sx - ribbonSpan, 0f),
                                end = Offset(sx + ribbonSpan, size.height)
                            )
                        )
                    }
                }

                // 4. Crisp unblurred foreground UI (icons & text) on top of the Liquid Glass
                drawContent()
            }
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(rimTopLeft, rimMid, rimBottomRight)
                ),
                shape = activeShape
            )
    }
}

/**
 * Luminous convex "Liquid Droplet" active pill indicator used for the currently selected tab
 * inside floating Liquid Glass navigation bars (matching the glowing convex glass lens
 * around the active tab in iOS / Android Liquid Glass bars).
 */
fun Modifier.liquidGlassActiveBubble(
    active: Boolean,
    shape: Shape,
    fallbackActiveColor: Color,
    fallbackInactiveColor: Color = Color.Transparent
): Modifier = composed {
    val palette = ActiveThemeState.current
    val lg = palette.liquidGlass

    if (!active) {
        this.clip(shape).background(fallbackInactiveColor, shape)
    } else if (!lg.enabled) {
        this.clip(shape).background(fallbackActiveColor, shape)
    } else {
        val spec = lg.specularGlow
        val centerGlow = if (palette.isDark) {
            Color.White.copy(alpha = 0.24f + spec * 0.16f)
        } else {
            Color.White.copy(alpha = 0.68f + spec * 0.24f)
        }
        val outerGlow = if (palette.isDark) {
            lerp(Color.White, fallbackActiveColor, 0.28f).copy(alpha = 0.10f + spec * 0.08f)
        } else {
            lerp(Color.White, fallbackActiveColor, 0.18f).copy(alpha = 0.34f + spec * 0.16f)
        }
        val rimHighlight = Color.White.copy(alpha = if (palette.isDark) 0.55f + spec * 0.35f else 0.85f)
        val rimBottom = Color.White.copy(alpha = if (palette.isDark) 0.22f else 0.45f)

        this
            .clip(shape)
            .drawBehind {
                // Soft convex droplet dome radial glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(centerGlow, outerGlow),
                        center = Offset(size.width * 0.5f, size.height * 0.32f),
                        radius = maxOf(size.width, size.height).coerceAtLeast(1f) * 0.78f
                    )
                )
                // Top liquid meniscus highlight
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = if (palette.isDark) 0.22f * spec else 0.48f * spec),
                            0.45f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = if (palette.isDark) 0.08f * spec else 0.18f * spec)
                        )
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(rimHighlight, rimBottom)),
                shape = shape
            )
    }
}

/**
 * Full-screen atmospheric backdrop for Settings and Documents screens.
 * In Liquid Glass mode, renders rich multi-colored luminous orbs & light ribbons behind the
 * scrolling content so the real-time backdrop blur & edge refraction are vividly visible.
 */
@Composable
fun LiquidGlassBackdrop(modifier: Modifier = Modifier) {
    val palette = ActiveThemeState.current
    val lg = palette.liquidGlass

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.bgDark, palette.bgDeepest)
            )
        )
        if (!lg.enabled) return@Canvas

        val intensity = (0.48f + lg.refractionTint * 0.52f).coerceIn(0.35f, 1f)
        val orbAlphaMain = if (palette.isDark) 0.52f * intensity else 0.44f * intensity
        val orbAlphaAlt = if (palette.isDark) 0.42f * intensity else 0.36f * intensity
        val maxDim = maxOf(size.width, size.height).coerceAtLeast(1f)

        // Orb 1: Vibrant primary aurora top-left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryBright.copy(alpha = orbAlphaMain),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, size.height * 0.12f),
                radius = maxDim * 0.48f
            ),
            radius = maxDim * 0.48f,
            center = Offset(size.width * 0.18f, size.height * 0.12f)
        )

        // Orb 2: Luminous rose/amber warm contrast orb top-right (makes refraction pop!)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.roseAccent.copy(alpha = orbAlphaAlt * 0.72f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.86f, size.height * 0.18f),
                radius = maxDim * 0.38f
            ),
            radius = maxDim * 0.38f,
            center = Offset(size.width * 0.86f, size.height * 0.18f)
        )

        // Orb 3: Crisp cyan caustic glow mid-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.cyanAccent.copy(alpha = orbAlphaAlt),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.84f, size.height * 0.52f),
                radius = maxDim * 0.46f
            ),
            radius = maxDim * 0.46f,
            center = Offset(size.width * 0.84f, size.height * 0.52f)
        )

        // Orb 4: Emerald/violet depth glow bottom-left
        val bottomOrbColor = if (palette.isDark) palette.purpleAccent else palette.emeraldAccent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bottomOrbColor.copy(alpha = orbAlphaAlt * 0.88f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.20f, size.height * 0.84f),
                radius = maxDim * 0.48f
            ),
            radius = maxDim * 0.48f,
            center = Offset(size.width * 0.20f, size.height * 0.84f)
        )
    }
}
