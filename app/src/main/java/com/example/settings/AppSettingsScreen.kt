package com.example.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAssistantUiState
import com.example.ai.AiKeySetupSection
import com.example.ai.GoogleAccountAvatar
import com.example.engine.SensitivityConfig
import com.example.geo2d.Geo2DSensitivity
import com.example.geo2d.Geo2DSensitivityControlsSection
import com.example.geo2d.Geo2DViewModel
import com.example.geo2d.MathWorkspace
import com.example.geo2d.WorkspaceModeSwitch
import com.example.ui.panes.SensitivityControlsSection
import com.example.ui.theme.*
import com.example.update.AppUpdateUiState
import com.example.update.GitHubUpdateChecker
import com.example.update.UpdateCheckStatus
import com.example.update.openExternalUpdateUrl
import com.example.viewmodel.GeometryViewModel

/**
 * Dedicated App Settings tab ("Cài đặt") consolidating all application-level preferences:
 * - Appearance, Light/Dark/System mode, curated themes, and custom accent color
 * - Touch & 3D gesture sensitivity calibration
 * - GitHub Releases automatic update checker & repository link
 * - App info
 *
 * Figure/Shape-specific controls remain strictly separated inside the 3D & 2D "Cài đặt hình" panels.
 */
@Composable
fun AppSettingsScreen(
    themeConfig: AppThemeConfig,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectThemePreset: (AppThemePreset) -> Unit,
    onSelectAccentColor: (Long?) -> Unit,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onApplyLiquidGlassStyle: (LiquidGlassPresetStyle) -> Unit,
    onUpdateLiquidGlass: (
        blurRadius: Float?,
        refraction: Float?,
        chromaticDispersion: Float?,
        translucency: Float?,
        specularGlow: Float?,
        refractionTint: Float?,
        borderIntensity: Float?,
        dynamicShimmer: Boolean?,
        highContrastText: Boolean?
    ) -> Unit,
    onResetThemeDefault: () -> Unit,
    sensitivity: SensitivityConfig,
    geometryViewModel: GeometryViewModel,
    sensitivity2D: Geo2DSensitivity,
    geo2DViewModel: Geo2DViewModel,
    updateUiState: AppUpdateUiState,
    onCheckUpdateNow: () -> Unit,
    onSaveUpdateRepoAndCheck: (String) -> Unit,
    onSkipUpdateVersion: () -> Unit,
    aiUiState: AiAssistantUiState,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSaveAndVerifyAiKey: (String, String) -> Unit,
    onSaveAiKeyOnly: (String, String) -> Unit,
    onSelectAiModel: (String) -> Unit,
    onClearAiKey: () -> Unit,
    onSwitchWorkspace: (MathWorkspace) -> Unit
) {
    val context = LocalContext.current
    var showRepoEditor by remember(updateUiState.configuredRepo) {
        mutableStateOf(updateUiState.configuredRepo.isBlank())
    }
    var repoInput by remember(updateUiState.configuredRepo) {
        mutableStateOf(updateUiState.configuredRepo)
    }

    val bgOnlyBackdrop = rememberLiquidGlassBackdropState()
    val scrollBackdrop = rememberLiquidGlassBackdropState()

    BackHandler {
        onSwitchWorkspace(MathWorkspace.SPACE_3D)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_settings_screen")
    ) {
        // Layer 1 (captured by floating top capsule bar): Aurora backdrop + scrolling settings cards
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(scrollBackdrop)
        ) {
            LiquidGlassBackdrop(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassBackdropSource(bgOnlyBackdrop)
            )

            CompositionLocalProvider(LocalLiquidGlassBackdrop provides bgOnlyBackdrop) {
                // Scrollable Settings Body flows directly underneath the floating Liquid Glass header
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, end = 14.dp, top = 66.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    // Page Title Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(IndigoPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = IndigoBright,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cài đặt ứng dụng",
                                style = MaterialTheme.typography.titleLarge,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Key AI, chủ đề giao diện, độ nhạy cảm ứng & cập nhật GitHub",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary
                            )
                        }
                    }

                    // 0. Dedicated Google Sign-In & AI Section
                    SettingsGroupCard(
                        icon = if (aiUiState.isSignedInWithGoogle) Icons.Default.AutoAwesome else Icons.Default.Key,
                        iconTint = if (aiUiState.isAiReady) EmeraldAccent else CyanAccent,
                        title = "Tài khoản Google & AI (Gemini)",
                        subtitle = when {
                            aiUiState.googleUser != null -> "Google: ${aiUiState.googleUser.email.ifBlank { aiUiState.googleUser.displayName }} • ${aiUiState.keyConfig.effectiveModelId}"
                            aiUiState.keyConfig.hasKey -> "API Key (${aiUiState.keyConfig.maskedKey}) • ${aiUiState.keyConfig.effectiveModelId}"
                            else -> "Đăng nhập Google để dùng Gemini không cần API Key"
                        },
                        leadingContent = aiUiState.googleUser?.let { user ->
                            {
                                GoogleAccountAvatar(
                                    user = user,
                                    size = 40.dp,
                                    showVerifiedBadge = true
                                )
                            }
                        }
                    ) {
                        AiKeySetupSection(
                            googleUser = aiUiState.googleUser,
                            isSigningIn = aiUiState.isSigningIn,
                            authStatusMessage = aiUiState.authStatusMessage,
                            authStatusSuccess = aiUiState.authStatusSuccess,
                            onSignInWithGoogle = onSignInWithGoogle,
                            onSignOutGoogle = onSignOutGoogle,
                            keyConfig = aiUiState.keyConfig,
                            isVerifying = aiUiState.isVerifyingKey,
                            statusMessage = aiUiState.keyStatusMessage,
                            statusSuccess = aiUiState.keyStatusSuccess,
                            onSaveAndVerify = onSaveAndVerifyAiKey,
                            onSaveOnly = onSaveAiKeyOnly,
                            onSelectModel = onSelectAiModel,
                            onClearKey = onClearAiKey
                        )
                    }

                    // 1. Theme & Colors Section
                    SettingsGroupCard(
                        icon = Icons.Default.Palette,
                        iconTint = IndigoBright,
                        title = "Chủ đề & Màu sắc giao diện",
                        subtitle = "${themeConfig.preset.title} • ${if (ActiveThemeState.current.isDark) "Chế độ Tối" else "Chế độ Sáng"}"
                    ) {
                        ThemeStudioSection(
                            config = themeConfig,
                            onSelectMode = onSelectThemeMode,
                            onSelectPreset = onSelectThemePreset,
                            onSelectAccentColor = onSelectAccentColor,
                            onResetDefault = onResetThemeDefault
                        )
                    }

                    // 1b. Dedicated Liquid Glass Material Settings Card
                    SettingsGroupCard(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = CyanAccent,
                        title = "Cài đặt Liquid Glass",
                        subtitle = if (themeConfig.liquidGlass.enabled) {
                            "Mờ ${themeConfig.liquidGlass.blurRadius.toInt()}dp • Khúc xạ ${(themeConfig.liquidGlass.refraction * 100).toInt()}% • Trong suốt ${(themeConfig.liquidGlass.translucency * 100).toInt()}%"
                        } else {
                            "Kính lỏng làm mờ nền, khúc xạ viền & thanh viên nang"
                        }
                    ) {
                        LiquidGlassSettingsSection(
                            config = themeConfig.liquidGlass,
                            onToggleEnabled = onToggleLiquidGlass,
                            onApplyPresetStyle = onApplyLiquidGlassStyle,
                            onUpdateBlurRadius = { onUpdateLiquidGlass(it, null, null, null, null, null, null, null, null) },
                            onUpdateRefraction = { onUpdateLiquidGlass(null, it, null, null, null, null, null, null, null) },
                            onUpdateChromaticDispersion = { onUpdateLiquidGlass(null, null, it, null, null, null, null, null, null) },
                            onUpdateTranslucency = { onUpdateLiquidGlass(null, null, null, it, null, null, null, null, null) },
                            onUpdateSpecularGlow = { onUpdateLiquidGlass(null, null, null, null, it, null, null, null, null) },
                            onUpdateRefractionTint = { onUpdateLiquidGlass(null, null, null, null, null, it, null, null, null) },
                            onUpdateBorderIntensity = { onUpdateLiquidGlass(null, null, null, null, null, null, it, null, null) },
                            onToggleDynamicShimmer = { onUpdateLiquidGlass(null, null, null, null, null, null, null, it, null) },
                            onToggleHighContrastText = { onUpdateLiquidGlass(null, null, null, null, null, null, null, null, it) }
                        )
                    }

                    // 2. Touch & Gesture Sensitivity Calibration (App-wide 3D & 2D control feel)
                    SettingsGroupCard(
                        icon = Icons.Default.Speed,
                        iconTint = CyanAccent,
                        title = "Độ nhạy cảm ứng & Điều khiển",
                        subtitle = "Cân chỉnh tốc độ xoay 3D, trượt 2D, thu phóng và vùng chạm"
                    ) {
                        Text(
                            text = "Không gian 3D",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                        SensitivityControlsSection(
                            sensitivity = sensitivity,
                            viewModel = geometryViewModel
                        )
                        HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                        Text(
                            text = "Mặt phẳng & Đồ thị 2D",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Geo2DSensitivityControlsSection(
                            sensitivity = sensitivity2D,
                            viewModel = geo2DViewModel
                        )
                    }

                    // 3. GitHub Releases Update Section
                    SettingsGroupCard(
                        icon = if (updateUiState.hasUpdate) Icons.Default.SystemUpdate else Icons.Default.CloudSync,
                        iconTint = if (updateUiState.hasUpdate) EmeraldLight else EmeraldAccent,
                        title = "Cập nhật từ GitHub",
                        subtitle = "Phiên bản hiện tại: ${updateUiState.currentVersionName} (mã ${updateUiState.currentVersionCode})"
                    ) {
                        when (val status = updateUiState.status) {
                            is UpdateCheckStatus.Checking -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SlateDeepest)
                                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.2.dp,
                                        color = CyanAccent
                                    )
                                    Text(
                                        text = "Đang kiểm tra bản phát hành mới trên GitHub...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextPrimary
                                    )
                                }
                            }

                            is UpdateCheckStatus.UpdateAvailable -> {
                                val rel = status.release
                                val sizeLabel = GitHubUpdateChecker.formatFileSize(rel.apkSizeBytes)
                                val dateLabel = GitHubUpdateChecker.formatPublishedDate(rel.publishedAt)
                                val notes = remember(rel.body) { GitHubUpdateChecker.formatReleaseNotes(rel.body) }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(EmeraldDark.copy(alpha = 0.22f))
                                        .border(1.dp, EmeraldAccent.copy(alpha = 0.7f), RoundedCornerShape(13.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = updateUiState.currentVersionName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                            color = SlateTextSecondary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = EmeraldLight,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = rel.tagName,
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                            color = EmeraldLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.weight(1f))
                                        if (dateLabel.isNotBlank()) {
                                            Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
                                        }
                                    }

                                    Text(
                                        text = rel.releaseName + if (sizeLabel.isNotBlank()) " • $sizeLabel" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SlateTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextLight,
                                        lineHeight = 18.sp
                                    )

                                    Button(
                                        onClick = { openExternalUpdateUrl(context, rel.preferredInstallUrl) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldAccent,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 46.dp)
                                            .testTag("settings_download_apk_btn")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(7.dp))
                                        Text("Tải & Cài đặt APK (${rel.tagName})", fontWeight = FontWeight.Bold)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { openExternalUpdateUrl(context, rel.htmlUrl) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("Xem GitHub", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = onSkipUpdateVersion,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Bỏ qua ${rel.tagName}", color = SlateTextSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            is UpdateCheckStatus.UpToDate -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(EmeraldDark.copy(alpha = 0.16f))
                                        .border(1.dp, EmeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Ứng dụng đang ở phiên bản mới nhất (${status.latestRelease?.tagName ?: updateUiState.currentVersionName}).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextPrimary
                                    )
                                }
                            }

                            is UpdateCheckStatus.NotConfigured -> {
                                Text(
                                    text = "Khi build APK bằng GitHub Actions, kho GitHub sẽ được tự động gắn sẵn. Bạn cũng có thể nhập thủ công tên kho bên dưới:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary
                                )
                            }

                            is UpdateCheckStatus.Error -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(RoseAccent.copy(alpha = 0.12f))
                                        .border(1.dp, RoseAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseLight, modifier = Modifier.size(18.dp))
                                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = SlateTextPrimary)
                                }
                            }

                            UpdateCheckStatus.Idle -> Unit
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kho GitHub Releases", style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
                                Text(
                                    text = updateUiState.configuredRepo.ifBlank { "Chưa liên kết" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                    color = if (updateUiState.configuredRepo.isNotBlank()) CyanAccent else SlateTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { showRepoEditor = !showRepoEditor }) {
                                Text(if (showRepoEditor) "Ẩn" else "Đổi kho", color = CyanAccent, fontSize = 12.sp)
                            }
                        }

                        if (showRepoEditor) {
                            OutlinedTextField(
                                value = repoInput,
                                onValueChange = { repoInput = it },
                                singleLine = true,
                                label = { Text("owner/repo hoặc link GitHub", fontSize = 12.sp) },
                                placeholder = { Text("vi-du/tool-toan", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("settings_github_repo_input"),
                                shape = RoundedCornerShape(11.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedTextColor = SlateTextPrimary,
                                    unfocusedTextColor = SlateTextPrimary
                                )
                            )
                            Button(
                                onClick = { onSaveUpdateRepoAndCheck(repoInput) },
                                enabled = updateUiState.status !is UpdateCheckStatus.Checking,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lưu kho & Kiểm tra ngay", fontWeight = FontWeight.SemiBold)
                            }
                        } else if (updateUiState.configuredRepo.isNotBlank()) {
                            OutlinedButton(
                                onClick = onCheckUpdateNow,
                                enabled = updateUiState.status !is UpdateCheckStatus.Checking,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("settings_check_update_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Kiểm tra bản cập nhật mới")
                            }
                        }
                    }

                    // 4. App Information Card
                    SettingsGroupCard(
                        icon = Icons.Default.Info,
                        iconTint = AmberAccent,
                        title = "Thông tin ứng dụng",
                        subtitle = "Tool Toán ${updateUiState.currentVersionName}"
                    ) {
                        Text(
                            text = "Bộ công cụ học tập Toán học toàn diện: Hình học không gian 3D & Oxyz, Khảo sát đồ thị hàm số & Bảng biến thiên 2D, và Kho công thức Toán THPT Lớp 10 – 11 – 12 (GDPT 2018).",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary,
                            lineHeight = 19.sp
                        )
                        Text(
                            text = "Mẹo: Các tùy chọn riêng cho từng hình (kích thước a, b, h, lưới, nét đứt, mặt cắt...) nằm tại mục \"Cài đặt hình\" ngay trong không gian 3D và 2D.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
            }
        }

        // Layer 2: Floating Liquid Glass Top Capsule Bar (blurs & refracts scrolling Settings cards underneath!)
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides scrollBackdrop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                WorkspaceModeSwitch(
                    current = MathWorkspace.SETTINGS,
                    hasUpdateBadge = updateUiState.hasUpdate,
                    onSelect = onSwitchWorkspace,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f))
        content()
    }
}
