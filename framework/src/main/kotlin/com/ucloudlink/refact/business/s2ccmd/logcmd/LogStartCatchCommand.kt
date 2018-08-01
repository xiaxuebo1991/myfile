package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file

/**
 * Created by hang.deng on 2017/9/6.
 * 2/开始手动抓QXDM LOG
 */
class LogStartCatchCommand(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
        val boardType = checkBoardType(s2c)
    }
}