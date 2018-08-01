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
object PerfLogVsimEstFail : PerfLogEventBase() {
    var lastErrorCode = -1
    var lastErrorTime = 0L

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        JLog.logd("createMsg VsimFailData = $any")
        any as VsimFailData
        val createTime = System.currentTimeMillis()
        if ((createTime - lastErrorTime < 1000 * 5)&&(any.errCode == lastErrorCode) ) {
            JLog.loge("createMsg  return: same error code in 5 second!!")
            return
        }
        lastErrorTime = createTime
        lastErrorCode = any.errCode
        val head = PerfUntil.getCommnoHead()
        val net = PerfUntil.getMobileNetInfo(false, SimInfoData(any.psReg, any.psRoam, false, any.csReg, any.csRoam, false))
        val err = Err_info.Builder().err_type(any.errType).err_code(any.errCode).build()
        val vsimFail = Vsim_EstFail.Builder()
                .head(head)
                .errorTime((createTime / 1000).toInt())
                .imsi(any.imsi)
                .net(net)
                .err(err)
                .vsim_ip(PerfLog.ip)
                .build()
        PerfUntil.saveFreqEventToList(vsimFail)
    }
}

data class VsimFailData(val iccid: String, val imsi: String, val errType: Int, val errCode: Int,
                        val upMbr: Int, val downMbr: Int, val exception: EnablerException?,
                        val psReg: Boolean, val csReg: Boolean, val psRoam: Boolean, val csRoam: Boolean)