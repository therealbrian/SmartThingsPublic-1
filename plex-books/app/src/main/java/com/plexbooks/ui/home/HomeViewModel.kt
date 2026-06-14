package com.plexbooks.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.DownloadStatus
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
    val isDownloaded: Boolean,
    val isCompleted: Boolean
)

data class HomeUiState(
    val serverName: String = "",
    val libraries: List<PlexLibrarySection> = emptyList(),
    val onDeck: List<PlexMediaItem> = emptyList(),
    val recentlyAdded: List<PlexMediaItem> = emptyList(),
    val allBooks: List<PlexMediaItem> = emptyList(),
    val allBooksSort: String = "titleSort",
    val serverUri: String = "",
    val serverToken: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreBooks: Boolean = true,
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

    private var bookSectionId: String? = null
    private var allBooksOffset = 0
    private var allBooksSort = "titleSort"
    private val PAGE_SIZE = 100

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
                isDownloaded = false,
                isCompleted = p.percentComplete >= 0.95f
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
                    isDownloaded = true,
                    isCompleted = false
                )
            }
        }

        map.values.sortedWith(
            compareBy<LibraryEntry> { it.isCompleted }
                .thenByDescending { it.isDownloaded && it.percentComplete == 0f }
                .thenByDescending { it.percentComplete }
        )
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
                bookSectionId = libraries.firstOrNull()?.key
                allBooksOffset = 0
                val onDeck = bookSectionId?.let { mediaRepo.getSectionOnDeck(it) } ?: emptyList()
                val recentlyAdded = bookSectionId?.let { mediaRepo.getSectionRecentlyAdded(it) } ?: emptyList()
                val allBooks = bookSectionId?.let {
                    runCatching { mediaRepo.getSectionItems(it, start = 0, size = PAGE_SIZE, sort = allBooksSort) }.getOrDefault(emptyList())
                } ?: emptyList()
                allBooksOffset = allBooks.size

                // Sync server-side on-deck progress into local DB so My Library shows all in-progress books
                onDeck.forEach { item ->
                    val posMs = item.viewOffset ?: 0L
                    val durMs = item.duration ?: 0L
                    if (posMs > 0 && durMs > 0) {
                        val thumbUrl = if (!item.thumb.isNullOrBlank())
                            "$serverUri${item.thumb}?X-Plex-Token=$serverToken" else null
                        mediaRepo.saveProgress(
                            ratingKey = item.ratingKey,
                            title = item.title,
                            thumb = thumbUrl,
                            positionMs = posMs,
                            durationMs = durMs
                        )
                    }
                }

                _state.value = HomeUiState(
                    serverName = serverName,
                    libraries = libraries,
                    onDeck = onDeck,
                    recentlyAdded = recentlyAdded,
                    allBooks = allBooks,
                    allBooksSort = allBooksSort,
                    serverUri = serverUri,
                    serverToken = serverToken,
                    isLoading = false,
                    hasMoreBooks = allBooks.size >= PAGE_SIZE
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMoreBooks() {
        val sectionId = bookSectionId ?: return
        if (_state.value.isLoadingMore || !_state.value.hasMoreBooks) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            runCatching {
                val more = mediaRepo.getSectionItems(sectionId, start = allBooksOffset, size = PAGE_SIZE, sort = allBooksSort)
                allBooksOffset += more.size
                _state.value = _state.value.copy(
                    allBooks = _state.value.allBooks + more,
                    isLoadingMore = false,
                    hasMoreBooks = more.size >= PAGE_SIZE
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun setSortOrder(sort: String) {
        if (allBooksSort == sort) return
        allBooksSort = sort
        val sectionId = bookSectionId ?: return
        allBooksOffset = 0
        _state.value = _state.value.copy(allBooksSort = sort, allBooks = emptyList(), isLoadingMore = false)
        viewModelScope.launch {
            val books = runCatching {
                mediaRepo.getSectionItems(sectionId, start = 0, size = PAGE_SIZE, sort = sort)
            }.getOrDefault(emptyList())
            allBooksOffset = books.size
            _state.value = _state.value.copy(allBooks = books, hasMoreBooks = books.size >= PAGE_SIZE)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            val bookSections = _state.value.libraries.filter {
                it.title.contains("book", ignoreCase = true) || it.type == "book"
            }.ifEmpty { _state.value.libraries.take(1) }
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

    fun removeFromLibrary(ratingKey: String) {
        viewModelScope.launch {
            mediaRepo.removeProgress(ratingKey)
            runCatching { mediaRepo.deleteDownload(ratingKey) }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}
