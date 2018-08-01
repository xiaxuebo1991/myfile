package com.ucloudlink.refact.platform.sprd.flow;

import android.net.NetworkPolicyManager;
import android.text.TextUtils;

import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.business.flow.speedlimit.INetSpeed;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

import static com.ucloudlink.framework.mbnload.MbnTestUtils.getPropertyValue;

/**
 * Created by jianguo.he on 2018/2/2.
 */
public class SprdU3CNetSpeedImpl implements INetSpeed {


    @Override
    public int getIfaceThrottleStates(String ifName) {
        JLog.logi("NetSpeedCtrlLog, getIfaceThrottleStates(): ifName = " + (ifName==null?"null":ifName));
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("getIfaceThrottleStates", new Class[]{String.class});
                JLog.logi("NetSpeedCtrlLog, getIfaceThrottleStates() -> getMethod() -> "+method.toString());
                ret = (int)method.invoke(npm, ifName);
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("NetSpeedCtrlLog getIfaceThrottleStates Exception: "+e);
            }
        }
        JLog.logi("NetSpeedCtrlLog, getIfaceThrottleStates(): return " + ret);
        return ret;
    }

    @NotNull
    @Override
    public String[] getIfaceSpeed(String ifName) {
        JLog.logi("NetSpeedCtrlLog, getIfaceSpeed(): ifName = " + (ifName==null?"null":ifName));
        String[] ret = null;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("getIfaceSpeed", new Class[]{String.class});
                JLog.logi("NetSpeedCtrlLog, getIfaceSpeed() -> getMethod() -> "+method.toString());
                ret = (String[])method.invoke(npm, ifName);
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("NetSpeedCtrlLog getIfaceSpeed Exception: "+e);
            }
        }
        JLog.logi("NetSpeedCtrlLog, getIfaceSpeed(): return " + ret);
        return ret;
    }

    @Nullable
    @Override
    public String getCloudInterfaceName() {
        String strVal;
        String key = "";
        strVal = getPropertyValue(key = "net.seth_lte0.dns1");
        if(TextUtils.isEmpty(strVal)){
            strVal = getPropertyValue(key = "net.seth_lte0.dns2");
        }
        JLog.logi("NetSpeedCtrlLog getCloudInterfaceName()_0: " + (strVal==null?"null":strVal)+", key = "+ key);
        if (!TextUtils.isEmpty(strVal) && strVal.length() > FlowBandWidthControl.BWCC_IP_ADDR_MIN_LENG){
            strVal = "seth_lte0";
        }else{
            strVal = getPropertyValue(key = "net.seth_lte1.dns1");
            if(TextUtils.isEmpty(strVal)){
                strVal = getPropertyValue(key = "net.seth_lte1.dns2");
            }
            JLog.logi("NetSpeedCtrlLog getCloudInterfaceName()_1: " + (strVal==null?"null":strVal)+", key = "+key);
            if (!TextUtils.isEmpty(strVal) && strVal.length() > FlowBandWidthControl.BWCC_IP_ADDR_MIN_LENG){
                strVal = "seth_lte1";
            }
        }
        JLog.logi("NetSpeedCtrlLog, getCloudInterfaceName() return: " + (strVal==null?"null":strVal)+", key = "+key);
        return strVal;
    }

    @Override
    public int setInterfaceThrottle(@Nullable String ifName, long rxKbps, long txKbps) {
        JLog.logi("NetSpeedCtrlLog, setInterfaceThrottle(): ifName = " + (ifName==null?"null":ifName)+", rxKbps = "+rxKbps+", txKbps = "+txKbps);
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

    @Override
    public int resetInterfaceThrottle(@Nullable String ifName, long rxKbps, long txKbps) {
        JLog.logi("NetSpeedCtrlLog, resetInterfaceThrottle():before)-> ifName = " + (ifName==null?"null":ifName)+", rxKbps = "+rxKbps+", txKbps = "+txKbps);
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("clearIfaceThrottle", new Class[]{String.class});
                JLog.logi("NetSpeedCtrlLog, resetInterfaceThrottle() -> getMethod() -> "+method.toString());
                method.invoke(npm, ifName);
                ret = 1;
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("NetSpeedCtrlLog resetInterfaceThrottle Exception: "+e);
            }
        }
        JLog.logi("NetSpeedCtrlLog, resetInterfaceThrottle(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowPermitByPassIpstr(@Nullable String ip) {
        JLog.logd("NetSpeedCtrlLog, setFlowPermitByPassIpstr(): ip = " + (ip==null?"null":ip));
        int ret = -1;
        if(!TextUtils.isEmpty(ip)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("addWhiteList", String.class);
                ret = (int)method.invoke(npm, ip);
            }catch (Exception e){
                JLog.loge("NetSpeedCtrlLog setFlowPermitByPassIpstr Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, setFlowPermitByPassIpstr(): return " + ret);
        return ret;
    }

    @Override
    public int clearFlowPermitByPassIpstr(@Nullable String ip) {
        JLog.logd("NetSpeedCtrlLog, clearFlowPermitByPassIpstr(): ip = " + ip);
        int ret = -1;
        if(!TextUtils.isEmpty(ip)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("removeWhiteList", String.class);
                ret = (int)method.invoke(npm, ip);
            }catch (Exception e){
                JLog.loge("NetSpeedCtrlLog clearFlowPermitByPassIpstr Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, clearFlowPermitByPassIpstr(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowPermitByPassUid(int uid) {
        return 0;
    }

    @Override
    public int clearFlowPermitByPassUid(int uid) {
        return 0;
    }

}
