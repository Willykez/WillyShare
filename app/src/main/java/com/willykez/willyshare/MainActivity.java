package com.willykez.willyshare;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── Hero action cards ─────────────────────────────────────────────
        LinearLayout cardSend    = findViewById(R.id.cardSend);
        LinearLayout cardReceive = findViewById(R.id.cardReceive);

        cardSend.setAlpha(0f);
        cardReceive.setAlpha(0f);
        cardSend.postDelayed(() -> AnimUtils.slideUp(cardSend), 80);
        cardReceive.postDelayed(() -> AnimUtils.slideUp(cardReceive), 200);

        // Logo bell pulse
        TextView tvLogo = findViewById(R.id.tvLogo);
        AnimUtils.pulse(tvLogo);

        cardSend.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, ScanActivity.class).putExtra("mode", "send"));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }, 120);
        });

        cardReceive.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, ScanActivity.class).putExtra("mode", "receive"));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }, 120);
        });

        // ── Browse by type (all open picker with a type filter intent) ────
        int[] typeIds = {R.id.typePhotos, R.id.typeVideos, R.id.typeMusic,
                R.id.typeApps, R.id.typeFiles};
        String[] mimeTypes = {"image/*", "video/*", "audio/*",
                "application/vnd.android.package-archive", "*/*"};

        for (int i = 0; i < typeIds.length; i++) {
            final String mime = mimeTypes[i];
            LinearLayout chip = findViewById(typeIds[i]);
            if (chip == null) continue;
            chip.setOnClickListener(v -> {
                AnimUtils.buttonPress(v);
                // Direct system picker for this mime type — no remote IP yet,
                // use FilePickerActivity which launches its own picker
                v.postDelayed(() -> {
                    Intent pick = new Intent(this, FilePickerActivity.class);
                    pick.putExtra("mimeFilter", mime);
                    pick.putExtra("remoteIp", HotspotManager.HOST_IP);
                    startActivity(pick);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }, 120);
            });
        }

        // ── Bottom nav ────────────────────────────────────────────────────
        TextView cardHistory = findViewById(R.id.cardHistory); // "VIEW ALL" text
        cardHistory.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }, 120);
        });

        LinearLayout navHistory = findViewById(R.id.navHistory);
        if (navHistory != null) {
            navHistory.setOnClickListener(v -> {
                AnimUtils.buttonPress(v);
                v.postDelayed(() -> {
                    startActivity(new Intent(this, HistoryActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }, 120);
            });
        }

        LinearLayout navDiscovery = findViewById(R.id.navDiscovery);
        if (navDiscovery != null) {
            navDiscovery.setOnClickListener(v -> {
                AnimUtils.buttonPress(v);
                v.postDelayed(() -> {
                    startActivity(new Intent(this, ScanActivity.class).putExtra("mode", "send"));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }, 120);
            });
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
