package com.willykez.willyshare;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private List<String[]> records;
    private HistoryAdapter adapter;
    private ListView lvHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = new DatabaseHelper(this);
        lvHistory = findViewById(R.id.lvHistory);
        Button btnClear = findViewById(R.id.btnClearHistory);

        loadHistory();

        btnClear.setOnClickListener(v -> {
            AnimUtils.buttonPress(v);
            db.getWritableDatabase().execSQL("DELETE FROM " + DatabaseHelper.TABLE_NAME);
            loadHistory();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadHistory() {
        records = db.getAllHistory();
        adapter = new HistoryAdapter();
        lvHistory.setAdapter(adapter);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private class HistoryAdapter extends BaseAdapter {
        @Override public int getCount() { return records.isEmpty() ? 1 : records.size(); }
        @Override public Object getItem(int pos) { return pos < records.size() ? records.get(pos) : null; }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (records.isEmpty()) {
                // Empty state — use a plain TextView, not an inflated item_history
                TextView empty = new TextView(HistoryActivity.this);
                empty.setText("No transfers yet.\nSend or receive a file to see history here.");
                empty.setTextColor(ContextCompat.getColor(HistoryActivity.this,
                        R.color.ws_on_surface_dim));
                empty.setTextSize(15f);
                empty.setGravity(android.view.Gravity.CENTER);
                empty.setPadding(32, 80, 32, 0);
                return empty;
            }

            // When transitioning from empty→items the recycler may hand us the
            // old empty TextView — discard it to avoid class-cast issues.
            if (convertView == null || convertView instanceof TextView) {
                convertView = LayoutInflater.from(HistoryActivity.this)
                        .inflate(R.layout.item_history, parent, false);
            }

            String[] r = records.get(pos);
            // columns: [id, fileName, fileSize, bytesTransferred, status, isSent]
            String  name        = r[1];
            long    fileSize    = safeLong(r[2]);
            long    transferred = safeLong(r[3]);
            String  status      = r[4];
            boolean isSent      = "1".equals(r[5]);
            int pct = fileSize > 0 ? (int)((transferred * 100) / fileSize) : 0;

            TextView tvIcon   = convertView.findViewById(R.id.tvHistIcon);
            TextView tvName   = convertView.findViewById(R.id.tvHistFileName);
            TextView tvSize   = convertView.findViewById(R.id.tvHistSize);
            TextView tvStatus = convertView.findViewById(R.id.tvHistStatus);

            tvIcon.setText(isSent ? "📤" : "📥");
            tvName.setText(name);
            tvSize.setText(FileUtils.formatSize(transferred)
                    + " / " + FileUtils.formatSize(fileSize)
                    + "  (" + pct + "%)");

            String statusLabel;
            int statusColor;
            switch (status) {
                case "done":      statusLabel = "done";      statusColor = 0xFF1B9E4B; break;
                case "cancelled": statusLabel = "cancelled"; statusColor = 0xFFBA1A1A; break;
                case "sending":   statusLabel = "sending";   statusColor = 0xFF6B38D4; break;
                case "receiving": statusLabel = "receiving"; statusColor = 0xFF6B38D4; break;
                default:          statusLabel = status;
                    statusColor = ContextCompat.getColor(
                            HistoryActivity.this, R.color.ws_on_surface_dim);
                    break;
            }
            tvStatus.setText(statusLabel);
            tvStatus.setTextColor(statusColor);

            convertView.setAlpha(0f);
            convertView.animate().alpha(1f).setStartDelay(pos * 50L).setDuration(300).start();

            return convertView;
        }

        private long safeLong(String s) {
            try { return Long.parseLong(s); }
            catch (Exception e) { return 0L; }
        }
    }
}
