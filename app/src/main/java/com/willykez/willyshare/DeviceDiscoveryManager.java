package com.willykez.willyshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * DeviceDiscoveryManager — WiFi Direct (P2P) device discovery.
 *
 * Replaces the old Bluetooth discovery approach with WiFi Direct, which:
 *  - Discovers devices in ~2-5 seconds (vs 12-15 seconds for BT)
 *  - Builds a direct 5 GHz P2P link at ~150-300 Mbps after connect
 *  - Does NOT need a visible hotspot or manual SSID/password exchange
 *
 * Flow (QuickShare-style):
 *   SENDER: calls startScan() → devices appear in callback
 *           user taps a device → calls connectToDevice()
 *           on P2P connection → gets hostIp from WifiP2pInfo
 *
 *   RECEIVER: calls startReceiving() → device is now "available" on P2P
 *             waits for MSG_CONNECTED with remote IP
 *             then starts TransferEngine.startReceiving()
 */
public class DeviceDiscoveryManager {
    private static final String TAG = "DeviceDiscovery";

    // UI Handler message codes
    public static final int MSG_DEVICES_UPDATED  = 10;  // obj = List<WifiP2pDevice>
    public static final int MSG_CONNECTED        = 11;  // obj = WifiP2pInfo
    public static final int MSG_CONNECT_FAILED   = 12;
    public static final int MSG_DISCOVERY_STARTED = 13;
    public static final int MSG_DISCOVERY_STOPPED = 14;
    public static final int MSG_THIS_DEVICE       = 15; // obj = WifiP2pDevice (own device info)

    private final Context context;
    private final Handler uiHandler;
    private final WifiP2pManager p2pManager;
    private final WifiP2pManager.Channel p2pChannel;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver p2pReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    // Request updated peer list
                    p2pManager.requestPeers(p2pChannel, peerList -> {
                        List<WifiP2pDevice> devices = new ArrayList<>(peerList.getDeviceList());
                        uiHandler.obtainMessage(MSG_DEVICES_UPDATED, devices).sendToTarget();
                    });
                    break;

                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    android.net.NetworkInfo netInfo = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (netInfo != null && netInfo.isConnected()) {
                        p2pManager.requestConnectionInfo(p2pChannel, info -> {
                            if (info != null) {
                                uiHandler.obtainMessage(MSG_CONNECTED, info).sendToTarget();
                            }
                        });
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION:
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                        uiHandler.sendEmptyMessage(MSG_DISCOVERY_STARTED);
                    } else {
                        uiHandler.sendEmptyMessage(MSG_DISCOVERY_STOPPED);
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                    WifiP2pDevice thisDevice = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                    if (thisDevice != null)
                        uiHandler.obtainMessage(MSG_THIS_DEVICE, thisDevice).sendToTarget();
                    break;
            }
        }
    };

    public DeviceDiscoveryManager(Context context, Handler uiHandler) {
        this.context    = context.getApplicationContext();
        this.uiHandler  = uiHandler;
        this.p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.p2pChannel = (p2pManager != null)
                ? p2pManager.initialize(context, context.getMainLooper(), null)
                : null;
    }

    public boolean isAvailable() {
        return p2pManager != null && p2pChannel != null;
    }

    /** Register P2P broadcast receiver — call from onResume(). */
    public void register() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        context.registerReceiver(p2pReceiver, filter);
        receiverRegistered = true;
    }

    /** Unregister — call from onPause(). */
    public void unregister() {
        if (!receiverRegistered) return;
        try { context.unregisterReceiver(p2pReceiver); } catch (Exception ignored) {}
        receiverRegistered = false;
    }

    /**
     * SENDER: start scanning for nearby WillyShare devices.
     * Results arrive via MSG_DEVICES_UPDATED.
     */
    public void startScan() {
        if (!isAvailable()) return;
        stopScan();
        p2pManager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "P2P discovery started");
            }
            @Override public void onFailure(int reason) {
                Log.e(TAG, "P2P discovery failed: " + reason);
                uiHandler.sendEmptyMessage(MSG_DISCOVERY_STOPPED);
            }
        });
    }

    public void stopScan() {
        if (!isAvailable()) return;
        p2pManager.stopPeerDiscovery(p2pChannel, null);
    }

    /**
     * SENDER: connect to a chosen device.
     * On success, MSG_CONNECTED fires with WifiP2pInfo containing the group owner IP.
     */
    public void connectToDevice(WifiP2pDevice device) {
        if (!isAvailable()) return;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        // Prefer 5 GHz (GO intent 15 = this device prefers being Group Owner)
        config.groupOwnerIntent = 15;

        p2pManager.connect(p2pChannel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "P2P connect initiated to " + device.deviceName);
            }
            @Override public void onFailure(int reason) {
                Log.e(TAG, "P2P connect failed: " + reason);
                uiHandler.sendEmptyMessage(MSG_CONNECT_FAILED);
            }
        });
    }

    /**
     * RECEIVER: make this device discoverable and wait for a connection.
     * Same as startScan() — P2P makes device visible automatically once
     * discoverPeers is active. MSG_CONNECTED will fire on incoming connect.
     */
    public void startReceiving() {
        // In WiFi Direct, both sides discover each other.
        // The receiver just needs to be registered and wait.
        // Optionally call discoverPeers so we're in discovery mode:
        startScan();
    }

    /** Disconnect and remove the P2P group. */
    public void disconnect() {
        if (!isAvailable()) return;
        p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { Log.d(TAG, "P2P group removed"); }
            @Override public void onFailure(int r) { Log.w(TAG, "removeGroup failed: " + r); }
        });
        stopScan();
    }
}
