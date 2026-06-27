package com.willykez.willyshare;

import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

/**
 * ScanActivity — QuickShare-style device discovery.
 *
 * SEND mode:
 *   - Scans for nearby WiFi Direct peers
 *   - Shows animated device list as peers appear
 *   - User taps a device → P2P connect → goes to FilePickerActivity
 *
 * RECEIVE mode:
 *   - Shows "Waiting — you're visible to senders" state
 *   - No device list (receiver doesn't need to pick anyone)
 *   - When a sender connects → auto-navigates to TransferActivity
 *
 * Both modes use WiFi Direct (WifiP2pManager) instead of Bluetooth for
 * fast discovery and a 5 GHz direct link.
 */
public class ScanActivity extends AppCompatActivity {

    private TextView      tvScanTitle, tvScanStatus, tvRadarIcon, tvThisDeviceName;
    private Button        btnRefresh;
    private ListView      lvDevices;
    private View          receiveWaitCard;
    private DeviceAdapter adapter;

    private final List<WifiP2pDevice> deviceList = new ArrayList<>();
    private DeviceDiscoveryManager discoveryManager;
    private String mode;

    private final Handler discoveryHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DeviceDiscoveryManager.MSG_DEVICES_UPDATED:
                    //noinspection unchecked
                    List<WifiP2pDevice> peers = (List<WifiP2pDevice>) msg.obj;
                    deviceList.clear();
                    deviceList.addAll(peers);
                    adapter.notifyDataSetChanged();
                    updateScanStatus();
                    break;

                case DeviceDiscoveryManager.MSG_CONNECTED:
                    handleP2pConnected((WifiP2pInfo) msg.obj);
                    break;

                case DeviceDiscoveryManager.MSG_CONNECT_FAILED:
                    Toast.makeText(ScanActivity.this,
                            "Connection failed — try again", Toast.LENGTH_SHORT).show();
                    btnRefresh.setEnabled(true);
                    break;

                case DeviceDiscoveryManager.MSG_DISCOVERY_STARTED:
                    setScanning(true);
                    break;

                case DeviceDiscoveryManager.MSG_DISCOVERY_STOPPED:
                    setScanning(false);
                    break;

                case DeviceDiscoveryManager.MSG_THIS_DEVICE:
                    WifiP2pDevice me = (WifiP2pDevice) msg.obj;
                    if (tvThisDeviceName != null && me != null) {
                        tvThisDeviceName.setText("This device: " + me.deviceName);
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mode = getIntent().getStringExtra("mode");
        discoveryManager = new DeviceDiscoveryManager(this, discoveryHandler);

        // Views
        tvScanTitle       = findViewById(R.id.tvScanTitle);
        tvScanStatus      = findViewById(R.id.tvScanStatus);
        tvRadarIcon       = findViewById(R.id.tvRadarIcon);
        tvThisDeviceName  = findViewById(R.id.tvThisDeviceName);
        btnRefresh        = findViewById(R.id.btnRefresh);
        lvDevices         = findViewById(R.id.lvDevices);
        receiveWaitCard   = findViewById(R.id.receiveWaitCard);

        adapter = new DeviceAdapter();
        lvDevices.setAdapter(adapter);

        AnimUtils.pulse(tvRadarIcon);

        if ("send".equals(mode)) {
            setupSendMode();
        } else {
            setupReceiveMode();
        }
    }

    // ─── SEND mode ────────────────────────────────────────────────────────────

    private void setupSendMode() {
        tvScanTitle.setText("Find a Device");
        if (receiveWaitCard != null) receiveWaitCard.setVisibility(View.GONE);
        lvDevices.setVisibility(View.VISIBLE);

        btnRefresh.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            deviceList.clear();
            adapter.notifyDataSetChanged();
            discoveryManager.startScan();
        });

        lvDevices.setOnItemClickListener((parent, view, pos, id) -> {
            WifiP2pDevice device = deviceList.get(pos);
            AnimUtils.buttonPress(view);
            tvScanStatus.setText("Connecting to " + device.deviceName + "…");
            btnRefresh.setEnabled(false);
            discoveryManager.connectToDevice(device);
        });

        // Auto-start scan
        discoveryManager.startScan();
    }

    // ─── RECEIVE mode ─────────────────────────────────────────────────────────

    private void setupReceiveMode() {
        tvScanTitle.setText("Waiting for Sender");
        if (receiveWaitCard != null) receiveWaitCard.setVisibility(View.VISIBLE);
        lvDevices.setVisibility(View.GONE);
        btnRefresh.setVisibility(View.GONE);

        tvScanStatus.setText("You're visible — waiting for a device to connect…");

        // Start TransferService in receive mode so it's ready for data
        Intent svc = new Intent(this, TransferService.class);
        svc.setAction(TransferService.ACTION_RECEIVE);
        startForegroundService(svc);

        // P2P: become discoverable and wait
        discoveryManager.startReceiving();
    }

    // ─── P2P Connected ────────────────────────────────────────────────────────

    private void handleP2pConnected(WifiP2pInfo info) {
        String remoteIp;

        if (info.isGroupOwner) {
            // We are the Group Owner — remote client is at a DHCP-assigned IP.
            // We can't know the exact IP here; use the standard P2P GO address.
            // The sender typically is NOT the GO; swap roles: receiver is GO.
            // In practice: receiver starts as GO; sender connects as client.
            // The sender's IP is not in WifiP2pInfo for the GO side.
            // We use the receiver-is-GO pattern: receiver knows its own IP (192.168.49.1)
            // The TransferEngine on the sender side connects OUT to us.
            // So receiver just starts receiving on PORT — sender already knows GO IP.
            remoteIp = info.groupOwnerAddress != null
                    ? info.groupOwnerAddress.getHostAddress()
                    : HotspotManager.HOST_IP;
        } else {
            // We are the client — group owner IP is the remote host
            remoteIp = info.groupOwnerAddress != null
                    ? info.groupOwnerAddress.getHostAddress()
                    : HotspotManager.HOST_IP;
        }

        if ("send".equals(mode)) {
            // Sender: now we know the receiver's IP (group owner addr), go pick files
            Toast.makeText(this, "Connected! Pick files to send.", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra("remoteIp", remoteIp);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else {
            // Receiver: sender connected — go to transfer screen
            Toast.makeText(this, "Sender connected!", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, TransferActivity.class);
            i.putExtra("mode", "receive");
            i.putExtra("remoteIp", remoteIp);
            i.putExtra("port", HotspotManager.TRANSFER_PORT);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private void setScanning(boolean scanning) {
        if (scanning) {
            tvScanStatus.setText("Scanning for devices…");
            AnimUtils.pulse(tvRadarIcon);
            btnRefresh.setEnabled(false);
        } else {
            AnimUtils.stopPulse(tvRadarIcon);
            if ("send".equals(mode)) {
                tvScanStatus.setText(deviceList.isEmpty()
                        ? "No devices found — tap Refresh"
                        : deviceList.size() + " device(s) found");
            }
            btnRefresh.setEnabled(true);
        }
    }

    private void updateScanStatus() {
        if ("send".equals(mode)) {
            tvScanStatus.setText(deviceList.isEmpty()
                    ? "No devices found — tap Refresh"
                    : deviceList.size() + " device(s) found nearby");
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override protected void onResume()  { super.onResume();  discoveryManager.register(); }
    @Override protected void onPause()   { super.onPause();   discoveryManager.unregister(); }
    @Override protected void onDestroy() { super.onDestroy(); discoveryManager.stopScan(); }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        discoveryManager.disconnect();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ─── Device list adapter ─────────────────────────────────────────────────

    private class DeviceAdapter extends BaseAdapter {
        @Override public int getCount()               { return deviceList.size(); }
        @Override public WifiP2pDevice getItem(int p) { return deviceList.get(p); }
        @Override public long getItemId(int p)        { return p; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ScanActivity.this)
                        .inflate(R.layout.item_device, parent, false);
            }
            WifiP2pDevice dev = deviceList.get(pos);
            TextView tvName = convertView.findViewById(R.id.tvDeviceName);
            TextView tvAddr = convertView.findViewById(R.id.tvDeviceAddr);
            TextView tvBadge= convertView.findViewById(R.id.tvDeviceBadge);

            tvName.setText(dev.deviceName != null && !dev.deviceName.isEmpty()
                    ? dev.deviceName : "Unknown Device");
            tvAddr.setText(dev.deviceAddress);

            // Status badge
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
                        break;
                }
            }

            // Stagger entrance animation
            convertView.setAlpha(0f);
            convertView.setTranslationX(40f);
            convertView.animate().alpha(1f).translationX(0f)
                    .setStartDelay(pos * 60L).setDuration(280).start();

            return convertView;
        }
    }
}
