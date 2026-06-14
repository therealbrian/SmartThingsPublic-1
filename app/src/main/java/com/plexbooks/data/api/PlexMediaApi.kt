package com.plexbooks.data.api

import com.plexbooks.data.api.model.LibrarySectionsContainer
import com.plexbooks.data.api.model.MediaContainer
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PlexMediaApi {

    @GET("library/sections")
    suspend fun getLibrarySections(): LibrarySectionsContainer

    /** All items in a library section. type=9=album, type=8=artist, type=10=track */
    @GET("library/sections/{sectionId}/all")
    suspend fun getSectionItems(
        @Path("sectionId") sectionId: String,
        @Query("type") type: Int? = null,
        @Query("sort") sort: String = "titleSort",
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 100
    ): MediaContainer

    /** Children of an item (e.g. tracks inside an album/audiobook) */
    @GET("library/metadata/{ratingKey}/children")
    suspend fun getChildren(
        @Path("ratingKey") ratingKey: String,
        @Query("includeChapters") includeChapters: Int = 1
    ): MediaContainer

    /** Chapter markers embedded in an M4B track */
    @GET("library/metadata/{ratingKey}/chapters")
    suspend fun getChapters(
        @Path("ratingKey") ratingKey: String
    ): MediaContainer

    /** Recently added items across all libraries */
    @GET("library/recentlyAdded")
    suspend fun getRecentlyAdded(
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 20
    ): MediaContainer

    /** Items currently in progress */
    @GET("library/onDeck")
    suspend fun getOnDeck(
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 20
    ): MediaContainer

    @GET("library/metadata/{ratingKey}")
    suspend fun getMetadata(
        @Path("ratingKey") ratingKey: String,
        @Query("includeChapters") includeChapters: Int = 1
    ): MediaContainer

    /** Report playback progress back to Plex */
    @GET(":/progress")
    suspend fun reportProgress(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Query("time") timeMs: Long,
        @Query("state") state: String,
        @Query("duration") durationMs: Long
    )

    /** Mark item as fully played */
    @GET(":/scrobble")
    suspend fun markPlayed(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library"
    )

    /** Mark item as unplayed */
    @GET(":/unscrobble")
    suspend fun markUnplayed(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library"
    )
}
