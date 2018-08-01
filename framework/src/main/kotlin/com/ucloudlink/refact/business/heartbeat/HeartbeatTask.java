package com.ucloudlink.refact.business.heartbeat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.ucloudlink.refact.access.AccessState;
import com.ucloudlink.refact.access.StateMessageId;

import java.util.concurrent.TimeUnit;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by shiqianhua on 2016/11/7.
 */
public class HeartbeatTask {
    private String TAG = "HeartbeatTask";
    private Context       ctx;
    private Intent        actIntent;
    private AlarmManager  alarmManager;
    private PendingIntent sender;
    private AccessState   accessState;
    private final static String HEARTBEAT_ACTION = "com.ucloudlink.refact.access.heartbeat";
    private HeartBeatBroadcastReceiver hbBR = new HeartBeatBroadcastReceiver();
    private boolean isRunning = false;

    private int after;
    private int repeat;

    public HeartbeatTask(Context context, AccessState as){
        ctx = context;
        accessState = as;
        alarmManager = (AlarmManager)context.getSystemService(Service.ALARM_SERVICE);
        actIntent = new Intent(HEARTBEAT_ACTION);
        sender = PendingIntent.getBroadcast(context, 1001, actIntent, PendingIntent.FLAG_CANCEL_CURRENT); // TODO: set param
    }

    public void start(int after, int repeat) {
        if (isRunning) {
            return;
        }
        IntentFilter filter = new IntentFilter(HEARTBEAT_ACTION);
        ctx.registerReceiver(hbBR, filter);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(after), TimeUnit.SECONDS.toMillis(repeat), sender);
        this.after = after;
        this.repeat = repeat;
        isRunning = true;
        logd( "start heartbeat, " + after + " " + repeat);
    }

    public void stop() {
        logd("stop heartbeat");
        if (isRunning) {
            isRunning = false;
            ctx.unregisterReceiver(hbBR);
            alarmManager.cancel(sender);
        }
    }

    public void refresh(){
        if(isRunning){
            logd( "refresh heartbeat");
            stop();
            start(after, repeat);
        }
    }

    public void refresh(int after, int repeat){
        if(isRunning){
            logd( "refresh heartbeat with param");
            stop();
            start(after, repeat);
        }
    }

    private class HeartBeatBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent){
            logd( "HeartBeatBroadcastReceiver recv msg");
            accessState.sendMessage(StateMessageId.USER_ALARM_HEART_BEAT_SEND_CMD);
        }
    }

}
