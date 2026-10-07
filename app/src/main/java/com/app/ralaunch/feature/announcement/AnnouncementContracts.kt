package com.app.ralaunch.feature.announcement

data class AnnouncementUiState(
    val announcements: List<AnnouncementItem> = emptyList(),
    val selectedAnnouncementId: String? = null,
    val markdownById: Map<String, String> = emptyMap(),
    val markdownErrors: Map<String, String> = emptyMap(),
    val loadingMarkdownIds: Set<String> = emptySet(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadErrorMessage: String? = null
) {
    val selectedAnnouncement: AnnouncementItem?
        get() {
            val selectedId = selectedAnnouncementId
            return when {
                selectedId.isNullOrBlank() -> announcements.firstOrNull()
                else -> announcements.firstOrNull { it.id == selectedId } ?: announcements.firstOrNull()
            }
        }
}

sealed interface AnnouncementUiEvent {
    data object Load : AnnouncementUiEvent
    data object Refresh : AnnouncementUiEvent
    data object Retry : AnnouncementUiEvent
    data class SelectAnnouncement(val announcementId: String) : AnnouncementUiEvent
    data class EnsureMarkdown(val announcementId: String, val forceRefresh: Boolean = false) : AnnouncementUiEvent
}
