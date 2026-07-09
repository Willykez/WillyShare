package com.willyshare.willykez.net

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Creates files inside a SAF tree Uri (from ACTION_OPEN_DOCUMENT_TREE), recreating the
 * relative folder path and avoiding name collisions the same way the plain-File path does.
 */
object SafFileWriter {

    /** Human-readable label for the currently configured destination, for the Settings row. */
    fun displayName(context: Context, treeUri: Uri): String {
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        return doc?.name ?: treeUri.lastPathSegment ?: treeUri.toString()
    }

    /** True if we still hold a usable permission grant for [treeUri]. */
    fun isAccessible(context: Context, treeUri: Uri): Boolean {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return doc.exists() && doc.canWrite()
    }

    /**
     * Creates a new, uniquely-named file at [relativePath] under [treeUri], creating any
     * missing subfolders along the way. Returns the created leaf [DocumentFile].
     */
    fun createUniqueFile(context: Context, treeUri: Uri, relativePath: String): DocumentFile {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Save folder is no longer accessible")
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        var dir = root
        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            val existing = dir.findFile(segment)
            dir = if (existing != null && existing.isDirectory) {
                existing
            } else {
                dir.createDirectory(segment) ?: error("Could not create folder \"$segment\"")
            }
        }

        val fileName = segments.lastOrNull()?.ifBlank { "received_file" } ?: "received_file"
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""

        var candidateName = fileName
        var counter = 1
        while (dir.findFile(candidateName) != null) {
            candidateName = "$base ($counter)$ext"
            counter++
        }

        return dir.createFile("application/octet-stream", candidateName)
            ?: error("Could not create file \"$candidateName\"")
    }
}
