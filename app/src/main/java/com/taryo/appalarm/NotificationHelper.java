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
    // New channel id avoids carrying forward the old channel's forced-silent behavior.
    public static final String CHANNEL_ID_PROMPT = "channel_prompt_v2";

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
            alarmCh.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(alarmCh);

            NotificationChannel promptCh = new NotificationChannel(
                    CHANNEL_ID_PROMPT,
                    "リマインダー通知（画面上部に表示）",
                    NotificationManager.IMPORTANCE_HIGH
            );
            promptCh.enableVibration(true);
            promptCh.setVibrationPattern(new long[]{0, 250, 150, 250});
            // The selected alarm sound / TTS is played separately; keep this channel
            // silent while retaining high importance for heads-up display.
            promptCh.setSound(null, null);
            nm.createNotificationChannel(promptCh);
        }
    }

    public static void showPromptNotification(Context context, AlarmEntry entry, int snoozesUsed) {
        createChannels(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // 開くIntent
        Intent openIntent = entry.pkg == null || entry.pkg.isEmpty() ? null : context.getPackageManager().getLaunchIntentForPackage(entry.pkg);
        if (openIntent == null) openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, entry.id, openIntent,
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
        dismissIntent.putExtra("label", entry.label);
        PendingIntent dismissPi = PendingIntent.getBroadcast(context, entry.id + 90000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_PROMPT)
                .setSmallIcon(R.drawable.ic_tile_alarm)
                .setContentTitle("「" + entry.label + "」を開きますか？")
                .setContentText("指定の時刻になりました")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setLocalOnly(!entry.watchEnabled)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .setDeleteIntent(dismissPi)
                .addAction(R.drawable.ic_tile_alarm, "開く", openPi)
                ;

        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        int maxSnooze = entry.maxSnooze != -1 ? entry.maxSnooze : prefs.getInt("default_max_snooze", 3);
        if (maxSnooze != -2 && (maxSnooze <= 0 || snoozesUsed < maxSnooze)) {
            int delay = entry.snoozeMinutes > 0 ? entry.snoozeMinutes : prefs.getInt("default_snooze_min", 5);
            b.addAction(R.drawable.ic_tile_alarm, delay + "分後に再通知", snoozePi);
        }

        // Start audible output independently of whether notification permission is granted.
        if (entry.soundMode == AlarmEntry.SOUND_VOICE) VoiceAlarmService.start(context, entry.label, entry.voiceText);
        else if (entry.soundMode == AlarmEntry.SOUND_DEFAULT || entry.soundMode == AlarmEntry.SOUND_CUSTOM) {
            android.net.Uri uri = entry.soundMode == AlarmEntry.SOUND_CUSTOM && entry.soundUri != null && !entry.soundUri.isEmpty()
                    ? android.net.Uri.parse(entry.soundUri) : android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            VoiceAlarmService.playTone(context, entry.label, uri, entry.notificationVolumePercent);
        }
        nm.notify(entry.id, b.build());
    }

    public static void showFullScreenAlarm(Context context, AlarmEntry entry, int snoozesUsed) {
        createChannels(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean fullScreenAllowed = Build.VERSION.SDK_INT < 34 || nm == null || nm.canUseFullScreenIntent();
        NotificationChannel channel = Build.VERSION.SDK_INT >= 26 && nm != null ? nm.getNotificationChannel(CHANNEL_ID_ALARM) : null;
        boolean channelTooLow = channel != null && channel.getImportance() < NotificationManager.IMPORTANCE_HIGH;
        Intent wake = new Intent(context, WakeActivity.class);
        wake.putExtra("id", entry.id); wake.putExtra("pkg", entry.pkg); wake.putExtra("label", entry.label);
        wake.putExtra("alarmSoundUri", entry.alarmSoundUri); wake.putExtra("snoozesUsed", snoozesUsed);
        wake.putExtra("volumePercent", entry.volumePercent); wake.putExtra("challengeEnabled", entry.challengeEnabled);
        wake.putExtra("standaloneAlarm", entry.standaloneAlarm);
        wake.putExtra("challengeType", entry.challengeType); wake.putExtra("challengeQrValue", entry.challengeQrValue);
        wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(context, entry.id + 120000, wake,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.net.Uri alarmUri = entry.alarmSoundUri == null || entry.alarmSoundUri.isEmpty()
                ? android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                : android.net.Uri.parse(entry.alarmSoundUri);
        VoiceAlarmService.playAlarmTone(context, entry.label, alarmUri, entry.volumePercent);
        String body = "アラーム画面が開かない場合は下の「表示設定」を確認してください";
        if (!fullScreenAllowed || channelTooLow) {
            body = !fullScreenAllowed ? "全画面通知の許可を確認してください。タップして設定できます。"
                    : "アラーム通知を「高」に設定してください。タップして設定できます。";
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
                .setSmallIcon(R.drawable.ic_tile_alarm).setContentTitle("アラーム: " + entry.label)
                .setContentText(body).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true).setLocalOnly(!entry.watchEnabled)
                .setContentIntent(full).setFullScreenIntent(full, true);
        Intent settings;
        if (!fullScreenAllowed) {
            settings = new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    android.net.Uri.parse("package:" + context.getPackageName()));
        } else if (channel != null && Build.VERSION.SDK_INT >= 26) {
            settings = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            settings.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID_ALARM);
        } else {
            settings = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        }
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent settingsPi = PendingIntent.getActivity(context, entry.id + 180000, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.addAction(R.drawable.ic_tile_alarm, "表示設定を開く", settingsPi);
        if (nm != null) nm.notify(entry.id, b.build());
    }
}
