package com.jinxin.unlockhub.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.jinxin.unlockhub.R;
import com.jinxin.unlockhub.util.Prefs;

public final class AppUi {
    private AppUi() {
    }

    // ---------- 主题 ----------

    /** 每个 Activity 在 super.onCreate 之后、setContentView 之前调用。 */
    public static void applyTheme(Activity activity) {
        activity.setTheme(Prefs.isLightTheme(activity) ? R.style.AppTheme_Light : R.style.AppTheme);
    }

    /** 解析当前主题下语义颜色属性（R.attr.appTextPrimary 等）的实际颜色值。 */
    public static int themeColor(Context context, int attrId) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attrId, value, true);
        return value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
    }

    /** 解析当前主题下 drawable 引用属性对应的资源 id。 */
    public static int themeDrawable(Context context, int attrId) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attrId, value, true);
        return value.resourceId;
    }

    /** 主页头部的主题切换按钮：深色时显示太阳（点击换浅色），浅色时显示月亮。 */
    public static View createThemeToggleButton(Activity activity) {
        LinearLayout wrap = new LinearLayout(activity);
        int size = dp(activity, 44);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(0, 0, dp(activity, 10), 0);
        wrap.setLayoutParams(params);
        wrap.setGravity(Gravity.CENTER);
        wrap.setBackgroundResource(R.drawable.bg_avatar_ring);
        wrap.setClickable(true);
        wrap.setFocusable(true);

        TextView icon = new TextView(activity);
        icon.setText(Prefs.isLightTheme(activity) ? "🌙" : "☀️");
        icon.setTextSize(17);
        wrap.addView(icon);
        wrap.setContentDescription(activity.getString(R.string.ui_toggle_theme));
        wrap.setOnClickListener(v -> {
            Prefs.setLightTheme(activity, !Prefs.isLightTheme(activity));
            activity.recreate();
        });
        return wrap;
    }

    /** 系统开关类控件（Switch/RadioButton/CheckBox）按主题着色：选中 accent，未选中用当前主题的次级色保证可见。 */
    public static void styleToggle(CompoundButton button) {
        Context context = button.getContext();
        int accent = context.getColor(R.color.accent);
        int normal = themeColor(context, R.attr.appTextSecondary);
        ColorStateList tint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, normal});
        if (button instanceof Switch) {
            Switch switchView = (Switch) button;
            switchView.setThumbTintList(tint);
            ColorStateList trackTint = new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{(accent & 0x00FFFFFF) | 0x66000000, themeColor(context, R.attr.appLine)});
            switchView.setTrackTintList(trackTint);
        } else {
            button.setButtonTintList(tint);
        }
    }

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void styleScreenBackground(View view) {
        view.setBackgroundResource(themeDrawable(view.getContext(), R.attr.appCanvasDrawable));
    }

    public static void styleScroll(ScrollView scrollView) {
        styleScreenBackground(scrollView);
    }

    public static LinearLayout createCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(context, 14));
        card.setLayoutParams(params);
        card.setElevation(dp(context, 2));
        int pad = dp(context, 16);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    public static LinearLayout createBrandHeader(Context context, String subtitle) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(context, 18));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.ic_brand_mark);
        header.addView(logo, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(context, 14), 0, 0, 0);
        copy.addView(title(context, "UnlockHub"));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = body(context, subtitle);
            sub.setPadding(0, dp(context, 4), 0, 0);
            copy.addView(sub);
        }
        header.addView(copy, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return header;
    }

    /** 右上角「设置」入口：齿轮图标圆钮，点击行为由调用方设置（通常打开 SettingsActivity）。 */
    public static View createSettingsButton(Activity activity) {
        LinearLayout wrap = new LinearLayout(activity);
        int size = dp(activity, 44);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(0, 0, dp(activity, 10), 0);
        wrap.setLayoutParams(params);
        wrap.setGravity(Gravity.CENTER);
        wrap.setBackgroundResource(R.drawable.bg_avatar_ring);
        wrap.setClickable(true);
        wrap.setFocusable(true);

        TextView icon = new TextView(activity);
        icon.setText("⚙️");
        icon.setTextSize(17);
        wrap.addView(icon);
        wrap.setContentDescription(activity.getString(R.string.ui_settings));
        return wrap;
    }

    public static View createAvatarButton(Context context) {
        LinearLayout wrap = new LinearLayout(context);
        int size = dp(context, 44);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setGravity(Gravity.CENTER);
        wrap.setBackgroundResource(R.drawable.bg_avatar_ring);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_brand_mark);
        int iconSize = dp(context, 24);
        wrap.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        wrap.setContentDescription(context.getString(R.string.ui_profile));
        return wrap;
    }

    public static TextView sectionTitle(Context context, String value) {
        TextView textView = text(context, value, 18, true);
        textView.setTextColor(themeColor(context, R.attr.appTextPrimary));
        textView.setPadding(0, dp(context, 8), 0, dp(context, 8));
        return textView;
    }

    public static TextView label(Context context, String value) {
        TextView textView = text(context, value, 13, true);
        textView.setTextColor(themeColor(context, R.attr.appTextSecondary));
        textView.setPadding(0, dp(context, 8), 0, dp(context, 4));
        return textView;
    }

    public static TextView body(Context context, String value) {
        TextView textView = text(context, value, 15, false);
        textView.setTextColor(themeColor(context, R.attr.appTextSecondary));
        textView.setLineSpacing(dp(context, 2), 1f);
        return textView;
    }

    public static TextView statNumber(Context context, String value) {
        TextView textView = text(context, value, 32, true);
        textView.setTextColor(color(context, R.color.stat_number));
        return textView;
    }

    public static TextView weekdayChip(Context context, String label) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(12);
        chip.setAllCaps(false);
        int horizontal = dp(context, 6);
        int vertical = dp(context, 10);
        chip.setPadding(horizontal, vertical, horizontal, vertical);
        chip.setClickable(true);
        chip.setFocusable(true);
        styleWeekdayChip(chip, false);
        return chip;
    }

    public static void styleWeekdayChip(TextView chip, boolean selected) {
        Context context = chip.getContext();
        chip.setSelected(selected);
        chip.setBackgroundResource(R.drawable.bg_chip);
        float elevation = selected ? dp(context, 4) : 0;
        chip.setElevation(elevation);
        chip.setTranslationZ(elevation);
        chip.setTypeface(chip.getTypeface(), selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setTextColor(selected
                ? color(context, R.color.text_on_accent)
                : themeColor(context, R.attr.appTextSecondary));
    }

    public static EditText input(Context context, String hint) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setTextSize(16);
        editText.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        editText.setBackgroundResource(R.drawable.bg_input);
        editText.setTextColor(color(context, R.color.input_text));
        editText.setHintTextColor(color(context, R.color.input_hint));
        editText.setMinHeight(dp(context, 50));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(context, 8));
        editText.setLayoutParams(params);
        return editText;
    }

    public static Button primaryButton(Context context, String value) {
        Button button = button(context, value);
        button.setTextColor(color(context, R.color.text_on_accent));
        button.setBackgroundResource(R.drawable.bg_button_primary);
        return button;
    }

    public static Button secondaryButton(Context context, String value) {
        Button button = button(context, value);
        button.setTextColor(color(context, R.color.accent));
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        return button;
    }

    public static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(16);
        button.setStateListAnimator(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)
        );
        params.setMargins(0, dp(context, 8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    public static TextView title(Context context, String value) {
        TextView textView = text(context, value, 28, true);
        textView.setTextColor(themeColor(context, R.attr.appTextPrimary));
        return textView;
    }

    private static TextView text(Context context, String value, int sp, boolean bold) {
        TextView textView = new TextView(context);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setGravity(Gravity.START);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        }
        return textView;
    }

    private static int color(Context context, int resId) {
        return context.getColor(resId);
    }
}
