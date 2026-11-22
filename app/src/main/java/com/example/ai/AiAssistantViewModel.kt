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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

    // Optional manual API Key fallback (BYOK)
    val keyConfig: AiKeyConfig = AiKeyConfig(),
    val activeSubTab: AiSubTab = AiSubTab.DOCS_QA,
    val isVerifyingKey: Boolean = false,
    val keyStatusMessage: String? = null,
    val keyStatusSuccess: Boolean = false,

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
    private var attemptedSilentAuth = false

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private fun createInitialState(): AiAssistantUiState {
        val loadedKey = keyPrefs.load()
        val existingGoogleUser = GoogleAuthManager.currentUserProfile(appContext)
        val ready = existingGoogleUser != null || loadedKey.hasKey
        return AiAssistantUiState(
            googleUser = existingGoogleUser,
            keyConfig = loadedKey,
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
    }

    override fun onCleared() {
        super.onCleared()
        toastAutoDismissJob?.cancel()
        activeDocsJob?.cancel()
        activeProblemJob?.cancel()
        activeTestJob?.cancel()
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
        val current = _uiState.value.keyConfig
        val next = current.copy(apiKey = rawKey.trim(), modelId = modelId.trim())
        keyPrefs.save(next)
        _uiState.update {
            it.copy(
                keyConfig = next,
                keyStatusMessage = if (next.hasKey) "Đã lưu API Key (${next.maskedKey})." else "Đã xóa API Key.",
                keyStatusSuccess = next.hasKey
            )
        }
    }

    fun selectModel(modelId: String) {
        val current = _uiState.value.keyConfig
        val next = current.copy(modelId = modelId.trim().ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId })
        keyPrefs.save(next)
        _uiState.update { it.copy(keyConfig = next) }
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
        val trimmed = rawKey.trim()
        val current = _uiState.value.keyConfig
        val next = current.copy(apiKey = trimmed, modelId = modelId.trim())
        keyPrefs.save(next)
        _uiState.update {
            it.copy(
                keyConfig = next,
                isVerifyingKey = true,
                keyStatusMessage = null
            )
        }
        viewModelScope.launch {
            try {
                val res = withTimeout(25_000L) { GeminiApiClient.verifyApiKey(trimmed) }
                res.onSuccess { msg ->
                    _uiState.update {
                        it.copy(
                            isVerifyingKey = false,
                            keyStatusMessage = msg,
                            keyStatusSuccess = true
                        )
                    }
                }.onFailure { err ->
                    Log.e(TAG, "API key verification failed", err)
                    _uiState.update {
                        it.copy(
                            isVerifyingKey = false,
                            keyStatusMessage = err.localizedMessage ?: "Kiểm tra API Key thất bại.",
                            keyStatusSuccess = false
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isVerifyingKey = false,
                        keyStatusMessage = "Quá thời gian kiểm tra kết nối. Hãy kiểm tra mạng.",
                        keyStatusSuccess = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected API key verification failure", e)
                _uiState.update {
                    it.copy(
                        isVerifyingKey = false,
                        keyStatusMessage = "Lỗi kiểm tra API Key: ${e.localizedMessage ?: e.javaClass.simpleName}",
                        keyStatusSuccess = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isVerifyingKey = false) }
            }
        }
    }

    // ================= DOCUMENT Q&A (388-PAGE DPAD PDF) =================

    fun setAttachedPdfPage(page1Based: Int?) {
        val safe = page1Based?.coerceIn(1, PDF_TOTAL_PAGES)
        _uiState.update { it.copy(attachedPdfPage = safe) }
    }

    fun prepareAskAboutPdfPage(page1Based: Int) {
        val safe = page1Based.coerceIn(1, PDF_TOTAL_PAGES)
        _uiState.update {
            it.copy(
                activeSubTab = if (it.isAiReady) AiSubTab.DOCS_QA else AiSubTab.API_KEY,
                attachedPdfPage = safe
            )
        }
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
                            attachedPageNumber = explicitPage
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
                            problemNote = state.problemNote
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
