package com.app.ralaunch.feature.filebrowser

/**
 * 文件项数据
 */
data class FileItemData(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isParent: Boolean = false,
    val size: Long = 0,
    val lastModified: Long = 0
)

/**
 * 排序模式
 */
enum class SortMode {
    NAME, SIZE, TIME
}

/**
 * 文件浏览器 UI 状态
 */
data class FileBrowserUiState(
    val currentPath: String = "",
    val files: List<FileItemData> = emptyList(),
    val selectedFile: FileItemData? = null,
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NAME,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * 格式化文件大小
 */
fun formatFileSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
    else -> "${size / (1024 * 1024 * 1024)} GB"
}
