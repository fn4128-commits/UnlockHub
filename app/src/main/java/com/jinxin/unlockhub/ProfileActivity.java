package com.jinxin.unlockhub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.ShareActions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfileActivity extends com.jinxin.unlockhub.ui.BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView nicknameText;
    private TextView uidText;
    private TextView statusText;
    private EditText currentPasswordInput;
    private EditText newPasswordInput;
    private EditText confirmPasswordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        if (!Prefs.hasSavedSession(this)) {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
            return;
        }
        setContentView(buildContent());
        refreshProfile();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
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

        Button backButton = AppUi.secondaryButton(this, getString(R.string.pf_back));
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton);

        root.addView(AppUi.createBrandHeader(this, getString(R.string.pf_header)));

        LinearLayout profileCard = AppUi.createCard(this);
        profileCard.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.addView(createAvatarView());
        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        profileText.setPadding(AppUi.dp(this, 14), 0, 0, 0);
        nicknameText = AppUi.title(this, "");
        nicknameText.setTextSize(20);
        uidText = AppUi.body(this, "");
        profileText.addView(nicknameText);
        profileText.addView(uidText);
        profileCard.addView(profileText);
        root.addView(profileCard);

        LinearLayout passwordCard = AppUi.createCard(this);
        passwordCard.addView(AppUi.sectionTitle(this, getString(R.string.pf_reset_pw)));
        currentPasswordInput = passwordInput(getString(R.string.pf_cur_pw));
        newPasswordInput = passwordInput(getString(R.string.pf_new_pw_hint));
        confirmPasswordInput = passwordInput(getString(R.string.pf_confirm_pw));
        passwordCard.addView(AppUi.label(this, getString(R.string.pf_cur_pw)));
        passwordCard.addView(currentPasswordInput);
        passwordCard.addView(AppUi.label(this, getString(R.string.pf_new_pw)));
        passwordCard.addView(newPasswordInput);
        passwordCard.addView(AppUi.label(this, getString(R.string.pf_confirm_pw)));
        passwordCard.addView(confirmPasswordInput);
        Button savePasswordButton = AppUi.primaryButton(this, getString(R.string.pf_save_pw));
        savePasswordButton.setOnClickListener(v -> changePassword());
        passwordCard.addView(savePasswordButton);
        root.addView(passwordCard);

        LinearLayout actionsCard = AppUi.createCard(this);
        actionsCard.addView(AppUi.sectionTitle(this, getString(R.string.pf_quick)));
        Button copyUidButton = AppUi.secondaryButton(this, getString(R.string.pf_copy_uid));
        copyUidButton.setOnClickListener(v -> {
            ShareActions.copyUid(this);
            toast(getString(R.string.pf_copied_uid));
        });
        actionsCard.addView(copyUidButton);
        Button copyLinkButton = AppUi.secondaryButton(this, getString(R.string.pf_copy_link));
        copyLinkButton.setOnClickListener(v -> {
            ShareActions.copyStatusLink(this);
            toast(getString(R.string.pf_copied_link));
        });
        actionsCard.addView(copyLinkButton);
        Button copyGuideButton = AppUi.secondaryButton(this, getString(R.string.pf_copy_guide));
        copyGuideButton.setOnClickListener(v -> {
            ShareActions.copyShareGuide(this);
            toast(getString(R.string.pf_copied_guide));
        });
        actionsCard.addView(copyGuideButton);
        Button shareButton = AppUi.primaryButton(this, getString(R.string.pf_share));
        shareButton.setOnClickListener(v -> ShareActions.shareGuide(this));
        actionsCard.addView(shareButton);
        Button openWebButton = AppUi.secondaryButton(this, getString(R.string.pf_open_web));
        openWebButton.setOnClickListener(v -> ShareActions.openInBrowser(this, ShareActions.statusPageUrl(this)));
        actionsCard.addView(openWebButton);
        root.addView(actionsCard);

        LinearLayout settingsCard = AppUi.createCard(this);
        settingsCard.addView(AppUi.sectionTitle(this, getString(R.string.pf_app_settings)));
        settingsCard.addView(AppUi.body(this, getString(R.string.pf_app_settings_desc)));
        Button openSettingsButton = AppUi.primaryButton(this, getString(R.string.pf_open_settings));
        openSettingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        settingsCard.addView(openSettingsButton);
        root.addView(settingsCard);

        Button logoutButton = AppUi.secondaryButton(this, getString(R.string.pf_logout));
        logoutButton.setOnClickListener(v -> logout());
        root.addView(logoutButton);

        statusText = AppUi.body(this, "");
        statusText.setPadding(0, AppUi.dp(this, 12), 0, 0);
        root.addView(statusText);

        return scrollView;
    }

    private void refreshProfile() {
        nicknameText.setText(Prefs.displayName(this));
        uidText.setText("UID: " + Prefs.publicId(this));
        currentPasswordInput.setText(Prefs.receiverAccessKey(this));
    }

    private void changePassword() {
        String currentPassword = currentPasswordInput.getText().toString();
        String newPassword = newPasswordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();
        if (currentPassword.isEmpty() || newPassword.length() < 6) {
            setStatus(getString(R.string.pf_pw_invalid));
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            setStatus(getString(R.string.pf_pw_mismatch));
            return;
        }
        String publicId = Prefs.publicId(this);
        executor.execute(() -> {
            try {
                new ApiClient(this).changePassword(publicId, currentPassword, newPassword);
                Prefs.setReceiverAccessKey(this, newPassword);
                runOnUiThread(() -> {
                    newPasswordInput.setText("");
                    confirmPasswordInput.setText("");
                    currentPasswordInput.setText(newPassword);
                    setStatus(getString(R.string.pf_pw_updated));
                });
            } catch (Exception e) {
                runOnUiThread(() -> setStatus(getString(R.string.pf_pw_fail, e.getMessage())));
            }
        });
    }

    private void logout() {
        Prefs.clearAccount(this);
        startActivity(new Intent(this, RegisterActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void setStatus(String value) {
        statusText.setText(value);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private View createAvatarView() {
        LinearLayout wrap = new LinearLayout(this);
        int size = AppUi.dp(this, 72);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setGravity(android.view.Gravity.CENTER);
        wrap.setBackgroundResource(com.jinxin.unlockhub.R.drawable.bg_avatar_ring);
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.jinxin.unlockhub.R.drawable.ic_brand_mark);
        int iconSize = AppUi.dp(this, 36);
        wrap.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        return wrap;
    }

    private EditText passwordInput(String hint) {
        EditText editText = AppUi.input(this, hint);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return editText;
    }
}
