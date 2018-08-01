package com.ucloudlink.refact.business.frequentauth;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.ucloudlink.refact.utils.JLog;

import java.util.concurrent.TimeUnit;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * Created by liuhaiping on 2018/1/1.
 */

public class FrequentAuthPingTask {
    private Context ctx;
    private AlarmManager alarmManager;
    private Intent actIntent;
    private PendingIntent sender;
    private FABroadcastRev faBRev = new FABroadcastRev();
    private boolean isRunning = false;
    private final static String FA_ACTION = "com.ucloudlink.frequentauth.ping";
    private int interval = 0;
    private String addr = "";

    public FrequentAuthPingTask(Context context) {
        ctx = context;
        alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
        actIntent = new Intent(FA_ACTION);
        sender = PendingIntent.getBroadcast(context, 10012, actIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * 开始执行
     *
     * @param interval     执行时间,秒
     * @param addr 地址
     */
    public void start(int interval,String addr) {
        if (isRunning) {
            loge("FrequentAuthPingTask already running!!");
            return;
        }
        if (interval<=0){
            loge("FrequentAuthPingTask interval<=0!!");
            return;
        }
        if (addr.length() <=0 ){
            loge("FrequentAuthPingTask addr.length() <=0!");
            return;
        }

        this.interval = interval;
        this.addr = addr;
        IntentFilter filter = new IntentFilter(FA_ACTION);
        ctx.registerReceiver(faBRev, filter);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP,System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(interval),sender);
//        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(interval), sender);
        isRunning = true;
        logd("FrequentAuthPingTask start: interval=" + interval);
    }

    public void stop() {
        logd("FrequentAuthPingTask stop!");
        if (isRunning) {
            isRunning = false;
            ctx.unregisterReceiver(faBRev);
            alarmManager.cancel(sender);
            interval = 0;
            addr = "";
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    private class FABroadcastRev extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            JLog.logd("onReceive action=" + intent.getAction());
            logd("FrequentAuthPingTask onReceive,interval="+interval);
            isRunning = false;
            if (interval>0){
                start(interval,addr);
                FrequentAuth.INSTANCE.pingOnetime(addr);
            }
        }
    }
}
