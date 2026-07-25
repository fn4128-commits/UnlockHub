package com.jinxin.unlockhub;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.jinxin.unlockhub.data.UnlockRepository;

public final class NotificationHelper {
    private static final String URGENT_CHANNEL_ID = "unlock_hub_urgent_v1";
    private static final int ID_RECORDED = 3001;
    private static final int ID_SYNC_SUCCESS = 3003;
    private static final int ID_TEST = 3002;

    private NotificationHelper() {
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel urgentChannel = new NotificationChannel(
                URGENT_CHANNEL_ID,
                context.getString(R.string.ch_urgent),
                NotificationManager.IMPORTANCE_HIGH
        );
        urgentChannel.setDescription(context.getString(R.string.ch_urgent_desc));
        urgentChannel.enableVibration(true);
        urgentChannel.setVibrationPattern(new long[]{0, 250, 120, 250});
        urgentChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        Uri sound = Settings.System.DEFAULT_NOTIFICATION_URI;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        urgentChannel.setSound(sound, attributes);
        manager.createNotificationChannel(urgentChannel);
    }

    public static void notifyRecorded(Context context, String localDate) {
        if (!canPostNotifications(context)) {
            return;
        }
        int totalDays = new UnlockRepository(context).totalCount();
        notifyHeadsUp(
                context,
                ID_RECORDED,
                context.getString(R.string.nt_recorded_title),
                context.getString(R.string.nt_recorded_text, localDate, totalDays)
        );
    }

    public static void notifySyncSuccess(Context context, String periodStart, String periodEnd) {
        if (!canPostNotifications(context)) {
            return;
        }
        String detail = context.getString(R.string.nt_sync_ok_text, com.jinxin.unlockhub.util.TimeFormat.formatUploadRange(periodStart, periodEnd));
        notifyHeadsUp(context, ID_SYNC_SUCCESS, context.getString(R.string.nt_sync_ok_title), detail);
    }

    public static void notifyTest(Context context) {
        if (!canPostNotifications(context)) {
            return;
        }
        notifyHeadsUp(context, ID_TEST, context.getString(R.string.nt_test_title), context.getString(R.string.nt_test_text));
    }

    public static Intent notificationSettingsIntent(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, URGENT_CHANNEL_ID);
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        }
        return intent;
    }

    private static void notifyHeadsUp(Context context, int notificationId, String title, String text) {
        ensureChannels(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        PendingIntent openIntent = openAppIntent(context);
        Notification.Builder builder = builder(context, URGENT_CHANNEL_ID);
        builder
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openIntent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setVibrate(new long[]{0, 250, 120, 250});
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        // 常规行为：横幅弹出后收入通知栏并一直留着，直到用户点击（setAutoCancel）或手动划掉。
        manager.notify(notificationId, builder.build());
    }

    private static boolean canPostNotifications(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static Notification.Builder builder(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(context, channelId);
        }
        return new Notification.Builder(context);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            intent = new Intent();
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 3003, intent, flags);
    }
}
