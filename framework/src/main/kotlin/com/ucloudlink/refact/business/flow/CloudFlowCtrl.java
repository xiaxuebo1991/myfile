package com.ucloudlink.refact.business.flow;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils;

import static com.ucloudlink.refact.utils.JLog.logd;


/**
 * Created by pengchugang on 2016/10/22.
 */
public class CloudFlowCtrl {

    /**
     * S1 上：
     * Note: 定义没错，云卡流量的用户流量跟种子卡的用户流量定义不一样
     * 种子卡流量：
     * 用户流量：应用本身使用的流量 UService
     * 系统流量：其他App使用的流量
     * 云卡流量：
     * 用户流量：其他App使用的流量
     * 系统流量：UService使用的流量
     *
     * U3C上：
     * 种子卡系统流量 = U3C本机流量
     * 种子卡用户流量 = U3C wifi client流量
     * */

    public static final Long UC_MAX_SPEED_BYTES = 120L * 1024 * 1024;

    public static final int UC_STATS_STATUS_STOP = 0;
    public static final int UC_STATS_STATUS_PAUSE = 1;
    public static final int UC_STATS_STATUS_ACTIVE = 2;

    public static final int UC_SIMCARD_TYPE_SEED = 1;
    public static final int UC_SIMCARD_TYPE_CLOUD = 4;
    public static final int UC_SIMCARD_TYPE_SEED_CLOUD = 5;

    public static final int STATS_MODE_SYSTEM_IF = 0;
    public static final int STATS_MODE_READ_FILE = 1;
    private static int statis_mode = STATS_MODE_READ_FILE;

    private static int seedSimCard = 0;
    private static int cloudSimCard = 0;

    private int curUid;
    private int statsStatus = 0;   /* stop,enable,pause */

    private ICloudFlowCtrl mICloudFlowStats;


    public CloudFlowCtrl(){
        mICloudFlowStats = ServiceManager.systemApi.getICloudFlowCtrl();
    }

    public void setCloudIfName(String ifName){
        mICloudFlowStats.setIfName(ifName);
    }

    public String getCloudIfName(){
        return mICloudFlowStats.getIfName();
    }

    public long getSeedTxFlow(){
        return mICloudFlowStats.getSeedTxFlow();
    }

    public long getSeedRxFlow(){
        return mICloudFlowStats.getSeedRxFlow();
    }


    public void enableSeedSimCard(){
        logd("FlowLog, enable seed card.");

        if (seedSimCard == 0) {
            mICloudFlowStats.enableSeedSimCard();
        }
        seedSimCard = 1;
    }


    public void disableSeedSimCard() {
        logd("FlowLog, disable seed card.");
        seedSimCard = 0;
    }

    public void enableCloudSimCard() {
        logd("FlowLog, enable cloud card.");
        if (seedSimCard == 1) {
            mICloudFlowStats.enableCloudSimCard();
        }
        cloudSimCard = 1;
    }

    public void disableCloudSimCard() {
        logd("FlowLog, disable cloud card.");
        cloudSimCard = 0;
    }

    public int getSeedSimCardStatus() {
        return seedSimCard;
    }

    public int getCloudSimCardStatus() {
        return cloudSimCard;
    }

    private int getDataChannelCardType(){
        int ct = 0;

        if (getCloudSimCardStatus() == 1){
            ct = UC_SIMCARD_TYPE_CLOUD;
        }
        if (getSeedSimCardStatus() == 1){
            ct += UC_SIMCARD_TYPE_SEED;
        }
        return ct;
    }

    public void resetTmpSd() {
        mICloudFlowStats.resetTmpSd();
    }

    private void initStats(){
        mICloudFlowStats.initStats();

        statsStatus = UC_STATS_STATUS_STOP;
        statis_mode = STATS_MODE_READ_FILE;

        logd("FlowLog, initStats()-> get flowStats mode:" + statis_mode);
    }

    public int getStatus(){
        return statsStatus;
    }

    public StatsData getStats(int logid) {
        return mICloudFlowStats.getStats(statsStatus, statis_mode, logid, curUid, getDataChannelCardType());

    }

    public int startStats() {
        if (statsStatus == UC_STATS_STATUS_ACTIVE){
            logd("FlowLog, startStats now.");
            return statsStatus;
        }
        initStats();
        statsStatus = UC_STATS_STATUS_ACTIVE;
        curUid = SysUtils.getUServiceUid();
        if (curUid == 0){
            return -1;
        }

        mICloudFlowStats.startStats(statis_mode, curUid);

        return statsStatus;
    }

    public int stopStats() {
        if (statsStatus == UC_STATS_STATUS_STOP){
            logd("FlowLog, has stopStats now.");
            return statsStatus;
        }

        statsStatus = UC_STATS_STATUS_STOP;
        logd("FlowLog, stopStats");

        mICloudFlowStats.stopStats(statis_mode, curUid);

        return statsStatus;
    }

}
