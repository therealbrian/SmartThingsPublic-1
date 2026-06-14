package com.plexbooks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val items: List<PlexMediaItem> = emptyList(),
    val serverUri: String = "",
    val serverToken: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepo: PlexMediaRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private var sectionId: String = ""
    private var offset = 0
    private val PAGE_SIZE = 100

    fun load(id: String) {
        sectionId = id
        offset = 0
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val serverUri = prefs.serverUri.first() ?: ""
            val serverToken = prefs.serverToken.first() ?: ""
            runCatching { mediaRepo.getSectionItems(id, start = 0, size = PAGE_SIZE) }
                .onSuccess { items ->
                    offset = items.size
                    _state.value = LibraryUiState(
                        items = items,
                        serverUri = serverUri,
                        serverToken = serverToken,
                        isLoading = false,
                        hasMore = items.size >= PAGE_SIZE
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            runCatching { mediaRepo.getSectionItems(sectionId, start = offset, size = PAGE_SIZE) }
                .onSuccess { more ->
                    offset += more.size
                    _state.value = _state.value.copy(
                        items = _state.value.items + more,
                        isLoadingMore = false,
                        hasMore = more.size >= PAGE_SIZE
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
        }
    }
}
