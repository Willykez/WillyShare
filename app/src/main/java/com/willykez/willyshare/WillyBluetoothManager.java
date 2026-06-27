package com.willykez.willyshare;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class WillyBluetoothManager {
    private static final String TAG = "WillyBTManager";
    private static final UUID UUID_HANDSHAKE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String SERVICE_NAME = "WillyShare";

    public static final int MSG_HANDSHAKE_RECEIVED = 2;

    private final BluetoothAdapter btAdapter;
    private final Handler uiHandler;
    private volatile boolean listeningActive = false;
    private BluetoothServerSocket serverSocket;

    public WillyBluetoothManager(Handler uiHandler) {
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
        this.uiHandler = uiHandler;
    }

    public boolean isAvailable() {
        return btAdapter != null && btAdapter.isEnabled();
    }

    public void startDiscovery() {
        if (btAdapter == null) return;
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();
    }

    public void stopDiscovery() {
        if (btAdapter != null && btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
    }

    public void sendHandshake(BluetoothDevice device, String ssid, String pass, String ip, int port) {
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                stopDiscovery();
                socket = device.createRfcommSocketToServiceRecord(UUID_HANDSHAKE);
                socket.connect();
                JSONObject json = new JSONObject();
                json.put("ssid", ssid);
                json.put("password", pass);
                json.put("hostIp", ip);
                json.put("port", port);
                OutputStream out = socket.getOutputStream();
                out.write(json.toString().getBytes("UTF-8"));
                out.flush();
            } catch (Exception e) {
                Log.e(TAG, "sendHandshake error: " + e.getMessage());
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public void startListeningForHandshake() {
        listeningActive = true;
        new Thread(() -> {
            try {
                serverSocket = btAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, UUID_HANDSHAKE);
                while (listeningActive) {
                    BluetoothSocket socket = serverSocket.accept(10000);
                    if (socket == null) continue;
                    try {
                        InputStream in = socket.getInputStream();
                        byte[] buf = new byte[2048];
                        int read = in.read(buf);
                        if (read > 0) {
                            String json = new String(buf, 0, read, "UTF-8");
                            uiHandler.obtainMessage(MSG_HANDSHAKE_RECEIVED, json).sendToTarget();
                        }
                    } finally {
                        try { socket.close(); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "BT listen error: " + e.getMessage());
            }
        }).start();
    }

    public void stopListening() {
        listeningActive = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
    }
}
