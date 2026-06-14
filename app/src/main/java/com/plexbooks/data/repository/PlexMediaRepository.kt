package com.plexbooks.data.repository

import com.plexbooks.data.api.PlexMediaApi
import com.plexbooks.data.api.model.BOOK_LIBRARY_TYPES
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.ProgressDao
import com.plexbooks.data.local.ProgressEntity
import com.plexbooks.data.prefs.PlexPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexMediaRepository @Inject constructor(
    private val mediaApi: PlexMediaApi,
    private val prefs: PlexPreferences,
    private val progressDao: ProgressDao
) {
    suspend fun getBookLibraries(): List<PlexLibrarySection> =
        mediaApi.getLibrarySections()
            .mediaContainer
            .directories
            .orEmpty()
            .filter { it.type in BOOK_LIBRARY_TYPES }

    suspend fun getSectionItems(sectionId: String): List<PlexMediaItem> =
        mediaApi.getSectionItems(sectionId, type = 9)
            .mediaContainer.metadata.orEmpty()

    suspend fun getChildren(ratingKey: String): List<PlexMediaItem> =
        mediaApi.getChildren(ratingKey).mediaContainer.metadata.orEmpty()

    suspend fun getOnDeck(): List<PlexMediaItem> =
        mediaApi.getOnDeck().mediaContainer.metadata.orEmpty()

    suspend fun getRecentlyAdded(): List<PlexMediaItem> =
        mediaApi.getRecentlyAdded().mediaContainer.metadata.orEmpty()

    suspend fun getMetadata(ratingKey: String): PlexMediaItem? =
        mediaApi.getMetadata(ratingKey).mediaContainer.metadata?.firstOrNull()

    suspend fun reportProgress(ratingKey: String, positionMs: Long, durationMs: Long, playing: Boolean) {
        val state = if (playing) "playing" else "paused"
        runCatching {
            mediaApi.reportProgress(
                ratingKey = ratingKey,
                timeMs = positionMs,
                state = state,
                durationMs = durationMs
            )
        }
        progressDao.upsert(
            ProgressEntity(
                ratingKey = ratingKey,
                title = "",
                thumb = null,
                positionMs = positionMs,
                durationMs = durationMs
            )
        )
    }

    suspend fun saveProgress(ratingKey: String, title: String, thumb: String?, positionMs: Long, durationMs: Long) {
        progressDao.upsert(
            ProgressEntity(
                ratingKey = ratingKey,
                title = title,
                thumb = thumb,
                positionMs = positionMs,
                durationMs = durationMs
            )
        )
    }

    suspend fun getLocalProgress(ratingKey: String): ProgressEntity? =
        progressDao.get(ratingKey)

    fun getAllProgress() = progressDao.getAll()

    fun thumbnailUrl(serverUri: String, thumb: String?, serverToken: String): String? {
        if (thumb.isNullOrBlank()) return null
        return "$serverUri$thumb?X-Plex-Token=$serverToken"
    }

    fun streamUrl(serverUri: String, partKey: String, serverToken: String): String =
        "$serverUri$partKey?X-Plex-Token=$serverToken&download=1"
}
