package com.ucloudlink.refact.channel.monitors;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import static com.ucloudlink.refact.utils.JLog.logd;


//import static com.ucloudlink.framework.helper.JLog.JLog.logd;
/**
 * Created by jiaming.liang on 2016/7/15.
 */
public class UcloudReceive extends BroadcastReceiver {
    public static final String SUBSCRIPTION_KEY  = "subscription";
    public static final String FAILURE_REASON_KEY = "reason";
    public static final String DATA_APN_TYPE_KEY = "apnType";
    @Override
    public void onReceive(Context context, Intent intent){
        //logd("ucloudlink ACTION_DATA_CONNECTION_FAILED");
        logd("ucloudlink ACTION_DATA_CONNECTION_FAILED reason is " + intent.getStringExtra(FAILURE_REASON_KEY));
        logd("ucloudlink ACTION_DATA_CONNECTION_FAILED sub id is " + intent.getStringExtra(SUBSCRIPTION_KEY));
        //logv("ucloudlink ACTION_DATA_CONNECTION_FAILED sub id is " + intent.getStringExtra(SUBSCRIPTION_KEY));
    }

    }

