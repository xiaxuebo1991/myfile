package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Vsim_MR
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfLogRepeatTimeTask
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils

/**
 * Created by haiping.liu on 2018/3/29.
 * 记录主板周期测量的打点信息
 */
object PerfLogVsimMR : PerfLogEventBase() {
     val perfLogRepeatTimeTask: PerfLogRepeatTimeTask = PerfLogRepeatTimeTask(ServiceManager.appContext)

    val ID_CLOUD_SUCCESS = 0
    val ID_CLOUD_STOP = 1
    val ID_TIME_TASK_REPEAT =2//周期测量事件
    var MR_Count = 0
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("PerfLogVsimMR:$arg1")
        when(arg1){
            ID_CLOUD_SUCCESS->{
                val time = SharedPreferencesUtils.getInt(ServiceManager.appContext, PerfLog.PERI_MR_INTVL_KEY,10)//默认10分钟
                perfLogRepeatTimeTask.start(time*60)
            }
            ID_CLOUD_STOP->{
                perfLogRepeatTimeTask.stop()
            }
            ID_TIME_TASK_REPEAT->{
                if (PerfLog.getCurrentPersent() != 100){
                    loge("getCurrentPersent() != 100 DO NOT SAVE!!!")
                    return
                }
                val dataInfo = ServiceManager.cloudSimEnabler.getDataEnableInfo()
                val net = PerfUntil.getMobileNetInfo(false, SimInfoData(dataInfo.dataReg, dataInfo.dataRoam, false, dataInfo.voiceReg, dataInfo.voiceRoam, false))

                val vsim_MR = Vsim_MR.Builder()
                        .head(PerfUntil.getCommnoHead())
                        .triggerTime((System.currentTimeMillis()/1000).toInt())
                        .imsi(ServiceManager.accessEntry.accessState.imis)
                        .net(net)
                        .ueTxPower(255)//todo 触发时UE发射功率 如果未取到赋初始值 255
                        .build()
                PerfUntil.saveFreqEventToList(vsim_MR)
                MR_Count++
                //每6次触发(10*6 分钟),间隔一小时上报
                if (MR_Count%6 == 0){
                    PerfUntil.saveEventToDatabase()
                    PerfLog.startUpload()
                }
            }
        }
    }
}