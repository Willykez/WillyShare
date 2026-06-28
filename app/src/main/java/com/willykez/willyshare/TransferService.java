package com.willykez.willyshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * TransferService — foreground service that keeps the process alive.
 *
 * CRITICAL FIX: This service NO LONGER creates or runs a TransferEngine.
 * The engine lives in TransferActivity where the UI handler is. Previously
 * the service was creating a SECOND engine that was competing for the same
 * server ports as TransferActivity's engine, causing:
 *  - Receiver stuck on "Waiting" (service engine stole the connections)
 *  - 0-byte or half-written files (two RandomAccessFile writers on same path)
 *  - TransferActivity never receiving progress updates (wrong engine had the sockets)
 *
 * The service now only:
 *  1. Starts the foreground notification (keeps process alive during transfer)
 *  2. Updates the notification text when TransferActivity tells it to via Intent
 *  3. Stops itself on cancel/done
 */
public class TransferService extends Service {

    public static final String ACTION_SEND          = "ACTION_SEND";
    public static final String ACTION_RECEIVE       = "ACTION_RECEIVE";
    public static final String ACTION_CANCEL        = "ACTION_CANCEL";
    public static final String ACTION_UPDATE_NOTIF  = "ACTION_UPDATE_NOTIF";
    public static final String EXTRA_NOTIF_TEXT     = "extra_notif_text";

    private static final String CHANNEL_ID = "willyshare_transfer";
    private static final int    NOTIF_ID   = 1;

    private NotificationManager notifManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Transfer running…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;

        switch (action) {
            case ACTION_SEND:
                updateNotification("Sending files…");
                break;
            case ACTION_RECEIVE:
                updateNotification("Waiting to receive…");
                break;
            case ACTION_UPDATE_NOTIF:
                String text = intent.getStringExtra(EXTRA_NOTIF_TEXT);
                if (text != null) updateNotification(text);
                break;
            case ACTION_CANCEL:
                updateNotification("Transfer cancelled");
                stopSelf();
                break;
        }
        return START_NOT_STICKY;
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() { super.onDestroy(); }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "WillyShare Transfer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("File transfer in progress");
            notifManager.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WillyShare")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String text) {
        notifManager.notify(NOTIF_ID, buildNotification(text));
    }
}
