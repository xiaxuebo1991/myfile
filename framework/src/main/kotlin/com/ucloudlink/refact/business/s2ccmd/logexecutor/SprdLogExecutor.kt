package com.ucloudlink.refact.business.s2ccmd.logexecutor

import android.os.Environment
import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.S2c_upload_log_file
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.business.log.FTPUtils
import com.ucloudlink.refact.business.log.FilterLogsSprd
import com.ucloudlink.refact.business.log.TimeConver
import com.ucloudlink.refact.business.log.ZipUtils
import com.ucloudlink.refact.business.s2ccmd.UPLOAD_LOG_LIMIT
import com.ucloudlink.refact.business.s2ccmd.UpLogArgs
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.FileUtils
import com.ucloudlink.refact.utils.JLog
import java.io.File

/**
 * Created by junsheng.zhang on 2018/3/26.
 */
object SprdLogExecutor : ILogExecutor {
    private val FTPSERV_PORT = 21
    private val SDCARD_PATH = Environment.getExternalStorageDirectory().absolutePath
    private val logZipDir = SDCARD_PATH + "/ylog/ZIP"
    private val ucZipDir = logZipDir + "/UC"
    private val commonZipDir = logZipDir + "/Common"
    private val qxdmZipDir = logZipDir + "/Qxdm"

    private var uploadingUcLog: Boolean = false
    private var uploadingQXDMLog: Boolean = false
    private var uploadingCommonLog: Boolean = false

    //展讯平台的UC log被包含在sdcard/ylog/android下
    override fun uploadUCLog(param: S2c_upload_log_file) {
        JLog.logd("uploadSprdUC: ${param}")
        JLog.logd("uploadSprdUC isUploading ${uploadingUcLog}!")
        if (uploadingUcLog) {
            JLog.logd("uploadSprdUC cmd is excute now do not uploadlog this time!")
            return
        }
        uploadingUcLog = true
        try {
            FileUtils.deleteAllInDir(ucZipDir)
            val paramFtp = createUpLogArgs(param)
            handleLogFiles(FilterLogsSprd.LOG_TYPE_UC, paramFtp, ucZipDir)
        } catch (e: Exception) {
            JLog.logd("uploadSprdUC: error")
            e.printStackTrace()
        } finally {
            FileUtils.deleteAllInDir(ucZipDir)
            uploadingUcLog = false
        }
    }

    override fun uploadCommonLog(param: S2c_upload_log_file) {
        JLog.logd("uploadCommonLog: ${param}")
        JLog.logd("uploadCommonLog isUploading ${uploadingCommonLog}!")
        if (uploadingCommonLog) {
            JLog.logd("uploadCommonLog cmd is excute now do not uploadlog this time!")
            return
        }
        uploadingCommonLog = true
        try {
            FileUtils.deleteAllInDir(commonZipDir)
            val paramFtp = createUpLogArgs(param)
            handleLogFiles(FilterLogsSprd.LOG_TYPE_COMMON, paramFtp, commonZipDir)
        } catch (e: Exception) {
            JLog.logd("uploadCommonLog: error")
            e.printStackTrace()
        } finally {
            FileUtils.deleteAllInDir(commonZipDir)
            uploadingCommonLog = false
        }
    }

    override fun uploadQXDMLog(param: S2c_upload_log_file) {
        JLog.logd("uploadQXDMLog: ${param}")
        JLog.logd("uploadQXDMLog: isUploading ${uploadingQXDMLog}!")
        if (uploadingQXDMLog) {
            JLog.logd("uploadQXDMLog cmd is excute now do not uploadlog this time!")
            return
        }
        uploadingQXDMLog = true
        try {
            FileUtils.deleteAllInDir(qxdmZipDir)
            val paramFtp = createUpLogArgs(param)
            handleLogFiles(FilterLogsSprd.LOG_TYPE_QXDM, paramFtp, qxdmZipDir)
        } catch (e: Exception) {
            JLog.logd("uploadQXDMLog: error")
            e.printStackTrace()
        } finally {
            FileUtils.deleteAllInDir(qxdmZipDir)
            uploadingQXDMLog = false
        }
    }

    private fun createUpLogArgs(param: S2c_upload_log_file): UpLogArgs {
        val paramFtp = UpLogArgs(
                StartTime = if (param.upload_info != null && !TextUtils.isEmpty(param.upload_info.start_time)) param.upload_info.start_time else "1970-01-01 00:00:00",
                EndTime = if (param.upload_info != null && !TextUtils.isEmpty(param.upload_info.end_time)) param.upload_info.end_time else "3000-01-01 00:00:00",
                FtpDN = if (param.upload_info != null && !TextUtils.isEmpty(param.upload_info.server_addr)) param.upload_info.server_addr else Configuration.FTP_DN,
                FtpUserName = if (param.upload_info != null && !TextUtils.isEmpty(param.upload_info.username)) param.upload_info.username else "gdcl",
                FtpUserPwd = if (param.upload_info != null && !TextUtils.isEmpty(param.upload_info.password)) param.upload_info.password else "Gdcl@Ucloud=2014",
                SaveLogPath = if (param.upload_info != null && !TextUtils.isEmpty(param.upload_info.path)) param.upload_info.path else "/"
        )
        JLog.logd("createUpLogArgs: ${paramFtp}")
        return paramFtp
    }

    private fun handleLogFiles(logType: Int, upLogArgs: UpLogArgs, zipDir: String) {
        JLog.logd("handleLogFiles: start")
        val logFiles = FilterLogsSprd.filterLogsByDate(upLogArgs.StartTime, upLogArgs.EndTime, logType)
        if (logFiles == null) {
            JLog.logd("filter logs by date: fail, maybe no log files")
            return
        }

        val zipName = getDstLogName(logType)

        val file = File(zipDir)
        if (!file.exists()) {
            file.mkdirs()
        }

        val zipFile = ZipUtils.zipMultiFile(logFiles, zipDir + File.separator + zipName)

        //日志上传,限制文件大小，以免上传过大的日志包浪费流量,目前限制400M
        if (zipFile != null && zipFile.exists() && zipFile.length() < UPLOAD_LOG_LIMIT) {
            JLog.logd("handleLogFiles: zip size < ${UPLOAD_LOG_LIMIT} can do upload")
            doUpload(zipFile.absolutePath, zipName, upLogArgs)
        }
        //删除压缩包
        if (zipFile.exists()) {
            zipFile.delete()
        }
        JLog.logd("handleLogFiles: end")
    }

    private fun getDstLogName(type: Int): String {
        val dstName: String
        var strType = ""
        when (type) {
            1 -> strType = "uc"
            2 -> strType = "common"
            3 -> strType = "qxdm"
        }
        if (ServiceManager.accessEntry.loginInfo == null) {
            var userName = RunningStates.getUserName()
            if (TextUtils.isEmpty(userName)) {
                userName = "Unknown"
            }
            dstName = userName + "_" + Configuration.getImei(ServiceManager.appContext) + "_" + TimeConver.getDateNow() + "_glmu18a01_$strType.zip"
        } else {
            dstName = ServiceManager.accessEntry.loginInfo.username + "_" + Configuration.getImei(ServiceManager.appContext) + "_" + TimeConver.getDateNow() + "_glmu18a01_$strType.zip"
        }

        return dstName
    }

    /**
     * 上传压缩好的日志
     */
    private fun doUpload(zipPath: String, zipName: String, parm: UpLogArgs): Boolean {
        var ret = false
        try {
            JLog.logd("doUpload UpLogArgs: " + parm.toString())
            ret = FTPUtils.getInstance().initFTPSetting(parm.FtpDN, FTPSERV_PORT, parm.FtpUserName, parm.FtpUserPwd)
            JLog.logd("doUpload ret: " + ret)
            if (ret) {
                ret = FTPUtils.getInstance().uploadFile(zipPath, zipName, parm.SaveLogPath)
            }
            JLog.logd("doUpload ret2: " + ret)
            return ret
        } catch (e: Exception) {
            JLog.logd("doUpload error msg: " + e.message)
            e.printStackTrace()
            return ret
        }

    }

    override fun deleteUCLog() {
        realDeleteUCLog(FilterLogsSprd.logRootDir)
    }

    override fun deleteQXDMLog() {
        delAllFile(FilterLogsSprd.pathqxdm)
    }

    override fun deleteCommonLog() {
        realDeleteCommonLog(FilterLogsSprd.logRootDir)
    }

    override fun deleteRadioLog() {
    }

    private fun realDeleteUCLog(dir: String) {
        val file = File(dir)
        if (file.isDirectory) {
            if (file.name.equals("android")) {
                delAllFile(file.absolutePath)
            } else {
                var subFile = file.listFiles()
                for (file in subFile) {
                    realDeleteUCLog(file.absolutePath)
                }
            }
        }
    }

    private fun realDeleteCommonLog(dir: String) {
        val file = File(dir)
        if (file.isDirectory) {
            if (file.name.equals("android")) {
                return
            }
            var subFile = file.listFiles()
            for (f in subFile) {
                if (f.isFile) {
                    f.delete()
                } else {
                    realDeleteCommonLog(f.absolutePath)
                }
            }
        } else {
            file.delete()
        }
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