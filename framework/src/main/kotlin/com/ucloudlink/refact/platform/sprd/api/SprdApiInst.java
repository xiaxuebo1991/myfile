package com.ucloudlink.refact.platform.sprd.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.ServiceState;
import android.text.TextUtils;

import com.android.internal.telephony.RILConstants;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.channel.enabler.datas.CardStatus;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.platform.sprd.channel.enabler.simcard.cardcontroller.SprdCardController;
import com.ucloudlink.refact.platform.sprd.nativeapi.SprdNative;
import com.ucloudlink.refact.platform.sprd.nativeapi.SprdNativeIntf;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.PhoneStateUtil;

import java.util.concurrent.TimeUnit;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;

/**
 * Created by shiqianhua on 2017/12/4.
 */

public class SprdApiInst {
    private static final String TAG = "SprdApiInst";
    public final static int UPDATEPLMN_TYPE_FPLMN = 0;
    public final static int UPDATEPLMN_TYPE_OPLMN = 1;
    
    public final static int UPDATEPLMN_ACTION_DELE = 0;
    public final static int UPDATEPLMN_ACTION_ADD = 1;
    public final static int UPDATEPLMN_ACTION_DELE_ALL = 2;
    
    private static SprdApiInst sprdApiInst;
    private static Object lock = new Object();
    private SprdIntfService sprdIntfService;

    private static final int MAX_SLOT = 2;

    private static final int MSG_EVENT_START = 1;

    private int[] mNetworkErrcode = {0, 0};
    private long[] mNetworkErrcodeTime = {0L, 0L};

    SprdCardController controller;

    // 标识物理种子卡是否在位
    public boolean mIsSeedPhyCardExist = false;
    public boolean mIsCloudPhyCardExist = false;
    private boolean mIsSeedPhyCard = true;
    private boolean mIsCloudPhyCard = true;

    public static SprdApiInst getInstance() {
        if (sprdApiInst == null) {
            synchronized (lock) {
                if (sprdApiInst == null) {
                    sprdApiInst = new SprdApiInst();
                }
            }
        }

        return sprdApiInst;
    }

    private boolean isUseKeyNative() {
        return ServiceManager.systemApi.isUseKeyNativeApi();
    }

    public int getNetworkErrcode(int phoneId) {
        if (phoneId < 0 || phoneId >= MAX_SLOT) {
            return 0;
        }
        return mNetworkErrcode[phoneId];
    }

    public long getNetworkErrcodeTime(int phoneId) {
        if (phoneId < 0 || phoneId >= MAX_SLOT) {
            return 0;
        }
        return mNetworkErrcodeTime[phoneId];
    }

    public void clearNetworkErrcode(int phoneId) {
        if (phoneId < 0 || phoneId >= MAX_SLOT) {
            return;
        }
        mNetworkErrcode[phoneId] = 0;
        mNetworkErrcodeTime[phoneId] = 0;
    }

    private void listenToCardStatus() {
        ServiceManager.simMonitor.addNetworkStateListen(new CardStateMonitor.NetworkStateListen() {
            @Override
            public void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
                logd("NetworkStateChange ddsid" + ddsId + " state:" + state + " type:" + type + " ifname" + ifName);
                mHandler.obtainMessage(EVENT_NETWORK_CHANGE, ddsId, type, state).sendToTarget();
            }
        });
        ServiceManager.simMonitor.addServiceStateListen(new CardStateMonitor.ServiceStateListen() {
            @Override
            public void serviceStateChange(int slot, int subId, int state) {
                logd("serviceStateChange slotId " + slot + " subId" + subId + " state:" + state);
                mHandler.obtainMessage(EVENT_CARD_SERVICE_CHANGE, slot, subId, state).sendToTarget();
            }
        });
        ServiceManager.simMonitor.addCardStateListen(new CardStateMonitor.CardStateListen() {
            @Override
            public void CardStateChange(int slotId, int subId, int state) {
                logd("CardStateChange slotId " + slotId + " subid" + subId, + state);
                mHandler.obtainMessage(EVENT_CARD_STATE_CHANGE, slotId, subId, state).sendToTarget();
            }
        });
    }

    private static final int EVENT_LISTEN_CARD_STATE = 1;
    private static final int EVENT_PROC_ERRCODE = 2;
    private static final int EVENT_PROC_MODE_CHANGE = 3;
    private static final int EVENT_NETWORK_CHANGE = 4;
    private static final int EVENT_CARD_SERVICE_CHANGE = 5;
    private static final int EVENT_CARD_STATE_CHANGE = 6;

    private class LocalHandler extends Handler {
        public LocalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_LISTEN_CARD_STATE:
                    listenToCardStatus();
                    break;
                case EVENT_PROC_ERRCODE: {
                    int phoneId = msg.arg1;
                    int reason = msg.arg2;
                    if (phoneId < MAX_SLOT || phoneId >= 0) {
                        mNetworkErrcode[phoneId] = reason;
                        mNetworkErrcodeTime[phoneId] = SystemClock.uptimeMillis();
                        ServiceManager.systemApi.updateNetworkErrCode(phoneId, reason);
                    }
                    break;
                }
                case EVENT_PROC_MODE_CHANGE: {
                    int mode = msg.arg1;
                    boolean ret = false;
                    if (mode == 1) {
                        ret = setPreferredNetworkType(ServiceManager.cloudSimEnabler.getCard().getSlot(), RILConstants.NETWORK_MODE_LTE_ONLY);
                    } else if (mode == 2) {
                        ret = setPreferredNetworkType(ServiceManager.cloudSimEnabler.getCard().getSlot(), RILConstants.NETWORK_MODE_WCDMA_ONLY);
                    } else if (mode == 3) {
                        ret = setPreferredNetworkType(ServiceManager.cloudSimEnabler.getCard().getSlot(), RILConstants.NETWORK_MODE_LTE_WCDMA);
                    } else {
                        ret = setPreferredNetworkType(ServiceManager.cloudSimEnabler.getCard().getSlot(), RILConstants.NETWORK_MODE_LTE_WCDMA);
                    }
                    logd("setPreferredNetworkType return " + ret);
                    break;
                }
                case EVENT_NETWORK_CHANGE: {
                    int ddsid = msg.arg1;
                    int type = msg.arg2;
                    NetworkInfo.State state = (NetworkInfo.State) msg.obj;
                    if (state == NetworkInfo.State.CONNECTED) {
                        clearNetworkErrcode(ddsid);
                    }
                    break;
                }
                case EVENT_CARD_SERVICE_CHANGE: {
                    int slot = msg.arg1;
                    int subid = msg.arg2;
                    int state = (int) msg.obj;
                    if (state == ServiceState.STATE_IN_SERVICE) {
                        clearNetworkErrcode(slot);
                    }
                    break;
                }
                case EVENT_CARD_STATE_CHANGE:
                {
                    int slot = msg.arg1;
                    int subid = msg.arg2;
                    int state = (int)msg.obj;
                    if(state == CardStateMonitor.SIM_STATE_ABSENT){
                        clearNetworkErrcode(slot);
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    private LocalHandler mHandler = null;

    private static String SPERR_ERRCODE_STR = "android.intent.action.NW_ERROR_CODE";
    private static String TEST_CLOUDSIM_MODE = "test.cloudsim.mode";
    private static String VSIM_STATE_CHANGED1 = "android.intent.action.VSIM_STATE_CHANGED1";
    private static String VSIM_STATE_CHANGED0 = "android.intent.action.VSIM_STATE_CHANGED0";

    private SprdApiInst() {
        sprdIntfService = new SprdIntfService(ServiceManager.appContext);
    }

    public void init(Looper looper, SprdCardController controller) {
        mHandler = new LocalHandler(looper);
        this.controller = controller;
        mHandler.sendEmptyMessageDelayed(EVENT_LISTEN_CARD_STATE, TimeUnit.SECONDS.toMillis(2));
        IntentFilter filter = new IntentFilter();
        filter.addAction(SPERR_ERRCODE_STR);
        filter.addAction(TEST_CLOUDSIM_MODE);
        filter.addAction(VSIM_STATE_CHANGED1);
        filter.addAction(VSIM_STATE_CHANGED0);
        ServiceManager.appContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                JLog.logd("onReceive action=" + intent.getAction());
                if (intent.getAction().equals(SPERR_ERRCODE_STR)) {
                    int phoneId = intent.getIntExtra("phoneId", -1);
                    int reasonType = intent.getIntExtra("ReasonType", -1);
                    int reason = intent.getIntExtra("Reason", -1);
                    String msg = "PhoneId = " + phoneId + ", ReasonType = " + reasonType + ", reason = " + reason;
                    logd("onReceive: recv action.network.errorCode msg: " + msg);
                    mHandler.obtainMessage(EVENT_PROC_ERRCODE, phoneId, reason).sendToTarget();
                } else if (intent.getAction().equals(TEST_CLOUDSIM_MODE)) {
                    // 1: 4G only  2:3G only  3:3g 4G
                    int mode = intent.getIntExtra("mode", -1);
                    logd("cloud sim is " + ServiceManager.cloudSimEnabler.getCard());
                    if (!ServiceManager.cloudSimEnabler.isCardOn()) {
                        logd("cloudsim is not on!");
                        return;
                    }
                    mHandler.obtainMessage(EVENT_PROC_MODE_CHANGE, mode, 0).sendToTarget();
                } else if (intent.getAction().equals(VSIM_STATE_CHANGED1)
                        || intent.getAction().equals(VSIM_STATE_CHANGED0)) {
                    // 种子卡\云卡信息更新
                    // 拿到这个广播之后，就知道物理卡卡槽有没有卡，然后用户物理卡和软卡切换的时候就不用等待了
                    int phoneId = intent.getIntExtra("phoneId", -1);
                    Object state = intent.getExtra("state");
                    logd("onReceive: recv android.intent.action.VSIM_STATE_CHANGED phoneId = " + phoneId + "; state = " + state);
                    logd("mIsSeedPhyCard = " + mIsSeedPhyCard + "; mIsCloudPhyCard = " + mIsCloudPhyCard);
                    // 0未插卡, 1插卡，2错误
                    if (phoneId == Configuration.INSTANCE.getSeedSimSlot() && mIsSeedPhyCard) {
                        if (state != null && "CARDSTATE_PRESENT".equals(state.toString())) {
                            logd("VSIM_STATE_CHANGED - Seed Phy card is present.");
                            mIsSeedPhyCardExist = true;
                        } else {
                            logd("VSIM_STATE_CHANGED - Seed Phy card is absent.");
                            mIsSeedPhyCardExist = false;
                        }
                    } else if (phoneId == Configuration.INSTANCE.getCloudSimSlot() && mIsCloudPhyCard) {
                        if (state != null && "CARDSTATE_PRESENT".equals(state.toString())) {
                            logd("VSIM_STATE_CHANGED - Cloud Phy card is present.");
                            mIsCloudPhyCardExist = true;
                        } else {
                            logd("VSIM_STATE_CHANGED - Cloud Phy card is absent.");
                            mIsCloudPhyCardExist = false;
                        }
                    }
                }
            }
        }, filter);
    }

    public int clearAllSim() {
        int ret = 0;
        for (int i = 0; i < MAX_SLOT; i++) {
            ret += clearSim(i);
        }
        return ret;
    }

    public int clearSim(int phoneId) {
        if (phoneId >= MAX_SLOT || phoneId < 0) {
            loge("clearSim: " + " slot invalid " + phoneId);
            return -1;
        }

        int ret = vsimQueryVirtual(phoneId);
        logd("init: vsimQueryVirtual " + phoneId + " return " + ret);
        if (ret != 0) {
//            if (phoneId == Configuration.INSTANCE.getCloudSimSlot()) {
//                // 关闭种子卡协议栈
//                int res = setSimPowerStateForSlot(Configuration.INSTANCE.getSeedSimSlot(), false);
//                logd("Close seed sim slot power state res = " + res);
//            }
            ret = vsimExit(phoneId);
            logd("run: vsim exit return " + ret);
            ret = vsimSetVirtual(phoneId, 0);
            logd("run: vsim setvirtual modem 0 return " + ret);
            ret = setSimPowerStateForSlot(phoneId, true);
            logd("run: setSimPowerStateForSlot true return " + ret);
//            if (phoneId == Configuration.INSTANCE.getCloudSimSlot()) {
//                // 打开种子卡协议栈
//                int res = setSimPowerStateForSlot(Configuration.INSTANCE.getSeedSimSlot(), true);
//                logd("Open seed sim slot power state res = " + res);
//            }
        }
        return 0;
    }


    public int vsimInit(int phoneId, SprdNativeIntf cb, int mode) {
        int ret = -1;
        if (phoneId >= MAX_SLOT || phoneId < 0) {
            loge("vsimInit: " + " slot invalid " + phoneId);
            return -1;
        }

        synchronized (controller.getLock(phoneId)) {
            if (isUseKeyNative()) {
                ret = SprdNative.vsim_init(phoneId, cb, mode);
            } else {
                ret = sprdIntfService.vsimInit(phoneId, cb, mode);
            }
        }
        logd("vsimInit " + phoneId + "," + mode + " return " + ret);
        return ret;
    }

    public int vsimExit(int phoneId) {
        int ret = -1;
        if (phoneId >= MAX_SLOT || phoneId < 0) {
            loge("vsimExit: " + " slot invalid " + phoneId);
            return -1;
        }
        synchronized (controller.getLock(phoneId)) {
            if (isUseKeyNative()) {
                ret = SprdNative.vsim_exit(phoneId);
            } else {
                try {
                    if (sprdIntfService.getSprdIntfApi() == null) {
                        logd("intf is null, vsimservice is not ok!");
                        return -1;
                    }
                    ret = sprdIntfService.getSprdIntfApi().vsimExit(phoneId);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        }
        logd("vsimExit " + phoneId + " return " + ret);
        return ret;
    }

    public int vsimSetAuthId(int authId) {
        int ret = -1;
        if (authId >= MAX_SLOT || authId < 0) {
            loge("vsimSetAuthId: " + " slot invalid " + authId);
            return -1;
        }
        synchronized (controller.getLock(authId)) {
            if (isUseKeyNative()) {
                ret = SprdNative.vsim_set_authid(authId);
            } else {
                try {
                    if (sprdIntfService.getSprdIntfApi() == null) {
                        logd("intf is null, vsimservice is not ok!");
                        return -1;
                    }
                    ret = sprdIntfService.getSprdIntfApi().vsimSetAuthid(authId);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        }
        logd("vsimSetAuthId " + authId + " return " + ret);
        return ret;
    }

    public int vsimQueryAuthId() {
        int ret = -1;
        if (isUseKeyNative()) {
            ret = SprdNative.vsim_query_authid();
        } else {
            try {
                if (sprdIntfService.getSprdIntfApi() == null) {
                    logd("intf is null, vsimservice is not ok!");
                    return -1;
                }
                ret = sprdIntfService.getSprdIntfApi().vsimQueryAuthid();
            } catch (RemoteException e) {
                e.printStackTrace();
                return -1;
            }
        }
        logd("vsimQueryAuthId return " + ret);
        return ret;
    }

    public int vsimSetVirtual(int phoneId, int mode) {
        int ret = -1;
        if (phoneId >= MAX_SLOT || phoneId < 0) {
            loge("vsimSetVirtual: " + " slot invalid " + phoneId);
            return -1;
        }
        synchronized (controller.getLock(phoneId)) {
            if (isUseKeyNative()) {
                // ret = SprdNative.vsim_set_virtual(phoneId, mode);
                // 新Modem修复启动时保留云卡状态的问题
                // isWrite == 0 不写入，1 写入
                int isWrite = 0;
                if (mode == 0)
                    isWrite = 1;
                loge("vsim_set_nv phoneId = " + phoneId + "; mode = " + mode + "; isWrite = " + isWrite);
                ret = SprdNative.vsim_set_nv(phoneId, mode, isWrite);
                loge("vsim_set_nv result = " + ret);
            } else {
                try {
                    if (sprdIntfService.getSprdIntfApi() == null) {
                        logd("intf is null, vsimservice is not ok!");
                        return -1;
                    }
                    ret = sprdIntfService.getSprdIntfApi().vsimSetVirtual(phoneId, mode);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        }
        logd("vsimSetVirtual " + phoneId + " mode" + mode + " return " + ret);
        return ret;
    }

    public int vsimQueryVirtual(int phoneId) {
        int ret = -1;
        if (phoneId >= MAX_SLOT || phoneId < 0) {
            loge("vsimQueryVirtual: " + " slot invalid " + phoneId);
            return -1;
        }
        synchronized (controller.getLock(phoneId)) {
            if (isUseKeyNative()) {
                ret = SprdNative.vsim_query_virtual(phoneId);
            } else {
                try {
                    if (sprdIntfService.getSprdIntfApi() == null) {
                        logd("intf is null, vsimservice is not ok!");
                        return -1;
                    }
                    ret = sprdIntfService.getSprdIntfApi().vsimQueryVirtual(phoneId);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        }
        logd("vsimQueryVirtual phone" + phoneId + " return " + ret);
        return ret;
    }

    /*
    1: LTE ATTACH
    2: LTE TAU
    3: LTE SR
    4:3G ATTACH
    5:3G RAU
    6:3G SR
    0:invalid
     */
    public int getVsimApduCause(int phoneId) {
        int ret = -1;
        if (phoneId >= MAX_SLOT || phoneId < 0) {
            loge("getVsimApduCause: " + " slot invalid " + phoneId);
            return -1;
        }
        if (isUseKeyNative()) {
            ret = SprdNative.vsim_get_auth_cause(phoneId);
        } else {
            try {
                if (sprdIntfService.getSprdIntfApi() == null) {
                    logd("intf is null, vsimservice is not ok!");
                    return -1;
                }
                ret = sprdIntfService.getSprdIntfApi().vsimGetAuthCause(phoneId);
            } catch (RemoteException e) {
                e.printStackTrace();
                return -1;
            }
        }
        logd("getVsimApduCause " + phoneId + " return " + ret);
        return ret;
    }

    public int setDefatultDataSubId(int subId) {
        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            sprdIntfService.getSprdIntfApi().setDefaultDataSubId(subId);
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public int setSimPowerStateForSlot(int slot, boolean state) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimPowerStateForSlot: " + " slot invalid " + slot);
            return -1;
        }

        /**
         * 这里需要增加一个配置，在设置setSimPowerStateForSlot之前，
         * 禁止另外一个卡槽拨号(dds在另外一路，并且还没有拨号上)，否则导致拨号失败，并且不会重试
         */
        boolean needReset = false;
        int defaultDataPhoneId = getDefaultDataPhoneId();
        int defaultSubId = -1;
        NetworkInfo networkInfo = null;
        if(defaultDataPhoneId == getNextSlot(slot)){
            defaultSubId = ServiceManager.systemApi.getDefaultDataSubId();
            boolean isEnable = false;
            if(defaultSubId > 0) {
                isEnable = PhoneStateUtil.Companion.getMobileDataEnable(ServiceManager.appContext, defaultSubId);
                ConnectivityManager connectivityManager = ConnectivityManager.from(ServiceManager.appContext);
                networkInfo = connectivityManager.getActiveNetworkInfo();
                if(isEnable && networkInfo != null && networkInfo.getState() != NetworkInfo.State.CONNECTED){
                    logd("need to close network mobile!");
                    PhoneStateUtil.Companion.setMobileDataEnable(ServiceManager.appContext, defaultSubId, false);
                    needReset = true;
                }
            }
            logd("defaultDataPhoneId " + defaultDataPhoneId + " default data subid:" + defaultSubId
                    + " current slot:" + slot + " networkinfo: " + networkInfo);
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            logd("setSimPowerStateForSlot: slot = " + slot + ", state = " + state);
            synchronized (controller.getLock(slot)) {
                sprdIntfService.getSprdIntfApi().setSimPowerStateForSlot(slot, state);
            }
            if (slot == Configuration.INSTANCE.getSeedSimSlot()) {
                // 种子卡
                mIsSeedPhyCard = state;
            } else {
                // 云卡
                mIsCloudPhyCard = state;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        } finally {
            if(needReset){
                logd("need to restore configuration");
                PhoneStateUtil.Companion.setMobileDataEnable(ServiceManager.appContext, defaultSubId, true);
            }
        }
        return 0;
    }

    public int setAttachApn(int slot, String pdpType, String apn, String userName, String pwd, int authtype) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimPowerStateForSlot: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                sprdIntfService.getSprdIntfApi().attachAPN(slot, pdpType, apn, userName, pwd, authtype);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public String sendAtCmd(int slot, String cmd) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimPowerStateForSlot: " + " slot invalid " + slot);
            return "slot invalid " + slot;
        }

        return SprdNative.send_at_cmd(slot, cmd);
    }

    public String getSubscriberIdForSlotIdx(int slotId) {
        if (slotId >= MAX_SLOT || slotId < 0) {
            loge("getSubscriberIdForSlotIdx: " + " slot invalid " + slotId);
            return "";
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return "";
            }
            synchronized (controller.getLock(slotId)) {
                String imsi = sprdIntfService.getSprdIntfApi().getSubscriberIdForSlotIdx(slotId);
                if (imsi == null) {
                    return "";
                } else {
                    return imsi;
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return "";
        }
    }

    public int getSubIdBySlot(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getSubscriberIdForSlotIdx: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                int subid = sprdIntfService.getSprdIntfApi().getSubId(slot);
                logd("get subid by slot " + slot + " subid:" + subid);
                return subid;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getVoiceRegState(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getVoiceRegState: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getVoiceRegState(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getDataRegState(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getDataRegState: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getDataRegState(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public String getNetworkOperator(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getDataRegState: " + " slot invalid " + slot);
            return "";
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return "";
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getNetworkOperator(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return "";
        }
    }

    public int getVoiceNetworkType(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getVoiceNetworkType: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getVoiceNetworkType(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getDataNetworkType(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getDataNetworkType: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getDataNetworkType(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getSimState(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("getSimState: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getSimState(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int setDataEnabled(boolean enable) {
        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            sprdIntfService.getSprdIntfApi().setDataEnabled(enable);
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public int setSimNeworkType(int slot, int type, boolean isPrimary) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                sprdIntfService.getSprdIntfApi().setSimNeworkType(slot, type, isPrimary);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public int getSimNeworkType(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().getSimNeworkType(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getDefaultDataPhoneId() {
        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            return sprdIntfService.getSprdIntfApi().getDefaultDataPhoneId();
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int setDefaultDataPhoneId(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                sprdIntfService.getSprdIntfApi().setDefaultDataPhoneId(slot);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public int restartRadio() {
        try {
            sprdIntfService.getSprdIntfApi().restartRadio();
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public int updatePlmn(int slot, int type, int action, java.lang.String plmn, int act1, int act2, int act3) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return -1;
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().updatePlmn(slot, type, action, plmn, act1, act2, act3);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public String[] queryPlmn(int slot, int type) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return new String[0];
        }

        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return new String[]{"null"};
            }
            synchronized (controller.getLock(slot)) {
                return sprdIntfService.getSprdIntfApi().queryPlmn(slot, type);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return new String[0];
        }
    }

    public int setImei(int slot, String imei) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return -1;
        }
        logd("setImei,slot " + slot + " imei " + imei);
        if (TextUtils.isEmpty(imei)){
            //当设置的imei为空时，将设备imei1传入进去
            imei = Configuration.getImei(ServiceManager.appContext, 0);
        }
        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return -1;
            }
            synchronized (controller.getLock(slot)) {
                int ret = sprdIntfService.getSprdIntfApi().setImei(slot, imei);
                logd("sprdIntfService.getSprdIntfApi().setImei " + slot + " imei " + imei + " ret:" + ret);
                return ret;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public String getImei(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setSimNeworkType: " + " slot invalid " + slot);
            return "";
        }
        try {
            if (sprdIntfService.getSprdIntfApi() == null) {
                logd("intf is null, vsimservice is not ok!");
                return "";
            }
            synchronized (controller.getLock(slot)) {
                String imei = sprdIntfService.getSprdIntfApi().getImei(slot);
                logd("sprdIntfService.getSprdIntfApi().getImei slot" + slot + " imei:" + imei);
                return imei;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 17A表示 l+w，种子卡是w+g模式，云卡是L+w模式
     * 18A表示 l+l，种子卡为L+w+g模式，云卡为L+w模式
     * 建议设置如下几种类型：
     * 云卡支持:
     * int RILConstants.NETWORK_MODE_WCDMA_ONLY     = 2; // WCDMA only
     * int RILConstants.NETWORK_MODE_LTE_ONLY       = 11; /* LTE Only mode.
     * int RILConstants.NETWORK_MODE_LTE_WCDMA      = 12; /* LTE/WCDMA

     * 种子卡支持:
     * int RILConstants.NETWORK_MODE_WCDMA_PREF     = 0; /* GSM/WCDMA (WCDMA preferred)
     * int RILConstants.NETWORK_MODE_GSM_ONLY       = 1; /* GSM only
     * int RILConstants.NETWORK_MODE_WCDMA_ONLY     = 2; /* WCDMA only
     *
     */
    public boolean setPreferredNetworkType(int slot, int type) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setPreferredNetworkType: " + " slot invalid " + slot);
            return false;
        }
        if (sprdIntfService.getSprdIntfApi() == null) {
            logd("intf is null, vsimservice is not ok!");
            return false;
        }
        // 根据需要设置实际type值
        type = getRealType(slot, type);
        // 根据展讯判断是否支持
        if (!isAllow(slot, type))
            return false;
        synchronized (controller.getLock(slot)) {
            return setCardNetworkType(slot, type);
        }
    }

    /**
     * 实际设置卡状态制式
     */
    private boolean setCardNetworkType(int slot, int type) {
        try {
            // 获取所设置的卡当前状态
            CardStatus cs;
            if (slot == Configuration.INSTANCE.getSeedSimSlot()) {
                cs = ServiceManager.seedCardEnabler.getCardState();
            } else {
                cs = ServiceManager.cloudSimEnabler.getCardState();
            }
            if (cs.ordinal() >= CardStatus.READY.ordinal()) {
                // 如果卡已经Ready，则调用setPreferredNetworkType 直接重启协议栈
                logd("Card state is " + cs + ", setPreferredNetworkType");
                return sprdIntfService.getSprdIntfApi().setPreferredNetworkType(slot, type);
            } else {
                // 如果卡未Ready，则调用setSimNeworkType 如果卡load之后调用需要手动重启协议栈
                // isPrimary true是云卡，false是种子卡
                boolean isPrimary = slot == Configuration.INSTANCE.getCloudSimSlot();
                logd("Card state is " + cs + ", set " + isPrimary + " to setSimNeworkType");
                // 修复Bug31439 对应CQ：SPCSS00469188
                sprdIntfService.getSprdIntfApi().setSimNeworkType(slot, type, isPrimary);
                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取实际需要设置的type值
     *
     * 仅在展讯云卡设置为2/3/4G时修改为3/4G，其他情况保持不变
     *
     * @param slot 卡槽位置
     * @param type 设置的属性值
     * @return 返回实际的属性值
     */
    private int getRealType(int slot, int type) {
        if (slot == Configuration.INSTANCE.getCloudSimSlot()
                && type == RILConstants.NETWORK_MODE_LTE_GSM_WCDMA) {
            return RILConstants.NETWORK_MODE_LTE_WCDMA;
        }if (slot == Configuration.INSTANCE.getCloudSimSlot()
                && type == RILConstants.NETWORK_MODE_WCDMA_PREF) {
            return RILConstants.NETWORK_MODE_WCDMA_ONLY;
        } else {
            return type;
        }
    }

    /**
     * 根据展讯平台不同，判断是否支持设置属性
     * @param slot 卡槽位置
     * @param type 所设置的属性值
     * @return true：支持；false：不支持
     */
    private boolean isAllow(int slot, int type) {
        // type需要为所支持的type种类之一
        if(type != RILConstants.NETWORK_MODE_GSM_ONLY
                && type != RILConstants.NETWORK_MODE_WCDMA_ONLY
                && type != RILConstants.NETWORK_MODE_LTE_ONLY
                && type != RILConstants.NETWORK_MODE_WCDMA_PREF
                && type != RILConstants.NETWORK_MODE_LTE_WCDMA
                && type != RILConstants.NETWORK_MODE_LTE_GSM_WCDMA){
            loge("type invalid in sprd!");
            return false;
        }

        /**
         * 展讯平台Modem版本所支持的属性值设置信息如下
         *
         * 17A表示 l+w，按照原有逻辑走，其中prislot修改为云卡的slot （种子卡是w+g模式，云卡是L+w模式）
         * 18A表示 l+l，种子卡可以设置为L+w+g模式，云卡可以设置为L+w模式
         * （l代表lte  w代表wcdma g代表gsm）
         */
        int cloudSlot = Configuration.INSTANCE.getCloudSimSlot();
        logd("Current cloud sim slot = " + cloudSlot + ", slot = " + slot);
        if (slot == cloudSlot) {
            if (type != RILConstants.NETWORK_MODE_WCDMA_ONLY
                    && type != RILConstants.NETWORK_MODE_LTE_ONLY
                    && type != RILConstants.NETWORK_MODE_LTE_WCDMA) {
                loge("cloud slot do not support type " + type);
                return false;
            }
        } else {
            if (isModem17A()) {
                // 17A
                logd("Modem 17A");
                if (type != RILConstants.NETWORK_MODE_WCDMA_PREF
                        && type != RILConstants.NETWORK_MODE_GSM_ONLY
                        && type != RILConstants.NETWORK_MODE_WCDMA_ONLY) {
                    loge("seed slot do not support type " + type);
                    return false;
                }
            } else {
                // 18A
                logd("Modem 18A");
                // 18A下支持所有模式，并且不支持的模式已经在本方法开头判断全部处理掉了
                return true;
            }
        }
        return true;
    }

    /**
     * 18A 平台 退出软卡/云卡时，恢复卡槽制式为2/3/4G（对应workmode=6）
     * 防止卡槽制式3/4G（workmode=24）引起的物理卡不可拨号打电话的问题
     * @param slot 卡槽
     */
    public boolean resetVsimSlotModeType(int slot) {
        if (slot >= MAX_SLOT || slot < 0) {
            loge("setPreferredNetworkType: " + " slot invalid " + slot);
            return false;
        }
        if (sprdIntfService.getSprdIntfApi() == null) {
            logd("intf is null, vsimservice is not ok!");
            return false;
        }
        synchronized (controller.getLock(slot)) {
            if (isModem17A()){
                logd("Current reset slot = " + slot);
                if (slot == Configuration.INSTANCE.getCloudSimSlot()) {
                    return setCardNetworkType(slot, RILConstants.NETWORK_MODE_LTE_WCDMA);
                } else if (slot == Configuration.INSTANCE.getSeedSimSlot()){
                    return setCardNetworkType(slot, RILConstants.NETWORK_MODE_GSM_UMTS);
                }
            } else {
                return setCardNetworkType(slot, RILConstants.NETWORK_MODE_WCDMA_PREF);
            }
            return false;
        }
    }

    /**
     * 展讯平台Modem版本所支持的属性值设置信息如下
     *
     * 可以从如下属性获取, 当前modem 版本, CP提供的at, 返回值最后会写在如下属性: adb shell getprop gsm.version.baseband
     *
     * 请根据base version来判断是LL还是LW
     * LW:FM_BASE_17A_Release_W18.xx.x
     * LL:FM_BASE_18A_W18.xx.x
     * @return true: 17A; false: 18A
     */
    private boolean isModem17A() {
        try {
            String baseBand = SystemProperties.get("gsm.version.baseband");
            logd("gsm.version.baseband is " + baseBand);
            return baseBand.toUpperCase().contains("BASE_17A");
        }catch (Exception e){
            e.printStackTrace();
            return true;
        }
    }

    /**
     * 获取另外一个卡槽，暂时只支持双卡
     */
    private int getNextSlot(int slot){
        if(slot == 0){
            return 1;
        }else if(slot == 1){
            return 0;
        }else {
            return -1;
        }
    }
}
