package com.ucloudlink.refact.business.log;

import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.restore.RunningStates;
import com.ucloudlink.refact.business.log.logcat.LogcatHelper;
import com.ucloudlink.refact.business.netcheck.NetworkManager;
import com.ucloudlink.refact.business.s2ccmd.UpLogArgs;
import com.ucloudlink.refact.business.s2ccmd.UpQxLogArgs;
import com.ucloudlink.refact.config.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.ucloudlink.refact.business.log.logcat.LogcatHelper.LOG_SUFFIX;
import static com.ucloudlink.refact.business.log.logcat.LogcatHelper.UNZIP;
import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logi;

/**
 * 筛选日志
 * 要从LogcatHelper类里面获取log文件的路劲
 */
public class FilterLogs {

    public final static String TAG                = "FilterLogs";

    public final static int FTPSERV_PORT = 21;

    /**
     * 过滤日志--以时间段
     *
     * @param startGMTTime 时间字符串格式 "yyyy-MM-dd HH:mm:ss" 为GMT时间
     * @param endGMTTime   时间字符串格式 "yyyy-MM-dd HH:mm:ss" 为GMT时间
     * @return
     */
    public static void FilterLogsByDate(String startGMTTime, String endGMTTime) {

        try {
            //starttime endtime转Date 且startime 小于endtime 转成功才能过滤，否则不处理，返回失败
            Date startDate = TimeConver.converGMTStr2LocalDate(startGMTTime);
            Date endDate = TimeConver.converGMTStr2LocalDate(endGMTTime);

            logd("FilterLogsByDate:startDate:" + startDate.toString() + ",endDate:" + endDate.toString());

            doFilterLogs(startDate, endDate);

        } catch (Exception e) {
            //            e.printStackTrace();
        }
    }

    //文件名的日期在要求范围内则压缩
    private static void doFilterLogs(Date startDate, Date endDate) {
        try {
            //根据文件名过滤，文件名在startDate 到 endDate之间的都压缩到指定文件
            Date dfName;
            String fileName;
            String fileNameNoSuffix;

            String upLoadedDir = LogcatHelper.COMPLETE_PATH_LOGCAT;
            String srcFileDir = LogcatHelper.COMPLETE_TEMPPATH_LOGCAT;
            String dstFileDir = LogcatHelper.COMPLETE_TEMPPATH_UP_LOGCAT;

            //清理上传文件夹里面的zip包，和crash文件
            deleteFileBySuffix(dstFileDir, ".zip");
            deleteFileBySuffix(dstFileDir, ".log");
            deleteFileBySuffix(upLoadedDir, ".zip");

            addCurrentLogToUploadDir(startDate, endDate);

            File fileOrDirectory = new File(srcFileDir);
            // 如果此文件是一个文件夹则处理，否则为false。
            if (fileOrDirectory.isDirectory() && isDateAvailable(startDate, endDate)) {
                File[] entries = fileOrDirectory.listFiles();
                for (int i = 0; i < entries.length; i++) {
                    fileName = entries[i].getName();
                    fileNameNoSuffix = fileName.substring(0, fileName.lastIndexOf("."));
                    dfName = TimeConver.converString2Time(fileNameNoSuffix, TimeConver.DataformatSec);
                    if (isDateAvailable(startDate, dfName) && isDateAvailable(dfName, endDate)) {
                        //                        logd( "doFilterLogs: CopySdcardFile" + fileName);
                        CopySdcardFile(srcFileDir + File.separator + fileName, dstFileDir + File.separator + fileName);
                    }
                }
            }

            addCrashLogToUploadDir(upLoadedDir, dstFileDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addCurrentLogToUploadDir(Date startDate, Date endDate) {
        LogcatHelper logcatHelper = LogcatHelper.getInstance(ServiceManager.appContext);
        File logFile = logcatHelper.getCurrentLogFile();
        if (logFile != null && logFile.exists() && logFile.isFile()) {
            
            String logStartTime = logFile.getName().replace(UNZIP, "").replace(LOG_SUFFIX, "");
            Date LogDate = TimeConver.converString2Time(logStartTime, TimeConver.DataformatSec);
            Date NowDate = new Date(System.currentTimeMillis());
            if (isDateAvailable(startDate, NowDate) && isDateAvailable(LogDate, endDate)) {
                logcatHelper.packCurrentLog();
                try {
                    Thread.sleep(8000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static void addCrashLogToUploadDir(String CrashLogDir, String dstFileDir) {
        try {
            String fileName;

            File fileOrDirectory = new File(CrashLogDir);
            // 如果此文件是一个文件夹则处理，否则为false。
            if (fileOrDirectory.isDirectory()) {
                File[] entries = fileOrDirectory.listFiles();
                for (int i = 0; i < entries.length; i++) {
                    fileName = entries[i].getName();
                    if (fileName.contains(LogcatHelper.SUFFIX_OF_CRASH_FILE)) {
                        CopySdcardFile(CrashLogDir + File.separator + fileName, dstFileDir + File.separator + fileName);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean CopySdcardFile(String fromFile, String toFile) {
        try {
            InputStream fosfrom = new FileInputStream(fromFile);
            OutputStream fosto = new FileOutputStream(toFile);
            byte bt[] = new byte[2048];
            int c;
            while ((c = fosfrom.read(bt)) > 0) {
                fosto.write(bt, 0, c);
            }
            fosfrom.close();
            fosto.close();
            return true;

        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 检测判断开始时间是否小于等于结束时间
     *
     * @param StartDate
     * @param endDate
     * @return
     */
    public static boolean isDateAvailable(Date StartDate, Date endDate) {
        if ((StartDate != null && endDate != null)) {
            if (StartDate.before(endDate) || StartDate.equals(endDate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 上传压缩好的日志
     *
     * @return
     */
    public static boolean doUpload(String zipPath, String zipName, UpLogArgs parm) {
        boolean ret = false;
        try {
            logd("UpLogArgs: " + parm.toString());

            ret = FTPUtils.getInstance().initFTPSetting(parm.getFtpDN(), FTPSERV_PORT, parm.getFtpUserName(), parm.getFtpUserPwd());
            if (ret) {
                ret = FTPUtils.getInstance().uploadFile(zipPath, zipName, parm.getSaveLogPath());
            }
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return ret;
        }
    }

    /**
     * 上传压缩好的日志
     *
     * @return
     */
    public static boolean doQxUpload(String zipPath, String zipName, UpQxLogArgs parm) {
        boolean ret = false;
        try {
            logd("UpQxLogArgs: " + parm.toString());

            ret = FTPUtils.getInstance().initFTPSetting(parm.getFtpDN(), FTPSERV_PORT, parm.getFtpUserName(), parm.getFtpUserPwd());
            if (!ret) {
                return ret;
            }
            ret = FTPUtils.getInstance().uploadFile(zipPath, zipName, parm.getSaveLogPath());
            if (!ret) {
                return ret;
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret = false;
        }
        return ret;
    }

    public static String getDstLogName() {
        String DstName;
        if (ServiceManager.INSTANCE.getAccessEntry().getLoginInfo() == null) {
            String userName = RunningStates.getUserName();
            if(TextUtils.isEmpty(userName)){
                userName = "Unknown";
            }
            DstName = userName + "_" + Configuration.INSTANCE.getImei(ServiceManager.appContext) + "_" + TimeConver.getDateNow() + ".zip";
        } else {
            DstName = ServiceManager.INSTANCE.getAccessEntry().getLoginInfo().getUsername() + "_" + NetworkManager.INSTANCE.getLoginNetInfo().getImei() + "_" + TimeConver.getDateNow() + ".zip";
        }

        return DstName;
    }

    //通过后缀删除文件
    public static void deleteFileBySuffix(String folder, String ext) {

        GenericExtFilter filter = new GenericExtFilter(ext);
        File dir = new File(folder);

        //list out all the file name with .zip extension
        String[] list = dir.list(filter);

        if (list.length == 0)
            return;

        File fileDelete;

        for (String file : list) {
            String temp = new StringBuffer(folder).append(File.separator).append(file).toString();
            fileDelete = new File(temp);
            boolean isdeleted = fileDelete.delete();
            logi("file : " + temp + " is deleted : " + isdeleted);
        }
    }

    //inner class, generic extension filter
    public static class GenericExtFilter implements FilenameFilter {

        private String ext;

        public GenericExtFilter(String ext) {
            this.ext = ext;
        }

        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(ext));
        }
    }

    //统计文件夹下文件个数
    public static int countFileOfDir(String dirName) {
        int count = 0;
        try {
            File fileOrDirectory = new File(dirName);
            // 如果此文件是一个文件，否则为false。
            if (fileOrDirectory.isDirectory()) {
                File[] entries = fileOrDirectory.listFiles();
                for (int i = 0; i < entries.length; i++) {
                    count++;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            return count;
        }
    }

    /**
     * 删除文件夹下".zip"文件，保留指定个数最新文件
     *
     * @param dirName   需要清理的文件夹
     * @param keepCount 保留文件数
     */
    public static void deleteSurplusFile(String dirName, int keepCount) {
        List<Date> fileList = new ArrayList<Date>();
        Date dfName;
        String fileName;
        File fileDelete;

        try {
            File fileOrDirectory = new File(dirName);
            // 如果此文件是一个文件，否则为false。
            if (fileOrDirectory.isDirectory()) {
                File[] entries = fileOrDirectory.listFiles();
                for (int i = 0; i < entries.length; i++) {
                    fileName = entries[i].getName();
                    fileName = fileName.substring(0, fileName.lastIndexOf("."));
                    dfName = TimeConver.converString2Time(fileName, TimeConver.DataformatSec);
                    fileList.add(dfName);
                }

                Collections.sort(fileList);

                for (int j = 0; j < fileList.size(); j++) {
                    if (j <= (fileList.size() - keepCount)) {
                        String temp = dirName + File.separator + TimeConver.getStrFromDate(fileList.get(j), TimeConver.DataformatSec) + ".zip";
                        fileDelete = new File(temp);
                        fileDelete.delete();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {

        }
    }

    public static long FileSize(String fileName) {
        try {
            File f = new File(fileName);
            if (f.exists() && f.isFile()) {
                return f.length();
            } else {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
