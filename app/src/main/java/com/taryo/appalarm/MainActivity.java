package com.taryo.appalarm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.ImageView;
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
    private String appliedThemeKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeHelper.applyToActivity(this);
        super.onCreate(savedInstanceState);
        appliedThemeKey = DynamicThemeHelper.themeKey(this);
        setContentView(R.layout.activity_main);

        NotificationHelper.createChannels(this);
        requestAlarmPermissions();

        ListView listView = findViewById(R.id.alarm_list);
        chipGroupTags = findViewById(R.id.chip_group_tags);
        tvNextSummary = findViewById(R.id.tv_next_summary);
        Button btnAdd = findViewById(R.id.btn_add_alarm);
        Button btnHistory = findViewById(R.id.btn_history);
        Button btnSettings = findViewById(R.id.btn_settings);

        findViewById(R.id.btn_quick_1).setOnClickListener(v -> createQuickAlarm(1));
        findViewById(R.id.btn_quick_5).setOnClickListener(v -> createQuickAlarm(5));
        findViewById(R.id.btn_quick_10).setOnClickListener(v -> createQuickAlarm(10));
        findViewById(R.id.btn_quick_30).setOnClickListener(v -> createQuickAlarm(30));

        adapter = new AlarmAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AlarmEntry entry = displayedAlarms.get(position);
            openAlarmEditor(entry.id);
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

    private void requestAlarmPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 410);
            return;
        }
        requestExactAlarmPermission();
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager am = getSystemService(android.app.AlarmManager.class);
            if (am != null && !am.canScheduleExactAlarms()) {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
                catch (Exception ignored) { }
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 410) requestExactAlarmPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!DynamicThemeHelper.themeKey(this).equals(appliedThemeKey)) {
            recreate();
            return;
        }
        loadAlarmsAndRefresh();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager am = getSystemService(android.app.AlarmManager.class);
            if (am != null && am.canScheduleExactAlarms()) {
                AlarmReceiver.rescheduleAll(this, AlarmEntry.loadAll(getSharedPreferences("appalarm_prefs", MODE_PRIVATE)));
                requestFullScreenPermissionIfNeeded();
            }
        }
    }

    private void requestFullScreenPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 34) return;
        android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        if (manager != null && !manager.canUseFullScreenIntent() && !prefs.getBoolean("full_screen_prompted", false)) {
            prefs.edit().putBoolean("full_screen_prompted", true).apply();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) { }
        }
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

    private void createQuickAlarm(int minutes) {
        long triggerAt = System.currentTimeMillis() + minutes * 60_000L;
        java.util.Calendar when = java.util.Calendar.getInstance();
        when.setTimeInMillis(triggerAt);

        AlarmEntry entry = new AlarmEntry();
        entry.id = (int) System.currentTimeMillis();
        entry.hour = when.get(java.util.Calendar.HOUR_OF_DAY);
        entry.minute = when.get(java.util.Calendar.MINUTE);
        entry.year = when.get(java.util.Calendar.YEAR);
        entry.month = when.get(java.util.Calendar.MONTH) + 1;
        entry.day = when.get(java.util.Calendar.DAY_OF_MONTH);
        entry.mode = AlarmEntry.MODE_ONCE;
        entry.label = minutes + "分後のアラーム";
        entry.oneShotAtMillis = triggerAt;
        entry.alarmMode = true;
        entry.standaloneAlarm = true;
        entry.enabled = true;

        allAlarms.add(entry);
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        AlarmEntry.saveAll(prefs, allAlarms);
        AlarmReceiver.rescheduleAll(this, allAlarms);
        loadAlarmsAndRefresh();
        android.widget.Toast.makeText(this, minutes + "分後にアラームを設定しました", android.widget.Toast.LENGTH_SHORT).show();
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
            convertView.setClickable(true);
            convertView.setFocusable(true);
            convertView.setContentDescription("編集: " + (entry.label == null || entry.label.isEmpty() ? "アラーム" : entry.label));
            convertView.setOnClickListener(v -> openAlarmEditor(entry.id));
            TextView tvTime = convertView.findViewById(R.id.item_time);
            TextView tvLabel = convertView.findViewById(R.id.item_label);
            TextView tvSummary = convertView.findViewById(R.id.item_summary);
            TextView tvBadge = convertView.findViewById(R.id.item_group_badge);
            SwitchCompat switchCompat = convertView.findViewById(R.id.item_switch);
            ImageView ivIcon = convertView.findViewById(R.id.item_app_icon);

            // 対象アプリのアイコンを表示（取得できなければ既定のベルアイコン）
            Drawable appIcon = null;
            if (entry.pkg != null && !entry.pkg.isEmpty()) {
                try {
                    appIcon = getPackageManager().getApplicationIcon(entry.pkg);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (appIcon != null) {
                ivIcon.setImageTintList(null);
                ivIcon.setImageDrawable(appIcon);
            } else {
                ivIcon.setImageResource(R.drawable.ic_tile_alarm);
                ivIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        com.google.android.material.color.MaterialColors.getColor(ivIcon,
                                androidx.appcompat.R.attr.colorPrimary)));
            }

            tvTime.setText(NextTime.timeText(entry));
            tvLabel.setText(entry.label != null && !entry.label.isEmpty() ? entry.label : "アプリ未選択");
            tvSummary.setText(NextTime.daySummary(entry) + (entry.alarmMode ? " ・ アラーム" : (entry.autoOpen ? " ・ 自動起動" : "")));
            ivIcon.setOnClickListener(v -> openAlarmEditor(entry.id));
            tvTime.setOnClickListener(v -> openAlarmEditor(entry.id));
            tvLabel.setOnClickListener(v -> openAlarmEditor(entry.id));
            tvSummary.setOnClickListener(v -> openAlarmEditor(entry.id));
            tvBadge.setOnClickListener(v -> openAlarmEditor(entry.id));

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

    private void openAlarmEditor(int alarmId) {
        Intent intent = new Intent(MainActivity.this, EditActivity.class);
        intent.putExtra("alarm_id", alarmId);
        startActivity(intent);
    }
}
