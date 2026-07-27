package com.jinxin.unlockhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;
import com.jinxin.unlockhub.util.MemoLock;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MemoActivity extends BaseActivity {
    private MemoRepository repository;
    private LinearLayout listContainer;
    private LinearLayout calendarContainer;
    private TextView statusLine;
    private TextView listChip;
    private TextView calendarChip;
    private TextView privateChip;

    private boolean calendarMode;
    private boolean privateUnlocked;
    private boolean readExpanded; // 「已读」折叠区是否展开
    private String selectedDate = "";
    private Calendar visibleMonth = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        repository = new MemoRepository(this);
        setContentView(buildContent());
        requestNotificationPermissionIfNeeded();
        long memoId = getIntent().getLongExtra("memoId", 0L);
        if (memoId > 0) {
            openEditor(memoId, "");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 已读满 30 天的备忘自动删除（清理后若有删除则同步云端）。
        try {
            if (repository.pruneExpiredRead() > 0) {
                com.jinxin.unlockhub.sync.MemoSync.syncAsync(this);
            }
        } catch (Throwable ignored) {
        }
        refresh();
        // 兜底：应用被杀/重装后闹钟会丢失，回到备忘录页时重建所有未来提醒。
        try {
            com.jinxin.unlockhub.scheduler.MemoReminderScheduler.rescheduleAll(this);
            MemoNotifier.updateBadge(this);
            MemoNotifier.applyQuickAdd(this); // 按开关显示/取消 通知栏常驻快速添加入口
            // 增删解锁弹窗备忘录会改变"是否需要实时前台服务"，回到列表页时重评一次，
            // 使新建的解锁弹窗备忘录能立即进入实时状态（不必等下次进主界面）。
            com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
        } catch (Throwable ignored) {
        }
    }

    /** 提醒和角标都依赖通知权限，进入备忘录时主动请求一次。 */
    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2002);
        }
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        AppUi.styleScroll(scrollView);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AppUi.dp(this, 18), AppUi.dp(this, 18), AppUi.dp(this, 18), AppUi.dp(this, 28));
        scrollView.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(AppUi.createBrandHeader(this, getString(R.string.module_memo_title)));

        // 视图切换 + 私密区
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, 0, 0, AppUi.dp(this, 10));
        listChip = AppUi.weekdayChip(this, getString(R.string.memo_tab_list));
        calendarChip = AppUi.weekdayChip(this, getString(R.string.memo_tab_calendar));
        privateChip = AppUi.weekdayChip(this, getString(R.string.memo_tab_private));
        listChip.setOnClickListener(v -> {
            calendarMode = false;
            selectedDate = "";
            refresh();
        });
        calendarChip.setOnClickListener(v -> {
            calendarMode = true;
            refresh();
        });
        privateChip.setOnClickListener(v -> togglePrivateArea());
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chipParams.setMargins(0, 0, AppUi.dp(this, 8), 0);
        chipRow.addView(listChip, chipParams);
        chipRow.addView(calendarChip, chipParams);
        chipRow.addView(privateChip, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(chipRow);

        // 通知栏「快捷新建备忘」开关
        LinearLayout quickAddRow = new LinearLayout(this);
        quickAddRow.setOrientation(LinearLayout.HORIZONTAL);
        quickAddRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        quickAddRow.setPadding(0, 0, 0, AppUi.dp(this, 8));
        TextView quickAddLabel = AppUi.body(this, getString(R.string.memo_quickadd_label));
        quickAddLabel.setTextSize(13);
        quickAddRow.addView(quickAddLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch quickAddSwitch = new android.widget.Switch(this);
        AppUi.styleToggle(quickAddSwitch);
        quickAddSwitch.setChecked(com.jinxin.unlockhub.util.Prefs.quickAddMemoEnabled(this));
        quickAddSwitch.setOnCheckedChangeListener((b, checked) -> {
            com.jinxin.unlockhub.util.Prefs.setQuickAddMemoEnabled(this, checked);
            MemoNotifier.applyQuickAdd(this);
        });
        quickAddRow.addView(quickAddSwitch);
        content.addView(quickAddRow);

        statusLine = AppUi.body(this, "");
        statusLine.setPadding(0, 0, 0, AppUi.dp(this, 8));
        content.addView(statusLine);

        calendarContainer = new LinearLayout(this);
        calendarContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(calendarContainer);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        android.widget.Button addButton = AppUi.primaryButton(this, getString(R.string.memo_add));
        addButton.setOnClickListener(v -> openEditor(0, selectedDate));
        content.addView(addButton);

        return scrollView;
    }

    private void refresh() {
        AppUi.styleWeekdayChip(listChip, !calendarMode);
        AppUi.styleWeekdayChip(calendarChip, calendarMode);
        AppUi.styleWeekdayChip(privateChip, privateUnlocked);

        calendarContainer.removeAllViews();
        if (calendarMode) {
            calendarContainer.addView(buildCalendarCard());
        }

        List<Memo> memos;
        if (calendarMode && !selectedDate.isEmpty()) {
            memos = repository.listByDate(selectedDate, privateUnlocked);
            statusLine.setText(getString(R.string.memo_date_count, selectedDate, memos.size()));
        } else if (calendarMode) {
            memos = repository.listAll(privateUnlocked);
            statusLine.setText(getString(R.string.memo_calendar_hint));
        } else {
            memos = repository.listAll(privateUnlocked);
            int unread = repository.unreadCount();
            int hidden = privateUnlocked ? 0 : repository.privateCount();
            StringBuilder builder = new StringBuilder(getString(R.string.memo_count_total, memos.size()));
            if (unread > 0) {
                builder.append(getString(R.string.memo_count_unread, unread));
            }
            if (hidden > 0) {
                builder.append(getString(R.string.memo_count_private_hidden, hidden));
            }
            statusLine.setText(builder.toString());
        }

        listContainer.removeAllViews();
        if (memos.isEmpty()) {
            TextView empty = AppUi.body(this, getString(calendarMode && !selectedDate.isEmpty()
                    ? R.string.memo_empty_day
                    : R.string.memo_empty_all));
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(0, AppUi.dp(this, 24), 0, AppUi.dp(this, 24));
            listContainer.addView(empty);
            return;
        }
        // 活跃条目直接列出；已读的（read_at>0）收进可折叠的「已读」区，
        // 避免读过的备忘越堆越多挤占列表，同时想查时一点就能展开。
        List<Memo> active = new java.util.ArrayList<>();
        List<Memo> read = new java.util.ArrayList<>();
        for (Memo memo : memos) {
            if (memo.readAt > 0) {
                read.add(memo);
            } else {
                active.add(memo);
            }
        }
        for (Memo memo : active) {
            listContainer.addView(buildMemoCard(memo));
        }
        if (!read.isEmpty()) {
            listContainer.addView(buildReadSection(read));
        }
    }

    /** 已读区：一行可点的标题，展开后显示已读备忘（含自动删除倒计时）。 */
    private View buildReadSection(List<Memo> read) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        TextView header = AppUi.body(this,
                (readExpanded ? "▾ " : "▸ ") + getString(R.string.memo_read_section, read.size()));
        header.setPadding(AppUi.dp(this, 4), AppUi.dp(this, 12), 0, AppUi.dp(this, 8));
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> {
            readExpanded = !readExpanded;
            refresh();
        });
        wrap.addView(header);

        if (readExpanded) {
            for (Memo memo : read) {
                wrap.addView(buildMemoCard(memo));
            }
        }
        return wrap;
    }

    /** 已读备忘距自动删除还剩几天（向上取整，至少 0）。 */
    private int daysUntilAutoDelete(Memo memo) {
        long elapsed = System.currentTimeMillis() - memo.readAt;
        long remain = MemoRepository.READ_RETENTION_MS - elapsed;
        if (remain <= 0) {
            return 0;
        }
        return (int) Math.ceil(remain / (24.0 * 60 * 60 * 1000));
    }

    // ---------- 列表卡片 ----------

    private View buildMemoCard(Memo memo) {
        LinearLayout card = AppUi.createCard(this);
        card.setClickable(true);
        card.setFocusable(true);
        // 真正读过的（read_at>0）才置灰淡出（配合排序下沉），30 天后自动删除；
        // 新建的普通备忘（未读过）不受影响。
        if (memo.readAt > 0) {
            card.setAlpha(0.5f);
        }

        String badges = buildBadges(memo);
        if (!badges.isEmpty()) {
            TextView badgeView = AppUi.body(this, badges);
            badgeView.setTextSize(12);
            card.addView(badgeView);
        }

        String titleText = memo.title.isEmpty() ? getString(R.string.memo_untitled) : memo.title;
        TextView titleView = new TextView(this);
        titleView.setText(titleText);
        titleView.setTextSize(17);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(AppUi.themeColor(this,
                memo.done ? R.attr.appTextSecondary : R.attr.appTextPrimary));
        if (memo.done) {
            titleView.setPaintFlags(titleView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        card.addView(titleView);

        String preview = memo.preview();
        if (!preview.isEmpty()) {
            TextView previewView = AppUi.body(this, preview);
            previewView.setPadding(0, AppUi.dp(this, 4), 0, 0);
            card.addView(previewView);
        }

        // 已读备忘显示自动删除倒计时；剩 3 天内标红提示。想留住就取消已读或置顶。
        if (memo.readAt > 0) {
            int days = daysUntilAutoDelete(memo);
            String text;
            if (days <= 0) {
                text = getString(R.string.memo_expire_today);
            } else if (days <= 3) {
                text = getString(R.string.memo_expire_soon, days);
            } else {
                text = getString(R.string.memo_expire_days, days);
            }
            TextView expire = AppUi.body(this, text);
            expire.setTextSize(12);
            expire.setPadding(0, AppUi.dp(this, 6), 0, 0);
            if (days <= 3) {
                expire.setTextColor(0xFFD32F2F);
            }
            card.addView(expire);
        }

        card.setOnClickListener(v -> {
            if (memo.unread) {
                repository.setUnread(memo.id, false);
                MemoNotifier.updateBadge(this);
            }
            openEditor(memo.id, "");
        });
        card.setOnLongClickListener(v -> {
            showMemoActions(memo);
            return true;
        });
        return card;
    }

    private String buildBadges(Memo memo) {
        StringBuilder builder = new StringBuilder();
        if (memo.pinned) {
            builder.append(getString(R.string.memo_badge_pinned));
        }
        if (memo.unread) {
            builder.append(getString(R.string.memo_badge_unread));
        }
        if (memo.isPrivate) {
            builder.append(getString(R.string.memo_badge_private));
        }
        if (memo.unlockPopup) {
            builder.append(getString(R.string.memo_badge_unlockpopup));
        }
        if (memo.remindAt > System.currentTimeMillis()) {
            builder.append("⏰ ").append(formatDateTime(memo.remindAt)).append("  ");
        }
        if (!memo.memoDate.isEmpty()) {
            builder.append("📅 ").append(memo.memoDate);
        }
        return builder.toString().trim();
    }

    private void showMemoActions(Memo memo) {
        String[] actions = new String[]{
                getString(memo.pinned ? R.string.memo_action_unpin : R.string.memo_action_pin),
                getString(memo.done ? R.string.memo_action_mark_undone : R.string.memo_action_mark_done),
                getString(memo.unread ? R.string.memo_action_mark_read : R.string.memo_action_mark_unread),
                getString(R.string.common_delete)
        };
        new AlertDialog.Builder(this)
                .setTitle(memo.title.isEmpty() ? getString(R.string.memo_actions_title) : memo.title)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            repository.setPinned(memo.id, !memo.pinned);
                            break;
                        case 1:
                            repository.setDone(memo.id, !memo.done);
                            break;
                        case 2:
                            repository.setUnread(memo.id, !memo.unread);
                            break;
                        case 3:
                            confirmDelete(memo);
                            return;
                    }
                    MemoNotifier.updateBadge(this);
                    com.jinxin.unlockhub.sync.MemoSync.syncAsync(this);
                    refresh();
                })
                .show();
    }

    private void confirmDelete(Memo memo) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.memo_delete_title))
                .setMessage(getString(R.string.memo_delete_message))
                .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> {
                    com.jinxin.unlockhub.scheduler.MemoReminderScheduler.cancel(this, memo.id);
                    repository.delete(memo.id);
                    MemoNotifier.updateBadge(this);
                    com.jinxin.unlockhub.sync.MemoSync.syncAsync(this);
                    refresh();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    // ---------- 日历 ----------

    private View buildCalendarCard() {
        LinearLayout card = AppUi.createCard(this);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = navButton("‹");
        prev.setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, -1);
            selectedDate = "";
            refresh();
        });
        TextView next = navButton("›");
        next.setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, 1);
            selectedDate = "";
            refresh();
        });
        TextView monthLabel = new TextView(this);
        monthLabel.setText(getString(R.string.memo_month_label,
                visibleMonth.get(Calendar.YEAR), visibleMonth.get(Calendar.MONTH) + 1));
        monthLabel.setTextSize(17);
        monthLabel.setTypeface(monthLabel.getTypeface(), Typeface.BOLD);
        monthLabel.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        monthLabel.setGravity(Gravity.CENTER);
        navRow.addView(prev, new LinearLayout.LayoutParams(AppUi.dp(this, 44), AppUi.dp(this, 44)));
        navRow.addView(monthLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        navRow.addView(next, new LinearLayout.LayoutParams(AppUi.dp(this, 44), AppUi.dp(this, 44)));
        card.addView(navRow);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        String[] weekdays = getResources().getStringArray(R.array.memo_weekdays);
        for (String weekday : weekdays) {
            TextView head = new TextView(this);
            head.setText(weekday);
            head.setGravity(Gravity.CENTER);
            head.setTextSize(12);
            head.setTextColor(AppUi.themeColor(this, R.attr.appTextSecondary));
            grid.addView(head, dayCellParams());
        }

        String month = String.format(Locale.US, "%04d-%02d",
                visibleMonth.get(Calendar.YEAR), visibleMonth.get(Calendar.MONTH) + 1);
        Set<String> markedDates = repository.datesInMonth(month, privateUnlocked);
        String today = String.format(Locale.US, "%04d-%02d-%02d",
                Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH) + 1,
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH));

        Calendar cursor = (Calendar) visibleMonth.clone();
        cursor.set(Calendar.DAY_OF_MONTH, 1);
        int firstWeekday = cursor.get(Calendar.DAY_OF_WEEK); // 1=Sunday
        int leadingBlanks = (firstWeekday + 5) % 7; // Monday-first
        int daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < leadingBlanks; i++) {
            grid.addView(new TextView(this), dayCellParams());
        }
        for (int day = 1; day <= daysInMonth; day++) {
            String date = String.format(Locale.US, "%s-%02d", month, day);
            TextView cell = new TextView(this);
            boolean marked = markedDates.contains(date);
            cell.setText(marked ? day + "\n•" : String.valueOf(day));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(14);
            cell.setLines(2);
            cell.setClickable(true);
            boolean isSelected = date.equals(selectedDate);
            boolean isToday = date.equals(today);
            if (isSelected) {
                cell.setBackgroundResource(R.drawable.bg_button_primary);
                cell.setTextColor(getColor(R.color.text_on_accent));
            } else {
                cell.setTextColor(marked
                        ? getColor(R.color.accent)
                        : AppUi.themeColor(this, R.attr.appTextPrimary));
                if (isToday) {
                    cell.setTypeface(cell.getTypeface(), Typeface.BOLD);
                }
            }
            cell.setOnClickListener(v -> {
                selectedDate = date.equals(selectedDate) ? "" : date;
                refresh();
            });
            grid.addView(cell, dayCellParams());
        }
        card.addView(grid);
        return card;
    }

    private GridLayout.LayoutParams dayCellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
        );
        params.width = 0;
        params.height = AppUi.dp(this, 48);
        return params;
    }

    private TextView navButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(24);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(getColor(R.color.accent));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    // ---------- 私密区 ----------

    private void togglePrivateArea() {
        if (privateUnlocked) {
            privateUnlocked = false;
            refresh();
            return;
        }
        if (!MemoLock.hasPin(this)) {
            promptCreatePin();
            return;
        }
        promptEnterPin();
    }

    private void promptCreatePin() {
        EditText first = pinInput(getString(R.string.memo_pin_set_hint));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.memo_pin_enable_title))
                .setMessage(getString(R.string.memo_pin_enable_msg))
                .setView(wrapDialogView(first))
                .setPositiveButton(getString(R.string.common_next), (dialog, which) -> {
                    String pin = first.getText().toString();
                    if (pin.length() < 4 || pin.length() > 8) {
                        toast(getString(R.string.memo_pin_len_error));
                        return;
                    }
                    EditText second = pinInput(getString(R.string.memo_pin_reenter));
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.memo_pin_confirm_title))
                            .setView(wrapDialogView(second))
                            .setPositiveButton(getString(R.string.common_ok), (d2, w2) -> {
                                if (!pin.equals(second.getText().toString())) {
                                    toast(getString(R.string.memo_pin_mismatch));
                                    return;
                                }
                                MemoLock.setPin(this, pin);
                                privateUnlocked = true;
                                toast(getString(R.string.memo_pin_enabled));
                                refresh();
                            })
                            .setNegativeButton(getString(R.string.common_cancel), null)
                            .show();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void promptEnterPin() {
        EditText input = pinInput(getString(R.string.memo_pin_enter));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.memo_pin_unlock_title))
                .setView(wrapDialogView(input))
                .setPositiveButton(getString(R.string.memo_pin_unlock_btn), (dialog, which) -> {
                    if (MemoLock.checkPin(this, input.getText().toString())) {
                        privateUnlocked = true;
                        refresh();
                    } else {
                        toast(getString(R.string.memo_pin_wrong));
                    }
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private EditText pinInput(String hint) {
        EditText input = AppUi.input(this, hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return input;
    }

    private View wrapDialogView(View view) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AppUi.dp(this, 20);
        wrap.setPadding(pad, AppUi.dp(this, 8), pad, 0);
        wrap.addView(view);
        return wrap;
    }

    // ---------- 其他 ----------

    private void openEditor(long memoId, String prefillDate) {
        Intent intent = new Intent(this, MemoEditActivity.class);
        if (memoId > 0) {
            intent.putExtra("memoId", memoId);
        }
        if (!prefillDate.isEmpty()) {
            intent.putExtra("memoDate", prefillDate);
        }
        intent.putExtra("privateUnlocked", privateUnlocked);
        startActivity(intent);
    }

    private String formatDateTime(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return String.format(Locale.US, "%02d-%02d %02d:%02d",
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
