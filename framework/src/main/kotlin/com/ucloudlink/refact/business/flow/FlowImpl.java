package com.ucloudlink.refact.business.flow;

import android.net.TrafficStats;

import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

/**
 * Created by jianguo.he on 2018/1/22.
 */

public class FlowImpl implements IFlow {

    @Override
    public long getTotalTxBytes(@Nullable String ifName) {
        long ret = TrafficStats.getTotalTxBytes();
        JLog.logi("FlowLog, getTotalTxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getTotalRxBytes(@Nullable String ifName) {
        long ret = TrafficStats.getTotalRxBytes();
        JLog.logi("FlowLog, getTotalRxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getMobileTxBytes(@Nullable String ifName) {
        long ret = TrafficStats.getMobileTxBytes();
        JLog.logi("FlowLog, getMobileTxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getMobileRxBytes(@Nullable String ifName) {
        long ret = TrafficStats.getMobileRxBytes();
        JLog.logi("FlowLog, getMobileRxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getUidTxBytes(@Nullable String ifName, int uid) {
        long ret = TrafficStats.getUidTxBytes(uid);
        JLog.logi("FlowLog, getUidTxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getUidRxBytes(@Nullable String ifName, int uid) {
        long ret = TrafficStats.getUidRxBytes(uid);
        JLog.logi("FlowLog, getUidRxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getIfNameTxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getAndroidSysFlowStatsFromFileIn(ifName, true);
        JLog.logi("FlowLog, getIfNameTxBytes -> ret = "+ret);
        return ret;
    }

    @Override
    public long getIfNameRxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getAndroidSysFlowStatsFromFileIn(ifName, false);
        JLog.logi("FlowLog, getIfNameRxBytes -> ret = "+ret);
        return  ret;
    }

    @Nullable
    @Override
    public String getIfNameArrayBytes(@Nullable String ifName) {
        return null;
    }

    @Nullable
    @Override
    public String getUidsArrayBytes(@Nullable String ifName) {
        return null;
    }

    @Nullable
    @Override
    public String getAllUidArrayBytes(@Nullable String ifName) {
        return null;
    }

    @Override
    public void setReadBytesUids(@Nullable String ifName, @Nullable String strUids) {

    }
}
