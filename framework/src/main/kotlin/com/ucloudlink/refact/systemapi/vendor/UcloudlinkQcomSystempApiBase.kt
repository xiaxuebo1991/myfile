package com.ucloudlink.refact.systemapi.vendor

import android.content.Context
import android.os.Build.VERSION_CODES.M
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.systemapi.platform.QCSystemApiBase
import com.ucloudlink.refact.business.virtimei.VirtImeiHelper
import com.ucloudlink.refact.systemapi.ModelConfig
import com.ucloudlink.refact.systemapi.struct.FileWapper
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import java.util.*

/**
 * Created by shiqianhua on 2018/1/9.
 */
open class UcloudlinkQcomSystempApiBase(context: Context, modelInfo: ModelInfo, sdkInt:Int): QCSystemApiBase(context, modelInfo, sdkInt) {
    override fun getDeniedReasonByState(phoneId:Int, state: ServiceState): Int {
        var ret = 0
        if(sdk > M){
            val field = state.javaClass.getField("mUcRegistrationDeniedReason")
            ret = field.getInt(state)
        }else {
            ret = state.mRegistrationDeniedReason
        }
        logd("UcloudlinkQcomSystempApiBase getDeniedReasonByState return $ret")
        if(ret == -1){
            ret = 0
        }
        return ret
    }

    override fun wirteVirtImei(context: Context, slot:Int, virtualImei: String): Int {
        val ret = VirtImeiHelper.wirteVirtImei(context, virtualImei)
        if(ret) return 0 else return -1
    }

    override fun recoveryImei(context: Context, slot: Int): Int {
        val ret = VirtImeiHelper.recoveryImei(context)
        if(ret == null) return -1
        if(ret) return 0 else return -1
    }

    override fun getSubscriberIdBySlot(slot: Int): String {
        val telephonyManager = TelephonyManager.from(ctx)
        if (telephonyManager == null) {
            JLog.loge("telephonyManager is null")
            return ""
        }

        val subList = SubscriptionManager.getSubId(slot)
        if(subList == null || subList.isEmpty()){
            return ""
        }
        return telephonyManager.getSubscriberId(subList[0])?:""
    }

    override fun getModemCfgFiles(): ArrayList<FileWapper> {
        val mbnData: ArrayList<FileWapper> = arrayListOf(
                FileWapper("mcfg_hw.mbn","mcfg_hw.mbn","mcfg_hw.mbn"),
                FileWapper("mcfg_sw.mbn","mcfg_sw.mbn", "mcfg_sw.mbn")
        )

        return mbnData
    }

    //KDDI卡为4G ONLY卡，无法做种子卡
    val lteOnlyPlmn :ArrayList<String> = arrayListOf("44051")

    override fun checkCardCanBeSeedsim(slotId: Int): Boolean {
        val imsi = ServiceManager.systemApi.getSubscriberIdBySlot(slotId)
        for (plmn in lteOnlyPlmn){
            logd("checkCardCanBeSeedsim card imsi:$imsi,plmn:$plmn")
            if(imsi.startsWith(plmn)){
                return false
            }
        }
        return true
    }

    override fun isSupportSwitchDDS(): Boolean {
        return true
    }
}