package com.ucloudlink.refact.systemapi.model

import android.content.Context
import android.telephony.SubscriptionManager
import com.ucloudlink.refact.model.mimax.ModelMiMax
import com.ucloudlink.refact.model.s1.ModelS1
import com.ucloudlink.refact.systemapi.ModelConfig
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.struct.ModelInfo
import com.ucloudlink.refact.systemapi.vendor.MiUIQcomSystemApiBase
import com.ucloudlink.refact.utils.JLog.logv

/**
 * Created by shiqianhua on 2018/1/9.
 */
class SystemApi_MI_MAX(context: Context, modelInfo: ModelInfo, sdkInt:Int): MiUIQcomSystemApiBase(context, modelInfo, sdkInt) {
    override fun setDefaultDataSubId(subId: Int): Int {
        val subMangerClass = Class.forName("miui.telephony.SubscriptionManager")
        val getDefault = subMangerClass.getMethod("getDefault")
        val miuiSubMnger = getDefault.invoke(null)
        val setDefaultDataSlotId = subMangerClass.getMethod("setDefaultDataSlotId", Int::class.java)
        val slot = SubscriptionManager.getPhoneId(subId)
        setDefaultDataSlotId.invoke(miuiSubMnger, slot)

        return 0
    }

    override fun saveDefaultDataSubId(subId: Int, slot: Int): Int {
        logv("restoreDefaultDataChoice for miui")
        val subMangerClass = Class.forName("miui.telephony.SubscriptionManager")
        val getDefault = subMangerClass.getMethod("getDefault")
        val miuiSubMnger = getDefault.invoke(null)
        val setDefaultDataSlotId = subMangerClass.getMethod("setDefaultDataSlotId", Int::class.java)
        setDefaultDataSlotId.invoke(miuiSubMnger, slot)
        return 0
    }

    override fun setDefaultSmsSubId(subId: Int, slot: Int): Int {
        logv("restoreDefaultSmsChoice for miui")
        val subMangerClass = Class.forName("miui.telephony.SubscriptionManager")
        val getDefault = subMangerClass.getMethod("getDefault")
        val miuiSubMnger = getDefault.invoke(null)
        val setDefaultSmsSlotId = subMangerClass.getMethod("setDefaultSmsSlotId", Int::class.javaPrimitiveType)
        setDefaultSmsSlotId.invoke(miuiSubMnger, slot)
        return 0
    }

    override fun setVoiceSlotId(slot: Int): Int {
        logv("restoreDefaultVoiceChoice for miui")
        val subMangerClass = Class.forName("miui.telephony.SubscriptionManager")
        val getDefault = subMangerClass.getMethod("getDefault")
        val miuiSubMnger = getDefault.invoke(null)
        val setDefaultVoiceSlotId = subMangerClass.getMethod("setDefaultVoiceSlotId", Int::class.javaPrimitiveType)
        setDefaultVoiceSlotId.invoke(miuiSubMnger, slot)
        return 0
    }

    override fun getNetTypeDelayTime(): Long {
        return 10000
    }

    override fun getTcpdumpEnable(): Boolean {
        return false
    }

    override fun getModemLogEnable(): Boolean {
        return false
    }

    override fun getModelIf(context: Context): ModelIf {
        return ModelMiMax(context)
    }
}