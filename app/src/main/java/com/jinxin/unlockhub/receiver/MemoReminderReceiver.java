package com.jinxin.unlockhub.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinxin.unlockhub.MemoNotifier;
import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;

public final class MemoReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long memoId = intent.getLongExtra("memoId", 0L);
        if (memoId <= 0) {
            return;
        }
        MemoRepository repository = new MemoRepository(context);
        Memo memo = repository.findById(memoId);
        if (memo == null || memo.remindAt <= 0) {
            return;
        }
        MemoNotifier.notifyReminder(context, memo);
        // 提醒后标记未读，配合角标提示用户查看。
        // 注意：不清空 remind_at——「解锁-弹窗加强提醒」需要用它判定
        // “提醒时间节点之后的首次解锁”（过期的提醒时间不会再排闹钟，无副作用）。
        repository.setUnread(memoId, true);
        MemoNotifier.updateBadge(context);
    }
}
