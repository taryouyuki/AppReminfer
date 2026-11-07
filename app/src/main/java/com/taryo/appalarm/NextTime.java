package com.taryo.appalarm;

import java.util.Calendar;
import java.util.Locale;

public class NextTime {

    public static long nextTrigger(AlarmEntry entry, long nowMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);

        if (entry.mode == AlarmEntry.MODE_ONCE) {
            if (entry.oneShotAtMillis > 0) return entry.oneShotAtMillis > nowMillis ? entry.oneShotAtMillis : 0;
            Calendar candidate = Calendar.getInstance();
            if (entry.year > 0 && entry.month > 0 && entry.day > 0) candidate.set(entry.year, entry.month - 1, entry.day, entry.hour, entry.minute, 0);
            else {
                candidate.setTimeInMillis(nowMillis);
                candidate.set(Calendar.HOUR_OF_DAY, entry.hour); candidate.set(Calendar.MINUTE, entry.minute); candidate.set(Calendar.SECOND, 0);
                if (candidate.getTimeInMillis() <= nowMillis) candidate.add(Calendar.DAY_OF_MONTH, 1);
            }
            candidate.set(Calendar.MILLISECOND, 0); applyOverride(entry, candidate);
            if (candidate.getTimeInMillis() <= nowMillis || isExcluded(entry, candidate)) return 0;
            return candidate.getTimeInMillis();
        }

        if (entry.mode == AlarmEntry.MODE_DATE) {
            if (entry.year == 0 || entry.month == 0 || entry.day == 0) return 0;
            Calendar candidate = Calendar.getInstance();
            candidate.set(entry.year, entry.month - 1, entry.day, entry.hour, entry.minute, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            applyOverride(entry, candidate);
            if (candidate.getTimeInMillis() <= nowMillis) return 0;
            if (isExcluded(entry, candidate)) return 0;
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
                    applyOverride(entry, candidate);
                    if (candidate.getTimeInMillis() > nowMillis) {
                        if (!isExcluded(entry, candidate) && (!entry.skipHolidays || !JapaneseHolidays.isHoliday(candidate))) {
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
        cur.set(Calendar.SECOND, 0);
        cur.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i <= 400; i++) {
            Calendar candidate = (Calendar) cur.clone();
            candidate.set(Calendar.HOUR_OF_DAY, entry.hour);
            candidate.set(Calendar.MINUTE, entry.minute);
            applyOverride(entry, candidate);
            if (candidate.getTimeInMillis() > nowMillis) {
                boolean match = true;
                if (entry.mode == AlarmEntry.MODE_WEEKLY) {
                    int dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK);
                    int idx = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - 2);
                    int bit = 1 << idx;
                    match = (entry.days & bit) != 0;
                }
                if (match) {
                    if (!isExcluded(entry, candidate) && (!entry.skipHolidays || !JapaneseHolidays.isHoliday(candidate))) {
                        return candidate.getTimeInMillis();
                    }
                }
            }
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    public static String dateKey(long timeMillis) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(timeMillis);
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static boolean isExcluded(AlarmEntry entry, Calendar date) {
        String key = dateKey(date.getTimeInMillis());
        if (key.equals(entry.skipOnceDate)) return true;
        if (entry.excludedDates == null || entry.excludedDates.trim().isEmpty()) return false;
        for (String value : entry.excludedDates.split(",")) if (key.equals(value.trim())) return true;
        return false;
    }

    private static void applyOverride(AlarmEntry entry, Calendar candidate) {
        if (entry.dateOverrides == null || entry.dateOverrides.trim().isEmpty()) return;
        String key = dateKey(candidate.getTimeInMillis());
        for (String value : entry.dateOverrides.split(",")) {
            String[] parts = value.trim().split("=", 2);
            if (parts.length == 2 && key.equals(parts[0].trim())) {
                String[] hm = parts[1].trim().split(":", 2);
                try { if (hm.length == 2) { candidate.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0])); candidate.set(Calendar.MINUTE, Integer.parseInt(hm[1])); } }
                catch (NumberFormatException ignored) { }
                return;
            }
        }
    }

    public static String timeText(AlarmEntry entry) {
        return String.format(Locale.JAPAN, "%02d:%02d", entry.hour, entry.minute);
    }

    public static String dateTimeText(long timeMillis) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN);
        return format.format(new java.util.Date(timeMillis));
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
