package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_power_off
import com.ucloudlink.framework.protocol.protobuf.preflog.Ter_power_on
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.data.RebootCheck
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.SharedPreferencesUtils


/**
 * Created by haiping.liu on 2018/3/27.
 */

object PerfLogPownOn :PerfLogEventBase(){
    val SP_BOOT_TIME = "bootTime"
    val SP_BOOT_POWER_LEFT = "bootPowerLeft"
    /**
     * arg1 :boot time
     * arg2:power left
     */
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("createMsg")
       val ter_power_on = Ter_power_on.Builder()
               .head(PerfUntil.getCommnoHead())
               .poweron_time(arg1)
               .up_type(RebootCheck.rebootType)
               .power_left(arg2)
               .build()
        PerfUntil.saveEventToList(ter_power_on)

        //读取poweroff数据保存到数据库
            try {
                val openFileInput = ServiceManager.appContext.openFileInput("ter_power_off")
                val getPowerOff = Ter_power_off.ADAPTER.decode(openFileInput)
                PerfUntil.saveEventToList(getPowerOff)
                val isdelsuccess = ServiceManager.appContext.deleteFile("ter_power_off")
                logd("del poweroff file:$isdelsuccess")
            }catch (e:Exception){
                logd("save PowerOff:$e")
            }
    }
}