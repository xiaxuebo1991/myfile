package com.ucloudlink.refact.business.performancelog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.ucloudlink.refact.business.performancelog.logs.PerfLogTerAccess;

import java.util.concurrent.TimeUnit;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * Created by liuhaiping on 2018/1/1.
 * 终端接入事件定时器
 */

public class PerfLogTimeTask {
    private Context ctx;
    private AlarmManager alarmManager;
    private Intent actIntent;
    private PendingIntent sender;
    private FABroadcastRev faBRev = new FABroadcastRev();
    private boolean isRunning = false;
    private final static String FA_ACTION = "com.ucloudlink.perflog.time.task";
    private final static int REQUEST_CODE  = 10022;

    public PerfLogTimeTask(Context context) {
        ctx = context;
        alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
        actIntent = new Intent(FA_ACTION);
        sender = PendingIntent.getBroadcast(context, REQUEST_CODE, actIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * 开始执行
     * @param interval     执行时间,秒
     */
    public boolean start(int interval) {
        if (isRunning) {
            loge("PerfLogTimeTask already running!!");
            return false;
        }
        IntentFilter filter = new IntentFilter(FA_ACTION);
        ctx.registerReceiver(faBRev, filter);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(interval), sender);
        isRunning = true;
        logd("PerfLogTimeTask start: interval=" + interval);
        return true;
    }

    public void stop() {
        logd("PerfLogTimeTask stop!");
        if (isRunning) {
            isRunning = false;
            ctx.unregisterReceiver(faBRev);
            alarmManager.cancel(sender);
        }
    }

    public boolean isRunning() {
        logd("isRunning="+isRunning);
        return isRunning;
    }

    private class FABroadcastRev extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            logd("PerfLogTimeTask onReceive");
            //终端接入事件时间到
            PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_TIME_END(),0,"");

        }
    }
}
