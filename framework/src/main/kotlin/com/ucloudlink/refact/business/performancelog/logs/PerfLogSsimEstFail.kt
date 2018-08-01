package com.ucloudlink.refact.business.performancelog.logs

import com.ucloudlink.framework.protocol.protobuf.preflog.*
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.business.performancelog.SimInfoData
import com.ucloudlink.refact.business.performancelog.data.RebootCheck
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by shiqianhua on 2018/3/24.
 */
object PerfLogSsimEstFail : PerfLogEventBase() {
    var lastErrorCode = -1
    var lastErrorTime = 0L

    override fun createMsg(arg1: Int, arg2: Int, any: Any) {
        logd("createMsg SsimEstFailData = $any")
        any as SsimEstFailData

        val createTime = System.currentTimeMillis()

        if ((createTime - lastErrorTime < 1000 * 5)&&(any.errCode == lastErrorCode) ) {
            JLog.loge("createMsg  return: same error code in 5 second!!")
            return
        }
        lastErrorTime = createTime
        lastErrorCode = any.errCode

        val head = PerfUntil.getCommnoHead()
        val net = PerfUntil.getMobileNetInfo(true, SimInfoData(any.psReg, any.psRoam, false, any.csReg, any.csRoam, false))
        val err = Err_info.Builder().err_type(PerfUntil.getPerfSsimErrType(any.errType)).err_code(any.errCode).build()
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
        val ssimFail = Ssim_EstFail.Builder()
                .head(head)
                .errorTime((createTime / 1000).toInt())
                .net(net)
                .err(err)
                .sys_start(startInfo)
                .ssim_info(seedCardInfo)
                .build()
        PerfUntil.saveFreqEventToList(ssimFail)
        RebootCheck.clearRebootCount()
    }
}

data class SsimEstFailData(val iccid: String, val imsi: String, val errType: EnablerException?,
                           val errCode: Int, val psReg: Boolean, val psRoam: Boolean, val csReg: Boolean, val csRoam: Boolean)