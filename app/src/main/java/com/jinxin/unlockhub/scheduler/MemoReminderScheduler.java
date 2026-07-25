package com.jinxin.unlockhub.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;
import com.jinxin.unlockhub.receiver.MemoReminderReceiver;

import java.util.List;

public final class MemoReminderScheduler {
    private MemoReminderScheduler() {
    }

    /** 是否允许精确闹钟（Android 12+ 需要用户在系统设置中授权）。 */
    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    /** 跳转系统「闹钟和提醒」授权页的 Intent（Android 12+）。 */
    public static Intent exactAlarmSettingsIntent(Context context) {
        Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    /** 为单条备忘设置（或取消）提醒闹钟。 */
    public static void schedule(Context context, Memo memo) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = pendingIntent(context, memo.id);
        if (memo.remindAt <= System.currentTimeMillis()) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, memo.remindAt, pendingIntent);
                return;
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, memo.remindAt, pendingIntent);
        } catch (SecurityException e) {
            // 部分 ROM 权限判定不一致，降级为不精确闹钟
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, memo.remindAt, pendingIntent);
        }
    }

    public static void cancel(Context context, long memoId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(pendingIntent(context, memoId));
    }

    /** 开机后重建所有未来提醒。 */
    public static void rescheduleAll(Context context) {
        List<Memo> pending = new MemoRepository(context).pendingReminders(System.currentTimeMillis());
        for (Memo memo : pending) {
            schedule(context, memo);
        }
    }

    private static PendingIntent pendingIntent(Context context, long memoId) {
        Intent intent = new Intent(context, MemoReminderReceiver.class);
        intent.putExtra("memoId", memoId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, (int) (5000 + memoId % 100000), intent, flags);
    }
}
