package com.ucloudlink.refact.business.s2ccmd.logcmd

import com.ucloudlink.framework.protocol.protobuf.Log_opt_control_type
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave
import com.ucloudlink.refact.business.s2ccmd.UpQxLogArgs
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog

/**
 * Created by hang.deng on 2017/9/6.
 */

/**
 * 主副板类型
 * LOG_OPT_HOST_FILE = 0; 0/主板日志
 * LOG_OPT_LOCAL_FILE = 1; 0/主板日志
 * LOG_OPT_HOST_AND_LOCAL_FILE = 2; 0/主板日志
 * LOG_OPT_FILE = 3; 3/不区分主副板的日志
 */
fun checkBoardType(s2c: S2c_upload_log_file): Int = s2c.file_board.value

/**
 * 日子类型
 *LOG_TYPE_UC = 0;//
 *LOG_TYPE_RADIO = 1;
 *LOG_TYPE_QXDM = 2;
 *LOG_TYPE_FOTA = 3;
 *LOG_TYPE_COMMON = 4;
 */
fun checkLogType(s2c: S2c_upload_log_file): Int = s2c.file_type.value

/**
 * 传输协议类型
 * LOG_OPT_PROTOCOL_TYPE_FTP = 0;
 *LOG_OPT_PROTOCOL_TYPE_HTTP = 1;
 */
fun checkProtocolType(s2c: S2c_upload_log_file): Int = s2c.protocol.value


/**
 * 服务器下发log处理命令的执行者
 */
fun createLogCommandInvoker(s2c: S2c_upload_log_file): Command? {
    var command: Command? = null
    when (s2c.control.value) {
    //0/自动抓QXDM LOG开启
        Log_opt_control_type.LOG_CONTROL_AUTO_SWITCH_ON.value -> command = LogAutoCatchOnCommand(s2c)
    //1/自动抓QXDM LOG禁止
        Log_opt_control_type.LOG_CONTROL_AUTO_SWITCH_OFF.value -> command = LogAutoCatchOffCommand(s2c)
    //2/开始手动抓QXDM LOG
        Log_opt_control_type.LOG_CONTROL_MANUAL_START.value -> command = LogStartCatchCommand(s2c)
    //3/停止手动抓QXDM LOG
        Log_opt_control_type.LOG_CONTROL_MANUAL_STOP.value -> command = LogStopCatchCommand(s2c)
    //4/更新抓QXDM LOG配置
        Log_opt_control_type.LOG_CONTROL_UPDATE_CONFIG.value -> command = ConfigUpdateCommand(s2c)
    //5/查询抓QXDM LOG配置
        Log_opt_control_type.LOG_CONTROL_QUERY_CONFIG.value -> command = ConfigQueryCommand(s2c)
    //6/下载抓QXDM LOG配置文件
        Log_opt_control_type.LOG_CONTROL_DOWNLOAD_CONFIG.value -> command = ConfigDownloadCommand(s2c)
    //7/上传并删除日志
        Log_opt_control_type.LOG_CONTROL_UPLOAD_AND_DELETE.value -> command = LogUploadAndDelete(s2c)
    //8/仅上传日志
        Log_opt_control_type.LOG_CONTROL_UPLOAD_ONLY.value -> command = LogUploadCommand(s2c)
    //9/仅删除日志
        Log_opt_control_type.LOG_CONTROL_DELETE_ONLY.value -> command = LogDeleteCommand(s2c)
    }
    return command
}







