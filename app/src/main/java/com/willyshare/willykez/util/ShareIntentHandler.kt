package com.willyshare.willykez.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.willyshare.willykez.net.SendableFile

/** True if this intent is another app sharing content into Sparks. */
fun Intent.isShareIntent(): Boolean =
    action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

object ShareIntentHandler {

    /** Extracts every shared content Uri from a SEND/SEND_MULTIPLE intent and resolves its name+size. */
    fun resolveSharedFiles(context: Context, intent: Intent): List<SendableFile> {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }
            }
            else -> emptyList()
        }

        return uris.mapNotNull { uri -> resolveOne(context.contentResolver, uri) }
    }

    private fun resolveOne(resolver: ContentResolver, uri: Uri): SendableFile? {
        var name = uri.lastPathSegment ?: "shared_file"
        var size = 0L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                        if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                    }
                }
        } catch (_: Exception) {
            // Some providers don't support OpenableColumns - fall back to the Uri's last segment/0 size.
        }
        if (size <= 0L) {
            size = try { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L } catch (_: Exception) { 0L }
        }
        return SendableFile(uri = uri, name = name, sizeBytes = size)
    }
}
