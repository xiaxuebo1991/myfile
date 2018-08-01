package com.ucloudlink.refact.platform.sprd.flow;

import com.ucloudlink.refact.business.flow.FlowStatsReadFileUtil;
import com.ucloudlink.refact.business.flow.IFlow;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

/**
 * Created by jianguo.he on 2018/2/7.
 */
public class SprdU3CSeedFlowImpl implements IFlow {

    @Override
    public long getTotalTxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getTotalFlowStatsFromFileIn(ifName, true);
        JLog.logi("FlowLog, getTotalTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getTotalRxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getTotalFlowStatsFromFileIn(ifName, false);
        JLog.logi("FlowLog, getTotalRxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getMobileTxBytes(@Nullable String ifName) {
        return 0;
    }

    @Override
    public long getMobileRxBytes(@Nullable String ifName) {
        return 0;
    }

    @Override
    public long getUidTxBytes(@Nullable String ifName, int uid) {
        long ret = FlowStatsReadFileUtil.getUidFlowStatsFromFileIn(ifName, uid,true);
        JLog.logi("FlowLog, getUidTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getUidRxBytes(@Nullable String ifName, int uid) {
        long ret = FlowStatsReadFileUtil.getUidFlowStatsFromFileIn(ifName, uid,false);
        JLog.logi("FlowLog, getUidRxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getIfNameTxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getLocalFlowStatsFromFileIn(ifName, true);
        JLog.logi("FlowLog, getIfNameTxBytes() -> ret = " + ret);
        return ret;
    }

    @Override
    public long getIfNameRxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getLocalFlowStatsFromFileIn(ifName, false);
        JLog.logi("FlowLog, getIfNameRxBytes() -> ret = " + ret);
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
    public void setReadBytesUids(@Nullable String ifName, @Nullable String strUids) {

    }
}
