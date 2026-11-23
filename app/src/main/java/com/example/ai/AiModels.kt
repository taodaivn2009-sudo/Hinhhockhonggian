package com.example.ai

import android.content.Context
import com.example.docs.PDF_TOTAL_PAGES
import com.example.engine.*
import com.example.geo2d.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class AiModelPreset(
    val modelId: String,
    val title: String,
    val subtitle: String
) {
    GEMINI_3_8_FLASH(
        modelId = "gemini-3.8-flash",
        title = "Gemini 3.8 Flash",
        subtitle = "Mặc định · Nhanh & chính xác cho ảnh đề bài + tài liệu"
    ),
    GEMINI_3_5_FLASH_LITE(
        modelId = "gemini-3.5-flash-lite",
        title = "Gemini 3.5 Flash-Lite",
        subtitle = "Siêu nhẹ · Tra cứu nhanh mục lục & công thức"
    ),
    GEMINI_4_ARGON(
        modelId = "gemini-4-argon",
        title = "Gemini 4 Argon",
        subtitle = "Chuyên sâu · Lập luận toán học & dựng hình phức tạp"
    );

    companion object {
        fun fromId(id: String?): AiModelPreset? = entries.firstOrNull { it.modelId.equals(id?.trim(), ignoreCase = true) }
    }

    fun toSyncedModel(source: String = "preset"): AiSyncedModel = AiSyncedModel(
        modelId = modelId,
        displayName = title,
        description = subtitle,
        source = source
    )
}

data class AiSyncedModel(
    val modelId: String,
    val displayName: String,
    val description: String = "",
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val source: String = "preset" // "api" | "vertex" | "firebase" | "custom" | "preset"
) {
    val formattedContextWindow: String
        get() = when {
            inputTokenLimit >= 1_000_000 -> "${inputTokenLimit / 1_000_000}M token"
            inputTokenLimit >= 1_000 -> "${inputTokenLimit / 1_000}K token"
            else -> ""
        }
}

data class AiModelSyncResult(
    val models: List<AiSyncedModel>,
    val sourceLabel: String,
    val message: String
)

enum class AiModelProbeStatus {
    TESTING,
    WORKING,
    QUOTA_LIMIT,
    FAILED
}

data class AiTokenUsage(
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val thoughtsTokens: Int = 0,
    val totalTokens: Int = 0,
    val rateLimitRemainingTokens: Long? = null,
    val rateLimitTotalTokens: Long? = null,
    val rateLimitRemainingRequests: Long? = null,
    val rateLimitTotalRequests: Long? = null
) {
    val hasData: Boolean get() = totalTokens > 0 || promptTokens > 0 || outputTokens > 0
}

data class AiModelTokenLedger(
    val modelId: String,
    val dayKey: String,
    val usedTokensToday: Long = 0L,
    val promptTokensToday: Long = 0L,
    val outputTokensToday: Long = 0L,
    val requestsToday: Int = 0,
    val lastPromptTokens: Int = 0,
    val lastOutputTokens: Int = 0,
    val lastTotalTokens: Int = 0,
    val lastUpdatedAtMillis: Long = 0L
)

data class AiPersistedTokenSnapshot(
    val modelId: String,
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val currentContextTokens: Int = 0,
    val isServerVerified: Boolean = false,
    val verificationSource: String = "",
    val rateLimitRemainingTokens: Long? = null,
    val rateLimitTotalTokens: Long? = null,
    val rateLimitRemainingRequests: Long? = null,
    val rateLimitTotalRequests: Long? = null,
    val accountBalanceLabel: String? = null,
    val lastSyncedAtMillis: Long = 0L
)

data class AiExactContextTokenSync(
    val modelId: String,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
    val currentContextTokens: Int,
    val syncedAtMillis: Long = System.currentTimeMillis(),
    val isServerVerified: Boolean = true,
    val verificationSource: String = "Google countTokens",
    val rateLimitRemainingTokens: Long? = null,
    val rateLimitTotalTokens: Long? = null,
    val rateLimitRemainingRequests: Long? = null,
    val rateLimitTotalRequests: Long? = null,
    val accountBalanceLabel: String? = null
)

data class AiContextTokenState(
    val modelId: String = "",
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val currentContextTokens: Int = 0,
    val lastTurnUsage: AiTokenUsage? = null,
    val usedTokensToday: Long = 0L,
    val requestsToday: Int = 0,
    val isSyncingTokens: Boolean = false,
    val isServerVerified: Boolean = false,
    val verificationSource: String = "",
    val rateLimitRemainingTokens: Long? = null,
    val rateLimitTotalTokens: Long? = null,
    val rateLimitRemainingRequests: Long? = null,
    val rateLimitTotalRequests: Long? = null,
    val accountBalanceLabel: String? = null,
    val lastSyncedAtMillis: Long = 0L,
    val syncError: String? = null
) {
    val hasContextLimit: Boolean get() = inputTokenLimit > 0
    val remainingContextTokens: Int
        get() = if (inputTokenLimit > 0) (inputTokenLimit - currentContextTokens).coerceAtLeast(0) else 0
    val remainingContextFraction: Float
        get() = if (inputTokenLimit > 0) {
            (remainingContextTokens.toFloat() / inputTokenLimit.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class AiModelProbeResult(
    val modelId: String,
    val status: AiModelProbeStatus,
    val latencyMs: Long = 0L,
    val httpCode: Int = 0,
    val detail: String = "",
    val tokenUsage: AiTokenUsage? = null
) {
    val shortBadgeLabel: String
        get() = when (status) {
            AiModelProbeStatus.TESTING -> "Đang test..."
            AiModelProbeStatus.WORKING -> if (latencyMs > 0L) "OK · ${latencyMs}ms" else "Hoạt động"
            AiModelProbeStatus.QUOTA_LIMIT -> when (httpCode) {
                402 -> "402 · Hết credit"
                429 -> "429 · Hết lượt"
                else -> "Hết quota"
            }
            AiModelProbeStatus.FAILED -> if (httpCode > 0) "Lỗi $httpCode" else "Không chạy"
        }
}

/**
 * Live incremental preview of an AI 3D/2D problem drawing plan while tokens are streaming in,
 * so users immediately read the detected mode, title, problem statement, drawing plan, and
 * solution hint before final geometry coordinates complete.
 */
data class AiProblemStreamingPreview(
    val target: AiDrawTarget? = null,
    val title: String = "",
    val problemText: String = "",
    val drawingSummary: String = "",
    val solutionHint: String = "",
    val isBuildingGeometry: Boolean = false,
    val thinkingChars: Int = 0,
    val rawChars: Int = 0
) {
    val hasVisibleText: Boolean
        get() = title.isNotBlank() ||
            problemText.isNotBlank() ||
            drawingSummary.isNotBlank() ||
            solutionHint.isNotBlank()
}

enum class AiSubTab(val key: String, val label: String) {
    DOCS_QA("docs_qa", "Hỏi tài liệu"),
    PROBLEM_DRAW("problem_draw", "Chụp đề vẽ hình"),
    API_KEY("api_key", "Tài khoản / Key")
}

enum class AiProviderMode(val key: String, val label: String) {
    GOOGLE_GEMINI("google_gemini", "Google Gemini (Tài khoản Google)"),
    PERSONAL_API_KEY("personal_api_key", "API Key riêng");

    companion object {
        fun fromKey(raw: String?): AiProviderMode =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: GOOGLE_GEMINI
    }
}

data class AiKeyConfig(
    val apiKey: String = "",
    val modelId: String = AiModelPreset.GEMINI_3_8_FLASH.modelId,
    val providerMode: AiProviderMode = AiProviderMode.GOOGLE_GEMINI
) {
    val trimmedKey: String get() = AiResponseParser.sanitizeApiKey(apiKey)
    val hasKey: Boolean get() = trimmedKey.isNotEmpty()
    val effectiveModelId: String get() = modelId.trim().ifEmpty { AiModelPreset.GEMINI_3_8_FLASH.modelId }

    val maskedKey: String
        get() {
            val k = trimmedKey
            return when {
                k.isEmpty() -> "Chưa nhập API Key"
                k.length <= 8 -> "•".repeat(k.length)
                else -> "${k.take(4)}${"•".repeat(6)}${k.takeLast(4)}"
            }
        }
}

class AiKeyPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tool_toan_ai_config_v1", Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
        migrateLegacyCustomEndpointIfNeeded()
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_MULTI_PROVIDER_MIGRATED, false)) return
        val legacyKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        val legacyModel = prefs.getString(KEY_MODEL_ID, AiModelPreset.GEMINI_3_8_FLASH.modelId)
            ?: AiModelPreset.GEMINI_3_8_FLASH.modelId
        val legacyMode = AiProviderMode.fromKey(prefs.getString(KEY_PROVIDER_MODE, AiProviderMode.GOOGLE_GEMINI.key))
        val legacyModelsRaw = prefs.getString(KEY_SYNCED_MODELS, null)
        val legacyModelsAt = prefs.getLong(KEY_SYNCED_MODELS_AT, 0L)
        val legacyLedgersRaw = prefs.getString(KEY_TOKEN_LEDGERS, null)

        val initialProvider = if (legacyKey.isNotBlank() && legacyMode == AiProviderMode.PERSONAL_API_KEY) {
            AiProvider.GOOGLE_API_KEY
        } else {
            AiProvider.GOOGLE_ACCOUNT
        }

        val editor = prefs.edit()
            .putBoolean(KEY_MULTI_PROVIDER_MIGRATED, true)
            .putString(KEY_ACTIVE_PROVIDER, initialProvider.key)
            .putString(providerKey(AiProvider.GOOGLE_ACCOUNT, "model_id"), legacyModel)
            .putString(providerKey(AiProvider.GOOGLE_API_KEY, "api_key"), legacyKey)
            .putString(providerKey(AiProvider.GOOGLE_API_KEY, "model_id"), legacyModel)

        if (!legacyModelsRaw.isNullOrBlank()) {
            editor.putString(providerKey(AiProvider.GOOGLE_API_KEY, "synced_models"), legacyModelsRaw)
            editor.putLong(providerKey(AiProvider.GOOGLE_API_KEY, "synced_at"), legacyModelsAt)
            if (legacyKey.isBlank()) {
                editor.putString(providerKey(AiProvider.GOOGLE_ACCOUNT, "synced_models"), legacyModelsRaw)
                editor.putLong(providerKey(AiProvider.GOOGLE_ACCOUNT, "synced_at"), legacyModelsAt)
            }
        }
        if (!legacyLedgersRaw.isNullOrBlank()) {
            editor.putString(providerKey(initialProvider, "token_ledgers"), legacyLedgersRaw)
        }
        editor.apply()
    }

    /**
     * Migrates any previously saved single Custom URL under `provider_custom_openai_*` into the
     * multi-endpoint Custom URL registry without losing its URL, key, synced models, or token history.
     */
    private fun migrateLegacyCustomEndpointIfNeeded() {
        if (prefs.getBoolean(KEY_CUSTOM_ENDPOINTS_MIGRATED, false)) return
        val existingList = loadCustomEndpoints()
        if (existingList.isNotEmpty()) {
            prefs.edit().putBoolean(KEY_CUSTOM_ENDPOINTS_MIGRATED, true).apply()
            return
        }
        val legacyUrl = prefs.getString(providerKey(AiProvider.CUSTOM_OPENAI, "base_url"), "").orEmpty().trim()
        val legacyKey = prefs.getString(providerKey(AiProvider.CUSTOM_OPENAI, "api_key"), "").orEmpty().trim()
        val editor = prefs.edit().putBoolean(KEY_CUSTOM_ENDPOINTS_MIGRATED, true)
        if (legacyUrl.isNotEmpty() || legacyKey.isNotEmpty()) {
            val normalizedUrl = AiProvider.normalizeBaseUrl(legacyUrl)
            val legacyModel = prefs.getString(providerKey(AiProvider.CUSTOM_OPENAI, "model_id"), "")
                .orEmpty().ifBlank { AiProvider.CUSTOM_OPENAI.defaultModelId }
            val legacyModels = parseSyncedModelsJson(
                prefs.getString(providerKey(AiProvider.CUSTOM_OPENAI, "synced_models"), null).orEmpty()
            )
            val legacyModelsAt = prefs.getLong(providerKey(AiProvider.CUSTOM_OPENAI, "synced_at"), 0L)
            val migratedId = "custom_default"
            val migrated = CustomEndpointProfile(
                id = migratedId,
                name = AiProvider.deriveCustomEndpointName(normalizedUrl),
                baseUrl = normalizedUrl,
                apiKey = legacyKey,
                modelId = legacyModel,
                syncedModels = legacyModels,
                syncedModelsUpdatedAt = legacyModelsAt
            )
            editor.putString(KEY_CUSTOM_ENDPOINTS_JSON, serializeCustomEndpoints(listOf(migrated)))
            editor.putString(KEY_ACTIVE_CUSTOM_ENDPOINT_ID, migratedId)

            val legacyLedgers = prefs.getString(providerKey(AiProvider.CUSTOM_OPENAI, "token_ledgers"), null)
            if (!legacyLedgers.isNullOrBlank()) {
                editor.putString(scopedKey(AiProvider.CUSTOM_OPENAI, "token_ledgers", migratedId), legacyLedgers)
            }
            val legacySnapshots = prefs.getString(providerKey(AiProvider.CUSTOM_OPENAI, "token_snapshots_v1"), null)
            if (!legacySnapshots.isNullOrBlank()) {
                editor.putString(scopedKey(AiProvider.CUSTOM_OPENAI, "token_snapshots_v1", migratedId), legacySnapshots)
            }
        }
        editor.apply()
    }

    private fun providerKey(provider: AiProvider, field: String): String =
        "provider_${provider.key}_$field"

    private fun scopedKey(provider: AiProvider, field: String, customEndpointId: String? = null): String {
        val cleanEp = customEndpointId?.trim().orEmpty()
        return if (provider == AiProvider.CUSTOM_OPENAI && cleanEp.isNotEmpty()) {
            "custom_ep_${cleanEp}_$field"
        } else {
            providerKey(provider, field)
        }
    }

    fun generateCustomEndpointId(): String =
        "custom_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"

    fun loadCustomEndpoints(): List<CustomEndpointProfile> {
        val raw = prefs.getString(KEY_CUSTOM_ENDPOINTS_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val url = AiProvider.normalizeBaseUrl(o.optString("baseUrl", ""))
                    if (id.isEmpty() || url.isEmpty()) continue
                    val modelsRaw = o.optJSONArray("syncedModels")?.toString().orEmpty()
                    add(
                        CustomEndpointProfile(
                            id = id,
                            name = o.optString("name", "").trim().ifEmpty { AiProvider.deriveCustomEndpointName(url) },
                            baseUrl = url,
                            apiKey = o.optString("apiKey", ""),
                            modelId = o.optString("modelId", "").trim()
                                .ifEmpty { AiProvider.CUSTOM_OPENAI.defaultModelId },
                            syncedModels = parseSyncedModelsJson(modelsRaw),
                            syncedModelsUpdatedAt = o.optLong("syncedModelsUpdatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomEndpoints(endpoints: List<CustomEndpointProfile>) {
        prefs.edit().putString(KEY_CUSTOM_ENDPOINTS_JSON, serializeCustomEndpoints(endpoints)).apply()
    }

    fun loadActiveCustomEndpointId(): String? {
        val saved = prefs.getString(KEY_ACTIVE_CUSTOM_ENDPOINT_ID, null)?.trim().orEmpty()
        val list = loadCustomEndpoints()
        if (list.isEmpty()) return null
        return if (saved.isNotEmpty() && list.any { it.id == saved }) saved else list.first().id
    }

    fun saveActiveCustomEndpointId(endpointId: String?) {
        val clean = endpointId?.trim().orEmpty()
        val editor = prefs.edit()
        if (clean.isEmpty()) {
            editor.remove(KEY_ACTIVE_CUSTOM_ENDPOINT_ID)
        } else {
            editor.putString(KEY_ACTIVE_CUSTOM_ENDPOINT_ID, clean)
        }
        editor.apply()
    }

    fun upsertCustomEndpoint(endpoint: CustomEndpointProfile): List<CustomEndpointProfile> {
        val cleanUrl = AiProvider.normalizeBaseUrl(endpoint.baseUrl)
        if (cleanUrl.isEmpty()) return loadCustomEndpoints()
        val normalized = endpoint.copy(
            id = endpoint.id.trim().ifEmpty { generateCustomEndpointId() },
            name = endpoint.name.trim().ifEmpty { AiProvider.deriveCustomEndpointName(cleanUrl) },
            baseUrl = cleanUrl,
            apiKey = AiResponseParser.sanitizeApiKey(endpoint.apiKey),
            modelId = endpoint.effectiveModelId
        )
        val current = loadCustomEndpoints().toMutableList()
        val idx = current.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) {
            current[idx] = normalized
        } else {
            current.add(normalized)
        }
        saveCustomEndpoints(current)
        return current
    }

    fun deleteCustomEndpoint(endpointId: String): List<CustomEndpointProfile> {
        val cleanId = endpointId.trim()
        val current = loadCustomEndpoints()
        val updated = current.filterNot { it.id == cleanId }
        val activeId = prefs.getString(KEY_ACTIVE_CUSTOM_ENDPOINT_ID, null)?.trim()
        val nextActiveId = if (activeId == cleanId) updated.firstOrNull()?.id else activeId
        val editor = prefs.edit()
            .putString(KEY_CUSTOM_ENDPOINTS_JSON, serializeCustomEndpoints(updated))
            .remove(scopedKey(AiProvider.CUSTOM_OPENAI, "token_ledgers", cleanId))
            .remove(scopedKey(AiProvider.CUSTOM_OPENAI, "token_snapshots_v1", cleanId))
        if (nextActiveId.isNullOrBlank()) {
            editor.remove(KEY_ACTIVE_CUSTOM_ENDPOINT_ID)
        } else {
            editor.putString(KEY_ACTIVE_CUSTOM_ENDPOINT_ID, nextActiveId)
        }
        editor.apply()
        return updated
    }

    private fun serializeCustomEndpoints(endpoints: List<CustomEndpointProfile>): String {
        val arr = JSONArray()
        endpoints.forEach { ep ->
            arr.put(
                JSONObject().apply {
                    put("id", ep.id)
                    put("name", ep.displayName)
                    put("baseUrl", ep.effectiveBaseUrl)
                    put("apiKey", ep.trimmedKey)
                    put("modelId", ep.effectiveModelId)
                    put("syncedModels", JSONArray(serializeSyncedModels(ep.syncedModels)))
                    put("syncedModelsUpdatedAt", ep.syncedModelsUpdatedAt)
                }
            )
        }
        return arr.toString()
    }

    fun loadActiveProvider(): AiProvider =
        AiProvider.fromKey(prefs.getString(KEY_ACTIVE_PROVIDER, AiProvider.GOOGLE_ACCOUNT.key))

    fun saveActiveProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.key).apply()
    }

    fun loadProfile(provider: AiProvider): AiProviderProfile {
        val rawModels = prefs.getString(providerKey(provider, "synced_models"), null).orEmpty()
        val models = parseSyncedModelsJson(rawModels)
        val storedModel = prefs.getString(providerKey(provider, "model_id"), "")
            .orEmpty()
            .ifBlank { provider.defaultModelId }
        return AiProviderProfile(
            provider = provider,
            apiKey = prefs.getString(providerKey(provider, "api_key"), "").orEmpty(),
            baseUrl = prefs.getString(providerKey(provider, "base_url"), "").orEmpty(),
            modelId = storedModel,
            syncedModels = models,
            syncedModelsUpdatedAt = prefs.getLong(providerKey(provider, "synced_at"), 0L)
        )
    }

    fun loadAllProfiles(): Map<AiProvider, AiProviderProfile> =
        AiProvider.entries.associateWith { loadProfile(it) }

    fun saveProfile(profile: AiProviderProfile) {
        val p = profile.provider
        val editor = prefs.edit()
            .putString(providerKey(p, "api_key"), profile.trimmedKey)
            .putString(providerKey(p, "base_url"), profile.effectiveBaseUrl)
            .putString(providerKey(p, "model_id"), profile.effectiveModelId)
        if (profile.syncedModels.isNotEmpty() || profile.syncedModelsUpdatedAt > 0L) {
            editor.putString(providerKey(p, "synced_models"), serializeSyncedModels(profile.syncedModels))
            editor.putLong(providerKey(p, "synced_at"), profile.syncedModelsUpdatedAt)
        }
        // Keep legacy Google fields in sync for backward compatibility
        if (p == AiProvider.GOOGLE_API_KEY) {
            editor.putString(KEY_API_KEY, profile.trimmedKey)
            editor.putString(KEY_MODEL_ID, profile.effectiveModelId)
        } else if (p == AiProvider.GOOGLE_ACCOUNT) {
            editor.putString(KEY_MODEL_ID, profile.effectiveModelId)
        }
        editor.apply()
    }

    fun clearProviderKey(provider: AiProvider): AiProviderProfile {
        val current = loadProfile(provider)
        val editor = prefs.edit().remove(providerKey(provider, "api_key"))
        if (provider == AiProvider.CUSTOM_OPENAI) {
            editor.remove(providerKey(provider, "base_url"))
        }
        if (provider == AiProvider.GOOGLE_API_KEY) {
            editor.remove(KEY_API_KEY)
            editor.putString(KEY_PROVIDER_MODE, AiProviderMode.GOOGLE_GEMINI.key)
        }
        editor.apply()
        return current.copy(
            apiKey = "",
            baseUrl = if (provider == AiProvider.CUSTOM_OPENAI) "" else current.baseUrl
        )
    }

    fun loadSyncedModels(provider: AiProvider, customEndpointId: String? = null): List<AiSyncedModel> {
        if (provider == AiProvider.CUSTOM_OPENAI && !customEndpointId.isNullOrBlank()) {
            return loadCustomEndpoints().firstOrNull { it.id == customEndpointId }?.syncedModels.orEmpty()
        }
        val raw = prefs.getString(providerKey(provider, "synced_models"), null).orEmpty()
        return parseSyncedModelsJson(raw)
    }

    fun loadSyncedModelsUpdatedAt(provider: AiProvider, customEndpointId: String? = null): Long {
        if (provider == AiProvider.CUSTOM_OPENAI && !customEndpointId.isNullOrBlank()) {
            return loadCustomEndpoints().firstOrNull { it.id == customEndpointId }?.syncedModelsUpdatedAt ?: 0L
        }
        return prefs.getLong(providerKey(provider, "synced_at"), 0L)
    }

    fun saveSyncedModels(
        provider: AiProvider,
        models: List<AiSyncedModel>,
        updatedAtMillis: Long = System.currentTimeMillis(),
        customEndpointId: String? = null
    ) {
        if (provider == AiProvider.CUSTOM_OPENAI && !customEndpointId.isNullOrBlank()) {
            val existing = loadCustomEndpoints().firstOrNull { it.id == customEndpointId }
            if (existing != null) {
                upsertCustomEndpoint(
                    existing.copy(
                        syncedModels = models,
                        syncedModelsUpdatedAt = updatedAtMillis
                    )
                )
            }
            return
        }
        val serialized = serializeSyncedModels(models)
        val editor = prefs.edit()
            .putString(providerKey(provider, "synced_models"), serialized)
            .putLong(providerKey(provider, "synced_at"), updatedAtMillis)
        if (provider == AiProvider.GOOGLE_API_KEY || provider == AiProvider.GOOGLE_ACCOUNT) {
            editor.putString(KEY_SYNCED_MODELS, serialized)
            editor.putLong(KEY_SYNCED_MODELS_AT, updatedAtMillis)
        }
        editor.apply()
    }

    fun addCustomModel(
        provider: AiProvider,
        rawModelId: String,
        customEndpointId: String? = null
    ): List<AiSyncedModel> {
        val cleanId = rawModelId.trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
            .trim()
        if (cleanId.isEmpty()) return loadSyncedModels(provider, customEndpointId)
        val existing = loadSyncedModels(provider, customEndpointId).ifEmpty { provider.defaultPresetModels() }
        val withoutDup = existing.filterNot { it.modelId.equals(cleanId, ignoreCase = true) }
        val presetTitle = if (provider == AiProvider.GOOGLE_ACCOUNT || provider == AiProvider.GOOGLE_API_KEY) {
            AiModelPreset.fromId(cleanId)?.title
        } else null
        val presetSubtitle = if (provider == AiProvider.GOOGLE_ACCOUNT || provider == AiProvider.GOOGLE_API_KEY) {
            AiModelPreset.fromId(cleanId)?.subtitle
        } else null
        val customEntry = AiSyncedModel(
            modelId = cleanId,
            displayName = presetTitle ?: AiResponseParser.formatModelDisplayName(cleanId),
            description = presetSubtitle ?: "Mô hình tùy chỉnh do bạn thêm (${provider.shortTitle})",
            source = "custom"
        )
        val updated = listOf(customEntry) + withoutDup
        saveSyncedModels(provider, updated, customEndpointId = customEndpointId)
        return updated
    }

    fun removeCustomModel(
        provider: AiProvider,
        modelId: String,
        customEndpointId: String? = null
    ): List<AiSyncedModel> {
        val updated = loadSyncedModels(provider, customEndpointId).filterNot {
            it.source == "custom" && it.modelId.equals(modelId.trim(), ignoreCase = true)
        }
        saveSyncedModels(provider, updated, customEndpointId = customEndpointId)
        return updated
    }

    fun loadTokenLedgers(
        provider: AiProvider,
        today: String = currentDayKey(),
        customEndpointId: String? = null
    ): Map<String, AiModelTokenLedger> {
        val raw = prefs.getString(scopedKey(provider, "token_ledgers", customEndpointId), null).orEmpty()
        return parseTokenLedgersJson(raw, today)
    }

    fun recordTokenUsage(
        provider: AiProvider,
        modelId: String,
        usage: AiTokenUsage,
        today: String = currentDayKey(),
        nowMillis: Long = System.currentTimeMillis(),
        customEndpointId: String? = null
    ): Map<String, AiModelTokenLedger> {
        val cleanId = modelId.trim().removePrefix("publishers/google/models/").removePrefix("models/")
        if (cleanId.isEmpty() || !usage.hasData) return loadTokenLedgers(provider, today, customEndpointId)
        val currentMap = loadTokenLedgers(provider, today, customEndpointId).toMutableMap()
        val key = cleanId.lowercase()
        val prev = currentMap[key]
        val turnTotal = usage.totalTokens.takeIf { it > 0 } ?: (usage.promptTokens + usage.outputTokens + usage.thoughtsTokens)
        val updated = AiModelTokenLedger(
            modelId = cleanId,
            dayKey = today,
            usedTokensToday = (prev?.usedTokensToday ?: 0L) + turnTotal.toLong(),
            promptTokensToday = (prev?.promptTokensToday ?: 0L) + usage.promptTokens.toLong(),
            outputTokensToday = (prev?.outputTokensToday ?: 0L) + usage.outputTokens.toLong(),
            requestsToday = (prev?.requestsToday ?: 0) + 1,
            lastPromptTokens = usage.promptTokens,
            lastOutputTokens = usage.outputTokens,
            lastTotalTokens = turnTotal,
            lastUpdatedAtMillis = nowMillis
        )
        currentMap[key] = updated
        saveTokenLedgers(provider, currentMap, customEndpointId)
        return currentMap
    }

    fun loadTokenSnapshots(
        provider: AiProvider,
        customEndpointId: String? = null
    ): Map<String, AiPersistedTokenSnapshot> {
        val raw = prefs.getString(scopedKey(provider, "token_snapshots_v1", customEndpointId), null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = LinkedHashMap<String, AiPersistedTokenSnapshot>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("modelId").trim()
                if (id.isEmpty()) continue
                map[id.lowercase()] = AiPersistedTokenSnapshot(
                    modelId = id,
                    inputTokenLimit = o.optInt("inputTokenLimit", 0),
                    outputTokenLimit = o.optInt("outputTokenLimit", 0),
                    currentContextTokens = o.optInt("currentContextTokens", 0),
                    isServerVerified = o.optBoolean("isServerVerified", false),
                    verificationSource = o.optString("verificationSource", ""),
                    rateLimitRemainingTokens = if (o.has("rateLimitRemainingTokens") && !o.isNull("rateLimitRemainingTokens")) {
                        o.optLong("rateLimitRemainingTokens")
                    } else null,
                    rateLimitTotalTokens = if (o.has("rateLimitTotalTokens") && !o.isNull("rateLimitTotalTokens")) {
                        o.optLong("rateLimitTotalTokens")
                    } else null,
                    rateLimitRemainingRequests = if (o.has("rateLimitRemainingRequests") && !o.isNull("rateLimitRemainingRequests")) {
                        o.optLong("rateLimitRemainingRequests")
                    } else null,
                    rateLimitTotalRequests = if (o.has("rateLimitTotalRequests") && !o.isNull("rateLimitTotalRequests")) {
                        o.optLong("rateLimitTotalRequests")
                    } else null,
                    accountBalanceLabel = o.optString("accountBalanceLabel", "").trim().ifEmpty { null },
                    lastSyncedAtMillis = o.optLong("lastSyncedAtMillis", 0L)
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun saveTokenSnapshot(
        provider: AiProvider,
        snapshot: AiPersistedTokenSnapshot,
        customEndpointId: String? = null
    ) {
        val cleanId = snapshot.modelId.trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
        if (cleanId.isEmpty()) return
        val currentMap = loadTokenSnapshots(provider, customEndpointId).toMutableMap()
        currentMap[cleanId.lowercase()] = snapshot.copy(modelId = cleanId)
        val arr = JSONArray()
        currentMap.values.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("modelId", s.modelId)
                    put("inputTokenLimit", s.inputTokenLimit)
                    put("outputTokenLimit", s.outputTokenLimit)
                    put("currentContextTokens", s.currentContextTokens)
                    put("isServerVerified", s.isServerVerified)
                    put("verificationSource", s.verificationSource)
                    if (s.rateLimitRemainingTokens != null) put("rateLimitRemainingTokens", s.rateLimitRemainingTokens)
                    if (s.rateLimitTotalTokens != null) put("rateLimitTotalTokens", s.rateLimitTotalTokens)
                    if (s.rateLimitRemainingRequests != null) put("rateLimitRemainingRequests", s.rateLimitRemainingRequests)
                    if (s.rateLimitTotalRequests != null) put("rateLimitTotalRequests", s.rateLimitTotalRequests)
                    if (!s.accountBalanceLabel.isNullOrBlank()) put("accountBalanceLabel", s.accountBalanceLabel)
                    put("lastSyncedAtMillis", s.lastSyncedAtMillis)
                }
            )
        }
        prefs.edit().putString(scopedKey(provider, "token_snapshots_v1", customEndpointId), arr.toString()).apply()
    }

    fun loadChatMessages(): List<AiChatMessage> {
        val raw = prefs.getString(KEY_DOCS_CHAT_HISTORY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val text = o.optString("text", "")
                    if (id.isEmpty() || text.isBlank()) continue
                    val pagesArr = o.optJSONArray("suggestedPages")
                    val pages = buildList {
                        if (pagesArr != null) {
                            for (j in 0 until pagesArr.length()) {
                                val p = pagesArr.optJSONObject(j) ?: continue
                                val pageNum = p.optInt("page", 0)
                                if (pageNum in 1..PDF_TOTAL_PAGES) {
                                    add(
                                        AiPdfPageSuggestion(
                                            page = pageNum,
                                            title = p.optString("title", "Trang $pageNum"),
                                            reason = p.optString("reason", "")
                                        )
                                    )
                                }
                            }
                        }
                    }
                    val attached = o.optInt("attachedPdfPage", 0).takeIf { it in 1..PDF_TOTAL_PAGES }
                    add(
                        AiChatMessage(
                            id = id,
                            isUser = o.optBoolean("isUser", false),
                            text = text,
                            suggestedPages = pages,
                            attachedPdfPage = attached,
                            isError = o.optBoolean("isError", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveChatMessages(messages: List<AiChatMessage>) {
        val trimmed = messages.filter { it.text.isNotBlank() }.takeLast(40)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_DOCS_CHAT_HISTORY).apply()
            return
        }
        val arr = JSONArray()
        trimmed.forEach { msg ->
            arr.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("isUser", msg.isUser)
                    put("text", msg.text)
                    put("isError", msg.isError)
                    if (msg.attachedPdfPage != null) put("attachedPdfPage", msg.attachedPdfPage)
                    if (msg.suggestedPages.isNotEmpty()) {
                        val pArr = JSONArray()
                        msg.suggestedPages.forEach { p ->
                            pArr.put(
                                JSONObject().apply {
                                    put("page", p.page)
                                    put("title", p.title)
                                    put("reason", p.reason)
                                }
                            )
                        }
                        put("suggestedPages", pArr)
                    }
                }
            )
        }
        prefs.edit().putString(KEY_DOCS_CHAT_HISTORY, arr.toString()).apply()
    }

    // Legacy single-provider convenience wrappers (preserve backward compatibility for callers & unit tests)
    fun load(): AiKeyConfig {
        val active = loadActiveProvider()
        val profile = loadProfile(active)
        val googleKeyProfile = loadProfile(AiProvider.GOOGLE_API_KEY)
        return AiKeyConfig(
            apiKey = if (active == AiProvider.GOOGLE_ACCOUNT) googleKeyProfile.apiKey else profile.apiKey,
            modelId = profile.effectiveModelId,
            providerMode = if (active == AiProvider.GOOGLE_ACCOUNT) AiProviderMode.GOOGLE_GEMINI else AiProviderMode.PERSONAL_API_KEY
        )
    }

    fun save(config: AiKeyConfig) {
        val targetProvider = if (config.providerMode == AiProviderMode.PERSONAL_API_KEY) {
            AiProvider.GOOGLE_API_KEY
        } else {
            AiProvider.GOOGLE_ACCOUNT
        }
        saveActiveProvider(targetProvider)
        val current = loadProfile(targetProvider)
        saveProfile(current.copy(apiKey = config.trimmedKey, modelId = config.effectiveModelId))
    }

    fun clearKey() {
        clearProviderKey(AiProvider.GOOGLE_API_KEY)
        saveActiveProvider(AiProvider.GOOGLE_ACCOUNT)
    }

    fun loadSyncedModels(): List<AiSyncedModel> = loadSyncedModels(loadActiveProvider())

    fun loadSyncedModelsUpdatedAt(): Long = loadSyncedModelsUpdatedAt(loadActiveProvider())

    fun saveSyncedModels(models: List<AiSyncedModel>, updatedAtMillis: Long = System.currentTimeMillis()) =
        saveSyncedModels(loadActiveProvider(), models, updatedAtMillis)

    fun addCustomModel(rawModelId: String): List<AiSyncedModel> =
        addCustomModel(loadActiveProvider(), rawModelId)

    fun removeCustomModel(modelId: String): List<AiSyncedModel> =
        removeCustomModel(loadActiveProvider(), modelId)

    fun loadTokenLedgers(today: String = currentDayKey()): Map<String, AiModelTokenLedger> =
        loadTokenLedgers(loadActiveProvider(), today)

    fun recordTokenUsage(
        modelId: String,
        usage: AiTokenUsage,
        today: String = currentDayKey(),
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, AiModelTokenLedger> =
        recordTokenUsage(loadActiveProvider(), modelId, usage, today, nowMillis)

    private fun parseSyncedModelsJson(raw: String): List<AiSyncedModel> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("modelId").trim()
                    if (id.isEmpty()) continue
                    add(
                        AiSyncedModel(
                            modelId = id,
                            displayName = o.optString("displayName").trim().ifEmpty {
                                AiResponseParser.formatModelDisplayName(id)
                            },
                            description = o.optString("description").trim(),
                            inputTokenLimit = o.optInt("inputTokenLimit", 0),
                            outputTokenLimit = o.optInt("outputTokenLimit", 0),
                            source = o.optString("source", "api").trim().ifEmpty { "api" }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeSyncedModels(models: List<AiSyncedModel>): String {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("modelId", m.modelId)
                    put("displayName", m.displayName)
                    put("description", m.description)
                    put("inputTokenLimit", m.inputTokenLimit)
                    put("outputTokenLimit", m.outputTokenLimit)
                    put("source", m.source)
                }
            )
        }
        return arr.toString()
    }

    private fun parseTokenLedgersJson(raw: String, today: String): Map<String, AiModelTokenLedger> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = LinkedHashMap<String, AiModelTokenLedger>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("modelId").trim()
                val day = o.optString("dayKey").trim()
                if (id.isEmpty() || day != today) continue
                map[id.lowercase()] = AiModelTokenLedger(
                    modelId = id,
                    dayKey = day,
                    usedTokensToday = o.optLong("usedTokensToday", 0L),
                    promptTokensToday = o.optLong("promptTokensToday", 0L),
                    outputTokensToday = o.optLong("outputTokensToday", 0L),
                    requestsToday = o.optInt("requestsToday", 0),
                    lastPromptTokens = o.optInt("lastPromptTokens", 0),
                    lastOutputTokens = o.optInt("lastOutputTokens", 0),
                    lastTotalTokens = o.optInt("lastTotalTokens", 0),
                    lastUpdatedAtMillis = o.optLong("lastUpdatedAtMillis", 0L)
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun saveTokenLedgers(
        provider: AiProvider,
        map: Map<String, AiModelTokenLedger>,
        customEndpointId: String? = null
    ) {
        val arr = JSONArray()
        map.values.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("modelId", entry.modelId)
                    put("dayKey", entry.dayKey)
                    put("usedTokensToday", entry.usedTokensToday)
                    put("promptTokensToday", entry.promptTokensToday)
                    put("outputTokensToday", entry.outputTokensToday)
                    put("requestsToday", entry.requestsToday)
                    put("lastPromptTokens", entry.lastPromptTokens)
                    put("lastOutputTokens", entry.lastOutputTokens)
                    put("lastTotalTokens", entry.lastTotalTokens)
                    put("lastUpdatedAtMillis", entry.lastUpdatedAtMillis)
                }
            )
        }
        val serialized = arr.toString()
        val editor = prefs.edit().putString(scopedKey(provider, "token_ledgers", customEndpointId), serialized)
        if (provider == AiProvider.GOOGLE_API_KEY || provider == AiProvider.GOOGLE_ACCOUNT) {
            editor.putString(KEY_TOKEN_LEDGERS, serialized)
        }
        editor.apply()
    }

    companion object {
        private const val KEY_API_KEY = "gemini_user_api_key"
        private const val KEY_MODEL_ID = "gemini_model_id"
        private const val KEY_PROVIDER_MODE = "gemini_provider_mode"
        private const val KEY_SYNCED_MODELS = "gemini_synced_models_v1"
        private const val KEY_SYNCED_MODELS_AT = "gemini_synced_models_updated_at"
        private const val KEY_TOKEN_LEDGERS = "gemini_token_ledgers_v1"
        private const val KEY_ACTIVE_PROVIDER = "ai_active_provider_v2"
        private const val KEY_MULTI_PROVIDER_MIGRATED = "ai_multi_provider_migrated_v2"
        private const val KEY_CUSTOM_ENDPOINTS_JSON = "ai_custom_endpoints_v1"
        private const val KEY_ACTIVE_CUSTOM_ENDPOINT_ID = "ai_active_custom_endpoint_id_v1"
        private const val KEY_CUSTOM_ENDPOINTS_MIGRATED = "ai_custom_endpoints_migrated_v1"
        private const val KEY_DOCS_CHAT_HISTORY = "ai_docs_chat_history_v1"

        fun currentDayKey(nowMillis: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
    }
}

data class AiPdfPageSuggestion(
    val page: Int,
    val title: String,
    val reason: String
)

data class AiDocsReply(
    val answer: String,
    val suggestedPages: List<AiPdfPageSuggestion> = emptyList(),
    val tokenUsage: AiTokenUsage? = null
)

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val suggestedPages: List<AiPdfPageSuggestion> = emptyList(),
    val attachedPdfPage: Int? = null,
    val isError: Boolean = false
)

enum class AiDrawTarget {
    SPACE_3D,
    PLANE_2D
}

data class Ai3DEdgePointSpec(
    val name: String,
    val from: String,
    val to: String,
    val ratio: Float
)

data class Ai3DXyzPointSpec(
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float
)

data class Ai3DLineSpec(
    val p1: String,
    val p2: String,
    val style: LineStyle = LineStyle.SOLID,
    val colorHex: String = "",
    val highlighted: Boolean = false
)

data class Ai3DSceneSpec(
    val shapeKey: String,
    val a: Float = 4f,
    val b: Float = 3f,
    val h: Float = 4.8f,
    val showAxes: Boolean = false,
    val showAltitude: Boolean = false,
    val originPoint: String? = null,
    val edgePoints: List<Ai3DEdgePointSpec> = emptyList(),
    val xyzPoints: List<Ai3DXyzPointSpec> = emptyList(),
    val lines: List<Ai3DLineSpec> = emptyList(),
    val planes: List<List<String>> = emptyList(),
    val dynamicSection: Triple<String, String, String>? = null
)

data class Ai2DPointSpec(
    val name: String,
    val x: Float,
    val y: Float,
    val colorHex: String = ""
)

data class Ai2DObjectSpec(
    val kind: String, // segment, line, ray, vector, circle, polygon, angle
    val p1: String? = null,
    val p2: String? = null,
    val p3: String? = null,
    val radius: Float? = null,
    val pointNames: List<String> = emptyList(),
    val dashed: Boolean = false,
    val colorHex: String = "",
    val highlighted: Boolean = false
)

data class Ai2DFunctionSpec(
    val expression: String,
    val showDerivative: Boolean = false,
    val colorHex: String = "#38BDF8"
)

data class Ai2DSceneSpec(
    val points: List<Ai2DPointSpec> = emptyList(),
    val objects: List<Ai2DObjectSpec> = emptyList(),
    val functions: List<Ai2DFunctionSpec> = emptyList(),
    val paramM: Double = 1.0
)

data class AiDrawingPlan(
    val target: AiDrawTarget,
    val title: String,
    val problemText: String,
    val drawingSummary: String,
    val solutionHint: String,
    val scene3D: Ai3DSceneSpec? = null,
    val scene2D: Ai2DSceneSpec? = null,
    val relatedPages: List<AiPdfPageSuggestion> = emptyList(),
    val tokenUsage: AiTokenUsage? = null
)

/**
 * Robust JSON parsing & scene conversion between Gemini structured output and the app's real
 * 3D ([GeometrySnapshot]) and 2D ([Geo2DSnapshot]) engines.
 */
object AiResponseParser {

    private val defaultColors = listOf("#F43F5E", "#38BDF8", "#10B981", "#FBBF24", "#A78BFA", "#FB923C")

    fun extractJsonObject(rawText: String): JSONObject? {
        val trimmed = rawText.trim()
        val unwrapped = trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        runCatching { return JSONObject(unwrapped) }
        val start = unwrapped.indexOf('{')
        val end = unwrapped.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { return JSONObject(unwrapped.substring(start, end + 1)) }
        }
        return null
    }

    fun parseUsageMetadata(root: JSONObject?): AiTokenUsage? {
        val u = root?.optJSONObject("usageMetadata") ?: return null
        val prompt = u.optInt("promptTokenCount", 0).coerceAtLeast(0)
        val output = u.optInt("candidatesTokenCount", 0).coerceAtLeast(0)
        val thoughts = u.optInt("thoughtsTokenCount", 0).coerceAtLeast(0)
        val total = u.optInt("totalTokenCount", prompt + output + thoughts).coerceAtLeast(0)
        return if (total > 0 || prompt > 0 || output > 0) {
            AiTokenUsage(
                promptTokens = prompt,
                outputTokens = output,
                thoughtsTokens = thoughts,
                totalTokens = if (total > 0) total else (prompt + output + thoughts)
            )
        } else null
    }

    fun parseOpenAiUsage(
        root: JSONObject?,
        rateLimitRemaining: Long? = null,
        rateLimitTotal: Long? = null,
        rateLimitRemainingRequests: Long? = null,
        rateLimitTotalRequests: Long? = null
    ): AiTokenUsage? {
        val u = root?.optJSONObject("usage") ?: return null
        val prompt = u.optInt("prompt_tokens", u.optInt("input_tokens", 0)).coerceAtLeast(0)
        val output = u.optInt("completion_tokens", u.optInt("output_tokens", 0)).coerceAtLeast(0)
        val reasoning = u.optJSONObject("completion_tokens_details")
            ?.optInt("reasoning_tokens", 0)?.coerceAtLeast(0) ?: 0
        val total = u.optInt("total_tokens", prompt + output).coerceAtLeast(0)
        return if (total > 0 || prompt > 0 || output > 0) {
            AiTokenUsage(
                promptTokens = prompt,
                outputTokens = output,
                thoughtsTokens = reasoning,
                totalTokens = if (total > 0) total else (prompt + output),
                rateLimitRemainingTokens = rateLimitRemaining,
                rateLimitTotalTokens = rateLimitTotal,
                rateLimitRemainingRequests = rateLimitRemainingRequests,
                rateLimitTotalRequests = rateLimitTotalRequests
            )
        } else null
    }

    fun parseAnthropicUsage(
        usageObj: JSONObject?,
        rateLimitRemaining: Long? = null,
        rateLimitTotal: Long? = null,
        rateLimitRemainingRequests: Long? = null,
        rateLimitTotalRequests: Long? = null
    ): AiTokenUsage? {
        if (usageObj == null) return null
        val input = usageObj.optInt("input_tokens", 0).coerceAtLeast(0)
        val cacheCreate = usageObj.optInt("cache_creation_input_tokens", 0).coerceAtLeast(0)
        val cacheRead = usageObj.optInt("cache_read_input_tokens", 0).coerceAtLeast(0)
        val prompt = input + cacheCreate + cacheRead
        val output = usageObj.optInt("output_tokens", 0).coerceAtLeast(0)
        val total = prompt + output
        return if (total > 0) {
            AiTokenUsage(
                promptTokens = prompt,
                outputTokens = output,
                totalTokens = total,
                rateLimitRemainingTokens = rateLimitRemaining,
                rateLimitTotalTokens = rateLimitTotal,
                rateLimitRemainingRequests = rateLimitRemainingRequests,
                rateLimitTotalRequests = rateLimitTotalRequests
            )
        } else null
    }

    fun formatSyncTime(millis: Long): String {
        if (millis <= 0L) return ""
        return runCatching {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))
        }.getOrDefault("")
    }

    fun formatExactTokens(value: Long): String {
        val negative = value < 0L
        val digits = kotlin.math.abs(value).toString()
        val sb = StringBuilder()
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(digits[i])
        }
        return if (negative) "-$sb" else sb.toString()
    }

    fun formatCompactTokens(value: Long): String {
        val absVal = kotlin.math.abs(value)
        return when {
            absVal >= 1_000_000L -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
            absVal >= 10_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
            else -> formatExactTokens(value)
        }
    }

    fun formatModelDisplayName(modelId: String): String {
        val clean = modelId.trim()
            .removePrefix("publishers/google/models/")
            .removePrefix("models/")
        if (clean.isEmpty()) return modelId
        return clean.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                when (token.lowercase()) {
                    "exp" -> "Exp"
                    "it" -> "IT"
                    "tts" -> "TTS"
                    "ocr" -> "OCR"
                    else -> token.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                    }
                }
            }
    }

    /**
     * Parses live model catalog JSON from either:
     * - Gemini Developer API (`GET /v1beta/models`) -> `models[]`
     * - Vertex AI Publisher Models (`GET /v1beta1/publishers/google/models`) -> `publisherModels[]` / `models[]`
     * Filters to text/multimodal generation models (`generateContent`) and excludes embedding/image-only models.
     */
    fun parseGeminiModelsListJson(
        jsonBody: String,
        source: String = "api"
    ): Pair<List<AiSyncedModel>, String?> {
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return emptyList<AiSyncedModel>() to null
        val arr = root.optJSONArray("models")
            ?: root.optJSONArray("publisherModels")
            ?: return emptyList<AiSyncedModel>() to null
        val nextPageToken = root.optString("nextPageToken", "").trim().ifEmpty { null }

        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val rawName = item.optString("name", "").trim()
                val cleanId = rawName
                    .removePrefix("publishers/google/models/")
                    .removePrefix("models/")
                    .trim()
                if (cleanId.isEmpty()) continue

                val methodsArr = item.optJSONArray("supportedGenerationMethods")
                if (methodsArr != null) {
                    var supportsGenerateContent = false
                    for (j in 0 until methodsArr.length()) {
                        if (methodsArr.optString(j).equals("generateContent", ignoreCase = true)) {
                            supportsGenerateContent = true
                            break
                        }
                    }
                    if (!supportsGenerateContent) continue
                } else {
                    val lower = cleanId.lowercase()
                    if (lower.contains("embedding") || lower.startsWith("imagen") || lower.startsWith("veo") || lower.startsWith("aqa")) {
                        continue
                    }
                }

                // Exclude pure embedding/aqa/tts/robotics models even if generateContent appears in metadata
                val lowerId = cleanId.lowercase()
                if (lowerId.contains("embedding") || lowerId == "aqa") continue

                val displayName = item.optString("displayName", "").trim()
                    .ifEmpty { AiModelPreset.fromId(cleanId)?.title ?: formatModelDisplayName(cleanId) }
                val description = item.optString("description", "").trim()
                    .ifEmpty { AiModelPreset.fromId(cleanId)?.subtitle.orEmpty() }
                val inputLimit = item.optInt("inputTokenLimit", 0)
                val outputLimit = item.optInt("outputTokenLimit", 0)

                add(
                    AiSyncedModel(
                        modelId = cleanId,
                        displayName = displayName,
                        description = description,
                        inputTokenLimit = inputLimit,
                        outputTokenLimit = outputLimit,
                        source = source
                    )
                )
            }
        }
        return parsed to nextPageToken
    }

    /**
     * Parses OpenAI-compatible `GET /models` JSON (`data[]` or `models[]`), supporting OpenAI, OpenRouter,
     * Groq, DeepSeek, Together, Mistral, Ollama, and custom servers.
     */
    fun parseOpenAiModelsJson(
        jsonBody: String,
        provider: AiProvider
    ): List<AiSyncedModel> {
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: return emptyList()
        val nonChatTokens = listOf(
            "embedding", "embed", "whisper", "tts", "dall-e", "moderation",
            "rerank", "transcribe", "transcription", "babbage", "davinci",
            "text-search", "code-search", "realtime", "audio-preview", "sora",
            "flux", "stable-diffusion", "midjourney", "playai-tts", "distil-whisper",
            "guard"
        )

        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                    .ifEmpty { item.optString("name", "").trim() }
                if (id.isEmpty()) continue
                val lower = id.lowercase()
                if (nonChatTokens.any { lower.contains(it) }) continue
                if (item.has("active") && !item.optBoolean("active", true)) continue

                val contextLen = item.optInt("context_length", 0)
                    .takeIf { it > 0 }
                    ?: item.optInt("context_window", 0).takeIf { it > 0 }
                    ?: item.optInt("max_model_len", 0).takeIf { it > 0 }
                    ?: item.optInt("input_token_limit", 0).takeIf { it > 0 }
                    ?: inferKnownContextWindow(id, provider)

                val topProvider = item.optJSONObject("top_provider")
                val outputLen = topProvider?.optInt("max_completion_tokens", 0)?.takeIf { it > 0 }
                    ?: item.optInt("max_completion_tokens", 0).takeIf { it > 0 }
                    ?: item.optInt("max_output_tokens", 0).takeIf { it > 0 }
                    ?: 0

                val pricing = item.optJSONObject("pricing")
                val isFreeOpenRouter = provider == AiProvider.OPENROUTER && (
                    lower.endsWith(":free") ||
                        (pricing != null && pricing.optString("prompt", "") == "0" && pricing.optString("completion", "") == "0")
                    )

                val rawName = item.optString("name", "").trim()
                val displayName = if (rawName.isNotEmpty() && !rawName.equals(id, ignoreCase = true)) {
                    rawName
                } else {
                    formatModelDisplayName(id.substringAfterLast('/'))
                }
                val rawDesc = item.optString("description", "").trim()
                val owner = item.optString("owned_by", "").trim()
                val description = when {
                    rawDesc.isNotEmpty() -> rawDesc.take(140)
                    isFreeOpenRouter -> "Miễn phí trên OpenRouter"
                    owner.isNotEmpty() -> "Nhà phát hành: $owner"
                    else -> ""
                }

                add(
                    AiSyncedModel(
                        modelId = id,
                        displayName = displayName,
                        description = description,
                        inputTokenLimit = contextLen,
                        outputTokenLimit = outputLen,
                        source = if (isFreeOpenRouter) "free" else provider.key
                    )
                )
            }
        }
    }

    /**
     * Parses Anthropic `GET /v1/models` JSON (`data[]`).
     */
    fun parseAnthropicModelsJson(jsonBody: String): Pair<List<AiSyncedModel>, String?> {
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return emptyList<AiSyncedModel>() to null
        val arr = root.optJSONArray("data") ?: return emptyList<AiSyncedModel>() to null
        val hasMore = root.optBoolean("has_more", false)
        val lastId = if (hasMore) root.optString("last_id", "").trim().ifEmpty { null } else null

        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isEmpty()) continue
                val displayName = item.optString("display_name", "").trim()
                    .ifEmpty { formatModelDisplayName(id) }
                val inputLimit = item.optInt("max_input_tokens", 0)
                    .takeIf { it > 0 } ?: 200_000
                val outputLimit = item.optInt("max_tokens", 0)
                    .takeIf { it > 0 } ?: 8_192
                val createdAt = item.optString("created_at", "").trim().take(10)
                add(
                    AiSyncedModel(
                        modelId = id,
                        displayName = displayName,
                        description = if (createdAt.isNotEmpty()) "Anthropic Claude · Phát hành $createdAt" else "Anthropic Claude",
                        inputTokenLimit = inputLimit,
                        outputTokenLimit = outputLimit,
                        source = AiProvider.ANTHROPIC.key
                    )
                )
            }
        }
        return parsed to lastId
    }

    internal fun inferKnownContextWindow(modelId: String, provider: AiProvider): Int {
        val id = modelId.lowercase()
        return when {
            id.contains("gemini") -> 1_048_576
            id.contains("gpt-4.1") -> 1_047_576
            id.contains("gpt-4o") || id.contains("gpt-4-turbo") -> 128_000
            id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4") -> 200_000
            id.contains("claude") -> 200_000
            id.contains("deepseek") -> 65_536
            id.contains("llama-4") || id.contains("llama-3.3") || id.contains("llama-3.1") -> 131_072
            provider == AiProvider.GOOGLE_ACCOUNT || provider == AiProvider.GOOGLE_API_KEY -> 1_048_576
            provider == AiProvider.ANTHROPIC -> 200_000
            else -> 0
        }
    }

    /**
     * Deduplicates and sorts synced models per provider so the most capable, current chat models appear first.
     */
    fun sortSyncedModels(
        models: List<AiSyncedModel>,
        provider: AiProvider = AiProvider.GOOGLE_API_KEY
    ): List<AiSyncedModel> {
        val dedup = LinkedHashMap<String, AiSyncedModel>()
        for (m in models) {
            val key = m.modelId.trim().lowercase()
            if (key.isEmpty()) continue
            val existing = dedup[key]
            if (existing == null || (existing.source == "preset" && m.source != "preset")) {
                dedup[key] = m
            }
        }
        val versionRegex = Regex("""(\d+)(?:\.(\d+))?""")
        if (provider == AiProvider.GOOGLE_ACCOUNT || provider == AiProvider.GOOGLE_API_KEY) {
            return dedup.values.sortedWith(
                compareByDescending<AiSyncedModel> { it.source == "custom" }
                    .thenByDescending { it.modelId.lowercase().startsWith("gemini") }
                    .thenByDescending { m ->
                        val match = versionRegex.find(m.modelId)
                        val major = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val minor = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                        major * 100 + minor
                    }
                    .thenByDescending { m ->
                        val low = m.modelId.lowercase()
                        when {
                            low.contains("latest") -> 1
                            low.contains("exp") || low.contains("preview") -> 2
                            low.contains("lite") -> 3
                            low.contains("flash") -> 5
                            low.contains("pro") || low.contains("argon") -> 4
                            else -> 2
                        }
                    }
                    .thenBy { it.modelId.lowercase() }
            )
        }

        return dedup.values.sortedWith(
            compareByDescending<AiSyncedModel> { it.source == "custom" }
                .thenByDescending { m ->
                    val id = m.modelId.lowercase()
                    when (provider) {
                        AiProvider.OPENAI -> when {
                            id == "gpt-4o-mini" || id == "gpt-4.1-mini" -> 10
                            id == "gpt-4o" || id == "gpt-4.1" -> 9
                            id.startsWith("gpt-5") -> 11
                            id.startsWith("o4") || id.startsWith("o3") -> 8
                            id.startsWith("gpt-4") -> 7
                            else -> 2
                        }
                        AiProvider.OPENROUTER -> when {
                            m.source == "free" || id.endsWith(":free") -> 8
                            id.startsWith("openai/gpt-4o") || id.startsWith("anthropic/claude") -> 7
                            id.startsWith("deepseek/") -> 6
                            else -> 3
                        }
                        AiProvider.GROQ -> when {
                            id.contains("llama-4") -> 10
                            id.contains("llama-3.3-70b") -> 9
                            id.contains("qwen") -> 8
                            id.contains("llama-3.1") -> 7
                            else -> 3
                        }
                        AiProvider.DEEPSEEK -> when {
                            id == "deepseek-chat" -> 10
                            id == "deepseek-reasoner" -> 9
                            else -> 5
                        }
                        AiProvider.ANTHROPIC -> when {
                            id.contains("sonnet") -> 10
                            id.contains("opus") -> 9
                            id.contains("haiku") -> 8
                            else -> 4
                        }
                        else -> if (m.inputTokenLimit > 0) 5 else 2
                    }
                }
                .thenByDescending { it.inputTokenLimit }
                .thenBy { it.modelId.lowercase() }
        )
    }

    /**
     * Cleans user-pasted API keys by stripping accidental env prefixes (`OPENAI_API_KEY=`, `GEMINI_API_KEY=`,
     * `ANTHROPIC_API_KEY=`, `Bearer `), surrounding quotes, and embedded whitespace/newlines.
     */
    fun sanitizeApiKey(raw: String): String {
        var cleaned = raw.trim()
        val prefixRegex = Regex(
            """^(?:export\s+)?(?:GEMINI_API_KEY|GOOGLE_API_KEY|OPENAI_API_KEY|OPENROUTER_API_KEY|GROQ_API_KEY|DEEPSEEK_API_KEY|ANTHROPIC_API_KEY|CLAUDE_API_KEY|API_KEY)\s*=\s*""",
            RegexOption.IGNORE_CASE
        )
        cleaned = cleaned.replaceFirst(prefixRegex, "").trim()
        cleaned = cleaned.replaceFirst(Regex("""^Bearer\s+""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.trim('"', '\'', '`', ' ')
        return cleaned.replace(Regex("""\s+"""), "")
    }

    /**
     * Selects the best default text+vision chat model from a synced model list, avoiding
     * specialized audio/TTS/image/video/robotics/embedding variants.
     */
    fun pickBestChatModel(
        models: List<AiSyncedModel>,
        provider: AiProvider = AiProvider.GOOGLE_API_KEY
    ): AiSyncedModel? {
        if (models.isEmpty()) return null
        val sorted = sortSyncedModels(models, provider)
        val excludedTokens = listOf(
            "tts", "audio", "image", "imagen", "veo", "lyria", "embedding",
            "robotics", "computer-use", "live", "aqa", "whisper", "dall-e", "moderation", "sora"
        )
        fun isStandardChatModel(m: AiSyncedModel): Boolean {
            val id = m.modelId.lowercase()
            return excludedTokens.none { id.contains(it) }
        }
        if (provider == AiProvider.GOOGLE_ACCOUNT || provider == AiProvider.GOOGLE_API_KEY) {
            return sorted.firstOrNull { m ->
                val id = m.modelId.lowercase()
                id.startsWith("gemini") && id.contains("flash") && !id.contains("lite") && isStandardChatModel(m)
            } ?: sorted.firstOrNull { m ->
                val id = m.modelId.lowercase()
                id.startsWith("gemini") && id.contains("flash") && isStandardChatModel(m)
            } ?: sorted.firstOrNull { m ->
                m.modelId.lowercase().startsWith("gemini") && isStandardChatModel(m)
            } ?: sorted.firstOrNull { isStandardChatModel(it) }
                ?: sorted.first()
        }
        return sorted.firstOrNull { isStandardChatModel(it) } ?: sorted.first()
    }

    /**
     * Reorders models when live probe results exist:
     * WORKING (fastest latency first) -> TESTING -> QUOTA_LIMIT -> untested -> FAILED.
     */
    fun sortModelsWithProbeResults(
        models: List<AiSyncedModel>,
        probeResults: Map<String, AiModelProbeResult>
    ): List<AiSyncedModel> {
        if (probeResults.isEmpty()) return models
        fun statusRank(m: AiSyncedModel): Int {
            return when (probeResults[m.modelId.lowercase()]?.status) {
                AiModelProbeStatus.WORKING -> 0
                AiModelProbeStatus.TESTING -> 1
                AiModelProbeStatus.QUOTA_LIMIT -> 2
                null -> 3
                AiModelProbeStatus.FAILED -> 4
            }
        }
        return models.sortedWith(
            compareBy<AiSyncedModel> { statusRank(it) }
                .thenBy { m ->
                    val r = probeResults[m.modelId.lowercase()]
                    if (r?.status == AiModelProbeStatus.WORKING && r.latencyMs > 0L) r.latencyMs else Long.MAX_VALUE
                }
        )
    }

    /**
     * Sanitizes conversation history before multi-turn Gemini calls:
     * - Strips error messages and blank messages
     * - Drops leading model messages so history always starts with a user turn
     * - Collapses consecutive same-role turns so roles strictly alternate user -> model
     * - Drops any trailing dangling user turn so history always ends with a model turn
     *   before the caller appends the new user message.
     */
    fun sanitizeChatHistory(history: List<AiChatMessage>, maxTurns: Int = 6): List<AiChatMessage> {
        val cleaned = mutableListOf<AiChatMessage>()
        for (msg in history) {
            if (msg.isError || msg.text.isBlank()) continue
            if (cleaned.isEmpty() && !msg.isUser) continue
            val last = cleaned.lastOrNull()
            if (last != null && last.isUser == msg.isUser) {
                cleaned[cleaned.lastIndex] = msg
            } else {
                cleaned.add(msg)
            }
        }
        // Ensure history ends on a model reply so the new user prompt never collides with a dangling user turn
        while (cleaned.isNotEmpty() && cleaned.last().isUser) {
            cleaned.removeAt(cleaned.lastIndex)
        }
        val capped = if (cleaned.size > maxTurns) cleaned.takeLast(maxTurns).toMutableList() else cleaned
        while (capped.isNotEmpty() && !capped.first().isUser) {
            capped.removeAt(0)
        }
        return capped
    }

    /**
     * Incrementally extracts a single JSON string field value from a partially streamed JSON
     * payload (e.g. `{"answer": "Công thức...\n`) while safely decoding escape sequences and
     * ignoring incomplete trailing escapes like `\` or `\u00` until the next chunk arrives.
     */
    fun extractStreamingJsonStringField(rawPartialJson: String, fieldName: String): String {
        val token = "\"$fieldName\""
        var searchFrom = 0
        while (searchFrom < rawPartialJson.length) {
            val keyIndex = rawPartialJson.indexOf(token, startIndex = searchFrom)
            if (keyIndex < 0) return ""
            var i = keyIndex + token.length
            while (i < rawPartialJson.length && rawPartialJson[i].isWhitespace()) i++
            if (i >= rawPartialJson.length) return ""
            if (rawPartialJson[i] != ':') {
                searchFrom = keyIndex + token.length
                continue
            }
            i++ // skip ':'
            while (i < rawPartialJson.length && rawPartialJson[i].isWhitespace()) i++
            if (i >= rawPartialJson.length) return ""
            if (rawPartialJson[i] != '"') {
                // Value is null / number / object / array — not a string
                return ""
            }
            i++ // skip opening quote

            val sb = StringBuilder()
            while (i < rawPartialJson.length) {
                val ch = rawPartialJson[i]
                if (ch == '"') break
                if (ch == '\\') {
                    if (i + 1 >= rawPartialJson.length) break
                    when (val next = rawPartialJson[i + 1]) {
                        'n' -> { sb.append('\n'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'r' -> { i += 2 }
                        '"' -> { sb.append('"'); i += 2 }
                        '\\' -> { sb.append('\\'); i += 2 }
                        '/' -> { sb.append('/'); i += 2 }
                        'b' -> { sb.append('\b'); i += 2 }
                        'f' -> { sb.append('\u000C'); i += 2 }
                        'u' -> {
                            if (i + 6 <= rawPartialJson.length) {
                                val hex = rawPartialJson.substring(i + 2, i + 6)
                                val code = hex.toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                i += 6
                            } else {
                                break
                            }
                        }
                        else -> { sb.append(next); i += 2 }
                    }
                } else {
                    sb.append(ch)
                    i++
                }
            }
            return sb.toString()
        }
        return ""
    }

    private fun stripLeadingMarkdownFence(raw: String): String {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        return if (firstNewline >= 0) {
            trimmed.substring(firstNewline + 1).trimStart()
        } else {
            ""
        }
    }

    /**
     * Incrementally extracts the human-readable "answer" string from a partially streamed
     * JSON response like `{"answer": "Công thức khoảng cách...\n` (or falls back immediately
     * to streaming plain text when a provider/model ignores JSON formatting).
     */
    fun extractStreamingAnswerPreview(rawPartialJson: String): String {
        if (rawPartialJson.isBlank()) return ""
        val cleaned = stripLeadingMarkdownFence(rawPartialJson)
        if (cleaned.isEmpty()) return ""

        val extracted = extractStreamingJsonStringField(cleaned, "answer")
        if (extracted.isNotEmpty()) return extracted

        // If the stream doesn't look like a JSON object or an opening markdown fence, stream it directly as plain text
        val rawTrimmed = rawPartialJson.trimStart()
        val looksLikeFenceStart = rawTrimmed == "`" || rawTrimmed == "``" ||
            (rawTrimmed.startsWith("```") && !rawTrimmed.contains('\n'))
        if (looksLikeFenceStart) return ""
        if (!cleaned.startsWith("{")) {
            return cleaned.removeSuffix("```").trimEnd()
        }
        return ""
    }

    /**
     * Incrementally extracts live readable fields (`target`, `title`, `problemText`,
     * `drawingSummary`, `solutionHint`) while the AI is streaming a 3D/2D drawing plan,
     * and detects when generation advances into `scene3D` / `scene2D` coordinates.
     */
    fun extractStreamingProblemPreview(
        rawPartial: String,
        thinkingChars: Int = 0
    ): AiProblemStreamingPreview {
        val cleaned = stripLeadingMarkdownFence(rawPartial)
        val targetStr = extractStreamingJsonStringField(cleaned, "target").trim().uppercase()
        val detectedTarget = when {
            targetStr.contains("2D") -> AiDrawTarget.PLANE_2D
            targetStr.contains("3D") -> AiDrawTarget.SPACE_3D
            cleaned.contains("\"scene2D\"", ignoreCase = true) || cleaned.contains("\"scene_2d\"", ignoreCase = true) -> AiDrawTarget.PLANE_2D
            cleaned.contains("\"scene3D\"", ignoreCase = true) || cleaned.contains("\"scene_3d\"", ignoreCase = true) -> AiDrawTarget.SPACE_3D
            else -> null
        }

        val title = extractStreamingJsonStringField(cleaned, "title")
        val problemText = extractStreamingJsonStringField(cleaned, "problemText")
        val drawingSummary = extractStreamingJsonStringField(cleaned, "drawingSummary")
        val solutionHint = extractStreamingJsonStringField(cleaned, "solutionHint")

        val isBuildingGeometry = cleaned.contains("\"scene3D\"", ignoreCase = true) ||
            cleaned.contains("\"scene2D\"", ignoreCase = true) ||
            cleaned.contains("\"scene_3d\"", ignoreCase = true) ||
            cleaned.contains("\"scene_2d\"", ignoreCase = true)

        // Plain-text fallback if a custom/non-JSON model streams prose instead of JSON
        val fallbackHint = if (title.isEmpty() && problemText.isEmpty() && drawingSummary.isEmpty() && solutionHint.isEmpty()) {
            val rawTrimmed = rawPartial.trimStart()
            val looksLikeFenceStart = rawTrimmed == "`" || rawTrimmed == "``" ||
                (rawTrimmed.startsWith("```") && !rawTrimmed.contains('\n'))
            if (!looksLikeFenceStart && cleaned.isNotEmpty() && !cleaned.startsWith("{")) {
                cleaned.removeSuffix("```").trimEnd()
            } else ""
        } else solutionHint

        return AiProblemStreamingPreview(
            target = detectedTarget,
            title = title,
            problemText = problemText,
            drawingSummary = drawingSummary,
            solutionHint = fallbackHint,
            isBuildingGeometry = isBuildingGeometry,
            thinkingChars = thinkingChars,
            rawChars = rawPartial.length
        )
    }

    fun parseDocsReply(rawText: String): AiDocsReply {
        val obj = extractJsonObject(rawText) ?: return AiDocsReply(answer = rawText.trim())
        val answer = obj.optString("answer").trim().ifEmpty { rawText.trim() }
        val pages = parsePageSuggestions(obj.optJSONArray("suggestedPages"))
        return AiDocsReply(answer = answer, suggestedPages = pages)
    }

    fun parseDrawingPlan(rawText: String): AiDrawingPlan? {
        val obj = extractJsonObject(rawText) ?: return null
        val rawScene3D = obj.optJSONObject("scene3D")
            ?: obj.optJSONObject("scene3d")
            ?: obj.optJSONObject("scene_3d")
        val rawScene2D = obj.optJSONObject("scene2D")
            ?: obj.optJSONObject("scene2d")
            ?: obj.optJSONObject("scene_2d")

        val targetStr = obj.optString("target", "").trim().uppercase()
        val target = when {
            targetStr.contains("2D") && rawScene2D != null -> AiDrawTarget.PLANE_2D
            targetStr.contains("3D") && rawScene3D != null -> AiDrawTarget.SPACE_3D
            rawScene3D != null -> AiDrawTarget.SPACE_3D
            rawScene2D != null -> AiDrawTarget.PLANE_2D
            else -> return null
        }

        val title = obj.optString("title").trim().ifEmpty {
            if (target == AiDrawTarget.SPACE_3D) "Hình không gian 3D từ đề bài" else "Hình phẳng & Đồ thị 2D từ đề bài"
        }
        val problemText = obj.optString("problemText").trim()
        val drawingSummary = obj.optString("drawingSummary").trim()
        val solutionHint = obj.optString("solutionHint").trim()
        val relatedPages = parsePageSuggestions(obj.optJSONArray("relatedPages"))

        val scene3D = if (target == AiDrawTarget.SPACE_3D) rawScene3D?.let { parse3DSpec(it) } else null
        val scene2D = if (target == AiDrawTarget.PLANE_2D) rawScene2D?.let { parse2DSpec(it) } else null

        if (target == AiDrawTarget.SPACE_3D && scene3D == null) return null
        if (target == AiDrawTarget.PLANE_2D && scene2D == null) return null

        return AiDrawingPlan(
            target = target,
            title = title,
            problemText = problemText,
            drawingSummary = drawingSummary,
            solutionHint = solutionHint,
            scene3D = scene3D,
            scene2D = scene2D,
            relatedPages = relatedPages
        )
    }

    fun buildGeometrySnapshot(spec: Ai3DSceneSpec): GeometrySnapshot {
        val shape = ShapeType.fromKey(spec.shapeKey)
        val safeA = spec.a.takeIf { it.isFinite() }?.coerceIn(1.5f, 9f) ?: 4f
        val safeB = spec.b.takeIf { it.isFinite() }?.coerceIn(1.5f, 9f) ?: 3f
        val safeH = spec.h.takeIf { it.isFinite() }?.coerceIn(1.5f, 9f) ?: 4.8f

        val edgePoints = spec.edgePoints.mapIndexedNotNull { idx, ep ->
            val name = ep.name.trim().take(4)
            val from = ep.from.trim()
            val to = ep.to.trim()
            if (name.isEmpty() || from.isEmpty() || to.isEmpty()) null
            else CustomEdgePoint(
                id = "ai_ep_$idx",
                name = name,
                from = from,
                to = to,
                ratio = ep.ratio.takeIf { it.isFinite() }?.coerceIn(0.05f, 0.95f) ?: 0.5f
            )
        }

        val xyzPoints = spec.xyzPoints.mapIndexedNotNull { idx, pt ->
            val name = pt.name.trim().take(4)
            if (name.isEmpty() || !pt.x.isFinite() || !pt.y.isFinite() || !pt.z.isFinite()) null
            else CustomXYZPoint(
                id = "ai_xyz_$idx",
                name = name,
                x = pt.x.coerceIn(-12f, 12f),
                y = pt.y.coerceIn(-12f, 12f),
                z = pt.z.coerceIn(-12f, 12f)
            )
        }

        var highlightIdx3D = 0
        val lines = spec.lines.mapIndexedNotNull { idx, ln ->
            val p1 = ln.p1.trim()
            val p2 = ln.p2.trim()
            if (p1.isEmpty() || p2.isEmpty() || p1 == p2) null
            else {
                val resolvedColor = if (ln.highlighted) {
                    sanitizeHex(ln.colorHex, defaultColors[highlightIdx3D++ % defaultColors.size])
                } else {
                    ""
                }
                // In 3D, always delegate dashed vs solid visibility to the real 3D occlusion engine
                // so unoccluded vectors/segments never stay falsely dashed when AI misguesses camera angle or user orbits.
                val autoVisibilityStyle = if (ln.style.isVector) LineStyle.VECTOR_SOLID else LineStyle.SOLID
                CustomLine(
                    id = "ai_ln_$idx",
                    p1 = p1,
                    p2 = p2,
                    style = autoVisibilityStyle,
                    colorHex = resolvedColor
                )
            }
        }

        val planes = spec.planes.mapIndexedNotNull { idx, pts ->
            val clean = pts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.size < 3) null else CustomPlane(id = "ai_pl_$idx", pts = clean)
        }

        val dynSection = spec.dynamicSection?.let { (p1, p2, p3) ->
            val a1 = p1.trim(); val a2 = p2.trim(); val a3 = p3.trim()
            if (a1.isNotEmpty() && a2.isNotEmpty() && a3.isNotEmpty() && setOf(a1, a2, a3).size == 3) {
                DynamicSection3Points(a1, a2, a3)
            } else null
        }

        return GeometrySnapshot(
            shape = shape,
            a = safeA,
            b = safeB,
            h = safeH,
            originPoint = spec.originPoint?.trim()?.ifEmpty { null } ?: shape.defaultOrigin,
            showAxes = spec.showAxes || xyzPoints.isNotEmpty(),
            showCoordsOnLabels = spec.showAxes || xyzPoints.isNotEmpty(),
            showGrid = true,
            dashed = true,
            labels = true,
            altitude = spec.showAltitude,
            faces = true,
            customPoints = edgePoints,
            customXYZPoints = xyzPoints,
            customLines = lines,
            customPlanes = planes,
            dynamicSection = dynSection,
            camera = CameraOrbitState.DEFAULT
        )
    }

    fun buildGeo2DSnapshot(spec: Ai2DSceneSpec): Geo2DSnapshot {
        val points = mutableListOf<Geo2DPoint>()
        val nameToId = LinkedHashMap<String, String>()

        spec.points.forEachIndexed { idx, pt ->
            val cleanName = pt.name.trim().take(6).ifEmpty { "P${idx + 1}" }
            if (pt.x.isFinite() && pt.y.isFinite() && cleanName !in nameToId) {
                val id = "ai_pt_$idx"
                nameToId[cleanName] = id
                points.add(
                    Geo2DPoint(
                        id = id,
                        name = cleanName,
                        x = pt.x.coerceIn(-50f, 50f),
                        y = pt.y.coerceIn(-50f, 50f),
                        colorHex = sanitizeHex(pt.colorHex, "#94A3B8")
                    )
                )
            }
        }

        val objects = mutableListOf<Geo2DObject>()
        var highlightIdx2D = 0
        spec.objects.forEachIndexed { idx, raw ->
            val kind = raw.kind.trim().lowercase()
            val color = if (raw.highlighted || kind == "angle") {
                sanitizeHex(raw.colorHex, defaultColors[highlightIdx2D++ % defaultColors.size])
            } else {
                ""
            }
            val id = "ai_obj_$idx"
            when (kind) {
                "segment" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.Segment(id, i1, i2, dashed = raw.dashed, colorHex = color))
                    }
                }
                "line" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.Line(id, i1, i2, dashed = raw.dashed, colorHex = color))
                    }
                }
                "ray" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.Ray(id, i1, i2, colorHex = color))
                    }
                }
                "vector" -> {
                    val i1 = nameToId[raw.p1?.trim()]
                    val i2 = nameToId[raw.p2?.trim()]
                    if (i1 != null && i2 != null && i1 != i2) {
                        objects.add(Geo2DObject.VectorObj(id, i1, i2, colorHex = color, dashed = raw.dashed))
                    }
                }
                "circle" -> {
                    val cId = nameToId[raw.p1?.trim()]
                    val eId = nameToId[raw.p2?.trim()]
                    val rad = raw.radius?.takeIf { it.isFinite() && it > 0.05f }?.coerceAtMost(40f)
                    if (cId != null && (eId != null || rad != null)) {
                        objects.add(Geo2DObject.Circle(id, cId, edgePointId = eId, fixedRadius = if (eId == null) rad else null, colorHex = color))
                    }
                }
                "polygon" -> {
                    val ids = raw.pointNames.mapNotNull { nameToId[it.trim()] }.distinct()
                    if (ids.size >= 3) {
                        objects.add(Geo2DObject.Polygon(id, ids, colorHex = color))
                    }
                }
                "angle" -> {
                    val aId = nameToId[raw.p1?.trim()]
                    val vId = nameToId[raw.p2?.trim()]
                    val cId = nameToId[raw.p3?.trim()]
                    if (aId != null && vId != null && cId != null && setOf(aId, vId, cId).size == 3) {
                        objects.add(Geo2DObject.AngleMeasure(id, aId, vId, cId, colorHex = color))
                    }
                }
            }
        }

        val functions = spec.functions.mapIndexedNotNull { idx, fn ->
            val cleanExpr = fn.expression.trim()
                .removePrefix("y=").removePrefix("y =")
                .removePrefix("f(x)=").removePrefix("f(x) =")
                .trim()
            if (cleanExpr.isEmpty()) return@mapIndexedNotNull null
            val parsed = MathExprParser.parse(cleanExpr)
            if (parsed !is MathExprParser.ParseResult.Ok) return@mapIndexedNotNull null
            GraphFunction(
                id = "ai_fn_$idx",
                label = "f${idx + 1}(x)",
                expression = cleanExpr,
                colorHex = sanitizeHex(fn.colorHex, defaultColors[idx % defaultColors.size]),
                visible = true,
                showDerivative = fn.showDerivative
            )
        }

        // Compute balanced camera framing around any drawn 2D points
        val camera = if (points.isNotEmpty()) {
            val minX = points.minOf { it.x }.toDouble()
            val maxX = points.maxOf { it.x }.toDouble()
            val minY = points.minOf { it.y }.toDouble()
            val maxY = points.maxOf { it.y }.toDouble()
            val cx = (minX + maxX) / 2.0
            val cy = (minY + maxY) / 2.0
            val span = max(abs(maxX - minX), abs(maxY - minY)).coerceAtLeast(4.0)
            val ppu = (420.0 / (span * 1.45)).coerceIn(28.0, 110.0)
            Geo2DCamera(centerX = cx, centerY = cy, pixelsPerUnit = ppu)
        } else {
            Geo2DCamera(centerX = 0.0, centerY = 0.0, pixelsPerUnit = 68.0)
        }

        return Geo2DSnapshot(
            points = points,
            objects = objects,
            functions = functions,
            camera = camera,
            paramM = spec.paramM.takeIf { it.isFinite() }?.coerceIn(-10.0, 10.0) ?: 1.0,
            showAxes = true,
            showGrid = true,
            showPointCoords = points.isNotEmpty(),
            showMeasurements = points.isNotEmpty(),
            showKeyGraphPoints = functions.isNotEmpty(),
            showAsymptotes = functions.isNotEmpty()
        )
    }

    private fun parsePageSuggestions(arr: JSONArray?): List<AiPdfPageSuggestion> {
        if (arr == null) return emptyList()
        val out = mutableListOf<AiPdfPageSuggestion>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val page = item.optInt("page", -1)
            if (page in 1..PDF_TOTAL_PAGES) {
                val title = item.optString("title").trim().ifEmpty { "Trang $page" }
                val reason = item.optString("reason").trim()
                if (out.none { it.page == page }) {
                    out.add(AiPdfPageSuggestion(page = page, title = title, reason = reason))
                }
            }
        }
        return out
    }

    private fun parse3DSpec(o: JSONObject): Ai3DSceneSpec {
        val epArr = o.optJSONArray("edgePoints")
        val edgePoints = buildList {
            if (epArr != null) {
                for (i in 0 until epArr.length()) {
                    val e = epArr.optJSONObject(i) ?: continue
                    add(
                        Ai3DEdgePointSpec(
                            name = e.optString("name"),
                            from = e.optString("from"),
                            to = e.optString("to"),
                            ratio = e.optDouble("ratio", 0.5).toFloat()
                        )
                    )
                }
            }
        }

        val xyzArr = o.optJSONArray("xyzPoints")
        val xyzPoints = buildList {
            if (xyzArr != null) {
                for (i in 0 until xyzArr.length()) {
                    val p = xyzArr.optJSONObject(i) ?: continue
                    add(
                        Ai3DXyzPointSpec(
                            name = p.optString("name"),
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            z = p.optDouble("z", 0.0).toFloat()
                        )
                    )
                }
            }
        }

        val lnArr = o.optJSONArray("lines")
        val lines = buildList {
            if (lnArr != null) {
                for (i in 0 until lnArr.length()) {
                    val l = lnArr.optJSONObject(i) ?: continue
                    val rawColor = l.optString("colorHex", "").trim()
                    val highlighted = if (l.has("highlighted") && !l.isNull("highlighted")) {
                        l.optBoolean("highlighted", false)
                    } else {
                        isValidHex(rawColor)
                    }
                    val rawStyle = l.optString("style", "solid").trim().lowercase()
                    val parsedStyle = if (rawStyle.contains("vector") || rawStyle.contains("vec")) {
                        LineStyle.VECTOR_SOLID
                    } else {
                        LineStyle.SOLID
                    }
                    add(
                        Ai3DLineSpec(
                            p1 = l.optString("p1"),
                            p2 = l.optString("p2"),
                            style = parsedStyle,
                            colorHex = rawColor,
                            highlighted = highlighted
                        )
                    )
                }
            }
        }

        val plArr = o.optJSONArray("planes")
        val planes = buildList {
            if (plArr != null) {
                for (i in 0 until plArr.length()) {
                    val ptsArr = plArr.optJSONArray(i) ?: plArr.optJSONObject(i)?.optJSONArray("pts") ?: continue
                    val list = (0 until ptsArr.length()).mapNotNull { j -> ptsArr.optString(j).takeIf { it.isNotBlank() } }
                    if (list.size >= 3) add(list)
                }
            }
        }

        val secObj = o.optJSONObject("dynamicSection")
        val dynSec = secObj?.let { s ->
            val p1 = s.optString("p1"); val p2 = s.optString("p2"); val p3 = s.optString("p3")
            if (p1.isNotBlank() && p2.isNotBlank() && p3.isNotBlank()) Triple(p1, p2, p3) else null
        }

        return Ai3DSceneSpec(
            shapeKey = o.optString("shapeKey", "pyramid4_sa"),
            a = o.optDouble("a", 4.0).toFloat(),
            b = o.optDouble("b", 3.0).toFloat(),
            h = o.optDouble("h", 4.8).toFloat(),
            showAxes = o.optBoolean("showAxes", false),
            showAltitude = o.optBoolean("showAltitude", false),
            originPoint = o.optString("originPoint").takeIf { it.isNotBlank() },
            edgePoints = edgePoints,
            xyzPoints = xyzPoints,
            lines = lines,
            planes = planes,
            dynamicSection = dynSec
        )
    }

    private fun parse2DSpec(o: JSONObject): Ai2DSceneSpec {
        val ptArr = o.optJSONArray("points")
        val points = buildList {
            if (ptArr != null) {
                for (i in 0 until ptArr.length()) {
                    val p = ptArr.optJSONObject(i) ?: continue
                    add(
                        Ai2DPointSpec(
                            name = p.optString("name"),
                            x = p.optDouble("x", 0.0).toFloat(),
                            y = p.optDouble("y", 0.0).toFloat(),
                            colorHex = p.optString("colorHex", "")
                        )
                    )
                }
            }
        }

        val objArr = o.optJSONArray("objects")
        val objects = buildList {
            if (objArr != null) {
                for (i in 0 until objArr.length()) {
                    val item = objArr.optJSONObject(i) ?: continue
                    val pnArr = item.optJSONArray("pointNames")
                    val pNames = buildList {
                        if (pnArr != null) {
                            for (j in 0 until pnArr.length()) {
                                pnArr.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    val rawColor = item.optString("colorHex", "").trim()
                    val highlighted = if (item.has("highlighted") && !item.isNull("highlighted")) {
                        item.optBoolean("highlighted", false)
                    } else {
                        isValidHex(rawColor)
                    }
                    add(
                        Ai2DObjectSpec(
                            kind = item.optString("kind", "segment"),
                            p1 = item.optString("p1").takeIf { it.isNotBlank() },
                            p2 = item.optString("p2").takeIf { it.isNotBlank() },
                            p3 = item.optString("p3").takeIf { it.isNotBlank() },
                            radius = if (item.has("radius") && !item.isNull("radius")) item.optDouble("radius").toFloat() else null,
                            pointNames = pNames,
                            dashed = item.optBoolean("dashed", false),
                            colorHex = rawColor,
                            highlighted = highlighted
                        )
                    )
                }
            }
        }

        val fnArr = o.optJSONArray("functions")
        val functions = buildList {
            if (fnArr != null) {
                for (i in 0 until fnArr.length()) {
                    val f = fnArr.optJSONObject(i) ?: continue
                    add(
                        Ai2DFunctionSpec(
                            expression = f.optString("expression"),
                            showDerivative = f.optBoolean("showDerivative", false),
                            colorHex = f.optString("colorHex", "#38BDF8")
                        )
                    )
                }
            }
        }

        return Ai2DSceneSpec(
            points = points,
            objects = objects,
            functions = functions,
            paramM = o.optDouble("paramM", 1.0)
        )
    }

    private fun isValidHex(raw: String?): Boolean {
        val t = raw?.trim().orEmpty()
        return Regex("^#[0-9A-Fa-f]{6}$").matches(t)
    }

    private fun sanitizeHex(raw: String?, fallback: String): String {
        val t = raw?.trim().orEmpty()
        return if (isValidHex(t)) t else fallback
    }
}
