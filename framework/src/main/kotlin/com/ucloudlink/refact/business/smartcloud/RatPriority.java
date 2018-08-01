package com.ucloudlink.refact.business.smartcloud;

import android.os.Handler;
import android.os.Message;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import com.ucloudlink.framework.protocol.protobuf.S2c_UpdatePlmnListRequest;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;

import rx.Subscription;
import rx.functions.Action1;

/**
 * Created by zhanlin.ma on 2018/1/19.
 *
 */


public class RatPriority {
    static final String TAG = "smartcloud RatPriority";
    private static RatPriority instance = null;
    private boolean      mEnable                   = false;
    private boolean      mPunishEnable             = false;
    private Subscription mServiceStateSubscription = null;
    TelephonyManager mTelephonyManager = TelephonyManager.from(ServiceManager.appContext);

    private static final int IN_2G_TIMEOUT_TIME = 5 * 60 * 1000;

    private RatPriority(){
    }

    public void setEnable(boolean enable) {
        mEnable = enable;
        if (enable){
            registerListen();
        }else {
            unRegisterListen();
            stopIn2GTimer();
            stopPunishTimer();
            mPunishEnable = false;
        }
    }

    public boolean getEnable() {
        return mEnable;
    }

    public static RatPriority getInstance(){
        if (instance == null) {
            instance = new RatPriority();
        }
        return instance;
    }

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case SmartCloudEventId.EVENT_SERVICE_STATE_CHANGED:{

                    ServiceState serviceState = (ServiceState)msg.obj;

                    JLog.logi(TAG,"EVENT_SERVICE_STATE_CHANGED serviceState:"
                            +serviceState+",net:"+mTelephonyManager.getNetworkClass(serviceState.getDataNetworkType()));

                    if (serviceState.getDataRegState() != ServiceState.STATE_IN_SERVICE){
                        JLog.logi(TAG,"cloud sim out off service stop timer");
                        stopIn2GTimer();
                        return;
                    }

                    if (TelephonyManager.NETWORK_CLASS_2_G == mTelephonyManager.getNetworkClass(serviceState.getDataNetworkType())){
                        if (hasMessages(SmartCloudEventId.EVENT_IN_2G_TIMEOUT)){
                            return;
                        }else{
                            startIn2GTimer();
                            JLog.logi(TAG,"rat in 2g start timer.");
                        }
                    }

                    if ((TelephonyManager.NETWORK_CLASS_3_G == mTelephonyManager.getNetworkClass(serviceState.getDataNetworkType()))
                        ||(TelephonyManager.NETWORK_CLASS_4_G == mTelephonyManager.getNetworkClass(serviceState.getDataNetworkType()))){
                        stopIn2GTimer();
                        JLog.logi(TAG,"rat in 3g or 4g.");
                    }

                    break;
                }
                case SmartCloudEventId.EVENT_IN_2G_TIMEOUT:
                    if (!SmartCloudController.getInstance().getHandler().hasMessages(SmartCloudEventId.EVENT_SWITCH_CARD_REQ)){
                        JLog.logi(TAG,"cloud sim in 2g time out start switch card.");
                        SmartCloudController.getInstance().getHandler()
                                .obtainMessage(SmartCloudEventId.EVENT_SWITCH_CARD_REQ,0,SmartCloudSwitchReason.SWITCH_VSIM_3G_PRIORITY,null).sendToTarget();
                    }
                    break;
                case SmartCloudEventId.EVENT_PUNISH_TIME_OUT:
                    mPunishEnable = false;
                    JLog.logi(TAG,"rat priority test punish time finished.");
                    break;
            }
        }
    };

    private void registerListen(){
        if (mServiceStateSubscription != null){
            if (!mServiceStateSubscription.isUnsubscribed()){
                mServiceStateSubscription.unsubscribe();
            }
        }

        mServiceStateSubscription = SmartCloudController.getInstance().getServiceStateObservable()
                .asObservable().subscribe(new Action1<ServiceState>() {
                    @Override
                    public void call(ServiceState serviceState) {

                        if (!mEnable){
                            return;
                        }

                        if (mPunishEnable){
                            return;
                        }

                        mHandler.obtainMessage(SmartCloudEventId.EVENT_SERVICE_STATE_CHANGED, serviceState).sendToTarget();
                    }
                });
    }

    private void unRegisterListen(){
        if (mServiceStateSubscription != null){
            if (mServiceStateSubscription.isUnsubscribed()){
                mServiceStateSubscription.unsubscribe();
                mServiceStateSubscription = null;
            }
        }
    }

    public void setRatPriorityCfg(S2c_UpdatePlmnListRequest cfg){
        mEnable = cfg.name > 0 ? true : false;
        JLog.logi(TAG,"setRatPriorityCfg cfg:"+cfg.toString());
        if (mEnable){
            registerListen();
        }else {
            stopIn2GTimer();
            unRegisterListen();
        }
    }

    private void startIn2GTimer(){
        if (!mHandler.hasMessages(SmartCloudEventId.EVENT_IN_2G_TIMEOUT)){
            mHandler.sendEmptyMessageDelayed(SmartCloudEventId.EVENT_IN_2G_TIMEOUT, IN_2G_TIMEOUT_TIME);
        }
    }

    private void stopIn2GTimer(){
        if (mHandler.hasMessages(SmartCloudEventId.EVENT_IN_2G_TIMEOUT)){
            mHandler.removeMessages(SmartCloudEventId.EVENT_IN_2G_TIMEOUT);
        }
    }

    public void setPunishment(int time){
        JLog.logi(TAG,"set rat priority punishment time:" + time);
        mPunishEnable = time != 0 ? true : false;
        stopPunishTimer();
        if (mPunishEnable){
            stopIn2GTimer();
            startPunishTimer(time);
        }
    }

    private void startPunishTimer(int time){
        mHandler.sendEmptyMessageDelayed(SmartCloudEventId.EVENT_PUNISH_TIME_OUT, time * 60 * 1000);
    }

    private void stopPunishTimer(){
        if (mHandler.hasMessages(SmartCloudEventId.EVENT_PUNISH_TIME_OUT)){
            mHandler.removeMessages(SmartCloudEventId.EVENT_PUNISH_TIME_OUT);
        }
    }
}
