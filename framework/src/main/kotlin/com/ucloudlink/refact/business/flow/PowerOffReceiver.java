package com.ucloudlink.refact.business.flow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ucloudlink.framework.tasks.UploadFlowTask;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by pengchugang on 2017/6/3.
 */

public class PowerOffReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent){
        if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
            logd("recv PowerOff message.");
            NetSpeedOperater.resetRetrict("PowerOff");
            UploadFlowTask.INSTANCE.saveLocalFlowStats();
            SCFlowController.getInstance().stop();
        }
    }
}
