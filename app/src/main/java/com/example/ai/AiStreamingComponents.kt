package com.example.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAccent
import kotlinx.coroutines.delay

/**
 * Smooth 60fps adaptive typewriter streaming text composable with an inline blinking cursor (`▍`).
 *
 * Network SSE chunks arrive in uneven bursts (sometimes 4 chars, sometimes 80 chars).
 * This composable maintains a lightweight character reveal index that:
 * - Reveals characters smoothly at ~60fps (~18ms ticks) with adaptive catch-up speed when
 *   network chunks burst ahead, so typing always feels fluid and never lags behind the AI.
 * - Renders a smoothly pulsing inline blinking cursor (`▍`) directly after the last visible
 *   character using `AnnotatedString` without layout jumps.
 */
@Composable
fun SmoothStreamingText(
    targetText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    cursorColor: Color = CyanAccent,
    lineHeight: TextUnit = TextUnit.Unspecified,
    showCursor: Boolean = isStreaming,
    onDisplayedLengthChanged: (Int) -> Unit = {}
) {
    var displayedLength by remember { mutableIntStateOf(0) }

    // Reset if a brand-new shorter text replaces an old stream
    if (targetText.length < displayedLength) {
        displayedLength = targetText.length
    }

    LaunchedEffect(targetText, isStreaming) {
        while (displayedLength < targetText.length) {
            val remaining = targetText.length - displayedLength
            val step = when {
                !isStreaming -> (remaining / 4).coerceAtLeast(8)
                remaining <= 10 -> 1
                remaining <= 28 -> 2
                remaining <= 70 -> 4
                remaining <= 160 -> (remaining / 14).coerceAtLeast(6)
                else -> (remaining / 8).coerceAtLeast(14)
            }
            displayedLength = (displayedLength + step).coerceAtMost(targetText.length)
            onDisplayedLengthChanged(displayedLength)
            if (displayedLength < targetText.length) {
                delay(18L)
            }
        }
    }

    val cursorAlpha = if (showCursor) {
        val infiniteTransition = rememberInfiniteTransition(label = "streaming_cursor_blink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursor_alpha"
        )
        alpha
    } else {
        0f
    }

    val visibleText = targetText.substring(0, displayedLength.coerceIn(0, targetText.length))
    val annotated = remember(visibleText, showCursor, cursorAlpha, cursorColor) {
        buildAnnotatedString {
            append(visibleText)
            if (showCursor) {
                if (visibleText.isNotEmpty() && !visibleText.endsWith(" ") && !visibleText.endsWith("\n")) {
                    append("\u2009") // thin space before cursor for crisp visual separation
                }
                withStyle(
                    SpanStyle(
                        color = cursorColor.copy(alpha = cursorAlpha),
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append("▍")
                }
            }
        }
    }

    Text(
        text = annotated,
        style = if (lineHeight != TextUnit.Unspecified) style.copy(lineHeight = lineHeight) else style,
        color = color,
        modifier = modifier
    )
}

/**
 * Compact pulsing live dot + status text indicator used on active AI streaming cards.
 */
@Composable
fun AiStreamingPulseDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming_dot_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun AiStreamingWaitingPlaceholder(
    label: String,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiStreamingPulseDot(color = accentColor)
        SmoothStreamingText(
            targetText = label,
            isStreaming = true,
            showCursor = true,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            cursorColor = accentColor
        )
    }
}
