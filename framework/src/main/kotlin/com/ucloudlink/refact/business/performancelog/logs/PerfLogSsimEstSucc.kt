package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.*
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.business.performancelog.data.RebootCheck
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2018/3/24.
 */
object PerfLogSsimEstSucc : PerfLogEventBase() {
    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        JLog.logd("createMsg SsimEstSuccData = $any")
        any as SsimEstSuccData
        val createTime = System.currentTimeMillis()
        val head = PerfUntil.getCommnoHead()
        val net = PerfUntil.getMobileNetInfo(true, SimInfoData(any.psReg, any.psRoam, false, any.csReg, any.csRoam, false))
        val startInfo = Sys_start_info.Builder()
                .type(RebootCheck.rebootType)
                .reboot_times(RebootCheck.rebootCount)
                .reason(Sys_abnormal_boot_reason.SYS_ABNORMAL_BOOT_REASON_NONE) // TODO: get data
                .build()
        val seedCardInfo = Seed_card.Builder()
                .iccid(OperatorNetworkInfo.iccid)
                .imsi(any.imsi)
                .ssim_ip(PerfLog.ip)
                .build()
        val ssimSucc = Ssim_EstSucc.Builder()
                .head(head)
                .succTime((createTime / 1000).toInt())
                .net(net)
                .sys_start(startInfo)
                .ssim_info(seedCardInfo)
                .build()
        PerfUntil.saveEventToList(ssimSucc)
        RebootCheck.clearRebootCount()
    }
}

data class SsimEstSuccData(val iccid: String, val imsi: String, val ip: Int, val exception: EnablerException?,
                           val psReg: Boolean, val csReg: Boolean, val psRoam: Boolean, val csRoam: Boolean)