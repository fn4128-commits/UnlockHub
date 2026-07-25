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

import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;

/** 备忘录通知：定时提醒 + 未读角标。 */
public final class MemoNotifier {
    private static final String REMINDER_CHANNEL_ID = "memo_reminder_v1";
    private static final String BADGE_CHANNEL_ID = "memo_badge_v1";
    private static final String QUICK_ADD_CHANNEL_ID = "memo_quick_add_v1";
    private static final int ID_BADGE = 4001;
    private static final int ID_QUICK_ADD = 4301;
    private static final int ID_REMINDER_BASE = 41000;
    private static final int ID_UNLOCK_POPUP_BASE = 42000; // 解锁弹窗：每条备忘一个独立通知 id

    private MemoNotifier() {
    }

    public static void ensureChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel reminderChannel = new NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.ch_memo_reminder),
                NotificationManager.IMPORTANCE_HIGH
        );
        reminderChannel.setDescription(context.getString(R.string.ch_memo_reminder_desc));
        reminderChannel.enableVibration(true);
        reminderChannel.setShowBadge(true);
        manager.createNotificationChannel(reminderChannel);

        NotificationChannel badgeChannel = new NotificationChannel(
                BADGE_CHANNEL_ID,
                context.getString(R.string.ch_memo_badge),
                NotificationManager.IMPORTANCE_LOW
        );
        badgeChannel.setDescription(context.getString(R.string.ch_memo_badge_desc));
        badgeChannel.setShowBadge(true);
        badgeChannel.setSound(null, null);
        badgeChannel.enableVibration(false);
        manager.createNotificationChannel(badgeChannel);

        NotificationChannel quickAddChannel = new NotificationChannel(
                QUICK_ADD_CHANNEL_ID,
                context.getString(R.string.ch_quick_add),
                NotificationManager.IMPORTANCE_LOW
        );
        quickAddChannel.setDescription(context.getString(R.string.ch_quick_add_desc));
        quickAddChannel.setShowBadge(false);
        quickAddChannel.setSound(null, null);
        quickAddChannel.enableVibration(false);
        manager.createNotificationChannel(quickAddChannel);
    }

    /** 通知栏常驻"快速添加备忘"入口：点一下直接打开新建备忘界面。 */
    public static void showQuickAdd(Context context) {
        if (!canPostNotifications(context)) {
            return;
        }
        ensureChannels(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(context, MemoEditActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("quickAdd", true);
        PendingIntent pending = PendingIntent.getActivity(context, 4300, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, QUICK_ADD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(context.getString(R.string.nt_quick_add_title))
                .setContentText(context.getString(R.string.nt_quick_add_text))
                .setOngoing(true)          // 常驻，不可滑动清除
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending);
        manager.notify(ID_QUICK_ADD, builder.build());
    }

    public static void cancelQuickAdd(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(ID_QUICK_ADD);
        }
    }

    /** 按用户设置的开关决定显示/取消"快速添加备忘"常驻入口。 */
    public static void applyQuickAdd(Context context) {
        if (com.jinxin.unlockhub.util.Prefs.quickAddMemoEnabled(context)) {
            showQuickAdd(context);
        } else {
            cancelQuickAdd(context);
        }
    }

    /**
     * 解锁-弹窗加强提醒。每次解锁都会调用，按精确度分档判定（三档都只弹一次，直到该备忘被读）：
     * ① 设了提醒时间（精确到分）→ 到达该时间节点后的第一次解锁弹出；
     * ② 只设了日期 → 到达该日期后的第一次解锁弹出；
     * ③ 都没设 → 下一次解锁弹出。
     * 弹窗延迟 2 秒发出，保证排在「状态已记录」通知之后。
     */
    public static void showUnlockPopupMemos(Context context) {
        final Context appContext = context.getApplicationContext();
        MemoRepository repository = new MemoRepository(appContext);
        java.util.List<Memo> pending = repository.unlockPopupPending();
        if (pending.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String today = com.jinxin.unlockhub.util.TimeFormat.localDate(now);
        java.util.List<Memo> toShow = new java.util.ArrayList<>();
        for (Memo memo : pending) {
            if (memo.remindAt > 0) {
                // ① 时间节点档：到点之后，每次解锁都弹，直到已读。
                if (now >= memo.remindAt) {
                    toShow.add(memo);
                }
            } else if (!memo.memoDate.isEmpty()) {
                // ② 日期档：到关联日期起，每次解锁都弹，直到已读。
                if (today.compareTo(memo.memoDate) >= 0) {
                    toShow.add(memo);
                }
            } else {
                // ③ 无时间档：每次解锁都弹，直到已读。
                toShow.add(memo);
            }
        }
        if (toShow.isEmpty()) {
            return;
        }
        // 每条备忘各弹各的（不再合并成"你有 N 条"总览）：各自带上自己的 memoId，
        // 这样点开弹窗内容就能跳到对应那条备忘并标记已读。
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int i = 0; i < toShow.size(); i++) {
            final Memo memo = toShow.get(i);
            repository.setLastPopupAt(memo.id, now);
            final String title;
            final String text;
            if (memo.isPrivate) {
                title = appContext.getString(R.string.nt_private_memo_title);
                text = appContext.getString(R.string.nt_private_memo_text);
            } else {
                title = memo.title.isEmpty() ? appContext.getString(R.string.nt_memo_reminder) : memo.title;
                String body = memo.preview();
                text = (body == null || body.isEmpty()) ? appContext.getString(R.string.nt_no_content) : body;
            }
            final int notificationId = ID_UNLOCK_POPUP_BASE + (int) (memo.id % 1000);
            // 首条延迟 2 秒（让「状态已记录」通知先显示），其余依次错开，避免横幅互相顶掉。
            handler.postDelayed(
                    () -> PopupNotifier.show(appContext, notificationId, title, text, memo.id),
                    2000L + i * 700L);
        }
    }

    /** 到点提醒。 */
    public static void notifyReminder(Context context, Memo memo) {
        // 加强提醒的备忘：到点直接整页弹窗，而不是普通通知
        if (memo.unlockPopup) {
            String title = (memo.isPrivate || memo.title.isEmpty()) ? context.getString(R.string.ch_memo_reminder) : memo.title;
            String text = memo.isPrivate ? context.getString(R.string.nt_private_due) : memo.preview().replace('\n', ' ');
            PopupNotifier.show(context, ID_REMINDER_BASE + (int) (memo.id % 1000), title, text, memo.id);
            return;
        }
        if (!canPostNotifications(context)) {
            return;
        }
        ensureChannels(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        String title = (memo.isPrivate || memo.title.isEmpty()) ? context.getString(R.string.ch_memo_reminder) : memo.title;
        String text = memo.isPrivate ? context.getString(R.string.nt_private_due) : memo.preview().replace('\n', ' ');
        Notification.Builder builder = new Notification.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_safeping)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openMemoIntent(context, memo.id));
        manager.notify(ID_REMINDER_BASE + (int) (memo.id % 1000), builder.build());
    }

    /**
     * 应用图标数字角标：角标数 = 需要关注的备忘条数（未读或未完成，含清单）。
     * 只走 OEM 私有接口设置图标角标——不再发「备忘录 · N 条待处理」那条总览通知
     * （用户要求移除；每条备忘现在各自弹各自的解锁弹窗）。
     */
    public static void updateBadge(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        MemoRepository.BadgeStats stats = new MemoRepository(context).badgeStats();
        com.jinxin.unlockhub.util.OemBadger.apply(context, stats.attention);
        if (manager != null) {
            manager.cancel(ID_BADGE); // 清掉历史遗留的总览通知
        }
    }

    private static PendingIntent openMemoIntent(Context context, long memoId) {
        Intent intent = new Intent(context, MemoActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (memoId > 0) {
            intent.putExtra("memoId", memoId);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, (int) (4100 + memoId % 1000), intent, flags);
    }

    private static boolean canPostNotifications(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
}
