package com.taryo.appalarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.widget.RemoteViews;
import java.util.List;

public class WidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE_NEXT = "com.taryo.appalarm.ACTION_TOGGLE_NEXT";
    public static final String ACTION_SKIP_NEXT = "com.taryo.appalarm.ACTION_SKIP_NEXT";
    public static final String ACTION_PREVIOUS = "com.taryo.appalarm.ACTION_WIDGET_PREVIOUS";
    public static final String ACTION_NEXT = "com.taryo.appalarm.ACTION_WIDGET_NEXT";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        List<AlarmEntry> alarms = AlarmEntry.loadAll(prefs);
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (ACTION_PREVIOUS.equals(action) || ACTION_NEXT.equals(action)) {
            cycleSelection(prefs, alarms, widgetId, ACTION_NEXT.equals(action) ? 1 : -1);
        } else if (ACTION_TOGGLE_NEXT.equals(action)) {
            AlarmEntry selected = findSelected(prefs, alarms, widgetId);
            if (selected != null) selected.enabled = !selected.enabled;
            AlarmEntry.saveAll(prefs, alarms);
            AlarmReceiver.rescheduleAll(context, alarms);
            AlarmTileService.requestTileUpdate(context);
        } else if (ACTION_SKIP_NEXT.equals(action)) {
            AlarmEntry selected = findSelected(prefs, alarms, widgetId);
            if (selected != null && selected.enabled) {
                if (selected.mode == AlarmEntry.MODE_ONCE || selected.mode == AlarmEntry.MODE_DATE) selected.enabled = false;
                else selected.skipOnceDate = NextTime.dateKey(NextTime.nextTrigger(selected, System.currentTimeMillis()));
                AlarmEntry.saveAll(prefs, alarms);
                AlarmReceiver.rescheduleAll(context, alarms);
                AlarmTileService.requestTileUpdate(context);
            }
        }
        updateAllWidgets(context);
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        List<AlarmEntry> alarms = AlarmEntry.loadAll(prefs);
        AlarmEntry selected = findSelected(prefs, alarms, widgetId);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_alarm);
        int accent = widgetColor(context, "system_accent1_100", 0xFFFFEAF1);
        int ink = widgetColor(context, "system_accent1_900", 0xFF633D50);
        if (Build.VERSION.SDK_INT >= 31) views.setColorStateList(R.id.widget_container, "setBackgroundTintList", ColorStateList.valueOf(accent));
        views.setInt(R.id.widget_time, "setTextColor", ink);
        views.setInt(R.id.widget_label, "setTextColor", ink);
        views.setInt(R.id.widget_btn_prev, "setTextColor", ink);
        views.setInt(R.id.widget_btn_next, "setTextColor", ink);
        views.setInt(R.id.widget_btn_toggle, "setTextColor", ink);
        views.setInt(R.id.widget_btn_skip, "setTextColor", ink);

        if (selected != null) {
            views.setTextViewText(R.id.widget_time, NextTime.dateTimeText(NextTime.nextTrigger(selected, System.currentTimeMillis())));
            views.setTextViewText(R.id.widget_label, selected.label + (selected.enabled ? "" : "（OFF）"));
            views.setTextViewText(R.id.widget_btn_toggle, selected.enabled ? "ON" : "OFF");
            views.setViewVisibility(R.id.widget_btn_skip, selected.enabled ? android.view.View.VISIBLE : android.view.View.GONE);
            Intent edit = new Intent(context, EditActivity.class).putExtra("alarm_id", selected.id);
            views.setOnClickPendingIntent(R.id.widget_container, PendingIntent.getActivity(context, 300000 + widgetId, edit, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            views.setTextViewText(R.id.widget_time, "--:--");
            views.setTextViewText(R.id.widget_label, "アラームがありません");
            views.setTextViewText(R.id.widget_btn_toggle, "追加");
            views.setViewVisibility(R.id.widget_btn_skip, android.view.View.GONE);
            Intent add = new Intent(context, MainActivity.class);
            views.setOnClickPendingIntent(R.id.widget_container, PendingIntent.getActivity(context, 300000 + widgetId, add, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        attachAction(context, views, R.id.widget_btn_toggle, ACTION_TOGGLE_NEXT, widgetId, 1);
        attachAction(context, views, R.id.widget_btn_skip, ACTION_SKIP_NEXT, widgetId, 2);
        attachAction(context, views, R.id.widget_btn_prev, ACTION_PREVIOUS, widgetId, 3);
        attachAction(context, views, R.id.widget_btn_next, ACTION_NEXT, widgetId, 4);
        manager.updateAppWidget(widgetId, views);
    }

    private static void attachAction(Context context, RemoteViews views, int viewId, String action, int widgetId, int requestCode) {
        Intent intent = new Intent(context, WidgetProvider.class).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent pi = PendingIntent.getBroadcast(context, 310000 + widgetId * 10 + requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(viewId, pi);
    }

    private static void cycleSelection(SharedPreferences prefs, List<AlarmEntry> alarms, int widgetId, int direction) {
        if (alarms.isEmpty() || widgetId < 0) return;
        String key = selectionKey(widgetId);
        int currentId = prefs.getInt(key, Integer.MIN_VALUE), index = -1;
        for (int i = 0; i < alarms.size(); i++) if (alarms.get(i).id == currentId) { index = i; break; }
        if (index < 0) index = direction > 0 ? -1 : 0;
        index = (index + direction + alarms.size()) % alarms.size();
        prefs.edit().putInt(key, alarms.get(index).id).apply();
    }

    private static AlarmEntry findSelected(SharedPreferences prefs, List<AlarmEntry> alarms, int widgetId) {
        if (alarms.isEmpty()) return null;
        if (widgetId >= 0) {
            int id = prefs.getInt(selectionKey(widgetId), Integer.MIN_VALUE);
            for (AlarmEntry alarm : alarms) if (alarm.id == id) return alarm;
        }
        AlarmEntry next = findNext(alarms);
        AlarmEntry selected = next != null ? next : alarms.get(0);
        if (widgetId >= 0) prefs.edit().putInt(selectionKey(widgetId), selected.id).apply();
        return selected;
    }

    private static String selectionKey(int widgetId) { return "widget_alarm_selection_" + widgetId; }

    private static int widgetColor(Context context, String colorName, int fallback) {
        if (Build.VERSION.SDK_INT >= 31) {
            try { int id = context.getResources().getIdentifier(colorName, "color", "android"); if (id != 0) return context.getColor(id); }
            catch (Exception ignored) { }
        }
        return fallback;
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static AlarmEntry findNext(List<AlarmEntry> alarms) {
        long now = System.currentTimeMillis(), min = Long.MAX_VALUE;
        AlarmEntry earliest = null;
        for (AlarmEntry alarm : alarms) if (alarm.enabled) {
            long time = NextTime.nextTrigger(alarm, now);
            if (time > now && time < min) { min = time; earliest = alarm; }
        }
        return earliest;
    }
}
