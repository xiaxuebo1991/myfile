package com.ucloudlink.refact.utils;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.ucloudlink.refact.ServiceManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by jiaming.liang on 2016/7/5.
 */
public class PeriodPingDns {
    public static final String TAG = "PeriodPingTask";
    PingTask mPingTask;
    Timer timer = new Timer();
    private int mDelay  = 0;//启动延迟
    private int mPeriod = 6000;  //执行周期 ms
    private Context mContext;

    public PeriodPingDns(Context context) {
        mContext = context;
    }

    public void startPeriodPing() {
        logd( "startPeriodPing");
        if (mPingTask != null) {
            mPingTask.cancel();
        }
        mPingTask = new PingTask();
        timer.scheduleAtFixedRate(mPingTask, mDelay, mPeriod);
    }

    public void stopPeriodPing() {
        logd( "stopPeriodPing");
        if (mPingTask != null) {
            mPingTask.cancel();
        }
    }

    public String getPhoneIp() {
        ConnectivityManager connmnger = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connmnger.getActiveNetworkInfo();
        if (activeNetworkInfo == null) {
            return "";
        }
        String subtypeName = activeNetworkInfo.getSubtypeName();
        boolean roaming = activeNetworkInfo.isRoaming();
        logd( "getPhoneIp: subtypeName:" + subtypeName + "roaming : " + roaming + "activeNetworkInfo" + activeNetworkInfo);
        //        try {
        //            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
        //                NetworkInterface intf = en.nextElement();
        //                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
        //                    InetAddress inetAddress = enumIpAddr.nextElement();
        //                    
        //                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
        //                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet6Address) {
        //                            return inetAddress.getHostAddress().toString();
        //                        }
        //                    }
        //                }
        //            }
        //        } catch (Exception e) {
        //            e.printStackTrace();
        //        }
        return "";
    }

    public static void doPingAlways() {
        new Thread(){
            @Override
            public void run() {
                super.run();
                while (true) {
                    logd("doPingAlways");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    dopingOnce();
                }
            }
        }.start();
    }

    public static void dopingOnce(){
        logd("dopingonce");
//        ping("www.163.com");
        new Thread(){
            @Override
            public void run() {
                Socket socket = null;
                super.run();
                try {
                    Thread.sleep(2000); //延迟2秒ping
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(getIp(), getPort()), 5000);
//                    byte[] buf =new byte[1024];
//                    InputStream input = socket.getInputStream();
//                   logi("InputStream==="+input);
//                    if(input!=null){
//                        boolean connected = socket.isConnected();
////                int len=input.read(buf);
//                        logd("socket respone:"+connected);
//                    }
                    boolean connected = socket.isConnected();
//                int len=input.read(buf);
                    logd("socket respone:"+connected);
//                    input.close();
                    socket.close();
                } catch (IOException e) {
                    logd("dopingonce faild:" + e.getLocalizedMessage());
                    e.printStackTrace();
                    try {
                        socket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();


    }
    public void pingOnce(){
        if (mPingTask==null) {
            mPingTask=new PingTask();
        }
        mPingTask.run();
    }
    class PingTask extends java.util.TimerTask {
        public void run() {
            {
                logd("dopingonce");
                //        ping("www.163.com");
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(getIp(), getPort()), 5000);
//                    byte[] buf =new byte[1024];
//                    InputStream input = socket.getInputStream();
//                    logi("InputStream==="+input);
//                    if(input!=null){
                        boolean connected = socket.isConnected();
                        //                int len=input.read(buf);
                        logd("socket respone:"+connected);
                        if (connected){
//                            OnDemanCallUtil.INSTANCE.setDunNetOk(true);
//                            ServiceManager.INSTANCE.getNetMonitor().setDunStatu(true);
                        }
//                    }
//                    input.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
           
        }
    }

    ExecutorService mExecutorService;

    public void ping(final String url) {
        if (mExecutorService == null) {
            //            mExecutorService = Executors.newFixedThreadPool(5);
            mExecutorService = Executors.newFixedThreadPool(5, new ThreadFactory() {
                int count;

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName(TAG + "_thread_" + count);
                    count++;
                    return thread;
                }
            });
        }
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InetAddress localHost = InetAddress.getLocalHost();
                    String hostAddress = localHost.getHostAddress();
                    String hostName = localHost.getHostName();
                    logd( "do Pingtask result hostAddress:" + hostAddress + " hostName:" + hostName);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                Ping.Domain domain = new Ping().ping(url);
                logd( "do Pingtask result:" + domain + " getPhoneIp:" + getPhoneIp());
            }
        });
    }

    private static String getIp() {
        if (ServiceManager.systemApi.isUnderDevelopMode()) {
            return "58.251.37.197";
        } else {
            return "59.41.253.6";
        }
    }

    private static Integer getPort() {
        if (ServiceManager.systemApi.isUnderDevelopMode()) {
            return 10144;
        } else {
            return 80;
        }
    }

    class Ping {
        public Domain ping(String url) {
            long start = System.currentTimeMillis();
            logd( "do Pingtask start---timeMark:" + start);
            Domain result = new Domain();
            result.timeMark = start;
            try {
                InetAddress address = InetAddress.getByName(url);
                result.ip = address.getHostAddress();
                result.host = address.getHostName();
                long end = System.currentTimeMillis();
                result.runTime = (end - start);
            } catch (UnknownHostException e) {
                result.ip = "0.0.0.0";
                result.host = String.format("ping %s fail!", url);
            }
            return result;
        }

        public class Domain {
            String ip;
            String host;
            long   runTime;
            long   timeMark;

            @Override
            public String toString() {
                return String.format("host=%s, ip=%s, time=%s, timeMark=%s", host, ip, runTime, timeMark);
            }
        }
        //        public static void main(String[] args) {
        //            Domain domain = ping("www.163.com");
        //            logi(domain);
        //        }
    }
}
