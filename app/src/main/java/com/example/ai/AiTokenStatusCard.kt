package com.example.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale

private const val AI_STUDIO_USAGE_URL = "https://aistudio.google.com/usage"

/**
 * Precision live token telemetry card displaying server-verified:
 * - Exact remaining context tokens (`inputTokenLimit - countTokens`)
 * - Exact current chat session tokens (`countTokens` free API)
 * - Exact cumulative tokens used today (`usageMetadata` across all real requests)
 * - 1-tap manual sync + direct link to Google AI Studio account quota page.
 */
@Composable
fun AiTokenStatusCard(
    tokenState: AiContextTokenState,
    onRefreshTokens: () -> Unit,
    activeProvider: AiProvider = AiProvider.GOOGLE_ACCOUNT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val remainingFraction = tokenState.remainingContextFraction
    val barColor = when {
        !tokenState.hasContextLimit -> CyanAccent
        remainingFraction > 0.4f -> EmeraldAccent
        remainingFraction > 0.15f -> AmberAccent
        else -> RoseAccent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateDeepest.copy(alpha = 0.72f))
            .border(1.dp, barColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("ai_token_status_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row: label + server verified chip + 1-tap refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DataUsage,
                contentDescription = null,
                tint = barColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Token ngữ cảnh còn lại",
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tokenState.isServerVerified) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmeraldAccent.copy(alpha = 0.15f))
                        .border(1.dp, EmeraldAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Đã đồng bộ từ máy chủ ${activeProvider.shortTitle}",
                        tint = EmeraldLight,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = tokenState.verificationSource.ifBlank { "Đã đồng bộ" },
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 34.dp, minHeight = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanAccent.copy(alpha = 0.14f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .clickable(enabled = !tokenState.isSyncingTokens) { onRefreshTokens() }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("ai_refresh_tokens_btn"),
                contentAlignment = Alignment.Center
            ) {
                if (tokenState.isSyncingTokens) {
                    CircularProgressIndicator(
                        color = CyanAccent,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(13.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Đồng bộ lại số token chính xác",
                        tint = CyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Exact remaining context readout + progress bar
        if (tokenState.hasContextLimit) {
            val remainingExact = AiResponseParser.formatExactTokens(tokenState.remainingContextTokens.toLong())
            val totalExact = AiResponseParser.formatExactTokens(tokenState.inputTokenLimit.toLong())
            val pctText = String.format(Locale.US, "%.1f%%", remainingFraction * 100f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = remainingExact,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = barColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("ai_remaining_tokens_value")
                    )
                    Text(
                        text = "/ $totalExact token",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = SlateTextSecondary,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "Còn $pctText",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            LinearProgressIndicator(
                progress = { remainingFraction },
                color = barColor,
                trackColor = SlateBorder.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Text(
                text = if (tokenState.isSyncingTokens) {
                    "Đang đồng bộ giới hạn & số token chính xác từ ${activeProvider.shortTitle}..."
                } else {
                    "Bấm nút làm mới hoặc Đồng bộ mô hình để lấy giới hạn token chính xác."
                },
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                fontSize = 11.sp
            )
        }

        // Optional live rate-limit or credit balance telemetry returned by provider headers / balance endpoints
        if (tokenState.rateLimitRemainingTokens != null || !tokenState.accountBalanceLabel.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tokenState.accountBalanceLabel?.takeIf { it.isNotBlank() }?.let { bal ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldAccent.copy(alpha = 0.14f))
                            .border(1.dp, EmeraldAccent.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${activeProvider.shortTitle}: $bal",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                tokenState.rateLimitRemainingTokens?.let { rlRem ->
                    val rlLimit = tokenState.rateLimitTotalTokens
                    val rlText = if (rlLimit != null && rlLimit > 0L) {
                        "TPM còn ${AiResponseParser.formatCompactTokens(rlRem)}/${AiResponseParser.formatCompactTokens(rlLimit)}"
                    } else {
                        "TPM còn ${AiResponseParser.formatCompactTokens(rlRem)}"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanAccent.copy(alpha = 0.14f))
                            .border(1.dp, CyanAccent.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = rlText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = CyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3-pill exact telemetry row: Current session | Used today | Last turn
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TokenMiniMetric(
                label = "Phiên chat",
                value = AiResponseParser.formatExactTokens(tokenState.currentContextTokens.toLong()),
                accent = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            TokenMiniMetric(
                label = "Hôm nay (${tokenState.requestsToday} lượt)",
                value = AiResponseParser.formatExactTokens(tokenState.usedTokensToday),
                accent = EmeraldLight,
                modifier = Modifier.weight(1f)
            )
            val last = tokenState.lastTurnUsage
            TokenMiniMetric(
                label = "Lượt vừa rồi",
                value = if (last != null) "${last.promptTokens}+${last.outputTokens}" else "—",
                accent = IndigoBright,
                modifier = Modifier.weight(1f)
            )
        }

        if (!tokenState.syncError.isNullOrBlank()) {
            Text(
                text = "Lưu ý đồng bộ: ${tokenState.syncError}",
                style = MaterialTheme.typography.labelSmall,
                color = AmberAccent,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        // Honest footer + 1-tap provider quota shortcut
        val footerText = when (activeProvider) {
            AiProvider.GOOGLE_ACCOUNT, AiProvider.GOOGLE_API_KEY ->
                "Đếm trực tiếp qua countTokens & usageMetadata. Hạn mức ngày (RPD/TPM) xem trên AI Studio."
            AiProvider.ANTHROPIC ->
                "Đếm ngữ cảnh trực tiếp qua Claude count_tokens & usage thực của từng lượt."
            AiProvider.OPENROUTER ->
                "Đọc context_length, số dư credit & usage thực trả về từ máy chủ OpenRouter."
            AiProvider.GROQ ->
                "Đọc context_window, header rate-limit & usage thực từ máy chủ Groq."
            AiProvider.DEEPSEEK ->
                "Đọc số dư tài khoản & usage thực từ máy chủ DeepSeek."
            AiProvider.OPENAI ->
                "Đọc giới hạn ngữ cảnh, rate-limit header & usage thực từ OpenAI."
            AiProvider.CUSTOM_OPENAI ->
                "Ghi nhận token thực từ trường usage do máy chủ Custom URL trả về."
        }
        val portalUrl = activeProvider.quotaPortalUrl.ifBlank { AI_STUDIO_USAGE_URL }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = footerText,
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary.copy(alpha = 0.85f),
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (activeProvider.quotaPortalUrl.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(SlateDark.copy(alpha = 0.85f))
                        .border(1.dp, SlateBorder, RoundedCornerShape(7.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("ai_open_quota_usage_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Quota",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Mở trang Quota ${activeProvider.quotaPortalLabel}",
                        tint = CyanAccent,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenMiniMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateDark.copy(alpha = 0.6f))
            .border(1.dp, SlateBorder.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SlateTextSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = accent,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
