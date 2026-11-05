package com.taryo.appalarm;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.material.color.DynamicColors;

public class DynamicThemeHelper {

    /**
     * Android 12 (API 31) 以上でシステムカラー (Material You / Dynamic Colors) が利用可能かを判定
     */
    public static boolean isDynamicColorSupported() {
        return DynamicColors.isDynamicColorAvailable();
    }

    /**
     * ユーザー設定でシステムカラーが有効化されているか
     */
    public static boolean isDynamicColorEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        // デフォルトでは、端末が対応していれば有効
        return prefs.getBoolean("use_dynamic_color", isDynamicColorSupported());
    }

    /**
     * システムカラーの有効/無効を保存
     */
    public static void setDynamicColorEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences("appalarm_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("use_dynamic_color", enabled).apply();
    }

    /**
     * Application起動時に全Activityへ動的配色を適用
     */
    public static void applyToApplication(Application app) {
        if (isDynamicColorSupported() && isDynamicColorEnabled(app)) {
            DynamicColors.applyToActivitiesIfAvailable(app);
        }
    }

    /**
     * 個別Activityへの適用
     */
    public static void applyToActivity(Activity activity) {
        if (isDynamicColorSupported() && isDynamicColorEnabled(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }
}
