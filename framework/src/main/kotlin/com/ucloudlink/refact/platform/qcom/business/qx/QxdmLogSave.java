package com.ucloudlink.refact.platform.qcom.business.qx;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.log.TimeConver;
import com.ucloudlink.refact.business.log.ZipUtils;
import com.ucloudlink.refact.business.s2ccmd.CmdPerform;
import com.ucloudlink.refact.business.s2ccmd.UpQxLogArgs;
import com.ucloudlink.refact.business.log.FilterLogs;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ucloudlink.refact.utils.JLog.logd;


/**
 * Created by zhangxian on 2016/11/11.
 */
public class QxdmLogSave{
    private static String LOG_TAG = "QxdmLogSaveLog";
    private static QxdmLogSave QxdmLogIns = null;
    private LogControl mLogControl = null;
    private LogFetch mLogFetch = null;
    private boolean mUploading = false;//是否正在上传日志
    private boolean mRunning = false;//QXDMlog开启与关闭标志
    private boolean mFetchFlag = false;//QXDMlog Fetch线程标志,判断该线程是否准好,用于handle退出
    //private boolean mQxLogUploading = false;//QXDMlog上报标志,处于上传日志中
    public static boolean mQxLogServerStatus = false;//QXDMlog上报标志,处于服务器下发打开与上传之间
    //public final static String QXDM_EXT_DIR_PATH = "/sdcard/diag_logs/";//QXDMlog存放文件夹
    public final static String QXDM_DIR_PATH = "/sdcard/diag_logs/";//QXDMlog存放文件夹
    public final static String QXDM_DIR_PATH_TEMP = "/sdcard/diag_logs/temp/";//QXDMlog存放文件夹
    private final static int COUNT_OF_QXDMLOG = 50;//保存日志个数
    private final static String MAX_LEN_OF_QXDMLOG = "50";//每个日志文件大小的最大值
    public final static String QXDM_CFG_PATH1 = "/sdcard/qxdmlogcfg/system_qxdm_config_all.cfg";
    public final static String QXDM_CFG_PATH0 = "/sdcard/qxdmlogcfg/system_qxdm_config_simple.cfg";
    private int closeSleepTime = 500;//关闭命令延时
    public final static int QXDM_OPEN_CMD = 1;
    public final static int QXDM_CLOSE_CMD = 2;
    public final static int QXDM_CLEAN_EXT_CMD = 3;
    public final static int QXDM_UPLOAD_CMD = 4;
    public final static int QXDM_CLEAN_ZIP_CMD = 5;
    public final static int QXDM_UPLOAD2_CMD = 6;
    private static UpQxLogArgs QxLogArgs = null;//ftp参数
    private static int QxLogCfg = 0;//服务器下发的配置,默认使用配置0
    private static boolean mQxLogAppStatus = false;//保存当前按键状态,用于处理服务器上传log后同步
    public final static int QXDM_SERVER_CMD = 0;//界面下发命令
    public final static int QXDM_APP_CMD = 3;//界面下发命令

    private QxdmLogSave(){
        //mPId = Thread.currentThread().getId();
        logd( "QxdmLogSave threadid:" + Thread.currentThread().getId());
    }

    public static QxdmLogSave getInstance(){
        if(QxdmLogIns == null){
            QxdmLogIns = new QxdmLogSave();
        }
        return QxdmLogIns;
    }

    public void setQxLogArgs(UpQxLogArgs upArg){
        if(QxLogArgs == null){
            QxLogArgs = new UpQxLogArgs();
        }

        QxLogArgs = upArg;
    }

    public void controlStart() {
        if (mLogControl == null) {
            mLogControl = new LogControl();
        } else {
            mLogControl = null;
            mLogControl = new LogControl();
        }
        mRunning = false;
        mLogControl.start();
    }

    public void fetchStart() {
        if (mLogFetch == null) {
            mLogFetch = new LogFetch();
        } else {
            mLogFetch = null;
            mLogFetch = new LogFetch();
        }
        mFetchFlag = true;
        mLogFetch.start();
    }

    public void startQxLogs(int upCfg,int upInt) {
        Message mCmd;

        if(!ServiceManager.systemApi.getModemLogEnable()){
            return;
        }

        if(upCfg == QXDM_APP_CMD){
            mQxLogAppStatus = true;//界面下发打开命令,保存开关状态
            if(mQxLogServerStatus){//服务器已下发打开则延时等上传完日志后再打开
                logd( "startLogs delay for s2c cmd");
                return;
            }
        }else if(upCfg == QXDM_SERVER_CMD){
            QxLogCfg = upInt;//服务器下发的配置
            if(mQxLogAppStatus || mQxLogServerStatus){
                //服务器已下发打开或者当前已通过按键打开则需先关闭
                mCmd = mLogControl.mHandler.obtainMessage(QXDM_CLOSE_CMD);
                mLogControl.mHandler.sendMessage(mCmd);
            }
            mQxLogServerStatus = true;
        }else{
            logd( "startQxLogs unkown cmd");
            return;
        }

        logd( "startQxLogs:" + QxLogCfg);
        mCmd = mLogControl.mHandler.obtainMessage(QXDM_OPEN_CMD);
        mLogControl.mHandler.sendMessage(mCmd);
    }

    public void stopQxLogs(int upCfg) {
        Message mCmd;

        if(!ServiceManager.systemApi.getModemLogEnable()){
            return;
        }

        if(upCfg == QXDM_SERVER_CMD){
            mQxLogServerStatus = false;//服务器上传命令结束(异步上传中,但可操作文件)
            if(mQxLogAppStatus){
                //当服务器下发的关闭命令时，按键处于开状态，则不关闭log打印
                logd( "stopQxLogs ignore");
                return;
            }
        }else if(upCfg == QXDM_APP_CMD){
            //界面按键下发的关闭命令
            mQxLogAppStatus = false;
            if(mQxLogServerStatus){
                //处于服务器打开log打印时,不关闭log打印
                logd( "stopQxLogs delay for s2c cmd");
                return;
            }
        }else{
            logd( "stopQxLogs unkown cmd");
            return;
        }

        logd( "stopQxLogs");
        mCmd = mLogControl.mHandler.obtainMessage(QXDM_CLOSE_CMD);
        mLogControl.mHandler.sendMessage(mCmd);
    }

    public void uploadQxLogs() {
        Message mCmd;

        logd( "uploadQxLogs");
        mCmd = mLogControl.mHandler.obtainMessage(QXDM_UPLOAD_CMD);
        mLogControl.mHandler.sendMessage(mCmd);
    }

    public void uploadQxLogs2() {
        Message mCmd;

        logd( "uploadQxLogs2");
        mCmd = mLogControl.mHandler.obtainMessage(QXDM_UPLOAD2_CMD);
        mLogControl.mHandler.sendMessage(mCmd);
    }

    public void cleanQxdmlogzipCmd(){
        Message mCmd;

        mCmd = mLogControl.mHandler.obtainMessage(QXDM_CLEAN_ZIP_CMD);
        mLogControl.mHandler.sendMessage(mCmd);
    }

    private class LogControl extends Thread {
        private Process QxdmProc;
        private int cleanSleepTime = 5;//线程间隔处理时间单位s
        public final static String QXDM_FILE_TAIL = ".qmdl";
        public final static String QXDM_ZIP_FILE_TAIL = ".z";
        private final int UPLOAD_QXDMLOG_LIMIT = 1500 * 1024 * 1024;
        private Looper mLooper;
        myHandler mHandler = null;

        public LogControl() {
            //mPID = 0;
        }
        private Looper getLooper() {
            return mLooper;
        }

        private boolean checkQxdmlogDir() {
            File QxdmDirectory = new File(QXDM_DIR_PATH);
            File[] entries = QxdmDirectory.listFiles();
            if(entries != null){
                logd( QXDM_DIR_PATH + " exist");
                return true;
            }else{
                logd( QXDM_DIR_PATH + " don't exist");
                return false;
            }
        }
        /**
         * 保留一个log文件其他进行压缩
         */
        private void zipExLogFile() {
            int count = 0;
            List<String> fileList = new ArrayList<String>();
            String fileName;
            File fileDelete;

            try{
                File QxdmDirectory = new File(QXDM_DIR_PATH);
                File[] entries = QxdmDirectory.listFiles();
                if(entries != null){
                    for (int i = 0; i < entries.length; i++) {
                        fileName = entries[i].getName();
                        if(fileName.startsWith("diag_log")){
                            count++;
                        }
                    }
                }

                if (count > 1) {//保持一个其他压缩
                    for (int i = 0; i < entries.length; i++) {
                        fileName = entries[i].getName();
                        if(fileName.startsWith("diag_log")){
                            fileName = fileName.substring(0, fileName.lastIndexOf("."));
                            fileList.add(fileName);
                        }
                    }
                    Collections.sort(fileList);
                    for (int j = 0; j < fileList.size(); j++) {
                        if (j < (fileList.size() - 1)) {
                            String ziptemp = QXDM_DIR_PATH + "z" + fileList.get(j) + QXDM_ZIP_FILE_TAIL;
                            String temp = QXDM_DIR_PATH + fileList.get(j) + QXDM_FILE_TAIL;
                            ZipUtils.zip(temp, ziptemp);//压缩
                            fileDelete = new File(temp);//删除
                            logd( "fileName　delete: " + temp);
                            fileDelete.delete();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /**
         * 清除QXDMLOG文件夹
         */
        private void cleanQxdmlogDir() {
            Process proc = null;
            List<String> commandList = new ArrayList<>();
            commandList.add("rm");
            commandList.add("-r");
            commandList.add(QXDM_DIR_PATH);

            try {
                proc = Runtime.getRuntime().exec(commandList.toArray(new String[commandList.size()]));
                logd( "delete " + QXDM_DIR_PATH);
                proc.waitFor();

            } catch (Exception e) {
                logd( "cleanQxdmlogDir failed", e);
            } finally {
                try {
                    if (proc != null) {
                        proc.exitValue();
                    }
                } catch (IllegalThreadStateException e) {
                    proc.destroy();
                    logd( "cleanQxdmlogDir failed", e);
                }catch (Exception e) {
                    logd( "cleanQxdmlogDir failed", e);
                }
            }
        }
        /**
         * 清除QXDMLOG文件夹
         */
        private void cleanQxdmlogTempDir() {
            Process proc = null;
            List<String> commandList = new ArrayList<>();
            commandList.add("rm");
            commandList.add("-r");
            commandList.add(QXDM_DIR_PATH_TEMP);

            try {
                proc = Runtime.getRuntime().exec(commandList.toArray(new String[commandList.size()]));
                logd( "delete " + QXDM_DIR_PATH + "temp/");
                proc.waitFor();

            } catch (Exception e) {
                logd( "cleanQxdmlogTempDir failed", e);
            } finally {
                try {
                    if (proc != null) {
                        proc.exitValue();
                    }
                } catch (IllegalThreadStateException e) {
                    proc.destroy();
                    logd( "cleanQxdmlogTempDir failed", e);
                }catch (Exception e) {
                    logd( "cleanQxdmlogTempDir failed", e);
                }
            }
        }
        /**
         * 清除QXDMLOG里面的每个子压缩包
         */
        private void cleanQxdmlogzip() {
            File ZipDirectory = new File(QXDM_DIR_PATH);
            File[] entries = ZipDirectory.listFiles();
            String fileName;
            String deleteFileName;
            File fileDelete;

            if(mQxLogServerStatus){
                logd( "cleanQxdmlogzip ignore for in server status");
                return;
            }
            if(entries == null){
                logd( "cleanQxdmlogzip ignore for no zip");
                return;
            }
            try {
                for (int i = 0; i < entries.length; i++) {
                    fileName = entries[i].getName();
                    if(fileName.startsWith("zdiag_log")){
                        deleteFileName = QXDM_DIR_PATH + fileName;
                        logd( "QXDM　delete: " + deleteFileName);
                        fileDelete = new File(deleteFileName);
                        fileDelete.delete();
                    }
                }
            }catch (Exception e) {
                logd( "cleanQxdmlogzip failed", e);
            }
        }
        /**
         * 每次打开清除xml文件
         */
        private void cleanQxdmlogxml() {
            File ZipDirectory = new File(QXDM_DIR_PATH);
            File[] entries = ZipDirectory.listFiles();
            String fileName;
            String deleteFileName;
            File fileDelete;

            if(entries == null){
                logd( "cleanQxdmlogxml ignore for no xml");
                return;
            }
            try {
                for (int i = 0; i < entries.length; i++) {
                    fileName = entries[i].getName();
                    if(fileName.startsWith("diag_qsr")){
                        deleteFileName = QXDM_DIR_PATH + fileName;
                        logd( "QXDM　delete: " + deleteFileName);
                        fileDelete = new File(deleteFileName);
                        fileDelete.delete();
                    }
                }
            }catch (Exception e) {
                logd( "cleanQxdmlogxml failed", e);
            }
        }
        /**
         * 清除上传给服务器的QXDM压缩包
         */
        private void cleanQxdmZip() {
            File ZipDirectory = new File("/sdcard/");
            File[] entries = ZipDirectory.listFiles();
            String fileName;
            String deleteFileName;
            File fileDelete;

            if(entries == null){
                logd( "cleanQxdmZip ignore for no zip");
                return;
            }
            try {
                for (int i = 0; i < entries.length; i++) {
                    fileName = entries[i].getName();
                    if(fileName.startsWith("QXDM")){
                        deleteFileName = "/sdcard/" + fileName;
                        logd( "QXDM　delete: " + deleteFileName);
                        fileDelete = new File(deleteFileName);
                        fileDelete.delete();
                    }
                }
            }catch (Exception e) {
                logd( "cleanQxdmZip failed", e);
            }
        }

        /**
         * 关闭diag_mdlog命令
         */
        private void closeQxdmlogCmd() {
            Process proc = null;
            List<String> commandList = new ArrayList<>();
            commandList.add("diag_mdlog");
            commandList.add("-k");
            int tryCount = 0;

            if(!ServiceManager.systemApi.getModemLogEnable()){
                return;
            }

            try {
                Thread.sleep(300);//确保Fetch线程命令已发出，快速开关时触发
                proc = Runtime.getRuntime().exec(commandList.toArray(new String[commandList.size()]));

                StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "Error");
                StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "Output");
                errorGobbler.start();
                outputGobbler.start();

                logd( " close diag_mdlog cmd");
                proc.waitFor();
                while(mFetchFlag) {//等待FETCHlog线程退出
                    if(tryCount++ > 30 ){//最大等待时间
                        break;
                    }

                    Thread.sleep(closeSleepTime);
                }
                logd( " close diag_mdlog cmd end");
            } catch (Exception e) {
                logd( "closeQxdmlogCmd failed", e);
            } finally {
                try {
                    if (proc != null) {
                        proc.exitValue();
                    }
                } catch (IllegalThreadStateException e) {
                    proc.destroy();
                    logd( "closeQxdmlogCmd failed", e);
                }catch (Exception e) {
                    logd( "closeQxdmlogCmd failed", e);
                }
            }
        }
        /**
         * 删除多余的日志文件
         */
        private void delectExLogFile() {
            int count = 0;
            List<String> fileList = new ArrayList<String>();
            String fileName;
            File fileDelete;

            try{
                File QxdmDirectory = new File(QXDM_DIR_PATH);
                File[] entries = QxdmDirectory.listFiles();
                if(entries != null){
                    for (int i = 0; i < entries.length; i++) {
                        fileName = entries[i].getName();
                        if(fileName.startsWith("zdiag_log")){
                            count++;
                        }
                    }
                }

                if (count > COUNT_OF_QXDMLOG) {//保持COUNT_OF_QXDMLOG个压缩包
                    for (int i = 0; i < entries.length; i++) {
                        fileName = entries[i].getName();
                        if(fileName.startsWith("zdiag_log")){
                            fileName = fileName.substring(0, fileName.lastIndexOf("."));
                            fileList.add(fileName);
                        }
                    }
                    Collections.sort(fileList);
                    for (int j = 0; j < fileList.size(); j++) {
                        if (j < (fileList.size() - COUNT_OF_QXDMLOG)) {
                            String temp = QXDM_DIR_PATH + fileList.get(j) + QXDM_ZIP_FILE_TAIL;
                            logd( "fileName　delete: " + temp);
                            fileDelete = new File(temp);
                            fileDelete.delete();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        private void qxLogUpload () {
            String qxdmAllZipName;//压缩包名,包含路径
            String qxdmZipName;//压缩包名

            logd( "zip " + QXDM_DIR_PATH);
            qxdmZipName = "QXDM_" + FilterLogs.getDstLogName();
            qxdmAllZipName = "/sdcard/" + qxdmZipName;
            //cleanQxdmZip();//压缩前清楚,确保存储空间
            ZipUtils.zipDir(QXDM_DIR_PATH, qxdmAllZipName);

            try {
                logd( "s2c_cmd uploadqxlog path: " + qxdmAllZipName);
                if (FilterLogs.FileSize(qxdmAllZipName) < UPLOAD_QXDMLOG_LIMIT) {
                    logd( "s2c_cmd uploadqxlog upload");
                    if(FilterLogs.doQxUpload(qxdmAllZipName, qxdmZipName, QxLogArgs)){
                        logd( "doQxUpload success");
                        cleanQxdmlogDir();
                    }else{
                        logd( "doQxUpload fail");
                    }
                }
                logd( "LogUpload thread OK");
            }catch (Exception e) {
                logd( "LogUpload thread", e);
            }
            cleanQxdmZip();
        }
        private void qxLogUpload2 () {
            String qxdmAllZipName;//压缩包名,包含路径
            String qxdmZipName;//压缩包名
            String qxdmTempDir;
            String fileName;
            File QxdmDirectory = new File(QXDM_DIR_PATH);
            File[] entries = QxdmDirectory.listFiles();
            Date dfName;
            String fileNameNoSuffix;
            String fileNameHead = "zdiag_log_";

            if(QxLogArgs == null){
                logd( "QxLogArgs null");
                CmdPerform.INSTANCE.sendUploadTips(CmdPerform.UPLOAD_QXDM_LOG_FAILED);
                return;
            }
            try {
                qxdmTempDir = QXDM_DIR_PATH + "temp/";
                File extLibDir = new File(qxdmTempDir);
                if (!extLibDir.exists()) {
                    extLibDir.mkdirs();
                }

                logd( "qxLogUpload2 startDate: " + QxLogArgs.getStartTime() + "endData: " + QxLogArgs.getEndTime());
                Date startDate = TimeConver.converGMTStr2LocalDate(QxLogArgs.getStartTime());
                Date endDate = TimeConver.converGMTStr2LocalDate(QxLogArgs.getEndTime());

                //拷贝文件到temp
                if(FilterLogs.isDateAvailable(startDate, endDate)){
                    if(entries != null){
                        for (int i = 0; i < entries.length; i++) {
                            fileName = entries[i].getName();
                            if(fileName.startsWith("zdiag")){
                                fileNameNoSuffix = fileName.substring(fileNameHead.length(), fileName.lastIndexOf("."));
                                dfName = TimeConver.converString2Time(fileNameNoSuffix, TimeConver.Dataformat2);
                                if (FilterLogs.isDateAvailable(startDate, dfName) && FilterLogs.isDateAvailable(dfName, endDate)) {
                                    FilterLogs.CopySdcardFile(QXDM_DIR_PATH + fileName, qxdmTempDir + fileName);
                                }
                            }else if(fileName.startsWith("diag_") || fileName.endsWith(".qdb")){
                                FilterLogs.CopySdcardFile(QXDM_DIR_PATH + fileName, qxdmTempDir + fileName);
                            }
                        }
                    }
                }

                qxdmZipName = "QXDM_" + FilterLogs.getDstLogName();
                qxdmAllZipName = "/sdcard/" + qxdmZipName;

                ZipUtils.zipDir(qxdmTempDir, qxdmAllZipName);
                CmdPerform.INSTANCE.sendUploadTips(CmdPerform.COMPRESS_QXDM_LOG_COMPLETE);
                logd( "s2c_cmd uploadqxlog2 path: " + qxdmAllZipName);
                if (FilterLogs.FileSize(qxdmAllZipName) < UPLOAD_QXDMLOG_LIMIT) {
                    logd( "s2c_cmd uploadqxlog2 upload");
                    CmdPerform.INSTANCE.sendUploadTips(CmdPerform.UPLOAD_QXDM_LOG_START);
                    if(FilterLogs.doQxUpload(qxdmAllZipName, qxdmZipName, QxLogArgs)){
                        CmdPerform.INSTANCE.sendUploadTips(CmdPerform.UPLOAD_QXDM_LOG_SUCCEED);
                        logd( "doQxUpload success");
                    }else{
                        CmdPerform.INSTANCE.sendUploadTips(CmdPerform.UPLOAD_QXDM_LOG_FAILED);
                        logd( "doQxUpload fail");
                    }
                }else {
                    CmdPerform.INSTANCE.sendUploadTips(CmdPerform.UPLOAD_QXDM_LOG_FAILED);
                }
                logd( "LogUpload thread OK");
            }catch (Exception e) {
                logd( "LogUpload thread", e);
            }
            cleanQxdmlogTempDir();
            cleanQxdmZip();
        }
        private class myHandler extends Handler{
            public myHandler(Looper looper){
                super (looper);
            }
            public void handleMessage (Message msg) {
                Message mCmd;
                int tryCount = 0;

                //logd( "handleMessage() id:" + Thread.currentThread().getId());
                switch (msg.what){
                    case QXDM_OPEN_CMD:
                        logd( "rcv QXDM_OPEN_CMD");
                        if(!mRunning){
                            mRunning = true;
                            //cleanQxdmlogDir();//打开不清除
                            cleanQxdmlogxml();
                            fetchStart();
                            mCmd = mHandler.obtainMessage(QXDM_CLEAN_EXT_CMD);
                            mHandler.sendMessageDelayed(mCmd, TimeUnit.SECONDS.toMillis(cleanSleepTime));
                        }
                        break;
                    case QXDM_CLOSE_CMD:
                        logd( "rcv QXDM_CLOSE_CMD");
                        if(mRunning){
                            mRunning = false;
                            closeQxdmlogCmd();
                        }
                        break;
                    case QXDM_CLEAN_EXT_CMD:
                        //logd( "rcv QXDM_CLEAN_EXT_CMD");
                        if(mRunning) {
                            zipExLogFile();
                            delectExLogFile();
                            mCmd = mHandler.obtainMessage(QXDM_CLEAN_EXT_CMD);
                            mHandler.sendMessageDelayed(mCmd, TimeUnit.SECONDS.toMillis(cleanSleepTime));
                        }
                        break;
                    case QXDM_UPLOAD_CMD:
                        logd( "rcv QXDM_UPLOAD_CMD");
                        if (!mUploading && checkQxdmlogDir()) {
                            mUploading = true;
                            if(mRunning){
                                closeQxdmlogCmd();
                            }
                            qxLogUpload();
                            if(mRunning){
                                fetchStart();
                            }
                            mUploading = false;
                        }else{
                            logd( "QXDM_UPLOAD_CMD ignore");
                        }
                        break;
                    case QXDM_UPLOAD2_CMD:
                        logd( "rcv QXDM_UPLOAD2_CMD");
                        if (checkQxdmlogDir()) {
                            if(mRunning){
                                closeQxdmlogCmd();
                            }
                            CmdPerform.INSTANCE.sendUploadTips(CmdPerform.COMPRESS_QXDM_LOG_START);
                            qxLogUpload2();
                            if(mRunning){
                                fetchStart();
                            }
                        }else{
                            CmdPerform.INSTANCE.sendUploadTips(CmdPerform.UPLOAD_QXDM_LOG_FAILED);
                            logd( "QXDM_UPLOAD2_CMD ignore");
                        }
                        break;
                    case QXDM_CLEAN_ZIP_CMD:
                        logd( "rcv QXDM_CLEAN_ZIP_CMD");
                        cleanQxdmlogzip();
                        break;
                    default:
                        logd( "rcv unknow cmd:" + msg.what);
                        break;
                }
            }
        };

        public void run(){
            logd( "QxdmLogSave thread:" + Thread.currentThread().getId());
            try{
                Looper.prepare();
                mLooper = Looper.myLooper();
                mHandler = new myHandler(mLooper);
                closeQxdmlogCmd();//开启前确认关闭qxdm命令
                Looper.loop();
            }catch (Exception e) {
                logd( "QxdmLogSave thread run", e);
            }finally {
                logd( "QxdmLogSave thread end");
            }

        }
    }
    private class LogFetch extends Thread {

        public void run(){
            Process proc = null;
            List<String> commandList = new ArrayList<>();

            commandList.add("diag_mdlog");
            commandList.add("-e");//加锁，不让休眠
            commandList.add("-f");
            if(QxLogCfg == 0)
            {
                commandList.add(QXDM_CFG_PATH0);
            }else{
                commandList.add(QXDM_CFG_PATH1);
            }
            commandList.add("-o");
            commandList.add(QXDM_DIR_PATH);
            commandList.add("-s");
            commandList.add(MAX_LEN_OF_QXDMLOG);
            commandList.add("-c");
            commandList.add("&");

            try {
                proc = Runtime.getRuntime().exec(commandList.toArray(new String[commandList.size()]));

                StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "Error");
                StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "Output");
                errorGobbler.start();
                outputGobbler.start();

                logd( "diag_mdlog1 fetch waitFor begin");
                proc.waitFor();
                logd( "diag_mdlog fetch waitFor end");
            } catch (Exception e) {
                logd( "diag_mdlog fetch failed", e);
            } finally {
                mFetchFlag = false;
                try {
                    if (proc != null) {
                        proc.exitValue();
                    }
                } catch (IllegalThreadStateException e) {
                    proc.destroy();
                    logd( "diag_mdlog fetch failed", e);
                }catch (Exception e) {
                    logd( "diag_mdlog fetch failed", e);
                }
            }
        }
    }

    private class StreamGobbler extends Thread {

        InputStream is;
        String type;

        public StreamGobbler(InputStream is, String type) {
            this.is = is;
            this.type = type;
        }

        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (type.equals("Error")) {
                        logd( "id[" + Thread.currentThread().getId() +"] Error :"+ line);
                    } else {
                        logd( "id[" + Thread.currentThread().getId() +"] Debug :"+ line);
                    }
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            if (type.equals("Error")) {
                logd( "id[" + Thread.currentThread().getId() +"] Error end");
            } else {
                logd( "id[" + Thread.currentThread().getId() +"] Debug end");
            }
        }
    }
}
