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

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE_NEXT.equals(intent.getAction())) {
            // 次回の1回だけスキップ。アラーム自体は有効のまま次回分を予約する
            SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
            List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
            AlarmEntry next = findNext(list);
            if (next != null) {
                next.skipAt = NextTime.nextTrigger(next, System.currentTimeMillis());
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
            views.setTextViewText(R.id.widget_time, NextTime.timeText(next));
            views.setTextViewText(R.id.widget_label, next.label);
            views.setTextViewText(R.id.widget_btn_toggle, "次回をスキップ");

            // タップでアプリ起動
            Intent openIntent = context.getPackageManager().getLaunchIntentForPackage(next.pkg);
            if (openIntent != null) {
                PendingIntent pi = PendingIntent.getActivity(context, next.id, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_container, pi);
            }
        } else {
            views.setTextViewText(R.id.widget_time, "--:--");
            views.setTextViewText(R.id.widget_label, "アラームなし");
            views.setTextViewText(R.id.widget_btn_toggle, "アラームなし");

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
