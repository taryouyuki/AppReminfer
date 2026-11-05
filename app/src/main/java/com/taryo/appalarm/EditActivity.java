package com.taryo.appalarm;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EditActivity extends AppCompatActivity {

    private TimePicker timePicker;
    private Spinner spinnerApps;
    private Spinner spinnerRepeat;
    private AutoCompleteTextView editGroupTag;
    private CheckBox cbSkipHolidays;
    private CheckBox cbAutoOpen;
    private CheckBox cbAlarmMode;

    private List<AppItem> installedApps = new ArrayList<>();
    private AlarmEntry entry;
    private boolean isNew = false;

    private static class AppItem {
        String pkg;
        String label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicThemeHelper.applyToActivity(this);
        setContentView(R.layout.activity_edit);

        timePicker = findViewById(R.id.edit_time_picker);
        timePicker.setIs24HourView(true);
        spinnerApps = findViewById(R.id.spinner_apps);
        spinnerRepeat = findViewById(R.id.spinner_repeat_mode);
        editGroupTag = findViewById(R.id.edit_group_tag);
        cbSkipHolidays = findViewById(R.id.cb_skip_holidays);
        cbAutoOpen = findViewById(R.id.cb_auto_open);
        cbAlarmMode = findViewById(R.id.cb_alarm_mode);

        Button btnSave = findViewById(R.id.btn_save);
        Button btnDelete = findViewById(R.id.btn_delete);

        loadInstalledApps();
        AppSpinnerAdapter appAdapter = new AppSpinnerAdapter();
        spinnerApps.setAdapter(appAdapter);

        // 繰り返しモード
        String[] repeatModes = new String[]{"毎日", "1回だけ", "平日のみ (月〜金)"};
        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, repeatModes);
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRepeat.setAdapter(repeatAdapter);

        // 既存のグループ名を収集してAutoComplete候補にする
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        List<AlarmEntry> allAlarms = AlarmEntry.loadAll(prefs);
        Set<String> groups = new HashSet<>();
        for (AlarmEntry a : allAlarms) {
            if (a.group != null && !a.group.trim().isEmpty()) {
                groups.add(a.group.trim());
            }
        }
        groups.add("朝");
        groups.add("仕事");
        groups.add("夜");
        groups.add("休日");
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(groups));
        editGroupTag.setAdapter(groupAdapter);

        int alarmId = getIntent().getIntExtra("alarm_id", -1);
        if (alarmId == -1) {
            isNew = true;
            entry = new AlarmEntry();
            entry.id = (int) System.currentTimeMillis();
            entry.hour = 7;
            entry.minute = 0;
            btnDelete.setVisibility(View.GONE);
        } else {
            isNew = false;
            for (AlarmEntry e : allAlarms) {
                if (e.id == alarmId) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                finish();
                return;
            }
        }

        // 初期値の反映
        timePicker.setHour(entry.hour);
        timePicker.setMinute(entry.minute);
        cbSkipHolidays.setChecked(entry.skipHolidays);
        cbAutoOpen.setChecked(entry.autoOpen);
        cbAlarmMode.setChecked(entry.alarmMode);
        editGroupTag.setText(entry.group != null ? entry.group : "");

        for (int i = 0; i < installedApps.size(); i++) {
            if (installedApps.get(i).pkg.equals(entry.pkg)) {
                spinnerApps.setSelection(i);
                break;
            }
        }

        if (entry.mode == AlarmEntry.MODE_ONCE) {
            spinnerRepeat.setSelection(1);
        } else if (entry.mode == AlarmEntry.MODE_WEEKLY && entry.days == 0b0011111) {
            spinnerRepeat.setSelection(2);
        } else {
            spinnerRepeat.setSelection(0);
        }

        btnSave.setOnClickListener(v -> saveAlarm());
        btnDelete.setOnClickListener(v -> deleteAlarm());
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo info : packages) {
            if (pm.getLaunchIntentForPackage(info.packageName) != null) {
                AppItem item = new AppItem();
                item.pkg = info.packageName;
                item.label = pm.getApplicationLabel(info).toString();
                item.icon = pm.getApplicationIcon(info);
                installedApps.add(item);
            }
        }
    }

    private void saveAlarm() {
        entry.hour = timePicker.getHour();
        entry.minute = timePicker.getMinute();

        int selectedAppIdx = spinnerApps.getSelectedItemPosition();
        if (selectedAppIdx >= 0 && selectedAppIdx < installedApps.size()) {
            AppItem app = installedApps.get(selectedAppIdx);
            entry.pkg = app.pkg;
            entry.label = app.label;
        }

        entry.group = editGroupTag.getText().toString().trim();
        entry.skipHolidays = cbSkipHolidays.isChecked();
        entry.autoOpen = cbAutoOpen.isChecked();
        entry.alarmMode = cbAlarmMode.isChecked();
        entry.enabled = true;

        int repeatIdx = spinnerRepeat.getSelectedItemPosition();
        if (repeatIdx == 1) {
            entry.mode = AlarmEntry.MODE_ONCE;
        } else if (repeatIdx == 2) {
            entry.mode = AlarmEntry.MODE_WEEKLY;
            entry.days = 0b0011111; // 月〜金
        } else {
            entry.mode = AlarmEntry.MODE_DAILY;
            entry.days = 0x7f;
        }

        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
        if (isNew) {
            list.add(entry);
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == entry.id) {
                    list.set(i, entry);
                    break;
                }
            }
        }
        AlarmEntry.saveAll(prefs, list);
        AlarmReceiver.rescheduleAll(this, list);
        WidgetProvider.updateAllWidgets(this);
        AlarmTileService.requestTileUpdate(this);

        Toast.makeText(this, "アラームを保存しました", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void deleteAlarm() {
        new AlertDialog.Builder(this)
                .setTitle("削除の確認")
                .setMessage("このアラームを削除しますか？")
                .setPositiveButton("削除", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
                    List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).id == entry.id) {
                            list.remove(i);
                            break;
                        }
                    }
                    AlarmEntry.saveAll(prefs, list);
                    AlarmReceiver.cancelAlarm(this, entry.id);
                    AlarmReceiver.rescheduleAll(this, list);
                    WidgetProvider.updateAllWidgets(this);
                    AlarmTileService.requestTileUpdate(this);
                    Toast.makeText(this, "アラームを削除しました", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private class AppSpinnerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return installedApps.size();
        }

        @Override
        public Object getItem(int position) {
            return installedApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(EditActivity.this)
                        .inflate(android.R.layout.activity_list_item, parent, false);
            }
            AppItem item = installedApps.get(position);
            TextView tv = convertView.findViewById(android.R.id.text1);
            ImageView iv = convertView.findViewById(android.R.id.icon);
            tv.setText(item.label);
            iv.setImageDrawable(item.icon);
            return convertView;
        }
    }
}
