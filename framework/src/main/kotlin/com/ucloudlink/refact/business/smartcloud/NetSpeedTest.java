package com.ucloudlink.refact.business.smartcloud;

import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.CellLocation;
import android.text.TextUtils;

import com.ucloudlink.framework.protocol.protobuf.DetectionResult;
import com.ucloudlink.framework.protocol.protobuf.S2c_speed_detection;
import com.ucloudlink.framework.protocol.protobuf.SpeedDetectionUrlReq;
import com.ucloudlink.framework.protocol.protobuf.SpeedDetectionUrlResp;
import com.ucloudlink.framework.protocol.protobuf.Speed_detect_trig_type;
import com.ucloudlink.framework.protocol.protobuf.Speed_detect_type;
import com.ucloudlink.framework.protocol.protobuf.Upload_Speed_Detection_Result;
import com.ucloudlink.framework.protocol.protobuf.Upload_Speed_Result_Resp;
import com.ucloudlink.framework.protocol.protobuf.speedStartTypeE;
import com.ucloudlink.refact.business.Requestor;
import com.ucloudlink.refact.business.flow.net.HttpReqHelper;
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo;
import com.ucloudlink.refact.utils.JLog;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import rx.Subscription;
import rx.functions.Action1;

import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;

/**
 * Created by zhanlin.ma on 2018/1/19.
 * 通过测速（ping）方式检测网络情况
 * 测速失败触发换卡流程
 * 触发条件：
 * 1、拨号成功1分钟
 * 2、位置发生改变
 */
public class NetSpeedTest {
    static final String TAG = "smartcloud NetSpeedTest";
    private static NetSpeedTest instance = null;
    private boolean                enable;
    private boolean                mPunishEnable = false;
    private boolean                mNeedTestInPunishmentTime = false;
    private int                    mSpeedDetectType;
    private int                    mDelayTime;
    private int                    mDownLoadRate;
    private int                    mCurLac = -1;
    private Speed_detect_trig_type mTrigerType;
    private int                    mSuitArea;
    private int                    mSn;
    private List<String>           mUrls;
    private List<DetectionResult> dtrList = new ArrayList<>();
    private HandlerThread          mPingHandlerThread;
    private Subscription mCellLocationSub = null;
    private Subscription mLacInfoSub = null;
    private Subscription mDataConnectionSub = null;
    private Subscription mSpeedTestUrlReqSub = null;
    private Subscription mSpeedTestResultSub = null;
    Handler mPingHandler;
    private boolean isDataConnected = false;

    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    private static final int MAX_RETRY_CNT = 3;
    private static final int SPEED_DETECT_TYPE_PING = 1;
    private static final int SPEED_DETECT_TYPE_HTTPDOWNLOAD = 8;
    private int mRetryCnt = 0;

    private NetSpeedTest(){
        init();
    }

    public static NetSpeedTest getInstance(){
        if (instance == null) {
            instance = new NetSpeedTest();
        }
        return instance;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
        if (enable){
            registerListen();
        }else{
            unRegisterListen();
            stopPunishTimer();
            isDataConnected = false;
            mPunishEnable = false;
            mNeedTestInPunishmentTime = false;
            if (mPingHandler.hasMessages(SmartCloudEventId.EVENT_DATA_CONNECTED)){
                mPingHandler.removeMessages(SmartCloudEventId.EVENT_DATA_CONNECTED);
            }
            dtrList.clear();
        }
    }

    public boolean getEnable(){
        return enable;
    }

    void init(){
        mUrls = new ArrayList<>();
        mPingHandlerThread = new HandlerThread("NetSpeedTest");
        mPingHandlerThread.start();
        mPingHandler = new Handler(mPingHandlerThread.getLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case SmartCloudEventId.EVENT_CELL_LOCATION_CHANGED:
                        logi(TAG,"EVENT_CELL_LOCATION_CHANGED mCurLac:"+mCurLac
                                +",new lac="+OperatorNetworkInfo.INSTANCE.getLacCloudSim());
                        if (-1 == mCurLac){
                            mCurLac = OperatorNetworkInfo.INSTANCE.getLacCloudSim();
                            return;
                        }

                        if (!enable){
                            return;
                        }

                        if (!isDataConnected){
                            return;
                        }

                        if (mCurLac != OperatorNetworkInfo.INSTANCE.getLacCloudSim()){
                            mCurLac = OperatorNetworkInfo.INSTANCE.getLacCloudSim();
                            if (mPunishEnable){
                                mNeedTestInPunishmentTime = true;
                                logi(TAG,"Lac changed in punishment time deal with later.");
                                return;
                            }

                            //lac变化启动测速
                            detectAllNetWorkSpeed();

                            //向服务器发送测速结果
                            uploadSpeedDetectionResult(createSpeedDetectionResult(speedStartTypeE.SPEED_ACTION_TYPE_POS_CHANGE));
                            sendEmptyMessage(SmartCloudEventId.EVENT_SPEED_TEST_PING_COMPLETE);
                        }
                        break;
                    case SmartCloudEventId.EVENT_DATA_CONNECTED:
                        logi(TAG,"cloud sim data connected.");
                        if (!enable){
                            return;
                        }

                        if (!isDataConnected){
                            return;
                        }

                        if (mPunishEnable){
                            mNeedTestInPunishmentTime = true;
                            if (mPingHandler.hasMessages(SmartCloudEventId.EVENT_DATA_CONNECTED)){
                                mPingHandler.removeMessages(SmartCloudEventId.EVENT_DATA_CONNECTED);
                            }
                            logi(TAG,"Data connected in punishment time deal with later.");
                            return;
                        }

                        //云卡拨号成功启动测速
                        detectAllNetWorkSpeed();

                        //向服务器发送测速结果
                        uploadSpeedDetectionResult(createSpeedDetectionResult(speedStartTypeE.SPEED_START_TYPE_PDP));
                        sendEmptyMessage(SmartCloudEventId.EVENT_SPEED_TEST_PING_COMPLETE);
                        break;
                    case SmartCloudEventId.EVENT_PUNISH_TIME_OUT:
                        mPunishEnable = false;
                        JLog.logi(TAG,"net speed test punish time finished.");
                        if (!enable){
                            return;
                        }

                        if (!isDataConnected){
                            JLog.logi(TAG,"data disconnected.");
                            return;
                        }

                        if (mNeedTestInPunishmentTime){
                            logi(TAG,"EVENT_RAT_PRIORITY_PUNISH_TIME_OUT need test network speed now.");

                            //惩罚时间内触发测速
                            detectAllNetWorkSpeed();

                            //向服务器发送测速结果
                            if (mTrigerType == Speed_detect_trig_type.SPEED_DETECT_TRIG_POS_UPDATE){
                                uploadSpeedDetectionResult(createSpeedDetectionResult(speedStartTypeE.SPEED_ACTION_TYPE_POS_CHANGE));
                            }else if (mTrigerType == Speed_detect_trig_type.SPEED_DETECT_TRIG_PDP_DAIL){
                                uploadSpeedDetectionResult(createSpeedDetectionResult(speedStartTypeE.SPEED_START_TYPE_PDP));
                            }
                            sendEmptyMessage(SmartCloudEventId.EVENT_SPEED_TEST_PING_COMPLETE);
                            mNeedTestInPunishmentTime = false;
                        }
                        break;
                    case SmartCloudEventId.EVENT_SEND_URL_REQ_2_SERVER:
                        logi(TAG,"EVENT_SEND_URL_REQ_2_SERVER send url req to server.");
                        if (mSpeedTestUrlReqSub != null){
                            if (!mSpeedTestUrlReqSub.isUnsubscribed()){
                                mSpeedTestUrlReqSub.unsubscribe();
                            }
                        }

                        if (!enable){
                            return;
                        }

                        mSpeedTestUrlReqSub = Requestor.INSTANCE.requstSpeedDetectionUrl(createSpeedDetectionUrlReq())
                                .subscribe(
                                        new Action1<Object>() {
                                            @Override
                                            public void call(Object o) {
                                                SpeedDetectionUrlResp rsp = (SpeedDetectionUrlResp)o;
                                                saveSeepDetectionUrlInfo(rsp);
                                                logi(TAG,"requstSpeedDetectionUrl success.");
                                                mRetryCnt = 0;
                                            }
                                        }, new Action1<Throwable>() {
                                            @Override
                                            public void call(Throwable throwable) {
                                                logi(TAG,"requstSpeedDetectionUrl fail retryCnt:"+mRetryCnt);
                                                if (mRetryCnt < MAX_RETRY_CNT){
                                                    mRetryCnt++;
                                                    mPingHandler.obtainMessage(SmartCloudEventId.EVENT_SEND_URL_REQ_2_SERVER).sendToTarget();
                                                }
                                            }
                                        });
                        break;
                    case SmartCloudEventId.EVENT_SPEED_TEST_PING_COMPLETE:
                        if (!enable){
                            return;
                        }

                        if (!isDataConnected){
                            JLog.logi(TAG,"data disconnected.");
                            return;
                        }

                        //测速失败触发换卡流程
                        if (!isNetworkSpeedSucc()){
                            logi(TAG,"net speed test fail start switch cloud card.");
                            if (!SmartCloudController.getInstance().getHandler().hasMessages(SmartCloudEventId.EVENT_SWITCH_CARD_REQ)){
                                SmartCloudController.getInstance().getHandler()
                                        .obtainMessage(SmartCloudEventId.EVENT_SWITCH_CARD_REQ,0,SmartCloudSwitchReason.SWITCH_VSIM_SPEEDTEST,null).sendToTarget();
                            }
                        }
                        dtrList.clear();
                        break;
                    default:
                        break;
                }
            }
        };

    }

    private void detectNetworkSpeedByHttpdownload(String url){
        int downLoadTime = 1;
        long startTime = 0;
        long endTime = 0;
        int readTotal = 0;
        HttpURLConnection conn = null;
        InputStream ins = null;
        BufferedInputStream bufferReader = null;
        int responseCode = -1;
        try{
            conn = HttpReqHelper.getHttpURLConnection(url);
            conn.connect();
            responseCode = conn.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_OK){
                startTime = (new Date()).getTime();
                ins = conn.getInputStream();
                bufferReader = new BufferedInputStream(ins);
                int readLen;
                readTotal = 0;
                byte[] bytes = new byte[1024];
                while ((readLen = bufferReader.read(bytes)) != -1) {
                    readTotal += readLen;
                }
                endTime = (new Date()).getTime();
                downLoadTime = (int)(endTime - startTime);
                if (downLoadTime == 0){
                    downLoadTime = 1;
                }
                JLog.logi(TAG + ",detectNetworkSpeedByHttpdownload downloadbyte = " + readTotal +
                        ",startTime="+startTime+"ms"+",endTime="+endTime+"ms"+
                        ",downLoadTime="+downLoadTime);
                if (readTotal > 0){
                    JLog.logi(TAG + ",current downloadrate="+(readTotal/downLoadTime)*1000+"/s"
                            +";Server cfg downloadrate="+mDownLoadRate);
                }
            }
            JLog.logi(TAG + " detectNetworkSpeedByHttpdownload download finish! responseCode = "+responseCode);

            DetectionResult dtr = new DetectionResult(0,0,0,0,0,readTotal,downLoadTime);
            dtrList.add(dtr);

        }catch (Exception e){
            JLog.logi(TAG + " detectNetworkSpeedByHttpdownload getHttpURLConnection error! Exception:" + e.toString());
            e.printStackTrace();
        } finally {
            try{
                if(conn!=null){
                    conn.disconnect();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void detectNetworkSpeedByPing(String ip){
        int maxTime = 0;
        int avgTime = 0;
        int minTime = 0;
        String ping = "ping -c 4 -i 1 " + ip;// -c 4 发送4个包；-i 每个包间隔1s
        Process process = null;
        BufferedReader successReader = null;
        BufferedReader errorReader = null;

        try {
            process = Runtime.getRuntime().exec(ping);
            logi(TAG,"detectNetworkSpeed ping:"+ip);
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
                logi(TAG, "detectNetworkSpeed receive line:" + lineStr);

                //丢包率
                if (lineStr.contains("packet loss")) {
                    int i = lineStr.indexOf("received");
                    int j = lineStr.indexOf("%");
                    logi(TAG, "detectNetworkSpeed lost rate:"+ lineStr.substring(i + 10, j + 1));
                }

                if (lineStr.contains("rtt")) {
                    //最小时延
                    int i = lineStr.indexOf("=");
                    int j = lineStr.indexOf(".", i);
                    minTime = Integer.parseInt(lineStr.substring(i + 2, j));
                    logi(TAG, "detectNetworkSpeed minTimeString:"
                            + lineStr.substring(i + 1, j) + ",minTime:" + minTime);

                    //平均时延
                    i = lineStr.indexOf("/", 20);
                    j = lineStr.indexOf(".", i);
                    avgTime = Integer.parseInt(lineStr.substring(i + 1, j));
                    logi(TAG, "detectNetworkSpeed avgTimeString:"
                            + lineStr.substring(i + 1, j) + ",avgTime" + avgTime);

                    //最大时延
                    i = lineStr.indexOf("/", j);
                    j = lineStr.indexOf(".", i);
                    maxTime = Integer.parseInt(lineStr.substring(i + 1, j));
                    logi(TAG, "detectNetworkSpeed maxTimeString:"
                            + lineStr.substring(i + 1, j) + ",minTime" + minTime);

                    logi(TAG, "detectNetworkSpeed ping ip:"
                            + ip + ",avgtime:" + avgTime + ",server delayTime:" + mDelayTime);
                }
            }

            while ((lineStr = errorReader.readLine()) != null) {
                logi(TAG, "detectNetworkSpeed error:" + lineStr);
            }

            DetectionResult dtr = new DetectionResult(0,maxTime,avgTime,minTime,0,0,0);
            dtrList.add(dtr);

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
    }

    private boolean isNetworkSpeedSucc(){
        Iterator<DetectionResult> it = dtrList.iterator();
        switch (mSpeedDetectType){
            case SPEED_DETECT_TYPE_PING:
                while (it.hasNext()){
                    DetectionResult dr = it.next();
                    if ((dr.avgtime != 0) && (dr.avgtime <= mDelayTime)){
                        logi(TAG,"ping speed detect succ");
                        return true;
                    }
                }
                break;
            case SPEED_DETECT_TYPE_HTTPDOWNLOAD:
                int downLoadRate = 0;
                while (it.hasNext()){
                    DetectionResult dr = it.next();
                    if (dr.downloadtime == 0){
                        continue;
                    }
                    downLoadRate = (dr.downloadbyte/dr.downloadtime) * 1000;
                    if ((dr.downloadbyte != 0) && (downLoadRate > mDownLoadRate)){
                        logi(TAG,"httpdownload speed detect succ");
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void detectAllNetWorkSpeed(){
        logi(TAG,"detectAllNetWorkSpeed start! urls:"+mUrls.toString());
        Iterator<String> it = mUrls.iterator();
        if (mUrls.isEmpty()){
            logi(TAG,"speed tset url is empty!");
            return;
        }
        switch (mSpeedDetectType){
            case SPEED_DETECT_TYPE_PING:
                while (it.hasNext()){
                    String url = it.next();
                    if (TextUtils.isEmpty(url)){
                        continue;
                    }

                    String dns = address2Ip(url);
                    if (TextUtils.isEmpty(dns)){
                        continue;
                    }
                    logi(TAG,"detectNetworkSpeedByPing url:"+url+",dns:"+dns);

                    detectNetworkSpeedByPing(dns);
                }
                break;
            case SPEED_DETECT_TYPE_HTTPDOWNLOAD:
                while (it.hasNext()){
                    String url = it.next();
                    if (TextUtils.isEmpty(url)){
                        continue;
                    }
                    logi(TAG,"detectNetworkSpeedByHttpdownload url:"+url);
                    detectNetworkSpeedByHttpdownload(url);
                }
                break;
            default:
                break;
        }

        logi(TAG,"detectAllNetWorkSpeed end.");
    }

    private void saveSeepDetectionUrlInfo(SpeedDetectionUrlResp rsp){
        loge(TAG,"saveSeepDetectionUrlInfo rsp:"+rsp.toString());
        mSn = rsp.sn;
        mUrls = rsp.urls;
        mSpeedDetectType = rsp.actionType;
        mDelayTime = rsp.delayTime;
        mDownLoadRate = rsp.downloadRate;
        switch (mSpeedDetectType){
            case SPEED_DETECT_TYPE_PING:
                if ((mDelayTime <= 0)||(mUrls.isEmpty())){
                    loge(TAG,"invalid param!");
                    setEnable(false);
                }
                break;
            case SPEED_DETECT_TYPE_HTTPDOWNLOAD:
                if ((mDownLoadRate <= 0)||(mUrls.isEmpty())){
                    loge(TAG,"invalid param!");
                    setEnable(false);
                }
                break;
            default:
                setEnable(false);
                break;
        }

    }

    private SpeedDetectionUrlReq createSpeedDetectionUrlReq(){
        SpeedDetectionUrlReq req = new SpeedDetectionUrlReq(OperatorNetworkInfo.INSTANCE.getSeedPlmnList(),
                OperatorNetworkInfo.INSTANCE.getLac(),
                OperatorNetworkInfo.INSTANCE.getSignalStrength(),mSuitArea);
        loge(TAG,"createSpeedDetectionUrlReq info:"+req.toString());
        return req;
    }

    private Upload_Speed_Detection_Result createSpeedDetectionResult(speedStartTypeE speedStartType){
        Integer smartPriorityEN = new Integer(1);
        Upload_Speed_Detection_Result ret = new Upload_Speed_Detection_Result(
                OperatorNetworkInfo.INSTANCE.getCloudPlmnList(),
                OperatorNetworkInfo.INSTANCE.getLacCloudSim(),
                OperatorNetworkInfo.INSTANCE.getSignalStrengthCloudSim(),
                mSn, speedStartType,mSpeedDetectType,dtrList,smartPriorityEN);
        return ret;
    }


    //向服务器发送测速结果
    private void uploadSpeedDetectionResult(final Upload_Speed_Detection_Result ret){
        if (mSpeedTestResultSub != null){
            if (!mSpeedTestResultSub.isUnsubscribed()){
                mSpeedTestResultSub.unsubscribe();
            }
        }

        if (!enable){
            return;
        }

        if (!isDataConnected){
            JLog.logi(TAG,"data disconnected.");
            return;
        }

        mSpeedTestResultSub = Requestor.INSTANCE.requstUploadSpeedDetectionResult(ret).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                Upload_Speed_Result_Resp rsp = (Upload_Speed_Result_Resp)o;
                logi(TAG,"uploadSpeedDetectionResult success. rsp:"+rsp.errorCode);
                mRetryCnt = 0;
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                logi(TAG,"uploadSpeedDetectionResult fail retryCnt:"+mRetryCnt);
                if (mRetryCnt < MAX_RETRY_CNT){
                    mRetryCnt++;
                    uploadSpeedDetectionResult(ret);
                }
            }
        });
    }

    public void setSpeedDetectCfg(S2c_speed_detection cfg){

        logi(TAG,"setSpeedDetectCfg cfg:"+cfg.toString());

        enable = cfg.detect_ctrl == Speed_detect_type.SPEED_DETECT_TYPE_START ? true : false;
        mTrigerType = cfg.trig_type;
        mSuitArea = cfg.suit.getValue();
        if (enable){
            registerListen();
            mPingHandler.sendEmptyMessage(SmartCloudEventId.EVENT_SEND_URL_REQ_2_SERVER);
        }else{
            unRegisterListen();
        }
        logi(TAG,"setSpeedDetectCfg enable:"+enable+",mTrigerType:"+mTrigerType+",mSuitArea:"+mSuitArea);
    }

    private void registerListen(){

        if (mCellLocationSub != null){
            if (!mCellLocationSub.isUnsubscribed()){
                mCellLocationSub.unsubscribe();
            }
        }

        mCellLocationSub = SmartCloudController.getInstance().getCellLocationObservable().subscribe(new Action1<CellLocation>() {
            @Override
            public void call(CellLocation cellLocation) {
                if (!enable){
                    return;
                }

                if (mTrigerType != Speed_detect_trig_type.SPEED_DETECT_TRIG_POS_UPDATE
                        && mTrigerType != Speed_detect_trig_type.SPEED_DETECT_TRIG_PDP_OR_POS_UPDATE){
                    return;
                }
                mPingHandler.obtainMessage(SmartCloudEventId.EVENT_CELL_LOCATION_CHANGED, cellLocation).sendToTarget();
            }
        });

        if (mLacInfoSub != null){
            if (!mLacInfoSub.isUnsubscribed()){
                mLacInfoSub.unsubscribe();
            }
        }

        mLacInfoSub = SmartCloudController.getInstance().getLacObservable().subscribe(new Action1<Integer>() {
            @Override
            public void call(Integer lac) {
                if (mTrigerType != Speed_detect_trig_type.SPEED_DETECT_TRIG_POS_UPDATE
                        && mTrigerType != Speed_detect_trig_type.SPEED_DETECT_TRIG_PDP_OR_POS_UPDATE){
                    return;
                }
                mPingHandler.obtainMessage(SmartCloudEventId.EVENT_CELL_LOCATION_CHANGED, lac).sendToTarget();
            }
        });

        if (mDataConnectionSub != null){
            if (!mDataConnectionSub.isUnsubscribed()){
                mDataConnectionSub.unsubscribe();
            }
        }

        mDataConnectionSub = SmartCloudController.getInstance().getDataConnectionStateObservable().subscribe(new Action1<NetworkInfo.State>() {
            @Override
            public void call(NetworkInfo.State state) {
                if (!enable){
                    return;
                }

                if (state == NetworkInfo.State.CONNECTED){
                    isDataConnected = true;
                }else {
                    isDataConnected = false;
                }

                if (isDataConnected){
                    if (mTrigerType != Speed_detect_trig_type.SPEED_DETECT_TRIG_PDP_DAIL
                            && mTrigerType != Speed_detect_trig_type.SPEED_DETECT_TRIG_PDP_OR_POS_UPDATE){
                        return;
                    }

                    if (!mPingHandler.hasMessages(SmartCloudEventId.EVENT_DATA_CONNECTED)){
                        mPingHandler.sendEmptyMessageDelayed(SmartCloudEventId.EVENT_DATA_CONNECTED, 60 * 1000);
                    }
                }
            }
        });
    }

    private void unRegisterListen(){
        if (mCellLocationSub != null){
            if (!mCellLocationSub.isUnsubscribed()){
                mCellLocationSub.unsubscribe();
                mCellLocationSub = null;
            }
        }

        if (mDataConnectionSub != null){
            if (!mDataConnectionSub.isUnsubscribed()){
                mDataConnectionSub.unsubscribe();
                mDataConnectionSub = null;
            }
        }

        if (mLacInfoSub != null){
            if (!mLacInfoSub.isUnsubscribed()){
                mLacInfoSub.unsubscribe();
                mLacInfoSub = null;
            }
        }
    }

    /**
     * 返回的ip 可能是域名，可能是数字ip
     * @param str
     * @return
     */
    public static String address2Ip(String str){
        if(TextUtils.isEmpty(str))
            return null;
        try{
            String result = str.replaceFirst(HTTP,"");
            result = result.replaceFirst(HTTP.toUpperCase(),"");
            result = result.replaceFirst(HTTPS,"");
            result = result.replaceFirst(HTTPS.toUpperCase(),"");

            String[] sArray = result.split("/");
            if(sArray!=null && sArray.length > 0 ){
                result =  sArray[0];
                if(result.contains(":")){
                    result = result.substring(0, result.indexOf(":"));
                }
                if(result.contains("?")){
                    result = result.substring(0, result.indexOf("?"));
                }
                return result;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public void setPunishment(int time){
        JLog.logi(TAG,"net speed test punishment time started:time:"+time);
        mPunishEnable = time != 0 ? true : false;
        stopPunishTimer();
        if (mPunishEnable){
            startPunishTimer(time);
        }
    }

    private void startPunishTimer(int time){
        mPingHandler.sendEmptyMessageDelayed(SmartCloudEventId.EVENT_PUNISH_TIME_OUT, time * 60 * 1000);
    }

    private void stopPunishTimer(){
        if (mPingHandler.hasMessages(SmartCloudEventId.EVENT_PUNISH_TIME_OUT)){
            mPingHandler.removeMessages(SmartCloudEventId.EVENT_PUNISH_TIME_OUT);
        }
    }

}
