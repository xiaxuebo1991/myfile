package com.ucloudlink.refact.business.smartcloud;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import com.ucloudlink.framework.protocol.protobuf.PlmnInfo;
import com.ucloudlink.framework.protocol.protobuf.S2cSwitchCardResult;
import com.ucloudlink.framework.protocol.protobuf.S2c_UpdatePlmnListRequest;
import com.ucloudlink.framework.protocol.protobuf.S2c_speed_detection;
import com.ucloudlink.framework.protocol.protobuf.S2c_weak_signal_ctrl;
import com.ucloudlink.framework.protocol.protobuf.report_searchnet_result_resp;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEventId;
import com.ucloudlink.refact.business.Requestor;
import com.ucloudlink.refact.business.netcheck.NetInfo;
import com.ucloudlink.refact.business.netcheck.NetworkManager;
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo;
import com.ucloudlink.refact.channel.enabler.datas.CardStatus;
import com.ucloudlink.refact.channel.enabler.simcard.UcPhoneStateListenerWrapper;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;

import java.util.ArrayList;
import java.util.HashMap;

import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

import static com.ucloudlink.refact.ServiceManager.cloudSimEnabler;
import static com.ucloudlink.refact.business.netcheck.NetworkDefineKt.SIGNAL_STRENGTH_UC_GOOD;
import static com.ucloudlink.refact.business.netcheck.NetworkDefineKt.SIGNAL_STRENGTH_UC_GREAT;
import static com.ucloudlink.refact.business.netcheck.NetworkDefineKt.SIGNAL_STRENGTH_UC_GREAT_MORE;

/**
 * Created by zhanlin.ma on 2018/1/19.
 * 用于控制控制3g优选，弱信号，测速，一件换卡四个模块
 * 为各模块提供云卡各种状态的主题
 * 处理各模块发送的搜网结果并发送给服务器并处理服务器返回的还卡结果
 * 控制各模块的惩罚时间
 */

public class SmartCloudController{
    private static final String TAG = "smartcloud SmartCloudController";
    private static final int PUNISH_TIME_15 = 15;
    private static final int PUNISH_TIME_30 = 30;
    private static final int MAX_RETRY_CNT  = 3;
    private static SmartCloudController instance = null;
    private HashMap listenMap = new HashMap<Integer, SmartCloudPhoneStateListener>();
    private Integer[] subIdArry = new Integer[]{-1,-1,-1};
    private TelephonyManager mTelephonyManager;
    private HandlerThread    mHandlerThread;
    Handler mHandler;
    private Subscription mReportSearchNetRetSub;
    private Subscription mCardStateSub;
    private Subscription mNetStateSub;
    private Subscription mLacValueSub;
    private BehaviorSubject<ServiceState> mServiceStateObservable;
    private BehaviorSubject<SignalStrength> mSignalStrengthObservable;
    private BehaviorSubject<NetworkInfo.State> mDataConnectionStateObservable;
    private BehaviorSubject<CellLocation>      mCellLocationObservable;
    private PublishSubject<Integer> mLacObservable;
    private int mRetryCnt = 0;
    private ArrayList<PlmnInfo> plmnInfos = null;
    private boolean ratPriorityEnable = false;
    private boolean weakTestEnable = false;
    private boolean speedTestEnable = false;
    private boolean isCloudNetConnected = false;

    private SmartCloudController(){
        init();
    }

    public static SmartCloudController getInstance(){
        if (instance == null) {
            instance = new SmartCloudController();
        }
        return instance;
    }

    public BehaviorSubject<NetworkInfo.State> getDataConnectionStateObservable() {
        return mDataConnectionStateObservable;
    }

    public BehaviorSubject<CellLocation> getCellLocationObservable() {
        return mCellLocationObservable;
    }

    public PublishSubject<Integer> getLacObservable() {
        return mLacObservable;
    }

    public BehaviorSubject<SignalStrength> getSignalStrengthObservable() {
        return mSignalStrengthObservable;
    }

    public BehaviorSubject<ServiceState> getServiceStateObservable() {
        return mServiceStateObservable;
    }

    public Handler getHandler() {
        return mHandler;
    }

    private void init(){
        mHandlerThread = new HandlerThread("SmartCloud");
        mHandlerThread.start();
        mServiceStateObservable = BehaviorSubject.create();
        mSignalStrengthObservable = BehaviorSubject.create();
        mDataConnectionStateObservable = BehaviorSubject.create();
        mCellLocationObservable        = BehaviorSubject.create();
        mLacObservable = PublishSubject.create();

        mTelephonyManager = (TelephonyManager) ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE);
        registListen();
        JLog.logd(TAG,"SmartCloudController init");
        mHandler = new Handler(mHandlerThread.getLooper()){
            Integer subId = -1;
            CardStatus state = CardStatus.ABSENT;
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case SmartCloudEventId.EVENT_ENABLE_RAT_PRIORITY:{
                         RatPriority.getInstance().setRatPriorityCfg((S2c_UpdatePlmnListRequest)msg.obj);
                         ratPriorityEnable = RatPriority.getInstance().getEnable();
                    }
                    break;
                    case SmartCloudEventId.EVENT_ENABLE_WEAK_SIGNAL_TEST:{
                         WeakSignalTest.getInstance().setWeakSignalTestCfg((S2c_weak_signal_ctrl)msg.obj);
                         weakTestEnable = WeakSignalTest.getInstance().getEnable();
                    }
                    break;
                    case SmartCloudEventId.EVENT_ENABLE_NET_SPEED_TEST:{
                         NetSpeedTest.getInstance().setSpeedDetectCfg((S2c_speed_detection)msg.obj);
                         speedTestEnable = NetSpeedTest.getInstance().getEnable();
                    }
                    break;
                    case SmartCloudEventId.EVENT_SWITCH_CARD_REQ:{
                        final int reason = msg.arg2;
                        plmnInfos = OperatorNetworkInfo.INSTANCE.getCloudPlmnList();
                        JLog.logi(TAG,"smartcloud switch card reason: "+reason);

                        if (!isCloudNetConnected){
                            JLog.logi(TAG,"cloud net disconnected cancel this report.");
                            return;
                        }

                        if (SmartCloudSwitchReason.SWITCH_VSIM_ONE_KEY_SWITCH_DEEP == reason){
                            plmnInfos = (ArrayList<PlmnInfo>)msg.obj;
                        }

                        //启动惩罚机制
                        if (SmartCloudSwitchReason.SWITCH_VSIM_ONE_KEY_SWITCH_DEEP != reason){
                            setPunishTime(reason);
                        }

                        if (mReportSearchNetRetSub != null){
                            if (!mReportSearchNetRetSub.isUnsubscribed()){
                                mReportSearchNetRetSub.unsubscribe();
                            }
                        }

                        mReportSearchNetRetSub = Requestor.INSTANCE.requstReportSearchNetResult(plmnInfos,reason)
                                .subscribe(
                                new Action1<Object>(){
                                    @Override
                                    public void call(Object reportNetSearchRsp) {
                                        report_searchnet_result_resp rsp = (report_searchnet_result_resp)reportNetSearchRsp;
                                        JLog.logi(TAG,"report_searchnet_result_resp success:"+rsp.errorCode);
                                        mRetryCnt = 0;
                                    }
                                },
                                new Action1<Throwable>(){
                                    @Override
                                    public void call(Throwable throwable) {
                                        JLog.logi(TAG,"report_searchnet_result_resp fail retryCnt:"+mRetryCnt);
                                        if (mRetryCnt < MAX_RETRY_CNT){
                                            mRetryCnt++;
                                            mHandler.obtainMessage(SmartCloudEventId.EVENT_SWITCH_CARD_REQ,0,reason,plmnInfos)
                                                    .sendToTarget();
                                        }else {
                                            if (SmartCloudSwitchReason.SWITCH_VSIM_ONE_KEY_SWITCH_DEEP == reason){
                                                SmartCloudUserSwitchCard.getInstance().setSwichCardRet(1);
                                                recoverEnable();
                                            }
                                        }
                                    }
                                });
                    }
                    break;
                    case SmartCloudEventId.EVENT_CARD_STATE_CHANGED:
                        state = (CardStatus)msg.obj;
                        if (state == CardStatus.ABSENT) {
                            subId = subIdArry[Configuration.INSTANCE.getCloudSimSlot()];
                            stopListenPhoneState(subId);
                        }else if((state == CardStatus.READY)||(state == CardStatus.LOAD)){
                            subId = cloudSimEnabler.getCard().getSubId();
                            subIdArry[Configuration.INSTANCE.getCloudSimSlot()] = subId;
                            startListenPhoneState(subId);
                        }

                        JLog.logi(TAG,"EVENT_CARD_STATE_CHANGED card state:"+state+",subId:"+subId);

                        break;

                    case SmartCloudEventId.EVENT_SWITCH_CARD_RESULT:{
                        S2cSwitchCardResult ret = (S2cSwitchCardResult)msg.obj;
                        JLog.logi(TAG,"EVENT_SWITCH_CARD_RESULT switch card ret: "+ret.toString());

                        if (ret.eventReason == SmartCloudSwitchReason.SWITCH_VSIM_ONE_KEY_SWITCH_DEEP){
                            SmartCloudUserSwitchCard.getInstance().setSwichCardRet(ret.reason);
                            recoverEnable();
                        }

                        if(ret.reason != 0){
                            JLog.logi(TAG,"switch card ret:fail");
                            return;
                        }

                        //通知状态机换卡
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_SMART_VSIM_SWITCH_CARD, ret);
                    }
                    break;
                }
            }
        };

        ServiceManager.INSTANCE.getAccessEntry().getStatePersentOb().subscribe(new Action1<Integer>() {
            @Override
            public void call(Integer persent) {
                if (persent.intValue() <= 35){
                    JLog.logi(TAG,"close smartcloud fun persent:"+persent.intValue());
                    isCloudNetConnected = false;
                    clearSmartCloudEnableCfg();
                    RatPriority.getInstance().setEnable(false);
                    WeakSignalTest.getInstance().setEnable(false);
                    NetSpeedTest.getInstance().setEnable(false);
                    clearPunishTime();
                }
            }
        });

        SmartCloudUserSwitchCard.getInstance();
    }

    public void startListenPhoneState(Integer subId){
        if (subId == -1){
            return;
        }

        JLog.logi(TAG,"startListenPhoneState subId:"+subId.intValue());

        if (listenMap.get(subId) == null){
            SmartCloudPhoneStateListener scPhoneListener = new SmartCloudPhoneStateListener(subId.intValue(), mHandlerThread.getLooper());
            listenMap.put(subId, scPhoneListener);
            mTelephonyManager.listen(scPhoneListener, PhoneStateListener.LISTEN_CELL_LOCATION |
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE |
                    PhoneStateListener.LISTEN_SERVICE_STATE|
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
    }

    public void stopListenPhoneState(Integer subId){
        if (subId == -1){
            return;
        }

        JLog.logi(TAG,"stopListenPhoneState subId:"+subId.intValue());

        if (listenMap.get(subId) != null){
            SmartCloudPhoneStateListener scPhoneListener = (SmartCloudPhoneStateListener)listenMap.get(subId);
            mTelephonyManager.listen(scPhoneListener, PhoneStateListener.LISTEN_NONE);
            listenMap.remove(subId);
        }
    }

    private class SmartCloudPhoneStateListener extends UcPhoneStateListenerWrapper {
        public SmartCloudPhoneStateListener(int subId, Looper looper) {
            super(subId, looper);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            mServiceStateObservable.onNext(serviceState);
        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            mCellLocationObservable.onNext(location);
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            mSignalStrengthObservable.onNext(signalStrength);
        }
    }

    public void enableRatPriority(S2c_UpdatePlmnListRequest value){
        boolean ret;
        ret = value.name > 0 ? true : false;
        JLog.logd(TAG,"enableRatPriority enable:"+ret);
        mHandler.obtainMessage(SmartCloudEventId.EVENT_ENABLE_RAT_PRIORITY, value).sendToTarget();
    }

    public void enableWeakSignalTest(S2c_weak_signal_ctrl value){
        mHandler.obtainMessage(SmartCloudEventId.EVENT_ENABLE_WEAK_SIGNAL_TEST, value).sendToTarget();
    }

    public void enableNetSpeedTest(S2c_speed_detection value){
        mHandler.obtainMessage(SmartCloudEventId.EVENT_ENABLE_NET_SPEED_TEST, value).sendToTarget();
    }

    public void sendSwitchCardResult(S2cSwitchCardResult value){
        mHandler.obtainMessage(SmartCloudEventId.EVENT_SWITCH_CARD_RESULT, value).sendToTarget();
    }

    public void setSpeedLimited(){
        JLog.logd(TAG,"setSpeedLimited");
        RatPriority.getInstance().setEnable(false);
        WeakSignalTest.getInstance().setEnable(false);
        NetSpeedTest.getInstance().setEnable(false);
    }

    public void clearSpeedLimited(){
        JLog.logd(TAG,"clearSpeedLimited");
        RatPriority.getInstance().setEnable(ratPriorityEnable);
        WeakSignalTest.getInstance().setEnable(weakTestEnable);
        NetSpeedTest.getInstance().setEnable(speedTestEnable);
    }

    private void registListen(){
        if (mCardStateSub != null){
            if (!mCardStateSub.isUnsubscribed()){
                mCardStateSub.unsubscribe();
            }
        }

        mCardStateSub = ServiceManager.cloudSimEnabler.cardStatusObser().subscribe(new Action1<CardStatus>() {
            @Override
            public void call(CardStatus cardStatus) {
                    mHandler.obtainMessage(SmartCloudEventId.EVENT_CARD_STATE_CHANGED, cardStatus).sendToTarget();
            }
        });

        if (mNetStateSub != null){
            if (!mNetStateSub.isUnsubscribed()){
                mNetStateSub.unsubscribe();
            }
        }

        mNetStateSub = ServiceManager.cloudSimEnabler.netStatusObser().subscribe(new Action1<NetworkInfo.State>() {
            @Override
            public void call(NetworkInfo.State state) {
                JLog.logd(TAG,"mNetStateSub state:"+state);
                    mDataConnectionStateObservable.onNext(state);
                JLog.logd(TAG,"slotId:"+ServiceManager.cloudSimEnabler.getCard().getSlot());
                if(state == NetworkInfo.State.CONNECTED){
                    NetworkManager.INSTANCE.getNetInfo(ServiceManager.cloudSimEnabler.getCard().getSlot());
                    isCloudNetConnected = true;
                }else{
                    isCloudNetConnected = false;
                }
            }
        });

        if (mLacValueSub != null){
            if (!mLacValueSub.isUnsubscribed()){
                mLacValueSub.unsubscribe();
            }
        }

        mLacValueSub = NetworkManager.INSTANCE.getCloudSimLacObservable().subscribe(new Action1<Integer>() {
            @Override
            public void call(Integer integer) {
                JLog.logd(TAG,"lac update lac="+integer.intValue());
                if (!isCloudNetConnected){
                    return;
                }
                mLacObservable.onNext(integer);
            }
        });
    }

    private void clearPunishTime(){
        RatPriority.getInstance().setPunishment(0);
        WeakSignalTest.getInstance().setPunishment(0);
        NetSpeedTest.getInstance().setPunishment(0);
    }

    private void setPunishTime(int reason){
        switch (reason){
            case SmartCloudSwitchReason.SWITCH_VSIM_3G_PRIORITY:
                RatPriority.getInstance().setPunishment(PUNISH_TIME_30);

                WeakSignalTest.getInstance().setPunishment(PUNISH_TIME_15);
                NetSpeedTest.getInstance().setPunishment(PUNISH_TIME_15);
                break;
            case SmartCloudSwitchReason.SWITCH_VSIM_WEAK_SIGNAL:
                WeakSignalTest.getInstance().setPunishment(PUNISH_TIME_30);

                RatPriority.getInstance().setPunishment(PUNISH_TIME_15);
                NetSpeedTest.getInstance().setPunishment(PUNISH_TIME_15);
                break;
            case SmartCloudSwitchReason.SWITCH_VSIM_SPEEDTEST:
                NetSpeedTest.getInstance().setPunishment(PUNISH_TIME_30);

                RatPriority.getInstance().setPunishment(PUNISH_TIME_15);
                WeakSignalTest.getInstance().setPunishment(PUNISH_TIME_15);
                break;
            default:
                break;
        }
    }

    public void oneKeySwitchCardDeep(){
        pauseEnable();

        SmartCloudUserSwitchCard.getInstance().deepSwitchCard();
    }

    public boolean isSignalStrengthWell(){
        NetInfo into = NetworkManager.INSTANCE.getNetInfo(Configuration.INSTANCE.getCloudSimSlot());
        if (into.getSignal() == SIGNAL_STRENGTH_UC_GOOD
                || into.getSignal() == SIGNAL_STRENGTH_UC_GREAT
                || into.getSignal() == SIGNAL_STRENGTH_UC_GREAT_MORE){
            JLog.logd(TAG,"cloud sim signal strength is well.");
            return true;
        }else{
            JLog.logd(TAG,"cloud sim signal strength is bad.");
            return false;
        }
    }

    public void recoverEnable(){
        JLog.logd(TAG,"recoverEnable ratPriorityEnable="
                +ratPriorityEnable+",weakTestEnable="+weakTestEnable+",speedTestEnable="+speedTestEnable);
        RatPriority.getInstance().setEnable(ratPriorityEnable);
        WeakSignalTest.getInstance().setEnable(weakTestEnable);
        NetSpeedTest.getInstance().setEnable(speedTestEnable);
    }

    private void pauseEnable(){
        ratPriorityEnable = RatPriority.getInstance().getEnable();
        weakTestEnable = WeakSignalTest.getInstance().getEnable();
        speedTestEnable = NetSpeedTest.getInstance().getEnable();
        RatPriority.getInstance().setEnable(false);
        WeakSignalTest.getInstance().setEnable(false);
        NetSpeedTest.getInstance().setEnable(false);
        JLog.logd(TAG,"pauseEnable ratPriorityEnable="
                +ratPriorityEnable+",weakTestEnable="+weakTestEnable+",speedTestEnable="+speedTestEnable);
    }

    private void clearSmartCloudEnableCfg(){
        ratPriorityEnable = false;
        weakTestEnable = false;
        speedTestEnable = false;
    }
}
