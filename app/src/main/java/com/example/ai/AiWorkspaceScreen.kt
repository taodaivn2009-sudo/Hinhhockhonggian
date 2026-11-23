package com.example.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.docs.PdfCurriculumRepository
import com.example.geo2d.MathWorkspace
import com.example.ui.theme.*

/**
 * Redesigned Minimal, Chat-Focused AI Assistant Workspace:
 * - Compact Top Bar: 2-mode Segmented Control (Hỏi tài liệu | Chụp đề vẽ hình) + Overflow Menu (⋮)
 *   containing "Xóa cuộc trò chuyện" (with confirmation dialog) & "Cài đặt AI & Nhà cung cấp".
 * - Compact AI Status Chip right under Top Bar with Provider/Model + ultra-thin token progress bar.
 *   Tapping it opens the AI Quick Control Bottom Sheet (Token Card + quick Provider/Custom URL/Model switcher).
 * - Spacious Chat / Problem Draw area + compact 1-line Prompt Composer with `+` attach button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWorkspaceScreen(
    aiViewModel: AiAssistantViewModel,
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    onOpenPdfPage: (Int) -> Unit,
    onApplyDrawingPlan: (AiDrawingPlan) -> Unit,
    onOpenAiSettings: () -> Unit
) {
    val uiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val triggerGoogleSignIn: () -> Unit = {
        context.findActivity()?.let { aiViewModel.signInWithGoogle(it) }
    }

    // If any legacy path set activeSubTab to API_KEY, normalize to DOCS_QA since full AI setup now lives in Settings
    val displayedTab = if (uiState.activeSubTab == AiSubTab.PROBLEM_DRAW) {
        AiSubTab.PROBLEM_DRAW
    } else {
        AiSubTab.DOCS_QA
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showAiQuickSheet by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showAiQuickSheet -> showAiQuickSheet = false
            displayedTab == AiSubTab.PROBLEM_DRAW -> aiViewModel.selectSubTab(AiSubTab.DOCS_QA)
            else -> onSwitchWorkspace(MathWorkspace.SPACE_3D)
        }
    }

    val sharedBackdrop = LocalLiquidGlassBackdrop.current
    val fallbackBackdrop = rememberLiquidGlassBackdropState()
    val bgOnlyBackdrop = sharedBackdrop ?: fallbackBackdrop

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_workspace_screen")
    ) {
        LiquidGlassBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(bgOnlyBackdrop, staticCapture = true)
        )

        CompositionLocalProvider(LocalLiquidGlassBackdrop provides bgOnlyBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Minimal Top Bar: 2-Mode Segmented Control + Overflow (⋮)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiTwoModeSegmentedControl(
                        activeTab = displayedTab,
                        onSelectTab = { aiViewModel.selectSubTab(it) },
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .liquidGlassSurface(
                                    shape = CircleShape,
                                    pillShape = CircleShape,
                                    cornerRadiusDp = 24.dp,
                                    elevated = false,
                                    fallbackBg = SlateDark,
                                    fallbackBorder = SlateBorder
                                )
                                .clickable { showOverflowMenu = true }
                                .testTag("ai_overflow_menu_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn Trợ lý AI",
                                tint = SlateTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = SlateDark
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Xóa cuộc trò chuyện",
                                        color = if (uiState.messages.isNotEmpty()) RoseAccent else SlateTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = if (uiState.messages.isNotEmpty()) RoseAccent else SlateTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                enabled = uiState.messages.isNotEmpty(),
                                onClick = {
                                    showOverflowMenu = false
                                    showClearChatConfirm = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Cài đặt AI & Nhà cung cấp",
                                        color = SlateTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenAiSettings()
                                }
                            )
                        }
                    }
                }

                // 2. Compact AI Status Chip with ultra-thin remaining token progress bar
                AiCompactStatusChip(
                    uiState = uiState,
                    onClick = { showAiQuickSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                )

                // 3. Main Content Area (Hỏi tài liệu OR Chụp đề vẽ hình)
                when (displayedTab) {
                    AiSubTab.DOCS_QA -> AiDocsQaTab(
                        uiState = uiState,
                        onSendQuestion = { aiViewModel.askDocumentQuestion(it) },
                        onCancelQuestion = { aiViewModel.cancelDocumentQuestion() },
                        onRetryLastQuestion = { aiViewModel.retryLastDocumentQuestion() },
                        onAttachPdfPage = { aiViewModel.setAttachedPdfPage(it) },
                        onClearAttachedPage = { aiViewModel.setAttachedPdfPage(null) },
                        onSwitchToProblemDraw = { aiViewModel.selectSubTab(AiSubTab.PROBLEM_DRAW) },
                        onOpenPdfPage = onOpenPdfPage,
                        onOpenAiSettings = onOpenAiSettings,
                        onSignInWithGoogle = triggerGoogleSignIn,
                        modifier = Modifier.weight(1f)
                    )

                    else -> AiProblemDrawTab(
                        uiState = uiState,
                        onImageSelected = { aiViewModel.setProblemImageFromUri(it) },
                        onNoteChange = { aiViewModel.updateProblemNote(it) },
                        onClearInput = { aiViewModel.clearProblemInput() },
                        onAnalyzeClick = { aiViewModel.analyzeProblemForDrawing() },
                        onCancelAnalyze = { aiViewModel.cancelProblemAnalysis() },
                        onApplyDrawingPlan = onApplyDrawingPlan,
                        onOpenPdfPage = onOpenPdfPage,
                        onGoToKeyTab = onOpenAiSettings,
                        onSignInWithGoogle = triggerGoogleSignIn,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Confirmation dialog before clearing chat history
        if (showClearChatConfirm) {
            AlertDialog(
                onDismissRequest = { showClearChatConfirm = false },
                containerColor = SlateDark,
                title = {
                    Text(
                        text = "Xóa cuộc trò chuyện?",
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Toàn bộ tin nhắn trong phiên hỏi đáp tài liệu hiện tại sẽ bị xóa. Bộ đếm token bền vững hôm nay vẫn được giữ nguyên.",
                        color = SlateTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearChatConfirm = false
                            aiViewModel.clearChatHistory()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Xóa lịch sử", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatConfirm = false }) {
                        Text("Hủy", color = SlateTextSecondary)
                    }
                }
            )
        }

        // AI Quick Control Bottom Sheet (Token Card + Fast Provider/Custom URL/Model Switcher)
        if (showAiQuickSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAiQuickSheet = false },
                containerColor = SlateDark,
                contentColor = SlateTextPrimary,
                dragHandle = { BottomSheetDefaults.DragHandle(color = SlateBorder) }
            ) {
                AiQuickControlSheetContent(
                    uiState = uiState,
                    onRefreshTokens = { aiViewModel.refreshTokenSync() },
                    onSelectProvider = { aiViewModel.selectProvider(it) },
                    onSelectCustomEndpoint = { aiViewModel.selectCustomEndpoint(it) },
                    onSelectModel = { aiViewModel.selectModel(it) },
                    onOpenFullSettings = {
                        showAiQuickSheet = false
                        onOpenAiSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun AiTwoModeSegmentedControl(
    activeTab: AiSubTab,
    onSelectTab: (AiSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(AiSubTab.DOCS_QA, AiSubTab.PROBLEM_DRAW)
    Row(
        modifier = modifier
            .liquidGlassSurface(
                shape = RoundedCornerShape(24.dp),
                pillShape = CircleShape,
                cornerRadiusDp = 24.dp,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab == activeTab
            val accent = if (tab == AiSubTab.DOCS_QA) CyanAccent else PurpleAccent
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .liquidGlassActiveBubble(
                        active = selected,
                        shape = RoundedCornerShape(20.dp),
                        fallbackActiveColor = accent.copy(alpha = 0.22f)
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("ai_subtab_${tab.key}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accent else SlateTextSecondary,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AiCompactStatusChip(
    uiState: AiAssistantUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = if (uiState.isAiReady) providerAccentColor(uiState.activeProvider) else AmberAccent
    val tok = uiState.tokenState
    val remainingFraction = when {
        !uiState.isAiReady -> 0f
        tok.hasContextLimit -> tok.remainingContextFraction
        else -> 1f
    }
    val barColor = when {
        !uiState.isAiReady -> AmberAccent
        !tok.hasContextLimit -> accentColor
        remainingFraction > 0.4f -> EmeraldAccent
        remainingFraction > 0.15f -> AmberAccent
        else -> RoseAccent
    }
    val signedInUser = uiState.googleUser
    val titleLabel = when {
        !uiState.isAiReady -> "Chưa kết nối AI · Nhấn để thiết lập"
        uiState.activeProvider == AiProvider.GOOGLE_ACCOUNT && signedInUser != null ->
            "${signedInUser.displayName} · ${uiState.activeProfile.effectiveModelId}"
        else -> "${uiState.activeProviderLabel} · ${uiState.activeProfile.effectiveModelId}"
    }
    val tokenBadge = when {
        !uiState.isAiReady -> "Cài đặt"
        tok.isSyncingTokens -> "Đang đo..."
        tok.hasContextLimit -> "${(remainingFraction * 100).toInt()}% ctx"
        tok.usedTokensToday > 0L -> "${AiResponseParser.formatCompactTokens(tok.usedTokensToday)} hôm nay"
        !tok.accountBalanceLabel.isNullOrBlank() -> tok.accountBalanceLabel
        else -> "Đổi nhanh"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .liquidGlassSurface(
                shape = RoundedCornerShape(14.dp),
                cornerRadiusDp = 14.dp,
                elevated = false,
                liveBackdrop = false,
                fallbackBg = SlateDark.copy(alpha = 0.85f),
                accentBorder = accentColor.copy(alpha = 0.4f)
            )
            .clickable(onClick = onClick)
            .testTag("ai_signed_in_account_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (signedInUser != null && uiState.activeProvider == AiProvider.GOOGLE_ACCOUNT) {
                GoogleAccountAvatar(
                    user = signedInUser,
                    size = 24.dp,
                    showVerifiedBadge = true
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = titleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = tokenBadge,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = barColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.testTag("ai_top_bar_token_status")
            )

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Mở bảng điều khiển AI nhanh",
                tint = SlateTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Ultra-thin token remaining progress bar along bottom edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(SlateBorder.copy(alpha = 0.28f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(remainingFraction.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun AiQuickControlSheetContent(
    uiState: AiAssistantUiState,
    onRefreshTokens: () -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onSelectCustomEndpoint: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onOpenFullSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Điều khiển AI nhanh",
                    style = MaterialTheme.typography.titleMedium,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${uiState.activeProviderLabel} · ${uiState.activeProfile.effectiveModelId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextSecondary
                )
            }
            TextButton(onClick = onOpenFullSettings) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cài đặt đủ", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Live persistent token status card
        AiTokenStatusCard(
            tokenState = uiState.tokenState,
            onRefreshTokens = onRefreshTokens,
            activeProvider = uiState.activeProvider
        )

        // Quick Provider Switcher
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Đổi nhanh Nhà cung cấp",
                style = MaterialTheme.typography.labelMedium,
                color = SlateTextSecondary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProvider.entries.forEach { provider ->
                    val selected = provider == uiState.activeProvider
                    val configured = provider in uiState.configuredProviders
                    val accent = providerAccentColor(provider)
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.22f) else SlateDeepest.copy(alpha = 0.7f))
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accent else SlateBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectProvider(provider) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (configured) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldAccent)
                            )
                        }
                        Text(
                            text = provider.shortTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) accent else SlateTextPrimary,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Quick Custom URL selector if Custom OpenAI is active and has saved endpoints
        if (uiState.activeProvider == AiProvider.CUSTOM_OPENAI && uiState.customEndpoints.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chọn nhanh Custom URL (${uiState.customEndpoints.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.customEndpoints.forEach { ep ->
                        val selected = ep.id == uiState.activeCustomEndpoint?.id
                        val accent = providerAccentColor(AiProvider.CUSTOM_OPENAI)
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accent.copy(alpha = 0.22f) else SlateDeepest.copy(alpha = 0.7f))
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) accent else SlateBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectCustomEndpoint(ep.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ep.name.ifBlank { ep.baseUrl },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) accent else SlateTextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Quick Model Switcher
        if (uiState.displayModels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Đổi nhanh Mô hình (${uiState.displayModels.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.displayModels.take(20).forEach { model ->
                        val selected = model.modelId == uiState.activeProfile.effectiveModelId
                        val accent = providerAccentColor(uiState.activeProvider)
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accent.copy(alpha = 0.22f) else SlateDeepest.copy(alpha = 0.7f))
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) accent else SlateBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectModel(model.modelId) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = model.displayName.ifBlank { model.modelId },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) accent else SlateTextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Full settings CTA
        Button(
            onClick = onOpenFullSettings,
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("ai_quick_open_settings_btn")
        ) {
            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Quản lý API Key, Custom URL & Test mô hình", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AiDocsQaTab(
    uiState: AiAssistantUiState,
    onSendQuestion: (String) -> Unit,
    onCancelQuestion: () -> Unit,
    onRetryLastQuestion: () -> Unit,
    onAttachPdfPage: (Int) -> Unit,
    onClearAttachedPage: () -> Unit,
    onSwitchToProblemDraw: () -> Unit,
    onOpenPdfPage: (Int) -> Unit,
    onOpenAiSettings: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showAttachPageDialog by remember { mutableStateOf(false) }
    var attachPageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val isImeVisible = imeBottomPx > 0
    var streamDisplayedChars by remember { mutableIntStateOf(0) }

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 2
        }
    }

    // Always scroll when a new message is added or streaming starts
    LaunchedEffect(uiState.messages.size, uiState.isAskingDocs) {
        if (!uiState.isAskingDocs) streamDisplayedChars = 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    // Follow smoothly as streamed text grows, without yanking users who manually scrolled up to read earlier messages
    LaunchedEffect(streamDisplayedChars / 28) {
        if (uiState.isAskingDocs && streamDisplayedChars > 0 && isNearBottom && !listState.isScrollInProgress) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                listState.scrollToItem(total - 1, scrollOffset = Int.MAX_VALUE / 4)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!uiState.isAiReady) {
                item(key = "docs_missing_key") {
                    Box(modifier = Modifier.widthIn(max = 720.dp)) {
                        AiMissingKeyBanner(
                            isSigningIn = uiState.isSigningIn,
                            onSignInWithGoogle = onSignInWithGoogle,
                            onGoToKeyTab = onOpenAiSettings
                        )
                    }
                }
            }

            if (uiState.messages.isEmpty()) {
                item(key = "docs_welcome") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                cornerRadiusDp = 18.dp,
                                elevated = false,
                                liveBackdrop = false,
                                fallbackBg = SlateDark,
                                accentBorder = CyanAccent.copy(alpha = 0.4f)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Hỏi AI tra cứu tài liệu 388 trang",
                                style = MaterialTheme.typography.titleSmall,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Hỏi bất kỳ công thức hay dạng bài Lớp 10, 11, 12 nào — AI sẽ tóm tắt và mở thẳng đúng trang PDF. Bạn cũng có thể bấm nút + để đính kèm trang PDF cụ thể.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        val quickPrompts = listOf(
                            "Tìm công thức khoảng cách 2 đường chéo nhau Oxyz ở trang nào?",
                            "Tóm tắt công thức nguyên hàm từng phần và chỉ trang PDF",
                            "Công thức lượng giác nhân đôi, hạ bậc ở trang nào?",
                            "Đọc và tóm tắt giúp tôi trang 121"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            quickPrompts.forEach { prompt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyanAccent.copy(alpha = 0.10f))
                                        .border(1.dp, CyanAccent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                        .clickable { onSendQuestion(prompt) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                    Text(prompt, style = MaterialTheme.typography.bodySmall, color = SlateTextPrimary)
                                }
                            }
                        }
                    }
                }
            } else {
                items(uiState.messages, key = { it.id }) { msg ->
                    val isLatestError = msg.isError && msg.id == uiState.messages.lastOrNull()?.id && !uiState.isAskingDocs
                    AiChatBubbleCard(
                        message = msg,
                        onOpenPdfPage = onOpenPdfPage,
                        onRetry = if (isLatestError && !uiState.lastFailedDocsQuestion.isNullOrBlank()) onRetryLastQuestion else null,
                        onOpenSettings = if (isLatestError) onOpenAiSettings else null,
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }

                if (uiState.isAskingDocs) {
                    item(key = "ai_loading_bubble") {
                        val isStreamingText = uiState.streamingDocsAnswer.isNotBlank()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .liquidGlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    cornerRadiusDp = 16.dp,
                                    liveBackdrop = false,
                                    fallbackBg = SlateDark,
                                    accentBorder = CyanAccent.copy(alpha = 0.5f)
                                )
                                .padding(14.dp)
                                .testTag("ai_docs_streaming_bubble"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isStreamingText) {
                                    AiStreamingPulseDot(color = CyanAccent)
                                } else {
                                    CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.docsStatusText.ifBlank { "AI đang tra cứu tài liệu PDF 388 trang..." },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val subLabel = when {
                                        isStreamingText && uiState.docsElapsedSeconds > 0 ->
                                            "Đang viết trực tiếp · ${uiState.docsElapsedSeconds}s"
                                        isStreamingText -> "Đang viết trực tiếp..."
                                        uiState.docsThinkingChars > 0 ->
                                            "Đã suy luận ${uiState.docsThinkingChars} ký tự · ${uiState.docsElapsedSeconds}s"
                                        uiState.docsElapsedSeconds > 0 ->
                                            "Đã chờ ${uiState.docsElapsedSeconds}s · Đang nhận phản hồi"
                                        else -> ""
                                    }
                                    if (subLabel.isNotEmpty()) {
                                        Text(
                                            text = subLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = onCancelQuestion,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("ai_docs_cancel_btn")
                                ) {
                                    Icon(Icons.Default.StopCircle, contentDescription = null, tint = RoseAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Hủy", color = RoseAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = SlateBorder.copy(alpha = 0.35f))
                            if (isStreamingText) {
                                SmoothStreamingText(
                                    targetText = uiState.streamingDocsAnswer,
                                    isStreaming = true,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateTextPrimary,
                                    cursorColor = CyanAccent,
                                    lineHeight = 21.sp,
                                    onDisplayedLengthChanged = { streamDisplayedChars = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_docs_streaming_text")
                                )
                            } else {
                                AiStreamingWaitingPlaceholder(
                                    label = if (uiState.docsThinkingChars > 0) {
                                        "AI đang suy luận công thức & trang tài liệu phù hợp"
                                    } else {
                                        "Đang chờ những chữ đầu tiên từ AI"
                                    },
                                    accentColor = CyanAccent,
                                    textColor = SlateTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Attached PDF page pill
        val attachedPage = uiState.attachedPdfPage
        if (attachedPage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanAccent.copy(alpha = 0.16f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Đính kèm Trang $attachedPage · ${PdfCurriculumRepository.sectionLabelForPage(attachedPage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearAttachedPage, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Bỏ đính kèm trang PDF", tint = CyanAccent, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Compact 1-Line Bottom Prompt Composer (clears FloatingPillBottomNavBar when IME closed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp, bottom = if (isImeVisible) 8.dp else 82.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    pillShape = CircleShape,
                    cornerRadiusDp = 24.dp,
                    elevated = true,
                    fallbackBg = SlateDark,
                    fallbackBorder = SlateBorder
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // `+` Attach Button (PDF Page or Problem Photo)
            Box {
                IconButton(
                    onClick = { showAttachMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("ai_composer_attach_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Đính kèm trang PDF hoặc chụp đề",
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false },
                    containerColor = SlateDark
                ) {
                    DropdownMenuItem(
                        text = { Text("Đính kèm trang PDF (1–388)...", color = SlateTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showAttachMenu = false
                            attachPageInput = (uiState.attachedPdfPage ?: 1).toString()
                            showAttachPageDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Chụp / chọn ảnh đề bài vẽ hình", color = SlateTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showAttachMenu = false
                            onSwitchToProblemDraw()
                        }
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "Hỏi công thức, giải thích trang PDF...",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val q = inputText.trim()
                        if (q.isNotEmpty() && !uiState.isAskingDocs) {
                            inputText = ""
                            onSendQuestion(q)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    cursorColor = CyanAccent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_docs_question_input")
            )

            if (uiState.isAskingDocs) {
                IconButton(
                    onClick = onCancelQuestion,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RoseAccent.copy(alpha = 0.2f))
                        .testTag("ai_composer_stop_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Dừng phản hồi AI",
                        tint = RoseAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val q = inputText.trim()
                        if (q.isNotEmpty()) {
                            inputText = ""
                            onSendQuestion(q)
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) CyanAccent else SlateBorder.copy(alpha = 0.4f))
                        .testTag("ai_docs_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gửi câu hỏi AI",
                        tint = if (inputText.isNotBlank()) Color.Black else SlateTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showAttachPageDialog) {
        AlertDialog(
            onDismissRequest = { showAttachPageDialog = false },
            containerColor = SlateDark,
            title = {
                Text(
                    text = "Đính kèm trang PDF",
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nhập số trang (1 – 388) để AI đọc trực tiếp ảnh trang đó khi trả lời:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
                    )
                    OutlinedTextField(
                        value = attachPageInput,
                        onValueChange = { raw -> attachPageInput = raw.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text("Số trang") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val page = attachPageInput.toIntOrNull()?.coerceIn(1, 388)
                        if (page != null) {
                            onAttachPdfPage(page)
                        }
                        showAttachPageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Đính kèm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAttachPageDialog = false }) {
                    Text("Hủy", color = SlateTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AiChatBubbleCard(
    message: AiChatMessage,
    onOpenPdfPage: (Int) -> Unit,
    onRetry: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        message.isError -> RoseAccent.copy(alpha = 0.14f)
        message.isUser -> IndigoPrimary.copy(alpha = 0.22f)
        else -> SlateDark
    }
    val borderColor = when {
        message.isError -> RoseAccent.copy(alpha = 0.55f)
        message.isUser -> IndigoBright.copy(alpha = 0.55f)
        else -> SlateBorder
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                cornerRadiusDp = 16.dp,
                elevated = !message.isUser,
                liveBackdrop = false,
                fallbackBg = bgColor,
                accentBorder = borderColor
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (message.isUser) "Bạn" else if (message.isError) "Thông báo lỗi" else "Trợ lý AI · Tài liệu DPAD",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    message.isError -> RoseAccent
                    message.isUser -> IndigoBright
                    else -> CyanAccent
                },
                fontWeight = FontWeight.ExtraBold
            )
            if (message.attachedPdfPage != null) {
                Text(
                    text = "• Đính kèm Trang ${message.attachedPdfPage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent
                )
            }
        }

        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = SlateTextPrimary,
            lineHeight = 21.sp
        )

        if (message.isError && (onRetry != null || onOpenSettings != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onRetry != null) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("ai_docs_retry_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thử lại", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                if (onOpenSettings != null) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Text("Kiểm tra kết nối / Key", color = SlateTextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (message.suggestedPages.isNotEmpty()) {
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.4f))
            Text(
                text = "Mở nhanh trong tài liệu PDF:",
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                message.suggestedPages.forEach { page ->
                    AiSuggestedPdfPageRow(suggestion = page, onOpenPdfPage = onOpenPdfPage)
                }
            }
        }
    }
}
