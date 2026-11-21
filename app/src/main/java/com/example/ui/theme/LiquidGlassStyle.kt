package com.example.ui.theme

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Computes the optical frosted glass core opacity from [LiquidGlassConfig] and elevation tier.
 * Higher translucency -> lower core alpha so underlying 3D/2D geometry & aurora shine through.
 */
object LiquidGlassMath {
    fun coreAlpha(config: LiquidGlassConfig, isDark: Boolean, elevated: Boolean): Float {
        val safe = config.sanitized()
        val baseOpaque = if (isDark) {
            if (elevated) 0.88f else 0.82f
        } else {
            if (elevated) 0.90f else 0.84f
        }
        val minAlpha = if (isDark) {
            if (elevated) 0.24f else 0.18f
        } else {
            if (elevated) 0.30f else 0.22f
        }
        val raw = baseOpaque - (safe.translucency * (baseOpaque - minAlpha))
        val contrastBoost = if (safe.highContrastText) 0.16f else 0f
        return (raw + contrastBoost).coerceIn(0.18f, 0.94f)
    }

    fun borderWidthDp(config: LiquidGlassConfig): Dp {
        val safe = config.sanitized()
        return (0.9f + safe.borderIntensity * 0.65f).dp
    }
}

/**
 * Multi-layered optical Liquid Glass surface modifier.
 * When Liquid Glass is disabled, renders the crisp fallback surface & border cleanly.
 * When enabled, renders:
 *  1. Translucent frosted glass gradient core reacting live to [LiquidGlassConfig.translucency]
 *  2. Chromatic refraction tint reacting live to [LiquidGlassConfig.refractionTint]
 *  3. Top convex specular lens highlight & optional gliding shimmer reacting to [LiquidGlassConfig.specularGlow]
 *  4. Prismatic directional rim highlight border reacting to [LiquidGlassConfig.borderIntensity]
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(14.dp),
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
        val shimmerPhase = if (lg.dynamicShimmer && lg.specularGlow > 0.05f) {
            val transition = rememberInfiniteTransition(label = "liquid_glass_shimmer")
            val phase by transition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shimmer_phase"
            )
            phase
        } else {
            0.22f
        }

        val coreAlpha = LiquidGlassMath.coreAlpha(lg, palette.isDark, elevated)
        val tintStrength = lg.refractionTint
        val specStrength = lg.specularGlow
        val rimStrength = lg.borderIntensity

        val glassBase = if (palette.isDark) {
            lerp(palette.bgDark, palette.surface, 0.35f)
        } else {
            lerp(Color.White, palette.bgDark, 0.25f)
        }
        val tintPrimary = lerp(palette.primaryBright, palette.cyanAccent, 0.45f)

        val topCoreColor = lerp(glassBase, tintPrimary, tintStrength * (if (palette.isDark) 0.24f else 0.14f))
            .copy(alpha = (coreAlpha + 0.06f).coerceAtMost(0.95f))
        val bottomCoreColor = lerp(glassBase, palette.bgDeepest, if (palette.isDark) 0.22f else 0.08f)
            .copy(alpha = (coreAlpha - 0.05f).coerceAtLeast(0.14f))

        val rimTopLeft = if (accentBorder != null) {
            lerp(Color.White, accentBorder, 0.45f).copy(alpha = (0.38f + rimStrength * 0.56f).coerceAtMost(0.98f))
        } else if (palette.isDark) {
            lerp(Color.White, palette.cyanAccent, 0.25f).copy(alpha = (0.20f + rimStrength * 0.50f).coerceAtMost(0.85f))
        } else {
            Color.White.copy(alpha = (0.55f + rimStrength * 0.42f).coerceAtMost(0.98f))
        }
        val rimMid = (accentBorder ?: tintPrimary).copy(alpha = 0.14f + tintStrength * 0.26f)
        val rimBottomRight = if (palette.isDark) {
            Color.White.copy(alpha = 0.08f + rimStrength * 0.16f)
        } else {
            palette.primary.copy(alpha = 0.16f + rimStrength * 0.22f)
        }

        val borderWidth = LiquidGlassMath.borderWidthDp(lg)

        this
            .clip(shape)
            .drawBehind {
                // Layer 1: Translucent frosted glass body gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topCoreColor, bottomCoreColor)
                    )
                )

                // Layer 2: Soft chromatic refraction wash from bottom-right
                if (tintStrength > 0.04f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tintPrimary.copy(alpha = tintStrength * (if (palette.isDark) 0.22f else 0.14f)),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.92f),
                            radius = maxOf(size.width, size.height).coerceAtLeast(1f) * 0.95f
                        )
                    )
                }

                // Layer 3: Convex upper-bevel specular reflection
                if (specStrength > 0.03f) {
                    val topGlossAlpha = specStrength * (if (palette.isDark) 0.22f else 0.48f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = topGlossAlpha),
                                0.34f to Color.White.copy(alpha = topGlossAlpha * 0.25f),
                                0.65f to Color.Transparent,
                                1.0f to Color.Transparent
                            )
                        )
                    )

                    // Gliding / static diagonal liquid sheen ribbon
                    val sx = size.width * shimmerPhase
                    val ribbonSpan = (size.width * 0.45f).coerceAtLeast(80f)
                    val sheenAlpha = specStrength * (if (palette.isDark) 0.16f else 0.30f)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = sheenAlpha),
                                palette.cyanAccent.copy(alpha = sheenAlpha * 0.45f),
                                Color.Transparent
                            ),
                            start = Offset(sx - ribbonSpan, 0f),
                            end = Offset(sx + ribbonSpan, size.height)
                        )
                    )
                }
            }
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(rimTopLeft, rimMid, rimBottomRight)
                ),
                shape = shape
            )
    }
}

/**
 * Full-screen atmospheric backdrop for Settings and Documents screens.
 * In Liquid Glass mode, renders glowing aurora light orbs behind every translucent glass card
 * so optical translucency & refraction tint are unmistakably visible.
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

        val intensity = (0.35f + lg.refractionTint * 0.65f).coerceIn(0.25f, 1f)
        val orbAlphaMain = if (palette.isDark) 0.42f * intensity else 0.36f * intensity
        val orbAlphaAlt = if (palette.isDark) 0.34f * intensity else 0.28f * intensity
        val maxDim = maxOf(size.width, size.height).coerceAtLeast(1f)

        // Orb 1: Primary luminous aurora top-left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryBright.copy(alpha = orbAlphaMain),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.16f, size.height * 0.14f),
                radius = maxDim * 0.56f
            ),
            radius = maxDim * 0.56f,
            center = Offset(size.width * 0.16f, size.height * 0.14f)
        )

        // Orb 2: Cyan caustic glow mid-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.cyanAccent.copy(alpha = orbAlphaAlt),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.88f, size.height * 0.42f),
                radius = maxDim * 0.50f
            ),
            radius = maxDim * 0.50f,
            center = Offset(size.width * 0.88f, size.height * 0.42f)
        )

        // Orb 3: Soft emerald/violet depth glow bottom-left
        val bottomOrbColor = if (palette.isDark) palette.purpleAccent else palette.emeraldAccent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bottomOrbColor.copy(alpha = orbAlphaAlt * 0.85f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.22f, size.height * 0.86f),
                radius = maxDim * 0.52f
            ),
            radius = maxDim * 0.52f,
            center = Offset(size.width * 0.22f, size.height * 0.86f)
        )
    }
}
