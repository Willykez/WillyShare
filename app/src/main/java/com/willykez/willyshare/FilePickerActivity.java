package com.willykez.willyshare;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilePickerActivity extends AppCompatActivity {
    private static final int REQ_PICK = 10;

    private ListView lvFiles;
    private TextView tvSelectedSize, tvSelectedCount;
    private FileAdapter adapter;
    private final List<String> filePaths  = new ArrayList<>();
    private final List<String> fileNames  = new ArrayList<>();
    private final List<Boolean> selected  = new ArrayList<>();
    private String remoteIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);

        remoteIp = getIntent().getStringExtra("remoteIp");
        if (remoteIp == null) remoteIp = HotspotManager.HOST_IP;

        lvFiles         = findViewById(R.id.lvFiles);
        tvSelectedSize  = findViewById(R.id.tvSelectedSize);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        Button btnPickMore   = findViewById(R.id.btnPickMore);
        Button btnSend       = findViewById(R.id.btnSendSelected);

        adapter = new FileAdapter();
        lvFiles.setAdapter(adapter);

        btnPickMore.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            openPicker();
        });

        btnSend.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            v.postDelayed(this::sendSelectedFiles, 120);
        });

        lvFiles.setOnItemClickListener((p, v, pos, id) -> {
            selected.set(pos, !selected.get(pos));
            adapter.notifyDataSetChanged();
            updateSummary();
        });

        openPicker();
    }

    private void openPicker() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(pick, "Select files"), REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;

        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++)
                addUri(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            addUri(data.getData());
        }
        adapter.notifyDataSetChanged();
        updateSummary();
    }

    private void addUri(Uri uri) {
        String path = resolveUri(uri);
        if (path == null) return;
        String name = FileUtils.getFileNameFromUri(this, uri);
        filePaths.add(path);
        fileNames.add(name);
        selected.add(true);
    }

    private String resolveUri(Uri uri) {
        if ("file".equals(uri.getScheme())) return uri.getPath();
        String[] proj = {MediaStore.Files.FileColumns.DATA};
        try (Cursor c = getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                if (col >= 0) {
                    String p = c.getString(col);
                    if (p != null) return p;
                }
            }
        } catch (Exception ignored) {}
        // Fallback: copy to cache
        try {
            String name = FileUtils.getFileNameFromUri(this, uri);
            File dest = new File(getCacheDir(), name);
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void updateSummary() {
        long total = 0;
        int count = 0;
        for (int i = 0; i < filePaths.size(); i++) {
            if (selected.get(i)) {
                total += FileUtils.getFileSize(filePaths.get(i));
                count++;
            }
        }
        tvSelectedSize.setText(FileUtils.formatSize(total));
        tvSelectedCount.setText("  " + count + " file" + (count == 1 ? "" : "s"));
    }

    private void sendSelectedFiles() {
        ArrayList<String> toSend = new ArrayList<>();
        for (int i = 0; i < filePaths.size(); i++) {
            if (selected.get(i)) toSend.add(filePaths.get(i));
        }
        if (toSend.isEmpty()) {
            Toast.makeText(this, "Select at least one file", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent svc = new Intent(this, TransferService.class);
        svc.setAction(TransferService.ACTION_SEND);
        svc.putStringArrayListExtra(TransferService.EXTRA_FILES, toSend);
        svc.putExtra(TransferService.EXTRA_REMOTE_IP, remoteIp);
        svc.putExtra(TransferService.EXTRA_PORT, HotspotManager.TRANSFER_PORT);
        startForegroundService(svc);

        Intent ui = new Intent(this, TransferActivity.class);
        ui.putExtra("mode", "send");
        ui.putStringArrayListExtra("files", toSend);
        ui.putExtra("remoteIp", remoteIp);
        startActivity(ui);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return filePaths.size(); }
        @Override public String getItem(int pos) { return filePaths.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FilePickerActivity.this)
                        .inflate(R.layout.item_file, parent, false);
            }
            TextView tvName = convertView.findViewById(R.id.tvFileName);
            TextView tvSize = convertView.findViewById(R.id.tvFileSize);
            CheckBox cb     = convertView.findViewById(R.id.cbFile);
            TextView tvIcon = convertView.findViewById(R.id.tvFileIcon);

            String name = fileNames.get(pos);
            tvName.setText(name);
            tvSize.setText(FileUtils.formatSize(FileUtils.getFileSize(filePaths.get(pos))));
            cb.setChecked(selected.get(pos));
            tvIcon.setText(iconForName(name));

            convertView.setAlpha(0f);
            convertView.animate().alpha(1f).setStartDelay(pos * 40L).setDuration(250).start();

            return convertView;
        }

        private String iconForName(String name) {
            if (name == null) return "📄";
            String lower = name.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")) return "🎬";
            if (lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".flac")) return "🎵";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")) return "🖼️";
            if (lower.endsWith(".pdf")) return "📕";
            if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "📦";
            if (lower.endsWith(".apk")) return "📱";
            return "📄";
        }
    }
}
