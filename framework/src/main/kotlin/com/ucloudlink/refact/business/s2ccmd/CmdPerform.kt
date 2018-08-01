package com.ucloudlink.refact.business.s2ccmd

import android.os.Handler
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.business.log.FilterLogs
import com.ucloudlink.refact.business.log.logcat.LogcatHelper
import com.ucloudlink.refact.business.log.logcat.LogcatHelper.COMPLETE_TEMPPATH_UP_LOGCAT
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave
import com.ucloudlink.refact.business.log.ZipUtils
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.loge
import java.io.File

/**
 * Created by chunjiao.li on 2016/9/10.
 */
object CmdPerform {
    lateinit var progressHint: Handler
    const val COMPRESS_ADB_LOG_START = 11
    const val COMPRESS_ADB_LOG_COMPLETE = 12
    const val UPLOAD_ADB_LOG_START = 13
    const val UPLOAD_ADB_LOG_SUCCEED = 14
    const val UPLOAD_ADB_LOG_FAILED = 15

    const val COMPRESS_QXDM_LOG_START = 21
    const val COMPRESS_QXDM_LOG_COMPLETE = 22
    const val UPLOAD_QXDM_LOG_START = 23
    const val UPLOAD_QXDM_LOG_SUCCEED = 24
    const val UPLOAD_QXDM_LOG_FAILED = 25

    var isUploadingLog:Boolean = false

    var isUploadingU3cUC:Boolean = false
    var isUploadingU3cQXDM:Boolean = false
    var isUploadingU3cCOMMON:Boolean = false

    //日志上传，由于受网络环境影响，所以提供的是不保证交付的服务
    fun uploadlog(parm: UpLogArgs) {
        logd("s2c_cmd uploadlog cmd isUploadingLog ${isUploadingLog}!")

        if(isUploadingLog){
            logd("s2c_cmd uploadlog cmd is excute now do not uploadlog this time!")
            return
        }

        var ret: Boolean = false
        try {
            logd("s2c_cmd uploadlog: start")
            isUploadingLog = true
            //日志过滤，先过滤处理
            FilterLogs.FilterLogsByDate(parm.StartTime, parm.EndTime)

            //压缩打包
            var zipName = FilterLogs.getDstLogName()
            var LogName = LogcatHelper.COMPLETE_PATH_LOGCAT + File.separator + zipName

            val file = File(COMPLETE_TEMPPATH_UP_LOGCAT)
            if (!file.exists()) {
                file.mkdirs()
            }

            ZipUtils.zipDir(LogcatHelper.COMPLETE_TEMPPATH_UP_LOGCAT, LogName)

            //日志上传,限制文件大小，以免上传过大的日志包浪费流量,目前限制400M
            if (FilterLogs.FileSize(LogName) < UPLOAD_LOG_LIMIT) {
                logd("s2c_cmd uploadlog: zip size < ${UPLOAD_LOG_LIMIT} can do upload")
                FilterLogs.doUpload(LogName, zipName, parm)
            }

            logd("s2c_cmd uploadlog: end")
        } catch(e: Exception) {
            logd("s2c_cmd uploadlog: error")
            e.printStackTrace()
        }finally {
            isUploadingLog = false
        }
    }

    fun uploadlogForUI(parm: UpLogArgs) {
        if (progressHint == null) {
            return
        }

        var ret: Boolean = false
        try {
            logd("s2c_cmd uploadlog: start")
            sendUploadTips(COMPRESS_ADB_LOG_START)
            //日志过滤，先过滤处理
            FilterLogs.FilterLogsByDate(parm.StartTime, parm.EndTime)

            //压缩打包
            var zipName = FilterLogs.getDstLogName()
            var LogName = LogcatHelper.COMPLETE_PATH_LOGCAT + File.separator + zipName

            val file = File(COMPLETE_TEMPPATH_UP_LOGCAT)
            if (!file.exists()) {
                file.mkdirs()
            }

            ZipUtils.zipDir(LogcatHelper.COMPLETE_TEMPPATH_UP_LOGCAT, LogName)

            sendUploadTips(COMPRESS_ADB_LOG_COMPLETE)

            //日志上传,限制文件大小，以免上传过大的日志包浪费流量,目前限制400M
            if (FilterLogs.FileSize(LogName) < UPLOAD_LOG_LIMIT) {
                logd("s2c_cmd uploadlog: zip size < ${UPLOAD_LOG_LIMIT} can do upload")
                sendUploadTips(UPLOAD_ADB_LOG_START)
                if (FilterLogs.doUpload(LogName, zipName, parm)) {
                    sendUploadTips(UPLOAD_ADB_LOG_SUCCEED)
                } else {
                    sendUploadTips(UPLOAD_ADB_LOG_FAILED)
                }
            } else {
                sendUploadTips(UPLOAD_ADB_LOG_FAILED)
            }

            logd("s2c_cmd uploadlog: end")
        } catch(e: Exception) {
            logd("s2c_cmd uploadlog: error")
            e.printStackTrace()
        }
    }

    //qx日志上传
    fun upqxloadlog(parm: UpQxLogArgs) {
        try {
            logd("s2c_cmd upqxloadlog: start")

            //QXDMlog压缩上传
            ServiceManager.systemApi.uploadLog(parm)
            ServiceManager.systemApi.stopModemLog(QxdmLogSave.QXDM_SERVER_CMD, 0, null)//关闭日志打印
            logd("s2c_cmd upqxloadlog: end")
        } catch(e: Exception) {
            logd("s2c_cmd upqxloadlog: error")
            e.printStackTrace()
        }
    }

    //qx按时间上传日志
    fun upqxloadlog2(parm: UpQxLogArgs) {
        if (progressHint == null) {
            return
        }
        try {
            logd("s2c_cmd upqxloadlog2: start")

            //QXDMlog压缩上传
            ServiceManager.systemApi.uploadLog(parm)
            ServiceManager.systemApi.stopModemLog(QxdmLogSave.QXDM_SERVER_CMD, 0, null)//关闭日志打印
            logd("s2c_cmd upqxloadlog2: end")
        } catch(e: Exception) {
            logd("s2c_cmd upqxloadlog2: error")
            e.printStackTrace()
        }
    }

    fun sendUploadTips(what: Int) {
        if (progressHint == null) {
            return
        } else {
            progressHint.sendEmptyMessage(what)
        }
    }
}
