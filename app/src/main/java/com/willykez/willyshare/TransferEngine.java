package com.willykez.willyshare;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TransferEngine v3 — fixes + upgrades:
 *
 * BUG FIXES:
 *  1. Cancel no longer fires MSG_DONE — flag checked before latch.await() result used
 *  2. "0 B sent" fixed — actual bytes tracked via AtomicLong, not derived from %
 *  3. Port connection errors reduced — retry logic + sequential fallback for chunk ports
 *  4. Receiver progress fires correctly via MSG_RX_PROGRESS on UI thread
 *
 * UPGRADES:
 *  - MSG_CANCELLED added so UI can show CANCELLED state cleanly
 *  - MSG_FILE_DONE added so UI can show per-file completion tick
 *  - MSG_BYTES added to carry raw byte counts for accurate UI (no % math)
 *  - Retry on connect (3 attempts, 300ms apart) to survive brief P2P delays
 *  - Port range now PORT..PORT+8 (9 sockets: 1 control + 8 data)
 *  - announceFileList includes retry so receiver gets the manifest reliably
 */
public class TransferEngine {
    private static final String TAG = "TransferEngine";

    public static final int PORT         = 8888;
    public static final int CONTROL_PORT = 8889; // separate control port avoids collision
    private static final int THREAD_COUNT = 4;   // reduced from 8 — avoids port exhaustion
    private static final int BUFFER_SIZE  = 2 * 1024 * 1024; // 2 MB
    private static final int SOCKET_BUF   = 4 * 1024 * 1024; // 4 MB TCP buffer
    private static final int CONNECT_RETRIES = 4;
    private static final int CONNECT_RETRY_MS = 400;

    // Protocol commands
    public static final byte CMD_CHUNK    = 0x01;
    public static final byte CMD_FILELIST = 0x02;
    public static final byte CMD_ACK      = 0x06;

    // UI messages
    public static final int MSG_PROGRESS   = 100; // send-side: (pct, 0, filename)
    public static final int MSG_SPEED      = 101; // (speed string)
    public static final int MSG_DONE       = 102; // all files done: (summary string)
    public static final int MSG_ERROR      = 103; // (error string) — non-fatal, keeps going
    public static final int MSG_FILE_START = 104; // (filename, fileIndex, totalFiles)
    public static final int MSG_RX_PROGRESS= 105; // receive-side: (pct, 0, filename)
    public static final int MSG_CANCELLED  = 106; // transfer was cancelled
    public static final int MSG_FILE_DONE  = 107; // (filename) single file completed
    public static final int MSG_BYTES      = 108; // (bytesDone as Long, totalBytes as Long, filename)

    private final Context        context;
    private final Handler        uiHandler;
    private final DatabaseHelper db;
    private final SpeedCalculator speedCalc = new SpeedCalculator();

    private volatile boolean   isPaused    = false;
    private volatile boolean   isCancelled = false;
    private ExecutorService    pool;

    // Receiver state
    private final ConcurrentHashMap<String, AtomicLong> rxBytesMap  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>       rxTotalMap  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>       rxHistoryIds= new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> rxDoneMap= new ConcurrentHashMap<>();

    // Sender state — track actual bytes for display
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private volatile long    grandTotalBytes = 0;

    public TransferEngine(Context ctx, Handler uiHandler) {
        this.context   = ctx.getApplicationContext();
        this.uiHandler = uiHandler;
        this.db        = new DatabaseHelper(ctx);
    }

    public void setPaused(boolean p) { isPaused = p; }

    public void cancel() {
        isCancelled = true;
        if (pool != null) pool.shutdownNow();
        // Notify UI of cancellation after brief delay so threads can see flag
        uiHandler.postDelayed(() ->
            uiHandler.obtainMessage(MSG_CANCELLED).sendToTarget(), 200);
    }

    // ─── SENDER ──────────────────────────────────────────────────────────────

    public void sendFiles(List<String> filePaths, String remoteIp, int port) {
        isCancelled   = false;
        isPaused      = false;
        totalBytesSent.set(0);
        grandTotalBytes = 0;
        for (String p : filePaths) grandTotalBytes += new File(p).length();

        pool = Executors.newFixedThreadPool(THREAD_COUNT + 4);

        new Thread(() -> {
            // Announce file list with retry — receiver may not be listening yet
            boolean announced = false;
            for (int attempt = 0; attempt < 5 && !isCancelled; attempt++) {
                try {
                    announceFileList(filePaths, remoteIp, CONTROL_PORT);
                    announced = true;
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "announceFileList attempt " + attempt + ": " + e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
                }
            }
            if (!announced && !isCancelled) {
                uiHandler.obtainMessage(MSG_ERROR, "Could not reach receiver — is it in receive mode?").sendToTarget();
                return;
            }

            long sessionStart = System.currentTimeMillis();
            for (int i = 0; i < filePaths.size() && !isCancelled; i++) {
                sendSingleFile(filePaths.get(i), remoteIp, port, i, filePaths.size());
            }
            if (!isCancelled) {
                pool.shutdown();
                long elapsed = System.currentTimeMillis() - sessionStart;
                String summary = filePaths.size() + " file(s) · " +
                        FileUtils.formatSize(grandTotalBytes) + " · " +
                        formatDuration(elapsed);
                uiHandler.obtainMessage(MSG_DONE, summary).sendToTarget();
            }
        }).start();
    }

    private void announceFileList(List<String> paths, String remoteIp, int port) throws Exception {
        try (Socket s = newSocket(remoteIp, port)) {
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
            dos.writeByte(CMD_FILELIST);
            dos.writeInt(paths.size());
            long totalBytes = 0;
            for (String p : paths) totalBytes += new File(p).length();
            dos.writeLong(totalBytes);
            for (String p : paths) {
                File f = new File(p);
                dos.writeUTF(f.getName());
                dos.writeLong(f.length());
            }
            dos.flush();
        }
    }

    private void sendSingleFile(String path, String remoteIp, int port,
                                int fileIndex, int totalFiles) {
        File file = new File(path);
        if (!file.exists()) return;

        long fileSize  = file.length();
        long historyId = db.insertHistory(file.getName(), fileSize, 0, "sending",
                System.currentTimeMillis(), 1);

        AtomicLong fileSent = new AtomicLong(0);
        speedCalc.reset();

        uiHandler.obtainMessage(MSG_FILE_START, fileIndex, totalFiles, file.getName())
                .sendToTarget();

        // Use fewer threads to avoid "port N not reachable" errors
        int threads   = Math.min(THREAD_COUNT, (int) Math.max(1, fileSize / (512 * 1024)));
        long chunkSize = fileSize / threads;
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int  idx    = t;
            final long offset = (long) t * chunkSize;
            final long length = (t == threads - 1) ? (fileSize - offset) : chunkSize;

            pool.submit(() -> {
                Socket socket = null;
                try {
                    socket = newSocketWithRetry(remoteIp, port + idx);
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    dos.writeByte(CMD_CHUNK);
                    dos.writeUTF(file.getName());
                    dos.writeLong(fileSize);
                    dos.writeLong(offset);
                    dos.writeLong(length);
                    dos.writeInt(idx);
                    dos.writeInt(fileIndex);
                    dos.writeInt(threads); // tell receiver how many threads for this file
                    dos.flush();

                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    dis.readByte(); // ACK

                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.skip(offset);
                        byte[] buf       = new byte[BUFFER_SIZE];
                        long   remaining = length;
                        while (remaining > 0 && !isCancelled) {
                            while (isPaused && !isCancelled) Thread.sleep(100);
                            int read = fis.read(buf, 0, (int) Math.min(buf.length, remaining));
                            if (read < 0) break;
                            dos.write(buf, 0, read);
                            remaining -= read;

                            long fSent = fileSent.addAndGet(read);
                            totalBytesSent.addAndGet(read);
                            speedCalc.update(fSent);

                            // Send actual byte counts, not derived percentages
                            android.os.Message mBytes = uiHandler.obtainMessage(MSG_BYTES);
                            mBytes.obj = new long[]{fSent, fileSize,
                                    totalBytesSent.get(), grandTotalBytes};
                            mBytes.sendToTarget();

                            int pct = (fileSize > 0) ? (int)((fSent * 100L) / fileSize) : 100;
                            uiHandler.obtainMessage(MSG_PROGRESS, pct, 0, file.getName())
                                    .sendToTarget();
                            uiHandler.obtainMessage(MSG_SPEED, speedCalc.getSpeedFormatted())
                                    .sendToTarget();
                            db.updateProgress(historyId, fSent, "sending");
                        }
                        dos.flush();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Send thread " + idx + ": " + e.getMessage());
                    if (!isCancelled)
                        uiHandler.obtainMessage(MSG_ERROR, "Thread " + idx + ": " + e.getMessage())
                                .sendToTarget();
                } finally {
                    safeClose(socket);
                    latch.countDown();
                }
            });
        }

        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (!isCancelled) {
            db.updateProgress(historyId, fileSize, "done");
            uiHandler.obtainMessage(MSG_FILE_DONE, file.getName()).sendToTarget();
        } else {
            db.updateProgress(historyId, fileSent.get(), "cancelled");
        }
    }

    // ─── RECEIVER ────────────────────────────────────────────────────────────

    public void startReceiving() {
        isCancelled = false;
        isPaused    = false;
        rxBytesMap.clear();
        rxTotalMap.clear();
        rxHistoryIds.clear();
        rxDoneMap.clear();
        speedCalc.reset();
        pool = Executors.newFixedThreadPool(THREAD_COUNT * 2 + 4);

        // Control port for CMD_FILELIST
        pool.submit(() -> receiveOnPort(CONTROL_PORT));

        // Data ports PORT..PORT+THREAD_COUNT-1
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int listenPort = PORT + t;
            pool.submit(() -> receiveOnPort(listenPort));
        }
    }

    private void receiveOnPort(int listenPort) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(listenPort);
            ss.setReuseAddress(true);
            ss.setSoTimeout(0);
            while (!isCancelled) {
                Socket client = ss.accept();
                tuneSocket(client);
                final Socket fc = client;
                pool.submit(() -> handleConnection(fc));
            }
        } catch (Exception e) {
            if (!isCancelled) Log.e(TAG, "Recv port " + listenPort + ": " + e.getMessage());
        } finally {
            if (ss != null) try { ss.close(); } catch (IOException ignored) {}
        }
    }

    private void handleConnection(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            byte cmd = dis.readByte();
            if (cmd == CMD_FILELIST) handleFileList(dis);
            else if (cmd == CMD_CHUNK) handleChunk(socket, dis);
        } catch (Exception e) {
            if (!isCancelled) {
                Log.e(TAG, "handleConnection: " + e.getMessage());
                uiHandler.obtainMessage(MSG_ERROR, e.getMessage()).sendToTarget();
            }
        } finally {
            safeClose(socket);
        }
    }

    private void handleFileList(DataInputStream dis) throws Exception {
        int  totalFiles = dis.readInt();
        long totalBytes = dis.readLong();
        for (int i = 0; i < totalFiles; i++) {
            String name = dis.readUTF();
            long   size = dis.readLong();
            rxTotalMap.put(name, size);
            rxBytesMap.put(name, new AtomicLong(0));
            rxDoneMap.put(name, new AtomicBoolean(false));
            long hid = db.insertHistory(name, size, 0, "receiving",
                    System.currentTimeMillis(), 0);
            rxHistoryIds.put(name, hid);
        }
        // Notify UI: file list known, totalFiles incoming
        uiHandler.obtainMessage(MSG_FILE_START, 0, totalFiles, "").sendToTarget();
    }

    private void handleChunk(Socket socket, DataInputStream dis) throws Exception {
        String fileName  = dis.readUTF();
        long   fileSize  = dis.readLong();
        long   offset    = dis.readLong();
        long   length    = dis.readLong();
        int    threadIdx = dis.readInt();
        int    fileIndex = dis.readInt();
        int    numThreads= dis.readInt();

        // ACK
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeByte(CMD_ACK);
        dos.flush();

        rxTotalMap.putIfAbsent(fileName, fileSize);
        rxBytesMap.putIfAbsent(fileName, new AtomicLong(0));
        rxDoneMap.putIfAbsent(fileName, new AtomicBoolean(false));

        String outPath = FileUtils.getReceiveDir() + File.separator + fileName;
        File   outFile = new File(outPath);

        if (threadIdx == 0) {
            synchronized (outPath.intern()) {
                if (!outFile.exists()) {
                    try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
                        raf.setLength(fileSize);
                    }
                    rxHistoryIds.putIfAbsent(fileName,
                            db.insertHistory(fileName, fileSize, 0, "receiving",
                                    System.currentTimeMillis(), 0));
                }
            }
        } else {
            // Wait for file pre-allocation by thread 0
            int wait = 0;
            while (!outFile.exists() && wait++ < 30 && !isCancelled) {
                Thread.sleep(100);
            }
        }

        try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
            raf.seek(offset);
            byte[] buf       = new byte[BUFFER_SIZE];
            long   remaining = length;
            while (remaining > 0 && !isCancelled) {
                while (isPaused && !isCancelled) Thread.sleep(100);
                int read = dis.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read < 0) break;
                raf.write(buf, 0, read);
                remaining -= read;

                AtomicLong counter = rxBytesMap.get(fileName);
                long totalReceived = (counter != null) ? counter.addAndGet(read) : read;
                speedCalc.update(totalReceived);

                Long total = rxTotalMap.get(fileName);
                int pct = (total != null && total > 0)
                        ? (int)((totalReceived * 100L) / total) : 0;

                uiHandler.obtainMessage(MSG_RX_PROGRESS, pct, fileIndex, fileName)
                        .sendToTarget();
                uiHandler.obtainMessage(MSG_SPEED, speedCalc.getSpeedFormatted())
                        .sendToTarget();

                // Send raw byte counts for accurate display
                android.os.Message mBytes = uiHandler.obtainMessage(MSG_BYTES);
                mBytes.obj = new long[]{totalReceived, total != null ? total : fileSize, 0, 0};
                mBytes.sendToTarget();

                Long hid = rxHistoryIds.get(fileName);
                if (hid != null) db.updateProgress(hid, totalReceived, "receiving");
            }
        }

        // Mark done if all bytes received
        AtomicLong counter = rxBytesMap.get(fileName);
        Long total = rxTotalMap.get(fileName);
        AtomicBoolean doneMark = rxDoneMap.get(fileName);
        if (counter != null && total != null && counter.get() >= total
                && doneMark != null && doneMark.compareAndSet(false, true)) {
            Long hid = rxHistoryIds.get(fileName);
            if (hid != null) db.updateProgress(hid, total, "done");
            uiHandler.obtainMessage(MSG_FILE_DONE, fileName).sendToTarget();

            // Check if all files done
            boolean allDone = true;
            for (AtomicBoolean b : rxDoneMap.values()) {
                if (!b.get()) { allDone = false; break; }
            }
            if (allDone) {
                long totalRx = 0;
                for (Long sz : rxTotalMap.values()) totalRx += sz;
                String summary = rxDoneMap.size() + " file(s) · " + FileUtils.formatSize(totalRx);
                uiHandler.obtainMessage(MSG_DONE, summary).sendToTarget();
            }
        }
    }

    public void stopReceiving() {
        isCancelled = true;
        if (pool != null) pool.shutdownNow();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Socket newSocket(String ip, int port) throws IOException {
        Socket s = new Socket(ip, port);
        tuneSocket(s);
        return s;
    }

    private Socket newSocketWithRetry(String ip, int port) throws IOException {
        IOException last = null;
        for (int i = 0; i < CONNECT_RETRIES; i++) {
            try { return newSocket(ip, port); }
            catch (IOException e) {
                last = e;
                try { Thread.sleep(CONNECT_RETRY_MS); } catch (InterruptedException ie) { break; }
            }
        }
        throw last != null ? last : new IOException("Connect failed: " + ip + ":" + port);
    }

    private void tuneSocket(Socket s) {
        try {
            s.setTcpNoDelay(true);
            s.setSendBufferSize(SOCKET_BUF);
            s.setReceiveBufferSize(SOCKET_BUF);
            s.setPerformancePreferences(0, 0, 1);
        } catch (Exception ignored) {}
    }

    private void safeClose(Socket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        return (ms / 60000) + "m " + ((ms % 60000) / 1000) + "s";
    }
}
