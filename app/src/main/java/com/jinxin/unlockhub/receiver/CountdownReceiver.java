package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.PopupNotifier;

/** 自动化「启用倒计时」动作：倒计时结束时整页弹窗提醒。 */
public final class CountdownReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");
            int notificationId = intent.getIntExtra("notificationId", 6600);
            PopupNotifier.show(context, notificationId,
                    title == null || title.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.cd_finished_title) : title,
                    text == null || text.isEmpty() ? context.getString(com.jinxin.unlockhub.R.string.cd_finished_text) : text);
        } catch (Throwable ignored) {
        }
    }
}
