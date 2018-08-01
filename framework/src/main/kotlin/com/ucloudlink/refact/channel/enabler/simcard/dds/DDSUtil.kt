package com.ucloudlink.refact.channel.enabler.simcard.dds

import android.content.Context
import android.provider.Settings
import android.telephony.SubscriptionManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog.*
import rx.Observable
import rx.functions.Func1

/**
 * Created by jiaming.liang on 2016/7/18.
 */

inline fun switchDdsToNext(subId: Int, slot: Int): Observable<Boolean> {

    val subMnger = ServiceManager.subMnger
    val slotId = SubscriptionManager.getPhoneId(ServiceManager.systemApi.getDefaultDataSubId())
    var nwSlotId = SubscriptionManager.getPhoneId(subId)
    logv("switchDdsToNext old slotId:$slotId to nwSlotId:$nwSlotId")

    if (nwSlotId == SubscriptionManager.INVALID_PHONE_INDEX) {
        nwSlotId = slot
    }

    return Observable.create<Boolean> {
        val mUiccProvisioned = ServiceManager.systemApi.getSimEnableState(nwSlotId) == 1
        if (!mUiccProvisioned) {
            loge("mUiccProvisioned != 1")
            Thread.sleep(1000)
            throw Exception("uicc not Provisioned")
        } else {
            it.onNext(true)
        }
    }.retry(10).map<Boolean>(Func1<Boolean, Boolean>({ sub:Boolean ->
        logv("version :${ServiceManager.getSystemType()}")
//        when (Configuration.currentSystemVersion) {
//            Configuration.ANDROID_ORGIN -> {
//                subMnger.setDefaultDataSubId(subId)
//            }
//            Configuration.ANDROID_MIUI_V8 -> {
//                val subMangerClass = Class.forName("miui.telephony.SubscriptionManager")
//                val getDefault = subMangerClass.getMethod("getDefault")
//                val miuiSubMnger = getDefault.invoke(null)
//                val setDefaultDataSlotId = subMangerClass.getMethod("setDefaultDataSlotId", Int::class.java)
//                setDefaultDataSlotId.invoke(miuiSubMnger, nwSlotId)
//            }
//            Configuration.ANDROID_COOL_C103 -> {
//                subMnger.setDefaultDataSubId(subId)
//            }
//        }
        logd("set dds to subid $subId")
        ServiceManager.systemApi.setDefaultDataSubId(subId)
        DDSUtil.setUserPrefDataSubIdInDb(ServiceManager.appContext, subId)

        
        val _slotId = SubscriptionManager.getPhoneId(ServiceManager.systemApi.getDefaultDataSubId())
        val currSubIds = SubscriptionManager.getSubId(_slotId)
        val setOK = currSubIds[0] == subId
        if (setOK) {
            true
        } else {
            throw Exception("set DDS fail")
        }
    })).retry(2)

}

object DDSUtil {
    private val SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub"
/*
    fun getCurrDDSslot(subMnger: SubscriptionManager): Int {
        val defaultDataSubscriptionInfo = subMnger.defaultDataSubscriptionInfo
        defaultDataSubscriptionInfo ?: return -1
        return defaultDataSubscriptionInfo.simSlotIndex
    }*/

    fun setUserPrefDataSubIdInDb(context: Context, subId: Int) {
        logv("do setUserPrefDataSubIdInDb")
        var bl = Settings.Global.putInt(context.getContentResolver(),
                SETTING_USER_PREF_DATA_SUB, subId)
        logv("bl setUserPrefDataSubIdInDb:$bl,subId:$subId")
        if (bl == false) {
            bl = Settings.Global.putInt(context.getContentResolver(),
                    SETTING_USER_PREF_DATA_SUB, subId)
            logv("retry setUserPrefDataSubIdInDb bl:" + bl)
        }
    }

    fun switchDdsToNext2(subId: Int, slot: Int): Observable<Boolean> {
        return switchDdsToNext(subId, slot)
    }
}
