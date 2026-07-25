package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.routine.RoutineEngine;

public final class RoutineAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long routineId = intent.getLongExtra("routineId", 0L);
        if (routineId > 0) {
            RoutineEngine.onTimeAlarm(context, routineId);
        }
    }
}
