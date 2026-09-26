package com.bomeber.homestream.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert
    suspend fun insertDownload(entity: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("UPDATE downloads SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: DownloadState)

    @Query("UPDATE downloads SET state = :state, errorMessage = :message WHERE id = :id")
    suspend fun updateFailure(id: String, state: DownloadState, message: String?)

    @Query("UPDATE downloads SET state = 'COMPLETED', localFilePath = :path WHERE id = :id")
    suspend fun markCompleted(id: String, path: String)

    @Query("UPDATE downloads SET totalBytes = :totalBytes, totalUnits = :totalUnits WHERE id = :id")
    suspend fun setPlan(id: String, totalBytes: Long, totalUnits: Int)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateProgressBytes(id: String, bytes: Long)

    @Query("UPDATE downloads SET completedUnits = completedUnits + 1 WHERE id = :id")
    suspend fun incrementCompletedUnits(id: String)

    @Query("UPDATE downloads SET state = 'QUEUED', errorMessage = NULL, retryCount = 0 WHERE id = :id")
    suspend fun resetForRetry(id: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Insert
    suspend fun insertUnits(units: List<DownloadUnitEntity>)

    @Query("SELECT * FROM download_units WHERE downloadId = :id ORDER BY unitIndex")
    suspend fun getUnits(id: String): List<DownloadUnitEntity>

    @Query(
        "UPDATE download_units SET completed = 1, byteCount = :byteCount " +
                "WHERE downloadId = :id AND unitIndex = :unitIndex"
    )
    suspend fun updateUnitCompleted(id: String, unitIndex: Int, byteCount: Long)

    /** Marks one unit complete AND bumps the parent's completedUnits counter atomically. */
    @Transaction
    suspend fun markUnitComplete(id: String, unitIndex: Int, byteCount: Long) {
        updateUnitCompleted(id, unitIndex, byteCount)
        incrementCompletedUnits(id)
    }

    @Query("DELETE FROM download_units WHERE downloadId = :id")
    suspend fun deleteUnits(id: String)
}