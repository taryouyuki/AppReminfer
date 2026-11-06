package com.taryo.appalarm;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AlarmEntry {
    public static final int MODE_ONCE = 0;
    public static final int MODE_DAILY = 1;
    public static final int MODE_WEEKLY = 2;
    public static final int MODE_DATE = 3;
    public static final int MODE_PERDAY = 4;

    public static final int SOUND_DEFAULT = 0;
    public static final int SOUND_SILENT = 1;
    public static final int SOUND_CUSTOM = 2;
    public static final int SOUND_VOICE = 3;

    public int id;
    public int hour;
    public int minute;
    public String pkg = "";
    public String label = "";
    public int mode = MODE_DAILY;
    public int days = 0x7f; // bit 0 = Mon ... bit 6 = Sun
    public boolean skipHolidays = false;
    public boolean enabled = true;
    public boolean autoOpen = false;

    // Date specific
    public int year;
    public int month;
    public int day;

    // Per day specific
    public int[] perDayHour = new int[]{-1, -1, -1, -1, -1, -1, -1};
    public int[] perDayMinute = new int[]{0, 0, 0, 0, 0, 0, 0};

    // Alarm mode & Sound
    public boolean alarmMode = false;
    public String alarmSoundUri = "";
    public int soundMode = SOUND_DEFAULT;
    public String soundUri = "";
    public String voiceText = "";

    // Snooze
    public int snoozeMinutes = -1; // -1 = default
    public int maxSnooze = -1;
    public long skipAt = 0;

    // NEW: グループ / タグ整理 ("朝", "仕事", "夜", etc.)
    public String group = "";

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("hour", hour);
        obj.put("minute", minute);
        obj.put("pkg", pkg);
        obj.put("label", label);
        obj.put("mode", mode);
        obj.put("days", days);
        obj.put("skipHolidays", skipHolidays);
        obj.put("enabled", enabled);
        obj.put("autoOpen", autoOpen);
        obj.put("year", year);
        obj.put("month", month);
        obj.put("day", day);

        JSONArray hArr = new JSONArray();
        JSONArray mArr = new JSONArray();
        for (int i = 0; i < 7; i++) {
            hArr.put(perDayHour[i]);
            mArr.put(perDayMinute[i]);
        }
        obj.put("perDayHour", hArr);
        obj.put("perDayMinute", mArr);

        obj.put("alarmMode", alarmMode);
        obj.put("alarmSoundUri", alarmSoundUri);
        obj.put("soundMode", soundMode);
        obj.put("soundUri", soundUri);
        obj.put("voiceText", voiceText);
        obj.put("snoozeMinutes", snoozeMinutes);
        obj.put("maxSnooze", maxSnooze);
        obj.put("skipAt", skipAt);
        obj.put("group", group != null ? group : "");
        return obj;
    }

    public static AlarmEntry fromJsonObject(JSONObject obj) {
        AlarmEntry e = new AlarmEntry();
        e.id = obj.optInt("id", (int) System.currentTimeMillis());
        e.hour = obj.optInt("hour", 7);
        e.minute = obj.optInt("minute", 0);
        e.pkg = obj.optString("pkg", "");
        e.label = obj.optString("label", "");
        e.mode = obj.optInt("mode", MODE_DAILY);
        e.days = obj.optInt("days", 0x7f);
        e.skipHolidays = obj.optBoolean("skipHolidays", false);
        e.enabled = obj.optBoolean("enabled", true);
        e.autoOpen = obj.optBoolean("autoOpen", false);
        e.year = obj.optInt("year", 0);
        e.month = obj.optInt("month", 0);
        e.day = obj.optInt("day", 0);

        JSONArray hArr = obj.optJSONArray("perDayHour");
        JSONArray mArr = obj.optJSONArray("perDayMinute");
        if (hArr != null && mArr != null && hArr.length() == 7 && mArr.length() == 7) {
            for (int i = 0; i < 7; i++) {
                e.perDayHour[i] = hArr.optInt(i, -1);
                e.perDayMinute[i] = mArr.optInt(i, 0);
            }
        }

        e.alarmMode = obj.optBoolean("alarmMode", false);
        e.alarmSoundUri = obj.optString("alarmSoundUri", "");
        e.soundMode = obj.optInt("soundMode", SOUND_DEFAULT);
        e.soundUri = obj.optString("soundUri", "");
        e.voiceText = obj.optString("voiceText", "");
        e.snoozeMinutes = obj.optInt("snoozeMinutes", -1);
        e.maxSnooze = obj.optInt("maxSnooze", -1);
        e.skipAt = obj.optLong("skipAt", 0);
        e.group = obj.optString("group", "");
        return e;
    }

    public static List<AlarmEntry> loadAll(SharedPreferences prefs) {
        String raw = prefs.getString("alarm_list_json", null);
        List<AlarmEntry> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(fromJsonObject(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Collections.sort(list, new Comparator<AlarmEntry>() {
            @Override
            public int compare(AlarmEntry a, AlarmEntry b) {
                if (a.hour != b.hour) return Integer.compare(a.hour, b.hour);
                return Integer.compare(a.minute, b.minute);
            }
        });
        return list;
    }

    public static void saveAll(SharedPreferences prefs, List<AlarmEntry> list) {
        JSONArray arr = new JSONArray();
        for (AlarmEntry e : list) {
            try {
                arr.put(e.toJsonObject());
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
        prefs.edit().putString("alarm_list_json", arr.toString()).apply();
    }
}
