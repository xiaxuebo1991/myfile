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
 * Created by liuhaiping on 2018/1/1
 *
 * 灭屏1分钟+ 热点关闭 = 停止ping
 */

public class FrequentAuthScreenTask {
    private Context ctx;
    private AlarmManager alarmManager;
    private Intent actIntent;
    private PendingIntent sender;
    private FABroadcastRev faBRev = new FABroadcastRev();
    private boolean isRunning = false;
    private final static String FA_ACTION = "com.ucloudlink.frequentauth.screen";
    private int interval = 0;

    public FrequentAuthScreenTask(Context context) {
        ctx = context;
        alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
        actIntent = new Intent(FA_ACTION);
        sender = PendingIntent.getBroadcast(context, 10013, actIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * 开始执行
     *
     * @param interval     执行时间,秒
     */
    public void start(int interval) {
        if (isRunning) {
            loge("------ already running!!");
            return;
        }
        if (interval<=0){
            loge("------ interval<=0!!");
            return;
        }

        this.interval = interval;
        IntentFilter filter = new IntentFilter(FA_ACTION);
        ctx.registerReceiver(faBRev, filter);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP,System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(interval),sender);
        isRunning = true;
        logd("------start: interval=" + interval);
    }

    public void stop() {
        logd("------stop!");
        if (isRunning) {
            isRunning = false;
            ctx.unregisterReceiver(faBRev);
            alarmManager.cancel(sender);
            interval = 0;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    private class FABroadcastRev extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            JLog.logd("onReceive action=" + intent.getAction());
            logd("------onReceive,interval="+interval);
            isRunning = false;
            FrequentAuth.INSTANCE.screenTaskRev();
        }
    }
}
