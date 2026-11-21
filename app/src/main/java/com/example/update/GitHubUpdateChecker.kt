package com.example.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkAssetName: String?,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long
) {
    /** Best link to open when the user taps "Tải & Cài đặt APK". */
    val preferredInstallUrl: String
        get() = apkDownloadUrl?.takeIf { it.isNotBlank() } ?: htmlUrl
}

sealed interface UpdateFetchResult {
    data class Success(val release: GitHubReleaseInfo) : UpdateFetchResult
    data class Error(val message: String, val httpCode: Int? = null) : UpdateFetchResult
}

object GitHubUpdateChecker {

    private val repoSlugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val githubUrlRegex = Regex(
        """(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?$""",
        RegexOption.IGNORE_CASE
    )
    private val numberTokenRegex = Regex("""\d+""")

    /**
     * Normalizes user input such as "owner/repo", "https://github.com/owner/repo",
     * or "https://github.com/owner/repo/releases" into a clean "owner/repo" slug.
     */
    fun normalizeRepoSlug(raw: String?): String? {
        val trimmed = raw?.trim()?.removeSuffix("/") ?: return null
        if (trimmed.isEmpty()) return null

        val cleaned = if (trimmed.contains("github.com", ignoreCase = true)) {
            val match = githubUrlRegex.find(trimmed) ?: return null
            val owner = match.groupValues[1]
            val repo = match.groupValues[2].removeSuffix(".git")
            "$owner/$repo"
        } else {
            trimmed.removeSuffix(".git")
        }

        return if (repoSlugRegex.matches(cleaned)) cleaned else null
    }

    /**
     * Extracts numeric version segments from tags like "v4", "tool-toan-v12", or "v1.2.3".
     */
    fun extractVersionParts(versionText: String): List<Int> {
        val cleaned = versionText.trim()
        if (cleaned.isEmpty()) return emptyList()
        return numberTokenRegex.findAll(cleaned)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    /**
     * Returns true if [remoteTag] represents a strictly newer version than the currently
     * installed [currentVersionName] / [currentVersionCode].
     */
    fun isNewerVersion(
        remoteTag: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): Boolean {
        val remoteParts = extractVersionParts(remoteTag)
        if (remoteParts.isEmpty()) return false

        val localParts = extractVersionParts(currentVersionName)

        // Single-number versioning used by Tool Toán GitHub Releases (v1, v2, v3, ...)
        if (remoteParts.size == 1 && localParts.size <= 1) {
            val remoteNum = remoteParts[0]
            val localNum = maxOf(localParts.firstOrNull() ?: 0, currentVersionCode)
            return remoteNum > localNum
        }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /**
     * Parses the JSON payload returned by GitHub's `/repos/{owner}/{repo}/releases/latest` API.
     */
    fun parseReleaseJson(jsonText: String): GitHubReleaseInfo? {
        return runCatching {
            val obj = JSONObject(jsonText)
            val tag = obj.optString("tag_name", "").trim()
            val htmlUrl = obj.optString("html_url", "").trim()
            if (tag.isEmpty() || htmlUrl.isEmpty()) return null

            val name = obj.optString("name", "").trim().ifEmpty { "Tool Toán $tag" }
            val body = obj.optString("body", "").trim()
            val publishedAt = obj.optString("published_at", "").trim()

            var bestApkName: String? = null
            var bestApkUrl: String? = null
            var bestApkSize = 0L

            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "").trim()
                    val downloadUrl = asset.optString("browser_download_url", "").trim()
                    if (assetName.lowercase(Locale.US).endsWith(".apk") && downloadUrl.isNotEmpty()) {
                        val size = asset.optLong("size", 0L).coerceAtLeast(0L)
                        val isPreferred = assetName.lowercase(Locale.US).contains("tool-toan")
                        if (bestApkUrl == null || isPreferred) {
                            bestApkName = assetName
                            bestApkUrl = downloadUrl
                            bestApkSize = size
                            if (isPreferred) break
                        }
                    }
                }
            }

            GitHubReleaseInfo(
                tagName = tag,
                releaseName = name,
                body = body,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                apkAssetName = bestApkName,
                apkDownloadUrl = bestApkUrl,
                apkSizeBytes = bestApkSize
            )
        }.getOrNull()
    }

    /**
     * Cleans GitHub Markdown release notes into readable plain lines for mobile display.
     */
    fun formatReleaseNotes(rawBody: String, maxLines: Int = 18): String {
        if (rawBody.isBlank()) {
            return "Bản phát hành mới đã sẵn sàng trên GitHub với các cải tiến và bản sửa lỗi mới nhất."
        }
        val lines = rawBody.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("^#{1,6}\\s*"), "• ")
                    .replace("**", "")
                    .replace("`", "")
            }
            .filter { it.isNotBlank() && it != "---" }
            .take(maxLines)

        return if (lines.isEmpty()) {
            "Bản phát hành mới đã sẵn sàng trên GitHub."
        } else {
            lines.joinToString("\n")
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatPublishedDate(isoTimestamp: String): String {
        val datePart = isoTimestamp.substringBefore('T').trim()
        val parts = datePart.split('-')
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
    }

    /**
     * Fetches the latest GitHub Release for [repoSlug] ("owner/repo") over HTTPS.
     */
    suspend fun fetchLatestRelease(
        repoSlug: String,
        currentVersionName: String
    ): UpdateFetchResult = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoSlug(repoSlug)
            ?: return@withContext UpdateFetchResult.Error("Định dạng kho GitHub chưa đúng (ví dụ: ten-tai-khoan/tool-toan).")

        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.github.com/repos/$normalized/releases/latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ToolToan-Android/$currentVersionName")
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()

            when (code) {
                200 -> {
                    val release = parseReleaseJson(body)
                    if (release != null) {
                        UpdateFetchResult.Success(release)
                    } else {
                        UpdateFetchResult.Error("Không thể đọc dữ liệu bản phát hành từ GitHub.", code)
                    }
                }
                404 -> UpdateFetchResult.Error(
                    "Chưa tìm thấy bản phát hành (Release) công khai tại github.com/$normalized.",
                    code
                )
                403 -> UpdateFetchResult.Error(
                    "GitHub tạm giới hạn lượt truy vấn. Vui lòng thử lại sau ít phút.",
                    code
                )
                else -> UpdateFetchResult.Error(
                    "Không thể kiểm tra cập nhật (mã HTTP $code).",
                    code
                )
            }
        } catch (e: Exception) {
            UpdateFetchResult.Error("Không thể kết nối GitHub. Hãy kiểm tra kết nối mạng.")
        } finally {
            conn?.disconnect()
        }
    }
}
