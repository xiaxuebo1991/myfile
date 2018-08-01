package com.ucloudlink.refact.business.keepalive;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jianguo.he on 2017/12/13.
 */

public class JobSchedulerCtrl {

    public static final boolean SWITCH_KEEP_LIVE = true;

    private int MIN_KEEP_LIVE_DELAY_TIME = 15 * 1000;

    private String KEEP_LIVE_JSON_STR = "KEEP_LIVE_JSON_STR";
    private HashMap<String, RemoteKeepLiveInfo> mapRemoteKeepLiveInfo = new HashMap<>();
    private HashMap<String, Runnable> mapRunnable = new HashMap<>();

    private Handler mHandler = new Handler();
    private Context mContext;

    public void onStartCommand(Intent intent, Service service){
        mContext = service.getApplicationContext();
        handlerKeepLive(intent!=null ? intent.getStringExtra(KEEP_LIVE_JSON_STR) : null);

//        boolean isKeepLive = SWITCH_KEEP_LIVE;
//        if(intent!=null)s
//            isKeepLive = intent.getBooleanExtra("keep_live", isKeepLive);
//        JobSchedulerUtils.startJobScheduler(service.getApplicationContext(), isKeepLive);
//        JLog.logi("Job","app keep_live="+isKeepLive+", currentTimeMillis:"+System.currentTimeMillis());

    }

    public void onDestroy(){
        mapRunnable.clear();
        mapRemoteKeepLiveInfo.clear();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void handlerKeepLive(String keepLiveJsonStr){
        JLog.logi("Job, handlerKeepLive keepLiveJsonStr："+(keepLiveJsonStr==null?"null":keepLiveJsonStr));
        ArrayList<RemoteKeepLiveInfo> listAdd = JobSchedulerUtils.parseKeepLiveJsonStr(keepLiveJsonStr);
        if(listAdd!=null && listAdd.size() > 0){
            for(RemoteKeepLiveInfo info : listAdd){
                if(info!=null && !TextUtils.isEmpty(info.packageName) && !TextUtils.isEmpty(info.clsName)){
                    Runnable runnable = mapRunnable.get(info.packageName + info.clsName);
                    if(runnable!=null){
                        mHandler.removeCallbacks(runnable);
                    }
                    mapRunnable.remove(info.packageName + info.clsName);

                    if(!info.isKeepLive){
                        mapRemoteKeepLiveInfo.remove(info.packageName + info.clsName);
                    } else {
                        mapRemoteKeepLiveInfo.put(info.packageName + info.clsName, new RemoteKeepLiveInfo(info.packageName, info.clsName, info.delayTimeMillis, info.isKeepLive));
                        Runnable r = new KeepLiveRunnable(info.packageName, info.clsName, info.delayTimeMillis);
                        mapRunnable.put(info.packageName + info.clsName, r);
                        mHandler.postDelayed(r, Math.max(MIN_KEEP_LIVE_DELAY_TIME, info.delayTimeMillis));
                    }
                }
            }

            try{
                Gson gson = new Gson();
                String str = gson.toJson(mapRemoteKeepLiveInfo.values());
                SharedPreferencesUtils.putString(mContext, KEEP_LIVE_JSON_STR, str);
            }catch (Exception e){
                JLog.logi("Job, handlerKeepLive save JSON to SP Exception："+e.toString());
            }

        }
    }

    class KeepLiveRunnable implements Runnable {

        private String packageName;
        private String clsName;
        private long delayTimeMillis;

        public KeepLiveRunnable(String packageName, String clsName, long delayTimeMillis){
            this.packageName = packageName;
            this.clsName = clsName;
            this.delayTimeMillis = delayTimeMillis;
        }

        @Override
        public void run() {
            JLog.logi("Job, KeepLiveRunnable run packageName = "+(packageName==null?"null":packageName)
                    +", clsName = "+(clsName==null?"null":clsName));
            JobSchedulerUtils.runService(mContext/*AccessEntryService.this.getApplicationContext()*/, packageName, clsName);
            mHandler.removeCallbacks(KeepLiveRunnable.this);
            if(!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(clsName)){
                mapRunnable.remove(packageName + clsName);
                //Runnable r = new KeepLiveRunnable(packageName, clsName, delayTimeMillis);
                mapRunnable.put(packageName + clsName, KeepLiveRunnable.this);
                mHandler.postDelayed(KeepLiveRunnable.this, Math.max(MIN_KEEP_LIVE_DELAY_TIME, delayTimeMillis));
            }
        }
    }

}
