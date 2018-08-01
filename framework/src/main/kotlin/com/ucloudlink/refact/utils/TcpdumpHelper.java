package com.ucloudlink.refact.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.log.TimeConver;
import com.ucloudlink.refact.business.log.ZipUtils;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.business.routetable.ServerRouter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 * Created by shiqianhua on 2017/4/11.
 */

public class TcpdumpHelper {
    private static final String TAG = "TcpdumpHelper";
    //private ArrayList<String> cmdList;
    private DateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static String TCPDUMP_DIR = "/sdcard/uaflogs/tcpdump";
    private static String TCPDUMP_PACK_DIR = "/sdcard/uaflogs/tcpdump_pack";
    private static String TCPDUMP_CMD = "/system/xbin/tcpdump";
    private Process tcpdumpProc;
    private String intf;
    private volatile boolean mRunning = false;
    private BufferedReader mReader = null;
    private RealTcpdumper realTcpdumper = null;

    private boolean isBusinessRunning = false;

    private static final int START_EV = 1;
    private static final int STOP_EV = 2;
    private static final int CLEAN_LOG_EV = 3;
    private static final int REUP_EV = 4;

    private Handler mhandler;
    private TcpdumperMessage msgThread;
    private String curFileName = null;
    private static final  int MAX_FILE_COUNT = 100;
    private static final int ZIP_THREAHOD = 10;
    private static final String TAR_FILE_HEAD = "tcpdump-";


    public TcpdumpHelper(){
        JLog.logd(TAG, "TcpdumpHelper: start instance");
        if(!ServiceManager.systemApi.getTcpdumpEnable()){
            JLog.loge(TAG, "TcpdumpHelper: system not support!");
            return;
        }

        initDir();
        msgThread = new TcpdumperMessage();
        msgThread.start();
    }

    private static class LazyHolder {
        private static final TcpdumpHelper INSTANCE = new TcpdumpHelper();
    }

    public static final TcpdumpHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    synchronized private static void initDir(){
        File filedir = new File(TCPDUMP_DIR);
        if(!(filedir.exists() && filedir.isDirectory())){
            boolean ismkok = filedir.mkdirs();
            JLog.logd(TAG, "initDir: " + TCPDUMP_DIR + " mkdir return " + ismkok);
        }

        File packDir = new File(TCPDUMP_PACK_DIR);
        if(!(packDir.exists() && packDir.isDirectory())){
            boolean ismkok = packDir.mkdir();
            JLog.logd(TAG, "initDir: " + TCPDUMP_PACK_DIR + " mkdir return " + ismkok);
        }
    }

    private class TcpdumperMessage extends Thread{
        public TcpdumperMessage(){
            super("tcpdump");
        }

        @Override
        public void run() {
            Looper.prepare();
            mhandler = new Handler(Looper.myLooper()){
                @Override
                public void handleMessage(Message msg) {
                    JLog.logd(TAG, "handleMessage: get message:" + msg.what);
                    switch (msg.what){
                        case START_EV:
                            removeMessages(REUP_EV);
                            startAction();
                            break;
                        case STOP_EV:
                            removeMessages(REUP_EV);
                            stopAction();
                            break;
                        case CLEAN_LOG_EV:
                            clearAction();
                            break;
                        case REUP_EV:
                            removeMessages(REUP_EV);
                            restartAction();
                            break;
                        default:
                            break;
                    }
                }
            };
            mhandler.sendEmptyMessageDelayed(CLEAN_LOG_EV, TimeUnit.MINUTES.toMillis(5));
            Looper.loop();
        }
    }



    private class RealTcpdumper extends Thread{
        public boolean isOver = false;
        public RealTcpdumper(){
            super("real-tcpdumper");
        }
        @Override
        public void run(){
            JLog.logd(TAG, "startTcpdump: ");
            if(!Configuration.INSTANCE.getTcpdumpEnable()){
                JLog.logd(TAG, "run: break not enabled," + Configuration.INSTANCE.getTcpdumpEnable()) ;
                return;
            }

            InetSocketAddress addr = ServerRouter.INSTANCE.getIpAddrMap().get(ServerRouter.Dest.ASS);
            String ipstr = addr.getAddress().getHostAddress();
            String time = formatter.format(new Date());
            String filename = "tcpdump-" + time +".pcap";
            JLog.logd(TAG, "run: dump filename:" + filename);
            String cmd = TCPDUMP_CMD
                    + " " + "-i any"
                    + " " + "-w"
                    + " " + TCPDUMP_DIR+"/"+filename
                    + " " + "host"
                    + " " + ipstr;
            JLog.logd(TAG, "run: cmd:" + cmd);
            JLog.logd(TAG, "run: start:" + formatter.format(new Date()));
            try {
//            tcpdumpProc = Runtime.getRuntime().exec("su");
//            Thread.sleep(1000);
//            OutputStream os = tcpdumpProc.getOutputStream();
//            os.write(cmd.getBytes());
//            os.flush();
//            tcpdumpProc.waitFor();
                curFileName = filename;
                tcpdumpProc = Runtime.getRuntime().exec(cmd);
                mReader = new BufferedReader(new InputStreamReader(
                        tcpdumpProc.getErrorStream()), 1024);
                String line;
                while ((line = mReader.readLine()) != null) {
                    JLog.logd(TAG, "run: err:" + line);
                }
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                JLog.logd(TAG, "startAction: destroy tcpdumpProc");
                if(tcpdumpProc != null) {
                    tcpdumpProc.destroy();
                    tcpdumpProc = null;
                }
            }
            curFileName = null;
            isOver = true;
            mhandler.sendEmptyMessageDelayed(REUP_EV, TimeUnit.MINUTES.toMillis(1));
            JLog.logd(TAG, "run: end:" + formatter.format(new Date()));
        }
    }

    private void startTcpdumpReal(){
        if(realTcpdumper == null || realTcpdumper.isOver){
            JLog.logd(TAG, "startTcpdumpReal: start a new tcpdumper!");
            realTcpdumper = new RealTcpdumper();
            realTcpdumper.start();
        }
    }

    private void startAction(){
        JLog.logd(TAG, "startAction: start action!");

        if(!Configuration.INSTANCE.getTcpdumpEnable()){
            JLog.loge(TAG, "startAction: tcpdumper not enabled!");
            return;
        }

        if(mRunning){
            JLog.logd(TAG, "startAction: is already running!");
        }

        mRunning = true;

        startTcpdumpReal();
        JLog.logd(TAG, "startAction: runnning over!");
    }

    private void restartAction(){
        JLog.logd(TAG, "restartAction: " + mRunning + " " + Configuration.INSTANCE.getTcpdumpEnable());
        if(mRunning && Configuration.INSTANCE.getTcpdumpEnable()){
            startTcpdumpReal();
        }
    }

    private void stopAction(){
        JLog.logd(TAG, "stopAction:  stop action");
        mRunning = false;
        try {
            Thread.sleep(500);
            if (realTcpdumper != null && realTcpdumper.isAlive()) {
                JLog.logd(TAG, "stopAction: tcpdumpProc " + tcpdumpProc);
                if(tcpdumpProc != null){
                    tcpdumpProc.destroy();
                    tcpdumpProc = null;
                }

                realTcpdumper.destroy();
                //realTcpdumper.interrupt();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void clearAction(){
        if(mhandler != null){
            mhandler.sendEmptyMessageDelayed(CLEAN_LOG_EV, TimeUnit.HOURS.toMillis(12));
        }

        packageDumpFiles();
        cleanPackFiles();
    }

    private void packageDumpFiles(){
        int count = 0;
        // package and clean
        ArrayList<String> fileList = new ArrayList<>();
        File dir = new File(TCPDUMP_DIR);
        File[] entries = dir.listFiles();
        if(entries.length > ZIP_THREAHOD){
            JLog.logd(TAG, "packageDumpFiles: start to package!");

            for(int i= 0; i < entries.length; i++ ){
//                JLog.logd(TAG, "packageDumpFiles: filename " + i + " "+ entries[i].getName() + " curent:" + curFileName);
                if(curFileName != null && entries[i].getName().equals(curFileName)){
                    continue;
                }
                //JLog.logd(TAG, "packageDumpFiles: " + entries[i].getName().substring(entries[i].getName().lastIndexOf(".")));
                if(entries[i].getName().substring(entries[i].getName().lastIndexOf("")).equals(".pcap")){
                    fileList.add(TCPDUMP_DIR + File.separator + entries[i].getName());
                }
            }
            JLog.logd(TAG, "packageDumpFiles: file count:" + fileList.size());
            if(fileList.size() > 0) {
                String dstName = TCPDUMP_PACK_DIR + File.separator + TAR_FILE_HEAD +TimeConver.getDateNow() + ".zip";
                JLog.logd(TAG, "packageDumpFiles: dest name:" + dstName);
                try {
                    ZipUtils.zipFiles(fileList, dstName,true);
                }catch (IOException e){
                    e.printStackTrace();
                }

            }
        }
    }

    private void cleanPackFiles(){
        ArrayList<Date> fileList = new ArrayList<>();
        String filename;
        Date dname;
        File delFile;

        try {
            File fileDir = new File(TCPDUMP_PACK_DIR);
            if(fileDir.isDirectory()){
                File[] entries = fileDir.listFiles();
                for(int i = 0; i < entries.length; i++){
                    filename = entries[i].getName();
                    filename = filename.substring(TAR_FILE_HEAD.length(), filename.lastIndexOf(""));
                    dname = TimeConver.converString2Time(filename, TimeConver.DataformatSec);
                    fileList.add(dname);
                }
            }

            Collections.sort(fileList);
            JLog.logd(TAG, "cleanPackFiles: after sort:" + fileList);
            if(fileList.size() > MAX_FILE_COUNT) {
                for (int j = 0; j < fileList.size(); j++) {
                    if (j < (fileList.size() - MAX_FILE_COUNT)) {
                        String tmp = TCPDUMP_PACK_DIR + File.separator + TAR_FILE_HEAD
                                + TimeConver.getStrFromDate(fileList.get(j), TimeConver.DataformatSec) + ".zip";
                        delFile = new File(tmp);
                        JLog.logd(TAG, "cleanPackFiles: del file:" + tmp);
                        delFile.delete();
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        } finally {

        }
    }


    private void startTcpdump(){
        JLog.logd(TAG, "startTcpdump: start ---------" );
        if(mhandler != null) {
            mhandler.sendEmptyMessage(START_EV);
        }else {
            JLog.loge(TAG, "startTcpdump: mhandler is null");
            JLog.logd(TAG, "startTcpdump: delay 5s send this request");
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                        if(mhandler != null) {
                            mhandler.sendEmptyMessage(START_EV);
                        }else {
                            JLog.loge(TAG, "run: mhandler still null");
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }


                }
            });
            thread.start();
        }

    }

    private void stopTcpdump(){
        JLog.logd(TAG, "stopThread: stop -----------------" );
        if(mhandler != null) {
            mhandler.sendEmptyMessage(STOP_EV);
        }else {
            JLog.loge(TAG, "startTcpdump: mhandler is null");
        }
    }


    public void setBusinessRunning(boolean running){
        if(running != isBusinessRunning) {
            JLog.logd(TAG, "setBusinessRunning: isBusinessRunning change" + isBusinessRunning + " -> " + running);
            isBusinessRunning = running;
            if(running){
                startTcpdump();
            }else {
                stopTcpdump();
            }
        }
    }

    public void sendStartTcpdump(boolean start){
        JLog.logd(TAG, "sendStartTcpdump: " + isBusinessRunning + " " + start);
        if(isBusinessRunning){
            if(start){
                startTcpdump();
            }else{
                stopTcpdump();
            }
        }
    }

    public void sendPackageFiles(){
        mhandler.sendEmptyMessage(CLEAN_LOG_EV);
    }

}
