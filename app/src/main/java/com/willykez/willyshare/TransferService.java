package com.willykez.willyshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;

public class TransferService extends Service {
    public static final String ACTION_SEND = "ACTION_SEND";
    public static final String ACTION_RECEIVE = "ACTION_RECEIVE";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_CANCEL = "ACTION_CANCEL";
    public static final String EXTRA_FILES = "extra_files";
    public static final String EXTRA_REMOTE_IP = "extra_remote_ip";
    public static final String EXTRA_PORT = "extra_port";

    private static final String CHANNEL_ID = "willyshare_channel";
    private static final int NOTIF_ID = 1;

    private TransferEngine engine;
    private NotificationManager notifManager;
    private boolean isPaused = false;

    private final Handler engineHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TransferEngine.MSG_PROGRESS:
                    updateNotification("Transferring: " + msg.obj + " - " + msg.arg1 + "%");
                    break;
                case TransferEngine.MSG_SPEED:
                    break;
                case TransferEngine.MSG_DONE:
                    updateNotification("Done: " + msg.obj);
                    stopSelf();
                    break;
                case TransferEngine.MSG_ERROR:
                    updateNotification("Error: " + msg.obj);
                    stopSelf();
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        engine = new TransferEngine(this, engineHandler);
        startForeground(NOTIF_ID, buildNotification("WillyShare running..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;

        switch (action) {
            case ACTION_SEND:
                ArrayList<String> files = intent.getStringArrayListExtra(EXTRA_FILES);
                String ip = intent.getStringExtra(EXTRA_REMOTE_IP);
                int port = intent.getIntExtra(EXTRA_PORT, TransferEngine.PORT);
                if (files != null && ip != null) engine.sendFiles(files, ip, port);
                break;
            case ACTION_RECEIVE:
                engine.startReceiving();
                break;
            case ACTION_PAUSE:
                isPaused = !isPaused;
                engine.setPaused(isPaused);
                break;
            case ACTION_CANCEL:
                engine.cancel();
                engine.stopReceiving();
                stopSelf();
                break;
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        engine.cancel();
        engine.stopReceiving();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "WillyShare Transfer", NotificationManager.IMPORTANCE_LOW);
            notifManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WillyShare")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        notifManager.notify(NOTIF_ID, buildNotification(text));
    }
}
