package com.app.ralaunch.feature.sponsor.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.feature.sponsor.SponsorRepository
import com.app.ralaunch.feature.sponsor.SponsorRepositoryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SponsorsUiState {
    data object Loading : SponsorsUiState()
    data class Error(val message: String) : SponsorsUiState()
    data class Success(val repository: SponsorRepository) : SponsorsUiState()
}

class SponsorsViewModel(
    private val appContext: Context,
    private val sponsorService: SponsorRepositoryService
) : ViewModel() {
    private val _uiState = MutableStateFlow<SponsorsUiState>(SponsorsUiState.Loading)
    val uiState: StateFlow<SponsorsUiState> = _uiState.asStateFlow()

    init {
        loadSponsors(forceRefresh = true)
    }

    fun retry() {
        loadSponsors(forceRefresh = true)
    }

    private fun loadSponsors(forceRefresh: Boolean) {
        _uiState.value = SponsorsUiState.Loading
        viewModelScope.launch {
            val result = sponsorService.fetchSponsors(forceRefresh = forceRefresh)
            result.fold(
                onSuccess = { repository ->
                    _uiState.value = if (repository.sponsors.isEmpty()) {
                        SponsorsUiState.Error(appContext.getString(R.string.sponsors_empty))
                    } else {
                        SponsorsUiState.Success(repository)
                    }
                },
                onFailure = { error ->
                    _uiState.value = SponsorsUiState.Error(
                        appContext.getString(R.string.sponsors_error) + "\n" + (error.message ?: "")
                    )
                }
            )
        }
    }
}
