package com.jinxin.unlockhub.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 自动化规则：条件（触发事件 + 可选约束）→ 动作。
 * 参数以 JSON 存储，便于扩展（例如未来的位置条件）。
 */
public final class Routine {
    // 触发事件
    public static final String TRIGGER_TIME = "time";               // {"hour":22,"minute":0,"weekdays":127}
    public static final String TRIGGER_CHARGER_ON = "charger_on";
    public static final String TRIGGER_CHARGER_OFF = "charger_off";
    public static final String TRIGGER_BATTERY_LOW = "battery_low";
    public static final String TRIGGER_FIRST_UNLOCK = "first_unlock";
    public static final String TRIGGER_ANY_UNLOCK = "any_unlock";   // 每次解锁
    public static final String TRIGGER_APP_OPEN = "app_open";       // {"package":"com.x","label":"某应用"}
    public static final String TRIGGER_WIFI_CONNECTED = "wifi_connected"; // {"ssid":""} 空=任意
    public static final String TRIGGER_BT_CONNECTED = "bt_connected";     // {"device":""} 空=任意
    public static final String TRIGGER_ENTER_PLACE = "enter_place";       // {"lat":..,"lng":..,"radius":300,"label":"家"}

    // 约束（第二个条件，可为空）
    public static final String CONSTRAINT_NONE = "";
    public static final String CONSTRAINT_TIME_RANGE = "time_range"; // {"startHour":8,"startMinute":0,"endHour":22,"endMinute":0}
    public static final String CONSTRAINT_CHARGING = "charging";
    public static final String CONSTRAINT_NOT_CHARGING = "not_charging";
    public static final String CONSTRAINT_WIFI = "on_wifi";          // {"ssid":""} 空=任意 Wi-Fi
    public static final String CONSTRAINT_BT = "bt_device";          // {"device":""} 空=任意蓝牙设备
    public static final String CONSTRAINT_PLACE = "at_place";        // {"lat":..,"lng":..,"radius":300,"label":"家"}

    // 动作
    public static final String ACTION_POPUP = "popup";               // {"text":"..."} 全屏弹窗，解锁必见
    public static final String ACTION_NOTIFY = "notify";             // {"text":"..."}
    public static final String ACTION_OPEN_APP = "open_app";         // {"package":"com.x","label":"某应用"}
    public static final String ACTION_DND_ON = "dnd_on";
    public static final String ACTION_DND_OFF = "dnd_off";
    public static final String ACTION_COUNTDOWN = "countdown";       // {"minutes":25,"text":"..."} 倒计时结束整页弹窗

    public long id;
    public String name = "";
    public boolean enabled = true;
    public String triggerType = TRIGGER_TIME;
    public String triggerParam = "";
    public String constraintType = CONSTRAINT_NONE;
    public String constraintParam = "";
    public String actionType = ACTION_NOTIFY;
    public String actionParam = "";
    public long lastFiredAt;
    public long createdAt;

    public JSONObject triggerJson() {
        return parse(triggerParam);
    }

    public JSONObject constraintJson() {
        return parse(constraintParam);
    }

    public JSONObject actionJson() {
        return parse(actionParam);
    }

    private static JSONObject parse(String value) {
        if (value == null || value.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** 人类可读的条件描述（按当前语言）。 */
    public String describeCondition(android.content.Context ctx) {
        StringBuilder builder = new StringBuilder();
        JSONObject trigger = triggerJson();
        switch (triggerType) {
            case TRIGGER_TIME:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_time,
                        two(trigger.optInt("hour", 0)), two(trigger.optInt("minute", 0)),
                        describeWeekdays(ctx, trigger.optInt("weekdays", 127))));
                break;
            case TRIGGER_CHARGER_ON:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_charger_on));
                break;
            case TRIGGER_CHARGER_OFF:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_charger_off));
                break;
            case TRIGGER_BATTERY_LOW:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_battery_low));
                break;
            case TRIGGER_FIRST_UNLOCK:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_first_unlock));
                break;
            case TRIGGER_ANY_UNLOCK:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_any_unlock));
                break;
            case TRIGGER_APP_OPEN:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_app_open,
                        trigger.optString("label", trigger.optString("package",
                                ctx.getString(com.jinxin.unlockhub.R.string.rt_default_app)))));
                break;
            case TRIGGER_WIFI_CONNECTED: {
                String ssid = trigger.optString("ssid", "");
                builder.append(ssid.isEmpty()
                        ? ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_wifi_any)
                        : ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_wifi, ssid));
                break;
            }
            case TRIGGER_BT_CONNECTED: {
                String device = trigger.optString("device", "");
                builder.append(device.isEmpty()
                        ? ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_bt_any)
                        : ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_bt, device));
                break;
            }
            case TRIGGER_ENTER_PLACE:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_trig_place,
                        trigger.optString("label",
                                ctx.getString(com.jinxin.unlockhub.R.string.rt_default_place))));
                break;
            default:
                builder.append(triggerType);
        }
        JSONObject constraint = constraintJson();
        switch (constraintType) {
            case CONSTRAINT_TIME_RANGE:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_con_time_range,
                        two(constraint.optInt("startHour", 0)), two(constraint.optInt("startMinute", 0)),
                        two(constraint.optInt("endHour", 23)), two(constraint.optInt("endMinute", 59))));
                break;
            case CONSTRAINT_CHARGING:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_con_charging));
                break;
            case CONSTRAINT_NOT_CHARGING:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_con_not_charging));
                break;
            case CONSTRAINT_WIFI: {
                String ssid = constraint.optString("ssid", "");
                builder.append(ssid.isEmpty()
                        ? ctx.getString(com.jinxin.unlockhub.R.string.rt_con_wifi_any)
                        : ctx.getString(com.jinxin.unlockhub.R.string.rt_con_wifi, ssid));
                break;
            }
            case CONSTRAINT_BT: {
                String device = constraint.optString("device", "");
                builder.append(device.isEmpty()
                        ? ctx.getString(com.jinxin.unlockhub.R.string.rt_con_bt_any)
                        : ctx.getString(com.jinxin.unlockhub.R.string.rt_con_bt, device));
                break;
            }
            case CONSTRAINT_PLACE:
                builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_con_place,
                        constraint.optString("label",
                                ctx.getString(com.jinxin.unlockhub.R.string.rt_default_place))));
                break;
            default:
                break;
        }
        return builder.toString();
    }

    /** 人类可读的动作描述（按当前语言）。 */
    public String describeAction(android.content.Context ctx) {
        JSONObject action = actionJson();
        switch (actionType) {
            case ACTION_POPUP:
                return ctx.getString(com.jinxin.unlockhub.R.string.rt_act_popup, action.optString("text", ""));
            case ACTION_NOTIFY:
                return ctx.getString(com.jinxin.unlockhub.R.string.rt_act_notify, action.optString("text", ""));
            case ACTION_OPEN_APP:
                return ctx.getString(com.jinxin.unlockhub.R.string.rt_act_open_app,
                        action.optString("label", action.optString("package",
                                ctx.getString(com.jinxin.unlockhub.R.string.rt_default_app))));
            case ACTION_DND_ON:
                return ctx.getString(com.jinxin.unlockhub.R.string.rt_act_dnd_on);
            case ACTION_DND_OFF:
                return ctx.getString(com.jinxin.unlockhub.R.string.rt_act_dnd_off);
            case ACTION_COUNTDOWN:
                return ctx.getString(com.jinxin.unlockhub.R.string.rt_act_countdown, action.optInt("minutes", 25));
            default:
                return actionType;
        }
    }

    private static String describeWeekdays(android.content.Context ctx, int mask) {
        if (mask == 127 || mask <= 0) {
            return ctx.getString(com.jinxin.unlockhub.R.string.rt_weekdays_all);
        }
        String[] names = ctx.getResources().getStringArray(com.jinxin.unlockhub.R.array.memo_weekdays);
        StringBuilder builder = new StringBuilder(ctx.getString(com.jinxin.unlockhub.R.string.rt_weekdays_prefix));
        boolean first = true;
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (!first) {
                    builder.append("/");
                }
                builder.append(names[i]);
                first = false;
            }
        }
        return builder.append(ctx.getString(com.jinxin.unlockhub.R.string.rt_weekdays_suffix)).toString();
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
