package com.plexbooks.service

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.plexbooks.R
import com.plexbooks.data.api.model.streamKey
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexMediaRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioPlaybackService : MediaLibraryService() {

    @Inject lateinit var mediaRepo: PlexMediaRepository
    @Inject lateinit var prefs: PlexPreferences

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var bookSectionId: String? = null
    private var serverUri: String = ""
    private var serverToken: String = ""

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        serviceScope.launch {
            serverUri = prefs.serverUri.first() ?: ""
            serverToken = prefs.serverToken.first() ?: ""
            runCatching {
                bookSectionId = mediaRepo.getBookLibraries().firstOrNull()?.key
            }
        }

        val skipBack = CommandButton.Builder()
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Skip back 15s")
            .setIconResId(R.drawable.ic_notif_replay)
            .build()

        val skipForward = CommandButton.Builder()
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Skip forward 30s")
            .setIconResId(R.drawable.ic_notif_forward)
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setCustomLayout(ImmutableList.of(skipBack, skipForward))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.accept(
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        )

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> =
            Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("PlexBooks")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val sectionId = bookSectionId
                    if (sectionId == null) {
                        future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
                        return@launch
                    }
                    val books = mediaRepo.getSectionItems(
                        sectionId,
                        start = page * pageSize,
                        size = pageSize,
                        sort = "titleSort"
                    )
                    val items = books.map { book ->
                        val thumbUri = book.thumb
                            ?.takeIf { it.isNotBlank() }
                            ?.let { Uri.parse("$serverUri$it?X-Plex-Token=$serverToken") }
                        MediaItem.Builder()
                            .setMediaId(book.ratingKey)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(book.title)
                                    .setArtist(book.grandparentTitle ?: book.parentTitle ?: "")
                                    .setArtworkUri(thumbUri)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .build()
                            )
                            .build()
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), null))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            if (mediaItems.all { it.localConfiguration?.uri != null }) {
                return Futures.immediateFuture(mediaItems)
            }
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                try {
                    val resolved = mediaItems.map { item ->
                        if (item.localConfiguration?.uri != null) return@map item
                        val ratingKey = item.mediaId
                        val localUri = mediaRepo.localFileUri(ratingKey)
                        if (localUri != null) {
                            item.buildUpon().setUri(Uri.parse(localUri)).build()
                        } else {
                            val metadata = mediaRepo.getMetadata(ratingKey)
                            val tracks = when (metadata?.type) {
                                "album", "artist" -> mediaRepo.getChildren(ratingKey)
                                else -> listOfNotNull(metadata)
                            }
                            val key = tracks.firstOrNull()?.streamKey()
                            if (key != null) {
                                item.buildUpon()
                                    .setUri(Uri.parse("$serverUri$key?X-Plex-Token=$serverToken&download=1"))
                                    .build()
                            } else item
                        }
                    }
                    future.set(resolved.toMutableList())
                } catch (e: Exception) {
                    future.set(mediaItems)
                }
            }
            return future
        }
    }

    companion object {
        private const val ROOT_ID = "ROOT"
    }
}
