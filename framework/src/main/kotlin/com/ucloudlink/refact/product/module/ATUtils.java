package com.ucloudlink.refact.product.module;

import android.net.NetworkPolicyManager;
import android.text.TextUtils;

import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.utils.JLog;

import java.lang.reflect.Method;

/**
 * Created by zhifeng.gao on 2018/6/8.
 */

public class ATUtils {
    //获取限速状态
    public  static int getQosState(String ifName){
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("getIfaceThrottleStates", new Class[]{String.class});
                JLog.logd("NetSpeedCtrlLog, getIfaceThrottleStates() -> getMethod() -> "+method.toString());
                ret = (int)method.invoke(npm, ifName);
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("NetSpeedCtrlLog getIfaceThrottleStates Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, getIfaceThrottleStates(): return " + ret);
        return ret;
    }

    //获取网口速率
    public static String[] getIfaceSpeed(String ifName){
        JLog.logd("NetSpeedCtrlLog, getIfaceSpeed(): ifName = " + (ifName==null?"null":ifName));
        String[] ret = null;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("getIfaceSpeed", new Class[]{String.class});
                JLog.logd("NetSpeedCtrlLog, getIfaceSpeed() -> getMethod() -> "+method.toString());
                ret = (String[])method.invoke(npm, ifName);
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("NetSpeedCtrlLog getIfaceSpeed Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, getIfaceSpeed(): return " + ret);
        return ret;
    }

    public static int setQosState(String ifName,int rxKbps,int txKbps){
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setIfaceThrottle", new Class[]{String.class, long.class, long.class});
                JLog.logi("NetSpeedCtrlLog, setInterfaceThrottle() -> getMethod() -> "+method.toString());
                method.invoke(npm, ifName, rxKbps, txKbps);
                ret = 1;
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("NetSpeedCtrlLog setInterfaceThrottle Exception: "+e);
            }
        }
        JLog.logi("NetSpeedCtrlLog, setInterfaceThrottle(): return " + ret);
        return ret;
    }
}
