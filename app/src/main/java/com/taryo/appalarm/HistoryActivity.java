package com.taryo.appalarm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
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
    private String appliedThemeKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeHelper.applyToActivity(this);
        super.onCreate(savedInstanceState);
        appliedThemeKey = DynamicThemeHelper.themeKey(this);
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

    @Override
    protected void onResume() {
        super.onResume();
        if (!DynamicThemeHelper.themeKey(this).equals(appliedThemeKey)) {
            recreate();
        }
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        records = HistoryRecord.loadAll(prefs);

        Map<String, Integer> stats = HistoryRecord.calculateStats(records);
        tvTotal.setText(String.valueOf(stats.get("total")));
        tvStopped.setText(String.valueOf(stats.get("stop")));
        tvSnoozed.setText(String.valueOf(stats.get("snooze")));

        String insight = HistoryRecord.findMostSnoozedAlarm(records);
        java.util.Calendar c = java.util.Calendar.getInstance();
        int[] snoozeByHour = new int[24], triggersByDay = new int[7], snoozeByDay = new int[7];
        for (HistoryRecord r : records) { c.setTimeInMillis(r.timestamp); int h=c.get(java.util.Calendar.HOUR_OF_DAY), d=(c.get(java.util.Calendar.DAY_OF_WEEK)+5)%7;
            if (HistoryRecord.ACTION_NOTIFY.equals(r.action)) triggersByDay[d]++;
            if (HistoryRecord.ACTION_SNOOZE.equals(r.action)) { snoozeByHour[h]++; snoozeByDay[d]++; }
        }
        int worstHour=0, worstDay=0; for(int i=1;i<24;i++) if(snoozeByHour[i]>snoozeByHour[worstHour]) worstHour=i;
        for(int i=1;i<7;i++) if(triggersByDay[i]>0 && (triggersByDay[worstDay]==0 || snoozeByDay[i]*1f/triggersByDay[i]>snoozeByDay[worstDay]*1f/triggersByDay[worstDay])) worstDay=i;
        String[] days={"月","火","水","木","金","土","日"};
        String dayInsight = triggersByDay[worstDay]==0 ? "曜日別分析: まだ記録がありません" : "曜日別: "+days[worstDay]+"曜のスヌーズ率 "+(100*snoozeByDay[worstDay]/triggersByDay[worstDay])+"%";
        tvInsight.setText(insight+"\n"+dayInsight+"\n時間帯: "+String.format(java.util.Locale.JAPAN,"%02d時台",worstHour)+"が最多スヌーズ ("+snoozeByHour[worstHour]+"回)");
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

            int bg;
            int fg;
            if (HistoryRecord.ACTION_STOP.equals(r.action)) {
                tvBadge.setText("停止");
                bg = R.color.badge_stop_bg;
                fg = R.color.badge_stop_fg;
            } else if (HistoryRecord.ACTION_SNOOZE.equals(r.action)) {
                tvBadge.setText("スヌーズ");
                bg = R.color.badge_snooze_bg;
                fg = R.color.badge_snooze_fg;
            } else if (HistoryRecord.ACTION_AUTO.equals(r.action)) {
                tvBadge.setText("自動起動");
                bg = R.color.badge_auto_bg;
                fg = R.color.badge_auto_fg;
            } else if (HistoryRecord.ACTION_IGNORE.equals(r.action)) {
                tvBadge.setText("今回は無視");
                bg = R.color.badge_notify_bg;
                fg = R.color.badge_notify_fg;
            } else {
                tvBadge.setText("通知");
                bg = R.color.badge_notify_bg;
                fg = R.color.badge_notify_fg;
            }
            tvBadge.setTextColor(ContextCompat.getColor(HistoryActivity.this, fg));
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(HistoryActivity.this, bg)));

            return convertView;
        }
    }
}
