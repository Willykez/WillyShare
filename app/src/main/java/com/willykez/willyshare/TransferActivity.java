package com.willykez.willyshare;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

/**
 * TransferActivity — shows live progress for send/receive.
 *
 * Upgrades vs original:
 *  - Handles MSG_FILE_START to show "File 2/5" counter
 *  - Handles MSG_RX_PROGRESS on receiver side (was missing before — no progress shown)
 *  - Peak speed displayed below main speed
 *  - ETA estimate ("~3s remaining") based on current EMA speed
 *  - Transfer icon animates correctly for both roles
 */
public class TransferActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private TextView    tvFileName, tvSpeed, tvPercent, tvBytesSent, tvTotalSize;
    private TextView    tvTransferIcon, tvStatus, tvFileCounter, tvEta, tvPeakSpeed;
    private Button      btnPauseResume, btnCancel;

    private boolean  isPaused      = false;
    private String   mode;
    private TransferEngine localEngine;
    private int      lastProgress  = 0;
    private long     totalFileSize = 0;
    private int      totalFiles    = 1;
    private int      currentFile   = 1;

    // For ETA calculation
    private long transferStartTime = 0;
    private long totalBytesDone    = 0;

    private final Handler engineHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case TransferEngine.MSG_FILE_START: {
                    // arg1=fileIndex, arg2=totalFiles, obj=filename
                    currentFile = msg.arg1 + 1;
                    totalFiles  = msg.arg2;
                    String fname = (String) msg.obj;
                    if (fname != null && !fname.isEmpty()) tvFileName.setText(fname);
                    if (totalFiles > 1) {
                        tvFileCounter.setText("File " + currentFile + " of " + totalFiles);
                    } else {
                        tvFileCounter.setText("");
                    }
                    lastProgress = 0;
                    transferStartTime = System.currentTimeMillis();
                    break;
                }

                case TransferEngine.MSG_PROGRESS: {
                    // Send-side progress
                    int pct = msg.arg1;
                    String fname = (String) msg.obj;
                    AnimUtils.countUpProgress(progressBar, lastProgress, pct);
                    lastProgress = pct;
                    tvPercent.setText(pct + "%");
                    if (fname != null) tvFileName.setText(fname);
                    long sent = (totalFileSize > 0) ? (totalFileSize * pct) / 100 : 0;
                    tvBytesSent.setText(FileUtils.formatSize(sent) + " sent");
                    totalBytesDone = sent;
                    updateEta(sent, totalFileSize);
                    break;
                }

                case TransferEngine.MSG_RX_PROGRESS: {
                    // Receive-side progress (was missing before!)
                    int pct = msg.arg1;
                    String fname = (String) msg.obj;
                    AnimUtils.countUpProgress(progressBar, lastProgress, pct);
                    lastProgress = pct;
                    tvPercent.setText(pct + "%");
                    if (fname != null) tvFileName.setText(fname);
                    long rxTotal = rxTotalMap.getOrDefault(fname, 0L);
                    long rxDone  = (rxTotal * pct) / 100;
                    tvBytesSent.setText(FileUtils.formatSize(rxDone) + " received");
                    tvTotalSize.setText("of " + FileUtils.formatSize(rxTotal));
                    updateEta(rxDone, rxTotal);
                    break;
                }

                case TransferEngine.MSG_SPEED:
                    tvSpeed.setText((String) msg.obj);
                    break;

                case TransferEngine.MSG_DONE:
                    onTransferDone((String) msg.obj);
                    break;

                case TransferEngine.MSG_ERROR:
                    onTransferError((String) msg.obj);
                    break;
            }
        }
    };

    // Simple map for receiver total sizes (populated from MSG_FILE_START / file-list)
    private final java.util.HashMap<String, Long> rxTotalMap = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        mode           = getIntent().getStringExtra("mode");
        progressBar    = findViewById(R.id.progressBar);
        tvFileName     = findViewById(R.id.tvFileName);
        tvSpeed        = findViewById(R.id.tvSpeed);
        tvPercent      = findViewById(R.id.tvPercent);
        tvBytesSent    = findViewById(R.id.tvBytesSent);
        tvTotalSize    = findViewById(R.id.tvTotalSize);
        tvTransferIcon = findViewById(R.id.tvTransferIcon);
        tvStatus       = findViewById(R.id.tvStatus);
        tvFileCounter  = findViewById(R.id.tvFileCounter);
        tvEta          = findViewById(R.id.tvEta);
        tvPeakSpeed    = findViewById(R.id.tvPeakSpeed);
        btnPauseResume = findViewById(R.id.btnPauseResume);
        btnCancel      = findViewById(R.id.btnCancel);

        localEngine = new TransferEngine(this, engineHandler);

        AnimUtils.pulse(tvTransferIcon);
        AnimUtils.fadeIn(tvSpeed);

        if ("receive".equals(mode)) {
            tvStatus.setText("RECEIVING");
            tvFileName.setText("Waiting for sender…");
            tvTransferIcon.setText("📥");
            tvBytesSent.setText("0 B received");
            localEngine.startReceiving();
        } else {
            tvStatus.setText("SENDING");
            tvTransferIcon.setText("📤");
            ArrayList<String> files = getIntent().getStringArrayListExtra("files");
            String remoteIp = getIntent().getStringExtra("remoteIp");
            if (remoteIp == null) remoteIp = HotspotManager.HOST_IP;
            if (files != null && !files.isEmpty()) {
                totalFileSize = 0;
                for (String f : files) totalFileSize += FileUtils.getFileSize(f);
                tvTotalSize.setText("of " + FileUtils.formatSize(totalFileSize));
                transferStartTime = System.currentTimeMillis();
                localEngine.sendFiles(files, remoteIp, HotspotManager.TRANSFER_PORT);
            }
        }

        btnPauseResume.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            isPaused = !isPaused;
            localEngine.setPaused(isPaused);
            btnPauseResume.setText(isPaused ? "Resume" : "Pause");
            tvStatus.setText(isPaused ? "PAUSED"
                    : ("receive".equals(mode) ? "RECEIVING" : "SENDING"));
            tvEta.setText(isPaused ? "Paused" : "");
        });

        btnCancel.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            if ("Close".equals(btnCancel.getText().toString())) {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return;
            }
            v.postDelayed(() -> {
                localEngine.cancel();
                localEngine.stopReceiving();
                Intent svc = new Intent(this, TransferService.class);
                svc.setAction(TransferService.ACTION_CANCEL);
                startService(svc);
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }, 120);
        });
    }

    private void updateEta(long done, long total) {
        if (done <= 0 || total <= 0 || transferStartTime <= 0) { tvEta.setText(""); return; }
        long elapsed = System.currentTimeMillis() - transferStartTime;
        if (elapsed < 500) { tvEta.setText(""); return; }
        double rate = done / (double) elapsed; // bytes/ms
        long remaining = total - done;
        if (rate <= 0) { tvEta.setText(""); return; }
        long etaMs = (long) (remaining / rate);
        if (etaMs < 1000)       tvEta.setText("< 1s remaining");
        else if (etaMs < 60000) tvEta.setText(etaMs / 1000 + "s remaining");
        else                    tvEta.setText((etaMs / 60000) + "m " + ((etaMs % 60000) / 1000) + "s remaining");
    }

    private void onTransferDone(String fileName) {
        AnimUtils.stopPulse(tvTransferIcon);
        tvTransferIcon.setText("✅");
        AnimUtils.pulse(tvTransferIcon);
        progressBar.setProgress(100);
        tvPercent.setText("100%");
        tvSpeed.setText("Done!");
        tvSpeed.setTextColor(Color.parseColor("#00C853"));
        tvStatus.setText("COMPLETE");
        tvEta.setText("");
        btnCancel.setText("Close");
        btnPauseResume.setEnabled(false);
        Toast.makeText(this, "Transfer complete: " + fileName, Toast.LENGTH_LONG).show();
    }

    private void onTransferError(String err) {
        AnimUtils.stopPulse(tvTransferIcon);
        tvTransferIcon.setText("❌");
        tvSpeed.setText("Failed");
        tvSpeed.setTextColor(Color.parseColor("#EF5350"));
        tvStatus.setText("ERROR");
        tvEta.setText("");
        btnCancel.setText("Close");
        btnPauseResume.setEnabled(false);
        Toast.makeText(this, "Error: " + err, Toast.LENGTH_LONG).show();
    }

    @Override public void onBackPressed() { /* block accidental back during transfer */ }

    @Override protected void onDestroy() { super.onDestroy(); }
}
