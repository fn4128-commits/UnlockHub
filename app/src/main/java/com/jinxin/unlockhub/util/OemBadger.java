package com.jinxin.unlockhub.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

/**
 * 桌面数字角标 OEM 适配。
 * 原生 Android 只支持通知角标（数字是否显示取决于桌面）；
 * 华为/荣耀、三星等有私有接口，这里做尽力而为的适配，全部静默失败。
 */
public final class OemBadger {
    private OemBadger() {
    }

    public static void apply(Context context, int count) {
        int safeCount = Math.max(0, Math.min(count, 99));
        String launcherClass = launcherClassName(context);
        if (launcherClass == null) {
            return;
        }
        applyHuawei(context, launcherClass, safeCount);
        applyLegacyBroadcast(context, launcherClass, safeCount);
    }

    /** 华为 / 荣耀（EMUI、MagicOS）。 */
    private static void applyHuawei(Context context, String launcherClass, int count) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        if (!manufacturer.contains("huawei") && !manufacturer.contains("honor")) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put("package", context.getPackageName());
            values.put("class", launcherClass);
            values.put("badgenumber", count);
            context.getContentResolver().insert(
                    Uri.parse("content://com.huawei.android.launcher.settings/badge/"),
                    values
            );
        } catch (Exception ignored) {
        }
    }

    /** 三星及部分旧桌面通用广播。 */
    private static void applyLegacyBroadcast(Context context, String launcherClass, int count) {
        try {
            Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
            intent.putExtra("badge_count", count);
            intent.putExtra("badge_count_package_name", context.getPackageName());
            intent.putExtra("badge_count_class_name", launcherClass);
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

    private static String launcherClassName(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent == null) {
                return null;
            }
            ComponentName component = intent.getComponent();
            return component == null ? null : component.getClassName();
        } catch (Exception e) {
            return null;
        }
    }
}
