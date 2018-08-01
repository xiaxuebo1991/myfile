package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.Log_opt_file_type
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by hang.deng on 2017/9/6.
 * 9/仅删除日志
 */
class LogDeleteCommand(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
        logd("LogDeleteCommand del file_type=${s2c.file_type}")
        val uploadLog = ServiceManager.systemApi.getILogExecutor()
        when {
            s2c.file_type == Log_opt_file_type.LOG_TYPE_UC -> uploadLog.deleteUCLog()
            s2c.file_type == Log_opt_file_type.LOG_TYPE_QXDM -> uploadLog.deleteQXDMLog()
            s2c.file_type == Log_opt_file_type.LOG_TYPE_COMMON -> uploadLog.deleteCommonLog()
            s2c.file_type == Log_opt_file_type.LOG_TYPE_RADIO -> uploadLog.deleteRadioLog()
        }
    }
}