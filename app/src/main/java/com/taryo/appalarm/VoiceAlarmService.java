package com.taryo.appalarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
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
    public static final String EXTRA_TONE_URI = "tone_uri";
    public static final String EXTRA_VOLUME = "tone_volume";
    private TextToSpeech tts;
    private boolean ready;
    private String pendingText = "";
    private int latestStartId;
    private MediaPlayer player;

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

    public static void playTone(Context context, String label, Uri uri, int volumePercent) {
        Intent intent = new Intent(context, VoiceAlarmService.class);
        intent.putExtra(EXTRA_LABEL, label);
        intent.putExtra(EXTRA_TONE_URI, uri == null ? "" : uri.toString());
        intent.putExtra(EXTRA_VOLUME, Math.max(0, Math.min(100, volumePercent)));
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent); else context.startService(intent); }
        catch (RuntimeException ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        String label = intent == null ? "アラーム" : intent.getStringExtra(EXTRA_LABEL);
        String toneUri = intent == null ? null : intent.getStringExtra(EXTRA_TONE_URI);
        if (toneUri != null) {
            startForeground(NOTIFICATION_ID, buildNotification(label));
            try {
                if (player != null) { player.release(); player = null; }
                Uri uri = toneUri.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(toneUri);
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                player.setDataSource(this, uri);
                float vol = Math.max(0, Math.min(100, intent.getIntExtra(EXTRA_VOLUME, 70))) / 100f;
                player.setVolume(vol, vol);
                player.setOnCompletionListener(mp -> stopSelfResult(startId));
                player.setOnErrorListener((mp, what, extra) -> { stopSelfResult(startId); return true; });
                player.prepareAsync();
                player.setOnPreparedListener(MediaPlayer::start);
            } catch (Exception e) { stopSelfResult(startId); }
            return START_NOT_STICKY;
        }
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
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
