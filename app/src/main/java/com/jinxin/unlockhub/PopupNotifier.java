package com.jinxin.unlockhub;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * 共享的「解锁-弹窗」发射器：全屏意图通知。
 * 亮屏使用中 → 顶部横幅；锁屏/刚解锁 → 整页弹窗（PopupAlertActivity）。
 * 被 SafePing 记录确认、备忘录加强提醒、自动化规则共用。
 */
public final class PopupNotifier {
    private static final String CHANNEL_ID = "popup_v1";

    private PopupNotifier() {
    }

    public static void show(Context context, int notificationId, String title, String text) {
        show(context, notificationId, title, text, 0L);
    }

    /**
     * @param memoId 该弹窗对应的备忘 id（0 = 不对应具体备忘）。传入后，弹窗内点击内容可跳转到
     *               该备忘并标记已读；红色「未读且退出应用程序」则保持未读。
     */
    public static void show(Context context, int notificationId, String title, String text, long memoId) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.ch_popup), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.ch_popup_desc));
        manager.createNotificationChannel(channel);

        Intent popupIntent = new Intent(context, PopupAlertActivity.class);
        popupIntent.putExtra("title", title);
        popupIntent.putExtra("text", text);
        popupIntent.putExtra("notificationId", notificationId);
        popupIntent.putExtra("memoId", memoId);
        popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(
                context, notificationId, popupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                // 用 REMINDER 而非 ALARM：ALARM 属"必须响应"类，系统会让横幅常驻不自动收起，
                // 导致同为备忘提醒却有的赖着不走、有的几秒就退。统一成几秒后自动收入状态栏。
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true);
        manager.notify(notificationId, builder.build());
    }
}
