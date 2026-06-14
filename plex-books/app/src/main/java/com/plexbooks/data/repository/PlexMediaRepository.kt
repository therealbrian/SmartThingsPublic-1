package com.plexbooks.data.repository

import com.plexbooks.data.api.PlexMediaApi
import com.plexbooks.data.api.model.BOOK_LIBRARY_TYPES
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.local.DownloadDao
import com.plexbooks.data.local.DownloadEntity
import com.plexbooks.data.local.DownloadManagerHelper
import com.plexbooks.data.local.DownloadStatus
import com.plexbooks.data.local.ProgressDao
import com.plexbooks.data.local.ProgressEntity
import com.plexbooks.data.prefs.PlexPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexMediaRepository @Inject constructor(
    private val mediaApi: PlexMediaApi,
    private val prefs: PlexPreferences,
    private val progressDao: ProgressDao,
    private val downloadDao: DownloadDao,
    private val downloadHelper: DownloadManagerHelper
) {
    suspend fun getBookLibraries(): List<PlexLibrarySection> =
        mediaApi.getLibrarySections()
            .mediaContainer
            .directories
            .orEmpty()
            .filter { it.title.contains("book", ignoreCase = true) || it.type == "book" }

    suspend fun getSectionItems(sectionId: String): List<PlexMediaItem> =
        mediaApi.getSectionItems(sectionId, type = 9)
            .mediaContainer.metadata.orEmpty()

    suspend fun searchBooks(sectionId: String, query: String): List<PlexMediaItem> =
        mediaApi.getSectionItems(sectionId, type = 9, title = query, size = 50)
            .mediaContainer.metadata.orEmpty()

    suspend fun getChildren(ratingKey: String): List<PlexMediaItem> =
        mediaApi.getChildren(ratingKey).mediaContainer.metadata.orEmpty()

    suspend fun getChaptersEndpoint(ratingKey: String): List<PlexMediaItem> =
        mediaApi.getChapters(ratingKey).mediaContainer.metadata.orEmpty()

    suspend fun getOnDeck(): List<PlexMediaItem> =
        mediaApi.getOnDeck().mediaContainer.metadata.orEmpty()

    suspend fun getRecentlyAdded(): List<PlexMediaItem> =
        mediaApi.getRecentlyAdded().mediaContainer.metadata.orEmpty()

    suspend fun getSectionOnDeck(sectionId: String): List<PlexMediaItem> =
        mediaApi.getSectionOnDeck(sectionId).mediaContainer.metadata.orEmpty()

    suspend fun getSectionRecentlyAdded(sectionId: String): List<PlexMediaItem> =
        mediaApi.getSectionRecentlyAdded(sectionId).mediaContainer.metadata.orEmpty()

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

    // ── Downloads ────────────────────────────────────────────────────────────

    suspend fun startDownload(ratingKey: String, title: String, partKey: String) {
        val serverUri = prefs.serverUri.first() ?: return
        val serverToken = prefs.serverToken.first() ?: return
        val url = "$serverUri$partKey?X-Plex-Token=$serverToken&download=1"
        val localPath = downloadHelper.localPath(ratingKey)
        val downloadId = downloadHelper.enqueue(ratingKey, title, url)
        downloadDao.upsert(
            DownloadEntity(
                ratingKey = ratingKey,
                title = title,
                partKey = partKey,
                localPath = localPath,
                status = DownloadStatus.DOWNLOADING,
                downloadId = downloadId
            )
        )
    }

    suspend fun checkAndUpdateDownloadStatus(ratingKey: String) {
        val entity = downloadDao.get(ratingKey) ?: return
        if (entity.status == DownloadStatus.DONE) return
        val status = downloadHelper.queryStatus(entity.downloadId)
        val newStatus = when (status) {
            android.app.DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.DONE
            android.app.DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
            else -> return
        }
        downloadDao.updateStatus(ratingKey, newStatus)
    }

    suspend fun deleteDownload(ratingKey: String) {
        val entity = downloadDao.get(ratingKey) ?: return
        downloadHelper.delete(entity.downloadId)
        java.io.File(entity.localPath).delete()
        downloadDao.delete(ratingKey)
    }

    fun observeDownload(ratingKey: String): Flow<DownloadEntity?> =
        downloadDao.observe(ratingKey)

    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAll()

    fun localFileUri(ratingKey: String): String? {
        val path = downloadHelper.localPath(ratingKey)
        return if (java.io.File(path).exists()) "file://$path" else null
    }
}
