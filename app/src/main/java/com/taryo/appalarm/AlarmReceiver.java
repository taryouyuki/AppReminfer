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
            HistoryRecord.record(context, label, HistoryRecord.ACTION_STOP);
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
            String label = intent.getStringExtra("label");
            int snoozesUsed = intent.getIntExtra("snoozesUsed", 1);
            HistoryRecord.record(context, label, HistoryRecord.ACTION_SNOOZE);

            List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
            AlarmEntry target = null;
            for (AlarmEntry e : list) {
                if (e.id == id) {
                    target = e;
                    break;
                }
            }

            if (target != null) {
                int min = target.snoozeMinutes > 0 ? target.snoozeMinutes : prefs.getInt("default_snooze_min", 5);
                long triggerAt = System.currentTimeMillis() + (min * 60 * 1000L);
                scheduleExact(context, target, triggerAt, snoozesUsed);
            }
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

            if (target == null || !target.enabled) return;

            // アラーム鳴動モードか通知確認モードか
            if (target.alarmMode) {
                Intent wake = new Intent(context, WakeActivity.class);
                wake.putExtra("id", target.id);
                wake.putExtra("pkg", target.pkg);
                wake.putExtra("label", target.label);
                wake.putExtra("alarmSoundUri", target.alarmSoundUri);
                wake.putExtra("snoozesUsed", snoozesUsed);
                wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(wake);
            } else if (target.autoOpen) {
                HistoryRecord.record(context, target.label, HistoryRecord.ACTION_AUTO);
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(target.pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                }
            } else {
                HistoryRecord.record(context, target.label, HistoryRecord.ACTION_NOTIFY);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
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
