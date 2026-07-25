package com.jinxin.unlockhub;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.util.BoundAppCatalog;
import com.jinxin.unlockhub.util.BoundAppMonitor;
import com.jinxin.unlockhub.util.Prefs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BoundAppsActivity extends com.jinxin.unlockhub.ui.BaseActivity {
    private static final int GRID_COLUMNS = 4;

    private LinearLayout gridContainer;
    private TextView summaryText;
    private TextView statusText;
    private EditText searchInput;

    private Set<String> selectedPackages = new HashSet<>();
    private List<BoundAppCatalog.Entry> allApps = List.of();
    private boolean renderingGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        if (!Prefs.hasSavedSession(this)) {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
            return;
        }
        selectedPackages = new HashSet<>(Prefs.boundAppPackages(this));
        setContentView(buildContent());
        reloadCatalog();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
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

        Button backButton = AppUi.secondaryButton(this, getString(R.string.ba_back));
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton);

        root.addView(AppUi.createBrandHeader(this,
                getString(R.string.ba_header)));

        LinearLayout card = AppUi.createCard(this);
        card.addView(AppUi.sectionTitle(this, getString(R.string.ba_title)));

        searchInput = AppUi.input(this, getString(R.string.ba_search));
        searchInput.addTextChangedListener(new SimpleTextWatcher(this::renderGrid));
        card.addView(searchInput);

        summaryText = AppUi.body(this, "");
        card.addView(summaryText);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button selectVisibleButton = AppUi.secondaryButton(this, getString(R.string.ba_select_visible));
        selectVisibleButton.setOnClickListener(v -> setVisibleSelection(true));
        Button clearVisibleButton = AppUi.secondaryButton(this, getString(R.string.ba_clear_visible));
        clearVisibleButton.setOnClickListener(v -> setVisibleSelection(false));
        actionRow.addView(selectVisibleButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionRow.addView(clearVisibleButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(actionRow);

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(gridContainer);

        root.addView(card);

        statusText = AppUi.body(this, "");
        statusText.setPadding(0, AppUi.dp(this, 12), 0, 0);
        root.addView(statusText);

        return scrollView;
    }

    private void reloadCatalog() {
        allApps = BoundAppCatalog.loadLaunchableApps(this);
        renderGrid();
    }

    private void renderGrid() {
        gridContainer.removeAllViews();
        List<BoundAppCatalog.Entry> visible = BoundAppCatalog.filter(
                allApps,
                searchInput == null ? "" : searchInput.getText().toString(),
                selectedPackages
        );
        updateSummary(visible.size());

        if (visible.isEmpty()) {
            gridContainer.addView(AppUi.body(this, getString(R.string.ba_no_match)));
            return;
        }

        renderingGrid = true;
        PackageManager packageManager = getPackageManager();
        LinearLayout currentRow = null;
        for (int i = 0; i < visible.size(); i++) {
            if (i % GRID_COLUMNS == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                gridContainer.addView(currentRow);
            }
            BoundAppCatalog.Entry entry = visible.get(i);
            currentRow.addView(
                    createAppCell(entry, packageManager),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            );
        }
        if (currentRow != null) {
            int remainder = visible.size() % GRID_COLUMNS;
            if (remainder != 0) {
                for (int i = remainder; i < GRID_COLUMNS; i++) {
                    currentRow.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
                }
            }
        }
        renderingGrid = false;
    }

    private LinearLayout createAppCell(BoundAppCatalog.Entry entry, PackageManager packageManager) {
        boolean selected = selectedPackages.contains(entry.packageName);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = AppUi.dp(this, 6);
        cell.setPadding(pad, pad, pad, pad);
        applyCellSelectionStyle(cell, selected);

        int iconSize = AppUi.dp(this, 56);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(entry.applicationInfo.loadIcon(packageManager));
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cell.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView nameView = new TextView(this);
        nameView.setText(entry.label);
        nameView.setTextSize(11f);
        nameView.setTextColor(AppUi.themeColor(this, R.attr.appTextPrimary));
        nameView.setGravity(Gravity.CENTER);
        nameView.setMaxLines(2);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setPadding(0, AppUi.dp(this, 4), 0, 0);
        cell.addView(nameView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView badgeView = new TextView(this);
        badgeView.setText(selected ? getString(R.string.ba_selected) : "");
        badgeView.setTextSize(10f);
        badgeView.setTypeface(Typeface.DEFAULT_BOLD);
        badgeView.setTextColor(getColor(R.color.accent));
        badgeView.setGravity(Gravity.CENTER);
        cell.addView(badgeView);

        cell.setOnClickListener(v -> {
            if (renderingGrid) {
                return;
            }
            toggleSelection(entry.packageName);
        });
        return cell;
    }

    private void toggleSelection(String packageName) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName);
        } else {
            selectedPackages.add(packageName);
        }
        Prefs.setBoundAppPackages(this, selectedPackages);
        renderGrid();
        refreshStatus();
    }

    private void applyCellSelectionStyle(LinearLayout cell, boolean selected) {
        if (selected) {
            cell.setBackgroundColor(AppUi.themeColor(this, R.attr.appAccentSoft));
        } else {
            cell.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void setVisibleSelection(boolean selected) {
        List<BoundAppCatalog.Entry> visible = BoundAppCatalog.filter(
                allApps,
                searchInput.getText().toString(),
                selectedPackages
        );
        for (BoundAppCatalog.Entry entry : visible) {
            if (selected) {
                selectedPackages.add(entry.packageName);
            } else {
                selectedPackages.remove(entry.packageName);
            }
        }
        Prefs.setBoundAppPackages(this, selectedPackages);
        renderGrid();
        refreshStatus();
    }

    private void updateSummary(int visibleCount) {
        summaryText.setText(getString(R.string.ba_summary, selectedPackages.size(), visibleCount, allApps.size()));
    }

    private void refreshStatus() {
        statusText.setText(BoundAppMonitor.statusLine(this) + getString(R.string.ba_note));
    }

    private interface TextChangedCallback {
        void onChanged();
    }

    private static final class SimpleTextWatcher implements TextWatcher {
        private final TextChangedCallback callback;

        private SimpleTextWatcher(TextChangedCallback callback) {
            this.callback = callback;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            callback.onChanged();
        }
    }
}
