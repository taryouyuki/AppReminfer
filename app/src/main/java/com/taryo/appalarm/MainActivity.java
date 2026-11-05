package com.taryo.appalarm;

import android.content.Intent;
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
import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private List<AlarmEntry> allAlarms = new ArrayList<>();
    private List<AlarmEntry> displayedAlarms = new ArrayList<>();
    private AlarmAdapter adapter;
    private ChipGroup chipGroupTags;
    private TextView tvNextSummary;
    private String selectedTag = null; // null = all

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicThemeHelper.applyToActivity(this);
        setContentView(R.layout.activity_main);

        NotificationHelper.createChannels(this);

        ListView listView = findViewById(R.id.alarm_list);
        chipGroupTags = findViewById(R.id.chip_group_tags);
        tvNextSummary = findViewById(R.id.tv_next_summary);
        Button btnAdd = findViewById(R.id.btn_add_alarm);
        Button btnHistory = findViewById(R.id.btn_history);
        Button btnSettings = findViewById(R.id.btn_settings);

        adapter = new AlarmAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AlarmEntry entry = displayedAlarms.get(position);
            Intent intent = new Intent(MainActivity.this, EditActivity.class);
            intent.putExtra("alarm_id", entry.id);
            startActivity(intent);
        });

        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EditActivity.class);
            startActivity(intent);
        });

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAlarmsAndRefresh();
    }

    private void loadAlarmsAndRefresh() {
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        allAlarms = AlarmEntry.loadAll(prefs);

        refreshGroupChips();
        applyFilter();
        updateNextSummary();

        WidgetProvider.updateAllWidgets(this);
        AlarmTileService.requestTileUpdate(this);
    }

    private void refreshGroupChips() {
        chipGroupTags.removeAllViews();

        // 「すべて」チップ
        Chip allChip = new Chip(this);
        allChip.setText("すべて (" + allAlarms.size() + ")");
        allChip.setCheckable(true);
        allChip.setChecked(selectedTag == null);
        allChip.setOnClickListener(v -> {
            selectedTag = null;
            applyFilter();
        });
        chipGroupTags.addView(allChip);

        // 各グループチップ
        Set<String> groups = new HashSet<>();
        for (AlarmEntry a : allAlarms) {
            if (a.group != null && !a.group.trim().isEmpty()) {
                groups.add(a.group.trim());
            }
        }

        for (String groupName : groups) {
            int count = 0;
            for (AlarmEntry a : allAlarms) {
                if (groupName.equals(a.group)) count++;
            }
            Chip chip = new Chip(this);
            chip.setText(groupName + " (" + count + ")");
            chip.setCheckable(true);
            chip.setChecked(groupName.equals(selectedTag));
            final String currentGroupName = groupName;
            chip.setOnClickListener(v -> {
                selectedTag = currentGroupName;
                applyFilter();
            });
            chipGroupTags.addView(chip);
        }
    }

    private void applyFilter() {
        displayedAlarms.clear();
        if (selectedTag == null) {
            displayedAlarms.addAll(allAlarms);
        } else {
            for (AlarmEntry a : allAlarms) {
                if (selectedTag.equals(a.group)) {
                    displayedAlarms.add(a);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void updateNextSummary() {
        long now = System.currentTimeMillis();
        AlarmEntry earliest = null;
        long min = Long.MAX_VALUE;

        for (AlarmEntry e : allAlarms) {
            if (!e.enabled) continue;
            long next = NextTime.nextTrigger(e, now);
            if (next > now && next < min) {
                min = next;
                earliest = e;
            }
        }

        if (earliest != null) {
            tvNextSummary.setText("次回予定: " + NextTime.timeText(earliest) + " (" + earliest.label + ")");
        } else {
            tvNextSummary.setText("次回予定: 有効なアラームはありません");
        }
    }

    private class AlarmAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return displayedAlarms.size();
        }

        @Override
        public Object getItem(int position) {
            return displayedAlarms.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_alarm, parent, false);
            }

            final AlarmEntry entry = displayedAlarms.get(position);
            TextView tvTime = convertView.findViewById(R.id.item_time);
            TextView tvLabel = convertView.findViewById(R.id.item_label);
            TextView tvSummary = convertView.findViewById(R.id.item_summary);
            TextView tvBadge = convertView.findViewById(R.id.item_group_badge);
            SwitchCompat switchCompat = convertView.findViewById(R.id.item_switch);

            tvTime.setText(NextTime.timeText(entry));
            tvLabel.setText(entry.label != null && !entry.label.isEmpty() ? entry.label : "アプリ未選択");
            tvSummary.setText(NextTime.daySummary(entry) + (entry.alarmMode ? " ・ アラーム" : (entry.autoOpen ? " ・ 自動起動" : "")));

            if (entry.group != null && !entry.group.trim().isEmpty()) {
                tvBadge.setText(entry.group);
                tvBadge.setVisibility(View.VISIBLE);
            } else {
                tvBadge.setVisibility(View.GONE);
            }

            switchCompat.setOnCheckedChangeListener(null);
            switchCompat.setChecked(entry.enabled);
            switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
                entry.enabled = isChecked;
                SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
                AlarmEntry.saveAll(prefs, allAlarms);
                AlarmReceiver.rescheduleAll(MainActivity.this, allAlarms);
                updateNextSummary();
                WidgetProvider.updateAllWidgets(MainActivity.this);
                AlarmTileService.requestTileUpdate(MainActivity.this);
            });

            return convertView;
        }
    }
}
