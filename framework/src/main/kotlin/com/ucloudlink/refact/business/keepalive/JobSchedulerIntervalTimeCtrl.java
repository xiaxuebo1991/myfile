package com.ucloudlink.refact.business.keepalive;

import android.provider.Settings;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.channel.monitors.AirplaneModeMonitor;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.utils.JLog;

import rx.functions.Action1;

/**
 * Created by jianguo.he on 2017/12/14.
 */

public class JobSchedulerIntervalTimeCtrl {

    private static JobSchedulerIntervalTimeCtrl instance;

    /** 保活触发时间间隔 */
    private static final long INTERVAL_MILLIS = 10 * 60 * 1000;//10 * 1000;
    /** 保活触发时间最大递增次数 */
    private static final int MAX_ITERATOR_COUNT = 6;
    /** 飞行模式或来电模式导致保活时间延长递增次数后，恢复正常限制，主要避免过长时间 */
    private static final int MAX_RESTORE_ITERATOR_COUNT = 2;
    /** 保活触发执行次数 */
    private int mOnStartJobCount = 1;

    private long curIntervalMillis = INTERVAL_MILLIS;
    private boolean isRunState = false;
    private boolean isPhoneCalling = false;
    private boolean isAirPlaneMode = false;

    private JobSchedulerIntervalTimeCtrl(){
        isPhoneCalling = CardStateMonitor.mCallState;
        isAirPlaneMode = isAirPlaneMode();
    }

    public static JobSchedulerIntervalTimeCtrl getInstance(){
        if(instance==null){
            synchronized (JobSchedulerIntervalTimeCtrl.class){
                if (instance==null){
                    instance = new JobSchedulerIntervalTimeCtrl();
                }
            }
        }
        return instance;
    }

    public void initListener(){
        ServiceManager.simMonitor.planeCallObser.asObservable().subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean aBoolean) {
                isPhoneCalling = aBoolean;
                if(!isPhoneCalling && !isAirPlaneMode){
                    mOnStartJobCount = 1;
                    if(isRunState && curIntervalMillis > MAX_RESTORE_ITERATOR_COUNT * INTERVAL_MILLIS){
                        JobSchedulerUtils.startJobScheduler(ServiceManager.appContext
                                , JobSchedulerCtrl.SWITCH_KEEP_LIVE, getIntervalMillis("PhoneCall.Observable"));
                    }
                }
            }
        });
        AirplaneModeMonitor.getObservable(ServiceManager.appContext).subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean aBoolean) {
                isAirPlaneMode = aBoolean;
                if(!isPhoneCalling && !isAirPlaneMode){
                    mOnStartJobCount = 1;
                    if(isRunState && curIntervalMillis > MAX_RESTORE_ITERATOR_COUNT * INTERVAL_MILLIS){
                        JobSchedulerUtils.startJobScheduler(ServiceManager.appContext
                                , JobSchedulerCtrl.SWITCH_KEEP_LIVE, getIntervalMillis("AirPlaneMode.Observable"));
                    }
                }
            }
        });
    }

    public void setOnStartJobCount(int value, String tag){
        JLog.logi("Job, mOnStartJobCount.set(value = "+value+"), mOnStartJobCount = "+mOnStartJobCount
                +", tag = " + (tag==null?"null":tag) + ", isPhoneCalling = " + isPhoneCalling + ", isAirPlaneMode = " + isAirPlaneMode);
        if(isPhoneCalling || isAirPlaneMode){
            if(value > MAX_ITERATOR_COUNT){
                value = MAX_ITERATOR_COUNT;
            }

            if(value<= 0 ){
                value = 1;
            }
            mOnStartJobCount = value;
        }

    }

    public int getOnStartJobCount(){
        return mOnStartJobCount;
    }

    public long getIntervalMillis(String tag){
        if(isPhoneCalling || isAirPlaneMode){
            if(mOnStartJobCount <= 0 ){
                mOnStartJobCount = 1;
            }
            curIntervalMillis = INTERVAL_MILLIS * mOnStartJobCount;

        } else {
            curIntervalMillis = INTERVAL_MILLIS;
        }
        JLog.logi("Job, getIntervalMillis()-> mOnStartJobCount = "+mOnStartJobCount
                + ", isPhoneCalling = " + isPhoneCalling + ", isAirPlaneMode = " + isAirPlaneMode
                + ", curIntervalMillis = " + (curIntervalMillis/1000)+"s" + ", tag = " + (tag==null?"null":tag));
        return curIntervalMillis;
    }

    public void setRunState(boolean isRunState){
        this.isRunState = isRunState;
    }

    private boolean isAirPlaneMode(){
        // air mode
        int mode = 0;
        try {
            mode = Settings.Global.getInt(ServiceManager.appContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mode == 1;
    }

}
