package com.ucloudlink.refact.business.flow.netlimit.uiddnsnet;

import android.net.NetworkPolicyManager;
import android.text.TextUtils;

import com.ucloudlink.framework.flow.ISeedCardNet;
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.utils.JLog;

import java.lang.reflect.Method;

/**
 * Created by jianguo.he on 2017/11/20.
 */

public class SeedCardNetImpl implements ISeedCardNet {

    @Override
    public int setRestrictAllNetworks(String ifName) {
        if(TextUtils.isEmpty(ifName)){
            JLog.logd("SeedCardNetLog setRestrictAllNetworks(): return -1");
            return -1;
        }
        int ret = -1;
        //@SuppressWarnings("unchecked")
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setRestrictAllNetworks", new Class[]{String.class});
            ret = (int)method.invoke(npm, ifName);
        }catch (Exception e){
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog setRestrictAllNetworks(): ifName = "+ifName+", ret = "+ret);
        return ret;
    }

    @Override
    public int removeRestrictAllNetworks(String ifName) {
        if(TextUtils.isEmpty(ifName)){
            JLog.logd("SeedCardNetLog removeRestrictAllNetworks(): return -1");
            return -1;
        }
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("removeRestrictAllNetworks", new Class[]{String.class});
            ret = (int)method.invoke(npm, ifName);
        }catch (Exception e){
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog removeRestrictAllNetworks(): ifName = "+ifName+", ret = "+ret);
        return ret;
    }

    @Override
    public int setNetworkPassByIp(String ifName, int uid, String ip) {
        if(TextUtils.isEmpty(ifName) || TextUtils.isEmpty(ip)){
            JLog.logd("SeedCardNetLog setNetworkPassByIp(): return -1");
            return -1;
        }

        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setNetworkPassByIp", new Class[]{String.class, int.class, String.class});
            ret = (int)method.invoke(npm, ifName, uid, ip);
        }catch (Exception e){
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog setNetworkPassByIp(): ifName = " + ifName + ", uid=" + uid + ", ip="+(ip==null?"null":ip) + ", ret = "+ret);
        return ret;
    }

    @Override
    public int removeNetworkPassByIp(String ifName, int uid, String ip) {
        if(TextUtils.isEmpty(ifName) || TextUtils.isEmpty(ip)){
            JLog.logd("SeedCardNetLog removeNetworkPassByIp(): return -1");
            return -1;
        }
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("removeNetworkPassByIp", new Class[]{String.class, int.class, String.class});
            ret = (int)method.invoke(npm, ifName, uid, ip);
        }catch (Exception e){
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog removeNetworkPassByIp(): ifName = "+ifName + ", uid=" + uid  + ", ip="+(ip==null?"null":ip) +", ret = "+ret);
        return ret;
    }

    @Override
    public int setEnableDNSByDomain(String ifName, int uid, String domain) {
        if(TextUtils.isEmpty(ifName) || TextUtils.isEmpty(domain)){
            JLog.logd("SeedCardNetLog setEnableDNSByDomain(): return -1");
            return -1;
        }
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setEnableDNSByDomain", new Class[]{String.class, int.class, String.class});
            ret = (int)method.invoke(npm, ifName, uid, domain);
        }catch (Exception e){
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog setEnableDNSByDomain(): ifName = "+ifName + ", uid=" + uid  + ", domain="+(domain==null?"null":domain) +", ret = "+ret);
        return ret;
    }

    @Override
    public int removeEnableDNSByDomain(String ifName, int uid, String domain) {
        if(TextUtils.isEmpty(ifName) || TextUtils.isEmpty(domain)){
            JLog.logd("SeedCardNetLog removeEnableDNSByDomain(): return -1");
            return -1;
        }
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("removeEnableDNSByDomain", new Class[]{String.class, int.class, String.class});
            ret = (int)method.invoke(npm, ifName, uid, domain);
        }catch (Exception e){
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog removeEnableDNSByDomain(): ifName = "+ifName + ", uid=" + uid  + ", domain="+(domain==null?"null":domain) +", ret = "+ret);
        return ret;
    }


    @Override
    public int clearRestrictAllRule(String ifName) {
        int ret = -1;
        if(TextUtils.isEmpty(ifName)){
            JLog.logd("SeedCardNetLog clearRestrictAllRule(): ifName = null");
            return ret;
        }
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("clearRestrictAllRule", new Class[]{String.class});
            ret = (int)method.invoke(npm, ifName);
        }catch (Exception e){
            //JLog.loge(e);
            e.printStackTrace();
            ret = -1;
        }
        JLog.logd("SeedCardNetLog clearRestrictAllRule(): ifName = "+ifName+", ret = "+ret);
        return ret;
    }
}
