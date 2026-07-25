package com.jinxin.unlockhub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jinxin.unlockhub.data.UnlockRepository;
import com.jinxin.unlockhub.sync.UnlockSync;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.TimeFormat;

public final class MainActivity extends BaseActivity {
    private TextView statusLine;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        UnlockSync.syncPendingSilently(this);
        // 确保后台守护/兜底在运行（记录由后台 Job + UsageStats 回查完成，打开 App 只查看不写库）。
        com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
        refreshStatus();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        AppUi.styleScreenBackground(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(AppUi.themeDrawable(this, R.attr.appStickyDrawable));
        header.setPadding(AppUi.dp(this, 18), AppUi.dp(this, 20), AppUi.dp(this, 18), AppUi.dp(this, 20));
        header.setElevation(AppUi.dp(this, 4));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.addView(AppUi.title(this, "UnlockHub"));
        TextView subtitle = AppUi.body(this, getString(R.string.hub_subtitle));
        subtitle.setPadding(0, AppUi.dp(this, 4), 0, 0);
        titleWrap.addView(subtitle);
        header.addView(titleWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(AppUi.createThemeToggleButton(this));

        // 右上角设置入口：符合直觉，点齿轮直接进设置。
        View settingsView = AppUi.createSettingsButton(this);
        settingsView.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settingsView);

        View avatarView = AppUi.createAvatarButton(this);
        avatarView.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        header.addView(avatarView);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        AppUi.styleScroll(scrollView);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AppUi.dp(this, 18), AppUi.dp(this, 20), AppUi.dp(this, 18), AppUi.dp(this, 28));
        scrollView.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout statusCard = AppUi.createCard(this);
        statusCard.addView(AppUi.sectionTitle(this, getString(R.string.main_status_overview)));
        statusLine = AppUi.body(this, "");
        statusCard.addView(statusLine);
        content.addView(statusCard);

        content.addView(AppUi.sectionTitle(this, getString(R.string.main_modules)));
        content.addView(createModuleCard(
                getString(R.string.module_safeping_title),
                getString(R.string.module_safeping_desc),
                R.color.module_safeping,
                SafePingActivity.class
        ));
        content.addView(createModuleCard(
                getString(R.string.module_memo_title),
                getString(R.string.module_memo_desc),
                R.color.module_memo,
                MemoActivity.class
        ));
        content.addView(createModuleCard(
                getString(R.string.module_routine_title),
                getString(R.string.module_routine_desc),
                R.color.module_routine,
                RoutinesActivity.class
        ));

        TextView versionText = AppUi.body(this, "UnlockHub v" + appVersionName());
        versionText.setGravity(Gravity.CENTER_HORIZONTAL);
        versionText.setPadding(0, AppUi.dp(this, 16), 0, 0);
        versionText.setOnLongClickListener(v -> {
            String crash = com.jinxin.unlockhub.util.CrashLogger.readLast(this);
            new android.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_crash_log_title))
                    .setMessage(crash.isEmpty() ? getString(R.string.main_no_crash) : crash)
                    .setPositiveButton(getString(R.string.common_close), null)
                    .show();
            return true;
        });
        content.addView(versionText);

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

    private View createModuleCard(String title, String description, int accentColorRes, Class<?> target) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_module_card);
        card.setPadding(AppUi.dp(this, 18), AppUi.dp(this, 18), AppUi.dp(this, 18), AppUi.dp(this, 18));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, AppUi.dp(this, 14));
        card.setLayoutParams(params);

        View accentBar = new View(this);
        accentBar.setBackgroundColor(getColor(accentColorRes));
        card.addView(accentBar, new LinearLayout.LayoutParams(
                AppUi.dp(this, 4),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setPadding(AppUi.dp(this, 14), 0, 0, 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        textWrap.addView(titleView);

        TextView descView = AppUi.body(this, description);
        textWrap.addView(descView);

        TextView action = new TextView(this);
        action.setText(getString(R.string.common_enter_arrow));
        action.setTextColor(getColor(accentColorRes));
        action.setTextSize(15);
        action.setTypeface(action.getTypeface(), Typeface.BOLD);
        action.setPadding(0, AppUi.dp(this, 10), 0, 0);
        textWrap.addView(action);

        card.addView(textWrap, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        card.setOnClickListener(v -> startActivity(new Intent(this, target)));
        return card;
    }

    private void refreshStatus() {
        if (statusLine == null) {
            return;
        }
        UnlockRepository repository = new UnlockRepository(this);
        UnlockRepository.Stats stats = repository.stats();
        String today = TimeFormat.localDate(System.currentTimeMillis());
        String recordState = repository.findByDate(today) == null
                ? getString(R.string.main_today_not_recorded)
                : getString(R.string.main_today_recorded);
        String pauseState = Prefs.isPaused(this)
                ? getString(R.string.main_auto_paused)
                : getString(R.string.main_auto_running);
        statusLine.setText(getString(R.string.main_status_line,
                Prefs.displayName(this), stats.total, UnlockRepository.MAX_LOCAL_RECORDS,
                pauseState, recordState));
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "0.6.0";
        }
    }
}
