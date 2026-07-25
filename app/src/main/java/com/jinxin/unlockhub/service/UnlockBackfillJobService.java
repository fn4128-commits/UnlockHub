package com.jinxin.unlockhub.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.jinxin.unlockhub.UsageUnlockBackfill;
import com.jinxin.unlockhub.util.Prefs;

/**
 * 周期性兜底 Job：定期唤醒，回查 UsageStats 历史并补记当天首次解锁。
 *
 * 这是"不打开 App 也能记录"的关键——它不依赖在解锁那一刻存活，只要一天里系统让它跑过
 * 一次即可把当天首解追回（配合电池白名单，JobScheduler 会稳定运行）。
 * setPersisted(true) 让它在重启后仍然存在（需 RECEIVE_BOOT_COMPLETED，已声明）。
 */
public final class UnlockBackfillJobService extends JobService {

    private static final int JOB_ID = 7412;
    // JobScheduler 周期下限为 15 分钟；系统会按 Doze / OEM 策略在此基础上适当延后。
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                UsageUnlockBackfill.backfillTodayIfNeeded(getApplicationContext(), "job");
            } catch (Throwable ignored) {
            }
            jobFinished(params, false);
        }, "unlock-backfill-job").start();
        return true; // 工作在后台线程继续
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // 被系统中断则允许重排
    }

    /** 确保周期兜底 Job 已排程（暂停时改为取消）。重复调用不会重置已存在的周期。 */
    public static void schedule(Context context) {
        try {
            Context app = context.getApplicationContext();
            JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return;
            }
            if (Prefs.isPaused(app)) {
                scheduler.cancel(JOB_ID);
                return;
            }
            if (scheduler.getPendingJob(JOB_ID) != null) {
                return; // 已排程，避免每次调用重置周期计时
            }
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(app, UnlockBackfillJobService.class))
                    .setPersisted(true)
                    .setPeriodic(PERIOD_MS)
                    .build();
            scheduler.schedule(job);
        } catch (Throwable ignored) {
        }
    }

    public static void cancel(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getApplicationContext()
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.cancel(JOB_ID);
            }
        } catch (Throwable ignored) {
        }
    }
}
