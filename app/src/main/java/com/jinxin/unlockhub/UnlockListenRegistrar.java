package com.jinxin.unlockhub;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.jinxin.unlockhub.sync.UnlockSync;
import com.jinxin.unlockhub.util.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 解锁监听（无常驻通知也能工作）。
 *
 * 重要平台事实（已在 Android 15 实测）：后台应用（哪怕挂前台服务）的动态接收器**收不到
 * ACTION_USER_PRESENT**（系统按后台策略把它从投递名单里排除），但**能收到 ACTION_SCREEN_ON**。
 * 因此不能只靠 USER_PRESENT。这里的策略：
 *  - 收到 SCREEN_ON 后，启动一个短时"锁屏观察"：每秒查一次 KeyguardManager.isKeyguardLocked()，
 *    一旦变为"未锁"（=用户已解锁 / 无锁屏直接亮屏）就当作解锁，触发 capture + 备忘弹窗；
 *  - 若 USER_PRESENT 恰好也送达（前台等场景），同样触发；
 *  - 每个"亮屏周期"只触发一次（SCREEN_OFF 复位），避免重复。
 */
public final class UnlockListenRegistrar {
    public static final String ACTION_DASHBOARD_REFRESH = "com.jinxin.unlockhub.DASHBOARD_REFRESH";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long KEYGUARD_POLL_MS = 1000L;
    private static final int KEYGUARD_POLL_MAX = 45; // 最多观察 ~45 秒
    private static BroadcastReceiver screenReceiver;
    private static Handler keyguardHandler;
    private static boolean firedThisWake; // 本次亮屏周期是否已触发过解锁处理

    private UnlockListenRegistrar() {
    }

    public static void ensureRegistered(Context context) {
        if (Prefs.isPaused(context)) {
            unregisterScreenReceiver(context);
            return;
        }
        if (screenReceiver != null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Prefs.isPaused(ctx)) {
                    return;
                }
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    fireUnlockOnce(ctx, "user_present");
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    notifyDashboardRefresh(ctx);
                    startKeyguardWatch(ctx); // 关键：靠 SCREEN_ON + 锁屏观察补上收不到的 USER_PRESENT
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    firedThisWake = false; // 新的亮屏周期重新允许触发
                    stopKeyguardWatch();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(screenReceiver, filter);
        }
    }

    /** SCREEN_ON 后每秒查锁屏状态，检测到"未锁"即触发一次解锁处理；息屏/超时/已触发则停止。 */
    private static void startKeyguardWatch(Context context) {
        final Context app = context.getApplicationContext();
        if (keyguardHandler == null) {
            keyguardHandler = new Handler(Looper.getMainLooper());
        }
        keyguardHandler.removeCallbacksAndMessages(null);
        final KeyguardManager km = (KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE);
        final int[] attempts = {0};
        keyguardHandler.post(new Runnable() {
            @Override
            public void run() {
                if (firedThisWake || Prefs.isPaused(app)) {
                    return;
                }
                boolean locked = km != null && km.isKeyguardLocked();
                if (!locked) {
                    fireUnlockOnce(app, "screen_on_unlocked");
                    return;
                }
                if (++attempts[0] < KEYGUARD_POLL_MAX && keyguardHandler != null) {
                    keyguardHandler.postDelayed(this, KEYGUARD_POLL_MS);
                }
            }
        });
    }

    private static void stopKeyguardWatch() {
        if (keyguardHandler != null) {
            keyguardHandler.removeCallbacksAndMessages(null);
        }
    }

    /** 本次亮屏周期只处理一次解锁：记录 + 备忘弹窗等。 */
    private static void fireUnlockOnce(Context context, String source) {
        if (firedThisWake) {
            return;
        }
        firedThisWake = true;
        stopKeyguardWatch();
        handleUserPresent(context);
    }

    public static void handleUserPresent(Context context) {
        handleUserPresent(context, null);
    }

    public static void handleUserPresent(Context context, BroadcastReceiver.PendingResult pendingResult) {
        if (Prefs.isPaused(context)) {
            if (pendingResult != null) {
                pendingResult.finish();
            }
            return;
        }
        Context appContext = context.getApplicationContext();
        long nowMillis = System.currentTimeMillis();
        EXECUTOR.execute(() -> {
            PowerManager.WakeLock wakeLock = acquireCaptureWakeLock(appContext);
            try {
                UnlockCapture.capture(appContext, nowMillis, "unlock");
                notifyDashboardRefresh(appContext);
            } catch (Exception ignored) {
                UnlockSync.scheduleRetryIfNeeded(appContext);
            } finally {
                releaseWakeLock(wakeLock);
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }

    private static void unregisterScreenReceiver(Context context) {
        stopKeyguardWatch();
        if (screenReceiver == null) {
            return;
        }
        try {
            context.getApplicationContext().unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        screenReceiver = null;
    }

    private static PowerManager.WakeLock acquireCaptureWakeLock(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return null;
        }
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "UnlockHub:UnlockCapture"
        );
        wakeLock.acquire(15_000L);
        return wakeLock;
    }

    private static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private static void notifyDashboardRefresh(Context context) {
        Intent intent = new Intent(ACTION_DASHBOARD_REFRESH);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
