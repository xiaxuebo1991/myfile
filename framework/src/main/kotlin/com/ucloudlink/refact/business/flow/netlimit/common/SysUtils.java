package com.ucloudlink.refact.business.flow.netlimit.common;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.NetworkPolicyManager;
import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;

import java.lang.reflect.Method;


/**
 * Created by jianguo.he on 2017/11/24.
 */

public class SysUtils {

    public static int getUServiceUid(){
        return getAppUid(ServiceManager.appContext.getPackageName());
    }

    /**
     * 获取本APP的UID，便于获取APP消耗流量
     */
    public static int getAppUid(String packageName){
        int uid = 0;
        if(TextUtils.isEmpty(packageName)){
            return uid;
        }
        try {
            PackageManager pm = ServiceManager.appContext.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            uid = ai.uid;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        JLog.logd("getAppuid is " + uid);
        return uid;
    }

    /**
     * 反射方法名，判断是否为 uid + ip 限制种子卡网络的版本
     * @return
     */
    public static boolean isFrameworkSupportSeedNetworkLimitByUidAndIp(){
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setRestrictAllNetworks", new Class[]{String.class});
            if(method!=null && !TextUtils.isEmpty(method.getName())){
                JLog.logi("SeedCardNetLog, isFrameworkNewNet returnt true ");
                return true;
            }
        }catch (Exception e){
            JLog.logi("SeedCardNetLog, isFrameworkNewNet exception: "+e.toString());
        }
        JLog.logi("SeedCardNetLog, isFrameworkNewNet returnt false ");
        return false;
    }

    public static NetworkPolicyManager getNetworkPolicyManager(){
        try{
            Object obj = NetworkPolicyManager.from(ServiceManager.appContext);
            if(obj!=null){
                return (NetworkPolicyManager) obj;
            }
        }catch (Exception e){
            JLog.loge("Get NetworkPolicyManager Exception: "+e);
        }
        return null;
    }
}
