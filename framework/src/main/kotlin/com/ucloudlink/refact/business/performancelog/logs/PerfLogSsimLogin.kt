package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2018/3/24.
 */
object PerfLogSsimLogin : PerfLogEventBase() {
    val LOGIN_REQ_EVENT = 1
    val LOGIN_RSP_EVENT = 2

    var loginReqTime = 0L
    var lastSN = 0

    private fun getLoginResultFromString(result: Int): rpc_err_type {
        when(result){
            0 -> return rpc_err_type.RPC_E_SUCCESS
            ErrorCode.RPC_USER_NOT_EXIST -> return rpc_err_type.RPC_ENN_01160001_USERNAME_ERR
            ErrorCode.RPC_PASSWD_CHECK_FAIL -> return rpc_err_type.RPC_ENN_01160002_PASSWORD_ERR
            ErrorCode.RPC_IMEI_OR_USERNAME_NULL -> return rpc_err_type.RPC_ENN_01160003_IMEI_IS_NULL
            ErrorCode.RPC_IMEI_BIND_NOT_EXIST -> return rpc_err_type.RPC_ENN_01160004_USER_UN_BIND
            ErrorCode.RPC_USER_OR_PASSWD_NULL_OR_NOT_IN_BIND -> return rpc_err_type.RPC_E24_01160005_USER_UN_BIND
            ErrorCode.RPC_NO_ACTIVATE_AFTER_FREE_USE -> return rpc_err_type.RPC_ENN_01160006_INACTIVE_USER

            ErrorCode.RPC_BOTH_HAVE_DAILY_MONTHLY_PACKAGE -> return rpc_err_type.RPC_E25_01160008_BOTH_DPKG_MPKG

            ErrorCode.RPC_MONTHLY_USERS_FULL -> return rpc_err_type.RPC_E26_01160009_USERS_LIMIT_IN_MPAGE
            ErrorCode.RPC_FEE_NOT_ENOUGH_FOR_DAILY_PACKAGE -> return rpc_err_type.RPC_ENN_01160010_NOT_AFFORD_DAY_PKG
            ErrorCode.RPC_NOT_IN_FAVOR_COUNTRY -> return rpc_err_type.RPC_ENN_01160011_OUT_OF_PKG_FIELD
            ErrorCode.RPC_GET_USER_INFO_FAIL -> return rpc_err_type.RPC_E52_01160012_GET_USER_FAILED
            ErrorCode.RPC_IMEI_NOT_EXIST -> return rpc_err_type.RPC_E05_01160013_IMEI_NOT_EXIST
            ErrorCode.RPC_GET_ACCESS_TOKEN_ERR -> return rpc_err_type.RPC_E53_01160015_GET_TOKEN_FAILED
            ErrorCode.RPC_USER_ACCOUNT_ERR -> return rpc_err_type.RPC_E54_01160016_USER_DATA_ERROR
            ErrorCode.RPC_BSS_UNKNOWN_ERR -> return rpc_err_type.RPC_E55_01160017_BSS_UNKNOWN_ERR
            ErrorCode.RPC_ACCOUNT_IS_DISALBE -> return rpc_err_type.RPC_E14_01160018_USER_BLOCKED
            ErrorCode.RPC_CALL_BSS_FAIL -> return rpc_err_type.RPC_E56_02161111_BSS_INVOK_FAILED
            ErrorCode.RPC_CALL_CSS_FAIL -> return rpc_err_type.RPC_E57_02162000_CSS_INVOK_FAILED
            ErrorCode.RPC_CALL_OSS_FAIL -> return rpc_err_type.RPC_E58_02163000_OSS_INVOK_FAILED
            ErrorCode.RPC_CALL_BAM_FAIL -> return rpc_err_type.RPC_E59_02164000_BAM_INVOK_FAILED
            ErrorCode.RPC_INVALID_SESSION -> return rpc_err_type.RPC_ENR_02160001_UNAUTHORIZED_ERR
            ErrorCode.RPC_APDU_DEAL_ERR -> return rpc_err_type.RPC_E60_02160002_APDU_EXCEPTION
            ErrorCode.RPC_CALL_DUBBO_FAIL -> return rpc_err_type.RPC_E61_02160003_DUBBO_EXCEPTION
            ErrorCode.RPC_CALL_SYSTEM_SERVICE_FAIL -> return rpc_err_type.RPC_E62_02160004_SYS_INVOK_ERR
            ErrorCode.RPC_TER_SID_EMPTY -> return rpc_err_type.RPC_ENR_02160006_SID_IS_EMPTY
            ErrorCode.RPC_TER_IMEI_EMPTY -> return rpc_err_type.RPC_ENR_02160007_IMEI_IS_EMPTY
            ErrorCode.RPC_LOGIN_FAIL_RET_NULL -> return rpc_err_type.RPC_ENR_02160008_LOGIN_RETURN_NULL
            ErrorCode.RPC_GET_SERVICE_LIST_FAIL -> return rpc_err_type.RPC_ENR_02160009_CHECK_SVC_LIST_FAIL
            ErrorCode.RPC_DISPATCH_CARD_FAIL -> return rpc_err_type.RPC_E63_02160010_GET_VSIM_FAIL
            ErrorCode.RPC_CALL_LOGIN_AUTH -> return rpc_err_type.RPC_E64_02160011_INVOK_LOGIN_API_FAIL
            ErrorCode.RPC_FEE_NOT_ENOUGH -> return rpc_err_type.RPC_E07_02160012_ACCOUNT_ARREAR
            ErrorCode.RPC_PLMN_LIST_EMPTY -> return rpc_err_type.RPC_ENR_02160020_PLMNLIST_IS_NULL
            ErrorCode.RPC_ASS_UNKNOWN_ERR -> return rpc_err_type.RPC_E13_02169999_ASS_UNKNOWN_ERR
            ErrorCode.RPC_NO_VSIM_AVAILABLE -> return rpc_err_type.RPC_E12_03040000_NO_SUITABLE_VSIM
            ErrorCode.RPC_GET_USERINFO_FAIL -> return rpc_err_type.RPC_E50_03040001_USERINFO_FROM_BSS_FAIL
            ErrorCode.RPC_EMPTY_PLMN_FROM_TER -> return rpc_err_type.RPC_ENR_03040002_NET_SET_IS_NULL
            ErrorCode.RPC_EMPTY_MCC_FROM_TER -> return rpc_err_type.RPC_E27_03040003_COUNTRY_SET_IS_NULL
            ErrorCode.RPC_NO_PRODUCT_BY_ID -> return rpc_err_type.RPC_E28_03040004_GET_PRODUCT_ID_FAILED
            ErrorCode.RPC_NO_POLICY_BY_ID -> return rpc_err_type.RPC_E29_03040005_GET_STRATEGY_FAILED
            ErrorCode.RPC_NO_CARD_POOL_BY_POLICY -> return rpc_err_type.RPC_E30_03040006_GET_VSIM_POOL_FAILED
            ErrorCode.RPC_NO_CARD_BY_IMSI -> return rpc_err_type.RPC_ENR_03040007_CANNOT_FIND_OBJ_BY_IMSI
            ErrorCode.RPC_NO_ONLINE_USER_BY_USERCODE -> return rpc_err_type.RPC_ENR_03040008_CANNOT_FIND_OBJ_BY_USERCODE
            ErrorCode.RPC_IMSI_NOT_EXIST -> return rpc_err_type.RPC_ENR_03040009_ONLIN_OBJ_NO_IMSI
            ErrorCode.RPC_RAT_ERROR -> return rpc_err_type.RPC_E51_03040010_NETWORK_RAT_ERROR
            ErrorCode.RPC_UPDATE_USER_ONLINE_ERR -> return rpc_err_type.RPC_ENR_03040011_UPDATE_USER_STATE_ERR
            ErrorCode.RPC_ADD_DISPATCH_CARD_LOG_ERR -> return rpc_err_type.RPC_ENR_03040012_ADD_VSIM_LOG_ERR
            ErrorCode.RPC_NO_NETWORK_AVAILABLE -> return rpc_err_type.RPC_ENN_03040013_NO_AVAILABLE_NETWORK
            ErrorCode.RPC_NO_GROUP_BY_IMSI -> return rpc_err_type.RPC_E31_03040100_CANNOT_FIND_GROUP_BY_IMSI
            ErrorCode.RPC_CARD_RELEASE_FAIL -> return rpc_err_type.RPC_ENR_03040200_RELEASE_VSIM_FAILED
            ErrorCode.RPC_LAST_IMSI_ERROR -> return rpc_err_type.RPC_ENR_03040201_LAST_IMSI_EXCEPTION
            ErrorCode.LOCAL_UNKNOWN_ERROR -> return rpc_err_type.LOCAL_E100_00000100_UNDEFINED_ERR
            ErrorCode.LOCAL_SERVER_PACKAGE_PARSE_ERR -> return rpc_err_type.LOCAL_E101_00000101_PARSE_ERR
            ErrorCode.LOCAL_TIMEOUT -> return rpc_err_type.LOCAL_E102_00000102_TIMEOUT
            ErrorCode.RPC_LOGIN_FAIL_RET_NULL -> return rpc_err_type.LOCAL_ENNN_00000103_LOGIN_SOMEWHERE_ELSE
            else -> return rpc_err_type.RPC_E_UNKNOWN
        }
    }

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        JLog.logd("PerfLogSsimLogin createMsg id=$arg1")
        when (arg1) {
            LOGIN_REQ_EVENT -> {
                loginReqTime = System.currentTimeMillis()
            }
            LOGIN_RSP_EVENT -> {
                any as SsimLoginRspData

                val dataInfo = ServiceManager.seedCardEnabler.getDataEnableInfo()

                val loginRspTime = System.currentTimeMillis()
                val head = PerfUntil.getCommnoHead()
                val net = PerfUntil.getMobileNetInfo(true, SimInfoData(dataInfo.dataReg,dataInfo.dataRoam,false, dataInfo.voiceReg, dataInfo.voiceRoam, false))
                val ssimLogin = Ssim_Login.Builder()
                        .head(head)
                        .loginReqTime((loginReqTime / 1000).toInt())
                        .loginResTime((loginRspTime / 1000).toInt())
                        .loginSN(lastSN)
                        .loginResult(getLoginResultFromString(any.loginResult))
                        .sessionId(any.sessionId)
                        .net(net)
                        .build()
                PerfUntil.saveEventToList(ssimLogin)
            }
        }
    }

    fun setLoginSN(sn:Int){
        lastSN = sn
    }
}

 data class SsimLoginRspData(val loginResult: Int, val sessionId: String?)