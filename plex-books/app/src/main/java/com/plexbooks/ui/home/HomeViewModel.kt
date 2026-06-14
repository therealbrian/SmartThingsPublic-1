package com.plexbooks.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.ProgressEntity
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexAuthRepository
import com.plexbooks.data.repository.PlexMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val serverName: String = "",
    val libraries: List<PlexLibrarySection> = emptyList(),
    val onDeck: List<PlexMediaItem> = emptyList(),
    val recentlyAdded: List<PlexMediaItem> = emptyList(),
    val inProgress: List<ProgressEntity> = emptyList(),
    val serverUri: String = "",
    val serverToken: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepo: PlexMediaRepository,
    private val authRepo: PlexAuthRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    val progressItems = mediaRepo.getAllProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val serverUri = prefs.serverUri.first() ?: run {
                _state.value = _state.value.copy(isLoading = false, error = "No server configured")
                return@launch
            }
            val serverToken = prefs.serverToken.first() ?: ""
            val serverName = prefs.serverName.first() ?: ""
            runCatching {
                val libraries = mediaRepo.getBookLibraries()
                val onDeck = mediaRepo.getOnDeck()
                val recentlyAdded = mediaRepo.getRecentlyAdded()
                _state.value = HomeUiState(
                    serverName = serverName,
                    libraries = libraries,
                    onDeck = onDeck,
                    recentlyAdded = recentlyAdded,
                    serverUri = serverUri,
                    serverToken = serverToken,
                    isLoading = false
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}
