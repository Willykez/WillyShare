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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TransferEngine — high-throughput multi-threaded file transfer.
 *
 * Speed tuning vs original:
 *  - THREAD_COUNT: 4 → 8   (saturates WiFi Direct / hotspot NIC)
 *  - BUFFER_SIZE:  512 KB → 4 MB   (fewer system calls per MB)
 *  - TCP socket options: TCP_NODELAY, SO_SNDBUF/SO_RCVBUF = 4 MB
 *  - Sender sends file-count header first so receiver knows total job
 *  - Receiver tracks per-file byte totals and fires real progress events
 *  - SpeedCalculator is now per-engine (not per-thread) with EMA smoothing
 */
public class TransferEngine {
    private static final String TAG = "TransferEngine";

    public static final int PORT = 8888;
    private static final int THREAD_COUNT = 8;
    private static final int BUFFER_SIZE  = 4 * 1024 * 1024; // 4 MB
    private static final int SOCKET_BUF   = 4 * 1024 * 1024; // 4 MB TCP buffer

    // Protocol commands (sent as first byte on each connection)
    public static final byte CMD_CHUNK    = 0x01;
    public static final byte CMD_FILELIST = 0x02; // sender→receiver: total file list
    public static final byte CMD_ACK      = 0x06;

    // UI messages
    public static final int MSG_PROGRESS     = 100;
    public static final int MSG_SPEED        = 101;
    public static final int MSG_DONE         = 102;
    public static final int MSG_ERROR        = 103;
    public static final int MSG_FILE_START   = 104; // (filename, fileIndex, totalFiles)
    public static final int MSG_RX_PROGRESS  = 105; // receive-side progress

    private final Context context;
    private final Handler uiHandler;
    private final DatabaseHelper db;
    private final SpeedCalculator speedCalc = new SpeedCalculator();

    private volatile boolean isPaused    = false;
    private volatile boolean isCancelled = false;
    private ExecutorService pool;

    // Receiver state — tracks bytes written per filename across threads
    private final ConcurrentHashMap<String, AtomicLong> rxBytesMap   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>       rxTotalMap    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>       rxHistoryIds  = new ConcurrentHashMap<>();

    public TransferEngine(Context ctx, Handler uiHandler) {
        this.context   = ctx.getApplicationContext();
        this.uiHandler = uiHandler;
        this.db        = new DatabaseHelper(ctx);
    }

    public void setPaused(boolean p) { isPaused = p; }
    public void cancel() {
        isCancelled = true;
        if (pool != null) pool.shutdownNow();
    }

    // ─── SENDER ──────────────────────────────────────────────────────────────

    /**
     * Send a list of files to remoteIp:port using 8 parallel TCP streams.
     * Files are sent one at a time; each file is split across THREAD_COUNT
     * streams for maximum throughput.
     */
    public void sendFiles(List<String> filePaths, String remoteIp, int port) {
        isCancelled = false;
        isPaused    = false;
        pool = Executors.newFixedThreadPool(THREAD_COUNT + 2);

        new Thread(() -> {
            // First announce file count so receiver can show total
            announceFileList(filePaths, remoteIp, port);

            for (int i = 0; i < filePaths.size(); i++) {
                if (isCancelled) break;
                sendSingleFile(filePaths.get(i), remoteIp, port, i, filePaths.size());
            }
            pool.shutdown();
        }).start();
    }

    /** Sends a one-shot control connection with the full file list metadata. */
    private void announceFileList(List<String> paths, String remoteIp, int port) {
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
        } catch (Exception e) {
            Log.w(TAG, "announceFileList: " + e.getMessage());
        }
    }

    private void sendSingleFile(String path, String remoteIp, int port,
                                int fileIndex, int totalFiles) {
        File file = new File(path);
        if (!file.exists()) return;

        long fileSize  = file.length();
        long historyId = db.insertHistory(file.getName(), fileSize, 0, "sending",
                System.currentTimeMillis(), 1);
        speedCalc.reset();

        AtomicLong totalSent = new AtomicLong(0);
        uiHandler.obtainMessage(MSG_FILE_START, fileIndex, totalFiles, file.getName())
                .sendToTarget();

        long chunkSize = fileSize / THREAD_COUNT;
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int    idx    = t;
            final long   offset = (long) t * chunkSize;
            final long   length = (t == THREAD_COUNT - 1) ? (fileSize - offset) : chunkSize;

            pool.submit(() -> {
                Socket socket = null;
                try {
                    socket = newSocket(remoteIp, port + idx);
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                    dos.writeByte(CMD_CHUNK);
                    dos.writeUTF(file.getName());
                    dos.writeLong(fileSize);
                    dos.writeLong(offset);
                    dos.writeLong(length);
                    dos.writeInt(idx);
                    dos.writeInt(fileIndex);
                    dos.flush();

                    // Wait for ACK before streaming data
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

                            long sent = totalSent.addAndGet(read);
                            speedCalc.update(sent);
                            int pct = (fileSize > 0) ? (int) ((sent * 100L) / fileSize) : 100;
                            uiHandler.obtainMessage(MSG_PROGRESS, pct, 0, file.getName()).sendToTarget();
                            uiHandler.obtainMessage(MSG_SPEED, speedCalc.getSpeedFormatted()).sendToTarget();
                            db.updateProgress(historyId, sent, "sending");
                        }
                        dos.flush();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Send thread " + idx + ": " + e.getMessage());
                    if (!isCancelled)
                        uiHandler.obtainMessage(MSG_ERROR, e.getMessage()).sendToTarget();
                } finally {
                    safeClose(socket);
                    latch.countDown();
                }
            });
        }

        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (!isCancelled) {
            db.updateProgress(historyId, fileSize, "done");
            uiHandler.obtainMessage(MSG_DONE, file.getName()).sendToTarget();
        } else {
            db.updateProgress(historyId, totalSent.get(), "cancelled");
        }
    }

    // ─── RECEIVER ────────────────────────────────────────────────────────────

    /**
     * Start THREAD_COUNT+1 server sockets.
     * Port PORT       → control port (CMD_FILELIST announcements)
     * Port PORT+1..+8 → chunk data streams
     */
    public void startReceiving() {
        isCancelled = false;
        isPaused    = false;
        rxBytesMap.clear();
        rxTotalMap.clear();
        rxHistoryIds.clear();
        speedCalc.reset();
        pool = Executors.newFixedThreadPool(THREAD_COUNT * 2 + 2);

        // Control port (CMD_FILELIST)
        pool.submit(() -> receiveOnPort(PORT, true));

        // Data ports
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int listenPort = PORT + t;
            pool.submit(() -> receiveOnPort(listenPort, false));
        }
    }

    private void receiveOnPort(int listenPort, boolean isControl) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(listenPort);
            ss.setReuseAddress(true);
            ss.setSoTimeout(0);
            while (!isCancelled) {
                Socket client = ss.accept();
                tuneSocket(client);
                pool.submit(() -> handleConnection(client));
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

            if (cmd == CMD_FILELIST) {
                handleFileList(dis);
            } else if (cmd == CMD_CHUNK) {
                handleChunk(socket, dis);
            }
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
        int  totalFiles  = dis.readInt();
        long totalBytes  = dis.readLong();
        for (int i = 0; i < totalFiles; i++) {
            String name = dis.readUTF();
            long   size = dis.readLong();
            rxTotalMap.put(name, size);
            rxBytesMap.put(name, new AtomicLong(0));
            long hid = db.insertHistory(name, size, 0, "receiving",
                    System.currentTimeMillis(), 0);
            rxHistoryIds.put(name, hid);
        }
        uiHandler.obtainMessage(MSG_FILE_START, 0, totalFiles, "").sendToTarget();
    }

    private void handleChunk(Socket socket, DataInputStream dis) throws Exception {
        String fileName   = dis.readUTF();
        long   fileSize   = dis.readLong();
        long   offset     = dis.readLong();
        long   length     = dis.readLong();
        int    threadIdx  = dis.readInt();
        int    fileIndex  = dis.readInt();

        // Send ACK so sender starts streaming
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeByte(CMD_ACK);
        dos.flush();

        // Ensure rx tracking maps are populated even if filelist arrived late
        rxTotalMap.putIfAbsent(fileName, fileSize);
        rxBytesMap.putIfAbsent(fileName, new AtomicLong(0));

        String outPath = FileUtils.getReceiveDir() + File.separator + fileName;
        File   outFile = new File(outPath);

        // Pre-allocate file on first thread (thread 0)
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
                        ? (int) ((totalReceived * 100L) / total) : 0;

                uiHandler.obtainMessage(MSG_RX_PROGRESS, pct, fileIndex, fileName).sendToTarget();
                uiHandler.obtainMessage(MSG_SPEED, speedCalc.getSpeedFormatted()).sendToTarget();

                Long hid = rxHistoryIds.get(fileName);
                if (hid != null) db.updateProgress(hid, totalReceived, "receiving");
            }
        }

        // Check if all threads done (all bytes received)
        AtomicLong counter = rxBytesMap.get(fileName);
        Long total = rxTotalMap.get(fileName);
        if (counter != null && total != null && counter.get() >= total) {
            Long hid = rxHistoryIds.get(fileName);
            if (hid != null) db.updateProgress(hid, total, "done");
            uiHandler.obtainMessage(MSG_DONE, fileName).sendToTarget();
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

    /** Tune TCP socket for maximum throughput on a local WiFi link. */
    private void tuneSocket(Socket s) {
        try {
            s.setTcpNoDelay(true);
            s.setSendBufferSize(SOCKET_BUF);
            s.setReceiveBufferSize(SOCKET_BUF);
            s.setPerformancePreferences(0, 0, 1); // bandwidth > latency > conn-time
        } catch (Exception ignored) {}
    }

    private void safeClose(Socket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }
}
