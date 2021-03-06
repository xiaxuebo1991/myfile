package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave

/**
 * Created by hang.deng on 2017/9/6.
 * 1/自动抓QXDM LOG禁止
 */
class LogAutoCatchOffCommand(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
        val boardType: Int = checkBoardType(s2c)
        ServiceManager.accessEntry.accessState.updateCommMessage(8, "false")
        logBoardOff(boardType)
    }

    /**
     * 根据主副版类型关闭抓取日志
     */
    fun logBoardOff(typeId: Int) {
        if (typeId in 0..3) {
            LogHostFileOff()
        }
    }

    /**
     * 主板日志关闭
     */
    fun LogHostFileOff() {
        ServiceManager.systemApi.stopModemLog(QxdmLogSave.QXDM_SERVER_CMD, 0, null)
    }
}