package com.ucloudlink.refact.access;

/**
 * Created by shiqianhua on 2016/10/17.  use 0-1000
 */
public class StateMessageId {
    private final static int USER_ID_BASE                   = 0;
    public final static  int USER_LOGIN_REQ_CMD             = USER_ID_BASE + 1;
    public final static  int USER_LOGIN_RSP_CMD             = USER_ID_BASE + 2;
    public final static  int USER_LOGOUT_REQ_CMD            = USER_ID_BASE + 3;
    public final static  int USER_LOGOUT_RSP_CMD            = USER_ID_BASE + 4;
    public final static  int USER_RELOGIN_REQ_CMD           = USER_ID_BASE + 5;
    public final static  int USER_HEARTBEAT_TIMEOUT_CMD     = USER_ID_BASE + 6;
    public final static  int USER_QUIT_RELOGIN_STATE_CMD    = USER_ID_BASE + 7;
    public final static  int USER_HEART_BEAT_SEND_CMD       = USER_ID_BASE + 8;
    public final static  int USER_HEART_BEAT_RSP_CMD        = USER_ID_BASE + 9;
    public final static  int USER_ALARM_HEART_BEAT_SEND_CMD = USER_ID_BASE + 10;
    public final static  int USER_REFRESH_HEART_BEAT_CMD    = USER_ID_BASE + 11;
    public final static  int USER_LOGOUT_WAIT_TIMEOUT_CMD   = USER_ID_BASE + 12;
    public final static  int USER_LOGIN_AFTER_RESTORE_CMD   = USER_ID_BASE + 13;
    public final static  int USER_LOGIN_RECOVERY_CMD        = USER_ID_BASE + 14;
    public final static  int USER_LOGOUT_COMM_PROC          = USER_ID_BASE + 15;
    public final static  int USER_LOGIN_RETRY_CMD           = USER_ID_BASE + 16;
    public final static  int USER_MODEM_RESET               = USER_ID_BASE + 17;

    private final static int VSIM_ID_BASE                    = 100;
    public final static  int VSIM_BEGIN_REQ_CMD              = VSIM_ID_BASE + 1;
    public final static  int DOWNLOAD_VSIM_REQ_CMD           = VSIM_ID_BASE + 2;
    public final static  int DOWNLOAD_VSIM_RSP_CMD           = VSIM_ID_BASE + 3;
    public final static  int START_VSIM_REQ_CMD              = VSIM_ID_BASE + 4;
    public final static  int START_VSIM_RSP_CMD              = VSIM_ID_BASE + 5;
    public final static  int VSIM_REG_REQ_CMD                = VSIM_ID_BASE + 8;
    public final static  int VSIM_REG_RSP_CMD                = VSIM_ID_BASE + 9;
    public final static  int VSIM_DATACALL_REQ_CMD           = VSIM_ID_BASE + 10;
    public final static  int VSIM_DATACALL_RSP_CMD           = VSIM_ID_BASE + 11;
    public final static  int SWITCH_VSIM_MANAUL_CMD          = VSIM_ID_BASE + 12;
    public final static  int SOCKET_CREATE_FAIL_CMD          = VSIM_ID_BASE + 13;
    public final static  int RELEASE_VSIM_CMD                = VSIM_ID_BASE + 14;
    public final static  int VSIM_REG_TIMEOUT_CMD            = VSIM_ID_BASE + 15;
    public final static  int VSIM_DATACALL_TIMEOUT_CMD       = VSIM_ID_BASE + 16;
    public final static  int VSIM_CARD_STATE_CHG_CMD         = VSIM_ID_BASE + 17;
    public final static  int SWITCH_VSIM_RSP_CMD             = VSIM_ID_BASE + 18;
    public final static  int QUIT_WAIT_SWITCH_VSIM_STATE_CMD = VSIM_ID_BASE + 19;
    public final static  int VSIM_READY_TIMEOUT              = VSIM_ID_BASE + 20;
    public final static  int GET_NETWORK_STATE_RSP_CMD       = VSIM_ID_BASE + 21;
    public final static  int RETRY_GET_NETWORK_STATE_REQ     = VSIM_ID_BASE + 22;
    public final static  int PRELOGIN_IP_CHECK_RSP_CMD       = VSIM_ID_BASE + 23;
    public final static  int DELAY_RETRY_IP_CHECK_REQ_CMD    = VSIM_ID_BASE + 24;
    public final static  int VSIM_RELEASE_TIMEOUT_CMD        = VSIM_ID_BASE + 25;
    public final static  int GET_VSIM_INFO_RSP_CMD           = VSIM_ID_BASE + 26;
    public final static  int DISPATCH_VSIM_RSP_CMD           = VSIM_ID_BASE + 27;
    public final static  int DISPATCH_VSIM_RETRY_CMD         = VSIM_ID_BASE + 28;
    public final static  int SWITCH_VSIM_RETRY_CMD           = VSIM_ID_BASE + 29;
    public final static  int PRELOGIN_IP_FAIL_STOP           = VSIM_ID_BASE + 30;
    public final static  int UPLOAD_FLOW_MANUAL_CMD          = VSIM_ID_BASE + 31;
    public final static  int PRELOGIN_IP_FAIL_RETRY           = VSIM_ID_BASE + 32;
    //    public final static int START_DOWN_LOAD_EXT_SOFTSIM_CMD = VSIM_ID_BASE + 32;

    private final static int OTHER_EVENT_BASE                = 300;
    public final static  int SEED_SIM_ABSET                  = OTHER_EVENT_BASE + 1;
    public final static  int CLOUD_SIM_ABSET                 = OTHER_EVENT_BASE + 2;
    public final static  int CHECK_CLOUDSIM_STATE            = OTHER_EVENT_BASE + 3;
    public final static  int WIFI_CONNECTED                  = OTHER_EVENT_BASE + 4;
    public final static  int WIFI_DISCONNECTED               = OTHER_EVENT_BASE + 5;
    public final static  int DO_RECOVERY_TIMEOUT             = OTHER_EVENT_BASE + 6;
    //public final static int CARD_STATE_CHANGE       = OTHER_EVENT_BASE + 7;
    public final static  int NETWORK_STATE_CHANGE            = OTHER_EVENT_BASE + 8;
    public final static  int NETWORK_CHECK_INTVL             = OTHER_EVENT_BASE + 9;
    public final static  int DISCONNECT_ALL_SIM_WAIT         = OTHER_EVENT_BASE + 10;
    public final static  int INIT_ENV                        = OTHER_EVENT_BASE + 11;
    public final static  int EXCEPTION_TIMEOUT               = OTHER_EVENT_BASE + 12;
    public final static  int SEED_CARD_EXCEPTION             = OTHER_EVENT_BASE + 13;
    public final static  int REMOVE_EXCEPTION_OUT_GOING_CALL = OTHER_EVENT_BASE + 14;
    public final static  int TRACEROUTE_EVENT                = OTHER_EVENT_BASE + 15;
    public final static  int SEED_CARD_STATU_CHANGE         = OTHER_EVENT_BASE + 16;

    protected final static int QC_EVENT_BASE = 1000;
}
