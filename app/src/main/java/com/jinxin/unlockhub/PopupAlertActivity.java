package com.jinxin.unlockhub;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jinxin.unlockhub.data.MemoRepository;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;

/**
 * 解锁-弹窗：全屏可见的提醒弹窗。
 *
 * 交互（针对备忘录弹窗，memoId > 0 时）：
 *  - 点击弹窗内容 → 标记为已读，并跳转到对应那条备忘；
 *  - 蓝色「我知道了」→ 标记为已读（不再重复弹），只关弹窗不跳转；
 *  - 红色「未读且退出应用程序」→ 保持未读（下次解锁还会提醒）并退出应用。
 */
public final class PopupAlertActivity extends BaseActivity {

    private long memoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        // 允许显示在锁屏上方并点亮屏幕
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        // 弹出即撤掉对应通知，避免重复
        int notificationId = getIntent().getIntExtra("notificationId", 0);
        if (notificationId > 0) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.cancel(notificationId);
            }
        }
        memoId = getIntent().getLongExtra("memoId", 0L);
        setContentView(buildContent(
                getIntent().getStringExtra("title"),
                getIntent().getStringExtra("text")
        ));
    }

    private LinearLayout buildContent(String title, String text) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        AppUi.styleScreenBackground(root);
        int pad = AppUi.dp(this, 24);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout card = AppUi.createCard(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int cardPad = AppUi.dp(this, 24);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);

        TextView titleView = new TextView(this);
        titleView.setText(title == null || title.isEmpty() ? getString(R.string.popup_default_title) : title);
        titleView.setTextSize(24);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);

        TextView textView = AppUi.body(this, text == null ? "" : text);
        textView.setTextSize(17);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, AppUi.dp(this, 14), 0, AppUi.dp(this, 8));
        card.addView(textView);

        // 点击内容区域 → 跳到对应备忘并标记已读（按钮各自消费点击，不会误触发）
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openMemoAndMarkRead());

        if (memoId > 0) {
            TextView tapHint = AppUi.body(this, getString(R.string.popup_tap_hint));
            tapHint.setTextSize(12);
            tapHint.setGravity(Gravity.CENTER);
            tapHint.setPadding(0, 0, 0, AppUi.dp(this, 6));
            card.addView(tapHint);
        }

        Button confirmButton = AppUi.primaryButton(this, getString(R.string.popup_got_it));
        confirmButton.setOnClickListener(v -> markReadAndClose());
        card.addView(confirmButton);

        // 红色：保持未读（下次解锁继续提醒）并退出应用。
        // 注意必须 mutate()：primaryButton 用的是共享 drawable，直接 setTint 会连"我知道了"一起染红。
        Button keepUnreadButton = AppUi.primaryButton(this, getString(R.string.popup_keep_unread_exit));
        android.graphics.drawable.Drawable redBg = keepUnreadButton.getBackground();
        if (redBg != null) {
            redBg = redBg.mutate();
            redBg.setTint(0xFFD32F2F);
            keepUnreadButton.setBackground(redBg);
        }
        keepUnreadButton.setOnClickListener(v -> keepUnreadAndExit());
        card.addView(keepUnreadButton);

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    /** 蓝色「我知道了」：标记为已读（不再重复弹），只关掉弹窗、不跳转。 */
    private void markReadAndClose() {
        markRead();
        finish();
    }

    /** 把该条备忘标记为已读（无对应备忘时无操作）。 */
    private void markRead() {
        if (memoId <= 0) {
            return;
        }
        try {
            new MemoRepository(this).setUnread(memoId, false);
            MemoNotifier.updateBadge(this);
        } catch (Throwable ignored) {
        }
    }

    /** 点内容：标记该备忘为已读并跳转过去；无对应备忘时打开备忘录列表。 */
    private void openMemoAndMarkRead() {
        markRead();
        Intent intent = new Intent(this, MemoActivity.class);
        if (memoId > 0) {
            intent.putExtra("memoId", memoId);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (Throwable ignored) {
        }
        finish();
    }

    /** 红色按钮：确保保持未读（下次解锁还会提醒），并退出应用。 */
    private void keepUnreadAndExit() {
        if (memoId > 0) {
            try {
                new MemoRepository(this).setUnread(memoId, true);
                MemoNotifier.updateBadge(this);
            } catch (Throwable ignored) {
            }
        }
        // finishAndRemoveTask() 仅在本 Activity 是任务根时才清空任务；弹窗常会并入已有任务，
        // 因此用 finishAffinity() 关掉本任务中本应用的所有页面，真正退出应用。
        try {
            finishAffinity();
        } catch (Throwable ignored) {
            finish();
        }
    }
}
