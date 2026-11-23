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
import java.net.UnknownHostException
import java.util.Locale

/**
 * Lightweight, zero-bloat OpenAI-compatible API client powering:
 * - OpenAI (`https://api.openai.com/v1`)
 * - OpenRouter (`https://openrouter.ai/api/v1`)
 * - Groq (`https://api.groq.com/openai/v1`)
 * - DeepSeek (`https://api.deepseek.com/v1`)
 * - Any Custom OpenAI-compatible Base URL (Together, Mistral, xAI, SiliconFlow, Ollama, LM Studio, ...)
 *
 * Supports full feature parity:
 * 1. Live model catalog sync (`GET {baseUrl}/models`)
 * 2. Live lightweight model probe (`POST {baseUrl}/chat/completions`, max_tokens=8)
 * 3. Document Q&A with real-time SSE streaming + optional PDF page Vision + exact usage
 * 4. Problem Photo -> Structured 3D/2D Geometry & Graph Drawing Plan
 * 5. Exact context limit + OpenRouter/DeepSeek credit balance + rate-limit header sync
 */
object OpenAiCompatApiClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 85_000
    private const val PROBE_TIMEOUT_MS = 14_000

    private class HttpStatusException(val statusCode: Int, message: String) : IllegalStateException(message)

    private data class ChatCompletionRawResult(
        val text: String,
        val usage: AiTokenUsage?
    )

    suspend fun syncAccountModels(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String = ""
    ): Result<AiModelSyncResult> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val baseUrl = resolveBaseUrl(provider, customBaseUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Vui lòng nhập Base URL hợp lệ (ví dụ: https://api.together.xyz/v1).")
            )
        if (provider.requiresApiKey && cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Vui lòng nhập API Key ${provider.shortTitle} trước khi đồng bộ mô hình.")
            )
        }

        runCatching {
            val endpoint = "$baseUrl/models"
            val conn = openConnection(endpoint, "GET", provider, cleanKey, connectTimeoutMs = 12_000, readTimeoutMs = 20_000)
            try {
                val code = conn.responseCode
                val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                if (code !in 200..299) {
                    throw HttpStatusException(code, formatHttpError(provider, code, body))
                }
                val parsed = AiResponseParser.parseOpenAiModelsJson(body, provider)
                if (parsed.isEmpty()) {
                    throw IllegalStateException(
                        "Máy chủ ${provider.shortTitle} không trả về mô hình chat nào từ $endpoint. Bạn có thể tự thêm mã mô hình tùy chỉnh."
                    )
                }
                val sorted = AiResponseParser.sortSyncedModels(parsed, provider)
                val freeCount = sorted.count { it.source == "free" || it.modelId.endsWith(":free", ignoreCase = true) }
                val freeSuffix = if (provider == AiProvider.OPENROUTER && freeCount > 0) " ($freeCount miễn phí)" else ""
                AiModelSyncResult(
                    models = sorted,
                    sourceLabel = provider.shortTitle,
                    message = "Đã đồng bộ thành công ${sorted.size} mô hình$freeSuffix từ ${provider.shortTitle}!"
                )
            } catch (e: UnknownHostException) {
                throw IllegalStateException("Không thể kết nối tới máy chủ ${provider.shortTitle}. Hãy kiểm tra Wi-Fi/4G hoặc Base URL.")
            } catch (e: SocketTimeoutException) {
                throw IllegalStateException("Kết nối tới ${provider.shortTitle} bị quá thời gian chờ.")
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun probeModel(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String,
        modelId: String
    ): AiModelProbeResult = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val cleanModel = modelId.trim()
        val baseUrl = resolveBaseUrl(provider, customBaseUrl)
            ?: return@withContext AiModelProbeResult(
                modelId = cleanModel,
                status = AiModelProbeStatus.FAILED,
                detail = "Thiếu Base URL"
            )
        if (provider.requiresApiKey && cleanKey.isEmpty()) {
            return@withContext AiModelProbeResult(
                modelId = cleanModel,
                status = AiModelProbeStatus.FAILED,
                detail = "Chưa có API Key"
            )
        }

        val startMs = System.currentTimeMillis()
        try {
            val payload = JSONObject().apply {
                put("model", cleanModel)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", "1+1=?")
                    )
                )
                put("max_tokens", 8)
                put("temperature", 0)
            }
            val result = try {
                executeChatCompletionNonStream(
                    provider = provider,
                    baseUrl = baseUrl,
                    apiKey = cleanKey,
                    payload = payload,
                    connectTimeoutMs = 9_000,
                    readTimeoutMs = PROBE_TIMEOUT_MS
                )
            } catch (e: HttpStatusException) {
                // OpenAI reasoning models (o1/o3/o4/gpt-5) require `max_completion_tokens` and disallow temperature=0
                val msg = e.message.orEmpty()
                if (e.statusCode == 400 && (
                        msg.contains("max_completion_tokens", ignoreCase = true) ||
                            msg.contains("max_tokens", ignoreCase = true) ||
                            msg.contains("temperature", ignoreCase = true)
                        )
                ) {
                    val alt = JSONObject().apply {
                        put("model", cleanModel)
                        put(
                            "messages",
                            JSONArray().put(
                                JSONObject().put("role", "user").put("content", "1+1=?")
                            )
                        )
                        put("max_completion_tokens", 16)
                    }
                    executeChatCompletionNonStream(
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = cleanKey,
                        payload = alt,
                        connectTimeoutMs = 9_000,
                        readTimeoutMs = PROBE_TIMEOUT_MS
                    )
                } else {
                    throw e
                }
            }
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
            val status = if (e.statusCode == 429 || e.statusCode == 402) {
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
            val raw = e.localizedMessage ?: e.javaClass.simpleName
            val status = if (raw.contains("429") || raw.contains("402") || raw.contains("quota", ignoreCase = true)) {
                AiModelProbeStatus.QUOTA_LIMIT
            } else {
                AiModelProbeStatus.FAILED
            }
            AiModelProbeResult(
                modelId = cleanModel,
                status = status,
                latencyMs = elapsed,
                detail = raw.take(96)
            )
        }
    }

    suspend fun askDocumentAssistant(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String,
        modelId: String,
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap? = null,
        attachedPageNumber: Int? = null,
        onPartialAnswer: (String) -> Unit = {}
    ): Result<AiDocsReply> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val baseUrl = resolveBaseUrl(provider, customBaseUrl)
            ?: return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình Base URL hợp lệ cho ${provider.shortTitle}.")
            )
        if (provider.requiresApiKey && cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Chưa nhập API Key ${provider.shortTitle}. Hãy cấu hình trong mục Tài khoản / Key.")
            )
        }

        runCatching {
            val messages = buildDocsMessagesArray(
                systemText = GeminiApiClient.buildDocsSystemPrompt(),
                question = question,
                history = history,
                attachedPageBitmap = attachedPageBitmap,
                attachedPageNumber = attachedPageNumber
            )

            val rawResult = executeChatCompletionStreamingWithFallback(
                provider = provider,
                baseUrl = baseUrl,
                apiKey = cleanKey,
                modelId = modelId.trim(),
                messages = messages,
                temperature = 0.25,
                preferJsonObject = true,
                hasImageAttachment = attachedPageBitmap != null,
                onRawAccumulated = { rawAccum ->
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
            )
            AiResponseParser.parseDocsReply(rawResult.text).copy(tokenUsage = rawResult.usage)
        }
    }

    suspend fun analyzeProblemToDrawing(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String,
        modelId: String,
        problemBitmap: Bitmap?,
        problemNote: String,
        onProgressChars: (Int) -> Unit = {}
    ): Result<AiDrawingPlan> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val baseUrl = resolveBaseUrl(provider, customBaseUrl)
            ?: return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình Base URL hợp lệ cho ${provider.shortTitle}.")
            )
        if (provider.requiresApiKey && cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Chưa nhập API Key ${provider.shortTitle}. Hãy cấu hình trong mục Tài khoản / Key.")
            )
        }
        if (problemBitmap == null && problemNote.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Hãy chụp/chọn ảnh đề bài hoặc nhập nội dung đề bài.")
            )
        }

        runCatching {
            val userContent: Any = if (problemBitmap != null) {
                val arr = JSONArray()
                arr.put(bitmapToImageUrlPart(problemBitmap, maxDim = 1024))
                val promptLine = buildString {
                    append("Hãy đọc thật chính xác đề bài toán trong ảnh đính kèm và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                    if (problemNote.isNotBlank()) {
                        append("\nNội dung / ghi chú thêm từ người dùng:\n")
                        append(problemNote.trim())
                    }
                }
                arr.put(JSONObject().put("type", "text").put("text", promptLine))
                arr
            } else {
                buildString {
                    append("Hãy phân tích đề bài toán sau và xuất kế hoạch dựng hình 3D hoặc 2D theo đúng định dạng JSON.")
                    append("\nNội dung đề bài:\n")
                    append(problemNote.trim())
                }
            }

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", GeminiApiClient.buildProblemDrawSystemPrompt()))
                put(JSONObject().put("role", "user").put("content", userContent))
            }

            val rawResult = executeChatCompletionStreamingWithFallback(
                provider = provider,
                baseUrl = baseUrl,
                apiKey = cleanKey,
                modelId = modelId.trim(),
                messages = messages,
                temperature = 0.2,
                preferJsonObject = true,
                hasImageAttachment = problemBitmap != null,
                onRawAccumulated = { raw -> onProgressChars(raw.length) }
            )
            val plan = AiResponseParser.parseDrawingPlan(rawResult.text)
                ?: throw IllegalStateException("AI không trả về dữ liệu dựng hình hợp lệ. Hãy thử chụp ảnh rõ nét hơn hoặc mô tả thêm đề bài.")
            plan.copy(tokenUsage = rawResult.usage)
        }
    }

    /**
     * Synchronizes exact server context window (`context_length` / `context_window`) and, where supported,
     * live account credit balance (OpenRouter `/key`, DeepSeek `/user/balance`).
     * Never fabricates a fake server-side token count when the provider has no free `countTokens` endpoint.
     */
    suspend fun syncContextTokens(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String,
        modelId: String,
        knownInputLimit: Int,
        knownOutputLimit: Int,
        lastMeasuredContextTokens: Int,
        lastRateLimitRemaining: Long? = null,
        lastRateLimitTotal: Long? = null
    ): Result<AiExactContextTokenSync> = withContext(Dispatchers.IO) {
        val cleanKey = AiResponseParser.sanitizeApiKey(apiKey)
        val cleanModel = modelId.trim()
        val baseUrl = resolveBaseUrl(provider, customBaseUrl)
            ?: return@withContext Result.failure(IllegalStateException("Chưa có Base URL hợp lệ"))

        runCatching {
            var inputLimit = knownInputLimit
            var outputLimit = knownOutputLimit

            if (inputLimit <= 0 && (!provider.requiresApiKey || cleanKey.isNotEmpty())) {
                val limits = fetchSingleModelLimits(provider, baseUrl, cleanKey, cleanModel)
                if (limits != null) {
                    inputLimit = limits.first
                    if (limits.second > 0) outputLimit = limits.second
                }
            }
            if (inputLimit <= 0) {
                inputLimit = AiResponseParser.inferKnownContextWindow(cleanModel, provider)
            }

            val balanceLabel = when {
                provider == AiProvider.OPENROUTER && cleanKey.isNotEmpty() ->
                    fetchOpenRouterBalance(baseUrl, cleanKey)
                provider == AiProvider.DEEPSEEK && cleanKey.isNotEmpty() ->
                    fetchDeepSeekBalance(baseUrl, cleanKey)
                else -> null
            }

            AiExactContextTokenSync(
                modelId = cleanModel,
                inputTokenLimit = inputLimit,
                outputTokenLimit = outputLimit,
                currentContextTokens = lastMeasuredContextTokens.coerceAtLeast(0),
                isServerVerified = lastMeasuredContextTokens > 0 || balanceLabel != null || lastRateLimitRemaining != null,
                verificationSource = when {
                    balanceLabel != null -> "${provider.shortTitle} Balance + usage"
                    lastRateLimitRemaining != null -> "${provider.shortTitle} Rate-limit + usage"
                    else -> "${provider.shortTitle} usage"
                },
                rateLimitRemainingTokens = lastRateLimitRemaining,
                rateLimitTotalTokens = lastRateLimitTotal,
                accountBalanceLabel = balanceLabel
            )
        }
    }

    private fun fetchSingleModelLimits(
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ): Pair<Int, Int>? {
        val conn = openConnection("$baseUrl/models", "GET", provider, apiKey, connectTimeoutMs = 8_000, readTimeoutMs = 12_000)
        return try {
            val code = conn.responseCode
            val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
            if (code !in 200..299) return null
            val list = AiResponseParser.parseOpenAiModelsJson(body, provider)
            val matched = list.firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }
            if (matched != null && matched.inputTokenLimit > 0) {
                matched.inputTokenLimit to matched.outputTokenLimit
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchOpenRouterBalance(baseUrl: String, apiKey: String): String? {
        val endpoints = listOf("$baseUrl/key", "$baseUrl/auth/key")
        for (ep in endpoints) {
            val conn = openConnection(ep, "GET", AiProvider.OPENROUTER, apiKey, connectTimeoutMs = 6_000, readTimeoutMs = 8_000)
            try {
                val code = conn.responseCode
                val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                if (code in 200..299) {
                    val data = JSONObject(body).optJSONObject("data") ?: continue
                    val isFreeTier = data.optBoolean("is_free_tier", false)
                    val limitRemaining = if (data.has("limit_remaining") && !data.isNull("limit_remaining")) {
                        data.optDouble("limit_remaining", Double.NaN)
                    } else Double.NaN
                    val usage = data.optDouble("usage", Double.NaN)
                    return when {
                        !limitRemaining.isNaN() -> String.format(Locale.US, "Còn $%.3f", limitRemaining)
                        isFreeTier -> "Gói Free Tier"
                        !usage.isNaN() -> String.format(Locale.US, "Đã dùng $%.3f", usage)
                        else -> null
                    }
                }
            } catch (_: Throwable) {
                // ignore optional telemetry failure
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun fetchDeepSeekBalance(baseUrl: String, apiKey: String): String? {
        val rootBase = baseUrl.removeSuffix("/v1")
        val conn = openConnection("$rootBase/user/balance", "GET", AiProvider.DEEPSEEK, apiKey, connectTimeoutMs = 6_000, readTimeoutMs = 8_000)
        return try {
            val code = conn.responseCode
            val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
            if (code !in 200..299) return null
            val root = JSONObject(body)
            val infos = root.optJSONArray("balance_infos") ?: return null
            if (infos.length() == 0) return null
            val first = infos.optJSONObject(0) ?: return null
            val currency = first.optString("currency", "USD")
            val total = first.optString("total_balance", "").trim()
            if (total.isNotEmpty()) "Số dư: $total $currency" else null
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun buildDocsMessagesArray(
        systemText: String,
        question: String,
        history: List<AiChatMessage>,
        attachedPageBitmap: Bitmap?,
        attachedPageNumber: Int?
    ): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemText))

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

        val userContent: Any = if (attachedPageBitmap != null) {
            JSONArray().apply {
                put(bitmapToImageUrlPart(attachedPageBitmap, maxDim = 960))
                put(JSONObject().put("type", "text").put("text", userPrompt))
            }
        } else {
            userPrompt
        }

        messages.put(JSONObject().put("role", "user").put("content", userContent))
        return messages
    }

    private fun executeChatCompletionStreamingWithFallback(
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        messages: JSONArray,
        temperature: Double,
        preferJsonObject: Boolean,
        hasImageAttachment: Boolean,
        onRawAccumulated: (String) -> Unit
    ): ChatCompletionRawResult {
        val streamPayload = buildChatPayload(
            modelId = modelId,
            messages = messages,
            temperature = temperature,
            preferJsonObject = preferJsonObject,
            stream = true,
            includeStreamUsage = true
        )

        return try {
            executeChatCompletionStream(provider, baseUrl, apiKey, streamPayload, onRawAccumulated)
        } catch (e: HttpStatusException) {
            val msg = e.message.orEmpty()
            if (hasImageAttachment && isVisionUnsupportedError(e.statusCode, msg)) {
                throw IllegalStateException(
                    "Mô hình '$modelId' trên ${provider.shortTitle} không hỗ trợ đọc ảnh (Vision). " +
                        "Hãy chuyển sang mô hình có Vision (vd: gpt-4o-mini, claude-sonnet-4-5, llama-4-scout, gemini) hoặc gửi đề bằng văn bản."
                )
            }
            // Retry non-streaming without `stream_options` and without `response_format` if rejected (400/422)
            if (e.statusCode == 400 || e.statusCode == 422 || e.statusCode == 501) {
                val compatPayload = buildChatPayload(
                    modelId = modelId,
                    messages = messages,
                    temperature = null,
                    preferJsonObject = false,
                    stream = false,
                    includeStreamUsage = false
                )
                executeChatCompletionNonStream(provider, baseUrl, apiKey, compatPayload)
            } else {
                throw e
            }
        }
    }

    private fun buildChatPayload(
        modelId: String,
        messages: JSONArray,
        temperature: Double?,
        preferJsonObject: Boolean,
        stream: Boolean,
        includeStreamUsage: Boolean
    ): JSONObject {
        val isReasoningModel = modelId.lowercase().let {
            it.startsWith("o1") || it.startsWith("o3") || it.startsWith("o4") || it.contains("deepseek-reasoner")
        }
        return JSONObject().apply {
            put("model", modelId)
            put("messages", messages)
            if (temperature != null && !isReasoningModel) {
                put("temperature", temperature)
            }
            if (preferJsonObject && !isReasoningModel) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            if (stream) {
                put("stream", true)
                if (includeStreamUsage) {
                    put("stream_options", JSONObject().put("include_usage", true))
                }
            }
        }
    }

    private fun executeChatCompletionStream(
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        payload: JSONObject,
        onRawAccumulated: (String) -> Unit
    ): ChatCompletionRawResult {
        val conn = openConnection("$baseUrl/chat/completions", "POST", provider, apiKey)
        try {
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.outputStream.use { it.write(bytes); it.flush() }

            val code = conn.responseCode
            val (rlRemaining, rlTotal) = parseRateLimitHeaders(conn)
            if (code !in 200..299) {
                val errBody = readStream(conn.errorStream)
                throw HttpStatusException(code, formatHttpError(provider, code, errBody))
            }

            val contentType = conn.contentType.orEmpty().lowercase()
            if (!contentType.contains("text/event-stream")) {
                // Server ignored stream=true and returned normal JSON
                val body = readStream(conn.inputStream)
                return parseNonStreamCompletionBody(body, rlRemaining, rlTotal)
            }

            val accumulated = StringBuilder()
            var lastUsage: AiTokenUsage? = null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue

                    AiResponseParser.parseOpenAiUsage(chunk, rlRemaining, rlTotal)?.let { lastUsage = it }

                    val choices = chunk.optJSONArray("choices")
                    val first = choices?.optJSONObject(0)
                    val delta = first?.optJSONObject("delta")
                    val contentPiece = extractContentString(delta?.opt("content"))
                    if (contentPiece.isNotEmpty()) {
                        accumulated.append(contentPiece)
                        onRawAccumulated(accumulated.toString())
                    }
                }
            }

            val finalText = accumulated.toString().trim()
            if (finalText.isEmpty()) {
                throw IllegalStateException("AI trả về nội dung trống. Vui lòng thử lại.")
            }
            return ChatCompletionRawResult(text = finalText, usage = lastUsage)
        } catch (e: UnknownHostException) {
            throw IllegalStateException("Không có kết nối Internet hoặc không truy cập được máy chủ ${provider.shortTitle}.")
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Kết nối tới ${provider.shortTitle} bị quá thời gian chờ. Hãy kiểm tra mạng và thử lại.")
        } finally {
            conn.disconnect()
        }
    }

    private fun executeChatCompletionNonStream(
        provider: AiProvider,
        baseUrl: String,
        apiKey: String,
        payload: JSONObject,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): ChatCompletionRawResult {
        val conn = openConnection("$baseUrl/chat/completions", "POST", provider, apiKey, connectTimeoutMs, readTimeoutMs)
        try {
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.outputStream.use { it.write(bytes); it.flush() }

            val code = conn.responseCode
            val (rlRemaining, rlTotal) = parseRateLimitHeaders(conn)
            val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
            if (code !in 200..299) {
                throw HttpStatusException(code, formatHttpError(provider, code, body))
            }
            return parseNonStreamCompletionBody(body, rlRemaining, rlTotal)
        } catch (e: UnknownHostException) {
            throw IllegalStateException("Không có kết nối Internet hoặc không truy cập được máy chủ ${provider.shortTitle}.")
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Kết nối tới ${provider.shortTitle} bị quá thời gian chờ. Hãy kiểm tra mạng và thử lại.")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseNonStreamCompletionBody(
        body: String,
        rlRemaining: Long?,
        rlTotal: Long?
    ): ChatCompletionRawResult {
        val root = JSONObject(body)
        val usage = AiResponseParser.parseOpenAiUsage(root, rlRemaining, rlTotal)
        val choices = root.optJSONArray("choices")
        val first = choices?.optJSONObject(0)
        val msg = first?.optJSONObject("message")
        val text = extractContentString(msg?.opt("content")).trim()
            .ifEmpty { first?.optString("text", "").orEmpty().trim() }
        if (text.isEmpty()) {
            throw IllegalStateException("AI trả về nội dung trống. Vui lòng thử lại.")
        }
        return ChatCompletionRawResult(text = text, usage = usage)
    }

    private fun extractContentString(raw: Any?): String = when (raw) {
        is String -> raw
        is JSONArray -> buildString {
            for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i)
                val t = item?.optString("text").orEmpty()
                if (t.isNotEmpty()) append(t)
            }
        }
        else -> ""
    }

    private fun openConnection(
        endpoint: String,
        method: String,
        provider: AiProvider,
        apiKey: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            if (apiKey.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            if (provider == AiProvider.OPENROUTER) {
                setRequestProperty("HTTP-Referer", "https://tooltoan.app")
                setRequestProperty("X-Title", "Tool Toan")
            }
        }
    }

    private fun parseRateLimitHeaders(conn: HttpURLConnection): Pair<Long?, Long?> {
        val remaining = conn.getHeaderField("x-ratelimit-remaining-tokens")?.trim()?.toLongOrNull()
        val limit = conn.getHeaderField("x-ratelimit-limit-tokens")?.trim()?.toLongOrNull()
        return remaining to limit
    }

    private fun resolveBaseUrl(provider: AiProvider, customBaseUrl: String): String? {
        val url = if (provider.supportsCustomBaseUrl) {
            AiProvider.normalizeBaseUrl(customBaseUrl)
        } else {
            provider.defaultBaseUrl
        }
        return url.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }

    private fun bitmapToImageUrlPart(source: Bitmap, maxDim: Int): JSONObject {
        val scaled = GeminiApiClient.downscaleBitmap(source, maxDim)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return JSONObject().apply {
            put("type", "image_url")
            put(
                "image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$base64")
            )
        }
    }

    private fun isVisionUnsupportedError(code: Int, message: String): Boolean {
        if (code != 400 && code != 422) return false
        val low = message.lowercase()
        return low.contains("image_url") ||
            low.contains("vision") ||
            low.contains("multimodal") ||
            low.contains("does not support image") ||
            low.contains("cannot read image") ||
            low.contains("unknown variant `image_url`")
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun formatHttpError(provider: AiProvider, code: Int, body: String): String {
        val apiMsg = runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message").orEmpty()
                .ifEmpty { root.optString("message").orEmpty() }
        }.getOrDefault(body.take(180))

        return when (code) {
            401 -> "API Key ${provider.shortTitle} không hợp lệ hoặc đã bị thu hồi (HTTP 401). ${apiMsg.take(200)}"
            402 -> "Tài khoản ${provider.shortTitle} đã hết số dư / credit (HTTP 402). Hãy nạp thêm hoặc chuyển sang mô hình miễn phí (:free). ${apiMsg.take(180)}"
            403 -> "Không có quyền truy cập mô hình này trên ${provider.shortTitle} (HTTP 403). ${apiMsg.take(200)}"
            404 -> "Không tìm thấy mô hình hoặc Base URL trên ${provider.shortTitle} (HTTP 404). Hãy bấm Đồng bộ mô hình hoặc kiểm tra lại Base URL. ${apiMsg.take(160)}"
            429 -> "Đã vượt giới hạn lượt gọi / tốc độ trên ${provider.shortTitle} (HTTP 429). Vui lòng đợi ít phút hoặc đổi mô hình khác. ${apiMsg.take(160)}"
            else -> "Lỗi máy chủ ${provider.shortTitle} (HTTP $code). ${apiMsg.take(220)}"
        }.trim()
    }
}
