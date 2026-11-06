package com.taryo.appalarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

/** Speaks a configured alarm message while the app is in the background. */
public class VoiceAlarmService extends Service implements TextToSpeech.OnInitListener {
    private static final String CHANNEL = "voice_alarm_service";
    private static final int NOTIFICATION_ID = 8701;
    public static final String EXTRA_TEXT = "voice_text";
    public static final String EXTRA_LABEL = "alarm_label";
    private TextToSpeech tts;
    private boolean ready;
    private String pendingText = "";
    private int latestStartId;

    public static void start(Context context, String label, String text) {
        Intent intent = new Intent(context, VoiceAlarmService.class);
        intent.putExtra(EXTRA_LABEL, label);
        intent.putExtra(EXTRA_TEXT, text);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException ignored) {
            // The alarm notification still appears if the OS prevents background speech.
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        String label = intent == null ? "アラーム" : intent.getStringExtra(EXTRA_LABEL);
        pendingText = intent == null ? "" : intent.getStringExtra(EXTRA_TEXT);
        if (pendingText == null || pendingText.trim().isEmpty()) pendingText = (label == null || label.trim().isEmpty() ? "アラーム" : label) + "です";
        startForeground(NOTIFICATION_ID, buildNotification(label));
        if (tts == null) tts = new TextToSpeech(this, this);
        else if (ready) speakPending();
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(new NotificationChannel(CHANNEL, "音声アラーム", NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    private android.app.Notification buildNotification(String label) {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_alarm)
                .setContentTitle("音声でアラームを通知中")
                .setContentText(label == null ? "アラーム" : label)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) { stopSelf(); return; }
        int language = tts.setLanguage(Locale.JAPAN);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) { stopSelfResult(latestStartId); }
            @Override public void onError(String utteranceId) { stopSelfResult(latestStartId); }
        });
        ready = true;
        speakPending();
    }

    private void speakPending() {
        if (!ready || tts == null || pendingText == null) return;
        tts.stop();
        tts.speak(pendingText, TextToSpeech.QUEUE_FLUSH, null, "alarm-" + latestStartId);
    }

    @Override public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
