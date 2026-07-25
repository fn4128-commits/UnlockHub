package com.jinxin.unlockhub;

import android.content.Context;

import com.jinxin.unlockhub.data.UnlockEvent;
import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.scheduler.AlertScheduler;
import com.jinxin.unlockhub.sync.UnlockSync;
import com.jinxin.unlockhub.util.Prefs;

public final class UnlockCapture {
    /**
     * 自动化规则派发延迟。解锁时三个功能可能同时弹窗，按 SafePing → 备忘录 → 自动化 错时发出：
     * SafePing 立即弹，备忘录内部延迟 2 秒（见 MemoNotifier），自动化再延后到备忘录之后。
     */
    private static final long ROUTINE_DISPATCH_DELAY_MS = 4000L;

    private UnlockCapture() {
    }

    public static UnlockEvent capture(Context context, long nowMillis) throws Exception {
        return capture(context, nowMillis, "auto");
    }

    public static UnlockEvent capture(Context context, long nowMillis, String source) throws Exception {
        Context appContext = context.getApplicationContext();
        Prefs.setLastAutoCapture(appContext, nowMillis, source);
        UnlockRepository repository = new UnlockRepository(appContext);
        UnlockEvent event = repository.recordFirstUnlockIfNeeded(nowMillis);
        AlertScheduler.scheduleNextCheck(appContext, event.firstUnlockAt);
        boolean firstUnlockOfDay = event.firstUnlockAt == nowMillis;
        // 解锁弹窗顺序：① SafePing 记录确认 → ② 备忘录提醒 → ③ 自动化规则。
        // ① SafePing 记录确认（最先，立即弹）：开启后用整页弹窗代替普通通知，确保用户看到"已记录"
        if (firstUnlockOfDay) {
            if (Prefs.isUnlockPopupEnabled(appContext)) {
                try {
                    PopupNotifier.show(appContext, 3101, appContext.getString(R.string.nt_capture_title),
                            appContext.getString(R.string.nt_capture_text, event.localDate));
                } catch (Exception ignored) {
                }
            } else {
                NotificationHelper.notifyRecorded(appContext, event.localDate);
            }
        }
        // ② 备忘录解锁-弹窗（居中）：每次解锁都判定（时间节点档需要在非首次解锁时也能触发），
        // 内部延迟 2 秒发出，排在 SafePing 记录确认之后。
        try {
            MemoNotifier.showUnlockPopupMemos(appContext);
        } catch (Exception ignored) {
        }
        // ③ 自动化规则（最后）：解锁相关触发；再延迟发出，排在备忘录弹窗之后。
        // 「每次解锁」每次都派发；「当日首次解锁」仅当天第一次解锁派发。放后台线程，避免主线程 DB 操作。
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> new Thread(() -> {
                    try {
                        com.jinxin.unlockhub.routine.RoutineEngine.onEvent(appContext,
                                com.jinxin.unlockhub.data.Routine.TRIGGER_ANY_UNLOCK, null);
                        if (firstUnlockOfDay) {
                            com.jinxin.unlockhub.routine.RoutineEngine.onEvent(appContext,
                                    com.jinxin.unlockhub.data.Routine.TRIGGER_FIRST_UNLOCK, null);
                        }
                    } catch (Throwable ignored) {
                    }
                }, "routine-unlock-dispatch").start(),
                ROUTINE_DISPATCH_DELAY_MS);
        try {
            UnlockSync.syncAfterCapture(appContext, false);
            Prefs.setLastSyncError(appContext, "");
        } catch (Exception syncError) {
            Prefs.setLastSyncError(appContext, syncError.getMessage());
            UnlockSync.scheduleRetryIfNeeded(appContext);
            throw syncError;
        }
        return event;
    }
}
