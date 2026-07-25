package com.jinxin.unlockhub.ui;

import android.app.Activity;
import android.content.Context;

import com.jinxin.unlockhub.util.LocaleManager;

/**
 * 所有界面的基类：在 attachBaseContext 里套用语言偏好，使 getString / 资源按所选语言加载。
 * 逐个界面改为 extends BaseActivity 即纳入语言切换（分批推进）。
 */
public abstract class BaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrap(newBase));
    }
}
