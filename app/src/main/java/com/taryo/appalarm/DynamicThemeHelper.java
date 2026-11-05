package com.taryo.appalarm;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public class DynamicThemeHelper {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
    }

    /** Android 12 (API 31) 以上でシステムカラー (Material You) が使えるか */
    public static boolean isDynamicColorSupported() {
        return DynamicColors.isDynamicColorAvailable();
    }

    /** システムカラーが有効か（既定: 対応端末なら有効） */
    public static boolean isDynamicColorEnabled(Context context) {
        return prefs(context).getBoolean("use_dynamic_color", isDynamicColorSupported());
    }

    public static void setDynamicColorEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("use_dynamic_color", enabled).apply();
    }

    /** 画面テーマ (0=システムに合わせる / 1=ライト / 2=ダーク) */
    public static int getThemeMode(Context context) {
        return prefs(context).getInt("theme_mode", THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt("theme_mode", mode).apply();
    }

    /** 保存済みのテーマ設定を AppCompat に反映 */
    public static void applyNightMode(Context context) {
        int mode;
        switch (getThemeMode(context)) {
            case THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case THEME_DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Activity の super.onCreate() より前に呼ぶ */
    public static void applyToActivity(Activity activity) {
        if (isDynamicColorSupported() && isDynamicColorEnabled(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }

    /** 色設定が変わったか判定するための値 */
    public static String themeKey(Context context) {
        return isDynamicColorEnabled(context) + "/" + getThemeMode(context);
    }
}
