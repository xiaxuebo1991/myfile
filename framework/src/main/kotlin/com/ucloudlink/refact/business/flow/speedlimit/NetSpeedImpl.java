package com.ucloudlink.refact.business.flow.speedlimit;

import android.annotation.Nullable;
import android.net.NetworkPolicyManager;
import android.text.TextUtils;

import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

import static com.ucloudlink.framework.mbnload.MbnTestUtils.getPropertyValue;

/**
 * Created by jianguo.he on 2018/1/9.
 */

public class NetSpeedImpl implements INetSpeed {

    @Nullable
    @Override
    public String getCloudInterfaceName() {
        String strVal;
        strVal = getPropertyValue("net.rmnet_data0.dns1");
        JLog.logd("NetSpeedCtrlLog getCloudInterfaceName()_0: " + strVal);
        if (!TextUtils.isEmpty(strVal) && strVal.length() > FlowBandWidthControl.BWCC_IP_ADDR_MIN_LENG){
            strVal = "rmnet_data0";
        }else{
            strVal = getPropertyValue("net.rmnet_data1.dns1");
            JLog.logd("NetSpeedCtrlLog getCloudInterfaceName()_1: " + (strVal==null?"null":strVal));
            if (!TextUtils.isEmpty(strVal) && strVal.length() > FlowBandWidthControl.BWCC_IP_ADDR_MIN_LENG){
                strVal = "rmnet_data1";
            }
        }
        JLog.logd("NetSpeedCtrlLog, getCloudInterfaceName() return: " + (strVal==null?"null":strVal));
        return strVal;
    }

    @Override
    public int setInterfaceThrottle(@Nullable String ifName, long rxKbps, long txKbps) {// void
        JLog.logd("NetSpeedCtrlLog, setInterfaceThrottle(): ifName = " + (ifName==null?"null":ifName)+", rxKbps = "+rxKbps+", txKbps = "+txKbps);
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setInterfaceThrottle", new Class[]{String.class, long.class, long.class});
                method.invoke(npm, ifName, rxKbps, txKbps);
                ret = 1;
            }catch (Exception e){
                JLog.loge("NetSpeedCtrlLog setInterfaceThrottle Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, setInterfaceThrottle(): return " + ret);
        return ret;
    }

    @Override
    public int resetInterfaceThrottle(@Nullable String ifName, long rxKbps, long txKbps) {// void
        JLog.logd("NetSpeedCtrlLog, resetInterfaceThrottle(): ifName = " + (ifName==null?"null":ifName)+", rxKbps = "+rxKbps+", txKbps = "+txKbps);
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("resetInterfaceThrottle", new Class[]{String.class, long.class, long.class});
                method.invoke(npm, ifName, rxKbps, txKbps);
                ret = 1;
            }catch (Exception e){
                JLog.loge("NetSpeedCtrlLog resetInterfaceThrottle Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, resetInterfaceThrottle(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowPermitByPassIpstr(@Nullable String ip) {//int
        JLog.logd("NetSpeedCtrlLog, setFlowPermitByPassIpstr(): ip = " + (ip==null?"null":ip));
        int ret = -1;
        if(!TextUtils.isEmpty(ip)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowPermitByPassIpstr", new Class[]{String.class});
                ret = (int)method.invoke(npm, ip);
            }catch (Exception e){
                JLog.loge("NetSpeedCtrlLog setFlowPermitByPassIpstr Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, setFlowPermitByPassIpstr(): return " + ret);
        return ret;
    }

    @Override
    public int clearFlowPermitByPassIpstr(@Nullable String ip) {// int
        JLog.logd("NetSpeedCtrlLog, clearFlowPermitByPassIpstr(): ip = " + ip);
        int ret = -1;
        if(!TextUtils.isEmpty(ip)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("clearFlowPermitByPassIpstr", new Class[]{String.class});
                ret = (int)method.invoke(npm, ip);
            }catch (Exception e){
                JLog.loge("NetSpeedCtrlLog clearFlowPermitByPassIpstr Exception: "+e);
            }
        }
        JLog.logd("NetSpeedCtrlLog, clearFlowPermitByPassIpstr(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowPermitByPassUid(int uid) {// void
        int ret = -1;
        JLog.logd("NetSpeedCtrlLog, setFlowPermitByPassUid(): uid = " + uid);
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setFlowPermitByPassUid", new Class[]{int.class});
            method.invoke(npm, uid);
            ret = 1;
        }catch (Exception e){
            JLog.loge("NetSpeedCtrlLog setFlowPermitByPassUid Exception: "+e);
        }
        JLog.logd("NetSpeedCtrlLog, setFlowPermitByPassUid(): return " + ret);
        return ret;
    }

    @Override
    public int clearFlowPermitByPassUid(int uid) {//void
        int ret = -1;
        JLog.logd("NetSpeedCtrlLog, clearFlowPermitByPassUid(): uid = " + uid);
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("clearFlowPermitByPassUid", new Class[]{int.class});
            method.invoke(npm, uid);
            ret = 1;
        }catch (Exception e){
            JLog.loge("NetSpeedCtrlLog clearFlowPermitByPassUid Exception: "+e);
        }
        JLog.logd("NetSpeedCtrlLog, clearFlowPermitByPassUid(): return " + ret);
        return ret;
    }

    @Override
    public int getIfaceThrottleStates(@org.jetbrains.annotations.Nullable String ifName) {
        return 0;
    }

    @NotNull
    @Override
    public String[] getIfaceSpeed(@org.jetbrains.annotations.Nullable String ifName) {
        return new String[0];
    }
}
