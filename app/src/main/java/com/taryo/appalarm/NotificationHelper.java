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
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, entry.id + 50000, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 閉じるIntent
        Intent dismissIntent = new Intent(context, AlarmReceiver.class);
        dismissIntent.setAction(AlarmReceiver.ACTION_DISMISS);
        dismissIntent.putExtra("id", entry.id);
        PendingIntent dismissPi = PendingIntent.getBroadcast(context, entry.id + 90000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_PROMPT)
                .setSmallIcon(R.drawable.ic_tile_alarm)
                .setContentTitle("「" + entry.label + "」を開きますか？")
                .setContentText("指定の時刻になりました")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .setDeleteIntent(dismissPi)
                .addAction(R.drawable.ic_tile_alarm, "開く", openPi);

        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        int maxSnooze = entry.maxSnooze != -1 ? entry.maxSnooze : prefs.getInt("default_max_snooze", 3);
        int snoozeMin = entry.snoozeMinutes > 0 ? entry.snoozeMinutes : prefs.getInt("default_snooze_min", 5);

        if (maxSnooze != -2 && (maxSnooze <= 0 || snoozesUsed < maxSnooze)) {
            b.addAction(R.drawable.ic_tile_alarm, "再通知 (" + snoozeMin + "分後)", snoozePi);
        }

        nm.notify(entry.id, b.build());
    }
}
