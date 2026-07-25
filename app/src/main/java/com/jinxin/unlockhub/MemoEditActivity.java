package com.jinxin.unlockhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;
import com.jinxin.unlockhub.scheduler.MemoReminderScheduler;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;
import com.jinxin.unlockhub.util.MemoLock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class MemoEditActivity extends BaseActivity {
    private MemoRepository repository;
    private Memo memo;

    // 提醒时间：日期取自"日历日期(memoDate)"，这里只存时:分（-1 表示未设置）
    private int remindHour = -1;
    private int remindMinute = -1;

    private EditText titleInput;
    private EditText contentInput;
    private LinearLayout checklistContainer;
    private LinearLayout checklistSection;
    private LinearLayout textSection;
    private TextView typeTextChip;
    private TextView typeChecklistChip;
    private Button dateButton;
    private Button remindButton;
    private Switch pinnedSwitch;
    private Switch unreadSwitch;
    private Switch privateSwitch;
    private Switch unlockPopupSwitch;
    private TextView unlockPopupHint;

    private final List<ChecklistRow> checklistRows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        repository = new MemoRepository(this);

        long memoId = getIntent().getLongExtra("memoId", 0L);
        if (memoId > 0) {
            memo = repository.findById(memoId);
        }
        if (memo == null) {
            memo = new Memo();
            memo.memoDate = getIntent().getStringExtra("memoDate") == null
                    ? "" : getIntent().getStringExtra("memoDate");
        }

        boolean privateUnlocked = getIntent().getBooleanExtra("privateUnlocked", false);
        if (memo.isPrivate && !privateUnlocked) {
            requirePinThenShow();
        } else {
            setContentView(buildContent());
        }
    }

    private void requirePinThenShow() {
        EditText input = AppUi.input(this, getString(R.string.memo_pin_enter));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AppUi.dp(this, 20);
        wrap.setPadding(pad, AppUi.dp(this, 8), pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.memoedit_private_title))
                .setMessage(getString(R.string.memoedit_private_msg))
                .setView(wrap)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.memo_pin_unlock_btn), (dialog, which) -> {
                    if (MemoLock.checkPin(this, input.getText().toString())) {
                        setContentView(buildContent());
                    } else {
                        Toast.makeText(this, getString(R.string.memo_pin_wrong), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.common_cancel), (dialog, which) -> finish())
                .show();
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

        content.addView(AppUi.createBrandHeader(this,
                getString(memo.id > 0 ? R.string.memoedit_title_edit : R.string.memoedit_title_new)));

        LinearLayout card = AppUi.createCard(this);

        card.addView(AppUi.label(this, getString(R.string.memoedit_label_title)));
        titleInput = AppUi.input(this, getString(R.string.memoedit_hint_title));
        titleInput.setText(memo.title);
        card.addView(titleInput);

        // 类型切换
        card.addView(AppUi.label(this, getString(R.string.memoedit_label_type)));
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeTextChip = AppUi.weekdayChip(this, getString(R.string.memoedit_type_text));
        typeChecklistChip = AppUi.weekdayChip(this, getString(R.string.memoedit_type_checklist));
        typeTextChip.setOnClickListener(v -> switchType(false));
        typeChecklistChip.setOnClickListener(v -> switchType(true));
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        typeParams.setMargins(0, 0, AppUi.dp(this, 8), 0);
        typeRow.addView(typeTextChip, typeParams);
        typeRow.addView(typeChecklistChip, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(typeRow);

        // 文本内容
        textSection = new LinearLayout(this);
        textSection.setOrientation(LinearLayout.VERTICAL);
        textSection.addView(AppUi.label(this, getString(R.string.memoedit_label_content)));
        contentInput = AppUi.input(this, getString(R.string.memoedit_hint_content));
        contentInput.setSingleLine(false);
        contentInput.setMinLines(4);
        contentInput.setGravity(Gravity.TOP | Gravity.START);
        contentInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textSection.addView(contentInput);
        card.addView(textSection);

        // 清单内容
        checklistSection = new LinearLayout(this);
        checklistSection.setOrientation(LinearLayout.VERTICAL);
        checklistSection.addView(AppUi.label(this, getString(R.string.memoedit_label_checklist)));
        checklistContainer = new LinearLayout(this);
        checklistContainer.setOrientation(LinearLayout.VERTICAL);
        checklistSection.addView(checklistContainer);
        Button addItemButton = AppUi.secondaryButton(this, getString(R.string.memoedit_add_item));
        addItemButton.setOnClickListener(v -> addChecklistRow("", false));
        checklistSection.addView(addItemButton);
        card.addView(checklistSection);

        // 初始化内容
        if (memo.isChecklist()) {
            for (Memo.Item item : memo.checklistItems()) {
                addChecklistRow(item.text, item.checked);
            }
            if (checklistRows.isEmpty()) {
                addChecklistRow("", false);
            }
        } else {
            contentInput.setText(memo.content);
        }
        switchType(memo.isChecklist());

        content.addView(card);

        // 日期与提醒
        LinearLayout scheduleCard = AppUi.createCard(this);
        scheduleCard.addView(AppUi.label(this, getString(R.string.memoedit_label_date)));
        dateButton = AppUi.secondaryButton(this, "");
        dateButton.setOnClickListener(v -> pickDate());
        scheduleCard.addView(dateButton);

        scheduleCard.addView(AppUi.label(this, getString(R.string.memoedit_label_remind)));
        remindButton = AppUi.secondaryButton(this, "");
        remindButton.setOnClickListener(v -> pickReminder());
        scheduleCard.addView(remindButton);
        content.addView(scheduleCard);

        // 解锁-弹窗加强提醒：显眼的独立卡片，紧跟时间设置
        LinearLayout unlockPopupCard = AppUi.createCard(this);
        LinearLayout unlockPopupHeader = new LinearLayout(this);
        unlockPopupHeader.setOrientation(LinearLayout.HORIZONTAL);
        unlockPopupHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView unlockPopupTitle = AppUi.sectionTitle(this, getString(R.string.memoedit_unlockpopup_title));
        unlockPopupTitle.setTextColor(getColor(R.color.accent));
        unlockPopupHeader.addView(unlockPopupTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        unlockPopupSwitch = new Switch(this);
        AppUi.styleToggle(unlockPopupSwitch);
        unlockPopupSwitch.setChecked(memo.unlockPopup);
        unlockPopupSwitch.setOnCheckedChangeListener((button, checked) -> updateUnlockPopupHint());
        unlockPopupHeader.addView(unlockPopupSwitch);
        unlockPopupCard.addView(unlockPopupHeader);
        unlockPopupHint = AppUi.body(this, "");
        unlockPopupHint.setTextSize(13);
        unlockPopupCard.addView(unlockPopupHint);
        content.addView(unlockPopupCard);

        updateDateButton();
        updateRemindButton();
        updateUnlockPopupHint();

        // 属性开关
        LinearLayout flagCard = AppUi.createCard(this);
        pinnedSwitch = buildSwitch(getString(R.string.memoedit_flag_pinned), memo.pinned);
        unreadSwitch = buildSwitch(getString(R.string.memoedit_flag_unread), memo.unread);
        privateSwitch = buildSwitch(getString(R.string.memoedit_flag_private), memo.isPrivate);
        flagCard.addView(pinnedSwitch);
        flagCard.addView(unreadSwitch);
        flagCard.addView(privateSwitch);
        content.addView(flagCard);

        Button saveButton = AppUi.primaryButton(this, getString(R.string.common_save));
        saveButton.setOnClickListener(v -> save());
        content.addView(saveButton);

        if (memo.id > 0) {
            Button deleteButton = AppUi.secondaryButton(this, getString(R.string.memoedit_delete));
            deleteButton.setOnClickListener(v -> confirmDelete());
            content.addView(deleteButton);
        }

        return scrollView;
    }

    private Switch buildSwitch(String label, boolean checked) {
        Switch switchView = new Switch(this);
        AppUi.styleToggle(switchView);
        switchView.setText(label);
        switchView.setTextSize(15);
        switchView.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        switchView.setChecked(checked);
        switchView.setPadding(0, AppUi.dp(this, 10), 0, AppUi.dp(this, 10));
        return switchView;
    }

    private void switchType(boolean checklist) {
        memo.type = checklist ? Memo.TYPE_CHECKLIST : Memo.TYPE_TEXT;
        AppUi.styleWeekdayChip(typeTextChip, !checklist);
        AppUi.styleWeekdayChip(typeChecklistChip, checklist);
        textSection.setVisibility(checklist ? View.GONE : View.VISIBLE);
        checklistSection.setVisibility(checklist ? View.VISIBLE : View.GONE);
        if (checklist && checklistRows.isEmpty()) {
            addChecklistRow("", false);
        }
    }

    private void addChecklistRow(String text, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        CheckBox checkBox = new CheckBox(this);
        AppUi.styleToggle(checkBox);
        checkBox.setChecked(checked);
        row.addView(checkBox);

        EditText itemInput = AppUi.input(this, getString(R.string.memoedit_hint_item));
        itemInput.setText(text);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputParams.setMargins(0, 0, 0, AppUi.dp(this, 4));
        itemInput.setLayoutParams(inputParams);
        row.addView(itemInput);

        TextView removeButton = new TextView(this);
        removeButton.setText("✕");
        removeButton.setTextSize(18);
        removeButton.setTextColor(AppUi.themeColor(this, R.attr.appTextSecondary));
        removeButton.setPadding(AppUi.dp(this, 10), 0, AppUi.dp(this, 4), 0);
        removeButton.setClickable(true);
        row.addView(removeButton);

        ChecklistRow checklistRow = new ChecklistRow(row, checkBox, itemInput);
        removeButton.setOnClickListener(v -> {
            checklistContainer.removeView(row);
            checklistRows.remove(checklistRow);
        });

        checklistRows.add(checklistRow);
        checklistContainer.addView(row);
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        if (!memo.memoDate.isEmpty()) {
            String[] parts = memo.memoDate.split("-");
            if (parts.length == 3) {
                calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            memo.memoDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            updateDateButton();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.common_clear), (d, w) -> {
            memo.memoDate = "";
            updateDateButton();
        });
        dialog.show();
    }

    /**
     * 定时提醒只选「时间」，日期取自上面的「日历日期」；没设日历日期则默认今天。
     * 这样两个选择器各司其职：日历日期只选日、定时提醒只选时。
     */
    private void pickReminder() {
        Calendar calendar = Calendar.getInstance();
        if (memo.remindAt > System.currentTimeMillis()) {
            calendar.setTimeInMillis(memo.remindAt);
        } else {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
            calendar.set(Calendar.MINUTE, 0);
        }
        TimePickerDialog timeDialog = new TimePickerDialog(this, (timeView, hour, minute) -> {
            Calendar chosen = Calendar.getInstance();
            if (!memo.memoDate.isEmpty()) {
                String[] parts = memo.memoDate.split("-");
                if (parts.length == 3) {
                    chosen.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                }
            }
            chosen.set(Calendar.HOUR_OF_DAY, hour);
            chosen.set(Calendar.MINUTE, minute);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            if (chosen.getTimeInMillis() <= System.currentTimeMillis()) {
                if (memo.memoDate.isEmpty()) {
                    chosen.add(Calendar.DAY_OF_MONTH, 1); // 用今天且时间已过 → 顺延到明天
                } else {
                    Toast.makeText(this, getString(R.string.memoedit_time_past), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            memo.remindAt = chosen.getTimeInMillis();
            updateRemindButton();
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
        timeDialog.setButton(TimePickerDialog.BUTTON_NEUTRAL, getString(R.string.memoedit_clear_remind), (d, w) -> {
            memo.remindAt = 0;
            updateRemindButton();
        });
        timeDialog.show();
    }

    private void updateDateButton() {
        dateButton.setText(memo.memoDate.isEmpty() ? getString(R.string.memoedit_no_date) : "📅 " + memo.memoDate);
        updateUnlockPopupHint();
    }

    /** 说明文案随时间设置动态变化，让用户明确当前的弹窗时机。 */
    private void updateUnlockPopupHint() {
        if (unlockPopupHint == null) {
            return;
        }
        if (unlockPopupSwitch != null && !unlockPopupSwitch.isChecked()) {
            unlockPopupHint.setText(getString(R.string.memoedit_uphint_off));
            return;
        }
        String timing;
        if (memo.remindAt > System.currentTimeMillis()) {
            timing = getString(R.string.memoedit_uphint_time);
        } else if (!memo.memoDate.isEmpty()) {
            timing = getString(R.string.memoedit_uphint_date, memo.memoDate);
        } else {
            timing = getString(R.string.memoedit_uphint_none);
        }
        unlockPopupHint.setText(timing + getString(R.string.memoedit_uphint_suffix));
    }

    private void updateRemindButton() {
        updateUnlockPopupHint();
        if (memo.remindAt <= System.currentTimeMillis()) {
            remindButton.setText(getString(R.string.memoedit_no_remind));
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(memo.remindAt);
        remindButton.setText(String.format(Locale.US, "⏰ %04d-%02d-%02d %02d:%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)));
    }

    private void save() {
        memo.title = titleInput.getText().toString().trim();
        if (memo.isChecklist()) {
            List<Memo.Item> items = new ArrayList<>();
            for (ChecklistRow row : checklistRows) {
                String text = row.input.getText().toString().trim();
                if (!text.isEmpty()) {
                    items.add(new Memo.Item(text, row.checkBox.isChecked()));
                }
            }
            if (memo.title.isEmpty() && items.isEmpty()) {
                Toast.makeText(this, getString(R.string.memoedit_empty_save), Toast.LENGTH_SHORT).show();
                return;
            }
            memo.content = Memo.encodeChecklist(items);
        } else {
            memo.content = contentInput.getText().toString();
            if (memo.title.isEmpty() && memo.content.trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.memoedit_empty_save), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        memo.pinned = pinnedSwitch.isChecked();
        memo.unread = unreadSwitch.isChecked();
        memo.unlockPopup = unlockPopupSwitch.isChecked();
        // 解锁弹窗依赖"未读"态才会进入候选（unlockPopupPending 查询 unread=1）：
        // 开了解锁弹窗就自动置为未读，确保能弹；用户打开该备忘查看后会自动转已读、停止弹出。
        if (memo.unlockPopup) {
            memo.unread = true;
        }

        boolean wantPrivate = privateSwitch.isChecked();
        if (wantPrivate && !MemoLock.hasPin(this)) {
            Toast.makeText(this, getString(R.string.memoedit_private_need_pin), Toast.LENGTH_LONG).show();
            return;
        }
        memo.isPrivate = wantPrivate;

        repository.save(memo);
        MemoReminderScheduler.schedule(this, memo);
        MemoNotifier.updateBadge(this);
        com.jinxin.unlockhub.sync.MemoSync.syncAsync(this);

        if (memo.remindAt > System.currentTimeMillis() && !MemoReminderScheduler.canScheduleExact(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.memoedit_alarm_title))
                    .setMessage(getString(R.string.memoedit_alarm_msg))
                    .setPositiveButton(getString(R.string.memoedit_alarm_go), (dialog, which) -> {
                        try {
                            startActivity(MemoReminderScheduler.exactAlarmSettingsIntent(this));
                        } catch (Exception ignored) {
                        }
                        finish();
                    })
                    .setNegativeButton(getString(R.string.memoedit_alarm_later), (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }
        Toast.makeText(this, getString(R.string.common_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.memo_delete_title))
                .setMessage(getString(R.string.memo_delete_message))
                .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> {
                    MemoReminderScheduler.cancel(this, memo.id);
                    repository.delete(memo.id);
                    MemoNotifier.updateBadge(this);
                    com.jinxin.unlockhub.sync.MemoSync.syncAsync(this);
                    finish();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private static final class ChecklistRow {
        final LinearLayout row;
        final CheckBox checkBox;
        final EditText input;

        ChecklistRow(LinearLayout row, CheckBox checkBox, EditText input) {
            this.row = row;
            this.checkBox = checkBox;
            this.input = input;
        }
    }
}
