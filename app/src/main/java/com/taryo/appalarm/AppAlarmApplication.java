package com.taryo.appalarm;

import android.app.Application;

public class AppAlarmApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 保存済みのライト/ダーク設定を反映
        DynamicThemeHelper.applyNightMode(this);
    }
}
