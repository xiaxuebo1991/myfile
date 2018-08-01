package com.ucloudlink.refact.business.log.logcat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.ucloudlink.refact.business.log.TimeConver;
import com.ucloudlink.refact.business.log.ZipUtils;
import com.ucloudlink.framework.BuildConfig;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.Rc4;
import com.ucloudlink.refact.business.log.FilterLogs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * 使用方法：
 * 1、设置保存日志文件的相对路径{@link #SAVE_PATH},默认是1、存sdcard，2、如果没有sdcard就存在应用路径下面。
 * 2、设置保存最大日志文件的大小{@link #LENTH_OF_LOGFILES}。
 * 3、开始保存日志文件只需执行以下操作，建议在Application里面开启。
 * LogcatHelper.getInstance(Context).start();
 * {@link LogcatHelper#getInstance(Context)#start();}
 * <p>
 * 功能：保存当前进程的日志，日志文件保存路径优先选择sdcard下面的SAVE_PATH，
 * 如果没有sdcard路径则保存到应用安装目录。目标文件：UAF_logcat.log。
 */
public class LogcatHelper implements Thread.UncaughtExceptionHandler {

    private static       String LOG_TAG           = "UAF_SaveLog";
    private final static String LOG_FILE_NAME     = "UAF_logcat.log";
    public final static String LOG_CACHE_PATH    = "TEMP";
    public final static String LOG_CACHE_UP_PATH = "TEMPUP";
    public static final String UNZIP = "unzip";
    public final static String LOG_SUFFIX = ".log";

    private static LogcatHelper INSTANCE   = null;
    private        LogDumper    mLogDumper = null;

    public static        String COMPLETE_TEMPPATH_LOGCAT;
    public static String COMPLETE_TEMPPATH_UP_LOGCAT;
    public static String SUFFIX_OF_CRASH_FILE = "crashLog";

    //设置保存单个日志的大小，单位Byte
    private static final int LENTH_OF_LOGFILES = 20 * 1024 * 1024;
    private final static int COUNT_OF_LOG      = 80;//保存压缩日志个数
    public static String COMPLETE_PATH_LOGCAT;
    public static final String SAVE_PATH = "uaflogs";
    Context mContext;
    // 系统默认的UncaughtException处理类
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    // 用来存储设备信息和异常信息
    private Map<String, String> infos     = new HashMap<>();
    // 用于格式化日期,作为日志文件名的一部分
    private DateFormat          formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    /**
     * 初始化目录
     */
    public void init(Context context) {
        mContext = context;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {// 优先保存到SD卡中
            COMPLETE_PATH_LOGCAT = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + SAVE_PATH;
        } else {// 如果SD卡不存在，就保存到本应用的目录下
            COMPLETE_PATH_LOGCAT = context.getFilesDir().getAbsolutePath() + File.separator + SAVE_PATH;
        }
        //创建3个文件夹，用来存放log和压缩后的log zip包,以及筛选出的要上传的日志
        File file = new File(COMPLETE_PATH_LOGCAT);
        if (!file.exists()) {
            boolean ismkFileok = file.mkdirs();
            Log.d(LOG_TAG, "mkdirs: " + COMPLETE_PATH_LOGCAT + ", result: " + ismkFileok);
        } else {
            if (!file.isDirectory()) {
                file.delete();
                boolean ismkFileok = file.mkdirs();
                Log.d(LOG_TAG, "mkdirs: " + COMPLETE_PATH_LOGCAT + ", result: " + ismkFileok);
            }
        }
        COMPLETE_TEMPPATH_LOGCAT = COMPLETE_PATH_LOGCAT + File.separator + LOG_CACHE_PATH;
        File fileTempDir = new File(COMPLETE_TEMPPATH_LOGCAT);
        if (!fileTempDir.exists()) {
            boolean ismkok = fileTempDir.mkdirs();
            Log.d(LOG_TAG, "mkdirs: " + COMPLETE_TEMPPATH_LOGCAT + ", result: " + ismkok);
        } else {
            if (!fileTempDir.isDirectory()) {
                fileTempDir.delete();
                boolean ismkFileok = file.mkdirs();
                Log.d(LOG_TAG, "mkdirs: " + COMPLETE_TEMPPATH_LOGCAT + ", result: " + ismkFileok);
            }
        }
        COMPLETE_TEMPPATH_UP_LOGCAT = COMPLETE_PATH_LOGCAT + File.separator + LOG_CACHE_UP_PATH;
        File fileTempUpDir = new File(COMPLETE_TEMPPATH_UP_LOGCAT);
        if (!fileTempUpDir.exists()) {
            boolean ismkupok = fileTempUpDir.mkdirs();
            Log.d(LOG_TAG, "mkdirs: " + COMPLETE_TEMPPATH_UP_LOGCAT + ", result: " + ismkupok);
        } else {
            if (!fileTempUpDir.isDirectory()) {
                fileTempUpDir.delete();
                boolean ismkFileok = fileTempUpDir.mkdirs();
                Log.d(LOG_TAG, "mkdirs: " + COMPLETE_TEMPPATH_UP_LOGCAT + ", result: " + ismkFileok);
            }
        }
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        //开始记录日志之前，清除日志缓存
        clearLogCache();
        clearLogRadioCache();
    }

    public static LogcatHelper getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new LogcatHelper(context);
        }
        return INSTANCE;
    }

    private LogcatHelper(Context context) {
        init(context);
        int PId = android.os.Process.myPid();
    }

    public void start() {
        if (mLogDumper == null) {
            mLogDumper = new LogDumper(COMPLETE_PATH_LOGCAT);
        }
        mLogDumper.start();
    }

    public void stop() {
        if (mLogDumper != null) {
            mLogDumper.setRunning(false);
            mLogDumper = null;
        }
    }

    public File getCurrentLogFile(){
        if (mLogDumper!=null) {
            return mLogDumper.getCurrentLogFile();
        }
        return null;
    }
    
    public void packCurrentLog() {
        if (mLogDumper != null) {
            mLogDumper.setNeedUpLoad(true);

        }
    }

    private void logCrashRestart() {
        stop();

        try {
            Thread.sleep(2000);
        }catch (InterruptedException e) {
            Log.e(LOG_TAG, "error : ", e);
        }

        start();
    }

    /**
     * 清除logcat的日志缓存
     */
    private void clearLogCache() {
        Process proc = null;
        List<String> commandList = new ArrayList<>();
        commandList.add("logcat");
        commandList.add("-c");
        try {
            proc = Runtime.getRuntime().exec(commandList.toArray(new String[commandList.size()]));
            if (proc.waitFor() != 0) {
                Log.e(LOG_TAG, " clearLogCache proc.waitFor() != 0");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "clearLogCache failed", e);
        } finally {
            try {
                if (proc != null) {
                    proc.exitValue();
                }
            } catch (IllegalThreadStateException e) {
                proc.destroy();
            } catch (Exception e) {
                Log.e(LOG_TAG, "clearLogCache failed", e);
            }
        }
    }

    /**
     * 清除logcat -b radio的日志缓存
     */
    private void clearLogRadioCache() {
        Process proc = null;
        List<String> commandList = new ArrayList<>();
        commandList.add("logcat");
        commandList.add("-c");
        commandList.add("-b");
        commandList.add("radio");
        try {
            proc = Runtime.getRuntime().exec(commandList.toArray(new String[commandList.size()]));
            if (proc.waitFor() != 0) {
                Log.e(LOG_TAG, " clearLogCache proc.waitFor() != 0");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "clearLogCache failed", e);
        } finally {
            try {
                if (proc != null) {
                    proc.exitValue();
                }
            } catch (IllegalThreadStateException e) {
                proc.destroy();
            } catch (Exception e) {
                Log.e(LOG_TAG, "clearLogCache failed", e);
            }
        }
    }

    /**
     * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
     *
     * @param ex
     * @return true:如果处理了该异常信息;否则返回false.
     */
    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        // 收集设备参数信息
        onCrash();
        collectDeviceInfo(mContext);
        // 保存日志文件
        ex.printStackTrace();
        String fileName = saveCrashInfo2File(ex);
        Log.d(LOG_TAG, "save a crash Log: " + COMPLETE_PATH_LOGCAT + "->" + fileName);
        return true;
    }

    /**
     * 收集设备参数信息
     *
     * @param ctx
     */
    private void collectDeviceInfo(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                String versionCode = pi.versionCode + "";
                infos.put("versionName", versionName);
                infos.put("versionCode", versionCode);
            }
            infos.put("builduser", BuildConfig.USER_NAME);
            infos.put("builddate", BuildConfig.BUILD_TIMESTAMP);
            infos.put("builddev", BuildConfig.DEV_NAME);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "an error occured when collect package info", e);
        }
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                infos.put(field.getName(), field.get(null).toString());
                Log.d(LOG_TAG, field.getName() + " : " + field.get(null));
            } catch (Exception e) {
                Log.e(LOG_TAG, "an error occured when collect crash info", e);
            }
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            // 如果用户没有处理则让系统默认的异常处理器来处理
            Log.d(LOG_TAG, "uncaughtException: handler by system");
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "error : ", e);
            }
            // 退出程序
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    /**
     * 保存错误信息到文件中
     *
     * @param ex
     * @return 返回文件名称, 便于将文件传送到服务器
     */
    private String saveCrashInfo2File(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key).append("=").append(value).append("\n");
        }
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        if (JLog.isEncryptLog){
            result = Rc4.encrypt(result);
        }
        //        L.d(WModel.CrashUpload, result);
        sb.append(result);
        try {
            long timestamp = System.currentTimeMillis();
            String time = formatter.format(new Date());
            String fileName = SUFFIX_OF_CRASH_FILE + "-" + time + "-" + timestamp + LOG_SUFFIX;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                String path = COMPLETE_PATH_LOGCAT;
                File file = new File(path, fileName);
                //                if (!dir.exists()) {
                //                    dir.mkdirs();
                //                }
                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write(sb.toString().getBytes());
                fos.flush();
                fos.close();
            }
            return fileName;
        } catch (Exception e) {
            Log.e(LOG_TAG, "an error occured while writing file...", e);
        }
        return null;
    }

    private class LogDumper extends Thread {
        
        private String LogPath;
        private boolean  mRunning   = true;
        private boolean  needUpLoad = false;
        private String[] saveCmd    = {"logcat", "-b", "main", "-b", "radio", "-v", "threadtime"};
        
        private File currentLogFile = null; 

        LogDumper(String dir) {
            LogPath = dir;
        }

        @Override
        public void run() {
            Log.i(LOG_TAG,"LogDumper run()");
            Process logcatProc = null;
            BufferedReader mReader = null;
            BufferedWriter out = null;
            File outFile = null;
            File pathDir = null;
            
            try {
                /*
                获取之前剩下还没压缩的文件，判定大小，还没到目标大小就继续使用，如果已经到了，就压缩，下次使用新的文件名
                未打包的使用unzip-文件创建时间.txt
                 */
                pathDir = new File(LogPath + File.separator + UNZIP);
                Log.i(LOG_TAG,"pathDir.exists()="+pathDir.exists()+" ,pathDir.isDirectory()="+pathDir.isDirectory());
                if (pathDir.exists() && pathDir.isDirectory()) {
                    File[] files = pathDir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            return filename.contains(UNZIP);
                        }
                    });
                    if (files == null || files.length == 0) {
                        return;
                    }
                    for (int i = 0; i < files.length; i++) {
                        File file = files[i];
                        if(i == 0) {
                            Log.i(LOG_TAG, "unzip log file name=" + file.getName() + " , file.isFile()=" + file.isFile() + " ,file.length()=" + file.length() + " ,LENTH_OF_LOGFILES=" + LENTH_OF_LOGFILES);
                            if (file.isFile()) {
                                if (file.length() >= LENTH_OF_LOGFILES) {
                                    //压缩打包
                                    compressLogFile(file.getAbsolutePath());
                                    boolean result = file.delete();
                                    Log.i(LOG_TAG, "unzip log file delete result=" + result);
                                    logv("compressLogFile :" + file.getAbsolutePath());
                                } else {
                                    //继续使用
                                    outFile = file;
                                }
                            }
                        } else {
                            //理论上只有一个，但是有删除失败的情况（#26021），这里需要删除一下其他log，
                            // 否则可能出现每次压缩的log都是同一个。
                            if(file.exists()) {
                                file.delete();
                            }
                        }
                    }
                }else {
                    pathDir.mkdirs();
                }
                JLog.logi(LOG_TAG,"outFile="+outFile);
                if (outFile == null) {
                    outFile = getNewOutFile();
                }
                JLog.logi(LOG_TAG,"outFile name="+outFile.getName());
                currentLogFile = outFile;
                
                logcatProc = Runtime.getRuntime().exec(saveCmd);
                mReader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), 1024);
                out = new BufferedWriter(new FileWriter(outFile, true));

                String line;
                long fileSize = outFile.length();
                JLog.logi(LOG_TAG,"outFile fileSize="+fileSize);
                while (mRunning && (line = mReader.readLine()) != null) {
                    if (!mRunning) {
                        break;
                    }
                    if (line.length() == 0) {
                        continue;
                    }

                    if (line.contains("tftp_server: pid=") || line.contains("Diag_Lib: Diag_LSM_Msg")) {
                        continue;
                    }

                    if (!pathDir.exists()) {
                        pathDir.mkdirs();
                        fileSize = 0;
                        closeIO(out);
                        outFile= getNewOutFile();
                        currentLogFile = outFile;
                        out = new BufferedWriter(new FileWriter(outFile, true));
                    }
                    
                    String log = line + System.lineSeparator();
                    out.write(log);
                    out.flush();

                    fileSize += log.getBytes().length;

                    if (fileSize >= LENTH_OF_LOGFILES) {
                        //达到上限，关闭就out , 新建out，新建线程压缩文件，filezise归零

                        final File compressLog = outFile;
                        
                        closeIO(out);

                        outFile = getNewOutFile();
                        currentLogFile = outFile;
                        out = new BufferedWriter(new FileWriter(outFile));

                        fileSize = 0;

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                compressLogFile(compressLog.getAbsolutePath());
                               boolean result =  compressLog.delete();
                                JLog.logi(LOG_TAG,"compressLog delete result="+result);
                            }
                        }).start();

                    } else if (needUpLoad) {
                        final String currentLogPath = outFile.getAbsolutePath();
                        needUpLoad = false;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                File file = new File(COMPLETE_TEMPPATH_UP_LOGCAT);
                                if (!file.exists()) {
                                    file.mkdirs();
                                }
                                FilterLogs.CopySdcardFile(currentLogPath, COMPLETE_TEMPPATH_UP_LOGCAT + File.separator + LOG_FILE_NAME);
                            }
                        }).start();

                    }
                }
            } catch (FileNotFoundException e){
                if (!pathDir.exists()) {
                    pathDir.mkdirs();
                }
            }catch (Throwable e) {
                e.printStackTrace();
            } finally {
                //非stop关闭，重新启动打印。包括crash以及(line = mReader.readLine()) == null
                if (mRunning) {
                    logCrashRestart();
                }

                try {
                    if (logcatProc != null) {
                        logcatProc.exitValue();
                    }
                } catch (IllegalThreadStateException e) {
                    logcatProc.destroy();
                }
                closeIO(mReader);
                closeIO(out);
            }
        }

        private File getNewOutFile() {
            return new File(LogPath + File.separator + UNZIP + File.separator + UNZIP + TimeConver.getDateNow() + LOG_SUFFIX);
        }

        /**
         * 压缩日志文件,且删除多余的压缩日志文件，且删除源文件
         * <p>
         * 把日志重命名到打包时间
         * <p>
         * 返回压缩后文件路径
         */
        private String compressLogFile(String filePath) {
            try {
                JLog.logi(LOG_TAG,"compressLogFile");
                File unzipFile = new File(filePath);

                String dateNow = TimeConver.getDateNow();
                File unzipRenameFile = new File(unzipFile.getParent(), dateNow + LOG_SUFFIX);

                boolean ret = unzipFile.renameTo(unzipRenameFile);
                if (!ret) {
                    return null;
                }
                
                String zipFileName  = COMPLETE_TEMPPATH_LOGCAT + File.separator + dateNow + ".zip";

                File tempDirectory = new File(COMPLETE_TEMPPATH_LOGCAT);
                if(!tempDirectory.exists()){
                    tempDirectory.mkdir();
                }

                ZipUtils.zip(unzipRenameFile.getAbsolutePath(), zipFileName);
                if (FilterLogs.countFileOfDir(COMPLETE_TEMPPATH_LOGCAT) > COUNT_OF_LOG) {
                    FilterLogs.deleteSurplusFile(COMPLETE_TEMPPATH_LOGCAT, COUNT_OF_LOG);
                }
                //删除已经压缩的unzip Log
                deleFile(unzipFile);
                deleFile(unzipRenameFile);
                return zipFileName;
            } catch (Exception e) {
                JLog.logi(LOG_TAG,"compressLogFile error = "+e);
                e.printStackTrace();
            }
            return null;
        }

        private void deleFile(File file) {
            JLog.logi(LOG_TAG,"deleFile file name = "+file.getName());
            if (file.exists() && file.isFile()) {
               boolean result =  file.delete();
                JLog.logi(LOG_TAG,"deleFile file result = "+result);
            }
        }

        private void closeIO(Closeable closeable) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public File getCurrentLogFile() {
            return currentLogFile;
        }

        public void setRunning(boolean running) {
            mRunning = running;
        }

        public void setNeedUpLoad(boolean needUpLoad) {
            this.needUpLoad = needUpLoad;
        }
    }

    private void onCrash() {
        Log.d("LogcatHelper", "onCrash: ");
        if (crashListen != null) {
            for (onCrashListener listener : crashListen) {
                listener.onCrash();
            }
        }
    }

    public interface onCrashListener {
        void onCrash();
    }

    private List<onCrashListener> crashListen;

    public void addOnCrashListenr(onCrashListener l) {
        if (crashListen == null) {
            crashListen = new ArrayList<>();
        }
        crashListen.add(l);
    }

    public void removeCrashListen(onCrashListener l) {
        if (crashListen == null) {
            return;
        }
        crashListen.remove(l);
    }
}