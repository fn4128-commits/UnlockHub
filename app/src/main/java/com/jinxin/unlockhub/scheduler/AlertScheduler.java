package com.jinxin.unlockhub.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jinxin.unlockhub.receiver.AlertCheckReceiver;

public final class AlertScheduler {
    public static final long INACTIVITY_WINDOW_MILLIS = 72L * 60L * 60L * 1000L;

    private AlertScheduler() {
    }

    public static void scheduleNextCheck(Context context, long lastActivityAt) {
        if (lastActivityAt <= 0L) {
            return;
        }
        long triggerAt = lastActivityAt + INACTIVITY_WINDOW_MILLIS;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = pendingIntent(context);
        if (alarmManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, AlertCheckReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 1001, intent, flags);
    }
}
