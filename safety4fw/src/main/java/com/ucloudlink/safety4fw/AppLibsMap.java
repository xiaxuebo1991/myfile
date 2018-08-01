package com.ucloudlink.safety4fw;

import android.content.Context;

import com.ucloudlink.cloudsim.app.AppLibs;

/**
 * Created by jianguo.he on 2017/7/20.
 */

public class AppLibsMap {

    // 不能删，这里被反射调用
    public void environmentInit(Context context){
        AppLibs.INSTANCE.environmentInit(context);
    }
}
