package com.taryo.appalarm;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class JapaneseHolidays {

    public static boolean isHoliday(Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        Set<String> holidays = getHolidaysForYear(year);
        String key = String.format("%04d-%02d-%02d", year, month, day);
        return holidays.contains(key);
    }

    private static Set<String> getHolidaysForYear(int year) {
        Set<String> set = new HashSet<>();
        // Fixed holidays
        add(set, year, 1, 1);   // 元日
        addNthMonday(set, year, 1, 2); // 成人の日
        add(set, year, 2, 11);  // 建国記念の日
        add(set, year, 2, 23);  // 天皇誕生日
        add(set, year, 3, equinoxDay(year, true)); // 春分の日
        add(set, year, 4, 29);  // 昭和の日
        add(set, year, 5, 3);   // 憲法記念日
        add(set, year, 5, 4);   // みどりの日
        add(set, year, 5, 5);   // こどもの日
        addNthMonday(set, year, 7, 3); // 海の日
        add(set, year, 8, 11);  // 山の日
        addNthMonday(set, year, 9, 3); // 敬老の日
        add(set, year, 9, equinoxDay(year, false)); // 秋分の日
        addNthMonday(set, year, 10, 2); // スポーツの日
        add(set, year, 11, 3);  // 文化の日
        add(set, year, 11, 23); // 勤労感謝の日

        // 振替休日
        Set<String> substitutes = new HashSet<>();
        for (String dateStr : set) {
            String[] parts = dateStr.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);
            Calendar c = Calendar.getInstance();
            c.set(y, m - 1, d);
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                Calendar next = (Calendar) c.clone();
                next.add(Calendar.DAY_OF_MONTH, 1);
                while (set.contains(format(next))) {
                    next.add(Calendar.DAY_OF_MONTH, 1);
                }
                substitutes.add(format(next));
            }
        }
        set.addAll(substitutes);

        // 国民の休日 (祝日に挟まれた平日)
        Calendar cur = Calendar.getInstance();
        cur.set(year, Calendar.JANUARY, 2);
        Calendar end = Calendar.getInstance();
        end.set(year, Calendar.DECEMBER, 30);
        Set<String> sandwiched = new HashSet<>();
        while (cur.before(end)) {
            String curKey = format(cur);
            if (!set.contains(curKey)) {
                Calendar prev = (Calendar) cur.clone();
                prev.add(Calendar.DAY_OF_MONTH, -1);
                Calendar next = (Calendar) cur.clone();
                next.add(Calendar.DAY_OF_MONTH, 1);
                if (set.contains(format(prev)) && set.contains(format(next))) {
                    sandwiched.add(curKey);
                }
            }
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }
        set.addAll(sandwiched);

        return set;
    }

    private static void add(Set<String> set, int year, int month, int day) {
        set.add(String.format("%04d-%02d-%02d", year, month, day));
    }

    private static void addNthMonday(Set<String> set, int year, int month, int n) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, 1);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        c.add(Calendar.DAY_OF_MONTH, (n - 1) * 7);
        add(set, year, month, c.get(Calendar.DAY_OF_MONTH));
    }

    private static int equinoxDay(int year, boolean spring) {
        double base = spring ? 20.8431 : 23.2488;
        double v = base + 0.242194 * (year - 1980) - Math.floor((year - 1980) / 4.0);
        return (int) Math.floor(v);
    }

    private static String format(Calendar c) {
        return String.format("%04d-%02d-%02d",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }
}
