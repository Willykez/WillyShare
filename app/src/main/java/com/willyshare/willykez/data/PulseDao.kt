package com.willyshare.willykez.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PulseDao {
    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity)

    @Delete
    suspend fun deleteTransfer(transfer: TransferEntity)

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfers")
    suspend fun clearAllTransfers()

    @Query("SELECT * FROM available_files ORDER BY name ASC")
    fun getAllFiles(): Flow<List<FileItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFiles(files: List<FileItemEntity>)

    @Query("DELETE FROM available_files")
    suspend fun clearAllFiles()

    @Query("UPDATE available_files SET isSelected = :selected WHERE id = :fileId")
    suspend fun updateFileSelection(fileId: String, selected: Boolean)

    @Query("UPDATE available_files SET isSelected = 0")
    suspend fun clearAllSelections()
}
