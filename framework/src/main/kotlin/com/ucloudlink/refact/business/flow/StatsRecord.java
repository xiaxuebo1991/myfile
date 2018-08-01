package com.ucloudlink.refact.business.flow;

import com.ucloudlink.refact.utils.DateUtil;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by jianguo.he on 2018/1/20.
 */

public class StatsRecord {

    public long sysUp;                      // 系统上行流量
    public long sysDown;                    // 系统下行流量
    public long mobileUp1;                  // 用户上行流量（高通平台上取 rmnet_data0 的Tx流量）
    public long mobileDown1;                // 用户下行流量（高通平台上取 rmnet_data0 的Rx流量）
    public long mobileUp2;                  // 用户上行流量（高通平台上取 rmnet_data1 的Tx流量）
    public long mobileDown2;                // 用户下行流量（高通平台上取 rmnet_data1 的Rx流量）
    public long userUpIncr;                 // 用户上行增量
    public long userDownIncr;               // 用户下行增量
    public long sysUpIncr;                  // 系统上行增量
    public long sysDownIncr;                // 系统下行增量
    public long userUpIncrTotal;            // 用户上行总增量(可对应mIFlow.getIfNameTxBytes())
    public long userDownIncrTotal;          // 用户下行总增量(可对应mIFlow.getIfNameRxBytes())
    public long sysUpIncrTotal;             // 系统上行总增量(可对应mIFlow.getIfNameTxBytes())
    public long sysDownIncrTotal;           // 用户下行总增量(可对应mIFlow.getIfNameTxBytes())
    public long seedUp;                      // 种子卡上行流量
    public long seedDown;                    // 种子卡下行流量
    public long getTag;                      // 标记，暂时无用
    public long timeStamp;                   // 数据记录时的时间戳(System.nanoTime())
    public int logId;                         // 数据记录的id

    public void updateLastRecord(StatsRecord curRecord){
        mobileUp1   = curRecord.mobileUp1;
        mobileDown1 = curRecord.mobileDown1;
        mobileUp2   = curRecord.mobileUp2;
        mobileDown2 = curRecord.mobileDown2;
        sysUp       = curRecord.sysUp;
        sysDown     = curRecord.sysDown;
        logId       = curRecord.logId;
        timeStamp   = curRecord.timeStamp;

        logd("update preRecorde[" + logId + "], Mobile0:" + mobileUp1 + "/" + mobileDown1
                + ", Mobile1:" + mobileUp2 + "/" + mobileDown2
                + ", sys:" + sysUp + "/" + sysDown);
    }

    public void makeZeroStats(){
        logId = 0;
        sysUp = 0;
        sysDown = 0;
        mobileUp1 = 0;
        mobileDown1 = 0;
        mobileUp2 = 0;
        mobileDown2 = 0;
        userUpIncr = 0;
        userDownIncr = 0;
        sysUpIncr = 0;
        sysDownIncr = 0;
        getTag = 0;
        userUpIncrTotal = 0;
        userDownIncrTotal = 0;
        sysUpIncrTotal = 0;
        sysDownIncrTotal = 0;
        timeStamp = 0;
    }

    @Override
    public String toString() {
        return "StatsRecord{" +
                "sys: " + sysUp + "/" + sysDown +
                ", mobile0: " + mobileUp1 +"/" + mobileDown1 +
                ", mobile1: " + mobileUp2 + "/" + mobileDown2 +
                ", userIncr: " + userUpIncr + "/" + userDownIncr +
                ", sysIncr: " + sysUpIncr + "/" + sysDownIncr +
                ", userIncrTotal: " + userUpIncrTotal + "/" + userDownIncrTotal +
                ", sysIncrTotal: " + sysUpIncrTotal + "/" + sysDownIncrTotal +
                ", seed: " + seedUp + "/" + seedDown +
                ", getTag=" + getTag +
                ", timeStamp=" + DateUtil.format_YYYY_MM_DD_HH_SS_SSS(timeStamp) +
                ", logId=" + logId +
                '}';
    }
}
