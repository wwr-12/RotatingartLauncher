package com.app.ralaunch.feature.filebrowser.vm

import android.os.Environment
import androidx.lifecycle.ViewModel
import com.app.ralaunch.feature.filebrowser.FileBrowserUiState
import com.app.ralaunch.feature.filebrowser.FileItemData
import com.app.ralaunch.feature.filebrowser.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class FileBrowserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var allowedExtensions: List<String> = emptyList()
    private var allFiles: List<FileItemData> = emptyList()
    private var initializedKey: String? = null

    fun initialize(
        initialPath: String,
        allowedExtensions: List<String>,
        hasPermission: Boolean
    ) {
        this.allowedExtensions = allowedExtensions
        val newKey = buildString {
            append(initialPath)
            append("|")
            append(allowedExtensions.joinToString(","))
        }
        val needsReload = initializedKey != newKey
        initializedKey = newKey

        _uiState.update { it.copy(hasPermission = hasPermission) }

        if (!hasPermission) return

        if (needsReload || _uiState.value.currentPath.isBlank()) {
            val startPath = resolveStartPath(initialPath)
            loadDirectory(startPath)
        } else {
            refreshVisibleFiles()
        }
    }

    fun updatePermission(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
        if (hasPermission && _uiState.value.currentPath.isBlank()) {
            loadDirectory(resolveStartPath(""))
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshVisibleFiles()
    }

    fun onSortModeChange(sortMode: SortMode) {
        _uiState.update { it.copy(sortMode = sortMode) }
        allFiles = sortFiles(allFiles, sortMode)
        refreshVisibleFiles()
    }

    fun onFileClick(file: FileItemData) {
        if (file.isDirectory) {
            loadDirectory(file.path)
            return
        }

        _uiState.update { state ->
            state.copy(
                selectedFile = if (state.selectedFile?.path == file.path) null else file
            )
        }
    }

    private fun loadDirectory(path: String) {
        _uiState.update { it.copy(isLoading = true) }

        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        val newFiles = mutableListOf<FileItemData>()

        directory.parentFile?.let { parent ->
            if (!isRootDirectory(directory)) {
                newFiles.add(
                    FileItemData(
                        name = "..",
                        path = parent.absolutePath,
                        isDirectory = true,
                        isParent = true
                    )
                )
            }
        }

        directory.listFiles()?.let { dirFiles ->
            dirFiles.filter { it.isDirectory && !it.isHidden }
                .forEach {
                    newFiles.add(
                        FileItemData(
                            name = it.name,
                            path = it.absolutePath,
                            isDirectory = true,
                            size = 0,
                            lastModified = it.lastModified()
                        )
                    )
                }

            dirFiles.filter { it.isFile && !it.isHidden && isFileAllowed(it, allowedExtensions) }
                .forEach {
                    newFiles.add(
                        FileItemData(
                            name = it.name,
                            path = it.absolutePath,
                            isDirectory = false,
                            size = it.length(),
                            lastModified = it.lastModified()
                        )
                    )
                }
        }

        allFiles = sortFiles(newFiles, _uiState.value.sortMode)
        _uiState.update {
            it.copy(
                currentPath = directory.absolutePath,
                selectedFile = null,
                isLoading = false
            )
        }
        refreshVisibleFiles()
    }

    private fun refreshVisibleFiles() {
        val query = _uiState.value.searchQuery
        val filteredFiles = if (query.isBlank()) {
            allFiles
        } else {
            allFiles.filter { it.name.contains(query, ignoreCase = true) }
        }

        _uiState.update { it.copy(files = filteredFiles) }
    }

    private fun resolveStartPath(initialPath: String): String {
        if (initialPath.isNotEmpty() && File(initialPath).exists()) {
            return initialPath
        }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return if (downloads.exists()) {
            downloads.absolutePath
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
    }

    private fun isRootDirectory(directory: File): Boolean {
        val externalStorage = Environment.getExternalStorageDirectory()
        return directory.absolutePath == "/" || directory.absolutePath == externalStorage.absolutePath
    }

    private fun isFileAllowed(file: File, allowedExtensions: List<String>): Boolean {
        if (allowedExtensions.isEmpty()) return true
        val fileName = file.name.lowercase()
        return allowedExtensions.any { fileName.endsWith(it.lowercase()) }
    }

    private fun sortFiles(files: List<FileItemData>, sortMode: SortMode): List<FileItemData> {
        val parentItem = files.firstOrNull { it.isParent }
        val directories = files.filter { it.isDirectory && !it.isParent }
        val regularFiles = files.filter { !it.isDirectory }

        val comparator: Comparator<FileItemData> = when (sortMode) {
            SortMode.SIZE -> compareByDescending { it.size }
            SortMode.TIME -> compareByDescending { it.lastModified }
            SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

        return buildList {
            parentItem?.let { add(it) }
            addAll(directories.sortedWith(comparator))
            addAll(regularFiles.sortedWith(comparator))
        }
    }
}
