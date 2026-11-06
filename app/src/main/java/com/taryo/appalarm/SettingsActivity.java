package com.taryo.appalarm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicThemeHelper.applyToActivity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        Button notificationSettings = findViewById(R.id.btn_notification_settings);
        notificationSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID_PROMPT);
                startActivity(intent);
            } catch (Exception ignored) {
                startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        Button fullScreenSettings = findViewById(R.id.btn_fullscreen_settings);
        fullScreenSettings.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(android.net.Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            } else {
                Toast.makeText(this, "Android 14以降で必要な設定です", Toast.LENGTH_SHORT).show();
            }
        });
        Button exactSettings = findViewById(R.id.btn_exact_alarm_settings);
        exactSettings.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {}
            } else {
                Toast.makeText(this, "このAndroidバージョンでは個別の許可画面はありません", Toast.LENGTH_SHORT).show();
            }
        });

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

        btnExport.setOnClickListener(v -> {
            String json = prefs.getString("alarm_list_json", "[]");
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("appalarm_backup", json));
                Toast.makeText(SettingsActivity.this, "設定JSONをクリップボードにコピーしました", Toast.LENGTH_SHORT).show();
            }
        });

        btnImport.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null && text.length() > 0) {
                    try {
                        new org.json.JSONArray(text.toString());
                        prefs.edit().putString("alarm_list_json", text.toString()).apply();
                        Toast.makeText(SettingsActivity.this, "クリップボードの設定から復元しました", Toast.LENGTH_SHORT).show();
                        AlarmReceiver.rescheduleAll(SettingsActivity.this, AlarmEntry.loadAll(prefs));
                    } catch (Exception e) {
                        Toast.makeText(SettingsActivity.this, "クリップボードのJSON形式が不正です", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }
}
