package com.taryo.appalarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.util.List;

public class WidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_NEXT = "com.taryo.appalarm.ACTION_TOGGLE_NEXT";
    public static final String ACTION_SKIP_NEXT = "com.taryo.appalarm.ACTION_SKIP_NEXT";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_SKIP_NEXT.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
            List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
            AlarmEntry next = findNext(list);
            if (next != null) {
                if (next.mode == AlarmEntry.MODE_ONCE || next.mode == AlarmEntry.MODE_DATE) next.enabled = false;
                else next.skipOnceDate = NextTime.dateKey(NextTime.nextTrigger(next, System.currentTimeMillis()));
                AlarmEntry.saveAll(prefs, list);
                AlarmReceiver.rescheduleAll(context, list);
                android.widget.Toast.makeText(context, "次のアラームを今回だけスキップしました", android.widget.Toast.LENGTH_SHORT).show();
                updateAllWidgets(context); AlarmTileService.requestTileUpdate(context);
            }
        } else if (ACTION_TOGGLE_NEXT.equals(intent.getAction())) {
            // ホーム画面ウィジェットから即ON/OFF切り替え
            SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
            List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
            AlarmEntry next = findNext(list);

            if (next != null && next.enabled) {
                next.enabled = false;
            } else {
                for (AlarmEntry e : list) {
                    if (!e.enabled) {
                        e.enabled = true;
                        break;
                    }
                }
            }
            AlarmEntry.saveAll(prefs, list);
            AlarmReceiver.rescheduleAll(context, list);
            updateAllWidgets(context);
            AlarmTileService.requestTileUpdate(context);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
        AlarmEntry next = findNext(list);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_alarm);

        if (next != null && next.enabled) {
            views.setTextViewText(R.id.widget_time, NextTime.dateTimeText(NextTime.nextTrigger(next, System.currentTimeMillis())));
            views.setTextViewText(R.id.widget_label, next.label);
            views.setTextViewText(R.id.widget_btn_toggle, "ON (タップでOFF)");

            Intent editIntent = new Intent(context, EditActivity.class).putExtra("alarm_id", next.id);
            PendingIntent editPi = PendingIntent.getActivity(context, next.id, editIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_container, editPi);
            Intent skipIntent = new Intent(context, WidgetProvider.class).setAction(ACTION_SKIP_NEXT);
            PendingIntent skipPi = PendingIntent.getBroadcast(context, 1000, skipIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_btn_skip, skipPi);
            views.setViewVisibility(R.id.widget_btn_skip, android.view.View.VISIBLE);
        } else {
            views.setTextViewText(R.id.widget_time, "--:--");
            views.setTextViewText(R.id.widget_label, "アラームなし");
            views.setTextViewText(R.id.widget_btn_toggle, "OFF (タップでON)");
            views.setViewVisibility(R.id.widget_btn_skip, android.view.View.GONE);

            Intent mainIntent = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 0, mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_container, pi);
        }

        // 即ON/OFF切り替え用PendingIntent
        Intent toggleIntent = new Intent(context, WidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE_NEXT);
        PendingIntent togglePi = PendingIntent.getBroadcast(context, 999, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_toggle, togglePi);

        appWidgetManager.updateAppWidget(widgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, WidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private static AlarmEntry findNext(List<AlarmEntry> list) {
        long now = System.currentTimeMillis();
        AlarmEntry earliest = null;
        long min = Long.MAX_VALUE;
        for (AlarmEntry e : list) {
            if (!e.enabled) continue;
            long t = NextTime.nextTrigger(e, now);
            if (t > now && t < min) {
                min = t;
                earliest = e;
            }
        }
        return earliest;
    }
}
