package com.jinxin.unlockhub.routine;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.jinxin.unlockhub.data.Routine;

/**
 * Wi-Fi 连接事件监听（进程存活期间有效）。
 * 连接成功 → 更新状态快照并触发 wifi_connected 规则。
 * 读取 SSID 需要定位权限且定位开启，读不到时以空串处理（只匹配"任意 Wi-Fi"规则）。
 */
public final class ConnectionEventMonitor {
    private static ConnectivityManager.NetworkCallback callback;

    private ConnectionEventMonitor() {
    }

    public static synchronized void ensureRegistered(Context context) {
        if (callback != null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        ConnectivityManager manager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return;
        }
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                String ssid = currentSsid(appContext);
                ConnState.setWifiSsid(appContext, ssid);
                RoutineEngine.onEvent(appContext, Routine.TRIGGER_WIFI_CONNECTED, ssid);
            }

            @Override
            public void onLost(Network network) {
                ConnState.setWifiSsid(appContext, "");
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            manager.registerNetworkCallback(request, callback);
        } catch (Exception e) {
            callback = null;
        }
    }

    /** 当前 Wi-Fi SSID；读不到（无权限/未开定位/未连接）返回空串。 */
    @SuppressWarnings("deprecation")
    public static String currentSsid(Context context) {
        try {
            WifiManager wifiManager =
                    (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                return "";
            }
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null || info.getSSID() == null) {
                return "";
            }
            String ssid = info.getSSID();
            if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() >= 2) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            return "<unknown ssid>".equals(ssid) ? "" : ssid;
        } catch (Exception e) {
            return "";
        }
    }
}
