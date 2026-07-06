package willyshare.spark.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import willyshare.spark.util.NotificationHelper

/**
 * Doesn't own any networking logic itself - [willyshare.spark.ui.PulseViewModel] still drives
 * Wi-Fi Direct / the send+receive sockets. This service exists purely so Android treats the
 * process as foreground-priority while a transfer or listening session is active, which stops
 * the OS from killing it when the app is backgrounded (the "opens to splash screen again"
 * problem). [PulseViewModel] starts/stops it and pushes progress updates through
 * [NotificationHelper], which posts to the same notification ID this service is holding.
 */
class SparkTransferService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
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
        } catch (e: Exception) {
            // If the OS refuses the foreground promotion for any reason, Sparks still works -
            // it just won't survive backgrounding as reliably. Never let this crash the app.
            android.util.Log.w("SparkTransferService", "startForeground failed", e)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, SparkTransferService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                // Never let a keep-alive/notification failure take down an in-progress transfer.
                android.util.Log.w("SparkTransferService", "Failed to start", e)
            }
        }

        /**
         * Call after a transfer/listening session ends. Sparks only ever runs one send OR
         * receive session at a time, so this always stops the service outright - if that
         * assumption changes (e.g. simultaneous send+receive), swap this for a reference count.
         */
        fun stopIfIdle(context: Context) {
            try {
                context.stopService(Intent(context, SparkTransferService::class.java))
            } catch (e: Exception) {
                android.util.Log.w("SparkTransferService", "Failed to stop", e)
            }
        }
    }
}
