package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val FIREBASE_AI_TAG = "FirebaseAiClient"

/**
 * Keyless Gemini client powered by Firebase AI Logic (`Firebase.ai`) + App Check.
 * Uses streaming (`generateContentStream`) for responsive Time-To-First-Token and reliable
 * feedback over mobile networks.
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

    /**
     * Live 2-step diagnostic for Google Gemini via Firebase AI Logic:
     * 1) Verifies Firebase App Check attestation token retrieval
     * 2) Sends a minimal prompt to the selected Gemini model
     */
    suspend fun testConnection(modelId: String): Result<String> = withContext(Dispatchers.IO) {
        val safeModel = sanitizeModelId(modelId)
        try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Firebase chưa được khởi tạo trong bản APK này.")
                )
            }
        } catch (e: Throwable) {
            return@withContext Result.failure(
                IllegalStateException("Bản APK chưa có cấu hình Firebase hợp lệ (${e.localizedMessage ?: e.javaClass.simpleName}).")
            )
        }

        // Step 1: Verify App Check token (with bounded timeout so it never hangs forever)
        val appCheckProvider = activeAppCheckProviderLabel()
        try {
            withTimeout(15_000L) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
        } catch (e: TimeoutCancellationException) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Xác thực App Check ($appCheckProvider) quá thời gian (15 giây). Hãy kiểm tra kết nối mạng hoặc thử lại."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(FIREBASE_AI_TAG, "App Check token retrieval failed ($appCheckProvider)", e)
            return@withContext Result.failure(
                IllegalStateException(
                    "Lỗi xác thực App Check ($appCheckProvider): ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            )
        }

        // Step 2: Minimal live ping to Gemini model
        try {
            val pingModel = Firebase.ai.generativeModel(modelName = safeModel)
            val response = withTimeout(25_000L) {
                pingModel.generateContent("Trả lời đúng 1 chữ: OK")
            }
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Google Gemini ($safeModel) phản hồi rỗng. Hãy thử đổi sang Gemini 3.8 Flash."))
            } else {
                Result.success("Kết nối Google Gemini ($safeModel) thành công! App Check: $appCheckProvider.")
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(
                IllegalStateException("Google Gemini ($safeModel) không phản hồi sau 25 giây. Hãy kiểm tra mạng hoặc đổi sang Gemini 3.5 Flash-Lite.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(FIREBASE_AI_TAG, "Firebase AI ping failed for $safeModel", e)
            Result.failure(IllegalStateException(formatFirebaseAiError(e, safeModel), e))
        }
    }

    /**
     * Probes live availability of models on Firebase AI Logic (`Firebase.ai`) using lightweight
     * `countTokens("ping")` calls in parallel. Note: Firebase AI Logic SDK does not expose a
     * server-side listModels() endpoint; full catalog discovery comes from Gemini Developer API (`AIza...`).
     */
    suspend fun probeAvailableModels(
        extraModelIds: List<String> = emptyList()
    ): Result<AiModelSyncResult> = withContext(Dispatchers.IO) {
        try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Firebase chưa được khởi tạo trong bản APK này.")
                )
            }
        } catch (e: Throwable) {
            return@withContext Result.failure(
                IllegalStateException("Bản APK chưa có cấu hình Firebase hợp lệ (${e.localizedMessage ?: e.javaClass.simpleName}).")
            )
        }

        val appCheckProvider = activeAppCheckProviderLabel()
        try {
            withTimeout(15_000L) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
        } catch (e: TimeoutCancellationException) {
            return@withContext Result.failure(
                IllegalStateException("Xác thực App Check ($appCheckProvider) quá thời gian (15 giây). Hãy kiểm tra mạng và thử lại.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(FIREBASE_AI_TAG, "App Check token retrieval failed during model probe", e)
            return@withContext Result.failure(
                IllegalStateException("Lỗi xác thực App Check ($appCheckProvider): ${e.localizedMessage ?: e.javaClass.simpleName}")
            )
        }

        val candidates = LinkedHashMap<String, AiSyncedModel>()
        AiModelPreset.entries.forEach { preset ->
            candidates[preset.modelId.lowercase()] = preset.toSyncedModel(source = "firebase")
        }
        extraModelIds.forEach { raw ->
            val cid = sanitizeModelId(raw)
            if (cid.isNotEmpty()) {
                candidates.putIfAbsent(
                    cid.lowercase(),
                    AiSyncedModel(
                        modelId = cid,
                        displayName = AiResponseParser.formatModelDisplayName(cid),
                        description = "Mô hình tùy chỉnh đã xác thực",
                        source = "custom"
                    )
                )
            }
        }

        var firstError: Throwable? = null
        val available = coroutineScope {
            candidates.values.map { candidate ->
                async(Dispatchers.IO) {
                    try {
                        val model = Firebase.ai.generativeModel(modelName = candidate.modelId)
                        withTimeout(12_000L) {
                            model.countTokens("ping")
                        }
                        candidate
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val msg = e.localizedMessage.orEmpty()
                        if (msg.contains("429") || msg.contains("quota", ignoreCase = true)) {
                            candidate
                        } else {
                            if (firstError == null) firstError = e
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (available.isNotEmpty()) {
            val sorted = AiResponseParser.sortSyncedModels(available)
            Result.success(
                AiModelSyncResult(
                    models = sorted,
                    sourceLabel = "Google Gemini (Firebase)",
                    message = "Đã kiểm tra ${sorted.size} mô hình hoạt động trên Google Gemini. Để tự động tải toàn bộ các mô hình khác trong tài khoản Google AI Studio của bạn, hãy dán API Key (dạng 'AIza...' hoặc 'AQ....') bên dưới rồi bấm Đồng bộ mô hình (hoặc thêm trực tiếp mã mô hình tùy chỉnh)."
                )
            )
        } else {
            val err = firstError
            Result.failure(
                IllegalStateException(
                    if (err != null) formatFirebaseAiError(err, AiModelPreset.GEMINI_3_8_FLASH.modelId)
                    else "Không thể kiểm tra danh sách mô hình trên Google Gemini. Hãy kiểm tra kết nối mạng."
                )
            )
        }
    }

    /**
     * Sends a real lightweight `generateContent` request via Firebase AI Logic to check if [modelId]
     * actively responds on Google Gemini.
     */
    suspend fun probeModelGenerateContent(modelId: String): AiModelProbeResult = withContext(Dispatchers.IO) {
        val safeModel = sanitizeModelId(modelId)
        val startMs = System.currentTimeMillis()
        try {
            val model = Firebase.ai.generativeModel(modelName = safeModel)
            val reply = withTimeout(14_000L) {
                model.generateContent("1+1=?")
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val text = reply.text.orEmpty().trim()
            val usage = reply.usageMetadata?.let { u ->
                AiTokenUsage(
                    promptTokens = u.promptTokenCount,
                    outputTokens = u.candidatesTokenCount ?: 0,
                    totalTokens = u.totalTokenCount
                ).takeIf { it.hasData }
            }
            if (text.isNotEmpty()) {
                val tokNote = usage?.let { " · ${it.totalTokens} tok" }.orEmpty()
                AiModelProbeResult(
                    modelId = safeModel,
                    status = AiModelProbeStatus.WORKING,
                    latencyMs = elapsed,
                    httpCode = 200,
                    detail = "Google Gemini phản hồi OK (${elapsed}ms$tokNote)",
                    tokenUsage = usage
                )
            } else {
                AiModelProbeResult(
                    modelId = safeModel,
                    status = AiModelProbeStatus.FAILED,
                    latencyMs = elapsed,
                    detail = "Trả về nội dung trống"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            AiModelProbeResult(
                modelId = safeModel,
                status = AiModelProbeStatus.FAILED,
                latencyMs = elapsed,
                detail = "Quá thời gian chờ (14s)"
            )
        } catch (e: Throwable) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val msg = e.localizedMessage.orEmpty()
            val isQuota = msg.contains("429") || msg.contains("quota", ignoreCase = true) || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
            AiModelProbeResult(
                modelId = safeModel,
                status = if (isQuota) AiModelProbeStatus.QUOTA_LIMIT else AiModelProbeStatus.FAILED,
                latencyMs = elapsed,
                httpCode = when {
                    msg.contains("429") -> 429
                    msg.contains("404") -> 404
                    msg.contains("403") -> 403
                    else -> 0
                },
                detail = formatFirebaseAiError(e, safeModel).take(120)
            )
        }
    }

    suspend fun askDocumentAssistant(
        modelId: String,
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null,
        onPartialAnswer: (String) -> Unit = {}
    ): Result<AiDocsReply> = withContext(Dispatchers.IO) {
        val safeModel = sanitizeModelId(modelId)
        try {
            val model = getDocsModel(safeModel)
            // Strictly alternate user -> model turns and ensure history ends with a model turn.
            // Format prior model replies as JSON matching docsReplySchema so startChat stays consistent.
            val sanitizedHistory = AiResponseParser.sanitizeChatHistory(history, maxTurns = 6)
            val chatHistory = sanitizedHistory.map { msg ->
                if (msg.isUser) {
                    content(role = "user") { text(msg.text) }
                } else {
                    val modelJson = JSONObject().apply {
                        put("answer", msg.text)
                        val pagesArr = JSONArray()
                        msg.suggestedPages.forEach { p ->
                            pagesArr.put(
                                JSONObject().apply {
                                    put("page", p.page)
                                    put("title", p.title)
                                    if (p.reason.isNotBlank()) put("reason", p.reason)
                                }
                            )
                        }
                        put("suggestedPages", pagesArr)
                    }.toString()
                    content(role = "model") { text(modelJson) }
                }
            }

            val userPrompt = content(role = "user") {
                if (attachedPageBitmap != null) {
                    image(GeminiApiClient.downscaleBitmap(attachedPageBitmap, maxDim = 1024))
                    val pageNote = attachedPageNumber?.let { " (Đính kèm hình ảnh Trang $it trong tài liệu PDF)" } ?: ""
                    text("$question$pageNote")
                } else {
                    text(question)
                }
            }

            val accumulated = StringBuilder()
            val stream = if (chatHistory.isNotEmpty()) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(userPrompt)
            } else {
                model.generateContentStream(userPrompt)
            }

            var lastUsage: AiTokenUsage? = null
            stream.collect { chunk ->
                chunk.usageMetadata?.let { u ->
                    val parsed = AiTokenUsage(
                        promptTokens = u.promptTokenCount,
                        outputTokens = u.candidatesTokenCount ?: 0,
                        totalTokens = u.totalTokenCount
                    )
                    if (parsed.hasData) lastUsage = parsed
                }
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    val preview = AiResponseParser.extractStreamingAnswerPreview(accumulated.toString())
                    if (preview.isNotEmpty()) {
                        onPartialAnswer(preview)
                    }
                }
            }

            val rawText = accumulated.toString().trim()
            if (rawText.isEmpty()) {
                throw IllegalStateException("Google Gemini trả về nội dung trống. Vui lòng thử lại.")
            }
            Result.success(AiResponseParser.parseDocsReply(rawText).copy(tokenUsage = lastUsage))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(FIREBASE_AI_TAG, "Firebase AI document query failed ($safeModel)", e)
            Result.failure(IllegalStateException(formatFirebaseAiError(e, safeModel), e))
        }
    }

    /**
     * Calls Firebase AI Logic `countTokens` to get the exact current context token count for the docs chat.
     */
    suspend fun countDocsContextTokens(
        modelId: String,
        history: List<AiChatMessage>,
        knownInputLimit: Int = 0,
        knownOutputLimit: Int = 0,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null
    ): Result<AiExactContextTokenSync> = withContext(Dispatchers.IO) {
        val safeModel = sanitizeModelId(modelId)
        try {
            val model = getDocsModel(safeModel)
            val sanitizedHistory = AiResponseParser.sanitizeChatHistory(history, maxTurns = 6)
            val contents = sanitizedHistory.map { msg ->
                content(role = if (msg.isUser) "user" else "model") { text(msg.text) }
            }.toMutableList()
            if (attachedPageBitmap != null) {
                contents.add(
                    content(role = "user") {
                        image(GeminiApiClient.downscaleBitmap(attachedPageBitmap, maxDim = 1024))
                        text("Trang ${attachedPageNumber ?: 1} PDF đính kèm")
                    }
                )
            } else if (contents.isEmpty()) {
                contents.add(content(role = "user") { text(" ") })
            }
            val counted = withTimeout(12_000L) {
                model.countTokens(contents).totalTokens
            }
            val resolvedInputLimit = knownInputLimit.takeIf { it > 0 }
                ?: AiResponseParser.inferKnownContextWindow(safeModel, AiProvider.GOOGLE_ACCOUNT)
            Result.success(
                AiExactContextTokenSync(
                    modelId = safeModel,
                    inputTokenLimit = resolvedInputLimit,
                    outputTokenLimit = knownOutputLimit,
                    currentContextTokens = counted,
                    isServerVerified = true,
                    verificationSource = "Google countTokens"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(IllegalStateException(formatFirebaseAiError(e, safeModel), e))
        }
    }

    suspend fun analyzeProblemToDrawing(
        modelId: String,
        problemBitmap: Bitmap?,
        problemNote: String,
        onProgressChars: (Int) -> Unit = {}
    ): Result<AiDrawingPlan> = withContext(Dispatchers.IO) {
        if (problemBitmap == null && problemNote.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Hãy chụp/chọn ảnh đề bài hoặc nhập nội dung đề bài.")
            )
        }
        val safeModel = sanitizeModelId(modelId)
        try {
            val model = getProblemModel(safeModel)
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
                    image(GeminiApiClient.downscaleBitmap(problemBitmap, maxDim = 1024))
                }
                text(promptLine)
            }

            val accumulated = StringBuilder()
            var lastUsage: AiTokenUsage? = null
            model.generateContentStream(input).collect { chunk ->
                chunk.usageMetadata?.let { u ->
                    val parsed = AiTokenUsage(
                        promptTokens = u.promptTokenCount,
                        outputTokens = u.candidatesTokenCount ?: 0,
                        totalTokens = u.totalTokenCount
                    )
                    if (parsed.hasData) lastUsage = parsed
                }
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    onProgressChars(accumulated.length)
                }
            }

            val rawText = accumulated.toString().trim()
            if (rawText.isEmpty()) {
                throw IllegalStateException("Google Gemini trả về dữ liệu trống. Vui lòng thử lại.")
            }
            val plan = AiResponseParser.parseDrawingPlan(rawText)
                ?: throw IllegalStateException(
                    "AI trả về dữ liệu chưa đúng cấu trúc dựng hình 3D/2D. Hãy thử chụp ảnh rõ nét hơn hoặc mô tả ngắn gọn đề bài."
                )
            Result.success(plan.copy(tokenUsage = lastUsage))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(FIREBASE_AI_TAG, "Firebase AI problem drawing failed ($safeModel)", e)
            Result.failure(IllegalStateException(formatFirebaseAiError(e, safeModel), e))
        }
    }

    private fun formatFirebaseAiError(e: Throwable, modelId: String): String {
        val rawMsg = buildString {
            val direct = e.localizedMessage?.trim().orEmpty()
            val causeMsg = e.cause?.localizedMessage?.trim().orEmpty()
            if (direct.isNotEmpty()) append(direct)
            if (causeMsg.isNotEmpty() && !direct.contains(causeMsg)) {
                if (isNotEmpty()) append(" · ")
                append(causeMsg)
            }
        }.ifEmpty { e.javaClass.simpleName }

        return when {
            rawMsg.contains("App Check", ignoreCase = true) ||
                rawMsg.contains("attestation", ignoreCase = true) ||
                rawMsg.contains("403", ignoreCase = true) && rawMsg.contains("token", ignoreCase = true) ->
                "Không thể xác thực App Check (${activeAppCheckProviderLabel()}). Hãy kiểm tra mạng, bấm 'Kiểm tra kết nối' trong mục Tài khoản hoặc dùng API Key riêng. ($rawMsg)"

            rawMsg.contains("billing", ignoreCase = true) ||
                rawMsg.contains("Blaze", ignoreCase = true) ||
                (rawMsg.contains("403", ignoreCase = true) && modelId.contains("argon", ignoreCase = true)) ->
                "Mô hình $modelId yêu cầu gói trả phí (Blaze). Hãy chuyển sang Gemini 3.8 Flash hoặc Gemini 3.5 Flash-Lite trong mục Tài khoản / Key."

            rawMsg.contains("403", ignoreCase = true) || rawMsg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Google Gemini từ chối quyền truy cập (403). Hãy kiểm tra kết nối mạng hoặc thử chọn Gemini 3.8 Flash / API Key riêng. ($rawMsg)"

            rawMsg.contains("429", ignoreCase = true) ||
                rawMsg.contains("quota", ignoreCase = true) ||
                rawMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                "Google Gemini đang quá tải tạm thời (429). Vui lòng đợi 30–60 giây rồi thử lại, hoặc đổi sang Gemini 3.5 Flash-Lite."

            rawMsg.contains("404", ignoreCase = true) || rawMsg.contains("not found", ignoreCase = true) ->
                "Mô hình '$modelId' chưa khả dụng. Hãy chọn Gemini 3.8 Flash trong mục Tài khoản / Key."

            rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                rawMsg.contains("UnknownHost", ignoreCase = true) ||
                rawMsg.contains("No address associated", ignoreCase = true) ||
                rawMsg.contains("ConnectException", ignoreCase = true) ->
                "Không có kết nối Internet hoặc mạng bị gián đoạn. Hãy kiểm tra Wi-Fi / 4G rồi thử lại."

            rawMsg.contains("timeout", ignoreCase = true) || rawMsg.contains("timed out", ignoreCase = true) ->
                "Kết nối tới Google Gemini bị quá thời gian chờ. Hãy thử lại hoặc chọn Gemini 3.5 Flash-Lite để phản hồi nhanh hơn."

            else -> "Lỗi Google Gemini ($modelId): $rawMsg"
        }
    }
}
