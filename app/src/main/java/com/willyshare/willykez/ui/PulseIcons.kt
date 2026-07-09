package com.willyshare.willykez.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One place to look up the Material icon standing in for each glyph that used
 * to be a raw emoji `Text(...)`. Keeps the "real icons, not emoji" look
 * consistent everywhere instead of picking icons ad hoc per screen.
 */
object PulseIcons {
    val Send: ImageVector = Icons.AutoMirrored.Filled.Send
    val Receive: ImageVector = Icons.Filled.Download
    val Broadcasting: ImageVector = Icons.Filled.SettingsInputAntenna
    val Discovering: ImageVector = Icons.Filled.WifiTethering
    val SignalBars: ImageVector = Icons.Filled.Wifi
    val Device: ImageVector = Icons.Filled.PhoneAndroid
    val TargetPin: ImageVector = Icons.Filled.LocationOn
    val EmptyInbox: ImageVector = Icons.Filled.Inbox
    val Camera: ImageVector = Icons.Filled.PhotoCamera
    val FolderClosed: ImageVector = Icons.Filled.Folder
    val FolderOpenEmpty: ImageVector = Icons.Filled.FolderOpen
    val AppPackage: ImageVector = Icons.Filled.Inventory2
    val GenericFile: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile
    val ArchiveFile: ImageVector = Icons.Filled.Archive
    val DocFile: ImageVector = Icons.Filled.Description
    val Warning: ImageVector = Icons.Filled.Warning
    val Photo: ImageVector = Icons.Filled.Image
    val Video: ImageVector = Icons.Filled.Movie
    val Audio: ImageVector = Icons.Filled.MusicNote
    val Brand: ImageVector = Icons.Filled.Bolt

    /** File-category icon, mirrors the switch that used to pick an emoji string. */
    fun forCategory(category: String): ImageVector =
        when (category) {
            "VIDEO" -> Video
            "PHOTO" -> Photo
            "AUDIO" -> Audio
            "ARCHIVE" -> ArchiveFile
            "APP" -> AppPackage
            else -> GenericFile
        }

    /** File-extension icon for on-device file browsing (SelectFilesScreen etc.). */
    fun forFileName(name: String): ImageVector =
        when {
            name.endsWith(".pdf", true) -> DocFile
            name.endsWith(".zip", true) || name.endsWith(".rar", true) -> ArchiveFile
            name.endsWith(".doc", true) || name.endsWith(".docx", true) -> DocFile
            else -> GenericFile
        }

    /** Category icon for the on-device file browser tabs (Photos/Videos/Audio/Documents/Apps). */
    fun forBrowseCategory(category: String): ImageVector =
        when (category) {
            "Photos" -> Photo
            "Videos" -> Video
            "Audio" -> Audio
            "Apps" -> AppPackage
            else -> GenericFile
        }
}
