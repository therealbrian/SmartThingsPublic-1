package com.plexbooks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.api.model.streamKey
import com.plexbooks.data.api.model.totalDurationMs
import com.plexbooks.data.local.ProgressEntity
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val item: PlexMediaItem? = null,
    val tracks: List<PlexMediaItem> = emptyList(),
    val progress: ProgressEntity? = null,
    val serverUri: String = "",
    val serverToken: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val mediaRepo: PlexMediaRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState())
    val state: StateFlow<BookDetailUiState> = _state

    fun load(ratingKey: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val serverUri = prefs.serverUri.first() ?: ""
            val serverToken = prefs.serverToken.first() ?: ""
            runCatching {
                val item = mediaRepo.getMetadata(ratingKey)
                val tracks = mediaRepo.getChildren(ratingKey)
                val progress = mediaRepo.getLocalProgress(ratingKey)
                _state.value = BookDetailUiState(
                    item = item,
                    tracks = tracks,
                    progress = progress,
                    serverUri = serverUri,
                    serverToken = serverToken,
                    isLoading = false
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
