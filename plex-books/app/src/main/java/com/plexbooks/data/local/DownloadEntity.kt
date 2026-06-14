package com.plexbooks.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val partKey: String,
    val localPath: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadId: Long = -1L,
    val createdAt: Long = System.currentTimeMillis()
)
