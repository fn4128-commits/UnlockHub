package com.jinxin.unlockhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.data.RoutineRepository;
import com.jinxin.unlockhub.routine.PlaceProximityScheduler;
import com.jinxin.unlockhub.routine.RoutineAlarmScheduler;
import com.jinxin.unlockhub.ui.AppUi;
import com.jinxin.unlockhub.ui.BaseActivity;

import java.util.List;

public final class RoutinesActivity extends BaseActivity {
    private RoutineRepository repository;
    private LinearLayout listContainer;
    private TextView statusLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        repository = new RoutineRepository(this);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        try {
            RoutineAlarmScheduler.rescheduleAll(this);
            PlaceProximityScheduler.rescheduleAll(this);
            com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
        } catch (Throwable ignored) {
        }
    }

    private ScrollView buildContent() {
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

        content.addView(AppUi.createBrandHeader(this, getString(R.string.rts_header)));

        statusLine = AppUi.body(this, "");
        statusLine.setPadding(0, 0, 0, AppUi.dp(this, 8));
        content.addView(statusLine);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        Button addButton = AppUi.primaryButton(this, getString(R.string.rts_add));
        addButton.setOnClickListener(v -> startActivity(new Intent(this, RoutineEditActivity.class)));
        content.addView(addButton);

        LinearLayout tipCard = AppUi.createCard(this);
        tipCard.addView(AppUi.sectionTitle(this, getString(R.string.rts_tips_title)));
        tipCard.addView(AppUi.body(this, getString(R.string.rts_tips)));
        content.addView(tipCard);

        return scrollView;
    }

    private void refresh() {
        List<Routine> routines = repository.listAll();
        int enabledCount = 0;
        for (Routine routine : routines) {
            if (routine.enabled) {
                enabledCount++;
            }
        }
        statusLine.setText(routines.isEmpty()
                ? getString(R.string.rts_empty)
                : getString(R.string.rts_count, routines.size(), enabledCount));

        listContainer.removeAllViews();
        for (Routine routine : routines) {
            listContainer.addView(buildRoutineCard(routine));
        }
    }

    private LinearLayout buildRoutineCard(Routine routine) {
        LinearLayout card = AppUi.createCard(this);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(routine.name.isEmpty() ? getString(R.string.rts_unnamed) : routine.name);
        nameView.setTextSize(17);
        nameView.setTypeface(nameView.getTypeface(), Typeface.BOLD);
        nameView.setTextColor(AppUi.themeColor(this,
                routine.enabled ? R.attr.appTextPrimary : R.attr.appTextSecondary));
        headerRow.addView(nameView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch enabledSwitch = new Switch(this);
        AppUi.styleToggle(enabledSwitch);
        enabledSwitch.setChecked(routine.enabled);
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            repository.setEnabled(routine.id, checked);
            routine.enabled = checked;
            RoutineAlarmScheduler.schedule(this, routine);
            PlaceProximityScheduler.schedule(this, routine);
            com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
            refresh();
        });
        headerRow.addView(enabledSwitch);
        card.addView(headerRow);

        TextView conditionView = AppUi.body(this, getString(R.string.rt_if, routine.describeCondition(this)));
        conditionView.setPadding(0, AppUi.dp(this, 6), 0, 0);
        card.addView(conditionView);
        TextView actionView = AppUi.body(this, getString(R.string.rt_then, routine.describeAction(this)));
        card.addView(actionView);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, RoutineEditActivity.class);
            intent.putExtra("routineId", routine.id);
            startActivity(intent);
        });
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.rts_delete_title))
                    .setMessage(getString(R.string.rts_delete_msg,
                            routine.name.isEmpty() ? getString(R.string.rts_unnamed) : routine.name))
                    .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> {
                        RoutineAlarmScheduler.cancel(this, routine.id);
                        PlaceProximityScheduler.cancel(this, routine.id);
                        repository.delete(routine.id);
                        com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
                        refresh();
                    })
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show();
            return true;
        });
        return card;
    }
}
