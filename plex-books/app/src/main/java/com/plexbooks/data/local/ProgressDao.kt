package com.plexbooks.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE ratingKey = :ratingKey")
    suspend fun get(ratingKey: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ProgressEntity>>

    @Query("DELETE FROM progress WHERE ratingKey = :ratingKey")
    suspend fun delete(ratingKey: String)
}
