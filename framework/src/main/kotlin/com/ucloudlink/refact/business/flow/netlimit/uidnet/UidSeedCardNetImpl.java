package com.ucloudlink.refact.business.flow.netlimit.uidnet;

import android.net.NetworkPolicyManager;

import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Created by jianguo.he on 2018/1/9.
 */

public class UidSeedCardNetImpl implements IUidSeedCardNet {

    @Override
    public int setAllUserRestrictAppsOnData() {// void
        JLog.logd("UidSeedCardNetLog, setAllUserRestritAppsOnData");
        int ret = -1;
        try{
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setAllUserRestrictAppsOnData", new Class[]{});
            method.invoke(npm);
            ret = 1;
        }catch (Exception e){
            JLog.loge("UidSeedCardNetLog setAllUserRestrictAppsOnData Exception: "+e);
        }
        JLog.logd("UidSeedCardNetLog, setAllUserRestrictAppsOnData, ret = " + ret);
        return ret;
    }

    @Override
    public int addRestrictAppOnData(int uid) {// void
        JLog.logd("UidSeedCardNetLog, addRestrictAppOnData, uid = " + uid);
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("addRestrictAppOnData", new Class[]{int.class});
            method.invoke(npm, uid);
            ret = 1;
        }catch (Exception e){
            JLog.loge("UidSeedCardNetLog addRestrictAppOnData Exception: "+e);
        }
        JLog.logd("UidSeedCardNetLog, addRestrictAppOnData, ret = " + ret);
        return ret;
    }

    @Override
    public int removeRestrictAppOnData(int uid) {// void
        JLog.logd("UidSeedCardNetLog, removeRestrictAppOnData, uid = " + uid);
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("removeRestrictAppOnData", new Class[]{int.class});
            method.invoke(npm, uid);
            ret = 1;
        }catch (Exception e){
            JLog.loge("UidSeedCardNetLog removeRestrictAppOnData Exception: "+e);
        }
        JLog.logd("UidSeedCardNetLog, removeRestrictAppOnData, ret = " + ret);
        return ret;
    }

    @Override
    public int setAllUserRestrictAppsOnDataByPass(int enable, @NotNull int[] uids) {// void
        JLog.logd("UidSeedCardNetLog, setAllUserRestrictAppsOnDataByPass, enable = "+enable+", uids = "+ uids.toString());
        int ret = -1;
        try{
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("setAllUserRestrictAppsOnDataByPass", new Class[]{int.class, int[].class});
            method.invoke(npm, enable, uids);
            ret = 1;
        }catch (Exception e){
            JLog.loge("UidSeedCardNetLog setAllUserRestrictAppsOnDataByPass Exception: "+e);
        }
        JLog.logd("UidSeedCardNetLog, setAllUserRestrictAppsOnDataByPass, ret = " + ret);
        return ret;
    }

    @Override
    public int resetAllUserRestrictAppsOnData() {// void
        JLog.logd("UidSeedCardNetLog, resetAllUserRestrictAppsOnData");
        int ret = -1;
        try {
            NetworkPolicyManager npm = SysUtils.getNetworkPolicyManager();
            Method method = npm.getClass().getMethod("resetAllUserRestrictAppsOnData", new Class[]{});
            method.invoke(npm);
            ret = 1;
        }catch (Exception e){
            JLog.loge("UidSeedCardNetLog resetAllUserRestrictAppsOnData Exception: "+e);
        }
        JLog.logd("UidSeedCardNetLog, resetAllUserRestrictAppsOnData, ret = " + ret);
        return ret;
    }
}
