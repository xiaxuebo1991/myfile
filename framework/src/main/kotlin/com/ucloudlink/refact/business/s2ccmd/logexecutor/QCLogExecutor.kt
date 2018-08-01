package com.ucloudlink.refact.business.s2ccmd.logexecutor

import android.os.Environment
import com.ucloudlink.framework.protocol.protobuf.Log_opt_protocol_type
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.log.FilterLogs
import com.ucloudlink.refact.business.log.ZipUtils
import com.ucloudlink.refact.business.log.logcat.LogcatHelper
import com.ucloudlink.refact.business.s2ccmd.DATE_FORMAT_DOWNNO
import com.ucloudlink.refact.business.s2ccmd.UPLOAD_LOG_LIMIT
import com.ucloudlink.refact.business.s2ccmd.UpLogArgs
import com.ucloudlink.refact.business.s2ccmd.UpQxLogArgs
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave
import com.ucloudlink.refact.utils.JLog
import java.io.File

/**
 * Created by junsheng.zhang on 2018/3/26.
 */
object QCLogExecutor : ILogExecutor {

    private var uploadingUcLog: Boolean = false
    private var uploadingQXDMLog: Boolean = false
    private var uploadingCommonLog: Boolean = false

    override fun uploadUCLog(param: S2c_upload_log_file) {
        JLog.logd("s2c uploadUcLog file: ${param}")

        if (param.protocol == null || param.protocol == Log_opt_protocol_type.LOG_OPT_PROTOCOL_TYPE_HTTP) {
            JLog.loge("do not support http! or protocol is null")
            return
        }

        if (param.upload_info == null) {
            JLog.loge("upload info is null!!!")
            return
        }

        if (uploadingUcLog) {
            JLog.logd("s2c_cmd uploadUcLog cmd is excute now do not uploadUcLog this time!")
            return
        }
        uploadingUcLog = true

        val args = createUploadUClogParam(param)
        try {
            JLog.logd("s2c_cmd uploadUcLog: start")
            //日志过滤，先过滤处理
            FilterLogs.FilterLogsByDate(args.StartTime, args.EndTime)

            //压缩打包
            var zipName = FilterLogs.getDstLogName()
            var logName = LogcatHelper.COMPLETE_PATH_LOGCAT + File.separator + zipName

            val file = File(LogcatHelper.COMPLETE_TEMPPATH_UP_LOGCAT)
            if (!file.exists()) {
                file.mkdirs()
            }

            ZipUtils.zipDir(LogcatHelper.COMPLETE_TEMPPATH_UP_LOGCAT, logName)

            //日志上传,限制文件大小，以免上传过大的日志包浪费流量,目前限制400M
            if (FilterLogs.FileSize(logName) < UPLOAD_LOG_LIMIT) {
                JLog.logd("s2c_cmd uploadUcLog: zip size < ${UPLOAD_LOG_LIMIT} can do upload")
                FilterLogs.doUpload(logName, zipName, args)
            }
            //删除压缩包
            val zFile = File(logName)
            if (zFile.exists()) {
                zFile.delete()
            }
            JLog.logd("s2c_cmd uploadUcLog: end")
        } catch (e: Exception) {
            JLog.logd("s2c_cmd uploadUcLog: error")
            e.printStackTrace()
        } finally {
            uploadingUcLog = false
        }
    }

    override fun uploadQXDMLog(param: S2c_upload_log_file) {
        uploadingQXDMLog = true
        JLog.logd("s2c upload QXDM log file: ${param}")

        if (param.upload_info == null) {
            JLog.loge("upload QXDM info is null!!!")
            return
        }
        var paramTemp = UpQxLogArgs(
                StartTime = if (param.upload_info.start_time != null) param.upload_info.start_time else "1970-01-01 00:00:00",
                EndTime = if (param.upload_info.end_time != null) param.upload_info.end_time else "3000-01-01 00:00:00",
                FtpDN = if (param.upload_info.server_addr != null) param.upload_info.server_addr else Configuration.FTP_DN/*"223.197.68.225"*/,
                FtpUserName = if (param.upload_info.username != null) param.upload_info.username else "gdcl",
                FtpUserPwd = if (param.upload_info.password != null) param.upload_info.password else "Gdcl@Ucloud=2014",
                SaveLogPath = if (param.upload_info.path != null) param.upload_info.path else "/"
        )
        ServiceManager.systemApi.uploadLog(paramTemp)
        uploadingQXDMLog = false
    }

    override fun uploadCommonLog(param: S2c_upload_log_file) {
    }

    private fun createUploadUClogParam(param: S2c_upload_log_file): UpLogArgs {
        var paramTemp = UpLogArgs(
                StartTime = if (param.upload_info.start_time != null) param.upload_info.start_time else "1970-01-01 00:00:00",
                EndTime = if (param.upload_info.end_time != null) param.upload_info.end_time else "3000-01-01 00:00:00",
                Tag = if (param.upload_info.tags != null && param.upload_info.tags.size != 0) param.upload_info.tags.get(0) else "", // todo::::
                Level = if (param.upload_info.level != null) param.upload_info.level else "",
                Model = if (param.upload_info.mode != null) param.upload_info.mode else "",
                FtpDN = if (param.upload_info.server_addr != null) param.upload_info.server_addr else Configuration.FTP_DN,
                FtpUserName = if (param.upload_info.username != null) param.upload_info.username else "gdcl",
                FtpUserPwd = if (param.upload_info.password != null) param.upload_info.password else "Gdcl@Ucloud=2014",
                SaveLogPath = if (param.upload_info.path != null) param.upload_info.path else "/"
        )
        //check 参数是否有问题，服务器内容，时间等其他参数
        //check失败，则使用默认Ftp参数上传log
        var upLogArgs = UpLogArgs()

        if (checkUpLogParamT(paramTemp.StartTime, paramTemp.EndTime)) {
            upLogArgs.StartTime = paramTemp.StartTime
            upLogArgs.EndTime = paramTemp.EndTime
        }

        if (checkUpLogParamFtp(paramTemp.FtpDN, paramTemp.FtpUserName, paramTemp.FtpUserPwd, paramTemp.SaveLogPath)) {
            upLogArgs.FtpDN = paramTemp.FtpDN
            upLogArgs.FtpUserName = paramTemp.FtpUserName
            upLogArgs.FtpUserPwd = paramTemp.FtpUserPwd
            upLogArgs.SaveLogPath = paramTemp.SaveLogPath
        }
        return upLogArgs
    }

    //长度为19，且不为 “StartDate”，"endDate"
    private fun checkUpLogParamT(StartTime: String, EndTime: String): Boolean {
        if (StartTime.equals("<StartDate>") || EndTime.equals("<endDate>")
                || StartTime.length != DATE_FORMAT_DOWNNO || EndTime.length != DATE_FORMAT_DOWNNO) {
            return false
        }
        return true
    }

    private fun checkUpLogParamFtp(ftpDN: String, ftpUserName: String, ftpUserPwd: String, SaveLogPath: String): Boolean {
        //如果参数跟服务器默认参数一致，说明ftp服务器没有配置，则使用app定义的默认参数配置
        if ((ftpDN.equals("glocalme.com") && ftpUserName.equals("gdcl")
                && ftpUserPwd.equals("Gdcl@Ucloud=2014") && SaveLogPath.equals("/")) ||
                (ftpDN.equals("<ftpDN>") && ftpUserName.equals("<ftpUserName>")
                        && ftpUserPwd.equals("<ftpUserPwd>") && SaveLogPath.equals("<saveLogPath>"))) {
            return false
        }
        return true
    }

    override fun deleteUCLog() {
        //删除app log(UI+Service)，unzip目录不删除，cloudsim.log不删除
        var UAFLOGS_DIR = Environment.getExternalStorageDirectory().absolutePath + File.separator + LogcatHelper.SAVE_PATH + File.separator
        delAllFile(UAFLOGS_DIR)
        delAllFile(UAFLOGS_DIR + File.separator + LogcatHelper.LOG_CACHE_PATH + File.separator)
        delAllFile(UAFLOGS_DIR + File.separator + LogcatHelper.LOG_CACHE_UP_PATH + File.separator)
    }

    override fun deleteQXDMLog() {
        //删除QXDM log
        delAllFile(QxdmLogSave.QXDM_DIR_PATH)
        delAllFile(QxdmLogSave.QXDM_DIR_PATH_TEMP)
    }

    override fun deleteCommonLog() {

    }

    override fun deleteRadioLog() {
        //删除RADIO log
        var RADIO_DIR = Environment.getExternalStorageDirectory().absolutePath + File.separator + "log" + File.separator
        delAllFile(RADIO_DIR)
    }

    /**
     * 删除当前目录下所有文件,cloudsim.log不删除
     * @path 文件夹绝对路径
     */
    private fun delAllFile(path: String) {
        JLog.logd("delAllFile path=$path")
        try {
            val directory = File(path)
            if (!directory.exists()) {
                JLog.loge("delAllFile $path not exists!")
                return
            }
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && !file.name.equals("cloudsim.log")) {
                        file.delete()
                    } else if (file.isDirectory) {
                        delAllFile(file.absolutePath)
                    }
                }
            } else {
                JLog.loge("delAllFile $path have no file!")
            }
        } catch (e: Exception) {
            JLog.loge("delAllFile error:$e ")
            e.printStackTrace()
        }
    }
}