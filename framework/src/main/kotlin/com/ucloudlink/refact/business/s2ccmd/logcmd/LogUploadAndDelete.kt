package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file

/**
 * Created by hang.deng on 2017/9/6.
 * 7/上传并删除日志
 */
class LogUploadAndDelete(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
        val logUpCommand: Command = LogUploadCommand(s2c)
        logUpCommand.Invoke()
    }
}