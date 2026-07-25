package com.jinxin.unlockhub;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jinxin.unlockhub.data.UnlockEvent;
import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.scheduler.AlertScheduler;
import com.jinxin.unlockhub.sync.SyncSchedule;
import com.jinxin.unlockhub.sync.UnlockSync;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;
import com.jinxin.unlockhub.util.NetworkUtil;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.ShareActions;
import com.jinxin.unlockhub.util.TimeFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SafePingActivity extends BaseActivity {
    private static final long REFRESH_INTERVAL_MS = 60_000L;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshDashboard();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private TextView todayUnlockText;
    private TextView nextSyncText;
    private TextView lastUnlockText;
    private TextView recordCountText;
    private TextView detailText;
    private TextView historyText;
    private TextView readStatusText;
    private RadioGroup modeGroup;
    private LinearLayout weekdayPanel;
    private LinearLayout intervalPanel;
    private TextView[] weekdayButtons;
    private Spinner intervalSpinner;
    private EditText customIntervalInput;
    private Button anchorDateButton;
    private BroadcastReceiver dashboardReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        if (!Prefs.hasSavedSession(this)) {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
            return;
        }
        NotificationHelper.ensureChannels(this);
        UnlockListenRegistrar.ensureRegistered(this);
        setContentView(buildContent());
        loadSyncSettings();
        requestNotificationPermission();
        AlertScheduler.scheduleNextCheck(this, new UnlockRepository(this).lastActivityAt());
        refreshDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerDashboardReceiver();
        UnlockSync.syncPendingSilently(this);
        refreshDashboard();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        unregisterDashboardReceiver();
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void registerDashboardReceiver() {
        if (dashboardReceiver != null) {
            return;
        }
        dashboardReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshDashboard();
            }
        };
        IntentFilter filter = new IntentFilter(UnlockListenRegistrar.ACTION_DASHBOARD_REFRESH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dashboardReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dashboardReceiver, filter);
        }
    }

    private void unregisterDashboardReceiver() {
        if (dashboardReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(dashboardReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        dashboardReceiver = null;
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdown();
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        AppUi.styleScreenBackground(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(AppUi.themeDrawable(this, R.attr.appStickyDrawable));
        header.setPadding(AppUi.dp(this, 16), AppUi.dp(this, 16), AppUi.dp(this, 16), AppUi.dp(this, 16));
        header.setElevation(AppUi.dp(this, 6));

        TextView backButton = new TextView(this);
        backButton.setText(getString(R.string.sp_back));
        backButton.setTextColor(getColor(R.color.accent));
        backButton.setTextSize(15);
        backButton.setTypeface(backButton.getTypeface(), Typeface.BOLD);
        backButton.setOnClickListener(v -> finish());
        header.addView(backButton);

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setPadding(AppUi.dp(this, 12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("SafePing");
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        titleWrap.addView(title);
        TextView subtitle = AppUi.body(this, getString(R.string.sp_subtitle));
        titleWrap.addView(subtitle);
        header.addView(titleWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settingsButton = new TextView(this);
        settingsButton.setText(getString(R.string.sp_settings));
        settingsButton.setTextColor(getColor(R.color.accent));
        settingsButton.setTextSize(14);
        settingsButton.setTypeface(settingsButton.getTypeface(), Typeface.BOLD);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settingsButton);

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

        LinearLayout todayCard = AppUi.createCard(this);
        todayCard.addView(AppUi.sectionTitle(this, getString(R.string.sp_today_status)));
        todayUnlockText = AppUi.body(this, "");
        nextSyncText = AppUi.body(this, "");
        lastUnlockText = AppUi.body(this, "");
        todayCard.addView(todayUnlockText);
        todayCard.addView(nextSyncText);
        todayCard.addView(lastUnlockText);
        content.addView(todayCard);

        LinearLayout shareCard = AppUi.createCard(this);
        shareCard.addView(AppUi.sectionTitle(this, getString(R.string.sp_share_title)));
        shareCard.addView(AppUi.body(this, getString(R.string.sp_share_desc)));
        Button copyUidButton = AppUi.secondaryButton(this, getString(R.string.sp_copy_uid));
        copyUidButton.setOnClickListener(v -> {
            ShareActions.copyUid(this);
            toast(getString(R.string.sp_copied_uid));
        });
        shareCard.addView(copyUidButton);
        Button copyLinkButton = AppUi.secondaryButton(this, getString(R.string.sp_copy_link));
        copyLinkButton.setOnClickListener(v -> {
            ShareActions.copyStatusLink(this);
            toast(getString(R.string.sp_copied_link));
        });
        shareCard.addView(copyLinkButton);
        Button copyGuideButton = AppUi.secondaryButton(this, getString(R.string.sp_copy_guide));
        copyGuideButton.setOnClickListener(v -> {
            ShareActions.copyShareGuide(this);
            toast(getString(R.string.sp_copied_guide));
        });
        shareCard.addView(copyGuideButton);
        Button shareButton = AppUi.primaryButton(this, getString(R.string.sp_share_other));
        shareButton.setOnClickListener(v -> ShareActions.shareGuide(this));
        shareCard.addView(shareButton);
        Button openWebButton = AppUi.secondaryButton(this, getString(R.string.sp_open_browser));
        openWebButton.setOnClickListener(v -> ShareActions.openInBrowser(this, ShareActions.statusPageUrl(this)));
        shareCard.addView(openWebButton);
        content.addView(shareCard);

        LinearLayout viewersCard = AppUi.createCard(this);
        viewersCard.addView(AppUi.sectionTitle(this, getString(R.string.sp_viewers_title)));
        viewersCard.addView(AppUi.body(this, getString(R.string.sp_viewers_desc)));
        Button manageViewersButton = AppUi.primaryButton(this, getString(R.string.sp_manage_viewers));
        manageViewersButton.setOnClickListener(v -> startActivity(new Intent(this, ViewersActivity.class)));
        viewersCard.addView(manageViewersButton);
        readStatusText = AppUi.body(this, getString(R.string.sp_loading_read));
        viewersCard.addView(readStatusText);
        content.addView(viewersCard);

        LinearLayout recordCard = AppUi.createCard(this);
        recordCard.addView(AppUi.sectionTitle(this, getString(R.string.sp_local_records)));
        recordCountText = AppUi.statNumber(this, getString(R.string.sp_days_fmt, 0, UnlockRepository.MAX_LOCAL_RECORDS));
        recordCard.addView(recordCountText);
        detailText = AppUi.body(this, "");
        recordCard.addView(detailText);
        content.addView(recordCard);

        LinearLayout syncCard = AppUi.createCard(this);
        syncCard.addView(AppUi.sectionTitle(this, getString(R.string.sp_sync_settings)));
        syncCard.addView(AppUi.body(this, getString(R.string.sp_sync_desc)));

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton weekdayMode = new RadioButton(this);
        weekdayMode.setText(getString(R.string.sp_mode_weekday));
        weekdayMode.setId(View.generateViewId());
        weekdayMode.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        AppUi.styleToggle(weekdayMode);
        RadioButton intervalMode = new RadioButton(this);
        intervalMode.setText(getString(R.string.sp_mode_interval));
        intervalMode.setId(View.generateViewId());
        intervalMode.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        AppUi.styleToggle(intervalMode);
        modeGroup.addView(weekdayMode);
        modeGroup.addView(intervalMode);
        syncCard.addView(modeGroup);

        weekdayPanel = new LinearLayout(this);
        weekdayPanel.setOrientation(LinearLayout.VERTICAL);
        weekdayPanel.addView(AppUi.label(this, getString(R.string.sp_sync_days)));
        LinearLayout weekdayRow = new LinearLayout(this);
        weekdayRow.setOrientation(LinearLayout.HORIZONTAL);
        weekdayButtons = new TextView[7];
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            TextView chip = AppUi.weekdayChip(this, SyncSchedule.weekdayLabel(this, days[i]));
            final TextView chipView = chip;
            chip.setOnClickListener(v -> AppUi.styleWeekdayChip(chipView, !chipView.isSelected()));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            chipParams.setMargins(AppUi.dp(this, 2), 0, AppUi.dp(this, 2), 0);
            weekdayRow.addView(chip, chipParams);
            weekdayButtons[i] = chip;
        }
        weekdayPanel.addView(weekdayRow);
        weekdayPanel.addView(AppUi.body(this, getString(R.string.sp_weekday_hint)));
        syncCard.addView(weekdayPanel);

        intervalPanel = new LinearLayout(this);
        intervalPanel.setOrientation(LinearLayout.VERTICAL);
        intervalPanel.addView(AppUi.label(this, getString(R.string.sp_anchor_label)));
        anchorDateButton = AppUi.secondaryButton(this, getString(R.string.sp_pick_anchor));
        anchorDateButton.setOnClickListener(v -> pickAnchorDate());
        intervalPanel.addView(anchorDateButton);
        intervalPanel.addView(AppUi.label(this, getString(R.string.sp_interval_label)));
        intervalSpinner = new Spinner(this);
        // 收起状态显示在当前主题的卡片上：用主题文字色；下拉弹窗是白底：用深色
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.sp_intervals)
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(AppUi.themeColor(getContext(), R.attr.appTextPrimary));
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(getContext().getColor(R.color.text_on_light));
                return view;
            }
        };
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        intervalSpinner.setAdapter(intervalAdapter);
        if (intervalSpinner.getBackground() != null) {
            intervalSpinner.getBackground().setTint(AppUi.themeColor(this, R.attr.appTextSecondary));
        }
        intervalPanel.addView(intervalSpinner);
        customIntervalInput = AppUi.input(this, getString(R.string.sp_custom_interval));
        customIntervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        customIntervalInput.setVisibility(View.GONE);
        intervalPanel.addView(customIntervalInput);
        intervalPanel.addView(AppUi.body(this, getString(R.string.sp_interval_hint)));
        syncCard.addView(intervalPanel);

        intervalSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                customIntervalInput.setVisibility(position == 6 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> updateSyncPanels());

        Button saveSyncButton = AppUi.primaryButton(this, getString(R.string.sp_save_sync));
        saveSyncButton.setOnClickListener(v -> saveSyncSettings());
        syncCard.addView(saveSyncButton);
        content.addView(syncCard);

        LinearLayout historyCard = AppUi.createCard(this);
        historyCard.addView(AppUi.sectionTitle(this, getString(R.string.sp_recent_sync)));
        historyText = AppUi.body(this, "");
        historyCard.addView(historyText);
        content.addView(historyCard);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        return root;
    }

    private void updateSyncPanels() {
        boolean weekday = modeGroup.getCheckedRadioButtonId() == modeGroup.getChildAt(0).getId();
        weekdayPanel.setVisibility(weekday ? View.VISIBLE : View.GONE);
        intervalPanel.setVisibility(weekday ? View.GONE : View.VISIBLE);
    }

    private void loadSyncSettings() {
        boolean weekday = SyncSchedule.isWeekdayMode(this);
        modeGroup.check(weekday ? modeGroup.getChildAt(0).getId() : modeGroup.getChildAt(1).getId());

        int mask = Prefs.syncWeekdaysMask(this);
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            boolean selected = (mask & SyncSchedule.weekdayBit(days[i])) != 0;
            AppUi.styleWeekdayChip(weekdayButtons[i], selected);
        }

        int interval = SyncSchedule.syncIntervalDays(this);
        int spinnerIndex = 6;
        for (int i = 0; i < SyncSchedule.INTERVAL_PRESETS.length; i++) {
            if (SyncSchedule.INTERVAL_PRESETS[i] == interval) {
                spinnerIndex = i;
                break;
            }
        }
        intervalSpinner.setSelection(spinnerIndex);
        if (spinnerIndex == 6) {
            customIntervalInput.setText(String.valueOf(interval));
            customIntervalInput.setVisibility(View.VISIBLE);
        }
        updateAnchorDateButton();
        updateSyncPanels();
    }

    private void saveSyncSettings() {
        boolean weekday = modeGroup.getCheckedRadioButtonId() == modeGroup.getChildAt(0).getId();
        Prefs.setSyncMode(this, weekday ? Prefs.SYNC_MODE_WEEKDAY : Prefs.SYNC_MODE_INTERVAL);

        if (weekday) {
            int mask = 0;
            DayOfWeek[] days = DayOfWeek.values();
            for (int i = 0; i < days.length; i++) {
                if (weekdayButtons[i].isSelected()) {
                    mask |= SyncSchedule.weekdayBit(days[i]);
                }
            }
            if (mask == 0) {
                toast(getString(R.string.sp_need_one_day));
                return;
            }
            Prefs.setSyncWeekdaysMask(this, mask);
        } else {
            int index = intervalSpinner.getSelectedItemPosition();
            int interval;
            if (index >= 0 && index < SyncSchedule.INTERVAL_PRESETS.length) {
                interval = SyncSchedule.INTERVAL_PRESETS[index];
            } else {
                try {
                    interval = Integer.parseInt(customIntervalInput.getText().toString().trim());
                } catch (NumberFormatException e) {
                    toast(getString(R.string.sp_invalid_interval));
                    return;
                }
            }
            if (interval < 1 || interval > SyncSchedule.MAX_INTERVAL_DAYS) {
                toast(getString(R.string.sp_interval_range, SyncSchedule.MAX_INTERVAL_DAYS));
                return;
            }
            Prefs.setSyncIntervalDays(this, interval);
        }
        toast(getString(R.string.sp_sync_saved));
        refreshDashboard();
    }

    private void pickAnchorDate() {
        String current = SyncSchedule.syncAnchorDate(this);
        LocalDate date = LocalDate.parse(current);
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String value = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    Prefs.setSyncAnchorDate(this, value);
                    updateAnchorDateButton();
                    refreshDashboard();
                },
                date.getYear(),
                date.getMonthValue() - 1,
                date.getDayOfMonth()
        ).show();
    }

    private void updateAnchorDateButton() {
        anchorDateButton.setText(getString(R.string.sp_anchor_btn, SyncSchedule.syncAnchorDate(this)));
    }

    private void refreshDashboard() {
        UnlockRepository repository = new UnlockRepository(this);
        UnlockRepository.Stats stats = repository.stats();
        String today = TimeFormat.localDate(System.currentTimeMillis());
        UnlockEvent todayEvent = repository.findByDate(today);

        recordCountText.setText(getString(R.string.sp_days_fmt, stats.total, UnlockRepository.MAX_LOCAL_RECORDS));

        todayUnlockText.setText(todayEvent == null
                ? getString(R.string.sp_today_first_none)
                : getString(R.string.sp_today_first_at, TimeFormat.humanDateTime(todayEvent.firstUnlockAt)));

        nextSyncText.setText(getString(R.string.sp_next_msg, SyncSchedule.formatNextSyncStatus(
                this,
                NetworkUtil.isOnline(this),
                todayEvent != null,
                stats.pending
        )));

        long lastUnlockAt = resolveLastUnlockAt(repository, todayEvent);
        lastUnlockText.setText(lastUnlockAt <= 0L
                ? getString(R.string.sp_last_unlock_none)
                : getString(R.string.sp_last_unlock_at, TimeFormat.humanDateTime(lastUnlockAt)));

        StringBuilder detail = new StringBuilder();
        detail.append(getString(R.string.sp_week_recorded, stats.thisWeek));
        detail.append(getString(R.string.sp_uploaded, stats.synced));
        if (stats.pending > 0) {
            detail.append(getString(R.string.sp_pending_upload, stats.pending));
        }
        detail.append(getString(R.string.sp_sync_way));
        if (SyncSchedule.isWeekdayMode(this)) {
            detail.append(getString(R.string.sp_by_weekday, SyncSchedule.formatWeekdaySelection(this)));
        } else {
            detail.append(getString(R.string.sp_by_interval,
                    SyncSchedule.syncAnchorDate(this), SyncSchedule.syncIntervalDays(this)));
        }
        String lastSyncError = Prefs.lastSyncError(this);
        if (lastSyncError != null && !lastSyncError.isEmpty()) {
            detail.append(getString(R.string.sp_last_sync_error, lastSyncError));
        }
        detailText.setText(detail.toString());

        refreshHistory();
        refreshReadStatus();
    }

    private void refreshReadStatus() {
        if (!Prefs.isAccountBound(this)) {
            readStatusText.setText(getString(R.string.sp_read_need_account));
            return;
        }
        networkExecutor.execute(() -> {
            try {
                String json = new ApiClient(this).listOwnerMessagesJson();
                JSONArray messages = new JSONObject(json).getJSONArray("messages");
                runOnUiThread(() -> readStatusText.setText(formatReadStatus(messages)));
            } catch (Exception e) {
                runOnUiThread(() -> readStatusText.setText(getString(R.string.sp_read_load_fail, e.getMessage())));
            }
        });
    }

    private String formatReadStatus(JSONArray messages) {
        if (messages.length() == 0) {
            return getString(R.string.sp_no_sync_msg);
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(messages.length(), 3);
        for (int i = 0; i < limit; i++) {
            try {
                JSONObject message = messages.getJSONObject(i);
                builder.append("• ").append(message.optString("title", getString(R.string.sp_msg_default)));
                JSONArray readers = message.optJSONArray("readers");
                if (readers == null || readers.length() == 0) {
                    builder.append(getString(R.string.sp_no_viewers));
                    continue;
                }
                builder.append("\n");
                for (int j = 0; j < readers.length(); j++) {
                    JSONObject reader = readers.getJSONObject(j);
                    builder.append("  - ")
                            .append(reader.optString("nickname", getString(R.string.sp_unknown)))
                            .append(getString(reader.optBoolean("read", false)
                                    ? R.string.sp_read : R.string.sp_unread))
                            .append("\n");
                }
                if (i < limit - 1) {
                    builder.append("\n");
                }
            } catch (Exception ignored) {
            }
        }
        return builder.toString().trim();
    }

    private long resolveLastUnlockAt(UnlockRepository repository, UnlockEvent todayEvent) {
        long lastCaptureAt = Prefs.lastAutoCaptureAt(this);
        if (lastCaptureAt > 0L) {
            return lastCaptureAt;
        }
        if (todayEvent != null) {
            return todayEvent.firstUnlockAt;
        }
        return repository.lastActivityAt();
    }

    private void refreshHistory() {
        UnlockRepository repository = new UnlockRepository(this);
        int pending = repository.unsyncedCount();
        String[] uploads = Prefs.syncUploadHistory(this);
        if (uploads.length == 0) {
            if (pending > 0) {
                historyText.setText(getString(R.string.sp_no_history_pending, pending));
            } else {
                historyText.setText(getString(R.string.sp_no_history));
            }
            return;
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(uploads.length, 10);
        for (int i = 0; i < limit; i++) {
            String[] parts = uploads[i].split("\\|");
            if (parts.length == 2) {
                builder.append(getString(R.string.sp_generated,
                        TimeFormat.formatUploadRange(parts[0], parts[1])));
                if (i < limit - 1) {
                    builder.append("\n");
                }
            }
        }
        if (pending > 0) {
            builder.append(getString(R.string.sp_pending_line, pending));
        }
        historyText.setText(builder.toString());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
