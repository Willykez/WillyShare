package com.willykez.willyshare;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * HotspotManager — constants and fallback hotspot support.
 *
 * Primary transfer link is now WiFi Direct (P2P) via DeviceDiscoveryManager.
 * The P2P group owner always gets IP 192.168.49.1; clients are DHCP-assigned.
 *
 * HOST_IP here is the WiFi Direct Group Owner IP — constant per Android spec.
 */
public class HotspotManager {
    private static final String TAG = "HotspotManager";

    /** WiFi Direct Group Owner IP — fixed by Android's P2P stack. */
    public static final String HOST_IP       = "192.168.49.1";
    public static final int    TRANSFER_PORT = 8888;

    // Legacy hotspot constants kept for backward compat
    public static final String HOTSPOT_SSID = "WillyShare-Hotspot";
    public static final String HOTSPOT_PASS = "willy1234";

    private final Context context;
    private final WifiManager wifiManager;

    public HotspotManager(Context context) {
        this.context     = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    /** Returns true if WiFi is enabled. */
    public boolean isWifiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    /** Enables WiFi if disabled. */
    @SuppressWarnings("deprecation")
    public void ensureWifiEnabled() {
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Log.d(TAG, "WiFi enabled for P2P");
        }
    }
}
