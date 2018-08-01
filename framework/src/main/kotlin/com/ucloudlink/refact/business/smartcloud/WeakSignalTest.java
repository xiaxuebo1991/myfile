package com.ucloudlink.refact.business.smartcloud;

import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import com.ucloudlink.framework.protocol.protobuf.S2c_weak_signal_ctrl;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;

import rx.Subscription;
import rx.functions.Action1;

/**
 * Created by zhanlin.ma on 2018/1/21.
 */

public class WeakSignalTest {
    static final   String         TAG             = "smartcloud WeakSignalTest";
    private static WeakSignalTest instance        = null;
    private        boolean        enable          = false;
    private        boolean        mPunishEnable   = false;
    private        boolean        isDataConnected = false;
    private int weakSignalMax4G;
    private int weakSignalMin4G;
    private int weakSignalMax3G;
    private int weakSignalMin3G;
    private int interval;
    private Subscription mSignalStrengthSubscription = null;
    private Subscription mServiceStateSubscription   = null;
    private Subscription mDataConnectionSub;
    TelephonyManager mTelephonyManager = TelephonyManager.from(ServiceManager.appContext);
    private             int     ratType   = TelephonyManager.NETWORK_CLASS_UNKNOWN;
    private             boolean isTesting = false;
    public static final int     INVALID   = 0x7FFFFFFF;

    private WeakSignalTest() {
    }

    public static WeakSignalTest getInstance() {
        if (instance == null) {
            instance = new WeakSignalTest();
        }
        return instance;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
        if (enable){
            registerListen();
        }else {
            unRegisterListen();
            stopWeakSignalTimer();
            stopPunishTimer();
            mPunishEnable = false;
        }
    }

    public boolean getEnable(){
        return enable;
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SmartCloudEventId.EVENT_SIGNAL_STRENGTH_CHANGED: {

                    if (!isValidRatForTest()) {
                        stopWeakSignalTimer();
                        JLog.logi(TAG, "ratType=" + ratType);
                        return;
                    }

                    SignalStrength signalStrength = (SignalStrength) msg.obj;

                    if ((!isTesting) && (isWeakSignal(signalStrength))) {
                        startWeakSignalTimer();
                    }

                    if ((isTesting) && (isStrongSignal(signalStrength))) {
                        stopWeakSignalTimer();
                    }

                }
                break;
                case SmartCloudEventId.EVENT_WEAK_SIGNAL_TIMEOUT: {
                    if (!SmartCloudController.getInstance().getHandler().hasMessages(SmartCloudEventId.EVENT_SWITCH_CARD_REQ)) {
                        JLog.logi(TAG, "EVENT_WEAK_SIGNAL_TIMEOUT req switch card.");
                        SmartCloudController.getInstance().getHandler()
                                .obtainMessage(SmartCloudEventId.EVENT_SWITCH_CARD_REQ, 0, SmartCloudSwitchReason.SWITCH_VSIM_WEAK_SIGNAL, null).sendToTarget();
                    }
                    isTesting = false;
                }
                break;
                case SmartCloudEventId.EVENT_PUNISH_TIME_OUT:
                    mPunishEnable = false;
                    JLog.logi(TAG, "weak signal test punish time finished.");
                    break;
            }
        }
    };

    public void registerListen() {
        if (mSignalStrengthSubscription != null) {
            if (!mSignalStrengthSubscription.isUnsubscribed()) {
                mSignalStrengthSubscription.unsubscribe();
            }
        }

        mSignalStrengthSubscription = SmartCloudController.getInstance().getSignalStrengthObservable()
                .subscribe(new Action1<SignalStrength>() {
                    @Override
                    public void call(SignalStrength signalStrength) {

                        if (!enable) {
                            return;
                        }

                        if (!isDataConnected) {
                            return;
                        }

                        if (mPunishEnable) {
                            JLog.logd(TAG, "weak signal test in punishment time.");
                            return;
                        }

                        mHandler.obtainMessage(SmartCloudEventId.EVENT_SIGNAL_STRENGTH_CHANGED, signalStrength).sendToTarget();
                    }
                });

        if (mServiceStateSubscription != null) {
            if (!mServiceStateSubscription.isUnsubscribed()) {
                mServiceStateSubscription.unsubscribe();
            }
        }

        mServiceStateSubscription = SmartCloudController.getInstance().getServiceStateObservable()
                .subscribe(new Action1<ServiceState>() {
                    @Override
                    public void call(ServiceState serviceState) {
                        ratType = mTelephonyManager.getNetworkClass(serviceState.getDataNetworkType());
                        JLog.logi(TAG, "ServiceState changed ratType:" + ratType);
                    }
                });

        if (mDataConnectionSub != null) {
            if (!mDataConnectionSub.isUnsubscribed()) {
                mDataConnectionSub.unsubscribe();
            }
        }

        mDataConnectionSub = SmartCloudController.getInstance().getDataConnectionStateObservable()
                .subscribe(new Action1<NetworkInfo.State>() {
                    @Override
                    public void call(NetworkInfo.State state) {
                        if (state == NetworkInfo.State.CONNECTED) {
                            isDataConnected = true;
                            JLog.logi(TAG, "weak signal DataConnection CONNECTED");
                        } else {
                            isDataConnected = false;
                            stopWeakSignalTimer();
                        }
                    }
                });
    }

    public void unRegisterListen() {

        stopWeakSignalTimer();

        if (mServiceStateSubscription != null) {
            if (!mServiceStateSubscription.isUnsubscribed()) {
                mServiceStateSubscription.unsubscribe();
                mServiceStateSubscription = null;
            }
        }

        if (mSignalStrengthSubscription != null) {
            if (!mSignalStrengthSubscription.isUnsubscribed()) {
                mSignalStrengthSubscription.unsubscribe();
                mSignalStrengthSubscription = null;
            }
        }

        if (mDataConnectionSub != null) {
            if (!mDataConnectionSub.isUnsubscribed()) {
                mDataConnectionSub.unsubscribe();
                mDataConnectionSub = null;
            }
        }
    }

    private void startWeakSignalTimer() {
        if (!mHandler.hasMessages(SmartCloudEventId.EVENT_WEAK_SIGNAL_TIMEOUT)) {
            mHandler.sendEmptyMessageDelayed(SmartCloudEventId.EVENT_WEAK_SIGNAL_TIMEOUT, interval * 60 * 1000);
            JLog.logi(TAG, "startTimer");
            isTesting = true;
        }
    }

    private void stopWeakSignalTimer() {
        if (mHandler.hasMessages(SmartCloudEventId.EVENT_WEAK_SIGNAL_TIMEOUT)) {
            mHandler.removeMessages(SmartCloudEventId.EVENT_WEAK_SIGNAL_TIMEOUT);
            isTesting = false;
        }
    }

    private boolean isWeakSignal(SignalStrength signalStrength) {
        boolean ret = false;
        switch (ratType) {
            case TelephonyManager.NETWORK_CLASS_3_G:
                if (weakSignalMin3G == INVALID) {
                    return false;
                }
                JLog.logi(TAG, "isWeakSignal 3g signalStrength=" + signalStrength.getGsmSignalStrength()
                        + ",weakSignalMin3G:" + weakSignalMin3G + ",dbm:" + getDbm(signalStrength.getGsmSignalStrength()));
                if (getDbm(signalStrength.getGsmSignalStrength()) < weakSignalMin3G) {
                    ret = true;
                }
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                if (weakSignalMin4G == INVALID) {
                    return false;
                }
                JLog.logi(TAG, "isWeakSignal 4g Rsrp=" + signalStrength.getLteRsrp()
                        + ",weakSignalMin4G:" + weakSignalMin4G);
                if (signalStrength.getLteRsrp() < weakSignalMin4G) {
                    ret = true;
                }
                break;
            default:
                break;
        }
        return ret;
    }

    private boolean isStrongSignal(SignalStrength signalStrength) {
        boolean ret = false;
        switch (ratType) {
            case TelephonyManager.NETWORK_CLASS_3_G:
                if (weakSignalMax3G == INVALID) {
                    return false;
                }
                JLog.logi(TAG, "isStrongSignal 3g signalStrength=" + signalStrength.getGsmSignalStrength()
                        + ",weakSignalMax3G:" + weakSignalMax3G + ",dbm:" + getDbm(signalStrength.getGsmSignalStrength()));
                if (getDbm(signalStrength.getGsmSignalStrength()) > weakSignalMax3G) {
                    ret = true;
                }
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                if (weakSignalMax4G == INVALID) {
                    return false;
                }
                JLog.logi(TAG, "isStrongSignal 4g signalStrength=" + signalStrength.getLteRsrp()
                        + ",weakSignalMax4G:" + weakSignalMax4G);
                if (signalStrength.getLteRsrp() > weakSignalMax4G) {
                    ret = true;
                }
                break;
            default:
                break;
        }
        return ret;
    }

    private boolean isValidRatForTest() {
        boolean ret = false;
        switch (ratType) {
            case TelephonyManager.NETWORK_CLASS_3_G:
            case TelephonyManager.NETWORK_CLASS_4_G:
                ret = true;
                break;
            default:
                break;
        }
        return ret;
    }

    public void setWeakSignalTestCfg(S2c_weak_signal_ctrl cfg) {

        JLog.logi(TAG, "S2c_weak_signal_ctrl:" + cfg.toString());

        if (cfg.ctrl == null) {
            return;
        }
        enable = cfg.ctrl == 1 ? true : false;

        weakSignalMax4G = cfg.lte_weak_signal_max != null ? cfg.lte_weak_signal_max.intValue() : INVALID;
        weakSignalMin4G = cfg.lte_weak_signal_min != null ? cfg.lte_weak_signal_min.intValue() : INVALID;
        weakSignalMax3G = cfg.max_csq != null ? cfg.max_csq.intValue() : INVALID;
        weakSignalMin3G = cfg.min_csq != null ? cfg.min_csq.intValue() : INVALID;
        interval = cfg.time.intValue();

        JLog.logi(TAG, "setWeakSignalTestInfo enable:" + enable
                + ",weakSignalMax4G:" + weakSignalMax4G + ",weakSignalMin4G:" + weakSignalMin4G
                + ",weakSignalMax3G:" + weakSignalMax3G + ",weakSignalMin3G:" + weakSignalMin3G
                + ",interval:" + interval);
        if (enable) {
            registerListen();
        } else {
            unRegisterListen();
        }
    }

    public int getDbm(int signalStrength) {
        int dBm;

        int level = signalStrength;
        int asu = (level == 99 ? Integer.MAX_VALUE : level);
        if (asu != Integer.MAX_VALUE) {
            dBm = -113 + (2 * asu);
        } else {
            dBm = Integer.MAX_VALUE;
        }
        JLog.logi(TAG, "getDbm=" + dBm);
        return dBm;
    }

    public void setPunishment(int time) {
        JLog.logi(TAG, "weak signal punishment time started:time:" + time);
        mPunishEnable = time != 0 ? true : false;
        stopPunishTimer();
        if (mPunishEnable) {
            stopWeakSignalTimer();
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
