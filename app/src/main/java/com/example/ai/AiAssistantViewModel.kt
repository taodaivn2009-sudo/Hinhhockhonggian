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
    // Google Sign-In + keyless Firebase AI Logic (Google Gemini)
    val googleUser: GoogleUserProfile? = null,
    val isSigningIn: Boolean = false,
    val isTestingGoogleAi: Boolean = false,
    val authStatusMessage: String? = null,
    val authStatusSuccess: Boolean = false,
    val authToast: AuthToastEvent? = null,

    // Optional manual API Key fallback (BYOK) + synced account models
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

    // Live batch request testing across all models
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
    val isSignedInWithGoogle: Boolean get() = googleUser != null
    val isAiReady: Boolean get() = isSignedInWithGoogle || keyConfig.hasKey
    val canSwitchProvider: Boolean get() = isSignedInWithGoogle && keyConfig.hasKey

    /**
     * Resolves which backend handles AI requests:
     * - If both Google Sign-In and a personal API key are available, respects user's providerMode choice.
     * - Otherwise automatically uses whichever is configured.
     */
    val useGoogleGeminiActive: Boolean
        get() = when {
            isSignedInWithGoogle && keyConfig.hasKey ->
                keyConfig.providerMode != AiProviderMode.PERSONAL_API_KEY
            isSignedInWithGoogle -> true
            else -> false
        }

    val activeProviderShortLabel: String
        get() = when {
            useGoogleGeminiActive -> "Google Gemini"
            keyConfig.hasKey -> "API Key riêng"
            else -> "Chưa kết nối"
        }

    val activeProviderLabel: String
        get() = when {
            useGoogleGeminiActive -> "Google Gemini · Không cần API Key"
            keyConfig.hasKey -> "API Key riêng (${keyConfig.maskedKey})"
            else -> "Chưa đăng nhập Google hoặc nhập Key"
        }

    val displayModels: List<AiSyncedModel>
        get() {
            val base = syncedModels.ifEmpty { AiModelPreset.entries.map { it.toSyncedModel() } }
            val currentId = keyConfig.effectiveModelId
            return if (base.any { it.modelId.equals(currentId, ignoreCase = true) }) {
                base
            } else {
                listOf(
                    AiSyncedModel(
                        modelId = currentId,
                        displayName = AiModelPreset.fromId(currentId)?.title
                            ?: AiResponseParser.formatModelDisplayName(currentId),
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

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private fun createInitialState(): AiAssistantUiState {
        val loadedKey = keyPrefs.load()
        val cachedSyncedModels = keyPrefs.loadSyncedModels()
        val cachedUpdatedAt = keyPrefs.loadSyncedModelsUpdatedAt()
        val ledgers = keyPrefs.loadTokenLedgers()
        val existingGoogleUser = GoogleAuthManager.currentUserProfile(appContext)
        val ready = existingGoogleUser != null || loadedKey.hasKey
        val activeModelId = loadedKey.effectiveModelId
        val matchedModel = cachedSyncedModels.firstOrNull { it.modelId.equals(activeModelId, ignoreCase = true) }
        val activeLedger = ledgers[activeModelId.lowercase()]
        val initialTokenState = AiContextTokenState(
            modelId = activeModelId,
            inputTokenLimit = matchedModel?.inputTokenLimit ?: 0,
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
            googleUser = existingGoogleUser,
            keyConfig = loadedKey,
            syncedModels = cachedSyncedModels,
            syncedModelsUpdatedAt = cachedUpdatedAt,
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

    fun selectProviderMode(mode: AiProviderMode) {
        val current = _uiState.value.keyConfig
        val next = current.copy(providerMode = mode)
        keyPrefs.save(next)
        _uiState.update { it.copy(keyConfig = next) }
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

    // ================= MANUAL API KEY FALLBACK =================

    fun saveApiKey(rawKey: String, modelId: String = _uiState.value.keyConfig.effectiveModelId) {
        val cleanKey = AiResponseParser.sanitizeApiKey(rawKey)
        val current = _uiState.value.keyConfig
        val next = current.copy(
            apiKey = cleanKey,
            modelId = modelId.trim(),
            providerMode = if (cleanKey.isNotEmpty()) AiProviderMode.PERSONAL_API_KEY else AiProviderMode.GOOGLE_GEMINI
        )
        keyPrefs.save(next)
        _uiState.update {
            it.copy(
                keyConfig = next,
                keyStatusMessage = if (next.hasKey) "Đã lưu và bật API Key riêng (${next.maskedKey}). Đang đồng bộ mô hình..." else "Đã xóa API Key.",
                keyStatusSuccess = next.hasKey
            )
        }
        if (cleanKey.isNotEmpty()) {
            syncAccountModels(cleanKey)
        }
    }

    fun selectModel(modelId: String) {
        val current = _uiState.value.keyConfig
        val nextId = modelId.trim().ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId }
        val next = current.copy(modelId = nextId)
        keyPrefs.save(next)
        _uiState.update { cur ->
            cur.copy(
                keyConfig = next,
                tokenState = buildBaselineTokenState(nextId, cur.syncedModels, cur.tokenLedgers)
            )
        }
        syncContextTokens()
    }

    fun addCustomModel(rawModelId: String) {
        val cleanId = rawModelId.trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
            .trim()
        if (cleanId.isEmpty()) {
            _uiState.update {
                it.copy(
                    modelSyncStatusMessage = "Hãy nhập mã mô hình hợp lệ (ví dụ: gemini-3.8-flash).",
                    modelSyncSuccess = false
                )
            }
            return
        }
        val updatedModels = keyPrefs.addCustomModel(cleanId)
        val nextConfig = _uiState.value.keyConfig.copy(modelId = cleanId)
        keyPrefs.save(nextConfig)
        _uiState.update { cur ->
            cur.copy(
                keyConfig = nextConfig,
                syncedModels = updatedModels,
                syncedModelsUpdatedAt = System.currentTimeMillis(),
                tokenState = buildBaselineTokenState(cleanId, updatedModels, cur.tokenLedgers),
                modelSyncStatusMessage = "Đã thêm và chọn mô hình '$cleanId'.",
                modelSyncSuccess = true
            )
        }
        syncContextTokens(forceFetchLimits = true)
    }

    fun removeCustomModel(modelId: String) {
        val updatedModels = keyPrefs.removeCustomModel(modelId)
        val current = _uiState.value.keyConfig
        val nextModelId = if (current.effectiveModelId.equals(modelId, ignoreCase = true)) {
            updatedModels.firstOrNull()?.modelId ?: AiModelPreset.GEMINI_3_8_FLASH.modelId
        } else {
            current.effectiveModelId
        }
        val nextConfig = current.copy(modelId = nextModelId)
        keyPrefs.save(nextConfig)
        _uiState.update {
            it.copy(
                keyConfig = nextConfig,
                syncedModels = updatedModels,
                modelSyncStatusMessage = "Đã xóa mô hình tùy chỉnh '$modelId'.",
                modelSyncSuccess = true
            )
        }
    }

    private fun applyAutoResolvedModel(bestModel: AiSyncedModel, syncedFromServer: List<AiSyncedModel>) {
        val existingCustom = _uiState.value.syncedModels.filter { it.source == "custom" }
        val merged = AiResponseParser.sortSyncedModels(existingCustom + syncedFromServer)
        val now = System.currentTimeMillis()
        keyPrefs.saveSyncedModels(merged, now)
        val nextConfig = _uiState.value.keyConfig.copy(
            modelId = bestModel.modelId,
            providerMode = AiProviderMode.PERSONAL_API_KEY
        )
        keyPrefs.save(nextConfig)
        _uiState.update { cur ->
            cur.copy(
                keyConfig = nextConfig,
                syncedModels = merged,
                syncedModelsUpdatedAt = now,
                modelSyncStatusMessage = "Đã tự động đồng bộ ${merged.size} mô hình và chuyển sang '${bestModel.modelId}'.",
                modelSyncSuccess = true
            )
        }
    }

    /**
     * Synchronizes live models available in the user's account:
     * - If an API key is available (either saved or typed in draft), fetches the full catalog via REST
     *   (supports both `AIza...` AI Studio keys and `AQ....` Vertex AI Express keys), automatically
     *   activates `PERSONAL_API_KEY`, and selects a valid model from the account if the previous model isn't available.
     * - Else if signed in with Google (Firebase AI Logic), probes live availability on `Firebase.ai`.
     */
    fun syncAccountModels(overrideApiKey: String? = null) {
        if (_uiState.value.isSyncingModels) return
        val state = _uiState.value
        val cleanedOverride = overrideApiKey?.let { AiResponseParser.sanitizeApiKey(it) }.orEmpty()
        val candidateKey = cleanedOverride.ifEmpty { state.keyConfig.trimmedKey }
        val existingCustomModels = state.syncedModels.filter { it.source == "custom" }
        val customIds = existingCustomModels.map { it.modelId }

        if (candidateKey.isEmpty() && !state.isSignedInWithGoogle) {
            _uiState.update {
                it.copy(
                    modelSyncStatusMessage = "Hãy đăng nhập Google hoặc nhập API Key (AIza... hoặc AQ....) rồi bấm Đồng bộ mô hình.",
                    modelSyncSuccess = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSyncingModels = true,
                modelSyncStatusMessage = if (candidateKey.isNotEmpty()) {
                    "Đang đồng bộ danh sách mô hình từ tài khoản API Key..."
                } else {
                    "Đang kiểm tra các mô hình khả dụng trên Google Gemini..."
                },
                modelSyncSuccess = true
            )
        }

        activeSyncJob?.cancel()
        activeSyncJob = viewModelScope.launch {
            try {
                val syncResult = withTimeout(30_000L) {
                    if (candidateKey.isNotEmpty()) {
                        GeminiApiClient.syncAccountModels(
                            apiKey = candidateKey,
                            preferredModelId = state.keyConfig.effectiveModelId,
                            extraModelIds = customIds
                        )
                    } else {
                        FirebaseAiClient.probeAvailableModels(extraModelIds = customIds)
                    }
                }

                syncResult.onSuccess { result ->
                    val merged = AiResponseParser.sortSyncedModels(existingCustomModels + result.models)
                    val now = System.currentTimeMillis()
                    keyPrefs.saveSyncedModels(merged, now)

                    val curConfig = _uiState.value.keyConfig
                    val currentId = curConfig.effectiveModelId
                    val isCurrentValid = result.models.any { it.modelId.equals(currentId, ignoreCase = true) } ||
                        existingCustomModels.any { it.modelId.equals(currentId, ignoreCase = true) }
                    val autoPicked = if (!isCurrentValid) AiResponseParser.pickBestChatModel(result.models) else null
                    val finalModelId = autoPicked?.modelId ?: currentId

                    val nextConfig = if (candidateKey.isNotEmpty()) {
                        curConfig.copy(
                            apiKey = candidateKey,
                            modelId = finalModelId,
                            providerMode = AiProviderMode.PERSONAL_API_KEY
                        )
                    } else {
                        curConfig.copy(modelId = finalModelId)
                    }
                    keyPrefs.save(nextConfig)

                    val statusMsg = buildString {
                        append(result.message)
                        if (autoPicked != null) {
                            append(" Đã tự động chọn '${autoPicked.modelId}'.")
                        }
                    }

                    _uiState.update { cur ->
                        cur.copy(
                            keyConfig = nextConfig,
                            syncedModels = merged,
                            syncedModelsUpdatedAt = now,
                            tokenState = buildBaselineTokenState(finalModelId, merged, cur.tokenLedgers),
                            keyStatusMessage = if (candidateKey.isNotEmpty()) statusMsg else cur.keyStatusMessage,
                            keyStatusSuccess = if (candidateKey.isNotEmpty()) true else cur.keyStatusSuccess,
                            modelSyncStatusMessage = statusMsg,
                            modelSyncSuccess = true
                        )
                    }
                    syncContextTokens()
                }.onFailure { err ->
                    Log.e(TAG, "Account model sync failed", err)
                    val errText = err.localizedMessage ?: "Đồng bộ danh sách mô hình thất bại."
                    _uiState.update {
                        it.copy(
                            keyStatusMessage = if (candidateKey.isNotEmpty()) errText else it.keyStatusMessage,
                            keyStatusSuccess = if (candidateKey.isNotEmpty()) false else it.keyStatusSuccess,
                            modelSyncStatusMessage = errText,
                            modelSyncSuccess = false
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val timeoutMsg = "Quá thời gian đồng bộ mô hình (30 giây). Hãy kiểm tra kết nối mạng."
                _uiState.update {
                    it.copy(
                        keyStatusMessage = if (candidateKey.isNotEmpty()) timeoutMsg else it.keyStatusMessage,
                        keyStatusSuccess = if (candidateKey.isNotEmpty()) false else it.keyStatusSuccess,
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
                        keyStatusMessage = if (candidateKey.isNotEmpty()) msg else it.keyStatusMessage,
                        keyStatusSuccess = if (candidateKey.isNotEmpty()) false else it.keyStatusSuccess,
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
     * Sends a real lightweight `generateContent` request to EVERY model in the catalog (3 concurrent workers
     * to stay friendly with rate limits), streaming per-model status + latency live into the UI.
     * If no account models are synced yet and an API key is available, automatically syncs first.
     */
    fun probeAllModels(overrideApiKey: String? = null) {
        if (_uiState.value.isProbingAllModels) {
            cancelProbeAllModels()
            return
        }
        val state = _uiState.value
        val cleanedOverride = overrideApiKey?.let { AiResponseParser.sanitizeApiKey(it) }.orEmpty()
        val candidateKey = cleanedOverride.ifEmpty { state.keyConfig.trimmedKey }
        val useApiKey = candidateKey.isNotEmpty() && (!state.useGoogleGeminiActive || cleanedOverride.isNotEmpty())

        if (!useApiKey && !state.isSignedInWithGoogle) {
            _uiState.update {
                it.copy(
                    modelProbeSummary = "Hãy đăng nhập Google hoặc nhập API Key rồi bấm Test tất cả mô hình."
                )
            }
            return
        }

        activeProbeAllJob?.cancel()
        activeProbeAllJob = viewModelScope.launch {
            try {
                // Ensure the saved key is persisted & activated if the user typed a fresh key draft
                if (useApiKey && cleanedOverride.isNotEmpty() && cleanedOverride != state.keyConfig.trimmedKey) {
                    val updatedCfg = state.keyConfig.copy(
                        apiKey = cleanedOverride,
                        providerMode = AiProviderMode.PERSONAL_API_KEY
                    )
                    keyPrefs.save(updatedCfg)
                    _uiState.update { it.copy(keyConfig = updatedCfg) }
                }

                // If user hasn't synced their account catalog yet, auto-sync first so we test ALL their real models
                if (useApiKey && _uiState.value.syncedModels.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProbingAllModels = true,
                            probeProgressDone = 0,
                            probeProgressTotal = 0,
                            modelProbeResults = emptyMap(),
                            modelProbeSummary = "Đang tải danh sách mô hình từ tài khoản trước khi test..."
                        )
                    }
                    val existingCustom = _uiState.value.syncedModels.filter { it.source == "custom" }
                    val syncRes = runCatching {
                        withTimeout(25_000L) {
                            GeminiApiClient.syncAccountModels(
                                apiKey = candidateKey,
                                preferredModelId = _uiState.value.keyConfig.effectiveModelId,
                                extraModelIds = existingCustom.map { it.modelId }
                            )
                        }
                    }.getOrNull()
                    syncRes?.onSuccess { result ->
                        val merged = AiResponseParser.sortSyncedModels(existingCustom + result.models)
                        val now = System.currentTimeMillis()
                        keyPrefs.saveSyncedModels(merged, now)
                        _uiState.update {
                            it.copy(
                                syncedModels = merged,
                                syncedModelsUpdatedAt = now,
                                modelSyncStatusMessage = result.message,
                                modelSyncSuccess = true
                            )
                        }
                    }
                }

                val targetModels = _uiState.value.displayModels
                if (targetModels.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProbingAllModels = false,
                            modelProbeSummary = "Không có mô hình nào để kiểm tra."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isProbingAllModels = true,
                        probeProgressDone = 0,
                        probeProgressTotal = targetModels.size,
                        modelProbeResults = emptyMap(),
                        modelProbeSummary = "Đang gửi request kiểm tra ${targetModels.size} mô hình..."
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
                                val probeRes = if (useApiKey) {
                                    GeminiApiClient.probeModelGenerateContent(candidateKey, model.modelId)
                                } else {
                                    FirebaseAiClient.probeModelGenerateContent(model.modelId)
                                }
                                probeRes.tokenUsage?.let { usage ->
                                    recordServerTokenUsage(model.modelId, usage, updateContextFromTurn = false)
                                }
                                val done = completedCounter.incrementAndGet()
                                _uiState.update { cur ->
                                    val updatedMap = cur.modelProbeResults + (keyLower to probeRes)
                                    val okCount = updatedMap.values.count { it.status == AiModelProbeStatus.WORKING }
                                    cur.copy(
                                        probeProgressDone = done,
                                        modelProbeResults = updatedMap,
                                        modelProbeSummary = "Đang kiểm tra $done / $total mô hình · Hoạt động: $okCount"
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

                // If currently selected model failed/quota-limited while working models exist, auto-switch to the best working model
                val curCfg = _uiState.value.keyConfig
                val currentWorking = workingList.any { it.modelId.equals(curCfg.effectiveModelId, ignoreCase = true) }
                var autoSwitchedText = ""
                if (!currentWorking && workingList.isNotEmpty()) {
                    val workingIdSet = workingList.map { it.modelId.lowercase() }.toSet()
                    val workingModels = targetModels.filter { it.modelId.lowercase() in workingIdSet }
                    val bestWorking = AiResponseParser.pickBestChatModel(workingModels)
                        ?: workingModels.minByOrNull { m ->
                            workingList.firstOrNull { it.modelId.equals(m.modelId, ignoreCase = true) }?.latencyMs
                                ?: Long.MAX_VALUE
                        }
                    if (bestWorking != null) {
                        val nextCfg = curCfg.copy(
                            modelId = bestWorking.modelId,
                            providerMode = if (useApiKey) AiProviderMode.PERSONAL_API_KEY else curCfg.providerMode
                        )
                        keyPrefs.save(nextCfg)
                        val bestLatency = workingList.firstOrNull { it.modelId.equals(bestWorking.modelId, ignoreCase = true) }?.latencyMs ?: 0L
                        autoSwitchedText = " Đã tự chọn '${bestWorking.modelId}' (${bestLatency}ms)."
                        _uiState.update { it.copy(keyConfig = nextCfg) }
                    }
                }

                val summary = buildString {
                    append("Hoàn tất kiểm tra $total mô hình: ")
                    append("${workingList.size} hoạt động")
                    if (quotaCount > 0) append(" · $quotaCount hết lượt (429)")
                    if (failedCount > 0) append(" · $failedCount lỗi")
                    append(".")
                    append(autoSwitchedText)
                }
                _uiState.update { it.copy(modelProbeSummary = summary) }
            } catch (e: CancellationException) {
                val curResults = _uiState.value.modelProbeResults
                    .filterValues { it.status != AiModelProbeStatus.TESTING }
                val okCount = curResults.values.count { it.status == AiModelProbeStatus.WORKING }
                _uiState.update {
                    it.copy(
                        modelProbeResults = curResults,
                        modelProbeSummary = "Đã dừng kiểm tra (${curResults.size}/${it.probeProgressTotal} mô hình · Hoạt động: $okCount)."
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

    /**
     * Public 1-tap refresh that synchronizes exact model `inputTokenLimit` + live `countTokens`
     * from Google's server (100% free; does not consume generation quota).
     */
    fun refreshTokenSync() {
        syncContextTokens(forceFetchLimits = true)
    }

    private fun buildBaselineTokenState(
        modelId: String,
        models: List<AiSyncedModel>,
        ledgers: Map<String, AiModelTokenLedger>,
        prev: AiContextTokenState? = null
    ): AiContextTokenState {
        val cleanId = modelId.trim().ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId }
        val matched = models.firstOrNull { it.modelId.equals(cleanId, ignoreCase = true) }
        val ledger = ledgers[cleanId.lowercase()]
        val sameModelPrev = prev?.takeIf { it.modelId.equals(cleanId, ignoreCase = true) }
        return AiContextTokenState(
            modelId = cleanId,
            inputTokenLimit = matched?.inputTokenLimit?.takeIf { it > 0 } ?: sameModelPrev?.inputTokenLimit ?: 0,
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
            lastSyncedAtMillis = sameModelPrev?.lastSyncedAtMillis ?: 0L,
            syncError = sameModelPrev?.syncError
        )
    }

    private fun recordServerTokenUsage(
        modelId: String,
        usage: AiTokenUsage?,
        updateContextFromTurn: Boolean
    ) {
        if (usage == null || !usage.hasData) return
        val updatedLedgers = keyPrefs.recordTokenUsage(modelId, usage)
        _uiState.update { cur ->
            val activeId = cur.keyConfig.effectiveModelId
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
        val activeModelId = state.keyConfig.effectiveModelId
        val matchedModel = state.syncedModels.firstOrNull { it.modelId.equals(activeModelId, ignoreCase = true) }
        val knownInput = if (forceFetchLimits) 0 else (matchedModel?.inputTokenLimit ?: state.tokenState.inputTokenLimit)
        val knownOutput = if (forceFetchLimits) 0 else (matchedModel?.outputTokenLimit ?: state.tokenState.outputTokenLimit)
        val useGoogleGemini = state.useGoogleGeminiActive
        val apiKey = state.keyConfig.trimmedKey
        val historySnapshot = state.messages
        val attachedPage = state.attachedPdfPage

        activeTokenSyncJob?.cancel()
        activeTokenSyncJob = viewModelScope.launch {
            _uiState.update { cur ->
                cur.copy(
                    tokenState = buildBaselineTokenState(
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
                    if (useGoogleGemini) {
                        FirebaseAiClient.countDocsContextTokens(
                            modelId = activeModelId,
                            history = historySnapshot,
                            knownInputLimit = knownInput,
                            knownOutputLimit = knownOutput,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = attachedPage
                        )
                    } else {
                        GeminiApiClient.syncExactContextTokens(
                            apiKey = apiKey,
                            modelId = activeModelId,
                            history = historySnapshot,
                            knownInputLimit = knownInput,
                            knownOutputLimit = knownOutput,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = attachedPage
                        )
                    }
                }
                res.onSuccess { exact ->
                    _uiState.update { cur ->
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
                            keyPrefs.saveSyncedModels(updatedSynced, cur.syncedModelsUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
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
                                isServerVerified = true,
                                lastSyncedAtMillis = exact.syncedAtMillis,
                                syncError = null
                            )
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { cur ->
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
        keyPrefs.clearKey()
        _uiState.update {
            it.copy(
                keyConfig = it.keyConfig.copy(apiKey = "", providerMode = AiProviderMode.GOOGLE_GEMINI),
                keyStatusMessage = "Đã xóa API Key khỏi thiết bị.",
                keyStatusSuccess = false
            )
        }
    }

    fun saveAndVerifyApiKey(rawKey: String, modelId: String) {
        val trimmed = AiResponseParser.sanitizeApiKey(rawKey)
        val current = _uiState.value.keyConfig
        val existingCustomModels = _uiState.value.syncedModels.filter { it.source == "custom" }
        val next = current.copy(
            apiKey = trimmed,
            modelId = modelId.trim(),
            providerMode = if (trimmed.isNotEmpty()) AiProviderMode.PERSONAL_API_KEY else AiProviderMode.GOOGLE_GEMINI
        )
        keyPrefs.save(next)
        _uiState.update {
            it.copy(
                keyConfig = next,
                isVerifyingKey = true,
                isSyncingModels = true,
                keyStatusMessage = "Đang kiểm tra API Key & đồng bộ danh sách mô hình...",
                keyStatusSuccess = true,
                modelSyncStatusMessage = "Đang đồng bộ danh sách mô hình từ API Key...",
                modelSyncSuccess = true
            )
        }
        viewModelScope.launch {
            try {
                val res = withTimeout(30_000L) {
                    GeminiApiClient.verifyApiKey(
                        apiKey = trimmed,
                        preferredModelId = next.effectiveModelId
                    )
                }
                res.onSuccess { syncResult ->
                    val merged = AiResponseParser.sortSyncedModels(existingCustomModels + syncResult.models)
                    val now = System.currentTimeMillis()
                    keyPrefs.saveSyncedModels(merged, now)

                    val currentId = next.effectiveModelId
                    val isCurrentValid = syncResult.models.any { it.modelId.equals(currentId, ignoreCase = true) } ||
                        existingCustomModels.any { it.modelId.equals(currentId, ignoreCase = true) }
                    val autoPicked = if (!isCurrentValid) AiResponseParser.pickBestChatModel(syncResult.models) else null
                    val finalModelId = autoPicked?.modelId ?: currentId
                    val verifiedConfig = next.copy(
                        modelId = finalModelId,
                        providerMode = AiProviderMode.PERSONAL_API_KEY
                    )
                    keyPrefs.save(verifiedConfig)

                    val finalMessage = buildString {
                        append(syncResult.message)
                        if (autoPicked != null) {
                            append(" Đã tự động chọn '${autoPicked.modelId}' và chuyển sang dùng API Key riêng.")
                        } else {
                            append(" Đã chuyển sang dùng API Key riêng.")
                        }
                    }

                    _uiState.update { cur ->
                        cur.copy(
                            keyConfig = verifiedConfig,
                            isVerifyingKey = false,
                            isSyncingModels = false,
                            syncedModels = merged,
                            syncedModelsUpdatedAt = now,
                            tokenState = buildBaselineTokenState(finalModelId, merged, cur.tokenLedgers),
                            keyStatusMessage = finalMessage,
                            keyStatusSuccess = true,
                            modelSyncStatusMessage = finalMessage,
                            modelSyncSuccess = true
                        )
                    }
                    syncContextTokens()
                }.onFailure { err ->
                    Log.e(TAG, "API key verification failed", err)
                    val errText = err.localizedMessage ?: "Kiểm tra API Key thất bại."
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

        val useGoogleGemini = snapshot.useGoogleGeminiActive
        val cfg = snapshot.keyConfig
        val explicitPage = snapshot.attachedPdfPage ?: extractMentionedPage(question)
        val userMsg = AiChatMessage(
            id = "u_${msgSeq++}",
            isUser = true,
            text = question,
            attachedPdfPage = explicitPage
        )
        val historySnapshot = snapshot.messages
        val providerName = if (useGoogleGemini) "Google Gemini" else "API Key riêng"

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isAskingDocs = true,
                streamingDocsAnswer = "",
                docsStatusText = if (explicitPage != null) {
                    "Đang đọc ảnh Trang $explicitPage & kết nối $providerName..."
                } else {
                    "Đang kết nối $providerName (${cfg.effectiveModelId})..."
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
                    if (useGoogleGemini) {
                        FirebaseAiClient.askDocumentAssistant(
                            modelId = cfg.effectiveModelId,
                            question = question,
                            history = historySnapshot,
                            attachedPageBitmap = pageBmp,
                            attachedPageNumber = explicitPage,
                            onPartialAnswer = { partial ->
                                _uiState.update {
                                    it.copy(
                                        streamingDocsAnswer = partial,
                                        docsStatusText = "AI đang trả lời..."
                                    )
                                }
                            }
                        )
                    } else {
                        GeminiApiClient.askDocumentAssistant(
                            apiKey = cfg.trimmedKey,
                            modelId = cfg.effectiveModelId,
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
                    val resolvedModel = _uiState.value.keyConfig.effectiveModelId
                    recordServerTokenUsage(resolvedModel, reply.tokenUsage, updateContextFromTurn = true)
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

        val useGoogleGemini = state.useGoogleGeminiActive
        val cfg = state.keyConfig
        val providerName = if (useGoogleGemini) "Google Gemini" else "API Key riêng"

        _uiState.update {
            it.copy(
                isAnalyzingProblem = true,
                problemError = null,
                latestDrawingPlan = null,
                problemElapsedSeconds = 0,
                problemStatusText = if (state.problemBitmap != null) {
                    "Đang gửi ảnh đề bài tới $providerName (${cfg.effectiveModelId})..."
                } else {
                    "Đang gửi đề bài tới $providerName (${cfg.effectiveModelId})..."
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
                    if (useGoogleGemini) {
                        FirebaseAiClient.analyzeProblemToDrawing(
                            modelId = cfg.effectiveModelId,
                            problemBitmap = state.problemBitmap,
                            problemNote = state.problemNote,
                            onProgressChars = { chars ->
                                _uiState.update {
                                    it.copy(problemStatusText = "AI đang dựng cấu trúc hình... ($chars ký tự)")
                                }
                            }
                        )
                    } else {
                        GeminiApiClient.analyzeProblemToDrawing(
                            apiKey = cfg.trimmedKey,
                            modelId = cfg.effectiveModelId,
                            problemBitmap = state.problemBitmap,
                            problemNote = state.problemNote,
                            onAutoResolvedModel = { bestModel, synced ->
                                applyAutoResolvedModel(bestModel, synced)
                                _uiState.update {
                                    it.copy(problemStatusText = "Đã chuyển sang ${bestModel.modelId}, đang dựng hình...")
                                }
                            }
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
                    val resolvedModel = _uiState.value.keyConfig.effectiveModelId
                    recordServerTokenUsage(resolvedModel, plan.tokenUsage, updateContextFromTurn = false)
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
