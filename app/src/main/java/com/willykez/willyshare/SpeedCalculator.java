package com.willykez.willyshare;

/**
 * SpeedCalculator — Exponential Moving Average speed meter.
 *
 * Upgrades vs original:
 *  - EMA alpha=0.25 smooths out bursty reads
 *  - Updates fire every 200 ms instead of 500 ms → more responsive UI
 *  - Peak speed tracking for UI display
 *  - Returns separate Mbps/KB/s/B/s strings
 */
public class SpeedCalculator {
    private static final long   UPDATE_INTERVAL_MS = 200;  // was 500
    private static final double EMA_ALPHA          = 0.25; // smoothing factor

    private long   lastBytes       = 0;
    private long   lastTime        = System.currentTimeMillis();
    private double emaSpeedBps     = 0.0;
    private double peakSpeedBps    = 0.0;

    public synchronized void update(long totalBytes) {
        long now     = System.currentTimeMillis();
        long timeDiff = now - lastTime;
        if (timeDiff >= UPDATE_INTERVAL_MS) {
            long   bytesDiff    = totalBytes - lastBytes;
            double instantSpeed = (bytesDiff / (double) timeDiff) * 1000.0;
            // EMA blend
            emaSpeedBps = (EMA_ALPHA * instantSpeed) + ((1.0 - EMA_ALPHA) * emaSpeedBps);
            if (emaSpeedBps > peakSpeedBps) peakSpeedBps = emaSpeedBps;
            lastBytes = totalBytes;
            lastTime  = now;
        }
    }

    public synchronized String getSpeedFormatted() {
        return formatBps(emaSpeedBps);
    }

    public synchronized String getPeakFormatted() {
        return formatBps(peakSpeedBps);
    }

    private String formatBps(double bps) {
        if (bps >= 1024.0 * 1024.0 * 1024.0)
            return String.format("%.2f GB/s", bps / (1024.0 * 1024.0 * 1024.0));
        if (bps >= 1024.0 * 1024.0)
            return String.format("%.1f MB/s", bps / (1024.0 * 1024.0));
        if (bps >= 1024.0)
            return String.format("%.0f KB/s", bps / 1024.0);
        return String.format("%.0f B/s", bps);
    }

    public synchronized void reset() {
        lastBytes    = 0;
        lastTime     = System.currentTimeMillis();
        emaSpeedBps  = 0.0;
        peakSpeedBps = 0.0;
    }
}
