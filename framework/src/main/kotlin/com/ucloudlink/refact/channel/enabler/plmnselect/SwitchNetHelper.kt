package com.ucloudlink.refact.channel.enabler.plmnselect

import com.ucloudlink.framework.protocol.protobuf.EquivalentPlmnInfo
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.monitors.AttachReject
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge

/**
 * 切网相关操作
 */
object SwitchNetHelper {
    // 拒绝重试次数
    private const val REJECT_RETRY_MAX_TIMES = 2
    val EVENT_CARD_REJECT_ERROR = 8
    /**
     * 数据连接失败错误处理
     *
     * S1 android.intent.action.DATA_CONNECTION_FAILED 广播
     */
    fun switchOnDataConnectFailed(failReasonStr: String?, subId: Int) {
        logd("[SwitchNet]", "switchOnDataConnectFailed: reason = $failReasonStr, subId = $subId")
        try {
            if (Integer.parseInt(failReasonStr) in switchNetReason) {
                val registeredPlmn = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
                val attachReject = AttachReject(subId, registeredPlmn)
                logd("[SwitchNet]", "Switch with PDP_REJECT: $attachReject")
                SeedPlmnSelector.updateEvent(PDP_REJECT_EXCEPTION, attachReject)
            }
        } catch (e: Exception) {
            loge("[SwitchNet]", "catch exception ${e.message}")
        }
    }

    /**
     * 数据连接失败错误处理
     *
     * S1 com.ucloudlink.attach.reject.cause 广播
     */
    fun switchOnAttachReject(cause: Int, rejectType: Byte, attachReject: AttachReject,slotId:Int) {
        logd("[SwitchNet]", "switchOnAttachReject: cause = $cause, rejectType = $rejectType, slotId = $slotId")
        val seedSimSlot = ServiceManager.accessSeedCard.getCard().slot
        val cloudSimSlot = ServiceManager.cloudSimEnabler.getCard().slot
        logd("seedSimSlot  = "+seedSimSlot)
        logd("cloudSimSlot  = "+cloudSimSlot)
        var simType:Int = -1
        if(cloudSimSlot == slotId){
            simType = SIM_TYPE_VSIM
        }else if(seedSimSlot == slotId){
            if(ServiceManager.accessSeedCard.getCard().cardType == CardType.PHYSICALSIM){
                simType = SIM_TYPE_PHY
            }else{
                simType = SIM_TYPE_SOFT
            }
        }else if(slotId!=cloudSimSlot && slotId!=seedSimSlot){
            simType = SIM_TYPE_PHY
        }
        logd("simType  =  "+simType)
        logd("rejectType  =  "+rejectType)
        if (rejectType == RejectType.NETWORK_ATTACH_REJECT.ordinal.toByte()) {
            when (simType) {
                SIM_TYPE_SOFT -> {//软卡
                    if (cause in switchAttachReason) {//注册被拒切网值，直接切网
                        logd("[SwitchNet]", "Switch with ATTACH_REJECT 1: $attachReject")
                        SeedPlmnSelector.updateEvent(ATTACH_REJECT_EXCEPTION, attachReject)
                    }
                }
                SIM_TYPE_PHY,SIM_TYPE_VSIM -> {
                    processRejectReason(cause,simType,slotId)
                }
            }
        } else if (rejectType == RejectType.NETWORK_AUTHENTICATION_AND_CIPHERION_REJECT.ordinal.toByte()) {
            when (simType) {
                SIM_TYPE_SOFT -> {//软卡
                    //鉴权被拒切网值，直接切网
                    if (cause == ATTACH_AUTH_REJECT) {
                        logd("[SwitchNet]", "Switch with ATTACH_REJECT 2: $attachReject")
                        SeedPlmnSelector.updateEvent(ATTACH_REJECT_EXCEPTION, attachReject)
                    }
                }
                SIM_TYPE_PHY,SIM_TYPE_VSIM -> {
                    processRejectReason(cause,simType,slotId)
                }
            }
        } else if (rejectType == RejectType.NETWORK_PDN_CONNECTIVITY_REJECT.ordinal.toByte()) {
            when (simType) {
                SIM_TYPE_SOFT -> {//软卡
                    //拨号被拒
                    switchOnDataCallFailed(cause, attachReject)
                }
                SIM_TYPE_PHY,SIM_TYPE_VSIM -> {
                    processRejectReason(cause,simType,slotId)
                }
            }
        }
    }

    fun processRejectReason(cause: Int,sim_type:Int,slotId:Int) {
        //获取有效eplmn个数
        var eplmnlist :List<EquivalentPlmnInfo>? = null
        when(sim_type){
            SIM_TYPE_PHY->{//物理卡
                eplmnlist = ServiceManager.seedCardEnabler.getCard().eplmnlist
                ServiceManager.phyCardWatcher.obtainMessage(EVENT_CARD_REJECT_ERROR,slotId,cause).sendToTarget()
            }
            SIM_TYPE_VSIM->{//云卡
                eplmnlist = ServiceManager.cloudSimEnabler.getCard().eplmnlist
            }
        }
        var eplmnnum = 0
        if (eplmnlist != null) {
            for (i in eplmnlist.indices) {
                if (eplmnlist[i].supportedRat != 0) {
                    eplmnnum++
                }
            }
        }
        val shoudDisableCard = eplmnnum <= 1
        when (cause) {
            in cloudSimDeniedReason -> {
                handleDenied(cause, shoudDisableCard,slotId,sim_type)
            }
            NO_SUITABLE_CELLS_IN_LOCATION_AREA -> {
                if (ServiceManager.accessSeedCard.getCard().rat == PreferredNetworkType.SERVER_RAT_TYPE_4G) {
                    handleDenied(cause, shoudDisableCard,slotId,sim_type)
                }
            }
        }
    }
    var lastDeniedReason = 0
    var sameDeniedReasonHit = 0
    var lastSubId = -1
    private fun handleDenied(reason: Int, isDisable: Boolean,slotId: Int,sim_type:Int) {
        var mSubId = ServiceManager.systemApi.getSubIdBySlotId(slotId)
        if(lastSubId != mSubId){
            sameDeniedReasonHit = 0
            lastDeniedReason = -1
        }
        lastSubId = mSubId
        val exception: EnablerException
        var isShouldDisable = false
        if(isDisable || sameDeniedReasonHit>=3) isShouldDisable=true
        if (reason == lastDeniedReason && isDisable) {
            sameDeniedReasonHit++
        } else {
            sameDeniedReasonHit = 1
        }
        lastDeniedReason = reason
        if (isShouldDisable) {
            sameDeniedReasonHit = 0
            exception = EnablerException.EXCEPTION_REG_DENIED
        } else {
            exception = EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE
        }
        if (reason > 0) {
            exception.reason.errorCode = reason
            val cardRej = CardReject(exception,"reason:$reason", isShouldDisable)
            when(sim_type){
                SIM_TYPE_PHY->{
                    ServiceManager.accessSeedCard.initEnabler.notifyEventToCard(DataEnableEvent.ENENT_CARD_REJECT,cardRej)
                }
                SIM_TYPE_VSIM->{
                    ServiceManager.cloudSimEnabler.notifyEventToCard(DataEnableEvent.ENENT_CARD_REJECT,cardRej)
                }
            }
            logd("handleDenied Registration Denied reason:$reason")
        }
    }

    /**
     * 拨号被拒失败错误处理
     *
     * 根据不同错误进行错误处理
     */
    fun switchOnDataCallFailed(failReason: Int, attachReject: AttachReject) {
        logd("[SwitchNet]", "switchOnDataCallFailed: reason = $failReason")
        when (failReason) {
            in switchNetReason -> {
                //拨号被拒切网值，直接切网
                logd("[SwitchNet]", "Switch with PDP_REJECT 1: $attachReject")
                SeedPlmnSelector.updateEvent(PDP_REJECT_EXCEPTION, attachReject)
            }
            in switchNetRetryReason -> {
                //拨号被拒重试值，多重试一次再切网
                ServiceManager.seedCardEnabler.getCard().rejectRetryTimes++

                if (ServiceManager.seedCardEnabler.getCard().rejectRetryTimes >= REJECT_RETRY_MAX_TIMES) {
                    logd("[SwitchNet]", "Switch with PDP_REJECT 2: $attachReject")
                    SeedPlmnSelector.updateEvent(PDP_REJECT_EXCEPTION, attachReject)
                    ServiceManager.seedCardEnabler.getCard().rejectRetryTimes = 0
                }
            }
            in OTHER_PROTOCOL_ERRORS_MIN..OTHER_PROTOCOL_ERRORS_MAX -> {
                //拨号被拒重试值，多重试一次再切网
                ServiceManager.seedCardEnabler.getCard().rejectRetryTimes++

                if (ServiceManager.seedCardEnabler.getCard().rejectRetryTimes >= REJECT_RETRY_MAX_TIMES) {
                    logd("[SwitchNet]", "Switch with PDP_REJECT 3: $attachReject")
                    SeedPlmnSelector.updateEvent(PDP_REJECT_EXCEPTION, attachReject)
                    ServiceManager.seedCardEnabler.getCard().rejectRetryTimes = 0
                }
            }
            else -> {
                //其他大于0值，直接进行切网
                if (failReason > 0) {
                    logd("[SwitchNet]", "Switch with PDP_REJECT 4: $attachReject")
                    SeedPlmnSelector.updateEvent(PDP_REJECT_EXCEPTION, attachReject)
                }
            }
        }
    }

    /**
     * 兼容老版本的ROM
     */
    fun switchOnAttachRejectOld(failReason: Int, attachReject: AttachReject) {
        logd("[SwitchNet]", "switchOnAttachRejectOld: reason = $failReason")
        when (failReason) {
            in switchAttachReason, ATTACH_AUTH_REJECT -> {
                logd("[SwitchNet]", "Switch with ATTACH_REJECT: $attachReject")
                SeedPlmnSelector.updateEvent(ATTACH_REJECT_EXCEPTION, attachReject)
            }
        }
    }

    // PDP failed
    //此类拨号被拒直接切网
    private const val OPERATOR_DETERMINED_BARRING = 8
    private const val INSUFFICIENT_RESOURCES = 26
    private const val MISSING_OR_UNKNOWN_APN = 27
    private const val UNKNOW_PDP_ADDRESS = 28
    private const val USER_AUTHENTICATION_FAILED = 29
    private const val REJECTED_BY_GGSN = 30
    private const val ACTIVATION_REJECTED_UNSPECIFIED = 31
    private const val SERVICE_OPTION_NOT_SUPPORTED = 32
    private const val REQUESTED_SERVICE_OPTION_NOT_SUBSCRIBED = 33
    private const val NSAPI_ALREADY_USED = 35
    private const val PDP_TYPE_IPV4_ONLY_ALLOWED = 50
    private const val PDP_TYPE_IPV6_ONLY_ALLOWED = 51
    private const val PROTOCAL_ERRORS = 111

    //此类拨号被拒多尝试一次
    private const val MBMS_BEARER_CAPABILITIES_INSUFFICIENT = 24
    private const val LLC_OR_SDNCP_FAILURE = 25
    private const val SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER = 34
    private const val REGULAR_DEACTIVATION = 36
    private const val QOS_NOT_ACCEPTED = 37
    private const val NETWORK_FAILURE = 38
    private const val REACTIVATION_REQUESTED = 39
    private const val FEATURE_NOT_SUPPORTED = 40
    private const val SEMANTIC_ERROR_IN_TFT = 41
    private const val SYNTACTICAL_ERROR_IN_TFT = 42
    private const val UNKNOW_PDP_CONTEXT = 43
    private const val SEMANTIC_ERROR_IN_PACKET_FILTER = 44
    private const val SYNTACTICAL_ERROR_IN_PACKET_FILTER = 45
    private const val PDP_CONTEXT_WITHOUT_TFT = 46
    private const val MULTICAST_GROUP_MEMBERSHIP_TIME_OUT = 47
    private const val REQUEST_REJECT = 48
    private const val SINGLE_ADDRESS_BEARERS_ONLY_ALLOWED = 52
    private const val CONLLISION_WITH_NETWORK_INITIATED_REQUEST = 56
    private const val BEARER_HANDLING_NOT_SUPPORTED = 60
    private const val MAXIMUM_NUMBER_OF_PDP_CONTEXTS_REACHED = 65
    private const val APN_NOT_SUPPORTED = 66
    private const val INVALID_TRANSACTION_IDENTIFIER_VALUE = 81
    private const val APN_INCOMPATIBLE = 112

    //此类拨号被拒多尝试一次
    private const val OTHER_PROTOCOL_ERRORS_MIN = 95
    private const val OTHER_PROTOCOL_ERRORS_MAX = 110


    //注册被拒 attach reject reason：进行切网。
    private const val ATTACH_ILLEGAL_MS = 3
    private const val ATTACH_ILLEGAL_ME = 6
    private const val ATTACH_SERVICES_NOT_ALLOWED = 7
    private const val ATTACH_ALL_SERVICES_NOT_ALLOWED = 8
    private const val ATTACH_LOCATION_AREA_NOT_ALLOWED = 12
    private const val GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN = 14
    private const val ATTACH_CONGESTION = 22
    private const val ATTACH_AUTH_REJECT = 255

    //其他被拒 else
    //被拒值大于0，直接切网

    //物理卡，云卡拨号被拒切网
    private const val NETWORK_AUTHENTICATION_AND_CIPHERION_REJECT = 3//ps 255
    private const val NETWORK_PDN_CONNECTIVITY_REJECT = 4
    private const val NETWORK_ATTACH_REJECT = 5
    private const val NETWORK_TRACKING_AREA_REJECT = 6
    private const val NETWORK_EMM_AUTHENTICATION_REJECT = 7
    private const val NETWORK_EMM_SECURITY_MODE_REJECT = 8
    private const val PLMN_NOT_ALLOWED = 11
    private const val NO_SUITABLE_CELLS_IN_LOCATION_AREA = 15

    private const val SIM_TYPE_PHY = 1
    private const val SIM_TYPE_SOFT = 2
    private const val SIM_TYPE_VSIM = 3
    //云卡、物理卡处理值
    private val cloudSimDeniedReason = arrayOf(
            NETWORK_AUTHENTICATION_AND_CIPHERION_REJECT,
            NETWORK_PDN_CONNECTIVITY_REJECT,
            NETWORK_ATTACH_REJECT,
            NETWORK_TRACKING_AREA_REJECT,
            NETWORK_EMM_AUTHENTICATION_REJECT,
            NETWORK_EMM_SECURITY_MODE_REJECT,
            PLMN_NOT_ALLOWED
    )

//    其他情况modem会自动切网。

    //拨号被拒切网值
    private val switchNetReason = arrayOf(
            OPERATOR_DETERMINED_BARRING,
            INSUFFICIENT_RESOURCES,
            MISSING_OR_UNKNOWN_APN,
            UNKNOW_PDP_ADDRESS,
            USER_AUTHENTICATION_FAILED,
            REJECTED_BY_GGSN,
            ACTIVATION_REJECTED_UNSPECIFIED,
            SERVICE_OPTION_NOT_SUPPORTED,
            REQUESTED_SERVICE_OPTION_NOT_SUBSCRIBED,
            NSAPI_ALREADY_USED,
            PDP_TYPE_IPV4_ONLY_ALLOWED,
            PDP_TYPE_IPV6_ONLY_ALLOWED,
            PROTOCAL_ERRORS
    )
    //拨号被拒重试值
    private val switchNetRetryReason = arrayOf(
            MBMS_BEARER_CAPABILITIES_INSUFFICIENT,
            LLC_OR_SDNCP_FAILURE,
            SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER,
            REGULAR_DEACTIVATION,
            QOS_NOT_ACCEPTED,
            NETWORK_FAILURE,
            REACTIVATION_REQUESTED,
            FEATURE_NOT_SUPPORTED,
            SEMANTIC_ERROR_IN_TFT,
            SYNTACTICAL_ERROR_IN_TFT,
            UNKNOW_PDP_CONTEXT,
            SEMANTIC_ERROR_IN_PACKET_FILTER,
            SYNTACTICAL_ERROR_IN_PACKET_FILTER,
            PDP_CONTEXT_WITHOUT_TFT,
            MULTICAST_GROUP_MEMBERSHIP_TIME_OUT,
            REQUEST_REJECT,
            SINGLE_ADDRESS_BEARERS_ONLY_ALLOWED,
            CONLLISION_WITH_NETWORK_INITIATED_REQUEST,
            BEARER_HANDLING_NOT_SUPPORTED,
            MAXIMUM_NUMBER_OF_PDP_CONTEXTS_REACHED,
            APN_NOT_SUPPORTED,
            INVALID_TRANSACTION_IDENTIFIER_VALUE,
            APN_INCOMPATIBLE
    )

    //注册被拒切网值
    private val switchAttachReason = arrayOf(
            ATTACH_ILLEGAL_MS,
            ATTACH_ILLEGAL_ME,
            ATTACH_SERVICES_NOT_ALLOWED,
            ATTACH_ALL_SERVICES_NOT_ALLOWED,
            ATTACH_LOCATION_AREA_NOT_ALLOWED,
            GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN,
            ATTACH_CONGESTION
    )

    //上报的被拒类型
    enum class RejectType(value: Byte) {
        NETWORK_LOCATION_UPDATING_REJECT(0),
        NETWORK_ROUTING_UPDATE_REJECT(1),
        NETWORK_AUTHENTICATION_REJECT(2),//cs
        NETWORK_AUTHENTICATION_AND_CIPHERION_REJECT(3),//ps 255
        NETWORK_PDN_CONNECTIVITY_REJECT(4),
        NETWORK_ATTACH_REJECT(5),
        NETWORK_TRACKING_AREA_REJECT(6),
        NETWORK_EMM_AUTHENTICATION_REJECT(7),
        NETWORK_EMM_SECURITY_MODE_REJECT(8),
        NETWORK_SERVICE_REJECT(9),
        NETWORK_ERR_MAX(10)
    }

}
data class CardReject(val exception:EnablerException,val msg:String,val isShouldDisable:Boolean)