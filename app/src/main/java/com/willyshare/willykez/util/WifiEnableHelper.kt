package com.willyshare.willykez.util

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

object WifiEnableHelper {

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    /**
     * Best available way to get the user to flip Wi-Fi on without leaving the app entirely.
     * API 29+: a quick-settings style panel that returns straight back to us.
     * Below that: WifiManager.setWifiEnabled(true) is still permitted for apps.
     */
    fun requestEnable(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
    }
}
