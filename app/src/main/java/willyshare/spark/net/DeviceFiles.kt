package willyshare.spark.net

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import willyshare.spark.data.FileItemEntity
import java.io.File

/**
 * Queries the real device (MediaStore + PackageManager) for shareable files.
 * Nothing here is seeded/simulated - if the device has no photos, none are returned.
 */
object DeviceFiles {

    fun queryPhotos(context: Context): List<FileItemEntity> =
        queryMedia(
            context,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "Photos",
            "\uD83D\uDDBC\uFE0F"
        )

    fun queryVideos(context: Context): List<FileItemEntity> =
        queryMedia(
            context,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "Videos",
            "\uD83C\uDFAC"
        )

    fun queryAudio(context: Context): List<FileItemEntity> =
        queryMedia(
            context,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "Audio",
            "\uD83C\uDFB5"
        )

    /** Non-media files (pdf, docx, zip, etc.) indexed by MediaStore's generic Files collection. */
    fun queryDocuments(context: Context): List<FileItemEntity> {
        val results = mutableListOf<FileItemEntity>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val docMimePrefixes = listOf(
            "application/pdf", "application/msword", "application/vnd.",
            "text/", "application/zip", "application/x-", "application/json"
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
        val selectionArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT.toString())
        try {
            context.contentResolver.query(
                collection, projection, selection, selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeCol) ?: ""
                    if (docMimePrefixes.none { mime.startsWith(it) } && mime.isNotBlank()) continue
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0) continue
                    val dateModified = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else 0L
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    results.add(
                        FileItemEntity(
                            id = "doc_$id", name = name, category = "Documents",
                            sizeBytes = size, iconEmoji = iconForDoc(name), uri = uri.toString(),
                            dateModifiedMs = dateModified
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return results
    }

    /** User-installed (non-system) apps, resolved to their real APK path on disk. */
    fun queryApps(context: Context): List<FileItemEntity> {
        val pm = context.packageManager
        val results = mutableListOf<FileItemEntity>()
        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystemApp) continue
                val apkPath = app.publicSourceDir ?: continue
                val apkFile = File(apkPath)
                if (!apkFile.exists() || !apkFile.canRead()) continue
                val label = pm.getApplicationLabel(app).toString()
                results.add(
                    FileItemEntity(
                        id = "app_${app.packageName}",
                        name = "$label.apk",
                        category = "Apps",
                        sizeBytes = apkFile.length(),
                        iconEmoji = "\uD83D\uDCE6",
                        uri = Uri.fromFile(apkFile).toString(),
                        dateModifiedMs = apkFile.lastModified()
                    )
                )
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun queryAll(context: Context): List<FileItemEntity> =
        queryPhotos(context) + queryVideos(context) + queryAudio(context) +
            queryDocuments(context) + queryApps(context)

    private fun queryMedia(
        context: Context,
        collection: Uri,
        category: String,
        emoji: String
    ): List<FileItemEntity> {
        val results = mutableListOf<FileItemEntity>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        try {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0) continue
                    val dateModified = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else 0L
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    results.add(
                        FileItemEntity(
                            id = "${category}_$id", name = name, category = category,
                            sizeBytes = size, iconEmoji = emoji, uri = uri.toString(),
                            dateModifiedMs = dateModified
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return results
    }

    private fun iconForDoc(name: String): String = when {
        name.endsWith(".pdf", true) -> "\uD83D\uDCC4"
        name.endsWith(".zip", true) || name.endsWith(".rar", true) -> "\uD83D\uDDDC\uFE0F"
        name.endsWith(".doc", true) || name.endsWith(".docx", true) -> "\uD83D\uDCDD"
        else -> "\uD83D\uDCC4"
    }
}
