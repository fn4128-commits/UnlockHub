package com.jinxin.unlockhub.routine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.jinxin.unlockhub.data.Routine;

/**
 * 充电 / 低电量事件监听。
 * Android 8+ 这些广播不能在清单里注册，必须在进程内动态注册；
 * 应用进程存活期间有效（无障碍服务开启时进程基本常驻）。
 */
public final class PowerEventMonitor {
    private static BroadcastReceiver receiver;

    private PowerEventMonitor() {
    }

    public static synchronized void ensureRegistered(Context context) {
        if (receiver != null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                String action = intent.getAction() == null ? "" : intent.getAction();
                switch (action) {
                    case Intent.ACTION_POWER_CONNECTED:
                        RoutineEngine.onEvent(receiverContext, Routine.TRIGGER_CHARGER_ON, null);
                        break;
                    case Intent.ACTION_POWER_DISCONNECTED:
                        RoutineEngine.onEvent(receiverContext, Routine.TRIGGER_CHARGER_OFF, null);
                        break;
                    case Intent.ACTION_BATTERY_LOW:
                        RoutineEngine.onEvent(receiverContext, Routine.TRIGGER_BATTERY_LOW, null);
                        break;
                    default:
                        break;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        try {
            // Android 13+（targetSdk 33+）动态注册必须声明导出标志，否则部分 ROM 直接抛异常
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                appContext.registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            // 注册失败只影响充电/电量触发，绝不能阻止应用启动
            receiver = null;
        }
    }
}
