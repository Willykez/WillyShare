package com.willyshare.willykez.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.willyshare.willykez.MainActivity
import com.willyshare.willykez.R
import com.willyshare.willykez.net.TransferProgress
import kotlin.math.roundToInt

object NotificationHelper {
    const val TRANSFER_CHANNEL_ID = "spark_transfers"
    const val EVENTS_CHANNEL_ID = "spark_events"

    /** Fixed ID shared by the foreground service notification, so progress updates replace it in place. */
    const val FOREGROUND_NOTIFICATION_ID = 4200
    private const val EVENT_NOTIFICATION_ID_BASE = 5000
    private var eventIdCounter = 0

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(TRANSFER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    TRANSFER_CHANNEL_ID,
                    "Transfers",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Ongoing send/receive progress and listening status" },
            )
        }
        if (manager.getNotificationChannel(EVENTS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    EVENTS_CHANNEL_ID,
                    "Connections & received files",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Peer connected, and files finished receiving" },
            )
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = MainActivity.newIntent(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /** The persistent "keep-alive" notification shown while listening or transferring. */
    fun buildIdleListeningNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setContentTitle("Sparks is ready to receive")
            .setContentText("Listening for nearby devices")
            .setSmallIcon(R.drawable.ic_notification_spark)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildProgressNotification(context: Context, isSending: Boolean, progress: TransferProgress): Notification {
        ensureChannels(context)
        val percent = if (progress.overallTotal > 0) {
            ((progress.overallBytes.toDouble() / progress.overallTotal) * 100).roundToInt()
        } else 0
        val title = if (isSending) "Sending files\u2026" else "Receiving files\u2026"
        val speedMb = progress.overallSpeed / (1024 * 1024)
        // NOTE: the literal '%' after $percent must never sit inside a .format() call -
        // Kotlin's .format() runs Java's Formatter over the WHOLE interpolated string, so
        // the stray '%' + space was read as a numeric flag, then choked on '\u2022'
        // (bullet) as an invalid conversion character (UnknownFormatConversionException),
        // crashing on every progress tick since this fires from a main-thread StateFlow
        // collector with no try/catch around it. Fix: format the speed value on its own
        // first, then interpolate into the final string with no further .format() pass.
        val speedText = "%.1f".format(speedMb)
        val text = "$percent% \u2022 $speedText MB/s"
        return NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_spark)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Updates the shared foreground-notification slot with fresh progress (works even outside the service).
     *  Guardrail: this fires on every progress tick from a main-thread StateFlow collector in
     *  PulseViewModel's init{} - any bad notification state here (malformed string, missing
     *  icon, thrown builder exception) must never propagate into a process crash. Swallow and
     *  drop that single tick instead; the next tick will simply try again. */
    fun updateProgress(context: Context, isSending: Boolean, progress: TransferProgress) {
        if (!hasPostPermission(context)) return
        try {
            NotificationManagerCompat.from(context)
                .notify(FOREGROUND_NOTIFICATION_ID, buildProgressNotification(context, isSending, progress))
        } catch (_: Throwable) {
            // Deliberately swallowed - see guardrail note above. A dropped progress-notification
            // tick is harmless; a crashed transfer is not.
        }
    }

    fun notifyConnectionStatus(context: Context, connected: Boolean, deviceName: String?) {
        if (!hasPostPermission(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setContentTitle(if (connected) "Connected" else "Disconnected")
            .setContentText(
                if (connected) "Linked with ${deviceName ?: "a nearby device"}" else "Connection ended",
            )
            .setSmallIcon(R.drawable.ic_notification_spark)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(nextEventId(), notification)
    }

    /** Posted when a file finishes writing to disk; tapping it opens the file with the system chooser. */
    fun notifyFileReceived(context: Context, fileName: String) {
        if (!hasPostPermission(context)) return
        ensureChannels(context)
        val intent = MainActivity.newIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context, fileName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setContentTitle("File received")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_notification_spark)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(nextEventId(), notification)
    }

    private fun nextEventId(): Int = EVENT_NOTIFICATION_ID_BASE + (eventIdCounter++ % 500)

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
