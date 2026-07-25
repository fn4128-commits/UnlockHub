package com.jinxin.unlockhub.service;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import com.jinxin.unlockhub.UnlockListenRegistrar;
import com.jinxin.unlockhub.UsageUnlockBackfill;
import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.data.RoutineRepository;
import com.jinxin.unlockhub.routine.RoutineEngine;
import com.jinxin.unlockhub.util.Prefs;

import java.util.List;

/**
 * 后台守护前台服务（单一常驻通知，负责两件事）：
 * 1) 保持进程存活，使解锁监听器（UnlockListenRegistrar 动态注册的 USER_PRESENT
 *    接收器）在重启后无需打开 App 也能持续捕获每日首次解锁——解决"重启后不记录"缺陷。
 * 2) 当已授予「使用情况访问权限」且存在启用的 APP_OPEN 规则时，轮询前台应用实现
 *    「应用启动」自动化（开 A 跳 B）；息屏暂停轮询以省电。
 *
 * 未暂停（!Prefs.isPaused）即运行。这是安全签到可靠性的必要代价（一条静音常驻通知）。
 */
public final class ForegroundAppWatcherService extends Service {

    private static final String CHANNEL_ID = "unlock_hub_app_watch_v1";
    private static final int NOTIF_ID = 4711;
    private static final long POLL_INTERVAL_MS = 1500L;
    private static final long LOOKBACK_MS = 8000L;

    private Handler handler;
    private UsageStatsManager usageStatsManager;
    private String lastForegroundPackage = "";
    private long lastQueryTime;
    private boolean polling;
    private boolean appOpenActive; // 是否启用了「应用启动」轮询（需使用情况访问 + 有 APP_OPEN 规则）

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            try {
                pollOnce();
            } catch (Throwable ignored) {
            }
            if (polling) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopPolling();
            } else if ((Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)) && appOpenActive) {
                startPolling();
            }
        }
    };

    // ---------- lifecycle ----------

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        lastQueryTime = System.currentTimeMillis() - LOOKBACK_MS;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        if (Prefs.isPaused(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // 关键：在这个"存活的"进程里注册解锁监听器，使其重启后也能持续捕获。
        try {
            UnlockListenRegistrar.ensureRegistered(this);
        } catch (Throwable ignored) {
        }
        // 确保周期兜底 Job 在跑；并立即回查一次——服务被杀后重启时，把这段空窗里
        // 漏掉的当天首次解锁用 UsageStats 历史追回（放后台线程，含 DB 与网络）。
        UnlockBackfillJobService.schedule(this);
        final Context backfillContext = getApplicationContext();
        new Thread(() -> {
            try {
                UsageUnlockBackfill.backfillTodayIfNeeded(backfillContext, "fgs_start");
            } catch (Throwable ignored) {
            }
        }, "fgs-start-backfill").start();
        // 「应用启动」轮询是可选的，仅在授权且有规则时开启。
        appOpenActive = hasUsageAccess(this) && hasEnabledAppOpenRoutine(this);
        if (appOpenActive) {
            startPolling();
        } else {
            stopPolling();
        }
        return START_STICKY;
    }

    /**
     * 从「最近任务」划掉 App 时被调用。三星等 OEM 在此场景下会杀掉进程且基本不遵守
     * START_STICKY，导致常驻通知消失、解锁监听失效。这里安排一次约 1 秒后的自重启，
     * 让前台服务与解锁监听器重新拉起（未暂停时）。setExactAndAllowWhileIdle 触发时系统
     * 会给一个临时窗口，允许在后台重新启动前台服务，绕过 Android 12+ 的后台启动限制。
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            if (!Prefs.isPaused(this) && wantsForeground(this)) {
                Intent restart = new Intent(getApplicationContext(), ForegroundAppWatcherService.class);
                int flags = PendingIntent.FLAG_ONE_SHOT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pending = PendingIntent.getService(
                        getApplicationContext(), 1, restart, flags);
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && pending != null) {
                    long triggerAt = SystemClock.elapsedRealtime() + 1000L;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
                    } else {
                        alarmManager.setExact(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopPolling();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- polling ----------

    private void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        handler.removeCallbacks(pollTask);
        handler.post(pollTask);
    }

    private void stopPolling() {
        polling = false;
        if (handler != null) {
            handler.removeCallbacks(pollTask);
        }
    }

    private void pollOnce() {
        if (usageStatsManager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long begin = Math.min(lastQueryTime, now - LOOKBACK_MS);
        UsageEvents events = usageStatsManager.queryEvents(begin, now);
        lastQueryTime = now;
        if (events == null) {
            return;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        String latest = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = event.getPackageName();
            }
        }
        if (latest == null || latest.isEmpty()) {
            return;
        }
        // 自己回到前台不触发；同一应用连续停留不重复触发。
        if (latest.equals(getPackageName()) || latest.equals(lastForegroundPackage)) {
            lastForegroundPackage = latest;
            return;
        }
        lastForegroundPackage = latest;
        final String opened = latest;
        final Context appContext = getApplicationContext();
        // DB 查询与动作执行放后台线程，避免占用主线程。
        new Thread(() -> {
            try {
                RoutineEngine.onEvent(appContext, Routine.TRIGGER_APP_OPEN, opened);
            } catch (Throwable ignored) {
            }
        }, "app-open-dispatch").start();
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(com.jinxin.unlockhub.R.string.ch_fgs), NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setDescription(getString(com.jinxin.unlockhub.R.string.ch_fgs_desc));
            manager.createNotificationChannel(channel);
        }
        Intent openSettings = new Intent(this, com.jinxin.unlockhub.SettingsActivity.class);
        openSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, NOTIF_ID, openSettings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(getString(com.jinxin.unlockhub.R.string.nt_fgs_title))
                .setContentText(getString(com.jinxin.unlockhub.R.string.nt_fgs_text))
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .build();
    }

    // ---------- static control ----------

    /**
     * 按需运行。每日首解记录已由 UnlockBackfillJobService（Job + UsageStats 回查）兜底、不依赖
     * 前台服务，因此这里只在启用了"需要进程实时存活"的功能时才启动前台服务（=显示常驻通知）：
     * 应用启动自动化、解锁触发规则、待弹解锁备忘录。其余情况不显示通知，纯靠 Job 记录。
     */
    public static void syncState(Context context) {
        try {
            Context app = context.getApplicationContext();
            Intent intent = new Intent(app, ForegroundAppWatcherService.class);
            if (Prefs.isPaused(app)) {
                app.stopService(intent);
                UnlockBackfillJobService.cancel(app);
                return;
            }
            // 兜底 Job 始终运行（这才是"不打开 App 也能记录"的保障，与通知无关）。
            UnlockBackfillJobService.schedule(app);
            if (wantsForeground(app)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent);
                } else {
                    app.startService(intent);
                }
            } else {
                app.stopService(intent); // 实时开关关闭且无实时功能：不显示常驻通知
            }
        } catch (Throwable ignored) {
            // 后台启动前台服务在部分场景会被系统拒绝，忽略即可（下次进 App 或重启会重试）。
        }
    }

    /**
     * 是否应运行前台服务（=显示常驻通知、进程实时存活）：
     * 用户开了"实时监控"总开关，或有需要实时的具体功能。
     */
    public static boolean wantsForeground(Context context) {
        return Prefs.isRealtimeMonitorEnabled(context) || needsRealtimeService(context);
    }

    /** 是否有需要"进程实时存活"的功能启用：应用启动自动化 / 解锁触发规则 / 待弹解锁备忘录。 */
    public static boolean needsRealtimeService(Context context) {
        try {
            RoutineRepository routines = new RoutineRepository(context);
            if (notEmpty(routines.enabledByTrigger(Routine.TRIGGER_APP_OPEN))
                    || notEmpty(routines.enabledByTrigger(Routine.TRIGGER_FIRST_UNLOCK))
                    || notEmpty(routines.enabledByTrigger(Routine.TRIGGER_ANY_UNLOCK))) {
                return true;
            }
            return !new com.jinxin.unlockhub.data.MemoRepository(context)
                    .unlockPopupPending().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasEnabledAppOpenRoutine(Context context) {
        try {
            List<Routine> list = new RoutineRepository(context)
                    .enabledByTrigger(Routine.TRIGGER_APP_OPEN);
            return list != null && !list.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }
}
