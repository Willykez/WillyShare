package com.willyshare.willykez.net

import android.content.Context
import android.os.Environment
import java.io.File

/** A single row in the folder browser: either a folder to descend into, or a file to select. */
data class LocalFileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
)

/** A storage volume root shown at the top level of the browser (e.g. "Internal storage", "SD card"). */
data class StorageRoot(
    val label: String,
    val path: String,
)

/**
 * Raw java.io.File based browsing of everything MANAGE_EXTERNAL_STORAGE grants access to -
 * internal storage and any mounted SD card / USB volume, unlike MediaStore which only sees
 * indexed media + a narrow set of document mime types.
 */
object LocalFileSystem {

    /** All storage volumes worth showing as browse entry points. */
    fun storageRoots(context: Context): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary.exists()) roots.add(StorageRoot("Internal storage", primary.absolutePath))

        // getExternalFilesDirs returns one path per volume, each ending in
        // .../Android/data/<package>/files - walk up to the volume root.
        val appFilesDirs = context.getExternalFilesDirs(null)
        for (dir in appFilesDirs) {
            if (dir == null) continue
            val volumeRoot = volumeRootFrom(dir) ?: continue
            if (volumeRoot.absolutePath == primary.absolutePath) continue
            if (roots.any { it.path == volumeRoot.absolutePath }) continue
            if (volumeRoot.exists() && volumeRoot.canRead()) {
                roots.add(StorageRoot("SD card", volumeRoot.absolutePath))
            }
        }
        return roots
    }

    /** Walks up from an app-specific external files dir to the actual volume root. */
    private fun volumeRootFrom(appFilesDir: File): File? {
        var current: File? = appFilesDir
        while (current != null) {
            if (current.name == "Android") return current.parentFile
            current = current.parentFile
        }
        return null
    }

    /** Lists the contents of [path], folders first, then files, both alphabetically. */
    fun listChildren(path: String): List<LocalFileNode> {
        val dir = File(path)
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !it.isHidden }
            .map {
                LocalFileNode(
                    path = it.absolutePath,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isDirectory) 0L else it.length(),
                    lastModified = it.lastModified(),
                )
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** Recursively collects every file under [folderPath] - used when a whole folder is selected for sending. */
    fun collectFilesRecursively(folderPath: String): List<File> {
        val root = File(folderPath)
        if (!root.isDirectory) return if (root.isFile) listOf(root) else emptyList()
        return root.walkTopDown()
            .onEnter { !it.isHidden }
            .filter { it.isFile && !it.isHidden }
            .toList()
    }

    /** Total size + file count under [folderPath], for showing "Calculating..." then a real total. */
    fun folderSummary(folderPath: String): Pair<Int, Long> {
        val files = collectFilesRecursively(folderPath)
        return files.size to files.sumOf { it.length() }
    }
}
