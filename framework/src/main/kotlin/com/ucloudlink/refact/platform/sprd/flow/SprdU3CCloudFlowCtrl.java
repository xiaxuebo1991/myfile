package com.ucloudlink.refact.platform.sprd.flow;

import android.text.TextUtils;

import com.ucloudlink.framework.tasks.UploadFlowTask;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.CloudFlowCtrl;
import com.ucloudlink.refact.business.flow.FlowParser;
import com.ucloudlink.refact.business.flow.ICloudFlowCtrl;
import com.ucloudlink.refact.business.flow.IFlow;
import com.ucloudlink.refact.business.flow.StatsData;
import com.ucloudlink.refact.business.flow.StatsRecord;
import com.ucloudlink.refact.utils.JLog;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by jianguo.he on 2018/1/19.
 *
 * U3C 上根据ifName获取数据时，ROM统计过的ifName，无关网络连接还是未连接，都可以拿到数据，如果ROM未统计过ifName，则拿不到数据，因为节点未创建
 *
 */

public class SprdU3CCloudFlowCtrl implements ICloudFlowCtrl {

    private long rm0TxSpeed = 0;
    private long rm0RxSpeed = 0;

    private StatsRecord curRecord = new StatsRecord();
    private StatsRecord preRecord = new StatsRecord();

    private IFlow mIFlow;
    private String curIfName;
    private String startIfName;

    public SprdU3CCloudFlowCtrl(){
        mIFlow = ServiceManager.systemApi.getCloudIFlow();
    }

    @Override
    public void initStats() {
        curRecord.makeZeroStats();
        preRecord.makeZeroStats();
    }

    @Nullable
    @Override
    public String getIfName() {
        return curIfName;
    }

    @Override
    public void setIfName(@Nullable String ifName) {
        curIfName = ifName;
    }

    @Override
    public void resetTmpSd() {
    }

    @Override
    public void enableSeedSimCard() {
        preRecord.seedUp = 0;
        preRecord.seedDown = 0;
    }

    @Override
    public void disableSeedSimCard() {

    }

    @Override
    public void enableCloudSimCard() {
        curRecord.seedUp = 0;
        curRecord.seedDown = 0;
    }

    @Override
    public void disableCloudSimCard() {

    }

    @Override
    public void startStats(int statis_mode, int curUid) {
        startIfName = getIfName();
        final String ifName = startIfName;

        curRecord.logId = 0;

//        // 系统流量
//        preRecord.sysUp    = mIFlow.getIfNameTxBytes(ifName);
//        preRecord.sysDown  = mIFlow.getIfNameRxBytes(ifName);
//        // (用户流量)
//        preRecord.mobileUp1   = mIFlow.getTotalTxBytes(ifName) - preRecord.sysUp;//172
//        preRecord.mobileDown1 = mIFlow.getTotalRxBytes(ifName) - preRecord.sysDown;
        long[] flowArray = parseIfNameArrayBytes(mIFlow.getIfNameArrayBytes(ifName));

        // 系统流量
        preRecord.sysUp    = flowArray[2];
        preRecord.sysDown  = flowArray[3];
        // (用户流量)
        preRecord.mobileUp1   = flowArray[0] - preRecord.sysUp;
        preRecord.mobileDown1 = flowArray[1] - preRecord.sysDown;

        preRecord.timeStamp = System.nanoTime();

        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", startStats()-> ifName = "+(ifName==null?"null":ifName)+", preRecord: "+preRecord.toString());
    }

    /**
     * //[<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>]
     * @param flowInfoStr
     * @return
     */
    private long[] parseIfNameArrayBytes(String flowInfoStr){
        long[] ret = new long[4];
        if(!TextUtils.isEmpty(flowInfoStr)){
            List<String> listFlow = FlowParser.Companion.parseIfNameArrayBytes(flowInfoStr);
            int size = listFlow.size();
            if(size >= 8){
                try{
                    ret[0] = Long.parseLong(listFlow.get(0));
                    ret[1] = Long.parseLong(listFlow.get(2));
                    ret[2] = Long.parseLong(listFlow.get(4));
                    ret[3] = Long.parseLong(listFlow.get(6));
                }catch (Exception e){
                    JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", parseIfNameArrayBytes()-> listFlow.size() = "+size+", === Error parse Exception: "+e.toString());
                    ret[0] = 0;
                    ret[1] = 0;
                    ret[2] = 0;
                    ret[3] = 0;
                }
            } else {
                JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", parseIfNameArrayBytes()-> listFlow.size() = "+size+", === Error size < 8 === ");
            }
        }

        return ret;
    }

    @Override
    public void stopStats(int statis_mode, int curUid) {
        final String ifName = getIfName();
        if(!TextUtils.isEmpty(ifName)){
//            // 系统流量
//            curRecord.sysUp   = mIFlow.getIfNameTxBytes(ifName);
//            curRecord.sysDown = mIFlow.getIfNameRxBytes(ifName);
//            //(用户流量)
//            curRecord.mobileUp1   = mIFlow.getTotalTxBytes(ifName) - curRecord.sysUp;
//            curRecord.mobileDown1 = mIFlow.getTotalRxBytes(ifName) - curRecord.sysDown;
            long[] flowArray = parseIfNameArrayBytes(mIFlow.getIfNameArrayBytes(ifName));

            // 系统流量
            curRecord.sysUp    = flowArray[2];
            curRecord.sysDown  = flowArray[3];
            // (用户流量)
            curRecord.mobileUp1   = flowArray[0] - curRecord.sysUp;
            curRecord.mobileDown1 = flowArray[1] - curRecord.sysDown;

            if(isErrorDistanceData() || isErrorSpeed()){
                curRecord.sysUp   = preRecord.sysUp;
                curRecord.sysDown = preRecord.sysDown;
                curRecord.mobileUp1   = preRecord.mobileUp1;
                curRecord.mobileDown1 = preRecord.mobileDown1;
            }

        } else {
            curRecord.sysUp   = preRecord.sysUp;
            curRecord.sysDown = preRecord.sysDown;
            curRecord.mobileUp1   = preRecord.mobileUp1;
            curRecord.mobileDown1 = preRecord.mobileDown1;
        }

        curRecord.getTag = 1;

        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", stopStats()-> ifName = "+(ifName==null?"null":ifName)+", curRecord: "+curRecord.toString());
    }

    @Nullable
    @Override
    public StatsData getStats(int statsStatus, int statis_mode, int logid, int curUid, int dataChannelCardType) {
        final String ifName = getIfName();
        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", getStats["+logid+"] statsStatus = " + statsStatus + ", statis_mode = "+statis_mode+", curUid = "+curUid
                +", dataChannelCardType = "+dataChannelCardType+", curRecord.logId = " + curRecord.logId+", ifName = "+(ifName==null?"null":ifName)
                +", startIfName = " + (startIfName==null?"null":startIfName));

        // 需要校验preRecord数据是否为0
        if (statsStatus == CloudFlowCtrl.UC_STATS_STATUS_ACTIVE) {
            if ((curRecord.logId < logid) && (curRecord.logId != 0)){
                //preRecord.makeZeroStats();
                preRecord.updateLastRecord(curRecord);
            }
            if(!TextUtils.isEmpty(ifName)){
//                // 系统流量
//                curRecord.sysUp   = mIFlow.getIfNameTxBytes(ifName);
//                curRecord.sysDown = mIFlow.getIfNameRxBytes(ifName);
//                //(用户流量)
//                curRecord.mobileUp1    = mIFlow.getTotalTxBytes(ifName) - curRecord.sysUp;
//                curRecord.mobileDown1  = mIFlow.getTotalRxBytes(ifName) - curRecord.sysDown;

                long[] flowArray = parseIfNameArrayBytes(mIFlow.getIfNameArrayBytes(ifName));
                // 系统流量
                curRecord.sysUp    = flowArray[2];
                curRecord.sysDown  = flowArray[3];
                // (用户流量)
                curRecord.mobileUp1   = flowArray[0] - curRecord.sysUp;
                curRecord.mobileDown1 = flowArray[1] - curRecord.sysDown;

                if(TextUtils.isEmpty(startIfName)){// 处理特殊情况,尽可能的保存数据
                    startIfName = ifName;

                    preRecord.sysUp   = curRecord.sysUp;
                    preRecord.sysDown   = curRecord.sysDown;
                    preRecord.mobileUp1   = curRecord.mobileUp1;
                    preRecord.mobileDown1   = curRecord.mobileDown1;

                    preRecord.sysUpIncrTotal    = curRecord.sysUpIncrTotal    = 0;
                    preRecord.sysDownIncrTotal  = curRecord.sysDownIncrTotal  = 0;
                    preRecord.userUpIncrTotal   = curRecord.userUpIncrTotal   = 0;
                    preRecord.userDownIncrTotal = curRecord.userDownIncrTotal = 0;

                } else if(!ifName.equals(startIfName)){// 处理特殊情况,尽可能的保存数据
                    final String tempStartIfName = startIfName;
//                    long sysUp   =  mIFlow.getIfNameTxBytes(tempStartIfName);
//                    long sysDown   = mIFlow.getIfNameRxBytes(tempStartIfName);
//                    long mobileUp1   = mIFlow.getTotalTxBytes(tempStartIfName) - sysUp;
//                    long mobileDown1   = mIFlow.getTotalRxBytes(tempStartIfName) - sysDown;

                    long[] tempFlowArray = parseIfNameArrayBytes(mIFlow.getIfNameArrayBytes(tempStartIfName));
                    long sysUp   =  tempFlowArray[2];
                    long sysDown   = tempFlowArray[3];
                    long mobileUp1   = tempFlowArray[0] - sysUp;
                    long mobileDown1   = tempFlowArray[1] - sysDown;

                    startIfName = ifName;

                    preRecord.sysUpIncr    = sysUp - preRecord.sysUp;
                    preRecord.sysDownIncr  = sysDown - preRecord.sysDown;
                    preRecord.userUpIncr   = mobileUp1 - preRecord.mobileUp1;
                    preRecord.userDownIncr = mobileDown1 - preRecord.mobileDown1;

                    if(preRecord.sysUpIncr < 0 ){
                        preRecord.sysUpIncr = 0;
                    }
                    if(preRecord.sysDownIncr < 0 ){
                        preRecord.sysDownIncr = 0;
                    }
                    if(preRecord.userUpIncr < 0 ){
                        preRecord.userUpIncr = 0;
                    }
                    if(preRecord.userDownIncr < 0 ){
                        preRecord.userDownIncr = 0;
                    }

                    preRecord.sysUpIncrTotal    = preRecord.sysUpIncr;
                    preRecord.sysDownIncrTotal  = preRecord.sysDownIncr;
                    preRecord.userUpIncrTotal   = preRecord.userUpIncr;
                    preRecord.userDownIncrTotal = preRecord.userDownIncr;

                }

            } else {
                curRecord.sysUp   = preRecord.sysUp;
                curRecord.sysDown = preRecord.sysDown;
                curRecord.mobileUp1   = preRecord.mobileUp1;
                curRecord.mobileDown1 = preRecord.mobileDown1;
            }
        } else {
            curRecord.sysUp   = preRecord.sysUp;
            curRecord.sysDown = preRecord.sysDown;
            curRecord.mobileUp1   = preRecord.mobileUp1;
            curRecord.mobileDown1 = preRecord.mobileDown1;
        }

        curRecord.getTag  = 1;
        curRecord.timeStamp = System.nanoTime();

        if( isErrorData(preRecord) || isErrorData(curRecord) || isErrorDistanceData() || isErrorSpeed()){
            JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+",CheckCCFlowData, getStats["+logid+"]  Note: verification: data error ====" );
            return null;
        }

        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", begin, getStats["+logid+"] preRecord: " + preRecord.toString());
        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", begin, getStats["+logid+"] curRecord: " + curRecord.toString());

        curRecord.sysUpIncr    = curRecord.sysUp - preRecord.sysUp;
        curRecord.sysDownIncr  = curRecord.sysDown - preRecord.sysDown;

        // curRecord.userUpIncr  =  用户流量增量 ;//- 系统流量增量
        curRecord.userUpIncr   = (curRecord.mobileUp1 - preRecord.mobileUp1);// - (curRecord.sysUp - preRecord.sysUp);// 0 - 172 = -172
        curRecord.userDownIncr = (curRecord.mobileDown1 - preRecord.mobileDown1);// - (curRecord.sysDown - preRecord.sysDown);

        if ((curRecord.logId == 0) || (curRecord.logId == logid)){
            // 计算当前数据的总流量增量数据
            curRecord.sysUpIncrTotal    = preRecord.sysUpIncrTotal    + curRecord.sysUpIncr;
            curRecord.sysDownIncrTotal  = preRecord.sysDownIncrTotal  + curRecord.sysDownIncr;
            curRecord.userUpIncrTotal   = preRecord.userUpIncrTotal   + curRecord.userUpIncr;//-172
            curRecord.userDownIncrTotal = preRecord.userDownIncrTotal + curRecord.userDownIncr;
        }else if (curRecord.logId < logid){
            // 将当前数据赋值给preRecord，作为下一次数据计算的依据
            preRecord.sysUpIncrTotal    = curRecord.sysUpIncrTotal;
            preRecord.sysDownIncrTotal  = curRecord.sysDownIncrTotal;
            preRecord.userUpIncrTotal   = curRecord.userUpIncrTotal;//-172
            preRecord.userDownIncrTotal = curRecord.userDownIncrTotal;

            curRecord.sysUpIncrTotal    = preRecord.sysUpIncrTotal    + curRecord.sysUpIncr;
            curRecord.sysDownIncrTotal  = preRecord.sysDownIncrTotal  + curRecord.sysDownIncr;
            curRecord.userUpIncrTotal   = preRecord.userUpIncrTotal   + curRecord.userUpIncr;//-172
            curRecord.userDownIncrTotal = preRecord.userDownIncrTotal + curRecord.userDownIncr;

        }
        // 保持当前数据作为下次计算的preRecord数据
        preRecord.userUpIncr   = curRecord.userUpIncr;
        preRecord.userDownIncr = curRecord.userDownIncr;
        preRecord.sysUpIncr    = curRecord.sysUpIncr;
        preRecord.sysDownIncr  = curRecord.sysDownIncr;

        curRecord.logId = logid;

        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", end, getStats["+logid+"] preRecord: " + preRecord.toString());
        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", end, getStats["+logid+"] curRecord: " + curRecord.toString());


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

        JLog.logi(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", result, getStats()-> StatsData："+sd.toString());
        return sd;
    }

    private boolean isErrorData(StatsRecord mStatsRecord){
        if(mStatsRecord.sysUp == 0 && mStatsRecord.sysDown == 0 && mStatsRecord.mobileUp1 == 0 && mStatsRecord.mobileDown1 == 0){
            JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, isErrorData -> All zero  error! ===== TODO Need Handler ======= " );
            return true;
        }
        return false;

    }

    private boolean isErrorDistanceData(){
        boolean ret = false;
        if(curRecord.sysUp < preRecord.sysUp){
            JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, sysUp  error! ===== TODO Need Handler ======= " );
            ret = true;
        } else if(curRecord.sysDown < preRecord.sysDown){
            JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, seedDown  error! ===== TODO Need Handler ======= " );
            ret = true;
        } else if(curRecord.mobileUp1 < preRecord.mobileUp1){
            JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, mobileUp1  error! ===== TODO Need Handler ======= " );
            ret = true;
        } else if(curRecord.mobileDown1 < preRecord.mobileDown1){
            JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, mobileDown1  error! ===== TODO Need Handler ======= " );
            ret = true;
        }
        return ret;
    }

    private boolean isErrorSpeed(){
        boolean ret = false;
        long curTime = System.nanoTime();
        long  timeGap = TimeUnit.NANOSECONDS.toSeconds(curTime - preRecord.timeStamp);
        if (timeGap > 0) {
            rm0TxSpeed = (curRecord.mobileUp1 - preRecord.mobileUp1) / timeGap;
            rm0RxSpeed = (curRecord.mobileDown1 - preRecord.mobileDown1) / timeGap;

            if(rm0TxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES){
                JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, rm0TxSpeed  error! ===== TODO Need Handler ======= " );
                ret = true;
            } else if(rm0RxSpeed > CloudFlowCtrl.UC_MAX_SPEED_BYTES){
                JLog.loge(UploadFlowTask.INSTANCE.getCCFLOWLOG_TAG()+", CheckCCFlowData, rm0RxSpeed  error! ===== TODO Need Handler ======= " );
                ret = true;
            }
        }
        return ret;
    }

    @Override
    public long getSeedTxFlow() {
        long fs = 0L;
        if (curRecord.seedUp > preRecord.seedUp){
            fs = curRecord.seedUp - preRecord.seedUp;
        }
        return fs;
    }

    @Override
    public long getSeedRxFlow() {
        long fs = 0L;
        if (curRecord.seedDown > preRecord.seedDown){
            fs = curRecord.seedDown - preRecord.seedDown;
        }
        return fs;
    }
}
