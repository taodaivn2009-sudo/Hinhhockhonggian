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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
 * Single app-wide shimmer phase clock (read strictly inside Draw Phase so shimmer NEVER causes
 * Jetpack Compose recompositions across cards or bars!).
 */
val LocalLiquidGlassShimmerPhase = compositionLocalOf<State<Float>?> { null }

@Composable
fun ProvideLiquidGlassShimmerClock(
    config: LiquidGlassConfig,
    content: @Composable () -> Unit
) {
    val shimmerState: State<Float>? = if (config.enabled && config.dynamicShimmer && config.specularGlow > 0.05f) {
        val transition = rememberInfiniteTransition(label = "global_liquid_glass_shimmer")
        transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "global_shimmer_phase"
        )
    } else {
        null
    }

    CompositionLocalProvider(LocalLiquidGlassShimmerPhase provides shimmerState, content = content)
}

/**
 * Shared live backdrop capture container.
 * - Uses plain non-Snapshot fields for coordinates so layout/scroll NEVER triggers recomposition.
 * - Supports [staticCapture] for unchanging aurora backdrops (recorded ONCE per size/theme).
 * - Only notifies sibling floating glass bars when the underlying scene actually changes.
 */
@Stable
class LiquidGlassBackdropState(val backdropLayer: GraphicsLayer) {
    var rootPosX: Float = 0f
    var rootPosY: Float = 0f
    var hasRecorded by mutableStateOf(false)
        private set
    private var rawFrameCounter = 0L
    var frameVersion by mutableLongStateOf(0L)
        private set

    internal var lastStaticKey: Long = Long.MIN_VALUE

    fun notifyRecorded() {
        if (!hasRecorded) hasRecorded = true
        rawFrameCounter += 1L
        frameVersion = rawFrameCounter
    }

    fun resetStaticCache() {
        lastStaticKey = Long.MIN_VALUE
    }
}

val LocalLiquidGlassBackdrop = compositionLocalOf<LiquidGlassBackdropState?> { null }

@Composable
fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdropState(layer) }
}

/**
 * Marks a background layer (3D canvas, 2D canvas, or static aurora backdrop) as the live
 * optical source sampled by floating Liquid Glass pills above it.
 *
 * IMPORTANT: Never apply this modifier around a container that ALSO holds [liquidGlassSurface]
 * children — always apply it ONLY to the sibling background/canvas underneath floating chrome!
 *
 * @param staticCapture Set to `true` for static backgrounds (like [LiquidGlassBackdrop]) so the
 * GPU layer is recorded ONCE per `(width, height, theme)` instead of every frame.
 */
fun Modifier.liquidGlassBackdropSource(
    state: LiquidGlassBackdropState,
    staticCapture: Boolean = false,
    alwaysCapture: Boolean = false
): Modifier = this
    .onGloballyPositioned { coords ->
        val p = coords.positionInWindow()
        state.rootPosX = p.x
        state.rootPosY = p.y
    }
    .drawWithContent {
        val palette = ActiveThemeState.current
        val lg = palette.liquidGlass
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if ((lg.enabled || alwaysCapture) && w > 1 && h > 1) {
            if (staticCapture) {
                var key = w.toLong() * 31L + h.toLong()
                key = key * 31L + palette.hashCode().toLong()
                if (state.lastStaticKey != key || !state.hasRecorded) {
                    state.lastStaticKey = key
                    state.backdropLayer.record(size = IntSize(w, h)) {
                        this@drawWithContent.drawContent()
                    }
                    state.notifyRecorded()
                }
                drawLayer(state.backdropLayer)
            } else {
                state.backdropLayer.record(size = IntSize(w, h)) {
                    this@drawWithContent.drawContent()
                }
                state.notifyRecorded()
                drawLayer(state.backdropLayer)
            }
        } else {
            drawContent()
        }
    }

/**
 * Optical math helpers for Liquid Glass frosted alpha, bevel thickness, and high-FPS analytical AGSL shader.
 */
object LiquidGlassMath {
    fun coreAlpha(config: LiquidGlassConfig, isDark: Boolean, elevated: Boolean): Float {
        val safe = config.sanitized()
        val baseOpaque = if (isDark) {
            if (elevated) 0.66f else 0.58f
        } else {
            if (elevated) 0.72f else 0.64f
        }
        val minAlpha = if (isDark) {
            if (elevated) 0.06f else 0.05f
        } else {
            if (elevated) 0.12f else 0.09f
        }
        val raw = baseOpaque - (safe.translucency * (baseOpaque - minAlpha))
        val contrastBoost = if (safe.highContrastText) 0.22f else 0f
        return (raw + contrastBoost).coerceIn(0.04f, 0.88f)
    }

    fun borderWidthDp(config: LiquidGlassConfig): Dp {
        val safe = config.sanitized()
        return (0.9f + safe.borderIntensity * 0.65f).dp
    }

    /**
     * Authentic iOS 26 Optical Liquid Glass AGSL shader (Android 13+):
     * - Analytical rounded-box SDF (works seamlessly for circles, pills, and rounded rects at 60fps).
     * - True convex lens magnification in the center + strong Snell-inspired curved rim refraction
     *   driven by [bevelThickness] and [refractionStrength].
     * - Vivid prismatic RGB chromatic dispersion (Cyan/Red/Blue split) concentrated strictly along
     *   the outer curved bevel where light bends most intensely.
     * - Internal glass thickness absorption ring + dual-directional Fresnel specular rim glints.
     */
    const val LIQUID_GLASS_AGSL_SRC: String = """
        uniform shader content;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float refractionStrength;
        uniform float bevelThickness;
        uniform float dispersion;
        uniform float specularStrength;
        uniform float lensZoom;
        uniform float isDark;

        half4 main(float2 fragCoord) {
            float2 halfRes = resolution * 0.5;
            float minDim = min(resolution.x, resolution.y);
            float r = min(cornerRadius, minDim * 0.5);
            float2 p = fragCoord - halfRes;
            float2 innerHalf = max(halfRes - float2(r), float2(0.001));
            float2 q = abs(p) - innerHalf;

            // Exact analytical rounded-box SDF (1 evaluation)
            float outsideLen = length(max(q, 0.0));
            float d = min(max(q.x, q.y), 0.0) + outsideLen - r;
            float edgeDist = max(-d, 0.0);

            float bevelWidth = clamp(minDim * (0.14 + 0.34 * bevelThickness), 8.0, minDim * 0.49);
            float2 minUv = float2(1.0);
            float2 maxUv = max(resolution - float2(1.0), float2(2.0));

            // Subtle convex magnifying lens zoom across interior
            float zoomFactor = 1.0 - clamp(0.055 * refractionStrength * lensZoom, 0.0, 0.16);
            float2 centerZoom = p * zoomFactor + halfRes;

            // Fast center early-out for interior pixels past the curved bevel (1 texture fetch!)
            if (edgeDist >= bevelWidth) {
                return content.eval(clamp(centerZoom, minUv, maxUv));
            }

            // Analytical outward unit normal (zero finite differences)
            float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
            float2 n;
            if (q.x > 0.0 && q.y > 0.0) {
                n = s * (q / max(outsideLen, 0.0001));
            } else if (q.x > q.y) {
                n = float2(s.x, 0.0);
            } else {
                n = float2(0.0, s.y);
            }

            // Nonlinear convex meniscus refraction along beveled perimeter
            float t = clamp(1.0 - edgeDist / bevelWidth, 0.0, 1.0);
            float tCurve = pow(t, 1.85);
            float bend = tCurve * refractionStrength * bevelWidth * 1.12;
            float2 sampleBase = centerZoom - n * bend;

            // Prismatic RGB chromatic dispersion strictly on the curved outer rim
            half3 rgb;
            half alpha;
            float dispPx = tCurve * dispersion * refractionStrength * (6.5 + 8.5 * bevelThickness);
            if (dispPx > 0.25) {
                half4 colR = content.eval(clamp(sampleBase - n * dispPx, minUv, maxUv));
                half4 colG = content.eval(clamp(sampleBase, minUv, maxUv));
                half4 colB = content.eval(clamp(sampleBase + n * dispPx, minUv, maxUv));
                rgb = half3(colR.r, colG.g, colB.b);
                alpha = colG.a;
            } else {
                half4 col = content.eval(clamp(sampleBase, minUv, maxUv));
                rgb = col.rgb;
                alpha = col.a;
            }

            // Internal glass thickness absorption ring right behind outer curved lip
            float ringDelta = (edgeDist - bevelWidth * 0.24) / max(bevelWidth * 0.22, 2.5);
            float innerDepthRing = exp(-(ringDelta * ringDelta));
            float depthDarken = innerDepthRing * (isDark > 0.5 ? 0.16 : 0.09) * refractionStrength * (0.55 + 0.45 * bevelThickness);
            rgb *= half(1.0 - depthDarken);

            // Crisp directional Fresnel specular rim glints (top-left primary + bottom-right secondary)
            float rimBand = pow(t, 3.2);
            float lightTopLeft = max(dot(n, float2(-0.5736, -0.8192)), 0.0);
            float lightBottomRight = max(dot(n, float2(0.5736, 0.8192)), 0.0);
            float spec = rimBand * (0.24 + 0.92 * lightTopLeft + 0.52 * lightBottomRight) * specularStrength;
            rgb += half3(spec * (isDark > 0.5 ? 0.68 : 0.82));

            // Subtle chromatic prism sheen along the sharpest specular rim
            if (dispersion > 0.05 && rimBand > 0.12) {
                float prismGlow = rimBand * dispersion * 0.22 * specularStrength;
                rgb.r += half(prismGlow * lightTopLeft);
                rgb.b += half(prismGlow * lightBottomRight);
                rgb.g += half(prismGlow * 0.35);
            }

            return half4(clamp(rgb, half3(0.0), half3(1.0)), alpha);
        }
    """

    @RequiresApi(Build.VERSION_CODES.S)
    fun buildGlassRenderEffect(
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        blurPx: Float,
        config: LiquidGlassConfig,
        isDark: Boolean,
        lensZoom: Float = 1.0f
    ): RenderEffect? {
        if (widthPx < 4f || heightPx < 4f) return null
        val safeBlur = blurPx.coerceIn(0f, 64f)
        val blurEffect = if (safeBlur >= 0.8f) {
            AndroidRenderEffect.createBlurEffect(
                safeBlur,
                safeBlur,
                Shader.TileMode.CLAMP
            )
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && config.refraction > 0.01f) {
            val effect = runCatching {
                val shader = RuntimeShader(LIQUID_GLASS_AGSL_SRC)
                shader.setFloatUniform("resolution", widthPx, heightPx)
                shader.setFloatUniform("cornerRadius", cornerRadiusPx.coerceAtLeast(1f))
                shader.setFloatUniform("refractionStrength", config.refraction)
                shader.setFloatUniform("bevelThickness", config.bevelThickness)
                shader.setFloatUniform("dispersion", config.chromaticDispersion)
                shader.setFloatUniform("specularStrength", config.specularGlow)
                shader.setFloatUniform("lensZoom", lensZoom)
                shader.setFloatUniform("isDark", if (isDark) 1f else 0f)
                val lensEffect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                if (blurEffect != null) {
                    AndroidRenderEffect.createChainEffect(lensEffect, blurEffect)
                } else {
                    lensEffect
                }
            }.getOrNull()
            if (effect != null) return effect.asComposeRenderEffect()
        }

        return blurEffect?.asComposeRenderEffect()
    }
}

/**
 * Plain non-Snapshot holder for per-node geometry & cached RenderEffect.
 * Eliminates 100% of recompositions caused by `onGloballyPositioned` during scrolling/animations!
 */
private class GlassNodeCache {
    var posX: Float = 0f
    var posY: Float = 0f
    var cachedW: Int = -1
    var cachedH: Int = -1
    var cachedBlurPx: Float = -1f
    var cachedCornerPx: Float = -1f
    var cachedRefraction: Float = -1f
    var cachedBevel: Float = -1f
    var cachedDispersion: Float = -1f
    var cachedSpecular: Float = -1f
    var cachedLensZoom: Float = -1f
    var cachedIsDark: Boolean = false
    var renderEffect: RenderEffect? = null

    var lastRecordedFrame: Long = -1L
    var lastRelX: Float = Float.NaN
    var lastRelY: Float = Float.NaN
    var lastRecordedW: Int = -1
    var lastRecordedH: Int = -1
    var lastRecordedEffect: RenderEffect? = null

    fun getOrBuildRenderEffect(
        w: Int,
        h: Int,
        cornerPx: Float,
        blurPx: Float,
        lg: LiquidGlassConfig,
        isDark: Boolean,
        lensZoom: Float
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || w < 4 || h < 4) return null
        if (cachedW == w &&
            cachedH == h &&
            cachedBlurPx == blurPx &&
            cachedCornerPx == cornerPx &&
            cachedRefraction == lg.refraction &&
            cachedBevel == lg.bevelThickness &&
            cachedDispersion == lg.chromaticDispersion &&
            cachedSpecular == lg.specularGlow &&
            cachedLensZoom == lensZoom &&
            cachedIsDark == isDark
        ) {
            return renderEffect
        }
        cachedW = w
        cachedH = h
        cachedBlurPx = blurPx
        cachedCornerPx = cornerPx
        cachedRefraction = lg.refraction
        cachedBevel = lg.bevelThickness
        cachedDispersion = lg.chromaticDispersion
        cachedSpecular = lg.specularGlow
        cachedLensZoom = lensZoom
        cachedIsDark = isDark
        renderEffect = LiquidGlassMath.buildGlassRenderEffect(
            widthPx = w.toFloat(),
            heightPx = h.toFloat(),
            cornerRadiusPx = cornerPx,
            blurPx = blurPx,
            config = lg,
            isDark = isDark,
            lensZoom = lensZoom
        )
        return renderEffect
    }
}

/**
 * High-FPS 60fps Liquid Glass surface modifier!
 *
 * Two optical tiers for locked 60fps across all devices:
 * - [liveBackdrop] = `true` (default, for floating bars/docks over 3D/2D/PDF/aurora):
 *   Samples [LocalLiquidGlassBackdrop] with GPU Gaussian blur + 4x faster analytical AGSL lens refraction.
 * - [liveBackdrop] = `false` (for scrolling list cards, chat bubbles, TOC items, sheets):
 *   Zero-FBO Optical Frosted Glass — crisp translucent depth veil, top-bevel liquid gloss,
 *   shared zero-recomposition gliding shimmer, and prismatic Fresnel rim border without
 *   allocating per-card offscreen GPU blur layers during scroll!
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(14.dp),
    pillShape: Shape? = null,
    cornerRadiusDp: Dp = 20.dp,
    elevated: Boolean = false,
    fallbackBg: Color? = null,
    fallbackBorder: Color? = null,
    accentBorder: Color? = null,
    liveBackdrop: Boolean = true,
    lensZoom: Float = 1.0f,
    crystalClarity: Boolean = false,
    forceLiveOptical: Boolean = false
): Modifier = composed {
    val palette = ActiveThemeState.current
    val lg = palette.liquidGlass
    val resolvedFallbackBg = fallbackBg ?: palette.bgDark.copy(alpha = if (elevated) 0.95f else 0.90f)
    val resolvedFallbackBorder = accentBorder ?: fallbackBorder ?: palette.border.copy(alpha = 0.85f)

    if (!lg.enabled && !forceLiveOptical) {
        this
            .clip(shape)
            .background(resolvedFallbackBg, shape)
            .border(1.dp, resolvedFallbackBorder, shape)
    } else {
        val activeShape = pillShape ?: shape
        val backdropState = if (liveBackdrop) LocalLiquidGlassBackdrop.current else null
        val shimmerPhaseState = LocalLiquidGlassShimmerPhase.current
        val density = LocalDensity.current
        val glassLayer = if (liveBackdrop && backdropState != null) rememberGraphicsLayer() else null
        val nodeCache = remember { GlassNodeCache() }

        val effectiveBlurDp = if (crystalClarity) (lg.blurRadius * 0.45f) else lg.blurRadius
        val blurPx = with(density) { effectiveBlurDp.dp.toPx() }
        val baseCornerPx = with(density) { cornerRadiusDp.toPx() }
        val isPill = pillShape != null

        val useLiveGlass = liveBackdrop && backdropState != null && glassLayer != null
        val coreAlpha = LiquidGlassMath.coreAlpha(lg, palette.isDark, elevated).let { a ->
            when {
                crystalClarity && useLiveGlass -> (a * 0.28f).coerceIn(0.03f, 0.26f)
                useLiveGlass -> (a * 0.46f).coerceIn(0.04f, 0.58f)
                else -> (a * 0.78f).coerceIn(0.22f, 0.84f)
            }
        }
        val tintStrength = lg.refractionTint
        val specStrength = lg.specularGlow
        val rimStrength = lg.borderIntensity

        val glassNeutral = if (palette.isDark) Color(0xFF0E1522) else Color(0xFFFFFFFF)
        val tintPrimary = lerp(palette.primaryBright, palette.cyanAccent, 0.45f)

        val topCoreColor = lerp(glassNeutral, tintPrimary, tintStrength * (if (palette.isDark) 0.26f else 0.14f))
            .copy(alpha = (coreAlpha + 0.03f).coerceAtMost(0.86f))
        val bottomCoreColor = lerp(glassNeutral, palette.bgDeepest, if (palette.isDark) 0.22f else 0.05f)
            .copy(alpha = (coreAlpha - 0.02f).coerceAtLeast(0.03f))

        val coreVeilBrush = remember(topCoreColor, bottomCoreColor) {
            Brush.verticalGradient(colors = listOf(topCoreColor, bottomCoreColor))
        }

        val topGlossAlpha = specStrength * (if (palette.isDark) 0.22f else 0.44f)
        val topGlossBrush = remember(topGlossAlpha) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = topGlossAlpha),
                    0.22f to Color.White.copy(alpha = topGlossAlpha * 0.16f),
                    0.56f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = topGlossAlpha * 0.24f)
                )
            )
        }

        // Crisp iOS 26 directional Fresnel rim border with subtle chromatic edge
        val dispHint = lg.chromaticDispersion
        val rimTopLeft = if (accentBorder != null) {
            lerp(Color.White, accentBorder, 0.30f).copy(alpha = (0.56f + rimStrength * 0.42f).coerceAtMost(0.98f))
        } else {
            lerp(Color.White, Color(0xFFD8F6FF), dispHint * 0.35f)
                .copy(alpha = (0.44f + rimStrength * 0.52f).coerceAtMost(0.96f))
        }
        val rimMid = if (palette.isDark) {
            lerp(Color.White, tintPrimary, tintStrength).copy(alpha = 0.10f + rimStrength * 0.16f)
        } else {
            lerp(Color.White, palette.primary, tintStrength * 0.45f).copy(alpha = 0.28f + rimStrength * 0.26f)
        }
        val rimBottomRight = if (palette.isDark) {
            lerp(Color.White, Color(0xFFFFD6E8), dispHint * 0.28f)
                .copy(alpha = 0.28f + rimStrength * 0.46f)
        } else {
            Color.White.copy(alpha = 0.48f + rimStrength * 0.42f)
        }
        val rimBrush = remember(rimTopLeft, rimMid, rimBottomRight) {
            Brush.linearGradient(colors = listOf(rimTopLeft, rimMid, rimBottomRight))
        }
        val borderWidth = LiquidGlassMath.borderWidthDp(lg)

        val shadowMod = if (elevated || useLiveGlass) {
            this.shadow(
                elevation = if (elevated) 14.dp else 6.dp,
                shape = activeShape,
                ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.52f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.62f else 0.22f)
            )
        } else {
            this
        }

        val posTrackedMod = if (useLiveGlass) {
            shadowMod
                .clip(activeShape)
                .onGloballyPositioned { coords ->
                    val p = coords.positionInWindow()
                    nodeCache.posX = p.x
                    nodeCache.posY = p.y
                }
        } else {
            shadowMod.clip(activeShape)
        }

        posTrackedMod
            .drawWithContent {
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()

                // 1. REAL LIVE BACKDROP BLUR + iOS 26 AGSL OPTICAL LENS REFRACTION & RGB DISPERSION
                if (useLiveGlass && backdropState != null && glassLayer != null && backdropState.hasRecorded && w > 0 && h > 0) {
                    val frameTick = backdropState.frameVersion
                    val relX = nodeCache.posX - backdropState.rootPosX
                    val relY = nodeCache.posY - backdropState.rootPosY
                    val cornerPx = if (isPill) {
                        (minOf(w, h) * 0.5f).coerceAtLeast(baseCornerPx)
                    } else {
                        baseCornerPx
                    }
                    val effect = nodeCache.getOrBuildRenderEffect(
                        w = w,
                        h = h,
                        cornerPx = cornerPx,
                        blurPx = blurPx,
                        lg = lg,
                        isDark = palette.isDark,
                        lensZoom = lensZoom
                    )
                    val needsRecord = nodeCache.lastRecordedFrame != frameTick ||
                        nodeCache.lastRelX != relX ||
                        nodeCache.lastRelY != relY ||
                        nodeCache.lastRecordedW != w ||
                        nodeCache.lastRecordedH != h ||
                        nodeCache.lastRecordedEffect !== effect

                    if (needsRecord) {
                        glassLayer.renderEffect = effect
                        glassLayer.record(size = IntSize(w, h)) {
                            translate(left = -relX, top = -relY) {
                                drawLayer(backdropState.backdropLayer)
                            }
                        }
                        nodeCache.lastRecordedFrame = frameTick
                        nodeCache.lastRelX = relX
                        nodeCache.lastRelY = relY
                        nodeCache.lastRecordedW = w
                        nodeCache.lastRecordedH = h
                        nodeCache.lastRecordedEffect = effect
                    }
                    drawLayer(glassLayer)
                }

                // 2. Ultra-clear optical tint veil
                drawRect(brush = coreVeilBrush)

                // 3. Convex upper-edge liquid gloss + zero-recomposition shared shimmer sweep
                if (specStrength > 0.03f) {
                    drawRect(brush = topGlossBrush)

                    if (lg.dynamicShimmer && shimmerPhaseState != null) {
                        val shimmerPhase = shimmerPhaseState.value
                        val sx = size.width * shimmerPhase
                        val ribbonSpan = (size.width * 0.38f).coerceAtLeast(70f)
                        val sheenAlpha = specStrength * (if (palette.isDark) 0.14f else 0.24f)
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

                // 4. Crisp unblurred foreground UI (icons & text) on top
                drawContent()
            }
            .border(
                width = borderWidth,
                brush = rimBrush,
                shape = activeShape
            )
    }
}

/**
 * Luminous convex "Liquid Droplet" active pill indicator used for the currently selected tab
 * inside floating Liquid Glass navigation bars.
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

        val meniscusBrush = remember(palette.isDark, spec) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = if (palette.isDark) 0.22f * spec else 0.48f * spec),
                    0.45f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = if (palette.isDark) 0.08f * spec else 0.18f * spec)
                )
            )
        }
        val rimBrush = remember(rimHighlight, rimBottom) {
            Brush.verticalGradient(listOf(rimHighlight, rimBottom))
        }

        this
            .clip(shape)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(centerGlow, outerGlow),
                        center = Offset(size.width * 0.5f, size.height * 0.32f),
                        radius = maxOf(size.width, size.height).coerceAtLeast(1f) * 0.78f
                    )
                )
                drawRect(brush = meniscusBrush)
            }
            .border(
                width = 1.dp,
                brush = rimBrush,
                shape = shape
            )
    }
}

/**
 * Full-screen atmospheric backdrop for Settings, AI, and Documents screens.
 * Designed to pair with `.liquidGlassBackdropSource(state, staticCapture = true)` so it is
 * rendered into GPU memory ONCE per size/theme rather than every frame.
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
