package com.example.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.ai.AiProvider
import com.example.ai.GoogleAccountAvatar
import com.example.ai.providerAccentColor
import com.example.engine.SensitivityConfig
import com.example.geo2d.Geo2DSensitivity
import com.example.geo2d.Geo2DSensitivityControlsSection
import com.example.geo2d.Geo2DViewModel
import com.example.geo2d.MathWorkspace
import com.example.ui.panes.SensitivityControlsSection
import com.example.ui.theme.*
import com.example.update.AppUpdateUiState
import com.example.update.GitHubUpdateChecker
import com.example.update.UpdateCheckStatus
import com.example.update.openExternalUpdateUrl
import com.example.viewmodel.GeometryViewModel
import kotlin.math.abs

enum class SettingsSubPage(val title: String) {
    APPEARANCE("Giao diện & Chủ đề"),
    LIQUID_GLASS("Hiệu ứng Liquid Glass"),
    AI_PROVIDERS("Kết nối AI & Nhà cung cấp"),
    CONTROLS("Cân chỉnh điều khiển"),
    STORAGE_EXPORT("Lưu trữ & Xuất file"),
    UPDATES_ABOUT("Cập nhật & Thông tin")
}

/**
 * Progressive-disclosure Settings Hub & 6 dedicated Detail Sub-Screens:
 * - Hub displays 6 concise summary cards with live status badges and chevrons
 * - Tapping any item navigates smoothly to a dedicated Sub-Screen with Top Back Bar + system BackHandler
 */
@Composable
fun AppSettingsScreen(
    activeSubPage: SettingsSubPage?,
    onSelectSubPage: (SettingsSubPage?) -> Unit,
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
    rememberLastWorkspace: Boolean,
    onToggleRememberLastWorkspace: (Boolean) -> Unit,
    exportFolderUri: String?,
    exportFolderLabel: String?,
    exportTransparentPng: Boolean,
    onSetExportFolder: (uri: String, label: String) -> Unit,
    onClearExportFolder: () -> Unit,
    onToggleExportTransparentPng: (Boolean) -> Unit,
    updateUiState: AppUpdateUiState,
    onCheckUpdateNow: () -> Unit,
    onSaveUpdateRepoAndCheck: (String) -> Unit,
    onSkipUpdateVersion: () -> Unit,
    aiUiState: AiAssistantUiState,
    onSelectAiProvider: (AiProvider) -> Unit,
    onSelectCustomAiEndpoint: (String) -> Unit = {},
    onCreateCustomAiEndpoint: (name: String, baseUrl: String, apiKey: String, verifyAndSync: Boolean) -> Unit = { _, _, _, _ -> },
    onUpdateCustomAiEndpoint: (endpointId: String, name: String, baseUrl: String, apiKey: String, verifyAndSync: Boolean) -> Unit = { _, _, _, _, _ -> },
    onDeleteCustomAiEndpoint: (String) -> Unit = {},
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onTestGoogleAi: () -> Unit = {},
    onSaveAndVerifyAiKey: (String, String, String) -> Unit,
    onSaveAiKeyOnly: (String, String, String) -> Unit,
    onSelectAiModel: (String) -> Unit,
    onSyncAiModels: (String?, String?) -> Unit = { _, _ -> },
    onProbeAllAiModels: (String?, String?, List<String>?) -> Unit = { _, _, _ -> },
    onRefreshAiTokens: () -> Unit = {},
    onAddCustomAiModel: (String) -> Unit = {},
    onRemoveCustomAiModel: (String) -> Unit = {},
    onClearAiKey: () -> Unit,
    onSwitchWorkspace: (MathWorkspace) -> Unit
) {
    val context = LocalContext.current

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            onSetExportFolder(treeUri.toString(), formatSettingsTreeLabel(treeUri))
        }
    }

    BackHandler {
        if (activeSubPage != null) {
            onSelectSubPage(null)
        } else {
            onSwitchWorkspace(MathWorkspace.SPACE_3D)
        }
    }

    val bgBackdrop = rememberLiquidGlassBackdropState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_settings_screen")
    ) {
        LiquidGlassBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(bgBackdrop)
        )

        CompositionLocalProvider(LocalLiquidGlassBackdrop provides bgBackdrop) {
            AnimatedContent(
                targetState = activeSubPage,
                transitionSpec = {
                    if (targetState != null && initialState == null) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else if (targetState == null && initialState != null) {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
                label = "settings_hub_subpage_transition",
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == null) {
                    SettingsHubView(
                        themeConfig = themeConfig,
                        aiUiState = aiUiState,
                        exportFolderLabel = exportFolderLabel,
                        updateUiState = updateUiState,
                        onOpenSubPage = onSelectSubPage
                    )
                } else {
                    SettingsSubScreenScaffold(
                        title = page.title,
                        onBack = { onSelectSubPage(null) }
                    ) {
                        when (page) {
                            SettingsSubPage.APPEARANCE -> {
                                SettingsSubCard {
                                    ThemeStudioSection(
                                        config = themeConfig,
                                        onSelectMode = onSelectThemeMode,
                                        onSelectPreset = onSelectThemePreset,
                                        onSelectAccentColor = onSelectAccentColor,
                                        onResetDefault = onResetThemeDefault
                                    )
                                }
                            }

                            SettingsSubPage.LIQUID_GLASS -> {
                                LiquidGlassLivePreviewBanner(config = themeConfig.liquidGlass)
                                SettingsSubCard {
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
                            }

                            SettingsSubPage.AI_PROVIDERS -> {
                                SettingsSubCard {
                                    AiKeySetupSection(
                                        activeProvider = aiUiState.activeProvider,
                                        activeProfile = aiUiState.activeProfile,
                                        configuredProviders = aiUiState.configuredProviders,
                                        onSelectProvider = onSelectAiProvider,
                                        customEndpoints = aiUiState.customEndpoints,
                                        activeCustomEndpointId = aiUiState.activeCustomEndpointId,
                                        onSelectCustomEndpoint = onSelectCustomAiEndpoint,
                                        onCreateCustomEndpoint = onCreateCustomAiEndpoint,
                                        onUpdateCustomEndpoint = onUpdateCustomAiEndpoint,
                                        onDeleteCustomEndpoint = onDeleteCustomAiEndpoint,
                                        googleUser = aiUiState.googleUser,
                                        isSigningIn = aiUiState.isSigningIn,
                                        isTestingGoogleAi = aiUiState.isTestingGoogleAi,
                                        authStatusMessage = aiUiState.authStatusMessage,
                                        authStatusSuccess = aiUiState.authStatusSuccess,
                                        onSignInWithGoogle = onSignInWithGoogle,
                                        onSignOutGoogle = onSignOutGoogle,
                                        onTestGoogleAi = onTestGoogleAi,
                                        displayModels = aiUiState.displayModels,
                                        isSyncedFromAccount = aiUiState.syncedModels.isNotEmpty(),
                                        isSyncingModels = aiUiState.isSyncingModels,
                                        modelSyncStatusMessage = aiUiState.modelSyncStatusMessage,
                                        modelSyncSuccess = aiUiState.modelSyncSuccess,
                                        onSyncModels = onSyncAiModels,
                                        isProbingAllModels = aiUiState.isProbingAllModels,
                                        probeProgressDone = aiUiState.probeProgressDone,
                                        probeProgressTotal = aiUiState.probeProgressTotal,
                                        modelProbeResults = aiUiState.modelProbeResults,
                                        modelProbeSummary = aiUiState.modelProbeSummary,
                                        onProbeAllModels = onProbeAllAiModels,
                                        tokenState = aiUiState.tokenState,
                                        tokenLedgers = aiUiState.tokenLedgers,
                                        onRefreshTokens = onRefreshAiTokens,
                                        onAddCustomModel = onAddCustomAiModel,
                                        onRemoveCustomModel = onRemoveCustomAiModel,
                                        isVerifying = aiUiState.isVerifyingKey,
                                        statusMessage = aiUiState.keyStatusMessage,
                                        statusSuccess = aiUiState.keyStatusSuccess,
                                        onSaveAndVerify = onSaveAndVerifyAiKey,
                                        onSaveOnly = onSaveAiKeyOnly,
                                        onSelectModel = onSelectAiModel,
                                        onClearKey = onClearAiKey
                                    )
                                }
                            }

                            SettingsSubPage.CONTROLS -> {
                                ControlsSubScreenContent(
                                    sensitivity = sensitivity,
                                    geometryViewModel = geometryViewModel,
                                    sensitivity2D = sensitivity2D,
                                    geo2DViewModel = geo2DViewModel,
                                    rememberLastWorkspace = rememberLastWorkspace,
                                    onToggleRememberLastWorkspace = onToggleRememberLastWorkspace
                                )
                            }

                            SettingsSubPage.STORAGE_EXPORT -> {
                                StorageExportSubScreenContent(
                                    exportFolderUri = exportFolderUri,
                                    exportFolderLabel = exportFolderLabel,
                                    exportTransparentPng = exportTransparentPng,
                                    onPickFolder = { pickFolderLauncher.launch(null) },
                                    onClearFolder = onClearExportFolder,
                                    onToggleTransparentPng = onToggleExportTransparentPng
                                )
                            }

                            SettingsSubPage.UPDATES_ABOUT -> {
                                UpdatesAndAboutSubScreenContent(
                                    updateUiState = updateUiState,
                                    onCheckUpdateNow = onCheckUpdateNow,
                                    onSaveUpdateRepoAndCheck = onSaveUpdateRepoAndCheck,
                                    onSkipUpdateVersion = onSkipUpdateVersion
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHubView(
    themeConfig: AppThemeConfig,
    aiUiState: AiAssistantUiState,
    exportFolderLabel: String?,
    updateUiState: AppUpdateUiState,
    onOpenSubPage: (SettingsSubPage) -> Unit
) {
    val isDarkActive = ActiveThemeState.current.isDark
    val modeLabel = when (themeConfig.mode) {
        AppThemeMode.SYSTEM -> if (isDarkActive) "Hệ thống (Tối)" else "Hệ thống (Sáng)"
        AppThemeMode.DARK -> "Chế độ Tối"
        AppThemeMode.LIGHT -> "Chế độ Sáng"
    }

    val lgConfig = themeConfig.liquidGlass
    val lgPresetName = remember(lgConfig) {
        LiquidGlassPresetStyle.entries.firstOrNull { style ->
            abs(lgConfig.blurRadius - style.config.blurRadius) < 1.5f &&
                abs(lgConfig.refraction - style.config.refraction) < 0.06f &&
                abs(lgConfig.translucency - style.config.translucency) < 0.05f &&
                lgConfig.highContrastText == style.config.highContrastText
        }?.label ?: "Tùy chỉnh"
    }
    val lgSubtitle = if (lgConfig.enabled) {
        "Đang Bật · $lgPresetName (mờ ${lgConfig.blurRadius.toInt()}dp)"
    } else {
        "Đang Tắt · Nhấn để bật hiệu ứng kính lỏng"
    }

    val aiSubtitle = if (aiUiState.isAiReady) {
        "${aiUiState.activeProviderLabel} · ${aiUiState.activeProfile.effectiveModelId}"
    } else {
        "Chưa kích hoạt · 8 nguồn AI & Custom URL"
    }

    val storageSubtitle = exportFolderLabel?.takeIf { it.isNotBlank() }
        ?: "Hỏi vị trí mỗi lần lưu (.JSON, .PNG, .SVG, .OBJ)"

    val updateSubtitle = when (val st = updateUiState.status) {
        is UpdateCheckStatus.UpdateAvailable -> "v${updateUiState.currentVersionName} · Có bản mới ${st.release.tagName}"
        is UpdateCheckStatus.UpToDate -> "v${updateUiState.currentVersionName} · Đang ở bản mới nhất"
        is UpdateCheckStatus.Checking -> "v${updateUiState.currentVersionName} · Đang kiểm tra..."
        else -> "Phiên bản ${updateUiState.currentVersionName} (mã ${updateUiState.currentVersionCode})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Minimalist Hub Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(IndigoPrimary.copy(alpha = 0.18f))
                        .border(1.dp, IndigoBright.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = IndigoBright,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cài đặt",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Chọn mục bên dưới để cấu hình chi tiết",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
                    )
                }
            }

            // Group 1: Giao diện & Hiển thị
            SettingsSectionLabel("Hiển thị & Cá nhân hóa")
            SettingsHubRowCard(
                icon = Icons.Default.Palette,
                iconTint = IndigoBright,
                title = "Giao diện & Chủ đề",
                subtitle = "$modeLabel · ${themeConfig.preset.title}",
                testTag = "settings_hub_appearance",
                onClick = { onOpenSubPage(SettingsSubPage.APPEARANCE) }
            )
            SettingsHubRowCard(
                icon = Icons.Default.AutoAwesome,
                iconTint = CyanAccent,
                title = "Hiệu ứng Liquid Glass (Kính lỏng)",
                subtitle = lgSubtitle,
                badgeText = if (lgConfig.enabled) "Bật" else "Tắt",
                badgeColor = if (lgConfig.enabled) CyanAccent else SlateTextSecondary,
                testTag = "settings_hub_liquid_glass",
                onClick = { onOpenSubPage(SettingsSubPage.LIQUID_GLASS) }
            )

            // Group 2: Trợ lý AI & Không gian thao tác
            SettingsSectionLabel("Trợ lý AI & Thao tác")
            SettingsHubRowCard(
                icon = if (aiUiState.activeProvider == AiProvider.GOOGLE_ACCOUNT) Icons.Default.AutoAwesome else Icons.Default.Key,
                iconTint = if (aiUiState.isAiReady) providerAccentColor(aiUiState.activeProvider) else PurpleAccent,
                title = "Kết nối AI & Nhà cung cấp",
                subtitle = aiSubtitle,
                leadingOverride = if (aiUiState.activeProvider == AiProvider.GOOGLE_ACCOUNT) {
                    aiUiState.googleUser?.let { user ->
                        { GoogleAccountAvatar(user = user, size = 40.dp, showVerifiedBadge = true) }
                    }
                } else null,
                testTag = "settings_hub_ai",
                onClick = { onOpenSubPage(SettingsSubPage.AI_PROVIDERS) }
            )
            SettingsHubRowCard(
                icon = Icons.Default.Tune,
                iconTint = EmeraldAccent,
                title = "Cân chỉnh điều khiển (3D & 2D)",
                subtitle = "Độ nhạy xoay, thu phóng, kéo điểm & vùng chạm",
                testTag = "settings_hub_controls",
                onClick = { onOpenSubPage(SettingsSubPage.CONTROLS) }
            )

            // Group 3: Lưu trữ & Hệ thống
            SettingsSectionLabel("Lưu trữ & Hệ thống")
            SettingsHubRowCard(
                icon = Icons.Default.FolderOpen,
                iconTint = AmberAccent,
                title = "Lưu trữ & Xuất file",
                subtitle = storageSubtitle,
                testTag = "settings_hub_storage",
                onClick = { onOpenSubPage(SettingsSubPage.STORAGE_EXPORT) }
            )
            SettingsHubRowCard(
                icon = if (updateUiState.hasUpdate) Icons.Default.SystemUpdate else Icons.Default.CloudSync,
                iconTint = if (updateUiState.hasUpdate) EmeraldLight else SkyBlue,
                title = "Cập nhật & Thông tin ứng dụng",
                subtitle = updateSubtitle,
                badgeText = if (updateUiState.hasUpdate) "Mới" else null,
                badgeColor = EmeraldLight,
                testTag = "settings_hub_updates",
                onClick = { onOpenSubPage(SettingsSubPage.UPDATES_ABOUT) }
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = SlateTextSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsHubRowCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    badgeText: String? = null,
    badgeColor: Color = CyanAccent,
    leadingOverride: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingOverride != null) {
            leadingOverride()
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.18f))
                    .border(1.dp, badgeColor.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Mở $title",
            tint = SlateTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Clean Sub-Screen Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        pillShape = CircleShape,
                        fallbackBg = SlateDark,
                        fallbackBorder = SlateBorder
                    )
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .testTag("settings_sub_back_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại Cài đặt",
                    tint = SlateTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSubCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun LiquidGlassLivePreviewBanner(
    config: LiquidGlassConfig
) {
    val previewBackdrop = rememberLiquidGlassBackdropState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(20.dp))
            .testTag("liquid_glass_live_preview")
    ) {
        // Vibrant mathematical gradient pattern behind the live glass sample
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            IndigoPrimary.copy(alpha = 0.85f),
                            CyanAccent.copy(alpha = 0.72f),
                            PurpleAccent.copy(alpha = 0.78f),
                            EmeraldAccent.copy(alpha = 0.65f)
                        )
                    )
                )
                .liquidGlassBackdropSource(previewBackdrop)
        ) {
            Text(
                text = "f(x) = x³ − 3mx + 1   ·   V = ⅓ Sđ · h   ·   Oxyz",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                color = Color.White.copy(alpha = 0.34f),
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            )
            Text(
                text = "∫ sin(x) dx = −cos(x) + C",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                color = Color.White.copy(alpha = 0.28f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
            )
        }

        // Foreground live Liquid Glass sample capsule
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides previewBackdrop) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        pillShape = CircleShape,
                        elevated = true,
                        fallbackBg = SlateDark.copy(alpha = 0.92f),
                        fallbackBorder = SlateBorder
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (config.enabled) "Xem trước kính lỏng trực tiếp" else "Liquid Glass đang tắt",
                        style = MaterialTheme.typography.labelLarge,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (config.enabled) {
                            "Mờ ${config.blurRadius.toInt()}dp · Khúc xạ ${(config.refraction * 100).toInt()}% · Tán sắc ${(config.chromaticDispersion * 100).toInt()}%"
                        } else {
                            "Bật công tắc bên dưới để thấy hiệu ứng"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsSubScreenContent(
    sensitivity: SensitivityConfig,
    geometryViewModel: GeometryViewModel,
    sensitivity2D: Geo2DSensitivity,
    geo2DViewModel: Geo2DViewModel,
    rememberLastWorkspace: Boolean,
    onToggleRememberLastWorkspace: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // 2-Tab Segmented Control: Không gian 3D | Mặt phẳng 2D
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = CircleShape,
                pillShape = CircleShape,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Không gian 3D" to CyanAccent, "Mặt phẳng 2D" to EmeraldAccent).forEachIndexed { idx, (label, accent) ->
            val selected = selectedTab == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .liquidGlassActiveBubble(
                        active = selected,
                        shape = CircleShape,
                        fallbackActiveColor = accent.copy(alpha = 0.25f)
                    )
                    .clip(CircleShape)
                    .clickable { selectedTab = idx }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag(if (idx == 0) "controls_tab_3d" else "controls_tab_2d"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accent else SlateTextSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }

    SettingsSubCard {
        if (selectedTab == 0) {
            Text(
                text = "Độ nhạy thao tác 3D",
                style = MaterialTheme.typography.titleSmall,
                color = CyanAccent,
                fontWeight = FontWeight.Bold
            )
            SensitivityControlsSection(
                sensitivity = sensitivity,
                viewModel = geometryViewModel
            )
        } else {
            Text(
                text = "Độ nhạy mặt phẳng & đồ thị 2D",
                style = MaterialTheme.typography.titleSmall,
                color = EmeraldAccent,
                fontWeight = FontWeight.Bold
            )
            Geo2DSensitivityControlsSection(
                sensitivity = sensitivity2D,
                viewModel = geo2DViewModel
            )
        }
    }

    // Remember last workspace toggle
    SettingsSubCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleRememberLastWorkspace(!rememberLastWorkspace) }
                .padding(vertical = 2.dp)
                .testTag("settings_remember_workspace_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = IndigoBright,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tự ghi nhớ không gian làm việc gần nhất",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mở lại đúng tab (3D, 2D, Tài liệu, AI) lần cuối khi khởi động ứng dụng",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )
            }
            Switch(
                checked = rememberLastWorkspace,
                onCheckedChange = onToggleRememberLastWorkspace,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = IndigoBright
                )
            )
        }
    }
}

@Composable
private fun StorageExportSubScreenContent(
    exportFolderUri: String?,
    exportFolderLabel: String?,
    exportTransparentPng: Boolean,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onToggleTransparentPng: (Boolean) -> Unit
) {
    val hasPinnedFolder = !exportFolderUri.isNullOrBlank()

    SettingsSubCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (hasPinnedFolder) EmeraldAccent else AmberAccent).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasPinnedFolder) Icons.Default.Folder else Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = if (hasPinnedFolder) EmeraldAccent else AmberAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPinnedFolder) "Thư mục lưu mặc định đang ghim" else "Chế độ hỏi vị trí mỗi lần lưu",
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = exportFolderLabel ?: "Mỗi lần xuất file, Android sẽ mở trình quản lý tệp để bạn chọn thư mục tùy ý",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )
            }
        }

        Button(
            onClick = onPickFolder,
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("settings_pick_export_folder_btn")
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasPinnedFolder) "Đổi thư mục lưu mặc định" else "Ghim thư mục lưu mặc định",
                fontWeight = FontWeight.Bold
            )
        }

        if (hasPinnedFolder) {
            OutlinedButton(
                onClick = onClearFolder,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .testTag("settings_clear_export_folder_btn")
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chuyển về hỏi vị trí mỗi lần lưu", color = SlateTextPrimary)
            }
        }

        HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleTransparentPng(!exportTransparentPng) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nền trong suốt khi xuất ảnh PNG",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Áp dụng đồng bộ cho ảnh xuất từ cả không gian 3D và 2D",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )
            }
            Switch(
                checked = exportTransparentPng,
                onCheckedChange = onToggleTransparentPng,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyanAccent
                )
            )
        }
    }

    SettingsSubCard {
        Text(
            text = "Định dạng hỗ trợ xuất & nhập",
            style = MaterialTheme.typography.titleSmall,
            color = SlateTextPrimary,
            fontWeight = FontWeight.Bold
        )
        ExportFormatInfoRow(".JSON", "Lưu & mở lại toàn bộ dự án 3D / 2D cùng thông số", IndigoBright)
        ExportFormatInfoRow(".PNG", "Ảnh độ phân giải cao dùng cho bài giảng, tài liệu", CyanAccent)
        ExportFormatInfoRow(".SVG", "Ảnh vector sắc nét vô hạn cho in ấn & thuyết trình", EmeraldAccent)
        ExportFormatInfoRow(".OBJ", "Mô hình lưới 3D chuẩn dùng trong Blender / phần mềm 3D", AmberAccent)
    }
}

@Composable
private fun ExportFormatInfoRow(ext: String, desc: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ext,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UpdatesAndAboutSubScreenContent(
    updateUiState: AppUpdateUiState,
    onCheckUpdateNow: () -> Unit,
    onSaveUpdateRepoAndCheck: (String) -> Unit,
    onSkipUpdateVersion: () -> Unit
) {
    val context = LocalContext.current
    var showRepoEditor by remember(updateUiState.configuredRepo) {
        mutableStateOf(updateUiState.configuredRepo.isBlank())
    }
    var repoInput by remember(updateUiState.configuredRepo) {
        mutableStateOf(updateUiState.configuredRepo)
    }

    SettingsSubCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (updateUiState.hasUpdate) Icons.Default.SystemUpdate else Icons.Default.CloudSync,
                contentDescription = null,
                tint = if (updateUiState.hasUpdate) EmeraldLight else EmeraldAccent,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cập nhật qua GitHub Releases",
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Phiên bản hiện tại: ${updateUiState.currentVersionName} (mã ${updateUiState.currentVersionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )
            }
        }

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
                            .heightIn(min = 48.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_github_repo_input"),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("settings_check_update_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kiểm tra bản cập nhật mới")
            }
        }
    }

    SettingsSubCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(20.dp))
            Text(
                text = "Tool Toán ${updateUiState.currentVersionName}",
                style = MaterialTheme.typography.titleSmall,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Bộ công cụ học tập Toán học toàn diện: Hình học không gian 3D & Oxyz, Mặt phẳng & Khảo sát đồ thị 2D, Kho tài liệu Toán THPT 388 trang (GDPT 2018), và Trợ lý AI Đa nhà cung cấp.",
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextSecondary,
            lineHeight = 19.sp
        )
    }
}

private fun formatSettingsTreeLabel(treeUri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
    if (!docId.isNullOrBlank()) {
        val parts = docId.split(":", limit = 2)
        if (parts.size == 2) {
            val root = if (parts[0].equals("primary", ignoreCase = true)) "Bộ nhớ trong" else "Bộ nhớ ngoài (${parts[0]})"
            val sub = parts[1].trim('/').replace("/", " / ")
            return if (sub.isBlank()) root else "$root / $sub"
        }
        return docId
    }
    return Uri.decode(treeUri.lastPathSegment ?: "Thư mục đã chọn")
}
