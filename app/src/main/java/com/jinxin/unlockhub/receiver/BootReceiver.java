package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.UnlockListenRegistrar;
import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.scheduler.AlertScheduler;
import com.jinxin.unlockhub.util.Prefs;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                com.jinxin.unlockhub.scheduler.MemoReminderScheduler.rescheduleAll(context);
                com.jinxin.unlockhub.routine.RoutineAlarmScheduler.rescheduleAll(context);
                com.jinxin.unlockhub.routine.PlaceProximityScheduler.rescheduleAll(context);
                com.jinxin.unlockhub.MemoNotifier.updateBadge(context);
                com.jinxin.unlockhub.MemoNotifier.applyQuickAdd(context);
                com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(context);
                com.jinxin.unlockhub.service.UnlockBackfillJobService.schedule(context);
            } catch (Exception ignored) {
                // 附加功能异常不能影响核心解锁监听的恢复
            }
            if (!Prefs.isPaused(context)) {
                com.jinxin.unlockhub.NotificationHelper.ensureChannels(context);
                UnlockListenRegistrar.ensureRegistered(context);
                UnlockRepository repository = new UnlockRepository(context);
                long lastActivityAt = repository.lastActivityAt();
                AlertScheduler.scheduleNextCheck(context, lastActivityAt);
                com.jinxin.unlockhub.sync.UnlockSync.scheduleRetryIfNeeded(context);
                // 开机后立即回查一次：把关机期间/开机后到现在漏掉的当天首解追回（后台线程，含网络）。
                final android.content.BroadcastReceiver.PendingResult pending = goAsync();
                new Thread(() -> {
                    try {
                        com.jinxin.unlockhub.UsageUnlockBackfill.backfillTodayIfNeeded(context, "boot");
                    } catch (Throwable ignored) {
                    } finally {
                        pending.finish();
                    }
                }, "boot-backfill").start();
            }
        }
    }
}
