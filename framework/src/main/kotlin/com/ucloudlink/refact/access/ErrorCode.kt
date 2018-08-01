package com.ucloudlink.refact.access

import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.utils.JLog.logd
import java.util.concurrent.TimeoutException

/**
 * Created by shiqianhua on 2017/5/19.
 */
object ErrorCode {

    val RPC_RET_OK = 100

    val RPC_NO_VSIM_AVAILABLE = 3040000
    val RPC_GET_USERINFO_FAIL = 3040001
    val RPC_EMPTY_PLMN_FROM_TER = 3040002
    val RPC_EMPTY_MCC_FROM_TER = 3040003
    val RPC_NO_PRODUCT_BY_ID = 3040004

    val RPC_NO_POLICY_BY_ID = 3040005
    val RPC_NO_CARD_POOL_BY_POLICY = 3040006
    val RPC_NO_CARD_BY_IMSI = 3040007
    val RPC_NO_ONLINE_USER_BY_USERCODE = 3040008
    val RPC_IMSI_NOT_EXIST = 3040009

    val RPC_NO_GROUP_BY_IMSI = 3040100
    val RPC_CARD_RELEASE_FAIL = 3040200
    val RPC_LAST_IMSI_ERROR = 3040201
    val RPC_RAT_ERROR = 3040010
    val RPC_UPDATE_USER_ONLINE_ERR = 3040011

    val RPC_ADD_DISPATCH_CARD_LOG_ERR = 3040012
    val RPC_NO_NETWORK_AVAILABLE = 3040013
    val RPC_VSIM_BIN_GET_FAIL = 3040401

    val RPC_NO_AVAILABLE_SOFTSIM = 3130000
    val RPC_SOFTSIM_PARAM_ERROR = 3130001
    val RPC_ORDER_NOT_EXIST = 3130002
    val RPC_CALL_BSS_SERVER_FAIL = 3130100
    val RPC_ORDER_COUNTRY_IS_NULL = 3130101

    val RPC_ORDER_GOODS_NOT_EXIST = 3130102
    val RPC_ORDER_GOODS_IS_NULL = 3130103
    val RPC_ODER_NO_NEED_SOFTSIM = 3130104

    val RPC_SEED_NO_SOFTSIM = 3130004

    val RPC_USER_NOT_EXIST = 1160001
    val RPC_PASSWD_CHECK_FAIL = 1160002
    val RPC_IMEI_OR_USERNAME_NULL = 1160003
    val RPC_IMEI_BIND_NOT_EXIST = 1160004
    val RPC_USER_OR_PASSWD_NULL_OR_NOT_IN_BIND = 1160005

    val RPC_NO_ACTIVATE_AFTER_FREE_USE = 1160006
    val RPC_BOTH_HAVE_DAILY_MONTHLY_PACKAGE = 1160008
    val RPC_MONTHLY_USERS_FULL = 1160009
    val RPC_FEE_NOT_ENOUGH_FOR_DAILY_PACKAGE = 1160010

    val RPC_NOT_IN_FAVOR_COUNTRY = 1160011
    val RPC_GET_USER_INFO_FAIL = 1160012
    val RPC_IMEI_NOT_EXIST = 1160013
    val RPC_IMEI_ALREADY_DELETED = 1160014
    val RPC_GET_ACCESS_TOKEN_ERR = 1160015

    val RPC_USER_ACCOUNT_ERR = 1160016
    val RPC_BSS_UNKNOWN_ERR = 1160017
    val RPC_ACCOUNT_IS_DISALBE = 1160018


    val RPC_CALL_BSS_FAIL = 2161111
    val RPC_CALL_CSS_FAIL = 2162000
    val RPC_CALL_OSS_FAIL = 2163000
    val RPC_CALL_BAM_FAIL = 2164000

    val RPC_INVALID_SESSION = 2160001
    val RPC_APDU_DEAL_ERR = 2160002
    val RPC_CALL_DUBBO_FAIL = 2160003
    val RPC_CALL_SYSTEM_SERVICE_FAIL = 2160004
    val RPC_TER_SID_EMPTY = 2160006

    val RPC_TER_IMEI_EMPTY = 2160007
    val RPC_LOGIN_FAIL_RET_NULL = 2160008
    val RPC_GET_SERVICE_LIST_FAIL = 2160009
    val RPC_DISPATCH_CARD_FAIL = 2160010
    val RPC_CALL_LOGIN_AUTH = 2160011

    val RPC_FEE_NOT_ENOUGH = 2160012
    val RPC_PLMN_LIST_EMPTY = 2160020

    val RPC_ASS_UNKNOWN_ERR = 2169999

    val LOCAL_PHY_CARD_NOT_EXIST = 1001
    val LOCAL_SOFT_CARD_NOT_EXIST = 1002
    val LOCAL_PHONE_DATA_DISABLED = 1003
    val LOCAL_PHY_NETWORK_UNAVAILABLE = 1004
    val LOCAL_PHONE_CALLING = 1005

    val LOCAL_PHY_CARD_DISABLE = 1006
    val LOCAL_AIR_MODE_ENABLED = 1007
    val LOCAL_APP_IN_BLACKLIST = 1008
    val LOCAL_APP_RECOVERY_TIMEOUT = 1009
    val LOCAL_USER_LOGIN_OTHER_PLACE = 1010

    val LOCAL_BIND_CHANGE = 1011
    val LOCAL_FLOW_CTRL_EXIT = 1012
    val LOCAL_FORCE_LOGOUT_MANUAL = 1013
    val LOCAL_FORCE_LOGOUT_FEE_NOT_ENOUGH = 1014
    val LOCAL_ROAM_NOT_ENABLED = 1015

    val LOCAL_ORDER_IS_NULL = 1016
    val LOCAL_ORDER_INFO_IS_NULL = 1017
    val LOCAL_ORDER_SOFTSIM_NULL = 1018
    val LOCAL_TIMEOUT   = 1019
    val LOCAL_ORDER_INACTIVATE = 1020

    val LOCAL_ORDER_OUT_OF_DATE = 1021
    val LOCAL_USERNAME_INVALID = 1022
    val LOCAL_SERVICE_RUNNING = 1023
    val LOCAL_SECURITY_FAIL = 1024
    val LOCAL_SECURITY_TIMEOUT = 1025

    val LOCAL_BIND_NETWORK_FAIL = 1026
    val LOCAL_FORCE_LOGOUT_UNKNOWN = 1027
    var LOCAL_ACCOUNT_IS_DISABLE = 1028
    var LOCAL_DEVICE_IS_DISABLE = 1029
    var LOCAL_HOST_LOGIN_SLEEP = 1030 //休眠抢卡

    val LOCAL_USER_AIR_MODE_ENABLE = 1051
    val LOCAL_USER_AIR_MODE_DISABLE = 1052
    val LOCAL_USER_PHONE_DATA_DISABLE = 1053
    val LOCAL_USER_PHONE_DATA_ENABLE = 1054
    val LOCAL_USER_CHANGE_DDS = 1055

    val LOCAL_USER_DDS_CHANGE_BACK = 1056
    val LOCAL_USER_PHONE_CALL_START = 1057
    val LOCAL_USER_PHONE_CALL_STOP = 1058
    val LOCAL_USER_SEED_SIM_DISABLE = 1059
    val LOCAL_USER_SEED_SIM_ENABLE = 1060

    val LOCAL_USER_WIFI_CONNECTED = 1061
    val LOCAL_USER_WIFI_DISCONNECTED = 1062
    val LOCAL_USER_APP_TO_BLACKLIST = 1063
    val LOCAL_USER_APP_OUT_BLACKLIST = 1064
    val LOCAL_USER_CLOUD_SIM_DISABLE = 1065

    val LOCAL_USER_CLOUD_SIM_ENABLE = 1066
    val LOCAL_SERVER_UNKNOWN_ERR = 1067
    val LOCAL_SERVER_PACKAGE_PARSE_ERR = 1068
    val LOCAL_LOGIN_TIMEOUT = 1069
    val LOCAL_UNKNOWN_ERROR = 1070

    val LOCAL_INVALID_VSIM_APN = 1071
    val LOCAL_INVALID_VSIM_IMSI = 1072
    val LOCAL_INVALID_VSIM_VIRT_IMEI = 1073
    val LOCAL_INVALID_SOFT_SIM_IMSI = 1074
    val LOCAL_INVALID_SOFT_SIM_VIRT_IMEI = 1075

    val LOCAL_INVALID_SOFT_SIM_APN = 1076
    val LOCAL_GET_ROUTE_TABLE_FAIL = 1077 //获取路由表失败
    val LOCAL_AIR_MODE_OVER_10MIN = 1078
    val LOCAL_SEED_CARD_DISABLE_OVER_10MIN = 1079
    val LOCAL_CLOUD_CARD_DISABLE_OVER_10MIN =1080

    val LOCAL_PHONE_DATA_DISABLE_OVER_10MIN = 1081
    val LOCAL_APP_IN_BLACKLIST_OVER_10MIN = 1082
    val LOCAL_DDS_EXCEPTION_OVER_10MIN = 1083
    val LOCAL_DDS_IN_EXCEPTION = 1084
    val LOCAL_DDS_SET_TO_NORMAL = 1085

    val LOCAL_SEND_FAILED_SINCE_NOT_READY = 1086
    val LOCAL_NO_SEED_CARD = 1087
    val LOCAL_CONFIG_ERROR = 1088
    val LOCAL_USER_PHY_SUBID_INVALID = 1089
    val LOCAL_USER_PHY_APN_INVALID = 1090

    val LOCAL_SYSTEM_ERROR = 1091
    val LOCAL_MCC_CHANGE = 1092

    val LOCAL_OUT_GOING_CALL_IN_EXCEPTION = 1093
    
    private val SOFTSIM_DL_BASE = 1500
    val SOFTSIM_DL_SUCC = 0

    val SOFTSIM_DL_PARAM_ERR = SOFTSIM_DL_BASE + 1
    val SOFTSIM_DL_LOGIN_TIMEOUT = SOFTSIM_DL_BASE + 2
    val SOFTSIM_DL_DISPATCH_TIMEOUT = SOFTSIM_DL_BASE + 3
    val SOFTSIM_DL_GET_SOFTSIM_INFO_TIMEOUT = SOFTSIM_DL_BASE + 4
    val SOFTSIM_DL_GET_BIN_TIMEOUT = SOFTSIM_DL_BASE + 5

    val SOFTSIM_DL_USER_CANCEL = SOFTSIM_DL_BASE + 6
    val SOFTSIM_DL_SOCKET_TIMEOUT = SOFTSIM_DL_BASE + 7
    val SOFTSIM_DL_CHANGE_NEW_USER = SOFTSIM_DL_BASE + 8
    val SOFTSIM_DL_NO_ORDER = SOFTSIM_DL_BASE + 9
    val SOFTSIM_DL_NO_SOFTSIM = SOFTSIM_DL_BASE + 10

    val SOFTSIM_DL_BIN_FILE_NULL = SOFTSIM_DL_BASE + 11
    val SOFTSIM_DL_NETWORK_TIMEOUT = SOFTSIM_DL_BASE + 12
    val SOFTSIM_DL_SEED_NETWORK_FAIL = SOFTSIM_DL_BASE + 13
    val SOFTSIM_UP_SESSION_INVALID = SOFTSIM_DL_BASE + 14
    val SOFTSIM_UP_USER_CANCEL = SOFTSIM_DL_BASE + 15

    val SOFTSIM_UP_DL_START = SOFTSIM_DL_BASE + 16
    val SOFTSIM_DOWNLOAD_ADDCARD_FAIL = SOFTSIM_DL_BASE + 17
    val SOFTSIM_DL_RSP_INVALID = SOFTSIM_DL_BASE + 18
    val SOFTSIM_INVALID_KI = SOFTSIM_DL_BASE + 19
    val SOFTSIM_INVALID_OPC = SOFTSIM_DL_BASE + 20

    val CARD_ERR_BASE = 1600
    val CARD_EXCEP_CARD_PARAMETER_WRONG = CARD_ERR_BASE + 1
    val CARD_EXCEP_PHY_CARD_IS_NULL = CARD_ERR_BASE + 2
    val CARD_EXCEP_PHY_CARD_DEFAULT_LOST = CARD_ERR_BASE + 3
    val CARD_INSERT_SOFT_SIM_TIMEOUT = CARD_ERR_BASE + 4
    val CARD_ADD_SOFT_SIM_TIMEOUT = CARD_ERR_BASE + 5

    val CARD_READY_TIMEOUT = CARD_ERR_BASE + 6
    val CARD_INSERVICE_TIMEOUT = CARD_ERR_BASE + 7
    val CARD_CONNECT_TIMEOUT = CARD_ERR_BASE + 8
    val CARD_DATA_ENABLE_CLOSED = CARD_ERR_BASE + 9
    val CARD_ROAM_DATA_ENABLE_CLOSED = CARD_ERR_BASE + 10

    val CARD_CLOSE_CARD_TIMEOUT = CARD_ERR_BASE + 11
    val CARD_SIM_CRASH = CARD_ERR_BASE + 12
    val CARD_EXCEPT_NO_AVAILABLE_SOFTSIM = CARD_ERR_BASE + 13
    val CARD_EXCEPTION_FAIL = CARD_ERR_BASE + 14
    val CARD_EXCEPTION_ENABLE_TIMEOUT = CARD_ERR_BASE + 15

    val CARD_EXCEPT_SOFTSIM_UNUSABLE = CARD_ERR_BASE + 16
    val CARD_EXCEPT_REG_DENIED = CARD_ERR_BASE + 17
    val EXCEPTION_REG_DENIED_NOT_DISABLE = CARD_ERR_BASE + 18
    val CARD_EXCEPT_NET_FAIL = CARD_ERR_BASE + 19
    val CARD_PHY_ROAM_DISABLE = CARD_ERR_BASE + 20

    val SEED_CARD_CANNOT_BE_CDMA = CARD_ERR_BASE + 21
    val CARD_NO_AVAILABLE_SEEDCARD = CARD_ERR_BASE + 22
    val NO_AVAILABLE_NETWORK_HERE = CARD_ERR_BASE + 23
    val SEED_CARD_DEPTH_OPT_CLOSE = CARD_ERR_BASE + 24
    val CARD_PHY_ROAM_UI_CONFIG_DISABLE = CARD_ERR_BASE + 25

    val INNER_ERR_BASE = 2000
    val INNER_USER_CANCEL = INNER_ERR_BASE + 1

    val RPC_HEADER_STR = "RPC:"
    val TIMEOUT_HEADER_STR = "TIMEOUT:"
    val PARSE_HEADER_STR = "PARSE:"

    enum class ErrActType {
        ACT_NONE,
        ACT_RELOGIN,// 重登陆
        ACT_RETRY, // 重试
        ACT_EXIT, // 退出登陆
        ACT_TIMEOUT, // 超时
        ACT_TER,    // 流程结束
        ACT_REPORT, // 只上报错误，不影响流程
        ACT_UNKNOWN
    }

    val errcodeList by lazy { ServiceManager.productApi.getErrorCodeList() }

    /**
     * 错误码配置：code 错误码ID  rpc 是否是服务器错误码   action 这个错误码处理的动作   portalcode  UI弹portal显示的错误  msg 错误码描述
     */
    data class ErrCodeInfo(val code: Int, val rpc: Boolean, val action: ErrActType, val portalCode: Int, val msg: String){
        override fun toString(): String {
            return "ErrCodeInfo(code=$code, rpc=$rpc, action=$action, portalCode=$portalCode, msg='$msg')"
        }
    }

    fun getErrInfoByCode(code:Int) : ErrCodeInfo?{
        for (errInfo in errcodeList){
            if(errInfo.code == code){
                return errInfo
            }
        }
        return null
    }


    fun getErrActByCode(code: Int) : ErrActType {
        val info = getErrInfoByCode(code)
        return if(info != null) info.action else ErrActType.ACT_UNKNOWN
    }

    fun getErrMsgByCode(code: Int):String{
        val info = getErrInfoByCode(code)
        return if(info != null) info.msg else "null"
    }

    fun getErrPortalByCode(code: Int):Int{
        val info = getErrInfoByCode(code)
        return if(info != null) info.portalCode else 0
    }

    fun getErrString(t: Throwable?): String {
        if (t == null) {
            return "UNKNOWN"
        } else if (t is TimeoutException) {
            return ErrorCode.TIMEOUT_HEADER_STR + t.message
        } else if (t.message == null) {
            return "UNKNOWN"
        } else {
            return t.message!!
        }
    }

    fun getErrInfoByStr(str:String): ErrCodeInfo {
        logd("str.substring(0, RPC_HEADER_STR.length):" + str.substring(0, RPC_HEADER_STR.length))
        if(str.length >= RPC_HEADER_STR.length && str.substring(0, RPC_HEADER_STR.length) == RPC_HEADER_STR){
            logd("str.substring(RPC_HEADER_STR.length, str.length):" + str.substring(RPC_HEADER_STR.length, str.length))
            logd("str.substring(RPC_HEADER_STR.length, str.length).toInt:" + str.substring(RPC_HEADER_STR.length, str.length).toInt())
            var info = getErrInfoByCode(str.substring(RPC_HEADER_STR.length, str.length).toInt())
            if(info == null){
                return getErrInfoByCode(LOCAL_SERVER_UNKNOWN_ERR)!!
            }
            return info
        }else if(str.length >= TIMEOUT_HEADER_STR.length && str.substring(0, TIMEOUT_HEADER_STR.length) == TIMEOUT_HEADER_STR){
            return ErrCodeInfo(LOCAL_TIMEOUT, false, ErrActType.ACT_TIMEOUT, 0, "timeout:" + str)
        }else if(str.length >= PARSE_HEADER_STR.length && str.substring(0, PARSE_HEADER_STR.length) == PARSE_HEADER_STR){
            return getErrInfoByCode(LOCAL_SERVER_PACKAGE_PARSE_ERR)!!
        }else{
            var errcode = 0
            try {
                errcode = str.toInt()
            }catch (e: Exception){
                e.printStackTrace()
                errcode = LOCAL_UNKNOWN_ERROR
            }
            var info = getErrInfoByCode(errcode)
            if(info == null){
                return getErrInfoByCode(LOCAL_UNKNOWN_ERROR)!!
            }
            return info
        }
    }

    fun getErrRealMsg(info: ErrCodeInfo, str:String ?):String?{
        if(info.code == LOCAL_UNKNOWN_ERROR || info.code == LOCAL_SERVER_UNKNOWN_ERR){
            if(str == null){
                return "unknown error"
            }
            return str
        }
        return info.msg
    }

    fun getErrCodeByCardExceptId(except: EnablerException):Int{
        var ret = 0
        when(except) {
            EnablerException.EXCEP_CARD_PARAMETER_WRONG -> ret = ErrorCode.CARD_EXCEP_CARD_PARAMETER_WRONG
            EnablerException.EXCEP_PHY_CARD_IS_NULL -> ret = ErrorCode.CARD_EXCEP_PHY_CARD_IS_NULL
            EnablerException.EXCEP_PHY_CARD_DEFAULT_LOST -> ret = ErrorCode.CARD_EXCEP_PHY_CARD_DEFAULT_LOST
            EnablerException.INSERT_SOFT_SIM_TIMEOUT -> ret = ErrorCode.CARD_INSERT_SOFT_SIM_TIMEOUT
            EnablerException.ADD_SOFT_SIM_TIMEOUT -> ret = ErrorCode.CARD_ADD_SOFT_SIM_TIMEOUT
            EnablerException.READY_TIMEOUT -> ret = ErrorCode.CARD_READY_TIMEOUT

            EnablerException.INSERVICE_TIMEOUT -> ret = ErrorCode.CARD_INSERVICE_TIMEOUT
            EnablerException.CONNECT_TIMEOUT -> ret = ErrorCode.CARD_CONNECT_TIMEOUT
            EnablerException.DATA_ENABLE_CLOSED -> ret = ErrorCode.CARD_DATA_ENABLE_CLOSED
            EnablerException.ROAM_DATA_ENABLE_CLOSED -> ret = ErrorCode.CARD_ROAM_DATA_ENABLE_CLOSED
            EnablerException.CLOSE_CARD_TIMEOUT -> ret = ErrorCode.CARD_CLOSE_CARD_TIMEOUT

            EnablerException.SIM_CRASH -> ret = ErrorCode.CARD_SIM_CRASH
            EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM -> {
                if(except.reason.errorCode == 1) {
                    ret = ErrorCode.CARD_EXCEPT_NO_AVAILABLE_SOFTSIM
                }else{
                    ret = ErrorCode.CARD_EXCEPT_SOFTSIM_UNUSABLE
                }
            }
            EnablerException.EXCEPTION_FAIL -> ret = ErrorCode.CARD_EXCEPTION_FAIL
            EnablerException.EXCEPTION_ENABLE_TIMEOUT -> ret = ErrorCode.CARD_EXCEPTION_ENABLE_TIMEOUT
            EnablerException.EXCEPTION_DATA_ENABLE_CLOSED -> ret = ErrorCode.CARD_DATA_ENABLE_CLOSED
            EnablerException.EXCEPTION_REG_DENIED -> ret = ErrorCode.CARD_EXCEPT_REG_DENIED
            EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE -> ret = ErrorCode.EXCEPTION_REG_DENIED_NOT_DISABLE
            EnablerException.EXCEPTION_CARD_NET_FAIL -> ret = ErrorCode.CARD_EXCEPT_NET_FAIL
            EnablerException.EXCEPTION_USER_PHY_ROAM_DISABLE -> ret = ErrorCode.CARD_PHY_ROAM_DISABLE
            EnablerException.EXCEPTION_UNSUPPORT_CDMA_PHY_CARD -> ret = ErrorCode.SEED_CARD_CANNOT_BE_CDMA
            EnablerException.EXCEPTION_NO_NETWORK_SIGNAL -> ret = ErrorCode.NO_AVAILABLE_NETWORK_HERE
            EnablerException.EXCEPTION_LOCAL_DEPTH_OPT_CLOSE -> ret = ErrorCode.SEED_CARD_DEPTH_OPT_CLOSE
            else -> ret = ErrorCode.CARD_EXCEPTION_FAIL
        }
        return ret
    }
}