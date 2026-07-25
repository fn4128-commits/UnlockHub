package com.jinxin.unlockhub.util;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

public final class BatteryOptimizationStatus {
    private BatteryOptimizationStatus() {
    }

    public static boolean isIgnoringOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** Short label for the settings summary line. */
    public static String summaryLine(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return context.getString(com.jinxin.unlockhub.R.string.bo_na);
        }
        if (isIgnoringOptimizations(context)) {
            return context.getString(com.jinxin.unlockhub.R.string.bo_off);
        }
        return context.getString(com.jinxin.unlockhub.R.string.bo_on);
    }

}
