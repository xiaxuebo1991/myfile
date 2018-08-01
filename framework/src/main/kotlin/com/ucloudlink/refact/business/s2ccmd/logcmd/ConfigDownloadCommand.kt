package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file

/**
 * Created by hang.deng on 2017/9/6.
 * 6/下载抓QXDM LOG配置文件
 */
class ConfigDownloadCommand(val s2c: S2c_upload_log_file) : Command() {

    override fun executer() {
    }
}