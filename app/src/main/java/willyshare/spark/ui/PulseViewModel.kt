package willyshare.spark.ui

import android.app.Application
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import willyshare.spark.data.FileItemEntity
import willyshare.spark.data.PulseDatabase
import willyshare.spark.data.StoragePrefs
import willyshare.spark.data.TransferEntity
import willyshare.spark.net.DeviceFiles
import willyshare.spark.net.FileReceiveServer
import willyshare.spark.net.FileSenderClient
import willyshare.spark.net.LocalFileNode
import willyshare.spark.net.LocalFileSystem
import willyshare.spark.net.NetworkUtils
import willyshare.spark.net.QrPairing
import willyshare.spark.net.ReceiveTarget
import willyshare.spark.net.SafFileWriter
import willyshare.spark.net.SendableFile
import willyshare.spark.net.StorageRoot
import willyshare.spark.net.TRANSFER_PORT
import willyshare.spark.net.TransferProgress
import willyshare.spark.net.WifiDirectManager
import willyshare.spark.service.SparkTransferService
import willyshare.spark.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** How the current outgoing transfer's target was resolved. */
enum class TargetSource { WIFI_DIRECT, QR_PAIR, NONE }

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

    private val fileReceiver = FileReceiveServer {
        val treeUriString = receiveTreeUri.value
        val treeUri = treeUriString?.let { Uri.parse(it) }
        if (treeUri != null && SafFileWriter.isAccessible(appContext, treeUri)) {
            ReceiveTarget.Tree(appContext, treeUri)
        } else {
            ReceiveTarget.Plain(defaultReceiveDir)
        }
    }

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

    init {
        wifiDirect.start()
        viewModelScope.launch {
            wifiDirect.connectionInfo.collect { info ->
                if (info != null && info.groupFormed && info.groupOwnerAddress != null) {
                    targetIp.value = info.groupOwnerAddress.hostAddress
                    targetPort.value = TRANSFER_PORT
                    targetSource.value = TargetSource.WIFI_DIRECT
                    NotificationHelper.notifyConnectionStatus(appContext, connected = true, deviceName = targetName.value)
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
        refreshMyQrPayload()
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirect.stop()
        fileReceiver.stop()
        SparkTransferService.stopIfIdle(appContext)
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
        if (highSpeedMode.value && wifiDirect.isFastConnectSupported) {
            val suffix = UUID.randomUUID().toString().take(4).uppercase()
            val networkName = "DIRECT-sk-Sparks$suffix"
            val passphrase = UUID.randomUUID().toString().replace("-", "").take(12)
            wifiDirect.createFastGroup(networkName, passphrase, preferHighSpeed = true) { success, message ->
                fastConnectStatus.value = message
                myQrPayload.value = when {
                    success -> QrPairing.buildFastConnectPayload(
                        wifiDirect.thisDeviceName.value, ip ?: "0.0.0.0", TRANSFER_PORT, networkName, passphrase
                    )
                    ip != null -> QrPairing.buildPayload(wifiDirect.thisDeviceName.value, ip, TRANSFER_PORT)
                    else -> null
                }
            }
        } else {
            fastConnectStatus.value = null
            myQrPayload.value = if (ip != null) {
                QrPairing.buildPayload(wifiDirect.thisDeviceName.value, ip, TRANSFER_PORT)
            } else null
        }
    }

    fun setHighSpeedMode(enabled: Boolean) {
        if (highSpeedMode.value == enabled) return
        highSpeedMode.value = enabled
        if (!enabled) wifiDirect.stopGroup()
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
        fileReceiver.start { savedPath, size ->
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
                val selected = allFiles.value.filter { it.isSelected }
                val fromMediaStore = selected.map { SendableFile(Uri.parse(it.uri), it.name, it.sizeBytes) }
                val fromBrowser = resolveBrowseSendables()
                val fromShareIntent = pendingSharedFiles.value
                val sendables = fromMediaStore + fromBrowser + fromShareIntent
                success = withContext(Dispatchers.IO) {
                    fileSender.send(ip, sendables)
                }
                if (success) {
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
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User hit Cancel - not an error, just stop quietly.
            } catch (t: Throwable) {
                success = false
            } finally {
                SparkTransferService.stopIfIdle(appContext)
                transferJob = null
                onComplete(success)
            }
        }
    }

    /** Called from the Cancel action on the transferring screen: actually stops the send instead of just navigating away. */
    fun cancelTransferSession() {
        transferJob?.cancel()
        transferJob = null
        SparkTransferService.stopIfIdle(appContext)
    }

    // ---------- Files shared into Sparks from another app (Gallery, Files, etc.) ----------

    /** Files handed to us via ACTION_SEND / ACTION_SEND_MULTIPLE from another app, awaiting a pick target. */
    val pendingSharedFiles = MutableStateFlow<List<SendableFile>>(emptyList())

    fun setPendingSharedFiles(files: List<SendableFile>) {
        pendingSharedFiles.value = files
    }

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
