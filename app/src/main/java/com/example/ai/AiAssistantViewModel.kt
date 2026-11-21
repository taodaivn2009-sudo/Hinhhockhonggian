package com.example.ai

import android.app.Application
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "AiAssistantViewModel"

data class AiAssistantUiState(
    val keyConfig: AiKeyConfig = AiKeyConfig(),
    val activeSubTab: AiSubTab = AiSubTab.DOCS_QA,
    val isVerifyingKey: Boolean = false,
    val keyStatusMessage: String? = null,
    val keyStatusSuccess: Boolean = false,

    // Document Q&A chat state
    val messages: List<AiChatMessage> = emptyList(),
    val isAskingDocs: Boolean = false,
    val attachedPdfPage: Int? = null,

    // Problem Photo -> Draw 3D/2D state
    val problemBitmap: Bitmap? = null,
    val problemNote: String = "",
    val isAnalyzingProblem: Boolean = false,
    val problemError: String? = null,
    val latestDrawingPlan: AiDrawingPlan? = null
)

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val keyPrefs = AiKeyPreferences(appContext)
    private var pdfRenderer: PdfBookRenderer? = null
    private var msgSeq = 1

    private val _uiState = MutableStateFlow(
        AiAssistantUiState(
            keyConfig = keyPrefs.load(),
            activeSubTab = if (keyPrefs.load().hasKey) AiSubTab.DOCS_QA else AiSubTab.API_KEY
        )
    )
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        pdfRenderer = null
    }

    fun selectSubTab(tab: AiSubTab) {
        _uiState.update { it.copy(activeSubTab = tab) }
    }

    fun saveApiKey(rawKey: String, modelId: String = _uiState.value.keyConfig.effectiveModelId) {
        val next = AiKeyConfig(apiKey = rawKey.trim(), modelId = modelId.trim())
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
                keyConfig = it.keyConfig.copy(apiKey = ""),
                keyStatusMessage = "Đã xóa API Key khỏi thiết bị.",
                keyStatusSuccess = false
            )
        }
    }

    fun saveAndVerifyApiKey(rawKey: String, modelId: String) {
        val trimmed = rawKey.trim()
        val next = AiKeyConfig(apiKey = trimmed, modelId = modelId.trim())
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
                activeSubTab = if (it.keyConfig.hasKey) AiSubTab.DOCS_QA else AiSubTab.API_KEY,
                attachedPdfPage = safe
            )
        }
    }

    fun clearChatHistory() {
        _uiState.update { it.copy(messages = emptyList(), attachedPdfPage = null) }
    }

    fun askDocumentQuestion(rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isEmpty() || _uiState.value.isAskingDocs) return
        val cfg = _uiState.value.keyConfig
        if (!cfg.hasKey) {
            _uiState.update {
                it.copy(
                    activeSubTab = AiSubTab.API_KEY,
                    keyStatusMessage = "Vui lòng nhập API Key trước khi hỏi AI.",
                    keyStatusSuccess = false
                )
            }
            return
        }

        // Detect explicit "trang 121" mention if user didn't manually attach a page
        val explicitPage = _uiState.value.attachedPdfPage ?: extractMentionedPage(question)
        val userMsg = AiChatMessage(
            id = "u_${msgSeq++}",
            isUser = true,
            text = question,
            attachedPdfPage = explicitPage
        )
        val historySnapshot = _uiState.value.messages
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isAskingDocs = true,
                attachedPdfPage = null
            )
        }

        viewModelScope.launch {
            try {
                val pageBmp = explicitPage?.let { renderPdfPageForAi(it) }
                val res = withTimeout(65_000L) {
                    GeminiApiClient.askDocumentAssistant(
                        apiKey = cfg.trimmedKey,
                        modelId = cfg.effectiveModelId,
                        question = question,
                        history = historySnapshot,
                        attachedPageBitmap = pageBmp,
                        attachedPageNumber = explicitPage
                    )
                }
                res.onSuccess { reply ->
                    val aiMsg = AiChatMessage(
                        id = "a_${msgSeq++}",
                        isUser = false,
                        text = reply.answer,
                        suggestedPages = reply.suggestedPages
                    )
                    _uiState.update { it.copy(messages = it.messages + aiMsg, isAskingDocs = false) }
                }.onFailure { err ->
                    Log.e(TAG, "Document Q&A failed", err)
                    val errMsg = AiChatMessage(
                        id = "e_${msgSeq++}",
                        isUser = false,
                        text = err.localizedMessage ?: "Không thể nhận phản hồi từ AI.",
                        isError = true
                    )
                    _uiState.update { it.copy(messages = it.messages + errMsg, isAskingDocs = false) }
                }
            } catch (e: TimeoutCancellationException) {
                val errMsg = AiChatMessage(
                    id = "e_${msgSeq++}",
                    isUser = false,
                    text = "Yêu cầu quá thời gian chờ (65 giây). Vui lòng thử lại.",
                    isError = true
                )
                _uiState.update { it.copy(messages = it.messages + errMsg, isAskingDocs = false) }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private suspend fun renderPdfPageForAi(page1Based: Int): Bitmap? {
        return runCatching {
            val r = pdfRenderer ?: PdfBookRenderer(appContext).also { pdfRenderer = it }
            r.open()
            r.renderPage((page1Based - 1).coerceIn(0, PDF_TOTAL_PAGES - 1), targetWidthPx = 1120)
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
        _uiState.update {
            it.copy(
                problemBitmap = null,
                problemNote = "",
                problemError = null,
                latestDrawingPlan = null
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
        val cfg = state.keyConfig
        if (!cfg.hasKey) {
            _uiState.update {
                it.copy(
                    activeSubTab = AiSubTab.API_KEY,
                    keyStatusMessage = "Vui lòng nhập API Key trước khi quét ảnh đề bài.",
                    keyStatusSuccess = false
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

        _uiState.update { it.copy(isAnalyzingProblem = true, problemError = null) }
        viewModelScope.launch {
            try {
                val res = withTimeout(65_000L) {
                    GeminiApiClient.analyzeProblemToDrawing(
                        apiKey = cfg.trimmedKey,
                        modelId = cfg.effectiveModelId,
                        problemBitmap = state.problemBitmap,
                        problemNote = state.problemNote
                    )
                }
                res.onSuccess { plan ->
                    _uiState.update {
                        it.copy(
                            isAnalyzingProblem = false,
                            latestDrawingPlan = plan,
                            problemError = null
                        )
                    }
                }.onFailure { err ->
                    Log.e(TAG, "Problem drawing analysis failed", err)
                    _uiState.update {
                        it.copy(
                            isAnalyzingProblem = false,
                            problemError = err.localizedMessage ?: "Không thể phân tích đề bài."
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isAnalyzingProblem = false,
                        problemError = "Quá thời gian phân tích đề bài (65 giây). Hãy thử lại."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private suspend fun decodeSafeBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxEdge / sample > 2048) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null

            // Normalize orientation if camera saved landscape/rotated JPEG via basic check
            if (raw.width > 1800 || raw.height > 1800) {
                val scale = 1800f / maxOf(raw.width, raw.height).toFloat()
                val m = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            } else {
                raw
            }
        }.getOrNull()
    }
}
