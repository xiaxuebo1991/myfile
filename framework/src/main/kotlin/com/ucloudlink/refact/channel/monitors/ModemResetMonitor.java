package com.ucloudlink.refact.channel.monitors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.StateMessageId;
import com.ucloudlink.refact.channel.enabler.DataEnableEvent;
import com.ucloudlink.refact.utils.JLog;

/**
 * Created by zhifeng.gao on 2018/1/23.
 */

public class ModemResetMonitor extends BroadcastReceiver {
    private Context mContext;
    private static final String RADIO_STATE = "android.intent.action.RADIO_STATE";
    private static final String RADIO_OFF = "RADIO_OFF";
    private static final String RADIO_ON = "RADIO_ON";
    private static final String RADIO_UNAVAILABLE = "RADIO_UNAVAILABLE";
    private boolean isModemException = false;

    public ModemResetMonitor(Context context) {
        mContext = context;
        startMonitoring();
    }

    public void startMonitoring() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(RADIO_STATE);
        mContext.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        JLog.logd("onReceive action=" + intent.getAction());
        final String action = intent.getAction();
        switch (action) {
            case RADIO_STATE:
                String mState = intent.getStringExtra("mState");
                if (mState.equals(RADIO_UNAVAILABLE)) {
                    //进行关卡操作
                    ServiceManager.seedCardEnabler.notifyEventToCard(DataEnableEvent.EVENT_MODEM_RESET,null);
                    ServiceManager.cloudSimEnabler.notifyEventToCard(DataEnableEvent.EVENT_MODEM_RESET,null);
                    isModemException = true;
                }
                if (mState.equals(RADIO_ON) && isModemException) {
                    //进行重登陆操作
                    ServiceManager.accessEntry.notifyEvent(StateMessageId.USER_MODEM_RESET);
                    isModemException = false;
                }
                break;
        }
    }

}
