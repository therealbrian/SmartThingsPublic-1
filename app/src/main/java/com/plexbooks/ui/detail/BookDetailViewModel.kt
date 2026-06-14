package com.plexbooks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexChapter
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.api.model.streamKey
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
    val sourceFiles: List<PlexMediaItem> = emptyList(),
    val progress: ProgressEntity? = null,
    val trackProgress: Map<String, ProgressEntity> = emptyMap(),
    val bookDownload: DownloadEntity? = null,
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

                val albumKey = when (item?.type) {
                    "track" -> item.parentRatingKey ?: ratingKey
                    else -> ratingKey
                }
                val bookItem = if (albumKey != ratingKey) mediaRepo.getMetadata(albumKey) ?: item else item

                val firstLevel = runCatching { mediaRepo.getChildren(albumKey) }.getOrNull().orEmpty()
                val tracks = resolveChapters(albumKey, firstLevel)
                val progress = mediaRepo.getLocalProgress(albumKey)

                val trackProgress = tracks.mapNotNull { track ->
                    mediaRepo.getLocalProgress(track.ratingKey)?.let { track.ratingKey to it }
                }.toMap()

                _state.value = BookDetailUiState(
                    item = bookItem,
                    tracks = tracks,
                    sourceFiles = firstLevel,
                    progress = progress,
                    trackProgress = trackProgress,
                    serverUri = serverUri,
                    serverToken = serverToken,
                    isLoading = false
                )
                observeBookDownload(albumKey)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun resolveChapters(albumKey: String, firstLevel: List<PlexMediaItem>): List<PlexMediaItem> {
        if (firstLevel.size > 1) return firstLevel

        val singleChild = firstLevel.firstOrNull()
        if (singleChild != null) {
            val deeper = runCatching { mediaRepo.getChildren(singleChild.ratingKey) }.getOrNull().orEmpty()
            if (deeper.size > 1) return deeper

            val chaptersEndpoint = runCatching {
                mediaRepo.getChaptersEndpoint(singleChild.ratingKey)
            }.getOrNull().orEmpty()
            if (chaptersEndpoint.isNotEmpty()) return chaptersEndpoint

            val trackMeta = runCatching { mediaRepo.getMetadata(singleChild.ratingKey) }.getOrNull()
            val embedded = trackMeta?.chapters
            if (!embedded.isNullOrEmpty()) return chaptersFromMarkers(singleChild, embedded)
        }

        val albumChapters = runCatching {
            mediaRepo.getChaptersEndpoint(albumKey)
        }.getOrNull().orEmpty()
        if (albumChapters.isNotEmpty()) return albumChapters

        return firstLevel
    }

    private fun chaptersFromMarkers(parent: PlexMediaItem, chapters: List<PlexChapter>): List<PlexMediaItem> =
        chapters.mapIndexed { i, ch ->
            parent.copy(
                ratingKey = parent.ratingKey,
                key = "${parent.key}#ch$i",
                title = ch.tag?.ifBlank { "Chapter ${i + 1}" } ?: "Chapter ${i + 1}",
                index = ch.index ?: (i + 1),
                duration = ch.endTimeOffset - ch.startTimeOffset,
                viewOffset = ch.startTimeOffset,
                chapters = null
            )
        }.distinctBy { it.key }

    private fun observeBookDownload(albumKey: String) {
        viewModelScope.launch {
            mediaRepo.observeDownload(albumKey).collect { download ->
                _state.value = _state.value.copy(bookDownload = download)
            }
        }
    }

    fun downloadBook() {
        viewModelScope.launch {
            val albumKey = _state.value.item?.ratingKey ?: return@launch
            val title = _state.value.item?.title ?: return@launch
            val files = _state.value.sourceFiles.ifEmpty { _state.value.tracks }
            val file = files.firstOrNull() ?: return@launch
            val partKey = file.streamKey() ?: return@launch
            mediaRepo.startDownload(albumKey, title, partKey)
        }
    }

    fun deleteBook() {
        viewModelScope.launch {
            val albumKey = _state.value.item?.ratingKey ?: return@launch
            mediaRepo.deleteDownload(albumKey)
        }
    }
}
