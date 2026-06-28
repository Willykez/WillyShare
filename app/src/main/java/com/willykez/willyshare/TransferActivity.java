package com.willykez.willyshare;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;

public class TransferActivity extends AppCompatActivity {

    private static final String TAG = "TransferActivity";

    private TextView     tvIcon, tvStatus, tvFileCounter, tvFileName;
    private TextView     tvSpeed, tvPercent, tvEta, tvBytesSent, tvTotalSize;
    private ProgressBar  progressBar;
    private Button       btnPauseResume, btnAction;
    private LinearLayout cardSummary;
    private TextView     tvSummaryTitle, tvSummaryDetail, tvSummarySpeed;
    private LinearLayout layoutError;
    private TextView     tvErrorMsg;
    private View[]       speedBars;

    private boolean  isPaused    = false;
    private boolean  isDone      = false;
    private String   mode;
    private TransferEngine engine;

    private int    totalFiles    = 1;
    private int    currentFile   = 0;
    private long   totalFileSize = 0;
    private long   transferStart = 0;
    private long   fileBytesRecv = 0;
    private long   fileBytesTotal= 0;

    // ETA
    private long etaBytesStart = 0;
    private long etaTimeStart  = 0;

    // rx file totals reported by filelist announcement
    private final HashMap<String, Long> rxTotalMap = new HashMap<>();

    // speed graph
    private final float[] speedHistory = new float[7];
    private int           speedHistIdx = 0;

    private ValueAnimator pulseAnim;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {

                case TransferEngine.MSG_FILE_START: {
                    // arg1=fileIndex, arg2=totalFiles, obj=filename (empty string for rx-only announcement)
                    int idx      = msg.arg1;
                    int numFiles = msg.arg2;
                    String fname = (String) msg.obj;
                    totalFiles   = numFiles;

                    if (fname != null && !fname.isEmpty()) {
                        currentFile = idx + 1;
                        tvFileName.setText(fname);
                        tvIcon.setText(emojiFor(fname));
                    } else {
                        // receiver got the manifest — switch status from WAITING → RECEIVING
                        tvStatus.setText("RECEIVING");
                        tvFileName.setText("Incoming transfer…");
                        tvIcon.setText("📥");
                    }
                    if (totalFiles > 1 && currentFile > 0) {
                        tvFileCounter.setVisibility(View.VISIBLE);
                        tvFileCounter.setText(currentFile + " / " + totalFiles);
                    }
                    progressBar.setProgress(0);
                    tvPercent.setText("0%");
                    tvEta.setText("");
                    etaBytesStart = 0;
                    etaTimeStart  = System.currentTimeMillis();
                    break;
                }

                case TransferEngine.MSG_PROGRESS: {
                    if (isDone) break;
                    int pct    = msg.arg1;
                    String fname = (String) msg.obj;
                    if (fname != null && !fname.isEmpty()) {
                        tvFileName.setText(fname);
                        tvIcon.setText(emojiFor(fname));
                    }
                    animProgress(pct);
                    tvPercent.setText(pct + "%");
                    break;
                }

                case TransferEngine.MSG_RX_PROGRESS: {
                    if (isDone) break;
                    int pct    = msg.arg1;
                    int fIdx   = msg.arg2;
                    String fname = (String) msg.obj;
                    if (fname != null && !fname.isEmpty()) {
                        // Update file counter on first progress for a new file
                        if (currentFile != fIdx + 1) {
                            currentFile = fIdx + 1;
                            tvFileName.setText(fname);
                            tvIcon.setText(emojiFor(fname));
                            if (totalFiles > 1) {
                                tvFileCounter.setVisibility(View.VISIBLE);
                                tvFileCounter.setText(currentFile + " / " + totalFiles);
                            }
                            etaBytesStart = 0;
                            etaTimeStart  = System.currentTimeMillis();
                        }
                    }
                    // Only update status to RECEIVING once (when it was still WAITING)
                    if ("WAITING".equals(tvStatus.getText().toString()) ||
                        tvStatus.getText().toString().startsWith("WAITING")) {
                        tvStatus.setText("RECEIVING");
                    }
                    animProgress(pct);
                    tvPercent.setText(pct + "%");
                    break;
                }

                case TransferEngine.MSG_BYTES: {
                    if (isDone || msg.obj == null) break;
                    long[] b      = (long[]) msg.obj;
                    long fileDone = b[0];
                    long fileTotal= b[1];
                    fileBytesRecv  = fileDone;
                    fileBytesTotal = fileTotal;
                    String label   = "receive".equals(mode) ? " received" : " sent";
                    tvBytesSent.setText(FileUtils.formatSize(fileDone) + label);
                    if (fileTotal > 0) tvTotalSize.setText("of " + FileUtils.formatSize(fileTotal));
                    updateEta(fileDone, fileTotal);
                    break;
                }

                case TransferEngine.MSG_SPEED:
                    if (!isDone) {
                        String spd = (String) msg.obj;
                        tvSpeed.setText(spd);
                        updateSpeedGraph(spd);
                        // Push progress notification
                        pushNotif(tvStatus.getText() + ": " + spd);
                    }
                    break;

                case TransferEngine.MSG_FILE_DONE: {
                    String fname = (String) msg.obj;
                    // brief icon flash
                    tvIcon.animate().alpha(0.3f).setDuration(120)
                          .withEndAction(() -> tvIcon.animate().alpha(1f).setDuration(120).start())
                          .start();
                    break;
                }

                case TransferEngine.MSG_DONE:
                    onAllDone((String) msg.obj);
                    break;

                case TransferEngine.MSG_ERROR:
                    showError((String) msg.obj);
                    break;

                case TransferEngine.MSG_CANCELLED:
                    onCancelled();
                    break;
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

        initSpeedGraph();
        setupButtons();

        if ("receive".equals(mode)) {
            startReceiveMode();
        } else {
            startSendMode();
        }
    }

    // ── Mode setup ────────────────────────────────────────────────────────────

    private void startSendMode() {
        tvStatus.setText("SENDING");
        tvIcon.setText("📤");
        tvBytesSent.setText("0 B sent");

        ArrayList<String> files = getIntent().getStringArrayListExtra("files");
        String remoteIp = getIntent().getStringExtra("remoteIp");
        if (remoteIp == null || remoteIp.isEmpty()) remoteIp = HotspotManager.HOST_IP;

        if (files == null || files.isEmpty()) {
            showError("No files to send");
            return;
        }

        totalFileSize = 0;
        for (String f : files) totalFileSize += FileUtils.getFileSize(f);
        totalFiles = files.size();
        tvTotalSize.setText("of " + FileUtils.formatSize(totalFileSize));
        String first = new java.io.File(files.get(0)).getName();
        tvFileName.setText(first);
        tvIcon.setText(emojiFor(first));
        if (totalFiles > 1) {
            tvFileCounter.setVisibility(View.VISIBLE);
            tvFileCounter.setText("1 / " + totalFiles);
        }

        // Start foreground service for keep-alive (engine stays here in Activity)
        Intent svc = new Intent(this, TransferService.class);
        svc.setAction(TransferService.ACTION_SEND);
        startForegroundService(svc);

        startPulse();
        engine.sendFiles(files, remoteIp, HotspotManager.TRANSFER_PORT);
    }

    private void startReceiveMode() {
        tvStatus.setText("WAITING");
        tvIcon.setText("📡");
        tvFileName.setText("Waiting for sender…");
        tvBytesSent.setText("0 B received");
        tvTotalSize.setText("of 0 B");
        tvSpeed.setText("—");
        tvPercent.setText("—");

        // Start foreground service
        Intent svc = new Intent(this, TransferService.class);
        svc.setAction(TransferService.ACTION_RECEIVE);
        startForegroundService(svc);

        // Engine lives HERE — not in the service
        startPulse();
        engine.startReceiving();

        // Animate waiting dots
        final String[] dots = {"WAITING", "WAITING ·", "WAITING ··", "WAITING ···"};
        final int[] i = {0};
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                String cur = (String) tvStatus.getText();
                if (!isDone && (cur.startsWith("WAITING"))) {
                    tvStatus.setText(dots[i[0]++ % dots.length]);
                    uiHandler.postDelayed(this, 600);
                }
            }
        }, 600);
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

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
                stopPulse();
            } else {
                btnPauseResume.setText("⏸  Pause");
                tvStatus.setText("receive".equals(mode) ? "RECEIVING" : "SENDING");
                tvEta.setText("");
                startPulse();
            }
        });

        btnAction.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            String label = btnAction.getText().toString();
            if ("Close".equals(label)) {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return;
            }
            // Cancel
            btnAction.setEnabled(false);
            btnAction.setText("Cancelling…");
            engine.cancel();
            engine.stopReceiving();
            pushCancel();
        });
    }

    // ── State transitions ─────────────────────────────────────────────────────

    private void onAllDone(String summary) {
        isDone = true;
        stopPulse();
        tvIcon.setText("✅");
        tvStatus.setText("COMPLETE");
        tvPercent.setText("100%");
        tvEta.setText("");
        tvSpeed.setText("Done!");
        tvSpeed.setTextColor(0xFF1B9E4B);
        progressBar.setProgress(100);
        btnPauseResume.setEnabled(false);
        btnAction.setText("Close");
        btnAction.setEnabled(true);

        cardSummary.setVisibility(View.VISIBLE);
        cardSummary.setAlpha(0f);
        cardSummary.animate().alpha(1f).setDuration(400).start();
        tvSummaryTitle.setText("Transfer complete");
        tvSummaryDetail.setText(summary);
        String role = "receive".equals(mode) ? "Received" : "Sent";
        tvSummarySpeed.setText(role + " · " + formatDuration(System.currentTimeMillis() - transferStart));

        // Bounce the checkmark
        ObjectAnimator sx = ObjectAnimator.ofFloat(tvIcon, "scaleX", 1f, 1.35f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(tvIcon, "scaleY", 1f, 1.35f, 1f);
        sx.setDuration(450); sy.setDuration(450);
        sx.start(); sy.start();

        pushNotif("Transfer complete — " + summary);
        stopService(new Intent(this, TransferService.class));
    }

    private void onCancelled() {
        isDone = true;
        stopPulse();
        tvIcon.setText("🚫");
        tvStatus.setText("CANCELLED");
        tvEta.setText("");
        tvSpeed.setText("Cancelled");
        tvSpeed.setTextColor(0xFFBA1A1A);
        btnPauseResume.setEnabled(false);
        btnAction.setEnabled(true);
        btnAction.setText("Close");
        cardSummary.setVisibility(View.VISIBLE);
        tvSummaryTitle.setText("Transfer cancelled");
        tvSummaryDetail.setText(FileUtils.formatSize(fileBytesRecv) + " of "
                + FileUtils.formatSize(fileBytesTotal));
        tvSummarySpeed.setText("File " + currentFile + " of " + totalFiles);
        pushCancel();
    }

    private void showError(String err) {
        if (err == null) return;
        Log.e(TAG, "Transfer error: " + err);
        tvErrorMsg.setText(err);
        layoutError.setVisibility(View.VISIBLE);
        layoutError.setAlpha(0f);
        layoutError.animate().alpha(1f).setDuration(200).withEndAction(() ->
            uiHandler.postDelayed(() ->
                layoutError.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> layoutError.setVisibility(View.GONE)).start(),
            5000)
        ).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void bindViews() {
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

    private void animProgress(int target) {
        int cur = progressBar.getProgress();
        if (target <= cur) { progressBar.setProgress(target); return; }
        ValueAnimator a = ValueAnimator.ofInt(cur, target);
        a.setDuration(120);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(x -> progressBar.setProgress((int) x.getAnimatedValue()));
        a.start();
    }

    private void initSpeedGraph() {
        for (View b : speedBars) if (b != null) b.setScaleY(0.1f);
    }

    private void updateSpeedGraph(String speedStr) {
        try {
            String[] p = speedStr.trim().split(" ");
            if (p.length < 2) return;
            float val = Float.parseFloat(p[0]);
            String unit = p[1];
            float bps = val;
            if (unit.startsWith("KB")) bps = val * 1024;
            else if (unit.startsWith("MB")) bps = val * 1024 * 1024;
            else if (unit.startsWith("GB")) bps = val * 1024L * 1024 * 1024;
            speedHistory[speedHistIdx % 7] = bps;
            speedHistIdx++;
            float max = 1f;
            for (float f : speedHistory) if (f > max) max = f;
            for (int i = 0; i < speedBars.length; i++) {
                if (speedBars[i] == null) continue;
                float ratio = speedHistory[(speedHistIdx + i) % 7] / max;
                speedBars[i].animate().scaleY(Math.max(0.1f, ratio)).setDuration(150).start();
                speedBars[i].setAlpha(0.3f + 0.7f * ratio);
            }
        } catch (Exception ignored) {}
    }

    private void updateEta(long done, long total) {
        if (done <= 0 || total <= 0) { tvEta.setText(""); return; }
        if (etaBytesStart == 0) { etaBytesStart = done; etaTimeStart = System.currentTimeMillis(); return; }
        long elapsedMs = System.currentTimeMillis() - etaTimeStart;
        if (elapsedMs < 400) return;
        long delta = done - etaBytesStart;
        if (delta <= 0) return;
        double ratePerMs = delta / (double) elapsedMs;
        long remaining = total - done;
        if (ratePerMs <= 0) return;
        long etaMs = (long)(remaining / ratePerMs);
        if      (etaMs < 1000)  tvEta.setText("< 1s left");
        else if (etaMs < 60000) tvEta.setText((etaMs / 1000) + "s left");
        else                    tvEta.setText((etaMs / 60000) + "m " + ((etaMs % 60000)/1000) + "s left");
    }

    private void startPulse() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(1f, 1.15f, 1f);
        pulseAnim.setDuration(900);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            if (tvIcon != null) { tvIcon.setScaleX(v); tvIcon.setScaleY(v); }
        });
        pulseAnim.start();
    }

    private void stopPulse() {
        if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
        if (tvIcon != null) { tvIcon.setScaleX(1f); tvIcon.setScaleY(1f); }
    }

    private void pushNotif(String text) {
        Intent svc = new Intent(this, TransferService.class);
        svc.setAction(TransferService.ACTION_UPDATE_NOTIF);
        svc.putExtra(TransferService.EXTRA_NOTIF_TEXT, text);
        startService(svc);
    }

    private void pushCancel() {
        Intent svc = new Intent(this, TransferService.class);
        svc.setAction(TransferService.ACTION_CANCEL);
        startService(svc);
    }

    private String emojiFor(String name) {
        if (name == null) return "📄";
        String n = name.toLowerCase();
        if (n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".avi")||n.endsWith(".mov")) return "🎬";
        if (n.endsWith(".mp3")||n.endsWith(".flac")||n.endsWith(".aac")||n.endsWith(".ogg")) return "🎵";
        if (n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".gif")
                ||n.endsWith(".webp")||n.endsWith(".heic")) return "🖼️";
        if (n.endsWith(".pdf")) return "📕";
        if (n.endsWith(".zip")||n.endsWith(".rar")||n.endsWith(".7z")) return "🗜️";
        if (n.endsWith(".apk")) return "📦";
        if (n.endsWith(".doc")||n.endsWith(".docx")) return "📝";
        if (n.endsWith(".xls")||n.endsWith(".xlsx")) return "📊";
        return "📁";
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        return (ms / 60000) + "m " + ((ms % 60000) / 1000) + "s";
    }

    @Override public void onBackPressed() { /* block accidental back during transfer */ }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulse();
        // Don't cancel the engine here — transfer should keep going if user rotates screen
        // Only cancel is via the Cancel button
    }
}
