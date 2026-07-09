package com.willyshare.willykez.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

    /** [savedPath] is either an absolute file path (default save folder) or a content:// Uri string (SAF tree). */
    fun open(context: Context, savedPath: String, displayName: String) {
        val contentUri = if (savedPath.startsWith("content://")) {
            Uri.parse(savedPath)
        } else {
            val file = File(savedPath)
            if (!file.exists()) return
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val mimeType = guessMimeType(displayName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Open \"$displayName\" with")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (_: android.content.ActivityNotFoundException) {
            // No app on the device can open this file type - silently no-op rather than crash;
            // the caller (History/Receive screen) can show a snackbar if desired.
        }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
