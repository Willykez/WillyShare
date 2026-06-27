package com.willykez.willyshare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PermissionActivity extends AppCompatActivity {
    private static final int REQ_PERMS = 101;
    private static final int REQ_MANAGE_STORAGE = 102;

    /**
     * FIX: userHasInteracted prevents auto-proceed on the very first onCreate
     * before the user has done anything.
     */
    private boolean userHasInteracted = false;

    // label -> permission string[]
    private final LinkedHashMap<String, String[]> permGroups = new LinkedHashMap<>();
    private final Map<String, TextView> statusViews = new LinkedHashMap<>();
    private final Map<String, TextView> iconViews   = new LinkedHashMap<>();

    /**
     * FIX: Track whether MANAGE_EXTERNAL_STORAGE is one of the groups we show.
     * On API 30 it always applies; on API 31-32 MANAGE_EXTERNAL_STORAGE was
     * deprecated in favour of READ_MEDIA_* (TIRAMISU), so we must NOT require
     * isExternalStorageManager() on those API levels — doing so caused an
     * infinite permission loop because the flag can never become true without
     * a special Settings intent the app never showed.
     */
    private boolean requiresManageStorage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        buildPermGroups();

        LinearLayout container = findViewById(R.id.permContainer);
        buildPermRows(container);

        Button btnGrant = findViewById(R.id.btnGrantAll);
        Button btnSkip  = findViewById(R.id.btnContinueAnyway);

        btnGrant.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            userHasInteracted = true;
            requestMissingPermissions();
        });

        btnSkip.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            proceed();
        });

        AnimUtils.staggerChildren(container);
        // First paint — never auto-proceed
        refreshStatuses(false);
    }

    private void buildPermGroups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — granular media permissions replace MANAGE_EXTERNAL_STORAGE
            permGroups.put("Media & Files", new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            });
            permGroups.put("Notifications", new String[]{
                    Manifest.permission.POST_NOTIFICATIONS
            });
            requiresManageStorage = false;  // not needed on 13+

        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            // Android 11 only — MANAGE_EXTERNAL_STORAGE is the right path
            permGroups.put("Storage (All Files)", new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
            requiresManageStorage = true;

        } else {
            // Android 8-10 — classic READ/WRITE
            permGroups.put("Storage", new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
            requiresManageStorage = false;
        }

        permGroups.put("Location (for Wi-Fi scan)", new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permGroups.put("Bluetooth", new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            });
        } else {
            permGroups.put("Bluetooth", new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            });
        }

        // Android 13+ requires NEARBY_WIFI_DEVICES for WiFi Direct peer discovery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permGroups.put("Nearby Devices (Wi-Fi Direct)", new String[]{
                    "android.permission.NEARBY_WIFI_DEVICES"
            });
        }
    }

    private static final String[] GROUP_EMOJIS = {"📁", "🔔", "📍", "📶"};

    private void buildPermRows(LinearLayout container) {
        container.removeAllViews();
        int idx = 0;
        for (Map.Entry<String, String[]> entry : permGroups.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dpToPx(10);
            row.setLayoutParams(rp);
            row.setBackgroundResource(R.drawable.bg_perm_item);
            row.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

            TextView icon = new TextView(this);
            icon.setText(idx < GROUP_EMOJIS.length ? GROUP_EMOJIS[idx] : "🔵");
            icon.setTextSize(22);
            icon.setGravity(Gravity.CENTER);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)));
            iconViews.put(entry.getKey(), icon);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tcp.setMarginStart(dpToPx(14));
            textCol.setLayoutParams(tcp);

            TextView label = new TextView(this);
            label.setText(entry.getKey());
            label.setTextColor(resolveTextColor());
            label.setTextSize(14.5f);
            textCol.addView(label);

            TextView subLabel = new TextView(this);
            subLabel.setText(permissionSubtitle(entry.getKey()));
            subLabel.setTextColor(Color.parseColor("#4A6D8F"));
            subLabel.setTextSize(11.5f);
            subLabel.setPadding(0, dpToPx(2), 0, 0);
            textCol.addView(subLabel);

            TextView status = new TextView(this);
            status.setTextSize(13);
            status.setGravity(Gravity.CENTER);
            status.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)));
            statusViews.put(entry.getKey(), status);

            row.addView(icon);
            row.addView(textCol);
            row.addView(status);
            container.addView(row);
            idx++;
        }
    }

    private int resolveTextColor() {
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                ? Color.WHITE : Color.parseColor("#0D1B2A");
    }

    private String permissionSubtitle(String key) {
        if (key.contains("Media") || key.contains("Storage")) return "Required to pick files";
        if (key.contains("Notification"))                       return "Transfer progress alerts";
        if (key.contains("Location"))                          return "Needed for Wi-Fi P2P scanning";
        if (key.contains("Bluetooth"))                         return "Device discovery & pairing";
        return "Required by WillyShare";
    }

    /**
     * Refresh the UI status dots.
     * @param autoProceed  true → navigate to MainActivity if everything is granted.
     */
    private void refreshStatuses(boolean autoProceed) {
        boolean allOk = true;

        for (Map.Entry<String, String[]> entry : permGroups.entrySet()) {
            boolean groupGranted = true;
            for (String perm : entry.getValue()) {
                if (ContextCompat.checkSelfPermission(this, perm)
                        != PackageManager.PERMISSION_GRANTED) {
                    groupGranted = false;
                    allOk = false;
                    break;
                }
            }

            TextView tv   = statusViews.get(entry.getKey());
            TextView icon = iconViews.get(entry.getKey());
            if (tv != null) {
                tv.setText(groupGranted ? "✓" : "○");
                tv.setTextColor(groupGranted
                        ? Color.parseColor("#00C853")
                        : Color.parseColor("#EF5350"));
            }
            if (icon != null) {
                icon.setAlpha(groupGranted ? 1f : 0.45f);
            }
        }

        // Only check MANAGE_EXTERNAL_STORAGE if actually required on this API level
        if (requiresManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean storageOk = Environment.isExternalStorageManager();
            if (!storageOk) allOk = false;
            // Update the storage row status
            for (String key : permGroups.keySet()) {
                if (key.contains("Storage") || key.contains("Files")) {
                    TextView tv   = statusViews.get(key);
                    TextView icon = iconViews.get(key);
                    if (tv != null) {
                        tv.setText(storageOk ? "✓" : "○");
                        tv.setTextColor(storageOk
                                ? Color.parseColor("#00C853")
                                : Color.parseColor("#EF5350"));
                    }
                    if (icon != null) icon.setAlpha(storageOk ? 1f : 0.45f);
                    break;
                }
            }
        }

        if (autoProceed && allOk) {
            proceed();
        }
    }

    private void requestMissingPermissions() {
        // MANAGE_EXTERNAL_STORAGE needs a Settings intent on Android 11
        if (requiresManageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_MANAGE_STORAGE);
                return;
            } catch (Exception e) {
                startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                        REQ_MANAGE_STORAGE);
                return;
            }
        }

        List<String> missing = new ArrayList<>();
        for (String[] perms : permGroups.values()) {
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p)
                        != PackageManager.PERMISSION_GRANTED) {
                    missing.add(p);
                }
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missing.toArray(new String[0]), REQ_PERMS);
        } else {
            proceed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refreshStatuses(true);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MANAGE_STORAGE) {
            refreshStatuses(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only auto-proceed on resume if user already interacted (came back from system dialog)
        refreshStatuses(userHasInteracted);
    }

    private void proceed() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
