package com.ucloudlink.refact.platform.sprd.flow;

import com.ucloudlink.refact.business.flow.FlowStatsReadFileUtil;
import com.ucloudlink.refact.business.flow.IFlow;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

/**
 * Created by jianguo.he on 2018/1/31.
 */

public class SprdU3CCloudFlowImpl implements IFlow {
    @Override
    public long getTotalTxBytes(@Nullable String ifName) {
        //long ret = TrafficStats.getTotalTxBytes();
        long ret = FlowStatsReadFileUtil.getTotalFlowStatsFromFileIn(ifName, true);
        JLog.logi("FlowLog, getTotalTxBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getTotalRxBytes(@Nullable String ifName) {
        //long ret = TrafficStats.getTotalRxBytes();
        long ret = FlowStatsReadFileUtil.getTotalFlowStatsFromFileIn(ifName, false);
        JLog.logi("FlowLog, getTotalRxBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getMobileTxBytes(@Nullable String ifName) {
        long ret = 0;//TrafficStats.getMobileTxBytes();
        JLog.logi("FlowLog, getMobileTxBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getMobileRxBytes(@Nullable String ifName) {
        long ret = 0;//TrafficStats.getMobileRxBytes();
        JLog.logi("FlowLog, getMobileRxBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getUidTxBytes(@Nullable String ifName, int uid) {
        //long ret = FlowStatsReadFileUtil.getAndroidSysFlowStatsFromFileIn(UploadFlowTask.INSTANCE.getSeedIfName(), true);
        long ret = FlowStatsReadFileUtil.getUidFlowStatsFromFileIn(ifName, uid, true);
        JLog.logi("FlowLog, getUidTxBytes("+uid+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getUidRxBytes(@Nullable String ifName, int uid) {
        //long ret = FlowStatsReadFileUtil.getAndroidSysFlowStatsFromFileIn(UploadFlowTask.INSTANCE.getSeedIfName(), false);
        long ret = FlowStatsReadFileUtil.getUidFlowStatsFromFileIn(ifName, uid, false);
        JLog.logi("FlowLog, getUidRxBytes("+uid+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getIfNameTxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getLocalFlowStatsFromFileIn(ifName, true);
        JLog.logi("FlowLog, getIfNameTxBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Override
    public long getIfNameRxBytes(@Nullable String ifName) {
        long ret = FlowStatsReadFileUtil.getLocalFlowStatsFromFileIn(ifName, false);
        JLog.logi("FlowLog, getIfNameRxBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Nullable
    @Override
    public String getIfNameArrayBytes(@Nullable String ifName) {
        String ret = FlowStatsReadFileUtil.getIfNameArrayBytes(ifName);
        JLog.logi("FlowLog, getIfNameArrayBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Nullable
    @Override
    public String getUidsArrayBytes(@Nullable String ifName) {
        String ret = FlowStatsReadFileUtil.getUidsArrayBytes(ifName);
        JLog.logi("FlowLog, getUidsArrayBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Nullable
    @Override
    public String getAllUidArrayBytes(@Nullable String ifName) {
        String ret = FlowStatsReadFileUtil.getAllUidArrayBytes(ifName);
        JLog.logi("FlowLog, getAllUidArrayBytes("+(ifName==null?"null":ifName)+") -> ret = " + ret);
        return ret;
    }

    @Override
    public void setReadBytesUids(@Nullable String ifName, @Nullable String strUids) {
        FlowStatsReadFileUtil.setReadBytesUids(ifName, strUids);
        JLog.logi("FlowLog, setReadBytesUids("+(ifName==null?"null":ifName)+") ->  ");
    }
}
