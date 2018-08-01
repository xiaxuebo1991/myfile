package com.ucloudlink.refact.access;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.DeadObjectException;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.ucloudlink.framework.protocol.protobuf.DispatchVsimResp;
import com.ucloudlink.framework.protocol.protobuf.EquivalentPlmnInfo;
import com.ucloudlink.framework.protocol.protobuf.GetVsimInfoResp;
import com.ucloudlink.framework.protocol.protobuf.LoginResp;
import com.ucloudlink.framework.protocol.protobuf.S2cSwitchCardResult;
import com.ucloudlink.framework.protocol.protobuf.SwitchVsimResp;
import com.ucloudlink.framework.tasks.UploadFlowTask;
import com.ucloudlink.framework.ui.IUcloudAccessCallback;
import com.ucloudlink.framework.ui.PlmnInfo;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.restore.RunningStates;
import com.ucloudlink.refact.access.restore.ServiceRestore;
import com.ucloudlink.refact.access.struct.LoginInfo;
import com.ucloudlink.refact.access.ui.AccessEntryService;
import com.ucloudlink.refact.business.AbortException;
import com.ucloudlink.refact.business.Requestor;
import com.ucloudlink.refact.business.cardprovisionstatus.CardProvisionStatus;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.netlimit.common.NetPackageStatisticsCtrl;
import com.ucloudlink.refact.business.heartbeat.HeartbeatTask;
import com.ucloudlink.refact.business.keepalive.JobSchedulerCtrl;
import com.ucloudlink.refact.business.keepalive.JobSchedulerIntervalTimeCtrl;
import com.ucloudlink.refact.business.keepalive.JobSchedulerUtils;
import com.ucloudlink.refact.business.login.Session;
import com.ucloudlink.refact.business.netcheck.Ncsi;
import com.ucloudlink.refact.business.netcheck.NetInfo;
import com.ucloudlink.refact.business.netcheck.NetworkManager;
import com.ucloudlink.refact.business.netcheck.NetworkTest;
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogBigCycle;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogSoftPhySwitch;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogSsimLogin;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogTerAccess;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogVsimDelayStat;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogVsimResAllo;
import com.ucloudlink.refact.business.performancelog.logs.ResAlloinfo;
import com.ucloudlink.refact.business.performancelog.logs.SsimLoginRspData;
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType;
import com.ucloudlink.refact.business.routetable.RequestGetRouteTable;
import com.ucloudlink.refact.business.routetable.RouteTableManager;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.business.s2ccmd.CmdDefineKt;
import com.ucloudlink.refact.business.softsim.SeedNetworkStart;
import com.ucloudlink.refact.business.softsim.SoftsimEntry;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.statebar.NoticeStatusBarServiceStatus;
import com.ucloudlink.refact.business.uploadlac.UploadLacTask;
import com.ucloudlink.refact.business.virtimei.VirtImeiHelper;
import com.ucloudlink.refact.channel.enabler.DataEnableEvent;
import com.ucloudlink.refact.channel.enabler.DeType;
import com.ucloudlink.refact.channel.enabler.EnablerException;
import com.ucloudlink.refact.channel.enabler.datas.Card;
import com.ucloudlink.refact.channel.enabler.datas.CardStatus;
import com.ucloudlink.refact.channel.enabler.datas.CardType;
import com.ucloudlink.refact.channel.enabler.plmnselect.SeedPlmnSelector;
import com.ucloudlink.refact.channel.enabler.simcard.dds.DDSUtil;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.channel.monitors.CheckWifiApDunReq;
import com.ucloudlink.refact.channel.monitors.Monitor;
import com.ucloudlink.refact.channel.monitors.WifiReceiver2;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.product.mifi.flow.protection.MifiCloudFlowProtectionXMLDownloadHolder;
import com.ucloudlink.refact.product.mifi.seedUpdate.intf.IBusinessTask;
import com.ucloudlink.refact.systemapi.interfaces.ProductTypeEnum;
import com.ucloudlink.refact.utils.BootOptimize;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.PerformanceStatistics;
import com.ucloudlink.refact.utils.PhoneStateUtil;
import com.ucloudlink.refact.utils.ProcessState;
import com.ucloudlink.refact.utils.TcpdumpHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

import static com.ucloudlink.refact.ServiceManager.cardController;
import static com.ucloudlink.refact.access.StateMessageId.PRELOGIN_IP_FAIL_RETRY;
import static com.ucloudlink.refact.access.StateMessageId.SEED_CARD_STATU_CHANGE;
import static com.ucloudlink.refact.channel.enabler.plmnselect.SeedFplmnSelectorKt.CLEAN_TEMP_FPLMN;
import static com.ucloudlink.refact.channel.enabler.plmnselect.SeedFplmnSelectorKt.SEED_SOCKET_FAIL_MAX_EXCEPTION;
import static com.ucloudlink.refact.config.UConstantKt.ACCESS_CLEAR_CARD;
import static com.ucloudlink.refact.config.UConstantKt.REASON_MCC_CHANGE;
import static com.ucloudlink.refact.config.UConstantKt.REASON_MONITOR_RESTART;
import static com.ucloudlink.refact.config.UConstantKt.USER_LOGOUT;

/**
 * 登录接入的状态机
 * Created by shiqianhua on 2016/10/17.
 */
public class AccessState extends StateMachine {
    static final         String TAG               = "AccessState";
    private static final String START_TIME_FILTER = "BootTimer";
    protected Context ctx;
    protected State   mParentState;
    protected State   mDefaultState;
    protected State   mRunState;
    protected State   mRecoveryState;
    protected State   mStartState;
    protected State   mInitState;
    protected State   mSeedChEstablishState;
    protected State   mPreloginIpCheckState;
    protected State   mLoginState;
    protected State   mInServiceState;
    protected State   mLogoutState;
    protected State   mLogoutWaitState;
    protected State   mWaitReloginState;
    protected State   mWaitResetCardState;

    protected State mVsimBegin;
    protected State mDispatchVsimState;
    protected State mGetVsimInfoState;
    protected State mDownloadState;
    protected State mStartVsimState;
    protected State mVsimRegState;
    protected State mVsimDatacallState;
    protected State mVsimEstablishedState;
    protected State mVsimReleaseState;
    protected State mSwitchVsimState;
    protected State mWaitSwitchVsimState;
    protected State mWaitResetCloudSimState;
    protected State mPlugPullCloudSimState;

    protected State mExceptionState;
    private ArrayList<Integer> mExceptionCmdList = new ArrayList<>();

    private State lastState;
    private State currState;
    private State nextState;

    private State beforeExceptionState;

    private State mNextStateAfterAllRest;
    private State mNextStateAfterCloudRest;

    private Session session = new Session();

    private String reloginReason = null;

    private LoginInfo loginInfo;

    private Subscription  requestCardSub;
    private Subscription  switchVsimSub;
    private Subscription  heartBeatSub;
    private Card          vsimCard;
    private HeartbeatTask heartbeatTask;
    private boolean useHearBeatAlarm = true;
    private boolean needEnableVsim   = true;
    private int     socket_timeout   = TimeoutValue.SOCKET_CONNECT_TIMEOUT;

    private              int     heartBeatTimes        = 0;
    private final static int     heartBeatTimesCount   = TimeoutValue.getHeartbeatSendTimeoutCnt();
    private              boolean isWaitForHeartBeatRsp = false;

    private int switchVsimReason = 0;
    private int switchVsimSubReason = 0;

    private int processPersent = 0;

    private int terFlowReason = 0;
    private String termFlowReasonInfo;

    private SeedState mSeedState = null;

    private String  heartbeatId = "heartbeat";
    private boolean isLoginOnce = false; // 表示曾经登陆过, 退出过程需要清理掉

    private              boolean needChannel      = false;
    private              String  stateMachineId   = "stateMachine";
    private static final String  ALWAYS_NEED_SEED = "ALWAYS_NEED_SEED";

    public boolean isWifiOn = false;

    private String  mLogoutReason       = null;
    private String  mResetCoudsimReason = null;
    private boolean isBUSINESS_RESTART  = false;//是否触发大循环

    private AccessEntry              accessEntry;
    private SoftsimEntry             softsimEntry;
    private String                   mSessionId;
    private String                   mImsi;
    private String                   mVirtualImei;
    private List<EquivalentPlmnInfo> mVsimEplmnList;
    private String                   mVsimApn;

    private BehaviorSubject<Integer> statePersentOb = BehaviorSubject.create(0);
    private Subscription mNetInfoChangedSubscription;

    private final PowerManager          pm       = (PowerManager) ServiceManager.appContext.getSystemService(Context.POWER_SERVICE);
    private final PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartBeat");

    // 存储Access状态机启动的时间戳
    private long mStartTime = 0L;
    // 存储当前状态启动的时间戳
    private long mStateTime = 0L;

    //是否进行物理卡切软卡
    private boolean isPhyToSoft = false;

    public String getSessionId() {
        if(mSessionId == null){
            return "";
        }
        return mSessionId;
    }

    public String getImis() {
        return mImsi;
    }

    public String getUserName() {
        if (loginInfo != null){
            return loginInfo.getUsername();
        }else {
            return "";
        }
    }

    private ArrayList<IUcloudAccessCallback> unsoliCbList = new ArrayList<IUcloudAccessCallback>();

    protected void transToNextState(State next) {
        JLog.logk("transState " + currState.getName() + " -> " + next.getName());
        nextState = next;
        transitionTo(next);
    }

    private void requireChannel() {
        if (!needChannel) {
            needChannel = true;
            Requestor.INSTANCE.requireChannel(stateMachineId);
        }
    }

    private void releaseChannel() {
        if (needChannel) {
            needChannel = false;
            Requestor.INSTANCE.releaseChannel(stateMachineId);
        }
    }

    private String add0ToHeadString(String str, int len) {
        if (str != null && str.length() < len) {
            StringBuffer stringBuffer = new StringBuffer("");
            for (int i = 0; i < len - str.length(); i++) {
                stringBuffer.append("0");
            }
            stringBuffer.append(str);
            return stringBuffer.toString();
        }
        return str;
    }

//    private AccessCloudSimImsi accessCloudSimState = AccessCloudSimImsi.getInstance();

    public AccessState(Context ctx, AccessEntry entry, SoftsimEntry softsimEntry) {
        super("AccessState");
        BootOptimize.startFun("Access State init");
        this.ctx = ctx;
        this.accessEntry = entry;
        this.softsimEntry = softsimEntry;
        this.mStartTime = SystemClock.uptimeMillis();
        wakeLock.setReferenceCounted(false);
        initAllStates();
        addAllStates();
        startAccessState();
        //        setDbg(true);
        ServiceManager.monitor = new Monitor(ctx);
        ServiceManager.productApi.getNetRestrictOperater().init();
        mSeedState = new SeedState(ctx, this);
        heartbeatTask = new HeartbeatTask(ctx, this);
        initWifiListener();
        BootOptimize.finishFun("Access State init");
    }

    protected void startAccessState() {
        setInitialState(mParentState);
        JLog.logd("Action:StatemachineStart");
        start();
    }

    protected void addAllStates() {
        addState(mParentState);
        addState(mDefaultState, mParentState);  // 0%
        addState(mRunState, mParentState);  // 0%
        addState(mRecoveryState, mRunState); // 5%
        addState(mStartState, mRunState);
        addState(mInitState, mStartState);
        addState(mSeedChEstablishState, mInitState);  // 10%
        addState(mPreloginIpCheckState, mInitState);  // 20%
        addState(mLoginState, mInitState);  // 35%
        addState(mInServiceState, mInitState);  // 40%
        addState(mVsimBegin, mInServiceState);
        addState(mDispatchVsimState, mInServiceState);  // 45%
        addState(mGetVsimInfoState, mInServiceState); // 50%
        addState(mDownloadState, mInServiceState);  // 55%
        addState(mStartVsimState, mInServiceState);  // 65%
        addState(mVsimRegState, mInServiceState);  // 75% 80% 81%-89%
        addState(mVsimDatacallState, mInServiceState);  // 90%
        addState(mVsimEstablishedState, mInServiceState); // 100%
        addState(mVsimReleaseState, mInServiceState);
        addState(mSwitchVsimState, mInServiceState);  // 45%
        addState(mWaitSwitchVsimState, mInServiceState);
        addState(mWaitResetCloudSimState, mInServiceState);
        addState(mPlugPullCloudSimState, mInServiceState);  //63%
        addState(mWaitReloginState, mInitState);
        addState(mExceptionState, mStartState);
        addState(mLogoutState, mStartState);
        addState(mLogoutWaitState, mStartState);
        addState(mWaitResetCardState, mStartState);
    }

    protected void initAllStates() {
        mParentState = new ParentState(); // 所有状态的父状态，业务无关，任何时候都需要处理的消息在这个状态处理
        mDefaultState = new DefaultState(); // 未登录时候的状态
        mRunState = new RunState(); // 用户点击登录后的父状态
        mRecoveryState = new RecoveryState(); // app异常关闭后恢复状态
        mStartState = new StartState(); // 业务运行父状态
        mInitState = new InitState(); // 业务正常运行父状态
        mSeedChEstablishState = new SeedChEstablishState(); // 种子卡网络建立状态，这个状态会拉起种子卡
        mPreloginIpCheckState = new PreloginIpCheckState(); // 预登陆状态，主要是做路由获取IP
        mLoginState = new LoginState(); // 登录状态，发送登录包
        mInServiceState = new InServiceState(); // 登录成功状态，此时拿到此次登录的session，是登录成功后业务的父状态
        mLogoutState = new LogoutState(); // 退出状态，发送退出登录包
        mLogoutWaitState = new LogoutWaitState(); // 退出等待状态
        mWaitReloginState = new WaitReloginState(); // 等待重登陆状态，根据业务不同时间会有长短
        mWaitResetCardState = new WaitResetCardState(); // 等待关卡状态，会关闭种子卡和云卡，同时等待其完成，disable卡的时候，进入的状态，不响应除了卡disable成功的其他所有事件

        mVsimBegin = new VsimBegin(); // 云卡启动状态
        mDispatchVsimState = new DispatchVsimSate(); // 分卡状态
        mGetVsimInfoState = new GetVsimInfoState(); // 获取vsim info
        mDownloadState = new DownloadState(); // 下载云卡bin文件
        mStartVsimState = new StartVsimState();  // 启动云卡状态,insert 卡, powerup, ready
        mVsimRegState = new VsimRegState(); // ready之后,等待注册状态,这个由framework处理,app只监听
        mVsimDatacallState = new VsimDatacallState(); // 拨号等待状态
        mVsimEstablishedState = new VsimEstablishedState(); // 拨号成功,云卡可用
        mVsimReleaseState = new VsimReleaseState();  //退出时需要做的处理都在这里
        mSwitchVsimState = new SwitchVsimState(); // 换卡状态
        mWaitSwitchVsimState = new WaitSwitchVsimState(); // 换卡等待状态,主要是换卡惩罚
        mWaitResetCloudSimState = new WaitResetCloudSimState();  // disable云卡的时候，进入的状态，不响应除了云卡disable成功的其他所有事件
        mPlugPullCloudSimState = new PlugPullCloudSimState(); // 种子卡软硬卡切换状态

        mExceptionState = new ExceptionState();
    }

    private void initWifiListener() {
        WifiReceiver2.Companion.getWifiNetObser().asObservable().subscribe(new Action1<NetworkInfo.State>() {
            @Override
            public void call(NetworkInfo.State state) {
                JLog.logd("wifi state change: " + state);
                if (state == NetworkInfo.State.CONNECTED) {
                    sendMessage(StateMessageId.WIFI_CONNECTED);
                } else if (state == NetworkInfo.State.DISCONNECTED) {
                    sendMessage(StateMessageId.WIFI_DISCONNECTED);
                }
            }
        });
    }

    private CardStateMonitor.NetworkStateListen mNetworkStateListen = new CardStateMonitor.NetworkStateListen() {
        @Override
        public void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
            sendMessage(StateMessageId.NETWORK_STATE_CHANGE, ddsId, type, state);
        }
    };

    private void addStateListen() {
        CardStateMonitor simMonitor = ServiceManager.simMonitor;
        JLog.logd("initStateListen CardStateChange init listen");

        if (mNetInfoChangedSubscription != null && !mNetInfoChangedSubscription.isUnsubscribed()) {
            mNetInfoChangedSubscription.unsubscribe();
        }
        mNetInfoChangedSubscription = simMonitor.cellLocationChangedBehaviorSubject.asObservable().subscribe(new Observer<Boolean>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Boolean aBoolean) {
                if (aBoolean) {
                    JLog.logd("StartState", "addStateListen");
                    updateCloudPlmn();
                } else {
                    updateSeedPlmn();
                }
            }
        });
    }

    private void removeStateListen() {
        CardStateMonitor simMonitor = ServiceManager.simMonitor;
        JLog.logd("removeStateListen CardStateChange do not listen");
        if (mNetInfoChangedSubscription != null && !mNetInfoChangedSubscription.isUnsubscribed()) {
            mNetInfoChangedSubscription.unsubscribe();
        }
    }

    private void logStateMsg(Message message, String state) {
        if (message.what != AccessEventId.EVENT_CLOUDSIM_AUTH_REPLIED) {
            JLog.logd("recv msg state[" + state + "]" + " " + message);
        }
    }

    private void logUnhandleMsg(Message message, String state) {
        if (message.what != AccessEventId.EVENT_CLOUDSIM_AUTH_REPLIED) {
            JLog.loge("ERROR: unhandled msg, state[" + state + "] msg:" + message);
        }
    }

    private void recvLoginStart(LoginInfo info) {
        loginInfo = info;
        JLog.logd("restoreMobileSettingsCheck has USER_LOGIN_REQ_CMD start!");
        int ret = ServiceManager.productApi.restoreCheck();
        if (ret == 0) {
            sendMessage(StateMessageId.USER_LOGIN_AFTER_RESTORE_CMD);
        } else {
            sendMessage(StateMessageId.USER_LOGIN_AFTER_RESTORE_CMD);
        }
    }

    private void setLogoutMsg(int code, String msg) {
        terFlowReason = code;
        termFlowReasonInfo = msg;
    }

    /**
     * BaseState
     * 用作所有State的基类，过滤的filter为“BootTimer”，实现以下功能：
     * <p>
     * 1）统计每个状态的执行时间；
     */
    private class BaseState extends State {
        private boolean mIsPrintFinish = true;

        @Override
        public void enter() {
            super.enter();
            if (!"100".equals(getPercent())) {
                mStateTime = SystemClock.uptimeMillis();
                String log = String.format("%25s Enter Start -- [ %3s ]", getClass().getSimpleName(), getPercent());
                JLog.logd(START_TIME_FILTER, log);
            } else {
                JLog.logd(START_TIME_FILTER, String.format("UService Start finished in %s ms.", (SystemClock.uptimeMillis() - mStartTime)));
            }
        }

        protected void finishEnter() {
            if (mIsPrintFinish) {
                JLog.logd(START_TIME_FILTER, String.format("%25s Enter Finished in %5s ms.", getClass().getSimpleName(), (SystemClock.uptimeMillis() - mStateTime)));
            }
        }

        @Override
        public void exit() {
            super.exit();
            if (!"100".equals(getPercent())) {
                long current = SystemClock.uptimeMillis();
                String log = String.format("%25s Exit  in %5s ms, total %5s ms.", getClass().getSimpleName(), (current - mStateTime), (current - mStartTime));
                JLog.logd(START_TIME_FILTER, log);
            } else {
                JLog.logd(START_TIME_FILTER, "Exit 100% percent. Reset start time.");
                mStartTime = SystemClock.uptimeMillis();
            }
        }

        protected String getPercent() {
            return "NAN";
        }
    }

    private class ParentState extends State {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            ServiceManager.productApi.getNetRestrictOperater().resetRestrict("ParentState enter");
            FlowBandWidthControl.getInstance().clearBwSetting(AccessState.this);
            FlowBandWidthControl.getInstance().getCloudFlowProtectionMgr().setWebEnableFlowProtection(true);
            transToNextState(mDefaultState);
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateMsg(msg, getClass().getSimpleName());
            switch (msg.what) {
                case AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM:
                    JLog.logd("recv AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM in " + this.getClass().getSimpleName());
                    ServiceManager.seedCardEnabler.cloudSimRestOver();
                    break;
                case AccessEventId.EVENT_SOFTSIM_ON:
                    //                    updateCommMessage(8, String.valueOf(true));
                    ctx.sendBroadcast(new Intent("com.cloudsim.widget.call.restore.show"));
                    break;
                case AccessEventId.EVENT_SOFTSIM_OFF:
                    //                    updateCommMessage(8, String.valueOf(false));
                    ctx.sendBroadcast(new Intent("com.cloudsim.widget.call.restore.hide"));
                    break;
                case StateMessageId.TRACEROUTE_EVENT:
                    JLog.logd("recv Event TRACEROUTE_EVENT: Try traceroute to Service");
                    NetworkManager.INSTANCE.tracerouteToService();
                    break;
                default:
                    logUnhandleMsg(msg, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class DefaultState extends State {
        private boolean checkUsernameValid(String username) {
            JLog.logd("username:" + username);
            if (username == null || username.length() == 0) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_USERNAME_INVALID());
                return false;
            }
            return true;
        }

        private boolean checkOrderValid(LoginInfo loginInfo) {
            JLog.logd("checkOrderValid " + Configuration.INSTANCE.getOrderId());
            if (Configuration.INSTANCE.getOrderId().length() == 0) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_ORDER_IS_NULL());
                return false;
            }
            OrderInfo info = softsimEntry.getOrderInfoByUserOrderId(loginInfo.getUsername(), Configuration.INSTANCE.getOrderId());
            JLog.logd("order info " + info);
            if (info == null) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_ORDER_INFO_IS_NULL());
                return false;
            }
            if (info.isOutOfDate()) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_ORDER_OUT_OF_DATE());
                return false;
            }
            //            if ((info.getSimUsePolicy() != OrderInfo.PHY_SIM_ONLY) && (info.getSofsimList() == null || info.getSofsimList().size() == 0)) {
            //                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_ORDER_SOFTSIM_NULL());
            //                return false;
            //            }

            if (info.getSimUsePolicy() == OrderInfo.PHY_SIM_ONLY) {
                if (CardProvisionStatus.INSTANCE.getSimSetStatus(CardType.PHYSICALSIM) == 0) {
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHY_CARD_DISABLE());
                    return false;
                }
                if (CardProvisionStatus.INSTANCE.getSimSetStatus(CardType.PHYSICALSIM) == -2) {
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHY_CARD_NOT_EXIST());
                    return false;
                }
                if (!getSystemNetworkConn()) {
                    int subids[] = SubscriptionManager.getSubId(Configuration.INSTANCE.getSeedSimSlot());
                    if (subids != null && subids.length >= 1 && subids[0] > 0) {
                        boolean mobileDataEnable = PhoneStateUtil.Companion.getMobileDataEnable(ServiceManager.appContext, subids[0]);
                        if (!mobileDataEnable) {
                            processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHONE_DATA_DISABLED());
                        } else {
                            processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHY_NETWORK_UNAVAILABLE());
                        }
                    } else {
                        processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHY_NETWORK_UNAVAILABLE());
                    }
                    return false;
                }
            }

            return true;
        }

        // return true, OK can login
        private boolean checkEnvBeforeLogin(LoginInfo info) {
            if (!checkUsernameValid(info.getUsername())) {
                return false;
            }
            if (!checkOrderValid(info)) {
                return false;
                // phone call
            }

            //切换成下一个状态mSeedChEstablishState做这个判断
//            if (CardStateMonitor.mCallState) {
//                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHONE_CALLING());
//                return false;
//            }

            /*if (SoftSimStateMark.INSTANCE.isProcessOutCall()){
                logd("SoftSimStateMark.INSTANCE.isProcessOutCall true");
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHONE_CALLING());
                return false;
            }*/

            int mode = 0;
            try {
                mode = Settings.Global.getInt(ServiceManager.appContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mode == 1) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_AIR_MODE_ENABLED());
                return false;
            }
            //             //phone data cellular
            //            if (Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_soft()) {
            //                PhoneStateUtil.Companion.setMobileDataEnable(ctx, true); // TODO: 2017/6/16 need enable when use softsim!
            //            } else {
            //                if (!getSystemNetworkConn()) {
            //                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHONE_DATA_DISABLED());
            //                    return false;
            //                }
            //            }
            // int blacklist // TODO: 2016/12/23
            if (false) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_APP_IN_BLACKLIST());
                return false;
            }
            return true;
        }

        private boolean getSystemNetworkConn() {
            ConnectivityManager connectivityManager = ConnectivityManager.from(ctx);
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

        @Override
        public void enter() {
            super.enter();
            currState = this;
            /*if (UcloudController.INSTANCE.getSocketDisConnectStatus() == true) {
                JLog.logd("ucloudlink connectSocket");
                UcloudController.INSTANCE.connectSocket(0);
            }*/
            JobSchedulerIntervalTimeCtrl.getInstance().setRunState(false);
            JobSchedulerIntervalTimeCtrl.getInstance().setOnStartJobCount(0, "DefaultState - enter()");
            JobSchedulerUtils.cancelJobScheduler(ctx);
            MifiCloudFlowProtectionXMLDownloadHolder.setPRO_DATA_CONFIG_CHECKED(false);
            ServiceManager.INSTANCE.getCardController().initEnv(-1);
            //JLog.logd("accessEntry.accessMonitor:" + accessEntry.accessMonitor);
            if (accessEntry.accessMonitor != null) {
                accessEntry.accessMonitor.setMonitorTimeoutFlag(false);
            }

            JLog.logd("processPersent:" + processPersent);
            if (processPersent > 0) {
                updateResetRsp(terFlowReason, termFlowReasonInfo);
                JLog.logd("last is not 0, " + processPersent);
                processPersent = 0;
                setStateProcess(0);
                return;
            }

            setStateProcess(0);

            if (ServiceManager.monitor != null) {  // 第一次进这个状态，monitor是null
                //monitor.stopDDSMonitor();
            }
            isLoginOnce = false;
            JLog.logd("isExceptionStart:" + ServiceRestore.isExceptionStart(ctx) + ",isRecordExist:" + RunningStates.isRecordExist() + ", ServiceRestore.isReBoot():" + ServiceRestore.isReBoot() + ", Configuration.INSTANCE.getRECOVE_WHEN_REBOOT():" + Configuration.INSTANCE.getRECOVE_WHEN_REBOOT() + "， Configuration.INSTANCE.getAUTO_WHEN_REBOOT():" + Configuration.INSTANCE.getAUTO_WHEN_REBOOT());
            //            if (ServiceRestore.isExceptionStart(ctx) && RunningStates.isRecordExist() && (!ServiceRestore.isReBoot() || Configuration.INSTANCE.getRECOVE_WHEN_REBOOT() || Configuration.INSTANCE.getAUTO_WHEN_REBOOT())) {
            if (ServiceManager.productApi.needRecovery() && ServiceRestore.isExceptionStart(ctx) && RunningStates.isRecordExist() && (!ServiceRestore.isReBoot() || Configuration.INSTANCE.getRECOVE_WHEN_REBOOT() || Configuration.INSTANCE.getAUTO_WHEN_REBOOT())) {
                JLog.logd("recovery from exception !!! so trans to recovery state!");
                transToNextState(mRecoveryState);
            } else {
                JLog.logd("restoreLoginParams normal start, do not auto restore login!");
                ServiceRestore.setStopServiceFlag(ctx);
                if (Configuration.INSTANCE.getAUTO_WHEN_REBOOT() && RunningStates.getUserName() != null && RunningStates.getPassWord() != null) {
                    ServiceRestore.recoverCSim();
                    JLog.logd("set slot:" + Configuration.INSTANCE.getSeedSimSlot() + " " + Configuration.INSTANCE.getCloudSimSlot());
                    LoginInfo info = new LoginInfo(RunningStates.getUserName(), RunningStates.getPassWord());
                    accessEntry.setLoginInfo(info);
                    sendMessage(StateMessageId.USER_LOGIN_REQ_CMD, info);
                }
            }
        }


        File file = new File("/productinfo/info.obj");
        LoginInfo inforead;


        private LoginInfo getLoginInfo() {  //todo: 需要挪到展讯平台里面
            if (!file.exists()) {
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                        //写入一个默认对象
                        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
                        out.writeObject(new LoginInfo("", ""));
                        out.close();
                    } catch (IOException e) {
                        JLog.logd(e);
                    }
                }
            }
            try {
                //从productNV分区读取用户信息
                ObjectInputStream inn = new ObjectInputStream(new FileInputStream(file));
                inforead = (LoginInfo) inn.readObject();
                inn.close();
            } catch (IOException e) {
                e.printStackTrace();
                JLog.logd("读取用户信息失败 ：" + e);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                JLog.logd("读取用户信息失败 ：" + e);
            }
            return inforead;
        }


        @Override
        public boolean processMessage(Message msg) {
            logStateMsg(msg, getClass().getSimpleName());
            switch (msg.what) {
                case StateMessageId.USER_LOGIN_REQ_CMD:
                    LoginInfo info = (LoginInfo) msg.obj;
                    if (ServiceManager.productApi.getProductType() == ProductTypeEnum.MIFI
                            || ServiceManager.productApi.getProductType() == ProductTypeEnum.MODULE) { // TODO: 需要修改
                        info = getLoginInfo();
                    }
                    if (info == null) {
                        Log.e(TAG, "processMessage: login error usename and password is null!");
                        break;
                    }
                    if (ServiceManager.productApi.ifNeedCheckBeforeLogin()) {
                        if (checkEnvBeforeLogin(info)) {
                            recvLoginStart(info);
                        }
                    } else {
                        recvLoginStart(info);
                    }
                    break;
                case StateMessageId.USER_LOGIN_AFTER_RESTORE_CMD:
                    JLog.logd("restoreMobileSettingsCheck USER_LOGIN_AFTER_RESTORE_CMD login");
                    PerformanceStatistics.INSTANCE.setProcess(ProcessState.AUTO_RUN_START);
                    PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_CLOUD_START(),0,"");
                    PerfLogVsimDelayStat.INSTANCE.create(PerfLogVsimDelayStat.INSTANCE.getSTART(), 0, "");
                    JLog.logd("Action:Userlogin");
                    transToNextState(mSeedChEstablishState);
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    break;
                default:
                    logUnhandleMsg(msg, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            ServiceManager.productApi.getNetRestrictOperater().resetRestrict("DefaultState exit");
        }
    }

    private class RunState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            ServiceManager.productApi.getNetRestrictOperater().setRestrict("RunState enter");
            JobSchedulerIntervalTimeCtrl.getInstance().setRunState(true);
            JobSchedulerUtils.startJobScheduler(ctx, JobSchedulerCtrl.SWITCH_KEEP_LIVE, JobSchedulerIntervalTimeCtrl.getInstance().getIntervalMillis("RunState enter"));
            NetPackageStatisticsCtrl.getInstance().start();
            finishEnter();
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            ServiceManager.productApi.getNetRestrictOperater().resetRestrict("RunState exit");
            JobSchedulerIntervalTimeCtrl.getInstance().setRunState(false);
            FlowBandWidthControl.getInstance().clearBwSetting(AccessState.this);
            //            SoftSimStateMark.INSTANCE.handlePauseCloudSimAction(false); //清除状态
            NetPackageStatisticsCtrl.getInstance().stop();
        }

        @Override
        protected String getPercent() {
            return "0";
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                default:
                    return NOT_HANDLED;
            }
        }
    }

    protected class RecoveryState extends BaseState {

        private void startToLogin() {
            LoginInfo info = new LoginInfo(RunningStates.getUserName(), RunningStates.getPassWord());
            accessEntry.setLoginInfo(info);
            sendMessage(StateMessageId.USER_LOGIN_RECOVERY_CMD, info);
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(5);
            updateEnterRecoveryState();
            if (!ServiceRestore.isReBoot()) {

                ServiceRestore.recoverCSim();

                sendMessage(StateMessageId.INIT_ENV);

                sendMessageDelayed(StateMessageId.DO_RECOVERY_TIMEOUT, TimeUnit.SECONDS.toMillis(120)); // 120s 超时退出
            } else if (Configuration.INSTANCE.getRECOVE_WHEN_REBOOT() || Configuration.INSTANCE.getAUTO_WHEN_REBOOT()) {
                ServiceRestore.recoverCSim();
                LoginInfo info = new LoginInfo(RunningStates.getUserName(), RunningStates.getPassWord());
                accessEntry.setLoginInfo(info);
                sendMessage(StateMessageId.USER_LOGIN_RECOVERY_CMD, info);
            } else {
                JLog.loge("recovery cann not run to here!!!");
            }
            finishEnter();
        }

        @Override
        protected String getPercent() {
            return "5";
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateMsg(msg, getClass().getSimpleName());
            switch (msg.what) {
                case StateMessageId.USER_LOGIN_RECOVERY_CMD:
                    //                    if(checkEnvBeforeLogin()){
                    recvLoginStart((LoginInfo) msg.obj);
                    //                    }else{
                    //                        JLog.loge("login env invalid, so trans to default state!");
                    //                        transToNextState(mDefaultState);
                    //                    }
                    break;
                case StateMessageId.USER_LOGIN_AFTER_RESTORE_CMD:
                    JLog.logd("restoreMobileSettingsCheck USER_LOGIN_AFTER_RESTORE_CMD login");
                    PerformanceStatistics.INSTANCE.setProcess(ProcessState.AUTO_RUN_START);
                    PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_CLOUD_START(),0,"");
                    PerfLogVsimDelayStat.INSTANCE.create(PerfLogVsimDelayStat.INSTANCE.getSTART(), 0, "");
                    transToNextState(mSeedChEstablishState);
                    break;
                case StateMessageId.DO_RECOVERY_TIMEOUT:
                    ErrorCode.ErrCodeInfo errCodeInfo = ErrorCode.INSTANCE.getErrInfoByCode(ErrorCode.INSTANCE.getLOCAL_APP_RECOVERY_TIMEOUT());
                    if (errCodeInfo.getAction() == ErrorCode.ErrActType.ACT_EXIT){
                        processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_APP_RECOVERY_TIMEOUT());
                        transToNextState(mDefaultState);
                    }else {
                        ServiceRestore.recoverCSim();
                        LoginInfo info = new LoginInfo(RunningStates.getUserName(), RunningStates.getPassWord());
                        accessEntry.setLoginInfo(info);
                        sendMessage(StateMessageId.USER_LOGIN_RECOVERY_CMD, info);
                    }
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    setLogoutMsg(msg.arg1, (String) msg.obj);
                    transToNextState(mDefaultState);
                    break;
                case StateMessageId.NETWORK_CHECK_INTVL:
                    if (getWifiStatus()) {
                        JLog.logd("wifi is on!!! so login!");
                        startToLogin();
                    } else {
                        String orderId = Configuration.INSTANCE.getOrderId();
                        OrderInfo orderInfo = softsimEntry.getOrderInfoByUserOrderId(Configuration.INSTANCE.getUsername(), orderId);
                        JLog.logd("last order id :" + orderId + " orderinfo:" + orderInfo);
                        if (orderInfo == null) {
                            ServiceManager.productApi.restoreCheck();
                            ServiceRestore.clearLogoutFlag(ctx);
                            transToNextState(mDefaultState);
                        } else if (orderInfo.getSimUsePolicy() != OrderInfo.PHY_SIM_ONLY) {
                            startToLogin();
                        } else {
                            SubscriptionManager subscriptionManager = SubscriptionManager.from(ctx);
                            int dds = subscriptionManager.getDefaultDataPhoneId();
                            int seedSimSlot = Configuration.INSTANCE.getSeedSimSlot();
                            if (dds == seedSimSlot) {
                                NetworkInfo networkInfo = ConnectivityManager.from(ctx).getActiveNetworkInfo();
                                if (networkInfo != null && networkInfo.getState() != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                                    JLog.logd("networkInfo.getType():" + networkInfo.getType());
                                    startToLogin();
                                } else {
                                    sendMessageDelayed(StateMessageId.NETWORK_CHECK_INTVL, TimeUnit.SECONDS.toMillis(2));
                                }
                            } else {
                                //获取种子卡subid，并设置dds
                                SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(seedSimSlot);
                                if (info != null) {
                                    int subId = info.getSubscriptionId();
                                    DDSUtil.INSTANCE.switchDdsToNext2(subId, Configuration.INSTANCE.getSeedSimSlot());
                                }
                                sendMessageDelayed(StateMessageId.NETWORK_CHECK_INTVL, TimeUnit.SECONDS.toMillis(2));
                            }
                        }
                    }
                    break;
                case StateMessageId.INIT_ENV:
                    /*完成初始化后请发送检查网络消息 NETWORK_CHECK_INTVL*/
                    doInitSoftSimChannel();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sendMessage(StateMessageId.NETWORK_CHECK_INTVL);
                    break;
                default:
                    logUnhandleMsg(msg, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        protected void doInitSoftSimChannel() {
            cardController.disableRSIMChannel(-1, true);
            cardController.disconnectTransfer(-1);
            Class cls = Context.class;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            updateExitRecoveryState();
            ServiceRestore.setStopServiceFlag(ctx);
            ServiceRestore.stopNormalStartFlag(ctx);
            removeMessages(StateMessageId.DISCONNECT_ALL_SIM_WAIT);
            removeMessages(StateMessageId.DO_RECOVERY_TIMEOUT);
            removeMessages(StateMessageId.NETWORK_CHECK_INTVL);
        }
    }

    private class StartState extends BaseState {
        private Subscription seedCardSub    = null;
        private Subscription cloudCardSub   = null;
        private Subscription seedExceptSub  = null;
        private Subscription ddsStatusSub   = null;
        private CardStatus   cloudsimStatus = CardStatus.ABSENT;
        private IBusinessTask mSeedUpdateTask;

        private void listenCardStatus() {
            cloudCardSub = ServiceManager.cloudSimEnabler.cardStatusObser().subscribe(new Action1<CardStatus>() {
                @Override
                public void call(CardStatus cardStatus) {
                    if (cardStatus == CardStatus.ABSENT) {
                        sendMessage(StateMessageId.CLOUD_SIM_ABSET);
                    }
                    cloudsimStatus = cardStatus;
                }
            });

            seedCardSub = ServiceManager.seedCardEnabler.cardStatusObser().subscribe(new Action1<CardStatus>() {
                @Override
                public void call(CardStatus cardStatus) {
                    if (cardStatus == CardStatus.ABSENT) {
                        sendMessage(StateMessageId.SEED_SIM_ABSET);
                    }
                }
            });

            seedExceptSub = ServiceManager.seedCardEnabler.exceptionObser().subscribe(new Action1<EnablerException>() {
                @Override
                public void call(EnablerException e) {
                    sendMessage(StateMessageId.SEED_CARD_EXCEPTION, e);
                }
            });
        }

        private void deListenCardStatus() {
            unsubscriptSub(cloudCardSub);
            unsubscriptSub(seedCardSub);
            unsubscriptSub(seedExceptSub);
        }

        private void listenDdsStatus() {
            ddsStatusSub = ServiceManager.monitor.getDdsObser().subscribe(new Action1<Integer>() {
                @Override
                public void call(Integer integer) {
                    JLog.logd("dds change: " + integer + ", cloudsim:" + ServiceManager.cloudSimEnabler.getCard());
                    //                    if (cloudsimStatus.ordinal() > CardStatus.READY.ordinal()
                    //                            && ServiceManager.cloudSimEnabler.getCard().getSubId() != integer
                    //                            && !ServiceManager.cloudSimEnabler.isClosing()) {
                    //                        JLog.logk("invalid dds!  new " + integer + " , " + ServiceManager.cloudSimEnabler.getCard().getSubId());
                    //                        sendMessage(AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY);
                    //                        ServiceManager.accessMonitor.ddsSetIllegal();
                    //                    } else {
                    //                        JLog.logk("dds change to normal!");
                    //                        sendMessage(AccessEventId.EVENT_EXCEPTION_SET_DDS_NORMAL);
                    //                        ServiceManager.accessMonitor.ddsSetNormal();
                    //                    }
                }
            });
        }

        private void deListenDdsStatus() {
            unsubscriptSub(ddsStatusSub);
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            mExceptionCmdList.clear();
            ServiceManager.productApi.serviceStart();
            addStateListen();
            if (getWifiStatus()) {
                JLog.logd("wifi is on!!!");
                isWifiOn = true;
                updateCommMessage(4, "true");
            } else {
                JLog.logd("wifi is off!!!");
                isWifiOn = false;
                updateCommMessage(4, "false");
            }
            SeedNetworkStart.INSTANCE.clearCardSub();

            //用户开始登陆需要保存
            ServiceRestore.setStartServiceFlag(ctx);
            ServiceRestore.setNormalStartFlag(ctx);
            ServiceRestore.recordLoginParams(loginInfo);

            /*注册订阅*/
            //UploadFlowTask.INSTANCE.registerSubscription();
            if (!ServiceManager.systemApi.isUnderDevelopMode()) {
                CheckWifiApDunReq.INSTANCE.registerCardSub();
            }
            SeedStatusSaveForCloudsim.INSTANCE.registSubCardStatus();
            listenCardStatus();
            listenDdsStatus();
            ServiceManager.accessSeedCard.startService();
            // 种子软卡相关初始化
            mSeedUpdateTask = ServiceManager.productApi.getSeedUpdateTask();
            if (!ServiceManager.systemApi.isUnderDevelopMode()) {
                if (mSeedUpdateTask != null) {
                    mSeedUpdateTask.serviceStart();
                }
                TcpdumpHelper.getInstance().setBusinessRunning(true);
            }
            if (ServiceManager.productApi.isSeedAlwaysOn()) {
                Requestor.INSTANCE.requireChannel(ALWAYS_NEED_SEED);
            }
            finishEnter();
        }

        @Override
        public boolean processMessage(Message msg) {
            ErrorCode.ErrCodeInfo securityErr;
            logStateMsg(msg, getClass().getSimpleName());
            switch (msg.what) {
                case StateMessageId.USER_LOGOUT_COMM_PROC:
                    ServiceRestore.setLogoutFlag(ctx);
                    break;
                case AccessEventId.EVENT_SEEDSIM_ENABLE_FAIL:
                    JLog.logd("recv AccessEventId.EVENT_SEEDSIM_ENABLE_FAIL " + msg.obj);
                    int errcode = (int) msg.obj;
                    ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(String.valueOf(errcode));
                    JLog.logd("ErrCodeInfo " + info);
                    if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                        sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, info.getMsg());
                    } else {
                        processSystemErrcode(info.getCode());
                    }
                    break;
                case AccessEventId.EVENT_NET_SECURITY_CHECK_FAIL:
                    JLog.logd(TAG, "processMessage: recv EVENT_NET_SECURITY_CHECK_FAIL");
                    securityErr = ErrorCode.INSTANCE.getErrInfoByCode(ErrorCode.INSTANCE.getLOCAL_SECURITY_FAIL());
                    sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, securityErr.getCode(), 0, securityErr.getMsg());
                    break;
                case AccessEventId.EVENT_NET_SECURITY_CHECK_TIMEOUT:
                    JLog.logd(TAG, "processMessage: recv EVENT_NET_SECURITY_CHECK_TIMEOUT");
                    NetworkTest.INSTANCE.startNetworkTest();
                    //securityErr = ErrorCode.INSTANCE.getErrInfoByCode(ErrorCode.INSTANCE.getLOCAL_SECURITY_TIMEOUT());
                    //sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, securityErr.getCode(), 0, securityErr.getMsg());
                    break;
                case AccessEventId.EVENT_NET_SOCKET_TIMEOUT:
                    JLog.logd(TAG, "processMessage: recv EVENT_NET_SOCKET_TIMEOUT");
                    NetworkTest.INSTANCE.startNetworkTest();
                    break;
                case StateMessageId.WIFI_CONNECTED:
                    if (!isWifiOn) {
                        updateCommMessage(5, "true");
                        isWifiOn = true;
                    }
                    break;
                case StateMessageId.WIFI_DISCONNECTED:
                    if (isWifiOn) {
                        updateCommMessage(5, "false");
                        isWifiOn = false;
                    }
                    break;

                case AccessEventId.EVENT_S2CCMD_DOWMLOAD_EXT_SOFTSIM_REQ://服务器下软卡指令
                case AccessEventId.EVENT_S2CCMD_UPDATE_EXT_SOFTSIM_REQ://服务器下更新软卡指令
                    //                    ServiceManager.productApi.startDownLoadSoftsim();
                    if(mSeedUpdateTask!=null) {
                        mSeedUpdateTask.notifyS2C(msg.obj);
                    }
                    break;

                default:
                    logUnhandleMsg(msg, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            //            ServiceManager.INSTANCE.getCardController().initEnv(-1);
            mExceptionCmdList.clear();
            ServiceManager.productApi.serviceExit();
            removeStateListen();
            //退出服务，恢复手机用户配置
            ServiceRestore.clearLogoutFlag(ctx);
            ServiceRestore.setStopServiceFlag(ctx);
            ServiceRestore.stopNormalStartFlag(ctx);
            ServiceManager.productApi.restoreCheck();
            // 修改为在父状态中执行
            //FlowBandWidthControl.getInstance().clearForwardRetrict();
            FlowBandWidthControl.getInstance().clearBwSetting(AccessState.this);
            //FlowBandWidthControl.getInstance().clearUserRestrict();
            //FlowBandWidthControl.getInstance().release();
            ServiceManager.accessSeedCard.clearLastSoftsimNoCheck();
            deListenCardStatus();
            deListenDdsStatus();
            ServiceManager.accessSeedCard.stopService();
            JLog.logd("Action:Logout," + "seed flow:" + UploadFlowTask.INSTANCE.getSeedTxFlow() + "," + UploadFlowTask.INSTANCE.getSeedRxFlow());
            if (mSeedUpdateTask != null) {
                mSeedUpdateTask.serviceEnd();
                mSeedUpdateTask = null;
            }
            if (!ServiceManager.systemApi.isUnderDevelopMode()) {
                TcpdumpHelper.getInstance().setBusinessRunning(false);
            }
        }

        @Override
        protected String getPercent() {
            return "5";
        }
    }

    private class InitState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            //monitor.startDDSMonitor();
            finishEnter();
        }

        private void removeException(int cmd) {
            JLog.loge("recv del exception " + cmd + ",  is wifion? " + isWifiOn);
            if (isWifiOn) {
                JLog.logd("remove except cmd:" + cmd);
                Iterator<Integer> iterator = mExceptionCmdList.iterator();
                if (iterator.hasNext()) {
                    if (cmd == iterator.next()) {
                        JLog.logd("find cmd and del!!");
                        iterator.remove();
                    }
                }
            }
        }

        @Override
        public boolean processMessage(Message message) {
            Integer cmdExcept;
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case AccessEventId.EVENT_NET_EXCEPTION:
                    break;
                case AccessEventId.EVENT_NET_JAM:
                    break;
                case AccessEventId.EVENT_SEEDSIM_CARD_LOST:
                    if (!isLoginOnce) {
                        processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHY_CARD_NOT_EXIST()); // TODO: 2017/5/23 需要区分物理卡和软卡
                    } else {
                        JLog.loge("system login before, so ignore this event!");
                    }
                    break;
                case AccessEventId.EVENT_EXCEPTION_DATA_NOT_ENABLED:
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHONE_DATA_DISABLED());
                    break;
                case AccessEventId.EVENT_SEEDSIM_ROAMING_NOT_ENABLED:
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_ROAM_NOT_ENABLED());
                    break;
                /*
                case AccessEventId.EVENT_SEEDSIM_SOFTSIM_DEFAULT_ENABLE:
                    UploadFlowTask.INSTANCE.enableSeedCard();
                    break;
                case  AccessEventId.EVENT_SEEDSIM_SOFTSIM_DUN_DISABLE:
                    UploadFlowTask.INSTANCE.disableSeedCard();
                    break;
                case AccessEventId.EVENT_EXCEPTION_AIRMODE_10MIN:
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_AIR_MODE_ENABLED());
                    break;
                */
                case AccessEventId.EVENT_SEEDSIM_PHYCARD_DEFAULT_FAIL:
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_PHY_NETWORK_UNAVAILABLE());
                    break;
                case AccessEventId.EVENT_SEEDSIM_IN_SERVICE:
                    if(isPhyToSoft){
                        isPhyToSoft = false;
                        transToNextState(mSeedChEstablishState);
                        break;
                    }
                case AccessEventId.EVENT_SEEDSIM_DATA_CONNECT:
                case AccessEventId.EVENT_SEEDSIM_DATA_DISCONNECT:
                case AccessEventId.EVENT_SEEDSIM_INSERT:
                case AccessEventId.EVENT_SEEDSIM_READY:
                case AccessEventId.EVENT_SEEDSIM_OUT_OF_SERVICE:
                case AccessEventId.EVENT_SEEDSIM_START_PS_CALL:
                case AccessEventId.EVENT_SEEDSIM_PS_CALL_SUCC:
                case AccessEventId.EVENT_NET_SOCKET_CONNECTED:
                case AccessEventId.EVENT_NET_SOCKET_DISCONNECT:
                case AccessEventId.EVENT_SEEDSIM_ENABLE:
                case AccessEventId.EVENT_SEEDSIM_DISABLE:
                    seedCardProcess(message);
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONECALL_START_BEFORE_SERVICE_START:
                    if (isWifiOn) {
                        boolean find = false;
                        for (Integer x : mExceptionCmdList) {
                            if (x == AccessEventId.EVENT_EXCEPTION_PHONECALL_START) {
                                find = true;
                            }
                        }
                        if (!find) {
                            mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_PHONECALL_START);
                        }
                    } else {
                        beforeExceptionState = currState;
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_PHONECALL_START);
                        transToNextState(mExceptionState);
                    }
                    break;
                case AccessEventId.EVENT_EXCEPTION_AIRMODE_OPEN:
                case AccessEventId.EVENT_EXCEPTION_PHONECALL_START:
                case AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE:
                case AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE:
                case AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE:
                case AccessEventId.EVENT_EXCEPTION_ADD_TO_BALCK_LIST:
                case AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY:
                case AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START:
                    JLog.loge("recv exception " + message.what + ",  is wifion? " + isWifiOn + " exception:" + mExceptionCmdList);
                    if (AccessEventId.EVENT_EXCEPTION_PHONECALL_START == message.what) {
                        sendMessage(AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END);
                        //                        SoftSimStateMark.INSTANCE.handlePauseCloudSimAction(true);
                    }

                    if (isWifiOn) {
                        boolean find = false;
                        for (Integer x : mExceptionCmdList) {
                            if (x == message.what) {
                                find = true;
                            }
                        }
                        if (!find) {
                            mExceptionCmdList.add(message.what);
                        }
                    } else {
                        beforeExceptionState = currState;
                        mExceptionCmdList.add(message.what);
                        transToNextState(mExceptionState);
                    }
                    break;
                case AccessEventId.EVENT_EXCEPTION_AIRMODE_CLOSE:
                    removeException(AccessEventId.EVENT_EXCEPTION_AIRMODE_OPEN);
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONECALL_STOP:
                    removeException(AccessEventId.EVENT_EXCEPTION_PHONECALL_START);
                    //                    SoftSimStateMark.INSTANCE.handlePauseCloudSimAction(false);
                    break;
                case AccessEventId.EVENT_EXCEPTION_SEED_CARD_ENABLE:
                    removeException(AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE);
                    break;
                case AccessEventId.EVENT_EXCEPTION_COULD_CARD_ENABLE:
                    removeException(AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE);
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONE_DATA_ENABLE:
                    removeException(AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE);
                    break;
                case AccessEventId.EVENT_EXCEPTION_DEL_FROM_BALCK_LIST:
                    removeException(AccessEventId.EVENT_EXCEPTION_ADD_TO_BALCK_LIST);
                    break;
                case AccessEventId.EVENT_EXCEPTION_SET_DDS_NORMAL:
                    removeException(AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY);
                    break;
                case AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END:
                    removeException(AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START);
                    break;
                case StateMessageId.WIFI_DISCONNECTED:
                    JLog.logd("recv wifi disconnected!!! isWifi? " + isWifiOn + "exception:" + mExceptionCmdList);
                    if (mExceptionCmdList.size() != 0) {
                        beforeExceptionState = currState;
                        transToNextState(mExceptionState);
                    }
                    return NOT_HANDLED;
                case AccessEventId.EVENT_BUSINESS_RESTART:
                    isBUSINESS_RESTART = true;//触发大循环
                    mResetCoudsimReason = REASON_MONITOR_RESTART;
                    mNextStateAfterAllRest = mSeedChEstablishState;
                    PerfLogBigCycle.INSTANCE.create(0,0,currState.getName());
                    transToNextState(mWaitResetCardState);
                    break;
                case AccessEventId.EVENT_SEED_MCC_CHANGE:
                    String mcc = (String) message.obj;
                    if (ServiceManager.productApi.reloginAfterMccChange(mcc)) {
                        JLog.logd("EVENT_SEED_MCC_CHANGE need relogin,currState:"+currState.getName());
                        if (currState != mSeedChEstablishState && currState != mPreloginIpCheckState) {
                            mNextStateAfterAllRest = mLoginState;
                            mResetCoudsimReason = REASON_MCC_CHANGE;
                            transToNextState(mWaitResetCardState);
                        }
                    }
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "5";
        }
    }

    private class ExceptionState extends BaseState {
        private Date startTime;
        private              boolean     isSendingExceptionTimeout = false;
        private              ExceptEvent airModeExcept             = new ExceptEvent();
        private              ExceptEvent phoneCallExcept           = new ExceptEvent();
        private              ExceptEvent seedCardExcept            = new ExceptEvent();
        private              ExceptEvent cloudCardExcept           = new ExceptEvent();
        private              ExceptEvent phoneDataExcept           = new ExceptEvent();
        private              ExceptEvent appBlackListExcept        = new ExceptEvent();
        private              ExceptEvent ddsException              = new ExceptEvent();
        private              ExceptEvent outGoingCallException     = new ExceptEvent();
        private static final int         EXCEPTION_TIMEOUT_VALUE   = 10; // unit:min
        private              boolean     needLogout                = false;

        private int getFirstExceptionTimeoutErrCode() {
            if (seedCardExcept.getInException()) {
                return ErrorCode.INSTANCE.getLOCAL_SEED_CARD_DISABLE_OVER_10MIN();
            }

            if (cloudCardExcept.getInException()) {
                return ErrorCode.INSTANCE.getLOCAL_CLOUD_CARD_DISABLE_OVER_10MIN();
            }

            if (phoneDataExcept.getInException()) {
                return ErrorCode.INSTANCE.getLOCAL_PHONE_DATA_DISABLE_OVER_10MIN();
            }

            if (appBlackListExcept.getInException()) {
                return ErrorCode.INSTANCE.getLOCAL_APP_IN_BLACKLIST_OVER_10MIN();
            }

            if (ddsException.getInException()) {
                return ErrorCode.INSTANCE.getLOCAL_DDS_EXCEPTION_OVER_10MIN();
            }

            return 0;
        }

        private void startSendExceptionTimeout() {
            if (!isSendingExceptionTimeout) {
                isSendingExceptionTimeout = true;
                sendMessageDelayed(StateMessageId.EXCEPTION_TIMEOUT, TimeUnit.MINUTES.toMillis(EXCEPTION_TIMEOUT_VALUE));
            }
        }

        private void clearExceptionTimeout() {
            if (isSendingExceptionTimeout) {
                isSendingExceptionTimeout = false;
                removeMessages(StateMessageId.EXCEPTION_TIMEOUT);
            }
        }

        private void checkClearExceptionTimeout() {
            int code = getFirstExceptionTimeoutErrCode();
            if (code == 0) {
                clearExceptionTimeout();
            }
        }

        private void getExceptEntry() {
            JLog.logd("last exception list!!!:" + mExceptionCmdList);
            for (Integer cmd : mExceptionCmdList) {
                switch (cmd) {
                    case AccessEventId.EVENT_EXCEPTION_AIRMODE_OPEN:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_AIR_MODE_ENABLE());
                        airModeExcept.startException();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START:
                        //                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_OUT_GOING_CALL_IN_EXCEPTION());
                        outGoingCallException.startException();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_PHONECALL_START:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_PHONE_CALL_START());
                        phoneCallExcept.startException();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_SEED_SIM_DISABLE());
                        seedCardExcept.startException();
                        startSendExceptionTimeout();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_CLOUD_SIM_DISABLE());
                        cloudCardExcept.startException();
                        startSendExceptionTimeout();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_PHONE_DATA_DISABLE());
                        phoneDataExcept.startException();
                        startSendExceptionTimeout();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_ADD_TO_BALCK_LIST:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_APP_TO_BLACKLIST());
                        appBlackListExcept.startException();
                        startSendExceptionTimeout();
                        break;
                    case AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY:
                        updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_DDS_IN_EXCEPTION());
                        ddsException.startException();
                        startSendExceptionTimeout();
                        break;
                    default:
                        JLog.loge("cannnot process cmd:" + cmd);
                        break;
                }
            }
            mExceptionCmdList.clear();
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            releaseChannel();
            startTime = new Date();
            getExceptEntry();
            updateExceptionStart();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message msg) {
            logStateMsg(msg, getClass().getSimpleName());
            switch (msg.what) {
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    setLogoutMsg(msg.arg1, (String) msg.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mResetCoudsimReason = (String) msg.obj;
                    mNextStateAfterAllRest = mLogoutWaitState;
                    transToNextState(mWaitResetCardState);
                    break;
                case AccessEventId.EVENT_EXCEPTION_AIRMODE_OPEN:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_AIR_MODE_ENABLE());
                    airModeExcept.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_AIRMODE_CLOSE:
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_USER_AIR_MODE_ENABLE());
                    airModeExcept.stopException();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONECALL_START:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_PHONE_CALL_START());
                    phoneCallExcept.startException();
                    //电话打通了，取消呼出处理异常
                    sendMessage(AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END);
                    //                    SoftSimStateMark.INSTANCE.handlePauseCloudSimAction(true);
                    break;
                case AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START:
                    //                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_OUT_GOING_CALL_IN_EXCEPTION());
                    outGoingCallException.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONECALL_STOP:
                    //                    SoftSimStateMark.INSTANCE.handlePauseCloudSimAction(false);
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_USER_PHONE_CALL_START());
                    phoneCallExcept.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_SEED_SIM_DISABLE());
                    seedCardExcept.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_SEED_CARD_ENABLE:
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_USER_SEED_SIM_DISABLE());
                    seedCardExcept.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_CLOUD_SIM_DISABLE());
                    cloudCardExcept.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_COULD_CARD_ENABLE:
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_USER_CLOUD_SIM_DISABLE());
                    cloudCardExcept.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_PHONE_DATA_DISABLE());
                    phoneDataExcept.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_PHONE_DATA_ENABLE:
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_USER_PHONE_DATA_DISABLE());
                    phoneDataExcept.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_ADD_TO_BALCK_LIST:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_USER_APP_TO_BLACKLIST());
                    appBlackListExcept.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_DEL_FROM_BALCK_LIST:
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_USER_APP_TO_BLACKLIST());
                    appBlackListExcept.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY:
                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_DDS_IN_EXCEPTION());
                    ddsException.startException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_SET_DDS_NORMAL:
                    updateCommMessage(7, "" + ErrorCode.INSTANCE.getLOCAL_DDS_IN_EXCEPTION());
                    ddsException.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END:
                    //                    updateCommMessage(6, "" + ErrorCode.INSTANCE.getLOCAL_OUT_GOING_CALL_IN_EXCEPTION());
                    outGoingCallException.stopException();
                    checkClearExceptionTimeout();
                    checkQuitException();
                    break;
                case AccessEventId.EVENT_CLOUDSIM_DATA_LOST:
                    JLog.logd("recv vsim data lost, last is " + beforeExceptionState.getName());
                    if (beforeExceptionState == mVsimEstablishedState) {
                        beforeExceptionState = mVsimRegState;
                    }
                    break;
                case AccessEventId.EVENT_CLOUDSIM_DATA_ENABLED:
                    JLog.logd("recv EVENT_CLOUDSIM_DATA_ENABLED , last is " + beforeExceptionState.getName());
                    if (beforeExceptionState == mVsimRegState || beforeExceptionState == mVsimDatacallState) {
                        beforeExceptionState = mVsimEstablishedState;
                    }
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CRASH:
                    JLog.logd("recv cloudsim crash, " + beforeExceptionState.getName());
                    beforeExceptionState = mStartVsimState;
                    break;
                case AccessEventId.EVENT_CLOUDSIM_REGISTER_NETWORK:
                    JLog.logd("recv EVENT_CLOUDSIM_REGISTER_NETWORK, " + beforeExceptionState.getName());
                    if (beforeExceptionState == mStartVsimState || beforeExceptionState == mVsimRegState) {
                        beforeExceptionState = mVsimDatacallState;
                    }
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CARD_READY:
                    JLog.logd("recv AccessEventId.EVENT_CLOUDSIM_CARD_READY, before" + beforeExceptionState.getName());
                    if (beforeExceptionState == mStartVsimState) {
                        beforeExceptionState = mVsimRegState;
                    }
                    break;
                case StateMessageId.WIFI_CONNECTED:
                    JLog.logd("recv wifi connected!!!");
                    if (airModeExcept.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_AIRMODE_OPEN);
                    }
                    if (phoneCallExcept.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_PHONECALL_START);
                    }
                    if (seedCardExcept.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE);
                    }
                    if (cloudCardExcept.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE);
                    }
                    if (phoneDataExcept.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE);
                    }
                    if (appBlackListExcept.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_ADD_TO_BALCK_LIST);
                    }
                    if (ddsException.getInException()) {
                        mExceptionCmdList.add(AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY);
                    }
                    transToNextState(beforeExceptionState);
                    return NOT_HANDLED;
                case StateMessageId.EXCEPTION_TIMEOUT:
                    clearExceptionTimeout();
                    JLog.loge("EXCEPTION_TIMEOUT so exit!");
                    int code = getFirstExceptionTimeoutErrCode();
                    if (code == 0) {
                        JLog.loge("unknown err exception!!!!");
                    } else {
                        if (airModeExcept.getInException() || phoneCallExcept.getInException()) {
                            JLog.loge("air or phonecall is running, so wait for it terminal");
                            needLogout = true;
                        } else {
                            sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, code, 0, ErrorCode.INSTANCE.getErrMsgByCode(code));
                        }
                    }
                    break;
                default:
                    logUnhandleMsg(msg, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            airModeExcept.clearException();
            phoneCallExcept.clearException();
            seedCardExcept.clearException();
            cloudCardExcept.clearException();
            phoneDataExcept.clearException();
            appBlackListExcept.clearException();
            ddsException.clearException();
            clearExceptionTimeout();
            updateExceptionStop();
            lastState = this;
        }

        private boolean checkException() {
            JLog.logd("current exception state:  airModeExcept:" + airModeExcept + " phoneCallExcept:" + phoneCallExcept + " seedCardExcept:" + seedCardExcept + " cloudCardExcept:" + cloudCardExcept + " phoneDataExcept:" + phoneDataExcept + " appBlackListExcept:" + appBlackListExcept + " outGoingCallException:" + outGoingCallException);
            if (airModeExcept.getInException() || phoneCallExcept.getInException() || seedCardExcept.getInException() || cloudCardExcept.getInException() || phoneDataExcept.getInException() || appBlackListExcept.getInException() || ddsException.getInException() || outGoingCallException.getInException()) {
                return true;
            }
            return false;
        }

        private void checkQuitException() {
            if (checkException()) {
                JLog.loge("still in exception!");
            } else {
                Date endTime = new Date();
                JLog.logk("end the exception state: " + endTime);

                int code = getFirstExceptionTimeoutErrCode();
                if (needLogout && code != 0) {
                    sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, code, 0, ErrorCode.INSTANCE.getErrMsgByCode(code));
                } else if (endTime.getTime() - startTime.getTime() > TimeUnit.MINUTES.toMillis(EXCEPTION_TIMEOUT_VALUE)) {
                    JLog.logk("relogin action!!");
                    reloginReason = "exception expire 10 min!";
                    mResetCoudsimReason = "relogin:" + reloginReason;
                    mNextStateAfterAllRest = mLoginState;
                    transToNextState(mWaitResetCardState);
                } else {
                    JLog.logk("exception over!!! trans to last state:" + beforeExceptionState.getName());
                    transToNextState(beforeExceptionState);
                    sendMessage(StateMessageId.CHECK_CLOUDSIM_STATE);
                }
            }
        }

        public ArrayList<Integer> getExceptionArray() {
            ArrayList<Integer> result = new ArrayList<Integer>();
            if (airModeExcept.getInException()) {
                //                JLog.logd("add UC_USER_AIR_MODE_START");
                result.add(ErrorCode.INSTANCE.getLOCAL_USER_AIR_MODE_ENABLE());
            }
            if (phoneCallExcept.getInException()) {
                //                JLog.logd("add UC_USER_IN_PHONE_CALL_START");
                result.add(ErrorCode.INSTANCE.getLOCAL_USER_PHONE_CALL_START());
            }
            if (seedCardExcept.getInException()) {
                //                JLog.logd("add UC_USER_SEED_CARD_DISABLE");
                result.add(ErrorCode.INSTANCE.getLOCAL_USER_SEED_SIM_DISABLE());
            }
            if (cloudCardExcept.getInException()) {
                //                JLog.logd("add UC_USER_CLOUD_CARD_DISABLE");
                result.add(ErrorCode.INSTANCE.getLOCAL_USER_CLOUD_SIM_DISABLE());
            }
            if (phoneDataExcept.getInException()) {
                //                JLog.logd("add UC_USER_PHONE_DATA_DISABLE");
                result.add(ErrorCode.INSTANCE.getLOCAL_USER_PHONE_DATA_DISABLE());
            }
            if (appBlackListExcept.getInException()) {
                //                JLog.logd("add UC_USER_APP_IN_BLACKLIST");
                result.add(ErrorCode.INSTANCE.getLOCAL_USER_APP_TO_BLACKLIST());
            }
            if (ddsException.getInException()) {
                result.add(ErrorCode.INSTANCE.getLOCAL_DDS_IN_EXCEPTION());
            }
            return result;
        }
    }

    /**
     * 种子通道网络建立状态
     */
    private class SeedChEstablishState extends BaseState {
        private Subscription mSubGetLoginNetInfo;
        private int perError=-1;

        @Override
        public void enter() {
            super.enter();
            currState = this;
            if(checkPhoneState()){//正在打电话状态，进入exceptionState
                sendMessage(AccessEventId.EVENT_EXCEPTION_PHONECALL_START_BEFORE_SERVICE_START);
            }else {
                setStateProcess(10);
                safeGetLoginNetworkInfo();
            }
            finishEnter();
        }

        @Override
        public void exit() {
            super.exit();
            safeUnsubscripHandle();
            removeMessages(StateMessageId.RETRY_GET_NETWORK_STATE_REQ);
            lastState = this;
            JLog.logd("exit SeedChEstablishState");
        }

        @Override
        protected String getPercent() {
            return "10";
        }

        //检测当前电话的状态
        private boolean checkPhoneState(){
            if(CardStateMonitor.mCallState){
                return true;
            }
            return false;
        }

        private void safeUnsubscripHandle() {
            if (mSubGetLoginNetInfo != null && !mSubGetLoginNetInfo.isUnsubscribed()) {
                JLog.logd("mSubGetLoginNetInfo.unsubscribe");
                mSubGetLoginNetInfo.unsubscribe();
            }
        }

        private Subscription getLoginNetworkInfo() {
            Subscriber<NetInfo> sub = new Subscriber<NetInfo>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable e) {
                    JLog.logd("getLoginNetworkInfo on error " + e.getMessage());
                    sendMessage(StateMessageId.GET_NETWORK_STATE_RSP_CMD, -1, 0, e);
                }

                @Override
                public void onNext(NetInfo info) {
                    JLog.logd("getLoginNetworkInfo on success");
                    sendMessage(StateMessageId.GET_NETWORK_STATE_RSP_CMD, 0, 0, info);
                    updateSeedPlmn();
                }
            };

            return NetworkManager.INSTANCE.refreshNetworkInfo().subscribe(sub);
        }

        private void safeGetLoginNetworkInfo() {
            safeUnsubscripHandle();
            mSubGetLoginNetInfo = getLoginNetworkInfo();
        }

        private boolean saveNetInfo(NetInfo info) {
            if (info.checkNetInfoParamValid()) {
                JLog.logd("saveNetInfo for seed network: " + info);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.GET_NETWORK_STATE_RSP_CMD:
                    if (message.arg1 == 0) {
                        JLog.logd("GET_NETWORK_STATE_RSP_CMD OK:" + (NetInfo) message.obj);
                        if (saveNetInfo((NetInfo) message.obj)) {
                            socket_timeout = TimeoutValue.SOCKET_CONNECT_FIRST_TIME_TIMEOUT;
                            transToNextState(mPreloginIpCheckState);
                        }
                        perError = -1;
                    } else {
                        String err = ErrorCode.INSTANCE.getErrString((Throwable) (message.obj));
                        ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
                        JLog.logd("StateMessageId.GET_NETWORK_STATE_RSP_CMD: " + info + " err:" + err);
                        if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                            terFlowReason = info.getCode();
                            termFlowReasonInfo = ErrorCode.INSTANCE.getErrMsgByCode(terFlowReason);
                            mResetCoudsimReason = termFlowReasonInfo;
                            mNextStateAfterAllRest = mDefaultState;
                            transToNextState(mWaitResetCardState);
                        } else {
                            processSystemErrcode(info.getCode());
                            sendMessageDelayed(StateMessageId.RETRY_GET_NETWORK_STATE_REQ, TimeUnit.SECONDS.toMillis(TimeoutValue.RETRY_GET_NETWORK_INFO_DELAY));
                            JLog.logd("GET_NETWORK_STATE_RSP_CMD Fail :Delay 5 senconds to retry get network info");
                            //todo 后期通过错误码处理 #31330 不插卡设备重启后web一直显示10%
                            if (isBUSINESS_RESTART) {
                                if (perError != info.getCode()) {
                                    if (ServiceManager.productApi.getProductType() == ProductTypeEnum.MIFI) {
                                        JLog.logd("info.getCode()=" + info.getCode() + "  ErrorCode.INSTANCE.getErrMsgByCode(info.getCode())=" + ErrorCode.INSTANCE.getErrMsgByCode(info.getCode()));
                                        updateError(info.getCode(), ErrorCode.INSTANCE.getErrMsgByCode(info.getCode()));
                                        perError = info.getCode();
                                    }
                                }
                            }
                        }
                    }
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    setLogoutMsg(message.arg1, (String) message.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mResetCoudsimReason = (String) message.obj;
                    mNextStateAfterAllRest = mDefaultState;
                    transToNextState(mWaitResetCardState);
                    break;
                case StateMessageId.RETRY_GET_NETWORK_STATE_REQ:
                    safeGetLoginNetworkInfo();
                    break;
                default:
                    JLog.logd("processMessage: " + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    /**
     * 预登陆状态:尝试建立种子卡socket通道和必要时获取路由表
     */
    private class PreloginIpCheckState extends BaseState {
        private Subscription msubIpCheckState;
        private Subscription seedCardSub;
        private Subscription seedExceptSub;
        private Subscription seedStatuSub;
        private int preloginCheckIpSocket_count  = 0;//ip轮询次数
        private int preloginCheckIpSocket_count2 = 0;//ip轮询次数(种子卡网络可用情况)
        private boolean isSeedAbsent = false;

        @Override
        public void enter() {
            super.enter();
            isPhyToSoft = false;
            currState = this;
            setStateProcess(20);
            preloginCheckIpSocket_count = 0;
            preloginCheckIpSocket_count2 = 0;
            listeningSeedCardException();
            safePreloginCheckIpSocket();
            //CrossBorder.INSTANCE.initSeedCardNetOperator();

            finishEnter();
        }

        @Override
        public void exit() {
            super.exit();
            JLog.logd("exit PreloginIpCheckState");
            safeUnsubscribeHandle();
            unsubscribeSeedExceptSub();
            unsubscribeSeedCardStatu();
            lastState = this;
            preloginCheckIpSocket_count = 0;
            preloginCheckIpSocket_count2 = 0;
            isSeedAbsent = false;
            RequestGetRouteTable.getRequestGetRouteTable().stopSocket();
        }

        @Override
        protected String getPercent() {
            return "20";
        }

        private Subscription preloginCheckIpSocket() {
            final long beginTimeMillis = System.currentTimeMillis();
            return RouteTableManager.INSTANCE.getRouteTableFromRCIfNeed(RouteTableManager.RT_SOCKET_TIME_OUT, Configuration.INSTANCE.getUsername(), "AccessState").timeout(RouteTableManager.GET_RT_TOTAL_TIME_OUT, TimeUnit.SECONDS).subscribe(new Action1<Object>() {
                @Override
                public void call(Object o) {
                    JLog.logd("tRoute preloginCheckIpSocket success -> getRouteTableFromRCIfNeed" +
                            " -> ExcuteTimeMillis = "+ (System.currentTimeMillis() - beginTimeMillis));
                    sendMessage(StateMessageId.PRELOGIN_IP_CHECK_RSP_CMD, 0, 0, o);
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    JLog.loge("tRoute preloginCheckIpSocket Fail :" + throwable.getMessage());
                    sendMessage(StateMessageId.PRELOGIN_IP_CHECK_RSP_CMD, -1, 0, throwable);
                }
            });
        }

        private void safeUnsubscribeHandle() {
            if (msubIpCheckState != null && !msubIpCheckState.isUnsubscribed()) {
                JLog.logd("msubIpCheckState.unsubscribe()");
                msubIpCheckState.unsubscribe();
            }
        }

        private void safePreloginCheckIpSocket() {
            safeUnsubscribeHandle();

            preloginCheckIpSocket_count = preloginCheckIpSocket_count + 1;
            JLog.logd("tRoute safePreloginCheckIpSocket() , count=" + preloginCheckIpSocket_count);
            if (preloginCheckIpSocket_count > 3) {
                //超过3次停止
                sendMessage(StateMessageId.PRELOGIN_IP_FAIL_STOP, ErrorCode.INSTANCE.getLOCAL_GET_ROUTE_TABLE_FAIL());
            } else {
                msubIpCheckState = preloginCheckIpSocket();
            }
        }

        //种子卡网络可用情况重试
        private void safePreloginCheckIpSocketSeedNetConnect() {
            safeUnsubscribeHandle();
            preloginCheckIpSocket_count2 = preloginCheckIpSocket_count2 + 1;
            JLog.logd("tRoute safePreloginCheckIpSocketSeedNetConnect() , count2=" + preloginCheckIpSocket_count2);
            if (preloginCheckIpSocket_count2 > 3) {
                //超过3次停止
                sendMessage(StateMessageId.PRELOGIN_IP_FAIL_STOP, ErrorCode.INSTANCE.getLOCAL_GET_ROUTE_TABLE_FAIL());
            } else {
                msubIpCheckState = preloginCheckIpSocket();
            }
        }

        //监听种子卡异常
        private void listeningSeedCardException() {
            unsubscribeSeedExceptSub();
            seedExceptSub = ServiceManager.INSTANCE.getSeedCardEnabler().exceptionObser().subscribe(new Action1<EnablerException>() {
                @Override
                public void call(EnablerException e) {
                    JLog.logd("tRoute getSeedCardNetworkStatu() exceptionObser:" + e);
                    unsubscribeSeedExceptSub();
                    sendMessage(StateMessageId.SEED_CARD_EXCEPTION, e);
                }
            });
        }

        private void unsubscribeSeedExceptSub() {
            if (seedExceptSub != null && !seedExceptSub.isUnsubscribed()) {
                seedExceptSub.unsubscribe();
            }
        }

        //监听种子卡状态
        private void listeningSeedCardStatu() {
            unsubscribeSeedCardStatu();
            seedStatuSub = ServiceManager.INSTANCE.getSeedCardEnabler().cardStatusObser().subscribe(new Action1<CardStatus>() {
                @Override
                public void call(CardStatus status) {
                    sendMessage(SEED_CARD_STATU_CHANGE, status);
                }
            });
        }
        private void unsubscribeSeedCardStatu() {
            if (seedStatuSub != null && !seedStatuSub.isUnsubscribed()) {
                seedStatuSub.unsubscribe();
            }
        }

        /**
         * ping 操作 1次 5秒超时
         * -1-失败
         * 0-成功
         */
        private int pingOnetime(String ipAddress) {
            Process process = null;
            BufferedReader buf = null;
            int restult = -1;
            try {
                logd("pingOnetime:$ipAddress");
                process = Runtime.getRuntime().exec("ping -c 1 -W 5 " + ipAddress);
                buf = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = "";
                while ((line = buf.readLine()) != null) {
                    logd("pingOnetime:"+line);
                }
                restult = process.waitFor();
                logd("pingOnetime restult="+restult);
            } catch (Exception ex ) {
                ex.printStackTrace();
            } finally {

                if (process != null) {
                    process.destroy();
                }
                if (buf != null) {
                    try {
                        buf.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return restult;
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.PRELOGIN_IP_CHECK_RSP_CMD:
                    if (message.arg1 == 0) {
                        JLog.logd("tRoute PRELOGIN_IP_CHECK_RSP_CMD OK");

                        List<String> assList = RouteTableManager.INSTANCE.getLocalAssIPList(ServerRouter.INSTANCE.getCurrent_mode());
                        String assIP = ServerRouter.INSTANCE.getCurrent_AssIp();

                        if (assList != null && !assList.contains(assIP)) {
                            //目前ip不是ass ip则初始化设为ass ip
                            JLog.logd("tRoute PRELOGIN_IP_CHECK_RSP_CMD OK current ip != ass ip , init!");
                            ServerRouter.INSTANCE.initIpByCurrentMode();
                        }

                        transToNextState(mLoginState);
                    } else {
                        JLog.loge("tRoute PRELOGIN_IP_CHECK_RSP_CMD Fail:retry");

                        Throwable t = (Throwable) message.obj;
                        String errorMsg = RouteTableManager.INSTANCE.getErrMsg(t);
                        JLog.logd("tRoute -----------Retry or TestSpeed------------" + "   reson:" + errorMsg);

                        NetworkInfo.State netState = ServiceManager.INSTANCE.getSeedCardEnabler().getNetState();

                        if (errorMsg.equalsIgnoreCase("Fail:Ass and RC IP Socket Timeout") || errorMsg.equalsIgnoreCase("Fail:Socket SecureTimeout") || errorMsg.equalsIgnoreCase("Fail:Server Socket Timeout") || errorMsg.equalsIgnoreCase("Fail:Server Response Timeout") || errorMsg.equalsIgnoreCase("Fail:Netty Socket StatusObservable Timeout")) {

                            JLog.logd("tRoute netState：" + netState);
                            if (netState == NetworkInfo.State.CONNECTED) {

                                NetworkTest.INSTANCE.startNetworkTest();
                                safePreloginCheckIpSocketSeedNetConnect();

                            } else if (netState == NetworkInfo.State.DISCONNECTED) {
                                transToNextState(mSeedChEstablishState);
                            }
                            NetworkManager.INSTANCE.tracerouteToService();

                        } else if (errorMsg.equalsIgnoreCase("Fail:No Update") || errorMsg.equalsIgnoreCase("Fail:Result Fail") || errorMsg.equalsIgnoreCase("Fail:Not RouteTable")) {
                            JLog.logd("tRoute fatal error retry");
                            safePreloginCheckIpSocket();

                        } else if (errorMsg.equalsIgnoreCase("Fail:Fail:Socket SecureError")) {
                            //安全错误，停止并提示1024错误
                            sendMessage(StateMessageId.PRELOGIN_IP_FAIL_STOP, ErrorCode.INSTANCE.getLOCAL_SECURITY_FAIL());

                        } else {
                            //其他错误，重试
                            JLog.logd("tRoute other error retry");
                            safePreloginCheckIpSocket();
                        }
                    }
                    break;
                case StateMessageId.DELAY_RETRY_IP_CHECK_REQ_CMD:
                    //                    safePreloginCheckIpSocket();
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    JLog.logd("tRoute USER_LOGOUT_REQ_CMD");
                    setLogoutMsg(message.arg1, message.obj.toString());
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mResetCoudsimReason = (String) message.obj;
                    mNextStateAfterAllRest = mDefaultState;
                    transToNextState(mWaitResetCardState);
                    break;
                case StateMessageId.SEED_CARD_EXCEPTION:
                    JLog.loge("tRoute recv seed card exception: " + (EnablerException) message.obj);
                    unsubscribeSeedExceptSub();
                    int code = ErrorCode.INSTANCE.getErrCodeByCardExceptId((EnablerException) message.obj);
                    ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByCode(code);
                    if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                        JLog.loge("tRoute recv seed card exception: logout!");
                        ServerRouter.INSTANCE.initIpByCurrentMode();
                        sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, ErrorCode.INSTANCE.getLOCAL_GET_ROUTE_TABLE_FAIL(), 0, ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getLOCAL_GET_ROUTE_TABLE_FAIL()));
                    } else if(info.getAction() == ErrorCode.ErrActType.ACT_RETRY){
                        logd("retry to checkip! after 10s");
                        sendMessageDelayed(PRELOGIN_IP_FAIL_RETRY, TimeUnit.SECONDS.toMillis(10));
                    }
                    break;

                case StateMessageId.PRELOGIN_IP_FAIL_STOP:
                    JLog.loge("tRoute PRELOGIN_IP_CHECK_RSP_CMD Fail:Stop");
                    //获取路由表停止运行，提示网络错误请稍后再试
                    int errorCode = message.arg1;
                    ErrorCode.ErrCodeInfo errCodeInfo = ErrorCode.INSTANCE.getErrInfoByCode(errorCode);
                    if (errorCode == ErrorCode.INSTANCE.getLOCAL_SECURITY_FAIL()) {
                        ServerRouter.INSTANCE.initIpByCurrentMode();
                        sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, errorCode, 0, errCodeInfo.getMsg());
                        break;
                    }
                    if (ServiceManager.seedCardEnabler.getCard().getCardType() == CardType.SOFTSIM) {

                        if (pingOnetime("t1.ukelink.com") != 0) {
                            int subId = ServiceManager.seedCardEnabler.getCard().getSubId();
                            logd("tRoute switch seednet:subId="+subId);
                            if (subId != -1) {
                                //切网
                                SeedPlmnSelector.INSTANCE.updateEvent(SEED_SOCKET_FAIL_MAX_EXCEPTION, subId);

                                //监听种子卡网络
                                listeningSeedCardStatu();
                                break;
                            }
                        }
                    }
                    //新增如果是物理卡的状态则切换软卡
                    else if(!isPhyToSoft && ServiceManager.seedCardEnabler.getCard().getCardType() == CardType.PHYSICALSIM){
                        JLog.logi(": ------  切卡开始");
                        NetworkTest.seedEnabler.notifyEventToCard(DataEnableEvent.EVENT_PHY_TO_SOFT, Ncsi.NcsiResult.DNS_SERVER_UNREACH);
                        isPhyToSoft = true;
                        break;
                    }
                    //新增结束
                    logd("tRoute not switch seednet stop!");
                    ServerRouter.INSTANCE.initIpByCurrentMode();
                    sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, errCodeInfo.getCode(), 0, errCodeInfo.getMsg());
                    break;
                case SEED_CARD_STATU_CHANGE:
                    CardStatus cardStatus = (CardStatus) message.obj;
                    if (cardStatus == CardStatus.ABSENT){
                        isSeedAbsent = true;
                    }
                    JLog.logd("tRoute SEED_CARD_STATU_CHANGE:"+cardStatus+"; isSeedAbsent="+isSeedAbsent);
                    if (isSeedAbsent && cardStatus == CardStatus.IN_SERVICE){
                        //切网后种子卡起来了
                        isSeedAbsent = false;
                        preloginCheckIpSocket_count = 0;
                        safePreloginCheckIpSocket();
                    }
                    break;
                case PRELOGIN_IP_FAIL_RETRY:
                    JLog.logd("tRoute PRELOGIN_IP_FAIL_RETRY");
                    msubIpCheckState = preloginCheckIpSocket();
                    break;

                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class LoginState extends BaseState {
        private              Subscription      loginSub              = null;
        private              int               loginCount            = 0;//总的错误次数
        private              ArrayList<String> loginFailReasons      = new ArrayList<String>();
        private              int               sameErrTimes          = 0;
        private final        int[]             sameErrLoginWaitTime  = {15, 120, 60, 240, 60, 360, 60, 480, 60, 600}; //sec
        private final static int               loginTimesTh          = 20;
        private final static int               timeoutReloginWait    = 10; // min
        private final static int               sameErrMaxCount       = 10;
        private final static int               loginMaxCount         = 20;
        private final static int               loginReleaseChannelTh = 30; // uinit:s
        Long loginTime = 0L;//登陆时间

        private void startLogin(String username, String passwd) {
            JLog.logk("startLogin: " + username);
            if (loginSub != null) {
                if (!loginSub.isUnsubscribed()) {
                    loginSub.unsubscribe();
                }
            }

            boolean isRelogin = (lastState != mPreloginIpCheckState);
            loginTime = System.currentTimeMillis();
            PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_LOGIN(),0,"");
            PerfLogVsimDelayStat.INSTANCE.create(PerfLogVsimDelayStat.INSTANCE.getLOGINRTT_START(), 0, "");
            PerfLogSsimLogin.INSTANCE.create(PerfLogSsimLogin.INSTANCE.getLOGIN_REQ_EVENT(),0, 0);
            loginSub = session.login(username, passwd, TimeoutValue.getLoginTimeout(), isRelogin, reloginReason, isBUSINESS_RESTART).timeout(TimeoutValue.getLoginTimeoutTotal(), TimeUnit.SECONDS).subscribe(new Action1<LoginResp>() {
                @Override
                public void call(LoginResp loginResp) {
                    JLog.loge("login rsp over");
                    loginInfo = ServiceManager.productApi.setLoginInfo(loginInfo, loginResp);
                    if (loginResp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()) {
                        if (loginResp.sessionId == null || loginResp.sessionId.length() == 0) {
                            sendMessage(StateMessageId.USER_LOGIN_RSP_CMD, -1, 0, new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ErrorCode.INSTANCE.getRPC_INVALID_SESSION()));
                        } else {
                            mSessionId = loginResp.sessionId;
                            ProtoPacketUtil.getInstance().setSession(mSessionId);
                            Requestor.INSTANCE.setSessionId(mSessionId);
                            JLog.logd("login succ!!" + loginResp);
                            sendMessage(StateMessageId.USER_LOGIN_RSP_CMD, 0);
                        }
                    } else {
                        sendMessage(StateMessageId.USER_LOGIN_RSP_CMD, -1, 0, new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + loginResp.errorCode));
                    }
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable t) {
                    t.printStackTrace();
                    JLog.loge("login fail:" + t.getMessage());
                    unsubscriptSub(NetworkManager.INSTANCE.getPollingASSIpSub());
                    sendMessage(StateMessageId.USER_LOGIN_RSP_CMD, -1, 0, t);
                }
            });
            JLog.logd("login start: " + loginSub.hashCode());
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(35);
            FlowBandWidthControl.getInstance().clearBwSetting(AccessState.this);
            if (lastState != mWaitReloginState) {
                loginCount = 0;
                loginFailReasons.clear();
            }
            requireChannel();
            loginTime = 0L;
            startLogin(loginInfo.getUsername(), loginInfo.getPasswd());
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.USER_LOGIN_RSP_CMD:
                    PerfLogVsimDelayStat.INSTANCE.create(PerfLogVsimDelayStat.INSTANCE.getLOGINRTT_END(), 0, "");
                    switch (message.arg1) {
                        case 0:
                            reloginReason = "";//登陆成功重置重登录原因和是否触发大循环
                            isBUSINESS_RESTART = false;

                            loginCount = 0;
                            PerformanceStatistics.INSTANCE.setProcess(ProcessState.LOGIN_OK);
                            PerfLogSsimLogin.INSTANCE.create(PerfLogSsimLogin.INSTANCE.getLOGIN_RSP_EVENT(),0, new SsimLoginRspData(0, mSessionId));
                            transToNextState(mInServiceState);
                            break;
                        case -1:
                            loginFailProcess(message.arg1, message.arg2, message.obj);
                            break;
                        default:
                            break;
                    }
                    break;
                case StateMessageId.USER_LOGIN_RETRY_CMD:
                    startLogin(loginInfo.getUsername(), loginInfo.getPasswd());
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    reloginReason = "";//重置重登录原因和是否触发大循环
                    isBUSINESS_RESTART = false;
                    setLogoutMsg(message.arg1, (String) message.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mResetCoudsimReason = (String) message.obj;
                    mNextStateAfterAllRest = mLogoutWaitState;
                    transToNextState(mWaitResetCardState);
                    break;

                case AccessEventId.EVENT_S2CCMD_RELOGIN:
                    reloginReason = "EVENT_S2CCMD_RELOGIN";
                    mNextStateAfterAllRest = mLoginState;
                    //路由重定向：重登录需要再次获取可用ip
                    if (message.arg2 == 5) {
                        mNextStateAfterAllRest = mSeedChEstablishState;
                        reloginReason = "S2c_redirect_route";
                    }
                    JLog.logk("relogin action!!" + reloginReason);
                    mResetCoudsimReason = "user relogin, " + reloginReason;
                    transToNextState(mWaitResetCardState);
                    break;

                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            if (nextState != mWaitReloginState) {
                loginCount = 0;
                loginFailReasons.clear();
            }
            unsubscriptSub(loginSub);
            lastState = this;
            loginTime = 0L;
        }

        @Override
        protected String getPercent() {
            return "35";
        }

        private void loginFailProcess(int arg1, int arg2, Object o) {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);
            int waitTime = 0;

            JLog.logd("login failed:" + err);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.logd("get login errinfo:" + info);
            PerfLogSsimLogin.INSTANCE.create(PerfLogSsimLogin.INSTANCE.getLOGIN_RSP_EVENT(),0, new SsimLoginRspData(info.getCode(), getSessionId()));
            if (info.getAction() == ErrorCode.ErrActType.ACT_TIMEOUT) {

                reloginReason = "login timeout!";
                if (loginCount >= loginTimesTh) {
                    sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, err));
                } else {
                    loginCount++;
                    startLogin(loginInfo.getUsername(), loginInfo.getPasswd());
                }
            } else {
                if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                    reloginReason = "";//重置重登录原因和是否触发大循环
                    isBUSINESS_RESTART = false;
                    terFlowReason = info.getCode();
                    termFlowReasonInfo = ErrorCode.INSTANCE.getErrRealMsg(info, err);
                    mResetCoudsimReason = "login fail! " + err;
                    mNextStateAfterAllRest = mLogoutWaitState;
                    transToNextState(mWaitResetCardState);
                } else if (info.getRpc()) {
                    processSystemErrcode(info.getCode());
                    waitTime = getWaitTime(err);
                    JLog.logk("do relogin: wait time:" + waitTime);
                    reloginReason = "rpc error:" + err;
                    if (sameErrTimes < sameErrMaxCount && loginCount < loginMaxCount) {
                        loginCount++;
                        if (waitTime < loginReleaseChannelTh) {
                            sendMessageDelayed(StateMessageId.USER_LOGIN_RETRY_CMD, TimeUnit.SECONDS.toMillis(waitTime));
                        } else {
                            transToNextState(mWaitReloginState);
                            sendMessageDelayed(StateMessageId.USER_QUIT_RELOGIN_STATE_CMD, TimeUnit.SECONDS.toMillis(waitTime));
                        }
                    } else {
                        sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, err));
                    }
                } else {
                    processSystemErrcode(info.getCode());
                    loginCount++;
                    loginFailReasons.add(err);
                    JLog.logk("do relogin!!! for other error!!" + err + ", wait for 5s");
                    sendMessageDelayed(StateMessageId.USER_LOGIN_RETRY_CMD, TimeUnit.SECONDS.toMillis(5));
                }
            }
        }

        /* need  */
        private int getWaitTime(String err) {
            //            String firstStr = null;
            sameErrTimes = 0;
            loginFailReasons.add(err);
            for (String str : loginFailReasons) {
                if (err.equals(str)) {
                    sameErrTimes++;
                }
            }
            return sameErrLoginWaitTime[sameErrTimes - 1];
        }
    }

    private class InServiceState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(40);
            heartBeatTimes = 0;
            if (useHearBeatAlarm) {
                heartbeatTask.start(TimeoutValue.getHeartbeatSendIntvlFirst(), TimeoutValue.getHeartbeatSendIntvlSecond());
            } else {
                sendMessageDelayed(StateMessageId.USER_HEART_BEAT_SEND_CMD, TimeUnit.SECONDS.toMillis(TimeoutValue.getHeartbeatSendIntvlFirst()));
            }
            if (lastState != mExceptionState) {
                if (needEnableVsim) {
                    transToNextState(mVsimBegin);
                }
            }
            isLoginOnce = true;
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    setLogoutMsg(message.arg1, (String) message.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mLogoutReason = (String) message.obj;
                    transToNextState(mLogoutState);
                    break;
                case StateMessageId.SWITCH_VSIM_MANAUL_CMD:
                    switchVsimReason = SwitchVsimReason.SWITCH_MANUAL;
                    startSwitchVsim();
                    break;
                case StateMessageId.USER_HEART_BEAT_SEND_CMD:
                    sendHeartBeat();
                    break;
                case StateMessageId.USER_HEART_BEAT_RSP_CMD:
                    heartBeatRspProc(message.arg1, message.arg2, message.obj);
                    break;
                case StateMessageId.USER_RELOGIN_REQ_CMD:
                    JLog.logk("relogin action!!" + reloginReason);
                    mResetCoudsimReason = "user relogin, " + reloginReason;
                    mNextStateAfterAllRest = mLoginState;
                    if (reloginReason.equalsIgnoreCase("S2c_redirect_route")) {
                        JLog.logk("tRoute relogin S2c_redirect_route  so NextState is mPreloginIpCheckState");
                        mNextStateAfterAllRest = mSeedChEstablishState;
                    }
                    transToNextState(mWaitResetCardState);
                    break;
                case StateMessageId.USER_ALARM_HEART_BEAT_SEND_CMD:
                    sendHeartBeat();
                    break;
                case StateMessageId.USER_REFRESH_HEART_BEAT_CMD:
                    refreshHeartBeat();
                    break;
                case AccessEventId.EVENT_S2CCMD_LOGOUT:
                    JLog.logd("recv AccessEventId.EVENT_S2CCMD_LOGOUT: 111" + message.arg1 + " " + (int) message.obj);
                    processS2cLogoutCmd(message.arg1, (int) message.obj);
                    break;
                case AccessEventId.EVENT_S2CCMD_SWAPCLOUDSIM:
                    switchVsimReason = SwitchVsimReason.SERVER_REQUEST;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_NET_MCC_CHANGE:
                    JLog.logd("recv EVENT_NET_MCC_CHANGE msg");
                    acessListenUpdateError(message.what, "EVENT_NET_MCC_CHANGE");
                    switchVsimReason = SwitchVsimReason.MCC_CHANGE;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_NET_RECONNECT_MSG_FAIL:
                    JLog.logd("recv AccessEventId.EVENT_NET_RECONNECT_MSG_FAIL");
                    processReconnectErrMsg(message.arg1, message.arg2, message.obj);
                    break;
                case AccessEventId.EVENT_NET_APDU_MSG_FAIL:
                    JLog.logd("recv msg: AccessEventId.EVENT_NET_APDU_MSG_FAIL");
                    processApduErrMsg(message.arg1, message.arg2, message.obj);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_APDU_INVALID:
                    JLog.logd("recv msg EVENT_CLOUDSIM_APDU_INVALID, so switch vsim");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_APDU_INVALID;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_S2CCMD_RELOGIN:
                    JLog.logd("recv s2c cmd relogin!");
                    reloginReason = "EVENT_S2CCMD_RELOGIN";
                    if (message.arg2 == 5) {
                        reloginReason = "S2c_redirect_route";
                    }
                    sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
                    break;
                case AccessEventId.EVENT_NET_BIND_DUN_FAIL:
                    JLog.logd("recv AccessEventId.EVENT_NET_BIND_DUN_FAIL so logout!!!");
                    sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, ErrorCode.INSTANCE.getLOCAL_BIND_NETWORK_FAIL(), 0, ErrorCode.INSTANCE.getErrMsgByCode(ErrorCode.INSTANCE.getLOCAL_BIND_NETWORK_FAIL()));
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            if (isWaitForHeartBeatRsp) {
                isWaitForHeartBeatRsp = false;
                Requestor.INSTANCE.releaseChannel(heartbeatId);
            }
            if (useHearBeatAlarm) {
                heartbeatTask.stop();
            } else {
                removeMessages(StateMessageId.USER_HEART_BEAT_SEND_CMD);
            }
            unsubscriptSub(heartBeatSub);
            wakeLock.release();
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "40";
        }

        private void doActionLogout() {
            termFlowReasonInfo = ErrorCode.INSTANCE.getErrMsgByCode(terFlowReason);
            mResetCoudsimReason = termFlowReasonInfo;
            mNextStateAfterAllRest = mLogoutWaitState;
            transToNextState(mWaitResetCardState);
        }

        private void processS2cLogoutCmd(int quitType, int param) {
            switch (param) {
                case CmdDefineKt.TERMINAL_NOTICE_QUIT:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_USER_LOGIN_OTHER_PLACE();
                    termFlowReasonInfo = ErrorCode.INSTANCE.getErrMsgByCode(terFlowReason);
                    transToNextState(mLogoutState);
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_RELOGIN:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_USER_LOGIN_OTHER_PLACE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_NO_FEE:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FORCE_LOGOUT_FEE_NOT_ENOUGH();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_USER_BIND_CHANGE:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_BIND_CHANGE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_SPEED_LIMIT_FAIL:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FLOW_CTRL_EXIT();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_MANUAL:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FORCE_LOGOUT_MANUAL();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_ACCOUNT_STOP:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_ACCOUNT_IS_DISABLE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_DEVICE_STOP:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_DEVICE_IS_DISABLE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_RESOURCE_SHORT:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_HOST_LOGIN_SLEEP();
                    doActionLogout();
                    break;
                default:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FORCE_LOGOUT_UNKNOWN();
                    doActionLogout();
                    JLog.loge("unknown param " + param);
            }
        }
    }

    private class LogoutState extends BaseState {
        private Subscription logoutSub = null;

        private void startLogout() {
            if (logoutSub != null) {
                if (!logoutSub.isUnsubscribed()) {
                    logoutSub.unsubscribe();
                }
            }
            logoutSub = session.logoutReq(TimeoutValue.getLogoutTimeoutTotal()).timeout(TimeoutValue.getLogoutTimeoutTotal(), TimeUnit.SECONDS).subscribe(new Action1<Object>() {
                @Override
                public void call(Object o) {
                    JLog.loge("logout succ!!!");
                    sendMessage(StateMessageId.USER_LOGOUT_RSP_CMD, 0);
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    //logout fail!!!
                    JLog.loge("logout fail!!!");
                    throwable.printStackTrace();
                    sendMessage(StateMessageId.USER_LOGOUT_RSP_CMD, -1, 0, throwable);
                }
            });
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            startLogout();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.USER_LOGOUT_RSP_CMD:
                    JLog.loge("logout over!!! return:" + message.arg1);
                    mResetCoudsimReason = "logout:" + mLogoutReason;
                    mNextStateAfterAllRest = mLogoutWaitState;
                    transToNextState(mWaitResetCardState);
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            unsubscriptSub(logoutSub);
            lastState = this;
        }
    }

    private class LogoutWaitState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            sendMessageDelayed(StateMessageId.USER_LOGOUT_WAIT_TIMEOUT_CMD, TimeUnit.SECONDS.toMillis(TimeoutValue.getLogoutWaitUploadStatTimeout()));
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.USER_LOGOUT_WAIT_TIMEOUT_CMD:
                    transToNextState(mDefaultState);
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            removeMessages(StateMessageId.USER_LOGOUT_WAIT_TIMEOUT_CMD);
        }
    }

    private class WaitReloginState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            releaseChannel();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    setLogoutMsg(message.arg1, (String) message.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mResetCoudsimReason = (String) message.obj;
                    mNextStateAfterAllRest = mLogoutWaitState;
                    transToNextState(mWaitResetCardState);
                    break;
                case StateMessageId.USER_QUIT_RELOGIN_STATE_CMD:
                    transToNextState(mLoginState);
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            removeMessages(StateMessageId.USER_QUIT_RELOGIN_STATE_CMD);
            lastState = this;
        }
    }

    private class WaitResetCardState extends BaseState {
        private boolean seedCardAbsent  = false;
        private boolean cloudCardAbsent = false;

        @Override
        public void enter() {
            super.enter();
            currState = this;
            releaseChannel();
            clearAllBeforeLogout();
            listenCloudCardStatus();
            listenSeedCardStatus(); //TODO: No Timeout
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.VSIM_BEGIN_REQ_CMD:
                    break;
                case StateMessageId.SEED_SIM_ABSET:
                    JLog.logd("recv StateMessageId.SEED_SIM_ABSET");
                    seedCardAbsent = true;
                    checkState();
                    break;
                case StateMessageId.CLOUD_SIM_ABSET:
                    JLog.logd("recv StateMessageId.CLOUD_SIM_ABSET");
                    cloudCardAbsent = true;
                    checkState();
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
                //TODO: no logout handle
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
        }

        private void checkState() {
            if (seedCardAbsent && cloudCardAbsent) {
                JLog.logd("all card absent, change to next state :" + mNextStateAfterAllRest);
                seedCardAbsent = false;
                cloudCardAbsent = false;
                transToNextState(mNextStateAfterAllRest);
            }
        }
    }

    private class VsimBegin extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            transToNextState(mDispatchVsimState);
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.VSIM_BEGIN_REQ_CMD:
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "40";
        }
    }

    private class DownloadState extends BaseState {
        private int retryCount = 0;

        private void requestCardErrProc(int arg1, int arg2, Object o) {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);

            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.logd("requestCardErrProc :" + info + " err:" + err);
            if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                reloginReason = "request card rpc error:" + err;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, err));
            } else {
                //                if (info.getRpc()) {
                //                    processSystemErrcode(info.getCode());
                //                }
                processSystemErrcode(info.getCode());
                if (retryCount >= 3) {
                    JLog.logd("retry count over 3 times, switch vsim " + retryCount);
                    switchVsimReason = SwitchVsimReason.BIN_DOWNLOAD_ERROR;
                    startSwitchVsim();
                } else {
                    retryCount++;
                    requestCardAction(session);
                }
            }
        }

        private void requestCardAction(Session session) {
            // download card
            if (requestCardSub != null) {
                if (!requestCardSub.isUnsubscribed()) {
                    requestCardSub.unsubscribe();
                }
            }
            PerfLogVsimResAllo.INSTANCE.create(PerfLogVsimResAllo.INSTANCE.getVSIM_RESALLO_ID_DOWNLOAD_START(),0, new ResAlloinfo(0,"",-1));
            requestCardSub = session.requestCloudCard(mImsi, mVsimEplmnList, mVsimApn, TimeoutValue.getRequestVsimBinTimeout()).
                    timeout(TimeoutValue.getRequestVsimBinTimeoutTotal(), TimeUnit.SECONDS).
                    subscribe(new Action1<Card>() {
                        @Override
                        public void call(Card card) {
                            card.setVritimei(mVirtualImei);
                            PerfLogVsimResAllo.INSTANCE.create(PerfLogVsimResAllo.INSTANCE.getVSIM_RESALLO_ID_DOWNLOAD_END(),0, new ResAlloinfo(0,mImsi,0));
                            sendMessage(StateMessageId.DOWNLOAD_VSIM_RSP_CMD, 0, 0, card);
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable t) {
                            JLog.loge("requestCardAction failed!:" + t.getMessage());
                            t.printStackTrace();
                            if (t instanceof TimeoutException) {
                                t = new Throwable(ErrorCode.INSTANCE.getTIMEOUT_HEADER_STR() + " get card bin timeout!");
                            }

                            String err = ErrorCode.INSTANCE.getErrString(t);
                            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
                            PerfLogVsimResAllo.INSTANCE.create(PerfLogVsimResAllo.INSTANCE.getVSIM_RESALLO_ID_DOWNLOAD_END(),0, new ResAlloinfo(0,mImsi,info.getCode()));
                            sendMessage(StateMessageId.DOWNLOAD_VSIM_RSP_CMD, -1, 0, t);
                        }
                    });
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(55);
            requireChannel();
            requestCardAction(session);
            JLog.logd("Action:DownloadCloudsim");
            //            if (Configuration.INSTANCE.getCurrentSystemVersion() == Configuration.ANDROID_COOL_C103) {
            //                JLog.logd("virtImei", "wirteVirtImei for ANDROID_COOL_C103 subId");
            //                String virtImei = mVirtualImei;
            //                //写入虚拟imei
            //                if (VirtImeiHelper.INSTANCE.checkValidImei(virtImei)) {
            //                    VirtImeiHelper.INSTANCE.wirteVirtImei(ctx, virtImei);
            //                } else {
            //                    JLog.logd("virtImei", "没有合法imei");
            //                }
            //            } else {
            //                JLog.logd("virtImei", "disable virtimei");
            //            }
            retryCount = 0;
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.DOWNLOAD_VSIM_RSP_CMD:
                    switch (message.arg1) {
                        case 0:
                            JLog.loge("download vsim ok");
                            setStateProcess(60);
                            vsimCard = (Card) message.obj;
                            transToNextState(mStartVsimState);
                            break;
                        case -1:
                            requestCardErrProc(message.arg1, message.arg2, message.obj);
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            unsubscriptSub(requestCardSub);
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "55";
        }
    }

    private class StartVsimState extends BaseState {
        private void enableCardAction(Card card) {
            JLog.logd("enableCardAction" + card);
            ArrayList<Card> cardList = new ArrayList<>();
            cardList.add(card);
            int ret = ServiceManager.cloudSimEnabler.enable(cardList);
            JLog.loge("start cloud sim ret " + ret);
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(65);
            enableCardAction(vsimCard);

            //将spn名称发给状态栏
            NoticeStatusBarServiceStatus.INSTANCE.noticFrameworkSpnName(vsimCard.getImsi());

            sendMessageDelayed(StateMessageId.VSIM_READY_TIMEOUT, TimeUnit.SECONDS.toMillis(TimeoutValue.getEnableCardTimeout()));
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.VSIM_READY_TIMEOUT:
                    JLog.loge("recv StateMessageId.VSIM_READY_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_READY_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_REMOTE_UIM_RESET_INDICATION:
                    JLog.logd("recv AccessEventId.EVENT_REMOTE_UIM_RESET_INDICATION!");
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CARD_READY:
                    JLog.logd("recv AccessEventId.EVENT_CLOUDSIM_CARD_READY, start vsim ok!");
                    setStateProcess(70);
                    transToNextState(mVsimRegState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_ADD_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_ADD_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_ADD_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_ADD_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_CLOUDSIM_INSERT_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_INSERT_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_INSERT_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_INSERT_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_CLOUDSIM_READY_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_READY_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_READY_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_READY_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CRASH:
                    log("recv ucloudsim crash");
                    // do nothing!
                    break;
                case AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM:
                    JLog.logd("AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM");
                    transToNextState(mPlugPullCloudSimState);
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            removeMessages(StateMessageId.VSIM_READY_TIMEOUT);
        }

        @Override
        protected String getPercent() {
            return "65";
        }
    }

    private class VsimRegState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(75);
            if (ServiceManager.cloudSimEnabler.getCardState() == CardStatus.IN_SERVICE) {
                JLog.logd("cloudsim reg ok");
                transToNextState(mVsimDatacallState);
            }
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.VSIM_REG_TIMEOUT_CMD:
                    JLog.loge("vsim reg timeout, switch vsim");
                    switchVsimReason = SwitchVsimReason.REG_TIMEOUT;
                    startSwitchVsim();
                    break;
                case StateMessageId.CHECK_CLOUDSIM_STATE:
                    CardStatus status = ServiceManager.cloudSimEnabler.getCardState();
                    JLog.loge("get card status!" + status);
                    switch (status) {
                        case ABSENT:
                        case INSERTED:
                        case POWERON:
                            transToNextState(mStartVsimState);
                            break;
                        case READY:
                        case LOAD:
                        case OUT_OF_SERVICE:
                        case EMERGENCY_ONLY:
                            break;
                        case IN_SERVICE:
                            if (TelephonyManager.from(ctx).getDataState() == TelephonyManager.DATA_CONNECTED) {
                                transToNextState(mVsimEstablishedState);
                            } else {
                                transToNextState(mVsimDatacallState);
                            }
                            break;
                    }
                    break;
                case AccessEventId.EVENT_SEEDSIM_START_PS_CALL:
                    JLog.logd("recv AccessEventId.EVENT_SEEDSIM_START_PS_CALL");
                    return NOT_HANDLED;
                case AccessEventId.EVENT_SEEDSIM_PS_CALL_SUCC:
                    JLog.logd("recv AccessEventId.EVENT_SEEDSIM_PS_CALL_SUCC");
                    return NOT_HANDLED;
                case AccessEventId.EVENT_CLOUDSIM_NEED_AUTH:
                    // 不处理，因为鉴权请求可能比dun拨号早来
                    //JLog.logd("recv AccessEventId.EVENT_CLOUDSIM_NEED_AUTH");
                    if (processPersent < 80) {
                        setStateProcess(80);
                    }
                    break;
                case AccessEventId.EVENT_CLOUDSIM_AUTH_REPLIED:
                    //                    JLog.logd("recv AccessEventId.EVENT_CLOUDSIM_AUTH_REPLIED");
                    if (processPersent >= 80 && processPersent < 89) {
                        setStateProcess(processPersent + 1);
                    }
                    break;
                case AccessEventId.EVENT_CLOUDSIM_REGISTER_NETWORK:
                    JLog.logd("recv AccessEventId.EVENT_CLOUDSIM_REGISTER_NETWORK1");
                    ServiceManager.appThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            long start = SystemClock.uptimeMillis();
                            loge("Start get cloud sim plmn");
                            NetworkManager.INSTANCE.getNetInfo(Configuration.INSTANCE.getCloudSimSlot());//add for save cloud sim plmn
                            loge("get cloud sim plmn success in: " + (SystemClock.uptimeMillis() - start) + " ms.");
                        }
                    });
                    transToNextState(mVsimDatacallState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_DATA_ENABLED:
                    JLog.logd("recv vsim datacall enable!! trans to vsimok!! but this is reg state");
                    transToNextState(mVsimEstablishedState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_INSERVICE_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_INSERVICE_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_INSERVICE_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_INSERVICE_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CRASH:
                    log("recv ucloudsim crash");
                    // do nothing!
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CONNECT_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_CONNECT_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_CONNECT_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_CONNECT_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM:
                    JLog.logd("AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM");
                    transToNextState(mPlugPullCloudSimState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_ADD_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_ADD_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_ADD_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_ADD_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_EXCEPTION_VSIM_REJECT:
                    JLog.logd("recv EVENT_EXCEPTION_VSIM_REJECT msg");
                    acessListenUpdateError(message.what, "EVENT_EXCEPTION_VSIM_REJECT");
                    switchVsimReason = SwitchVsimReason.VSIM_REG_REJECT;
                    startSwitchVsim();
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            removeMessages(StateMessageId.VSIM_REG_TIMEOUT_CMD);
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "75";
        }
    }

    private class VsimDatacallState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(90);
            releaseChannel();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.VSIM_DATACALL_TIMEOUT_CMD:
                    JLog.loge("data call timeout, switch vsim" + " timeout value:" + TimeoutValue.getVsimDatacallTimeout());
                    switchVsimReason = SwitchVsimReason.DATACALL_TIMEOUT;
                    startSwitchVsim();
                    break;
                case StateMessageId.CHECK_CLOUDSIM_STATE:
                    CardStatus status = ServiceManager.cloudSimEnabler.getCardState();
                    JLog.loge("get card status!" + status);
                    switch (status) {
                        case ABSENT:
                        case INSERTED:
                        case POWERON:
                            transToNextState(mStartVsimState);
                            break;
                        case READY:
                        case LOAD:
                        case OUT_OF_SERVICE:
                        case EMERGENCY_ONLY:
                            transToNextState(mVsimRegState);
                            break;
                        case IN_SERVICE:
                            if (TelephonyManager.from(ctx).getDataState() == TelephonyManager.DATA_CONNECTED) {
                                transToNextState(mVsimEstablishedState);
                            }
                            break;
                    }
                    break;
                case AccessEventId.EVENT_CLOUDSIM_DATA_ENABLED:
                    JLog.logd("recv vsim datacall enable!! trans to vsimok!!");
                    transToNextState(mVsimEstablishedState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CONNECT_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_CONNECT_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_CONNECT_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_CONNECT_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CRASH:
                    log("recv ucloudsim crash");
                    // do nothing!
                    break;
                case AccessEventId.EVENT_CLOUDSIM_OUT_OF_SERVICE:
                    JLog.logd("recv EVENT_CLOUDSIM_OUT_OF_SERVICE");
                    transToNextState(mVsimRegState);
                    break;
                case AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM:
                    JLog.logd("AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM");
                    transToNextState(mPlugPullCloudSimState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_ADD_TIMEOUT:
                    JLog.logd("recv EVENT_CLOUDSIM_ADD_TIMEOUT msg");
                    acessListenUpdateError(message.what, "EVENT_CLOUDSIM_ADD_TIMEOUT");
                    switchVsimReason = SwitchVsimReason.VSIM_CARD_ADD_TIMEOUT;
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_EXCEPTION_VSIM_REJECT:
                    JLog.logd("recv EVENT_EXCEPTION_VSIM_REJECT msg");
                    acessListenUpdateError(message.what, "EVENT_EXCEPTION_VSIM_REJECT");
                    switchVsimReason = SwitchVsimReason.VSIM_REG_REJECT;
                    startSwitchVsim();
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            removeMessages(StateMessageId.VSIM_DATACALL_TIMEOUT_CMD);
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "90";
        }
    }

    private class VsimEstablishedState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            ServiceManager.productApi.getNetRestrictOperater().resetRestrict("VsimEstablishedState enter");
            NetPackageStatisticsCtrl.getInstance().stop();

            currState = this;
            setStateProcess(100);
            releaseChannel();
            updateCloudPlmn();
            updateCloudsimSucc();
            PerformanceStatistics.INSTANCE.setProcess(ProcessState.CLOUD_CONNECTED);
            UploadLacTask.INSTANCE.uploadLacChange();
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().registerNetworkCallback(0, "");
            //UploadLacTask.INSTANCE.startTask();
            //FlowBandWidthControl.getInstance().setBwWhenCloudsimChange();
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().registerNetworkCallback(0, "");
            JLog.logd("Action:StartCloudsimSuccess");
            //            sendMessageDelayed(StateMessageId.START_DOWN_LOAD_EXT_SOFTSIM_CMD,30000);
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.CHECK_CLOUDSIM_STATE:
                    CardStatus status = ServiceManager.cloudSimEnabler.getCardState();
                    JLog.loge("get card status!" + status);
                    switch (status) {
                        case ABSENT:
                        case INSERTED:
                        case POWERON:
                            transToNextState(mStartVsimState);
                            break;
                        case READY:
                        case LOAD:
                        case OUT_OF_SERVICE:
                        case EMERGENCY_ONLY:
                            transToNextState(mVsimRegState);
                            break;
                        case IN_SERVICE:
                            if (TelephonyManager.from(ctx).getDataState() != TelephonyManager.DATA_CONNECTED) {
                                transToNextState(mVsimDatacallState);
                            }
                            break;
                    }
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    setLogoutMsg(message.arg1, (String) message.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    transToNextState(mVsimReleaseState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_DATA_LOST:
                    JLog.loge("vsim data lost!!!");
                    transToNextState(mVsimRegState);
                    break;
                case AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_SUCC:
                    PerfLogVsimDelayStat.INSTANCE.create(PerfLogVsimDelayStat.INSTANCE.getUPLOAD_FIRST_FLOWPACK(), 0, "");
                    PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_FLOW_UPLOAD(),0,"");
                    refreshHeartBeat();
                    break;
                case AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL:
                    processUploadFlowFail(message.arg1, message.arg2, message.obj);
                    break;
                case AccessEventId.EVENT_S2CCMD_LOGOUT:
                    JLog.logd("recv AccessEventId.EVENT_S2CCMD_LOGOUT:" + message.arg1 + " " + (int) message.obj);
                    processS2cLogoutCmdVsim(message.arg1, (int) message.obj);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_CRASH:
                    log("recv ucloudsim crash");
                    transToNextState(mStartVsimState);
                    break;
                case AccessEventId.EVENT_CLOUDSIM_OUT_OF_SERVICE:
                    JLog.logd("recv EVENT_CLOUDSIM_OUT_OF_SERVICE");
                    transToNextState(mVsimRegState);
                    break;
                case AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM:
                    JLog.logd("AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM");
                    transToNextState(mPlugPullCloudSimState);
                    break;
                case AccessEventId.EVENT_EXCEPTION_VSIM_NET_FAIL:
                    JLog.logd("recv EVENT_EXCEPTION_VSIM_NET_FAIL msg");
                    acessListenUpdateError(message.what, "EVENT_EXCEPTION_VSIM_NET_FAIL");
                    switchVsimReason = SwitchVsimReason.VSIM_NET_FAIL;
                    startSwitchVsim();
                    break;
                case StateMessageId.UPLOAD_FLOW_MANUAL_CMD:
                    JLog.logd("recv StateMessageId.UPLOAD_FLOW_MANUAL_CMD");
                    UploadFlowTask.INSTANCE.forceUploadFlowWithHeartbeat();
                    break;
//                case StateMessageId.START_DOWN_LOAD_EXT_SOFTSIM_CMD:
//                    ServiceManager.productApi.startDownLoadSoftsim();
//                    break;
                case AccessEventId.EVENT_EXCEPTION_SMART_VSIM_SWITCH_CARD:
                    JLog.logd("recv EVENT_EXCEPTION_SMART_VSIM_SWITCH_CARD msg");
                    switchVsimReason = SwitchVsimReason.SERVER_REQUEST;
                    S2cSwitchCardResult ret = (S2cSwitchCardResult)message.obj;
                    if (ret != null){
                        switchVsimSubReason = ret.subReason;
                        JLog.logd("EVENT_EXCEPTION_SMART_VSIM_SWITCH_CARD eventReason:"+ret.eventReason);
                    }
                    startSwitchVsim();
                    break;
                case AccessEventId.EVENT_EXCEPTION_VSIM_REJECT:
                    JLog.logd("recv EVENT_EXCEPTION_VSIM_REJECT msg");
                    acessListenUpdateError(message.what, "EVENT_EXCEPTION_VSIM_REJECT");
                    switchVsimReason = SwitchVsimReason.VSIM_REG_REJECT;
                    startSwitchVsim();
                    break;

                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            ServiceManager.productApi.getNetRestrictOperater().setRestrict("VsimEstablishedState exit");
            // UploadFlowService.getInstance().stop();
            lastState = this;
            removeMessages(AccessEventId.EVENT_S2CCMD_DOWMLOAD_EXT_SOFTSIM_REQ);
            removeMessages(AccessEventId.EVENT_S2CCMD_UPDATE_EXT_SOFTSIM_REQ);
            NetPackageStatisticsCtrl.getInstance().start();
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().unRegisterNetworkCallback(0, "");
        }

        @Override
        protected String getPercent() {
            return "100";
        }

        private void processUploadFlowFail(int arg1, int arg2, Object o) {
            Throwable t = (Throwable) o;
            String errMsg = ErrorCode.INSTANCE.getErrString(t);

            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(errMsg);
            JLog.logd("get errinfo " + info + " err:" + errMsg);
            if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                JLog.loge("relogin!!!!");
                reloginReason = "upload flow rpc error:" + errMsg;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, errMsg));
            } else {
                processSystemErrcode(info.getCode());
            }
        }

        private void doActionLogout() {
            termFlowReasonInfo = ErrorCode.INSTANCE.getErrMsgByCode(terFlowReason);
            mResetCoudsimReason = termFlowReasonInfo;
            mNextStateAfterAllRest = mLogoutWaitState;
            transToNextState(mWaitResetCardState);
        }

        private void processS2cLogoutCmdVsim(int quitType, int param) {
            switch (param) {
                case CmdDefineKt.TERMINAL_NOTICE_QUIT:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_USER_LOGIN_OTHER_PLACE();
                    termFlowReasonInfo = ErrorCode.INSTANCE.getErrMsgByCode(terFlowReason);
                    mResetCoudsimReason = termFlowReasonInfo;
                    mNextStateAfterCloudRest = mLogoutState;
                    transToNextState(mVsimReleaseState);
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_RELOGIN:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_USER_LOGIN_OTHER_PLACE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_NO_FEE:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FORCE_LOGOUT_FEE_NOT_ENOUGH();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_USER_BIND_CHANGE:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_BIND_CHANGE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_SPEED_LIMIT_FAIL:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FLOW_CTRL_EXIT();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_MANUAL:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FORCE_LOGOUT_MANUAL();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_ACCOUNT_STOP:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_ACCOUNT_IS_DISABLE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_DEVICE_STOP:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_DEVICE_IS_DISABLE();
                    doActionLogout();
                    break;
                case CmdDefineKt.TERMINAL_FORCE_QUIT_RESOURCE_SHORT:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_HOST_LOGIN_SLEEP();
                    doActionLogout();
                    break;
                default:
                    terFlowReason = ErrorCode.INSTANCE.getLOCAL_FORCE_LOGOUT_UNKNOWN();
                    doActionLogout();
                    JLog.loge("unknown param " + param);
            }
        }
    }

    private class VsimReleaseState extends BaseState {
        private boolean idDone = false;

        // 退出前的处理，
        private void startReleaseActions() {
            /*退出前上报流量*/
            UploadFlowTask.INSTANCE.release();
        }

        // 每做完一件事，检查是否全部完成
        private boolean checkAllDone() {
            Boolean tf = true;
            if (UploadFlowTask.INSTANCE.getUploadFlowStatus()) {
                tf = false;
            }
            return tf;
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            startReleaseActions();
            if (checkAllDone() == true) {
                sendMessage(StateMessageId.VSIM_RELEASE_TIMEOUT_CMD);
            } else {
                sendMessageDelayed(StateMessageId.VSIM_RELEASE_TIMEOUT_CMD, TimeUnit.SECONDS.toMillis(2)); // 2s 超时
            }
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.VSIM_RELEASE_TIMEOUT_CMD:
                    transToNextState(mLogoutState);
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
            removeMessages(StateMessageId.VSIM_RELEASE_TIMEOUT_CMD);
        }
    }

    private static final String INVALID_IMSI_STR  = "INVALID_IMSI";
    private static final String INVALID_VIRT_IMEI = "INVALID_VIRT_IMEI";
    private static final String INVALID_APN_STR   = "INVALID_APN";

    private class SwitchVsimState extends BaseState {
        private              int               switchVsimCount               = 0;//换卡次数
        private              int               switchVsimTimeoutCount        = 0;//超时错误次数
        private              int               switchVsimSameErrCount        = 0;//相同错误次数
        private              long[]            publishTime                   = {0, 0, 0, 60, 120, 180, 0, 60, 120, 180, 0, 60};
        private final static int               switchVsimTimeoutReloginCount = 3;
        private              ArrayList<String> switchVsimErrs                = new ArrayList<String>();
        private final static int               switchVsimReleaseChannelTh    = 30; // uinit:s
        private final static int               switchVsimSameErrMinCount     = 3;//相同换卡原因次数的下限
        private final static int               switchVsimSameErrMaxCount     = 12;//相同换卡原因次数的上限
        private final static int               switchVsimMaxCount            = 20;//最大换卡次数


        private long getPublishTimeByStrErr(String err) {
            switchVsimSameErrCount = 0;
            for (String str : switchVsimErrs) {
                if (err.equals(str)) {
                    switchVsimSameErrCount++;
                }
            }
            if (switchVsimSameErrCount < switchVsimSameErrMaxCount) {
                return publishTime[switchVsimSameErrCount - 1];
            } else {
                return publishTime[11];
            }
        }

        private void switchVsimAction(int switchVsimReason, int subReason) {
            requireChannel();
            switchVsimCount++;
            JLog.logk("switch vsim start, reason:" + switchVsimReason + ",subReason:"+subReason);
            if (switchVsimSub != null) {
                if (!switchVsimSub.isUnsubscribed()) {
                    switchVsimSub.unsubscribe();
                }
            }
            switchVsimSub = session.requestSwitchVsim(SwitchVsimReason.getServerSwitchReason(switchVsimReason),subReason, TimeoutValue.getSwitchVsimTimeout()).timeout(TimeoutValue.getSwitchVsimTimeoutTotal(), TimeUnit.SECONDS).subscribe(new Action1<SwitchVsimResp>() {
                @Override
                public void call(SwitchVsimResp switchVsimResp) {
                    JLog.logd("switch vsim rsp: " + switchVsimResp);
                    if (switchVsimResp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()) {
                        mImsi = switchVsimResp.imsi.toString();
                        if (switchVsimResp.virtualImei != null && VirtImeiHelper.INSTANCE.checkValidImei(switchVsimResp.virtualImei.toString())) {
                            mVirtualImei = switchVsimResp.virtualImei.toString();
                            if (mVirtualImei.length() != 0) {
                                mVirtualImei = add0ToHeadString(mVirtualImei, 15);
                            }
                        } else {
                            mVirtualImei = "";
                        }
                        if (switchVsimResp.eplmnlist != null) {
                            mVsimEplmnList = switchVsimResp.eplmnlist;
                        } else {
                            mVsimEplmnList.clear();
                        }
                        JLog.logd("debug switch rsp imsi:$imsi, eplmnlist:" + mVsimEplmnList);
                        boolean apnValid = session.setCloudSimApn(switchVsimResp.apn, switchVsimResp.imsi.toString());
                        if (!apnValid) {
                            sendMessage(StateMessageId.SWITCH_VSIM_RSP_CMD, -1, 0, new Throwable(INVALID_APN_STR));
                        } else if (!checkValidImsi(mImsi)) {
                            sendMessage(StateMessageId.SWITCH_VSIM_RSP_CMD, -1, 0, new Throwable(INVALID_IMSI_STR));
                        } else if (mVirtualImei != null && mVirtualImei.length() != 0 && !checkValidImsi(mVirtualImei)) {
                            sendMessage(StateMessageId.SWITCH_VSIM_RSP_CMD, -1, 0, new Throwable(INVALID_VIRT_IMEI));
                        } else {
                            mVsimApn = switchVsimResp.apn;
                            PreferredNetworkType.INSTANCE.setMDataRoaming(switchVsimResp.vsim_roam_enable);
                            JLog.logd("debug login rsp mDataRoaming:" + PreferredNetworkType.INSTANCE.getMDataRoaming());
                            PreferredNetworkType.INSTANCE.setMPreferredNetworkType(switchVsimResp.rat);
                            JLog.logd("debug login rsp mPreferredNetworkType:" + PreferredNetworkType.INSTANCE.getMPreferredNetworkType());
                            sendMessage(StateMessageId.SWITCH_VSIM_RSP_CMD, 0, 0, switchVsimResp);
                        }
                    } else {
                        sendMessage(StateMessageId.SWITCH_VSIM_RSP_CMD, -1, 0, new Throwable(ErrorCode.INSTANCE.getRPC_HEADER_STR() + switchVsimResp.errorCode));
                    }
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    if (throwable instanceof AbortException) {
                        loge("requestSwitchVsim is aborted");
                        return;
                    }

                    JLog.loge("switchVsimAction failed:" + throwable.getMessage());
                    throwable.printStackTrace();
                    sendMessage(StateMessageId.SWITCH_VSIM_RSP_CMD, -1, 0, throwable);
                }
            });
        }

        private void progressSwitchVsimReqByErr(String errMsg, ErrorCode.ErrCodeInfo info) {
            switchVsimErrs.add(errMsg);
            long time = getPublishTimeByStrErr(errMsg);
            if (switchVsimSameErrCount < switchVsimSameErrMinCount && switchVsimCount < switchVsimMaxCount) {//相同换卡原因小于3次，总次数小于20次，立即重试
                sendMessageDelayed(StateMessageId.SWITCH_VSIM_RETRY_CMD, 0);
            } else if (switchVsimSameErrCount >= switchVsimSameErrMinCount && switchVsimSameErrCount <= switchVsimSameErrMaxCount && switchVsimCount < switchVsimMaxCount) {//相同换卡原因大于3次，小于12次，动态调整换卡间隔
                if (time > switchVsimReleaseChannelTh) {//超过30s释放种子通道
                    transToNextState(mWaitSwitchVsimState);
                    sendMessageDelayed(StateMessageId.QUIT_WAIT_SWITCH_VSIM_STATE_CMD, TimeUnit.SECONDS.toMillis(time));
                } else {
                    sendMessageDelayed(StateMessageId.SWITCH_VSIM_RETRY_CMD, TimeUnit.SECONDS.toMillis(time));
                }
            } else if (switchVsimCount >= switchVsimMaxCount) {//换卡次数达到最大值，Logout
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, errMsg));
            }
        }

        private void processSwitchVsimRspFail(int arg1, int arg2, Object o) {
            Throwable t = (Throwable) o;
            String errMsg = ErrorCode.INSTANCE.getErrString(t);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(errMsg);
            if (errMsg.equals(INVALID_APN_STR) || errMsg.equals(INVALID_IMSI_STR)) {
                if (errMsg.equals(INVALID_APN_STR)) {
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_INVALID_VSIM_APN());
                    switchVsimReason = SwitchVsimReason.INVALID_VSIM_APN;
                } else if (errMsg.equals(INVALID_IMSI_STR)) {
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_INVALID_VSIM_IMSI());
                    switchVsimReason = SwitchVsimReason.INVALID_VSIM_IMSI;
                }
                progressSwitchVsimReqByErr(errMsg, info);
                sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
                return;
            } else if (errMsg.equals(INVALID_VIRT_IMEI)) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_INVALID_VSIM_VIRT_IMEI());
                JLog.loge("INVALID_VIRT_IMEI run anyway!");
                JLog.logk("switch vsim OVER!!!");
                sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
                transToNextState(mGetVsimInfoState);
                return;
            }

            JLog.logd("switch vsim return " + info + " err:" + errMsg);
            if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                JLog.loge("relogin!!!!");
                reloginReason = "switch vsim rpc error:" + errMsg;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, errMsg));
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_TIMEOUT) {
                switchVsimTimeoutCount++;
                if (switchVsimTimeoutCount > switchVsimTimeoutReloginCount) {
                    reloginReason = "switch vsim timeout times over " + switchVsimTimeoutReloginCount;
                    sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
                } else {
                    switchVsimAction(switchVsimReason, 0);
                }
            } else if (info.getRpc()) {
                processSystemErrcode(info.getCode());
                progressSwitchVsimReqByErr(errMsg,info);
            }else {
				processSystemErrcode(info.getCode());
                switchVsimAction(switchVsimReason, 0);
            }
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(45);
            if (lastState != mWaitSwitchVsimState) {
                JLog.logd("SwitchVsimState: lastState is " + lastState.getName());
                switchVsimCount = 0;
                switchVsimErrs.clear();
                switchVsimTimeoutCount = 0;
                switchVsimSameErrCount = 0;
            }
//            if (Configuration.INSTANCE.getCurrentSystemVersion() == Configuration.ANDROID_COOL_C103) {
//                JLog.logd("recoveryImei for ANDROID_COOL_C103 subId");
//                //清除虚拟imei
//                VirtImeiHelper.INSTANCE.recoveryImei(ctx);
//            }
            switchVsimAction(switchVsimReason, switchVsimSubReason);
            JLog.logd("Action:SwitchtCloudsim, reason:"
                    + SwitchVsimReason.getSwitchVsimReasonStr(switchVsimReason)+",subReason:"+switchVsimSubReason);
            //清除换卡子原因
            switchVsimSubReason = 0;
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.SWITCH_VSIM_RSP_CMD:
                    switch (message.arg1) {
                        case 0:
                            JLog.logk("switch vsim succ");
                            sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
                            transToNextState(mGetVsimInfoState);
                            break;
                        case -1:
                            processSwitchVsimRspFail(message.arg1, message.arg2, message.obj);
                            break;
                        default:
                            break;
                    }
                    break;
                case StateMessageId.SWITCH_VSIM_RETRY_CMD:
                    switchVsimAction(switchVsimReason, 0);
                    JLog.logd("Action:SwitchtCloudsim," + SwitchVsimReason.getSwitchVsimReasonStr(switchVsimReason));
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            if (nextState != mWaitSwitchVsimState) {
                JLog.logd("SwitchVsimState: next stat" + nextState.getName());
                switchVsimCount = 0;
                switchVsimErrs.clear();
                switchVsimTimeoutCount = 0;
                switchVsimSameErrCount = 0;
            }
            unsubscriptSub(switchVsimSub);
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "45";
        }
    }

    private class WaitSwitchVsimState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            releaseChannel();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.QUIT_WAIT_SWITCH_VSIM_STATE_CMD:
                    startSwitchVsim();
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            removeMessages(StateMessageId.QUIT_WAIT_SWITCH_VSIM_STATE_CMD);
            lastState = this;
        }
    }

    private class WaitResetCloudSimState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            releaseChannel();
            JLog.logd("state machine:reset cloud sim," + mResetCoudsimReason);
            ServiceManager.cloudSimEnabler.disable("state machine:reset cloud sim," + mResetCoudsimReason, false);
            session.resetAllChannel();
            listenCloudCardStatus();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.CLOUD_SIM_ABSET:
                    JLog.logd("recv StateMessageId.CLOUD_SIM_ABSET");
                    JLog.logd("all card absent, change to next state " + mNextStateAfterCloudRest);
                    transToNextState(mNextStateAfterCloudRest);
                    break;
                case StateMessageId.USER_LOGOUT_REQ_CMD:
                    JLog.logd("recv logout req");
                    setLogoutMsg(message.arg1, (String) message.obj);
                    sendMessage(StateMessageId.USER_LOGOUT_COMM_PROC);
                    mNextStateAfterCloudRest = mLogoutWaitState;
                    break;
                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
        }
    }

    private class DispatchVsimSate extends BaseState {
        private              long              dispatchVsimCount               = 0;//分卡次数
        private              long              dispatchVsimTimeoutCount        = 0;//超时错误次数
        private final static long              dispatchVsimTimeoutReloginCount = 3;
        private              ArrayList<String> dispatchVsimErrs                = new ArrayList<String>();
        private final static long              dispatchReleaseChannelTh        = 60; // unit:s
        private              int               dispatchVsimSameErrCount        = 0;//相同错误次数
        private              long[]            publishTime                     = {0, 0, 0, 60, 120, 180, 0, 60, 120, 180, 0, 60};
        private final static int               dispatchVsimSameErrMinCount     = 3;//相同分卡原因次数的下限
        private final static int               dispatchVsimSameErrMaxCount     = 12;//相同分卡原因次数的上限
        private final static int               dispatchVsimMaxCount            = 20;//最大分卡次数

        private Subscription dispatchVsimSub;
        private long getPublishTimeByStrErr(String err){
            dispatchVsimSameErrCount = 0;
            for (String str : dispatchVsimErrs) {
                if (err.equals(str)) {
                    dispatchVsimSameErrCount++;
                }
            }
            if (dispatchVsimSameErrCount < 12) {
                return publishTime[dispatchVsimSameErrCount - 1];
            } else {
                return publishTime[11];
            }
        }

        /**
         * 分配卡
         */
        private void dispatchVsimReq() {
            PerfLogVsimResAllo.INSTANCE.create(PerfLogVsimResAllo.INSTANCE.getVSIM_RESALLO_ID_REQ(),0, new ResAlloinfo(-1,"",-1));
            requireChannel();
            dispatchVsimCount++;
            dispatchVsimSub = session.dispatchVsim(TimeoutValue.getAllocVsimTimeout()).timeout(TimeoutValue.getAllocVsimTimeoutTotal(), TimeUnit.SECONDS).subscribe(new Action1<DispatchVsimResp>() {
                @Override
                public void call(DispatchVsimResp dispatchVsimResp) {
                    JLog.loge("dispatchVsiem call ret: + " + dispatchVsimResp);
                    if (dispatchVsimResp.errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()) {
                        if (dispatchVsimResp.virtualImei != null && VirtImeiHelper.INSTANCE.checkValidImei(dispatchVsimResp.virtualImei.toString())) {
                            mVirtualImei = dispatchVsimResp.virtualImei.toString();
                            if (mVirtualImei.length() != 0) {
                                mVirtualImei = add0ToHeadString(mVirtualImei, 15);
                            }
                        } else {
                            mVirtualImei = "";
                        }
                        mImsi = dispatchVsimResp.imsi.toString();
                        if (dispatchVsimResp.eplmnList != null) {
                            mVsimEplmnList = dispatchVsimResp.eplmnList;
                        } else {
                            mVsimEplmnList.clear();
                        }
                        boolean apnValid = session.setCloudSimApn(dispatchVsimResp.apn, dispatchVsimResp.imsi.toString());
                        PreferredNetworkType.INSTANCE.setMDataRoaming(dispatchVsimResp.vsim_roam_enable);
                        JLog.logd("debug login rsp mDataRoaming:" + PreferredNetworkType.INSTANCE.getMDataRoaming());
                        PreferredNetworkType.INSTANCE.setMPreferredNetworkType(dispatchVsimResp.rat);
                        JLog.logd("debug login rsp mPreferredNetworkType:" + PreferredNetworkType.INSTANCE.getMPreferredNetworkType());

                        if (!apnValid) {
                            sendMessage(StateMessageId.DISPATCH_VSIM_RSP_CMD, -1, 0, new Throwable(INVALID_APN_STR));
                        } else if (!checkValidImsi(mImsi)) {
                            sendMessage(StateMessageId.DISPATCH_VSIM_RSP_CMD, -1, 0, new Throwable(INVALID_IMSI_STR));
                        } else if (mVirtualImei != null && mVirtualImei.length() != 0 && !checkValidImsi(mVirtualImei)) {
                            sendMessage(StateMessageId.DISPATCH_VSIM_RSP_CMD, -1, 0, new Throwable(INVALID_VIRT_IMEI));
                        } else {
                            mVsimApn = dispatchVsimResp.apn;
                            sendMessage(StateMessageId.DISPATCH_VSIM_RSP_CMD, 0);
                        }
                    } else {
                        sendMessage(StateMessageId.DISPATCH_VSIM_RSP_CMD, -1, 0, new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + dispatchVsimResp.errorCode));
                    }
                    PerfLogVsimResAllo.INSTANCE.create(PerfLogVsimResAllo.INSTANCE.getVSIM_RESALLO_ID_RSP(),0, new ResAlloinfo(dispatchVsimResp.errorCode,mImsi,-1));
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable t) {
                    JLog.loge("dispatch vsim fail:" + t);
                    sendMessage(StateMessageId.DISPATCH_VSIM_RSP_CMD, -1, 0, t);
                    PerfLogVsimResAllo.INSTANCE.create(PerfLogVsimResAllo.INSTANCE.getVSIM_RESALLO_ID_RSP(),0, new ResAlloinfo(1053,mImsi,-1));
                }
            });
        }

        private void dispatchVsimRsp(int arg1, int arg2, Object o) {
            Throwable t = (Throwable) o;
            String errMsg = ErrorCode.INSTANCE.getErrString(t);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(errMsg);
            if (errMsg.equals(INVALID_APN_STR) || errMsg.equals(INVALID_IMSI_STR)) {
                if (errMsg.equals(INVALID_APN_STR)) {
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_INVALID_VSIM_APN());
                    switchVsimReason = SwitchVsimReason.INVALID_VSIM_APN;
                } else if (errMsg.equals(INVALID_IMSI_STR)) {
                    processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_INVALID_VSIM_IMSI());
                    switchVsimReason = SwitchVsimReason.INVALID_VSIM_IMSI;
                }
                progressSwitchVsimReqByErr(errMsg, info);
                sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
                return;
            } else if (errMsg.equals(INVALID_VIRT_IMEI)) {
                processSystemErrcode(ErrorCode.INSTANCE.getLOCAL_INVALID_VSIM_VIRT_IMEI());
                JLog.loge("INVALID_VIRT_IMEI run anyway!");
                JLog.logk("switch vsim OVER!!!");
                sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
                transToNextState(mGetVsimInfoState);
                return;
            }

            JLog.logd("switch vsim return " + info + " err:" + errMsg);
            if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                JLog.loge("relogin!!!!");
                reloginReason = "switch vsim rpc error:" + errMsg;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, errMsg));
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_TIMEOUT) {
                dispatchVsimTimeoutCount++;
                if (dispatchVsimTimeoutCount > dispatchVsimTimeoutReloginCount) {//超时三次重新登录
                    reloginReason = "switch vsim timeout times over " + dispatchVsimTimeoutReloginCount;
                    sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
                } else {
                    dispatchVsimReq();
                }
            } else if (info.getRpc()) {
                processSystemErrcode(info.getCode());
                dispatchVsimErrs.add(errMsg);
                long time = getPublishTimeByStrErr(errMsg);
                if (time > dispatchReleaseChannelTh) {
                    releaseChannel();
                }
                progressSwitchVsimReqByErr(errMsg, info);
            } else {
                processSystemErrcode(info.getCode());
                dispatchVsimReq();
            }
        }

        private void progressSwitchVsimReqByErr(String errMsg, ErrorCode.ErrCodeInfo info) {
            dispatchVsimErrs.add(errMsg);
            long time = getPublishTimeByStrErr(errMsg);
            if (dispatchVsimSameErrCount < dispatchVsimSameErrMinCount && dispatchVsimCount < dispatchVsimMaxCount) {//相同分卡原因小于3次，总次数小于20次，立即重试
                sendMessageDelayed(StateMessageId.DISPATCH_VSIM_RETRY_CMD, 0);
            } else if (dispatchVsimSameErrCount >= dispatchVsimSameErrMinCount && dispatchVsimSameErrCount <= dispatchVsimSameErrMaxCount && dispatchVsimCount < dispatchVsimMaxCount) {//相同分卡原因大于3次，小于12次，动态调整换卡间隔
                if (time > dispatchReleaseChannelTh) {//超过30s释放种子通道
                    transToNextState(mWaitSwitchVsimState);
                    sendMessageDelayed(StateMessageId.QUIT_WAIT_SWITCH_VSIM_STATE_CMD, TimeUnit.SECONDS.toMillis(time));
                } else {
                    sendMessageDelayed(StateMessageId.DISPATCH_VSIM_RETRY_CMD, TimeUnit.SECONDS.toMillis(time));
                }
            } else if (dispatchVsimCount >= dispatchVsimMaxCount) {//换卡次数达到最大值，Logout
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, errMsg));
            }
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(45);
            dispatchVsimReq();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.DISPATCH_VSIM_RSP_CMD:
                    switch (message.arg1) {
                        case 0:
                            JLog.loge("dispatch vsim  ok");
                            transToNextState(mGetVsimInfoState);
                            break;
                        case -1:
                            dispatchVsimRsp(message.arg1, message.arg2, message.obj);
                            break;
                        default:
                            break;
                    }
                    break;

                case StateMessageId.DISPATCH_VSIM_RETRY_CMD:
                    JLog.logd("retry dispatch vsim!");
                    dispatchVsimReq();
                    break;

                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            unsubscriptSub(dispatchVsimSub);
            lastState = this;
            dispatchVsimCount = 0;
            dispatchVsimErrs.clear();
            dispatchVsimTimeoutCount = 0;
            dispatchVsimSameErrCount = 0;
        }

        @Override
        protected String getPercent() {
            return "45";
        }
    }

    private class GetVsimInfoState extends BaseState {
        private int mRetryCount = 0;
        private Subscription getVsimInfoSub;
        private static final int maxRetryCount = 3;


        /**
         * 获取卡信息
         */
        private void getVsimInfo() {
            JLog.loge("getVsimInfo .....");
            mRetryCount++;
            getVsimInfoSub = session.getVsimInfo(TimeoutValue.getVsimInfoTimeout()).timeout(TimeoutValue.getVsimInfoTimeoutTotal(), TimeUnit.SECONDS).subscribe(new Action1<GetVsimInfoResp>() {
                @Override
                public void call(GetVsimInfoResp rsp) {
                    JLog.loge("getVsimInfo call succ: " + rsp);
                    sendMessage(StateMessageId.GET_VSIM_INFO_RSP_CMD, 0, 0, rsp);
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable t) {
                    sendMessage(StateMessageId.GET_VSIM_INFO_RSP_CMD, -1, 0, t);
                }
            });
        }

        private void getVsimInfoRspFail(int arg1, int arg2, Object o) {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.logd("get vsiminfo rsp errinfo:" + info + " err value:" + err);
            if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                terFlowReason = info.getCode();
                termFlowReasonInfo = ErrorCode.INSTANCE.getErrRealMsg(info, err);
                mLogoutReason = "get vsim info fail" + err;
                transToNextState(mLogoutState);
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                reloginReason = "get vsim info " + err;
                transToNextState(mLoginState);
            } else {
                //                if (info.getRpc()) {
                //                    processSystemErrcode(info.getCode());
                //                }
                processSystemErrcode(info.getCode());
                // retry
                if (mRetryCount > maxRetryCount) {
                    switchVsimReason = SwitchVsimReason.VSIM_INFO_GET_FAIL;
                    startSwitchVsim();
                } else {
                    getVsimInfo();
                }
            }
        }

        @Override
        public void enter() {
            super.enter();
            currState = this;
            setStateProcess(50);
            mRetryCount = 0;
            getVsimInfo();
            finishEnter();
        }

        @Override
        public boolean processMessage(Message message) {
            logStateMsg(message, getClass().getSimpleName());
            switch (message.what) {
                case StateMessageId.GET_VSIM_INFO_RSP_CMD:
                    switch (message.arg1) {
                        case 0:
                            JLog.loge("get vsim info ok");
                            GetVsimInfoResp rsp = (GetVsimInfoResp) message.obj;
                            sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
                            transToNextState(mDownloadState);
                            break;
                        case -1:
                            getVsimInfoRspFail(message.arg1, message.arg2, message.obj);
                            break;
                        default:
                            break;
                    }
                    break;

                default:
                    logUnhandleMsg(message, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            super.exit();
            unsubscriptSub(getVsimInfoSub);
            lastState = this;
            mRetryCount = 0;
        }

        @Override
        protected String getPercent() {
            return "50";
        }
    }

    private class PlugPullCloudSimState extends BaseState {
        @Override
        public void enter() {
            super.enter();
            currState = this;
            PerfLogSoftPhySwitch.INSTANCE.create(PerfLogSoftPhySwitch.INSTANCE.getID_SWITCH_START(),0,0);
            setStateProcess(63);
            ServiceManager.cloudSimEnabler.disable("seed card change!", true);
            Requestor.INSTANCE.resetUserRequestor();
            Requestor.INSTANCE.getMHandler().obtainMessage(Requestor.INSTANCE.getEVENT_STOP_RELEASE_CHANNEL()).sendToTarget();
            Requestor.INSTANCE.getMHandler().obtainMessage(Requestor.INSTANCE.getEVENT_STOP_RELEASE_APDU()).sendToTarget();
            Requestor.INSTANCE.clearRequireId(Requestor.INSTANCE.getApduid());
            listenCloudCardStatus();
            finishEnter();
        }

        @Override
        public void exit() {
            super.exit();
            lastState = this;
        }

        @Override
        protected String getPercent() {
            return "63";
        }

        @Override
        public boolean processMessage(Message msg) {
            int mode = 0;
            logStateMsg(msg, getClass().getSimpleName());
            switch (msg.what) {
                case StateMessageId.CLOUD_SIM_ABSET:
                    log("recv StateMessageId.CLOUD_SIM_ABSET and start cloudsim ");
                    ServiceManager.seedCardEnabler.cloudSimRestOver();
                    JLog.logd("last state is " + lastState + ", wait for seed card ready");
                    break;
                case AccessEventId.EVENT_SEEDSIM_READY:
                    JLog.logd("recv AccessEventId.EVENT_SEEDSIM_READY");
					PerfLogSoftPhySwitch.INSTANCE.create(PerfLogSoftPhySwitch.INSTANCE.getID_SWITCH_END(),1,0);
                    mode = ServiceManager.systemApi.switchSeedMode();
                    if (mode != 2) {
                        transToNextState(mStartVsimState);
                    }
                    break;
                case StateMessageId.SEED_CARD_EXCEPTION:
                    PerfLogSoftPhySwitch.INSTANCE.create(PerfLogSoftPhySwitch.INSTANCE.getID_SWITCH_END(),-1,0);
                    EnablerException e = (EnablerException) msg.obj;
                    JLog.logd("seed card Exception!" + e);
                    if (e == EnablerException.CLOSE_CARD_TIMEOUT || e == EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE) {
                        // donothing!
                    } else if (e == EnablerException.EXCEP_PHY_CARD_IS_NULL || e == EnablerException.EXCEP_PHY_CARD_DEFAULT_LOST || e == EnablerException.DATA_ENABLE_CLOSED || e == EnablerException.ROAM_DATA_ENABLE_CLOSED
                            //|| e == EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM
                            || e == EnablerException.EXCEPTION_REG_DENIED || e == EnablerException.EXCEPTION_USER_PHY_ROAM_DISABLE || e == EnablerException.EXCEPTION_UNSUPPORT_CDMA_PHY_CARD) {
                        JLog.loge("recv seed card exception, logout!");
                        int errcode = ErrorCode.INSTANCE.getErrCodeByCardExceptId(e);
                        sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, errcode, 0, ErrorCode.INSTANCE.getErrMsgByCode(errcode));
                    } else if (e == EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM) {
                        if (e.getReason().getErrorCode() == 1) {
                            JLog.loge("recv seed card exception:CARD_EXCEPT_NO_AVAILABLE_SOFTSIM, logout!");
                            int errcode = ErrorCode.INSTANCE.getErrCodeByCardExceptId(e);
                            sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, errcode, 0, ErrorCode.INSTANCE.getErrMsgByCode(errcode));

                        } else {
                            JLog.logd("recv seed card exception:CARD_EXCEPT_SOFTSIM_UNUSABLE,restart seed card!");
                            ServiceManager.seedCardEnabler.enable(new ArrayList<Card>());
                        }

                    } else {
                        JLog.logd("restart seed card!");
                        ServiceManager.seedCardEnabler.enable(new ArrayList<Card>());
                    }
                    break;
                case AccessEventId.EVENT_SEEDSIM_DATA_CONNECT:
                    logd("recv AccessEventId.EVENT_SEEDSIM_DATA_CONNECT");
                    mode = ServiceManager.systemApi.switchSeedMode();
                    if (mode == 2) {
                        transToNextState(mStartVsimState);
                    }
                    break;
                default:
                    logUnhandleMsg(msg, getClass().getSimpleName());
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private void startSwitchVsim() {
        if (currState == mStartVsimState || currState == mVsimRegState || currState == mVsimDatacallState || currState == mVsimEstablishedState) {
            mResetCoudsimReason = "switch vsim";
            mNextStateAfterCloudRest = mSwitchVsimState;
            transToNextState(mWaitResetCloudSimState);
        } else {
            transToNextState(mSwitchVsimState);
        }
    }

    private void processReconnectErrMsg(int arg1, int arg2, Object o) {
        if (arg1 == -1) {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);

            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.loge("reconnect failed " + info + " err:" + err);
            if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, err));
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                reloginReason = "processReconnectErrMsg rpc error:" + err;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else {
                //do nothing
            }
        } else {
            JLog.logd("reconnect succ");
        }
    }

    private void processApduErrMsg(int arg1, int arg2, Object o) {
        if (arg1 == 0) {
            JLog.logd("apdu recv succ");
            sendMessage(StateMessageId.USER_REFRESH_HEART_BEAT_CMD);
        } else {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.loge("processApduErrMsg failed " + info + " err:" + err);
            if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, err));
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                reloginReason = "processApduErrMsg rpc error:" + err;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else {
                //do nothing
            }
        }
    }

    private void heartBeatRspProc(int arg1, int arg2, Object o) {
        int ret = arg1;
        if (ret == 0) {
            JLog.logd("heart beat recv succ!");
            heartBeatTimes = 0;
            refreshHeartBeat();
            sendMessage(StateMessageId.UPLOAD_FLOW_MANUAL_CMD);
        } else {
            Throwable t = (Throwable) o;
            String err = ErrorCode.INSTANCE.getErrString(t);
            JLog.loge("heartbeat error:" + err);
            ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
            JLog.loge("heartBeatRspProc failed " + info + " err:" + err);
            if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("inner logout req");
                sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrRealMsg(info, err));
            } else if (info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN) {
                reloginReason = "heartBeatRspProc rpc error:" + err;
                sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
            } else {
                //do nothing
            }
        }
    }

    private void sendHeartBeat() {
        heartBeatTimes++;
        if (heartBeatTimes >= heartBeatTimesCount) {
            JLog.loge("heartbeat timeout " + heartBeatTimesCount + " times, relogin!!!");
            reloginReason = "heartbeat timeout over" + heartBeatTimesCount + " times";
            sendMessage(StateMessageId.USER_RELOGIN_REQ_CMD);
        } else {
            int nextActionTimeout = TimeoutValue.getHeartbeatSendIntvlSecond();
            heartBeatAction(nextActionTimeout - 1);
            if (useHearBeatAlarm) {
                // do nothing
            } else {
                sendMessageDelayed(StateMessageId.USER_HEART_BEAT_SEND_CMD, TimeUnit.SECONDS.toMillis(nextActionTimeout));
            }
        }
    }

    /**
     * 刷新心跳定时器,有以下几个地方会刷新:
     * 1 心跳包回应
     * 2 流量包
     * 3 换卡
     * 4 apdu
     * 等
     */
    private void refreshHeartBeat() {
        heartBeatTimes = 0;
        if (isWaitForHeartBeatRsp) {
            isWaitForHeartBeatRsp = false;
            Requestor.INSTANCE.releaseChannel(heartbeatId);
        }

        JLog.logd("tart to refreshHeartBeat");
        if (useHearBeatAlarm) {
            JLog.logd("refresh hearbeat alarm!!!");
            heartbeatTask.refresh();
        } else {
            removeMessages(StateMessageId.USER_HEART_BEAT_SEND_CMD);
            JLog.logd("refresh heartbeat new time:" + TimeoutValue.getHeartbeatSendIntvlFirst());
            sendMessageDelayed(StateMessageId.USER_HEART_BEAT_SEND_CMD, TimeUnit.SECONDS.toMillis(TimeoutValue.getHeartbeatSendIntvlFirst()));
        }
    }

    /**
     * 发送心跳请求, 心跳使用alarm功能实现
     *
     * @param timeout
     */
    private void heartBeatAction(int timeout) {
        wakeLock.acquire(30000);
        JLog.logd("heartBeatAction timeout:" + timeout + " isWaitForHeartBeatRsp:" + isWaitForHeartBeatRsp + " heartBeatSub:" + heartBeatSub);
        if (!isWaitForHeartBeatRsp) {
            Requestor.INSTANCE.requireChannel(heartbeatId);
            isWaitForHeartBeatRsp = true;
        }
        if (heartBeatSub != null && !heartBeatSub.isUnsubscribed()) {
            heartBeatSub.unsubscribe();
        }
        heartBeatSub = session.requestHeartBeat(timeout).timeout(timeout, TimeUnit.SECONDS).subscribe(new Action1<Object>() {
            @Override
            public void call(Object o) {
                wakeLock.release();
                sendMessage(StateMessageId.USER_HEART_BEAT_RSP_CMD, 0);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                wakeLock.release();
                JLog.loge("heartBeatAction failed!" + throwable.getMessage());
                throwable.printStackTrace();
                sendMessage(StateMessageId.USER_HEART_BEAT_RSP_CMD, -1, 0, throwable);
            }
        });
    }

    private void unsubscriptSub(Subscription sub) {
        if (sub != null) {
            JLog.logd("unsubscriptSub: hash:" + sub.hashCode() + ", state:" + !sub.isUnsubscribed());
            if (!sub.isUnsubscribed()) {
                sub.unsubscribe();
            }
        }
    }

    private boolean checkValidImsi(String imsi) {
        if (imsi == null) {
            return false;
        }
        if (imsi.length() != 15) {
            return false;
        } else {
            for (char c : imsi.toCharArray()) {
                if (c < '0' || c > '9') {
                    return false;
                }
            }
        }
        return true;
    }

    private void setStateProcess(int persent) {
        if (lastState != mExceptionState) {
            JLog.logd("perset change " + processPersent + " -> " + persent);
            processPersent = persent;
            updatePersent(persent);
        }
    }

    private void clearAllBeforeLogout() {
        JLog.logd("clear all before logout!!!!");
        unsubscriptSub(NetworkManager.INSTANCE.getPollingASSIpSub());
        JLog.logd("clear card:" + mResetCoudsimReason);
        if ((!TextUtils.isEmpty(mResetCoudsimReason)) && (mResetCoudsimReason.equals(REASON_MONITOR_RESTART) || mResetCoudsimReason.contains(USER_LOGOUT) || mResetCoudsimReason.equals(REASON_MCC_CHANGE))){
            SeedPlmnSelector.INSTANCE.updateEvent(CLEAN_TEMP_FPLMN, null);
        }
        ServiceManager.cloudSimEnabler.disable(ACCESS_CLEAR_CARD + ":" + mResetCoudsimReason, false);
        ServiceManager.seedCardEnabler.disable(ACCESS_CLEAR_CARD, false);//種子卡也需要一個通知
        //Configuration.INSTANCE.setSoftSimImsi(null);
        session.release();
        mSeedState.sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD);
    }

    private void listenSeedCardStatus() {
        if (ServiceManager.seedCardEnabler.getDeType() != DeType.SIMCARD) {
            sendMessage(StateMessageId.SEED_SIM_ABSET);
            return;
        }
        CardStatus seedStatus = ServiceManager.seedCardEnabler.getCardState();
        if (seedStatus == CardStatus.ABSENT) {
            sendMessage(StateMessageId.SEED_SIM_ABSET);
        }
    }

    private void listenCloudCardStatus() {
        CardStatus cloudStatus = ServiceManager.cloudSimEnabler.getCardState();
        if (cloudStatus == CardStatus.ABSENT) {
            sendMessage(StateMessageId.CLOUD_SIM_ABSET);
        }
    }

    public int getSystemPersent() {
        return processPersent;
    }

    private void updateEnterRecoveryState() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.enterRecoveryState();
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
    }

    private void updateExitRecoveryState() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.exitRecoveryState();
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
    }

    private void updateCloudsimSucc() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.eventCloudsimServiceSuccess();
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
        acessListenCloudsimSucc();
    }

    private void updateResetRsp(int result, String info) {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.eventCloudsimServiceStop(result, info);
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
        if(result != 0) {
            acessListenCloudsimStop(result, info);
        }
    }

    private void updateExceptionStart() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.enterExceptionState();
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
    }

    private void updateExceptionStop() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.exitExceptionState();
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
    }

    /*
     * UPDATE_SOFTSIM_START = 1
     * UPDATE_SOFTSIM_OVER = 2   "succ"  "fail:code"
     * SPEED_LIMIT_START = 3   "up:xxx,down:xxx,display:(true|false)"
     * SPEED_LIMIT_STOP = 4      ""
     * WIFI_STATE_CHANGE = 5    "true" "false"
     * EXCEPTION_EVENT_START = 6    exceptionid
     * EXCEPTION_EVENT_STOP  = 7   exceptionid
     * code  = 8   软卡是在位，true 在， false 不在
     */
    public void updateCommMessage(int code, String msg) {
        try {
            if (unsoliCbList == null) {
                return;
            }
            ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
            for (IUcloudAccessCallback cb : unsoliCbList) {
                try {
                    cb.updateCommMessage(code, msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    if (e instanceof DeadObjectException) {
                        deadObjs.add(cb);
                    }
                }
            }
            for (IUcloudAccessCallback cb : deadObjs) {
                unsoliCbList.remove(cb);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processSystemErrcode(int errcode) {
        processSystemErrcode(errcode, null);
    }

    private void processSystemErrcode(int errcode, String str) {
        ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByCode(errcode);
        if (info != null) {
            if (info.getAction() == ErrorCode.ErrActType.ACT_EXIT) {
                JLog.loge("portal code " + errcode + " is termial!!!");
                if (processPersent != 0) {
                    sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, info.getCode(), 0, ErrorCode.INSTANCE.getErrMsgByCode(errcode));
                } else {
                    updateResetRsp(info.getCode(), ErrorCode.INSTANCE.getErrMsgByCode(errcode));
                }
            } else if (info.getRpc() || info.getAction() == ErrorCode.ErrActType.ACT_REPORT) {
                updateError(errcode, ErrorCode.INSTANCE.getErrRealMsg(info, str));
            } else {
                logd("do not report errcode " + info);
            }
        } else {
            //updateError(errcode, str);
            logd("do not report errinfo because cannot find code " + errcode + " ," + str);
        }
    }

    private void updateError(int errcode, String info) {
        if (info == null) {
            info = "Unknown error";
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.errorUpdate(errcode, info);
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
        acessListenUpdateError(errcode, info);
    }

    private void updatePersent(int persent) {
        statePersentOb.onNext(persent);
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.processUpdate(persent);
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
        acessListenUpdateProcess(persent);
    }

    private void updateSeedPlmn() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        PlmnInfo plmnInfo = new PlmnInfo();
        plmnInfo.setPlmnList(OperatorNetworkInfo.INSTANCE.getSeedPlmnListString());
        JLog.logd("updateSeedPlmn:" + plmnInfo.toString());
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.updateSeedPlmn(plmnInfo);
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
    }

    public void logoutReq(int module) {
        SeedPlmnSelector.INSTANCE.updateEvent(CLEAN_TEMP_FPLMN, null);
        sendMessage(StateMessageId.USER_LOGOUT_REQ_CMD, 0, 0, USER_LOGOUT + " (" + module + ")");
    }

    public void registerUnsoliCb(IUcloudAccessCallback cb) {
        JLog.logd("registerUnsoliCb:" + cb);
        unsoliCbList.add(cb);
    }

    public void unregisterUnsoliCb(IUcloudAccessCallback cb) {
        unsoliCbList.remove(cb);
    }

    // TODO: 可以将accessStateListeners相关的拆成个单独的
    private List<AccessStateListen> accessStateListeners = Collections.synchronizedList(new ArrayList<AccessStateListen>());

    public interface AccessStateListen {
        void errorUpdate(int errorCode, String message);

        void processUpdate(int persent);

        void eventCloudSIMServiceStop(int reason, String message);

        void eventCloudsimServiceSuccess();

        void eventSeedState(int persent);

        void eventSeedError(int code, String message);
    }

    public void AccessStateListenReg(AccessStateListen l) {
        if (accessStateListeners == null) {
            accessStateListeners = Collections.synchronizedList(new ArrayList<AccessStateListen>());
            accessStateListeners.add(l);
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                if (listenListIterator.next() == l) {
                    return;
                }
            }
            accessStateListeners.add(l);
        }
    }

    public void AccessStateListenUnreg(AccessStateListen l) {
        synchronized (accessStateListeners) {
            accessStateListeners.remove(l);
        }
    }

    private void acessListenUpdateError(int err, String msg) {
        if (accessStateListeners == null) {
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                listenListIterator.next().errorUpdate(err, msg);
            }
        }
    }

    private void acessListenUpdateProcess(int persent) {
        if (accessStateListeners == null) {
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                listenListIterator.next().processUpdate(persent);
            }
        }
    }

    private void acessListenCloudsimStop(int err, String msg) {
        JLog.logd("acessListenCloudsimStop");
        if (accessStateListeners == null) {
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                JLog.logd("send msg to listener!! " + err + " " + msg);
                listenListIterator.next().eventCloudSIMServiceStop(err, msg);
            }
        }
    }

    private void acessListenCloudsimSucc() {
        if (accessStateListeners == null) {
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                listenListIterator.next().eventCloudsimServiceSuccess();
            }
        }
    }

    public void acessListenUpdateSeedProcess(int persent) {
        if (accessStateListeners == null) {
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                listenListIterator.next().eventSeedState(persent);
            }
        }
    }

    public void accessListenUpdateSeedError(int err, String msg) {
        if (accessStateListeners == null) {
            return;
        }
        synchronized (accessStateListeners) {
            ListIterator<AccessStateListen> listenListIterator = accessStateListeners.listIterator();
            while (listenListIterator.hasNext()) {
                listenListIterator.next().eventSeedError(err, msg);
            }
        }
    }

    private void countSeedCardProcessStatistics(int msg) {
        if (msg == AccessEventId.EVENT_SEEDSIM_ENABLE) {
            PerformanceStatistics.INSTANCE.setProcess(ProcessState.SEED_ENABLE);
        } else if (msg == AccessEventId.EVENT_SEEDSIM_DATA_CONNECT) {
            PerformanceStatistics.INSTANCE.setProcess(ProcessState.SEED_CONNECTED);
        } else if (msg == AccessEventId.EVENT_SEEDSIM_IN_SERVICE) {
            PerformanceStatistics.INSTANCE.setProcess(ProcessState.SEED_REG_OK);
        }
    }

    private void seedCardProcess(Message message) {
        Message msg = mSeedState.obtainMessage();
        msg.copyFrom(message);
        mSeedState.sendMessage(msg);
        countSeedCardProcessStatistics(message.what);
    }

    public ArrayList<Integer> getExceptionArray() {
        if (currState != mExceptionState) {
            return new ArrayList<Integer>();
        } else {
            ExceptionState tmp = (ExceptionState) mExceptionState;
            return tmp.getExceptionArray();
        }
    }

    public boolean isServiceRunning() {
        if (currState != mDefaultState) {
            return true;
        }
        return false;
    }

    public boolean isVsimServiceOK() {
        return currState == mVsimEstablishedState;
    }

    public boolean isNextVsimServiceOK() {
        return nextState == mVsimEstablishedState;
    }

    public boolean isInExceptionState() {
        JLog.logd("isInExceptionState " + currState + " " + mExceptionState);
        if (currState != mExceptionState) {
            return false;
        }
        return true;
    }

    public int getSeedPersent() {
        return mSeedState.getPercent();
    }

    public Session getSession() {
        return this.session;
    }

    private boolean getWifiStatus() {
        ConnectivityManager connManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager == null) {
            JLog.loge("connManager is NULL");
        } else {
            NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (mWifi.isConnected()) {
                return true;
            }
        }
        return false;
    }

    public boolean isInRecoveryState() {
        JLog.logd("isInRecoveryState " + currState + " " + mRecoveryState);
        if (currState != mRecoveryState) {
            return false;
        }
        return true;
    }

    public Observable<Integer> getStatePersentOb() {
        return statePersentOb;
    }

    /**
     * 云卡可以正常使用后：上传云卡的plmn给UI层
     */
    private void updateCloudPlmn() {
        if (unsoliCbList == null) {
            return;
        }
        ArrayList<IUcloudAccessCallback> deadObjs = new ArrayList<>();
        PlmnInfo plmnInfo = new PlmnInfo();
        plmnInfo.setPlmnList(OperatorNetworkInfo.INSTANCE.getCloudPlmnListString());
        JLog.logd("updateCloudPlmn:" + plmnInfo.toString());
        for (IUcloudAccessCallback cb : unsoliCbList) {
            try {
                cb.updateSeedPlmn(plmnInfo);
            } catch (RemoteException e) {
                e.printStackTrace();
                if (e instanceof DeadObjectException) {
                    deadObjs.add(cb);
                }
            }
        }
        for (IUcloudAccessCallback cb : deadObjs) {
            unsoliCbList.remove(cb);
        }
    }

}
