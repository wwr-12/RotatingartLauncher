package com.app.ralaunch.feature.installer.contract

data class InstallerUiState(
    val gameFilePath: String? = null,
    val detectedGameName: String? = null,
    val modLoaderFilePath: String? = null,
    val detectedModLoaderName: String? = null,
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val status: String = "",
    val errorMessage: String? = null,
    val modLoaderDownload: ModLoaderDownloadUiState = ModLoaderDownloadUiState()
)

/**
 * ModLoader 在线版本条目（来源 GitHub Releases，动态获取）
 */
data class ModLoaderDownloadVersion(
    val version: String,
    val displayName: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val prerelease: Boolean,
    val publishedAt: String
)

/**
 * 在线下载目标：ModLoader 或 游戏本体（两者共用同一套 GitHub Releases 下载对话框）
 */
enum class ModLoaderDownloadTarget {
    MOD_LOADER,
    GAME_FILES
}

data class ModLoaderDownloadUiState(
    val isVisible: Boolean = false,
    val modLoaderName: String = "",
    val target: ModLoaderDownloadTarget = ModLoaderDownloadTarget.MOD_LOADER,
    val isLoadingVersions: Boolean = false,
    val versionsError: String? = null,
    val versions: List<ModLoaderDownloadVersion> = emptyList(),
    val selectedVersion: ModLoaderDownloadVersion? = null,
    val useMirror: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadingFileName: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val downloadError: String? = null
)

enum class InstallerFileType(
    val routeValue: String,
    val allowedExtensions: List<String>
) {
    GAME(
        routeValue = "game",
        allowedExtensions = listOf(".sh", ".zip", ".exe")
    ),
    MOD_LOADER(
        routeValue = "modloader",
        allowedExtensions = listOf(".zip")
    );

    companion object {
        fun fromRouteValue(value: String?): InstallerFileType? {
            return entries.firstOrNull { it.routeValue == value }
        }
    }
}

sealed interface InstallerUiEvent {
    data class PrefillFromDownload(
        val gameFilePath: String?,
        val modLoaderFilePath: String?,
        val detectedGameName: String?
    ) : InstallerUiEvent

    data class BrowseRequested(val fileType: InstallerFileType) : InstallerUiEvent

    data class FileSelected(
        val fileType: InstallerFileType,
        val path: String,
        val preferredName: String? = null
    ) : InstallerUiEvent

    data class ShowModLoaderDownload(
        val modLoaderName: String = "tModLoader",
        val target: ModLoaderDownloadTarget = ModLoaderDownloadTarget.MOD_LOADER
    ) : InstallerUiEvent
    data object DismissModLoaderDownload : InstallerUiEvent
    data class RefreshModLoaderVersions(val forceRefresh: Boolean = true) : InstallerUiEvent
    data class SelectModLoaderVersion(val version: ModLoaderDownloadVersion) : InstallerUiEvent
    data class DownloadModLoaderVersion(val version: ModLoaderDownloadVersion) : InstallerUiEvent
    data class SetModLoaderDownloadMirror(val enabled: Boolean) : InstallerUiEvent
    data object CancelModLoaderDownload : InstallerUiEvent

    data object StartImport : InstallerUiEvent
    data object DismissError : InstallerUiEvent
    data object ResetSelections : InstallerUiEvent
}

sealed interface InstallerUiEffect {
    data class NavigateToFileBrowser(val fileType: InstallerFileType) : InstallerUiEffect
    data object NavigateToGames : InstallerUiEffect
    data class ShowToast(val message: String) : InstallerUiEffect
    data class ShowSuccess(val message: String) : InstallerUiEffect
}
