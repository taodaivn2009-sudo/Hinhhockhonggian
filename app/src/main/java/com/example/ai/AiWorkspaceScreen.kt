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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.docs.PdfCurriculumRepository
import com.example.geo2d.MathWorkspace
import com.example.geo2d.WorkspaceModeSwitch
import com.example.ui.theme.*

/**
 * Main AI Assistant Workspace ("AI") integrated with Liquid Glass floating chrome:
 * - Sub-tab 1: Hỏi tài liệu (asks AI grounded on the 388-page DPAD PDF + 1-tap jump to exact PDF pages)
 * - Sub-tab 2: Chụp đề vẽ hình (camera/gallery problem scanner -> automatic 3D or 2D construction)
 * - Sub-tab 3: Mục Key (end-user Gemini API key entry, verification, and model choice)
 */
@Composable
fun AiWorkspaceScreen(
    aiViewModel: AiAssistantViewModel,
    onSwitchWorkspace: (MathWorkspace) -> Unit,
    onOpenPdfPage: (Int) -> Unit,
    onApplyDrawingPlan: (AiDrawingPlan) -> Unit,
    hasUpdateBadge: Boolean = false
) {
    val uiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val triggerGoogleSignIn: () -> Unit = {
        context.findActivity()?.let { aiViewModel.signInWithGoogle(it) }
    }

    BackHandler {
        onSwitchWorkspace(MathWorkspace.SPACE_3D)
    }

    val bgOnlyBackdrop = rememberLiquidGlassBackdropState()
    val scrollBackdrop = rememberLiquidGlassBackdropState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_workspace_screen")
    ) {
        // Layer 1: Live backdrop + AI workspace content
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // AI Sub-Tab Switcher Pill
                    AiSubTabBar(
                        activeTab = uiState.activeSubTab,
                        isSignedInWithGoogle = uiState.isSignedInWithGoogle,
                        hasKey = uiState.keyConfig.hasKey,
                        onSelectTab = { aiViewModel.selectSubTab(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    // Compact active AI provider & model strip on Docs Q&A / Problem Draw tabs
                    val signedInUser = uiState.googleUser
                    if (uiState.isAiReady && uiState.activeSubTab != AiSubTab.API_KEY) {
                        val accentColor = if (uiState.useGoogleGeminiActive) EmeraldAccent else CyanAccent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .clickable { aiViewModel.selectSubTab(AiSubTab.API_KEY) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("ai_signed_in_account_strip"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (signedInUser != null && uiState.useGoogleGeminiActive) {
                                GoogleAccountAvatar(
                                    user = signedInUser,
                                    size = 30.dp,
                                    showVerifiedBadge = true
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(CyanAccent.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.useGoogleGeminiActive && signedInUser != null) {
                                        signedInUser.displayName
                                    } else {
                                        "API Key riêng (${uiState.keyConfig.maskedKey})"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${uiState.activeProviderShortLabel} · ${uiState.keyConfig.effectiveModelId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.useGoogleGeminiActive) EmeraldLight else CyanAccent,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "Đổi mô hình",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    when (uiState.activeSubTab) {
                        AiSubTab.DOCS_QA -> AiDocsQaTab(
                            uiState = uiState,
                            onSendQuestion = { aiViewModel.askDocumentQuestion(it) },
                            onCancelQuestion = { aiViewModel.cancelDocumentQuestion() },
                            onRetryLastQuestion = { aiViewModel.retryLastDocumentQuestion() },
                            onClearAttachedPage = { aiViewModel.setAttachedPdfPage(null) },
                            onClearChat = { aiViewModel.clearChatHistory() },
                            onOpenPdfPage = onOpenPdfPage,
                            onGoToKeyTab = { aiViewModel.selectSubTab(AiSubTab.API_KEY) },
                            onSignInWithGoogle = triggerGoogleSignIn,
                            modifier = Modifier.weight(1f)
                        )

                        AiSubTab.PROBLEM_DRAW -> AiProblemDrawTab(
                            uiState = uiState,
                            onImageSelected = { aiViewModel.setProblemImageFromUri(it) },
                            onNoteChange = { aiViewModel.updateProblemNote(it) },
                            onClearInput = { aiViewModel.clearProblemInput() },
                            onAnalyzeClick = { aiViewModel.analyzeProblemForDrawing() },
                            onCancelAnalyze = { aiViewModel.cancelProblemAnalysis() },
                            onApplyDrawingPlan = onApplyDrawingPlan,
                            onOpenPdfPage = onOpenPdfPage,
                            onGoToKeyTab = { aiViewModel.selectSubTab(AiSubTab.API_KEY) },
                            onSignInWithGoogle = triggerGoogleSignIn,
                            modifier = Modifier.weight(1f)
                        )

                        AiSubTab.API_KEY -> Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(20.dp),
                                        cornerRadiusDp = 20.dp,
                                        elevated = true,
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
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                                    Column {
                                        Text(
                                            text = "Kết nối Google Gemini & Key AI",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SlateTextPrimary,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Đăng nhập Google để dùng ngay không cần API Key, hoặc nhập khóa riêng",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateTextSecondary
                                        )
                                    }
                                }

                                AiKeySetupSection(
                                    googleUser = uiState.googleUser,
                                    isSigningIn = uiState.isSigningIn,
                                    isTestingGoogleAi = uiState.isTestingGoogleAi,
                                    authStatusMessage = uiState.authStatusMessage,
                                    authStatusSuccess = uiState.authStatusSuccess,
                                    onSignInWithGoogle = triggerGoogleSignIn,
                                    onSignOutGoogle = { aiViewModel.signOutGoogle(context) },
                                    onTestGoogleAi = { aiViewModel.testGoogleGeminiConnection() },
                                    keyConfig = uiState.keyConfig,
                                    displayModels = uiState.displayModels,
                                    isSyncedFromAccount = uiState.syncedModels.isNotEmpty(),
                                    isSyncingModels = uiState.isSyncingModels,
                                    modelSyncStatusMessage = uiState.modelSyncStatusMessage,
                                    modelSyncSuccess = uiState.modelSyncSuccess,
                                    onSyncModels = { overrideKey -> aiViewModel.syncAccountModels(overrideKey) },
                                    onAddCustomModel = { aiViewModel.addCustomModel(it) },
                                    onRemoveCustomModel = { aiViewModel.removeCustomModel(it) },
                                    isVerifying = uiState.isVerifyingKey,
                                    statusMessage = uiState.keyStatusMessage,
                                    statusSuccess = uiState.keyStatusSuccess,
                                    onSaveAndVerify = { key, model -> aiViewModel.saveAndVerifyApiKey(key, model) },
                                    onSaveOnly = { key, model -> aiViewModel.saveApiKey(key, model) },
                                    onSelectModel = { aiViewModel.selectModel(it) },
                                    onSelectProviderMode = { aiViewModel.selectProviderMode(it) },
                                    onClearKey = { aiViewModel.clearApiKey() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Layer 2: Floating Liquid Glass Top Workspace Switcher
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
                    current = MathWorkspace.AI,
                    hasUpdateBadge = hasUpdateBadge,
                    onSelect = onSwitchWorkspace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                )
            }
        }
    }
}

@Composable
private fun AiSubTabBar(
    activeTab: AiSubTab,
    isSignedInWithGoogle: Boolean,
    hasKey: Boolean,
    onSelectTab: (AiSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                pillShape = CircleShape,
                cornerRadiusDp = 16.dp,
                elevated = false,
                fallbackBg = SlateDark,
                fallbackBorder = SlateBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AiSubTab.entries.forEach { tab ->
            val selected = tab == activeTab
            val connected = isSignedInWithGoogle || hasKey
            val accent = when (tab) {
                AiSubTab.DOCS_QA -> CyanAccent
                AiSubTab.PROBLEM_DRAW -> PurpleAccent
                AiSubTab.API_KEY -> if (connected) EmeraldAccent else AmberAccent
            }
            val label = if (tab == AiSubTab.API_KEY) {
                when {
                    isSignedInWithGoogle -> "Google ✓"
                    hasKey -> "Key ✓"
                    else -> "Đăng nhập"
                }
            } else tab.label

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .liquidGlassActiveBubble(
                        active = selected,
                        shape = RoundedCornerShape(12.dp),
                        fallbackActiveColor = accent.copy(alpha = 0.24f)
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("ai_subtab_${tab.key}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accent else SlateTextSecondary,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AiDocsQaTab(
    uiState: AiAssistantUiState,
    onSendQuestion: (String) -> Unit,
    onCancelQuestion: () -> Unit,
    onRetryLastQuestion: () -> Unit,
    onClearAttachedPage: () -> Unit,
    onClearChat: () -> Unit,
    onOpenPdfPage: (Int) -> Unit,
    onGoToKeyTab: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll accurately to the TRUE last item (including loading/streaming bubble) whenever chat updates
    LaunchedEffect(uiState.messages.size, uiState.isAskingDocs, uiState.streamingDocsAnswer.length / 40) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
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
                            onGoToKeyTab = onGoToKeyTab
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
                                fallbackBg = SlateDark,
                                accentBorder = CyanAccent.copy(alpha = 0.45f)
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
                                text = "Hỏi AI tìm nội dung tài liệu 388 trang",
                                style = MaterialTheme.typography.titleSmall,
                                color = SlateTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Hỏi bất kỳ công thức hay dạng bài Lớp 10, 11, 12 nào — AI sẽ tóm tắt nội dung và trả về nút mở thẳng đúng trang trong cuốn PDF DPAD 388 trang. Bạn cũng có thể gõ \"Giải thích trang 121\" để AI đọc trực tiếp ảnh trang đó!",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        val quickPrompts = listOf(
                            "Tìm công thức khoảng cách 2 đường chéo nhau Oxyz ở trang nào?",
                            "Tóm tắt công thức nguyên hàm từng phần và chỉ trang PDF",
                            "Công thức lượng giác nhân đôi, hạ bậc ở trang nào?",
                            "Tìm công thức xác suất có điều kiện & Bayes lớp 12",
                            "Đọc và tóm tắt giúp tôi trang 121"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            quickPrompts.forEach { prompt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CyanAccent.copy(alpha = 0.10f))
                                        .border(1.dp, CyanAccent.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                                        .clickable { onSendQuestion(prompt) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                item(key = "chat_clear_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onClearChat) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Xóa cuộc trò chuyện", color = SlateTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    val isLatestError = msg.isError && msg.id == uiState.messages.lastOrNull()?.id && !uiState.isAskingDocs
                    AiChatBubbleCard(
                        message = msg,
                        onOpenPdfPage = onOpenPdfPage,
                        onRetry = if (isLatestError && !uiState.lastFailedDocsQuestion.isNullOrBlank()) onRetryLastQuestion else null,
                        onOpenSettings = if (isLatestError) onGoToKeyTab else null,
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }

                if (uiState.isAskingDocs) {
                    item(key = "ai_loading_bubble") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .liquidGlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    cornerRadiusDp = 16.dp,
                                    fallbackBg = SlateDark,
                                    accentBorder = CyanAccent.copy(alpha = 0.45f)
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.docsStatusText.ifBlank { "AI đang tra cứu tài liệu PDF 388 trang..." },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uiState.docsElapsedSeconds > 0) {
                                        Text(
                                            text = "Đã chờ ${uiState.docsElapsedSeconds}s · Đang nhận phản hồi",
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
                            if (uiState.streamingDocsAnswer.isNotBlank()) {
                                HorizontalDivider(color = SlateBorder.copy(alpha = 0.35f))
                                Text(
                                    text = uiState.streamingDocsAnswer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateTextPrimary,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Attached PDF page indicator (when user jumped from PDF Reader "Hỏi AI về trang X")
        val attachedPage = uiState.attachedPdfPage
        if (attachedPage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyanAccent.copy(alpha = 0.16f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Đính kèm ảnh Trang $attachedPage · ${PdfCurriculumRepository.sectionLabelForPage(attachedPage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearAttachedPage, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Bỏ đính kèm trang PDF", tint = CyanAccent, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Bottom Prompt Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    cornerRadiusDp = 16.dp,
                    elevated = true,
                    fallbackBg = SlateDark,
                    fallbackBorder = SlateBorder
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Hỏi AI tìm công thức hoặc giải thích trang PDF...",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
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

            IconButton(
                onClick = {
                    val q = inputText.trim()
                    if (q.isNotEmpty()) {
                        inputText = ""
                        onSendQuestion(q)
                    }
                },
                enabled = inputText.isNotBlank() && !uiState.isAskingDocs,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank() && !uiState.isAskingDocs) CyanAccent else SlateBorder.copy(alpha = 0.4f))
                    .testTag("ai_docs_send_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gửi câu hỏi AI",
                    tint = if (inputText.isNotBlank() && !uiState.isAskingDocs) Color.Black else SlateTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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
