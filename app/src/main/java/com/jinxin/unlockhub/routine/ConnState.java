package com.jinxin.unlockhub.routine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 当前连接状态快照：由监听器写入，供约束（条件二）判定读取。 */
public final class ConnState {
    private static final String NAME = "conn_state";
    private static final String KEY_WIFI_SSID = "wifi_ssid";       // 空串=未连 Wi-Fi
    private static final String KEY_BT_DEVICES = "bt_devices";     // 已连接蓝牙设备名集合

    private ConnState() {
    }

    public static void setWifiSsid(Context context, String ssid) {
        prefs(context).edit().putString(KEY_WIFI_SSID, ssid == null ? "" : ssid).apply();
    }

    public static String wifiSsid(Context context) {
        return prefs(context).getString(KEY_WIFI_SSID, "");
    }

    public static void addBtDevice(Context context, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Set<String> devices = new HashSet<>(btDevices(context));
        devices.add(name);
        prefs(context).edit().putStringSet(KEY_BT_DEVICES, devices).apply();
    }

    public static void removeBtDevice(Context context, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Set<String> devices = new HashSet<>(btDevices(context));
        devices.remove(name);
        prefs(context).edit().putStringSet(KEY_BT_DEVICES, devices).apply();
    }

    public static Set<String> btDevices(Context context) {
        Set<String> devices = prefs(context).getStringSet(KEY_BT_DEVICES, null);
        return devices == null ? new HashSet<>() : devices;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
