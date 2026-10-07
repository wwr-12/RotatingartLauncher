package com.app.ralaunch.feature.announcement.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.feature.announcement.AnnouncementRepositoryService
import com.app.ralaunch.feature.announcement.AnnouncementUiEvent
import com.app.ralaunch.feature.announcement.AnnouncementUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
class AnnouncementViewModel(
    private val appContext: Context,
    private val repositoryService: AnnouncementRepositoryService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementUiState())
    val uiState: StateFlow<AnnouncementUiState> = _uiState.asStateFlow()

    private var announcementLoadJob: Job? = null
    private val markdownLoadJobs = mutableMapOf<String, Job>()

    init {
        onEvent(AnnouncementUiEvent.Load)
    }

    fun onEvent(event: AnnouncementUiEvent) {
        when (event) {
            AnnouncementUiEvent.Load -> loadAnnouncements(forceRefresh = false)
            AnnouncementUiEvent.Refresh -> loadAnnouncements(forceRefresh = true)
            AnnouncementUiEvent.Retry -> loadAnnouncements(forceRefresh = true)
            is AnnouncementUiEvent.SelectAnnouncement -> {
                selectAnnouncement(event.announcementId)
            }
            is AnnouncementUiEvent.EnsureMarkdown -> {
                ensureMarkdownLoaded(
                    announcementId = event.announcementId,
                    forceRefresh = event.forceRefresh
                )
            }
        }
    }

    private fun loadAnnouncements(forceRefresh: Boolean) {
        if (announcementLoadJob?.isActive == true) return

        val hasExistingContent = _uiState.value.announcements.isNotEmpty()
        _uiState.update { state ->
            state.copy(
                isInitialLoading = !hasExistingContent && !forceRefresh,
                isRefreshing = forceRefresh,
                loadErrorMessage = null
            )
        }

        announcementLoadJob = viewModelScope.launch {
            val result = repositoryService.fetchAnnouncements(forceRefresh = forceRefresh)
            result.fold(
                onSuccess = { announcements ->
                    val validIds = announcements.map { it.id }.toSet()
                    val currentState = _uiState.value

                    val markdownById = currentState.markdownById
                        .filterKeys { it in validIds }
                        .toMutableMap()
                    val markdownErrors = currentState.markdownErrors
                        .filterKeys { it in validIds }
                    val loadingMarkdownIds = currentState.loadingMarkdownIds
                        .filter { it in validIds }
                        .toSet()

                    announcements.forEach { announcement ->
                        if (!announcement.markdown.isNullOrBlank()) {
                            markdownById[announcement.id] = announcement.markdown
                        }
                    }

                    val selectedId = currentState.selectedAnnouncementId
                        ?.takeIf { existingId -> announcements.any { it.id == existingId } }
                        ?: announcements.firstOrNull()?.id

                    _uiState.update { state ->
                        state.copy(
                            announcements = announcements,
                            selectedAnnouncementId = selectedId,
                            markdownById = markdownById,
                            markdownErrors = markdownErrors,
                            loadingMarkdownIds = loadingMarkdownIds,
                            isInitialLoading = false,
                            isRefreshing = false,
                            loadErrorMessage = null
                        )
                    }

                    if (!selectedId.isNullOrBlank()) {
                        ensureMarkdownLoaded(
                            announcementId = selectedId,
                            forceRefresh = forceRefresh
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            loadErrorMessage = appContext.getString(R.string.announcement_load_failed)
                        )
                    }
                }
            )
        }
    }

    private fun selectAnnouncement(announcementId: String) {
        if (announcementId.isBlank()) return
        val current = _uiState.value
        if (current.selectedAnnouncementId == announcementId) return

        _uiState.update { state ->
            state.copy(
                selectedAnnouncementId = announcementId,
                loadErrorMessage = null
            )
        }
        ensureMarkdownLoaded(announcementId = announcementId, forceRefresh = false)
    }

    private fun ensureMarkdownLoaded(announcementId: String, forceRefresh: Boolean) {
        if (announcementId.isBlank()) return

        val current = _uiState.value
        if (!forceRefresh) {
            val hasContent = !current.markdownById[announcementId].isNullOrBlank()
            val isLoading = announcementId in current.loadingMarkdownIds
            if (hasContent || isLoading) return
        }

        if (markdownLoadJobs[announcementId]?.isActive == true) return

        _uiState.update { state ->
            state.copy(
                loadingMarkdownIds = state.loadingMarkdownIds + announcementId,
                markdownErrors = state.markdownErrors - announcementId
            )
        }

        markdownLoadJobs[announcementId] = viewModelScope.launch {
            val result = repositoryService.fetchAnnouncementMarkdown(
                announcementId = announcementId,
                forceRefresh = forceRefresh
            )

            result.fold(
                onSuccess = { markdown ->
                    _uiState.update { state ->
                        state.copy(
                            markdownById = state.markdownById + (announcementId to markdown),
                            markdownErrors = state.markdownErrors - announcementId
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(
                            markdownErrors = state.markdownErrors + (
                                announcementId to appContext.getString(R.string.announcement_load_content_failed)
                                )
                        )
                    }
                }
            )

            _uiState.update { state ->
                state.copy(loadingMarkdownIds = state.loadingMarkdownIds - announcementId)
            }
            markdownLoadJobs.remove(announcementId)
        }
    }

    override fun onCleared() {
        announcementLoadJob?.cancel()
        markdownLoadJobs.values.forEach { it.cancel() }
        markdownLoadJobs.clear()
        super.onCleared()
    }
}
