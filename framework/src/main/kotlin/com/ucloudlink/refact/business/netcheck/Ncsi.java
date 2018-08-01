package com.ucloudlink.refact.business.netcheck;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import rx.Single;
import rx.SingleSubscriber;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;

/*
 * Created by pengchugang on 2016/10/19.
 * @author pengchugang
 */
public class Ncsi {
    private final String szMsUrl = "http://www.msftncsi.com/ncsi.txt";
    private final String uclServiceUrl = "http://t1.ukelink.com/";
    private final String szAppleUrl1 = "captive.apple.com/hotspod-detect.html";
    private final String szAppleUrl2 = "www.apple.com/library/test/success.html";
    private final String szXiaomiUrl = "http://connect.rom.miui.com/generate_204";

    private final String NCSI_REALCONTENTED = "Microsoft NCSI";
    //    private final String RMNETDATA0_DNS_PROPERTY_KEY = "net.rmnet_data0.dns1";
//    private final String RMNETDATA1_DNS_PROPERTY_KEY = "net.rmnet_data1.dns1";
    public final int UC_NCSI_NETWORK_CONNECTED = 0;
    public final int UC_NCSI_DNSSERVER_LESS = 1;
    public final int UC_NCSI_DISCONNECT = 2;
    public final int UC_NCSI_NETWORK_UNKNOW = 3;
    public final int UC_NCSI_DNSSERVER_UNREACH = 4;
    public final String strWifiBlock = "com.ucloudlink.ucapp.wifiblock";
    public final String strWifiUnBlock = "com.ucloudlink.ucapp.wifiunblock";
    public final String strDnsServerUnReach = "com.ucloudlink.ucapp.dnsserverunreach";
    public final String strNetworkDisconnect = "com.ucloudlink.ucapp.networkdisconnect";
    public final String strNetworkConnected = "com.ucloudlink.ucapp.networkconnected";
    public final static int START_PING = 5;
    private boolean pingRet = false;
    private Object lock = new Object();
    /*
    ping dns 超时时间，5s
     */
    private static final int PING_DNS_WAIT_TIME = 5000;

    private Context mContext;
    private static Ncsi instance = null;
    private HandlerThread mHandlerThread = null;
    Handler mHandler = null;
    private Ncsi() {
        mContext = ServiceManager.appContext;
        mHandlerThread = new HandlerThread("Ncsi");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()){
            @Override
            public void handleMessage(Message msg){
                String ip = (String )msg.obj;
                switch (msg.what){
                    case START_PING:
                        synchronized (lock){
                            pingRet = ping(ip);
                            lock.notifyAll();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public enum NcsiResult {
        /*
        成功
         */
        NETWORK_OK,
        /*
        dns服务器不对
         */
        DNS_SERVER_LESS,
        /*
        网络断开
         */
        DISCONNECTED,
        /*
        网络未知
         */
        NETWORK_UNKNOWN,
        /*
        dns不可达
         */
        DNS_SERVER_UNREACH,
    }

    private boolean ping(String ip){
        int maxTime = 0;
        int avgTime = 0;
        int minTime = 0;
        String ping = "ping -c 3 -i 1 " + ip;// -c 3 发送3个包；-i 每个包间隔1s
        Process process = null;
        BufferedReader successReader = null;
        BufferedReader errorReader = null;

        try {
            process = Runtime.getRuntime().exec(ping);
            logi("ping:"+ip);
            InputStream in = process.getInputStream();
            // success
            successReader = new BufferedReader(
                    new InputStreamReader(in));
            // error
            errorReader = new BufferedReader(new InputStreamReader(
                    process.getErrorStream()));
            String lineStr;
            //e.g:rtt min/avg/max/mdev = 65.818/83.289/115.594/20.254 ms
            while ((lineStr = successReader.readLine()) != null) {
                logi("ping receive line:" + lineStr);

                //丢包率
                if (lineStr.contains("packet loss")) {
                    int i = lineStr.indexOf("received");
                    int j = lineStr.indexOf("%");
                    logi("ping lost rate:"+ lineStr.substring(i + 10, j + 1));
                }

                if (lineStr.contains("rtt")) {
                    //最小时延
                    int i = lineStr.indexOf("=");
                    int j = lineStr.indexOf(".", i);
                    minTime = Integer.parseInt(lineStr.substring(i + 2, j));
                    logi("ping minTimeString:"
                            + lineStr.substring(i + 1, j) + ",minTime:" + minTime);

                    //平均时延
                    i = lineStr.indexOf("/", 20);
                    j = lineStr.indexOf(".", i);
                    avgTime = Integer.parseInt(lineStr.substring(i + 1, j));
                    logi("ping avgTimeString:"
                            + lineStr.substring(i + 1, j) + ",avgTime" + avgTime);

                    //最大时延
                    i = lineStr.indexOf("/", j);
                    j = lineStr.indexOf(".", i);
                    maxTime = Integer.parseInt(lineStr.substring(i + 1, j));
                    logi("ping maxTimeString:"
                            + lineStr.substring(i + 1, j) + ",maxTime" + maxTime);

                    logi("ping ip:" + ip + ",avgtime:" + avgTime);
                }
            }

            while ((lineStr = errorReader.readLine()) != null) {
                logi("ping error:" + lineStr);
            }

            if (avgTime != 0){
                return true;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (successReader != null) {
                    successReader.close();
                }
                if (errorReader != null) {
                    errorReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (process != null) {
                process.destroy();
            }
        }
        return false;
    }

    public boolean dnsServerIsReachable(int netType) {
        logd("DnsServerIsReachable start");

        ArrayList<InetAddress> list = ServiceManager.systemApi.getDnsServers(netType);
        if ((list == null)||(list.isEmpty())){
            loge("dns is null.");
            return false;
        }

        InetAddress dns = list.get(0);
        if (dns == null){
            loge("dns is null.");
            return false;
        }

        logd("DnsServerIsReachable dns:"+dns.toString());

        String ip = dns.toString();
        int startpos = ip.indexOf("/");
        ip = ip.substring(startpos+1);

        mHandler.obtainMessage(START_PING, ip).sendToTarget();

        synchronized (lock){
            try {
                pingRet = false;
                lock.wait(PING_DNS_WAIT_TIME);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }

        return pingRet;
    }

    private void sendMobileNetworkBroadcast(NcsiResult ret) {
        if (NcsiResult.DNS_SERVER_UNREACH == ret) {
            Intent intent = new Intent();
            intent.setAction(strDnsServerUnReach);
            mContext.sendBroadcast(intent);
        } else {
            if (ret == NcsiResult.NETWORK_OK) {
                Intent intent = new Intent();
                intent.setAction(strNetworkConnected);
                mContext.sendBroadcast(intent);
            } else {
                Intent intent = new Intent();
                intent.setAction(strNetworkDisconnect);
                mContext.sendBroadcast(intent);
            }
        }
    }

    private void sendWifiNetworkBroadcast(NcsiResult ret) {
        if (ret == NcsiResult.NETWORK_OK) {
            Intent intent = new Intent();
            intent.setAction(strWifiUnBlock);
            mContext.sendBroadcast(intent);
        } else {
            Intent intent = new Intent();
            intent.setAction(strWifiBlock);
            mContext.sendBroadcast(intent);
        }
    }

    private NcsiResult mobileNetworkTest(int netType) {
        JLog.logd("start to mobileNetworkTest " + uclServiceUrl);
        NcsiResult ret = NcsiResult.DNS_SERVER_LESS;
        boolean bStatus = false;
        String line;
        String mContent = "";
        BufferedReader br = null;
        try {
            bStatus = dnsServerIsReachable(netType);
            if (bStatus) {
                String strUrl = uclServiceUrl;
                StringBuffer sb = new StringBuffer();
                URL url = new URL(strUrl);
                br = new BufferedReader(new InputStreamReader(url.openStream()));

                while ((line = br.readLine()) != null) {
                    sb.append(line);
                    break;
                }

                mContent = sb.toString();

                logd("Ncsi content:" + mContent);
                if (mContent.equals(NCSI_REALCONTENTED)) {
                    ret = NcsiResult.NETWORK_OK;
                } else if (mContent.isEmpty()) {
                    ret = NcsiResult.DISCONNECTED;
                } else {
                    ret = NcsiResult.NETWORK_OK;
                }
            } else {
                ret = NcsiResult.DNS_SERVER_UNREACH;
            }
        } catch (IOException e) {
            e.printStackTrace();
            ret = NcsiResult.NETWORK_UNKNOWN;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        logd("Mobile Ncsi check result " + ret);
        sendMobileNetworkBroadcast(ret);

        return ret;
    }

    private NcsiResult wifiNetworkTest() {
        logd("start to test wifi");
        NcsiResult ret;
        String mContent = "";
        String line;
        BufferedReader br = null;

        try {
            String strUrl = szXiaomiUrl;
            URL mUrl = new URL(strUrl);
            URLConnection urlcon = mUrl.openConnection();

            urlcon.setConnectTimeout(3000);
            urlcon.setReadTimeout(3000);
            br = new BufferedReader(new InputStreamReader(urlcon.getInputStream(), "UTF-8"));
            StringBuffer sb = new StringBuffer();

            while ((line = br.readLine()) != null) {
                sb.append(line);
                break;
            }

            mContent = sb.toString();

            logd("Ncsi content:" + mContent);
            ret = NcsiResult.NETWORK_OK;
        } catch (IOException e) {
            e.printStackTrace();
            ret = NcsiResult.DISCONNECTED;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        logd("Wifi Ncsi check result " + ret);
        sendWifiNetworkBroadcast(ret);
        return ret;
    }

    public static Ncsi getInstance() {
        if (instance == null) {
            instance = new Ncsi();
        }
        return instance;
    }


    public void testWifiNcsi(String name) {
        logd("test WiFi Ncsi.");
        new WifiSpeedTest(name).start();
    }

    public void testMobileNcsi(String name, int netType) {
        logd("test Mobile Ncsi.");
        new MobileSpeedTest(name, netType).start();
    }

    private class WifiSpeedTest extends Thread {
        int ret = UC_NCSI_DNSSERVER_LESS;
        String mContent = "";
        String line;
        BufferedReader br = null;

        WifiSpeedTest(String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                String strUrl = szXiaomiUrl;
                URL mUrl = new URL(strUrl);
                URLConnection urlcon = mUrl.openConnection();

                /*set timeout 3s*/
                urlcon.setConnectTimeout(3000);
                urlcon.setReadTimeout(3000);
                br = new BufferedReader(new InputStreamReader(urlcon.getInputStream(), "UTF-8"));
                //  BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                StringBuffer sb = new StringBuffer();

                while ((line = br.readLine()) != null) {
                    sb.append(line);
                    break;
                }
                mContent = sb.toString();

                logd("Ncsi content:" + mContent);
                ret = UC_NCSI_NETWORK_CONNECTED;
                /*
                if(mContent.equals(NCSI_REALCONTENTED)){
                    ret = UC_NCSI_NETWORK_CONNECTED;
                }else{
                    ret = UC_NCSI_DISCONNECT;
                }
                */
            } catch (IOException e) {
                e.printStackTrace();
                ret = UC_NCSI_DISCONNECT;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            logd("Wifi Ncsi check result " + ret);
            if (ret == UC_NCSI_NETWORK_CONNECTED) {
                Intent intent = new Intent();
                intent.setAction(strWifiUnBlock);
                mContext.sendBroadcast(intent);
            } else {
                Intent intent = new Intent();
                intent.setAction(strWifiBlock);
                mContext.sendBroadcast(intent);
            }
        }
    }

    private class MobileSpeedTest extends Thread {
        int ret = UC_NCSI_DNSSERVER_LESS;
        String mContent;
        String line;
        boolean bStatus = false;
        int netType = 0;

        MobileSpeedTest(String name, int netType) {
            super(name);
            this.netType = netType;
        }

        @Override
        public void run() {
            NcsiResult ret = mobileNetworkTest(netType);
            JLog.logd("mobileNetworkTest ret:" + ret);
        }
    }

    /* 阻塞的，使用的时候需要注意 */
    public Single<NcsiResult> startMobileNetworkTest(final int netType) {
        return Single.create(new Single.OnSubscribe<NcsiResult>() {
            @Override
            public void call(final SingleSubscriber<? super NcsiResult> singleSubscriberFather) {
                NcsiResult result = mobileNetworkTest(netType);
                singleSubscriberFather.onSuccess(result);
            }
        });
    }

    /* 阻塞的，使用的时候需要注意 */
    public Single<NcsiResult> startWifiNetworkTest() {
        return Single.create(new Single.OnSubscribe<NcsiResult>() {
            @Override
            public void call(SingleSubscriber<? super NcsiResult> singleSubscriber) {
                NcsiResult result = wifiNetworkTest();
                singleSubscriber.onSuccess(result);
            }
        });
    }

    public Single<NcsiResult> startNetworkTest(final Boolean isMobile, final int netType) {
        JLog.logd("startNetworkTest: " + isMobile);
        return Single.create(new Single.OnSubscribe<NcsiResult>() {
            @Override
            public void call(final SingleSubscriber<? super NcsiResult> singleSubscriberFather) {
                NcsiResult result;
                logd("startNetworkTest: call  " + isMobile + " " + netType);
                if (isMobile) {
                    result = mobileNetworkTest(netType);
                } else {
                    result = wifiNetworkTest();
                }
                singleSubscriberFather.onSuccess(result);
            }
        });
    }
}

