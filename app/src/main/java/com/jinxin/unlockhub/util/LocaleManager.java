package com.jinxin.unlockhub.util;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * 运行时语言切换：把持久化的语言偏好（Prefs.appLanguage）套用到给定 Context 上。
 *
 * App 未用 AppCompat（零依赖），因此不走 AppCompatDelegate；改为在每个界面的
 * attachBaseContext（见 BaseActivity）与 Application.attachBaseContext 里包一层带目标
 * Locale 的 Context。切换语言时重启到主界面即可全局生效。
 */
public final class LocaleManager {
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    private LocaleManager() {
    }

    /** 返回套用了当前语言偏好的 Context；base 为空或任何异常则原样返回，绝不因此崩溃。 */
    public static Context wrap(Context base) {
        if (base == null) {
            return null;
        }
        try {
            String language = Prefs.appLanguage(base);
            Locale locale = new Locale(language);
            Locale.setDefault(locale);
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocale(locale);
            return base.createConfigurationContext(config);
        } catch (Throwable t) {
            return base;
        }
    }
}
