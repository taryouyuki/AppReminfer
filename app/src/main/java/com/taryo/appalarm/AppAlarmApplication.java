package com.taryo.appalarm;

import android.app.Application;

public class AppAlarmApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Android 12+ のシステムカラー (Material You) を自動適用
        DynamicThemeHelper.applyToApplication(this);
    }
}
