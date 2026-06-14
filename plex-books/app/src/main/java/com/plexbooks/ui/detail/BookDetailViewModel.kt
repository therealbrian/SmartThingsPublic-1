package com.plexbooks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexChapter
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.DownloadEntity
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
    val trackProgress: Map<String, ProgressEntity> = emptyMap(),
    val downloads: Map<String, DownloadEntity> = emptyMap(),
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

                // The ratingKey might be for an individual track (arrived from On Deck)
                // or an album (arrived from Library). Figure out the right level to load.
                val albumKey = when (item?.type) {
                    "track" -> item.parentRatingKey ?: ratingKey
                    else -> ratingKey
                }
                val bookItem = if (albumKey != ratingKey) mediaRepo.getMetadata(albumKey) ?: item else item

                val tracks = resolveChapters(albumKey)
                val progress = mediaRepo.getLocalProgress(albumKey)

                val trackProgress = tracks.mapNotNull { track ->
                    mediaRepo.getLocalProgress(track.ratingKey)?.let { track.ratingKey to it }
                }.toMap()

                _state.value = BookDetailUiState(
                    item = bookItem,
                    tracks = tracks,
                    progress = progress,
                    trackProgress = trackProgress,
                    serverUri = serverUri,
                    serverToken = serverToken,
                    isLoading = false
                )
                observeDownloads(tracks.map { it.ratingKey })
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    /**
     * Plex exposes M4B chapters in several ways depending on version and indexing state.
     * Try each strategy in order until we get more than one item.
     */
    private suspend fun resolveChapters(albumKey: String): List<PlexMediaItem> {
        // Strategy 1: direct children of the album — multi-file or pre-broken chapters
        val direct = runCatching { mediaRepo.getChildren(albumKey) }.getOrNull().orEmpty()
        if (direct.size > 1) return direct

        val singleChild = direct.firstOrNull()

        // Strategy 2: children of a single M4B child (chapters one level deeper)
        if (singleChild != null) {
            val deeper = runCatching { mediaRepo.getChildren(singleChild.ratingKey) }.getOrNull().orEmpty()
            if (deeper.size > 1) return deeper

            // Strategy 3: /chapters endpoint on the single child
            val chaptersEndpoint = runCatching {
                mediaRepo.getChaptersEndpoint(singleChild.ratingKey)
            }.getOrNull().orEmpty()
            if (chaptersEndpoint.isNotEmpty()) return chaptersEndpoint

            // Strategy 4: Chapter markers embedded in the track metadata
            val trackMeta = runCatching { mediaRepo.getMetadata(singleChild.ratingKey) }.getOrNull()
            val embedded = trackMeta?.chapters
            if (!embedded.isNullOrEmpty()) {
                return chaptersFromMarkers(singleChild, embedded)
            }
        }

        // Strategy 5: /chapters endpoint on the album itself
        val albumChapters = runCatching {
            mediaRepo.getChaptersEndpoint(albumKey)
        }.getOrNull().orEmpty()
        if (albumChapters.isNotEmpty()) return albumChapters

        return direct
    }

    private fun chaptersFromMarkers(parent: PlexMediaItem, chapters: List<PlexChapter>): List<PlexMediaItem> =
        chapters.mapIndexed { i, ch ->
            parent.copy(
                ratingKey = "${parent.ratingKey}_ch_$i",
                title = ch.tag.ifBlank { "Chapter ${i + 1}" },
                index = i + 1,
                duration = ch.endTimeOffset - ch.startTimeOffset,
                chapters = null
            )
        }

    private fun observeDownloads(ratingKeys: List<String>) {
        viewModelScope.launch {
            mediaRepo.getAllDownloads().collect { allDownloads ->
                val relevant = allDownloads
                    .filter { it.ratingKey in ratingKeys }
                    .associateBy { it.ratingKey }
                _state.value = _state.value.copy(downloads = relevant)
            }
        }
    }

    fun downloadTrack(track: PlexMediaItem) {
        val partKey = track.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: return
        viewModelScope.launch {
            mediaRepo.startDownload(track.ratingKey, track.title, partKey)
        }
    }

    fun deleteDownload(ratingKey: String) {
        viewModelScope.launch {
            mediaRepo.deleteDownload(ratingKey)
        }
    }

    fun refreshDownloadStatus(ratingKey: String) {
        viewModelScope.launch {
            mediaRepo.checkAndUpdateDownloadStatus(ratingKey)
        }
    }
}
