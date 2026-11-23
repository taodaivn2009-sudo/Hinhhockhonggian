package com.example.ai

/**
 * Supported AI providers in Tool Toán:
 * - [GOOGLE_ACCOUNT]: Keyless Google Gemini via Firebase AI Logic + Google Sign-In
 * - [GOOGLE_API_KEY]: Personal Google AI Studio / Vertex Express API key
 * - [OPENAI]: Official OpenAI API (`api.openai.com/v1`)
 * - [OPENROUTER]: OpenRouter unified gateway (`openrouter.ai/api/v1`)
 * - [GROQ]: Groq ultra-fast inference (`api.groq.com/openai/v1`)
 * - [DEEPSEEK]: DeepSeek official API (`api.deepseek.com/v1`)
 * - [ANTHROPIC]: Anthropic Claude native Messages API (`api.anthropic.com/v1`)
 * - [CUSTOM_OPENAI]: Any custom OpenAI-compatible server/proxy (Together, Mistral, xAI, Ollama, LM Studio, ...)
 */
enum class AiProvider(
    val key: String,
    val shortTitle: String,
    val fullTitle: String,
    val defaultBaseUrl: String,
    val defaultModelId: String,
    val keyPlaceholder: String,
    val keyHelpTitle: String,
    val keyHelpSubtitle: String,
    val apiKeyPortalUrl: String,
    val quotaPortalUrl: String,
    val quotaPortalLabel: String,
    val usesOpenAiProtocol: Boolean = false,
    val requiresApiKey: Boolean = true,
    val supportsCustomBaseUrl: Boolean = false
) {
    GOOGLE_ACCOUNT(
        key = "google_account",
        shortTitle = "Google",
        fullTitle = "Google Gemini (Đăng nhập)",
        defaultBaseUrl = "",
        defaultModelId = AiModelPreset.GEMINI_3_8_FLASH.modelId,
        keyPlaceholder = "",
        keyHelpTitle = "Đăng nhập Google 1 chạm",
        keyHelpSubtitle = "Dùng ngay Google Gemini mà không cần tạo hay dán API Key.",
        apiKeyPortalUrl = "https://aistudio.google.com/apikey",
        quotaPortalUrl = "https://aistudio.google.com/usage",
        quotaPortalLabel = "AI Studio",
        usesOpenAiProtocol = false,
        requiresApiKey = false
    ),
    GOOGLE_API_KEY(
        key = "google_api_key",
        shortTitle = "Google Key",
        fullTitle = "Google AI Studio (API Key)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModelId = AiModelPreset.GEMINI_3_8_FLASH.modelId,
        keyPlaceholder = "Dán Google API Key (AIza... hoặc AQ....)",
        keyHelpTitle = "Lấy API Key tại Google AI Studio",
        keyHelpSubtitle = "Hỗ trợ cả khóa AIza... và khóa AQ.... từ Google AI Studio / Vertex Express.",
        apiKeyPortalUrl = "https://aistudio.google.com/apikey",
        quotaPortalUrl = "https://aistudio.google.com/usage",
        quotaPortalLabel = "AI Studio",
        usesOpenAiProtocol = false,
        requiresApiKey = true
    ),
    OPENAI(
        key = "openai",
        shortTitle = "OpenAI",
        fullTitle = "OpenAI (ChatGPT API)",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModelId = "gpt-4o-mini",
        keyPlaceholder = "Dán OpenAI API Key (sk-proj-... hoặc sk-...)",
        keyHelpTitle = "Lấy API Key tại OpenAI Platform",
        keyHelpSubtitle = "Hỗ trợ GPT-4o, GPT-4.1, o4-mini, đọc ảnh đề bài và vẽ hình 3D/2D.",
        apiKeyPortalUrl = "https://platform.openai.com/api-keys",
        quotaPortalUrl = "https://platform.openai.com/usage",
        quotaPortalLabel = "OpenAI Usage",
        usesOpenAiProtocol = true,
        requiresApiKey = true
    ),
    OPENROUTER(
        key = "openrouter",
        shortTitle = "OpenRouter",
        fullTitle = "OpenRouter (Đa mô hình)",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModelId = "openai/gpt-4o-mini",
        keyPlaceholder = "Dán OpenRouter API Key (sk-or-v1-...)",
        keyHelpTitle = "Lấy API Key tại OpenRouter",
        keyHelpSubtitle = "1 khóa duy nhất dùng hàng trăm mô hình (có nhiều mô hình :free miễn phí).",
        apiKeyPortalUrl = "https://openrouter.ai/keys",
        quotaPortalUrl = "https://openrouter.ai/activity",
        quotaPortalLabel = "OpenRouter",
        usesOpenAiProtocol = true,
        requiresApiKey = true
    ),
    GROQ(
        key = "groq",
        shortTitle = "Groq",
        fullTitle = "Groq (Tốc độ cao)",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModelId = "llama-3.3-70b-versatile",
        keyPlaceholder = "Dán Groq API Key (gsk_...)",
        keyHelpTitle = "Lấy API Key miễn phí tại Groq Console",
        keyHelpSubtitle = "Phản hồi siêu nhanh với Llama 4 (Vision), Llama 3.3, Qwen và hạn mức miễn phí hàng ngày.",
        apiKeyPortalUrl = "https://console.groq.com/keys",
        quotaPortalUrl = "https://console.groq.com/settings/limits",
        quotaPortalLabel = "Groq Limits",
        usesOpenAiProtocol = true,
        requiresApiKey = true
    ),
    DEEPSEEK(
        key = "deepseek",
        shortTitle = "DeepSeek",
        fullTitle = "DeepSeek API",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModelId = "deepseek-chat",
        keyPlaceholder = "Dán DeepSeek API Key (sk-...)",
        keyHelpTitle = "Lấy API Key tại DeepSeek Platform",
        keyHelpSubtitle = "Lập luận toán học mạnh với deepseek-chat & deepseek-reasoner chi phí rất thấp.",
        apiKeyPortalUrl = "https://platform.deepseek.com/api_keys",
        quotaPortalUrl = "https://platform.deepseek.com/usage",
        quotaPortalLabel = "DeepSeek",
        usesOpenAiProtocol = true,
        requiresApiKey = true
    ),
    ANTHROPIC(
        key = "anthropic",
        shortTitle = "Claude",
        fullTitle = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModelId = "claude-sonnet-4-5",
        keyPlaceholder = "Dán Anthropic API Key (sk-ant-...)",
        keyHelpTitle = "Lấy API Key tại Anthropic Console",
        keyHelpSubtitle = "Hỗ trợ Vision phân tích ảnh đề toán, đếm token chính xác qua count_tokens.",
        apiKeyPortalUrl = "https://console.anthropic.com/settings/keys",
        quotaPortalUrl = "https://console.anthropic.com/settings/usage",
        quotaPortalLabel = "Anthropic",
        usesOpenAiProtocol = false,
        requiresApiKey = true
    ),
    CUSTOM_OPENAI(
        key = "custom_openai",
        shortTitle = "Custom URL",
        fullTitle = "Custom URL (Chuẩn OpenAI)",
        defaultBaseUrl = "",
        defaultModelId = "gpt-4o-mini",
        keyPlaceholder = "Dán API Key (có thể để trống nếu là Ollama/LAN nội bộ)",
        keyHelpTitle = "Kết nối mọi máy chủ chuẩn OpenAI",
        keyHelpSubtitle = "Hỗ trợ Together, Mistral, xAI, SiliconFlow, Ollama, LM Studio hoặc proxy riêng.",
        apiKeyPortalUrl = "",
        quotaPortalUrl = "",
        quotaPortalLabel = "Endpoint",
        usesOpenAiProtocol = true,
        requiresApiKey = false,
        supportsCustomBaseUrl = true
    );

    fun defaultPresetModels(): List<AiSyncedModel> = when (this) {
        GOOGLE_ACCOUNT, GOOGLE_API_KEY -> AiModelPreset.entries.map { it.toSyncedModel() }
        OPENAI -> listOf(
            AiSyncedModel("gpt-4o-mini", "GPT-4o Mini", "Nhanh · Hỗ trợ Vision đọc ảnh đề & vẽ hình", 128_000, 16_384, "preset"),
            AiSyncedModel("gpt-4o", "GPT-4o", "Đa phương thức mạnh · Đọc ảnh toán & lập luận tốt", 128_000, 16_384, "preset"),
            AiSyncedModel("gpt-4.1-mini", "GPT-4.1 Mini", "Cửa sổ 1M token · Tối ưu tra cứu tài liệu", 1_047_576, 32_768, "preset"),
            AiSyncedModel("o4-mini", "o4-mini", "Suy luận toán học chuyên sâu", 200_000, 100_000, "preset")
        )
        OPENROUTER -> listOf(
            AiSyncedModel("openai/gpt-4o-mini", "OpenAI: GPT-4o Mini", "Đa năng · Hỗ trợ đọc ảnh đề bài", 128_000, 16_384, "preset"),
            AiSyncedModel("anthropic/claude-sonnet-4", "Anthropic: Claude Sonnet 4", "Phân tích hình học & ảnh đề bài xuất sắc", 200_000, 16_384, "preset"),
            AiSyncedModel("deepseek/deepseek-chat-v3-0324:free", "DeepSeek V3 (Free)", "Miễn phí trên OpenRouter · Giải toán văn bản tốt", 128_000, 8_192, "preset"),
            AiSyncedModel("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", "Miễn phí trên OpenRouter · Tra cứu nhanh", 128_000, 8_192, "preset")
        )
        GROQ -> listOf(
            AiSyncedModel("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", "Nhanh & thông minh · Hỏi đáp tài liệu toán", 128_000, 32_768, "preset"),
            AiSyncedModel("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout (Vision)", "Hỗ trợ ảnh đề bài & tốc độ Groq siêu nhanh", 131_072, 8_192, "preset"),
            AiSyncedModel("llama-3.1-8b-instant", "Llama 3.1 8B Instant", "Siêu nhẹ · Hạn mức miễn phí cao", 131_072, 8_192, "preset")
        )
        DEEPSEEK -> listOf(
            AiSyncedModel("deepseek-chat", "DeepSeek Chat (V3)", "Giải toán, tra cứu công thức & dựng hình từ văn bản", 65_536, 8_192, "preset"),
            AiSyncedModel("deepseek-reasoner", "DeepSeek Reasoner (R1)", "Suy luận từng bước cho bài toán khó", 65_536, 8_192, "preset")
        )
        ANTHROPIC -> listOf(
            AiSyncedModel("claude-sonnet-4-5", "Claude Sonnet 4.5", "Cân bằng mạnh nhất · Vision & toán hình xuất sắc", 200_000, 64_000, "preset"),
            AiSyncedModel("claude-3-7-sonnet-latest", "Claude 3.7 Sonnet", "Lập luận sâu & hỗ trợ ảnh đề bài rõ nét", 200_000, 64_000, "preset"),
            AiSyncedModel("claude-3-5-haiku-latest", "Claude 3.5 Haiku", "Nhanh & tiết kiệm chi phí token", 200_000, 8_192, "preset")
        )
        CUSTOM_OPENAI -> listOf(
            AiSyncedModel("gpt-4o-mini", "Mặc định (bấm Đồng bộ để tải từ server)", "Nhập Base URL rồi bấm Đồng bộ mô hình hoặc tự thêm mã mô hình", 0, 0, "preset")
        )
    }

    companion object {
        fun fromKey(raw: String?): AiProvider =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: GOOGLE_ACCOUNT

        /**
         * Suggests the most likely provider when a user pastes an API key with an unmistakable prefix.
         */
        fun detectProviderHintFromKey(rawKey: String): AiProvider? {
            val k = AiResponseParser.sanitizeApiKey(rawKey)
            return when {
                k.startsWith("sk-or-") -> OPENROUTER
                k.startsWith("sk-ant-") -> ANTHROPIC
                k.startsWith("gsk_") -> GROQ
                k.startsWith("AIza") || k.startsWith("AQ.") -> GOOGLE_API_KEY
                k.startsWith("sk-proj-") -> OPENAI
                else -> null
            }
        }

        /**
         * Normalizes a user-entered OpenAI-compatible Base URL:
         * - Trims whitespace and trailing slashes
         * - Strips accidental `/chat/completions` or `/models` suffixes if the user pasted a full endpoint
         * - Validates `http://` or `https://` scheme (prepends `https://` only if user typed a bare domain)
         */
        fun normalizeBaseUrl(rawUrl: String): String {
            var u = rawUrl.trim()
            if (u.isEmpty()) return ""
            if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
                u = "https://$u"
            }
            while (u.endsWith("/")) u = u.dropLast(1)
            val lower = u.lowercase()
            u = when {
                lower.endsWith("/chat/completions") -> u.dropLast("/chat/completions".length)
                lower.endsWith("/completions") -> u.dropLast("/completions".length)
                lower.endsWith("/models") -> u.dropLast("/models".length)
                else -> u
            }
            while (u.endsWith("/")) u = u.dropLast(1)
            return u
        }

        val customEndpointSuggestions: List<Pair<String, String>> = listOf(
            "Together" to "https://api.together.xyz/v1",
            "Mistral" to "https://api.mistral.ai/v1",
            "xAI Grok" to "https://api.x.ai/v1",
            "SiliconFlow" to "https://api.siliconflow.cn/v1",
            "Ollama LAN" to "http://192.168.1.100:11434/v1"
        )
    }
}

/**
 * Isolated per-provider configuration & cached catalog persisted on device.
 * Switching providers preserves every provider's own API key, base URL, model, and synced catalog.
 */
data class AiProviderProfile(
    val provider: AiProvider,
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelId: String = provider.defaultModelId,
    val syncedModels: List<AiSyncedModel> = emptyList(),
    val syncedModelsUpdatedAt: Long = 0L
) {
    val trimmedKey: String get() = AiResponseParser.sanitizeApiKey(apiKey)
    val hasKey: Boolean get() = trimmedKey.isNotEmpty()

    val effectiveBaseUrl: String
        get() = if (provider.supportsCustomBaseUrl) {
            AiProvider.normalizeBaseUrl(baseUrl)
        } else {
            provider.defaultBaseUrl
        }

    val effectiveModelId: String
        get() = modelId.trim().ifEmpty {
            syncedModels.firstOrNull()?.modelId ?: provider.defaultModelId
        }

    val maskedKey: String
        get() {
            val k = trimmedKey
            return when {
                k.isEmpty() -> if (provider == AiProvider.CUSTOM_OPENAI && effectiveBaseUrl.isNotEmpty()) {
                    "Không dùng Key"
                } else {
                    "Chưa nhập API Key"
                }
                k.length <= 8 -> "•".repeat(k.length)
                else -> "${k.take(4)}${"•".repeat(6)}${k.takeLast(4)}"
            }
        }

    fun isReady(isSignedInWithGoogle: Boolean): Boolean = when (provider) {
        AiProvider.GOOGLE_ACCOUNT -> isSignedInWithGoogle
        AiProvider.CUSTOM_OPENAI -> effectiveBaseUrl.isNotEmpty()
        else -> hasKey
    }
}
