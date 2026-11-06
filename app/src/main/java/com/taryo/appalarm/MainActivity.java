package com.taryo.appalarm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.app.AlarmManager;
import android.provider.Settings;
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.text.Collator;
import java.util.Locale;
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
    private String searchQuery = "";
    private boolean sortAscending = true;

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
        EditText search = findViewById(R.id.edit_search_apps);
        Spinner sort = findViewById(R.id.spinner_sort);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"名前順 あ→ん / A→Z", "名前順 Z→A"});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sort.setAdapter(sortAdapter);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                searchQuery = s.toString().trim();
                applyFilter();
            }
            public void afterTextChanged(android.text.Editable e) {}
        });
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                sortAscending = pos == 0;
                applyFilter();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

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
        if (!DynamicThemeHelper.themeKey(this).equals(appliedThemeKey)) {
            recreate();
            return;
        }
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
        for (AlarmEntry a : allAlarms) {
            boolean tagMatch = selectedTag == null || selectedTag.equals(a.group);
            String name = a.label == null ? "" : a.label;
            boolean textMatch = searchQuery.isEmpty() || name.toLowerCase(Locale.ROOT)
                    .contains(searchQuery.toLowerCase(Locale.ROOT));
            if (tagMatch && textMatch) displayedAlarms.add(a);
        }
        Collator collator = Collator.getInstance(Locale.JAPANESE);
        displayedAlarms.sort((a, b) -> {
            String left = a.label == null ? "" : a.label;
            String right = b.label == null ? "" : b.label;
            int leftGroup = sortGroup(left), rightGroup = sortGroup(right);
            int result = Integer.compare(leftGroup, rightGroup);
            if (result == 0) result = collator.compare(toHiragana(left), toHiragana(right));
            return sortAscending ? result : -result;
        });
        adapter.notifyDataSetChanged();
    }

    private int sortGroup(String value) {
        if (value == null || value.isEmpty()) return 2;
        char c = toHiragana(value).charAt(0);
        if ((c >= '\u3040' && c <= '\u309F') || (c >= '\u30A0' && c <= '\u30FF')) return 0;
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return 1;
        return 2;
    }

    private String toHiragana(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u30A1' && c <= '\u30F6') c = (char) (c - 0x60);
            out.append(c);
        }
        return out.toString();
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
