package com.ucloudlink.refact.business.softsim.download;

import android.annotation.CallSuper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.ucloudlink.framework.protocol.protobuf.SimpleLoginRsp;
import com.ucloudlink.framework.protocol.protobuf.SoftsimFlowUploadRsp;
import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.access.restore.RunningStates;
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo;
import com.ucloudlink.refact.business.routetable.RequestGetRouteTable;
import com.ucloudlink.refact.business.routetable.RouteTableManager;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.business.softsim.CardRepository;
import com.ucloudlink.refact.business.softsim.download.remote.RequesteAction;
import com.ucloudlink.refact.business.softsim.download.remote.TransceiverAdapter;
import com.ucloudlink.refact.business.softsim.download.struct.DownloadReqInfo;
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimBinInfoSingleReq;
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimUpdateParam;
import com.ucloudlink.refact.business.softsim.manager.SoftsimManager;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimBinLocalFile;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;
import com.ucloudlink.refact.channel.enabler.datas.Card;
import com.ucloudlink.refact.channel.enabler.datas.CardType;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum;
import com.ucloudlink.refact.utils.EncryptUtils;
import com.ucloudlink.refact.utils.JLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okio.BufferedSink;
import okio.Okio;
import rx.Single;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

import static com.ucloudlink.refact.utils.JLog.logk;

/**
 * Created by shiqianhua on 2016/12/1.
 */

public class SoftsimDownloadState extends StateMachine {
    private final static String GLOCALME_WORD = "glocalme-service-11-23_B";
    private Context context;

    private TransceiverAdapter transceiverAdapter;

    private static final int SOFTSIM_DL_EV_BASE              = 0;
    private static final int SOFTSIM_DL_EV_DOWNLOAD_REQ      = SOFTSIM_DL_EV_BASE + 1;
    private static final int SOFTSIM_DL_EV_DOWNLOAD_STOP     = SOFTSIM_DL_EV_BASE + 2;
    private static final int SOFTSIM_DL_EV_LOGOUT_RSP        = SOFTSIM_DL_EV_BASE + 3;
    private static final int SOFTSIM_DL_EV_LOGIN_RSP         = SOFTSIM_DL_EV_BASE + 4;
    private static final int SOFTSIM_DL_EV_RELOGIN_REQ       = SOFTSIM_DL_EV_BASE + 5;
    private static final int SOFTSIM_DL_EV_ALL_COMPLETE      = SOFTSIM_DL_EV_BASE + 6;
    private static final int SOFTSIM_DL_EV_USER_CANCEL       = SOFTSIM_DL_EV_BASE + 7;
    private static final int SOFTSIM_DL_EV_LOGIN_FAIL        = SOFTSIM_DL_EV_BASE + 8;
    private static final int SOFTSIM_DL_EV_CHECK_NETWORK_RSP = SOFTSIM_DL_EV_BASE + 9;
    private static final int SOFTSIM_DL_EV_LOOP_IP_RSP       = SOFTSIM_DL_EV_BASE + 10;
    private static final int SOFTSIM_DL_EV_LOOP_IP_DELAY     = SOFTSIM_DL_EV_BASE + 11;
    private static final int SOFTSIM_DL_EV_GET_SOCKET_RESULT = SOFTSIM_DL_EV_BASE + 12;
    private static final int SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG = SOFTSIM_DL_EV_BASE + 13;

    private static final int SOFTSIM_UP_EV_START    = SOFTSIM_DL_EV_BASE + 51;
    private static final int SOFTSIM_UP_EV_STOP     = SOFTSIM_DL_EV_BASE + 52;
    private static final int SOFTSIM_UP_EV_COMPLETE = SOFTSIM_DL_EV_BASE + 53;

    public static final int SECURITY_SOCKET_FAIL    = SOFTSIM_DL_EV_BASE + 101;
    public static final int SECURITY_SOCKET_TIMEOUT = SOFTSIM_DL_EV_BASE + 102;

    public static final int SOFTSIM_FLOW_UPLOAD_START      = SOFTSIM_DL_EV_BASE + 201;
    public static final int SOFTSIM_FLOW_UPLOAD_STOP       = SOFTSIM_DL_EV_BASE + 202;
    public static final int SOFTSIM_FLOW_UPLOAD_COMPLETE   = SOFTSIM_DL_EV_BASE + 203;
    public static final int SOFTSIM_FLOW_LOGOUT_REQ        = SOFTSIM_DL_EV_BASE + 204;
    public static final int SOFTSIM_FLOW_UPLOAD_START_NEXT = SOFTSIM_DL_EV_BASE + 205;

    public static final int DEUBG_BASE            = 100000;
    public static final int DEUBG_EV_FORCE_LOGOUT = DEUBG_BASE + 1;
    public static final int DEUBG_EV_UPLOAD_FLOW  = DEUBG_BASE + 2;

    private State mParentState    = new ParentState();
    private State mDefaultState   = new DefaultState();
    private State mInitState      = new InitState();
    private State mPreLoginState  = new PreLoginState();
    private State mLoginState     = new LoginState();
    private State mInServiceState = new InServiceState();
    private State mLogoutState    = new LogoutState();

    private String username;
    private String passwd;

    private State          curState       = null;
    private State          lastState      = null;
    private State          nextState      = null;
    private SoftsimManager softsimManager = null;

    private ArrayList<FlowOrder>      orderRequesUndotList  = new ArrayList<>();
    private ArrayList<SoftsimRequest> orderRequestDoingList = new ArrayList<>();

    private SimpleLoginRsp mLoginRsp = null;

    private String mSessionId;
    private boolean isNeedRelogin = false;

    private Subscription loginSub;
    private Subscription logoutSub;

    private static final int LOGIN_RETRY_COUNT_MAX = 5;
    private              int loginRetryCount       = 0;

    private int    terErrcode;
    private String terErrMsg;

    private SoftsimUpdateState softsimUpdateState;
    private SoftsimUpdateParam mSoftsimUpdateParam = null;

    private boolean needUploadFlow = false;

    // debug
    private boolean donotLogout = false;



    public SoftsimDownloadState(Context ctx, SoftsimManager softsimManager) {
        super("SoftsimDownloadState");
        context = ctx;
        this.softsimManager = softsimManager;
        addState(mParentState);
        addState(mDefaultState, mParentState);
        addState(mInitState, mParentState);
        addState(mPreLoginState, mInitState);
        addState(mLoginState, mInitState);
        addState(mInServiceState, mInitState);
        addState(mLogoutState, mInitState);

        setInitialState(mParentState);
        //        setDbg(true);
        start();
    }

    private static final String PREFERENCE_SOFTSIM_NAME = "PREFERENCE_SOFTSIM_NAME";
    private static final String SOFTSIM_USERNAME        = "SOFTSIM_USERNAME";
    private static final String SOFTSIM_PASSWD          = "SOFTSIM_PASSWD";

//    private void saveUsername(String username) {
//        SharedPreferencesUtils.putString(context, PREFERENCE_SOFTSIM_NAME, SOFTSIM_USERNAME, username);
//    }

//    private void savePassword(String passwd) {
//        //SharedPreferencesUtils.putString(context, PREFERENCE_SOFTSIM_NAME, SOFTSIM_PASSWD, passwd);
//        SharedPreferencesUtils.putString(context, PREFERENCE_SOFTSIM_NAME, SOFTSIM_PASSWD,
//                EncryptUtils.encyption(context,passwd,PREFERENCE_SOFTSIM_NAME,GLOCALME_WORD));
//
//    }

//    private String getUsername() {
//        return SharedPreferencesUtils.getString(context, PREFERENCE_SOFTSIM_NAME, SOFTSIM_USERNAME, "");
//    }

//    private String getPasswd() {
//        String getValue = SharedPreferencesUtils.getString(context, PREFERENCE_SOFTSIM_NAME, SOFTSIM_PASSWD, "");
//        return EncryptUtils.decyption(context,getValue,PREFERENCE_SOFTSIM_NAME,GLOCALME_WORD);
//        //return SharedPreferencesUtils.getString(context, PREFERENCE_SOFTSIM_NAME, SOFTSIM_PASSWD, "");
//    }

    private void innerStopUpdate(int result, String msg) {
        if (mSoftsimUpdateParam != null) {
            softsimUpdateState.stopUpdate();
            mSoftsimUpdateParam = null;
            sendMessage(SOFTSIM_UP_EV_COMPLETE, result, 0, msg);
        }
    }

    private void safeUndescribe(Subscription sub) {
        if (sub != null && !sub.isUnsubscribed()) {
            JLog.logd("tRoute safeUndescribe:"+sub.toString());
            sub.unsubscribe();
        }
    }

    private void transToNextState(State next) {
        logk("state change: " + curState.getName() + " -> " + next.getName());
        nextState = next;
        transitionTo(next);
    }

    private class UpdateSoftsimStateImpl extends SoftsimUpdateState {
        public UpdateSoftsimStateImpl(Context ctx, SoftsimManager manager, Looper looper) {
            super(ctx, manager, looper);
        }

        @Override
        public void sendUpdateMsg(String username, String mcc, String mnc, ArrayList<SoftsimLocalInfo> sims, String curImsi) {
            if (mSessionId == null) {
                JLog.loge("session is null, so stop it");
                stopUpdate();
                innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_SESSION_INVALID(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_SESSION_INVALID()));
            } else {
                Single<Object> single = RequesteAction.requestSoftsimUpdateSimple(username, mcc, mnc, sims, curImsi, mSessionId, transceiverAdapter, 35);
                setUpdateMsgOb(single);
            }
        }

        @Override
        public void sendGetSimInfoMsg(ArrayList<String> sims) {
            if (mSessionId == null) {
                JLog.loge("session is null, so stop it");
                stopUpdate();
                innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_SESSION_INVALID(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_SESSION_INVALID()));
            } else {
                Single<Object> single = RequesteAction.requestGetSoftsimInfo(sims, mSessionId, transceiverAdapter, 35);
                setGetSimInfoMsgOb(single);
            }
        }

        @Override
        public void sendGetSimBinMsg(ArrayList<SoftsimBinInfoSingleReq> sims) {
            if (mSessionId == null) {
                JLog.loge("session is null, so stop it");
                stopUpdate();
                innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_SESSION_INVALID(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_SESSION_INVALID()));
            } else {
                Single<Object> single = RequesteAction.reqeustSoftsimBinFile(sims, mSessionId, transceiverAdapter, 35);
                setGetSimBinMsgOb(single);
            }
        }

        @Override
        public void onResult(int result, int stage) {
            JLog.logd("update over!!!" + result + " " + stage);
            if (result == 0) {
                JLog.logd("update succ!!!");
                innerStopUpdate(0, "");
            } else {
                ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByCode(result);
                JLog.loge("udpate softsim failed!!! " + info);
                if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                    sendMessage(SOFTSIM_DL_EV_RELOGIN_REQ);
                } else if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT || info.getAction() == ErrorCode.ErrActType.ACT_TER) {
                    innerStopUpdate(info.getCode(), info.getMsg());
                } else {
                    innerStopUpdate(info.getCode(), info.getMsg());
                }
            }
            ServiceManager.accessEntry.getAccessState().updateCommMessage(9,Integer.toString(result));
        }
    }

    private class ParentState extends State {
        @Override
        public void enter() {
            super.enter();
            curState = this;
            softsimUpdateState = new UpdateSoftsimStateImpl(context, softsimManager, Looper.myLooper());
            transToNextState(mDefaultState);
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                default:
                    return NOT_HANDLED;
            }
            //            return HANDLED;
        }
    }

    private class DefaultState extends State {
        private boolean firstEnter = true;

        @Override
        public void enter() {
            curState = this;
            if (!firstEnter) {
                // TODO: 2017/6/3 need set errcode to server!
                stopAllRequest(terErrcode, terErrMsg);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SOFTSIM_DL_EV_DOWNLOAD_REQ:
                    DownloadReqInfo info = (DownloadReqInfo) msg.obj;
                    username = info.getUsername();
                    passwd = info.getPassword();
//                    saveUsername(username);
//                    savePassword(passwd);
                    addOrderToUndoList(info.getOrder());
                    transToNextState(mPreLoginState);
                    break;
                case SOFTSIM_UP_EV_START:
                    //软卡在位时，不进行更新
                    if(ServiceManager.seedCardEnabler.getCard().getCardType() == CardType.SOFTSIM){
                        JLog.logd("SOFTSIM_UP_EV_START while SOFTSIM");
                        break;
                    }
                    if (TextUtils.isEmpty(RunningStates.getUserName()) || TextUtils.isEmpty(RunningStates.getPassWord())) {
                        JLog.loge("username or passwd is null!");
                    } else {
                        username = RunningStates.getUserName();
                        passwd = RunningStates.getPassWord();
                        JLog.logd("username: " +RunningStates.getUserName());
                        mSoftsimUpdateParam = (SoftsimUpdateParam) msg.obj;
                        mSoftsimUpdateParam.username = RunningStates.getUserName();
                        transToNextState(mPreLoginState);
                    }
                    break;
                case SOFTSIM_FLOW_UPLOAD_START:
                    boolean isVsimServiceOK = ServiceManager.accessEntry.getAccessState().isVsimServiceOK();
                    NetworkInfo.State cloudSimNetState = ServiceManager.cloudSimEnabler.getNetState();
                    JLog.logd("SCFlowLog, SoftSim.DefaultState -> recv SOFTSIM_FLOW_UPLOAD_START" +
                            ", cloudSimNetState = "+cloudSimNetState+", isVsimServiceOK = "+isVsimServiceOK);
                    if(isVsimServiceOK && cloudSimNetState == NetworkInfo.State.CONNECTED){
                        ArrayList list = softsimManager.getFirstSoftsimState();
                        if (list == null) {
                            JLog.loge("SCFlowLog no need to upload softsimflow");
                        } else {
                            if (TextUtils.isEmpty(RunningStates.getUserName()) || TextUtils.isEmpty(RunningStates.getPassWord())) {
                                JLog.loge("SCFlowLog username or passwd is null!");
                            } else {
                                username = RunningStates.getUserName();
                                passwd = RunningStates.getPassWord();
                                JLog.logd("SCFlowLog username: " + RunningStates.getUserName());
                                needUploadFlow = true;
                                transToNextState(mPreLoginState);
                            }
                        }
                    } else {

                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            lastState = this;
            firstEnter = false;
        }
    }

    private class InitState extends State {
        @Override
        public void enter() {
            curState = this;
            startCreateSocket(context);
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SECURITY_SOCKET_FAIL:
                    JLog.logd("recv SECURITY_SOCKET_FAIL");
                    stopAllRequest(ErrorCode.INSTANCE.getLOCAL_SECURITY_FAIL(), "security error " + msg.obj);
                    transToNextState(mDefaultState);
                    break;
                case SOFTSIM_UP_EV_STOP:
                    if (mSoftsimUpdateParam != null) {
                        mSoftsimUpdateParam = null;
                    }
                    JLog.logd("orderRequesUndotList.size() : " + orderRequesUndotList.size());
                    if (orderRequesUndotList.size() == 0) {
                        transToNextState(mDefaultState);
                    }
                    break;
                case SOFTSIM_FLOW_UPLOAD_START:
                    boolean isVsimServiceOK = ServiceManager.accessEntry.getAccessState().isVsimServiceOK();
                    NetworkInfo.State cloudSimNetState = ServiceManager.cloudSimEnabler.getNetState();
                    JLog.logd("SCFlowLog, SoftSim.DefaultState -> recv SOFTSIM_FLOW_UPLOAD_START" +
                            ", cloudSimNetState = "+cloudSimNetState+", isVsimServiceOK = "+isVsimServiceOK);
                    if(isVsimServiceOK && cloudSimNetState == NetworkInfo.State.CONNECTED){
                        ArrayList list = softsimManager.getFirstSoftsimState();
                        JLog.logd("SCFlowLog processMessage: list==null ? "+(list==null));
                        if (list != null) {
                            needUploadFlow = true;
                        }
                    } else {
                        needUploadFlow = false;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            stopSocket();
            lastState = this;
        }
    }

    private class PreLoginState extends State {
        private Subscription loopIpSub;
        private int loopUsableIpAction_count = 0;//ip轮询次数

        private void loopUsableIpAction() {
            loopUsableIpAction_count = loopUsableIpAction_count + 1;
            JLog.logd("tRoute loopUsableIpAction(), count=" + loopUsableIpAction_count);
            loopIpSub = RouteTableManager.INSTANCE.getRouteTableFromRCIfNeed(RouteTableManager.RT_SOCKET_TIME_OUT, username,"SoftsimDownloadState")
                    .timeout(RouteTableManager.GET_RT_TOTAL_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe(new Action1<Object>() {
                        @Override
                        public void call(Object o) {
                            JLog.logd("tRoute loopUsableIpAction success");
                            safeUndescribe(loopIpSub);
                            sendMessage(SOFTSIM_DL_EV_LOOP_IP_RSP, 0, 0, o);
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            JLog.loge("tRoute loopUsableIpAction Fail:" + throwable.getMessage());
                            safeUndescribe(loopIpSub);
                            sendMessage(SOFTSIM_DL_EV_LOOP_IP_RSP, -1, 0, throwable);
                        }
                    });
        }

        @Override
        public void enter() {
            curState = this;
            loopUsableIpAction_count = 0;
            loopUsableIpAction();
        }

        @Override
        public void exit() {
            lastState = this;
            loopUsableIpAction_count = 0;
            RequestGetRouteTable.getRequestGetRouteTable().stopSocket();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SOFTSIM_DL_EV_LOOP_IP_RSP:
                    safeUndescribe(loopIpSub);
                    if(msg.arg1 == 0){
                        transToNextState(mLoginState);
                    } else {
                        Throwable t = (Throwable) msg.obj;
                        String errorMsg = t.getMessage();
                        if (errorMsg == null) {
                            if (t instanceof TimeoutException)
                                errorMsg = "Fail:Ass and RC IP Socket Timeout";
                            else
                                errorMsg = "UNKNOWN";
                        }
                        JLog.loge("tRoute SOFTSIM_DL_EV_LOOP_IP_RSP errorMsg="+errorMsg);

                        if(errorMsg.equalsIgnoreCase("AccessState Running")){
                            //云卡正在启动,停止下载软卡
                            terErrcode = ErrorCode.INSTANCE.getLOCAL_GET_ROUTE_TABLE_FAIL();
                            terErrMsg = "Get Route Table Fail:"+errorMsg+", stop!";
                            transToNextState(mDefaultState);
                            break;
                        }
                        if (errorMsg.equalsIgnoreCase("Fail:Fail:Socket SecureError")){
                            //安全错误，停止并提示1024错误
                            terErrcode = ErrorCode.INSTANCE.getLOCAL_SECURITY_FAIL();
                            terErrMsg = "Get Route Table Fail:"+errorMsg+", stop!";
                            transToNextState(mDefaultState);
                            break;
                        }
                        if (loopUsableIpAction_count <= 2) {

                            if(errorMsg.equalsIgnoreCase("Network unuse waite")){
                                //延迟30s
                                sendMessageDelayed(SOFTSIM_DL_EV_LOOP_IP_DELAY, TimeUnit.SECONDS.toMillis(30));
                            }else{
                                loopUsableIpAction();
                            }
                        } else {
                            if (ServiceManager.productApi.getProductType() == ProductTypeEnum.MIFI){
                                //u3c 继续重试
                                //延迟10s
                                sendMessageDelayed(SOFTSIM_DL_EV_LOOP_IP_DELAY, TimeUnit.SECONDS.toMillis(10));
//                                loopUsableIpAction();
                            }else {
                                ServerRouter.INSTANCE.initIpByCurrentMode();//初始化ip
                                terErrcode = ErrorCode.INSTANCE.getLOCAL_GET_ROUTE_TABLE_FAIL();
                                terErrMsg = "Get Route Table Fail";
                                transToNextState(mDefaultState);
                            }
                        }
                    }
                    break;

                case SOFTSIM_DL_EV_DOWNLOAD_REQ:
                    DownloadReqInfo info = (DownloadReqInfo) msg.obj;
                    if (!info.getUsername().equals(username)) {
                        JLog.logd("new user name come!!! " + username + " -> " + info.getUsername());
                        username = info.getUsername();
                        passwd = info.getPassword();
                        stopAllRequest(ErrorCode.INSTANCE.getSOFTSIM_DL_CHANGE_NEW_USER(), "change new user!");
                        addOrderToUndoList(info.getOrder());
                        //transToNextState(mLoginState); //不跳转，可能路由还没完成。
                    } else {
                        addOrderToUndoList(info.getOrder());
                    }
                    innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START()));
                    break;
                case SOFTSIM_DL_EV_LOOP_IP_DELAY:
                    JLog.logd("tRoute SOFTSIM_DL_EV_LOOP_IP_DELAY");
                    loopUsableIpAction();
                    break;
                case SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG:
                    JLog.logd("SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG stop!");
                    if (mSoftsimUpdateParam != null) {
                        mSoftsimUpdateParam = null;
                    }
                    transToNextState(mDefaultState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class LoginState extends State {
        private Subscription subscribeSocket;
        private int getSocketStatusCount = 0;//重试次数

        @Override
        public void enter() {
            curState = this;
            loginRetryCount = 0;
            isNeedRelogin = false;
            getSocketStatusCount = 0;
            getSocketStatus();
        }

        //监听socket状态，socket成功后再登陆。
        private void getSocketStatus() {
            getSocketStatusCount = getSocketStatusCount+1;
            JLog.logd("tRoute getSocketStatus count="+getSocketStatusCount);

            List<String> assList = RouteTableManager.INSTANCE.getLocalAssIPList(ServerRouter.INSTANCE.getCurrent_mode());
            String assIP = ServerRouter.INSTANCE.getCurrent_AssIp();
            //目前ip不是ass ip则初始化设为ass ip
            if (assList != null && !assList.contains(assIP)){
                JLog.logd("tRoute getSocketStatus current ip != ass ip , init!");
                ServerRouter.INSTANCE.initIpByCurrentMode();
            }
            startCreateSocket(context);
            subscribeSocket = ServiceManager.INSTANCE.getTransceiver().statusObservable(ServerRouter.Dest.ASS)
                    .asObservable()
                    .filter(new Func1<String, Boolean>() {
                        @Override
                        public Boolean call(String s) {
                            JLog.logd("tRoute getSocketStatus,Socket statusObservable="+s);
                            return s.equalsIgnoreCase("SocketConnected");
                        }
                    })
                    .timeout(RouteTableManager.RT_SOCKET_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe(
                            new Subscriber<String>() {
                                @Override
                                public void onCompleted() {
                                    JLog.loge("tRoute getSocketStatus ,onCompleted");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    safeUndescribe(subscribeSocket);
                                    JLog.loge("tRoute getSocketStatus ,onError Throwable:" + e.getMessage());
                                    sendMessage(SOFTSIM_DL_EV_GET_SOCKET_RESULT, 0, -1, e.getMessage());
                                }

                                @Override
                                public void onNext(String s) {
                                    JLog.logd("tRoute getSocketStatus,onNext Socket:" + s);
                                    safeUndescribe(subscribeSocket);
                                    sendMessage(SOFTSIM_DL_EV_GET_SOCKET_RESULT, 0, 0, s);
                                }
                            }
                    );
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SOFTSIM_DL_EV_DOWNLOAD_REQ:
                    DownloadReqInfo info = (DownloadReqInfo) msg.obj;
                    if (!info.getUsername().equals(username)) {
                        JLog.logd("new user name come!!! " + username + " -> " + info.getUsername());
                        username = info.getUsername();
                        passwd = info.getPassword();
                        stopAllRequest(ErrorCode.INSTANCE.getSOFTSIM_DL_CHANGE_NEW_USER(), "change new user!");
                        addOrderToUndoList(info.getOrder());
                        transToNextState(mLoginState);
                    } else {
                        addOrderToUndoList(info.getOrder());
                    }
                    innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START()));
                    break;
                case SOFTSIM_DL_EV_LOGIN_RSP:
                    processLoginRsp(msg.arg1, msg.obj);
                    break;
                case SOFTSIM_DL_EV_RELOGIN_REQ:
                    startLogin(username, passwd);
                    break;
                case SOFTSIM_DL_EV_USER_CANCEL:
                    stopAllRequest(ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL(), "user cancel");
                    if (mSoftsimUpdateParam == null) {
                        transToNextState(mDefaultState);
                    }
                    break;
                case SOFTSIM_DL_EV_LOGIN_FAIL:
                    ErrorCode.ErrCodeInfo errInfo = (ErrorCode.ErrCodeInfo) msg.obj;
                    stopAllRequest(errInfo.getCode(), errInfo.getMsg());
                    transToNextState(mDefaultState);
                    break;
                case SOFTSIM_DL_EV_GET_SOCKET_RESULT:
                    safeUndescribe(subscribeSocket);

                    if(msg.arg2 == 0){
                        startLogin(username, passwd);
                    }else{

                        if (getSocketStatusCount < 2){
                            //失败重试一次
                            getSocketStatus();
                        }else {
                            startLogin(username, passwd);
                        }
                    }
                    break;
                case SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG:
                    JLog.logd("SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG stop!");
                    if (mSoftsimUpdateParam != null) {
                        mSoftsimUpdateParam = null;
                    }
                    transToNextState(mDefaultState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            lastState = this;
            getSocketStatusCount = 0;
            safeUndescribe(loginSub);
        }
    }

    private class InServiceState extends State {
        @Override
        public void enter() {
            curState = this;
            startProcRequest();
            if (mSoftsimUpdateParam != null) {
                softsimUpdateState.startUpdate(mSoftsimUpdateParam);
            }
            if (needUploadFlow) {
                startUploadSoftsimFlowAction();
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SOFTSIM_DL_EV_DOWNLOAD_REQ:
                    DownloadReqInfo info = (DownloadReqInfo) msg.obj;
                    if (!info.getUsername().equals(username)) {
                        JLog.logd("new user name come!!! " + username + " -> " + info.getUsername());
                        username = info.getUsername();
                        passwd = info.getPassword();
                        stopAllRequest(ErrorCode.INSTANCE.getSOFTSIM_DL_CHANGE_NEW_USER(), "change new user!");
                        addOrderToUndoList(info.getOrder());
                        isNeedRelogin = true;
                        transToNextState(mLogoutState);
                    } else {
                        addOrderToUndoList(info.getOrder());
                        startProcRequest();
                    }
                    innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START()));
                    break;
                case SOFTSIM_DL_EV_ALL_COMPLETE:
                    JLog.logd("recv SOFTSIM_DL_EV_ALL_COMPLETE");
                    if (isAllFunctionComplited()) {
                        transToNextState(mLogoutState);
                    }
                    break;
                case SOFTSIM_DL_EV_USER_CANCEL:
                    stopAllRequest(ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL(), "user cancel");
                    if (isAllFunctionComplited()) {
                        transToNextState(mLogoutState);
                    }
                    break;
                case SOFTSIM_UP_EV_COMPLETE:
                    JLog.logd("recv SOFTSIM_UP_EV_COMPLETE");
                    mSoftsimUpdateParam = null;
                    softsimUpdateOver(msg.arg1, (String) msg.obj);
                    if (isAllFunctionComplited()) {
                        transToNextState(mLogoutState);
                    }
                    break;
                case SOFTSIM_UP_EV_STOP:
                    JLog.logd("recv SOFTSIM_UP_EV_STOP");
                    innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_USER_CANCEL(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_USER_CANCEL()));
                    break;
                case SOFTSIM_FLOW_UPLOAD_START:
                    boolean isVsimServiceOK = ServiceManager.accessEntry.getAccessState().isVsimServiceOK();
                    NetworkInfo.State cloudSimNetState = ServiceManager.cloudSimEnabler.getNetState();
                    JLog.logd("SCFlowLog, SoftSim.DefaultState -> recv SOFTSIM_FLOW_UPLOAD_START" +
                            ", cloudSimNetState = "+cloudSimNetState+", isVsimServiceOK = "+isVsimServiceOK);
                    if(isVsimServiceOK && cloudSimNetState == NetworkInfo.State.CONNECTED){
                        if (isUploadSoftsimFlow()) {
                            JLog.logd("SCFlowLog softsim state is still uploading!");
                        } else {
                            startUploadSoftsimFlowAction();
                        }
                    } else {

                    }
                    break;
                case SOFTSIM_FLOW_UPLOAD_START_NEXT:
                    startUploadSoftsimFlowAction();
                    break;
                case SOFTSIM_FLOW_UPLOAD_COMPLETE:
                    if (isAllFunctionComplited()) {
                        transToNextState(mLogoutState);
                    }
                    break;
                case SOFTSIM_DL_EV_RELOGIN_REQ:
                    for(SoftsimRequest request: orderRequestDoingList){
                        orderRequesUndotList.add(request.getFlowOrder());
                    }
                    orderRequestDoingList.clear();
                    transToNextState(mLoginState);
                    break;
                case SOFTSIM_FLOW_LOGOUT_REQ:
                    transToNextState(mLogoutState);
                    break;
                case SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG:
                    JLog.logd("SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG stop!");
                    transToNextState(mLogoutState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            lastState = this;
        }
    }

    private class LogoutState extends State {
        @Override
        public void enter() {
            curState = this;
            if (donotLogout) {
                JLog.loge("donot logout!!!");
                return;
            }
            stopUploadSoftsimFlow();
            startLogout();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SOFTSIM_DL_EV_LOGOUT_RSP:
                    JLog.logd("recv logout rsp: " + msg.arg1);
                    if (isNeedRelogin) {
                        transToNextState(mLoginState);
                    } else {
                        transToNextState(mDefaultState);
                    }
                    break;
                case SOFTSIM_DL_EV_USER_CANCEL:
                    JLog.logd("SOFTSIM_DL_EV_USER_CANCEL!! do nothing!");
                    break;
                case SOFTSIM_DL_EV_DOWNLOAD_REQ:
                    DownloadReqInfo info = (DownloadReqInfo) msg.obj;
                    if (isNeedRelogin) {
                        JLog.logd("new user name come!!! " + username + " -> " + info.getUsername());
                        stopAllRequest(ErrorCode.INSTANCE.getSOFTSIM_DL_CHANGE_NEW_USER(), "change new user!");
                    }
                    username = info.getUsername();
                    passwd = info.getPassword();
                    addOrderToUndoList(info.getOrder());
                    isNeedRelogin = true;
                    innerStopUpdate(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START(), ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getSOFTSIM_UP_DL_START()));
                    break;
                // start debug
                case DEUBG_EV_FORCE_LOGOUT:
                    JLog.logd("recv DEUBG_EV_FORCE_LOGOUT value " + donotLogout);
                    if (donotLogout) {
                        startLogout();
                    }
                    break;
                case DEUBG_EV_UPLOAD_FLOW:
                    startUploadSoftsimFlowAction();
                    break;
                // end debug
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            lastState = this;
            if (logoutSub != null && !logoutSub.isUnsubscribed()) {
                logoutSub.unsubscribe();
            }
        }
    }

    private void startLogin(String username, String passwd) {
        safeUndescribe(loginSub);
        loginRetryCount++;
        loginSub = RequesteAction.requestSimpleLogin(username, passwd, Configuration.INSTANCE.getImei(ServiceManager.appContext), ServiceManager.sysModel.getDeviceName(), "version", transceiverAdapter, 100) // TODO: 2017/1/17 test
                .timeout(35, TimeUnit.SECONDS).subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        JLog.logd("login rsp: " + o);
                        sendMessage(SOFTSIM_DL_EV_LOGIN_RSP, 0, 0, o);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        sendMessage(SOFTSIM_DL_EV_LOGIN_RSP, -1, 0, throwable);
                    }
                });
    }

    private void processLoginRsp(int arg, Object o) {
        if (arg == 0) {
            JLog.logd("login succ!!!");
            //// TODO: 2016/12/29 get param!! here!!
            try {
                mLoginRsp = (SimpleLoginRsp) o;
            } catch (Exception e) {
                JLog.logd("cast SimpleLoginRsp error!!!");
                JLog.loge(e.getMessage());
                return;
            }
            mLoginRsp = (SimpleLoginRsp) o;
            JLog.logd("recv login rsp:" + mLoginRsp);
            mSessionId = mLoginRsp.sessionId;
            ProtoPacketUtil.getInstance().setSession(mLoginRsp.sessionId);
            transToNextState(mInServiceState);
        } else {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);
            JLog.logd("login fail!" + err);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.logd("get login errinfo:" + info);
            if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("need logout!");
                sendMessage(SOFTSIM_DL_EV_LOGIN_FAIL, 0, 0, info);
            } else {
                if (loginRetryCount > LOGIN_RETRY_COUNT_MAX) {
                    JLog.loge("login retry max!!! " + LOGIN_RETRY_COUNT_MAX + " so logout!");
                    sendMessage(SOFTSIM_DL_EV_LOGIN_FAIL, 0, 0, info);
                } else {
                    sendMessageDelayed(SOFTSIM_DL_EV_RELOGIN_REQ, TimeUnit.SECONDS.toMillis(3));
                }
            }
        }
    }

    private void startLogout() {
        if (logoutSub != null && !logoutSub.isUnsubscribed()) {
            logoutSub.unsubscribe();
        }
        logoutSub = RequesteAction.requestSimpleLogout(0, mSessionId, transceiverAdapter, 4).
                timeout(20, TimeUnit.SECONDS).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                JLog.logd("logout succ!!!");
                sendMessage(SOFTSIM_DL_EV_LOGOUT_RSP, 0, 0, o);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (throwable instanceof TimeoutException) {
                    throwable = new Throwable("TIMEOUT: business timeout!");
                }
                JLog.logd("logout failed!" + throwable.getMessage());
                sendMessage(SOFTSIM_DL_EV_LOGOUT_RSP, -1, 0, throwable);
            }
        });
    }

    private void addOrderToUndoList(ArrayList<FlowOrder> orders) {
        if (orderRequesUndotList == null) {
            orderRequesUndotList = new ArrayList<>();
        }

        for(FlowOrder wo: orders){
            boolean isFind = false;
            for (FlowOrder o : orderRequesUndotList){
                if(o.getOrderId().equals(wo.getOrderId())){
                    JLog.logd("order " + wo.getOrderId() + " is aready in undolist!");
                    isFind = true;
                    break;
                }
            }
            if(!isFind){
                orderRequesUndotList.add(wo);
            }
        }
    }

    private void startProcRequest() {
        JLog.logd("startProcRequest");
        int count = 0;
        ArrayList<SoftsimRequest> tmpList = new ArrayList<SoftsimRequest>();
        Iterator<FlowOrder> it = orderRequesUndotList.iterator();
        while (it.hasNext()) {
            FlowOrder order = it.next();
            boolean find = false;
            for (SoftsimRequest sr : orderRequestDoingList) {
                if (order.equals(sr.getOrder())) {
                    find = true;
                    JLog.logd("order " + order + " is downloading, please wait!");
                    break;
                }
            }
            it.remove();
            if (!find) {
                JLog.logd("start request:" + ++count + " order:" + order + " username:" + username);
                SoftsimRequest request = new SoftsimRequest(username, order, mSessionId, transceiverAdapter) {
                    @Override
                    public void onSucc(String order, OrderInfo orderInfo, ArrayList<SoftsimLocalInfo> sims, ArrayList<SoftsimBinLocalFile> bins) {
                        // set to database

                        ArrayList<String> failInfo = new ArrayList();
                        for (SoftsimLocalInfo info : sims) {
                            String binname = info.getPlmnBin();
                            String fileName = "00001" + binname.substring(binname.length() - 12, binname.length());
                            // store fin bin
                            if (bins != null && bins.size() != 0) {
                                for (SoftsimBinLocalFile binFile : bins) {
                                    if (binFile.getRef().equals(info.getPlmnBin())) {
                                        String dirName = Configuration.INSTANCE.getSimDataDir() + fileName + ".bin";
                                        JLog.logd("write softsim bin to file:" + dirName);
                                        File file = new File(dirName);
                                        try {
                                            BufferedSink writer = Okio.buffer(Okio.sink(file));
                                            writer.write(binFile.getData());
                                            writer.flush();
                                            writer.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                }
                            }
                            final Card sCard = new Card();
                            sCard.setCardType(CardType.SOFTSIM);
                            sCard.setImsi(info.getImsi());
                            sCard.setKi(info.getKi());
                            sCard.setOpc(info.getOpc());
                            sCard.setImageId(fileName);
                            sCard.setIccId(info.getIccid());
                            sCard.setMsisdn(info.getMsisdn());
                            JLog.loge("onSucc: fetch softcard:" + sCard);
                            boolean ret = CardRepository.INSTANCE.fetchSoftCard(sCard);
                            if (ret) {
                                info.setKi(EncryptUtils.getMd5Digest(info.getKi()));
                                info.setOpc(EncryptUtils.getMd5Digest(info.getOpc()));
                                softsimManager.updateSoftsimInfo(info);
                                JLog.logd("download order succ" + order + " " + orderInfo);
                            } else {
                                failInfo.add(info.getImsi());
                            }
                        }
                        if (bins != null && bins.size() != 0) {
                            for (SoftsimBinLocalFile binFile : bins) {
                                softsimManager.updateSoftsimBinFile(binFile);
                            }
                        }
                        orderRequestDoingList.remove(this);
                        checkAllRequestComplited();
                        if (failInfo.size() == 0) {
                            softsimManager.updateUserOrderInfo(username, orderInfo);
                            downloadSucc(username, order, orderInfo);
                        } else {
                            downloadFail(username, order, ErrorCode.INSTANCE.getSOFTSIM_DOWNLOAD_ADDCARD_FAIL(), new Exception("addcard fail"));
                        }
                    }

                    @Override
                    public void onError(String order, int errcode, Throwable throwable) {
                        JLog.loge("onError: order:" + order + " downloadFail!!!" + " code:" + errcode + " :" + throwable.getMessage());
                        if(errcode == 0){
                            loge("errcode invalid!!!!!! ");
                            errcode = ErrorCode.INSTANCE.getLOCAL_UNKNOWN_ERROR();
                        }
                        downloadFail(username, order, errcode, throwable);
                        ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByCode(errcode);
                        if(info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN){
                            sendMessage(SOFTSIM_DL_EV_RELOGIN_REQ);
                        }else {
                            orderRequestDoingList.remove(this);
                            checkAllRequestComplited();
                        }
                    }
                };
                tmpList.add(request);
            } else {
                JLog.logd("order " + order.getOrderId() + " find in doing list!");
            }
        }
        for (SoftsimRequest re : tmpList) {
            orderRequestDoingList.add(re);
        }
    }

    private Subscription uploadFLowSub = null;

    private boolean isUploadSoftsimFlow() {
        if (uploadFLowSub != null && !uploadFLowSub.isUnsubscribed()) {
            return true;
        }
        return false;
    }

    private void stopUploadSoftsimFlow() {
        if (uploadFLowSub != null && !uploadFLowSub.isUnsubscribed()) {
            uploadFLowSub.unsubscribe();
        }
    }

    private void startUploadSoftsimFlowAction() {
        stopUploadSoftsimFlow();
        ArrayList list = softsimManager.getFirstSoftsimState();
        if (list == null) {
            JLog.logd("SCFlowLog no softsim state info need upload!");
            needUploadFlow = false;
            sendMessage(SOFTSIM_FLOW_UPLOAD_COMPLETE);
            return;
        }
        int logid = (int) list.get(0);
        SoftsimFlowStateInfo info = (SoftsimFlowStateInfo) list.get(1);
        uploadFLowSub = RequesteAction.requestUploadSoftsimFlow(logid, Configuration.INSTANCE.getImei(context), info, mSessionId, transceiverAdapter, 30).timeout(100, TimeUnit.SECONDS).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                if (o instanceof SoftsimFlowUploadRsp) {
                    JLog.logd("SCFlowLog recv requestUploadSoftsimFlow succ!" + o);
                    softsimManager.delSoftsimStateById(((SoftsimFlowUploadRsp) o).reqId);

                    boolean isVsimServiceOK = ServiceManager.accessEntry.getAccessState().isVsimServiceOK();
                    NetworkInfo.State cloudSimNetState = ServiceManager.cloudSimEnabler.getNetState();
                    if(isVsimServiceOK && cloudSimNetState== NetworkInfo.State.CONNECTED){
                        sendMessage(SOFTSIM_FLOW_UPLOAD_START_NEXT);
                    } else {
                        JLog.logi("SCFlowLog recv requestUploadSoftsimFlow succ! => but finish by isVsimServiceOK = "+isVsimServiceOK+", cloudSimNetState = "+cloudSimNetState );
                        needUploadFlow = false;
                        sendMessage(SOFTSIM_FLOW_UPLOAD_COMPLETE);
                    }
                } else {
                    JLog.logi("SCFlowLog upload softsim flow failed!" + o);
                    needUploadFlow = false;
                    sendMessage(SOFTSIM_FLOW_UPLOAD_COMPLETE);
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                JLog.logi("SCFlowLog recv requestUploadSoftsimFlow fail!" + throwable);
                needUploadFlow = false;
                sendMessage(SOFTSIM_FLOW_UPLOAD_COMPLETE);
            }
        });
    }

    private void stopAllRequest(int reason, String msg) {
        for (FlowOrder or : orderRequesUndotList) {
            downloadFail(username, or.getOrderId(), reason, new Throwable(msg));
        }
        orderRequesUndotList.clear();
        for (SoftsimRequest re : orderRequestDoingList) {
            re.cancel();
            downloadFail(username, re.getOrder(), reason, new Throwable(msg));
        }
        orderRequestDoingList.clear();
    }

    private void startCreateSocket(Context ctx) {
        if (transceiverAdapter == null) {
            transceiverAdapter = new TransceiverAdapter(ctx);
        }
        transceiverAdapter.startSocket();
    }

    private void stopSocket() {
        JLog.logd("stop socket!");
        if (transceiverAdapter != null) {
            transceiverAdapter.stopSocket();
        }
    }

    private void processUserStopOrder(String order) {
        for (FlowOrder or : orderRequesUndotList) {
            if (or.equals(order)) {
                orderRequesUndotList.remove(or);
                downloadFail(username, order, ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL(), new Throwable("user cancel"));
                checkAllRequestComplited();
                return;
            }
        }
        for (SoftsimRequest re : orderRequestDoingList) {
            if (re.equals(order)) {
                re.cancel();
                orderRequestDoingList.remove(re);
                downloadFail(username, order, ErrorCode.INSTANCE.getSOFTSIM_DL_USER_CANCEL(), new Throwable("user cancel"));
                checkAllRequestComplited();
                return;
            }
        }
    }

    private boolean isAllRequestOver() {
        for (SoftsimRequest re : orderRequestDoingList) {
            if (!re.isOver()) {
                JLog.logd("order " + re.getOrder() + " Is doing list!!!");
                return false;
            } else {
                JLog.logd("order " + re.getOrder() + " is over!");
            }
        }
        if (orderRequesUndotList.size() != 0) {
            return false;
        } else {
            JLog.logd("orderRequesUndotList count is " + orderRequesUndotList.size());
        }
        return true;
    }

    private void checkAllRequestComplited() {
        removeMessages(SOFTSIM_DL_EV_ALL_COMPLETE);
        if (isAllRequestOver()) {
            sendMessage(SOFTSIM_DL_EV_ALL_COMPLETE);
        }
    }

    private boolean isAllFunctionComplited() {
        if (isAllRequestOver() && mSoftsimUpdateParam == null && !needUploadFlow) {
            return true;
        }
        return false;
    }

    public int startDownloadSoftsim(String username, String passwd, ArrayList<FlowOrder> order) {
        //        if (curState != mDefaultState && !username.equals(this.username)) {
        //            loge("new request not the same user!");
        //            return -1;
        //        }
        sendMessage(SOFTSIM_DL_EV_DOWNLOAD_REQ, new DownloadReqInfo(username, passwd, order));
        return 0;
    }

    public int stopDownloadSoftsim(String order) {
        if (curState == mDefaultState) {
            JLog.loge("state machine is stop!!!");
            return -1;
        }
        sendMessage(SOFTSIM_DL_EV_DOWNLOAD_STOP, order);
        return 0;
    }

    public int stopDownloadAllSoftsim() {
        if (curState == mDefaultState) {
            JLog.loge("state machine is stop!!!");
            return -1;
        }
        sendMessage(SOFTSIM_DL_EV_USER_CANCEL);
        return 0;
    }

    @CallSuper
    public void downloadSucc(String username, String order, OrderInfo orderInfo) {
        updateResult(order, 0, "succ");
    }

    @CallSuper
    public void downloadFail(String username, String order, int errcode, Throwable t) {
        updateResult(order, errcode, t.getMessage());
    }

    public void softsimUpdateOver(int result, String msg) {
    }

    public interface SoftsimEventCb {
        void downloadResult(String order, int result, String msg);

        void errorUpdate(int errcode, String msg);

        void persentUpdate(int persent);
    }

    private ArrayList<SoftsimEventCb> cbList = new ArrayList<>();

    public void SoftsimEventCbReg(SoftsimEventCb cb) {
        if (cb == null)
            return;
        for (SoftsimEventCb c : cbList) {
            if (c == cb) {
                return;
            }
        }
        cbList.add(cb);
    }

    public void SoftsimEventCbUnreg(SoftsimEventCb cb) {
        if (cb == null)
            return;
        for (SoftsimEventCb c : cbList) {
            if (c == cb) {
                cbList.remove(c);
                return;
            }
        }
    }

    private void updateResult(String order, int result, String msg) {
        for (SoftsimEventCb c : cbList) {
            c.downloadResult(order, result, msg);
        }
    }

    public int startUpdateSoftsim(String curImsi, String mcc, String mnc) {
        sendMessage(SOFTSIM_UP_EV_START, new SoftsimUpdateParam(RunningStates.getUserName(), curImsi, mcc, mnc));
//        sendMessage(SOFTSIM_UP_EV_START, new SoftsimUpdateParam(username, curImsi, mcc, mnc));
        return 0;
    }

    public void stopUpdateSoftsim() {
        JLog.logd("stopUpdateSoftsim start");
        sendMessage(SOFTSIM_UP_EV_STOP);
    }

    private boolean getSystemNetworkConn() {
        ConnectivityManager connectivityManager = ConnectivityManager.from(context);
        NetworkInfo[] networks = connectivityManager.getAllNetworkInfo();
        boolean connect = false;
        if (networks != null && networks.length > 0) {
            for (int i = 0; i < networks.length; i++) {
                JLog.logd("count:" + i + " type:" + networks[i].getTypeName() + " status:" + networks[i].getState());
                if (networks[i].getState() == NetworkInfo.State.CONNECTED) {
                    connect = true;
                }
            }
        }
        return connect;
    }

    public void startUploadSoftsimFlow() {
        //sendMessage(SOFTSIM_FLOW_UPLOAD_START);
        startUploadSoftsimFlow(0);
    }
    public void startUploadSoftsimFlow(long delayMillis) {
        //sendMessage(SOFTSIM_FLOW_UPLOAD_START);
        removeMessages(SOFTSIM_FLOW_UPLOAD_START);
        sendMessageDelayed(SOFTSIM_FLOW_UPLOAD_START, delayMillis);
    }

    // debu start
    public void setDonotLogout(boolean value) {
        donotLogout = value;
    }

    public void forLogoutMsg() {
        sendMessage(DEUBG_EV_FORCE_LOGOUT);
    }

    public void startUploadSoftsimFlowTest() {
        sendMessage(DEUBG_EV_UPLOAD_FLOW);
    }
    // debug end

    public void stopWhenAccessStateRunning(){
        logd("stopWhenAccessStateRunning");
        sendMessage(SOFTSIM_DL_EV_ACCESSSTATE_RUNNIG);
    }
}
