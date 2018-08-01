package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.Log_opt_file_type
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.framework.tasks.UploadFlowTask
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog

/**
 * Created by hang.deng on 2017/9/6.
 * 8/仅上传日志
 */
class LogUploadCommand(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
        val uploadLog = ServiceManager.systemApi.getILogExecutor()
        when {
            s2c.file_type == Log_opt_file_type.LOG_TYPE_UC -> {
                configNetSpeedWith()
                uploadLog.uploadUCLog(s2c)
            }
            s2c.file_type == Log_opt_file_type.LOG_TYPE_QXDM -> {
                configNetSpeedWith()
                uploadLog.uploadQXDMLog(s2c)
            }
            s2c.file_type == Log_opt_file_type.LOG_TYPE_COMMON -> {
                configNetSpeedWith()
                uploadLog.uploadCommonLog(s2c)
            }
        }
    }

    private fun configNetSpeedWith(){
        if (s2c.upload_info?.server_addr != null) {
            JLog.loge("BandWidth LogUploadCommand.Invoke() param server_addr = " + (s2c.upload_info.server_addr))
            FlowBandWidthControl.getInstance().iNetSpeedCtrl.configNetSpeedWithIP(s2c.upload_info.server_addr, 0, true, true, Configuration.isEnableBandWidth, 0, 0)
        } else {
            JLog.loge("BandWidth LogUploadCommand.Invoke() param FTP_DN = " + (Configuration.FTP_DN))
            FlowBandWidthControl.getInstance().iNetSpeedCtrl.configNetSpeedWithIP(Configuration.FTP_DN, 0, true, true, Configuration.isEnableBandWidth, 0, 0)
        }
        UploadFlowTask.checkDnsParse()
    }
}