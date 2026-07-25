package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.sync.UnlockSync;
import com.jinxin.unlockhub.util.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SyncRetryReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Prefs.isPaused(context)) {
            return;
        }
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                UnlockSync.trySyncDue(appContext);
            } catch (Exception ignored) {
                UnlockSync.scheduleRetryIfNeeded(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
