package com.app.ralaunch.feature.controls.packs.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.packs.ControlPackItem
import com.app.ralaunch.feature.controls.packs.ControlPackRepositoryService
import com.app.ralaunch.feature.controls.packs.vm.ControlPackViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * 控制包商店页面 Wrapper
 * 处理 Android 特定逻辑，将纯 UI 委托给 ControlPackScreen
 */
@Composable
fun ControlStoreScreenWrapper(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val viewModelStoreOwner = activity as? ViewModelStoreOwner ?: return
    val viewModel: ControlPackViewModel = koinViewModel(
        viewModelStoreOwner = viewModelStoreOwner
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPack by remember { mutableStateOf<ControlPackItem?>(null) }
    
    // 获取仓库 URL
    val repoUrl = remember { ControlPackRepositoryService.getDefaultRepoUrl(context) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        ControlPackScreen(
            uiState = uiState,
            onBackClick = onBack,
            onRefresh = { viewModel.loadPacks(forceRefresh = true) },
            onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
            onPackClick = { selectedPack = it },
            onDownloadClick = { viewModel.downloadPack(it) },
            onUpdateClick = { viewModel.downloadPack(it) },
            onApplyClick = { applyPack(context, viewModel, it) },
            onDeleteClick = { confirmDeletePack(context, viewModel, it) }
        )

        // 预览对话框
        selectedPack?.let { pack ->
            PackPreviewDialog(
                pack = pack,
                repoUrl = repoUrl,
                onDismiss = { selectedPack = null },
                onDownloadClick = { viewModel.downloadPack(pack) },
                onUpdateClick = { viewModel.downloadPack(pack) },
                onApplyClick = { applyPack(context, viewModel, pack) },
                onDeleteClick = { confirmDeletePack(context, viewModel, pack) }
            )
        }
    }
}

private fun applyPack(
    context: android.content.Context,
    viewModel: ControlPackViewModel,
    item: ControlPackItem
) {
    val success = viewModel.applyPack(item)
    if (success) {
        Toast.makeText(
            context,
            context.getString(R.string.pack_applied_success, item.info.name),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.pack_apply_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun confirmDeletePack(
    context: android.content.Context,
    viewModel: ControlPackViewModel,
    item: ControlPackItem
) {
    // 简化：直接删除，实际可改为 Compose Dialog
    val success = viewModel.deletePack(item)
    if (success) {
        Toast.makeText(
            context,
            context.getString(R.string.pack_deleted_success, item.info.name),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.pack_delete_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}
