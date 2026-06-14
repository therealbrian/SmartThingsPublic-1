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
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepo: PlexMediaRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    fun load(sectionId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val serverUri = prefs.serverUri.first() ?: ""
            val serverToken = prefs.serverToken.first() ?: ""
            runCatching { mediaRepo.getSectionItems(sectionId) }
                .onSuccess { items ->
                    _state.value = LibraryUiState(
                        items = items,
                        serverUri = serverUri,
                        serverToken = serverToken,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
        }
    }
}
