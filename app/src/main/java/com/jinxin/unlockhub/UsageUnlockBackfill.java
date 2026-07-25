package com.jinxin.unlockhub;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.scheduler.AlertScheduler;
import com.jinxin.unlockhub.sync.UnlockSync;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.TimeFormat;

/**
 * 治本的解锁记录兜底：不依赖在解锁那一刻活着。
 *
 * 用系统「使用情况」历史（PACKAGE_USAGE_STATS，已授权）回查"今天第一次解锁/亮屏"的时刻，
 * 当天若尚未记录则用真实时间补记 + 发确认通知 + 同步。即便 App 在解锁时被系统杀死/冻结，
 * 只要事后某个时刻（周期 Job / 开机 / 前台服务重启）跑到这里，就能把当天首解补上并通知——
 * 无需用户打开 App，也无需打开 App 才能确认（记录成功会弹一条普通通知）。
 */
public final class UsageUnlockBackfill {

    // UsageEvents.Event 常量（KEYGUARD_HIDDEN/SCREEN_INTERACTIVE 自 API 28 公开，
    // 这里用字面量以避免 minSdk 26 的 NewApi 顾虑；低版本设备不产生这些事件，会退回到首个前台事件）。
    private static final int KEYGUARD_HIDDEN = 18;      // 解锁（keyguard 隐藏）——最准
    private static final int SCREEN_INTERACTIVE = 15;   // 亮屏可交互——次选

    private UsageUnlockBackfill() {
    }

    /**
     * 纯历史回查并补记今天的首次解锁；成功则发一条确认通知。查不到今日解锁事件则不记录。
     *
     * @return 是否新写入了一条记录。
     */
    public static boolean backfillTodayIfNeeded(Context context, String source) {
        if (context == null || Prefs.isPaused(context)) {
            return false;
        }
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        String today = TimeFormat.localDate(now);
        UnlockRepository repository = new UnlockRepository(app);
        if (repository.findByDate(today) != null) {
            return false; // 今天已记录，无需补
        }
        long unlockAt = firstUnlockTimestampToday(app, now);
        if (unlockAt <= 0L) {
            return false; // 历史里今天还没有解锁事件（可能确实还没解锁，或无使用情况权限）
        }
        repository.recordFirstUnlockIfNeeded(unlockAt);
        Prefs.setLastAutoCapture(app, unlockAt, source);
        AlertScheduler.scheduleNextCheck(app, unlockAt);
        // 后台记录成功 → 普通抬头通知作为确认（后台 Job 里全屏弹窗不可靠，普通通知最稳）。
        try {
            NotificationHelper.notifyRecorded(app, today);
        } catch (Throwable ignored) {
        }
        try {
            UnlockSync.syncAfterCapture(app, false);
            Prefs.setLastSyncError(app, "");
        } catch (Exception syncError) {
            Prefs.setLastSyncError(app, syncError.getMessage());
            UnlockSync.scheduleRetryIfNeeded(app);
        }
        notifyDashboardRefresh(app);
        return true;
    }

    /** 从 UsageStats 历史中取今天最早的解锁时刻：KEYGUARD_HIDDEN 优先，其次亮屏，再次首个前台应用。 */
    private static long firstUnlockTimestampToday(Context context, long now) {
        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return 0L;
        }
        long startOfToday = TimeFormat.startOfDayMillis(TimeFormat.localDate(now));
        UsageEvents events;
        try {
            events = usm.queryEvents(startOfToday, now);
        } catch (Throwable t) {
            return 0L;
        }
        if (events == null) {
            return 0L;
        }
        long firstKeyguardHidden = 0L;
        long firstScreenInteractive = 0L;
        long firstForeground = 0L;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            long ts = event.getTimeStamp();
            if (ts < startOfToday) {
                continue;
            }
            int type = event.getEventType();
            if (type == KEYGUARD_HIDDEN) {
                if (firstKeyguardHidden == 0L || ts < firstKeyguardHidden) {
                    firstKeyguardHidden = ts;
                }
            } else if (type == SCREEN_INTERACTIVE) {
                if (firstScreenInteractive == 0L || ts < firstScreenInteractive) {
                    firstScreenInteractive = ts;
                }
            } else if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (firstForeground == 0L || ts < firstForeground) {
                    firstForeground = ts;
                }
            }
        }
        if (firstKeyguardHidden > 0L) {
            return firstKeyguardHidden;
        }
        if (firstScreenInteractive > 0L) {
            return firstScreenInteractive;
        }
        return firstForeground; // 可能为 0（今天还没用过任何应用）
    }

    private static void notifyDashboardRefresh(Context context) {
        Intent intent = new Intent(UnlockListenRegistrar.ACTION_DASHBOARD_REFRESH);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
