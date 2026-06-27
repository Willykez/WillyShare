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

        LinearLayout cardSend    = findViewById(R.id.cardSend);
        LinearLayout cardReceive = findViewById(R.id.cardReceive);
        LinearLayout cardHistory = findViewById(R.id.cardHistory);
        TextView     tvLogo      = findViewById(R.id.tvLogo);

        /**
         * FIX: Previously the code called AnimUtils.slideUp(cardSend) — which
         * sets alpha=1 and starts the animator — then immediately reset alpha to
         * 0f on the very next line, fighting the running animator and causing
         * a flicker / invisible card on the first frame.
         *
         * Correct approach: set all cards invisible FIRST, then kick off each
         * slideUp() via postDelayed() so no conflicting state assignment happens
         * after the animator has started.
         */
        cardSend.setAlpha(0f);
        cardReceive.setAlpha(0f);
        cardHistory.setAlpha(0f);

        cardSend.postDelayed(()    -> AnimUtils.slideUp(cardSend),    80);
        cardReceive.postDelayed(() -> AnimUtils.slideUp(cardReceive), 200);
        cardHistory.postDelayed(() -> AnimUtils.slideUp(cardHistory), 320);

        // Logo pulse — uses the fixed AnimatorSet-based pulse
        AnimUtils.pulse(tvLogo);

        cardSend.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, ScanActivity.class).putExtra("mode", "send"));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }, 120);
        });

        cardReceive.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);;
            v.postDelayed(() -> {
                startActivity(new Intent(this, ScanActivity.class).putExtra("mode", "receive"));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }, 120);
        });

        cardHistory.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }, 120);
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
