package com.taryo.appalarm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryRecord {
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SNOOZE = "snooze";
    public static final String ACTION_AUTO = "auto";
    public static final String ACTION_NOTIFY = "notify";
    public static final String ACTION_IGNORE = "ignore";

    public long timestamp;
    public String label = "";
    public String action = ACTION_STOP;
    public int alarmId = -1;

    public HistoryRecord(long timestamp, String label, String action) {
        this(timestamp, label, action, -1);
    }
    public HistoryRecord(long timestamp, String label, String action, int alarmId) {
        this.timestamp = timestamp;
        this.label = label != null ? label : "";
        this.action = action != null ? action : ACTION_STOP;
        this.alarmId = alarmId;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("timestamp", timestamp);
        obj.put("label", label);
        obj.put("action", action);
        obj.put("alarmId", alarmId);
        return obj;
    }

    public static HistoryRecord fromJsonObject(JSONObject obj) {
        return new HistoryRecord(
                obj.optLong("timestamp", System.currentTimeMillis()),
                obj.optString("label", ""),
                obj.optString("action", ACTION_STOP), obj.optInt("alarmId", -1)
        );
    }

    public static void record(Context context, String label, String action) {
        record(context, label, action, -1);
    }

    public static void record(Context context, String label, String action, int alarmId) {
        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("history_enabled", true);
        if (!enabled) return;
        if (alarmId >= 0) {
            for (AlarmEntry entry : AlarmEntry.loadAll(prefs)) if (entry.id == alarmId && !entry.analyticsEnabled) return;
        }

        List<HistoryRecord> list = loadAll(prefs);
        list.add(0, new HistoryRecord(System.currentTimeMillis(), label, action, alarmId));
        if (list.size() > 200) {
            list = list.subList(0, 200);
        }
        saveAll(prefs, list);
    }

    public static List<HistoryRecord> loadAll(SharedPreferences prefs) {
        String raw = prefs.getString("history_records_json", null);
        List<HistoryRecord> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(fromJsonObject(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveAll(SharedPreferences prefs, List<HistoryRecord> list) {
        JSONArray arr = new JSONArray();
        for (HistoryRecord r : list) {
            try {
                arr.put(r.toJsonObject());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString("history_records_json", arr.toString()).apply();
    }

    public static void clearAll(SharedPreferences prefs) {
        prefs.edit().remove("history_records_json").apply();
    }

    public static Map<String, Integer> calculateStats(List<HistoryRecord> list) {
        Map<String, Integer> map = new HashMap<>();
        map.put("total", list.size());
        int stop = 0, snooze = 0, auto = 0, notify = 0, ignore = 0;
        for (HistoryRecord r : list) {
            if (ACTION_STOP.equals(r.action)) stop++;
            else if (ACTION_SNOOZE.equals(r.action)) snooze++;
            else if (ACTION_AUTO.equals(r.action)) auto++;
            else if (ACTION_IGNORE.equals(r.action)) ignore++;
            else notify++;
        }
        map.put("stop", stop);
        map.put("snooze", snooze);
        map.put("auto", auto);
        map.put("notify", notify);
        map.put("ignore", ignore);
        return map;
    }

    public static String findMostSnoozedAlarm(List<HistoryRecord> list) {
        Map<String, Integer> counts = new HashMap<>();
        for (HistoryRecord r : list) {
            if (ACTION_SNOOZE.equals(r.action) && !r.label.isEmpty()) {
                counts.put(r.label, counts.getOrDefault(r.label, 0) + 1);
            }
        }
        String topLabel = null;
        int max = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                topLabel = entry.getKey();
            }
        }
        if (topLabel != null && max > 0) {
            return "寝坊注意: 「" + topLabel + "」は計" + max + "回スヌーズされています";
        }
        return "分析: 現在のところ目立った二度寝・連続スヌーズはありません";
    }
}
