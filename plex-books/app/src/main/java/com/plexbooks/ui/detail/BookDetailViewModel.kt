package com.plexbooks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.DownloadEntity
import com.plexbooks.data.local.ProgressEntity
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

                // M4B audiobooks: Plex stores a single M4B as one child, with the actual
                // chapters nested one level deeper as children of that track.
                val firstLevel = mediaRepo.getChildren(ratingKey)
                val tracks = if (firstLevel.size == 1) {
                    val deeper = runCatching { mediaRepo.getChildren(firstLevel[0].ratingKey) }.getOrNull()
                    if (!deeper.isNullOrEmpty()) deeper else firstLevel
                } else {
                    firstLevel
                }

                val progress = mediaRepo.getLocalProgress(ratingKey)

                // Load per-track progress
                val trackProgress = tracks.mapNotNull { track ->
                    mediaRepo.getLocalProgress(track.ratingKey)?.let { track.ratingKey to it }
                }.toMap()

                _state.value = BookDetailUiState(
                    item = item,
                    tracks = tracks,
                    progress = progress,
                    trackProgress = trackProgress,
                    serverUri = serverUri,
                    serverToken = serverToken,
                    isLoading = false
                )

                // Observe download states for all tracks
                observeDownloads(tracks.map { it.ratingKey })
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
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
