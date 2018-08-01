package com.ucloudlink.refact.platform.qcom.flow;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.CloudFlowCtrl;
import com.ucloudlink.refact.business.flow.FlowStatsReadFileUtil;
import com.ucloudlink.refact.business.flow.IFlow;
import com.ucloudlink.refact.business.flow.ICloudFlowCtrl;
import com.ucloudlink.refact.business.flow.StatsData;
import com.ucloudlink.refact.business.flow.StatsRecord;
import com.ucloudlink.refact.utils.DateUtil;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Created by jianguo.he on 2018/1/20.
 */

public class QcCloudFlowCtrl implements ICloudFlowCtrl {

    private long rm0TxSpeed = 0;
    private long rm0RxSpeed = 0;
    private long rm1TxSpeed = 0;
    private long rm1RxSpeed = 0;


    private StatsRecord curRecord = new StatsRecord();
    private StatsRecord preRecord = new StatsRecord();
    private StatsData tmpSd     = new StatsData();

    private IFlow mIFlow;

    public QcCloudFlowCtrl(){
        mIFlow = ServiceManager.systemApi.getCloudIFlow();
    }

    @Override
    public void initStats(){
        curRecord.makeZeroStats();
        preRecord.makeZeroStats();
        tmpSd.reset();
    }

    @Override
    public void resetTmpSd() {
        tmpSd.reset();
    }

    @Override
    public long getSeedTxFlow(){
        long fs = 0L;

        if (curRecord.seedUp > preRecord.seedUp){
            fs = curRecord.seedUp - preRecord.seedUp;
        }
        return fs;
    }

    @Override
    public long getSeedRxFlow(){
        long fs = 0L;

        if (curRecord.seedDown > preRecord.seedDown){
            fs = curRecord.seedDown - preRecord.seedDown;
        }
        return fs;
    }

    @Override
    public void enableSeedSimCard(){
        preRecord.seedUp = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX)
                + FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
        preRecord.seedDown = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX)
                + FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
    }

    @Override
    public void disableSeedSimCard() {

    }

    @Override
    public void enableCloudSimCard() {
        curRecord.seedUp = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX)
                + FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
        curRecord.seedDown = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX)
                + FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
    }

    @Override
    public void disableCloudSimCard() {

    }

    @Override
    public StatsData getStats(int statsStatus, int statis_mode, int logid, int curUid, int dataChannelCardType) {
        JLog.logi("FlowLog, getStats[" + logid + "]: statsStatus = "+statsStatus+", statis_mode = "+statis_mode
        +", logid = "+logid +", curUid = "+curUid+", dataChannelCardType = "+dataChannelCardType+", curRecord.logId = "+curRecord.logId);

        if (statsStatus == CloudFlowCtrl.UC_STATS_STATUS_ACTIVE) {
            if ((curRecord.logId < logid) && (curRecord.logId != 0)){
                preRecord.updateLastRecord(curRecord);
            }

            if (statis_mode == CloudFlowCtrl.STATS_MODE_READ_FILE){
                long  valTx0;
                long  valRx0;
                long  valTx1;
                long  valRx1;
                long curTime = System.nanoTime();
                long  timeGap = TimeUnit.NANOSECONDS.toSeconds(curTime - preRecord.timeStamp);
                JLog.logi("FlowLog, GetTimeGap:Cur:" + timeGap +"/" + curTime + "/" + preRecord.timeStamp
                        +", format(): "+timeGap+"/"+DateUtil.format_YYYY_MM_DD_HH_SS_SSS(timeGap)+"/"+DateUtil.format_YYYY_MM_DD_HH_SS_SSS(preRecord.timeStamp));
                valTx0 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX);
                if (timeGap > 0) {
                    rm0TxSpeed = (valTx0 - preRecord.mobileUp1) / timeGap;
                }

                if ((valTx0 - preRecord.mobileUp1 < 0) || (rm0TxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES)){
                    return null;
                } else{
                    curRecord.mobileUp1 = valTx0;
                }

                valRx0 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX);
                if (timeGap > 0) {
                    rm0RxSpeed = (valRx0 - preRecord.mobileDown1) / timeGap;
                }
                if ((valRx0 - preRecord.mobileDown1 < 0) || (rm0RxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES)){
                    return null;
                } else{
                    curRecord.mobileDown1 = valRx0;
                }

                valTx1 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
                if (timeGap > 0) {
                    rm1TxSpeed = (valTx1 - preRecord.mobileUp2) / timeGap;
                }
                if ((valTx1 - preRecord.mobileUp2 < 0) || (rm1TxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES)){
                    return null;
                } else{
                    curRecord.mobileUp2   = valTx1;
                }

                valRx1 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
                if (timeGap > 0) {
                    rm1RxSpeed = (valRx1 - preRecord.mobileDown2) / timeGap;
                }
                if ((valRx1 - preRecord.mobileDown2 < 0) || (rm1RxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES)){
                    return null;
                }else{
                    curRecord.mobileDown2 = valRx1;
                }

//                JLog.logi("FlowLog, getStats[" + logid + "],cur Mobile0:" + curRecord.mobileUp1 + "/" + curRecord.mobileDown1
//                        + ", Mobile1:" + curRecord.mobileUp2 + "/" + curRecord.mobileDown2
//                        + ", sys:" + curRecord.sysUp + "/" + curRecord.sysDown);
                JLog.logi("FlowLog, getStats[" + logid + "]: curRecord = "+curRecord.toString());
                JLog.logi("FlowLog, DataSpeed: TimeGap:" + timeGap + ", Up:(" + rm0TxSpeed + "+" + rm1TxSpeed + ")Bps, Down:(" + rm0RxSpeed + "+" + rm1RxSpeed + ")Bps");
            }else{
                curRecord.mobileUp1    = mIFlow.getMobileTxBytes(getIfName());
                curRecord.mobileDown1  = mIFlow.getMobileRxBytes(getIfName());
                //JLog.logi("FlowLog, GetStats[" + logid + "], Mobile:" + curRecord.mobileUp1 + "/" + curRecord.mobileDown1 + ", sys:" + curRecord.sysUp + "/" + curRecord.sysDown);
                JLog.logi("FlowLog, GetStats[" + logid + "]: curRecord = "+curRecord.toString());
            }

            curRecord.sysUp   = mIFlow.getUidTxBytes(getIfName(), curUid);
            curRecord.sysDown = mIFlow.getUidRxBytes(getIfName(), curUid);
            curRecord.getTag  = 1;
            curRecord.timeStamp = System.nanoTime();
        }

        curRecord.sysUpIncr    = curRecord.sysUp - preRecord.sysUp;
        curRecord.sysDownIncr  = curRecord.sysDown - preRecord.sysDown;
        JLog.logi("FlowLog, calculation begin, getStats["+logid+"] preRecord: "+preRecord.toString());
        JLog.logi("FlowLog, calculation begin, getStats["+logid+"] curRecord: "+curRecord.toString());

        if (statis_mode == CloudFlowCtrl.STATS_MODE_READ_FILE) {
            curRecord.userUpIncr = 0;
            if (curRecord.mobileUp1 > preRecord.mobileUp1) {
                curRecord.userUpIncr += (curRecord.mobileUp1 - preRecord.mobileUp1);
            }
            if (curRecord.mobileUp2 > preRecord.mobileUp2) {
                curRecord.userUpIncr += (curRecord.mobileUp2 - preRecord.mobileUp2);
            }
            curRecord.userDownIncr = 0;
            if (curRecord.mobileDown1 > preRecord.mobileDown1){
                curRecord.userDownIncr += (curRecord.mobileDown1 - preRecord.mobileDown1);
            }
            if (curRecord.mobileDown2 > preRecord.mobileDown2){
                curRecord.userDownIncr += (curRecord.mobileDown2 - preRecord.mobileDown2);
            }

            // -- 2018-04-16  读文件rmnet_data0 + rmnet_data1拿到的数据是总数据   begin  ---
            if(curRecord.userUpIncr > curRecord.sysUpIncr){
                curRecord.userUpIncr -= curRecord.sysUpIncr;
            }
            if(curRecord.userDownIncr > curRecord.sysDownIncr){
                curRecord.userDownIncr -= curRecord.sysDownIncr;
            }
            // -- 2018-04-16  读文件rmnet_data0 + rmnet_data1拿到的数据是总数据   end  ---

        } else{
            curRecord.userUpIncr   = (curRecord.mobileUp1 - preRecord.mobileUp1) - (curRecord.sysUp - preRecord.sysUp);
            curRecord.userDownIncr = (curRecord.mobileDown1 - preRecord.mobileDown1) - (curRecord.sysDown - preRecord.sysDown);
        }

        if ((curRecord.logId == 0) || (curRecord.logId == logid)){
            curRecord.sysUpIncrTotal    = preRecord.sysUpIncrTotal    + curRecord.sysUpIncr;
            curRecord.sysDownIncrTotal  = preRecord.sysDownIncrTotal  + curRecord.sysDownIncr;
            curRecord.userUpIncrTotal   = preRecord.userUpIncrTotal   + curRecord.userUpIncr;
            curRecord.userDownIncrTotal = preRecord.userDownIncrTotal + curRecord.userDownIncr;
        }else if (curRecord.logId < logid){
            preRecord.sysUpIncrTotal    = curRecord.sysUpIncrTotal;
            preRecord.sysDownIncrTotal  = curRecord.sysDownIncrTotal;
            preRecord.userUpIncrTotal   = curRecord.userUpIncrTotal;
            preRecord.userDownIncrTotal = curRecord.userDownIncrTotal;

            curRecord.sysUpIncrTotal    = preRecord.sysUpIncrTotal    + curRecord.sysUpIncr;
            curRecord.sysDownIncrTotal  = preRecord.sysDownIncrTotal  + curRecord.sysDownIncr;
            curRecord.userUpIncrTotal   = preRecord.userUpIncrTotal   + curRecord.userUpIncr;
            curRecord.userDownIncrTotal = preRecord.userDownIncrTotal + curRecord.userDownIncr;

            resetTmpSd();
        }
        preRecord.userUpIncr   = curRecord.userUpIncr;
        preRecord.userDownIncr = curRecord.userDownIncr;
        preRecord.sysUpIncr    = curRecord.sysUpIncr;
        preRecord.sysDownIncr  = curRecord.sysDownIncr;

        curRecord.logId = logid;
        JLog.logi("FlowLog, calculation end, getStats["+logid+"] preRecord: "+preRecord.toString());
        JLog.logi("FlowLog, calculation end, getStats["+logid+"] curRecord: "+curRecord.toString());

        StatsData sd = new StatsData();

        sd.logId = logid;
        if (sd.logId == 1){
            sd.seedUp = getSeedTxFlow();
            sd.seedDown = getSeedRxFlow();
        }

        sd.userUp   = curRecord.userUpIncrTotal;
        sd.userDown = curRecord.userDownIncrTotal;
        sd.sysUp    = curRecord.sysUpIncrTotal;
        sd.sysDown  = curRecord.sysDownIncrTotal;
        sd.userUpIncr    = curRecord.userUpIncr;
        sd.userDownIncr  = curRecord.userDownIncr;
        sd.sysUpIncr     = curRecord.sysUpIncr;
        sd.sysDownIncr   = curRecord.sysDownIncr;
        sd.simCardType   = dataChannelCardType;

        curRecord.getTag = 0;

        tmpSd.userUp       = sd.userUp;
        tmpSd.userDown     = sd.userDown;
        tmpSd.sysUp        = sd.sysUp;
        tmpSd.sysDown      = sd.sysDown;

        tmpSd.userUpIncr   = sd.userUpIncr;
        tmpSd.userDownIncr = sd.userDownIncr;
        tmpSd.sysUpIncr    = sd.sysUpIncr;
        tmpSd.sysDownIncr  = sd.sysDownIncr;
        tmpSd.simCardType  = sd.simCardType;
        tmpSd.logId        = sd.logId;

//        JLog.logi("FlowLog, getStats()-> logId [" + logid + "], current period user:" + sd.userUp + "/" + sd.userDown + ", sys: " + sd.sysUp + "/" + sd.sysDown
//                + ", user incr:" + sd.userUpIncr + "/" + sd.userDownIncr
//                + ", sys incr:" + sd.sysUpIncr + "/" + sd.sysDownIncr + "seedFlow:" + sd.seedUp + "/" +  sd.seedDown);
        JLog.logi("FlowLog, calculation result, getStats()-> logId [" + logid + "] StatsData: "+sd.toString());
        return sd;

    }

    @Override
    public void startStats(int statis_mode, int curUid) {
        curRecord.logId = 0;
        if (statis_mode == CloudFlowCtrl.STATS_MODE_READ_FILE){
            long val;

            val = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX);
            if (val  < 0){
                val = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX);
            }
            preRecord.mobileUp1 = val;

            val = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX);
            if (val < 0){
                FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX);
            }
            preRecord.mobileDown1 = val;

            val = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
            if (val < 0){
                FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
            }
            preRecord.mobileUp2 = val;
            val = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
            if (val < 0){
                FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
            }
            preRecord.mobileDown2 = val;
        }else{
            preRecord.mobileUp1   = mIFlow.getMobileTxBytes(getIfName());
            preRecord.mobileDown1 = mIFlow.getMobileRxBytes(getIfName());
        }

        preRecord.sysUp    = mIFlow.getUidTxBytes(getIfName(), curUid);
        preRecord.sysDown  = mIFlow.getUidRxBytes(getIfName(), curUid);
        preRecord.timeStamp = System.nanoTime();
//        JLog.logi("FlowLog, startStats()-> mobile0:" + preRecord.mobileUp1 + " " + preRecord.mobileDown1
//                +", mobile1:" + preRecord.mobileUp2 + " " + preRecord.mobileDown2
//                + ", sys:" + preRecord.sysUp + " " + preRecord.sysDown);
        JLog.logi("FlowLog, startStats()-> preRecord: "+preRecord.toString());
    }

    @Override
    public void stopStats(int statis_mode, int curUid) {
        if (statis_mode == CloudFlowCtrl.STATS_MODE_READ_FILE){
            long valTx0;
            long valRx0;
            long valTx1;
            long valRx1;
            long timeGap  = TimeUnit.MILLISECONDS.toSeconds(System.nanoTime() - preRecord.timeStamp);

            valTx0 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX);
            rm0TxSpeed = (valTx0 - preRecord.mobileUp1) / timeGap;
            if ((valTx0 - preRecord.mobileUp1 < 0) || (rm0TxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                valTx0 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0TX);
                if ((valTx0 - preRecord.mobileUp1 >= 0) || (rm0TxSpeed < CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                    curRecord.mobileUp1 = valTx0;
                }
            } else{
                curRecord.mobileUp1 = valTx0;
            }

            valRx0 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX);
            rm0RxSpeed = (valRx0 - preRecord.mobileDown1) / timeGap;
            if ((valRx0 < 0) || (valRx0 - preRecord.mobileDown1 < 0) || (rm0RxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                valRx0 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM0RX);
                if ((valRx0 - preRecord.mobileDown1 >= 0) || (rm0RxSpeed < CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                    curRecord.mobileDown1 = valRx0;
                }
            } else{
                curRecord.mobileDown1 = valRx0;
            }

            valTx1 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
            rm1TxSpeed = (valTx1 - preRecord.mobileUp2) / timeGap;
            if ((valTx1 < 0) || (valTx1 - preRecord.mobileUp2 < 0) || (rm1TxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                valTx1 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1TX);
                if ((valTx1 - preRecord.mobileUp2 >= 0) || (rm1TxSpeed < CloudFlowCtrl.UC_MAX_SPEED_BYTES)){
                    curRecord.mobileUp2   = valTx1;
                }
            } else{
                curRecord.mobileUp2 = valTx1;
            }

            valRx1 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
            rm1RxSpeed = (valRx1 - preRecord.mobileDown2) / timeGap;
            if ((valRx1 < 0) || (valRx1 - preRecord.mobileDown2 < 0) || (rm1RxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                valRx1 = FlowStatsReadFileUtil.getStatsFromFileIn(FlowStatsReadFileUtil.RM1RX);
                if ((valRx1 - preRecord.mobileDown2 >= 0) || (rm1RxSpeed < CloudFlowCtrl.UC_MAX_SPEED_BYTES )){
                    curRecord.mobileDown2   = valRx1;
                }
            } else{
                curRecord.mobileDown2   = valRx1;
            }
        }else{
            curRecord.mobileUp1    = mIFlow.getMobileTxBytes(getIfName());
            curRecord.mobileDown1  = mIFlow.getMobileRxBytes(getIfName());
        }

        curRecord.sysUp   = mIFlow.getUidTxBytes(getIfName(), curUid);
        curRecord.sysDown = mIFlow.getUidRxBytes(getIfName(), curUid);

        curRecord.getTag = 1;

//        JLog.logi("FlowLog, stopStats, mobile0:" + curRecord.mobileUp1 + "/" + curRecord.mobileDown1
//                + ", mobile1:" + curRecord.mobileUp2 + "/" + curRecord.mobileDown2
//                + ", sys:" + curRecord.sysUp + "/" + curRecord.sysDown);
        JLog.logi("FlowLog, stopStats() -> curRecord: "+curRecord.toString());
    }

    @Nullable
    @Override
    public String getIfName() {
        return null;
    }

    @Override
    public void setIfName(@Nullable String ifName) {

    }
}
