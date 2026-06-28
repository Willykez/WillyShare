package com.willykez.willyshare;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TransferEngine {
    private static final String TAG = "TransferEngine";

    public static final int PORT         = 8888; // data ports 8888..8891
    public static final int CONTROL_PORT = 8893; // filelist announcement
    private static final int THREAD_COUNT = 4;
    private static final int BUFFER_SIZE  = 256 * 1024; // 256 KB — reliable for all devices
    private static final int SOCKET_BUF   = 2 * 1024 * 1024;
    private static final int CONNECT_RETRIES  = 6;
    private static final int CONNECT_RETRY_MS = 600;
    private static final int CONNECT_TIMEOUT  = 5000;

    public static final byte CMD_CHUNK    = 0x01;
    public static final byte CMD_FILELIST = 0x02;
    public static final byte CMD_ACK      = 0x06;

    // UI message codes
    public static final int MSG_PROGRESS    = 100;
    public static final int MSG_SPEED       = 101;
    public static final int MSG_DONE        = 102;
    public static final int MSG_ERROR       = 103;
    public static final int MSG_FILE_START  = 104;
    public static final int MSG_RX_PROGRESS = 105;
    public static final int MSG_CANCELLED   = 106;
    public static final int MSG_FILE_DONE   = 107;
    public static final int MSG_BYTES       = 108;

    private final Context        context;
    private final Handler        uiHandler;
    private final DatabaseHelper db;
    private final SpeedCalculator speedCalc = new SpeedCalculator();

    private volatile boolean isPaused    = false;
    private volatile boolean isCancelled = false;
    private ExecutorService  pool;

    // ── Receiver state ────────────────────────────────────────────────────────
    private final ConcurrentHashMap<String, AtomicLong>   rxBytesMap   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>         rxTotalMap   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>         rxHistoryIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> rxDoneMap   = new ConcurrentHashMap<>();
    // track expected thread count per file so we know when all chunks are written
    private final ConcurrentHashMap<String, Integer>      rxThreadsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> rxThreadsDone= new ConcurrentHashMap<>();
    // server sockets held so stopReceiving() can close them
    private final List<ServerSocket> serverSockets = new CopyOnWriteArrayList<>();

    // ── Sender state ──────────────────────────────────────────────────────────
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private volatile long    grandTotalBytes = 0;

    public TransferEngine(Context ctx, Handler handler) {
        this.context   = ctx.getApplicationContext();
        this.uiHandler = handler;
        this.db        = new DatabaseHelper(ctx);
    }

    public void setPaused(boolean p) { isPaused = p; }

    public void cancel() {
        isCancelled = true;
        closeServerSockets();
        if (pool != null) pool.shutdownNow();
        uiHandler.postDelayed(() -> uiHandler.obtainMessage(MSG_CANCELLED).sendToTarget(), 150);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SENDER
    // ═══════════════════════════════════════════════════════════════════════════

    public void sendFiles(List<String> filePaths, String remoteIp, int basePort) {
        isCancelled = false;
        isPaused    = false;
        totalBytesSent.set(0);
        grandTotalBytes = 0;
        for (String p : filePaths) grandTotalBytes += new File(p).length();

        // Use a simple cached pool — we're serialising files anyway
        pool = Executors.newFixedThreadPool(THREAD_COUNT + 2);

        new Thread(() -> {
            // ── 1. Announce file list (with retry, receiver may not be ready yet) ──
            for (int attempt = 0; attempt < 8 && !isCancelled; attempt++) {
                try {
                    announceFileList(filePaths, remoteIp, CONTROL_PORT);
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "announceFileList attempt " + attempt + ": " + e.getMessage());
                    if (attempt == 7) {
                        uiHandler.obtainMessage(MSG_ERROR,
                                "Cannot reach receiver — make sure it is in Receive mode").sendToTarget();
                        return;
                    }
                    sleep(700);
                }
            }

            long sessionStart = System.currentTimeMillis();

            // ── 2. Send each file sequentially ────────────────────────────────
            for (int i = 0; i < filePaths.size() && !isCancelled; i++) {
                sendSingleFile(filePaths.get(i), remoteIp, basePort, i, filePaths.size());
            }

            if (!isCancelled) {
                if (pool != null) pool.shutdown();
                long elapsed = System.currentTimeMillis() - sessionStart;
                String summary = filePaths.size() + " file(s) · "
                        + FileUtils.formatSize(grandTotalBytes) + " · "
                        + formatDuration(elapsed);
                uiHandler.obtainMessage(MSG_DONE, summary).sendToTarget();
            }
        }, "ws-send-coordinator").start();
    }

    private void announceFileList(List<String> paths, String remoteIp, int port) throws Exception {
        try (Socket s = newSocketWithRetry(remoteIp, port)) {
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
            // Wait for receiver ACK so we know the manifest landed
            DataInputStream dis = new DataInputStream(s.getInputStream());
            dis.readByte(); // ACK byte from receiver
        }
    }

    private void sendSingleFile(String path, String remoteIp, int basePort,
                                int fileIndex, int totalFiles) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) return;

        long fileSize  = file.length();
        long historyId = db.insertHistory(file.getName(), fileSize, 0, "sending",
                System.currentTimeMillis(), 1);
        speedCalc.reset();

        AtomicLong fileSent = new AtomicLong(0);
        uiHandler.obtainMessage(MSG_FILE_START, fileIndex, totalFiles, file.getName())
                .sendToTarget();

        // For tiny files use 1 thread; otherwise up to THREAD_COUNT
        int threads = (fileSize < 512 * 1024) ? 1
                : Math.min(THREAD_COUNT, (int)(fileSize / (256 * 1024)));
        if (threads < 1) threads = 1;

        long chunkSize = fileSize / threads;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean anyError = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int  idx    = t;
            final long offset = (long) t * chunkSize;
            final long length = (t == threads - 1) ? (fileSize - offset) : chunkSize;

            pool.submit(() -> {
                Socket sock = null;
                try {
                    sock = newSocketWithRetry(remoteIp, basePort + idx);
                    DataOutputStream dos = new DataOutputStream(sock.getOutputStream());

                    // Write chunk header
                    dos.writeByte(CMD_CHUNK);
                    dos.writeUTF(file.getName());
                    dos.writeLong(fileSize);
                    dos.writeLong(offset);
                    dos.writeLong(length);
                    dos.writeInt(idx);
                    dos.writeInt(fileIndex);
                    dos.writeInt(threads); // receiver needs this to know when file is complete
                    dos.flush();

                    // Wait for receiver ACK before streaming
                    DataInputStream dis = new DataInputStream(sock.getInputStream());
                    dis.readByte();

                    // Stream the chunk
                    byte[] buf = new byte[BUFFER_SIZE];
                    try (FileInputStream fis = new FileInputStream(file)) {
                        long skipped = 0;
                        while (skipped < offset) {
                            long s = fis.skip(offset - skipped);
                            if (s <= 0) break;
                            skipped += s;
                        }
                        long remaining = length;
                        while (remaining > 0 && !isCancelled) {
                            while (isPaused && !isCancelled) sleep(100);
                            int toRead = (int) Math.min(buf.length, remaining);
                            int read   = fis.read(buf, 0, toRead);
                            if (read < 0) break;
                            dos.write(buf, 0, read);
                            remaining -= read;

                            long fSent = fileSent.addAndGet(read);
                            totalBytesSent.addAndGet(read);
                            speedCalc.update(fSent);

                            int pct = (fileSize > 0) ? (int)((fSent * 100L) / fileSize) : 100;
                            uiHandler.obtainMessage(MSG_PROGRESS, pct, 0, file.getName()).sendToTarget();
                            uiHandler.obtainMessage(MSG_SPEED, speedCalc.getSpeedFormatted()).sendToTarget();

                            android.os.Message mb = uiHandler.obtainMessage(MSG_BYTES);
                            mb.obj = new long[]{fSent, fileSize, totalBytesSent.get(), grandTotalBytes};
                            mb.sendToTarget();

                            db.updateProgress(historyId, fSent, "sending");
                        }
                        dos.flush();
                    }
                } catch (Exception e) {
                    if (!isCancelled) {
                        Log.e(TAG, "Send chunk " + idx + ": " + e.getMessage());
                        anyError.set(true);
                        uiHandler.obtainMessage(MSG_ERROR, "Chunk " + idx + " error: " + e.getMessage())
                                .sendToTarget();
                    }
                } finally {
                    safeClose(sock);
                    latch.countDown();
                }
            });
        }

        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (!isCancelled) {
            db.updateProgress(historyId, fileSize, anyError.get() ? "error" : "done");
            uiHandler.obtainMessage(MSG_FILE_DONE, file.getName()).sendToTarget();
        } else {
            db.updateProgress(historyId, fileSent.get(), "cancelled");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  RECEIVER
    // ═══════════════════════════════════════════════════════════════════════════

    public void startReceiving() {
        isCancelled = false;
        isPaused    = false;
        rxBytesMap.clear();
        rxTotalMap.clear();
        rxHistoryIds.clear();
        rxDoneMap.clear();
        rxThreadsMap.clear();
        rxThreadsDone.clear();
        serverSockets.clear();
        speedCalc.reset();

        // One thread per port, plus extras for concurrent file handling
        pool = Executors.newFixedThreadPool(THREAD_COUNT + 4);

        // Control port
        pool.submit(() -> startListening(CONTROL_PORT));
        // Data ports — one ServerSocket per chunk thread
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int port = PORT + t;
            pool.submit(() -> startListening(port));
        }
    }

    private void startListening(int port) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            serverSockets.add(ss);
            Log.d(TAG, "Listening on port " + port);
            while (!isCancelled) {
                Socket client = ss.accept();
                tuneSocket(client);
                final Socket fc = client;
                pool.submit(() -> dispatch(fc));
            }
        } catch (Exception e) {
            if (!isCancelled) Log.e(TAG, "Port " + port + " died: " + e.getMessage());
        } finally {
            if (ss != null) { try { ss.close(); } catch (IOException ignored) {} }
        }
    }

    private void dispatch(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            byte cmd = dis.readByte();
            if      (cmd == CMD_FILELIST) handleFileList(socket, dis);
            else if (cmd == CMD_CHUNK)    handleChunk(socket, dis);
            else                          safeClose(socket);
        } catch (Exception e) {
            if (!isCancelled) Log.e(TAG, "dispatch: " + e.getMessage());
            safeClose(socket);
        }
    }

    /** Receives the file manifest from sender and ACKs it. */
    private void handleFileList(Socket socket, DataInputStream dis) throws Exception {
        int  totalFiles  = dis.readInt();
        long totalBytes  = dis.readLong();

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

        // ACK the manifest so sender starts sending
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeByte(CMD_ACK);
        dos.flush();
        safeClose(socket);

        uiHandler.obtainMessage(MSG_FILE_START, 0, totalFiles, "").sendToTarget();
    }

    /** Receives one chunk from sender, writes it at the correct offset. */
    private void handleChunk(Socket socket, DataInputStream dis) {
        String outPath = null;
        String fileName = null;
        try {
            fileName  = dis.readUTF();
            long fileSize   = dis.readLong();
            long offset     = dis.readLong();
            long length     = dis.readLong();
            int  threadIdx  = dis.readInt();
            int  fileIndex  = dis.readInt();
            int  numThreads = dis.readInt();

            // ACK immediately so sender can start streaming
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            dos.writeByte(CMD_ACK);
            dos.flush();

            // Populate maps in case filelist arrived after first chunk
            rxTotalMap.putIfAbsent(fileName, fileSize);
            rxBytesMap.putIfAbsent(fileName, new AtomicLong(0));
            rxDoneMap.putIfAbsent(fileName, new AtomicBoolean(false));
            rxThreadsMap.putIfAbsent(fileName, numThreads);
            rxThreadsDone.putIfAbsent(fileName, new AtomicInteger(0));

            outPath = FileUtils.getReceiveDir() + File.separator + fileName;
            File outFile = new File(outPath);

            // Pre-allocate file — only one thread should do this (use CAS on done flag)
            // We use a dedicated lock object per filename
            synchronized (outPath.intern()) {
                if (!outFile.exists()) {
                    try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
                        raf.setLength(fileSize);
                    }
                    rxHistoryIds.putIfAbsent(fileName,
                            db.insertHistory(fileName, fileSize, 0, "receiving",
                                    System.currentTimeMillis(), 0));
                    Log.d(TAG, "Pre-allocated " + fileName + " (" + fileSize + " bytes)");
                }
            }

            // Read the full chunk — must loop because dis.read() can return partial
            try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
                raf.seek(offset);
                byte[] buf       = new byte[BUFFER_SIZE];
                long   remaining = length;
                while (remaining > 0 && !isCancelled) {
                    while (isPaused && !isCancelled) sleep(100);

                    // CRITICAL: readFully equivalent — keeps looping until we get all bytes
                    int toRead = (int) Math.min(buf.length, remaining);
                    int read   = 0;
                    while (read < toRead) {
                        int r = dis.read(buf, read, toRead - read);
                        if (r < 0) break;
                        read += r;
                    }
                    if (read <= 0) break;

                    raf.write(buf, 0, read);
                    remaining -= read;

                    AtomicLong counter = rxBytesMap.get(fileName);
                    long totalRx = (counter != null) ? counter.addAndGet(read) : read;
                    speedCalc.update(totalRx);

                    Long total = rxTotalMap.get(fileName);
                    int pct = (total != null && total > 0)
                            ? (int)((totalRx * 100L) / total) : 0;

                    uiHandler.obtainMessage(MSG_RX_PROGRESS, pct, fileIndex, fileName).sendToTarget();
                    uiHandler.obtainMessage(MSG_SPEED, speedCalc.getSpeedFormatted()).sendToTarget();

                    android.os.Message mb = uiHandler.obtainMessage(MSG_BYTES);
                    mb.obj = new long[]{totalRx, total != null ? total : fileSize, 0, 0};
                    mb.sendToTarget();

                    Long hid = rxHistoryIds.get(fileName);
                    if (hid != null) db.updateProgress(hid, totalRx, "receiving");
                }
            }

            // Mark this chunk thread done; fire file-done when all threads finished
            AtomicInteger doneThreads = rxThreadsDone.get(fileName);
            Integer expectedThreads   = rxThreadsMap.get(fileName);
            if (doneThreads != null && expectedThreads != null) {
                int nowDone = doneThreads.incrementAndGet();
                if (nowDone >= expectedThreads) {
                    AtomicBoolean fileDoneFlag = rxDoneMap.get(fileName);
                    if (fileDoneFlag != null && fileDoneFlag.compareAndSet(false, true)) {
                        Long total = rxTotalMap.get(fileName);
                        Long hid   = rxHistoryIds.get(fileName);
                        if (hid != null) db.updateProgress(hid, total != null ? total : 0, "done");

                        uiHandler.obtainMessage(MSG_FILE_DONE, fileName).sendToTarget();
                        Log.d(TAG, "File complete: " + fileName);

                        // Check if all files across the session are done
                        checkAllFilesDone();
                    }
                }
            }

        } catch (Exception e) {
            if (!isCancelled) {
                Log.e(TAG, "handleChunk [" + fileName + "]: " + e.getMessage());
                uiHandler.obtainMessage(MSG_ERROR, "Receive error: " + e.getMessage()).sendToTarget();
            }
        } finally {
            safeClose(socket);
        }
    }

    private void checkAllFilesDone() {
        if (rxDoneMap.isEmpty()) return;
        for (AtomicBoolean b : rxDoneMap.values()) {
            if (!b.get()) return;
        }
        long totalRx = 0;
        for (Long sz : rxTotalMap.values()) if (sz != null) totalRx += sz;
        String summary = rxDoneMap.size() + " file(s) · " + FileUtils.formatSize(totalRx);
        uiHandler.obtainMessage(MSG_DONE, summary).sendToTarget();
    }

    public void stopReceiving() {
        isCancelled = true;
        closeServerSockets();
        if (pool != null) pool.shutdownNow();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Socket newSocket(String ip, int port) throws IOException {
        Socket s = new Socket();
        s.connect(new java.net.InetSocketAddress(ip, port), CONNECT_TIMEOUT);
        tuneSocket(s);
        return s;
    }

    private Socket newSocketWithRetry(String ip, int port) throws IOException {
        IOException last = null;
        for (int i = 0; i < CONNECT_RETRIES; i++) {
            try { return newSocket(ip, port); }
            catch (IOException e) {
                last = e;
                Log.w(TAG, "Connect to " + ip + ":" + port + " attempt " + i + " failed");
                sleep(CONNECT_RETRY_MS);
            }
        }
        throw last != null ? last : new IOException("All retries failed: " + ip + ":" + port);
    }

    private void tuneSocket(Socket s) {
        try {
            s.setTcpNoDelay(true);
            s.setSendBufferSize(SOCKET_BUF);
            s.setReceiveBufferSize(SOCKET_BUF);
        } catch (Exception ignored) {}
    }

    private void safeClose(Socket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    private void closeServerSockets() {
        for (ServerSocket ss : serverSockets) {
            try { ss.close(); } catch (IOException ignored) {}
        }
        serverSockets.clear();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        return (ms / 60000) + "m " + ((ms % 60000) / 1000) + "s";
    }
}
