package com.plexbooks.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.api.model.streamKey
import com.plexbooks.data.api.model.totalDurationMs
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexMediaRepository
import com.plexbooks.service.AudioPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val author: String = "",
    val thumb: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val speed: Float = 1.0f
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepo: PlexMediaRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var currentRatingKey: String = ""
    private var serverUri: String = ""
    private var serverToken: String = ""

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startPositionUpdates() else positionJob?.cancel()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun initPlayer(ratingKey: String) {
        currentRatingKey = ratingKey
        viewModelScope.launch {
            serverUri = prefs.serverUri.first() ?: return@launch
            serverToken = prefs.serverToken.first() ?: return@launch

            val item = mediaRepo.getMetadata(ratingKey)
            if (item == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Item not found")
                return@launch
            }

            val thumbUrl = if (!item.thumb.isNullOrBlank())
                "$serverUri${item.thumb}?X-Plex-Token=$serverToken" else null

            val tracks = if (item.type == "album" || item.type == "artist") {
                mediaRepo.getChildren(ratingKey)
            } else {
                listOf(item)
            }

            val totalDuration = if (tracks.size == 1) tracks.first().totalDurationMs()
            else tracks.sumOf { it.totalDurationMs() }

            val author = item.grandparentTitle ?: item.parentTitle ?: ""
            _state.value = _state.value.copy(
                title = item.title,
                author = author,
                thumb = thumbUrl,
                durationMs = totalDuration,
                isLoading = false
            )

            connectAndPlay(tracks, item)
        }
    }

    private fun connectAndPlay(tracks: List<PlexMediaItem>, rootItem: PlexMediaItem) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)

            val savedProgress = viewModelScope.launch {
                val progress = mediaRepo.getLocalProgress(currentRatingKey)
                val mediaItems = tracks.mapNotNull { track ->
                    val localUri = mediaRepo.localFileUri(track.ratingKey)
                    val url = localUri ?: run {
                        val key = track.streamKey() ?: return@mapNotNull null
                        "$serverUri$key?X-Plex-Token=$serverToken&download=1"
                    }
                    MediaItem.Builder()
                        .setMediaId(track.ratingKey)
                        .setUri(url)
                        .build()
                }
                controller?.setMediaItems(mediaItems)
                controller?.prepare()
                progress?.let { p ->
                    controller?.seekTo(p.positionMs)
                }
                controller?.play()
            }
        }, MoreExecutors.directExecutor())
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun skipForward() {
        val current = controller?.currentPosition ?: return
        controller?.seekTo(current + 30_000)
    }

    fun skipBackward() {
        val current = controller?.currentPosition ?: return
        controller?.seekTo(maxOf(0, current - 15_000))
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val c = controller ?: break
                val pos = c.currentPosition
                val dur = c.duration.coerceAtLeast(0)
                _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
                if (pos > 0 && dur > 0) {
                    mediaRepo.saveProgress(
                        ratingKey = currentRatingKey,
                        title = _state.value.title,
                        thumb = _state.value.thumb,
                        positionMs = pos,
                        durationMs = dur
                    )
                }
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        positionJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        super.onCleared()
    }
}
