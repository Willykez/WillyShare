package willyshare.spark.net

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

const val TRANSFER_PORT = 8988
const val PARALLEL_STREAMS = 4
private const val CHUNK_SIZE = 4 * 1024 * 1024
private const val SOCKET_BUF = 2 shl 20 // 2 MB - a 5GHz link can push a lot more than the 1 MB this used to be
private const val PROGRESS_THROTTLE_MS = 120L

data class SendableFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    /** "MyFolder/sub/file.txt" when this came from a selected folder; null for a flat pick. */
    val relativePath: String? = null,
)

data class FileProgressItem(
    val key: String,
    val name: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Double = 0.0,
    val isComplete: Boolean = false,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false
)

data class TransferProgress(
    val files: List<FileProgressItem> = emptyList(),
    val overallBytes: Long = 0L,
    val overallTotal: Long = 0L,
    val overallSpeed: Double = 0.0,
    val isConnecting: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (!name.contains("wlan") && !name.contains("p2p") && !name.contains("ap")) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) return addr.hostAddress
                }
            }
            for (intf in interfaces) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) return addr.hostAddress
                }
            }
            null
        } catch (_: Exception) { null }
    }
}

private class ProgressAggregator {
    val items = ConcurrentHashMap<String, FileProgressItem>()
    val flow = MutableStateFlow(TransferProgress())

    @Volatile
    private var _expectedTotal = -1
    val expectedTotal: Int get() = _expectedTotal

    @Volatile
    private var lastEmit = 0L

    fun reset() {
        synchronized(this) {
            items.clear()
            _expectedTotal = -1
            flow.value = TransferProgress()
        }
    }

    /**
     * Like [reset] + set-total combined, but only when it's actually needed: if the last
     * transfer already finished (or this is the very first one), start completely fresh.
     * If it's still the same transfer in progress (sibling parallel-stream connections
     * calling this with the same total), this is a no-op - their in-flight items must
     * survive. Synchronized because multiple parallel connections for the same new
     * transfer can all race in here at once; without serializing, a slightly-later
     * connection could wipe out progress a slightly-earlier one already started reporting.
     */
    fun beginTransferIfNeeded(totalCount: Int) {
        synchronized(this) {
            if (_expectedTotal < 0 || flow.value.isComplete) {
                items.clear()
                _expectedTotal = totalCount
                flow.value = TransferProgress()
            }
        }
    }

    fun update(item: FileProgressItem, force: Boolean = false) {
        items[item.key] = item
        val now = System.currentTimeMillis()
        if (!force && now - lastEmit < PROGRESS_THROTTLE_MS) return
        lastEmit = now
        emit()
    }

    fun emit() {
        val snapshot = items.values.sortedBy { it.key }
        val expected = _expectedTotal
        flow.value = flow.value.copy(
            files = snapshot,
            overallBytes = snapshot.sumOf { it.transferredBytes },
            overallTotal = snapshot.sumOf { it.totalBytes },
            overallSpeed = snapshot.sumOf { it.speedBytesPerSec },
            isComplete = expected > 0 && snapshot.size >= expected && snapshot.all { it.isComplete }
        )
    }

    fun setError(msg: String) { flow.value = flow.value.copy(error = msg) }
    fun setConnecting(v: Boolean) { flow.value = flow.value.copy(isConnecting = v) }
}

/** Where received files get written: the public Downloads folder (default), or a user-chosen SAF tree. */
sealed interface ReceiveTarget {
    /** Default destination - public Downloads/[subfolder], visible outside the app. */
    data class PublicDownloads(val context: Context, val subfolder: String = "PulseReceived") : ReceiveTarget
    data class Tree(val context: Context, val treeUri: Uri) : ReceiveTarget
}

/**
 * Where a received file's name puts it on disk: Downloads/PulseReceived/<category>/<name>
 * instead of everything landing in one flat folder. Deliberately coarse (extension-based,
 * same categories already used for Transfer History) rather than sniffing MIME types - fast,
 * good enough, and consistent with how the rest of the app already buckets files.
 */
object FileCategorizer {
    fun subfolderFor(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) ||
            name.endsWith(".gif", true) || name.endsWith(".webp", true) || name.endsWith(".heic", true) -> "Images"
        name.endsWith(".mp4", true) || name.endsWith(".mov", true) || name.endsWith(".mkv", true) ||
            name.endsWith(".webm", true) || name.endsWith(".3gp", true) -> "Videos"
        name.endsWith(".mp3", true) || name.endsWith(".m4a", true) || name.endsWith(".wav", true) ||
            name.endsWith(".ogg", true) || name.endsWith(".flac", true) -> "Audio"
        name.endsWith(".apk", true) -> "Apps"
        name.endsWith(".zip", true) || name.endsWith(".rar", true) || name.endsWith(".7z", true) ||
            name.endsWith(".tar", true) || name.endsWith(".gz", true) -> "Archives"
        else -> "Documents"
    }

    /** Prepends the category folder as the first path segment, keeping any existing subfolder structure after it. */
    fun categorize(relPath: String): String {
        val name = relPath.substringAfterLast('/')
        return "${subfolderFor(name)}/$relPath"
    }
}

class FileReceiveServer(
    private val targetProvider: () -> ReceiveTarget,
    private val tokenProvider: () -> String? = { null }
) {
    private val aggregator = ProgressAggregator()
    val progress: StateFlow<TransferProgress> = aggregator.flow.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    private val _senderConnected = MutableStateFlow(false)
    val senderConnected: StateFlow<Boolean> = _senderConnected.asStateFlow()

    /** IP of whoever last connected to us - real transfer or a zero-file announce ping alike. */
    private val _peerConnectedIp = MutableStateFlow<String?>(null)
    val peerConnectedIp: StateFlow<String?> = _peerConnectedIp.asStateFlow()

    @Volatile private var serverChannel: ServerSocketChannel? = null
    @Volatile private var running = false
    private val pool = Executors.newCachedThreadPool()
    private val activeStreams = AtomicLong(0)

    fun start(onFileReceived: (String, Long) -> Unit) {
        if (running) return
        running = true
        aggregator.reset()
        Thread {
            try {
                val server = ServerSocketChannel.open()
                server.socket().reuseAddress = true
                server.socket().bind(InetSocketAddress(TRANSFER_PORT))
                serverChannel = server
                _isListening.value = true
                while (running) {
                    val client = try { server.accept() } catch (e: Exception) {
                        if (running) aggregator.setError(e.message ?: "Listener error")
                        break
                    }
                    val s = client.socket()
                    s.tcpNoDelay = true
                    s.receiveBufferSize = SOCKET_BUF
                    activeStreams.incrementAndGet()
                    _senderConnected.value = true
                    pool.execute {
                        try {
                            handleClient(client, s.inetAddress?.hostAddress, onFileReceived)
                        } catch (t: Throwable) {
                            aggregator.setError(t.message ?: "Receive failed")
                        } finally {
                            if (activeStreams.decrementAndGet() == 0L) _senderConnected.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                aggregator.setError(e.message ?: "Failed to start listener")
            } finally {
                _isListening.value = false
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handleClient(channel: SocketChannel, remoteIp: String?, onFileReceived: (String, Long) -> Unit) {
        channel.use { ch ->
            val din = DataInputStream(ch.socket().getInputStream())

            // Token preamble: every connection starts with a length-prefixed string (empty
            // if the sender has no token to present). Only enforced when WE currently have
            // an active token of our own (i.e., we generated a QR this session) - proves
            // whoever's connecting actually scanned it, instead of just guessing our IP off
            // the same Wi-Fi network or a stale/replayed connection attempt.
            val presentedTokenLen = din.readInt().coerceIn(0, 128)
            val presentedTokenBytes = ByteArray(presentedTokenLen)
            din.readFully(presentedTokenBytes)
            val presentedToken = String(presentedTokenBytes, StandardCharsets.UTF_8)
            val expectedToken = tokenProvider()
            if (!expectedToken.isNullOrBlank() && presentedToken != expectedToken) {
                // Quietly drop it - don't surface this on aggregator/receiveProgress.error,
                // since that's shared, visible UI state and a rejected probe from some
                // unrelated device on the same network shouldn't scare the user mid a
                // perfectly fine, separate legitimate session.
                return@use
            }
            // Only now - proven legitimate (or no token was required at all) - does this
            // count as "someone found me." This used to fire the instant any TCP connection
            // landed, before this check even ran, which is exactly how an unrelated/bogus
            // connection could make the sender think a receiver had connected when nothing
            // legitimate had happened yet.
            _peerConnectedIp.value = remoteIp

            val totalCount = din.readInt()
            aggregator.beginTransferIfNeeded(totalCount)   // ← FIXED HERE
            val fileCount = din.readInt()
            repeat(fileCount) {
                val nameLen = din.readInt().coerceIn(0, 4096)
                val nameBytes = ByteArray(nameLen)
                din.readFully(nameBytes)
                val relPath = sanitizeRelativePath(String(nameBytes, StandardCharsets.UTF_8))
                val size = din.readLong()
                val key = "${ch.hashCode()}_$relPath"
                val startTime = System.currentTimeMillis()

                val (outputStream, savedPath) = openSink(FileCategorizer.categorize(relPath))
                outputStream.use { out ->
                    // Generic byte-channel copy - works for a real FileOutputStream (fast path,
                    // File-backed) as much as for a ContentResolver-backed stream (SAF Tree,
                    // or the default MediaStore Downloads sink), neither of which is actually
                    // a java.io.FileOutputStream under the hood.
                    val sink = java.nio.channels.Channels.newChannel(out)
                    val buffer = java.nio.ByteBuffer.allocateDirect(CHUNK_SIZE)
                    var pos = 0L
                    while (pos < size) {
                        buffer.clear()
                        buffer.limit(minOf(CHUNK_SIZE.toLong(), size - pos).toInt())
                        val n = ch.read(buffer)
                        if (n <= 0) break
                        buffer.flip()
                        while (buffer.hasRemaining()) sink.write(buffer)
                        pos += n
                        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                        aggregator.update(FileProgressItem(key, relPath, size, pos, pos / elapsed))
                    }
                }
                aggregator.update(FileProgressItem(key, relPath, size, size, 0.0, isComplete = true), force = true)
                onFileReceived(savedPath, size)
            }
        }
    }

    /** Opens an output stream for [relPath] under whichever [target] is configured, returning where it landed. */
    private fun openSink(relPath: String): Pair<java.io.OutputStream, String> {
        return when (val t = targetProvider()) {
            is ReceiveTarget.PublicDownloads -> PublicDownloadsWriter.openSink(t.context, t.subfolder, relPath)
            is ReceiveTarget.Tree -> {
                val leaf = SafFileWriter.createUniqueFile(t.context, t.treeUri, relPath)
                val stream = t.context.contentResolver.openOutputStream(leaf.uri, "w")
                    ?: throw java.io.IOException("Could not open output stream for $relPath")
                stream to leaf.uri.toString()
            }
        }
    }

    /** Sanitizes each path segment of a possibly-nested relative path (e.g. "Trip/IMG_1.jpg"). */
    private fun sanitizeRelativePath(raw: String): String {
        val segments = raw.split('/', '\\')
            .map { it.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim() }
            .filter { it.isNotBlank() && it != "." && it != ".." }
        return if (segments.isEmpty()) "received_file" else segments.joinToString("/")
    }

    fun stop() {
        running = false
        try { serverChannel?.close() } catch (_: Exception) {}
        serverChannel = null
        _isListening.value = false
        _senderConnected.value = false
    }
}

class FileSenderClient(private val context: Context) {
    private val aggregator = ProgressAggregator()
    val progress: StateFlow<TransferProgress> = aggregator.flow.asStateFlow()

    // Per-file controls, keyed the same as FileProgressItem.key. Cleared at the start of
    // every send() so a paused/cancelled file from a previous session can never bleed into
    // the next one.
    private val pausedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cancelledKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun toggleFilePause(key: String) {
        if (!pausedKeys.add(key)) pausedKeys.remove(key)
    }

    fun cancelFile(key: String) {
        cancelledKeys.add(key)
        pausedKeys.remove(key)
    }

    /**
     * Zero-file "hello" - a valid degenerate case of the same wire protocol (totalCount=0,
     * fileCount=0, no file entries). Lets a device with no cart of its own announce its IP to
     * whoever it just paired with, without going through the real transfer path. Fire-and-forget:
     * best effort, no result to report back.
     */
    fun announce(hostIp: String, token: String? = null) {
        Thread {
            try {
                SocketChannel.open(InetSocketAddress(hostIp, TRANSFER_PORT)).use { channel ->
                    val dout = DataOutputStream(channel.socket().getOutputStream())
                    writeToken(dout, token)
                    dout.writeInt(0)
                    dout.writeInt(0)
                    dout.flush()
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    private fun writeToken(dout: DataOutputStream, token: String?) {
        val bytes = (token ?: "").toByteArray(StandardCharsets.UTF_8)
        dout.writeInt(bytes.size)
        dout.write(bytes)
    }

    suspend fun send(hostIp: String, files: List<SendableFile>, token: String? = null): Boolean = coroutineScope {
        if (files.isEmpty()) return@coroutineScope false
        pausedKeys.clear()
        cancelledKeys.clear()
        aggregator.reset()
        aggregator.beginTransferIfNeeded(files.size)   // ← FIXED HERE
        aggregator.setConnecting(true)
        aggregator.emit()

        try {
            val groupCount = minOf(PARALLEL_STREAMS, files.size)
            val groups = Array(groupCount) { mutableListOf<SendableFile>() }
            files.forEachIndexed { i, f -> groups[i % groupCount].add(f) }

            // Every group already catches its own errors and resolves to a Boolean, but
            // awaiting on Dispatchers.IO can still surface a CancellationException (e.g. the
            // screen was left mid-transfer) or, in the worst case, an OutOfMemoryError from a
            // huge file. Neither should ever crash the whole app - just report the failure.
            val results = groups.map { group ->
                async(Dispatchers.IO) { sendGroup(hostIp, files.size, group, token) }
            }.map { runCatching { it.await() }.getOrDefault(false) }

            aggregator.setConnecting(false)
            val success = results.all { it }
            aggregator.emit()
            success
        } catch (t: Throwable) {
            aggregator.setConnecting(false)
            aggregator.setError(t.message ?: "Transfer failed")
            aggregator.emit()
            false
        }
    }

    private fun sendGroup(hostIp: String, totalCount: Int, files: List<SendableFile>, token: String? = null): Boolean {
        return try {
            SocketChannel.open(InetSocketAddress(hostIp, TRANSFER_PORT)).use { channel ->
                val socket = channel.socket()
                socket.tcpNoDelay = true
                socket.sendBufferSize = SOCKET_BUF
                val dout = DataOutputStream(java.io.BufferedOutputStream(socket.getOutputStream(), 64 * 1024))
                writeToken(dout, token)
                dout.writeInt(totalCount)
                dout.writeInt(files.size)
                dout.flush()

                for (file in files) {
                    val wireName = file.relativePath ?: file.name
                    val key = "${channel.hashCode()}_${wireName}"

                    // Cancelled before we ever sent its header - safe to just skip it, the
                    // receiver never learns this file existed.
                    if (key in cancelledKeys) {
                        aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, 0L, 0.0, isCancelled = true), force = true)
                        continue
                    }

                    val nameBytes = wireName.toByteArray(StandardCharsets.UTF_8)
                    dout.writeInt(nameBytes.size)
                    dout.write(nameBytes)
                    dout.writeLong(file.sizeBytes)
                    dout.flush()

                    val startTime = System.currentTimeMillis()
                    val pfd = try { context.contentResolver.openFileDescriptor(file.uri, "r") } catch (_: Exception) { null }

                    var cancelled = false
                    if (pfd != null) {
                        pfd.use { d ->
                            FileInputStream(d.fileDescriptor).channel.use { fc ->
                                var pos = 0L
                                while (pos < file.sizeBytes) {
                                    if (key in cancelledKeys) { cancelled = true; break }
                                    if (key in pausedKeys) {
                                        aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, pos, 0.0, isPaused = true), force = true)
                                        while (key in pausedKeys && key !in cancelledKeys) Thread.sleep(150)
                                        if (key in cancelledKeys) { cancelled = true; break }
                                    }
                                    val toSend = minOf(CHUNK_SIZE.toLong(), file.sizeBytes - pos)
                                    val n = fc.transferTo(pos, toSend, channel)
                                    if (n <= 0) break
                                    pos += n
                                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                                    aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, pos, pos / elapsed))
                                }
                            }
                        }
                    } else {
                        context.contentResolver.openInputStream(file.uri)?.buffered(CHUNK_SIZE)?.use { input ->
                            val buffer = ByteArray(CHUNK_SIZE)
                            var sent = 0L
                            while (true) {
                                if (key in cancelledKeys) { cancelled = true; break }
                                if (key in pausedKeys) {
                                    aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, sent, 0.0, isPaused = true), force = true)
                                    while (key in pausedKeys && key !in cancelledKeys) Thread.sleep(150)
                                    if (key in cancelledKeys) { cancelled = true; break }
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                dout.write(buffer, 0, read)
                                sent += read
                                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                                aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, sent, sent / elapsed))
                            }
                        } ?: throw java.io.IOException("Could not open ${file.name}")
                        dout.flush()
                    }
                    if (cancelled) {
                        // The header for this file was already sent, so the receiver is
                        // mid-read expecting exact byte count - we can't just move on to the
                        // next file without desyncing its parser. Close this connection; any
                        // other files still queued in THIS group are lost, but the other
                        // parallel groups (and files already completed) are unaffected.
                        aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, 0L, 0.0, isCancelled = true), force = true)
                        return@use false
                    }
                    aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, file.sizeBytes, 0.0, isComplete = true), force = true)
                }
                true
            }
        } catch (t: Throwable) {
            aggregator.setError(t.message ?: "Transfer failed")
            false
        }
    }
}