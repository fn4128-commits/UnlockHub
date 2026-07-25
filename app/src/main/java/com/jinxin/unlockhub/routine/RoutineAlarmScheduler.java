package com.jinxin.unlockhub.routine;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.data.RoutineRepository;
import com.jinxin.unlockhub.receiver.RoutineAlarmReceiver;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/** 定时类规则的闹钟调度：每条规则一个闹钟，触发后由引擎重排下一次。 */
public final class RoutineAlarmScheduler {
    private RoutineAlarmScheduler() {
    }

    public static void schedule(Context context, Routine routine) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = pendingIntent(context, routine.id);
        if (!routine.enabled || !Routine.TRIGGER_TIME.equals(routine.triggerType)) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        long triggerAt = nextTriggerAt(routine, System.currentTimeMillis());
        if (triggerAt <= 0) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException e) {
            // 部分 ROM 权限判定不一致，降级为不精确闹钟
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void cancel(Context context, long routineId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, routineId));
        }
    }

    /** 开机 / 进入自动化页时重建全部定时闹钟。 */
    public static void rescheduleAll(Context context) {
        List<Routine> routines = new RoutineRepository(context).enabledByTrigger(Routine.TRIGGER_TIME);
        for (Routine routine : routines) {
            schedule(context, routine);
        }
    }

    /** 下一次触发时间：今天 HH:mm 已过则明天，跳过未勾选的星期。 */
    static long nextTriggerAt(Routine routine, long nowMillis) {
        JSONObject trigger = routine.triggerJson();
        int hour = trigger.optInt("hour", -1);
        int minute = trigger.optInt("minute", 0);
        int weekdays = trigger.optInt("weekdays", 127);
        if (hour < 0 || weekdays == 0) {
            return 0;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= nowMillis) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        for (int i = 0; i < 7; i++) {
            if ((weekdays & RoutineEngine.mondayFirstBit(calendar)) != 0) {
                return calendar.getTimeInMillis();
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    private static PendingIntent pendingIntent(Context context, long routineId) {
        Intent intent = new Intent(context, RoutineAlarmReceiver.class);
        intent.putExtra("routineId", routineId);
        return PendingIntent.getBroadcast(
                context,
                (int) (7000 + routineId % 100000),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
