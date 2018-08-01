package com.ucloudlink.refact.business.keepalive;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ucloudlink.refact.utils.JLog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jianguo.he on 2017/12/13.
 */

public class JobSchedulerUtils {

    public static final int JOB_SCHEDULER_ID = 1;

    public static void cancelJobScheduler(Context context){
        try{
            JobScheduler mJobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            JobSchedulerUtils.cancelJobScheduler(mJobScheduler);
        }catch (Throwable t){
            JLog.logi("Job, " + t.toString());
        }
    }

    private static void cancelJobScheduler(JobScheduler mJobScheduler) throws Throwable{
        JLog.logi("Job, cancelJobScheduler() ");
        List<JobInfo> allPendingJobs = mJobScheduler.getAllPendingJobs();
        if(allPendingJobs!=null && allPendingJobs.size() > 0){
            for(JobInfo info : allPendingJobs){
                if(info!=null && info.getId()==JOB_SCHEDULER_ID){
                    JLog.logi("Job, cancelJobScheduler() JOB_SCHEDULER_ID = "+JOB_SCHEDULER_ID);
                    mJobScheduler.cancel(JOB_SCHEDULER_ID);
                    break;
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void startJobScheduler(Context context, boolean isKeepLive, long intervalMillis){
        JLog.logi("Job, startJobScheduler enter ++");
        try{
            JobScheduler mJobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            JobSchedulerUtils.cancelJobScheduler(mJobScheduler);
            if(isKeepLive){
                JobInfo.Builder builder = new JobInfo.Builder(JobSchedulerUtils.JOB_SCHEDULER_ID, new ComponentName(context, AccessEntryJobService.class));
                builder.setPeriodic(intervalMillis);//设置间隔时间
                //builder.setRequiresDeviceIdle()
                builder.setPersisted(true);//设备重启之后任务是否还要继续执行
                mJobScheduler.schedule(builder.build());
                JLog.logi("Job, startJobScheduler start schedule ++");
            }

        }catch (Throwable t){
            JLog.logi("Job, " + t.toString());
        }
    }

    public static ArrayList<RemoteKeepLiveInfo> parseKeepLiveJsonStr(String keepLiveJsonStr){
        ArrayList<RemoteKeepLiveInfo> listAdd = null;
        if(!TextUtils.isEmpty(keepLiveJsonStr)){
            listAdd = new ArrayList<>();
            try{
                Gson gson = new Gson();
                JsonParser jsonParser = new JsonParser();
                JsonArray jsonArray = (JsonArray)jsonParser.parse(keepLiveJsonStr);
                Iterator<JsonElement> iterator = jsonArray.iterator();

                while (iterator.hasNext()){
                    JsonObject jsonObject = (JsonObject) iterator.next();
                    boolean isKeepLive = gson.fromJson(jsonObject.get("isKeepLive"), Boolean.class);
                    String packageName = gson.fromJson(jsonObject.get("packageName"), String.class);
                    String clsName = gson.fromJson(jsonObject.get("clsName"), String.class);
                    long delayTimeMillis = gson.fromJson(jsonObject.get("delayTimeMillis"), Long.class);

                    if(!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(clsName)){
                        listAdd.add(new RemoteKeepLiveInfo(packageName, clsName, delayTimeMillis, isKeepLive));
                    }
                }
            }catch (Exception e){
                JLog.logi("Job, handlerKeepLive Exception："+e.toString());
            }
        }
        return listAdd;
    }


    public static void runService(Context context, Class serviceCls){
        if(context == null){
            return;
        }
        if(!isServiceRunning(context, serviceCls)){
            JLog.logi("Job, service isRunning = false, reRuning by jobService");
            try{
                Intent _intent = new Intent(context.getApplicationContext(), serviceCls);
                context.startService(_intent);
            }catch (Exception e){
                JLog.loge("Job, " + e.toString());
            }
        }
    }

    public static void runService(Context context, String packageName, String serviceClsName){
        if(context == null || TextUtils.isEmpty(packageName)){
            return;
        }
        if(!isServiceRunning(context, serviceClsName)){
            try{
                Intent _intent = new Intent();
                _intent.setComponent(new ComponentName(packageName, serviceClsName));
                context.startService(_intent);
            }catch (Exception e){
                JLog.loge("Job, " + e.toString());
            }
        }
    }

    public static boolean isServiceRunning(Context context, Class serviceCls) {
        if(serviceCls==null)
            return false;
        return isServiceRunning(context, serviceCls.getName());
    }

    public static boolean isServiceRunning(Context context, String serviceClsName) {
        boolean isRunning = false;
        if(TextUtils.isEmpty(serviceClsName))
            return isRunning;

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList = null;
        try{
            serviceList = activityManager.getRunningServices(Integer.MAX_VALUE);
        }catch(Exception e){
            JLog.loge("Job, " + e.toString());
        }

        if (serviceList == null || serviceList.size() == 0) {
            return false;
        }

        int size = serviceList.size();
        for (int i = 0; i < size; i++) {
            if (serviceClsName.equals(serviceList.get(i).service.getClassName())) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }

}
