package willyshare.spark.net

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Where received files land by default: the public Downloads/<subfolder> directory, visible
 * in the system Files app, Downloads app, and to other apps - not the app-private
 * Android/data/<package>/... sandbox, which disappears the moment the app is uninstalled and
 * isn't visible anywhere else.
 *
 * Android 10+ (API 29+): goes through MediaStore's Downloads collection with a RELATIVE_PATH -
 * this is the scoped-storage-compliant way to write into public Downloads and needs no extra
 * permission beyond what the app already declares.
 *
 * Pre-Android 10: MediaStore.Downloads doesn't exist yet, so this falls back to a plain File
 * under the public Downloads directory (needs WRITE_EXTERNAL_STORAGE, declared maxSdk 28).
 */
object PublicDownloadsWriter {

    /** Opens an output stream for [relPath] under Downloads/[subfolder], returning where it landed. */
    fun openSink(context: Context, subfolder: String, relPath: String): Pair<OutputStream, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openViaMediaStore(context, subfolder, relPath)
        } else {
            openViaLegacyFile(subfolder, relPath)
        }
    }

    private fun openViaMediaStore(context: Context, subfolder: String, relPath: String): Pair<OutputStream, String> {
        val segments = relPath.split('/').filter { it.isNotBlank() }
        val fileName = segments.lastOrNull()?.ifBlank { "received_file" } ?: "received_file"
        val subDirs = segments.dropLast(1).joinToString("/")
        val relativePath = if (subDirs.isBlank()) {
            "${Environment.DIRECTORY_DOWNLOADS}/$subfolder/"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$subfolder/$subDirs/"
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create \"$fileName\" in Downloads/$subfolder")
        val stream = resolver.openOutputStream(itemUri) ?: error("Could not open output stream for $fileName")

        // Wrap so IS_PENDING clears the moment the caller closes the stream - without this the
        // file stays hidden/"processing" in Files/Downloads until something else clears it.
        val wrapped = object : OutputStream() {
            override fun write(b: Int) = stream.write(b)
            override fun write(b: ByteArray) = stream.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
            override fun flush() = stream.flush()
            override fun close() {
                stream.close()
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                try { resolver.update(itemUri, done, null, null) } catch (_: Exception) {}
            }
        }
        return wrapped to itemUri.toString()
    }

    private fun openViaLegacyFile(subfolder: String, relPath: String): Pair<OutputStream, String> {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = uniqueFile(File(downloadsDir, subfolder), relPath)
        return FileOutputStream(destFile) to destFile.absolutePath
    }

    private fun uniqueFile(baseDir: File, relPath: String): File {
        val destination = File(baseDir, relPath)
        destination.parentFile?.mkdirs()
        if (!destination.exists()) return destination
        val name = destination.name
        val parent = destination.parentFile ?: baseDir
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate = File(parent, "$base ($i)$ext")
        while (candidate.exists()) { i++; candidate = File(parent, "$base ($i)$ext") }
        return candidate
    }
}
