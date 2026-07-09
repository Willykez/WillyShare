package com.willyshare.willykez.ui

import android.app.Application
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willyshare.willykez.data.FileItemEntity
import com.willyshare.willykez.data.PulseDatabase
import com.willyshare.willykez.data.StoragePrefs
import com.willyshare.willykez.data.TransferEntity
import com.willyshare.willykez.net.DeviceFiles
import com.willyshare.willykez.net.FileReceiveServer
import com.willyshare.willykez.net.FileSenderClient
import com.willyshare.willykez.net.LocalFileNode
import com.willyshare.willykez.net.LocalFileSystem
import com.willyshare.willykez.net.NetworkUtils
import com.willyshare.willykez.net.QrPairing
import com.willyshare.willykez.net.ReceiveTarget
import com.willyshare.willykez.net.SafFileWriter
import com.willyshare.willykez.net.SendableFile
import com.willyshare.willykez.net.StorageRoot
import com.willyshare.willykez.net.TRANSFER_PORT
import com.willyshare.willykez.net.TransferProgress
import com.willyshare.willykez.net.WifiDirectManager
import com.willyshare.willykez.service.SparkTransferService
import com.willyshare.willykez.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.channels.SocketChannel
import java.util.UUID

/** How the current outgoing transfer's target was resolved. */
enum class TargetSource { WIFI_DIRECT, QR_PAIR, NONE }

/**
 * Single, unified "what's going on right now" signal - replaces having to separately check
 * targetSource / hostHasPeer / senderConnected / progress in every screen to answer the
 * same question. This is step one of the state-machine work; screens can adopt it
 * incrementally.
 */
enum class LinkState { IDLE, CONNECTED, TRANSFERRING }

class PulseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PulseDatabase.getDatabase(application).pulseDao()
    private val appContext get() = getApplication<Application>()
    private val storagePrefs = StoragePrefs(application)

    // ---- Real networking components ----
    val wifiDirect = WifiDirectManager(application)
    private val fileSender = FileSenderClient(application)
    private val defaultReceiveDir: File
        get() = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "PulseReceived"
        ).apply { mkdirs() }

    /** Where a custom "save received files to" folder currently points, or null for the app default. */
    val receiveTreeUri: StateFlow<String?> = storagePrefs.receiveTreeUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val fileReceiver = FileReceiveServer(
        targetProvider = {
            val treeUriString = receiveTreeUri.value
            val treeUri = treeUriString?.let { Uri.parse(it) }
            if (treeUri != null && SafFileWriter.isAccessible(appContext, treeUri)) {
                ReceiveTarget.Tree(appContext, treeUri)
            } else {
                ReceiveTarget.Plain(defaultReceiveDir)
            }
        },
        onPullRequested = ::handleIncomingPullRequest
    )

    /** Human-readable label for Settings: either the default path or the picked folder's name. */
    fun receiveDestinationLabel(uriString: String?): String {
        val treeUri = uriString?.let { Uri.parse(it) }
        return if (treeUri != null && SafFileWriter.isAccessible(appContext, treeUri)) {
            SafFileWriter.displayName(appContext, treeUri)
        } else {
            "Downloads/PulseReceived (default)"
        }
    }

    fun setReceiveDestination(treeUri: Uri?) {
        viewModelScope.launch { storagePrefs.setReceiveTreeUri(treeUri?.toString()) }
    }

    // ---- Persisted data ----
    val transfers: StateFlow<List<TransferEntity>> = dao.getAllTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFiles: StateFlow<List<FileItemEntity>> = dao.getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoadingFiles = MutableStateFlow(false)
    val selectedCategoryTab = MutableStateFlow("Photos")

    // ---- Full-device folder browser (internal storage + SD card, folders included) ----
    val storageRoots: List<StorageRoot> by lazy { LocalFileSystem.storageRoots(appContext) }
    /** Empty = showing the root volume list; each entry is one level deeper. */
    val browsePathStack = MutableStateFlow<List<String>>(emptyList())
    val browseEntries = MutableStateFlow<List<LocalFileNode>>(emptyList())
    val browseLoading = MutableStateFlow(false)
    /** Individually-checked file paths. */
    val browseSelectedFiles = MutableStateFlow<Set<String>>(emptySet())
    /** Whole-folder checks - resolved to their files at send time. */
    val browseSelectedFolders = MutableStateFlow<Set<String>>(emptySet())
    /** (file count, total bytes) across both selection sets, recomputed after each toggle. */
    val browseSelectionSummary = MutableStateFlow(0 to 0L)

    // ---- Wi-Fi Direct discovery (Send flow, primary QuickShare-style option) ----
    val discoveredDevices: StateFlow<List<WifiP2pDevice>> = wifiDirect.peers
    val isDiscovering: StateFlow<Boolean> = wifiDirect.isDiscovering
    val thisDeviceName: StateFlow<String> = wifiDirect.thisDeviceName
    val wifiDirectError: StateFlow<String?> = wifiDirect.lastError

    // ---- Resolved send target (either a Wi-Fi Direct peer or a scanned QR pairing code) ----
    val targetIp = MutableStateFlow<String?>(null)
    val targetPort = MutableStateFlow(TRANSFER_PORT)
    val targetName = MutableStateFlow<String?>(null)
    val targetSource = MutableStateFlow(TargetSource.NONE)

    // ---- Receive flow ----
    val isListening: StateFlow<Boolean> = fileReceiver.isListening
    val senderConnected: StateFlow<Boolean> = fileReceiver.senderConnected
    val receiveProgress: StateFlow<TransferProgress> = fileReceiver.progress

    // ---- Send flow progress ----
    val sendProgress: StateFlow<TransferProgress> = fileSender.progress

    val myQrPayload = MutableStateFlow<String?>(null)
    /** "High-speed Mode" toggle (mirrors Xender's): creates our own 5GHz Wi-Fi Direct
     *  group instead of relying on whatever Wi-Fi network happens to be shared. */
    val highSpeedMode = MutableStateFlow(false)
    val fastConnectStatus = MutableStateFlow<String?>(null)
    val isFastConnectSupported: Boolean get() = wifiDirect.isFastConnectSupported

    /** One combined signal for "what's going on right now," usable from any screen. */
    val linkState: StateFlow<LinkState> = combine(
        targetSource, wifiDirect.hostHasPeer, fileReceiver.senderConnected, sendProgress, receiveProgress
    ) { source, hostPeer, senderConn, sendP, recvP ->
        when {
            (sendP.overallTotal > 0 && !sendP.isComplete) || (recvP.overallTotal > 0 && !recvP.isComplete) ->
                LinkState.TRANSFERRING
            source != TargetSource.NONE || hostPeer || senderConn -> LinkState.CONNECTED
            else -> LinkState.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LinkState.IDLE)

    init {
        wifiDirect.start()
        viewModelScope.launch {
            wifiDirect.connectionInfo.collect { info ->
                // CLIENT side only: "groupFormed" also fires the instant a HOST forms its
                // own solo group (zero peers yet) - that used to be misread as "connected"
                // the moment the QR screen opened. Only trust this signal when we are
                // definitely the joining client of someone else's group.
                if (info != null && info.groupFormed && !info.isGroupOwner && info.groupOwnerAddress != null) {
                    targetIp.value = info.groupOwnerAddress.hostAddress
                    targetPort.value = TRANSFER_PORT
                    targetSource.value = TargetSource.WIFI_DIRECT
                    NotificationHelper.notifyConnectionStatus(appContext, connected = true, deviceName = targetName.value)
                }
            }
        }
        viewModelScope.launch {
            // HOST side: only a real, non-empty client list counts as "someone connected."
            wifiDirect.hostHasPeer.collect { hasPeer ->
                if (hasPeer) {
                    NotificationHelper.notifyConnectionStatus(appContext, connected = true, deviceName = "Nearby device")
                }
            }
        }
        viewModelScope.launch {
            fileReceiver.senderConnected.collect { connected ->
                if (connected) NotificationHelper.notifyConnectionStatus(appContext, connected = true, deviceName = "Nearby device")
            }
        }
        viewModelScope.launch {
            receiveProgress.collect { progress ->
                if (!progress.isConnecting && progress.overallTotal > 0 && !progress.isComplete) {
                    NotificationHelper.updateProgress(appContext, isSending = false, progress)
                }
            }
        }
        viewModelScope.launch {
            sendProgress.collect { progress ->
                if (progress.overallTotal > 0 && !progress.isComplete) {
                    NotificationHelper.updateProgress(appContext, isSending = true, progress)
                }
            }
        }
        // Receiving is now always-on for the lifetime of the app process, independent of
        // which screen is open - matches how Xender/Quick Share stay reachable in the
        // background instead of only listening while a specific screen is on top.
        startReceiving()
        refreshMyQrPayload()
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirect.stop()
        fileReceiver.stop()
        SparkTransferService.stopIfIdle(appContext)
    }

    /**
     * The one "panic button" reset: tears down whatever connection/pairing state exists
     * (Wi-Fi Direct group or client link, any in-flight send) and returns everything to a
     * clean idle state. This is what was missing before - a failed/half-formed connection
     * used to just linger forever with no way to back out of it short of restarting the app.
     */
    fun resetConnection() {
        transferJob?.cancel()
        transferJob = null
        wifiDirect.stopDiscovery()
        wifiDirect.disconnect()
        wifiDirect.stopGroup()
        targetIp.value = null
        targetName.value = null
        targetSource.value = TargetSource.NONE
        fastConnectStatus.value = null
        // NOTE: deliberately not calling SparkTransferService.stopIfIdle() here - receiving
        // is always-on now, so the foreground service must keep running regardless of a
        // send/pairing attempt being reset. It only ever stops in onCleared()/stopReceiving().
        // Receiving itself stays on (it's always-on now) - this only clears an in-progress
        // or stuck pairing/send attempt, not the "am I reachable at all" state.
    }

    // ---------- Device file browsing (real MediaStore, no seeded data) ----------

    fun loadDeviceFiles() {
        viewModelScope.launch {
            isLoadingFiles.value = true
            val files = withContext(Dispatchers.IO) { DeviceFiles.queryAll(appContext) }
            dao.clearAllFiles()
            if (files.isNotEmpty()) dao.insertAllFiles(files)
            isLoadingFiles.value = false
        }
    }

    fun toggleFileSelection(fileId: String, currentSelected: Boolean) {
        viewModelScope.launch { dao.updateFileSelection(fileId, !currentSelected) }
    }

    fun clearSelections() {
        viewModelScope.launch { dao.clearAllSelections() }
    }

    // ---------- Full-device folder browser ----------

    /** Enter a top-level storage root (shown when [browsePathStack] is empty). */
    fun browseIntoRoot(root: StorageRoot) {
        browsePathStack.value = listOf(root.path)
        loadBrowseEntries(root.path)
    }

    /** Descend into a folder row from the current listing. */
    fun browseInto(node: LocalFileNode) {
        if (!node.isDirectory) return
        browsePathStack.value = browsePathStack.value + node.path
        loadBrowseEntries(node.path)
    }

    /** Goes up one level; true if it was able to (false = caller should leave the screen). */
    fun browseUp(): Boolean {
        val stack = browsePathStack.value
        if (stack.isEmpty()) return false
        val next = stack.dropLast(1)
        browsePathStack.value = next
        if (next.isEmpty()) {
            browseEntries.value = emptyList()
        } else {
            loadBrowseEntries(next.last())
        }
        return true
    }

    private fun loadBrowseEntries(path: String) {
        viewModelScope.launch {
            browseLoading.value = true
            browseEntries.value = withContext(Dispatchers.IO) { LocalFileSystem.listChildren(path) }
            browseLoading.value = false
        }
    }

    fun toggleBrowseFile(path: String) {
        val current = browseSelectedFiles.value
        browseSelectedFiles.value = if (path in current) current - path else current + path
        refreshBrowseSelectionSummary()
    }

    fun toggleBrowseFolder(path: String) {
        val current = browseSelectedFolders.value
        browseSelectedFolders.value = if (path in current) current - path else current + path
        refreshBrowseSelectionSummary()
    }

    fun clearBrowseSelection() {
        browseSelectedFiles.value = emptySet()
        browseSelectedFolders.value = emptySet()
        browseSelectionSummary.value = 0 to 0L
    }

    private fun refreshBrowseSelectionSummary() {
        val files = browseSelectedFiles.value
        val folders = browseSelectedFolders.value
        viewModelScope.launch {
            val (count, bytes) = withContext(Dispatchers.IO) {
                var totalCount = files.size
                var totalBytes = files.sumOf { File(it).length() }
                folders.forEach { folderPath ->
                    val (fCount, fBytes) = LocalFileSystem.folderSummary(folderPath)
                    totalCount += fCount
                    totalBytes += fBytes
                }
                totalCount to totalBytes
            }
            browseSelectionSummary.value = count to bytes
        }
    }

    /** Resolves the current browse selection (files + whole folders) into sendable items. */
    private suspend fun resolveBrowseSendables(): List<SendableFile> = withContext(Dispatchers.IO) {
        val fromFiles = browseSelectedFiles.value.map { path ->
            val f = File(path)
            SendableFile(Uri.fromFile(f), f.name, f.length())
        }
        val fromFolders = browseSelectedFolders.value.flatMap { folderPath ->
            val root = File(folderPath)
            LocalFileSystem.collectFilesRecursively(folderPath).map { f ->
                val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                val relativePath = if (rel.isBlank()) root.name else "${root.name}/$rel"
                SendableFile(Uri.fromFile(f), f.name, f.length(), relativePath)
            }
        }
        fromFiles + fromFolders
    }

    // ---------- Wi-Fi Direct: Send flow, primary "nearby devices" option ----------

    fun startPeerDiscovery() = wifiDirect.startDiscovery()
    fun stopPeerDiscovery() = wifiDirect.stopDiscovery()

    fun connectToPeer(device: WifiP2pDevice, onStatus: (String) -> Unit) {
        targetName.value = device.deviceName
        wifiDirect.connect(device) { _, message -> onStatus(message) }
    }

    // ---------- QR pairing (Xender-style alternate option) ----------

    /** Called on the Receive screen: builds this device's own pairing QR content. */
    fun refreshMyQrPayload() {
        val ip = NetworkUtils.getLocalIpAddress()
        val suffix = UUID.randomUUID().toString().take(4).uppercase()
        val networkName = "DIRECT-sk-Sparks$suffix"
        val passphrase = UUID.randomUUID().toString().replace("-", "").take(12)
        // Wi-Fi Direct group creation works on every Android version via the plain
        // (non-band-forced) overload - it's the reliable primary path now, not gated
        // behind "High-speed Mode" or an existing shared Wi-Fi network. The old code only
        // fell back to sharing this device's regular Wi-Fi IP, which is null for anyone
        // who isn't already on a router (i.e. most people who actually need this app).
        wifiDirect.createFastGroup(networkName, passphrase, preferHighSpeed = highSpeedMode.value) { success, message ->
            fastConnectStatus.value = message
            myQrPayload.value = when {
                success -> QrPairing.buildFastConnectPayload(
                    wifiDirect.thisDeviceName.value, ip ?: "0.0.0.0", TRANSFER_PORT, networkName, passphrase
                )
                ip != null -> QrPairing.buildPayload(wifiDirect.thisDeviceName.value, ip, TRANSFER_PORT)
                else -> null
            }
        }
    }

    fun setHighSpeedMode(enabled: Boolean) {
        if (highSpeedMode.value == enabled) return
        highSpeedMode.value = enabled
        // The group itself stays up either way now - only the band preference changes -
        // so just recreate it with the new preference instead of tearing pairing down.
        refreshMyQrPayload()
    }

    /** Called on the Send screen after a successful QR scan of another device's code. */
    fun applyScannedPayload(raw: String): Boolean {
        val parsed = QrPairing.parsePayload(raw) ?: return false
        targetName.value = parsed.deviceName
        // Always set the LAN ip/port bundled in the QR first. If this is a high-speed
        // (Wi-Fi Direct Fast Connect) code, the connectionInfo collector in init{}
        // will overwrite targetIp with the P2P group owner's address - the 5GHz link -
        // once the join below succeeds; on failure we simply keep using this LAN address.
        targetIp.value = parsed.ip
        targetPort.value = parsed.port
        targetSource.value = TargetSource.QR_PAIR
        if (parsed.isFastConnect && wifiDirect.isFastConnectSupported) {
            wifiDirect.joinFastGroup(parsed.fastConnectNetworkName!!, parsed.fastConnectPassphrase!!) { _, message ->
                fastConnectStatus.value = message
            }
        }
        return true
    }

    fun clearTarget() {
        targetIp.value = null
        targetName.value = null
        targetSource.value = TargetSource.NONE
        wifiDirect.disconnect()
    }

    // ---------- Receiving files ----------

    fun startReceiving() {
        SparkTransferService.start(appContext)
        fileReceiver.start { savedPath, size -> recordReceivedFile(savedPath, size) }
    }

    fun stopReceiving() {
        fileReceiver.stop()
        SparkTransferService.stopIfIdle(appContext)
    }

    private fun displayNameFromSavedPath(savedPath: String): String =
        if (savedPath.startsWith("content://")) {
            Uri.parse(savedPath).lastPathSegment?.substringAfterLast('/') ?: "received_file"
        } else {
            File(savedPath).name
        }

    // ---------- Sending files ----------

    /** The in-flight send coroutine, if any - kept so [cancelTransferSession] can actually stop it. */
    private var transferJob: kotlinx.coroutines.Job? = null

    /** The full pending cart right now, from every source (MediaStore picks, folder browser,
     *  and files handed in via another app's share sheet) - used by both the normal push
     *  flow ([startTransferSession]) and the pull-response flow ([handleIncomingPullRequest]). */
    private fun resolveCurrentCart(): Triple<List<FileItemEntity>, List<SendableFile>, List<SendableFile>> {
        val selected = allFiles.value.filter { it.isSelected }
        val fromBrowser = resolveBrowseSendables()
        val fromShareIntent = pendingSharedFiles.value
        return Triple(selected, fromBrowser, fromShareIntent)
    }

    /** Shared by both send paths: writes history rows for a completed send and clears the cart. */
    private suspend fun recordSentHistory(selected: List<FileItemEntity>, fromBrowser: List<SendableFile>, fromShareIntent: List<SendableFile>) {
        selected.forEach { f ->
            dao.insertTransfer(
                TransferEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = f.name,
                    category = f.category.uppercase(),
                    sizeBytes = f.sizeBytes,
                    timestamp = System.currentTimeMillis(),
                    deviceName = targetName.value ?: "Nearby device",
                    isSend = true,
                    status = "COMPLETED"
                )
            )
        }
        (fromBrowser + fromShareIntent).forEach { f ->
            dao.insertTransfer(
                TransferEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = f.name,
                    category = categoryForFile(f.name),
                    sizeBytes = f.sizeBytes,
                    timestamp = System.currentTimeMillis(),
                    deviceName = targetName.value ?: "Nearby device",
                    isSend = true,
                    status = "COMPLETED"
                )
            )
        }
        dao.clearAllSelections()
        clearBrowseSelection()
        pendingSharedFiles.value = emptyList()
    }

    /**
     * Fires when someone scans this device's QR and connects to *pull* the queued cart,
     * instead of the traditional flow where this device dials out and pushes. Runs
     * synchronously on [FileReceiveServer]'s own background pool thread (see the
     * onPullRequested doc comment on that class) - blocking here is fine, the same way the
     * rest of this file's raw socket I/O already blocks its own worker threads.
     *
     * Reuses the existing [fileSender] instance, so its progress flows into the same
     * [sendProgress] that TransferringScreen already displays - no separate UI path needed.
     *
     * Known limitation: unlike [startTransferSession], this isn't tracked via [transferJob],
     * so [cancelTransferSession] can't currently interrupt an in-flight pull-triggered push.
     * Cancelling would need a cooperative check inside FileSenderClient's write loop, which
     * is a larger change than this pass covers.
     */
    private fun handleIncomingPullRequest(channel: SocketChannel) {
        val (selected, fromBrowser, fromShareIntent) = resolveCurrentCart()
        val fromMediaStore = selected.map { SendableFile(Uri.parse(it.uri), it.name, it.sizeBytes) }
        val sendables = fromMediaStore + fromBrowser + fromShareIntent
        if (sendables.isEmpty()) {
            try { channel.close() } catch (_: Exception) {}
            return
        }
        SparkTransferService.start(appContext)
        val success = fileSender.pushOverAcceptedChannel(channel, sendables)
        if (success) {
            kotlinx.coroutines.runBlocking { recordSentHistory(selected, fromBrowser, fromShareIntent) }
        }
    }

    /**
     * Called after successfully scanning another device's QR: connects out to them and
     * *pulls* their queued cart, the mirror image of [startTransferSession]. Progress flows
     * into [receiveProgress] exactly like an ordinary incoming transfer, so ReceiveScreen's
     * existing UI needs no special-casing for this path.
     */
    fun startPullSession(onComplete: (Boolean) -> Unit) {
        val ip = targetIp.value
        if (ip == null) {
            onComplete(false)
            return
        }
        SparkTransferService.start(appContext)
        transferJob = viewModelScope.launch {
            var success = false
            try {
                success = withContext(Dispatchers.IO) {
                    fileReceiver.pullFrom(ip, targetPort.value) { savedPath, size ->
                        recordReceivedFile(savedPath, size)
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User hit Cancel - not an error, just stop quietly.
            } catch (t: Throwable) {
                success = false
            } finally {
                transferJob = null
                onComplete(success)
            }
        }
    }

    /** Shared by [startReceiving]'s always-on listener and [startPullSession] - records one
     *  received file into history and notifies, regardless of which path brought it in. */
    private fun recordReceivedFile(savedPath: String, size: Long) {
        val fileName = displayNameFromSavedPath(savedPath)
        viewModelScope.launch {
            dao.insertTransfer(
                TransferEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    category = categoryForFile(fileName),
                    sizeBytes = size,
                    timestamp = System.currentTimeMillis(),
                    deviceName = targetName.value ?: "Nearby device",
                    isSend = false,
                    status = "COMPLETED",
                    savedPath = savedPath
                )
            )
        }
        NotificationHelper.notifyFileReceived(appContext, fileName)
    }

    fun startTransferSession(onComplete: (Boolean) -> Unit) {
        val ip = targetIp.value
        if (ip == null) {
            onComplete(false)
            return
        }
        SparkTransferService.start(appContext)
        transferJob = viewModelScope.launch {
            // Wrapped end-to-end: any unexpected exception here (a bad URI, a database
            // hiccup, a socket dying mid-write) must never crash the app - it should just
            // surface as a failed transfer with an error message on screen.
            var success = false
            try {
                val (selected, fromBrowser, fromShareIntent) = resolveCurrentCart()
                val fromMediaStore = selected.map { SendableFile(Uri.parse(it.uri), it.name, it.sizeBytes) }
                val sendables = fromMediaStore + fromBrowser + fromShareIntent
                success = withContext(Dispatchers.IO) {
                    fileSender.send(ip, sendables)
                }
                if (success) {
                    recordSentHistory(selected, fromBrowser, fromShareIntent)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User hit Cancel - not an error, just stop quietly.
            } catch (t: Throwable) {
                success = false
            } finally {
                // Deliberately not stopping the service here - it's the same always-on
                // foreground service keeping receiving alive; a finished/failed send must
                // not tear that down.
                transferJob = null
                onComplete(success)
            }
        }
    }

    /** Called from the Cancel action on the transferring screen: actually stops the send instead of just navigating away. */
    fun cancelTransferSession() {
        transferJob?.cancel()
        transferJob = null
        // Not stopping the service - same reasoning as above, receiving stays up.
    }

    // ---------- Files shared into Sparks from another app (Gallery, Files, etc.) ----------

    /** Files handed to us via ACTION_SEND / ACTION_SEND_MULTIPLE from another app, awaiting a pick target. */
    val pendingSharedFiles = MutableStateFlow<List<SendableFile>>(emptyList())

    fun setPendingSharedFiles(files: List<SendableFile>) {
        pendingSharedFiles.value = files
    }

    /**
     * True the instant the user has picked *anything* to send - from MediaStore, from the
     * folder browser, or from another app's share sheet - regardless of whether a device is
     * connected yet. This is what makes "pick files first, then connect" possible: the Send
     * screen checks this to decide whether to jump straight to Transferring once a device is
     * found, instead of always routing back through the picker.
     */
    val hasPendingCart: StateFlow<Boolean> = combine(
        allFiles, browseSelectionSummary, pendingSharedFiles
    ) { files, browseSummary, shared ->
        files.any { it.isSelected } || browseSummary.first > 0 || shared.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---------- History ----------

    fun deleteTransfer(transfer: TransferEntity) {
        viewModelScope.launch { dao.deleteTransfer(transfer) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { dao.clearAllTransfers() }
    }

    private fun categoryForFile(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true) -> "PHOTO"
        name.endsWith(".mp4", true) || name.endsWith(".mov", true) || name.endsWith(".mkv", true) -> "VIDEO"
        name.endsWith(".mp3", true) || name.endsWith(".m4a", true) || name.endsWith(".wav", true) -> "AUDIO"
        name.endsWith(".apk", true) -> "APP"
        name.endsWith(".zip", true) || name.endsWith(".rar", true) -> "ARCHIVE"
        else -> "DOC"
    }
}
