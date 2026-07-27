package com.jinxin.unlockhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jinxin.unlockhub.data.Memo;
import com.jinxin.unlockhub.data.MemoRepository;
import com.jinxin.unlockhub.data.Routine;
import com.jinxin.unlockhub.data.RoutineRepository;
import com.jinxin.unlockhub.routine.ConnectionEventMonitor;
import com.jinxin.unlockhub.routine.PlaceProximityScheduler;
import com.jinxin.unlockhub.routine.RoutineAlarmScheduler;
import com.jinxin.unlockhub.ui.AppUi;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RoutineEditActivity extends com.jinxin.unlockhub.ui.BaseActivity {

    /** 分组选项：value 为空串时是占位（禁用）。 */
    private static final class Option {
        final String value;
        final String label;
        final boolean enabled;

        Option(String value, String label, boolean enabled) {
            this.value = value;
            this.label = label;
            this.enabled = enabled;
        }
    }

    private static final class Group {
        final String header;
        final List<Option> options;

        Group(String header, List<Option> options) {
            this.header = header;
            this.options = options;
        }
    }

    private static final String[] ACTION_TYPES = {
            Routine.ACTION_POPUP, Routine.ACTION_COUNTDOWN,
            Routine.ACTION_ACTIVATE_MEMO,
            Routine.ACTION_OPEN_APP, Routine.ACTION_DND_ON, Routine.ACTION_DND_OFF,
    };
    /** 与 ACTION_TYPES 一一对应的文案资源 id（静态上下文不能 getString，运行时再取）。 */
    private static final int[] ACTION_LABEL_RES = {
            R.string.red_a_popup, R.string.red_a_countdown,
            R.string.red_a_memo,
            R.string.red_a_open_app, R.string.red_a_dnd_on, R.string.red_a_dnd_off,
    };

    private String[] actionLabels() {
        String[] labels = new String[ACTION_LABEL_RES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(ACTION_LABEL_RES[i]);
        }
        return labels;
    }

    private RoutineRepository repository;
    private Routine routine;

    private EditText nameInput;
    private Button triggerButton;
    private LinearLayout triggerParamContainer;
    private Switch constraintSwitch;
    private LinearLayout constraintBody;
    private Button constraintButton;
    private LinearLayout constraintParamContainer;
    private Button actionButton;
    private LinearLayout actionParamContainer;

    // 触发参数
    private int timeHour = 8;
    private int timeMinute = 0;
    private int weekdayMask = 127;
    private String triggerAppPackage = "";
    private String triggerAppLabel = "";
    private EditText triggerWifiInput;
    private EditText triggerBtInput;
    private String triggerWifiSsid = "";
    private String triggerBtDevice = "";
    private double triggerPlaceLat = Double.NaN;
    private double triggerPlaceLng = Double.NaN;
    private String triggerPlaceLabel = "";
    private int triggerPlaceRadius = 300;
    private EditText triggerPlaceLabelInput;
    private EditText triggerPlaceRadiusInput;
    // 约束参数
    private int rangeStartHour = 8;
    private int rangeStartMinute = 0;
    private int rangeEndHour = 22;
    private int rangeEndMinute = 0;
    private EditText constraintWifiInput;
    private EditText constraintBtInput;
    private String constraintWifiSsid = "";
    private String constraintBtDevice = "";
    private double constraintPlaceLat = Double.NaN;
    private double constraintPlaceLng = Double.NaN;
    private String constraintPlaceLabel = "";
    private int constraintPlaceRadius = 300;
    private EditText constraintPlaceLabelInput;
    private EditText constraintPlaceRadiusInput;
    // 动作参数
    private EditText actionTextInput;
    private EditText actionMinutesInput;
    private long actionMemoId;
    private String actionMemoTitle = "";
    private String actionAppPackage = "";
    private String actionAppLabel = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.applyTheme(this);
        repository = new RoutineRepository(this);
        long routineId = getIntent().getLongExtra("routineId", 0L);
        routine = routineId > 0 ? repository.findById(routineId) : null;
        if (routine == null) {
            routine = new Routine();
            // 新建规则不默认填充：条件一与动作都由用户显式选择
            routine.triggerType = "";
            routine.actionType = "";
        } else {
            loadParams();
        }
        setContentView(buildContent());
        renderAll();
    }

    private void loadParams() {
        JSONObject trigger = routine.triggerJson();
        timeHour = trigger.optInt("hour", 8);
        timeMinute = trigger.optInt("minute", 0);
        weekdayMask = trigger.optInt("weekdays", 127);
        triggerAppPackage = trigger.optString("package", "");
        triggerAppLabel = trigger.optString("label", "");
        triggerWifiSsid = trigger.optString("ssid", "");
        triggerBtDevice = trigger.optString("device", "");
        if (Routine.TRIGGER_ENTER_PLACE.equals(routine.triggerType)) {
            triggerPlaceLat = trigger.optDouble("lat", Double.NaN);
            triggerPlaceLng = trigger.optDouble("lng", Double.NaN);
            triggerPlaceLabel = trigger.optString("label", "");
            triggerPlaceRadius = trigger.optInt("radius", 300);
        }
        JSONObject constraint = routine.constraintJson();
        rangeStartHour = constraint.optInt("startHour", 8);
        rangeStartMinute = constraint.optInt("startMinute", 0);
        rangeEndHour = constraint.optInt("endHour", 22);
        rangeEndMinute = constraint.optInt("endMinute", 0);
        constraintWifiSsid = constraint.optString("ssid", "");
        constraintBtDevice = constraint.optString("device", "");
        if (Routine.CONSTRAINT_PLACE.equals(routine.constraintType)) {
            constraintPlaceLat = constraint.optDouble("lat", Double.NaN);
            constraintPlaceLng = constraint.optDouble("lng", Double.NaN);
            constraintPlaceLabel = constraint.optString("label", "");
            constraintPlaceRadius = constraint.optInt("radius", 300);
        }
        JSONObject action = routine.actionJson();
        actionMemoId = action.optLong("memoId", 0);
        actionMemoTitle = action.optString("title", "");
        actionAppPackage = action.optString("package", "");
        actionAppLabel = action.optString("label", "");
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

        content.addView(AppUi.createBrandHeader(this, getString(routine.id > 0 ? R.string.red_title_edit : R.string.red_title_new)));

        LinearLayout nameCard = AppUi.createCard(this);
        nameCard.addView(AppUi.label(this, getString(R.string.red_name)));
        nameInput = AppUi.input(this, getString(R.string.red_name_hint));
        nameInput.setText(routine.name);
        nameCard.addView(nameInput);
        content.addView(nameCard);

        // 条件一
        LinearLayout triggerCard = AppUi.createCard(this);
        triggerCard.addView(AppUi.sectionTitle(this, getString(R.string.red_cond1)));
        triggerButton = AppUi.secondaryButton(this, "");
        triggerButton.setOnClickListener(v -> showGroupedPicker(getString(R.string.red_pick_trigger), triggerGroups(), value -> {
            routine.triggerType = value;
            // 条件一变化后，若条件二与之冲突则复位
            if (!isConstraintAllowed(routine.constraintType)) {
                routine.constraintType = Routine.CONSTRAINT_NONE;
                constraintSwitch.setChecked(false);
            }
            renderAll();
        }));
        triggerCard.addView(triggerButton);
        triggerParamContainer = new LinearLayout(this);
        triggerParamContainer.setOrientation(LinearLayout.VERTICAL);
        triggerCard.addView(triggerParamContainer);
        content.addView(triggerCard);

        // 条件二（默认关闭，开启后才可选）
        LinearLayout constraintCard = AppUi.createCard(this);
        LinearLayout constraintHeader = new LinearLayout(this);
        constraintHeader.setOrientation(LinearLayout.HORIZONTAL);
        constraintHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView constraintTitle = AppUi.sectionTitle(this, getString(R.string.red_cond2));
        constraintHeader.addView(constraintTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        constraintSwitch = new Switch(this);
        AppUi.styleToggle(constraintSwitch);
        constraintSwitch.setChecked(!routine.constraintType.isEmpty());
        constraintSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                routine.constraintType = Routine.CONSTRAINT_NONE;
            }
            renderAll();
        });
        constraintHeader.addView(constraintSwitch);
        constraintCard.addView(constraintHeader);

        constraintBody = new LinearLayout(this);
        constraintBody.setOrientation(LinearLayout.VERTICAL);
        constraintButton = AppUi.secondaryButton(this, "");
        constraintButton.setOnClickListener(v -> {
            if (constraintSwitch.isChecked()) {
                showGroupedPicker(getString(R.string.red_pick_constraint), constraintGroups(), value -> {
                    routine.constraintType = value;
                    renderAll();
                });
            }
        });
        constraintBody.addView(constraintButton);
        constraintParamContainer = new LinearLayout(this);
        constraintParamContainer.setOrientation(LinearLayout.VERTICAL);
        constraintBody.addView(constraintParamContainer);
        constraintCard.addView(constraintBody);
        content.addView(constraintCard);

        // 动作
        LinearLayout actionCard = AppUi.createCard(this);
        actionCard.addView(AppUi.sectionTitle(this, getString(R.string.red_action)));
        actionButton = AppUi.secondaryButton(this, "");
        actionButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.red_pick_action))
                .setSingleChoiceItems(actionLabels(),
                        routine.actionType.isEmpty() ? -1 : indexOf(ACTION_TYPES, routine.actionType),
                        (dialog, which) -> {
                    dialog.dismiss();
                    routine.actionType = ACTION_TYPES[which];
                    renderAll();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show());
        actionCard.addView(actionButton);
        actionParamContainer = new LinearLayout(this);
        actionParamContainer.setOrientation(LinearLayout.VERTICAL);
        actionCard.addView(actionParamContainer);
        content.addView(actionCard);

        Button saveButton = AppUi.primaryButton(this, getString(R.string.red_save));
        saveButton.setOnClickListener(v -> save());
        content.addView(saveButton);

        if (routine.id > 0) {
            Button deleteButton = AppUi.secondaryButton(this, getString(R.string.red_delete));
            deleteButton.setOnClickListener(v -> {
                RoutineAlarmScheduler.cancel(this, routine.id);
                PlaceProximityScheduler.cancel(this, routine.id);
                repository.delete(routine.id);
                com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
                finish();
            });
            content.addView(deleteButton);
        }

        return scrollView;
    }

    // ---------- 分组数据 ----------

    private List<Group> triggerGroups() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group(getString(R.string.red_g_time), list(
                new Option(Routine.TRIGGER_TIME, getString(R.string.red_t_time), true))));
        groups.add(new Group(getString(R.string.red_g_unlock), list(
                new Option(Routine.TRIGGER_FIRST_UNLOCK, getString(R.string.red_t_first_unlock), true),
                new Option(Routine.TRIGGER_ANY_UNLOCK, getString(R.string.red_t_any_unlock), true),
                new Option(Routine.TRIGGER_APP_OPEN, getString(R.string.red_t_app_open), true))));
        // 「应用启动」用「使用情况访问权限」检测前台应用（不再依赖无障碍服务）。
        groups.add(new Group(getString(R.string.red_g_power), list(
                new Option(Routine.TRIGGER_CHARGER_ON, getString(R.string.red_t_charger_on), true),
                new Option(Routine.TRIGGER_CHARGER_OFF, getString(R.string.red_t_charger_off), true),
                new Option(Routine.TRIGGER_BATTERY_LOW, getString(R.string.red_t_battery_low), true))));
        groups.add(new Group(getString(R.string.red_g_conn), list(
                new Option(Routine.TRIGGER_WIFI_CONNECTED, getString(R.string.red_t_wifi), true),
                new Option(Routine.TRIGGER_BT_CONNECTED, getString(R.string.red_t_bt), true))));
        groups.add(new Group(getString(R.string.red_g_place), list(
                new Option(Routine.TRIGGER_ENTER_PLACE, getString(R.string.red_t_place), true))));
        return groups;
    }

    /** 条件二选项：按条件一过滤掉冲突项。 */
    private List<Group> constraintGroups() {
        List<Group> groups = new ArrayList<>();
        List<Option> timeOptions = new ArrayList<>();
        if (isConstraintAllowed(Routine.CONSTRAINT_TIME_RANGE)) {
            timeOptions.add(new Option(Routine.CONSTRAINT_TIME_RANGE, getString(R.string.red_c_time_range), true));
        }
        if (!timeOptions.isEmpty()) {
            groups.add(new Group(getString(R.string.red_g_time), timeOptions));
        }
        List<Option> powerOptions = new ArrayList<>();
        if (isConstraintAllowed(Routine.CONSTRAINT_CHARGING)) {
            powerOptions.add(new Option(Routine.CONSTRAINT_CHARGING, getString(R.string.red_c_charging), true));
        }
        if (isConstraintAllowed(Routine.CONSTRAINT_NOT_CHARGING)) {
            powerOptions.add(new Option(Routine.CONSTRAINT_NOT_CHARGING, getString(R.string.red_c_not_charging), true));
        }
        if (!powerOptions.isEmpty()) {
            groups.add(new Group(getString(R.string.red_g_power), powerOptions));
        }
        List<Option> connectionOptions = new ArrayList<>();
        if (isConstraintAllowed(Routine.CONSTRAINT_WIFI)) {
            connectionOptions.add(new Option(Routine.CONSTRAINT_WIFI, getString(R.string.red_c_wifi), true));
        }
        if (isConstraintAllowed(Routine.CONSTRAINT_BT)) {
            connectionOptions.add(new Option(Routine.CONSTRAINT_BT, getString(R.string.red_c_bt), true));
        }
        if (!connectionOptions.isEmpty()) {
            groups.add(new Group(getString(R.string.red_g_conn), connectionOptions));
        }
        List<Option> placeOptions = new ArrayList<>();
        if (isConstraintAllowed(Routine.CONSTRAINT_PLACE)) {
            placeOptions.add(new Option(Routine.CONSTRAINT_PLACE, getString(R.string.red_c_place), true));
        }
        if (!placeOptions.isEmpty()) {
            groups.add(new Group(getString(R.string.red_g_place), placeOptions));
        }
        return groups;
    }

    /** 条件二与条件一的兼容矩阵。 */
    private boolean isConstraintAllowed(String constraintType) {
        if (constraintType == null || constraintType.isEmpty()) {
            return true;
        }
        String trigger = routine.triggerType;
        switch (constraintType) {
            case Routine.CONSTRAINT_TIME_RANGE:
                return !Routine.TRIGGER_TIME.equals(trigger);
            case Routine.CONSTRAINT_CHARGING:
            case Routine.CONSTRAINT_NOT_CHARGING:
                return !Routine.TRIGGER_CHARGER_ON.equals(trigger)
                        && !Routine.TRIGGER_CHARGER_OFF.equals(trigger);
            case Routine.CONSTRAINT_WIFI:
                return !Routine.TRIGGER_WIFI_CONNECTED.equals(trigger);
            case Routine.CONSTRAINT_BT:
                return !Routine.TRIGGER_BT_CONNECTED.equals(trigger);
            case Routine.CONSTRAINT_PLACE:
                return !Routine.TRIGGER_ENTER_PLACE.equals(trigger);
            default:
                return true;
        }
    }

    // ---------- 渲染 ----------

    private void renderAll() {
        triggerButton.setText(labelFor(triggerGroups(), routine.triggerType, getString(R.string.red_pick_trigger)));
        boolean constraintOn = constraintSwitch.isChecked();
        // 条件二关闭时整块降低不透明度并禁用
        constraintBody.setAlpha(constraintOn ? 1f : 0.35f);
        setEnabledDeep(constraintBody, constraintOn);
        constraintButton.setText(routine.constraintType.isEmpty()
                ? getString(R.string.red_pick_constraint)
                : labelFor(constraintGroups(), routine.constraintType, getString(R.string.red_pick_constraint)));
        actionButton.setText(routine.actionType.isEmpty()
                ? getString(R.string.red_pick_action)
                : actionLabels()[indexOf(ACTION_TYPES, routine.actionType)]);
        renderTriggerParams();
        renderConstraintParams();
        renderActionParams();
    }

    private static void setEnabledDeep(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledDeep(group.getChildAt(i), enabled);
            }
        }
    }

    private void renderTriggerParams() {
        triggerParamContainer.removeAllViews();
        triggerWifiInput = null;
        triggerBtInput = null;
        triggerPlaceLabelInput = null;
        triggerPlaceRadiusInput = null;
        switch (routine.triggerType) {
            case Routine.TRIGGER_TIME: {
                Button timeButton = AppUi.secondaryButton(this,
                        getString(R.string.red_time_at, two(timeHour), two(timeMinute)));
                timeButton.setOnClickListener(v -> new TimePickerDialog(this, R.style.ClockTimePicker, (view, hour, minute) -> {
                    timeHour = hour;
                    timeMinute = minute;
                    renderAll();
                }, timeHour, timeMinute, true).show());
                triggerParamContainer.addView(timeButton);
                triggerParamContainer.addView(AppUi.label(this, getString(R.string.red_repeat)));
                triggerParamContainer.addView(buildWeekdayRow());
                break;
            }
            case Routine.TRIGGER_APP_OPEN: {
                Button appButton = AppUi.secondaryButton(this,
                        triggerAppPackage.isEmpty() ? getString(R.string.red_pick_watch_app) : getString(R.string.red_app_is, triggerAppLabel));
                appButton.setOnClickListener(v -> pickApp((pkg, label) -> {
                    triggerAppPackage = pkg;
                    triggerAppLabel = label;
                    renderAll();
                }));
                triggerParamContainer.addView(appButton);
                addHint(triggerParamContainer, getString(R.string.red_hint_usage));
                break;
            }
            case Routine.TRIGGER_WIFI_CONNECTED: {
                triggerParamContainer.addView(AppUi.label(this, getString(R.string.red_wifi_label)));
                triggerWifiInput = AppUi.input(this, getString(R.string.red_wifi_hint));
                triggerWifiInput.setText(triggerWifiSsid);
                triggerParamContainer.addView(triggerWifiInput);
                Button fillWifiButton = AppUi.secondaryButton(this, getString(R.string.red_fill_wifi));
                final EditText wifiTarget = triggerWifiInput;
                fillWifiButton.setOnClickListener(v -> fillCurrentWifi(wifiTarget));
                triggerParamContainer.addView(fillWifiButton);
                addHint(triggerParamContainer, getString(R.string.red_hint_wifi));
                break;
            }
            case Routine.TRIGGER_BT_CONNECTED: {
                triggerParamContainer.addView(AppUi.label(this, getString(R.string.red_bt_label)));
                triggerBtInput = AppUi.input(this, getString(R.string.red_bt_hint));
                triggerBtInput.setText(triggerBtDevice);
                triggerParamContainer.addView(triggerBtInput);
                Button pickBtButton = AppUi.secondaryButton(this, getString(R.string.red_pick_paired));
                final EditText btTarget = triggerBtInput;
                pickBtButton.setOnClickListener(v -> pickBondedBtDevice(btTarget));
                triggerParamContainer.addView(pickBtButton);
                addHint(triggerParamContainer, getString(R.string.red_hint_bt));
                break;
            }
            case Routine.TRIGGER_ENTER_PLACE:
                addPlaceParams(triggerParamContainer, true);
                addHint(triggerParamContainer,
                        getString(R.string.red_hint_place));
                break;
            case Routine.TRIGGER_BATTERY_LOW:
                addHint(triggerParamContainer, getString(R.string.red_hint_battery));
                break;
            case Routine.TRIGGER_FIRST_UNLOCK:
                addHint(triggerParamContainer, getString(R.string.red_hint_first_unlock));
                break;
            case Routine.TRIGGER_ANY_UNLOCK:
                addHint(triggerParamContainer, getString(R.string.red_hint_any_unlock));
                break;
            default:
                break;
        }
    }

    private void renderConstraintParams() {
        constraintParamContainer.removeAllViews();
        constraintWifiInput = null;
        constraintBtInput = null;
        constraintPlaceLabelInput = null;
        constraintPlaceRadiusInput = null;
        if (!constraintSwitch.isChecked()) {
            return;
        }
        switch (routine.constraintType) {
            case Routine.CONSTRAINT_TIME_RANGE: {
                Button startButton = AppUi.secondaryButton(this,
                        getString(R.string.red_from, two(rangeStartHour), two(rangeStartMinute)));
                startButton.setOnClickListener(v -> new TimePickerDialog(this, R.style.ClockTimePicker, (view, hour, minute) -> {
                    rangeStartHour = hour;
                    rangeStartMinute = minute;
                    renderAll();
                }, rangeStartHour, rangeStartMinute, true).show());
                Button endButton = AppUi.secondaryButton(this,
                        getString(R.string.red_to, two(rangeEndHour), two(rangeEndMinute)));
                endButton.setOnClickListener(v -> new TimePickerDialog(this, R.style.ClockTimePicker, (view, hour, minute) -> {
                    rangeEndHour = hour;
                    rangeEndMinute = minute;
                    renderAll();
                }, rangeEndHour, rangeEndMinute, true).show());
                constraintParamContainer.addView(startButton);
                constraintParamContainer.addView(endButton);
                break;
            }
            case Routine.CONSTRAINT_WIFI: {
                constraintParamContainer.addView(AppUi.label(this, getString(R.string.red_wifi_label)));
                constraintWifiInput = AppUi.input(this, getString(R.string.red_wifi_hint));
                constraintWifiInput.setText(constraintWifiSsid);
                constraintParamContainer.addView(constraintWifiInput);
                Button fillWifiButton = AppUi.secondaryButton(this, getString(R.string.red_fill_wifi));
                final EditText wifiTarget = constraintWifiInput;
                fillWifiButton.setOnClickListener(v -> fillCurrentWifi(wifiTarget));
                constraintParamContainer.addView(fillWifiButton);
                break;
            }
            case Routine.CONSTRAINT_BT: {
                constraintParamContainer.addView(AppUi.label(this, getString(R.string.red_bt_label)));
                constraintBtInput = AppUi.input(this, getString(R.string.red_bt_hint2));
                constraintBtInput.setText(constraintBtDevice);
                constraintParamContainer.addView(constraintBtInput);
                Button pickBtButton = AppUi.secondaryButton(this, getString(R.string.red_pick_paired));
                final EditText btTarget = constraintBtInput;
                pickBtButton.setOnClickListener(v -> pickBondedBtDevice(btTarget));
                constraintParamContainer.addView(pickBtButton);
                break;
            }
            case Routine.CONSTRAINT_PLACE:
                addPlaceParams(constraintParamContainer, false);
                addHint(constraintParamContainer, getString(R.string.red_hint_place_con));
                break;
            default:
                break;
        }
    }

    /** 地点参数 UI：名称 + 记录当前位置 + 半径。触发与约束共用。 */
    private void addPlaceParams(LinearLayout container, boolean forTrigger) {
        double lat = forTrigger ? triggerPlaceLat : constraintPlaceLat;
        double lng = forTrigger ? triggerPlaceLng : constraintPlaceLng;
        String label = forTrigger ? triggerPlaceLabel : constraintPlaceLabel;
        int radius = forTrigger ? triggerPlaceRadius : constraintPlaceRadius;

        container.addView(AppUi.label(this, getString(R.string.red_place_name)));
        EditText labelInput = AppUi.input(this, getString(R.string.red_place_hint));
        labelInput.setText(label);
        container.addView(labelInput);

        boolean recorded = !Double.isNaN(lat) && !Double.isNaN(lng);
        Button locateButton = AppUi.secondaryButton(this, recorded
                ? getString(R.string.red_place_saved, lat, lng)
                : getString(R.string.red_place_record));
        locateButton.setOnClickListener(v -> captureCurrentLocation(forTrigger));
        container.addView(locateButton);

        container.addView(AppUi.label(this, getString(R.string.red_radius)));
        EditText radiusInput = AppUi.input(this, getString(R.string.red_radius_hint));
        radiusInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        radiusInput.setText(String.valueOf(radius));
        container.addView(radiusInput);

        if (forTrigger) {
            triggerPlaceLabelInput = labelInput;
            triggerPlaceRadiusInput = radiusInput;
        } else {
            constraintPlaceLabelInput = labelInput;
            constraintPlaceRadiusInput = radiusInput;
        }
    }

    private void renderActionParams() {
        actionParamContainer.removeAllViews();
        actionTextInput = null;
        actionMinutesInput = null;
        switch (routine.actionType) {
            case Routine.ACTION_COUNTDOWN: {
                actionParamContainer.addView(AppUi.label(this, getString(R.string.red_minutes)));
                actionMinutesInput = AppUi.input(this, getString(R.string.red_minutes_hint));
                actionMinutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                int minutes = routine.actionJson().optInt("minutes", 25);
                actionMinutesInput.setText(String.valueOf(minutes));
                actionParamContainer.addView(actionMinutesInput);
                actionParamContainer.addView(AppUi.label(this, getString(R.string.red_end_text)));
                actionTextInput = AppUi.input(this, getString(R.string.red_end_hint));
                actionTextInput.setText(routine.actionJson().optString("text", ""));
                actionParamContainer.addView(actionTextInput);
                addHint(actionParamContainer, getString(R.string.red_hint_countdown));
                break;
            }
            case Routine.ACTION_POPUP:
            case Routine.ACTION_NOTIFY: {
                String label = Routine.ACTION_POPUP.equals(routine.actionType) ? getString(R.string.red_popup_content) : getString(R.string.red_notify_content);
                actionParamContainer.addView(AppUi.label(this, label));
                actionTextInput = AppUi.input(this, getString(R.string.red_text_hint));
                actionTextInput.setText(routine.actionJson().optString("text", ""));
                actionParamContainer.addView(actionTextInput);
                if (Routine.ACTION_POPUP.equals(routine.actionType)) {
                    addHint(actionParamContainer, getString(R.string.red_hint_popup));
                }
                break;
            }
            case Routine.ACTION_ACTIVATE_MEMO: {
                Button memoButton = AppUi.secondaryButton(this,
                        actionMemoId > 0 ? getString(R.string.red_memo_is, actionMemoTitle) : getString(R.string.red_pick_memo));
                memoButton.setOnClickListener(v -> pickMemo());
                actionParamContainer.addView(memoButton);
                break;
            }
            case Routine.ACTION_OPEN_APP: {
                Button appButton = AppUi.secondaryButton(this,
                        actionAppPackage.isEmpty() ? getString(R.string.red_pick_open_app) : getString(R.string.red_app_is, actionAppLabel));
                appButton.setOnClickListener(v -> pickApp((pkg, label) -> {
                    actionAppPackage = pkg;
                    actionAppLabel = label;
                    renderAll();
                }));
                actionParamContainer.addView(appButton);
                break;
            }
            case Routine.ACTION_DND_ON:
            case Routine.ACTION_DND_OFF:
                addHint(actionParamContainer, getString(R.string.red_hint_dnd));
                break;
            default:
                break;
        }
    }

    private void addHint(LinearLayout container, String text) {
        TextView hint = AppUi.body(this, text);
        hint.setTextSize(13);
        container.addView(hint);
    }

    private LinearLayout buildWeekdayRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = getResources().getStringArray(R.array.memo_weekdays);
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView chip = AppUi.weekdayChip(this, names[i]);
            AppUi.styleWeekdayChip(chip, (weekdayMask & bit) != 0);
            chip.setOnClickListener(v -> {
                weekdayMask ^= bit;
                if (weekdayMask == 0) {
                    weekdayMask = bit;
                }
                renderAll();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(0, 0, i < 6 ? AppUi.dp(this, 4) : 0, 0);
            row.addView(chip, params);
        }
        return row;
    }

    // ---------- 分组选择器 ----------

    private interface ValuePickCallback {
        void onPick(String value);
    }

    private void showGroupedPicker(String title, List<Group> groups, ValuePickCallback callback) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = AppUi.dp(this, 16);
        list.setPadding(pad, AppUi.dp(this, 8), pad, pad);
        scrollView.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton(getString(R.string.common_cancel), null)
                .create();

        for (Group group : groups) {
            TextView header = new TextView(this);
            header.setText(group.header);
            header.setTextSize(13);
            header.setTypeface(header.getTypeface(), Typeface.BOLD);
            // 对话框是白底：分组标题用浅色表面的次级深色
            header.setTextColor(getColor(R.color.text_on_light_secondary));
            header.setPadding(0, AppUi.dp(this, 12), 0, AppUi.dp(this, 4));
            list.addView(header);

            for (Option option : group.options) {
                TextView row = new TextView(this);
                row.setText(option.label);
                row.setTextSize(16);
                row.setPadding(AppUi.dp(this, 8), AppUi.dp(this, 12), AppUi.dp(this, 8), AppUi.dp(this, 12));
                if (option.enabled) {
                    // 对话框是白底：选项文字用深色
                    row.setTextColor(getColor(R.color.text_on_light));
                    row.setClickable(true);
                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        callback.onPick(option.value);
                    });
                } else {
                    // 规划中的条件：降低不透明度，不可点
                    row.setTextColor(getColor(R.color.text_on_light_secondary));
                    row.setAlpha(0.5f);
                }
                list.addView(row);
            }
        }
        dialog.show();
    }

    private static String labelFor(List<Group> groups, String value, String fallback) {
        for (Group group : groups) {
            for (Option option : group.options) {
                if (option.value.equals(value) && !option.value.isEmpty()) {
                    return option.label;
                }
            }
        }
        return fallback;
    }

    // ---------- 应用 / 备忘选择 ----------

    private interface AppPickCallback {
        void onPick(String packageName, String label);
    }

    private void pickApp(AppPickCallback callback) {
        PackageManager packageManager = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(mainIntent, 0);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (!getPackageName().equals(info.activityInfo.packageName)) {
                apps.add(info);
            }
        }
        Collections.sort(apps, (a, b) ->
                String.valueOf(a.loadLabel(packageManager)).compareToIgnoreCase(String.valueOf(b.loadLabel(packageManager))));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = AppUi.dp(this, 8);
        list.setPadding(pad, pad, pad, pad);
        scrollView.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.red_pick_app_title))
                .setView(scrollView)
                .setNegativeButton(getString(R.string.common_cancel), null)
                .create();

        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            String label = String.valueOf(info.loadLabel(packageManager));
            Drawable icon = info.loadIcon(packageManager);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            row.setClickable(true);

            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(icon);
            int size = AppUi.dp(this, 36);
            row.addView(iconView, new LinearLayout.LayoutParams(size, size));

            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextSize(16);
            // 应用选择对话框是白底：文字用深色
            labelView.setTextColor(getColor(R.color.text_on_light));
            labelView.setPadding(AppUi.dp(this, 12), 0, 0, 0);
            row.addView(labelView);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onPick(pkg, label);
            });
            list.addView(row);
        }
        dialog.show();
    }

    private void pickMemo() {
        List<Memo> memos = new MemoRepository(this).listAll(false);
        if (memos.isEmpty()) {
            Toast.makeText(this, getString(R.string.red_no_memo), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[memos.size()];
        for (int i = 0; i < memos.size(); i++) {
            titles[i] = memos.get(i).title.isEmpty() ? getString(R.string.memo_untitled) : memos.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.red_pick_memo_title))
                .setItems(titles, (dialog, which) -> {
                    actionMemoId = memos.get(which).id;
                    actionMemoTitle = titles[which];
                    renderAll();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    // ---------- 位置 / Wi-Fi / 蓝牙辅助 ----------

    /** 把当前显示中的地点输入框内容写回状态（重建视图或保存前调用）。 */
    private void readPlaceInputs() {
        if (triggerPlaceLabelInput != null) {
            triggerPlaceLabel = triggerPlaceLabelInput.getText().toString().trim();
        }
        if (triggerPlaceRadiusInput != null) {
            triggerPlaceRadius = parseIntSafe(triggerPlaceRadiusInput.getText().toString(), 300);
        }
        if (constraintPlaceLabelInput != null) {
            constraintPlaceLabel = constraintPlaceLabelInput.getText().toString().trim();
        }
        if (constraintPlaceRadiusInput != null) {
            constraintPlaceRadius = parseIntSafe(constraintPlaceRadiusInput.getText().toString(), 300);
        }
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean hasFineLocation() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void captureCurrentLocation(boolean forTrigger) {
        if (!hasFineLocation()) {
            requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
            }, 2004);
            Toast.makeText(this, getString(R.string.red_need_loc_retry), Toast.LENGTH_SHORT).show();
            return;
        }
        android.location.LocationManager manager =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, getString(R.string.red_no_loc), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // 先用近 2 分钟内的已知位置，取不到再请求单次定位
            android.location.Location fresh = freshestLocation(manager, 2 * 60_000L);
            if (fresh != null) {
                applyCapturedLocation(forTrigger, fresh);
                return;
            }
            String provider = manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    ? android.location.LocationManager.GPS_PROVIDER
                    : android.location.LocationManager.NETWORK_PROVIDER;
            Toast.makeText(this, getString(R.string.red_getting_loc), Toast.LENGTH_SHORT).show();
            manager.requestSingleUpdate(provider, new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    applyCapturedLocation(forTrigger, location);
                }

                @Override
                public void onProviderEnabled(String p) {
                }

                @Override
                public void onProviderDisabled(String p) {
                }

                @Override
                public void onStatusChanged(String p, int status, Bundle extras) {
                }
            }, android.os.Looper.getMainLooper());
        } catch (SecurityException e) {
            Toast.makeText(this, getString(R.string.red_no_loc_perm), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.red_loc_fail), Toast.LENGTH_SHORT).show();
        }
    }

    private android.location.Location freshestLocation(
            android.location.LocationManager manager, long maxAgeMillis) {
        android.location.Location best = null;
        for (String provider : manager.getAllProviders()) {
            try {
                android.location.Location location = manager.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            } catch (SecurityException ignored) {
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() > maxAgeMillis) {
            return null;
        }
        return best;
    }

    private void applyCapturedLocation(boolean forTrigger, android.location.Location location) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (location == null) {
            Toast.makeText(this, getString(R.string.red_loc_fail2), Toast.LENGTH_SHORT).show();
            return;
        }
        readPlaceInputs();
        if (forTrigger) {
            triggerPlaceLat = location.getLatitude();
            triggerPlaceLng = location.getLongitude();
        } else {
            constraintPlaceLat = location.getLatitude();
            constraintPlaceLng = location.getLongitude();
        }
        Toast.makeText(this, getString(R.string.red_loc_saved), Toast.LENGTH_SHORT).show();
        renderAll();
    }

    private void fillCurrentWifi(EditText target) {
        if (!hasFineLocation()) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 2006);
            Toast.makeText(this, getString(R.string.red_wifi_need_loc), Toast.LENGTH_SHORT).show();
            return;
        }
        String ssid = ConnectionEventMonitor.currentSsid(this);
        if (ssid.isEmpty()) {
            Toast.makeText(this, getString(R.string.red_wifi_fail), Toast.LENGTH_SHORT).show();
        } else {
            target.setText(ssid);
        }
    }

    private void pickBondedBtDevice(EditText target) {
        if (android.os.Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2007);
            Toast.makeText(this, getString(R.string.red_bt_need_perm), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.bluetooth.BluetoothManager bluetoothManager =
                    (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            android.bluetooth.BluetoothAdapter adapter =
                    bluetoothManager == null ? null : bluetoothManager.getAdapter();
            if (adapter == null) {
                Toast.makeText(this, getString(R.string.red_no_bt), Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> names = new ArrayList<>();
            for (android.bluetooth.BluetoothDevice device : adapter.getBondedDevices()) {
                String name = device.getName();
                if (name != null && !name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
            if (names.isEmpty()) {
                Toast.makeText(this, getString(R.string.red_no_paired), Toast.LENGTH_SHORT).show();
                return;
            }
            Collections.sort(names, String::compareToIgnoreCase);
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.red_pick_paired_title))
                    .setItems(items, (dialog, which) -> target.setText(items[which]))
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show();
        } catch (SecurityException e) {
            Toast.makeText(this, getString(R.string.red_no_bt_perm), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 保存 ----------

    private void save() {
        routine.name = nameInput.getText().toString().trim();
        if (routine.triggerType.isEmpty()) {
            Toast.makeText(this, getString(R.string.red_need_trigger), Toast.LENGTH_SHORT).show();
            return;
        }
        if (routine.actionType.isEmpty()) {
            Toast.makeText(this, getString(R.string.red_need_action), Toast.LENGTH_SHORT).show();
            return;
        }
        readPlaceInputs();
        if (triggerWifiInput != null) {
            triggerWifiSsid = triggerWifiInput.getText().toString().trim();
        }
        if (triggerBtInput != null) {
            triggerBtDevice = triggerBtInput.getText().toString().trim();
        }
        if (constraintWifiInput != null) {
            constraintWifiSsid = constraintWifiInput.getText().toString().trim();
        }
        if (constraintBtInput != null) {
            constraintBtDevice = constraintBtInput.getText().toString().trim();
        }
        if (!constraintSwitch.isChecked()) {
            routine.constraintType = Routine.CONSTRAINT_NONE;
        } else if (routine.constraintType.isEmpty()) {
            Toast.makeText(this, getString(R.string.red_need_constraint), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject trigger = new JSONObject();
            switch (routine.triggerType) {
                case Routine.TRIGGER_TIME:
                    trigger.put("hour", timeHour);
                    trigger.put("minute", timeMinute);
                    trigger.put("weekdays", weekdayMask);
                    break;
                case Routine.TRIGGER_APP_OPEN:
                    if (triggerAppPackage.isEmpty()) {
                        Toast.makeText(this, getString(R.string.red_need_watch_app), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    trigger.put("package", triggerAppPackage);
                    trigger.put("label", triggerAppLabel);
                    break;
                case Routine.TRIGGER_WIFI_CONNECTED:
                    trigger.put("ssid", triggerWifiSsid);
                    break;
                case Routine.TRIGGER_BT_CONNECTED:
                    trigger.put("device", triggerBtDevice);
                    break;
                case Routine.TRIGGER_ENTER_PLACE:
                    if (Double.isNaN(triggerPlaceLat) || Double.isNaN(triggerPlaceLng)) {
                        Toast.makeText(this, getString(R.string.red_need_place), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    trigger.put("lat", triggerPlaceLat);
                    trigger.put("lng", triggerPlaceLng);
                    trigger.put("radius", (int) PlaceProximityScheduler.clampRadius(triggerPlaceRadius));
                    trigger.put("label", triggerPlaceLabel.isEmpty() ? getString(R.string.rt_default_place) : triggerPlaceLabel);
                    break;
                default:
                    break;
            }
            routine.triggerParam = trigger.toString();

            JSONObject constraint = new JSONObject();
            switch (routine.constraintType) {
                case Routine.CONSTRAINT_TIME_RANGE:
                    constraint.put("startHour", rangeStartHour);
                    constraint.put("startMinute", rangeStartMinute);
                    constraint.put("endHour", rangeEndHour);
                    constraint.put("endMinute", rangeEndMinute);
                    break;
                case Routine.CONSTRAINT_WIFI:
                    constraint.put("ssid", constraintWifiSsid);
                    break;
                case Routine.CONSTRAINT_BT:
                    constraint.put("device", constraintBtDevice);
                    break;
                case Routine.CONSTRAINT_PLACE:
                    if (Double.isNaN(constraintPlaceLat) || Double.isNaN(constraintPlaceLng)) {
                        Toast.makeText(this, getString(R.string.red_need_place), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    constraint.put("lat", constraintPlaceLat);
                    constraint.put("lng", constraintPlaceLng);
                    constraint.put("radius", (int) PlaceProximityScheduler.clampRadius(constraintPlaceRadius));
                    constraint.put("label", constraintPlaceLabel.isEmpty() ? getString(R.string.rt_default_place) : constraintPlaceLabel);
                    break;
                default:
                    break;
            }
            routine.constraintParam = constraint.toString();

            JSONObject action = new JSONObject();
            switch (routine.actionType) {
                case Routine.ACTION_COUNTDOWN: {
                    int minutes = 0;
                    try {
                        minutes = Integer.parseInt(
                                actionMinutesInput == null ? "" : actionMinutesInput.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    if (minutes <= 0 || minutes > 24 * 60) {
                        Toast.makeText(this, getString(R.string.red_need_minutes), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    action.put("minutes", minutes);
                    action.put("text", actionTextInput == null ? "" : actionTextInput.getText().toString().trim());
                    break;
                }
                case Routine.ACTION_POPUP:
                case Routine.ACTION_NOTIFY: {
                    String text = actionTextInput == null ? "" : actionTextInput.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this, getString(R.string.red_need_text), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    action.put("text", text);
                    break;
                }
                case Routine.ACTION_ACTIVATE_MEMO:
                    if (actionMemoId <= 0) {
                        Toast.makeText(this, getString(R.string.red_need_memo), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    action.put("memoId", actionMemoId);
                    action.put("title", actionMemoTitle);
                    break;
                case Routine.ACTION_OPEN_APP:
                    if (actionAppPackage.isEmpty()) {
                        Toast.makeText(this, getString(R.string.red_need_open_app), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    action.put("package", actionAppPackage);
                    action.put("label", actionAppLabel);
                    break;
                default:
                    break;
            }
            routine.actionParam = action.toString();
        } catch (JSONException e) {
            Toast.makeText(this, getString(R.string.red_save_fail, e.getMessage()), Toast.LENGTH_SHORT).show();
            return;
        }

        routine.enabled = true;
        repository.save(routine);
        try {
            RoutineAlarmScheduler.schedule(this, routine);
            PlaceProximityScheduler.schedule(this, routine);
            com.jinxin.unlockhub.service.ForegroundAppWatcherService.syncState(this);
        } catch (Throwable ignored) {
        }
        requestRuntimePermissionsIfNeeded();
        if (showPermissionGuideIfNeeded()) {
            return;
        }
        Toast.makeText(this, getString(R.string.red_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    /** 运行时权限：按规则用到的能力按需请求。 */
    private void requestRuntimePermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        boolean usesNotification = Routine.ACTION_POPUP.equals(routine.actionType)
                || Routine.ACTION_NOTIFY.equals(routine.actionType)
                || Routine.ACTION_COUNTDOWN.equals(routine.actionType)
                || Routine.ACTION_ACTIVATE_MEMO.equals(routine.actionType);
        if (usesNotification && android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        boolean usesWifiSsid = (Routine.TRIGGER_WIFI_CONNECTED.equals(routine.triggerType) && !triggerWifiSsid.isEmpty())
                || (Routine.CONSTRAINT_WIFI.equals(routine.constraintType) && !constraintWifiSsid.isEmpty());
        boolean usesPlace = Routine.TRIGGER_ENTER_PLACE.equals(routine.triggerType)
                || Routine.CONSTRAINT_PLACE.equals(routine.constraintType);
        if ((usesWifiSsid || usesPlace)
                && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        boolean usesBt = Routine.TRIGGER_BT_CONNECTED.equals(routine.triggerType)
                || Routine.CONSTRAINT_BT.equals(routine.constraintType);
        if (usesBt && android.os.Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 2003);
            return;
        }
        // 后台定位（Q+）必须在前台定位已授予后单独申请，否则系统会直接忽略
        if (usesPlace && android.os.Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2005);
        }
    }

    /** 特殊权限（不能用 requestPermissions 的）：弹对话框引导。返回 true 表示已接管收尾。 */
    private boolean showPermissionGuideIfNeeded() {
        if ((Routine.ACTION_DND_ON.equals(routine.actionType) || Routine.ACTION_DND_OFF.equals(routine.actionType))
                && !isDndAccessGranted()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.red_dnd_title))
                    .setMessage(getString(R.string.red_dnd_msg))
                    .setPositiveButton(getString(R.string.red_go_enable), (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                        } catch (Exception ignored) {
                        }
                        finish();
                    })
                    .setNegativeButton(getString(R.string.red_later), (dialog, which) -> finish())
                    .show();
            return true;
        }
        if (Routine.TRIGGER_APP_OPEN.equals(routine.triggerType)
                && !com.jinxin.unlockhub.service.ForegroundAppWatcherService.hasUsageAccess(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.red_usage_title))
                    .setMessage(getString(R.string.red_usage_msg))
                    .setPositiveButton(getString(R.string.red_go_enable), (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                        } catch (Exception ignored) {
                        }
                        finish();
                    })
                    .setNegativeButton(getString(R.string.red_later), (dialog, which) -> finish())
                    .show();
            return true;
        }
        return false;
    }

    private boolean isDndAccessGranted() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationPolicyAccessGranted();
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/");
    }

    private static List<Option> list(Option... options) {
        List<Option> result = new ArrayList<>();
        Collections.addAll(result, options);
        return result;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static String two(int value) {
        return String.format(Locale.US, "%02d", value);
    }
}
