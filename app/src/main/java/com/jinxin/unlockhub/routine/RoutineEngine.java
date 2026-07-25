package com.jinxin.unlockhub.routine;

import android.content.Context;

import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.data.RoutineRepository;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * 规则引擎：事件到达 → 匹配触发条件 → 校验约束（第二条件）→ 执行动作。
 */
public final class RoutineEngine {
    /** 同一规则两次触发的最小间隔，防抖（防止同一次解锁被系统重复广播导致弹多次）。 */
    private static final long MIN_FIRE_INTERVAL_MS = 5_000L;

    private RoutineEngine() {
    }

    /** 事件入口。eventPackage 仅 app_open 事件使用，其余传 null。任何异常静默，不影响宿主流程。 */
    public static void onEvent(Context context, String triggerType, String eventPackage) {
        try {
            onEventInternal(context, triggerType, eventPackage);
        } catch (Throwable ignored) {
        }
    }

    private static void onEventInternal(Context context, String triggerType, String eventPackage) {
        Context appContext = context.getApplicationContext();
        RoutineRepository repository = new RoutineRepository(appContext);
        List<Routine> routines = repository.enabledByTrigger(triggerType);
        long now = System.currentTimeMillis();
        for (Routine routine : routines) {
            if (now - routine.lastFiredAt < MIN_FIRE_INTERVAL_MS) {
                continue;
            }
            if (!matchesTrigger(routine, eventPackage)) {
                continue;
            }
            if (!passesConstraint(appContext, routine, now)) {
                continue;
            }
            repository.markFired(routine.id, now);
            RoutineExecutor.execute(appContext, routine);
        }
    }

    /** 定时触发由闹钟带 routineId 精确送达。 */
    public static void onTimeAlarm(Context context, long routineId) {
        try {
            onTimeAlarmInternal(context, routineId);
        } catch (Throwable ignored) {
        }
    }

    private static void onTimeAlarmInternal(Context context, long routineId) {
        Context appContext = context.getApplicationContext();
        RoutineRepository repository = new RoutineRepository(appContext);
        Routine routine = repository.findById(routineId);
        if (routine == null || !routine.enabled || !Routine.TRIGGER_TIME.equals(routine.triggerType)) {
            return;
        }
        long now = System.currentTimeMillis();
        JSONObject trigger = routine.triggerJson();
        int weekdays = trigger.optInt("weekdays", 127);
        int todayBit = mondayFirstBit(Calendar.getInstance());
        boolean dueToday = (weekdays & todayBit) != 0;
        if (dueToday && passesConstraint(appContext, routine, now)) {
            repository.markFired(routine.id, now);
            RoutineExecutor.execute(appContext, routine);
        }
        // 无论今天是否执行，都排下一次闹钟
        RoutineAlarmScheduler.schedule(appContext, routine);
    }

    /** 地理围栏触发由 PendingIntent 带 routineId 精确送达，只响应"进入"事件。 */
    public static void onPlaceProximity(Context context, long routineId, boolean entering) {
        try {
            if (!entering) {
                return;
            }
            Context appContext = context.getApplicationContext();
            RoutineRepository repository = new RoutineRepository(appContext);
            Routine routine = repository.findById(routineId);
            if (routine == null || !routine.enabled
                    || !Routine.TRIGGER_ENTER_PLACE.equals(routine.triggerType)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - routine.lastFiredAt < MIN_FIRE_INTERVAL_MS) {
                return;
            }
            if (!passesConstraint(appContext, routine, now)) {
                return;
            }
            repository.markFired(routine.id, now);
            RoutineExecutor.execute(appContext, routine);
        } catch (Throwable ignored) {
        }
    }

    private static boolean matchesTrigger(Routine routine, String eventData) {
        if (Routine.TRIGGER_APP_OPEN.equals(routine.triggerType)) {
            String target = routine.triggerJson().optString("package", "");
            return !target.isEmpty() && target.equals(eventData);
        }
        if (Routine.TRIGGER_WIFI_CONNECTED.equals(routine.triggerType)) {
            String target = routine.triggerJson().optString("ssid", "");
            return target.isEmpty() || target.equals(eventData);
        }
        if (Routine.TRIGGER_BT_CONNECTED.equals(routine.triggerType)) {
            String target = routine.triggerJson().optString("device", "");
            return target.isEmpty() || target.equals(eventData);
        }
        return true;
    }

    private static boolean passesConstraint(Context context, Routine routine, long nowMillis) {
        switch (routine.constraintType == null ? "" : routine.constraintType) {
            case Routine.CONSTRAINT_TIME_RANGE:
                return inTimeRange(routine.constraintJson(), nowMillis);
            case Routine.CONSTRAINT_CHARGING:
                return isCharging(context);
            case Routine.CONSTRAINT_NOT_CHARGING:
                return !isCharging(context);
            case Routine.CONSTRAINT_WIFI: {
                String currentSsid = ConnectionEventMonitor.currentSsid(context);
                if (currentSsid.isEmpty()) {
                    currentSsid = ConnState.wifiSsid(context);
                }
                String target = routine.constraintJson().optString("ssid", "");
                if (target.isEmpty()) {
                    return !ConnState.wifiSsid(context).isEmpty() || !currentSsid.isEmpty() || isOnWifi(context);
                }
                return target.equals(currentSsid);
            }
            case Routine.CONSTRAINT_BT: {
                String target = routine.constraintJson().optString("device", "");
                java.util.Set<String> connected = ConnState.btDevices(context);
                if (target.isEmpty()) {
                    return !connected.isEmpty();
                }
                return connected.contains(target);
            }
            case Routine.CONSTRAINT_PLACE:
                return isAtPlace(context, routine.constraintJson());
            default:
                return true;
        }
    }

    /** 约束：当前是否在指定地点半径内。取不到近期位置时视为不满足。 */
    private static boolean isAtPlace(Context context, JSONObject param) {
        double lat = param.optDouble("lat", Double.NaN);
        double lng = param.optDouble("lng", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            return false;
        }
        android.location.Location location = lastKnownLocation(context);
        if (location == null) {
            return false;
        }
        float radius = PlaceProximityScheduler.clampRadius(
                (float) param.optDouble("radius", PlaceProximityScheduler.DEFAULT_RADIUS_METERS));
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                location.getLatitude(), location.getLongitude(), lat, lng, results);
        return results[0] <= radius;
    }

    /** 各 provider 中最新的已知位置；超过 30 分钟的视为过期不用。 */
    private static android.location.Location lastKnownLocation(Context context) {
        try {
            android.location.LocationManager manager =
                    (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                return null;
            }
            android.location.Location best = null;
            for (String provider : manager.getAllProviders()) {
                android.location.Location location;
                try {
                    location = manager.getLastKnownLocation(provider);
                } catch (SecurityException e) {
                    continue;
                }
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            }
            if (best != null && System.currentTimeMillis() - best.getTime() > 30 * 60_000L) {
                return null;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isOnWifi(Context context) {
        try {
            android.net.ConnectivityManager manager =
                    (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            android.net.NetworkCapabilities capabilities =
                    manager.getNetworkCapabilities(manager.getActiveNetwork());
            return capabilities != null
                    && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    /** 支持跨天时间段（如 22:00–07:00）。 */
    static boolean inTimeRange(JSONObject param, long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        int nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int start = param.optInt("startHour", 0) * 60 + param.optInt("startMinute", 0);
        int end = param.optInt("endHour", 23) * 60 + param.optInt("endMinute", 59);
        if (start <= end) {
            return nowMinutes >= start && nowMinutes <= end;
        }
        return nowMinutes >= start || nowMinutes <= end;
    }

    private static boolean isCharging(Context context) {
        android.os.BatteryManager batteryManager =
                (android.os.BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return batteryManager != null && batteryManager.isCharging();
    }

    /** Calendar 的星期 → 周一为 bit0 的掩码位。 */
    static int mondayFirstBit(Calendar calendar) {
        int day = calendar.get(Calendar.DAY_OF_WEEK); // 1=Sunday
        int mondayIndex = day == Calendar.SUNDAY ? 6 : day - 2;
        return 1 << mondayIndex;
    }
}
