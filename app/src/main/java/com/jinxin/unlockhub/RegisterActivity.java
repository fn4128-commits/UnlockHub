package com.jinxin.unlockhub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.TimeFormat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RegisterActivity extends com.jinxin.unlockhub.ui.BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText nicknameInput;
    private EditText passwordInput;
    private EditText loginNicknameInput;
    private EditText loginPasswordInput;
    private EditText backendInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        if (Prefs.hasSavedSession(this)) {
            openHome();
            return;
        }
        NotificationHelper.ensureChannels(this);
        setContentView(buildContent());
        backendInput.setText(Prefs.backendUrl(this));
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

        root.addView(AppUi.createBrandHeader(this, getString(R.string.reg_header)));

        statusText = AppUi.body(this, "");
        statusText.setPadding(0, 0, 0, AppUi.dp(this, 12));
        root.addView(statusText);

        LinearLayout registerCard = AppUi.createCard(this);
        registerCard.addView(AppUi.sectionTitle(this, getString(R.string.reg_title)));
        backendInput = AppUi.input(this, "https://safeping.unlockhub.workers.dev");
        nicknameInput = AppUi.input(this, getString(R.string.reg_hint_nickname));
        passwordInput = AppUi.input(this, getString(R.string.reg_hint_password));
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        loginNicknameInput = AppUi.input(this, getString(R.string.reg_hint_nickname));
        registerCard.addView(AppUi.label(this, getString(R.string.reg_label_backend)));
        registerCard.addView(backendInput);
        registerCard.addView(AppUi.label(this, getString(R.string.reg_label_nickname)));
        registerCard.addView(nicknameInput);
        registerCard.addView(AppUi.label(this, getString(R.string.reg_label_password)));
        registerCard.addView(passwordInput);
        Button registerButton = AppUi.primaryButton(this, getString(R.string.reg_button));
        registerButton.setOnClickListener(v -> registerAccount());
        registerCard.addView(registerButton);
        root.addView(registerCard);

        LinearLayout loginCard = AppUi.createCard(this);
        loginCard.addView(AppUi.sectionTitle(this, getString(R.string.reg_login_title)));
        loginCard.addView(AppUi.label(this, getString(R.string.reg_label_nickname2)));
        loginCard.addView(loginNicknameInput);
        loginPasswordInput = AppUi.input(this, getString(R.string.reg_hint_password));
        loginPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        loginCard.addView(AppUi.label(this, getString(R.string.reg_label_password)));
        loginCard.addView(loginPasswordInput);
        Button loginButton = AppUi.secondaryButton(this, getString(R.string.reg_login_button));
        loginButton.setOnClickListener(v -> loginAccount());
        loginCard.addView(loginButton);
        root.addView(loginCard);

        return scrollView;
    }

    private void registerAccount() {
        saveBackend();
        String nickname = nicknameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (nickname.isEmpty() || password.length() < 6) {
            setStatus(getString(R.string.reg_invalid));
            return;
        }
        executor.execute(() -> {
            try {
                ApiClient.Account account = new ApiClient(this).registerAccount(nickname, password);
                bindAccount(account, password);
                runOnUiThread(() -> {
                    com.jinxin.unlockhub.util.ShareActions.copyShareGuide(RegisterActivity.this);
                    setStatus(getString(R.string.reg_done, account.publicId));
                    openHome();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setStatus(getString(R.string.reg_fail, e.getMessage())));
            }
        });
    }

    private void loginAccount() {
        saveBackend();
        String nickname = loginNicknameInput.getText().toString().trim();
        String password = loginPasswordInput.getText().toString();
        if (nickname.isEmpty() || password.isEmpty()) {
            setStatus(getString(R.string.reg_need_both));
            return;
        }
        executor.execute(() -> {
            try {
                ApiClient.Account account = new ApiClient(this).loginAccount(nickname, password);
                bindAccount(account, password);
                runOnUiThread(this::openHome);
            } catch (Exception e) {
                runOnUiThread(() -> setStatus(getString(R.string.reg_login_fail, e.getMessage())));
            }
        });
    }

    private void bindAccount(ApiClient.Account account, String password) {
        Prefs.setPublicId(this, account.publicId);
        Prefs.setGuardianHandle(this, account.publicId);
        Prefs.setReceiverAccessKey(this, password);
        Prefs.setDisplayName(this, account.nickname.isEmpty() ? nicknameInput.getText().toString().trim() : account.nickname);
        Prefs.setAccountBound(this, true);
        if (Prefs.syncAnchorDate(this).isEmpty()) {
            Prefs.setSyncAnchorDate(this, TimeFormat.currentWeekStartDate());
        }
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void saveBackend() {
        Prefs.setBackendUrl(this, backendInput.getText().toString());
    }

    private void setStatus(String value) {
        statusText.setText(value);
    }

}
