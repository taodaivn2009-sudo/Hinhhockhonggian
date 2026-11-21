package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

/**
 * Opens an external GitHub Release or APK asset download link in the system browser/downloader
 * via standard Intent.ACTION_VIEW (zero restricted install permissions required).
 */
fun openExternalUpdateUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Không thể mở trình duyệt cho liên kết cập nhật.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Compact version & GitHub update indicator pill for workspace headers.
 * Shows current version (e.g. "v1") or highlights a new GitHub version ("Mới: v2").
 */
@Composable
fun UpdateVersionPill(
    uiState: AppUpdateUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUpdate = uiState.hasUpdate
    val isChecking = uiState.status is UpdateCheckStatus.Checking
    val badgeTag = uiState.availableRelease?.tagName
    val borderColor = if (hasUpdate) EmeraldAccent.copy(alpha = 0.85f) else SlateBorder.copy(alpha = 0.8f)
    val bgColor = if (hasUpdate) EmeraldDark.copy(alpha = 0.35f) else SlateDeepest.copy(alpha = 0.88f)

    Row(
        modifier = modifier
            .testTag("check_update_pill")
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.8.dp,
                color = CyanAccent
            )
        } else {
            Box {
                Icon(
                    imageVector = if (hasUpdate) Icons.Default.SystemUpdate else Icons.Default.CloudSync,
                    contentDescription = if (hasUpdate) "Có bản cập nhật mới trên GitHub" else "Kiểm tra cập nhật GitHub",
                    tint = if (hasUpdate) EmeraldLight else SlateTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(AmberAccent)
                    )
                }
            }
        }
        Text(
            text = if (hasUpdate && badgeTag != null) "Mới $badgeTag" else uiState.currentVersionName,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = if (hasUpdate) EmeraldLight else SlateTextSecondary,
            fontWeight = if (hasUpdate) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Animated top notification banner displayed automatically when a newer GitHub Release is detected.
 */
@Composable
fun UpdateAvailableBanner(
    uiState: AppUpdateUiState,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val release = uiState.availableRelease
    AnimatedVisibility(
        visible = uiState.showAutoBanner && release != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (release != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .testTag("update_available_banner")
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF064E3B), Color(0xFF0F172A))
                        )
                    )
                    .border(1.2.dp, EmeraldAccent.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenDetails)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = EmeraldLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Có bản mới ${release.releaseName}!",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${uiState.currentVersionName} → ${release.tagName} trên GitHub • Chạm để cài đặt",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onOpenDetails,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = EmeraldAccent,
                        contentColor = SlateDeepest
                    ),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("banner_install_update_btn")
                ) {
                    Text("Cài đặt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("banner_dismiss_update_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng thông báo cập nhật",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full Material 3 GitHub Update & Install Suggestion Modal.
 * Shows changelog, APK download action, GitHub release link, skip button, and repo configuration.
 */
@Composable
fun UpdateDetailsDialog(
    uiState: AppUpdateUiState,
    onCheckNow: () -> Unit,
    onSaveRepoAndCheck: (String) -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!uiState.showDialog) return

    val context = LocalContext.current
    var showRepoConfig by remember(uiState.configuredRepo) {
        mutableStateOf(uiState.configuredRepo.isBlank())
    }
    var repoInput by remember(uiState.configuredRepo) {
        mutableStateOf(uiState.configuredRepo)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .testTag("update_details_dialog")
                    .clip(RoundedCornerShape(20.dp))
                    .background(SlateDark)
                    .border(1.dp, SlateBorder, RoundedCornerShape(20.dp))
                    .clickable(enabled = false) {}
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.hasUpdate) EmeraldAccent.copy(alpha = 0.2f)
                                    else IndigoPrimary.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.hasUpdate) Icons.Default.SystemUpdate else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (uiState.hasUpdate) EmeraldLight else CyanAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (uiState.hasUpdate) "Có bản cập nhật mới" else "Cập nhật từ GitHub",
                                style = MaterialTheme.typography.titleMedium,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Phiên bản hiện tại: ${uiState.currentVersionName} (mã ${uiState.currentVersionCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_update_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng hộp thoại cập nhật",
                            tint = SlateTextSecondary
                        )
                    }
                }

                // Status Content
                when (val status = uiState.status) {
                    is UpdateCheckStatus.Checking -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SlateDeepest)
                                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.4.dp,
                                color = CyanAccent
                            )
                            Column {
                                Text(
                                    text = "Đang kiểm tra bản phát hành mới nhất...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SlateTextPrimary
                                )
                                Text(
                                    text = "github.com/${uiState.configuredRepo}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                    color = CyanAccent
                                )
                            }
                        }
                    }

                    is UpdateCheckStatus.UpdateAvailable -> {
                        val rel = status.release
                        val sizeLabel = GitHubUpdateChecker.formatFileSize(rel.apkSizeBytes)
                        val dateLabel = GitHubUpdateChecker.formatPublishedDate(rel.publishedAt)
                        val cleanNotes = remember(rel.body) { GitHubUpdateChecker.formatReleaseNotes(rel.body) }

                        // Version Transition Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF064E3B).copy(alpha = 0.55f), SlateDeepest)
                                    )
                                )
                                .border(1.dp, EmeraldAccent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VersionBadgeChip(uiState.currentVersionName, SlateBorder, SlateTextPrimary)
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = EmeraldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                VersionBadgeChip(rel.tagName, EmeraldAccent, SlateDeepest, bold = true)
                                Spacer(Modifier.weight(1f))
                                if (dateLabel.isNotBlank()) {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateTextSecondary
                                    )
                                }
                            }

                            Text(
                                text = rel.releaseName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            if (rel.apkAssetName != null) {
                                Text(
                                    text = buildString {
                                        append("Tệp cài đặt: ${rel.apkAssetName}")
                                        if (sizeLabel.isNotBlank()) append(" • $sizeLabel")
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                    color = EmeraldLight
                                )
                            }
                        }

                        // Changelog Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateDeepest)
                                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Điểm mới trong phiên bản này",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = cleanNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextPrimary,
                                lineHeight = 19.sp
                            )
                        }

                        // Primary Install CTA
                        Button(
                            onClick = { openExternalUpdateUrl(context, rel.preferredInstallUrl) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAccent,
                                contentColor = SlateDeepest
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("dialog_download_apk_btn")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (rel.apkDownloadUrl != null) "Tải & Cài đặt APK (${rel.tagName})" else "Mở trang tải bản mới",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Mẹo: Sau khi tải tệp APK về máy, chỉ cần bấm mở để cập nhật đè trực tiếp, giữ nguyên toàn bộ bài vẽ 2D/3D.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openExternalUpdateUrl(context, rel.htmlUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("dialog_open_github_btn"),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Xem GitHub", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = onSkipVersion,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("dialog_skip_version_btn")
                            ) {
                                Text("Bỏ qua ${rel.tagName}", color = SlateTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    is UpdateCheckStatus.UpToDate -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(EmeraldDark.copy(alpha = 0.22f))
                                .border(1.dp, EmeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bạn đang dùng phiên bản mới nhất!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold
                                )
                                val remoteTag = status.latestRelease?.tagName
                                Text(
                                    text = if (remoteTag != null) "Bản mới nhất trên GitHub hiện là $remoteTag." else "Không có bản phát hành mới hơn.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary
                                )
                            }
                        }
                    }

                    is UpdateCheckStatus.NotConfigured -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AmberAccent.copy(alpha = 0.12f))
                                .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Chưa liên kết kho GitHub",
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Khi đóng gói APK bằng GitHub Actions, ứng dụng sẽ tự nhận kho của bạn. Bạn cũng có thể nhập thủ công tên kho (owner/repo) bên dưới để kiểm tra ngay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextPrimary
                            )
                        }
                    }

                    is UpdateCheckStatus.Error -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(RoseLight.copy(alpha = 0.12f))
                                .border(1.dp, RoseLight.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseLight, modifier = Modifier.size(20.dp))
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextPrimary
                            )
                        }
                    }

                    UpdateCheckStatus.Idle -> Unit
                }

                // Manual Recheck & GitHub Repo Configuration
                HorizontalDivider(color = SlateBorder.copy(alpha = 0.65f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nguồn GitHub Releases",
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.configuredRepo.ifBlank { "Chưa cấu hình" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = if (uiState.configuredRepo.isNotBlank()) CyanAccent else SlateTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = { showRepoConfig = !showRepoConfig },
                        modifier = Modifier.testTag("toggle_repo_config_btn")
                    ) {
                        Text(if (showRepoConfig) "Ẩn" else "Đổi kho", fontSize = 12.sp, color = CyanAccent)
                    }
                }

                if (showRepoConfig) {
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        singleLine = true,
                        label = { Text("Kho GitHub (owner/repo hoặc URL)", fontSize = 12.sp) },
                        placeholder = { Text("vi-du/tool-toan", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input"),
                        shape = RoundedCornerShape(11.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary,
                            focusedLabelColor = CyanAccent,
                            unfocusedLabelColor = SlateTextSecondary
                        )
                    )
                    Button(
                        onClick = { onSaveRepoAndCheck(repoInput) },
                        enabled = uiState.status !is UpdateCheckStatus.Checking,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("save_and_check_update_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lưu kho & Kiểm tra bản mới", fontWeight = FontWeight.SemiBold)
                    }
                } else if (uiState.configuredRepo.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCheckNow,
                        enabled = uiState.status !is UpdateCheckStatus.Checking,
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("manual_check_update_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kiểm tra bản mới trên GitHub")
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionBadgeChip(
    text: String,
    background: Color,
    contentColor: Color,
    bold: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = contentColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
