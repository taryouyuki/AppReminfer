package com.taryo.appalarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.animation.ValueAnimator;
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
    public static final String EXTRA_ALARM_LOOP = "alarm_loop";
    private TextToSpeech tts;
    private boolean ready;
    private String pendingText = "";
    private int latestStartId;
    private MediaPlayer player;
    private ValueAnimator fadeAnimator;

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
        startTone(context, label, uri, volumePercent, false);
    }

    public static void playAlarmTone(Context context, String label, Uri uri, int volumePercent) {
        startTone(context, label, uri, volumePercent, true);
    }

    private static void startTone(Context context, String label, Uri uri, int volumePercent, boolean alarmLoop) {
        Intent intent = new Intent(context, VoiceAlarmService.class);
        intent.putExtra(EXTRA_LABEL, label);
        intent.putExtra(EXTRA_TONE_URI, uri == null ? "" : uri.toString());
        intent.putExtra(EXTRA_VOLUME, Math.max(0, Math.min(100, volumePercent)));
        intent.putExtra(EXTRA_ALARM_LOOP, alarmLoop);
        if (alarmLoop) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
            intent.putExtra("fade_enabled", prefs.getBoolean("fade_enabled", true));
            intent.putExtra("fade_seconds", prefs.getInt("fade_seconds", 10));
        }
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent); else context.startService(intent); }
        catch (RuntimeException ignored) { }
    }

    public static void stopPlayback(Context context) { context.stopService(new Intent(context, VoiceAlarmService.class)); }

    public static int getTtsVolumeOffset(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("tts_volume_boost_v2", false)) {
            int previous = prefs.getInt("tts_volume_offset_steps", 3);
            android.content.SharedPreferences.Editor edit = prefs.edit().putBoolean("tts_volume_boost_v2", true);
            if (!prefs.contains("tts_volume_offset_steps") || previous == 3) edit.putInt("tts_volume_offset_steps", 5);
            edit.apply();
        }
        return Math.max(-5, Math.min(5, prefs.getInt("tts_volume_offset_steps", 5)));
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
            boolean alarmLoop = intent.getBooleanExtra(EXTRA_ALARM_LOOP, false);
            startForeground(NOTIFICATION_ID, buildNotification(label, alarmLoop));
            try {
                if (player != null) { player.release(); player = null; }
                Uri uri = toneUri.isEmpty() ? RingtoneManager.getDefaultUri(alarmLoop ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(toneUri);
                player = new MediaPlayer();
                player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                player.setDataSource(this, uri);
                float vol = Math.max(0, Math.min(100, intent.getIntExtra(EXTRA_VOLUME, 100))) / 100f;
                boolean fade = alarmLoop && intent.getBooleanExtra("fade_enabled", false);
                player.setVolume(fade ? 0.05f : vol, fade ? 0.05f : vol);
                player.setLooping(alarmLoop);
                player.setOnCompletionListener(mp -> { if (!alarmLoop) stopSelfResult(startId); });
                player.setOnErrorListener((mp, what, extra) -> { stopSelfResult(startId); return true; });
                player.setOnPreparedListener(mp -> {
                    mp.start();
                    if (fade) {
                        if (fadeAnimator != null) fadeAnimator.cancel();
                        fadeAnimator = ValueAnimator.ofFloat(0.05f, vol);
                        fadeAnimator.setDuration(Math.max(1, intent.getIntExtra("fade_seconds", 10)) * 1000L);
                        fadeAnimator.addUpdateListener(a -> { if (player == mp && mp.isPlaying()) { float level = (float) a.getAnimatedValue(); mp.setVolume(level, level); } });
                        fadeAnimator.start();
                    }
                });
                player.prepareAsync();
            } catch (Exception e) { stopSelfResult(startId); }
            return START_NOT_STICKY;
        }
        pendingText = intent == null ? "" : intent.getStringExtra(EXTRA_TEXT);
        if (pendingText == null || pendingText.trim().isEmpty()) pendingText = (label == null || label.trim().isEmpty() ? "アラーム" : label) + "です";
        startForeground(NOTIFICATION_ID, buildNotification(label, false));
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

    private android.app.Notification buildNotification(String label, boolean alarmLoop) {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_alarm)
                .setContentTitle(alarmLoop ? "アラーム音を再生中" : "音声でアラームを通知中")
                .setContentText(label == null ? "アラーム" : label)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) { stopSelf(); return; }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
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
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = audio == null ? 0 : audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        int current = audio == null ? max : audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        int offset = getTtsVolumeOffset(this);
        float level = max <= 0 ? 1f : Math.max(0, Math.min(max, current + offset)) / (float) max;
        android.os.Bundle parameters = new android.os.Bundle();
        parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, level);
        tts.speak(pendingText, TextToSpeech.QUEUE_FLUSH, parameters, "alarm-" + latestStartId);
    }

    @Override public void onDestroy() {
        if (fadeAnimator != null) { fadeAnimator.cancel(); fadeAnimator = null; }
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
