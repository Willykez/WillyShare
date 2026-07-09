package com.willyshare.willykez.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val category: String, // PHOTO, VIDEO, DOC, APP, AUDIO, ARCHIVE
    val sizeBytes: Long,
    val timestamp: Long,
    val deviceName: String,
    val isSend: Boolean,
    val status: String, // COMPLETED, FAILED, IN_PROGRESS
    val savedPath: String? = null // Where a received file was written on disk
)
