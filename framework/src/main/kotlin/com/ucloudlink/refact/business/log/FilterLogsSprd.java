package com.ucloudlink.refact.business.log;


import android.os.Environment;
import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.restore.RunningStates;
import com.ucloudlink.refact.business.s2ccmd.UpLogArgs;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * 筛选日志 U3C
 */
public class FilterLogsSprd {
    public final static int LOG_TYPE_UC = 1;
    public final static int LOG_TYPE_COMMON = 2;
    public final static int LOG_TYPE_QXDM = 3;

    private static String SDCARD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath();

    public static String logRootDir = SDCARD_PATH + "/ylog";
    public static String crashLogDir = SDCARD_PATH + "/uaflogs";
    public static String pathqxdm = "/data/ylog";

    /**
     * 过滤日志--以时间段
     *
     * @param startGMTTime 时间字符串格式 "yyyy-MM-dd HH:mm:ss" 为GMT时间
     * @param endGMTTime   时间字符串格式 "yyyy-MM-dd HH:mm:ss" 为GMT时间
     * @param logType      log 类型
     */
    public static List<File> filterLogsByDate(String startGMTTime, String endGMTTime, int logType) {
        try {
            Date startDate = TimeConver.converGMTStr2LocalDate(startGMTTime);
            Date endDate = TimeConver.converGMTStr2LocalDate(endGMTTime);
            logd("FilterLogsByDate:startDate:" + startDate + ",endDate:" + endDate + " , logType=" + logType);
            if (logType == LOG_TYPE_UC) {
                List<File> crashLog = filtrateCrashLog(startDate, endDate);
                List<File> adbLog = filtrateUcLog(startDate, endDate);
                List<File> logFiles = new ArrayList<>();
                if (crashLog != null) {
                    logFiles.addAll(crashLog);
                }
                if (adbLog != null) {
                    logFiles.addAll(adbLog);
                }
                return logFiles;
            } else if (logType == LOG_TYPE_COMMON) {
                return filtrateCommonLog(startDate, endDate);
            } else if (logType == LOG_TYPE_QXDM) {
                return filtrateQxdmLog(startDate, endDate);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static List<File> filtrateUcLog(Date starttime, Date endtime) throws IOException {
        File file = new File(logRootDir);
        if (!file.exists() || !file.isDirectory()) {
            return null;
        }
        List<File> filelist = new ArrayList<>();
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && f.getName().equalsIgnoreCase("ylog")) {
                    filelist.add(f);
                } else if (f.isDirectory() && f.getName().equalsIgnoreCase("last_ylog")) {
                    File[] sub = f.listFiles();
                    if (sub != null) {
                        Collections.addAll(filelist, sub);
                    }
                }
            }
        }
        List<File> logFiles = new ArrayList<>();
        for (File f : filelist) {
            File[] subList = f.listFiles();
            if (subList != null) {
                for (File sub : subList) {
                    if (sub.getName().equalsIgnoreCase("android")) {
                        List<File> logs = matchingLogFiles(sub, starttime, endtime);
                        if (logs != null) {
                            logFiles.addAll(logs);
                        }
                    }
                }
            }
        }
        return logFiles;
    }

    private static List<File> filtrateCrashLog(Date startTime, Date endTime) {
        File file = new File(crashLogDir);
        if (file.exists()) {
            File[] fileList = file.listFiles();
            if (fileList != null) {
                List<File> files = new ArrayList<>();
                for (File f : fileList) {
                    if (f.isFile() && f.getName().startsWith("crashLog")) {
                        String fName = f.getName();
                        String timestampStr = fName.substring(fName.lastIndexOf("-") + 1, fName.lastIndexOf(".log"));
                        if (TextUtils.isDigitsOnly(timestampStr)) {
                            long timestamp = Long.parseLong(timestampStr);
                            if (startTime != null && endTime != null) {
                                if (startTime.getTime() <= timestamp && timestamp <= endTime.getTime()) {
                                    files.add(f);
                                }
                            } else {
                                files.add(f);
                            }
                        }
                    }
                }
                return files;
            }
        }
        return null;
    }

    private static List<File> filtrateCommonLog(Date startTime, Date endTime) throws IOException {
        File file = new File(logRootDir);
        if (!file.exists() || !file.isDirectory()) {
            return null;
        }
        List<File> filelist = new ArrayList<>();
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && f.getName().equalsIgnoreCase("ylog")) {
                    filelist.add(f);
                } else if (f.isDirectory() && f.getName().equalsIgnoreCase("last_ylog")) {
                    File[] sub = f.listFiles();
                    if (sub != null) {
                        Collections.addAll(filelist, sub);
                    }
                }
            }
        }
        List<File> logFiles = new ArrayList<>();
        for (File f : filelist) {
            File[] subList = f.listFiles();
            if (subList != null) {
                for (File sub : subList) {
                    if (!sub.getName().equalsIgnoreCase("android")) {
                        List<File> logs = matchingLogFiles(sub, startTime, endTime);
                        if (logs != null) {
                            logFiles.addAll(logs);
                        }
                    }
                }
            }
        }
        return logFiles;
    }

    private static List<File> filtrateQxdmLog(Date startTime, Date endTime) throws IOException {
        File file = new File(pathqxdm);
        if (!file.exists() || !file.isDirectory()) {
            return null;
        }
        File[] fileList = file.listFiles();
        return filtrateLog(fileList, startTime, endTime);
    }

    private static List<File> matchingLogFiles(File parentDir, final Date startTime, final Date endTime) throws IOException {
        if (parentDir != null && parentDir.isDirectory()) {
            File[] logFiles = parentDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    if (pathname.getName().equalsIgnoreCase("outline")
                            || pathname.getName().equalsIgnoreCase("analyzer.py")) {
                        return false;
                    }
                    return true;
                }
            });
            List<File> fileList = filtrateLog(logFiles, startTime, endTime);
            if (fileList != null && fileList.size() > 0) {
                logFiles = parentDir.listFiles();
                for (File file : logFiles) {
                    if (file.getName().equalsIgnoreCase("outline")
                            || file.getName().equalsIgnoreCase("analyzer.py")) {
                        fileList.add(file);
                    }
                }
                return fileList;
            }
        }
        return null;
    }

    private static List<File> filtrateLog(File[] fileList, Date startTime, Date endTime) throws IOException {
        if (fileList == null) {
            return null;
        }
        List<File> allFiles = new ArrayList<>();
        long start = startTime == null ? 0 : startTime.getTime();
        long end = endTime == null ? Long.MAX_VALUE : endTime.getTime();
        for (File f : fileList) {
            List<File> files = FileFilterUtil.filter(f, start, end);
            if (files != null) {
                allFiles.addAll(files);
            }
        }
        return allFiles;
    }
}
