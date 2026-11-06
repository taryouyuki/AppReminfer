package com.taryo.appalarm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.os.PowerManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.net.Uri;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQ_EXPORT = 801, REQ_IMPORT = 802;
    private static final int REQ_CALENDAR = 803, REQ_NOTIFICATIONS = 804;
    private SharedPreferences backupPrefs;
    private TextView tvBackupLast, tvPermissionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeHelper.applyToActivity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        backupPrefs = prefs;
        tvBackupLast = findViewById(R.id.tv_backup_last);
        tvPermissionStatus = findViewById(R.id.tv_permission_status);
        refreshBackupDate(); refreshPermissionStatus();
        findViewById(R.id.btn_permission_check).setOnClickListener(v -> showPermissionOptions());
        findViewById(R.id.btn_perm_exact).setOnClickListener(v -> openPermission(0));
        findViewById(R.id.btn_perm_notification).setOnClickListener(v -> openPermission(1));
        findViewById(R.id.btn_perm_fullscreen).setOnClickListener(v -> openPermission(2));
        findViewById(R.id.btn_perm_alarm_channel).setOnClickListener(v -> openPermission(6));
        findViewById(R.id.btn_perm_calendar).setOnClickListener(v -> openPermission(4));
        findViewById(R.id.btn_perm_battery).setOnClickListener(v -> openPermission(3));

        // 0. システムカラー (Material You / Dynamic Colors) 設定
        SwitchCompat switchDynamicColor = findViewById(R.id.switch_dynamic_color);
        TextView tvDynamicDesc = findViewById(R.id.tv_dynamic_color_desc);

        if (DynamicThemeHelper.isDynamicColorSupported()) {
            switchDynamicColor.setEnabled(true);
            switchDynamicColor.setChecked(DynamicThemeHelper.isDynamicColorEnabled(this));
            switchDynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
                DynamicThemeHelper.setDynamicColorEnabled(SettingsActivity.this, isChecked);
                Toast.makeText(SettingsActivity.this,
                        "配色設定を更新しました",
                        Toast.LENGTH_SHORT).show();
                // 画面を再生成して即時反映
                recreate();
            });
        } else {
            switchDynamicColor.setEnabled(false);
            switchDynamicColor.setChecked(false);
            tvDynamicDesc.setText("※ Android 12以降の端末で利用できます（現在の端末は非対応）");
        }

        // 0-b. 画面テーマ (システム / ライト / ダーク)
        Spinner spinnerTheme = findViewById(R.id.spinner_theme_mode);
        String[] themeLabels = new String[]{"システムに合わせる", "ライト", "ダーク"};
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeLabels);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);
        final int savedThemeMode = DynamicThemeHelper.getThemeMode(this);
        spinnerTheme.setSelection(savedThemeMode, false);
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != DynamicThemeHelper.getThemeMode(SettingsActivity.this)) {
                    DynamicThemeHelper.setThemeMode(SettingsActivity.this, position);
                    // 設定変更後、全画面が自動的に作り直される
                    DynamicThemeHelper.applyNightMode(SettingsActivity.this);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 1. 音量フェードイン設定
        SwitchCompat switchFade = findViewById(R.id.switch_fade_enabled);
        final View layoutFadeDuration = findViewById(R.id.layout_fade_duration);
        Spinner spinnerFade = findViewById(R.id.spinner_fade_seconds);

        boolean fadeEnabled = prefs.getBoolean("fade_enabled", true);
        switchFade.setChecked(fadeEnabled);
        layoutFadeDuration.setVisibility(fadeEnabled ? View.VISIBLE : View.GONE);

        switchFade.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("fade_enabled", isChecked).apply();
            layoutFadeDuration.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        final Integer[] fadeSecs = new Integer[]{5, 10, 20, 30};
        String[] fadeLabels = new String[]{"5秒", "10秒 (おすすめ)", "20秒", "30秒"};
        ArrayAdapter<String> fadeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fadeLabels);
        fadeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFade.setAdapter(fadeAdapter);

        int currentFade = prefs.getInt("fade_seconds", 10);
        for (int i = 0; i < fadeSecs.length; i++) {
            if (fadeSecs[i] == currentFade) {
                spinnerFade.setSelection(i);
                break;
            }
        }
        spinnerFade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("fade_seconds", fadeSecs[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        android.widget.SeekBar seekTtsOffset = findViewById(R.id.seek_tts_volume_offset);
        TextView tvTtsOffset = findViewById(R.id.tv_tts_volume_offset);
        int ttsOffset = VoiceAlarmService.getTtsVolumeOffset(this);
        seekTtsOffset.setProgress(ttsOffset + 5);
        tvTtsOffset.setText("通知音量 " + (ttsOffset > 0 ? "+" : "") + ttsOffset + "段階");
        seekTtsOffset.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int offset = progress - 5;
                tvTtsOffset.setText("通知音量 " + (offset > 0 ? "+" : "") + offset + "段階");
                if (fromUser) prefs.edit().putInt("tts_volume_offset_steps", offset).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });

        // 2. 履歴・統計設定
        SwitchCompat switchHistory = findViewById(R.id.switch_history_enabled);
        Button btnHistory = findViewById(R.id.btn_open_history);

        boolean historyEnabled = prefs.getBoolean("history_enabled", true);
        switchHistory.setChecked(historyEnabled);
        switchHistory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("history_enabled", isChecked).apply();
        });

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        // 3. スヌーズ設定
        Spinner spinnerSnoozeMin = findViewById(R.id.spinner_snooze_minutes);
        Spinner spinnerMaxSnooze = findViewById(R.id.spinner_max_snooze);

        final Integer[] minValues = new Integer[]{5, 10, 15, 30, 60};
        String[] minLabels = new String[]{"5分後", "10分後", "15分後", "30分後", "60分後"};
        ArrayAdapter<String> minAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, minLabels);
        minAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSnoozeMin.setAdapter(minAdapter);

        int currentMin = prefs.getInt("default_snooze_min", 5);
        for (int i = 0; i < minValues.length; i++) {
            if (minValues[i] == currentMin) {
                spinnerSnoozeMin.setSelection(i);
                break;
            }
        }
        spinnerSnoozeMin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("default_snooze_min", minValues[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        final Integer[] maxValues = new Integer[]{1, 2, 3, 5, 0, -2};
        String[] maxLabels = new String[]{"1回まで", "2回まで", "3回まで (既定)", "5回まで", "上限なし", "再通知しない"};
        ArrayAdapter<String> maxAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, maxLabels);
        maxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxSnooze.setAdapter(maxAdapter);

        int currentMax = prefs.getInt("default_max_snooze", 3);
        for (int i = 0; i < maxValues.length; i++) {
            if (maxValues[i] == currentMax) {
                spinnerMaxSnooze.setSelection(i);
                break;
            }
        }
        spinnerMaxSnooze.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("default_max_snooze", maxValues[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. バックアップと復元
        Button btnExport = findViewById(R.id.btn_export_backup);
        Button btnImport = findViewById(R.id.btn_import_backup);

        btnExport.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "app-remind-backup.json"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQ_EXPORT); });
        btnImport.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQ_IMPORT); });
    }

    private void refreshBackupDate() {
        long timestamp = backupPrefs.getLong("last_backup_timestamp", 0L);
        String value = timestamp <= 0 ? "まだありません" : new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(new Date(timestamp));
        tvBackupLast.setText("前回のバックアップ: " + value);
    }

    private void refreshPermissionStatus() {
        boolean notification = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        AlarmManager am = getSystemService(AlarmManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean full = Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent());
        boolean calendar = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        String alarmChannel = "Android 8未満";
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            android.app.NotificationChannel channel = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALARM);
            alarmChannel = channel == null ? "未作成" : (channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH ? "高（全画面表示に対応）" : "低（高に変更が必要）");
        }
        PowerManager power = getSystemService(PowerManager.class);
        boolean unrestricted = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        tvPermissionStatus.setText("通知: " + status(notification) + "\n正確なアラーム: " + status(exact)
                + "\nロック中の全画面表示: " + status(full) + "\nアラーム通知の重要度: " + alarmChannel + "\n他アプリの上に表示: アラームには不要\nカレンダー: " + status(calendar)
                + "\n電池最適化の対象外: " + status(unrestricted));
    }

    @Override protected void onResume() {
        super.onResume();
        if (tvPermissionStatus != null) {
            refreshPermissionStatus();
            AlarmManager manager = getSystemService(AlarmManager.class);
            if (Build.VERSION.SDK_INT < 31 || (manager != null && manager.canScheduleExactAlarms())) {
                AlarmReceiver.rescheduleAll(this, AlarmEntry.loadAll(getSharedPreferences("appalarm_prefs", MODE_PRIVATE)));
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR || requestCode == REQ_NOTIFICATIONS) refreshPermissionStatus();
    }

    private String status(boolean granted) { return granted ? "許可済み" : "未許可"; }

    private void openPermission(int which) {
        if (which == 1 && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS); return;
        }
        if (which == 4 && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR); return;
        }
        Intent intent;
        if (which == 0 && Build.VERSION.SDK_INT >= 31) intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
        else if (which == 1) { intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS); intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()); }
        else if (which == 2 && Build.VERSION.SDK_INT >= 34) intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()));
        else if (which == 3) intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        else if (which == 4) { Toast.makeText(this, "カレンダー権限は許可済みです", Toast.LENGTH_SHORT).show(); return; }
        else if (which == 6 && Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID_ALARM);
        }
        else intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); } catch (Exception e) { Toast.makeText(this, "この設定画面を開けませんでした", Toast.LENGTH_SHORT).show(); }
    }

    private void showPermissionOptions() {
        refreshPermissionStatus();
        String[] options = {"正確なアラーム設定", "通知の許可", "全画面通知設定", "電池最適化設定", "カレンダー権限を許可"};
        new android.app.AlertDialog.Builder(this).setTitle("現在の状態")
                .setMessage(tvPermissionStatus.getText())
                .setItems(options, (dialog, which) -> {
                    Intent intent;
                    if (which == 1 && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS); return;
                    }
                    if (which == 4 && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR); return;
                    }
                    if (which == 0 && Build.VERSION.SDK_INT >= 31) intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
                    else if (which == 1) { intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS); intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()); }
                    else if (which == 2 && Build.VERSION.SDK_INT >= 34) intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()));
                    else if (which == 3) intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    else intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    try { startActivity(intent); } catch (Exception e) { Toast.makeText(this, "この設定画面を開けませんでした", Toast.LENGTH_SHORT).show(); }
                }).setPositiveButton("閉じる", null).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                JSONObject root = new JSONObject(); root.put("format", "app-remind-backup-v1");
                JSONObject values = new JSONObject();
                for (String key : backupPrefs.getAll().keySet()) if (!"last_backup_timestamp".equals(key)) values.put(key, backupPrefs.getAll().get(key));
                org.json.JSONArray selectedAlarms = new org.json.JSONArray();
                for (AlarmEntry alarm : AlarmEntry.loadAll(backupPrefs)) if (alarm.cloudBackupEnabled) selectedAlarms.put(alarm.toJsonObject());
                values.put("alarm_list_json", selectedAlarms.toString());
                root.put("preferences", values);
                long createdAt = System.currentTimeMillis(); root.put("createdAt", createdAt);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(root.toString(2).getBytes(StandardCharsets.UTF_8)); }
                backupPrefs.edit().putLong("last_backup_timestamp", createdAt).apply(); refreshBackupDate();
                Toast.makeText(this, "バックアップを書き出しました", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) { byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n); }
                JSONObject root = new JSONObject(bytes.toString("UTF-8")); JSONObject values = root.getJSONObject("preferences");
                long previousBackup = backupPrefs.getLong("last_backup_timestamp", 0L);
                SharedPreferences.Editor ed = backupPrefs.edit(); ed.clear();
                for (java.util.Iterator<String> it = values.keys(); it.hasNext();) { String k = it.next(); Object val = values.get(k); if (val instanceof Boolean) ed.putBoolean(k, (Boolean) val); else if (val instanceof Integer) ed.putInt(k, (Integer) val); else if (val instanceof Long) ed.putLong(k, (Long) val); else if (val instanceof String) ed.putString(k, (String) val); }
                ed.putLong("last_backup_timestamp", previousBackup);
                ed.apply(); refreshBackupDate(); AlarmReceiver.rescheduleAll(this, AlarmEntry.loadAll(backupPrefs));
                Toast.makeText(this, "バックアップを復元しました", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { Toast.makeText(this, "ファイルを読み書きできません: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
}
