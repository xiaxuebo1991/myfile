package com.ucloudlink.refact.channel.monitors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtectionCtrl;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogPownOff;
import com.ucloudlink.refact.product.phone.restore.MobileStates;
import com.ucloudlink.refact.business.keepalive.JobSchedulerCtrl;
import com.ucloudlink.refact.business.keepalive.JobSchedulerIntervalTimeCtrl;
import com.ucloudlink.refact.business.keepalive.JobSchedulerUtils;
import com.ucloudlink.refact.utils.JLog;

import rx.Observable;
import rx.functions.Action1;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logv;
/**package com.ucloudlink.refact.network.Monitor;

 import android.content.BroadcastReceiver;
 import android.content.Context;
 import android.content.Intent;
 import android.util.Log;

 import com.ucloudlink.framework.util.SharedPreferencesUtils;
 import com.ucloudlink.refact.access.AccessEventId;

 /**
 * Created by wangkun on 2017/8/7.
 * 接收smartphone 发送的 SHUTDOWN 广播，用于恢复用户信息
 */


public class ShutdownReceiver extends BroadcastReceiver{
    private static final String TAG = "ShutdownReceiver";
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager    mTelephonyManager;
    private SubscriptionInfo subscriptionInfo;
    //private AccessState mAccessState;
    private int tmprPercent = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        JLog.logd("onReceive action=" + intent.getAction());
        mSubscriptionManager = SubscriptionManager.from(context);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (action.equals("android.intent.action.ACTION_SHUTDOWN")) {
            PerfLogPownOff.INSTANCE.createMsg(PerfLogPownOff.INSTANCE.getID_POWER_OFF(),0,0);
            JLog.logd("SHUTDOWN restoreMobileSettings!");
            if(JobSchedulerCtrl.SWITCH_KEEP_LIVE){
                JobSchedulerIntervalTimeCtrl.getInstance().setOnStartJobCount(0, "Shutdown - onReceive");
                JobSchedulerUtils.cancelJobScheduler(context);
            }

            try{
                ICloudFlowProtectionCtrl mICloudFlowProtectionCtrl = FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().getMICloudFlowProtectionCtrl();
                if(mICloudFlowProtectionCtrl!=null){
                    mICloudFlowProtectionCtrl.clearRetrict("Shutdown - onReceive");
                }
            }catch (Exception e){
                logd(e);
                e.printStackTrace();
            }


            try {
                  Observable<Integer> percent = ServiceManager.INSTANCE.getAccessEntry().getStatePersentOb();
                  percent.subscribe(new Action1<Integer>() {
                      @Override
                    public void call(Integer integer) {
                        logd("persent change " + integer);
                          tmprPercent = integer;
                    }
                });
                  logv("restoredefDataSlot percent:" + tmprPercent);
                  if(tmprPercent > 0){
                    int defDataSlot = MobileStates.getSlotOfDdp();
                    subscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(defDataSlot);
                    int subId = subscriptionInfo.getSubscriptionId();
                    logv("restoredefDataSlot defDataSlot:" + defDataSlot);
                    ServiceManager.systemApi.setDefaultDataSubId(subId);
                   } else{
                    logv("restoredefDataSlot else percent:" + percent);
                   }
                 } catch (Exception e) {
                    logd(e);
                 }
        }
    }
}

