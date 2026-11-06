package com.taryo.appalarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class NotificationHelper {

    public static final String CHANNEL_ID_ALARM = "channel_alarm";
    public static final String CHANNEL_ID_PROMPT = "channel_prompt";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;

            NotificationChannel alarmCh = new NotificationChannel(
                    CHANNEL_ID_ALARM,
                    "アラーム",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alarmCh.enableVibration(true);
            nm.createNotificationChannel(alarmCh);

            NotificationChannel promptCh = new NotificationChannel(
                    CHANNEL_ID_PROMPT,
                    "アプリ起動確認",
                    NotificationManager.IMPORTANCE_HIGH
            );
            promptCh.enableVibration(true);
            nm.createNotificationChannel(promptCh);
        }
    }

    public static void showPromptNotification(Context context, AlarmEntry entry, int snoozesUsed) {
        createChannels(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // 開くIntent
        Intent openIntent = new Intent(context, AlarmReceiver.class);
        openIntent.setAction(AlarmReceiver.ACTION_OPEN);
        openIntent.putExtra("pkg", entry.pkg);
        openIntent.putExtra("label", entry.label);
        openIntent.putExtra("id", entry.id);
        PendingIntent openPi = PendingIntent.getBroadcast(context, entry.id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // スヌーズIntent
        Intent snoozeIntent = new Intent(context, AlarmReceiver.class);
        snoozeIntent.setAction(AlarmReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra("id", entry.id);
        snoozeIntent.putExtra("label", entry.label);
        snoozeIntent.putExtra("snoozesUsed", snoozesUsed + 1);
        snoozeIntent.putExtra("fixedDelayMinutes", 5);
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, entry.id + 50000, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 閉じるIntent
        Intent dismissIntent = new Intent(context, AlarmReceiver.class);
        dismissIntent.setAction(AlarmReceiver.ACTION_DISMISS);
        dismissIntent.putExtra("id", entry.id);
        dismissIntent.putExtra("label", entry.label);
        PendingIntent dismissPi = PendingIntent.getBroadcast(context, entry.id + 90000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent ignoreIntent = new Intent(context, AlarmReceiver.class);
        ignoreIntent.setAction("com.taryo.appalarm.ACTION_IGNORE");
        ignoreIntent.putExtra("id", entry.id);
        ignoreIntent.putExtra("label", entry.label);
        PendingIntent ignorePi = PendingIntent.getBroadcast(context, entry.id + 150000, ignoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_PROMPT)
                .setSmallIcon(R.drawable.ic_tile_alarm)
                .setContentTitle("「" + entry.label + "」を開きますか？")
                .setContentText("指定の時刻になりました")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setLocalOnly(!entry.watchEnabled)
                .setSilent(entry.soundMode == AlarmEntry.SOUND_SILENT || entry.soundMode == AlarmEntry.SOUND_VOICE)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .setDeleteIntent(dismissPi)
                .addAction(R.drawable.ic_tile_alarm, "開く", openPi)
                .addAction(R.drawable.ic_tile_alarm, "今回は無視", ignorePi);

        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        int maxSnooze = entry.maxSnooze != -1 ? entry.maxSnooze : prefs.getInt("default_max_snooze", 3);
        if (maxSnooze != -2 && (maxSnooze <= 0 || snoozesUsed < maxSnooze)) {
            b.addAction(R.drawable.ic_tile_alarm, "5分後に再通知", snoozePi);
        }

        nm.notify(entry.id, b.build());
    }

    public static void showFullScreenAlarm(Context context, AlarmEntry entry, int snoozesUsed) {
        createChannels(context);
        Intent wake = new Intent(context, WakeActivity.class);
        wake.putExtra("id", entry.id); wake.putExtra("pkg", entry.pkg); wake.putExtra("label", entry.label);
        wake.putExtra("alarmSoundUri", entry.alarmSoundUri); wake.putExtra("snoozesUsed", snoozesUsed);
        wake.putExtra("volumePercent", entry.volumePercent); wake.putExtra("challengeEnabled", entry.challengeEnabled);
        wake.putExtra("standaloneAlarm", entry.standaloneAlarm);
        wake.putExtra("challengeType", entry.challengeType); wake.putExtra("challengeQrValue", entry.challengeQrValue);
        wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(context, entry.id + 120000, wake,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
                .setSmallIcon(R.drawable.ic_tile_alarm).setContentTitle("アラーム: " + entry.label)
                .setContentText("タップして停止またはスヌーズ").setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true).setLocalOnly(!entry.watchEnabled)
                .setContentIntent(full).setFullScreenIntent(full, true);
        android.app.NotificationManager nm = (android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(entry.id, b.build());
    }
}
