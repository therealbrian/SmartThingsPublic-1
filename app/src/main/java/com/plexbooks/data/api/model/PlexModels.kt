package com.plexbooks.data.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Auth ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class PlexPin(
    val id: Long,
    val code: String,
    @Json(name = "authToken") val authToken: String?,
    @Json(name = "expiresAt") val expiresAt: String
)

// ── Resources (servers) ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class PlexResource(
    val name: String,
    @Json(name = "clientIdentifier") val clientIdentifier: String,
    val provides: String,
    @Json(name = "accessToken") val accessToken: String?,
    val connections: List<PlexConnection>
)

@JsonClass(generateAdapter = true)
data class PlexConnection(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
    val relay: Boolean
)

// ── Libraries ─────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LibrarySectionsContainer(
    @Json(name = "MediaContainer") val mediaContainer: LibrarySectionsMediaContainer
)

@JsonClass(generateAdapter = true)
data class LibrarySectionsMediaContainer(
    @Json(name = "Directory") val directories: List<PlexLibrarySection>?
)

@JsonClass(generateAdapter = true)
data class PlexLibrarySection(
    val key: String,
    val type: String,
    val title: String,
    val thumb: String?,
    val art: String?,
    @Json(name = "updatedAt") val updatedAt: Long?
)

// ── Media items ───────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MediaContainer(
    @Json(name = "MediaContainer") val mediaContainer: MetadataContainer
)

@JsonClass(generateAdapter = true)
data class MetadataContainer(
    val size: Int,
    @Json(name = "Metadata") val metadata: List<PlexMediaItem>?
)

@JsonClass(generateAdapter = true)
data class PlexMediaItem(
    val ratingKey: String,
    val key: String,
    val type: String,
    val title: String,
    @Json(name = "parentTitle") val parentTitle: String?,
    @Json(name = "grandparentTitle") val grandparentTitle: String?,
    @Json(name = "parentRatingKey") val parentRatingKey: String?,
    val summary: String?,
    val thumb: String?,
    val art: String?,
    val duration: Long?,
    @Json(name = "viewOffset") val viewOffset: Long?,
    val index: Int?,
    @Json(name = "parentIndex") val parentIndex: Int?,
    @Json(name = "addedAt") val addedAt: Long?,
    @Json(name = "updatedAt") val updatedAt: Long?,
    @Json(name = "Media") val media: List<PlexMedia>?,
    @Json(name = "Chapter") val chapters: List<PlexChapter>?,
    @Json(name = "grandparentRatingKey") val grandparentRatingKey: String?
)

@JsonClass(generateAdapter = true)
data class PlexMedia(
    val id: Long?,
    val duration: Long?,
    @Json(name = "Part") val parts: List<PlexPart>
)

@JsonClass(generateAdapter = true)
data class PlexPart(
    val id: Long?,
    val key: String,
    val duration: Long?,
    val size: Long?,
    val file: String?
)

// Chapter markers embedded in M4B files (returned by Plex in the track metadata)
@JsonClass(generateAdapter = true)
data class PlexChapter(
    val id: Int?,
    val tag: String,
    @Json(name = "startTimeOffset") val startTimeOffset: Long,
    @Json(name = "endTimeOffset") val endTimeOffset: Long,
    val thumb: String?
)

// ── Domain model helpers ──────────────────────────────────────────────────────

fun PlexMediaItem.streamKey(): String? = media?.firstOrNull()?.parts?.firstOrNull()?.key

fun PlexMediaItem.totalDurationMs(): Long =
    media?.firstOrNull()?.parts?.sumOf { it.duration ?: 0 } ?: duration ?: 0

fun PlexMediaItem.displayAuthor(): String? = grandparentTitle ?: parentTitle

val BOOK_LIBRARY_TYPES = setOf("music", "book", "artist")
