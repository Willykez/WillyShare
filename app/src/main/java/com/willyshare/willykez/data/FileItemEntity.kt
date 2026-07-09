package com.willyshare.willykez.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a real file discovered on the device via MediaStore/PackageManager.
 * [uri] is the real content:// (or file path) location used to open an InputStream
 * for the actual transfer - there is no mock data behind this entity.
 */
@Entity(tableName = "available_files")
data class FileItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // Photos, Videos, Documents, Apps
    val sizeBytes: Long,
    val iconEmoji: String,
    val uri: String,
    val isSelected: Boolean = false,
    /** Last-modified time in epoch milliseconds, used for "Newest first" sorting. 0 if unknown. */
    val dateModifiedMs: Long = 0L
)
