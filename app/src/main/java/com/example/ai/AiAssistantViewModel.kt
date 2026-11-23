package com.example.ai

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docs.PDF_TOTAL_PAGES
import com.example.docs.PdfBookRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "AiAssistantViewModel"

data class AuthToastEvent(
    val id: Long,
    val title: String,
    val subtitle: String,
    val user: GoogleUserProfile? = null,
    val isSuccess: Boolean = true
)

data class AiAssistantUiState(
    // Multi-provider state (each provider stores its own key, baseUrl, model, synced catalog & token ledger)
    val activeProvider: AiProvider = AiProvider.GOOGLE_ACCOUNT,
    val providerProfiles: Map<AiProvider, AiProviderProfile> = emptyMap(),

    // Google Sign-In + keyless Firebase AI Logic (Google Gemini)
    val googleUser: GoogleUserProfile? = null,
    val isSigningIn: Boolean = false,
    val isTestingGoogleAi: Boolean = false,
    val authStatusMessage: String? = null,
    val authStatusSuccess: Boolean = false,
    val authToast: AuthToastEvent? = null,

    // Active provider configuration projection + synced account models
    val keyConfig: AiKeyConfig = AiKeyConfig(),
    val activeSubTab: AiSubTab = AiSubTab.DOCS_QA,
    val isVerifyingKey: Boolean = false,
    val keyStatusMessage: String? = null,
    val keyStatusSuccess: Boolean = false,
    val syncedModels: List<AiSyncedModel> = emptyList(),
    val isSyncingModels: Boolean = false,
    val modelSyncStatusMessage: String? = null,
    val modelSyncSuccess: Boolean = false,
    val syncedModelsUpdatedAt: Long = 0L,

    // Live batch request testing across models
    val isProbingAllModels: Boolean = false,
    val probeProgressDone: Int = 0,
    val probeProgressTotal: Int = 0,
    val modelProbeResults: Map<String, AiModelProbeResult> = emptyMap(),
    val modelProbeSummary: String? = null,

    // Exact server-verified token usage & remaining context window sync
    val tokenState: AiContextTokenState = AiContextTokenState(),
    val tokenLedgers: Map<String, AiModelTokenLedger> = emptyMap(),

    // Document Q&A chat state
    val messages: List<AiChatMessage> = emptyList(),
    val isAskingDocs: Boolean = false,
    val streamingDocsAnswer: String = "",
    val docsStatusText: String = "",
    val docsElapsedSeconds: Int = 0,
    val lastFailedDocsQuestion: String? = null,
    val attachedPdfPage: Int? = null,

    // Problem Photo -> Draw 3D/2D state
    val problemBitmap: Bitmap? = null,
    val problemNote: String = "",
    val isAnalyzingProblem: Boolean = false,
    val problemStatusText: String = "",
    val problemElapsedSeconds: Int = 0,
    val problemError: String? = null,
    val latestDrawingPlan: AiDrawingPlan? = null
) {
    val activeProfile: AiProviderProfile
        get() = providerProfiles[activeProvider] ?: AiProviderProfile(provider = activeProvider)

    val isSignedInWithGoogle: Boolean get() = googleUser != null

    val isAiReady: Boolean
        get() = activeProfile.isReady(isSignedInWithGoogle)

    val hasAnyConfiguredProvider: Boolean
        get() = isSignedInWithGoogle || providerProfiles.values.any { it.isReady(isSignedInWithGoogle) }

    val configuredProviders: Set<AiProvider>
        get() = AiProvider.entries.filter { p ->
            (providerProfiles[p] ?: AiProviderProfile(p)).isReady(isSignedInWithGoogle)
        }.toSet()

    val useGoogleGeminiActive: Boolean
        get() = activeProvider == AiProvider.GOOGLE_ACCOUNT

    val activeProviderShortLabel: String
        get() = if (isAiReady) activeProvider.shortTitle else "Chưa kết nối"

    val activeProviderLabel: String
        get() = when {
            activeProvider == AiProvider.GOOGLE_ACCOUNT && isSignedInWithGoogle ->
                "Google Gemini · Không cần API Key"
            activeProvider == AiProvider.CUSTOM_OPENAI && activeProfile.effectiveBaseUrl.isNotEmpty() ->
                "Custom URL (${activeProfile.effectiveBaseUrl.removePrefix("https://").removePrefix("http://").take(26)})"
            activeProfile.hasKey ->
                "${activeProvider.shortTitle} (${activeProfile.maskedKey})"
            else -> "Chưa kết nối ${activeProvider.shortTitle}"
        }

    val displayModels: List<AiSyncedModel>
        get() {
            val base = syncedModels.ifEmpty { activeProvider.defaultPresetModels() }
            val currentId = activeProfile.effectiveModelId
            return if (base.any { it.modelId.equals(currentId, ignoreCase = true) }) {
                base
            } else {
                listOf(
                    AiSyncedModel(
                        modelId = currentId,
                        displayName = if (activeProvider == AiProvider.GOOGLE_ACCOUNT || activeProvider == AiProvider.GOOGLE_API_KEY) {
                            AiModelPreset.fromId(currentId)?.title ?: AiResponseParser.formatModelDisplayName(currentId)
                        } else {
                            AiResponseParser.formatModelDisplayName(currentId.substringAfterLast('/'))
                        },
                        description = "Mô hình đang chọn",
                        source = "custom"
                    )
                ) + base
            }
        }
}

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val keyPrefs = AiKeyPreferences(appContext)
    private var pdfRenderer: PdfBookRenderer? = null
    private var msgSeq = 1
    private var toastSeq = 1L
    private var toastAutoDismissJob: Job? = null
    private var activeDocsJob: Job? = null
    private var activeProblemJob: Job? = null
    private var activeTestJob: Job? = null
    private var activeSyncJob: Job? = null
    private var activeProbeAllJob: Job? = null
    private var activeTokenSyncJob: Job? = null
    private var attemptedSilentAuth = false
    private val probeResultsByProvider = LinkedHashMap<AiProvider, Map<String, AiModelProbeResult>>()
    private val probeSummaryByProvider = LinkedHashMap<AiProvider, String?>()

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private fun projectKeyConfig(profile: AiProviderProfile): AiKeyConfig = AiKeyConfig(
        apiKey = profile.apiKey,
        modelId = profile.effectiveModelId,
        providerMode = if (profile.provider == AiProvider.GOOGLE_ACCOUNT) {
            AiProviderMode.GOOGLE_GEMINI
        } else {
            AiProviderMode.PERSONAL_API_KEY
        }
    )

    private fun createInitialState(): AiAssistantUiState {
        val allProfiles = keyPrefs.loadAllProfiles()
        val existingGoogleUser = GoogleAuthManager.currentUserProfile(appContext)
        var activeProvider = keyPrefs.loadActiveProvider()
        // If active provider is Google Account (not signed in) while another provider is configured, keep activeProvider
        val activeProfile = allProfiles[activeProvider] ?: AiProviderProfile(activeProvider)
        val ledgers = keyPrefs.loadTokenLedgers(activeProvider)
        val ready = activeProfile.isReady(existingGoogleUser != null)
        val activeModelId = activeProfile.effectiveModelId
        val matchedModel = activeProfile.syncedModels.firstOrNull { it.modelId.equals(activeModelId, ignoreCase = true) }
        val activeLedger = ledgers[activeModelId.lowercase()]
        val initialTokenState = AiContextTokenState(
            modelId = activeModelId,
            inputTokenLimit = matchedModel?.inputTokenLimit?.takeIf { it > 0 }
                ?: AiResponseParser.inferKnownContextWindow(activeModelId, activeProvider),
            outputTokenLimit = matchedModel?.outputTokenLimit ?: 0,
            usedTokensToday = activeLedger?.usedTokensToday ?: 0L,
            requestsToday = activeLedger?.requestsToday ?: 0,
            lastTurnUsage = activeLedger?.takeIf { it.lastTotalTokens > 0 }?.let {
                AiTokenUsage(
                    promptTokens = it.lastPromptTokens,
                    outputTokens = it.lastOutputTokens,
                    totalTokens = it.lastTotalTokens
                )
            }
        )
        return AiAssistantUiState(
            activeProvider = activeProvider,
            providerProfiles = allProfiles,
            googleUser = existingGoogleUser,
            keyConfig = projectKeyConfig(activeProfile),
            syncedModels = activeProfile.syncedModels,
            syncedModelsUpdatedAt = activeProfile.syncedModelsUpdatedAt,
            tokenState = initialTokenState,
            tokenLedgers = ledgers,
            activeSubTab = if (ready) AiSubTab.DOCS_QA else AiSubTab.API_KEY
        )
    }

    init {
        viewModelScope.launch {
            GoogleAuthManager.observeUserProfile(appContext).collect { profile ->
                _uiState.update { current ->
                    current.copy(googleUser = profile)
                }
            }
        }
        if (_uiState.value.isAiReady) {
            syncContextTokens()
        }
    }

    override fun onCleared() {
        super.onCleared()
        toastAutoDismissJob?.cancel()
        activeDocsJob?.cancel()
        activeProblemJob?.cancel()
        activeTestJob?.cancel()
        activeSyncJob?.cancel()
        activeProbeAllJob?.cancel()
        activeTokenSyncJob?.cancel()
        pdfRenderer?.close()
        pdfRenderer = null
    }

    fun selectSubTab(tab: AiSubTab) {
        _uiState.update { it.copy(activeSubTab = tab) }
    }

    fun dismissAuthToast() {
        toastAutoDismissJob?.cancel()
        _uiState.update { it.copy(authToast = null) }
    }

    private fun showAuthToast(
        title: String,
        subtitle: String,
        user: GoogleUserProfile? = null,
        isSuccess: Boolean = true,
        durationMs: Long = 4200L
    ) {
        toastAutoDismissJob?.cancel()
        val eventId = toastSeq++
        _uiState.update {
            it.copy(
                authToast = AuthToastEvent(
                    id = eventId,
                    title = title,
                    subtitle = subtitle,
                    user = user,
                    isSuccess = isSuccess
                )
            )
        }
        toastAutoDismissJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { current ->
                if (current.authToast?.id == eventId) current.copy(authToast = null) else current
            }
        }
    }

    // ================= GOOGLE SIGN-IN & PROVIDER SELECTION =================

    fun selectProvider(provider: AiProvider) {
        val prevProvider = _uiState.value.activeProvider
        if (prevProvider == provider) return

        // Cancel in-flight probe/sync/token jobs belonging to previous provider
        activeProbeAllJob?.cancel()
        activeSyncJob?.cancel()
        activeTokenSyncJob?.cancel()

        // Cache current provider's probe results in memory
        probeResultsByProvider[prevProvider] = _uiState.value.modelProbeResults
            .filterValues { it.status != AiModelProbeStatus.TESTING }
        probeSummaryByProvider[prevProvider] = _uiState.value.modelProbeSummary

        keyPrefs.saveActiveProvider(provider)
        val profile = _uiState.value.providerProfiles[provider] ?: keyPrefs.loadProfile(provider)
        val ledgers = keyPrefs.loadTokenLedgers(provider)
        val cachedProbes = probeResultsByProvider[provider].orEmpty()
        val cachedProbeSummary = probeSummaryByProvider[provider]

        _uiState.update { cur ->
            cur.copy(
                activeProvider = provider,
                providerProfiles = cur.providerProfiles + (provider to profile),
                keyConfig = projectKeyConfig(profile),
                syncedModels = profile.syncedModels,
                syncedModelsUpdatedAt = profile.syncedModelsUpdatedAt,
                isSyncingModels = false,
                isVerifyingKey = false,
                isProbingAllModels = false,
                probeProgressDone = 0,
                probeProgressTotal = 0,
                keyStatusMessage = null,
                modelSyncStatusMessage = null,
                modelProbeResults = cachedProbes,
                modelProbeSummary = cachedProbeSummary,
                tokenLedgers = ledgers,
                tokenState = buildBaselineTokenState(
                    provider = provider,
                    modelId = profile.effectiveModelId,
                    models = profile.syncedModels,
                    ledgers = ledgers
                )
            )
        }
        if (profile.isReady(_uiState.value.isSignedInWithGoogle)) {
            syncContextTokens()
        }
    }

    fun selectProviderMode(mode: AiProviderMode) {
        val target = if (mode == AiProviderMode.GOOGLE_GEMINI) {
            AiProvider.GOOGLE_ACCOUNT
        } else {
            val cur = _uiState.value.activeProvider
            if (cur == AiProvider.GOOGLE_ACCOUNT) AiProvider.GOOGLE_API_KEY else cur
        }
        selectProvider(target)
    }

    fun testGoogleGeminiConnection() {
        if (_uiState.value.isTestingGoogleAi) return
        val modelId = _uiState.value.keyConfig.effectiveModelId
        _uiState.update {
            it.copy(
                isTestingGoogleAi = true,
                authStatusMessage = "Đang kiểm tra App Check & Google Gemini ($modelId)...",
                authStatusSuccess = true
            )
        }
        activeTestJob?.cancel()
        activeTestJob = viewModelScope.launch {
            try {
                val res = FirebaseAiClient.testConnection(modelId)
                res.onSuccess { okMsg ->
                    _uiState.update {
                        it.copy(
                            authStatusMessage = okMsg,
                            authStatusSuccess = true
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            authStatusMessage = err.localizedMessage ?: "Kiểm tra kết nối Google Gemini thất bại.",
                            authStatusSuccess = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected error testing Google Gemini", e)
                _uiState.update {
                    it.copy(
                        authStatusMessage = "Lỗi kiểm tra kết nối: ${e.localizedMessage ?: e.javaClass.simpleName}",
                        authStatusSuccess = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isTestingGoogleAi = false) }
            }
        }
    }

    fun attemptSilentGoogleSignIn(context: Context) {
        if (attemptedSilentAuth || _uiState.value.isSignedInWithGoogle) return
        attemptedSilentAuth = true
        viewModelScope.launch {
            val profile = GoogleAuthManager.attemptAutoSignIn(context)
            if (profile != null) {
                _uiState.update { current ->
                    current.copy(
                        googleUser = profile,
                        authStatusMessage = "Đã tự động đăng nhập Google (${profile.email.ifEmpty { profile.displayName }}).",
                        authStatusSuccess = true,
                        activeSubTab = if (current.activeSubTab == AiSubTab.API_KEY) AiSubTab.DOCS_QA else current.activeSubTab
                    )
                }
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true, authStatusMessage = null) }
        viewModelScope.launch {
            try {
                when (val outcome = GoogleAuthManager.signInWithGoogle(activity)) {
                    is GoogleSignInOutcome.Success -> {
                        val user = outcome.user
                        _uiState.update { current ->
                            current.copy(
                                googleUser = user,
                                isSigningIn = false,
                                authStatusMessage = "Đăng nhập thành công! Xin chào ${user.displayName} — bạn có thể dùng Google Gemini ngay mà không cần API Key.",
                                authStatusSuccess = true,
                                keyStatusMessage = null
                            )
                        }
                        showAuthToast(
                            title = "Đăng nhập Google thành công!",
                            subtitle = "Xin chào ${user.displayName} · Sẵn sàng dùng Google Gemini",
                            user = user,
                            isSuccess = true
                        )
                    }
                    is GoogleSignInOutcome.Cancelled -> {
                        val hint = outcome.diagnosticHint ?: "Đã hủy đăng nhập Google."
                        _uiState.update {
                            it.copy(
                                isSigningIn = false,
                                authStatusMessage = hint,
                                authStatusSuccess = false
                            )
                        }
                        showAuthToast(
                            title = "Chưa hoàn tất đăng nhập",
                            subtitle = hint,
                            user = null,
                            isSuccess = false,
                            durationMs = 5200L
                        )
                    }
                    is GoogleSignInOutcome.Error -> {
                        _uiState.update {
                            it.copy(
                                isSigningIn = false,
                                authStatusMessage = outcome.message,
                                authStatusSuccess = false
                            )
                        }
                        showAuthToast(
                            title = "Đăng nhập Google thất bại",
                            subtitle = outcome.message,
                            user = null,
                            isSuccess = false,
                            durationMs = 5500L
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected signInWithGoogle error", e)
                _uiState.update {
                    it.copy(
                        isSigningIn = false,
                        authStatusMessage = "Lỗi đăng nhập: ${e.localizedMessage ?: e.javaClass.simpleName}",
                        authStatusSuccess = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isSigningIn = false) }
            }
        }
    }

    fun signOutGoogle(context: Context = appContext) {
        viewModelScope.launch {
            GoogleAuthManager.signOut(context)
            _uiState.update {
                it.copy(
                    googleUser = null,
                    isSigningIn = false,
                    authStatusMessage = "Đã đăng xuất tài khoản Google.",
                    authStatusSuccess = false
                )
            }
            showAuthToast(
                title = "Đã đăng xuất Google",
                subtitle = "Bạn có thể đăng nhập lại bất kỳ lúc nào.",
                user = null,
                isSuccess = true,
                durationMs = 3000L
            )
        }
    }

    // ================= PER-PROVIDER API KEY, MODEL SYNC, PROBE & TOKENS =================

    private fun updateProfileInState(profile: AiProviderProfile, persist: Boolean = true) {
        if (persist) {
            keyPrefs.saveProfile(profile)
        }
        _uiState.update { cur ->
            val updatedMap = cur.providerProfiles + (profile.provider to profile)
            if (cur.activeProvider == profile.provider) {
                cur.copy(
                    providerProfiles = updatedMap,
                    keyConfig = projectKeyConfig(profile),
                    syncedModels = profile.syncedModels,
                    syncedModelsUpdatedAt = profile.syncedModelsUpdatedAt
                )
            } else {
                cur.copy(providerProfiles = updatedMap)
            }
        }
    }

    fun saveApiKey(
        rawKey: String,
        modelId: String = _uiState.value.activeProfile.effectiveModelId,
        customBaseUrl: String = _uiState.value.activeProfile.baseUrl
    ) {
        val provider = _uiState.value.activeProvider.let {
            if (it == AiProvider.GOOGLE_ACCOUNT) AiProvider.GOOGLE_API_KEY else it
        }
        if (provider != _uiState.value.activeProvider) {
            selectProvider(provider)
        }
        val cleanKey = AiResponseParser.sanitizeApiKey(rawKey)
        val cleanBaseUrl = if (provider.supportsCustomBaseUrl) AiProvider.normalizeBaseUrl(customBaseUrl) else provider.defaultBaseUrl
        val current = _uiState.value.providerProfiles[provider] ?: keyPrefs.loadProfile(provider)
        val nextProfile = current.copy(
            apiKey = cleanKey,
            baseUrl = cleanBaseUrl,
            modelId = modelId.trim().ifEmpty { current.effectiveModelId }
        )
        keyPrefs.saveActiveProvider(provider)
        updateProfileInState(nextProfile, persist = true)

        val ready = nextProfile.isReady(_uiState.value.isSignedInWithGoogle)
        _uiState.update {
            it.copy(
                keyStatusMessage = when {
                    provider == AiProvider.CUSTOM_OPENAI && cleanBaseUrl.isEmpty() ->
                        "Vui lòng nhập Base URL hợp lệ (vd: https://api.together.xyz/v1)."
                    ready -> "Đã lưu cấu hình ${provider.shortTitle} (${nextProfile.maskedKey}). Đang đồng bộ mô hình..."
                    else -> "Đã xóa API Key ${provider.shortTitle}."
                },
                keyStatusSuccess = ready
            )
        }
        if (ready) {
            syncAccountModels(overrideApiKey = cleanKey, overrideBaseUrl = cleanBaseUrl)
        }
    }

    fun selectModel(modelId: String) {
        val provider = _uiState.value.activeProvider
        val current = _uiState.value.activeProfile
        val nextId = modelId.trim().ifEmpty { provider.defaultModelId }
        val nextProfile = current.copy(modelId = nextId)
        updateProfileInState(nextProfile, persist = true)
        _uiState.update { cur ->
            cur.copy(
                tokenState = buildBaselineTokenState(provider, nextId, cur.syncedModels, cur.tokenLedgers)
            )
        }
        syncContextTokens()
    }

    fun addCustomModel(rawModelId: String) {
        val provider = _uiState.value.activeProvider
        val cleanId = rawModelId.trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
            .trim()
        if (cleanId.isEmpty()) {
            _uiState.update {
                it.copy(
                    modelSyncStatusMessage = "Hãy nhập mã mô hình hợp lệ (ví dụ: ${provider.defaultModelId}).",
                    modelSyncSuccess = false
                )
            }
            return
        }
        val updatedModels = keyPrefs.addCustomModel(provider, cleanId)
        val now = System.currentTimeMillis()
        val nextProfile = _uiState.value.activeProfile.copy(
            modelId = cleanId,
            syncedModels = updatedModels,
            syncedModelsUpdatedAt = now
        )
        updateProfileInState(nextProfile, persist = true)
        _uiState.update { cur ->
            cur.copy(
                tokenState = buildBaselineTokenState(provider, cleanId, updatedModels, cur.tokenLedgers),
                modelSyncStatusMessage = "Đã thêm và chọn mô hình '$cleanId' cho ${provider.shortTitle}.",
                modelSyncSuccess = true
            )
        }
        syncContextTokens(forceFetchLimits = true)
    }

    fun removeCustomModel(modelId: String) {
        val provider = _uiState.value.activeProvider
        val updatedModels = keyPrefs.removeCustomModel(provider, modelId)
        val current = _uiState.value.activeProfile
        val nextModelId = if (current.effectiveModelId.equals(modelId, ignoreCase = true)) {
            updatedModels.firstOrNull()?.modelId ?: provider.defaultModelId
        } else {
            current.effectiveModelId
        }
        val nextProfile = current.copy(
            modelId = nextModelId,
            syncedModels = updatedModels
        )
        updateProfileInState(nextProfile, persist = true)
        _uiState.update {
            it.copy(
                modelSyncStatusMessage = "Đã xóa mô hình tùy chỉnh '$modelId'.",
                modelSyncSuccess = true
            )
        }
    }

    private fun applyAutoResolvedModel(bestModel: AiSyncedModel, syncedFromServer: List<AiSyncedModel>) {
        val provider = _uiState.value.activeProvider
        val existingCustom = _uiState.value.syncedModels.filter { it.source == "custom" }
        val merged = AiResponseParser.sortSyncedModels(existingCustom + syncedFromServer, provider)
        val now = System.currentTimeMillis()
        keyPrefs.saveSyncedModels(provider, merged, now)
        val nextProfile = _uiState.value.activeProfile.copy(
            modelId = bestModel.modelId,
            syncedModels = merged,
            syncedModelsUpdatedAt = now
        )
        updateProfileInState(nextProfile, persist = true)
        _uiState.update { cur ->
            cur.copy(
                modelSyncStatusMessage = "Đã tự động đồng bộ ${merged.size} mô hình và chuyển sang '${bestModel.modelId}'.",
                modelSyncSuccess = true
            )
        }
    }

    /**
     * Synchronizes live models available on the active provider (Google Account, Google AI Key,
     * OpenAI, OpenRouter, Groq, DeepSeek, Anthropic Claude, or Custom OpenAI-compatible URL).
     */
    fun syncAccountModels(
        overrideApiKey: String? = null,
        overrideBaseUrl: String? = null
    ) {
        if (_uiState.value.isSyncingModels) return
        val state = _uiState.value
        val provider = state.activeProvider
        val profile = state.activeProfile
        val cleanedOverrideKey = overrideApiKey?.let { AiResponseParser.sanitizeApiKey(it) }.orEmpty()
        val candidateKey = cleanedOverrideKey.ifEmpty { profile.trimmedKey }
        val candidateBaseUrl = if (provider.supportsCustomBaseUrl) {
            AiProvider.normalizeBaseUrl(overrideBaseUrl ?: profile.baseUrl)
        } else {
            provider.defaultBaseUrl
        }
        val existingCustomModels = profile.syncedModels.filter { it.source == "custom" }
        val customIds = existingCustomModels.map { it.modelId }

        if (provider == AiProvider.GOOGLE_ACCOUNT && !state.isSignedInWithGoogle) {
            _uiState.update {
                it.copy(
                    modelSyncStatusMessage = "Hãy đăng nhập Google hoặc chọn một nhà cung cấp API Key phía trên rồi bấm Đồng bộ mô hình.",
                    modelSyncSuccess = false
                )
            }
            return
        }
        if (provider == AiProvider.CUSTOM_OPENAI && candidateBaseUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    modelSyncStatusMessage = "Vui lòng nhập Base URL của máy chủ (vd: https://api.together.xyz/v1) trước khi đồng bộ.",
                    modelSyncSuccess = false
                )
            }
            return
        }
        if (provider.requiresApiKey && candidateKey.isEmpty()) {
            _uiState.update {
                it.copy(
                    modelSyncStatusMessage = "Hãy nhập API Key ${provider.shortTitle} rồi bấm Đồng bộ mô hình.",
                    modelSyncSuccess = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSyncingModels = true,
                modelSyncStatusMessage = "Đang đồng bộ danh sách mô hình từ ${provider.shortTitle}...",
                modelSyncSuccess = true
            )
        }

        activeSyncJob?.cancel()
        activeSyncJob = viewModelScope.launch {
            try {
                val syncResult = withTimeout(30_000L) {
                    when (provider) {
                        AiProvider.GOOGLE_ACCOUNT ->
                            FirebaseAiClient.probeAvailableModels(extraModelIds = customIds)
                        AiProvider.GOOGLE_API_KEY ->
                            GeminiApiClient.syncAccountModels(
                                apiKey = candidateKey,
                                preferredModelId = profile.effectiveModelId,
                                extraModelIds = customIds
                            )
                        AiProvider.ANTHROPIC ->
                            AnthropicApiClient.syncAccountModels(apiKey = candidateKey)
                        AiProvider.OPENAI,
                        AiProvider.OPENROUTER,
                        AiProvider.GROQ,
                        AiProvider.DEEPSEEK,
                        AiProvider.CUSTOM_OPENAI ->
                            OpenAiCompatApiClient.syncAccountModels(
                                provider = provider,
                                apiKey = candidateKey,
                                customBaseUrl = candidateBaseUrl
                            )
                    }
                }

                syncResult.onSuccess { result ->
                    val merged = AiResponseParser.sortSyncedModels(existingCustomModels + result.models, provider)
                    val now = System.currentTimeMillis()
                    keyPrefs.saveSyncedModels(provider, merged, now)

                    val currentId = profile.effectiveModelId
                    val isCurrentValid = result.models.any { it.modelId.equals(currentId, ignoreCase = true) } ||
                        existingCustomModels.any { it.modelId.equals(currentId, ignoreCase = true) }
                    val autoPicked = if (!isCurrentValid) AiResponseParser.pickBestChatModel(result.models, provider) else null
                    val finalModelId = autoPicked?.modelId ?: currentId

                    val nextProfile = profile.copy(
                        apiKey = if (provider != AiProvider.GOOGLE_ACCOUNT) candidateKey else profile.apiKey,
                        baseUrl = if (provider.supportsCustomBaseUrl) candidateBaseUrl else profile.baseUrl,
                        modelId = finalModelId,
                        syncedModels = merged,
                        syncedModelsUpdatedAt = now
                    )
                    updateProfileInState(nextProfile, persist = true)

                    val statusMsg = buildString {
                        append(result.message)
                        if (autoPicked != null) {
                            append(" Đã tự động chọn '${autoPicked.modelId}'.")
                        }
                    }

                    _uiState.update { cur ->
                        cur.copy(
                            tokenState = buildBaselineTokenState(provider, finalModelId, merged, cur.tokenLedgers),
                            keyStatusMessage = if (provider != AiProvider.GOOGLE_ACCOUNT) statusMsg else cur.keyStatusMessage,
                            keyStatusSuccess = if (provider != AiProvider.GOOGLE_ACCOUNT) true else cur.keyStatusSuccess,
                            modelSyncStatusMessage = statusMsg,
                            modelSyncSuccess = true
                        )
                    }
                    syncContextTokens()
                }.onFailure { err ->
                    Log.e(TAG, "Account model sync failed for $provider", err)
                    val errText = err.localizedMessage ?: "Đồng bộ danh sách mô hình thất bại."
                    _uiState.update {
                        it.copy(
                            keyStatusMessage = if (provider != AiProvider.GOOGLE_ACCOUNT) errText else it.keyStatusMessage,
                            keyStatusSuccess = if (provider != AiProvider.GOOGLE_ACCOUNT) false else it.keyStatusSuccess,
                            modelSyncStatusMessage = errText,
                            modelSyncSuccess = false
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val timeoutMsg = "Quá thời gian đồng bộ mô hình (30 giây). Hãy kiểm tra kết nối mạng."
                _uiState.update {
                    it.copy(
                        keyStatusMessage = if (provider != AiProvider.GOOGLE_ACCOUNT) timeoutMsg else it.keyStatusMessage,
                        keyStatusSuccess = if (provider != AiProvider.GOOGLE_ACCOUNT) false else it.keyStatusSuccess,
                        modelSyncStatusMessage = timeoutMsg,
                        modelSyncSuccess = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected account model sync error", e)
                val msg = "Lỗi đồng bộ mô hình: ${e.localizedMessage ?: e.javaClass.simpleName}"
                _uiState.update {
                    it.copy(
                        keyStatusMessage = if (provider != AiProvider.GOOGLE_ACCOUNT) msg else it.keyStatusMessage,
                        keyStatusSuccess = if (provider != AiProvider.GOOGLE_ACCOUNT) false else it.keyStatusSuccess,
                        modelSyncStatusMessage = msg,
                        modelSyncSuccess = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isSyncingModels = false) }
            }
        }
    }

    /**
     * Sends a real lightweight request to models of the active provider (3 concurrent workers),
     * streaming per-model status + latency live into the UI.
     * For massive catalogs (e.g., OpenRouter with 200+ models), respects optional [subsetModelIds]
     * from the UI filter/search, or caps to top 24 models to protect user credits/rate limits.
     */
    fun probeAllModels(
        overrideApiKey: String? = null,
        overrideBaseUrl: String? = null,
        subsetModelIds: List<String>? = null
    ) {
        if (_uiState.value.isProbingAllModels) {
            cancelProbeAllModels()
            return
        }
        val state = _uiState.value
        val provider = state.activeProvider
        val profile = state.activeProfile
        val cleanedOverride = overrideApiKey?.let { AiResponseParser.sanitizeApiKey(it) }.orEmpty()
        val candidateKey = cleanedOverride.ifEmpty { profile.trimmedKey }
        val candidateBaseUrl = if (provider.supportsCustomBaseUrl) {
            AiProvider.normalizeBaseUrl(overrideBaseUrl ?: profile.baseUrl)
        } else {
            provider.defaultBaseUrl
        }

        if (provider == AiProvider.GOOGLE_ACCOUNT && !state.isSignedInWithGoogle) {
            _uiState.update {
                it.copy(modelProbeSummary = "Hãy đăng nhập Google hoặc chọn nhà cung cấp API Key rồi bấm Test tất cả.")
            }
            return
        }
        if (provider == AiProvider.CUSTOM_OPENAI && candidateBaseUrl.isEmpty()) {
            _uiState.update {
                it.copy(modelProbeSummary = "Hãy nhập Base URL trước khi test mô hình.")
            }
            return
        }
        if (provider.requiresApiKey && candidateKey.isEmpty()) {
            _uiState.update {
                it.copy(modelProbeSummary = "Hãy nhập API Key ${provider.shortTitle} rồi bấm Test tất cả.")
            }
            return
        }

        activeProbeAllJob?.cancel()
        activeProbeAllJob = viewModelScope.launch {
            try {
                // Persist fresh draft key/URL if user edited before clicking Test
                if (provider != AiProvider.GOOGLE_ACCOUNT &&
                    (candidateKey != profile.trimmedKey || candidateBaseUrl != profile.effectiveBaseUrl)
                ) {
                    updateProfileInState(
                        profile.copy(apiKey = candidateKey, baseUrl = candidateBaseUrl),
                        persist = true
                    )
                }

                // Auto-sync catalog first if not yet synced on API providers
                if (provider != AiProvider.GOOGLE_ACCOUNT && _uiState.value.syncedModels.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProbingAllModels = true,
                            probeProgressDone = 0,
                            probeProgressTotal = 0,
                            modelProbeResults = emptyMap(),
                            modelProbeSummary = "Đang tải danh sách mô hình từ ${provider.shortTitle} trước khi test..."
                        )
                    }
                    val existingCustom = _uiState.value.syncedModels.filter { it.source == "custom" }
                    val syncRes = runCatching {
                        withTimeout(25_000L) {
                            when (provider) {
                                AiProvider.GOOGLE_API_KEY -> GeminiApiClient.syncAccountModels(
                                    apiKey = candidateKey,
                                    preferredModelId = _uiState.value.activeProfile.effectiveModelId,
                                    extraModelIds = existingCustom.map { it.modelId }
                                )
                                AiProvider.ANTHROPIC -> AnthropicApiClient.syncAccountModels(candidateKey)
                                else -> OpenAiCompatApiClient.syncAccountModels(provider, candidateKey, candidateBaseUrl)
                            }
                        }
                    }.getOrNull()
                    syncRes?.onSuccess { result ->
                        val merged = AiResponseParser.sortSyncedModels(existingCustom + result.models, provider)
                        val now = System.currentTimeMillis()
                        keyPrefs.saveSyncedModels(provider, merged, now)
                        updateProfileInState(
                            _uiState.value.activeProfile.copy(syncedModels = merged, syncedModelsUpdatedAt = now),
                            persist = true
                        )
                        _uiState.update {
                            it.copy(
                                modelSyncStatusMessage = result.message,
                                modelSyncSuccess = true
                            )
                        }
                    }
                }

                val catalog = _uiState.value.displayModels
                val baseTargets = if (!subsetModelIds.isNullOrEmpty()) {
                    val allowed = subsetModelIds.map { it.lowercase() }.toSet()
                    catalog.filter { it.modelId.lowercase() in allowed }
                } else {
                    catalog
                }
                val maxProbeBatch = 24
                val wasCapped = baseTargets.size > maxProbeBatch
                val targetModels = if (wasCapped) baseTargets.take(maxProbeBatch) else baseTargets

                if (targetModels.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProbingAllModels = false,
                            modelProbeSummary = "Không có mô hình nào để kiểm tra."
                        )
                    }
                    return@launch
                }

                val cappedNote = if (wasCapped) " (giới hạn top $maxProbeBatch/${baseTargets.size} để tiết kiệm credit; hãy lọc/tìm để test nhóm khác)" else ""
                _uiState.update {
                    it.copy(
                        isProbingAllModels = true,
                        probeProgressDone = 0,
                        probeProgressTotal = targetModels.size,
                        modelProbeResults = emptyMap(),
                        modelProbeSummary = "Đang gửi request kiểm tra ${targetModels.size} mô hình trên ${provider.shortTitle}...$cappedNote"
                    )
                }

                val semaphore = Semaphore(permits = 3)
                val completedCounter = AtomicInteger(0)
                val total = targetModels.size

                val results = coroutineScope {
                    targetModels.map { model ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val keyLower = model.modelId.lowercase()
                                _uiState.update { cur ->
                                    cur.copy(
                                        modelProbeResults = cur.modelProbeResults + (
                                            keyLower to AiModelProbeResult(
                                                modelId = model.modelId,
                                                status = AiModelProbeStatus.TESTING
                                            )
                                            )
                                    )
                                }
                                val probeRes = when (provider) {
                                    AiProvider.GOOGLE_ACCOUNT ->
                                        FirebaseAiClient.probeModelGenerateContent(model.modelId)
                                    AiProvider.GOOGLE_API_KEY ->
                                        GeminiApiClient.probeModelGenerateContent(candidateKey, model.modelId)
                                    AiProvider.ANTHROPIC ->
                                        AnthropicApiClient.probeModel(candidateKey, model.modelId)
                                    AiProvider.OPENAI,
                                    AiProvider.OPENROUTER,
                                    AiProvider.GROQ,
                                    AiProvider.DEEPSEEK,
                                    AiProvider.CUSTOM_OPENAI ->
                                        OpenAiCompatApiClient.probeModel(provider, candidateKey, candidateBaseUrl, model.modelId)
                                }
                                probeRes.tokenUsage?.let { usage ->
                                    recordServerTokenUsage(provider, model.modelId, usage, updateContextFromTurn = false)
                                }
                                val done = completedCounter.incrementAndGet()
                                _uiState.update { cur ->
                                    val updatedMap = cur.modelProbeResults + (keyLower to probeRes)
                                    val okCount = updatedMap.values.count { it.status == AiModelProbeStatus.WORKING }
                                    cur.copy(
                                        probeProgressDone = done,
                                        modelProbeResults = updatedMap,
                                        modelProbeSummary = "Đang kiểm tra $done / $total mô hình (${provider.shortTitle}) · Hoạt động: $okCount"
                                    )
                                }
                                probeRes
                            }
                        }
                    }.awaitAll()
                }

                val workingList = results.filter { it.status == AiModelProbeStatus.WORKING }
                val quotaCount = results.count { it.status == AiModelProbeStatus.QUOTA_LIMIT }
                val failedCount = results.count { it.status == AiModelProbeStatus.FAILED }

                val curProfile = _uiState.value.activeProfile
                val currentWorking = workingList.any { it.modelId.equals(curProfile.effectiveModelId, ignoreCase = true) }
                var autoSwitchedText = ""
                if (!currentWorking && workingList.isNotEmpty()) {
                    val workingIdSet = workingList.map { it.modelId.lowercase() }.toSet()
                    val workingModels = targetModels.filter { it.modelId.lowercase() in workingIdSet }
                    val bestWorking = AiResponseParser.pickBestChatModel(workingModels, provider)
                        ?: workingModels.minByOrNull { m ->
                            workingList.firstOrNull { it.modelId.equals(m.modelId, ignoreCase = true) }?.latencyMs
                                ?: Long.MAX_VALUE
                        }
                    if (bestWorking != null) {
                        updateProfileInState(curProfile.copy(modelId = bestWorking.modelId), persist = true)
                        val bestLatency = workingList.firstOrNull { it.modelId.equals(bestWorking.modelId, ignoreCase = true) }?.latencyMs ?: 0L
                        autoSwitchedText = " Đã tự chọn '${bestWorking.modelId}' (${bestLatency}ms)."
                    }
                }

                val summary = buildString {
                    append("Hoàn tất kiểm tra $total mô hình (${provider.shortTitle}): ")
                    append("${workingList.size} hoạt động")
                    if (quotaCount > 0) append(" · $quotaCount hết quota/credit")
                    if (failedCount > 0) append(" · $failedCount lỗi")
                    append(".")
                    if (wasCapped) append(" (Top $maxProbeBatch/${baseTargets.size})")
                    append(autoSwitchedText)
                }
                probeResultsByProvider[provider] = _uiState.value.modelProbeResults
                probeSummaryByProvider[provider] = summary
                _uiState.update { it.copy(modelProbeSummary = summary) }
            } catch (e: CancellationException) {
                val curResults = _uiState.value.modelProbeResults
                    .filterValues { it.status != AiModelProbeStatus.TESTING }
                val okCount = curResults.values.count { it.status == AiModelProbeStatus.WORKING }
                val stoppedMsg = "Đã dừng kiểm tra (${curResults.size}/${_uiState.value.probeProgressTotal} mô hình · Hoạt động: $okCount)."
                probeResultsByProvider[provider] = curResults
                probeSummaryByProvider[provider] = stoppedMsg
                _uiState.update {
                    it.copy(
                        modelProbeResults = curResults,
                        modelProbeSummary = stoppedMsg
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Batch model probe failed", e)
                _uiState.update {
                    it.copy(modelProbeSummary = "Lỗi kiểm tra mô hình: ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            } finally {
                _uiState.update { cur ->
                    cur.copy(
                        isProbingAllModels = false,
                        modelProbeResults = cur.modelProbeResults.filterValues { it.status != AiModelProbeStatus.TESTING }
                    )
                }
            }
        }
    }

    fun cancelProbeAllModels() {
        activeProbeAllJob?.cancel()
        activeProbeAllJob = null
    }

    fun refreshTokenSync() {
        syncContextTokens(forceFetchLimits = true)
    }

    private fun buildBaselineTokenState(
        provider: AiProvider,
        modelId: String,
        models: List<AiSyncedModel>,
        ledgers: Map<String, AiModelTokenLedger>,
        prev: AiContextTokenState? = null
    ): AiContextTokenState {
        val cleanId = modelId.trim().ifEmpty { provider.defaultModelId }
        val matched = models.firstOrNull { it.modelId.equals(cleanId, ignoreCase = true) }
        val ledger = ledgers[cleanId.lowercase()]
        val sameModelPrev = prev?.takeIf { it.modelId.equals(cleanId, ignoreCase = true) }
        val inferredLimit = matched?.inputTokenLimit?.takeIf { it > 0 }
            ?: sameModelPrev?.inputTokenLimit?.takeIf { it > 0 }
            ?: AiResponseParser.inferKnownContextWindow(cleanId, provider)
        return AiContextTokenState(
            modelId = cleanId,
            inputTokenLimit = inferredLimit,
            outputTokenLimit = matched?.outputTokenLimit?.takeIf { it > 0 } ?: sameModelPrev?.outputTokenLimit ?: 0,
            currentContextTokens = sameModelPrev?.currentContextTokens ?: 0,
            lastTurnUsage = ledger?.takeIf { it.lastTotalTokens > 0 }?.let {
                AiTokenUsage(
                    promptTokens = it.lastPromptTokens,
                    outputTokens = it.lastOutputTokens,
                    totalTokens = it.lastTotalTokens
                )
            } ?: sameModelPrev?.lastTurnUsage,
            usedTokensToday = ledger?.usedTokensToday ?: 0L,
            requestsToday = ledger?.requestsToday ?: 0,
            isSyncingTokens = sameModelPrev?.isSyncingTokens ?: false,
            isServerVerified = sameModelPrev?.isServerVerified ?: false,
            verificationSource = sameModelPrev?.verificationSource.orEmpty(),
            rateLimitRemainingTokens = sameModelPrev?.rateLimitRemainingTokens,
            rateLimitTotalTokens = sameModelPrev?.rateLimitTotalTokens,
            accountBalanceLabel = sameModelPrev?.accountBalanceLabel,
            lastSyncedAtMillis = sameModelPrev?.lastSyncedAtMillis ?: 0L,
            syncError = sameModelPrev?.syncError
        )
    }

    private fun recordServerTokenUsage(
        provider: AiProvider,
        modelId: String,
        usage: AiTokenUsage?,
        updateContextFromTurn: Boolean
    ) {
        if (usage == null || !usage.hasData) return
        val updatedLedgers = keyPrefs.recordTokenUsage(provider, modelId, usage)
        _uiState.update { cur ->
            if (cur.activeProvider != provider) return@update cur
            val activeId = cur.activeProfile.effectiveModelId
            val isActiveModel = activeId.equals(modelId.trim().removePrefix("models/"), ignoreCase = true)
            val nextTokenState = if (isActiveModel) {
                val ledger = updatedLedgers[activeId.lowercase()]
                cur.tokenState.copy(
                    modelId = activeId,
                    currentContextTokens = if (updateContextFromTurn && usage.totalTokens > 0) {
                        usage.totalTokens
                    } else {
                        cur.tokenState.currentContextTokens
                    },
                    lastTurnUsage = usage,
                    usedTokensToday = ledger?.usedTokensToday ?: cur.tokenState.usedTokensToday,
                    requestsToday = ledger?.requestsToday ?: cur.tokenState.requestsToday,
                    isServerVerified = true,
                    verificationSource = cur.tokenState.verificationSource.ifBlank { "${provider.shortTitle} usage" },
                    rateLimitRemainingTokens = usage.rateLimitRemainingTokens ?: cur.tokenState.rateLimitRemainingTokens,
                    rateLimitTotalTokens = usage.rateLimitTotalTokens ?: cur.tokenState.rateLimitTotalTokens,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    syncError = null
                )
            } else {
                cur.tokenState
            }
            cur.copy(
                tokenLedgers = updatedLedgers,
                tokenState = nextTokenState
            )
        }
    }

    private fun syncContextTokens(forceFetchLimits: Boolean = false) {
        val state = _uiState.value
        if (!state.isAiReady) return
        val provider = state.activeProvider
        val profile = state.activeProfile
        val activeModelId = profile.effectiveModelId
        val matchedModel = state.syncedModels.firstOrNull { it.modelId.equals(activeModelId, ignoreCase = true) }
        val knownInput = if (forceFetchLimits) 0 else (matchedModel?.inputTokenLimit ?: state.tokenState.inputTokenLimit)
        val knownOutput = if (forceFetchLimits) 0 else (matchedModel?.outputTokenLimit ?: state.tokenState.outputTokenLimit)
        val apiKey = profile.trimmedKey
        val baseUrl = profile.effectiveBaseUrl
        val historySnapshot = state.messages
        val attachedPage = state.attachedPdfPage
        val prevMeasured = if (state.messages.isEmpty()) 0 else state.tokenState.currentContextTokens
        val prevRlRem = state.tokenState.rateLimitRemainingTokens
        val prevRlTotal = state.tokenState.rateLimitTotalTokens

        activeTokenSyncJob?.cancel()
        activeTokenSyncJob = viewModelScope.launch {
            _uiState.update { cur ->
                cur.copy(
                    tokenState = buildBaselineTokenState(
                        provider,
                        activeModelId,
                        cur.syncedModels,
                        cur.tokenLedgers,
                        cur.tokenState
                    ).copy(isSyncingTokens = true, syncError = null)
                )
            }
            try {
                val pageBmp = attachedPage?.let { renderPdfPageForAi(it) }
                val res = withTimeout(18_000L) {
                    when (provider) {
                        AiProvider.GOOGLE_ACCOUNT -> FirebaseAiClient.countDocsContextTokens(
                            modelId = activeModelId,
                            history = historySnapshot,
                            knownInputLimit = knownInput,
                            knownOutputLimit = knownOutput,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = attachedPage
                        )
                        AiProvider.GOOGLE_API_KEY -> GeminiApiClient.syncExactContextTokens(
                            apiKey = apiKey,
                            modelId = activeModelId,
                            history = historySnapshot,
                            knownInputLimit = knownInput,
                            knownOutputLimit = knownOutput,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = attachedPage
                        )
                        AiProvider.ANTHROPIC -> AnthropicApiClient.syncExactContextTokens(
                            apiKey = apiKey,
                            modelId = activeModelId,
                            history = historySnapshot,
                            knownInputLimit = knownInput,
                            knownOutputLimit = knownOutput,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = attachedPage
                        )
                        AiProvider.OPENAI,
                        AiProvider.OPENROUTER,
                        AiProvider.GROQ,
                        AiProvider.DEEPSEEK,
                        AiProvider.CUSTOM_OPENAI -> OpenAiCompatApiClient.syncContextTokens(
                            provider = provider,
                            apiKey = apiKey,
                            customBaseUrl = baseUrl,
                            modelId = activeModelId,
                            knownInputLimit = knownInput,
                            knownOutputLimit = knownOutput,
                            lastMeasuredContextTokens = prevMeasured,
                            lastRateLimitRemaining = prevRlRem,
                            lastRateLimitTotal = prevRlTotal
                        )
                    }
                }
                res.onSuccess { exact ->
                    _uiState.update { cur ->
                        if (cur.activeProvider != provider) return@update cur
                        val updatedSynced = if (exact.inputTokenLimit > 0) {
                            cur.syncedModels.map { m ->
                                if (m.modelId.equals(exact.modelId, ignoreCase = true) && m.inputTokenLimit != exact.inputTokenLimit) {
                                    m.copy(
                                        inputTokenLimit = exact.inputTokenLimit,
                                        outputTokenLimit = exact.outputTokenLimit.takeIf { it > 0 } ?: m.outputTokenLimit
                                    )
                                } else m
                            }
                        } else cur.syncedModels
                        if (updatedSynced !== cur.syncedModels) {
                            keyPrefs.saveSyncedModels(
                                provider,
                                updatedSynced,
                                cur.syncedModelsUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                        }
                        val ledger = cur.tokenLedgers[activeModelId.lowercase()]
                        cur.copy(
                            syncedModels = updatedSynced,
                            tokenState = cur.tokenState.copy(
                                modelId = exact.modelId,
                                inputTokenLimit = exact.inputTokenLimit.takeIf { it > 0 } ?: cur.tokenState.inputTokenLimit,
                                outputTokenLimit = exact.outputTokenLimit.takeIf { it > 0 } ?: cur.tokenState.outputTokenLimit,
                                currentContextTokens = exact.currentContextTokens,
                                usedTokensToday = ledger?.usedTokensToday ?: cur.tokenState.usedTokensToday,
                                requestsToday = ledger?.requestsToday ?: cur.tokenState.requestsToday,
                                isSyncingTokens = false,
                                isServerVerified = exact.isServerVerified,
                                verificationSource = exact.verificationSource,
                                rateLimitRemainingTokens = exact.rateLimitRemainingTokens ?: cur.tokenState.rateLimitRemainingTokens,
                                rateLimitTotalTokens = exact.rateLimitTotalTokens ?: cur.tokenState.rateLimitTotalTokens,
                                accountBalanceLabel = exact.accountBalanceLabel ?: cur.tokenState.accountBalanceLabel,
                                lastSyncedAtMillis = exact.syncedAtMillis,
                                syncError = null
                            )
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { cur ->
                        if (cur.activeProvider != provider) return@update cur
                        cur.copy(
                            tokenState = cur.tokenState.copy(
                                isSyncingTokens = false,
                                syncError = err.localizedMessage?.take(120)
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { cur ->
                    if (cur.activeProvider != provider) return@update cur
                    cur.copy(
                        tokenState = cur.tokenState.copy(
                            isSyncingTokens = false,
                            syncError = (e.localizedMessage ?: "Không thể đồng bộ token").take(120)
                        )
                    )
                }
            }
        }
    }

    fun clearApiKey() {
        val provider = _uiState.value.activeProvider
        if (provider == AiProvider.GOOGLE_ACCOUNT) return
        val cleared = keyPrefs.clearProviderKey(provider)
        updateProfileInState(cleared, persist = false)
        _uiState.update {
            it.copy(
                keyStatusMessage = "Đã xóa cấu hình Key của ${provider.shortTitle} khỏi thiết bị.",
                keyStatusSuccess = false
            )
        }
    }

    fun saveAndVerifyApiKey(
        rawKey: String,
        modelId: String,
        customBaseUrl: String = _uiState.value.activeProfile.baseUrl
    ) {
        val provider = _uiState.value.activeProvider.let {
            if (it == AiProvider.GOOGLE_ACCOUNT) AiProvider.GOOGLE_API_KEY else it
        }
        if (provider != _uiState.value.activeProvider) {
            selectProvider(provider)
        }
        val trimmedKey = AiResponseParser.sanitizeApiKey(rawKey)
        val normalizedUrl = if (provider.supportsCustomBaseUrl) {
            AiProvider.normalizeBaseUrl(customBaseUrl)
        } else {
            provider.defaultBaseUrl
        }
        val current = _uiState.value.providerProfiles[provider] ?: keyPrefs.loadProfile(provider)
        val existingCustomModels = current.syncedModels.filter { it.source == "custom" }
        val nextProfile = current.copy(
            apiKey = trimmedKey,
            baseUrl = normalizedUrl,
            modelId = modelId.trim().ifEmpty { current.effectiveModelId }
        )
        keyPrefs.saveActiveProvider(provider)
        updateProfileInState(nextProfile, persist = true)

        if (provider == AiProvider.CUSTOM_OPENAI && normalizedUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    keyStatusMessage = "Vui lòng nhập Base URL hợp lệ (vd: https://api.together.xyz/v1).",
                    keyStatusSuccess = false
                )
            }
            return
        }
        if (provider.requiresApiKey && trimmedKey.isEmpty()) {
            _uiState.update {
                it.copy(
                    keyStatusMessage = "Vui lòng nhập API Key ${provider.shortTitle}.",
                    keyStatusSuccess = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isVerifyingKey = true,
                isSyncingModels = true,
                keyStatusMessage = "Đang kiểm tra Key & đồng bộ danh sách mô hình ${provider.shortTitle}...",
                keyStatusSuccess = true,
                modelSyncStatusMessage = "Đang đồng bộ danh sách mô hình từ ${provider.shortTitle}...",
                modelSyncSuccess = true
            )
        }
        viewModelScope.launch {
            try {
                val res = withTimeout(30_000L) {
                    when (provider) {
                        AiProvider.GOOGLE_API_KEY -> GeminiApiClient.verifyApiKey(
                            apiKey = trimmedKey,
                            preferredModelId = nextProfile.effectiveModelId
                        )
                        AiProvider.ANTHROPIC -> AnthropicApiClient.syncAccountModels(apiKey = trimmedKey)
                        else -> OpenAiCompatApiClient.syncAccountModels(
                            provider = provider,
                            apiKey = trimmedKey,
                            customBaseUrl = normalizedUrl
                        )
                    }
                }
                res.onSuccess { syncResult ->
                    val merged = AiResponseParser.sortSyncedModels(existingCustomModels + syncResult.models, provider)
                    val now = System.currentTimeMillis()
                    keyPrefs.saveSyncedModels(provider, merged, now)

                    val currentId = nextProfile.effectiveModelId
                    val isCurrentValid = syncResult.models.any { it.modelId.equals(currentId, ignoreCase = true) } ||
                        existingCustomModels.any { it.modelId.equals(currentId, ignoreCase = true) }
                    val autoPicked = if (!isCurrentValid) AiResponseParser.pickBestChatModel(syncResult.models, provider) else null
                    val finalModelId = autoPicked?.modelId ?: currentId
                    val verifiedProfile = nextProfile.copy(
                        modelId = finalModelId,
                        syncedModels = merged,
                        syncedModelsUpdatedAt = now
                    )
                    updateProfileInState(verifiedProfile, persist = true)

                    val finalMessage = buildString {
                        append(syncResult.message)
                        if (autoPicked != null) {
                            append(" Đã tự động chọn '${autoPicked.modelId}'.")
                        }
                    }

                    _uiState.update { cur ->
                        cur.copy(
                            isVerifyingKey = false,
                            isSyncingModels = false,
                            tokenState = buildBaselineTokenState(provider, finalModelId, merged, cur.tokenLedgers),
                            keyStatusMessage = finalMessage,
                            keyStatusSuccess = true,
                            modelSyncStatusMessage = finalMessage,
                            modelSyncSuccess = true
                        )
                    }
                    syncContextTokens()
                }.onFailure { err ->
                    Log.e(TAG, "API key verification failed for $provider", err)
                    val errText = err.localizedMessage ?: "Kiểm tra API Key ${provider.shortTitle} thất bại."
                    _uiState.update {
                        it.copy(
                            isVerifyingKey = false,
                            isSyncingModels = false,
                            keyStatusMessage = errText,
                            keyStatusSuccess = false,
                            modelSyncStatusMessage = errText,
                            modelSyncSuccess = false
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val timeoutMsg = "Quá thời gian kiểm tra kết nối (30 giây). Hãy kiểm tra mạng."
                _uiState.update {
                    it.copy(
                        isVerifyingKey = false,
                        isSyncingModels = false,
                        keyStatusMessage = timeoutMsg,
                        keyStatusSuccess = false,
                        modelSyncStatusMessage = timeoutMsg,
                        modelSyncSuccess = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected API key verification failure", e)
                val msg = "Lỗi kiểm tra API Key: ${e.localizedMessage ?: e.javaClass.simpleName}"
                _uiState.update {
                    it.copy(
                        isVerifyingKey = false,
                        isSyncingModels = false,
                        keyStatusMessage = msg,
                        keyStatusSuccess = false,
                        modelSyncStatusMessage = msg,
                        modelSyncSuccess = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isVerifyingKey = false, isSyncingModels = false) }
            }
        }
    }

    // ================= DOCUMENT Q&A (388-PAGE DPAD PDF) =================

    fun setAttachedPdfPage(page1Based: Int?) {
        val safe = page1Based?.coerceIn(1, PDF_TOTAL_PAGES)
        _uiState.update { it.copy(attachedPdfPage = safe) }
        syncContextTokens()
    }

    fun prepareAskAboutPdfPage(page1Based: Int) {
        val safe = page1Based.coerceIn(1, PDF_TOTAL_PAGES)
        _uiState.update {
            it.copy(
                activeSubTab = if (it.isAiReady) AiSubTab.DOCS_QA else AiSubTab.API_KEY,
                attachedPdfPage = safe
            )
        }
        syncContextTokens()
    }

    fun clearChatHistory() {
        activeDocsJob?.cancel()
        activeDocsJob = null
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isAskingDocs = false,
                streamingDocsAnswer = "",
                docsStatusText = "",
                docsElapsedSeconds = 0,
                lastFailedDocsQuestion = null,
                attachedPdfPage = null
            )
        }
        syncContextTokens()
    }

    fun cancelDocumentQuestion() {
        val job = activeDocsJob ?: return
        activeDocsJob = null
        job.cancel()
        _uiState.update {
            it.copy(
                isAskingDocs = false,
                streamingDocsAnswer = "",
                docsStatusText = "",
                docsElapsedSeconds = 0
            )
        }
    }

    fun retryLastDocumentQuestion() {
        val q = _uiState.value.lastFailedDocsQuestion?.trim().orEmpty()
        if (q.isNotEmpty()) {
            askDocumentQuestion(q)
        }
    }

    fun askDocumentQuestion(rawQuestion: String) {
        val question = rawQuestion.trim()
        val snapshot = _uiState.value
        if (question.isEmpty() || snapshot.isAskingDocs) return

        if (!snapshot.isAiReady) {
            _uiState.update {
                it.copy(
                    activeSubTab = AiSubTab.API_KEY,
                    authStatusMessage = "Hãy đăng nhập Google để dùng Gemini không cần Key (hoặc nhập API Key bên dưới).",
                    authStatusSuccess = false
                )
            }
            return
        }

        val provider = snapshot.activeProvider
        val profile = snapshot.activeProfile
        val explicitPage = snapshot.attachedPdfPage ?: extractMentionedPage(question)
        val userMsg = AiChatMessage(
            id = "u_${msgSeq++}",
            isUser = true,
            text = question,
            attachedPdfPage = explicitPage
        )
        val historySnapshot = snapshot.messages
        val providerName = provider.shortTitle

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isAskingDocs = true,
                streamingDocsAnswer = "",
                docsStatusText = if (explicitPage != null) {
                    "Đang đọc ảnh Trang $explicitPage & kết nối $providerName..."
                } else {
                    "Đang kết nối $providerName (${profile.effectiveModelId})..."
                },
                docsElapsedSeconds = 0,
                lastFailedDocsQuestion = null,
                attachedPdfPage = null
            )
        }

        activeDocsJob?.cancel()
        activeDocsJob = viewModelScope.launch {
            val timerJob = launch {
                var sec = 0
                while (isActive) {
                    delay(1_000L)
                    sec++
                    _uiState.update { it.copy(docsElapsedSeconds = sec) }
                }
            }
            try {
                val pageBmp = explicitPage?.let { renderPdfPageForAi(it) }
                val res = withTimeout(90_000L) {
                    val partialCallback: (String) -> Unit = { partial ->
                        _uiState.update {
                            it.copy(
                                streamingDocsAnswer = partial,
                                docsStatusText = "$providerName đang trả lời..."
                            )
                        }
                    }
                    when (provider) {
                        AiProvider.GOOGLE_ACCOUNT -> FirebaseAiClient.askDocumentAssistant(
                            modelId = profile.effectiveModelId,
                            question = question,
                            history = historySnapshot,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = explicitPage,
                            onPartialAnswer = partialCallback
                        )
                        AiProvider.GOOGLE_API_KEY -> GeminiApiClient.askDocumentAssistant(
                            apiKey = profile.trimmedKey,
                            modelId = profile.effectiveModelId,
                            question = question,
                            history = historySnapshot,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = explicitPage,
                            onAutoResolvedModel = { bestModel, synced ->
                                applyAutoResolvedModel(bestModel, synced)
                                _uiState.update {
                                    it.copy(docsStatusText = "Đã chuyển sang ${bestModel.modelId}, đang trả lời...")
                                }
                            }
                        )
                        AiProvider.ANTHROPIC -> AnthropicApiClient.askDocumentAssistant(
                            apiKey = profile.trimmedKey,
                            modelId = profile.effectiveModelId,
                            question = question,
                            history = historySnapshot,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = explicitPage,
                            onPartialAnswer = partialCallback
                        )
                        AiProvider.OPENAI,
                        AiProvider.OPENROUTER,
                        AiProvider.GROQ,
                        AiProvider.DEEPSEEK,
                        AiProvider.CUSTOM_OPENAI -> OpenAiCompatApiClient.askDocumentAssistant(
                            provider = provider,
                            apiKey = profile.trimmedKey,
                            customBaseUrl = profile.effectiveBaseUrl,
                            modelId = profile.effectiveModelId,
                            question = question,
                            history = historySnapshot,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = explicitPage,
                            onPartialAnswer = partialCallback
                        )
                    }
                }
                res.onSuccess { reply ->
                    val aiMsg = AiChatMessage(
                        id = "a_${msgSeq++}",
                        isUser = false,
                        text = reply.answer,
                        suggestedPages = reply.suggestedPages
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + aiMsg,
                            lastFailedDocsQuestion = null
                        )
                    }
                    val resolvedModel = _uiState.value.activeProfile.effectiveModelId
                    recordServerTokenUsage(provider, resolvedModel, reply.tokenUsage, updateContextFromTurn = true)
                    syncContextTokens()
                }.onFailure { err ->
                    Log.e(TAG, "Document Q&A failed", err)
                    val errMsg = AiChatMessage(
                        id = "e_${msgSeq++}",
                        isUser = false,
                        text = err.localizedMessage ?: "Không thể nhận phản hồi từ AI.",
                        isError = true
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + errMsg,
                            lastFailedDocsQuestion = question
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Document Q&A timed out after 90s", e)
                val errMsg = AiChatMessage(
                    id = "e_${msgSeq++}",
                    isUser = false,
                    text = "AI không phản hồi sau 90 giây. Hãy kiểm tra kết nối mạng, bấm Thử lại hoặc chọn Gemini 3.5 Flash-Lite trong mục Tài khoản / Key.",
                    isError = true
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + errMsg,
                        lastFailedDocsQuestion = question
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected Document Q&A error", e)
                val errMsg = AiChatMessage(
                    id = "e_${msgSeq++}",
                    isUser = false,
                    text = "Lỗi không mong muốn khi gọi AI (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Vui lòng thử lại."}",
                    isError = true
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + errMsg,
                        lastFailedDocsQuestion = question
                    )
                }
            } finally {
                timerJob.cancel()
                _uiState.update {
                    it.copy(
                        isAskingDocs = false,
                        streamingDocsAnswer = "",
                        docsStatusText = "",
                        docsElapsedSeconds = 0
                    )
                }
            }
        }
    }

    private suspend fun renderPdfPageForAi(page1Based: Int): Bitmap? {
        return runCatching {
            val r = pdfRenderer ?: PdfBookRenderer(appContext).also { pdfRenderer = it }
            r.open()
            r.renderPage((page1Based - 1).coerceIn(0, PDF_TOTAL_PAGES - 1), targetWidthPx = 1024)
        }.getOrNull()
    }

    private fun extractMentionedPage(question: String): Int? {
        val regex = Regex("""(?i)\b(?:trang|tr\.?)\s*(\d{1,3})\b""")
        val num = regex.find(question)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return num?.takeIf { it in 1..PDF_TOTAL_PAGES }
    }

    // ================= PROBLEM PHOTO -> AI 3D/2D DRAWING =================

    fun updateProblemNote(note: String) {
        _uiState.update { it.copy(problemNote = note, problemError = null) }
    }

    fun clearProblemInput() {
        activeProblemJob?.cancel()
        activeProblemJob = null
        _uiState.update {
            it.copy(
                problemBitmap = null,
                problemNote = "",
                isAnalyzingProblem = false,
                problemStatusText = "",
                problemElapsedSeconds = 0,
                problemError = null,
                latestDrawingPlan = null
            )
        }
    }

    fun cancelProblemAnalysis() {
        val job = activeProblemJob ?: return
        activeProblemJob = null
        job.cancel()
        _uiState.update {
            it.copy(
                isAnalyzingProblem = false,
                problemStatusText = "",
                problemElapsedSeconds = 0,
                problemError = "Đã hủy yêu cầu phân tích đề bài."
            )
        }
    }

    fun setProblemImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val decoded = decodeSafeBitmapFromUri(uri)
            if (decoded != null) {
                _uiState.update {
                    it.copy(
                        problemBitmap = decoded,
                        problemError = null,
                        activeSubTab = AiSubTab.PROBLEM_DRAW
                    )
                }
            } else {
                _uiState.update {
                    it.copy(problemError = "Không thể đọc ảnh đề bài đã chọn. Vui lòng thử ảnh khác.")
                }
            }
        }
    }

    fun analyzeProblemForDrawing() {
        val state = _uiState.value
        if (state.isAnalyzingProblem) return
        if (!state.isAiReady) {
            _uiState.update {
                it.copy(
                    activeSubTab = AiSubTab.API_KEY,
                    authStatusMessage = "Hãy đăng nhập Google để dùng Gemini quét đề bài (hoặc nhập API Key bên dưới).",
                    authStatusSuccess = false
                )
            }
            return
        }
        if (state.problemBitmap == null && state.problemNote.isBlank()) {
            _uiState.update {
                it.copy(problemError = "Hãy chụp ảnh đề bài, chọn ảnh từ thư viện hoặc nhập nội dung đề bài.")
            }
            return
        }

        val provider = state.activeProvider
        val profile = state.activeProfile
        val providerName = provider.shortTitle

        _uiState.update {
            it.copy(
                isAnalyzingProblem = true,
                problemError = null,
                latestDrawingPlan = null,
                problemElapsedSeconds = 0,
                problemStatusText = if (state.problemBitmap != null) {
                    "Đang gửi ảnh đề bài tới $providerName (${profile.effectiveModelId})..."
                } else {
                    "Đang gửi đề bài tới $providerName (${profile.effectiveModelId})..."
                }
            )
        }

        activeProblemJob?.cancel()
        activeProblemJob = viewModelScope.launch {
            val timerJob = launch {
                var sec = 0
                while (isActive) {
                    delay(1_000L)
                    sec++
                    _uiState.update { it.copy(problemElapsedSeconds = sec) }
                }
            }
            try {
                val res = withTimeout(90_000L) {
                    val progressCallback: (Int) -> Unit = { chars ->
                        _uiState.update {
                            it.copy(problemStatusText = "$providerName đang dựng cấu trúc hình... ($chars ký tự)")
                        }
                    }
                    when (provider) {
                        AiProvider.GOOGLE_ACCOUNT -> FirebaseAiClient.analyzeProblemToDrawing(
                            modelId = profile.effectiveModelId,
                            problemBitmap = state.problemBitmap,
                            problemNote = state.problemNote,
                            onProgressChars = progressCallback
                        )
                        AiProvider.GOOGLE_API_KEY -> GeminiApiClient.analyzeProblemToDrawing(
                            apiKey = profile.trimmedKey,
                            modelId = profile.effectiveModelId,
                            problemBitmap = state.problemBitmap,
                            problemNote = state.problemNote,
                            onAutoResolvedModel = { bestModel, synced ->
                                applyAutoResolvedModel(bestModel, synced)
                                _uiState.update {
                                    it.copy(problemStatusText = "Đã chuyển sang ${bestModel.modelId}, đang dựng hình...")
                                }
                            }
                        )
                        AiProvider.ANTHROPIC -> AnthropicApiClient.analyzeProblemToDrawing(
                            apiKey = profile.trimmedKey,
                            modelId = profile.effectiveModelId,
                            problemBitmap = state.problemBitmap,
                            problemNote = state.problemNote,
                            onProgressChars = progressCallback
                        )
                        AiProvider.OPENAI,
                        AiProvider.OPENROUTER,
                        AiProvider.GROQ,
                        AiProvider.DEEPSEEK,
                        AiProvider.CUSTOM_OPENAI -> OpenAiCompatApiClient.analyzeProblemToDrawing(
                            provider = provider,
                            apiKey = profile.trimmedKey,
                            customBaseUrl = profile.effectiveBaseUrl,
                            modelId = profile.effectiveModelId,
                            problemBitmap = state.problemBitmap,
                            problemNote = state.problemNote,
                            onProgressChars = progressCallback
                        )
                    }
                }
                res.onSuccess { plan ->
                    _uiState.update {
                        it.copy(
                            latestDrawingPlan = plan,
                            problemError = null
                        )
                    }
                    val resolvedModel = _uiState.value.activeProfile.effectiveModelId
                    recordServerTokenUsage(provider, resolvedModel, plan.tokenUsage, updateContextFromTurn = false)
                }.onFailure { err ->
                    Log.e(TAG, "Problem drawing analysis failed", err)
                    _uiState.update {
                        it.copy(
                            problemError = err.localizedMessage ?: "Không thể phân tích đề bài."
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Problem drawing analysis timed out after 90s", e)
                _uiState.update {
                    it.copy(
                        problemError = "AI không phản hồi sau 90 giây. Hãy kiểm tra mạng, thử chụp ảnh gần/rõ hơn hoặc chuyển sang Gemini 3.5 Flash-Lite."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected problem drawing failure", e)
                _uiState.update {
                    it.copy(
                        problemError = "Lỗi khi phân tích đề bài (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Vui lòng thử lại."}"
                    )
                }
            } finally {
                timerJob.cancel()
                _uiState.update {
                    it.copy(
                        isAnalyzingProblem = false,
                        problemStatusText = "",
                        problemElapsedSeconds = 0
                    )
                }
            }
        }
    }

    private suspend fun decodeSafeBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxEdge / sample > 1600) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null

            if (raw.width > 1280 || raw.height > 1280) {
                val scale = 1280f / maxOf(raw.width, raw.height).toFloat()
                val m = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            } else {
                raw
            }
        }.getOrNull()
    }
}
