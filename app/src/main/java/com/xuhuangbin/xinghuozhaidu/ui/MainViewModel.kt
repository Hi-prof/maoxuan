package com.xuhuangbin.xinghuozhaidu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuhuangbin.xinghuozhaidu.data.AppRepository
import com.xuhuangbin.xinghuozhaidu.BuildConfig
import com.xuhuangbin.xinghuozhaidu.data.content.RemoteManifestDto
import com.xuhuangbin.xinghuozhaidu.data.update.AppUpdateManager
import com.xuhuangbin.xinghuozhaidu.domain.model.AppRelease
import com.xuhuangbin.xinghuozhaidu.domain.model.InstalledContentState
import com.xuhuangbin.xinghuozhaidu.domain.model.PersonalNote
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.domain.model.ReaderState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class MainUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val reader: ReaderState = ReaderState(),
    val allCards: List<QuoteCard> = emptyList(),
    val favorites: List<QuoteCard> = emptyList(),
    val liked: List<QuoteCard> = emptyList(),
    val notes: List<PersonalNote> = emptyList(),
    val contentState: InstalledContentState? = null,
)

enum class UpdatePhase {
    Idle,
    Checking,
    Available,
    Downloading,
    Success,
    UpToDate,
    Error,
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val manifest: RemoteManifestDto? = null,
    val progress: Float = 0f,
    val message: String? = null,
    val requiresAppUpdate: Boolean = false,
)

enum class AppUpdatePhase {
    Idle,
    Checking,
    Available,
    Downloading,
    PermissionRequired,
    ReadyToInstall,
    UpToDate,
    Error,
}

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val release: AppRelease? = null,
    val progress: Float = 0f,
    val message: String? = null,
)

data class NoteOperationUiState(
    val inProgress: Boolean = false,
    val errorMessage: String? = null,
)

class MainViewModel(
    private val repository: AppRepository,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {
    private val initializing = MutableStateFlow(true)
    private val initializationError = MutableStateFlow<String?>(null)
    val searchQuery = MutableStateFlow("")
    val updateState = MutableStateFlow(UpdateUiState())
    val appUpdateState = MutableStateFlow(AppUpdateUiState())
    val noteOperationState = MutableStateFlow(NoteOperationUiState())
    private var updateJob: Job? = null
    private var appUpdateJob: Job? = null
    private var downloadedAppUpdate: File? = null

    private val cardUiState = combine(
        repository.readerState,
        repository.allCards,
        repository.favorites,
        repository.liked,
        repository.contentState,
    ) { reader, allCards, favorites, liked, contentState ->
        MainUiState(
            reader = reader,
            allCards = allCards,
            favorites = favorites,
            liked = liked,
            contentState = contentState,
        )
    }

    private val contentUiState = combine(cardUiState, repository.notes) { state, notes ->
        state.copy(notes = notes)
    }

    val uiState = combine(
        contentUiState,
        initializing,
        initializationError,
    ) { content, isInitializing, error ->
        content.copy(isLoading = isInitializing, errorMessage = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults = searchQuery
        .flatMapLatest(repository::search)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchHistory = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            initializing.value = true
            initializationError.value = null
            runCatching { repository.initialize() }
                .onFailure { error ->
                    initializationError.value = error.message ?: "离线内容初始化失败"
                }
            initializing.value = false
        }
    }

    fun updatePosition(index: Int) {
        viewModelScope.launch { repository.updatePosition(index) }
    }

    fun markRead(cardId: String) {
        viewModelScope.launch { repository.markRead(cardId) }
    }

    fun startNewRound() {
        viewModelScope.launch { repository.startNewRound() }
    }

    fun toggleLike(cardId: String) {
        viewModelScope.launch { repository.toggleLike(cardId) }
    }

    fun toggleFavorite(cardId: String) {
        viewModelScope.launch { repository.toggleFavorite(cardId) }
    }

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun submitSearchQuery() {
        viewModelScope.launch { repository.saveSearchQuery(searchQuery.value) }
    }

    fun deleteSearchHistory(keyword: String) {
        viewModelScope.launch { repository.deleteSearchHistory(keyword) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    fun saveNote(
        noteId: Long?,
        cardId: String?,
        title: String,
        body: String,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            noteOperationState.value = NoteOperationUiState(inProgress = true)
            runCatching { repository.saveNote(noteId, cardId, title, body) }
                .onSuccess {
                    noteOperationState.value = NoteOperationUiState()
                    onSaved()
                }
                .onFailure { error ->
                    noteOperationState.value = NoteOperationUiState(
                        errorMessage = error.message ?: "笔记保存失败",
                    )
                }
        }
    }

    fun deleteNote(noteId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            noteOperationState.value = NoteOperationUiState(inProgress = true)
            runCatching { repository.deleteNote(noteId) }
                .onSuccess {
                    noteOperationState.value = NoteOperationUiState()
                    onDeleted()
                }
                .onFailure { error ->
                    noteOperationState.value = NoteOperationUiState(
                        errorMessage = error.message ?: "笔记删除失败",
                    )
                }
        }
    }

    fun clearNoteOperationState() {
        noteOperationState.value = NoteOperationUiState()
    }

    fun checkForUpdate() {
        if (updateState.value.phase in setOf(UpdatePhase.Checking, UpdatePhase.Downloading)) return
        viewModelScope.launch {
            updateState.value = UpdateUiState(UpdatePhase.Checking)
            runCatching { repository.checkForUpdate(BuildConfig.CONTENT_MANIFEST_URL) }
                .onSuccess { manifest ->
                    updateState.value = if (manifest == null) {
                        UpdateUiState(UpdatePhase.UpToDate, message = "当前已经是最新内容")
                    } else if (manifest.minimumAppVersionCode > BuildConfig.VERSION_CODE) {
                        UpdateUiState(
                            phase = UpdatePhase.Error,
                            message = "此内容版本需要更新应用后才能安装",
                            requiresAppUpdate = true,
                        )
                    } else {
                        UpdateUiState(UpdatePhase.Available, manifest = manifest)
                    }
                }
                .onFailure { error ->
                    updateState.value = UpdateUiState(
                        phase = UpdatePhase.Error,
                        message = error.message ?: "检查更新失败",
                    )
                }
        }
    }

    fun confirmUpdate() {
        val manifest = updateState.value.manifest ?: return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            updateState.value = UpdateUiState(UpdatePhase.Downloading, manifest = manifest)
            try {
                repository.downloadAndInstall(manifest) { progress ->
                    updateState.value = updateState.value.copy(progress = progress)
                }
                updateState.value = UpdateUiState(UpdatePhase.Success, message = "内容已更新")
            } catch (error: CancellationException) {
                updateState.value = UpdateUiState()
                throw error
            } catch (error: Exception) {
                updateState.value = UpdateUiState(
                    phase = UpdatePhase.Error,
                    message = error.message ?: "内容更新失败",
                )
            }
        }
    }

    fun dismissUpdate() {
        if (updateState.value.phase == UpdatePhase.Downloading) {
            updateJob?.cancel()
            updateJob = null
        }
        updateState.value = UpdateUiState()
    }

    fun checkForAppUpdate() {
        if (appUpdateState.value.phase in setOf(AppUpdatePhase.Checking, AppUpdatePhase.Downloading)) return
        appUpdateJob?.cancel()
        downloadedAppUpdate = null
        appUpdateJob = viewModelScope.launch {
            appUpdateState.value = AppUpdateUiState(AppUpdatePhase.Checking)
            try {
                val release = appUpdateManager.findUpdate(
                    releasesUrl = BuildConfig.APP_RELEASES_API_URL,
                    currentVersion = BuildConfig.VERSION_NAME,
                )
                appUpdateState.value = if (release == null) {
                    AppUpdateUiState(
                        phase = AppUpdatePhase.UpToDate,
                        message = "当前已经是最新应用",
                    )
                } else {
                    AppUpdateUiState(AppUpdatePhase.Available, release = release)
                }
            } catch (error: CancellationException) {
                appUpdateState.value = AppUpdateUiState()
                throw error
            } catch (error: Exception) {
                appUpdateState.value = AppUpdateUiState(
                    phase = AppUpdatePhase.Error,
                    message = error.message ?: "检查应用更新失败",
                )
            } finally {
                appUpdateJob = null
            }
        }
    }

    fun confirmAppUpdate() {
        val release = appUpdateState.value.release ?: return
        if (appUpdateState.value.phase == AppUpdatePhase.Downloading) return
        appUpdateJob?.cancel()
        appUpdateJob = viewModelScope.launch {
            appUpdateState.value = AppUpdateUiState(
                phase = AppUpdatePhase.Downloading,
                release = release,
            )
            try {
                downloadedAppUpdate = appUpdateManager.download(release) { progress ->
                    appUpdateState.value = appUpdateState.value.copy(progress = progress)
                }
                requestAppInstall()
            } catch (error: CancellationException) {
                downloadedAppUpdate = null
                appUpdateState.value = AppUpdateUiState()
                throw error
            } catch (error: Exception) {
                downloadedAppUpdate = null
                appUpdateState.value = AppUpdateUiState(
                    phase = AppUpdatePhase.Error,
                    release = release,
                    message = error.message ?: "应用更新下载失败",
                )
            } finally {
                appUpdateJob = null
            }
        }
    }

    fun requestAppInstall() {
        val apk = downloadedAppUpdate
        val release = appUpdateState.value.release
        if (apk == null || release == null) {
            appUpdateState.value = AppUpdateUiState(
                phase = AppUpdatePhase.Error,
                message = "安装包已失效，请重新检查更新",
            )
            return
        }
        try {
            if (!appUpdateManager.canRequestPackageInstalls()) {
                appUpdateState.value = AppUpdateUiState(
                    phase = AppUpdatePhase.PermissionRequired,
                    release = release,
                    progress = 1f,
                    message = "请允许星火摘读安装未知应用，返回后将继续安装",
                )
                appUpdateManager.openInstallPermissionSettings()
                return
            }
            appUpdateManager.launchInstaller(apk)
            appUpdateState.value = AppUpdateUiState(
                phase = AppUpdatePhase.ReadyToInstall,
                release = release,
                progress = 1f,
                message = "系统安装程序已打开；若已取消，可再次安装",
            )
        } catch (error: Exception) {
            appUpdateState.value = AppUpdateUiState(
                phase = AppUpdatePhase.Error,
                release = release,
                message = error.message ?: "无法打开应用安装程序",
            )
        }
    }

    fun resumePendingAppInstall() {
        if (appUpdateState.value.phase != AppUpdatePhase.PermissionRequired) return
        if (appUpdateManager.canRequestPackageInstalls()) requestAppInstall()
    }

    fun startAppUpdateFromContent() {
        dismissUpdate()
        checkForAppUpdate()
    }

    fun dismissAppUpdate() {
        if (appUpdateState.value.phase == AppUpdatePhase.Downloading) {
            appUpdateJob?.cancel()
            appUpdateJob = null
            downloadedAppUpdate = null
        }
        appUpdateState.value = AppUpdateUiState()
    }

    companion object {
        fun factory(
            repository: AppRepository,
            appUpdateManager: AppUpdateManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java))
                    return MainViewModel(repository, appUpdateManager) as T
                }
            }
    }
}
