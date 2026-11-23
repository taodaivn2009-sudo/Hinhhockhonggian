package com.example.ai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.*

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Semantic accent color per AI provider using centralized theme tokens. */
@Composable
fun providerAccentColor(provider: AiProvider): Color = when (provider) {
    AiProvider.GOOGLE_ACCOUNT -> EmeraldAccent
    AiProvider.GOOGLE_API_KEY -> CyanAccent
    AiProvider.OPENAI -> EmeraldLight
    AiProvider.OPENROUTER -> PurpleAccent
    AiProvider.GROQ -> AmberAccent
    AiProvider.DEEPSEEK -> IndigoBright
    AiProvider.ANTHROPIC -> RoseLight
    AiProvider.CUSTOM_OPENAI -> CyanAccent
}

/**
 * Reusable Multi-Provider AI Connection section embedded inside BOTH:
 * - AI Workspace -> "Key / Kết nối" sub-tab
 * - App Settings -> "Kết nối AI Đa Nhà Cung Cấp"
 *
 * Supports 8 independent providers with isolated saved keys, base URLs, models, and token ledgers:
 * Google Account (keyless), Google AI Studio Key, OpenAI, OpenRouter, Groq, DeepSeek, Claude, Custom URL.
 */
@Composable
fun AiKeySetupSection(
    activeProvider: AiProvider,
    activeProfile: AiProviderProfile,
    configuredProviders: Set<AiProvider>,
    onSelectProvider: (AiProvider) -> Unit,
    googleUser: GoogleUserProfile?,
    isSigningIn: Boolean,
    isTestingGoogleAi: Boolean = false,
    authStatusMessage: String?,
    authStatusSuccess: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onTestGoogleAi: () -> Unit = {},
    displayModels: List<AiSyncedModel> = activeProvider.defaultPresetModels(),
    isSyncedFromAccount: Boolean = false,
    isSyncingModels: Boolean = false,
    modelSyncStatusMessage: String? = null,
    modelSyncSuccess: Boolean = false,
    onSyncModels: (overrideApiKey: String?, overrideBaseUrl: String?) -> Unit = { _, _ -> },
    isProbingAllModels: Boolean = false,
    probeProgressDone: Int = 0,
    probeProgressTotal: Int = 0,
    modelProbeResults: Map<String, AiModelProbeResult> = emptyMap(),
    modelProbeSummary: String? = null,
    onProbeAllModels: (overrideApiKey: String?, overrideBaseUrl: String?, subsetModelIds: List<String>?) -> Unit = { _, _, _ -> },
    tokenState: AiContextTokenState = AiContextTokenState(),
    tokenLedgers: Map<String, AiModelTokenLedger> = emptyMap(),
    onRefreshTokens: () -> Unit = {},
    onAddCustomModel: (String) -> Unit = {},
    onRemoveCustomModel: (String) -> Unit = {},
    isVerifying: Boolean,
    statusMessage: String?,
    statusSuccess: Boolean,
    onSaveAndVerify: (apiKey: String, modelId: String, customBaseUrl: String) -> Unit,
    onSaveOnly: (apiKey: String, modelId: String, customBaseUrl: String) -> Unit,
    onSelectModel: (String) -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keyDraft by remember(activeProvider, activeProfile.apiKey) { mutableStateOf(activeProfile.apiKey) }
    var baseUrlDraft by remember(activeProvider, activeProfile.baseUrl) { mutableStateOf(activeProfile.baseUrl) }
    var showKeyPlain by remember(activeProvider) { mutableStateOf(false) }
    var pendingTransfer by remember { mutableStateOf<Pair<AiProvider, String>?>(null) }

    LaunchedEffect(activeProvider, pendingTransfer) {
        val transfer = pendingTransfer
        if (transfer != null && transfer.first == activeProvider) {
            keyDraft = transfer.second
            pendingTransfer = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Horizontal 8-provider selector strip
        ProviderSelectorStrip(
            activeProvider = activeProvider,
            configuredProviders = configuredProviders,
            onSelectProvider = onSelectProvider
        )

        // 2. Provider-specific authentication / API Key + Base URL card
        if (activeProvider == AiProvider.GOOGLE_ACCOUNT) {
            GoogleGeminiAuthCard(
                googleUser = googleUser,
                isSigningIn = isSigningIn,
                isTestingGoogleAi = isTestingGoogleAi,
                authStatusMessage = authStatusMessage,
                authStatusSuccess = authStatusSuccess,
                onSignInWithGoogle = onSignInWithGoogle,
                onSignOutGoogle = onSignOutGoogle,
                onTestGoogleAi = onTestGoogleAi
            )
        } else {
            ProviderApiKeyCard(
                provider = activeProvider,
                profile = activeProfile,
                keyDraft = keyDraft,
                onKeyDraftChange = { keyDraft = it },
                baseUrlDraft = baseUrlDraft,
                onBaseUrlDraftChange = { baseUrlDraft = it },
                showKeyPlain = showKeyPlain,
                onToggleShowKey = { showKeyPlain = !showKeyPlain },
                isVerifying = isVerifying,
                statusMessage = statusMessage,
                statusSuccess = statusSuccess,
                onSwitchToDetectedProvider = { detected ->
                    pendingTransfer = detected to keyDraft
                    onSelectProvider(detected)
                },
                onSaveAndVerify = {
                    onSaveAndVerify(keyDraft, activeProfile.effectiveModelId, baseUrlDraft)
                },
                onSaveOnly = {
                    onSaveOnly(keyDraft, activeProfile.effectiveModelId, baseUrlDraft)
                },
                onClearKey = {
                    keyDraft = ""
                    if (activeProvider.supportsCustomBaseUrl) baseUrlDraft = ""
                    onClearKey()
                }
            )
        }

        // 3. Per-provider Model Catalog, Live Probe & Token Sync
        HorizontalDivider(color = SlateBorder.copy(alpha = 0.45f))

        AiModelPickerSection(
            activeProvider = activeProvider,
            models = displayModels,
            selectedModelId = activeProfile.effectiveModelId,
            isSyncedFromAccount = isSyncedFromAccount,
            isSyncingModels = isSyncingModels,
            syncStatusMessage = modelSyncStatusMessage,
            syncSuccess = modelSyncSuccess,
            onSyncModels = {
                onSyncModels(
                    keyDraft.trim().ifEmpty { null },
                    baseUrlDraft.trim().ifEmpty { null }
                )
            },
            onSelectModel = onSelectModel,
            onAddCustomModel = onAddCustomModel,
            onRemoveCustomModel = onRemoveCustomModel,
            isProbingAllModels = isProbingAllModels,
            probeProgressDone = probeProgressDone,
            probeProgressTotal = probeProgressTotal,
            modelProbeResults = modelProbeResults,
            modelProbeSummary = modelProbeSummary,
            onProbeAllModels = { subset ->
                onProbeAllModels(
                    keyDraft.trim().ifEmpty { null },
                    baseUrlDraft.trim().ifEmpty { null },
                    subset
                )
            },
            tokenState = tokenState,
            tokenLedgers = tokenLedgers,
            onRefreshTokens = onRefreshTokens
        )
    }
}

@Composable
private fun ProviderSelectorStrip(
    activeProvider: AiProvider,
    configuredProviders: Set<AiProvider>,
    onSelectProvider: (AiProvider) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Chọn nhà cung cấp AI",
                style = MaterialTheme.typography.labelLarge,
                color = SlateTextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (configuredProviders.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldAccent.copy(alpha = 0.16f))
                        .border(1.dp, EmeraldAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${configuredProviders.size} đã kết nối",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiProvider.entries.forEach { provider ->
                val selected = provider == activeProvider
                val isConfigured = provider in configuredProviders
                val accent = providerAccentColor(provider)

                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (selected) accent.copy(alpha = 0.22f)
                            else SlateDeepest.copy(alpha = 0.6f)
                        )
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) accent else SlateBorder.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(13.dp)
                        )
                        .clickable { onSelectProvider(provider) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("ai_provider_tab_${provider.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConfigured -> EmeraldAccent
                                    selected -> accent
                                    else -> SlateTextSecondary.copy(alpha = 0.35f)
                                }
                            )
                    )
                    Text(
                        text = provider.shortTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) accent else SlateTextPrimary,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderApiKeyCard(
    provider: AiProvider,
    profile: AiProviderProfile,
    keyDraft: String,
    onKeyDraftChange: (String) -> Unit,
    baseUrlDraft: String,
    onBaseUrlDraftChange: (String) -> Unit,
    showKeyPlain: Boolean,
    onToggleShowKey: () -> Unit,
    isVerifying: Boolean,
    statusMessage: String?,
    statusSuccess: Boolean,
    onSwitchToDetectedProvider: (AiProvider) -> Unit,
    onSaveAndVerify: () -> Unit,
    onSaveOnly: () -> Unit,
    onClearKey: () -> Unit
) {
    val context = LocalContext.current
    val accent = providerAccentColor(provider)
    val isConfigured = profile.isReady(isSignedInWithGoogle = false)
    val canSubmit = !isVerifying && if (provider.supportsCustomBaseUrl) {
        baseUrlDraft.trim().isNotEmpty()
    } else {
        keyDraft.trim().isNotEmpty()
    }
    val hasSavedConfig = profile.hasKey || (provider.supportsCustomBaseUrl && profile.baseUrl.isNotBlank())
    val detectedHint = remember(keyDraft, provider) {
        if (provider == AiProvider.CUSTOM_OPENAI) null
        else AiProvider.detectProviderHintFromKey(keyDraft)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.14f),
                        SlateDeepest.copy(alpha = 0.72f)
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = if (isConfigured) 0.6f else 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (provider.supportsCustomBaseUrl) Icons.Default.Dns else Icons.Default.Key,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.fullTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        provider.supportsCustomBaseUrl && profile.baseUrl.isNotBlank() ->
                            "URL: ${profile.effectiveBaseUrl.removePrefix("https://").removePrefix("http://")}"
                        profile.hasKey -> "Đã lưu Key (${profile.maskedKey}) · Lưu riêng theo nhà cung cấp"
                        else -> "Key & danh sách mô hình được lưu riêng cho ${provider.shortTitle}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConfigured) EmeraldLight else SlateTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isConfigured) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Đã cấu hình ${provider.shortTitle}",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Custom OpenAI-compatible Base URL input + quick endpoint chips
        if (provider.supportsCustomBaseUrl) {
            OutlinedTextField(
                value = baseUrlDraft,
                onValueChange = onBaseUrlDraftChange,
                label = { Text("Base URL (chuẩn OpenAI /v1)") },
                placeholder = { Text("https://api.together.xyz/v1") },
                leadingIcon = {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    cursorColor = accent
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_custom_base_url_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiProvider.customEndpointSuggestions.forEach { (label, url) ->
                    val activeChip = baseUrlDraft.trim().equals(url, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeChip) accent.copy(alpha = 0.2f) else SlateDeepest.copy(alpha = 0.65f))
                            .border(
                                1.dp,
                                if (activeChip) accent else SlateBorder.copy(alpha = 0.7f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onBaseUrlDraftChange(url) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeChip) accent else SlateTextLight,
                            fontWeight = if (activeChip) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // API Key input
        OutlinedTextField(
            value = keyDraft,
            onValueChange = onKeyDraftChange,
            label = {
                Text(
                    if (provider.supportsCustomBaseUrl) "API Key (tùy chọn với Ollama/LAN)"
                    else "${provider.shortTitle} API Key"
                )
            },
            placeholder = { Text(provider.keyPlaceholder, fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                IconButton(
                    onClick = onToggleShowKey,
                    modifier = Modifier.testTag("ai_key_toggle_visibility")
                ) {
                    Icon(
                        imageVector = if (showKeyPlain) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showKeyPlain) "Ẩn API Key" else "Hiện API Key",
                        tint = SlateTextSecondary
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = SlateTextPrimary,
                unfocusedTextColor = SlateTextPrimary,
                cursorColor = accent
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_api_key_input")
        )

        // Smart key-prefix mismatch suggestion banner
        if (detectedHint != null && detectedHint != provider) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AmberAccent.copy(alpha = 0.14f))
                    .border(1.dp, AmberAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("ai_key_provider_mismatch_hint"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = AmberAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Khóa này có dạng của ${detectedHint.shortTitle}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onSwitchToDetectedProvider(detectedHint) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("ai_switch_detected_provider_btn")
                ) {
                    Text(
                        text = "Chuyển sang ${detectedHint.shortTitle}",
                        color = AmberAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Action Buttons: Save & Sync, Save, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSaveAndVerify,
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("ai_save_verify_key_btn")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đang đồng bộ...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Lưu & Đồng bộ", fontWeight = FontWeight.ExtraBold)
                }
            }

            OutlinedButton(
                onClick = onSaveOnly,
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("ai_save_key_only_btn")
            ) {
                Text("Lưu", color = SlateTextPrimary, fontWeight = FontWeight.SemiBold)
            }

            if (hasSavedConfig) {
                IconButton(
                    onClick = onClearKey,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RoseAccent.copy(alpha = 0.14f))
                        .border(1.dp, RoseAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .testTag("ai_clear_key_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Xóa cấu hình ${provider.shortTitle}",
                        tint = RoseAccent
                    )
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusSuccess) EmeraldLight else RoseAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("ai_key_status_message")
            )
        }

        // External API key portal link
        if (provider.apiKeyPortalUrl.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndigoPrimary.copy(alpha = 0.12f))
                    .border(1.dp, IndigoPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(provider.apiKeyPortalUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("ai_get_key_link"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.keyHelpTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = IndigoBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = provider.keyHelpSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Mở trang lấy API Key ${provider.shortTitle}",
                    tint = IndigoBright,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Text(
                text = provider.keyHelpSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SlateTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun GoogleGeminiAuthCard(
    googleUser: GoogleUserProfile?,
    isSigningIn: Boolean,
    isTestingGoogleAi: Boolean = false,
    authStatusMessage: String?,
    authStatusSuccess: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onTestGoogleAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSignedIn = googleUser != null
    val borderColor = if (isSignedIn) EmeraldAccent.copy(alpha = 0.55f) else CyanAccent.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isSignedIn) {
                        listOf(
                            EmeraldAccent.copy(alpha = 0.16f),
                            CyanAccent.copy(alpha = 0.08f)
                        )
                    } else {
                        listOf(
                            CyanAccent.copy(alpha = 0.16f),
                            IndigoPrimary.copy(alpha = 0.12f)
                        )
                    }
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (googleUser != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoogleAccountAvatar(
                    user = googleUser,
                    size = 50.dp,
                    showVerifiedBadge = true
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = googleUser.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (googleUser.email.isNotBlank()) {
                        Text(
                            text = googleUser.email,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Đã đăng nhập Google · App Check: ${activeAppCheckProviderLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTestGoogleAi,
                    enabled = !isTestingGoogleAi,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("google_test_ai_button")
                ) {
                    if (isTestingGoogleAi) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Đang kiểm tra...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Kiểm tra kết nối AI", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onSignOutGoogle,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("google_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = SlateTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Đăng xuất", color = SlateTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyanAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dùng Gemini bằng tài khoản Google",
                        style = MaterialTheme.typography.titleSmall,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Đăng nhập Google 1 chạm — sử dụng ngay Google Gemini mà không cần tạo hay dán API Key.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = onSignInWithGoogle,
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        color = Color(0xFF1F1F1F),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Đang đăng nhập Google...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng nhập với Google", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }

        if (!authStatusMessage.isNullOrBlank()) {
            Text(
                text = authStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (authStatusSuccess) EmeraldLight else RoseAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("google_auth_status_message")
            )
        }

        if (googleUser == null) {
            val context = LocalContext.current
            val signingDiag = remember(context) { GoogleAuthManager.inspectAppSigningSha1(context) }
            val showSha1Helper = signingDiag.isMismatch ||
                (!authStatusSuccess && (
                    authStatusMessage?.contains("SHA-1", ignoreCase = true) == true ||
                        authStatusMessage?.contains("reauth", ignoreCase = true) == true ||
                        authStatusMessage?.contains("[16]") == true
                    ))

            if (showSha1Helper && !signingDiag.actualSha1.isNullOrBlank()) {
                var copiedSha1 by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateDeepest.copy(alpha = 0.72f))
                        .border(
                            1.dp,
                            if (signingDiag.isMismatch) AmberAccent.copy(alpha = 0.55f) else SlateBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                        .testTag("google_sha1_diagnostic_card"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (signingDiag.isMismatch) {
                                "Phát hiện lệch chữ ký SHA-1 trên APK này"
                            } else {
                                "Chữ ký SHA-1 của bản APK đang cài"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (signingDiag.isMismatch) AmberAccent else SlateTextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanAccent.copy(alpha = 0.16f))
                                .border(1.dp, CyanAccent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("APK SHA-1", signingDiag.actualSha1))
                                    copiedSha1 = true
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("copy_apk_sha1_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Sao chép SHA-1",
                                tint = CyanAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (copiedSha1) "Đã chép!" else "Chép SHA-1",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "APK hiện tại: ${signingDiag.actualSha1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = SlateTextPrimary,
                        fontSize = 11.sp
                    )
                    if (signingDiag.isMismatch && signingDiag.expectedSha1s.isNotEmpty()) {
                        Text(
                            text = "Firebase đăng ký: ${signingDiag.expectedSha1s.first()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldLight,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Cách sửa (chọn 1 trong 2):\n• Cách 1: Thêm GitHub Secret DEBUG_KEYSTORE_BASE64 (copy từ debug.keystore.base64) rồi build lại APK.\n• Cách 2: Dán mã SHA-1 'APK hiện tại' vào Firebase Console → Project Settings → Add fingerprint.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextSecondary,
                            lineHeight = 16.sp,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays the signed-in Google account's profile photo via Coil with an automatic
 * initials fallback and optional emerald verified checkmark badge.
 */
@Composable
fun GoogleAccountAvatar(
    user: GoogleUserProfile,
    size: Dp = 46.dp,
    showVerifiedBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val badgeSize = (size * 0.36f).coerceIn(14.dp, 20.dp)

    Box(
        modifier = modifier
            .size(size)
            .testTag("google_account_avatar"),
        contentAlignment = Alignment.Center
    ) {
        val avatarBoxModifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(EmeraldAccent.copy(alpha = 0.22f))
            .border(1.5.dp, EmeraldAccent, CircleShape)

        val photoUrl = user.photoUrl
        if (!photoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Ảnh đại diện ${user.displayName}",
                contentScale = ContentScale.Crop,
                modifier = avatarBoxModifier,
                loading = {
                    GoogleInitialsAvatarContent(user = user, size = size)
                },
                error = {
                    GoogleInitialsAvatarContent(user = user, size = size)
                }
            )
        } else {
            Box(modifier = avatarBoxModifier, contentAlignment = Alignment.Center) {
                GoogleInitialsAvatarContent(user = user, size = size)
            }
        }

        if (showVerifiedBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(SlateDark)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Đã đăng nhập Google",
                    tint = EmeraldAccent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GoogleInitialsAvatarContent(user: GoogleUserProfile, size: Dp) {
    val textSize = when {
        size <= 30.dp -> 11.sp
        size <= 40.dp -> 13.sp
        else -> 16.sp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        EmeraldAccent.copy(alpha = 0.32f),
                        CyanAccent.copy(alpha = 0.28f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.initials,
            color = EmeraldLight,
            fontWeight = FontWeight.ExtraBold,
            fontSize = textSize
        )
    }
}

/**
 * Floating Liquid Glass notification banner shown at the top of the app whenever
 * Google Sign-In succeeds, fails, or signs out.
 */
@Composable
fun AuthToastBanner(
    event: AuthToastEvent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 64.dp, start = 14.dp, end = 14.dp)
            .widthIn(max = 540.dp)
    ) {
        if (event != null) {
            val accent = if (event.isSuccess) EmeraldAccent else RoseAccent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SlateDark.copy(alpha = 0.96f))
                    .border(1.2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("google_auth_toast_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (event.user != null) {
                    GoogleAccountAvatar(
                        user = event.user,
                        size = 40.dp,
                        showVerifiedBadge = true
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (event.isSuccess) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (event.isSuccess) EmeraldLight else RoseAccent,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = event.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng thông báo",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
