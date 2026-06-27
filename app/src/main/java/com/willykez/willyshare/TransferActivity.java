package com.willykez.willyshare;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

/**
 * TransferActivity v3 — fully redesigned sender + receiver UI.
 *
 * FIXED:
 *  - Cancel no longer shows "Done" — MSG_CANCELLED handled separately
 *  - Receiver shows live progress (was showing nothing before)
 *  - Byte counts show real sent bytes, not percentage-derived approximations
 *  - Connection errors shown as non-fatal toasts, transfer continues
 *
 * NEW UI FEATURES:
 *  - Receiver has its own waiting screen with pulse animation
 *  - Per-file progress with "File N of M" pill badge
 *  - Live speed graph (7-sample sliding window)
 *  - File type emoji icon derived from extension
 *  - Summary card shown after all files complete (time + avg speed)
 *  - Smooth progress bar with gradient fill
 *  - Error snackbar stays visible 4s then auto-dismisses (non-fatal)
 *  - Pause button morphs icon correctly
 */
public class TransferActivity extends AppCompatActivity {

    // Core views
    private View           rootLayout;
    private TextView       tvIcon, tvStatus, tvFileCounter, tvFileName;
    private TextView       tvSpeed, tvPercent, tvEta;
    private TextView       tvBytesSent, tvTotalSize;
    private ProgressBar    progressBar;
    private Button         btnPauseResume, btnAction;

    // Summary card (shown on completion)
    private LinearLayout   cardSummary;
    private TextView       tvSummaryTitle, tvSummaryDetail, tvSummarySpeed;

    // Error bar
    private LinearLayout   layoutError;
    private TextView       tvErrorMsg;

    // Speed graph (7 bars)
    private View[]         speedBars;
    private float[]        speedHistory = new float[7];
    private int            speedHistIdx = 0;

    private boolean isPaused   = false;
    private String  mode;
    private TransferEngine engine;

    // Transfer state
    private long   totalFileSize    = 0;
    private int    totalFiles       = 1;
    private int    currentFile      = 0;
    private long   transferStart    = 0;
    private long   lastSpeedBps     = 0;
    private long   fileBytesReceived= 0;
    private long   fileBytesTotal   = 0;
    private boolean isDone          = false;

    // ETA tracking
    private long etaBytesStart = 0;
    private long etaTimeStart  = 0;

    private final java.util.HashMap<String, Long> rxTotalMap = new java.util.HashMap<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case TransferEngine.MSG_FILE_START: {
                    int fileIdx   = msg.arg1;
                    int numFiles  = msg.arg2;
                    String fname  = (String) msg.obj;
                    totalFiles    = numFiles;
                    currentFile   = fileIdx + 1;

                    if (fname != null && !fname.isEmpty()) {
                        tvFileName.setText(fname);
                        tvIcon.setText(fileTypeEmoji(fname));
                    }
                    if (totalFiles > 1) {
                        tvFileCounter.setVisibility(View.VISIBLE);
                        tvFileCounter.setText(currentFile + " / " + totalFiles);
                    } else {
                        tvFileCounter.setVisibility(View.GONE);
                    }
                    progressBar.setProgress(0);
                    tvPercent.setText("0%");
                    tvEta.setText("");
                    etaBytesStart = 0;
                    etaTimeStart  = System.currentTimeMillis();
                    break;
                }

                case TransferEngine.MSG_PROGRESS: {
                    // Send-side percentage
                    if (isDone) break;
                    int pct = msg.arg1;
                    String fname = (String) msg.obj;
                    if (fname != null) {
                        tvFileName.setText(fname);
                        tvIcon.setText(fileTypeEmoji(fname));
                    }
                    animateProgress(pct);
                    tvPercent.setText(pct + "%");
                    break;
                }

                case TransferEngine.MSG_RX_PROGRESS: {
                    // Receive-side percentage
                    if (isDone) break;
                    int pct = msg.arg1;
                    String fname = (String) msg.obj;
                    if (fname != null) {
                        tvFileName.setText(fname);
                        tvIcon.setText(fileTypeEmoji(fname));
                        Long tot = rxTotalMap.get(fname);
                        if (tot != null) {
                            fileBytesTotal = tot;
                            tvTotalSize.setText("of " + FileUtils.formatSize(tot));
                        }
                    }
                    animateProgress(pct);
                    tvPercent.setText(pct + "%");
                    break;
                }

                case TransferEngine.MSG_BYTES: {
                    // Raw byte counts — more accurate than % math
                    if (isDone || msg.obj == null) break;
                    long[] b = (long[]) msg.obj;
                    long fileDone  = b[0];
                    long fileTotal = b[1];
                    fileBytesReceived = fileDone;
                    fileBytesTotal    = fileTotal;
                    String sentLabel = "receive".equals(mode) ? " received" : " sent";
                    tvBytesSent.setText(FileUtils.formatSize(fileDone) + sentLabel);
                    if (fileTotal > 0) tvTotalSize.setText("of " + FileUtils.formatSize(fileTotal));
                    updateEta(fileDone, fileTotal);
                    break;
                }

                case TransferEngine.MSG_SPEED: {
                    String spd = (String) msg.obj;
                    tvSpeed.setText(spd);
                    // Parse speed for mini graph
                    updateSpeedGraph(spd);
                    break;
                }

                case TransferEngine.MSG_FILE_DONE: {
                    String fname = (String) msg.obj;
                    // Brief green flash on progress bar
                    flashComplete();
                    break;
                }

                case TransferEngine.MSG_DONE: {
                    String summary = (String) msg.obj;
                    onAllDone(summary);
                    break;
                }

                case TransferEngine.MSG_ERROR: {
                    String err = (String) msg.obj;
                    showErrorBar(err);
                    break;
                }

                case TransferEngine.MSG_CANCELLED: {
                    onCancelled();
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        bindViews();

        mode          = getIntent().getStringExtra("mode");
        engine        = new TransferEngine(this, uiHandler);
        transferStart = System.currentTimeMillis();

        setupSpeedGraph();
        setupButtons();

        if ("receive".equals(mode)) {
            setupReceiveMode();
        } else {
            setupSendMode();
        }
    }

    private void bindViews() {
        rootLayout     = findViewById(R.id.rootLayout);
        tvIcon         = findViewById(R.id.tvIcon);
        tvStatus       = findViewById(R.id.tvStatus);
        tvFileCounter  = findViewById(R.id.tvFileCounter);
        tvFileName     = findViewById(R.id.tvFileName);
        tvSpeed        = findViewById(R.id.tvSpeed);
        tvPercent      = findViewById(R.id.tvPercent);
        tvEta          = findViewById(R.id.tvEta);
        tvBytesSent    = findViewById(R.id.tvBytesSent);
        tvTotalSize    = findViewById(R.id.tvTotalSize);
        progressBar    = findViewById(R.id.progressBar);
        btnPauseResume = findViewById(R.id.btnPauseResume);
        btnAction      = findViewById(R.id.btnAction);
        cardSummary    = findViewById(R.id.cardSummary);
        tvSummaryTitle = findViewById(R.id.tvSummaryTitle);
        tvSummaryDetail= findViewById(R.id.tvSummaryDetail);
        tvSummarySpeed = findViewById(R.id.tvSummarySpeed);
        layoutError    = findViewById(R.id.layoutError);
        tvErrorMsg     = findViewById(R.id.tvErrorMsg);

        speedBars = new View[]{
            findViewById(R.id.bar1), findViewById(R.id.bar2), findViewById(R.id.bar3),
            findViewById(R.id.bar4), findViewById(R.id.bar5), findViewById(R.id.bar6),
            findViewById(R.id.bar7)
        };
    }

    private void setupSendMode() {
        tvStatus.setText("SENDING");
        tvIcon.setText("📤");
        tvBytesSent.setText("0 B sent");

        ArrayList<String> files = getIntent().getStringArrayListExtra("files");
        String remoteIp = getIntent().getStringExtra("remoteIp");
        if (remoteIp == null) remoteIp = HotspotManager.HOST_IP;

        if (files != null && !files.isEmpty()) {
            totalFileSize = 0;
            for (String f : files) totalFileSize += FileUtils.getFileSize(f);
            tvTotalSize.setText("of " + FileUtils.formatSize(totalFileSize));
            if (files.size() > 0) {
                String first = new java.io.File(files.get(0)).getName();
                tvFileName.setText(first);
                tvIcon.setText(fileTypeEmoji(first));
            }
            totalFiles = files.size();
            if (totalFiles > 1) {
                tvFileCounter.setVisibility(View.VISIBLE);
                tvFileCounter.setText("1 / " + totalFiles);
            }
            engine.sendFiles(files, remoteIp, HotspotManager.TRANSFER_PORT);
        }
        startPulseIcon();
    }

    private void setupReceiveMode() {
        tvStatus.setText("WAITING");
        tvIcon.setText("📡");
        tvFileName.setText("Waiting for sender…");
        tvBytesSent.setText("0 B received");
        tvTotalSize.setText("of 0 B");
        tvSpeed.setText("—");
        tvPercent.setText("—");
        engine.startReceiving();
        startPulseIcon();

        // Animate status label cycling while waiting
        final String[] waitLabels = {"WAITING", "WAITING ·", "WAITING ··", "WAITING ···"};
        final int[] waitIdx = {0};
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!isDone && "receive".equals(mode) && progressBar.getProgress() == 0) {
                    tvStatus.setText(waitLabels[waitIdx[0]++ % waitLabels.length]);
                    uiHandler.postDelayed(this, 600);
                }
            }
        }, 600);
    }

    private void setupButtons() {
        btnPauseResume.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            isPaused = !isPaused;
            engine.setPaused(isPaused);
            if (isPaused) {
                btnPauseResume.setText("▶  Resume");
                tvStatus.setText("PAUSED");
                tvEta.setText("Paused");
                tvSpeed.setText("—");
                stopPulseIcon();
            } else {
                btnPauseResume.setText("⏸  Pause");
                tvStatus.setText("receive".equals(mode) ? "RECEIVING" : "SENDING");
                tvEta.setText("");
                startPulseIcon();
            }
        });

        btnAction.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            String label = btnAction.getText().toString();
            if ("Close".equals(label)) {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            } else {
                // Cancel
                btnAction.setEnabled(false);
                btnAction.setText("Cancelling…");
                engine.cancel();
                engine.stopReceiving();
                Intent svc = new Intent(this, TransferService.class);
                svc.setAction(TransferService.ACTION_CANCEL);
                startService(svc);
            }
        });
    }

    // ─── State transitions ───────────────────────────────────────────────────

    private void onAllDone(String summary) {
        isDone = true;
        stopPulseIcon();
        tvIcon.setText("✅");
        tvStatus.setText("COMPLETE");
        tvPercent.setText("100%");
        tvEta.setText("");
        tvSpeed.setText("Done!");
        tvSpeed.setTextColor(0xFF00C853);
        progressBar.setProgress(100);
        btnPauseResume.setEnabled(false);
        btnAction.setText("Close");

        // Show summary card
        long elapsed = System.currentTimeMillis() - transferStart;
        cardSummary.setVisibility(View.VISIBLE);
        cardSummary.setAlpha(0f);
        cardSummary.animate().alpha(1f).setDuration(400).start();
        tvSummaryTitle.setText("Transfer complete");
        tvSummaryDetail.setText(summary);
        String role = "receive".equals(mode) ? "Received" : "Sent";
        tvSummarySpeed.setText(role + " · " + formatDuration(elapsed));

        // Pulse the checkmark
        ObjectAnimator scale = ObjectAnimator.ofFloat(tvIcon, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvIcon, "scaleY", 1f, 1.3f, 1f);
        scale.setDuration(500);
        scaleY.setDuration(500);
        scale.start();
        scaleY.start();
    }

    private void onCancelled() {
        isDone = true;
        stopPulseIcon();
        tvIcon.setText("🚫");
        tvStatus.setText("CANCELLED");
        tvEta.setText("");
        tvSpeed.setText("Cancelled");
        tvSpeed.setTextColor(0xFFFF6B35);
        btnPauseResume.setEnabled(false);
        btnAction.setEnabled(true);
        btnAction.setText("Close");
        cardSummary.setVisibility(View.VISIBLE);
        tvSummaryTitle.setText("Transfer cancelled");
        String sentLabel = "receive".equals(mode) ? "received" : "sent";
        tvSummaryDetail.setText(FileUtils.formatSize(fileBytesReceived) + " " + sentLabel
                + " of " + FileUtils.formatSize(fileBytesTotal));
        tvSummarySpeed.setText("File " + currentFile + " of " + totalFiles);
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    private void animateProgress(int target) {
        int current = progressBar.getProgress();
        if (target <= current) {
            progressBar.setProgress(target);
            return;
        }
        ValueAnimator anim = ValueAnimator.ofInt(current, target);
        anim.setDuration(120);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> progressBar.setProgress((int) a.getAnimatedValue()));
        anim.start();
    }

    private void flashComplete() {
        // Quick green pulse on the icon
        ObjectAnimator a = ObjectAnimator.ofFloat(tvIcon, "alpha", 1f, 0.3f, 1f);
        a.setDuration(300);
        a.start();
    }

    private void showErrorBar(String err) {
        tvErrorMsg.setText(err);
        layoutError.setVisibility(View.VISIBLE);
        layoutError.setAlpha(0f);
        layoutError.animate().alpha(1f).setDuration(200).withEndAction(() ->
            uiHandler.postDelayed(() ->
                layoutError.animate().alpha(0f).setDuration(300).withEndAction(() ->
                    layoutError.setVisibility(View.GONE)).start(),
            4000)
        ).start();
    }

    private ValueAnimator pulseAnim;
    private void startPulseIcon() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(1f, 1.15f, 1f);
        pulseAnim.setDuration(900);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            tvIcon.setScaleX(v);
            tvIcon.setScaleY(v);
        });
        pulseAnim.start();
    }

    private void stopPulseIcon() {
        if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
        tvIcon.setScaleX(1f);
        tvIcon.setScaleY(1f);
    }

    private void setupSpeedGraph() {
        for (View b : speedBars) if (b != null) b.setAlpha(0.2f);
    }

    private void updateSpeedGraph(String speedStr) {
        // Parse numeric value from speed string like "12.3 MB/s"
        try {
            String[] parts = speedStr.trim().split(" ");
            if (parts.length < 2) return;
            float val = Float.parseFloat(parts[0]);
            String unit = parts[1];
            float bps = val;
            if (unit.startsWith("KB")) bps = val * 1024;
            else if (unit.startsWith("MB")) bps = val * 1024 * 1024;
            else if (unit.startsWith("GB")) bps = val * 1024L * 1024 * 1024;

            speedHistory[speedHistIdx % 7] = bps;
            speedHistIdx++;

            // Find max for normalization
            float max = 1f;
            for (float f : speedHistory) if (f > max) max = f;

            for (int i = 0; i < speedBars.length; i++) {
                if (speedBars[i] == null) continue;
                int idx = (speedHistIdx + i) % 7;
                float ratio = speedHistory[idx] / max;
                speedBars[i].animate().scaleY(Math.max(0.1f, ratio)).setDuration(150).start();
                speedBars[i].setAlpha(0.3f + 0.7f * ratio);
            }
        } catch (Exception ignored) {}
    }

    private void updateEta(long done, long total) {
        if (done <= 0 || total <= 0) { tvEta.setText(""); return; }
        if (etaBytesStart == 0) { etaBytesStart = done; etaTimeStart = System.currentTimeMillis(); return; }
        long elapsedMs = System.currentTimeMillis() - etaTimeStart;
        if (elapsedMs < 500) return;
        long deltaDone = done - etaBytesStart;
        if (deltaDone <= 0) return;
        double ratePerMs = deltaDone / (double) elapsedMs;
        long remaining = total - done;
        if (ratePerMs <= 0) return;
        long etaMs = (long)(remaining / ratePerMs);
        if (etaMs < 1000)       tvEta.setText("< 1s left");
        else if (etaMs < 60000) tvEta.setText((etaMs / 1000) + "s left");
        else                    tvEta.setText((etaMs / 60000) + "m " + ((etaMs % 60000)/1000) + "s left");
    }

    private String fileTypeEmoji(String name) {
        if (name == null) return "📄";
        String n = name.toLowerCase();
        if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov"))
            return "🎬";
        if (n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".aac") || n.endsWith(".ogg"))
            return "🎵";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif")
                || n.endsWith(".webp") || n.endsWith(".heic"))
            return "🖼️";
        if (n.endsWith(".pdf")) return "📕";
        if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") || n.endsWith(".tar"))
            return "🗜️";
        if (n.endsWith(".apk")) return "📦";
        if (n.endsWith(".doc") || n.endsWith(".docx")) return "📝";
        if (n.endsWith(".xls") || n.endsWith(".xlsx")) return "📊";
        if (n.endsWith(".ppt") || n.endsWith(".pptx")) return "📑";
        if (n.endsWith(".txt") || n.endsWith(".md"))   return "📄";
        return "📁";
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        return (ms / 60000) + "m " + ((ms % 60000) / 1000) + "s";
    }

    @Override public void onBackPressed() { /* block accidental back */ }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopPulseIcon();
    }
}
