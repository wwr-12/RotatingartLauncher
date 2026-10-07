package com.app.ralaunch.feature.installer.vm

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.common.util.HttpFileDownloader
import com.app.ralaunch.core.common.util.HttpRangeDownloader
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.gog.domain.ModLoaderVersionFetcher
import com.app.ralaunch.feature.installer.GameInstaller
import com.app.ralaunch.feature.installer.InstallCallback
import com.app.ralaunch.feature.installer.InstallPluginRegistry
import com.app.ralaunch.feature.installer.contract.InstallerFileType
import com.app.ralaunch.feature.installer.contract.InstallerUiEffect
import com.app.ralaunch.feature.installer.contract.InstallerUiEvent
import com.app.ralaunch.feature.installer.contract.InstallerUiState
import com.app.ralaunch.feature.installer.contract.ModLoaderDownloadTarget
import com.app.ralaunch.feature.installer.contract.ModLoaderDownloadUiState
import com.app.ralaunch.feature.installer.contract.ModLoaderDownloadVersion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException

class InstallerViewModel(
    private val appContext: Context,
    private val gameRepository: IGameRepositoryServiceV3
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<InstallerUiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<InstallerUiEffect> = _effects.asSharedFlow()

    private var activeInstaller: GameInstaller? = null

    // 每次下载独立的取消令牌与会话计数：取消后令牌永久保持 true（其线程/采样器不会
    // 因下次下载复位标志而"复活"并向 UI 交错上报进度），会话 ID 用于丢弃过期进度
    @Volatile private var activeDownloadToken = AtomicBoolean(false)
    @Volatile private var downloadSessionCounter = 0
    private var modLoaderVersionsJob: Job? = null
    private var modLoaderDownloadJob: Job? = null

    // 在线下载（游戏本体/ModLoader）源文件登记：导入成功后删除源文件，导入失败保留
    // 以便重试。仅登记通过本启动器在线下载的文件；用户手动选择的文件永不删除。
    private val onlineDownloadedFiles: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun onEvent(event: InstallerUiEvent) {
        when (event) {
            is InstallerUiEvent.PrefillFromDownload -> prefillFromDownload(
                gameFilePath = event.gameFilePath,
                modLoaderFilePath = event.modLoaderFilePath,
                detectedGameName = event.detectedGameName
            )

            is InstallerUiEvent.BrowseRequested -> browseFor(event.fileType)
            is InstallerUiEvent.FileSelected -> selectFile(
                fileType = event.fileType,
                path = event.path,
                preferredName = event.preferredName
            )

            is InstallerUiEvent.ShowModLoaderDownload ->
                showModLoaderDownload(event.modLoaderName, event.target)
            InstallerUiEvent.DismissModLoaderDownload -> dismissModLoaderDownload()
            is InstallerUiEvent.RefreshModLoaderVersions ->
                fetchModLoaderVersions(forceRefresh = event.forceRefresh)
            is InstallerUiEvent.SelectModLoaderVersion ->
                updateModLoaderDownload { it.copy(selectedVersion = event.version, downloadError = null) }
            is InstallerUiEvent.DownloadModLoaderVersion ->
                downloadModLoaderVersion(event.version)
            is InstallerUiEvent.SetModLoaderDownloadMirror ->
                updateModLoaderDownload { it.copy(useMirror = event.enabled) }
            InstallerUiEvent.CancelModLoaderDownload -> cancelModLoaderDownload()

            InstallerUiEvent.StartImport -> startImport()
            InstallerUiEvent.DismissError -> clearError()
            InstallerUiEvent.ResetSelections -> resetSelections()
        }
    }

    private fun prefillFromDownload(
        gameFilePath: String?,
        modLoaderFilePath: String?,
        detectedGameName: String?
    ) {
        if (_uiState.value.isImporting) return

        _uiState.update {
            it.copy(
                gameFilePath = gameFilePath,
                detectedGameName = detectedGameName
                    ?: gameFilePath?.let(::fallbackDisplayName),
                modLoaderFilePath = modLoaderFilePath,
                detectedModLoaderName = modLoaderFilePath?.let(::fallbackDisplayName),
                progress = 0,
                status = "",
                errorMessage = null
            )
        }

        gameFilePath?.let { detectSelection(InstallerFileType.GAME, it, detectedGameName) }
        modLoaderFilePath?.let { detectSelection(InstallerFileType.MOD_LOADER, it, null) }
    }

    private fun browseFor(fileType: InstallerFileType) {
        if (_uiState.value.isImporting) return
        _effects.tryEmit(InstallerUiEffect.NavigateToFileBrowser(fileType))
    }

    // ==================== ModLoader 在线下载 ====================

    private fun updateModLoaderDownload(
        transform: (ModLoaderDownloadUiState) -> ModLoaderDownloadUiState
    ) {
        _uiState.update { it.copy(modLoaderDownload = transform(it.modLoaderDownload)) }
    }

    private fun showModLoaderDownload(
        modLoaderName: String,
        target: ModLoaderDownloadTarget = ModLoaderDownloadTarget.MOD_LOADER
    ) {
        if (_uiState.value.isImporting) return

        updateModLoaderDownload {
            it.copy(isVisible = true, modLoaderName = modLoaderName, target = target, downloadError = null)
        }
        if (_uiState.value.modLoaderDownload.versions.isEmpty()) {
            fetchModLoaderVersions(forceRefresh = false)
        }
    }

    private fun dismissModLoaderDownload() {
        if (_uiState.value.modLoaderDownload.isDownloading) {
            cancelModLoaderDownload()
        } else {
            modLoaderVersionsJob?.cancel()
            modLoaderVersionsJob = null
        }
        updateModLoaderDownload { ModLoaderDownloadUiState() }
    }

    private fun fetchModLoaderVersions(forceRefresh: Boolean) {
        val state = _uiState.value.modLoaderDownload
        if (!state.isVisible || state.isDownloading) return
        if (state.isLoadingVersions) return
        if (!forceRefresh && state.versions.isNotEmpty()) return

        val modLoaderName = state.modLoaderName
        val useMirror = state.useMirror
        updateModLoaderDownload {
            it.copy(isLoadingVersions = true, versionsError = null)
        }

        modLoaderVersionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val versions = ModLoaderVersionFetcher.fetchAllVersions(modLoaderName, useMirror)

                withContext(Dispatchers.Main) {
                    updateModLoaderDownload { current ->
                        val mapped = versions.map { release ->
                            ModLoaderDownloadVersion(
                                version = release.version,
                                displayName = release.displayName,
                                url = release.url,
                                fileName = release.fileName,
                                sizeBytes = release.sizeBytes,
                                prerelease = release.prerelease,
                                publishedAt = release.publishedAt
                            )
                        }
                        current.copy(
                            isLoadingVersions = false,
                            versionsError = null,
                            versions = mapped,
                            // 默认选中最新的稳定版；若全是预发布版则选第一个
                            selectedVersion = current.selectedVersion
                                ?: mapped.firstOrNull { !it.prerelease }
                                ?: mapped.firstOrNull()
                        )
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "获取 $modLoaderName 版本列表失败", e)
                withContext(Dispatchers.Main) {
                    updateModLoaderDownload {
                        it.copy(
                            isLoadingVersions = false,
                            versionsError = appContext.getString(
                                R.string.modloader_download_fetch_failed_reason,
                                e.message ?: appContext.getString(R.string.common_unknown_error)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun downloadModLoaderVersion(version: ModLoaderDownloadVersion) {
        val state = _uiState.value
        if (state.isImporting || state.modLoaderDownload.isDownloading) return

        // 下载完成后的落点：游戏本体 → GAME 文件位；ModLoader → MOD_LOADER 文件位
        val downloadTarget = state.modLoaderDownload.target

        // 每次下载独立取消令牌 + 会话 ID（修复：取消后再下载，旧下载线程因共享
        // cancelled 标志被复位而"复活"，新旧采样器交错上报进度，进度条如同多个下载叠加）
        val token = AtomicBoolean(false)
        activeDownloadToken = token
        val session = ++downloadSessionCounter

        updateModLoaderDownload {
            it.copy(
                selectedVersion = version,
                isDownloading = true,
                downloadError = null,
                downloadingFileName = version.fileName,
                downloadedBytes = 0,
                totalBytes = version.sizeBytes,
                speedBytesPerSecond = 0
            )
        }

        val useMirror = _uiState.value.modLoaderDownload.useMirror
        modLoaderDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            var partFile: File? = null
            try {
                val targetDir = resolveModLoaderDownloadDir()
                val targetFile = File(targetDir, version.fileName)
                // 唯一临时文件：取消/同名重下互不干扰，成功后重命名为最终名
                val downloadPartFile = File(targetDir, "${version.fileName}.part$session")
                partFile = downloadPartFile
                val progressCallback: (Long, Long, Long) -> Unit = { downloaded, _, speed ->
                    if (session == downloadSessionCounter) {   // 过期会话的进度直接丢弃
                        updateModLoaderDownload {
                            it.copy(
                                downloadedBytes = downloaded,
                                // 分母锁定为 GitHub API 声明的文件大小；镜像站返回的
                                // content-length 不可靠（可能为 -1 或与实际不符），不采用
                                totalBytes = if (it.totalBytes > 0) it.totalBytes else version.sizeBytes,
                                speedBytesPerSecond = speed
                            )
                        }
                    }
                }

                if (version.sizeBytes >= RANGE_DOWNLOAD_THRESHOLD) {
                    // 大文件：多线程分段下载（镜像按实测速度择优，支持 Range 的镜像可达
                    // 单线程的数倍速度）；全部候选不可用时回退单线程
                    try {
                        HttpRangeDownloader.downloadFirstAvailable(
                            candidates = ModLoaderVersionFetcher.getDownloadCandidateUrls(
                                version.url, useMirror
                            ),
                            targetFile = downloadPartFile,
                            expectedSize = version.sizeBytes,
                            onProgress = progressCallback,
                            isCancelled = { token.get() }
                        )
                    } catch (e: HttpRangeDownloader.NotRangeSupportedException) {
                        AppLog.w(TAG, "无支持分段的镜像，回退单线程下载: ${e.message}")
                        HttpFileDownloader.download(
                            url = ModLoaderVersionFetcher.applyGitHubProxy(version.url, useMirror),
                            targetFile = downloadPartFile,
                            isCancelled = { token.get() },
                            onProgress = progressCallback
                        )
                    }
                } else {
                    HttpFileDownloader.download(
                        url = ModLoaderVersionFetcher.applyGitHubProxy(version.url, useMirror),
                        targetFile = downloadPartFile,
                        isCancelled = { token.get() },
                        onProgress = progressCallback
                    )
                }

                if (token.get()) throw IOException("download cancelled")

                // 成功：临时文件重命名为最终名
                if (targetFile.exists()) targetFile.delete()
                if (!downloadPartFile.renameTo(targetFile)) throw IOException("重命名下载临时文件失败")
                partFile = null

                withContext(Dispatchers.Main) {
                    if (session != downloadSessionCounter) return@withContext   // 过期会话不触发安装预填
                    // 保留用户的镜像偏好，其余归零
                    updateModLoaderDownload { ModLoaderDownloadUiState(useMirror = it.useMirror) }
                    // 下载完成后直接按本地文件已选的路径走原有选择/检测流程
                    selectFile(
                        fileType = if (downloadTarget == ModLoaderDownloadTarget.GAME_FILES)
                            InstallerFileType.GAME else InstallerFileType.MOD_LOADER,
                        path = targetFile.absolutePath,
                        preferredName = version.version
                    )
                    // 登记：该文件来自本启动器的在线下载，导入成功后自动清理
                    onlineDownloadedFiles.add(targetFile.absolutePath)
                    _effects.tryEmit(
                        InstallerUiEffect.ShowSuccess(
                            appContext.getString(R.string.modloader_download_complete, version.version)
                        )
                    )
                }
            } catch (e: Exception) {
                partFile?.delete()
                AppLog.e(TAG, "ModLoader 下载失败: ${version.version}", e)
                // NonCancellable：取消下载时协程已处于 cancelled 状态，
                // 普通 withContext(Main) 会直接抛 CancellationException 跳过状态恢复，
                // 导致对话框永远停留在"下载中"（表现为点取消后卡死）
                withContext(Dispatchers.Main + NonCancellable) {
                    val cancelledByUser = token.get() || e.message?.contains("cancelled", ignoreCase = true) == true
                    if (session == downloadSessionCounter) {
                        if (cancelledByUser) {
                            // 用户主动取消：cancelModLoaderDownload 已即时恢复 UI，此处幂等
                            updateModLoaderDownload { it.copy(isDownloading = false) }
                        } else {
                            updateModLoaderDownload {
                                it.copy(
                                    isDownloading = false,
                                    downloadError = e.message
                                        ?: appContext.getString(R.string.modloader_download_failed)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cancelModLoaderDownload() {
        // 仅置位当前会话的令牌：老任务令牌保持 true，线程/采样器退出后永不复活；
        // 立即恢复对话框为版本列表（后台收尾幂等）
        activeDownloadToken.set(true)
        modLoaderDownloadJob?.cancel()
        modLoaderDownloadJob = null
        updateModLoaderDownload { it.copy(isDownloading = false, downloadError = null) }
    }

    private fun resolveModLoaderDownloadDir(): File {
        val external = File(Environment.getExternalStorageDirectory(), "RALauncher/ModLoaders")
        if (external.mkdirs() || external.exists()) return external

        // 外部存储不可用时回退到应用私有目录
        val appExternal = File(appContext.getExternalFilesDir(null), "ModLoaders")
        return if (appExternal.mkdirs() || appExternal.exists()) {
            appExternal
        } else {
            File(appContext.cacheDir, "ModLoaders").apply { mkdirs() }
        }
    }

    private fun selectFile(
        fileType: InstallerFileType,
        path: String,
        preferredName: String?
    ) {
        if (_uiState.value.isImporting) return

        val fallbackName = preferredName ?: fallbackDisplayName(path)
        _uiState.update { state ->
            when (fileType) {
                InstallerFileType.GAME -> state.copy(
                    gameFilePath = path,
                    detectedGameName = fallbackName,
                    progress = 0,
                    status = "",
                    errorMessage = null
                )

                InstallerFileType.MOD_LOADER -> state.copy(
                    modLoaderFilePath = path,
                    detectedModLoaderName = fallbackName,
                    progress = 0,
                    status = "",
                    errorMessage = null
                )
            }
        }

        detectSelection(fileType, path, preferredName)
    }

    private fun detectSelection(
        fileType: InstallerFileType,
        path: String,
        preferredName: String?
    ) {
        val file = File(path)
        viewModelScope.launch(Dispatchers.IO) {
            val detectedName = when (fileType) {
                InstallerFileType.GAME -> InstallPluginRegistry.detectGame(file)
                    ?.second
                    ?.definition
                    ?.displayName

                InstallerFileType.MOD_LOADER -> InstallPluginRegistry.detectModLoader(file)
                    ?.second
                    ?.definition
                    ?.displayName
            } ?: preferredName ?: fallbackDisplayName(path)

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    when (fileType) {
                        InstallerFileType.GAME ->
                            if (state.gameFilePath == path) {
                                state.copy(detectedGameName = detectedName)
                            } else {
                                state
                            }

                        InstallerFileType.MOD_LOADER ->
                            if (state.modLoaderFilePath == path) {
                                state.copy(detectedModLoaderName = detectedName)
                            } else {
                                state
                            }
                    }
                }
            }
        }
    }

    private fun startImport() {
        val state = _uiState.value
        if (state.isImporting) return

        if (state.gameFilePath.isNullOrEmpty() && state.modLoaderFilePath.isNullOrEmpty()) {
            _uiState.update {
                it.copy(errorMessage = appContext.getString(R.string.import_select_game_first))
            }
            return
        }

        _uiState.update {
            it.copy(
                isImporting = true,
                progress = 0,
                status = appContext.getString(R.string.import_preparing_import),
                errorMessage = null
            )
        }

        val installer = GameInstaller(gameRepository)
        activeInstaller = installer

        installer.install(
            gameFilePath = state.gameFilePath.orEmpty(),
            modLoaderFilePath = state.modLoaderFilePath,
            callback = object : InstallCallback {
                override fun onProgress(message: String, progress: Int) {
                    _uiState.update {
                        it.copy(
                            status = message,
                            progress = progress.coerceIn(0, 100)
                        )
                    }
                }

                override fun onComplete(gameItem: GameItem) {
                    activeInstaller = null
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            gameRepository.upsert(gameItem, 0)
                            // 导入成功：清理在线下载的源文件（仅登记过的来自在线下载的文件；
                            // 导入失败/取消时不清理，保留以便重试，用户手动选的文件永不删除）
                            val staleDownloads = onlineDownloadedFiles.toList()
                            onlineDownloadedFiles.removeAll(staleDownloads.toSet())
                            staleDownloads.forEach { path ->
                                runCatching { File(path).delete() }
                            }
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isImporting = false,
                                        progress = 100,
                                        status = appContext.getString(R.string.import_complete_exclamation),
                                        errorMessage = null
                                    )
                                }
                                _effects.tryEmit(
                                    InstallerUiEffect.ShowSuccess(
                                        appContext.getString(R.string.game_added_success)
                                    )
                                )
                                _effects.tryEmit(InstallerUiEffect.NavigateToGames)
                                resetSelections()
                            }
                        } catch (e: Exception) {
                            val message = e.message
                                ?: appContext.getString(R.string.import_error_game_import_failed)
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isImporting = false,
                                        errorMessage = message
                                    )
                                }
                                _effects.tryEmit(
                                    InstallerUiEffect.ShowToast(
                                        appContext.getString(
                                            R.string.import_failed_colon,
                                            e.message ?: appContext.getString(R.string.common_unknown_error)
                                        )
                                    )
                                )
                            }
                        }
                    }
                }

                override fun onError(error: String) {
                    activeInstaller = null
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = error
                        )
                    }
                    _effects.tryEmit(
                        InstallerUiEffect.ShowToast(
                            appContext.getString(R.string.import_failed_colon, error)
                        )
                    )
                }

                override fun onCancelled() {
                    activeInstaller = null
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = appContext.getString(R.string.import_cancelled)
                        )
                    }
                }
            }
        )
    }

    private fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun resetSelections() {
        cancelModLoaderDownload()
        modLoaderVersionsJob?.cancel()
        modLoaderVersionsJob = null
        _uiState.value = InstallerUiState()
    }

    private fun fallbackDisplayName(path: String): String {
        return File(path).nameWithoutExtension
    }

    override fun onCleared() {
        activeInstaller?.cancel()
        activeInstaller = null
        cancelModLoaderDownload()
        modLoaderVersionsJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "InstallerViewModel"

        /** 超过此大小（16MB）的下载走多线程分段（Range）下载器 */
        private const val RANGE_DOWNLOAD_THRESHOLD = 16L * 1024 * 1024
    }
}
