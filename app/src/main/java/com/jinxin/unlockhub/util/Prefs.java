package com.jinxin.unlockhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Prefs {
    public static final String SYNC_MODE_WEEKDAY = "weekday";
    public static final String SYNC_MODE_INTERVAL = "interval";

    private static final String NAME = "unlock_hub";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PUBLIC_ID = "public_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_GUARDIAN_HANDLE = "guardian_handle";
    private static final String KEY_RECEIVER_ACCESS_KEY = "receiver_access_key";
    private static final String KEY_BACKEND_URL = "backend_url";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_LAST_ALERT_AT = "last_alert_at";
    private static final String KEY_ACCOUNT_BOUND = "account_bound";
    private static final String KEY_LAST_AUTO_CAPTURE_AT = "last_auto_capture_at";
    private static final String KEY_LAST_AUTO_CAPTURE_SOURCE = "last_auto_capture_source";
    private static final String KEY_LAST_SYNC_ERROR = "last_sync_error";
    private static final String KEY_SYNC_MODE = "sync_mode";
    private static final String KEY_SYNC_WEEKDAYS_MASK = "sync_weekdays_mask";
    private static final String KEY_SYNC_INTERVAL_DAYS = "sync_interval_days";
    private static final String KEY_SYNC_ANCHOR_DATE = "sync_anchor_date";
    private static final String KEY_LAST_SYNCED_PERIOD_DUE_DATE = "last_synced_period_due_date";
    private static final String KEY_SYNC_UPLOAD_HISTORY = "sync_upload_history";
    private static final String KEY_BOUND_APP_PACKAGES = "bound_app_packages";
    private static final String KEY_LIGHT_THEME = "light_theme";
    private static final String KEY_QUICK_ADD_MEMO = "quick_add_memo";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean quickAddMemoEnabled(Context context) {
        return prefs(context).getBoolean(KEY_QUICK_ADD_MEMO, true);
    }

    public static void setQuickAddMemoEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_QUICK_ADD_MEMO, enabled).apply();
    }

    public static String deviceId(Context context) {
        SharedPreferences preferences = prefs(context);
        String id = preferences.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static String publicId(Context context) {
        String id = prefs(context).getString(KEY_PUBLIC_ID, "");
        return id == null ? "" : id;
    }

    public static void setPublicId(Context context, String value) {
        prefs(context).edit().putString(KEY_PUBLIC_ID, value.trim()).apply();
    }

    public static boolean isAccountBound(Context context) {
        return prefs(context).getBoolean(KEY_ACCOUNT_BOUND, false);
    }

    public static void setAccountBound(Context context, boolean bound) {
        prefs(context).edit().putBoolean(KEY_ACCOUNT_BOUND, bound).apply();
    }

    public static String displayName(Context context) {
        return prefs(context).getString(KEY_DISPLAY_NAME, "Me");
    }

    public static void setDisplayName(Context context, String value) {
        prefs(context).edit().putString(KEY_DISPLAY_NAME, value.trim()).apply();
    }

    public static String guardianHandle(Context context) {
        return prefs(context).getString(KEY_GUARDIAN_HANDLE, "");
    }

    public static void setGuardianHandle(Context context, String value) {
        prefs(context).edit().putString(KEY_GUARDIAN_HANDLE, value.trim()).apply();
    }

    public static String receiverAccessKey(Context context) {
        return prefs(context).getString(KEY_RECEIVER_ACCESS_KEY, "");
    }

    public static void setReceiverAccessKey(Context context, String value) {
        prefs(context).edit().putString(KEY_RECEIVER_ACCESS_KEY, value.trim()).apply();
    }

    private static final String DEFAULT_BACKEND_URL = "https://safeping.unlockhub.workers.dev";

    public static String backendUrl(Context context) {
        String stored = prefs(context).getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL);
        // 域名迁移：老版本存过旧地址的设备自动切到新域名
        if (stored != null && stored.contains("safeping-fn412")) {
            prefs(context).edit().putString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL).apply();
            return DEFAULT_BACKEND_URL;
        }
        return stored;
    }

    public static void setBackendUrl(Context context, String value) {
        prefs(context).edit().putString(KEY_BACKEND_URL, trimTrailingSlash(value.trim())).apply();
    }

    private static final String KEY_UNLOCK_POPUP = "unlock_popup_enabled";

    /** 解锁-弹窗（状态确认）：首次解锁记录成功后整页弹窗告知「已记录」。 */
    public static boolean isUnlockPopupEnabled(Context context) {
        return prefs(context).getBoolean(KEY_UNLOCK_POPUP, false);
    }

    public static void setUnlockPopupEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_UNLOCK_POPUP, enabled).apply();
    }

    private static final String KEY_REALTIME_MONITOR = "realtime_monitor_enabled";

    /**
     * 实时监控总开关（默认开）。
     * 开：前台服务常驻，解锁瞬间实时记录/确认/弹窗（有常驻通知）。
     * 关：仅在有实时功能（解锁弹窗备忘录/解锁规则/应用启动自动化）时才按需开启前台服务；
     *     纯签到记录场景无通知，由后台 Job 定时回查补记（有延迟）。
     */
    public static boolean isRealtimeMonitorEnabled(Context context) {
        return prefs(context).getBoolean(KEY_REALTIME_MONITOR, true);
    }

    public static void setRealtimeMonitorEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REALTIME_MONITOR, enabled).apply();
    }

    private static final String KEY_APP_LANGUAGE = "app_language";

    /** 应用语言："zh"（默认，中文）或 "en"（英文）。空/未设 = 跟随默认中文。 */
    public static String appLanguage(Context context) {
        String v = prefs(context).getString(KEY_APP_LANGUAGE, "zh");
        return (v == null || v.isEmpty()) ? "zh" : v;
    }

    public static void setAppLanguage(Context context, String language) {
        prefs(context).edit().putString(KEY_APP_LANGUAGE, language).apply();
    }

    /** 主题：false = 深色（默认），true = 浅色。主页头部按钮切换。 */
    public static boolean isLightTheme(Context context) {
        return prefs(context).getBoolean(KEY_LIGHT_THEME, false);
    }

    public static void setLightTheme(Context context, boolean light) {
        prefs(context).edit().putBoolean(KEY_LIGHT_THEME, light).apply();
    }

    public static boolean isPaused(Context context) {
        return prefs(context).getBoolean(KEY_PAUSED, false);
    }

    public static void setPaused(Context context, boolean paused) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    public static long lastAlertAt(Context context) {
        return prefs(context).getLong(KEY_LAST_ALERT_AT, 0L);
    }

    public static void setLastAlertAt(Context context, long value) {
        prefs(context).edit().putLong(KEY_LAST_ALERT_AT, value).apply();
    }

    public static long lastAutoCaptureAt(Context context) {
        return prefs(context).getLong(KEY_LAST_AUTO_CAPTURE_AT, 0L);
    }

    public static String lastAutoCaptureSource(Context context) {
        return prefs(context).getString(KEY_LAST_AUTO_CAPTURE_SOURCE, "");
    }

    public static void setLastAutoCapture(Context context, long value, String source) {
        prefs(context).edit()
                .putLong(KEY_LAST_AUTO_CAPTURE_AT, value)
                .putString(KEY_LAST_AUTO_CAPTURE_SOURCE, source)
                .apply();
    }

    public static String lastSyncError(Context context) {
        return prefs(context).getString(KEY_LAST_SYNC_ERROR, "");
    }

    public static void setLastSyncError(Context context, String value) {
        prefs(context).edit().putString(KEY_LAST_SYNC_ERROR, value == null ? "" : value).apply();
    }

    public static String syncMode(Context context) {
        String mode = prefs(context).getString(KEY_SYNC_MODE, SYNC_MODE_WEEKDAY);
        return SYNC_MODE_INTERVAL.equals(mode) ? SYNC_MODE_INTERVAL : SYNC_MODE_WEEKDAY;
    }

    public static void setSyncMode(Context context, String mode) {
        String value = SYNC_MODE_INTERVAL.equals(mode) ? SYNC_MODE_INTERVAL : SYNC_MODE_WEEKDAY;
        prefs(context).edit().putString(KEY_SYNC_MODE, value).apply();
    }

    public static int syncWeekdaysMask(Context context) {
        int mask = prefs(context).getInt(KEY_SYNC_WEEKDAYS_MASK, 0);
        return mask == 0 ? 1 : mask;
    }

    public static void setSyncWeekdaysMask(Context context, int mask) {
        prefs(context).edit().putInt(KEY_SYNC_WEEKDAYS_MASK, mask & 0x7F).apply();
    }

    public static int syncIntervalDays(Context context) {
        return prefs(context).getInt(KEY_SYNC_INTERVAL_DAYS, 7);
    }

    public static void setSyncIntervalDays(Context context, int days) {
        int clamped = Math.max(1, Math.min(days, 180));
        prefs(context).edit().putInt(KEY_SYNC_INTERVAL_DAYS, clamped).apply();
    }

    public static String syncAnchorDate(Context context) {
        return prefs(context).getString(KEY_SYNC_ANCHOR_DATE, "");
    }

    public static void setSyncAnchorDate(Context context, String value) {
        prefs(context).edit().putString(KEY_SYNC_ANCHOR_DATE, value == null ? "" : value.trim()).apply();
    }

    public static String lastSyncedPeriodDueDate(Context context) {
        return prefs(context).getString(KEY_LAST_SYNCED_PERIOD_DUE_DATE, "");
    }

    public static void setLastSyncedPeriodDueDate(Context context, String value) {
        prefs(context).edit().putString(KEY_LAST_SYNCED_PERIOD_DUE_DATE, value == null ? "" : value.trim()).apply();
    }

    public static void appendSyncUploadRecord(Context context, String periodStart, String periodEnd) {
        String existing = prefs(context).getString(KEY_SYNC_UPLOAD_HISTORY, "");
        String entry = periodStart + "|" + periodEnd;
        String updated = existing == null || existing.isEmpty() ? entry : entry + "\n" + existing;
        prefs(context).edit().putString(KEY_SYNC_UPLOAD_HISTORY, updated).apply();
    }

    public static String[] syncUploadHistory(Context context) {
        String raw = prefs(context).getString(KEY_SYNC_UPLOAD_HISTORY, "");
        if (raw == null || raw.isEmpty()) {
            return new String[0];
        }
        return raw.split("\n");
    }

    public static Set<String> boundAppPackages(Context context) {
        String raw = prefs(context).getString(KEY_BOUND_APP_PACKAGES, "");
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> packages = new HashSet<>();
        Collections.addAll(packages, raw.split("\n"));
        packages.remove("");
        return packages;
    }

    public static void setBoundAppPackages(Context context, Set<String> packages) {
        StringBuilder builder = new StringBuilder();
        String[] sorted = packages.toArray(new String[0]);
        Arrays.sort(sorted);
        for (String sortedPackage : sorted) {
            if (sortedPackage == null || sortedPackage.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(sortedPackage);
        }
        prefs(context).edit().putString(KEY_BOUND_APP_PACKAGES, builder.toString()).apply();
    }

    public static boolean isBoundApp(Context context, String packageName) {
        return packageName != null && boundAppPackages(context).contains(packageName);
    }

    public static int boundAppCount(Context context) {
        return boundAppPackages(context).size();
    }

    public static boolean hasSavedSession(Context context) {
        return isAccountBound(context)
                && publicId(context) != null
                && !publicId(context).isEmpty()
                && receiverAccessKey(context) != null
                && !receiverAccessKey(context).isEmpty();
    }

    public static void clearAccount(Context context) {
        prefs(context).edit()
                .putString(KEY_PUBLIC_ID, "")
                .putString(KEY_GUARDIAN_HANDLE, "")
                .putString(KEY_RECEIVER_ACCESS_KEY, "")
                .putBoolean(KEY_ACCOUNT_BOUND, false)
                .apply();
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
