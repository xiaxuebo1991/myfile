package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave

/**
 * Created by hang.deng on 2017/9/6.
 * 0/自动抓QXDM LOG开启
 */
class LogAutoCatchOnCommand(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
        val boardType: Int = checkBoardType(s2c)
        ServiceManager.accessEntry.accessState.updateCommMessage(8,"true")
        logBoardOn(boardType)
    }

    /**
     * 根据主副版类型开启抓取日志
     */
    fun logBoardOn(typeId: Int) {
        if (typeId in 0..3) {
            LogHostFileOn()
        }
    }

    /**
     * 主板日志开启
     */
    fun LogHostFileOn() {
        ServiceManager.systemApi.startModemLog(QxdmLogSave.QXDM_SERVER_CMD, 0, null)
    }
}