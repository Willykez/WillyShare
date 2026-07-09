package com.willyshare.willykez.net

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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

/**
 * Every connection now opens with a single mode byte declaring the connecting party's intent,
 * BEFORE the existing totalCount/fileCount/file-data stream. This is what makes the QR
 * sender/receiver role swap possible without breaking the underlying transfer direction:
 *  - MODE_PUSH: "I am sending files to you" (the original, and still the only behavior for
 *    the Wi-Fi Direct peer-list connect flow) - unchanged wire format follows this byte.
 *  - MODE_PULL: "I am requesting your queued files" (used when a receiver scans a sender's
 *    QR code) - the accepting side must respond by switching into the sender role for this
 *    one connection and writing its pending cart down the same channel, instead of reading.
 */
private const val MODE_PUSH: Byte = 1
private const val MODE_PULL: Byte = 2

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
    val isComplete: Boolean = false
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
        items.clear()
        _expectedTotal = -1
        flow.value = TransferProgress()
    }

    fun initExpectedTotal(n: Int) {
        if (_expectedTotal < 0) _expectedTotal = n
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

/** Where received files get written: the app's default folder, or a user-chosen SAF tree. */
sealed interface ReceiveTarget {
    data class Plain(val dir: File) : ReceiveTarget
    data class Tree(val context: Context, val treeUri: Uri) : ReceiveTarget
}

class FileReceiveServer(
    private val targetProvider: () -> ReceiveTarget,
    /** Called (synchronously, on a background pool thread) when an accepted connection
     *  declares MODE_PULL - i.e. someone scanned this device's QR and wants to pull our
     *  queued files rather than push us theirs. The callback owns the channel from this
     *  point: it must write the cart (or nothing, if empty) and the channel will be closed
     *  automatically once it returns. */
    private val onPullRequested: (SocketChannel) -> Unit = {}
) {
    private val aggregator = ProgressAggregator()
    val progress: StateFlow<TransferProgress> = aggregator.flow.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    private val _senderConnected = MutableStateFlow(false)
    val senderConnected: StateFlow<Boolean> = _senderConnected.asStateFlow()

    @Volatile private var serverChannel: ServerSocketChannel? = null
    @Volatile private var running = false
    private val pool = Executors.newCachedThreadPool()
    private val activeStreams = AtomicLong(0)

    fun start(onFileReceived: (String, Long) -> Unit) {
        if (running) return
        running = true
        aggregator.reset()
        (targetProvider() as? ReceiveTarget.Plain)?.dir?.mkdirs()
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
                            client.use { ch ->
                                val din = DataInputStream(ch.socket().getInputStream())
                                when (din.readByte()) {
                                    MODE_PULL -> onPullRequested(ch)
                                    else -> receiveFilesInto(ch, din, onFileReceived) // MODE_PUSH (default)
                                }
                            }
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

    /**
     * CLIENT-initiated pull: connects out to [hostIp]:[port], declares MODE_PULL, then reads
     * whatever the far end pushes back down the same channel using the normal receive format.
     * This is what a device does after scanning another device's "I have files" QR code -
     * the roles on the wire (who reads vs writes) are unchanged from a normal push; only who
     * dialed the connection is reversed, via the leading mode byte.
     */
    suspend fun pullFrom(hostIp: String, port: Int, onFileReceived: (String, Long) -> Unit): Boolean =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            aggregator.reset()
            _senderConnected.value = true
            try {
                SocketChannel.open(InetSocketAddress(hostIp, port)).use { channel ->
                    val socket = channel.socket()
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = SOCKET_BUF
                    val dout = DataOutputStream(socket.getOutputStream())
                    dout.writeByte(MODE_PULL.toInt())
                    dout.flush()
                    val din = DataInputStream(socket.getInputStream())
                    receiveFilesInto(channel, din, onFileReceived)
                }
                aggregator.emit()
                true
            } catch (t: Throwable) {
                aggregator.setError(t.message ?: "Pull failed")
                false
            } finally {
                _senderConnected.value = false
            }
        }

    /** Shared by both the server-accept (MODE_PUSH) path and the client-initiated [pullFrom]
     *  path - reads the standard totalCount/fileCount/file-data stream and writes it to
     *  whatever [targetProvider] currently points at. Caller owns the channel's lifecycle. */
    private fun receiveFilesInto(channel: SocketChannel, din: DataInputStream, onFileReceived: (String, Long) -> Unit) {
        val totalCount = din.readInt()
        aggregator.initExpectedTotal(totalCount)
        val fileCount = din.readInt()
        repeat(fileCount) {
            val nameLen = din.readInt().coerceIn(0, 4096)
            val nameBytes = ByteArray(nameLen)
            din.readFully(nameBytes)
            val relPath = sanitizeRelativePath(String(nameBytes, StandardCharsets.UTF_8))
            val size = din.readLong()
            val key = "${channel.hashCode()}_$relPath"
            val startTime = System.currentTimeMillis()

            val (outputStream, savedPath) = openSink(relPath)
            (outputStream as FileOutputStream).channel.use { fc ->
                var pos = 0L
                while (pos < size) {
                    val toRead = minOf(CHUNK_SIZE.toLong(), size - pos)
                    val n = fc.transferFrom(channel, pos, toRead)
                    if (n <= 0) break
                    pos += n
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                    aggregator.update(FileProgressItem(key, relPath, size, pos, pos / elapsed))
                }
            }
            aggregator.update(FileProgressItem(key, relPath, size, size, 0.0, isComplete = true), force = true)
            onFileReceived(savedPath, size)
        }
    }

    /** Opens an output stream for [relPath] under whichever [target] is configured, returning where it landed. */
    private fun openSink(relPath: String): Pair<java.io.OutputStream, String> {
        return when (val t = targetProvider()) {
            is ReceiveTarget.Plain -> {
                val destFile = uniquePlainFile(t.dir, relPath)
                FileOutputStream(destFile) to destFile.absolutePath
            }
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

    private fun uniquePlainFile(dir: File, relPath: String): File {
        val destination = File(dir, relPath)
        destination.parentFile?.mkdirs()
        if (!destination.exists()) return destination
        val name = destination.name
        val parent = destination.parentFile ?: dir
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate = File(parent, "$base ($i)$ext")
        while (candidate.exists()) { i++; candidate = File(parent, "$base ($i)$ext") }
        return candidate
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

    suspend fun send(hostIp: String, files: List<SendableFile>): Boolean = coroutineScope {
        if (files.isEmpty()) return@coroutineScope false
        aggregator.reset()
        aggregator.initExpectedTotal(files.size)   // ← FIXED HERE
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
                async(Dispatchers.IO) { sendGroup(hostIp, files.size, group) }
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

    /** Used when this device scans another device's QR and that QR belongs to a sender who
     *  is waiting to be pulled from - see [FileReceiveServer.pullFrom] and [FileReceiveServer]'s
     *  onPullRequested callback for the other end of this handshake. Not used by [send]/[sendGroup]. */
    fun pushOverAcceptedChannel(channel: SocketChannel, files: List<SendableFile>): Boolean {
        if (files.isEmpty()) return false
        aggregator.reset()
        aggregator.initExpectedTotal(files.size)
        aggregator.emit()
        return try {
            writeFiles(channel, files.size, files)
        } catch (t: Throwable) {
            aggregator.setError(t.message ?: "Transfer failed")
            false
        }
    }

    private fun sendGroup(hostIp: String, totalCount: Int, files: List<SendableFile>): Boolean {
        return try {
            SocketChannel.open(InetSocketAddress(hostIp, TRANSFER_PORT)).use { channel ->
                val socket = channel.socket()
                socket.tcpNoDelay = true
                socket.sendBufferSize = SOCKET_BUF
                // Mode byte first - see the MODE_PUSH/MODE_PULL doc comment near the top of this
                // file. This is the normal "I'm sending you files" declaration; every existing
                // send path (peer-list connect, or scanning someone's old-style receive QR)
                // still goes through here unchanged.
                DataOutputStream(socket.getOutputStream()).also {
                    it.writeByte(MODE_PUSH.toInt())
                    it.flush()
                }
                writeFiles(channel, totalCount, files)
            }
        } catch (t: Throwable) {
            aggregator.setError(t.message ?: "Transfer failed")
            false
        }
    }

    /** Shared by [sendGroup] (which opens its own outbound connection) and
     *  [pushOverAcceptedChannel] (which reuses a connection someone else already opened to
     *  request a pull) - writes the standard totalCount/fileCount/file-data stream. */
    private fun writeFiles(channel: SocketChannel, totalCount: Int, files: List<SendableFile>): Boolean {
        return try {
            val socket = channel.socket()
            val dout = DataOutputStream(java.io.BufferedOutputStream(socket.getOutputStream(), 64 * 1024))
            dout.writeInt(totalCount)
            dout.writeInt(files.size)
            dout.flush()

            for (file in files) {
                val wireName = file.relativePath ?: file.name
                val nameBytes = wireName.toByteArray(StandardCharsets.UTF_8)
                dout.writeInt(nameBytes.size)
                dout.write(nameBytes)
                dout.writeLong(file.sizeBytes)
                dout.flush()

                val key = "${channel.hashCode()}_${wireName}"
                val startTime = System.currentTimeMillis()
                val pfd = try { context.contentResolver.openFileDescriptor(file.uri, "r") } catch (_: Exception) { null }

                if (pfd != null) {
                    pfd.use { d ->
                        FileInputStream(d.fileDescriptor).channel.use { fc ->
                            var pos = 0L
                            while (pos < file.sizeBytes) {
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
                aggregator.update(FileProgressItem(key, file.name, file.sizeBytes, file.sizeBytes, 0.0, isComplete = true), force = true)
            }
            true
        } catch (t: Throwable) {
            aggregator.setError(t.message ?: "Transfer failed")
            false
        }
    }
}