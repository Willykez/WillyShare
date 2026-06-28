package com.willykez.willyshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.*;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * DeviceDiscoveryManager — WiFi Direct P2P discovery.
 *
 * FIX: groupOwnerIntent collision.
 * Previously BOTH sender and receiver set groupOwnerIntent=15 (both want to be GO).
 * This causes "BUSY" errors and repeated failed negotiations.
 *
 * Correct pattern:
 *  - RECEIVER sets groupOwnerIntent = 15  (always becomes GO, gets fixed IP 192.168.49.1)
 *  - SENDER   sets groupOwnerIntent = 0   (always becomes client, connects to GO)
 *
 * This is exactly how QuickShare works: receiver is always GO, sender always client.
 * Receiver IP is always 192.168.49.1 — sender connects TransferEngine to that address.
 *
 * FIX: "connected but shows as disconnected"
 * MSG_CONNECTED was firing before the IP was actually routable. Now we wait for
 * WIFI_P2P_CONNECTION_CHANGED_ACTION with isConnected=true AND then requestConnectionInfo.
 * Only fire MSG_CONNECTED once (guarded by connectedFired flag).
 *
 * FIX: receiver jumping to TransferActivity on a NEW P2P connection even when
 * it was already transferring — handled by letting ScanActivity finish itself
 * once it launches TransferActivity, so duplicate connection events are ignored.
 */
public class DeviceDiscoveryManager {
    private static final String TAG = "DeviceDiscovery";

    public static final int MSG_DEVICES_UPDATED   = 10;
    public static final int MSG_CONNECTED         = 11;
    public static final int MSG_CONNECT_FAILED    = 12;
    public static final int MSG_DISCOVERY_STARTED = 13;
    public static final int MSG_DISCOVERY_STOPPED = 14;
    public static final int MSG_THIS_DEVICE       = 15;

    private final Context            context;
    private final Handler            uiHandler;
    private final WifiP2pManager     p2pManager;
    private final WifiP2pManager.Channel p2pChannel;
    private       boolean            receiverRegistered = false;
    private       boolean            isSender           = false;
    // Guard: only fire MSG_CONNECTED once per session
    private volatile boolean         connectedFired     = false;

    private final BroadcastReceiver p2pReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {

                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    if (p2pManager != null) {
                        p2pManager.requestPeers(p2pChannel, peerList -> {
                            List<WifiP2pDevice> devs = new ArrayList<>(peerList.getDeviceList());
                            uiHandler.obtainMessage(MSG_DEVICES_UPDATED, devs).sendToTarget();
                        });
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    android.net.NetworkInfo netInfo = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (netInfo != null && netInfo.isConnected() && !connectedFired) {
                        if (p2pManager != null) {
                            p2pManager.requestConnectionInfo(p2pChannel, info -> {
                                if (info != null && info.groupOwnerAddress != null
                                        && !connectedFired) {
                                    connectedFired = true;
                                    uiHandler.obtainMessage(MSG_CONNECTED, info).sendToTarget();
                                    Log.d(TAG, "P2P connected. GO=" + info.isGroupOwner
                                            + " addr=" + info.groupOwnerAddress.getHostAddress());
                                }
                            });
                        }
                    } else if (netInfo != null && !netInfo.isConnected()) {
                        // Reset so next connection fires again
                        connectedFired = false;
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION:
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED)
                        uiHandler.sendEmptyMessage(MSG_DISCOVERY_STARTED);
                    else
                        uiHandler.sendEmptyMessage(MSG_DISCOVERY_STOPPED);
                    break;

                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                    WifiP2pDevice me = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                    if (me != null) uiHandler.obtainMessage(MSG_THIS_DEVICE, me).sendToTarget();
                    break;
            }
        }
    };

    public DeviceDiscoveryManager(Context context, Handler uiHandler, boolean isSender) {
        this.context   = context.getApplicationContext();
        this.uiHandler = uiHandler;
        this.isSender  = isSender;
        this.p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.p2pChannel = (p2pManager != null)
                ? p2pManager.initialize(context, context.getMainLooper(), () -> {
                    Log.w(TAG, "P2P channel disconnected");
                }) : null;
    }

    public boolean isAvailable() {
        return p2pManager != null && p2pChannel != null;
    }

    public void register() {
        if (receiverRegistered) return;
        IntentFilter f = new IntentFilter();
        f.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        context.registerReceiver(p2pReceiver, f);
        receiverRegistered = true;
    }

    public void unregister() {
        if (!receiverRegistered) return;
        try { context.unregisterReceiver(p2pReceiver); } catch (Exception ignored) {}
        receiverRegistered = false;
    }

    /** SENDER: start peer scan */
    public void startScan() {
        if (!isAvailable()) return;
        p2pManager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { Log.d(TAG, "discoverPeers started"); }
            @Override public void onFailure(int r) {
                Log.e(TAG, "discoverPeers failed: " + r);
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
     * groupOwnerIntent=0 → sender is always the P2P client.
     * Receiver sets groupOwnerIntent=15 → receiver is always GO (fixed IP 192.168.49.1).
     */
    public void connectToDevice(WifiP2pDevice device) {
        if (!isAvailable()) return;
        connectedFired = false; // reset for new session
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress   = device.deviceAddress;
        config.groupOwnerIntent = isSender ? 0 : 15; // sender=client, receiver=GO
        p2pManager.connect(p2pChannel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { Log.d(TAG, "connect initiated → " + device.deviceName); }
            @Override public void onFailure(int r) {
                Log.e(TAG, "connect failed: " + r);
                uiHandler.sendEmptyMessage(MSG_CONNECT_FAILED);
            }
        });
    }

    /**
     * RECEIVER: become discoverable. groupOwnerIntent=15 ensures this device
     * will be the P2P Group Owner and gets the fixed IP 192.168.49.1.
     */
    public void startReceiving() {
        if (!isAvailable()) return;
        connectedFired = false;
        // Receiver just needs to be in discovery so the sender can find it.
        // The GO role is negotiated when sender calls connect() with intent=0.
        p2pManager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { Log.d(TAG, "Receiver: discoverPeers started"); }
            @Override public void onFailure(int r) { Log.w(TAG, "Receiver: discoverPeers failed " + r); }
        });
    }

    public void disconnect() {
        connectedFired = false;
        if (!isAvailable()) return;
        p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { Log.d(TAG, "P2P group removed"); }
            @Override public void onFailure(int r) { Log.w(TAG, "removeGroup failed: " + r); }
        });
        stopScan();
    }
}
