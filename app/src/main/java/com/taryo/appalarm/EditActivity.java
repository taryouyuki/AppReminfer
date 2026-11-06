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
import android.widget.SeekBar;
import android.widget.EditText;
import android.widget.AdapterView;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.text.Collator;

public class EditActivity extends AppCompatActivity {

    private TimePicker timePicker;
    private Spinner spinnerApps;
    private Spinner spinnerRepeat;
    private AutoCompleteTextView editGroupTag;
    private CheckBox cbSkipHolidays;
    private CheckBox cbAutoOpen;
    private CheckBox cbAlarmMode;
    private CheckBox cbChallenge, cbQrChallenge, cbProgressive, cbCalendar, cbCloud, cbWatch, cbAnalytics;
    private EditText editQrValue;
    private SeekBar seekVolume;
    private TextView tvVolume;

    private List<AppItem> installedApps = new ArrayList<>();
    private List<AppItem> filteredApps = new ArrayList<>();
    private AppSpinnerAdapter appAdapter;
    private String selectedAppPackage = "";
    private boolean updatingAppFilter = false;
    private boolean initializingAppSelection = true;
    private AlarmEntry entry;
    private boolean isNew = false;

    private static class AppItem {
        String pkg;
        String label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeHelper.applyToActivity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        timePicker = findViewById(R.id.edit_time_picker);
        timePicker.setIs24HourView(true);
        spinnerApps = findViewById(R.id.spinner_apps);
        spinnerRepeat = findViewById(R.id.spinner_repeat_mode);
        editGroupTag = findViewById(R.id.edit_group_tag);
        cbSkipHolidays = findViewById(R.id.cb_skip_holidays);
        cbAutoOpen = findViewById(R.id.cb_auto_open);
        cbAlarmMode = findViewById(R.id.cb_alarm_mode);
        cbChallenge = findViewById(R.id.cb_challenge); cbQrChallenge = findViewById(R.id.cb_qr_challenge);
        cbProgressive = findViewById(R.id.cb_progressive); cbCalendar = findViewById(R.id.cb_calendar);
        cbCloud = findViewById(R.id.cb_cloud_backup); cbWatch = findViewById(R.id.cb_watch); cbAnalytics = findViewById(R.id.cb_analytics);
        editQrValue = findViewById(R.id.edit_qr_value); seekVolume = findViewById(R.id.seek_volume); tvVolume = findViewById(R.id.tv_volume);

        Button btnSave = findViewById(R.id.btn_save);
        Button btnDelete = findViewById(R.id.btn_delete);

        loadInstalledApps();
        filteredApps.addAll(installedApps);
        appAdapter = new AppSpinnerAdapter();
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
        cbChallenge.setChecked(entry.challengeEnabled && entry.challengeType == 0);
        cbQrChallenge.setChecked(entry.challengeEnabled && entry.challengeType == 1);
        editQrValue.setText(entry.challengeQrValue);
        seekVolume.setProgress(entry.volumePercent); tvVolume.setText("音量: " + entry.volumePercent + "%");
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean u) { tvVolume.setText("音量: " + p + "%"); }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        });
        cbProgressive.setChecked(entry.progressiveEnabled); cbCalendar.setChecked(entry.calendarEnabled);
        cbCloud.setChecked(entry.cloudBackupEnabled); cbWatch.setChecked(entry.watchEnabled); cbAnalytics.setChecked(entry.analyticsEnabled);
        findViewById(R.id.btn_calendar_import).setOnClickListener(v -> {
            if (!cbCalendar.isChecked()) { Toast.makeText(this, "先にカレンダー連携をONにしてください", Toast.LENGTH_SHORT).show(); return; }
            startActivity(new Intent(this, CalendarActivity.class).putExtra("alarm_id", entry.id));
        });

        for (int i = 0; i < installedApps.size(); i++) {
            if (installedApps.get(i).pkg.equals(entry.pkg)) {
                spinnerApps.setSelection(i);
                break;
            }
        }
        selectedAppPackage = entry.pkg == null ? "" : entry.pkg;
        spinnerApps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initializingAppSelection && !updatingAppFilter && position >= 0 && position < filteredApps.size()) selectedAppPackage = filteredApps.get(position).pkg;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinnerApps.post(() -> initializingAppSelection = false);
        EditText appSearch = findViewById(R.id.edit_app_search);
        appSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });

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
        Collator japanese = Collator.getInstance(Locale.JAPANESE);
        japanese.setStrength(Collator.PRIMARY);
        installedApps.sort((left, right) -> {
            int leftGroup = sortGroup(left.label), rightGroup = sortGroup(right.label);
            if (leftGroup != rightGroup) return Integer.compare(leftGroup, rightGroup);
            if (leftGroup == 1) return left.label.toLowerCase(Locale.ROOT).compareTo(right.label.toLowerCase(Locale.ROOT));
            return japanese.compare(left.label, right.label);
        });
    }

    private int sortGroup(String label) {
        if (label == null || label.isEmpty()) return 2;
        for (int i = 0; i < label.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(label.charAt(i));
            if (block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return 0;
        }
        return label.matches(".*[A-Za-z].*") ? 1 : 2;
    }

    private void filterApps(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        updatingAppFilter = true;
        filteredApps.clear();
        for (AppItem item : installedApps) {
            if (normalized.isEmpty() || item.label.toLowerCase(Locale.ROOT).contains(normalized)) filteredApps.add(item);
        }
        appAdapter.notifyDataSetChanged();
        for (int i = 0; i < filteredApps.size(); i++) {
            if (filteredApps.get(i).pkg.equals(selectedAppPackage)) { spinnerApps.setSelection(i); break; }
        }
        spinnerApps.post(() -> updatingAppFilter = false);
    }

    private void saveAlarm() {
        entry.hour = timePicker.getHour();
        entry.minute = timePicker.getMinute();

        if (selectedAppPackage != null && !selectedAppPackage.isEmpty()) {
            for (AppItem app : installedApps) if (app.pkg.equals(selectedAppPackage)) {
                entry.pkg = app.pkg; entry.label = app.label; break;
            }
        }

        entry.group = editGroupTag.getText().toString().trim();
        entry.skipHolidays = cbSkipHolidays.isChecked();
        entry.autoOpen = cbAutoOpen.isChecked();
        entry.alarmMode = cbAlarmMode.isChecked();
        entry.challengeEnabled = cbChallenge.isChecked() || cbQrChallenge.isChecked();
        entry.challengeType = cbQrChallenge.isChecked() ? 1 : 0;
        entry.challengeQrValue = editQrValue.getText().toString(); entry.volumePercent = seekVolume.getProgress();
        entry.progressiveEnabled = cbProgressive.isChecked(); entry.calendarEnabled = cbCalendar.isChecked();
        entry.cloudBackupEnabled = cbCloud.isChecked(); entry.watchEnabled = cbWatch.isChecked(); entry.analyticsEnabled = cbAnalytics.isChecked();
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
            return filteredApps.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(EditActivity.this)
                        .inflate(R.layout.item_app_choice, parent, false);
            }
            AppItem item = installedApps.get(position);
            TextView tv = convertView.findViewById(R.id.app_choice_label);
            ImageView iv = convertView.findViewById(R.id.app_choice_icon);
            tv.setText(item.label);
            iv.setImageDrawable(item.icon);
            return convertView;
        }

        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) { return getView(position, convertView, parent); }
    }
}
