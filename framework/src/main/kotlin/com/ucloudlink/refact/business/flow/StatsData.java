package com.ucloudlink.refact.business.flow;

/**
 * Created by pengchugang on 2016/10/26.
 */

public class StatsData{
    public int logId;
    public long userUp;                 // 用户上行总增量(多次userUpIncr累加)
    public long userUpIncr;             // 用户上行当前次增量
    public long userDown;               // 用户下行总增量(多次userDownIncr累加))
    public long userDownIncr;           // 用户下行当前次增量
    public long sysUp;                   // 系统上行总增量(多次sysUpIncr累加)
    public long sysUpIncr;              // 系统上行当前次增量
    public long sysDown;                // 系统下行总增量(多次sysDownIncr累加)
    public long sysDownIncr;            // 系统下行当前次增量
    public long seedUp;
    public long seedDown;
    public long mobileUp;
    public long mobileDown;
    public int simCardType;

    public int getLogId() {
        return logId;
    }

    public void reset(){
        logId       = 0;
        mobileUp    = 0;
        mobileDown  = 0;
        sysUp       = 0;
        sysDown     = 0;
        userUp      = 0;
        userDown    = 0;

    }

    @Override
    public String toString() {
        return "StatsData{" +
                "logId=" + logId +
                ", user: " + userUp + "/" + userDown +
                ", userIncr: " + userUpIncr + "/" + userDownIncr +
                ", sys: " + sysUp + "/" + sysDown +
                ", sysIncr: " + sysDownIncr + "/" + sysUpIncr +
                ", mobile: " + mobileUp + "/" + mobileDown +
                ", seed: " + seedUp + "/" + seedDown +
                ", simCardType=" + simCardType +
                '}';
    }
}
