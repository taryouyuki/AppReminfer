package com.taryo.appalarm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    private List<HistoryRecord> records;
    private HistoryAdapter adapter;
    private TextView tvTotal;
    private TextView tvStopped;
    private TextView tvSnoozed;
    private TextView tvInsight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicThemeHelper.applyToActivity(this);
        setContentView(R.layout.activity_history);

        tvTotal = findViewById(R.id.tv_stat_total);
        tvStopped = findViewById(R.id.tv_stat_stopped);
        tvSnoozed = findViewById(R.id.tv_stat_snoozed);
        tvInsight = findViewById(R.id.tv_oversleep_insight);
        ListView listView = findViewById(R.id.list_history);
        Button btnClear = findViewById(R.id.btn_clear_history);

        loadData();

        adapter = new HistoryAdapter();
        listView.setAdapter(adapter);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("確認")
                        .setMessage("履歴をすべて消去しますか？")
                        .setPositiveButton("消去", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
                                HistoryRecord.clearAll(prefs);
                                loadData();
                                adapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("キャンセル", null)
                        .show();
            }
        });
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        records = HistoryRecord.loadAll(prefs);

        Map<String, Integer> stats = HistoryRecord.calculateStats(records);
        tvTotal.setText(String.valueOf(stats.get("total")));
        tvStopped.setText(String.valueOf(stats.get("stop")));
        tvSnoozed.setText(String.valueOf(stats.get("snooze")));

        String insight = HistoryRecord.findMostSnoozedAlarm(records);
        tvInsight.setText(insight);
    }

    private class HistoryAdapter extends BaseAdapter {
        private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN);

        @Override
        public int getCount() {
            return records.size();
        }

        @Override
        public Object getItem(int position) {
            return records.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(HistoryActivity.this)
                        .inflate(R.layout.item_history, parent, false);
            }

            HistoryRecord r = records.get(position);
            TextView tvTime = convertView.findViewById(R.id.history_time);
            TextView tvLabel = convertView.findViewById(R.id.history_label);
            TextView tvBadge = convertView.findViewById(R.id.history_badge);

            tvTime.setText(sdf.format(new Date(r.timestamp)));
            tvLabel.setText(r.label);

            if (HistoryRecord.ACTION_STOP.equals(r.action)) {
                tvBadge.setText("停止");
                tvBadge.setTextColor(0xFF065F46);
                tvBadge.setBackgroundColor(0xFFD1FAE5);
            } else if (HistoryRecord.ACTION_SNOOZE.equals(r.action)) {
                tvBadge.setText("スヌーズ");
                tvBadge.setTextColor(0xFF92400E);
                tvBadge.setBackgroundColor(0xFFFEF3C7);
            } else if (HistoryRecord.ACTION_AUTO.equals(r.action)) {
                tvBadge.setText("自動起動");
                tvBadge.setTextColor(0xFF3730A3);
                tvBadge.setBackgroundColor(0xFFE0E7FF);
            } else {
                tvBadge.setText("通知");
                tvBadge.setTextColor(0xFF1E40AF);
                tvBadge.setBackgroundColor(0xFFDBEAFE);
            }

            return convertView;
        }
    }
}
