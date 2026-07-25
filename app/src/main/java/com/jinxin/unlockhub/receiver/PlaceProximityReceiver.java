package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import com.jinxin.unlockhub.routine.RoutineEngine;

/** 地理围栏事件入口：进入指定地点半径时由系统唤醒。 */
public final class PlaceProximityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false);
        long routineId = intent.getLongExtra("routineId", 0L);
        RoutineEngine.onPlaceProximity(context, routineId, entering);
    }
}
