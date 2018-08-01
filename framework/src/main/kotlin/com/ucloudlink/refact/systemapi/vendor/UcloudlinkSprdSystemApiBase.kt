package com.ucloudlink.refact.systemapi.vendor

import android.content.Context
import android.telephony.ServiceState
import com.android.internal.telephony.RILConstants
import com.ucloudlink.refact.platform.sprd.api.SprdApiInst
import com.ucloudlink.refact.systemapi.platform.SprdSystemApiBase
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.utils.JLog.*

/**
 * Created by shiqianhua on 2018/1/9.
 */
open class UcloudlinkSprdSystemApiBase(context: Context, modelInfo: ModelInfo, sdkInt: Int) : SprdSystemApiBase(context, modelInfo, sdkInt) {
    override fun getDeniedReasonByState(phoneId: Int, state: ServiceState): Int {
        return SprdApiInst.getInstance().getNetworkErrcode(phoneId)
    }

    override fun geDeniedReasonTime(phoneId: Int): Long {
        return SprdApiInst.getInstance().getNetworkErrcodeTime(phoneId)
    }

    override fun isNewReason(phoneId: Int, time: Long): Boolean {
        return time != geDeniedReasonTime(phoneId)
    }

    override fun wirteVirtImei(context: Context, slot: Int, virtualImei: String): Int {
        return SprdApiInst.getInstance().setImei(slot, virtualImei)
    }

    override fun recoveryImei(context: Context, slot: Int): Int {
        // TODO: 可能不需要清除
        return SprdApiInst.getInstance().setImei(slot, "")
    }

    override fun setDefaultDataSlotId(slot: Int): Int {
        logk("setDefaultDataSlotId $slot subId: ${SprdApiInst.getInstance().getSubIdBySlot(slot)}")
        return SprdApiInst.getInstance().setDefaultDataPhoneId(slot)
    }

    override fun setDefaultDataSubId(subId: Int): Int {
        logk("setDefaultDataSubId $subId")
        return SprdApiInst.getInstance().setDefatultDataSubId(subId)
    }

    /**
     * 建议设置如下几种类型：
     * RILConstants.NETWORK_MODE_WCDMA_PREF
    RILConstants.NETWORK_MODE_GSM_ONLY
    RILConstants.NETWORK_MODE_WCDMA_ONLY
    RILConstants.NETWORK_MODE_LTE_GSM_WCDMA
    RILConstants.NETWORK_MODE_LTE_ONLY
    RILConstants.NETWORK_MODE_LTE_WCDMA
     */
    // do not support 2G right now, may support next version
    private fun getRealNetworkTypeSprd(networkType: Int): Int {
        return when (networkType) {
            RILConstants.NETWORK_MODE_WCDMA_PREF, RILConstants.NETWORK_MODE_GSM_ONLY,
            RILConstants.NETWORK_MODE_WCDMA_ONLY, RILConstants.NETWORK_MODE_LTE_GSM_WCDMA,
            RILConstants.NETWORK_MODE_LTE_ONLY, RILConstants.NETWORK_MODE_LTE_WCDMA -> {
                networkType
            }
            else -> {
                RILConstants.NETWORK_MODE_LTE_WCDMA
            }
        }
    }

    override fun setPreferredNetworkType(slot: Int, subId: Int, networkType: Int): Int {
        val type = getRealNetworkTypeSprd(networkType)
        logd("SprdApiInst.getInstance().setPreferredNetworkType $type")
        val ret = SprdApiInst.getInstance().setPreferredNetworkType(slot, type)
        return if (ret) 0
        else -1
    }

    override fun resetPreferredNetworkType(slot: Int): Int {
        logd("SprdApiInst.getInstance().resetPreferredNetworkType $slot")
        val ret = SprdApiInst.getInstance().resetVsimSlotModeType(slot)
        return if (ret) 0
        else -1
    }

    override fun setPreferredNetworkTypeToGlobal(): Boolean {
        val defaultSlotId = SprdApiInst.getInstance().defaultDataPhoneId
        logd("set default subid $defaultSlotId PreferredNeworkType ${RILConstants.NETWORK_MODE_LTE_WCDMA}")
        SprdApiInst.getInstance().setPreferredNetworkType(defaultSlotId, RILConstants.NETWORK_MODE_LTE_WCDMA)
        return true
    }

    override fun setDataEnabled(slot: Int, subId: Int, isDataEnabled: Boolean) {
        loge("set data enabled! $slot $subId $isDataEnabled")
        SprdApiInst.getInstance().setDataEnabled(isDataEnabled) // 展讯不需要传入subid吗？
    }

    override fun getSimState(slot: Int): Int {
        logd("get sim state: $slot")
        return SprdApiInst.getInstance().getSimState(slot)
    }

    override fun getSubscriberIdBySlot(slot: Int): String {
        logd("getSubscriberIdBySlot $slot")
        return SprdApiInst.getInstance().getSubscriberIdForSlotIdx(slot)
    }

    override fun getSubIdBySlotId(slot: Int): Int {
        logd("getSubidBySlot $slot")
        return SprdApiInst.getInstance().getSubIdBySlot(slot)
    }

    override fun getNetworkOperator(slot: Int): String {
        logd("getNetworkOperator $slot")
        return SprdApiInst.getInstance().getNetworkOperator(slot)
    }

    override fun isSupportSwitchDDS(): Boolean {
        return false
    }
}