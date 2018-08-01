package com.ucloudlink.refact.access;

import com.ucloudlink.refact.config.Configuration;

/**
 * Created by shiqianhua on 2016/12/1.
 */

public class TimeoutValue {
    public static final int PRELOGIN_IP_POOLING_TIMEOUT = 120;
    public static final int SOCKET_CONNECT_TIMEOUT = 35;
    static final int RETRY_GET_NETWORK_INFO_DELAY = 5;
    static final int RETRY_PRELOGIN_CHECK_IP_DELAY = 3;
    static final int WAIT_SOFT_CARD_CONNECTED = 200;
    static final int WAIT_PHY_CARD_CONNECTED = 30;
    static final int SOCKET_CONNECT_FIRST_TIME_TIMEOUT = 10; //拨号连接建立时第一次CONNECT超时10秒，以后35秒
    private static final int LOGIN_TIMEOUT_TOTAL_PHY = 270; // unit:s
    private static final int LOGIN_TIMEOUT_TOTAL_SOFT = 465;
    private static final int LOGIN_REMOTE_TIMEOUT = 35;
    private static final long GET_LOGIN_PARAM_TIMEOUT_PHY = 35;
    private static final long GET_LOGIN_PARAM_TIMEOUT_SOFT = 205;
    private static final long IP_POLLING_TIMEOUT = 77;
    private static final int LOGOUT_TIMEOUT_TOTAL = 4;
    private static final int LOGOUT_WAIT_UPLOAD_STAT_TIMEOUT = 1; // 退出成功后，等待发送状态给应用层
    private static final int REQUEST_VSIM_BIN_TIMEOUT_TOTAL_PHY = 115;
    private static final int REQUEST_VSIM_BIN_TIMEOUT_TOTAL_SOFT = 150;
    private static final int REQUEST_VSIM_BIN_TIMEOUT = 35;
    private static final int ENABLE_CARD_TIMEOUT = 60;
    private static final int SWITCH_VSIM_TIMEOUT_TOTAL_PHY = 115;
    private static final int SWITCH_VSIM_TIMEOUT_TOTAL_SOFT = 150;
    private static final int SWITCH_VSIM_TIMEOUT = 35;
    private static final int GET_VSIM_INFO_TIMEOUT_TOTAL_PHY = 115;
    private static final int GET_VSIM_INFO_TIMEOUT_TOTAL_SOFT = 115;

    //    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_PHY_LOCAL = 140;
//    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_PHY_ROAM = 260;
//    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_SOFT_LOCAL = 175;
//    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_SOFT_ROAM = 295;
    private static final int GET_VSIM_INFO_TIMEOUT = 35;
    private static final int HEARTBEAT_SEND_INTVL_FIRST = (8 * 60);
    private static final int HEARTBEAT_SEND_INTVL_SECOND = 45;
    private static final int HEARTBEAT_SEND_TIMEOUT_CNT = 45;
    private static final int HEARTBEAT_MAX_TIMEOUT = 2700;
    private static final int ALLOC_VSIM_TIMEOUT_TOTAL_PHY = 115;
    private static final int ALLOC_VSIM_TIMEOUT_TOTAL_SOFT = 150;
    private static final int ALLOC_VSIM_TIMEOUT = 35;
    //调整超时时间， 增加40s
    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_PHY_LOCAL = 180;
    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_PHY_ROAM = 300;
    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_SOFT_LOCAL = 215;
    private static final int VSIM_AVAILABLE_TIMEOUT_TOTAL_SOFT_ROAM = 235;
    private static final int VSIM_DATACALL_TIMEOUT = 60;  // 暂时设置成1min，测试不同场景下的拨号时长，再定义一下超时
    private static final int PERF_ABNORMAL_TIMEOUT = (6 * 60);
    private static final int USER_EXPERIENCE_TIMEOUT = 90;
    private static final int SWAP_VSIM_CARD_TIMEOUT = 15;
    private static final int SIM_READY_TIMEOUT = 45;//等待卡ready的超时时间

    public static int getLoginTimeoutTotal(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return LOGIN_TIMEOUT_TOTAL_SOFT;
        }else{
            return LOGIN_TIMEOUT_TOTAL_PHY;
        }
    }

    public static long getLoginParamTimeout(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return GET_LOGIN_PARAM_TIMEOUT_SOFT;
        }else{
            return GET_LOGIN_PARAM_TIMEOUT_PHY;
        }
    }

    public static long getSeedCardConnectedTimeout(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return WAIT_SOFT_CARD_CONNECTED;
        }else{
            return WAIT_PHY_CARD_CONNECTED;
        }
    }

    public static int getHeartbeatMaxTimeout() {
        return HEARTBEAT_MAX_TIMEOUT;
    }

    public static long getIpPollingTimeout(){
        return IP_POLLING_TIMEOUT;
    }


    public static int getLoginTimeout(){
        return LOGIN_REMOTE_TIMEOUT;
    }

    public static int getLogoutTimeoutTotal(){
        return LOGOUT_TIMEOUT_TOTAL;
    }

    public static int getLogoutWaitUploadStatTimeout(){
        return LOGOUT_WAIT_UPLOAD_STAT_TIMEOUT;
    }

    public static int getRequestVsimBinTimeoutTotal(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return REQUEST_VSIM_BIN_TIMEOUT_TOTAL_SOFT;
        }else {
            return REQUEST_VSIM_BIN_TIMEOUT_TOTAL_PHY;
        }
    }

    public static int getSimReadyTimeout() {
        return SIM_READY_TIMEOUT;
    }

    public static int getRequestVsimBinTimeout(){
        return REQUEST_VSIM_BIN_TIMEOUT;
    }

    public static int getEnableCardTimeout(){
        return ENABLE_CARD_TIMEOUT;
    }

    public static int getSwitchVsimTimeoutTotal(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return SWITCH_VSIM_TIMEOUT_TOTAL_SOFT;
        }else{
            return SWITCH_VSIM_TIMEOUT_TOTAL_PHY;
        }
    }

    public static int getSwitchVsimTimeout(){
        return SWITCH_VSIM_TIMEOUT;
    }

    public static int getHeartbeatSendIntvlFirst(){
        return HEARTBEAT_SEND_INTVL_FIRST;
    }

    public static int getHeartbeatSendIntvlSecond(){
        return HEARTBEAT_SEND_INTVL_SECOND;
    }

    public static  int getHeartbeatSendTimeoutCnt(){
        return HEARTBEAT_SEND_TIMEOUT_CNT;
    }

    public static int getAllocVsimTimeoutTotal(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return ALLOC_VSIM_TIMEOUT_TOTAL_SOFT;
        }else {
            return ALLOC_VSIM_TIMEOUT_TOTAL_PHY;
        }
    }

    public static int getVsimInfoTimeout(){
        return GET_VSIM_INFO_TIMEOUT;
    }

    public static int getVsimInfoTimeoutTotal(){
        if(Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()){
            return GET_VSIM_INFO_TIMEOUT_TOTAL_SOFT;
        }else {
            return GET_VSIM_INFO_TIMEOUT_TOTAL_PHY;
        }
    }

    public static int getAllocVsimTimeout(){
        return ALLOC_VSIM_TIMEOUT;
    }

    public static int getVsimAvailableTimeoutTotal(boolean isLocalCard){
        boolean isSoftMode = (Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft());

        if(isSoftMode){
            if(isLocalCard){
                return VSIM_AVAILABLE_TIMEOUT_TOTAL_SOFT_LOCAL;
            }else{
                return VSIM_AVAILABLE_TIMEOUT_TOTAL_SOFT_ROAM;
            }
        }else{
            if(isLocalCard){
                return VSIM_AVAILABLE_TIMEOUT_TOTAL_PHY_LOCAL;
            }else{
                return VSIM_AVAILABLE_TIMEOUT_TOTAL_PHY_ROAM;
            }
        }
    }

    public static int getVsimDatacallTimeout(){
        return VSIM_DATACALL_TIMEOUT;
    }

    public static int getPerfAbnormalTimeout(){
        return PERF_ABNORMAL_TIMEOUT;
    }

    public static int getUserExperienceTimeout(){
        return USER_EXPERIENCE_TIMEOUT;
    }

    public static int getSwapVsimCardTimeout(){
        return SWAP_VSIM_CARD_TIMEOUT;
    }
}
