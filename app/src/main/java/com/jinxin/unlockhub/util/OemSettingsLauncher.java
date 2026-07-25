package com.jinxin.unlockhub.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Opens manufacturer-specific power / autostart screens when available.
 * Falls back to app details when the OEM page cannot be launched.
 */
public final class OemSettingsLauncher {
    private OemSettingsLauncher() {
    }

    public static LaunchResult openAutostart(Context context) {
        String packageName = context.getPackageName();
        Intent[] candidates = {
                component(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                ),
                component(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                component(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ),
                component(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                ),
                component(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                component(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                ),
                component(
                        "com.meizu.safe",
                        "com.meizu.safe.security.AppSecActivity"
                ),
                component(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                ),
        };
        LaunchResult result = tryCandidates(context, candidates, context.getString(com.jinxin.unlockhub.R.string.oem_autostart));
        if (result.opened) {
            return result;
        }

        Intent miuiBattery = component(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        );
        miuiBattery.putExtra("package_name", packageName);
        miuiBattery.putExtra("package_label", appLabel(context));
        result = tryOpen(context, miuiBattery, context.getString(com.jinxin.unlockhub.R.string.oem_autostart));
        if (result.opened) {
            return result;
        }

        return fallbackAppDetails(context, context.getString(com.jinxin.unlockhub.R.string.oem_no_autostart));
    }

    public static LaunchResult openBackgroundUnrestricted(Context context) {
        String packageName = context.getPackageName();
        Intent[] candidates = {
                packageIntent(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                        packageName,
                        context
                ),
                component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                ),
                component(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                ),
                component(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                ),
                component(
                        "com.vivo.abe",
                        "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                ),
                component(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                ),
                component(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                ),
        };
        LaunchResult result = tryCandidates(context, candidates, context.getString(com.jinxin.unlockhub.R.string.oem_unrestricted));
        if (result.opened) {
            return result;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            request.setData(Uri.parse("package:" + packageName));
            result = tryOpen(context, request, context.getString(com.jinxin.unlockhub.R.string.oem_unrestricted));
            if (result.opened) {
                return result;
            }
            result = tryOpen(context, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), context.getString(com.jinxin.unlockhub.R.string.oem_unrestricted));
            if (result.opened) {
                return result;
            }
        }

        return fallbackAppDetails(context, context.getString(com.jinxin.unlockhub.R.string.oem_no_battery));
    }

    public static LaunchResult openBackgroundActivity(Context context) {
        String packageName = context.getPackageName();
        Intent[] candidates = {
                component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                ),
                component(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                component(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                packageIntent(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                        packageName,
                        context
                ),
                component(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                ),
        };
        LaunchResult result = tryCandidates(context, candidates, context.getString(com.jinxin.unlockhub.R.string.oem_bg_activity));
        if (result.opened) {
            return result;
        }
        return fallbackAppDetails(context, context.getString(com.jinxin.unlockhub.R.string.oem_no_bg));
    }

    private static LaunchResult tryCandidates(Context context, Intent[] candidates, String label) {
        for (Intent candidate : candidates) {
            LaunchResult result = tryOpen(context, candidate, label);
            if (result.opened) {
                return result;
            }
        }
        return LaunchResult.failed(context, label);
    }

    private static LaunchResult tryOpen(Context context, Intent intent, String label) {
        if (intent == null || intent.getComponent() == null) {
            return LaunchResult.failed(context, label);
        }
        if (!isComponentAvailable(context, intent.getComponent())) {
            return LaunchResult.failed(context, label);
        }
        if (SystemSettingsLauncher.open(context, intent)) {
            return LaunchResult.opened(context, label, intentHint(context, intent));
        }
        return LaunchResult.failed(context, label);
    }

    private static LaunchResult fallbackAppDetails(Context context, String hint) {
        SystemSettingsLauncher.openAppDetails(context);
        return LaunchResult.fallback(hint);
    }

    private static Intent component(String pkg, String cls) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, cls));
        return intent;
    }

    private static Intent packageIntent(String pkg, String cls, String packageName, Context context) {
        Intent intent = component(pkg, cls);
        intent.putExtra("package_name", packageName);
        intent.putExtra("package_label", appLabel(context));
        intent.putExtra("packageName", packageName);
        return intent;
    }

    private static boolean isComponentAvailable(Context context, ComponentName component) {
        try {
            return context.getPackageManager().getPackageInfo(component.getPackageName(), 0) != null;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static CharSequence appLabel(Context context) {
        return context.getApplicationInfo().loadLabel(context.getPackageManager());
    }

    private static String intentHint(Context context, Intent intent) {
        ComponentName component = intent.getComponent();
        if (component == null) {
            return "";
        }
        String pkg = component.getPackageName();
        if (pkg.contains("miui")) {
            return context.getString(com.jinxin.unlockhub.R.string.oem_hint_xiaomi);
        }
        if (pkg.contains("huawei") || pkg.contains("honor")) {
            return context.getString(com.jinxin.unlockhub.R.string.oem_hint_huawei);
        }
        if (pkg.contains("coloros") || pkg.contains("oppo") || pkg.contains("oneplus")) {
            return context.getString(com.jinxin.unlockhub.R.string.oem_hint_oppo);
        }
        if (pkg.contains("vivo") || pkg.contains("iqoo")) {
            return context.getString(com.jinxin.unlockhub.R.string.oem_hint_vivo);
        }
        if (pkg.contains("samsung")) {
            return context.getString(com.jinxin.unlockhub.R.string.oem_hint_samsung);
        }
        return context.getString(com.jinxin.unlockhub.R.string.oem_hint_generic);
    }

    public static final class LaunchResult {
        public final boolean opened;
        public final boolean usedFallback;
        public final String hint;

        private LaunchResult(boolean opened, boolean usedFallback, String hint) {
            this.opened = opened;
            this.usedFallback = usedFallback;
            this.hint = hint;
        }

        static LaunchResult opened(Context context, String label, String hint) {
            return new LaunchResult(true, false, hint == null || hint.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.oem_opened, label) : hint);
        }

        static LaunchResult fallback(String hint) {
            return new LaunchResult(true, true, hint);
        }

        static LaunchResult failed(Context context, String label) {
            return new LaunchResult(false, false, context.getString(com.jinxin.unlockhub.R.string.oem_open_fail, label));
        }
    }
}
