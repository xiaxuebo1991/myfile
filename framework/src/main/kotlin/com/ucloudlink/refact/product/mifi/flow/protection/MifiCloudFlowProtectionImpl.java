package com.ucloudlink.refact.product.mifi.flow.protection;

import android.net.NetworkPolicyManager;
import android.text.TextUtils;

import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.business.flow.protection.ICloudFlowProtection;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Created by jianguo.he on 2018/2/6.
 */

public class MifiCloudFlowProtectionImpl implements ICloudFlowProtection {

    @Override
    public void enableFlowfilter() {
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("enableFlowfilter");
            method.invoke(npm);
            JLog.loge("CloudFlowProtectionLog enableFlowfilter call success! ");
        }catch (Exception e){
            e.printStackTrace();
            JLog.loge("CloudFlowProtectionLog enableFlowfilter Exception: "+e.toString());
        }
    }

    @Override
    public void disableFlowfilter() {
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("disableFlowfilter");
            method.invoke(npm);
            JLog.loge("CloudFlowProtectionLog disableFlowfilter call success! ");
        }catch (Exception e){
            JLog.loge("CloudFlowProtectionLog disableFlowfilter Exception: "+e.toString());
        }
    }

    @Override
    public boolean isFlowfilterEnabled() {
        boolean ret = false;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("isFlowfilterEnabled");
            ret = (boolean)method.invoke(npm);
        }catch (Exception e){
            JLog.loge("CloudFlowProtectionLog isFlowfilterEnabled Exception: "+e.toString());
        }
        JLog.logi("CloudFlowProtectionLog, isFlowfilterEnabled(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowfilterDomainRule(@Nullable String domain, @Nullable String protocol, int port, int type, boolean allow) {
        JLog.logi("CloudFlowProtectionLog, setFlowfilterDomainRule(): domain = " + (domain==null?"null":domain)
                +", protocol = "+(protocol==null?"null":protocol)
                +", port = "+port +", type = "+type+", allow = "+allow);
        int ret = -1;
        if(!TextUtils.isEmpty(domain) && !TextUtils.isEmpty(protocol)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowfilterDomainRule", new Class[]{String.class, String.class, int.class, int.class,boolean.class});
                ret = (int)method.invoke(npm, domain, protocol, port, type, allow);
            }catch (Exception e){
                e.printStackTrace();
                JLog.loge("CloudFlowProtectionLog setFlowfilterDomainRule Exception: "+e.toString());
            }
        }
        JLog.logi("CloudFlowProtectionLog, setFlowfilterDomainRule(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowfilterMacRule(@Nullable String mac, boolean allow) {
        JLog.logi("CloudFlowProtectionLog, setFlowfilterMacRule(): mac = " + (mac==null?"null":mac) + ", allow = "+allow);
        int ret = -1;
        if(!TextUtils.isEmpty(mac)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowfilterMacRule", new Class[]{String.class, boolean.class});
                ret = (int)method.invoke(npm, mac, allow);
            }catch (Exception e){
                JLog.loge("CloudFlowProtectionLog setFlowfilterMacRule Exception: "+e.toString());
            }
        }
        JLog.logi("CloudFlowProtectionLog, setFlowfilterMacRule(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowfilterInterfaceRule(@Nullable String ifName, boolean allow) {
        JLog.logi("CloudFlowProtectionLog, setFlowfilterInterfaceRule(): ifName = " + (ifName==null?"null":ifName) + ", allow = "+allow);
        int ret = -1;
        if(!TextUtils.isEmpty(ifName)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowfilterInterfaceRule", new Class[]{String.class, boolean.class});
                ret = (int)method.invoke(npm, ifName, allow);
            }catch (Exception e){
                JLog.loge("CloudFlowProtectionLog setFlowfilterInterfaceRule Exception: "+e.toString());
            }
        }
        JLog.logi("CloudFlowProtectionLog, setFlowfilterInterfaceRule(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowfilterAddrRule(@Nullable String addr, boolean allow) {
        JLog.logi("CloudFlowProtectionLog, setFlowfilterAddrRule(): addr = " + (addr==null?"null":addr) + ", allow = "+allow);
        int ret = -1;
        if(!TextUtils.isEmpty(addr)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowfilterAddrRule", new Class[]{String.class, boolean.class});
                ret = (int)method.invoke(npm, addr, allow);
            }catch (Exception e){
                JLog.loge("CloudFlowProtectionLog setFlowfilterAddrRule Exception: "+e.toString());
            }
        }
        JLog.logi("CloudFlowProtectionLog, setFlowfilterAddrRule(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowfilterPortRule(@Nullable String protocol, int port, boolean allow) {
        JLog.logi("CloudFlowProtectionLog, setFlowfilterPortRule(): protocol = "+(protocol==null?"null":protocol)+", port = "+port+", allow = "+allow);
        int ret = -1;
        if(!TextUtils.isEmpty(protocol)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowfilterPortRule", new Class[]{String.class, int.class, boolean.class});
                ret = (int)method.invoke(npm, protocol, port, allow);
            }catch (Exception e){
                JLog.loge("CloudFlowProtectionLog setFlowfilterPortRule Exception: "+e.toString());
            }
        }
        JLog.logi("CloudFlowProtectionLog, setFlowfilterPortRule(): return " + ret);
        return ret;
    }

    @Override
    public int setFlowfilterEgressDestRule(@Nullable String addr, @Nullable String protocol, int port, boolean allow) {
        JLog.logi("CloudFlowProtectionLog, setFlowfilterEgressDestRule(): addr = "+(addr==null?"null":addr)
                + ", protocol = " + (protocol==null?"null":protocol) +", port = "+port+", allow = "+allow);
        int ret = -1;
        if(!TextUtils.isEmpty(addr) && !TextUtils.isEmpty(protocol)){
            try {
                NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
                Method method = npm.getClass().getMethod("setFlowfilterEgressDestRule", new Class[]{String.class, String.class, int.class, boolean.class});
                ret = (int)method.invoke(npm, addr, protocol, port, allow);
            }catch (Exception e){
                JLog.loge("CloudFlowProtectionLog setFlowfilterEgressDestRule Exception: "+e.toString());
            }
        }
        JLog.logi("CloudFlowProtectionLog, setFlowfilterEgressDestRule(): return " + ret);
        return ret;
    }

    @Override
    public int clearAllRules() {
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("clearAllRules");
            ret = (int)method.invoke(npm);
        }catch (Exception e){
            e.printStackTrace();
            JLog.loge("CloudFlowProtectionLog clearAllRules Exception: "+e.toString());
        }
        JLog.logi("CloudFlowProtectionLog, clearAllRules(): return " + ret);
        return ret;
    }
}
