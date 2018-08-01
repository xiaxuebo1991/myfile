package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.*
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2018/3/24.
 */
object PerfLogVsimEstSucc : PerfLogEventBase() {
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        JLog.logd("createMsg VsimSuccData = $any")
        any as VsimSuccData
        val createTime = System.currentTimeMillis()
        val head = PerfUntil.getCommnoHead()
        val net = PerfUntil.getMobileNetInfo(false, SimInfoData(any.psReg, any.psRoam, false, any.csReg, any.csRoam, false))
        val vsimSucc = Vsim_EstSucc.Builder()
                .head(head)
                .succTime((createTime / 1000).toInt())
                .imsi(any.imsi)
                .net(net)
                .vsim_ip(PerfLog.ip)
                .build()
        PerfUntil.saveEventToList(vsimSucc)
    }
}

data class VsimSuccData(val iccid: String, val imsi: String, val upMbr: Long, val downMbr: Long, val ip: Int, val exception: EnablerException?,
                        val psReg: Boolean, val csReg: Boolean, val psRoam: Boolean, val csRoam: Boolean)