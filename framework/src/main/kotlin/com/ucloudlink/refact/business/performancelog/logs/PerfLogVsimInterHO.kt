package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.InterHoType_E
import com.ucloudlink.framework.protocol.protobuf.preflog.Vsim_InterHO
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.config.Configuration.cloudSimSlot
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by haiping.liu on 2018/3/28.
 * 主板 WCDMA->GSM;LET->GSM;LTE->WCDMA 切换
 * 100% - 75% - 100%
 */

object PerfLogVsimInterHO : PerfLogEventBase() {
    //  0-2G; 1-3G; 2-4G 255-unkonwn
    var old_rat = 255
    var new_rat = 255
    var triggerTime = 0

    //arg1 persent
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("createMsg persent=$arg1 ,old_rat=$old_rat")
        if (arg1 < 75) {
            old_rat = 255
            new_rat = 255
            triggerTime = 0
        } else if (arg1 == 75) {
            if (old_rat != 255) {
                triggerTime = (System.currentTimeMillis() / 1000).toInt()
                logd("createMsg 75 triggerTime=$triggerTime")
            }
        } else if (arg1 == 100) {
            if (old_rat == 255) {
                //第一次100%
                val cloudsim_rat = PerfUntil.getRatBySlot(cloudSimSlot)
                old_rat = PerfUntil.getPerfRat(cloudsim_rat).value
                logd("createMsg first 100, old_rat=$old_rat")
            } else {
                //不是第一次100%
                val cloudsim_rat = PerfUntil.getRatBySlot(cloudSimSlot)
                new_rat = PerfUntil.getPerfRat(cloudsim_rat).value
                logd("createMsg second 100, new_rat=$new_rat,old_rat=$old_rat,triggerTime=$triggerTime")
                //新旧不一样就保存
                if (old_rat != new_rat && triggerTime != 0) {
                    val interHoType = getInterHoTypeE(old_rat.toString() + new_rat.toString())
                    save(interHoType)
                    old_rat = new_rat
                }
            }
        }
    }

    fun save(type: InterHoType_E?) {
        val dataInfo = ServiceManager.cloudSimEnabler.getDataEnableInfo()
        val net = PerfUntil.getMobileNetInfo(false, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))

        val vsim_InterHO = Vsim_InterHO.Builder()
                .head(PerfUntil.getCommnoHead())
                .imsi(ServiceManager.accessEntry.accessState.imis)
                .triggerTime(triggerTime)
                .interHoType(type)
                .net(net)
                .build()
        PerfUntil.saveFreqEventToList(vsim_InterHO)
    }

    fun getInterHoTypeE(oldnew: String): InterHoType_E? {
        var type: InterHoType_E? = null
        when (oldnew) {
            "01" -> {
                type = InterHoType_E.INTER_HO_TYPE_2G_TO_3G
            }
            "02" -> {
                type = InterHoType_E.INTER_HO_TYPE_2G_TO_4G
            }
            "0255" -> {
                type = InterHoType_E.INTER_HO_TYPE_2G_TO_NO_SERVICE
            }
            "10" -> {
                type = InterHoType_E.INTER_HO_TYPE_3G_TO_2G
            }
            "12" -> {
                type = InterHoType_E.INTER_HO_TYPE_3G_TO_4G
            }
            "1255" -> {
                type = InterHoType_E.INTER_HO_TYPE_3G_TO_NO_SERVICE
            }
            "20" -> {
                type = InterHoType_E.INTER_HO_TYPE_4G_TO_2G
            }
            "21" -> {
                type = InterHoType_E.INTER_HO_TYPE_4G_TO_3G
            }
            "2255" -> {
                type = InterHoType_E.INTER_HO_TYPE_4G_TO_NO_SERVICE
            }
            "2550" -> {
                type = InterHoType_E.INTER_HO_TYPE_NO_SERVICE_TO_2G
            }
            "2551" -> {
                type = InterHoType_E.INTER_HO_TYPE_NO_SERVICE_TO_3G
            }
            "2552" -> {
                type = InterHoType_E.INTER_HO_TYPE_NO_SERVICE_TO_4G
            }
        }
        logd("getInterHoTypeE oldnew $oldnew, type=$type")
        return type
    }
}