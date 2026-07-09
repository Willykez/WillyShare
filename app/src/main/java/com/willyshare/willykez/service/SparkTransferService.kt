package com.willyshare.willykez.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.willyshare.willykez.util.NotificationHelper

/**
 * Doesn't own any networking logic itself - [com.willyshare.willykez.ui.PulseViewModel] still drives
 * Wi-Fi Direct / the send+receive sockets. This service exists purely so Android treats the
 * process as foreground-priority while a transfer or listening session is active, which stops
 * the OS from killing it when the app is backgrounded (the "opens to splash screen again"
 * problem). [PulseViewModel] starts/stops it and pushes progress updates through
 * [NotificationHelper], which posts to the same notification ID this service is holding.
 */
class SparkTransferService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildIdleListeningNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SparkTransferService::class.java)
            context.startService(intent)
        }

        /**
         * Call after a transfer/listening session ends. Sparks only ever runs one send OR
         * receive session at a time, so this always stops the service outright - if that
         * assumption changes (e.g. simultaneous send+receive), swap this for a reference count.
         */
        fun stopIfIdle(context: Context) {
            context.stopService(Intent(context, SparkTransferService::class.java))
        }
    }
}
