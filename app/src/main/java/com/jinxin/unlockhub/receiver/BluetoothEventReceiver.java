package com.jinxin.unlockhub.receiver;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.routine.ConnState;
import com.jinxin.unlockhub.routine.RoutineEngine;

/**
 * 蓝牙连接/断开事件（ACL 广播在清单注册即可收到，进程不在也能唤醒）。
 * 读取设备名在 Android 12+ 需要 BLUETOOTH_CONNECT 权限，未授权时按空名处理。
 */
public final class BluetoothEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction() == null ? "" : intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String name = deviceName(context, device);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                ConnState.addBtDevice(context, name);
                RoutineEngine.onEvent(context, Routine.TRIGGER_BT_CONNECTED, name);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                ConnState.removeBtDevice(context, name);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String deviceName(Context context, BluetoothDevice device) {
        if (device == null) {
            return "";
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31
                    && context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return "";
            }
            String name = device.getName();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }
}
