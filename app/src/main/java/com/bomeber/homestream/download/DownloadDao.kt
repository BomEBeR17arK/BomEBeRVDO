package com.bomeber.homestream.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String?)

    @Query(
        "UPDATE downloads SET status = :status, progressPercent = :progressPercent, " +
                "downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id"
    )
    suspend fun updateProgress(
        id: String,
        status: DownloadStatus,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long
    )
}