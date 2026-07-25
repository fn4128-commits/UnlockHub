package com.jinxin.unlockhub.routine;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.data.RoutineRepository;
import com.jinxin.unlockhub.receiver.PlaceProximityReceiver;

import org.json.JSONObject;

import java.util.List;

/**
 * 「到达指定地点」触发的地理围栏调度：每条规则一个 ProximityAlert，
 * 由系统在进入半径时唤醒 PlaceProximityReceiver。
 * 需要定位权限；后台触发建议将定位权限设为「始终允许」。
 */
public final class PlaceProximityScheduler {
    public static final float MIN_RADIUS_METERS = 100f;
    public static final float MAX_RADIUS_METERS = 5000f;
    public static final float DEFAULT_RADIUS_METERS = 300f;

    private PlaceProximityScheduler() {
    }

    public static void schedule(Context context, Routine routine) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return;
        }
        PendingIntent pendingIntent = pendingIntent(context, routine.id);
        try {
            manager.removeProximityAlert(pendingIntent);
        } catch (Exception ignored) {
        }
        if (!routine.enabled || !Routine.TRIGGER_ENTER_PLACE.equals(routine.triggerType)) {
            return;
        }
        JSONObject trigger = routine.triggerJson();
        double lat = trigger.optDouble("lat", Double.NaN);
        double lng = trigger.optDouble("lng", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            return;
        }
        float radius = clampRadius((float) trigger.optDouble("radius", DEFAULT_RADIUS_METERS));
        try {
            manager.addProximityAlert(lat, lng, radius, -1L, pendingIntent);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // 无定位权限时静默；用户授权后再次保存/进入自动化页会重建
        }
    }

    public static void cancel(Context context, long routineId) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.removeProximityAlert(pendingIntent(context, routineId));
        } catch (Exception ignored) {
        }
    }

    /** 开机 / 进入自动化页时重建全部地点围栏。 */
    public static void rescheduleAll(Context context) {
        List<Routine> routines = new RoutineRepository(context).enabledByTrigger(Routine.TRIGGER_ENTER_PLACE);
        for (Routine routine : routines) {
            schedule(context, routine);
        }
    }

    public static float clampRadius(float radius) {
        if (Float.isNaN(radius) || radius < MIN_RADIUS_METERS) {
            return MIN_RADIUS_METERS;
        }
        return Math.min(radius, MAX_RADIUS_METERS);
    }

    private static PendingIntent pendingIntent(Context context, long routineId) {
        Intent intent = new Intent(context, PlaceProximityReceiver.class);
        intent.putExtra("routineId", routineId);
        return PendingIntent.getBroadcast(
                context,
                (int) (7200 + routineId % 100000),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
