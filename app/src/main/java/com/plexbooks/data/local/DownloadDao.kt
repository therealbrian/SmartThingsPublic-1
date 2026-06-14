package com.plexbooks.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE ratingKey = :ratingKey")
    suspend fun get(ratingKey: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE ratingKey = :ratingKey")
    fun observe(ratingKey: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("UPDATE downloads SET status = :status WHERE ratingKey = :ratingKey")
    suspend fun updateStatus(ratingKey: String, status: DownloadStatus)

    @Query("UPDATE downloads SET status = :status WHERE downloadId = :downloadId")
    suspend fun updateStatusByDownloadId(downloadId: Long, status: DownloadStatus)

    @Query("DELETE FROM downloads WHERE ratingKey = :ratingKey")
    suspend fun delete(ratingKey: String)
}
