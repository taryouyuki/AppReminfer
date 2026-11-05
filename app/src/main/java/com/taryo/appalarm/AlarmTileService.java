package com.taryo.appalarm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.List;

public class AlarmTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
        AlarmEntry next = findNextAlarm(list);

        if (next != null && next.enabled) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(NextTime.timeText(next) + " " + next.label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("タップでOFF");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setContentDescription("次のアラーム: " + next.label + "、タップでOFFにします");
            }
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("AppAlarm");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("アラームOFF (タップでON)");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setContentDescription("アラームOFF、タップでONにします");
            }
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences("appalarm_prefs", MODE_PRIVATE);
        List<AlarmEntry> list = AlarmEntry.loadAll(prefs);
        AlarmEntry next = findNextAlarm(list);

        if (next != null && next.enabled) {
            // 即座にOFFにする
            next.enabled = false;
            AlarmEntry.saveAll(prefs, list);
            AlarmReceiver.rescheduleAll(this, list);
        } else {
            // アラームをONにする（最初に見つかったアラームをON、または全員ON）
            boolean enabledAny = false;
            for (AlarmEntry e : list) {
                if (!e.enabled) {
                    e.enabled = true;
                    enabledAny = true;
                    break;
                }
            }
            if (!enabledAny && !list.isEmpty()) {
                list.get(0).enabled = true;
            }
            AlarmEntry.saveAll(prefs, list);
            AlarmReceiver.rescheduleAll(this, list);
        }

        updateTileState();
        WidgetProvider.updateAllWidgets(this);
    }

    private AlarmEntry findNextAlarm(List<AlarmEntry> list) {
        long now = System.currentTimeMillis();
        AlarmEntry earliest = null;
        long minTime = Long.MAX_VALUE;

        for (AlarmEntry e : list) {
            if (!e.enabled) continue;
            long t = NextTime.nextTrigger(e, now);
            if (t > now && t < minTime) {
                minTime = t;
                earliest = e;
            }
        }
        return earliest;
    }

    public static void requestTileUpdate(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestListeningState(context,
                        new android.content.ComponentName(context, AlarmTileService.class));
            } catch (Exception ignored) {
            }
        }
    }
}
