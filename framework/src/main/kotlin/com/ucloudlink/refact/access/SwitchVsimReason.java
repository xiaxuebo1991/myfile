package com.ucloudlink.refact.access;

/**
 * Created by shiqianhua on 2016/10/24.
 */
public class SwitchVsimReason {

    // inner user
    public final static int REG_TIMEOUT = 1;
    public final static int DATACALL_TIMEOUT = 2;
    public final static int SERVER_REQUEST = 3;
    public final static int PDP_REJECT = 4;
    public final static int ATTACH_REJECT = 5;
    public final static int VSIM_SOCKET_ERROR = 6;
    public final static int MCC_CHANGE = 7;
    public final static int RTU = 8;
    public final static int ILLEGAL_VIRTUAL_IMEI = 9;
    public final static int BIN_TOO_LONG = 10;
    public final static int BIN_PACKAGE_ERROR = 11;
    public final static int BIN_DOWNLOAD_ERROR = 12;
    public final static int BAN_APDU_ERROR = 13;
    public final static int VSIM_CARD_ADD_TIMEOUT = 14;
    public final static int VSIM_CARD_INSERT_TIMEOUT = 15;
    public final static int VSIM_CARD_READY_TIMEOUT = 16;
    public final static int VSIM_CARD_INSERVICE_TIMEOUT = 17;
    public final static int VSIM_CARD_CONNECT_TIMEOUT = 18;
    public final static int VSIM_CARD_APDU_INVALID = 19;
    public final static int INVALID_VSIM_APN = 20;
    public final static int SWITCH_MANUAL = 21;
    public final static int VSIM_INFO_GET_FAIL = 22;
    public final static int INVALID_VSIM_IMSI = 23;
    public final static int VSIM_REG_REJECT = 24;
    public final static int VSIM_NET_FAIL = 25;



    public static String getSwitchVsimReasonStr(int reason){
        switch (reason){
            case REG_TIMEOUT:
                return "vsim reg timeout";
            case DATACALL_TIMEOUT:
                return "vsim datacall timeout";
            case SERVER_REQUEST:
                return "server send switch vsim";
            case PDP_REJECT:
                return "pdp reject";
            case ATTACH_REJECT:
                return "attach reject";
            case VSIM_SOCKET_ERROR:
                return "vsim socket failed";
            case MCC_CHANGE:
                return "country change";
            case RTU:
                return "rtu request";
            case ILLEGAL_VIRTUAL_IMEI:
                return "illegal virtual imei";
            case BIN_TOO_LONG:
                return "vsim bin file too long";
            case BIN_PACKAGE_ERROR:
                return "vsim bin file package error";
            case BIN_DOWNLOAD_ERROR:
                return "bin file download error";
            case BAN_APDU_ERROR:
                return "bam apdu error";
            case VSIM_CARD_ADD_TIMEOUT:
                return "vsim card add timeout";
            case VSIM_CARD_INSERT_TIMEOUT:
                return "vsim card insert timeout";
            case VSIM_CARD_INSERVICE_TIMEOUT:
                return "vsim card inservice timeout";
            case VSIM_CARD_CONNECT_TIMEOUT:
                return "vsim card connect timeout";
            case VSIM_CARD_APDU_INVALID:
                return "vsim card apdu invalid";
            case INVALID_VSIM_APN:
                return "vsim invalid apn";
            case SWITCH_MANUAL:
                return "switch by user";
            case VSIM_INFO_GET_FAIL:
                return "vsim get info fail";
            case INVALID_VSIM_IMSI:
                return "invalid vsim imsi";
            default:
                return "unknown error," + reason;
        }
    }
    // server use
    private final static int SER_CMD_FROM_SERVER = 0;
    private final static int SER_PDP_REJECT_5_TIMES = 1;
    private final static int SER_ATTACH_REJECT = 2;
    private final static int SER_REG_NETWORK_FAIL = 3;
    private final static int SER_HOST_SOCKET_CREATE_FAIL = 4;
    private final static int SER_REG_NET_NO_PS = 5;
    private final static int SER_ILLEGAL_VIRTUAL_IMEI = 6;
    private final static int SER_VSIM_BIN_PACK_TOO_LONG = 7;
    private final static int SER_VSIM_BIN_PACK_DOWNLOAD_ERR = 8;
    private final static int SER_VSIM_BIN_PACK_DATA_ERR = 9;
    private final static int SER_VSIM_BAN_APDU_ERR = 10;
    private final static int SER_RTU = 11;
    private final static int SER_VSIM_SOCKET_CREATE_ERR = 12;
    private final static int SER_VSIM_DATACALL_FAIL = 13;
    private final static int SER_VSIM_MCC_CHANGE = 14;
    public final static int SER_VSIM_PUBLISH_TIMER = 15;

    private final static int DSDS_ERR_BASE = 1000;
    private final static int SER_VSIM_CARD_ADD_TIMEOUT = DSDS_ERR_BASE + 1;
    private final static int SER_VSIM_CARD_INSERT_TIMEOUT = DSDS_ERR_BASE + 2;
    private final static int SER_VSIM_CARD_READY_TIMEOUT = DSDS_ERR_BASE + 3;
    private final static int SER_VSIM_CARD_INSERVICE_TIMEOUT = DSDS_ERR_BASE + 4;
    private final static int SER_VSIM_CARD_CONNECT_TIMEOUT = DSDS_ERR_BASE + 5;
    private final static int SER_VSIM_CARD_APDU_INVALID = DSDS_ERR_BASE + 6;
    private final static int SER_VSIM_INVALID_APN = DSDS_ERR_BASE + 7;
    private final static int SER_VSIM_SWITCH_MANUAL_FROM_TER = DSDS_ERR_BASE + 8;
    private final static int SER_VSIM_GET_INFO_FAIL = DSDS_ERR_BASE + 9;
    private final static int SER_VSIM_INVALID_IMSI = DSDS_ERR_BASE + 10;
    private final static int SER_VSIM_REG_REJECT = DSDS_ERR_BASE + 11;
    private final static int SER_VSIM_NET_FAIL = DSDS_ERR_BASE + 12;

    public static int getServerSwitchReason(int innerReason){
        switch (innerReason){
            case REG_TIMEOUT:
                return SER_REG_NETWORK_FAIL;
            case DATACALL_TIMEOUT:
                return SER_VSIM_DATACALL_FAIL;
            case SERVER_REQUEST:
                return SER_CMD_FROM_SERVER;
            case PDP_REJECT:
                return SER_PDP_REJECT_5_TIMES;
            case ATTACH_REJECT:
                return SER_ATTACH_REJECT;
            case VSIM_SOCKET_ERROR:
                return SER_HOST_SOCKET_CREATE_FAIL;
            case MCC_CHANGE:
                return SER_VSIM_MCC_CHANGE;
            case RTU:
                return SER_RTU;
            case ILLEGAL_VIRTUAL_IMEI:
                return SER_ILLEGAL_VIRTUAL_IMEI;
            case BIN_TOO_LONG:
                return SER_VSIM_BIN_PACK_TOO_LONG;
            case BIN_PACKAGE_ERROR:
                return SER_VSIM_BIN_PACK_DATA_ERR;
            case BIN_DOWNLOAD_ERROR:
                return SER_VSIM_BIN_PACK_DOWNLOAD_ERR;
            case BAN_APDU_ERROR:
                return SER_VSIM_BAN_APDU_ERR;
            case VSIM_CARD_ADD_TIMEOUT:
                return SER_VSIM_CARD_ADD_TIMEOUT;
            case VSIM_CARD_INSERT_TIMEOUT:
                return SER_VSIM_CARD_INSERT_TIMEOUT;
            case VSIM_CARD_READY_TIMEOUT:
                return SER_VSIM_CARD_READY_TIMEOUT;
            case VSIM_CARD_INSERVICE_TIMEOUT:
                return SER_VSIM_CARD_INSERVICE_TIMEOUT;
            case VSIM_CARD_CONNECT_TIMEOUT:
                return SER_VSIM_CARD_CONNECT_TIMEOUT;
            case VSIM_CARD_APDU_INVALID:
                return SER_VSIM_CARD_APDU_INVALID;
            case INVALID_VSIM_APN:
                return SER_VSIM_INVALID_APN;
            case SWITCH_MANUAL:
                return SER_VSIM_SWITCH_MANUAL_FROM_TER;
            case VSIM_INFO_GET_FAIL:
                return SER_VSIM_GET_INFO_FAIL;
            case INVALID_VSIM_IMSI:
                return SER_VSIM_INVALID_IMSI;
            case VSIM_REG_REJECT:
                return SER_VSIM_REG_REJECT;
            case VSIM_NET_FAIL:
                return SER_VSIM_NET_FAIL;
            default:
                return 0;
        }
    }

}
