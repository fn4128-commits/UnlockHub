package com.jinxin.unlockhub;



import android.Manifest;

import android.app.Activity;

import android.content.Intent;

import android.content.pm.PackageManager;

import android.os.Build;

import android.os.Bundle;

import android.view.Gravity;

import android.view.ViewGroup;

import android.widget.Button;

import android.widget.CompoundButton;

import android.widget.LinearLayout;

import android.widget.ScrollView;

import android.widget.Switch;

import android.widget.TextView;

import android.widget.Toast;



import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;

import com.jinxin.unlockhub.util.BatteryOptimizationStatus;
import com.jinxin.unlockhub.util.BoundAppMonitor;
import com.jinxin.unlockhub.util.LocaleManager;
import com.jinxin.unlockhub.util.OemSettingsLauncher;

import com.jinxin.unlockhub.util.Prefs;

import com.jinxin.unlockhub.util.SystemSettingsLauncher;



public final class SettingsActivity extends BaseActivity {

    private static final int REQUEST_NOTIFICATIONS = 2101;



    private TextView statusText;

    private Switch pauseSwitch;



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

        setContentView(buildContent());

        refreshState();

    }



    @Override

    protected void onResume() {

        super.onResume();

        refreshState();

    }



    @Override

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATIONS) {

            refreshState();

        }

    }



    private ScrollView buildContent() {

        ScrollView scrollView = new ScrollView(this);

        scrollView.setFillViewport(true);

        AppUi.styleScroll(scrollView);



        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(AppUi.dp(this, 18), AppUi.dp(this, 24), AppUi.dp(this, 18), AppUi.dp(this, 28));

        scrollView.addView(root, new ViewGroup.LayoutParams(

                ViewGroup.LayoutParams.MATCH_PARENT,

                ViewGroup.LayoutParams.WRAP_CONTENT

        ));



        Button backButton = AppUi.secondaryButton(this, getString(R.string.settings_back));

        backButton.setOnClickListener(v -> finish());

        root.addView(backButton);

        // 语言切换
        LinearLayout languageCard = AppUi.createCard(this);
        LinearLayout languageRow = new LinearLayout(this);
        languageRow.setOrientation(LinearLayout.HORIZONTAL);
        languageRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView languageLabel = AppUi.label(this, getString(R.string.settings_language));
        Switch languageSwitch = new Switch(this);
        AppUi.styleToggle(languageSwitch);
        languageSwitch.setChecked(LocaleManager.LANG_EN.equals(Prefs.appLanguage(this)));
        languageSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) {
                return;
            }
            Prefs.setAppLanguage(this, checked ? LocaleManager.LANG_EN : LocaleManager.LANG_ZH);
            // 重启到主界面，让全应用按新语言重新加载。
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        languageRow.addView(languageLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        languageRow.addView(languageSwitch);
        languageCard.addView(languageRow);
        TextView languageHint = AppUi.body(this, getString(R.string.settings_language_hint));
        languageHint.setTextSize(13);
        languageCard.addView(languageHint);
        root.addView(languageCard);

        root.addView(AppUi.createBrandHeader(this,

                getString(R.string.settings_header_hint)));



        LinearLayout recordCard = AppUi.createCard(this);

        recordCard.addView(AppUi.sectionTitle(this, getString(R.string.settings_auto_record)));

        recordCard.addView(AppUi.body(this, getString(R.string.settings_auto_record_desc)));

        LinearLayout pauseRow = new LinearLayout(this);

        pauseRow.setOrientation(LinearLayout.HORIZONTAL);

        pauseRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView pauseLabel = AppUi.label(this, getString(R.string.settings_pause_label));

        pauseSwitch = new Switch(this);
        AppUi.styleToggle(pauseSwitch);

        pauseRow.addView(pauseLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pauseRow.addView(pauseSwitch);

        pauseSwitch.setOnCheckedChangeListener(this::onPauseChanged);

        recordCard.addView(pauseRow);

        // 实时监控总开关
        LinearLayout realtimeRow = new LinearLayout(this);
        realtimeRow.setOrientation(LinearLayout.HORIZONTAL);
        realtimeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView realtimeLabel = AppUi.label(this, getString(R.string.settings_realtime_label));
        Switch realtimeSwitch = new Switch(this);
        AppUi.styleToggle(realtimeSwitch);
        realtimeSwitch.setChecked(com.jinxin.unlockhub.util.Prefs.isRealtimeMonitorEnabled(this));
        realtimeSwitch.setOnCheckedChangeListener((button, checked) -> {
            com.jinxin.unlockhub.util.Prefs.setRealtimeMonitorEnabled(this, checked);
            // 立即生效：开→起前台服务转入实时；关→按需（无实时功能则撤下通知）。
            com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
        });
        realtimeRow.addView(realtimeLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        realtimeRow.addView(realtimeSwitch);
        recordCard.addView(realtimeRow);
        TextView realtimeHint = AppUi.body(this, getString(R.string.settings_realtime_hint));
        realtimeHint.setTextSize(13);
        recordCard.addView(realtimeHint);

        // 解锁-弹窗（状态确认）
        LinearLayout unlockPopupRow = new LinearLayout(this);
        unlockPopupRow.setOrientation(LinearLayout.HORIZONTAL);
        unlockPopupRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView unlockPopupLabel = AppUi.label(this, getString(R.string.settings_unlockpopup_label));
        Switch unlockPopupSwitch = new Switch(this);
        AppUi.styleToggle(unlockPopupSwitch);
        unlockPopupSwitch.setChecked(com.jinxin.unlockhub.util.Prefs.isUnlockPopupEnabled(this));
        unlockPopupSwitch.setOnCheckedChangeListener((button, checked) ->
                com.jinxin.unlockhub.util.Prefs.setUnlockPopupEnabled(this, checked));
        unlockPopupRow.addView(unlockPopupLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        unlockPopupRow.addView(unlockPopupSwitch);
        recordCard.addView(unlockPopupRow);
        TextView unlockPopupHint = AppUi.body(this, getString(R.string.settings_unlockpopup_hint));
        unlockPopupHint.setTextSize(13);
        recordCard.addView(unlockPopupHint);

        Button testPopupButton = AppUi.secondaryButton(this, getString(R.string.settings_test_popup));
        testPopupButton.setOnClickListener(v -> {
            int pending = new com.jinxin.unlockhub.data.MemoRepository(this).unlockPopupPending().size();
            boolean canNotify = Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;
            toast(getString(R.string.settings_test_toast, pending,
                    getString(canNotify ? R.string.common_on : R.string.common_off)));
            com.jinxin.unlockhub.MemoNotifier.showUnlockPopupMemos(this);
        });
        recordCard.addView(testPopupButton);

        root.addView(recordCard);



        LinearLayout essentialCard = AppUi.createCard(this);

        essentialCard.addView(AppUi.sectionTitle(this, getString(R.string.settings_perms_title)));

        essentialCard.addView(AppUi.body(this, getString(R.string.settings_perms_desc)));

        essentialCard.addView(settingsButton(getString(R.string.settings_perm_1), this::requestNotifications));

        essentialCard.addView(settingsButton(getString(R.string.settings_perm_2), () ->

                SystemSettingsLauncher.openUrgentNotificationChannel(this)));

        essentialCard.addView(oemButton(getString(R.string.settings_perm_3), OemSettingsLauncher::openAutostart));

        essentialCard.addView(oemButton(getString(R.string.settings_perm_4), OemSettingsLauncher::openBackgroundUnrestricted));

        essentialCard.addView(oemButton(getString(R.string.settings_perm_5), OemSettingsLauncher::openBackgroundActivity));

        essentialCard.addView(settingsButton(getString(R.string.settings_perm_6), () ->

                SystemSettingsLauncher.openAppDetails(this)));

        Button testNotifyButton = AppUi.secondaryButton(this, getString(R.string.settings_send_test_notify));

        testNotifyButton.setOnClickListener(v -> {

            NotificationHelper.notifyTest(this);

            toast(getString(R.string.settings_test_notify_sent));

        });

        essentialCard.addView(testNotifyButton);

        root.addView(essentialCard);

        LinearLayout boundAppCard = AppUi.createCard(this);
        boundAppCard.addView(AppUi.sectionTitle(this, getString(R.string.settings_boundapp_title)));
        boundAppCard.addView(AppUi.body(this, getString(R.string.settings_boundapp_desc)));
        Button manageBoundAppsButton = AppUi.primaryButton(this, getString(R.string.settings_manage_boundapps));
        manageBoundAppsButton.setOnClickListener(v ->
                startActivity(new Intent(this, BoundAppsActivity.class)));
        boundAppCard.addView(manageBoundAppsButton);
        root.addView(boundAppCard);

        statusText = AppUi.body(this, "");

        statusText.setPadding(0, AppUi.dp(this, 12), 0, 0);

        root.addView(statusText);



        LinearLayout accountCard = AppUi.createCard(this);

        accountCard.addView(AppUi.sectionTitle(this, getString(R.string.settings_account)));

        Button profileButton = AppUi.secondaryButton(this, getString(R.string.settings_profile_password));

        profileButton.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        accountCard.addView(profileButton);

        root.addView(accountCard);



        return scrollView;

    }



    private Button settingsButton(String label, Runnable action) {

        Button button = AppUi.secondaryButton(this, label);

        button.setOnClickListener(v -> {

            try {

                action.run();

            } catch (Exception ignored) {

                toast(getString(R.string.settings_cannot_open));

            }

        });

        return button;

    }



    private Button oemButton(String label, OemLauncher launcher) {

        Button button = AppUi.secondaryButton(this, label);

        button.setOnClickListener(v -> {

            try {

                OemSettingsLauncher.LaunchResult result = launcher.open(this);

                if (result.hint != null && !result.hint.isEmpty()) {

                    toast(result.hint);

                }

            } catch (Exception ignored) {

                toast(getString(R.string.settings_cannot_open));

            }

        });

        return button;

    }



    private void onPauseChanged(CompoundButton button, boolean isChecked) {

        if (!button.isPressed()) {

            return;

        }

        Prefs.setPaused(this, isChecked);

        UnlockListenRegistrar.ensureRegistered(this);

        setStatus(getString(isChecked ? R.string.settings_paused_done : R.string.settings_resumed_done));

    }



    private void requestNotifications() {

        if (Build.VERSION.SDK_INT >= 33) {

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);

                return;

            }

        }

        SystemSettingsLauncher.openAppNotificationSettings(this);

    }



    private void refreshState() {

        pauseSwitch.setOnCheckedChangeListener(null);

        pauseSwitch.setChecked(Prefs.isPaused(this));

        pauseSwitch.setOnCheckedChangeListener(this::onPauseChanged);



        StringBuilder status = new StringBuilder();

        status.append(getString(R.string.settings_status_notif_perm))
                .append(getString(hasNotificationPermission()
                        ? R.string.common_allowed : R.string.settings_notif_not_allowed));

        status.append("\n").append(getString(R.string.settings_status_autorecord))
                .append(getString(Prefs.isPaused(this) ? R.string.common_paused : R.string.common_running));

        status.append("\n").append(BatteryOptimizationStatus.summaryLine(this));
        status.append("\n").append(BoundAppMonitor.statusLine(this));

        statusText.setText(status.toString());

    }



    private boolean hasNotificationPermission() {

        return Build.VERSION.SDK_INT < 33

                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

    }



    private void setStatus(String value) {

        statusText.setText(value);

    }



    private void toast(String value) {

        Toast.makeText(this, value, Toast.LENGTH_LONG).show();

    }



    private interface OemLauncher {

        OemSettingsLauncher.LaunchResult open(android.content.Context context);

    }

}


