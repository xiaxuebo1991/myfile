package com.ucloudlink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.ui.AccessEntryService;
import com.ucloudlink.refact.utils.JLog;

/**
 * Created by zhifeng.gao on 2018/1/17.
 */

public class DebugReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
        JLog.logd("onReceive action=" + intent.getAction());
        Intent intent1 = new Intent(context,AccessEntryService.class);
        context.startService(intent1);
    }
}
