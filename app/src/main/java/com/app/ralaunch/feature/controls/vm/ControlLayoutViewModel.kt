package com.app.ralaunch.feature.controls.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.packs.ControlPackInfo
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class ControlLayoutUiState(
    val layouts: List<ControlPackInfo> = emptyList(),
    val selectedPackId: String? = null,
    val quickSwitchIds: List<String> = emptyList(),
    val selectedLayoutId: String? = null,
    val showCreateDialog: Boolean = false,
    val showDeleteDialogId: String? = null,
    val showRenameDialogId: String? = null,
    val showMoreMenuId: String? = null,
    val previewPackId: String? = null
) {
    val selectedLayout: ControlPackInfo?
        get() = layouts.find { it.id == selectedLayoutId }

    val deleteDialogPack: ControlPackInfo?
        get() = layouts.find { it.id == showDeleteDialogId }

    val renameDialogPack: ControlPackInfo?
        get() = layouts.find { it.id == showRenameDialogId }

    val moreMenuPack: ControlPackInfo?
        get() = layouts.find { it.id == showMoreMenuId }

    val previewPack: ControlPackInfo?
        get() = layouts.find { it.id == previewPackId }
}

class ControlLayoutViewModel(
    private val appContext: Context,
    private val packManager: ControlPackManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ControlLayoutUiState())
    val uiState: StateFlow<ControlLayoutUiState> = _uiState.asStateFlow()

    init {
        loadLayouts()
    }

    fun loadLayouts() {
        val layouts = packManager.getInstalledPacks()
        val selectedPackId = packManager.getSelectedPackId()
        val quickSwitchIds = packManager.getQuickSwitchPackIds()
        val selectedLayoutId = _uiState.value.selectedLayoutId
            ?.takeIf { id -> layouts.any { it.id == id } }
            ?: selectedPackId
            ?: layouts.firstOrNull()?.id

        _uiState.value = _uiState.value.copy(
            layouts = layouts,
            selectedPackId = selectedPackId,
            quickSwitchIds = quickSwitchIds,
            selectedLayoutId = selectedLayoutId,
            showDeleteDialogId = _uiState.value.showDeleteDialogId?.takeIf { id -> layouts.any { it.id == id } },
            showRenameDialogId = _uiState.value.showRenameDialogId?.takeIf { id -> layouts.any { it.id == id } },
            showMoreMenuId = _uiState.value.showMoreMenuId?.takeIf { id -> layouts.any { it.id == id } },
            previewPackId = _uiState.value.previewPackId?.takeIf { id -> layouts.any { it.id == id } }
        )
    }

    fun selectLayout(pack: ControlPackInfo) {
        _uiState.update { it.copy(selectedLayoutId = pack.id) }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun showRenameDialog(pack: ControlPackInfo) {
        _uiState.update { it.copy(showRenameDialogId = pack.id, showMoreMenuId = null) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(showRenameDialogId = null) }
    }

    fun showDeleteDialog(pack: ControlPackInfo) {
        _uiState.update { it.copy(showDeleteDialogId = pack.id, showMoreMenuId = null) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialogId = null) }
    }

    fun showMoreMenu(pack: ControlPackInfo) {
        _uiState.update { it.copy(showMoreMenuId = pack.id) }
    }

    fun dismissMoreMenu() {
        _uiState.update { it.copy(showMoreMenuId = null) }
    }

    fun showPreview(pack: ControlPackInfo) {
        _uiState.update { it.copy(previewPackId = pack.id) }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewPackId = null) }
    }

    fun createNewLayout(name: String): Result<ControlPackInfo> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.layout_name_hint)))
        }

        if (_uiState.value.layouts.any { it.name.equals(name, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.control_layout_name_exists)))
        }

        val newPack = packManager.createPack(name)
        if (_uiState.value.selectedPackId == null) {
            packManager.setSelectedPackId(newPack.id)
        }
        _uiState.update { it.copy(showCreateDialog = false, selectedLayoutId = newPack.id) }
        loadLayouts()
        return Result.success(newPack)
    }

    fun setDefaultLayout(pack: ControlPackInfo) {
        packManager.setSelectedPackId(pack.id)
        loadLayouts()
    }

    fun toggleQuickSwitch(packId: String, enabled: Boolean) {
        if (enabled) {
            packManager.addToQuickSwitch(packId)
        } else {
            packManager.removeFromQuickSwitch(packId)
        }
        loadLayouts()
    }

    fun renameLayout(pack: ControlPackInfo, newName: String): Result<Unit> {
        if (newName.isBlank()) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.layout_name_hint)))
        }

        if (_uiState.value.layouts.any { it.id != pack.id && it.name.equals(newName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.control_layout_name_exists)))
        }

        if (!packManager.renamePack(pack.id, newName)) {
            return Result.failure(IllegalStateException(appContext.getString(R.string.control_rename_layout)))
        }

        _uiState.update { it.copy(showRenameDialogId = null) }
        loadLayouts()
        return Result.success(Unit)
    }

    fun deleteLayout(pack: ControlPackInfo): Boolean {
        val deleted = packManager.deletePack(pack.id)
        if (deleted && _uiState.value.selectedPackId == pack.id) {
            val remaining = packManager.getInstalledPacks()
            packManager.setSelectedPackId(remaining.firstOrNull()?.id)
        }
        _uiState.update { it.copy(showDeleteDialogId = null) }
        loadLayouts()
        return deleted
    }

    fun getPreviewImages(pack: ControlPackInfo): List<File> {
        val packDir = packManager.getPackDir(pack.id)
        return if (pack.previewImagePaths.isNotEmpty()) {
            pack.previewImagePaths.mapNotNull { path ->
                File(packDir, path).takeIf { it.exists() }
            }
        } else {
            listOf("preview.jpg", "preview.png", "preview.webp").mapNotNull { name ->
                File(packDir, name).takeIf { it.exists() }
            }
        }
    }
}
