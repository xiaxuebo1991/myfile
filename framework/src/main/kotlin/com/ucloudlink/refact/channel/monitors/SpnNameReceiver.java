package com.ucloudlink.refact.channel.monitors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

/**
 * Created by haiping.liu on 2017/8/2.
 * 接收dsds ui 发送的 spn name 广播，用于显示状态栏云卡名称
 */


public class SpnNameReceiver extends BroadcastReceiver{
    private static final String TAG = "SpnNameReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        JLog.logd("onReceive action=" + intent.getAction());
        if (action.equals("ukelink.spn.name")) {
          String spnName =  intent.getStringExtra("spnName");
            String localSpnName = SharedPreferencesUtils.getString(context,"spnName");
            Log.d(TAG, "getSpnShow onReceive spnName="+spnName+". localSpnName="+localSpnName);
            if (!localSpnName.equalsIgnoreCase(spnName)){
                SharedPreferencesUtils.putString(context,"spnName",spnName);
            }
        }
    }
}
