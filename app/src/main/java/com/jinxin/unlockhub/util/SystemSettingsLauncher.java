package com.jinxin.unlockhub.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.jinxin.unlockhub.NotificationHelper;

public final class SystemSettingsLauncher {
    private SystemSettingsLauncher() {
    }

    public static boolean open(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void openAppNotificationSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            if (open(context, intent)) {
                return;
            }
        }
        openAppDetails(context);
    }

    public static void openUrgentNotificationChannel(Context context) {
        if (!open(context, NotificationHelper.notificationSettingsIntent(context))) {
            openAppNotificationSettings(context);
        }
    }

    public static void openBatteryOptimization(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = context.getSystemService(PowerManager.class);
            String packageName = context.getPackageName();
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                request.setData(Uri.parse("package:" + packageName));
                if (open(context, request)) {
                    return;
                }
            }
            open(context, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            return;
        }
        openAppDetails(context);
    }

    public static void openExactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            if (open(context, intent)) {
                return;
            }
        }
        openAppDetails(context);
    }

    public static void openFullScreenIntentSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            if (open(context, intent)) {
                return;
            }
        }
        openAppNotificationSettings(context);
    }

    public static void openDoNotDisturbAccess(Context context) {
        if (!open(context, new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))) {
            openAppNotificationSettings(context);
        }
    }

    public static void openAppDetails(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        open(context, intent);
    }

    public static void openAccessibilitySettings(Context context) {
        if (!open(context, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
            openAppDetails(context);
        }
    }
}
