package com.jinxin.unlockhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jinxin.unlockhub.network.ApiClient;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.util.Prefs;
import com.jinxin.unlockhub.util.ShareActions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ViewersActivity extends com.jinxin.unlockhub.ui.BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout listContainer;
    private TextView statusText;

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
        refreshList();
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

        root.addView(AppUi.createBrandHeader(this, getString(R.string.vw_header)));

        LinearLayout card = AppUi.createCard(this);
        card.addView(AppUi.sectionTitle(this, getString(R.string.vw_title)));
        card.addView(AppUi.body(this, getString(R.string.vw_desc)));
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(listContainer);
        Button addButton = AppUi.primaryButton(this, getString(R.string.vw_add));
        addButton.setOnClickListener(v -> promptNickname(-1, ""));
        card.addView(addButton);
        root.addView(card);

        statusText = AppUi.body(this, "");
        statusText.setPadding(0, AppUi.dp(this, 12), 0, 0);
        root.addView(statusText);

        return scrollView;
    }

    private void refreshList() {
        executor.execute(() -> {
            try {
                String json = new ApiClient(this).listViewersJson();
                JSONArray viewers = new JSONObject(json).getJSONArray("viewers");
                runOnUiThread(() -> renderList(viewers));
            } catch (Exception e) {
                runOnUiThread(() -> setStatus(getString(R.string.vw_load_fail, e.getMessage())));
            }
        });
    }

    private void renderList(JSONArray viewers) {
        listContainer.removeAllViews();
        if (viewers.length() == 0) {
            listContainer.addView(AppUi.body(this, getString(R.string.vw_empty)));
            return;
        }
        for (int i = 0; i < viewers.length(); i++) {
            try {
                JSONObject viewer = viewers.getJSONObject(i);
                long id = viewer.getLong("id");
                String nickname = viewer.getString("viewer_nickname");
                String uid = viewer.optString("viewer_public_id", "");
                listContainer.addView(createViewerRow(id, nickname, uid));
            } catch (Exception ignored) {
            }
        }
    }

    private LinearLayout createViewerRow(long id, String nickname, String uid) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AppUi.dp(this, 10), 0, AppUi.dp(this, 10));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = AppUi.body(this, nickname);
        textWrap.addView(nameView);
        if (uid == null || uid.isEmpty()) {
            TextView uidHint = AppUi.body(this, getString(R.string.vw_uid_none));
            uidHint.setTextSize(13f);
            textWrap.addView(uidHint);
        } else {
            TextView uidView = AppUi.body(this, "UID：" + uid);
            uidView.setTextSize(13f);
            textWrap.addView(uidView);
        }
        row.addView(textWrap);

        TextView menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(22f);
        menuButton.setTextColor(AppUi.themeColor(this, R.attr.appTextSecondary));
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setContentDescription(getString(R.string.vw_more));
        menuButton.setPadding(AppUi.dp(this, 12), AppUi.dp(this, 4), AppUi.dp(this, 4), AppUi.dp(this, 4));
        menuButton.setOnClickListener(v -> showViewerMenu(menuButton, id, nickname, uid));
        row.addView(menuButton);

        return row;
    }

    private void showViewerMenu(TextView anchor, long viewerId, String nickname, String uid) {
        PopupMenu popup = new PopupMenu(this, anchor);
        if (uid != null && !uid.isEmpty()) {
            popup.getMenu().add(0, 3, 0, getString(R.string.vw_copy_uid));
        }
        popup.getMenu().add(0, 1, 1, getString(R.string.vw_edit));
        popup.getMenu().add(0, 2, 2, getString(R.string.common_delete));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 3) {
                ShareActions.copy(this, getString(R.string.vw_uid_label), uid);
                toast(getString(R.string.vw_uid_copied));
                return true;
            }
            if (item.getItemId() == 1) {
                promptNickname(viewerId, nickname);
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDelete(viewerId, nickname);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void promptNickname(long viewerId, String currentNickname) {
        EditText input = AppUi.input(this, getString(R.string.vw_hint_nickname));
        input.setText(currentNickname);
        new AlertDialog.Builder(this)
                .setTitle(viewerId < 0 ? getString(R.string.vw_add_title) : getString(R.string.vw_edit_title))
                .setView(input)
                .setPositiveButton(getString(R.string.common_save), (dialog, which) -> {
                    String nickname = input.getText().toString().trim();
                    if (nickname.isEmpty()) {
                        toast(getString(R.string.vw_empty_nickname));
                        return;
                    }
                    saveNickname(viewerId, nickname);
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void saveNickname(long viewerId, String nickname) {
        executor.execute(() -> {
            try {
                ApiClient api = new ApiClient(this);
                if (viewerId < 0) {
                    api.addViewer(nickname);
                } else {
                    api.updateViewer(viewerId, nickname);
                }
                runOnUiThread(() -> {
                    toast(getString(R.string.vw_saved));
                    refreshList();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(getString(R.string.vw_save_fail, e.getMessage())));
            }
        });
    }

    private void confirmDelete(long viewerId, String nickname) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.vw_del_title, nickname))
                .setMessage(getString(R.string.vw_del_msg))
                .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> executor.execute(() -> {
                    try {
                        new ApiClient(this).deleteViewer(viewerId);
                        runOnUiThread(() -> {
                            toast(getString(R.string.vw_deleted));
                            refreshList();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> toast(getString(R.string.vw_del_fail, e.getMessage())));
                    }
                }))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void setStatus(String value) {
        statusText.setText(value);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
