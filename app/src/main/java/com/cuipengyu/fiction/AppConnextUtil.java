package com.cuipengyu.fiction;

import android.app.Application;
import android.content.Context;

/**
 * App全局对象 ,manifest中声明name
 * Created by cuipengyu on 2018/3/14.
 */

public class AppConnextUtil extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        AppConnextUtil.context = getApplicationContext();
        SharedPreferencesUtil.init(this,getPackageName() + "_preference", Context.MODE_MULTI_PROCESS);
    }

    public static Context getAppConnect() {
        return AppConnextUtil.context;
    }

}
