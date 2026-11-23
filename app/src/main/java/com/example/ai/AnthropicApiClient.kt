package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Native Anthropic Claude API client (`https://api.anthropic.com/v1`) supporting:
 * 1. Live model catalog sync (`GET /v1/models`)
 * 2. Live lightweight model probe (`POST /v1/messages`, max_tokens=8)
 * 3. FREE exact server-side context token counting (`POST /v1/messages/count_tokens`)
 * 4. Document Q&A with real-time SSE streaming + optional PDF page Vision + exact usage
 * 5. Problem Photo -> Structured 3D/2D Geometry & Graph Drawing Plan
 */
object AnthropicApiClient {

    private const val BASE_URL = "https://api.anthropic.com/v1"
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 85_000
    private const val PROBE_TIMEOUT_MS = 14_000

    private class HttpStatusException(val statusCode: Int, message: String) : IllegalStateException(message)

    private data class ClaudeRateLimits(
        val remainingTokens: Long? = null,
        val limitTokens: Long? = null,
        val remainingRequests: Long? = null,
        val limitRequests: Long? = null
    )

    private data class ClaudeRawResult(
        val text: String,
        val usage: AiTokenUsage?
    )

    suspend fun syncAccountModels(apiKey: String): Result<AiModelSyncResult> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Vui lòng nhập Anthropic API Key (sk-ant-...) trước khi đồng bộ mô hình.")
            )
        }

        runCatching {
            val collected = mutableListOf<AiSyncedModel>()
            var afterId: String? = null
            var pages = 0

            while (pages < 5) {
                pages++
                val url = buildString {
                    append("$BASE_URL/models?limit=100")
                    if (!afterId.isNullOrBlank()) {
                        append("&after_id=")
                        append(URLEncoder.encode(afterId, "UTF-8"))
                    }
                }
                val conn = openConnection(url, "GET", cleanKey, connectTimeoutMs = 12_000, readTimeoutMs = 18_000)
                try {
                    val code = conn.responseCode
                    val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                    if (code !in 200..299) {
                        throw HttpStatusException(code, formatHttpError(code, body))
                    }
                    val (pageModels, nextAfterId) = AiResponseParser.parseAnthropicModelsJson(body)
                    collected.addAll(pageModels)
                    if (nextAfterId.isNullOrBlank()) break
                    afterId = nextAfterId
                } catch (e: UnknownHostException) {
                    throw IllegalStateException("Không có kết nối Internet hoặc không truy cập được máy chủ Anthropic.")
                } catch (e: SocketTimeoutException) {
                    throw IllegalStateException("Kết nối tới Anthropic bị quá thời gian chờ.")
                } finally {
                    conn.disconnect()
                }
            }

            if (collected.isEmpty()) {
                throw IllegalStateException("Tài khoản Anthropic không trả về mô hình Claude nào.")
            }
            val sorted = AiResponseParser.sortSyncedModels(collected, AiProvider.ANTHROPIC)
            AiModelSyncResult(
                models = sorted,
                sourceLabel = "Anthropic Claude",
                message = "Đã đồng bộ thành công ${sorted.size} mô hình Claude từ tài khoản Anthropic!"
            )
        }
    }

    suspend fun probeModel(
        apiKey: String,
        modelId: String
    ): AiModelProbeResult = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val cleanModel = modelId.trim()
        if (cleanKey.isEmpty()) {
            return@withContext AiModelProbeResult(
                modelId = cleanModel,
                status = AiModelProbeStatus.FAILED,
                detail = "Chưa có Anthropic API Key"
            )
        }

        val startMs = System.currentTimeMillis()
        try {
            val payload = JSONObject().apply {
                put("model", cleanModel)
                put("max_tokens", 8)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", "1+1=?")
                    )
                )
            }
            val result = executeMessagesNonStream(
                apiKey = cleanKey,
                payload = payload,
                connectTimeoutMs = 9_000,
                readTimeoutMs = PROBE_TIMEOUT_MS
            )
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val preview = result.text.replace('\n', ' ').trim().take(28)
            AiModelProbeResult(
                modelId = cleanModel,
                status = AiModelProbeStatus.WORKING,
                latencyMs = elapsed,
                httpCode = 200,
                detail = if (preview.isNotEmpty()) "Phản hồi: \"$preview\"" else "Phản hồi thành công",
                tokenUsage = result.usage
            )
        } catch (e: HttpStatusException) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val status = if (e.statusCode == 429 || e.statusCode == 402 || e.statusCode == 529) {
                AiModelProbeStatus.QUOTA_LIMIT
            } else {
                AiModelProbeStatus.FAILED
            }
            AiModelProbeResult(
                modelId = cleanModel,
                status = status,
                latencyMs = elapsed,
                httpCode = e.statusCode,
                detail = (e.message ?: "HTTP ${e.statusCode}").take(96)
            )
        } catch (e: Throwable) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            AiModelProbeResult(
                modelId = cleanModel,
                status = AiModelProbeStatus.FAILED,
                latencyMs = elapsed,
                detail = (e.localizedMessage ?: e.javaClass.simpleName).take(96)
            )
        }
    }

    /**
     * Uses Anthropic's FREE server-side `POST /v1/messages/count_tokens` endpoint to measure exact
     * context tokens (system prompt + conversation history + optional attached PDF page bitmap).
     */
    suspend fun syncExactContextTokens(
        apiKey: String,
        modelId: String,
        history: List<AiChatMessage>,
        knownInputLimit: Int = 200_000,
        knownOutputLimit: Int = 8_192,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null
    ): Result<AiExactContextTokenSync> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val cleanModel = modelId.trim().ifEmpty { AiProvider.ANTHROPIC.defaultModelId }
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Chưa có Anthropic API Key"))
        }

        runCatching {
            val systemText = GeminiApiClient.buildDocsSystemPrompt()
            val messages = buildDocsMessagesForCount(history, attachedPageBitmap, attachedPageNumber)
            val payload = JSONObject().apply {
                put("model", cleanModel)
                put("system", systemText)
                put("messages", messages)
            }
            val conn = openConnection("$BASE_URL/messages/count_tokens", "POST", cleanKey, connectTimeoutMs = 10_000, readTimeoutMs = 14_000)
            try {
                conn.doOutput = true
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(bytes); it.flush() }

                val code = conn.responseCode
                val rateLimits = parseRateLimitHeaders(conn)
                val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                if (code !in 200..299) {
                    throw HttpStatusException(code, formatHttpError(code, body))
                }
                val inputTokens = JSONObject(body).optInt("input_tokens", -1)
                if (inputTokens < 0) {
                    throw IllegalStateException("Anthropic count_tokens không trả về input_tokens.")
                }
                AiExactContextTokenSync(
                    modelId = cleanModel,
                    inputTokenLimit = knownInputLimit.takeIf { it > 0 }
                        ?: AiResponseParser.inferKnownContextWindow(cleanModel, AiProvider.ANTHROPIC),
                    outputTokenLimit = knownOutputLimit.takeIf { it > 0 } ?: 8_192,
                    currentContextTokens = inputTokens,
                    isServerVerified = true,
                    verificationSource = "Claude count_tokens",
                    rateLimitRemainingTokens = rateLimits.remainingTokens,
                    rateLimitTotalTokens = rateLimits.limitTokens,
                    rateLimitRemainingRequests = rateLimits.remainingRequests,
                    rateLimitTotalRequests = rateLimits.limitRequests
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun askDocumentAssistant(
        apiKey: String,
        modelId: String,
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null,
        onPartialAnswer: (String) -> Unit = {}
    ): Result<AiDocsReply> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Chưa nhập Anthropic API Key (sk-ant-...). Hãy cấu hình trong mục Tài khoản / Key.")
            )
        }

        runCatching {
            val messages = buildDocsMessagesArray(question, history, attachedPageBitmap, attachedPageNumber)
            val payload = JSONObject().apply {
                put("model", modelId.trim().ifEmpty { AiProvider.ANTHROPIC.defaultModelId })
                put("max_tokens", 4096)
                put("temperature", 0.25)
                put("system", GeminiApiClient.buildDocsSystemPrompt())
                put("messages", messages)
                put("stream", true)
            }
            val rawResult = try {
                executeMessagesStream(cleanKey, payload) { rawAccum ->
                    val preview = AiResponseParser.extractStreamingAnswerPreview(rawAccum)
                    if (preview.isNotEmpty()) {
                        onPartialAnswer(preview)
                    } else {
                        val trimmed = rawAccum.trimStart()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("{") && !trimmed.startsWith("```")) {
                            onPartialAnswer(trimmed)
                        }
                    }
                }
            } catch (e: HttpStatusException) {
                if (e.statusCode == 400) {
                    payload.remove("stream")
                    executeMessagesNonStream(cleanKey, payload)
                } else {
                    throw e
                }
            }
            AiResponseParser.parseDocsReply(rawResult.text).copy(tokenUsage = rawResult.usage)
        }
    }

    suspend fun analyzeProblemToDrawing(
        apiKey: String,
        modelId: String,
        problemBitmap: Bitmap?,
        problemNote: String,
        onProgressChars: (Int) -> Unit = {}
    ): Result<AiDrawingPlan> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Chưa nhập Anthropic API Key (sk-ant-...). Hãy cấu hình trong mục Tài khoản / Key.")
            )
        }
        if (problemBitmap == null && problemNote.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Hãy chụp/chọn ảnh đề bài hoặc nhập nội dung đề bài.")
            )
        }

        runCatching {
            val contentBlocks = JSONArray()
            if (problemBitmap != null) {
                contentBlocks.put(bitmapToClaudeImageBlock(problemBitmap, maxDim = 1024))
            }
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
            contentBlocks.put(JSONObject().put("type", "text").put("text", promptLine))

            val payload = JSONObject().apply {
                put("model", modelId.trim().ifEmpty { AiProvider.ANTHROPIC.defaultModelId })
                put("max_tokens", 4096)
                put("temperature", 0.2)
                put("system", GeminiApiClient.buildProblemDrawSystemPrompt())
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", contentBlocks)
                    )
                )
                put("stream", true)
            }
            val rawResult = try {
                executeMessagesStream(cleanKey, payload) { raw -> onProgressChars(raw.length) }
            } catch (e: HttpStatusException) {
                if (e.statusCode == 400) {
                    payload.remove("stream")
                    executeMessagesNonStream(cleanKey, payload)
                } else {
                    throw e
                }
            }
            val plan = AiResponseParser.parseDrawingPlan(rawResult.text)
                ?: throw IllegalStateException("Claude không trả về dữ liệu dựng hình hợp lệ. Hãy thử chụp ảnh rõ nét hơn hoặc mô tả thêm đề bài.")
            plan.copy(tokenUsage = rawResult.usage)
        }
    }

    private fun buildDocsMessagesArray(
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap?,
        attachedPageNumber: Int?
    ): JSONArray {
        val messages = JSONArray()
        val recent = AiResponseParser.sanitizeChatHistory(history, maxTurns = 6)
        recent.forEach { msg ->
            messages.put(
                JSONObject()
                    .put("role", if (msg.isUser) "user" else "assistant")
                    .put("content", msg.text)
            )
        }

        val userPrompt = buildString {
            if (attachedPageNumber != null) {
                append("[Người dùng đang đính kèm ảnh Trang $attachedPageNumber của tài liệu PDF 388 trang]\n")
            }
            append(question.trim())
        }
        val content: Any = if (attachedPageBitmap != null) {
            JSONArray().apply {
                put(bitmapToClaudeImageBlock(attachedPageBitmap, maxDim = 960))
                put(JSONObject().put("type", "text").put("text", userPrompt))
            }
        } else {
            userPrompt
        }
        messages.put(JSONObject().put("role", "user").put("content", content))
        return messages
    }

    private fun buildDocsMessagesForCount(
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap?,
        attachedPageNumber: Int?
    ): JSONArray {
        val messages = JSONArray()
        val recent = AiResponseParser.sanitizeChatHistory(history, maxTurns = 6)
        recent.forEach { msg ->
            messages.put(
                JSONObject()
                    .put("role", if (msg.isUser) "user" else "assistant")
                    .put("content", msg.text)
            )
        }
        if (attachedPageBitmap != null || messages.length() == 0) {
            val content: Any = if (attachedPageBitmap != null) {
                JSONArray().apply {
                    put(bitmapToClaudeImageBlock(attachedPageBitmap, maxDim = 960))
                    put(JSONObject().put("type", "text").put("text", "Trang ${attachedPageNumber ?: 1}"))
                }
            } else {
                " "
            }
            messages.put(JSONObject().put("role", "user").put("content", content))
        }
        return messages
    }

    private fun executeMessagesStream(
        apiKey: String,
        payload: JSONObject,
        onRawAccumulated: (String) -> Unit
    ): ClaudeRawResult {
        val conn = openConnection("$BASE_URL/messages", "POST", apiKey)
        try {
            conn.doOutput = true
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes); it.flush() }

            val code = conn.responseCode
            val rateLimits = parseRateLimitHeaders(conn)
            if (code !in 200..299) {
                val errBody = readStream(conn.errorStream)
                throw HttpStatusException(code, formatHttpError(code, errBody))
            }

            val accumulated = StringBuilder()
            var inputTokens = 0
            var outputTokens = 0

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    when (obj.optString("type")) {
                        "message_start" -> {
                            val u = obj.optJSONObject("message")?.optJSONObject("usage")
                            if (u != null) {
                                inputTokens = u.optInt("input_tokens", inputTokens) +
                                    u.optInt("cache_creation_input_tokens", 0) +
                                    u.optInt("cache_read_input_tokens", 0)
                                outputTokens = u.optInt("output_tokens", outputTokens)
                            }
                        }
                        "content_block_delta" -> {
                            val delta = obj.optJSONObject("delta")
                            if (delta?.optString("type") == "text_delta") {
                                val piece = delta.optString("text", "")
                                if (piece.isNotEmpty()) {
                                    accumulated.append(piece)
                                    onRawAccumulated(accumulated.toString())
                                }
                            }
                        }
                        "message_delta" -> {
                            val u = obj.optJSONObject("usage")
                            if (u != null) {
                                val outTok = u.optInt("output_tokens", 0)
                                if (outTok > 0) outputTokens = outTok
                            }
                        }
                    }
                }
            }

            val text = accumulated.toString().trim()
            if (text.isEmpty()) {
                throw IllegalStateException("Claude trả về nội dung trống. Vui lòng thử lại.")
            }
            val total = inputTokens + outputTokens
            val usage = if (total > 0) {
                AiTokenUsage(
                    promptTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = total,
                    rateLimitRemainingTokens = rateLimits.remainingTokens,
                    rateLimitTotalTokens = rateLimits.limitTokens,
                    rateLimitRemainingRequests = rateLimits.remainingRequests,
                    rateLimitTotalRequests = rateLimits.limitRequests
                )
            } else null
            return ClaudeRawResult(text = text, usage = usage)
        } catch (e: UnknownHostException) {
            throw IllegalStateException("Không có kết nối Internet hoặc không truy cập được máy chủ Anthropic.")
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Kết nối tới Anthropic bị quá thời gian chờ. Hãy kiểm tra mạng và thử lại.")
        } finally {
            conn.disconnect()
        }
    }

    private fun executeMessagesNonStream(
        apiKey: String,
        payload: JSONObject,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): ClaudeRawResult {
        val conn = openConnection("$BASE_URL/messages", "POST", apiKey, connectTimeoutMs, readTimeoutMs)
        try {
            conn.doOutput = true
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes); it.flush() }

            val code = conn.responseCode
            val rateLimits = parseRateLimitHeaders(conn)
            val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
            if (code !in 200..299) {
                throw HttpStatusException(code, formatHttpError(code, body))
            }
            val root = JSONObject(body)
            val usage = AiResponseParser.parseAnthropicUsage(
                usageObj = root.optJSONObject("usage"),
                rateLimitRemaining = rateLimits.remainingTokens,
                rateLimitTotal = rateLimits.limitTokens,
                rateLimitRemainingRequests = rateLimits.remainingRequests,
                rateLimitTotalRequests = rateLimits.limitRequests
            )
            val contentArr = root.optJSONArray("content")
            val sb = StringBuilder()
            if (contentArr != null) {
                for (i in 0 until contentArr.length()) {
                    val block = contentArr.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") {
                        sb.append(block.optString("text", ""))
                    }
                }
            }
            val text = sb.toString().trim()
            if (text.isEmpty()) {
                throw IllegalStateException("Claude trả về nội dung trống. Vui lòng thử lại.")
            }
            return ClaudeRawResult(text = text, usage = usage)
        } catch (e: UnknownHostException) {
            throw IllegalStateException("Không có kết nối Internet hoặc không truy cập được máy chủ Anthropic.")
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Kết nối tới Anthropic bị quá thời gian chờ. Hãy kiểm tra mạng và thử lại.")
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(
        endpoint: String,
        method: String,
        apiKey: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
    }

    private fun parseRateLimitHeaders(conn: HttpURLConnection): ClaudeRateLimits {
        val lowerHeaders = LinkedHashMap<String, String>()
        conn.headerFields?.forEach { (k, values) ->
            val key = k?.lowercase()?.trim() ?: return@forEach
            val value = values?.firstOrNull()?.trim().orEmpty()
            if (value.isNotEmpty()) lowerHeaders[key] = value
        }
        fun longHeader(vararg names: String): Long? {
            for (n in names) {
                val raw = lowerHeaders[n] ?: conn.getHeaderField(n)?.trim()
                val parsed = raw?.toLongOrNull()
                if (parsed != null) return parsed
            }
            return null
        }
        return ClaudeRateLimits(
            remainingTokens = longHeader("anthropic-ratelimit-tokens-remaining", "anthropic-ratelimit-input-tokens-remaining"),
            limitTokens = longHeader("anthropic-ratelimit-tokens-limit", "anthropic-ratelimit-input-tokens-limit"),
            remainingRequests = longHeader("anthropic-ratelimit-requests-remaining"),
            limitRequests = longHeader("anthropic-ratelimit-requests-limit")
        )
    }

    private fun bitmapToClaudeImageBlock(source: Bitmap, maxDim: Int): JSONObject {
        val scaled = GeminiApiClient.downscaleBitmap(source, maxDim)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return JSONObject().apply {
            put("type", "image")
            put(
                "source",
                JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", base64)
                }
            )
        }
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun formatHttpError(code: Int, body: String): String {
        val apiMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault(body.take(180))

        return when (code) {
            401 -> "Anthropic API Key không hợp lệ (HTTP 401). Hãy kiểm tra khóa sk-ant-... của bạn. ${apiMsg.take(180)}"
            402, 400 -> if (apiMsg.contains("credit", ignoreCase = true) || apiMsg.contains("balance", ignoreCase = true)) {
                "Tài khoản Anthropic hết số dư credit (HTTP $code). ${apiMsg.take(180)}"
            } else {
                "Yêu cầu Anthropic bị từ chối (HTTP $code). ${apiMsg.take(200)}"
            }
            403 -> "Không có quyền truy cập mô hình Claude này (HTTP 403). ${apiMsg.take(180)}"
            404 -> "Không tìm thấy mô hình Claude đã chọn (HTTP 404). Hãy bấm Đồng bộ mô hình. ${apiMsg.take(160)}"
            429, 529 -> "Anthropic đang giới hạn tốc độ hoặc quá tải (HTTP $code). Vui lòng đợi ít phút. ${apiMsg.take(160)}"
            else -> "Lỗi máy chủ Anthropic (HTTP $code). ${apiMsg.take(200)}"
        }.trim()
    }
}
