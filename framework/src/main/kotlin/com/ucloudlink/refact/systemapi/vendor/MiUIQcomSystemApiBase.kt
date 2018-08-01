package com.ucloudlink.refact.systemapi.vendor

import android.content.Context
import android.telephony.TelephonyManager
import com.android.internal.telephony.RILConstants
import com.ucloudlink.refact.systemapi.*
import com.ucloudlink.refact.systemapi.platform.QCSystemApiBase
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by shiqianhua on 2018/1/9.
 */
open class MiUIQcomSystemApiBase(context: Context, modelInfo: ModelInfo, sdkInt:Int): QCSystemApiBase(context, modelInfo, sdkInt) {

    /*
    小米首选网络类型
    2G优先对应值分别为1
    3G优先对应值分别为18
    4G优先对应值分别为20
     */
    val MI_RAT_TYPE_2G: Int = 1
    val MI_RAT_TYPE_3G: Int = 18
    val MI_RAT_TYPE_4G: Int = 20
    private fun getRealTypeMIUI( networkType: Int):Int{
        when(networkType){
            RILConstants.NETWORK_MODE_GSM_ONLY, RILConstants.NETWORK_MODE_CDMA_NO_EVDO ->{
                return MI_RAT_TYPE_2G
            }
            RILConstants.NETWORK_MODE_WCDMA_ONLY, RILConstants.NETWORK_MODE_GSM_UMTS -> {
                return MI_RAT_TYPE_3G
            }
            RILConstants.NETWORK_MODE_LTE_CDMA_EVDO, RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA,
            RILConstants.NETWORK_MODE_LTE_GSM_WCDMA, RILConstants.NETWORK_MODE_LTE_ONLY,
            RILConstants.NETWORK_MODE_LTE_WCDMA -> {
                return MI_RAT_TYPE_4G
            }
            else -> {
                return MI_RAT_TYPE_4G
            }
        }
    }

    override fun setPreferredNetworkType(slot: Int, subId: Int, networkType: Int): Int {
        val realType = getRealTypeMIUI(networkType)
        logd("setPreferredNetworkType:" + realType)
        val telemanager = TelephonyManager.from(ctx)
        val ret = telemanager.setPreferredNetworkType(subId, realType)
        logd("telemanager.setPreferredNetworkType return $ret")
        return if(ret) 0 else -1
    }
}