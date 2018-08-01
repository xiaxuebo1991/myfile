package com.ucloudlink.ucapp;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;

import com.ucloudlink.refact.business.log.logcat.LogcatHelper;
import com.ucloudlink.refact.access.restore.ServiceRestore;

import java.lang.reflect.Method;
import java.util.List;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;

/**
 * Created by chentao on 2016/7/8.
 */
public class UCApp extends Application {
    private static Context ontext;
    private String  packgeName;


    @Override
    public void onCreate() {
        super.onCreate();
        UCApp.ontext = getApplicationContext();
        try{
            // 使用反射调用是因为如果不使用加固，则会找不到该类，详看：gradle.properties
            Class cls = Class.forName("com.ucloudlink.safety4fw.AppLibsMap");
            Method method = cls.getDeclaredMethod("environmentInit", Context.class);
            Object methodObject = cls.newInstance();
            method.invoke(methodObject,this.getApplicationContext());
            loge("desLib","des success.");
        }catch (Exception e){
            loge("desLib","des fail. exception: \n"+e.toString());
        }

//        initXlog(this);//初始化xlog
        LogcatHelper.getInstance(this).start();//处理保存crash日志
        String versionName="unknow";
        try {
            PackageManager manager=getPackageManager();
            PackageInfo packageInfo = manager.getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
            packgeName = getPackageName();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        logi("UCAPP onCreate current UIversion:"+versionName);



//        ExceptionHandler.INSTANCE.setServerExceptionListener(new ExceptionCallback() {
//            @Override
//            public void serverError(@NotNull final Ept_Event event) {
//                Handler sHandler = new Handler(Looper.getMainLooper());
//                sHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        ServerExceptionHandler.getInstance().exceptionHandle(event);
//                    }
//                });
//            }
//        });

        NoticeCtrl.init(this);

//        DetectAndStartInitActivity();
        logi("UCAPP onCreate end");

    }

    public static Context getAppContext() {
        return UCApp.ontext;
    }

    @Deprecated
    private void DetectAndStartInitActivity(){
//        if(SharedPreferencesUtils.getBoolean(this,"isExceptionStart")){//如果有异常关闭，且不是重启手机，则重新启动activity
        if(ServiceRestore.isExceptionStart(this)){//如果有异常关闭，且不是重启手机，则重新启动activity
            logd("UCAPP onCreate current isExceptionStart: ture");

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    logd("UCAPP Handler().postDelayed start");
                    Intent intent = new Intent();
                    intent.setClassName(packgeName, packgeName + ".InitActivity");//设置程序入口

                    if(!isActivityRunning(ontext, packgeName + ".InitActivity")) {
                        // 说明系统中这个activity不是活动状态
                        logd("UCAPP InitActivity: is not active");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }else {
                        logd("UCAPP InitActivity: active");
                    }
                }
            }, 1000);
        }else {
            logd("UCAPP onCreate current isExceptionStart: false");
        }
    }

    public static boolean isActivityRunning(Context mContext, String activityClassName){
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> info = activityManager.getRunningTasks(1);
        if(info != null && info.size() > 0){
            ComponentName component = info.get(0).topActivity;
            if(activityClassName.equals(component.getClassName())){
                return true;
            }
        }
        return false;
    }
}
