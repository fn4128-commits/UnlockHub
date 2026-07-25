package com.jinxin.unlockhub;

import android.app.Application;

/**
 * 启动加固：每个初始化步骤单独兜底，
 * 任何一个附加功能出错都不能导致应用无法打开。
 */
public final class UnlockHubApplication extends Application {
    // 注意：不要在 Application.attachBaseContext 里读 SharedPreferences——此阶段
    // getApplicationContext() 为 null，会 NPE 导致应用一启动就崩。语言切换由各界面
    // 的 BaseActivity.attachBaseContext 承担即可。

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            com.jinxin.unlockhub.util.CrashLogger.install(this);
        } catch (Throwable ignored) {
        }
        try {
            NotificationHelper.ensureChannels(this);
        } catch (Throwable ignored) {
        }
        try {
            UnlockListenRegistrar.ensureRegistered(this);
        } catch (Throwable ignored) {
        }
        try {
            com.jinxin.unlockhub.routine.PowerEventMonitor.ensureRegistered(this);
        } catch (Throwable ignored) {
        }
        try {
            com.jinxin.unlockhub.routine.ConnectionEventMonitor.ensureRegistered(this);
        } catch (Throwable ignored) {
        }
        try {
            com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
        } catch (Throwable ignored) {
        }
    }
}
