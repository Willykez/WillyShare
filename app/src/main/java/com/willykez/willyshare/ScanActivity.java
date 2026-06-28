package com.willykez.willyshare;

import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends AppCompatActivity {

    private TextView      tvScanTitle, tvScanStatus, tvRadarIcon, tvThisDeviceName;
    private Button        btnRefresh;
    private ListView      lvDevices;
    private View          receiveWaitCard;
    private DeviceAdapter adapter;

    private final List<WifiP2pDevice> deviceList = new ArrayList<>();
    private DeviceDiscoveryManager    discoveryManager;
    private String                    mode;
    // once we've launched the next screen, ignore any further connection events
    private boolean                   launched = false;

    private final Handler discoveryHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {

                case DeviceDiscoveryManager.MSG_DEVICES_UPDATED:
                    //noinspection unchecked
                    List<WifiP2pDevice> peers = (List<WifiP2pDevice>) msg.obj;
                    deviceList.clear();
                    deviceList.addAll(peers);
                    adapter.notifyDataSetChanged();
                    updateStatus();
                    break;

                case DeviceDiscoveryManager.MSG_CONNECTED:
                    if (!launched) handleConnected((WifiP2pInfo) msg.obj);
                    break;

                case DeviceDiscoveryManager.MSG_CONNECT_FAILED:
                    Toast.makeText(ScanActivity.this,
                            "Connection failed — try again", Toast.LENGTH_SHORT).show();
                    if (btnRefresh != null) btnRefresh.setEnabled(true);
                    break;

                case DeviceDiscoveryManager.MSG_DISCOVERY_STARTED:
                    setScanning(true);
                    break;

                case DeviceDiscoveryManager.MSG_DISCOVERY_STOPPED:
                    setScanning(false);
                    break;

                case DeviceDiscoveryManager.MSG_THIS_DEVICE:
                    WifiP2pDevice me = (WifiP2pDevice) msg.obj;
                    if (tvThisDeviceName != null && me != null)
                        tvThisDeviceName.setText(me.deviceName);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mode = getIntent().getStringExtra("mode");
        // isSender=true → groupOwnerIntent=0 for sender; false → 15 for receiver
        discoveryManager = new DeviceDiscoveryManager(this, discoveryHandler,
                "send".equals(mode));

        tvScanTitle      = findViewById(R.id.tvScanTitle);
        tvScanStatus     = findViewById(R.id.tvScanStatus);
        tvRadarIcon      = findViewById(R.id.tvRadarIcon);
        tvThisDeviceName = findViewById(R.id.tvThisDeviceName);
        btnRefresh       = findViewById(R.id.btnRefresh);
        lvDevices        = findViewById(R.id.lvDevices);
        receiveWaitCard  = findViewById(R.id.receiveWaitCard);

        adapter = new DeviceAdapter();
        lvDevices.setAdapter(adapter);
        AnimUtils.pulse(tvRadarIcon);

        if ("send".equals(mode)) setupSend();
        else                     setupReceive();
    }

    // ── SEND ──────────────────────────────────────────────────────────────────

    private void setupSend() {
        tvScanTitle.setText("Find a Device");
        receiveWaitCard.setVisibility(View.GONE);
        lvDevices.setVisibility(View.VISIBLE);
        if (btnRefresh != null) btnRefresh.setVisibility(View.VISIBLE);

        btnRefresh.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            deviceList.clear();
            adapter.notifyDataSetChanged();
            tvScanStatus.setText("Scanning…");
            discoveryManager.startScan();
        });

        lvDevices.setOnItemClickListener((parent, view, pos, id) -> {
            WifiP2pDevice dev = deviceList.get(pos);
            AnimUtils.buttonPress(view);
            tvScanStatus.setText("Connecting to " + dev.deviceName + "…");
            btnRefresh.setEnabled(false);
            discoveryManager.connectToDevice(dev);
        });

        discoveryManager.startScan();
    }

    // ── RECEIVE ───────────────────────────────────────────────────────────────

    private void setupReceive() {
        tvScanTitle.setText("Waiting for Sender");
        receiveWaitCard.setVisibility(View.VISIBLE);
        lvDevices.setVisibility(View.GONE);
        if (btnRefresh != null) btnRefresh.setVisibility(View.GONE);
        tvScanStatus.setText("You're visible — waiting…");
        discoveryManager.startReceiving();
    }

    // ── P2P connected ─────────────────────────────────────────────────────────

    private void handleConnected(WifiP2pInfo info) {
        launched = true; // prevent double-launch

        // The Group Owner always gets 192.168.49.1.
        // Sender is always client → connects TO that address.
        // Receiver is always GO → listens ON that address.
        String remoteIp = (info.groupOwnerAddress != null)
                ? info.groupOwnerAddress.getHostAddress()
                : HotspotManager.HOST_IP;

        if ("send".equals(mode)) {
            // Sender: go to file picker so user can choose files
            Toast.makeText(this, "Connected — pick files to send", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra("remoteIp", remoteIp);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            // Don't finish() — user may press back from picker to re-select device
        } else {
            // Receiver: go straight to transfer screen to start listening
            Toast.makeText(this, "Sender connected!", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, TransferActivity.class);
            i.putExtra("mode", "receive");
            i.putExtra("remoteIp", remoteIp);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish(); // receiver doesn't need to come back to scan
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setScanning(boolean on) {
        if (on) {
            tvScanStatus.setText("Scanning for devices…");
            AnimUtils.pulse(tvRadarIcon);
            if (btnRefresh != null) btnRefresh.setEnabled(false);
        } else {
            AnimUtils.stopPulse(tvRadarIcon);
            if ("send".equals(mode)) updateStatus();
            if (btnRefresh != null) btnRefresh.setEnabled(true);
        }
    }

    private void updateStatus() {
        if ("send".equals(mode)) {
            tvScanStatus.setText(deviceList.isEmpty()
                    ? "No devices found — tap Refresh"
                    : deviceList.size() + " device(s) nearby");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        launched = false; // allow re-connection if user comes back to this screen
        discoveryManager.register();
    }

    @Override protected void onPause()   { super.onPause();   discoveryManager.unregister(); }
    @Override protected void onDestroy() { super.onDestroy(); discoveryManager.stopScan();   }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        discoveryManager.disconnect();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Device list adapter ───────────────────────────────────────────────────

    private class DeviceAdapter extends BaseAdapter {
        @Override public int getCount()              { return deviceList.size(); }
        @Override public WifiP2pDevice getItem(int p){ return deviceList.get(p); }
        @Override public long getItemId(int p)       { return p; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) cv = LayoutInflater.from(ScanActivity.this)
                    .inflate(R.layout.item_device, parent, false);

            WifiP2pDevice dev   = deviceList.get(pos);
            TextView tvName     = cv.findViewById(R.id.tvDeviceName);
            TextView tvAddr     = cv.findViewById(R.id.tvDeviceAddr);
            TextView tvBadge    = cv.findViewById(R.id.tvDeviceBadge);

            tvName.setText((dev.deviceName != null && !dev.deviceName.isEmpty())
                    ? dev.deviceName : "Unknown Device");
            tvAddr.setText(dev.deviceAddress);

            if (tvBadge != null) {
                switch (dev.status) {
                    case WifiP2pDevice.CONNECTED:
                        tvBadge.setText("Connected");
                        tvBadge.setVisibility(View.VISIBLE);
                        break;
                    case WifiP2pDevice.AVAILABLE:
                        tvBadge.setText("Available");
                        tvBadge.setVisibility(View.VISIBLE);
                        break;
                    default:
                        tvBadge.setVisibility(View.GONE);
                }
            }

            cv.setAlpha(0f);
            cv.setTranslationX(40f);
            cv.animate().alpha(1f).translationX(0f)
                    .setStartDelay(pos * 55L).setDuration(260).start();
            return cv;
        }
    }
}
