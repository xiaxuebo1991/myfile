package com.ucloudlink.refact.business.performancelog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.ucloudlink.refact.business.performancelog.logs.PerfLogVsimMR;

import java.util.concurrent.TimeUnit;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * @author haiping.liu
 */
public class PerfLogRepeatTimeTask {
    private Context ctx;
    private AlarmManager alarmManager;
    private Intent actIntent;
    private PendingIntent sender;
    private PerfLogRepeatBR perfRepeatBr = new PerfLogRepeatBR();
    private int count = 0;

    private boolean isRunning = false;
    private final static String FA_ACTION = "com.ucloudlink.perflog.repeat.time.task";
    private final static int REQUEST_CODE  = 10032;

    public PerfLogRepeatTimeTask(Context context) {
        ctx = context;
        alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
        actIntent = new Intent(FA_ACTION);
        sender = PendingIntent.getBroadcast(context, REQUEST_CODE, actIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * 开始执行
     *
     * @param interval     间隔时间,秒
     */
    public void start(int interval) {
        if (isRunning) {
            loge("PerfLogRepeatTimeTask already running!!");
            return;
        }
        IntentFilter filter = new IntentFilter(FA_ACTION);
        ctx.registerReceiver(perfRepeatBr, filter);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(interval), TimeUnit.SECONDS.toMillis(interval), sender);
        isRunning = true;

        logd("PerfLogRepeatTimeTask start: interval=" + interval);
    }

    public void stop() {
        logd("PerfLogRepeatTimeTask stop!");
        if (isRunning) {
            isRunning = false;
            ctx.unregisterReceiver(perfRepeatBr);
            alarmManager.cancel(sender);
            count = 0;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    private class PerfLogRepeatBR extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            count++;
            logd("PerfLogRepeatBR recv, count="+count);
            PerfLogVsimMR.INSTANCE.create(PerfLogVsimMR.INSTANCE.getID_TIME_TASK_REPEAT(),0,"");
        }
    }
}
