package com.jinxin.unlockhub.routine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jinxin.unlockhub.R;
import com.jinxin.unlockhub.data.Routine;

import org.json.JSONObject;

/** 规则动作执行器。所有动作失败都静默，不能让引擎崩溃。 */
public final class RoutineExecutor {
    private static final String CHANNEL_ID = "routine_v1";
    private static final int ID_BASE = 6000;

    private RoutineExecutor() {
    }

    public static void execute(Context context, Routine routine) {
        try {
            JSONObject action = routine.actionJson();
            switch (routine.actionType) {
                case Routine.ACTION_POPUP:
                    showPopup(context, routine, action.optString("text", context.getString(com.jinxin.unlockhub.R.string.re_triggered)));
                    break;
                case Routine.ACTION_NOTIFY:
                    notifyText(context, routine, action.optString("text", context.getString(com.jinxin.unlockhub.R.string.re_triggered)));
                    break;
                case Routine.ACTION_OPEN_APP:
                    openApp(context, action.optString("package", ""), action.optString("label", context.getString(com.jinxin.unlockhub.R.string.re_app)), routine);
                    break;
                case Routine.ACTION_DND_ON:
                    setDnd(context, true, routine);
                    break;
                case Routine.ACTION_DND_OFF:
                    setDnd(context, false, routine);
                    break;
                case Routine.ACTION_COUNTDOWN:
                    startCountdown(context, routine,
                            action.optInt("minutes", 25), action.optString("text", ""));
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
        }
    }

    private static void openApp(Context context, String packageName, String label, Routine routine) {
        if (packageName.isEmpty()) {
            return;
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            notifyPlain(context, notifId(routine), context.getString(com.jinxin.unlockhub.R.string.re_open_fail_title), context.getString(com.jinxin.unlockhub.R.string.re_open_fail_text, label));
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // Android 10+ 后台启动 Activity 受限，且被拦截时不一定抛异常。
            context.startActivity(launch);
        } catch (Exception ignored) {
        }
        // 兜底：无论是否成功都发一条可点击通知（同一 ID 会覆盖，不会重复）。
        notifyOpenApp(context, routine, label, launch);
    }

    private static void setDnd(Context context, boolean on, Routine routine) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        if (!manager.isNotificationPolicyAccessGranted()) {
            notifyRoutineStatus(context, routine, context.getString(com.jinxin.unlockhub.R.string.re_dnd_fail_title),
                    context.getString(com.jinxin.unlockhub.R.string.re_dnd_fail_text));
            return;
        }
        manager.setInterruptionFilter(on
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL);
    }

    /**
     * 解锁-弹窗：全屏意图通知。
     * 亮屏使用中 → 顶部横幅（点击进弹窗页）；锁屏/刚解锁 → 直接整页弹出。
     * 相比普通通知，被看到的概率大幅提升。
     */
    private static void showPopup(Context context, Routine routine, String text) {
        String title = routine.name.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.re_reminder) : routine.name;
        com.jinxin.unlockhub.PopupNotifier.show(context, notifId(routine), title, text);
    }

    /** 启用倒计时：X 分钟后整页弹窗提醒；开始时发一条静默确认通知。 */
    private static void startCountdown(Context context, Routine routine, int minutes, String text) {
        int safeMinutes = Math.max(1, Math.min(minutes, 24 * 60));
        android.app.AlarmManager alarmManager =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        String title = context.getString(com.jinxin.unlockhub.R.string.re_countdown_title, routine.name.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.re_countdown) : routine.name, safeMinutes);
        Intent intent = new Intent(context, com.jinxin.unlockhub.receiver.CountdownReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("text", text);
        intent.putExtra("notificationId", 6600 + notifId(routine) % 200);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, (int) (6600 + routine.id % 200), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long triggerAt = System.currentTimeMillis() + safeMinutes * 60_000L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException e) {
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        notifyRoutineStatus(context, routine, context.getString(com.jinxin.unlockhub.R.string.re_countdown_started),
                context.getString(com.jinxin.unlockhub.R.string.re_countdown_text, safeMinutes, text.isEmpty() ? "" : ": " + text));
    }

    // ---------- 通知 ----------

    private static void notifyText(Context context, Routine routine, String text) {
        String title = routine.name.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.re_auto_reminder) : routine.name;
        notifyRoutineStatus(context, routine, title, text);
    }

    private static void notifyOpenApp(Context context, Routine routine, String label, Intent launch) {
        NotificationManager manager = prepare(context);
        if (manager == null) {
            return;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notifId(routine), launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(routine.name.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.re_automation) : routine.name)
                .setContentText(context.getString(com.jinxin.unlockhub.R.string.re_tap_open, label))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        manager.notify(notifId(routine), builder.build());
    }

    /** 打开「编辑这条规则」页面的点击意图。 */
    private static PendingIntent editRoutineIntent(Context context, Routine routine) {
        Intent intent = new Intent(context, com.jinxin.unlockhub.RoutineEditActivity.class);
        intent.putExtra("routineId", routine.id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context, (int) (700000 + routine.id % 1000), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 规则状态通知：点按跳转到编辑该规则。 */
    private static void notifyRoutineStatus(Context context, Routine routine, String title, String text) {
        NotificationManager manager = prepare(context);
        if (manager == null) {
            return;
        }
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setContentIntent(editRoutineIntent(context, routine));
        manager.notify(notifId(routine), builder.build());
    }

    private static void notifyPlain(Context context, int id, String title, String text) {
        NotificationManager manager = prepare(context);
        if (manager == null) {
            return;
        }
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setShowWhen(true);
        manager.notify(id, builder.build());
    }

    private static NotificationManager prepare(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return null;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, context.getString(com.jinxin.unlockhub.R.string.ch_routine), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(com.jinxin.unlockhub.R.string.ch_routine_desc));
        manager.createNotificationChannel(channel);
        return manager;
    }

    private static int notifId(Routine routine) {
        return (int) (ID_BASE + routine.id % 500);
    }
}
