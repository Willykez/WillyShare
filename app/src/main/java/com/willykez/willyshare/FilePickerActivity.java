package com.willykez.willyshare;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * FilePickerActivity
 *
 * FIXED:
 *  - No longer auto-opens picker on onCreate. Screen is shown first so user
 *    sees the "Add Files" button and can tap it intentionally.
 *  - "Add more" now correctly accumulates files from multiple picker sessions
 *    into the same list without losing previous selections.
 *  - TransferService is NOT started here — TransferActivity handles that.
 *  - remoteIp is forwarded cleanly to TransferActivity.
 *  - Empty file list shows a clear empty state, not a crash.
 *  - File deduplication: same path can't be added twice.
 */
public class FilePickerActivity extends AppCompatActivity {

    private static final int REQ_PICK = 10;

    private ListView     lvFiles;
    private TextView     tvSelectedCount, tvSelectedSize, tvEmptyHint;
    private Button       btnPickMore, btnSendSelected;
    private FileAdapter  adapter;

    private final List<String>  filePaths = new ArrayList<>();
    private final List<String>  fileNames = new ArrayList<>();
    private final List<Boolean> selected  = new ArrayList<>();
    private String remoteIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);

        remoteIp = getIntent().getStringExtra("remoteIp");
        if (remoteIp == null || remoteIp.isEmpty()) remoteIp = HotspotManager.HOST_IP;

        lvFiles         = findViewById(R.id.lvFiles);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        tvSelectedSize  = findViewById(R.id.tvSelectedSize);
        tvEmptyHint     = findViewById(R.id.tvEmptyHint);
        btnPickMore     = findViewById(R.id.btnPickMore);
        btnSendSelected = findViewById(R.id.btnSendSelected);

        adapter = new FileAdapter();
        lvFiles.setAdapter(adapter);

        // Item tap toggles selection
        lvFiles.setOnItemClickListener((parent, view, pos, id) -> {
            selected.set(pos, !selected.get(pos));
            adapter.notifyDataSetChanged();
            updateSummary();
        });

        btnPickMore.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            openSystemPicker();
        });

        btnSendSelected.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(this::confirmAndSend, 120);
        });

        updateSummary();
        // Show empty state hint
        showEmptyState(true);
    }

    // ── System file picker ────────────────────────────────────────────────────

    private void openSystemPicker() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(pick, "Select files to send"), REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;

        int addedCount = 0;
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                if (addUri(data.getClipData().getItemAt(i).getUri())) addedCount++;
            }
        } else if (data.getData() != null) {
            if (addUri(data.getData())) addedCount++;
        }

        adapter.notifyDataSetChanged();
        updateSummary();
        if (addedCount > 0) showEmptyState(false);

        if (addedCount > 0) {
            Toast.makeText(this, addedCount + " file(s) added", Toast.LENGTH_SHORT).show();
        }
    }

    /** @return true if this URI was newly added (false if duplicate) */
    private boolean addUri(Uri uri) {
        String path = resolveToPath(uri);
        if (path == null) return false;
        // Deduplication
        if (filePaths.contains(path)) return false;
        String name = FileUtils.getFileNameFromUri(this, uri);
        filePaths.add(path);
        fileNames.add(name != null ? name : new File(path).getName());
        selected.add(true); // new files are selected by default
        return true;
    }

    private String resolveToPath(Uri uri) {
        if ("file".equals(uri.getScheme())) return uri.getPath();
        // Try MediaStore column
        String[] proj = {MediaStore.Files.FileColumns.DATA};
        try (Cursor c = getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                if (col >= 0) {
                    String p = c.getString(col);
                    if (p != null && !p.isEmpty()) return p;
                }
            }
        } catch (Exception ignored) {}
        // Fallback: copy content to app cache
        try {
            String name = FileUtils.getFileNameFromUri(this, uri);
            if (name == null) name = "file_" + System.currentTimeMillis();
            File dest = new File(getCacheDir(), name);
            try (java.io.InputStream  in  = getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                byte[] buf = new byte[32768];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    private void confirmAndSend() {
        ArrayList<String> toSend = new ArrayList<>();
        for (int i = 0; i < filePaths.size(); i++) {
            if (selected.get(i)) toSend.add(filePaths.get(i));
        }
        if (toSend.isEmpty()) {
            Toast.makeText(this, "Select at least one file", Toast.LENGTH_SHORT).show();
            return;
        }

        // Go to TransferActivity — it starts the engine AND the service
        Intent ui = new Intent(this, TransferActivity.class);
        ui.putExtra("mode", "send");
        ui.putStringArrayListExtra("files", toSend);
        ui.putExtra("remoteIp", remoteIp);
        startActivity(ui);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    // ── Summary bar ──────────────────────────────────────────────────────────

    private void updateSummary() {
        long total = 0;
        int  count = 0;
        for (int i = 0; i < filePaths.size(); i++) {
            if (selected.get(i)) {
                total += FileUtils.getFileSize(filePaths.get(i));
                count++;
            }
        }
        tvSelectedCount.setText(count + " item" + (count == 1 ? "" : "s") + " selected");
        tvSelectedSize.setText("Total: " + FileUtils.formatSize(total));
        btnSendSelected.setEnabled(count > 0);
    }

    private void showEmptyState(boolean show) {
        if (tvEmptyHint != null) tvEmptyHint.setVisibility(show ? View.VISIBLE : View.GONE);
        lvFiles.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class FileAdapter extends BaseAdapter {
        @Override public int     getCount()          { return filePaths.size(); }
        @Override public String  getItem(int pos)    { return filePaths.get(pos); }
        @Override public long    getItemId(int pos)  { return pos; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null)
                cv = LayoutInflater.from(FilePickerActivity.this)
                        .inflate(R.layout.item_file, parent, false);

            TextView tvIcon = cv.findViewById(R.id.tvFileIcon);
            TextView tvName = cv.findViewById(R.id.tvFileName);
            TextView tvSize = cv.findViewById(R.id.tvFileSize);
            CheckBox cb     = cv.findViewById(R.id.cbFile);

            String name = fileNames.get(pos);
            tvName.setText(name);
            tvSize.setText(FileUtils.formatSize(FileUtils.getFileSize(filePaths.get(pos))));
            cb.setChecked(selected.get(pos));
            if (tvIcon != null) tvIcon.setText(iconFor(name));

            cv.setAlpha(0f);
            cv.animate().alpha(1f).setStartDelay(pos * 35L).setDuration(220).start();
            return cv;
        }

        private String iconFor(String name) {
            if (name == null) return "📄";
            String n = name.toLowerCase();
            if (n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".avi")||n.endsWith(".mov")) return "🎬";
            if (n.endsWith(".mp3")||n.endsWith(".aac")||n.endsWith(".flac")||n.endsWith(".ogg")) return "🎵";
            if (n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".gif")) return "🖼️";
            if (n.endsWith(".pdf")) return "📕";
            if (n.endsWith(".zip")||n.endsWith(".rar")||n.endsWith(".7z")) return "📦";
            if (n.endsWith(".apk")) return "📱";
            return "📄";
        }
    }
}
