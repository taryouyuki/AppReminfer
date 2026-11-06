package com.taryo.appalarm;

import java.util.Calendar;
import java.util.Locale;

public class NextTime {

    public static long nextTrigger(AlarmEntry entry, long nowMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);

        if (entry.mode == AlarmEntry.MODE_DATE) {
            if (entry.year == 0 || entry.month == 0 || entry.day == 0) return 0;
            Calendar candidate = Calendar.getInstance();
            candidate.set(entry.year, entry.month - 1, entry.day, entry.hour, entry.minute, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            if (candidate.getTimeInMillis() <= nowMillis) return 0;
            if (entry.skipHolidays && JapaneseHolidays.isHoliday(candidate)) return 0;
            return candidate.getTimeInMillis();
        }

        if (entry.mode == AlarmEntry.MODE_PERDAY) {
            Calendar cur = (Calendar) now.clone();
            cur.set(Calendar.SECOND, 0);
            cur.set(Calendar.MILLISECOND, 0);

            for (int i = 0; i <= 400; i++) {
                int dayOfWeek = cur.get(Calendar.DAY_OF_WEEK); // Sunday = 1, Monday = 2
                int idx = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - 2);

                int h = entry.perDayHour[idx];
                int m = entry.perDayMinute[idx];
                if (h >= 0) {
                    Calendar candidate = (Calendar) cur.clone();
                    candidate.set(Calendar.HOUR_OF_DAY, h);
                    candidate.set(Calendar.MINUTE, m);
                    if (candidate.getTimeInMillis() > nowMillis) {
                        if (!entry.skipHolidays || !JapaneseHolidays.isHoliday(candidate)) {
                            return candidate.getTimeInMillis();
                        }
                    }
                }
                cur.add(Calendar.DAY_OF_MONTH, 1);
            }
            return 0;
        }

        // MODE_ONCE, MODE_DAILY, MODE_WEEKLY
        Calendar cur = (Calendar) now.clone();
        cur.set(Calendar.HOUR_OF_DAY, entry.hour);
        cur.set(Calendar.MINUTE, entry.minute);
        cur.set(Calendar.SECOND, 0);
        cur.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i <= 400; i++) {
            if (cur.getTimeInMillis() > nowMillis) {
                boolean match = true;
                if (entry.mode == AlarmEntry.MODE_WEEKLY) {
                    int dayOfWeek = cur.get(Calendar.DAY_OF_WEEK);
                    int idx = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - 2);
                    int bit = 1 << idx;
                    match = (entry.days & bit) != 0;
                }
                if (match) {
                    if (!entry.skipHolidays || !JapaneseHolidays.isHoliday(cur)) {
                        return cur.getTimeInMillis();
                    }
                }
            }
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    public static String timeText(AlarmEntry entry) {
        return String.format(Locale.JAPAN, "%02d:%02d", entry.hour, entry.minute);
    }

    public static String daySummary(AlarmEntry entry) {
        if (entry.mode == AlarmEntry.MODE_ONCE) return "1回だけ";
        if (entry.mode == AlarmEntry.MODE_DATE) {
            return String.format(Locale.JAPAN, "%04d/%02d/%02d のみ", entry.year, entry.month, entry.day);
        }
        if (entry.mode == AlarmEntry.MODE_PERDAY) return "曜日別設定";
        if (entry.mode == AlarmEntry.MODE_DAILY) {
            return entry.skipHolidays ? "毎日（祝日除く）" : "毎日";
        }
        String[] names = {"月", "火", "水", "木", "金", "土", "日"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((entry.days & (1 << i)) != 0) sb.append(names[i]);
        }
        sb.append("曜");
        if (entry.skipHolidays) sb.append("（祝日除く）");
        return sb.toString();
    }
}
