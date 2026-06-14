package com.plexbooks.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.DownloadStatus
import com.plexbooks.data.local.ProgressEntity
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexAuthRepository
import com.plexbooks.data.repository.PlexMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryEntry(
    val ratingKey: String,
    val title: String,
    val thumbUrl: String?,
    val percentComplete: Float,
    val isDownloaded: Boolean
)

data class HomeUiState(
    val serverName: String = "",
    val libraries: List<PlexLibrarySection> = emptyList(),
    val onDeck: List<PlexMediaItem> = emptyList(),
    val recentlyAdded: List<PlexMediaItem> = emptyList(),
    val serverUri: String = "",
    val serverToken: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepo: PlexMediaRepository,
    private val authRepo: PlexAuthRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<PlexMediaItem>>(emptyList())
    val searchResults: StateFlow<List<PlexMediaItem>> = _searchResults

    val libraryItems: StateFlow<List<LibraryEntry>> = combine(
        mediaRepo.getAllProgress(),
        mediaRepo.getAllDownloads()
    ) { progressList, downloadList ->
        val map = mutableMapOf<String, LibraryEntry>()

        progressList.forEach { p ->
            map[p.ratingKey] = LibraryEntry(
                ratingKey = p.ratingKey,
                title = p.title.ifBlank { return@forEach },
                thumbUrl = p.thumb,
                percentComplete = p.percentComplete,
                isDownloaded = false
            )
        }

        downloadList.filter { it.status == DownloadStatus.DONE }.forEach { d ->
            val existing = map[d.ratingKey]
            if (existing != null) {
                map[d.ratingKey] = existing.copy(isDownloaded = true)
            } else if (d.title.isNotBlank()) {
                map[d.ratingKey] = LibraryEntry(
                    ratingKey = d.ratingKey,
                    title = d.title,
                    thumbUrl = null,
                    percentComplete = 0f,
                    isDownloaded = true
                )
            }
        }

        map.values.sortedWith(compareByDescending<LibraryEntry> { it.isDownloaded && it.percentComplete == 0f }
            .thenByDescending { it.percentComplete })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            // Only search in dedicated book libraries (type == "book"), not music/podcast sections
            val bookSections = _state.value.libraries.filter { it.type == "book" }
                .ifEmpty { _state.value.libraries }  // fallback to all if no "book" type found
            val results = bookSections.flatMap { section ->
                runCatching { mediaRepo.searchBooks(section.key, query) }.getOrDefault(emptyList())
            }.distinctBy { it.ratingKey }
            _searchResults.value = results
        }
    }

    fun deleteDownload(ratingKey: String) {
        viewModelScope.launch {
            mediaRepo.deleteDownload(ratingKey)
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}
