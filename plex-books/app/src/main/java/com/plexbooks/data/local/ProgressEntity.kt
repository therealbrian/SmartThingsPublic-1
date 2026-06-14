package com.plexbooks.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val thumb: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val percentComplete: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}
