package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val FIREBASE_AI_TAG = "FirebaseAiClient"

/**
 * Keyless Gemini client powered by Firebase AI Logic (`Firebase.ai`) + App Check.
 * Activated automatically once the user signs in with Google — zero API key entry needed!
 */
object FirebaseAiClient {

    private val docsModelCache = ConcurrentHashMap<String, GenerativeModel>()
    private val problemModelCache = ConcurrentHashMap<String, GenerativeModel>()

    private val pageSuggestionSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "page" to Schema.integer(description = "Số trang PDF từ 1 đến 388"),
                "title" to Schema.string(description = "Tiêu đề ngắn gọn của mục hoặc công thức"),
                "reason" to Schema.string(description = "Lý do ngắn gọn tại sao trang này liên quan")
            ),
            optionalProperties = listOf("reason")
        )
    }

    private val docsReplySchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "answer" to Schema.string(description = "Nội dung trả lời chi tiết bằng tiếng Việt"),
                "suggestedPages" to Schema.array(
                    items = pageSuggestionSchema,
                    description = "Danh sách tối đa 4 trang PDF 388 trang liên quan nhất"
                )
            ),
            optionalProperties = listOf("suggestedPages")
        )
    }

    private fun getDocsModel(modelId: String): GenerativeModel {
        val safeModel = sanitizeModelId(modelId)
        return docsModelCache.computeIfAbsent(safeModel) { id ->
            Firebase.ai.generativeModel(
                modelName = id,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = docsReplySchema
                    temperature = 0.3f
                },
                systemInstruction = content {
                    text(GeminiApiClient.buildDocsSystemPrompt())
                }
            )
        }
    }

    private fun getProblemModel(modelId: String): GenerativeModel {
        val safeModel = sanitizeModelId(modelId)
        return problemModelCache.computeIfAbsent(safeModel) { id ->
            Firebase.ai.generativeModel(
                modelName = id,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.2f
                },
                systemInstruction = content {
                    text(GeminiApiClient.buildProblemDrawSystemPrompt())
                }
            )
        }
    }

    private fun sanitizeModelId(raw: String): String {
        val cleaned = raw.trim().removePrefix("models/")
        return cleaned.ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId }
    }

    suspend fun askDocumentAssistant(
        modelId: String,
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null
    ): Result<AiDocsReply> = withContext(Dispatchers.IO) {
        try {
            val model = getDocsModel(modelId)
            val chatHistory = history
                .filter { !it.isError }
                .takeLast(6)
                .map { msg ->
                    content(role = if (msg.isUser) "user" else "model") {
                        text(msg.text)
                    }
                }

            val userPrompt = content(role = "user") {
                if (attachedPageBitmap != null) {
                    image(GeminiApiClient.downscaleBitmap(attachedPageBitmap, maxDim = 1400))
                    val pageNote = attachedPageNumber?.let { " (Đính kèm hình ảnh Trang $it trong tài liệu PDF)" } ?: ""
                    text("$question$pageNote")
                } else {
                    text(question)
                }
            }

            val response = if (chatHistory.isNotEmpty()) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessage(userPrompt)
            } else {
                model.generateContent(userPrompt)
            }

            val rawText = response.text?.trim().orEmpty()
            if (rawText.isEmpty()) {
                throw IllegalStateException("Google Gemini trả về nội dung trống. Vui lòng thử lại.")
            }
            Result.success(AiResponseParser.parseDocsReply(rawText))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(FIREBASE_AI_TAG, "Firebase AI document query failed", e)
            Result.failure(IllegalStateException(formatFirebaseAiError(e), e))
        }
    }

    suspend fun analyzeProblemToDrawing(
        modelId: String,
        problemBitmap: Bitmap?,
        problemNote: String
    ): Result<AiDrawingPlan> = withContext(Dispatchers.IO) {
        if (problemBitmap == null && problemNote.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Hãy chụp/chọn ảnh đề bài hoặc nhập nội dung đề bài.")
            )
        }
        try {
            val model = getProblemModel(modelId)
            val promptLine = buildString {
                if (problemBitmap != null) {
                    append("Hãy đọc thật chính xác đề bài toán trong ảnh đính kèm và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                } else {
                    append("Hãy phân tích đề bài toán sau và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                }
                if (problemNote.isNotBlank()) {
                    append("\nNội dung / ghi chú thêm từ người dùng:\n")
                    append(problemNote.trim())
                }
            }

            val input = content(role = "user") {
                if (problemBitmap != null) {
                    image(GeminiApiClient.downscaleBitmap(problemBitmap, maxDim = 1600))
                }
                text(promptLine)
            }

            val response = model.generateContent(input)
            val rawText = response.text?.trim().orEmpty()
            if (rawText.isEmpty()) {
                throw IllegalStateException("Google Gemini trả về dữ liệu trống. Vui lòng thử lại.")
            }
            val plan = AiResponseParser.parseDrawingPlan(rawText)
                ?: throw IllegalStateException("AI không trả về dữ liệu dựng hình hợp lệ. Hãy thử chụp ảnh rõ nét hơn hoặc mô tả thêm đề bài.")
            Result.success(plan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(FIREBASE_AI_TAG, "Firebase AI problem drawing failed", e)
            Result.failure(IllegalStateException(formatFirebaseAiError(e), e))
        }
    }

    private fun formatFirebaseAiError(e: Exception): String {
        val msg = e.localizedMessage?.trim().orEmpty()
        return when {
            msg.contains("403", ignoreCase = true) || msg.contains("App Check", ignoreCase = true) ->
                "Không thể xác thực kết nối Google Gemini (App Check/403). Hãy kiểm tra kết nối mạng hoặc thử lại. ($msg)"
            msg.contains("429", ignoreCase = true) || msg.contains("quota", ignoreCase = true) ->
                "Google Gemini đang quá tải tạm thời. Vui lòng đợi ít phút rồi thử lại."
            msg.contains("404", ignoreCase = true) || msg.contains("not found", ignoreCase = true) ->
                "Mô hình đang chọn chưa khả dụng. Hãy chọn Gemini 3.8 Flash trong mục Tài khoản / Key."
            msg.isNotEmpty() -> msg
            else -> "Lỗi kết nối Google Gemini. Vui lòng kiểm tra mạng và thử lại."
        }
    }
}
