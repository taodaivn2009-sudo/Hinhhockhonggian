package com.example.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tool_toan_github_updates", Context.MODE_PRIVATE)

    fun loadCustomRepo(): String = prefs.getString(KEY_CUSTOM_REPO, "").orEmpty()

    fun saveCustomRepo(repo: String) {
        prefs.edit().putString(KEY_CUSTOM_REPO, repo.trim()).apply()
    }

    fun loadSkippedTag(): String = prefs.getString(KEY_SKIPPED_TAG, "").orEmpty()

    fun saveSkippedTag(tag: String) {
        prefs.edit().putString(KEY_SKIPPED_TAG, tag.trim()).apply()
    }

    companion object {
        private const val KEY_CUSTOM_REPO = "custom_github_repo"
        private const val KEY_SKIPPED_TAG = "skipped_release_tag"
    }
}

sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data object NotConfigured : UpdateCheckStatus
    data class UpToDate(val latestRelease: GitHubReleaseInfo? = null) : UpdateCheckStatus
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckStatus
    data class Error(val message: String) : UpdateCheckStatus
}

data class AppUpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val configuredRepo: String = "",
    val isBuiltInRepo: Boolean = false,
    val status: UpdateCheckStatus = UpdateCheckStatus.Idle,
    val showAutoBanner: Boolean = false,
    val showDialog: Boolean = false,
    val skippedTag: String = ""
) {
    val availableRelease: GitHubReleaseInfo?
        get() = (status as? UpdateCheckStatus.UpdateAvailable)?.release

    val hasUpdate: Boolean
        get() = availableRelease != null
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UpdatePreferences(application.applicationContext)
    private val builtInRepo = GitHubUpdateChecker.normalizeRepoSlug(BuildConfig.GITHUB_REPO).orEmpty()

    private val _uiState = MutableStateFlow(
        run {
            val savedCustom = GitHubUpdateChecker.normalizeRepoSlug(prefs.loadCustomRepo()).orEmpty()
            val effectiveRepo = savedCustom.ifEmpty { builtInRepo }
            AppUpdateUiState(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                configuredRepo = effectiveRepo,
                isBuiltInRepo = savedCustom.isEmpty() && builtInRepo.isNotEmpty(),
                status = if (effectiveRepo.isEmpty()) UpdateCheckStatus.NotConfigured else UpdateCheckStatus.Idle,
                skippedTag = prefs.loadSkippedTag()
            )
        }
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    init {
        checkForUpdates(isAutoStartup = true)
    }

    fun openUpdateDialog() {
        val current = _uiState.value
        _uiState.update { it.copy(showDialog = true, showAutoBanner = false) }
        if (current.configuredRepo.isNotBlank() &&
            (current.status is UpdateCheckStatus.Idle || current.status is UpdateCheckStatus.Error)
        ) {
            checkForUpdates(isAutoStartup = false)
        }
    }

    fun closeUpdateDialog() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun dismissBannerForNow() {
        _uiState.update { it.copy(showAutoBanner = false) }
    }

    fun skipCurrentAvailableVersion() {
        val tag = _uiState.value.availableRelease?.tagName ?: return
        prefs.saveSkippedTag(tag)
        _uiState.update {
            it.copy(
                skippedTag = tag,
                showAutoBanner = false,
                showDialog = false
            )
        }
    }

    fun saveRepoAndCheck(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            prefs.saveCustomRepo("")
            val fallback = builtInRepo
            _uiState.update {
                it.copy(
                    configuredRepo = fallback,
                    isBuiltInRepo = fallback.isNotEmpty(),
                    status = if (fallback.isEmpty()) UpdateCheckStatus.NotConfigured else UpdateCheckStatus.Idle
                )
            }
            if (fallback.isNotEmpty()) {
                checkForUpdates(isAutoStartup = false)
            }
            return
        }

        val normalized = GitHubUpdateChecker.normalizeRepoSlug(trimmed)
        if (normalized == null) {
            _uiState.update {
                it.copy(
                    status = UpdateCheckStatus.Error(
                        "Kho GitHub không hợp lệ. Hãy nhập dạng owner/repo hoặc dán link github.com/owner/repo."
                    )
                )
            }
            return
        }

        prefs.saveCustomRepo(normalized)
        prefs.saveSkippedTag("")
        _uiState.update {
            it.copy(
                configuredRepo = normalized,
                isBuiltInRepo = false,
                skippedTag = ""
            )
        }
        checkForUpdates(isAutoStartup = false)
    }

    fun checkForUpdates(isAutoStartup: Boolean = false) {
        val repo = _uiState.value.configuredRepo.trim()
        if (repo.isEmpty()) {
            _uiState.update { it.copy(status = UpdateCheckStatus.NotConfigured) }
            return
        }
        if (_uiState.value.status is UpdateCheckStatus.Checking) return

        _uiState.update { it.copy(status = UpdateCheckStatus.Checking) }

        viewModelScope.launch {
            val state = _uiState.value
            when (val result = GitHubUpdateChecker.fetchLatestRelease(repo, state.currentVersionName)) {
                is UpdateFetchResult.Success -> {
                    val release = result.release
                    val isNewer = GitHubUpdateChecker.isNewerVersion(
                        remoteTag = release.tagName,
                        currentVersionName = state.currentVersionName,
                        currentVersionCode = state.currentVersionCode
                    )
                    if (isNewer) {
                        val isSkipped = isAutoStartup && release.tagName.equals(state.skippedTag, ignoreCase = true)
                        _uiState.update {
                            it.copy(
                                status = UpdateCheckStatus.UpdateAvailable(release),
                                showAutoBanner = isAutoStartup && !isSkipped && !it.showDialog
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                status = UpdateCheckStatus.UpToDate(release),
                                showAutoBanner = false
                            )
                        }
                    }
                }
                is UpdateFetchResult.Error -> {
                    _uiState.update {
                        it.copy(
                            status = if (isAutoStartup) UpdateCheckStatus.Idle else UpdateCheckStatus.Error(result.message),
                            showAutoBanner = false
                        )
                    }
                }
            }
        }
    }
}
