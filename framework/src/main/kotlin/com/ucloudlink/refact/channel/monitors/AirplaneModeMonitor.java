package com.ucloudlink.refact.channel.monitors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.ucloudlink.refact.Framework;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEventId;
import com.ucloudlink.refact.utils.JLog;

import rx.Observable;
import rx.subjects.BehaviorSubject;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * 飞行模式监听
 */
public class AirplaneModeMonitor extends BroadcastReceiver {
    
    private static BehaviorSubject<Boolean> airplaneModeObser;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        JLog.logd("onReceive action=" + intent.getAction());
        if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {//飞行模式状态改变
            if (isAirPlaneModeOn(context)) {
                //开启飞行模式
                logd("AirplaneMode opened");
                getObservable(context).onNext(true);
                try {
                    ServiceManager.INSTANCE.getAccessEntry().notifyEvent(AccessEventId.EVENT_EXCEPTION_AIRMODE_OPEN);
                }catch (Exception e){
                    e.printStackTrace();
                }
            } else {
                //关闭飞行模式
                logd("AirplaneMode closed");
                getObservable(context).onNext(false);
                try {
                    ServiceManager.INSTANCE.getAccessEntry().notifyEvent(AccessEventId.EVENT_EXCEPTION_AIRMODE_CLOSE);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public static BehaviorSubject<Boolean> getObservable(Context context){
        if(airplaneModeObser == null){
            synchronized (AirplaneModeMonitor.class) {
                if(airplaneModeObser == null) {
                    airplaneModeObser = BehaviorSubject.create(isAirPlaneModeOn(context));
                }
            }
        }
        return airplaneModeObser;
    }

    /**
     * 判断当前状态是否为飞行模式
     */
    private static boolean isAirPlaneModeOn(Context context) {
        int mode = 0;
        try {
            mode = Settings.Global.getInt(context.getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mode == 1;//为1的时候是飞行模式
    }

}
