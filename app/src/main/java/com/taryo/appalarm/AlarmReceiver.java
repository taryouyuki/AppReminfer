package com.taryo.appalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.List;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM = "com.taryo.appalarm.ACTION_ALARM";
    public static final String ACTION_SNOOZE = "com.taryo.appalarm.ACTION_SNOOZE";
    public static final String ACTION_OPEN = "com.taryo.appalarm.ACTION_OPEN";
    public static final String ACTION_DISMISS = "com.taryo.appalarm.ACTION_DISMISS";
    public static final String ACTION_TOGGLE_NEXT = "com.taryo.appalarm.ACTION_TOGGLE_NEXT";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);

        if (ACTION_OPEN.equals(action)) {
            String pkg = intent.getStringExtra("pkg");
            String label = intent.getStringExtra("label");
            HistoryRecord.record(context, label, HistoryRecord.ACTION_STOP, intent.getIntExtra("id", -1));
            if (pkg != null && !pkg.isEmpty()) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                }
            }
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            int id = intent.getIntExtra("id", 0);
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) notificationManager.cancel(id);
            String label = intent.getStringExtra("label");
            int snoozesUsed = intent.getIntExtra("snoozesUsed", 1);
            List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
            AlarmEntry target = null;
            for (AlarmEntry e : list) {
                if (e.id == id) {
                    target = e;
                    break;
                }
            }

            if (target != null) {
                HistoryRecord.record(context, label, HistoryRecord.ACTION_SNOOZE, id);
                int fixedDelay = intent.getIntExtra("fixedDelayMinutes", -1);
                int min = fixedDelay > 0 ? fixedDelay : (target.snoozeMinutes > 0 ? target.snoozeMinutes : prefs.getInt("default_snooze_min", 5));
                if (target.progressiveEnabled && fixedDelay <= 0) min = Math.min(60, min * Math.max(1, snoozesUsed));
                long triggerAt = System.currentTimeMillis() + (min * 60 * 1000L);
                scheduleExact(context, target, triggerAt, snoozesUsed);
            }
            return;
        }

        if (ACTION_DISMISS.equals(action)) {
            int id = intent.getIntExtra("id", 0);
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(id);
            HistoryRecord.record(context, intent.getStringExtra("label"), HistoryRecord.ACTION_STOP, id);
            return;
        }

        if ("com.taryo.appalarm.ACTION_IGNORE".equals(action)) {
            int id = intent.getIntExtra("id", 0);
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(id);
            HistoryRecord.record(context, intent.getStringExtra("label"), HistoryRecord.ACTION_IGNORE, id);
            return;
        }

        if (ACTION_ALARM.equals(action)) {
            int id = intent.getIntExtra("id", 0);
            int snoozesUsed = intent.getIntExtra("snoozesUsed", 0);

            List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
            AlarmEntry target = null;
            for (AlarmEntry e : list) {
                if (e.id == id) {
                    target = e;
                    break;
                }
            }

            if (target == null || (!target.enabled && snoozesUsed == 0)) return;

            if (target.mode == AlarmEntry.MODE_ONCE || target.mode == AlarmEntry.MODE_DATE) {
                target.enabled = false;
                AlarmEntry.saveAll(prefs, list);
            }

            // アラーム鳴動モードか通知確認モードか
            if (target.alarmMode) {
                HistoryRecord.record(context, target.label, HistoryRecord.ACTION_NOTIFY, target.id);
                NotificationHelper.showFullScreenAlarm(context, target, snoozesUsed);
            } else if (target.autoOpen) {
                HistoryRecord.record(context, target.label, HistoryRecord.ACTION_AUTO, target.id);
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(target.pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                }
            } else {
                HistoryRecord.record(context, target.label, HistoryRecord.ACTION_NOTIFY, target.id);
                NotificationHelper.showPromptNotification(context, target, snoozesUsed);
            }

            // 次回予定を再スケジュール
            rescheduleAll(context, list);
            WidgetProvider.updateAllWidgets(context);
            AlarmTileService.requestTileUpdate(context);
        }
    }

    public static void rescheduleAll(Context context, List<AlarmEntry> list) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long now = System.currentTimeMillis();
        for (AlarmEntry e : list) {
            cancelAlarm(context, e.id);
            if (!e.enabled) continue;

            long next = NextTime.nextTrigger(e, now);
            if (next > now) {
                scheduleExact(context, e, next, 0);
            }
        }
    }

    private static void scheduleExact(Context context, AlarmEntry e, long triggerAtMillis, int snoozesUsed) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_ALARM);
        intent.putExtra("id", e.id);
        intent.putExtra("snoozesUsed", snoozesUsed);

        PendingIntent pi = PendingIntent.getBroadcast(context, e.id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } catch (SecurityException denied) {
            // Keep a best-effort reminder scheduled when exact-alarm access is denied.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            else am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    public static void cancelAlarm(Context context, int id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }
}
