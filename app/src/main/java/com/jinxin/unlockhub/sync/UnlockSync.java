package com.jinxin.unlockhub.sync;

import android.content.Context;

import com.jinxin.unlockhub.NotificationHelper;
import com.jinxin.unlockhub.data.UnlockEvent;
import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.scheduler.SyncRetryScheduler;
import com.jinxin.unlockhub.util.NetworkUtil;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.TimeFormat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UnlockSync {
    private static final ExecutorService SYNC_EXECUTOR = Executors.newSingleThreadExecutor();

    private UnlockSync() {
    }

    public static void syncAfterCapture(Context context) throws IOException {
        syncAfterCapture(context, false);
    }

    public static void syncAfterCapture(Context context, boolean requireFirstUnlockToday) throws IOException {
        if (!Prefs.isAccountBound(context)) {
            return;
        }
        if (requireFirstUnlockToday) {
            String today = TimeFormat.localDate(System.currentTimeMillis());
            UnlockEvent todayEvent = new UnlockRepository(context).findByDate(today);
            if (todayEvent == null) {
                return;
            }
        }
        if (!NetworkUtil.isOnline(context)) {
            scheduleRetryIfNeeded(context);
            throw new IOException(context.getString(com.jinxin.unlockhub.R.string.sync_no_network));
        }

        ApiClient.SyncResult result = syncPending(context);
        if (result.syncReportCreated && result.dueDate != null) {
            String periodStart = result.periodStart != null
                    ? result.periodStart
                    : SyncSchedule.periodStartForDueDate(context, result.dueDate);
            String periodEnd = result.periodEnd != null
                    ? result.periodEnd
                    : SyncSchedule.periodEndForDueDate(context, result.dueDate);
            Prefs.appendSyncUploadRecord(context, periodStart, periodEnd);
            Prefs.setLastSyncedPeriodDueDate(context, result.dueDate);
            NotificationHelper.notifySyncSuccess(context, periodStart, periodEnd);
        }
        Prefs.setLastSyncError(context, "");
    }

    public static void syncPendingSilently(Context context) {
        if (!Prefs.isAccountBound(context)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        SYNC_EXECUTOR.execute(() -> {
            try {
                syncAfterCapture(appContext, false);
            } catch (IOException error) {
                Prefs.setLastSyncError(appContext, error.getMessage());
                scheduleRetryIfNeeded(appContext);
            }
        });
    }

    public static void trySyncDueOnFirstUnlock(Context context) throws IOException {
        syncAfterCapture(context, true);
    }

    public static void trySyncDue(Context context) throws IOException {
        syncAfterCapture(context, false);
    }

    public static ApiClient.SyncResult syncPending(Context context) throws IOException {
        ApiClient api = new ApiClient(context);
        if (!api.hasBackend()) {
            return ApiClient.SyncResult.empty();
        }

        UnlockRepository repository = new UnlockRepository(context);
        List<UnlockEvent> pending = repository.unsynced();
        ApiClient.SyncResult latestResult = ApiClient.SyncResult.empty();
        for (UnlockEvent event : pending) {
            latestResult = api.sendUnlockEvent(event);
            repository.markSynced(event.id);
        }
        return latestResult;
    }

    public static void scheduleRetryIfNeeded(Context context) {
        if (!Prefs.isAccountBound(context)) {
            return;
        }
        if (!new UnlockRepository(context).unsynced().isEmpty()) {
            SyncRetryScheduler.scheduleRetry(context);
        }
    }
}
