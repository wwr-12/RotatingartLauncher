package com.app.ralaunch.feature.gog.model

/**
 * GOG 客户端 UI 状态 - 跨平台
 */
data class GogUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null,
    
    // 用户信息
    val username: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    
    // 游戏列表
    val games: List<GogGameUi> = emptyList(),
    val filteredGames: List<GogGameUi> = emptyList(),
    val searchQuery: String = "",
    
    // 选中的游戏
    val selectedGame: GogGameUi? = null,
    val gameDetails: GogGameDetailsUi? = null,
    
    // 下载状态
    val downloadState: DownloadState? = null
)

/**
 * GOG 游戏 UI 模型 - 跨平台
 */
data class GogGameUi(
    val id: Long,
    val title: String,
    val imageUrl: String?,
    val isInstalled: Boolean = false
)

/**
 * GOG 游戏详情 UI 模型 - 跨平台
 */
data class GogGameDetailsUi(
    val id: Long,
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val versions: List<GameVersionUi> = emptyList(),
    val modLoaderVersions: List<ModLoaderVersionUi> = emptyList()
)

data class GameVersionUi(
    val name: String,
    val version: String,
    val size: String,
    val path: String?
)

data class ModLoaderVersionUi(
    val name: String,
    val version: String,
    val url: String,
    val fileName: String?
)

/**
 * 下载状态 - 跨平台
 */
data class DownloadState(
    val gameFileName: String,
    val modLoaderName: String,
    val gameProgress: Float = 0f,
    val modLoaderProgress: Float = 0f,
    val gameStatus: String = "",
    val modLoaderStatus: String = "",
    val isComplete: Boolean = false
)
