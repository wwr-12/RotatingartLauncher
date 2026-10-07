package com.app.ralaunch.feature.filebrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.ralaunch.feature.filebrowser.vm.FileBrowserViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 文件浏览器 Screen Wrapper - App 层
 * 处理 Android 特定逻辑（文件系统访问、权限）
 */
@Composable
fun FileBrowserScreenWrapper(
    initialPath: String = "",
    fileType: String? = null,
    allowedExtensions: List<String> = emptyList(),
    title: String? = null,
    hasPermission: Boolean = true,
    onFileSelected: (String, String?) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit = {}
) {
    val viewModel: FileBrowserViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialPath, allowedExtensions, hasPermission) {
        viewModel.initialize(
            initialPath = initialPath,
            allowedExtensions = allowedExtensions,
            hasPermission = hasPermission
        )
    }

    FileBrowserScreen(
        state = uiState,
        title = title,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSortModeChange = viewModel::onSortModeChange,
        onFileClick = viewModel::onFileClick,
        onConfirm = {
            uiState.selectedFile?.let { file ->
                onFileSelected(file.path, fileType)
            }
        },
        onBack = onBack,
        onRequestPermission = onRequestPermission
    )
}
