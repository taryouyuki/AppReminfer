package com.taryo.appalarm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WakeActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private ValueAnimator fadeAnimator;
    private Vibrator vibrator;
    private int alarmId;
    private String pkg;
    private String label;
    private int snoozesUsed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ロック画面上に表示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_wake);

        Intent intent = getIntent();
        alarmId = intent.getIntExtra("id", 0);
        pkg = intent.getStringExtra("pkg");
        label = intent.getStringExtra("label");
        snoozesUsed = intent.getIntExtra("snoozesUsed", 0);

        TextView tvTime = findViewById(R.id.wake_time);
        TextView tvTitle = findViewById(R.id.wake_title);
        TextView tvFade = findViewById(R.id.wake_fade_indicator);
        Button btnStop = findViewById(R.id.btn_wake_stop);
        Button btnSnooze = findViewById(R.id.btn_wake_snooze);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.JAPAN);
        tvTime.setText(sdf.format(new Date()));
        tvTitle.setText("「" + (label != null ? label : "アプリ") + "」を開きますか？");

        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        boolean fadeEnabled = prefs.getBoolean("fade_enabled", true);
        int fadeSeconds = prefs.getInt("fade_seconds", 10);

        if (fadeEnabled) {
            tvFade.setVisibility(View.VISIBLE);
        }

        startAlarmSound(fadeEnabled, fadeSeconds);

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSound();
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(alarmId + 120000);
                HistoryRecord.record(WakeActivity.this, label, HistoryRecord.ACTION_STOP);
                if (pkg != null && !pkg.isEmpty()) {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        startActivity(launch);
                    }
                }
                finish();
            }
        });

        int configuredMax = getIntent().getIntExtra("maxSnooze", -1);
        int configuredMin = getIntent().getIntExtra("snoozeMinutes", -1);
        int maxSnooze = configuredMax != -1 ? configuredMax : prefs.getInt("default_max_snooze", 3);
        int snoozeMin = configuredMin > 0 ? configuredMin : prefs.getInt("default_snooze_min", 5);
        if (maxSnooze == -2 || (maxSnooze > 0 && snoozesUsed >= maxSnooze)) {
            btnSnooze.setVisibility(View.GONE);
        } else {
            btnSnooze.setText("スヌーズ (" + snoozeMin + "分後)");
            btnSnooze.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopSound();
                    android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(alarmId + 120000);
                    HistoryRecord.record(WakeActivity.this, label, HistoryRecord.ACTION_SNOOZE);
                    Intent snoozeIntent = new Intent(WakeActivity.this, AlarmReceiver.class);
                    snoozeIntent.setAction(AlarmReceiver.ACTION_SNOOZE);
                    snoozeIntent.putExtra("id", alarmId);
                    snoozeIntent.putExtra("label", label);
                    snoozeIntent.putExtra("snoozesUsed", snoozesUsed + 1);
                    sendBroadcast(snoozeIntent);
                    finish();
                }
            });
        }
    }

    private void startAlarmSound(boolean fadeEnabled, int fadeSeconds) {
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alert);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setLooping(true);

            if (fadeEnabled) {
                // 小さい音量(0.05)からスタート
                mediaPlayer.setVolume(0.05f, 0.05f);
                mediaPlayer.prepare();
                mediaPlayer.start();

                fadeAnimator = ValueAnimator.ofFloat(0.05f, 1.0f);
                fadeAnimator.setDuration(fadeSeconds * 1000L);
                fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            float vol = (float) animation.getAnimatedValue();
                            mediaPlayer.setVolume(vol, vol);
                        }
                    }
                });
                fadeAnimator.start();
            } else {
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // バイブレーション
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = {0, 600, 400, 600, 400};
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopSound() {
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
            vibrator = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSound();
    }
}
