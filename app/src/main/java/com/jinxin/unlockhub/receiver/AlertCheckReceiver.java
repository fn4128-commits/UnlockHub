package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.scheduler.AlertScheduler;
import com.jinxin.unlockhub.util.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlertCheckReceiver extends BroadcastReceiver {
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
                UnlockRepository repository = new UnlockRepository(appContext);
                long lastActivityAt = repository.lastActivityAt();
                long now = System.currentTimeMillis();
                boolean inactive = lastActivityAt > 0L && now - lastActivityAt >= AlertScheduler.INACTIVITY_WINDOW_MILLIS;
                boolean alreadyAlertedForWindow = Prefs.lastAlertAt(appContext) >= lastActivityAt;
                if (inactive && !alreadyAlertedForWindow && new ApiClient(appContext).hasBackend()) {
                    new ApiClient(appContext).sendInactivityAlert(lastActivityAt);
                    Prefs.setLastAlertAt(appContext, now);
                }
                AlertScheduler.scheduleNextCheck(appContext, lastActivityAt);
            } catch (Exception ignored) {
                // The next boot, app open, or unlock will schedule/check again.
            } finally {
                pendingResult.finish();
            }
        });
    }
}
