package com.ucloudlink.refact.platform.qcom.flow;

import android.net.TrafficStats;

import com.ucloudlink.refact.business.flow.FlowStatsReadFileUtil;
import com.ucloudlink.refact.business.flow.IFlow;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

/**
 * Created by jianguo.he on 2018/2/28.
 */

public class QcSeedFlowImpl implements IFlow {
    @Override
    public long getTotalTxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getTotalFlowStatsFromFileIn(ifName, true);
        JLog.loge("FlowLog, getTotalTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getTotalRxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getTotalFlowStatsFromFileIn(ifName, false);
        JLog.loge("FlowLog, getTotalRxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getMobileTxBytes(@Nullable String ifName) {
        long ret = 0;//TrafficStats.getMobileTxBytes();
        JLog.loge("FlowLog, getMobileTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getMobileRxBytes(@Nullable String ifName) {
        long ret = 0;//TrafficStats.getMobileRxBytes();
        JLog.loge("FlowLog, getMobileRxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getUidTxBytes(@Nullable String ifName, int uid) {
        long ret = FlowStatsReadFileUtil.getUidFlowStatsFromFileIn(ifName, uid, true);
        JLog.loge("FlowLog, getUidTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getUidRxBytes(@Nullable String ifName, int uid) {
        long ret = FlowStatsReadFileUtil.getUidFlowStatsFromFileIn(ifName, uid, false);
        JLog.loge("FlowLog, getUidRxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getIfNameTxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getLocalFlowStatsFromFileIn(ifName, true);
        JLog.loge("FlowLog, getIfNameTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getIfNameRxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getLocalFlowStatsFromFileIn(ifName, false);
        JLog.loge("FlowLog, getIfNameRxBytes() -> ret = " + ret);
        return ret;
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
    public void setReadBytesUids(@Nullable String ifName,@Nullable String strUids) {

    }
}
