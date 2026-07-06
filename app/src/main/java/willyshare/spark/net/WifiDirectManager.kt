package willyshare.spark.net

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin, real wrapper around Android's WifiP2pManager (Wi-Fi Direct).
 * No simulated/fake devices anywhere here - every device in [peers] came from
 * an actual WIFI_P2P_PEERS_CHANGED_ACTION broadcast from the OS.
 */
class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _thisDeviceName = MutableStateFlow(android.os.Build.MODEL ?: "This device")
    val thisDeviceName: StateFlow<String> = _thisDeviceName.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val isSupported: Boolean get() = manager != null

    fun start() {
        val mgr = manager ?: return
        channel = mgr.initialize(context, context.mainLooper, null)

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        val br = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        _isWifiP2pEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        val ch = channel ?: return
                        try {
                            mgr.requestPeers(ch) { peerList ->
                                _peers.value = peerList.deviceList.toList()
                            }
                        } catch (e: SecurityException) {
                            _lastError.value = "Missing permission to read nearby devices"
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val ch = channel ?: return
                        try {
                            mgr.requestConnectionInfo(ch) { info -> _connectionInfo.value = info }
                        } catch (_: SecurityException) {
                        }
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device =
                            intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        if (!device?.deviceName.isNullOrBlank()) {
                            _thisDeviceName.value = device!!.deviceName
                        }
                    }
                }
            }
        }
        receiver = br
        context.registerReceiver(br, filter)
    }

    fun stop() {
        stopDiscovery()
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        receiver = null
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _isDiscovering.value = true
                    _lastError.value = null
                }

                override fun onFailure(reason: Int) {
                    _isDiscovering.value = false
                    _lastError.value = "Discovery failed (code $reason)"
                }
            })
        } catch (e: SecurityException) {
            _lastError.value = "Location / Nearby devices permission required"
        }
    }

    fun stopDiscovery() {
        val mgr = manager ?: return
        val ch = channel ?: return
        _isDiscovering.value = false
        try {
            mgr.stopPeerDiscovery(ch, null)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice, onResult: (Boolean, String) -> Unit) {
        val mgr = manager ?: return onResult(false, "Wi-Fi Direct not supported")
        val ch = channel ?: return onResult(false, "Not initialized")
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        try {
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onResult(true, "Connecting to ${device.deviceName}\u2026")
                }

                override fun onFailure(reason: Int) {
                    onResult(false, "Connection failed (code $reason)")
                }
            })
        } catch (e: SecurityException) {
            onResult(false, "Missing permission to connect")
        }
    }

    fun disconnect() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.removeGroup(ch, null)
        } catch (_: Exception) {
        }
        _connectionInfo.value = null
    }
}
