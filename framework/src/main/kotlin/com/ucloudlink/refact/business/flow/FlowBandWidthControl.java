package com.ucloudlink.refact.business.flow;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;

import com.ucloudlink.framework.protocol.protobuf.Limit_speed_ctrl;
import com.ucloudlink.framework.protocol.protobuf.S2c_limit_up_down_speed;
import com.ucloudlink.framework.protocol.protobuf.Upload_Current_Speed;
import com.ucloudlink.framework.protocol.protobuf.speed_limit_cuase;
import com.ucloudlink.framework.protocol.protobuf.speed_limit_req_from;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessState;
import com.ucloudlink.refact.business.Requestor;
import com.ucloudlink.refact.business.flow.netlimit.ExtraNetworkCtrl;
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.SeedCardNetManager;
import com.ucloudlink.refact.business.flow.protection.CloudFlowProtectionMgr;
import com.ucloudlink.refact.business.flow.speedlimit.INetSpeedCtrl;
import com.ucloudlink.refact.business.flow.speedlimit.NetSpeedCtrlImpl;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import java.util.concurrent.TimeUnit;

import rx.functions.Action1;

/**
 * Created by pengchugang on 2016/12/15.
 */

public class FlowBandWidthControl {

    public final static int BWCC_IP_ADDR_MIN_LENG = 7;

    public final static String SYS_PRE_FIX_RIL = "ril.";
    public final static String UCLOUD_BANDWIDTH_LEVEL = "ucloud.bandwidth.level";

    private boolean bwccThrottle = false;
    private long bwccTxValue = 0;
    private long bwccRxValue = 0;
    private static FlowBandWidthControl instance = null;
    private int bwccLevel = 0;
    private int bwccSource = 0;
    private int bwccDisplay = 0;
    //private boolean bwccResult;

    private String ifCloudNetwork = "";
    private byte[] bwccThrottleOpLock = new byte[0];
    private byte[] ifCloudNetworkOpLock = new byte[0];

    private NetworkInfo.State cloudCacheState = NetworkInfo.State.UNKNOWN;

    private FlowHandlerThread flowHandlerThread;
    private Handler mHandler;

    private static final int SET_BW_CMD = 1;
    private static final int CLEAR_BW_CMD = 2;
    private static final int GET_BW_CMD = 3;

    private static final String SAVE_IFACE_NAME = "iface";
    private static final String SAVE_RXKBPS_VALUE = "rxkbps";
    private static final String SAVE_TXKBPS_VALUE = "txkbps";

    private INetSpeedCtrl mINetSpeedCtrl;// = new NetSpeedCtrlImpl();// 构造函数中初始化
    private SeedCardNetManager mSeedCardNetManager;// = new SeedCardNetManager();
    private ExtraNetworkCtrl mExtraNetworkCtrl;// = new ExtraNetworkCtrl();
    private CloudFlowProtectionMgr mCloudFlowProtectionMgr;// = new CloudFlowProtectionMgr();
    private boolean isFromS2C = false;

    public CloudFlowProtectionMgr getCloudFlowProtectionMgr(){
        return mCloudFlowProtectionMgr;
    }

    public ExtraNetworkCtrl getExtraNetworkCtrl() {
        return mExtraNetworkCtrl;
    }

    public INetSpeedCtrl getINetSpeedCtrl(){
        return mINetSpeedCtrl;
    }

    public SeedCardNetManager getSeedCardNetManager(){
        return mSeedCardNetManager;
    }

    private FlowBandWidthControl(){
        mINetSpeedCtrl = new NetSpeedCtrlImpl();
        mINetSpeedCtrl.addNetworkCallbackListener(new ConnectivityManager.NetworkCallback(){
            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                FlowBandWidthControl.this.checkOnLinkPropertiesChanged(network, linkProperties);
            }
        });
        mSeedCardNetManager = new SeedCardNetManager();
        mExtraNetworkCtrl = new ExtraNetworkCtrl();
        mCloudFlowProtectionMgr = new CloudFlowProtectionMgr();

        initCloudCardNetPreIfName();

        initCloudMonitor();
        ServiceManager.simMonitor.addNetworkStateListen(networkListen);
        flowHandlerThread = new FlowHandlerThread("flowHandler");
        flowHandlerThread.start();
    }

    public static FlowBandWidthControl getInstance(){
        if (instance == null) {
            instance = new FlowBandWidthControl();
        }
        return instance;
    }

    private void initCloudCardNetPreIfName(){
        String cloudCardNetPreIfName;
        try{
            cloudCardNetPreIfName = ServiceManager.systemApi.getCloudCardNetPreIfName();
        }catch (Exception e){
            cloudCardNetPreIfName = null;
        }
        if(!TextUtils.isEmpty(cloudCardNetPreIfName)){
            ifCloudNetwork = cloudCardNetPreIfName;
        }
        JLog.logi("NetSpeedCtrlLog initCloudCardNetPreIfName() -> cloudCardNetPreIfName = "+(cloudCardNetPreIfName==null?"null":cloudCardNetPreIfName)
                +", ifCloudNetwork = "+(ifCloudNetwork==null?"null":ifCloudNetwork));
    }

    private void initCloudMonitor() {
        ServiceManager.cloudSimEnabler.netStatusObser().subscribe(new Action1<NetworkInfo.State>() {
            @Override
            public void call(NetworkInfo.State cardStatus) {
                JLog.logi("NetSpeedCtrlLog, cloud sim state changed, status = " + cardStatus);
                if (cardStatus != NetworkInfo.State.CONNECTED && cloudCacheState == NetworkInfo.State.CONNECTED) {
                    String temp_ifCloudNetwork = getCurCloudSimIfName();
                    resetInterfaceThrottle(temp_ifCloudNetwork, 0, 0);
                } else if (cardStatus == NetworkInfo.State.CONNECTED && cloudCacheState != NetworkInfo.State.CONNECTED) {
                    //setBwWhenCloudsimChange();
                }
                cloudCacheState = cardStatus;
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                JLog.loge("NetSpeedCtrlLog, cloudCardStatus sub failed," + throwable.getMessage());
            }
        });
    }

    public String getCurCloudSimIfName(){
        return ifCloudNetwork;
    }

    private class FlowHandlerThread extends HandlerThread{
        public FlowHandlerThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            super.run();
        }

        @Override
        protected void onLooperPrepared() {
            mHandler = new Handler(getLooper()){
                @Override
                public void handleMessage(Message msg) {
                    HandlerParam param = (HandlerParam)msg.obj;
                    switch (msg.what){
                        case SET_BW_CMD:
                            setBwProcessInner(param.cmd,param.packet);
                            break;
                        case CLEAR_BW_CMD:
                            clearBwProcessInner(param.cmd,param.packet);
                            break;
                        case GET_BW_CMD:
                            queryBwccThrottleInner(param.cmd,0);
                            break;
                        default:
                            JLog.loge("unknown msg id" + msg.what);
                            break;
                    }
                }
            };
        }
    }

    private CardStateMonitor.NetworkStateListen networkListen = new CardStateMonitor.NetworkStateListen() {
        @Override
        public void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
            JLog.logi("NetSpeedCtrlLog [NetworkStateListen]->" + ", ddsId=" + ddsId + ", state=" + state + ", type=" + type
                    + ", ifName=" + ifName + ", isExistIfNameExtra=" + isExistIfNameExtra + ", subId=" + subId);
            if (state == NetworkInfo.State.CONNECTED) {
                if (ifName != null && ifName.trim().length() > 0) {
                    if (subId > -1 && subId == ServiceManager.cloudSimEnabler.getCard().getSubId()) {
                        ifCloudNetwork = ifName;
                        setBwWhenCloudsimChange();
                    }
                }
            }
        }
    };

    public void release(){
        getSeedCardNetManager().getIUidSeedCardNet().resetAllUserRestrictAppsOnData();
        getSeedCardNetManager().saveUserRestrict(false);
        String temp_ifCloudNetwork = getCurCloudSimIfName();
        if(temp_ifCloudNetwork != null && !temp_ifCloudNetwork.isEmpty()) {
            if(resetInterfaceThrottle(temp_ifCloudNetwork, 0, 0)){
                resetParams();
                setSystemPropertiesBandWidthLv(bwccDisplay, bwccLevel, "call_release");
            }
        }
    }

    public boolean getBwccThrottle(){
        return bwccThrottle;
    }

    public long getBwccTxValue(){
        return  bwccTxValue;
    }

    public long getBwccRxValue(){
        return bwccRxValue;
    }

    /*
    *boolean miui.provider.ExtraNetwork.isMobileRestrict(Context context, String pkgName)
    *boolean miui.provider.ExtraNetwork.setMobileRestrict(Context context, String pkgName, boolean isRestrict)
    */

    /**
     * 仅设置限速
     */
    private boolean setInterfaceThrottle(String ifname, long rxkbps, long txkbps) {
        if (!TextUtils.isEmpty(ifname)) {
            if (mINetSpeedCtrl.getINetSpeed().setInterfaceThrottle(ifname, rxkbps, txkbps) > 0) {
                JLog.logd("NetSpeedCtrlLog setInterfaceThrottle:[success!!] ifname = " + ifname + ", bwccTxValue = " + rxkbps + ", bwccRxValue = " + txkbps);
                return true;
            }
        }
        JLog.logd("NetSpeedCtrlLog setInterfaceThrottle: [fail!!] ifname = " + ifname);
        return false;
    }

    /**
     * 仅解除限制
     */
    private boolean resetInterfaceThrottle(String ifname, long rxKbps, long txKbps) {
        if (mINetSpeedCtrl.getINetSpeed().resetInterfaceThrottle(ifname, rxKbps, txKbps) > 0) {
            JLog.logd("NetSpeedCtrlLog resetInterfaceThrottle : [success!!] ifname = " + ifname);
            return true;
        }
        JLog.logd("NetSpeedCtrlLog resetInterfaceThrottle : [fail!!] ifname = " + ifname);
        return false;
    }

    /**
     * 设置限速并临时保持参数
     */
    private boolean setBwccThrottle(long rxkbps, long txkbps, int isDisplay) {
        setSystemPropertiesBandWidthLv(isDisplay, bwccLevel, "set_BwccThrottle");
        synchronized (ifCloudNetworkOpLock){
            ifCloudNetwork = mINetSpeedCtrl.getINetSpeed().getCloudInterfaceName();
        }
        JLog.logd("NetSpeedCtrlLog [setBwccThrottle()]  rxkbps = " + rxkbps + ", txkbps = " + txkbps + ", isDisplay = " + isDisplay);
        clearOldSettingIfExist();
        String temp_ifCloudNetwork = getCurCloudSimIfName();
        //设置限速前先接触限速
        resetInterfaceThrottle(temp_ifCloudNetwork, rxkbps, txkbps);

        if(setInterfaceThrottle(temp_ifCloudNetwork, rxkbps, txkbps)){
            bwccThrottle = true;
            bwccTxValue = txkbps > 0 ? txkbps : bwccTxValue;
            bwccRxValue = rxkbps > 0 ? rxkbps : bwccRxValue;
            saveBwSetting(temp_ifCloudNetwork, bwccRxValue, bwccTxValue);
            if(isFromS2C){
                ServiceManager.accessEntry.getAccessState().updateCommMessage(3, "up:" + bwccTxValue + ",down:" + bwccRxValue
                        + ",display:" + ((isDisplay == 0)? "false" : "true"));

            }
            JLog.logd("NetSpeedCtrlLog setBwccThrottle:[success!!] ifname = " + temp_ifCloudNetwork + ", current real bandwidth (up/down) = " + bwccTxValue + "/" + bwccRxValue);
            setSystemPropertiesBandWidthLv(isDisplay, bwccLevel, "set_BwccThrottle");
            return true;
        }
        JLog.logd("NetSpeedCtrlLog setBwccThrottle:[fail!!] ifname = " + temp_ifCloudNetwork + "current real bandwidth (up/down) = " + bwccTxValue + "/" + bwccRxValue);

        return false;
    }

    /**
     * 解除限速并重置参数
     */
    private boolean resetBwccThrottle(AccessState accessState) {
        JLog.logd("NetSpeedCtrlLog [resetBwccThrottle()]");
        clearOldSettingIfExist();
        String temp_ifCloudNetwork = getCurCloudSimIfName();
        if(resetInterfaceThrottle(temp_ifCloudNetwork, bwccRxValue, bwccTxValue)){
            resetParams();
            saveBwSetting("", 0, 0);
            accessState.updateCommMessage(4, "");
            return true;
        }
        JLog.logd("NetSpeedCtrlLog resetBwccThrottle:[fail!!] ifname = " + temp_ifCloudNetwork + "current real bandwidth (up/down) = " + bwccTxValue + "/" + bwccRxValue);
        return false;
    }

    public void setBwWhenCloudsimChange(){
//        synchronized (bwccThrottleOpLock){
//            String ifname = mINetSpeedCtrl.getINetSpeed().getCloudInterfaceName();
//            String temp_ifCloudNetwork = getCurCloudSimIfName();
//            JLog.logd("NetSpeedCtrlLog, now cloudsim interface is " + (ifname==null?"null":ifname)+", ifCloudNetwork = "+(temp_ifCloudNetwork==null?"null":temp_ifCloudNetwork));
//            if(!TextUtils.isEmpty(ifname) && !ifname.equals(temp_ifCloudNetwork)){
//                JLog.logd("NetSpeedCtrlLog, cloud sim interface change " + temp_ifCloudNetwork + " -> " + ifname);
//                setBwccThrottle(bwccRxValue, bwccTxValue, bwccDisplay);
//            }
//        }
        synchronized (bwccThrottleOpLock) {
            JLog.logi("NetSpeedCtrlLog [setBwWhenCloudsimChange()]");
            JLog.logd("now cloudsim interface = " + (ifCloudNetwork == null ? "null" : ifCloudNetwork) + ",bwccThrottle = " + bwccThrottle);
            if (bwccThrottle) {
                setBwccThrottle(bwccRxValue, bwccTxValue, bwccDisplay);
            } else {
                clearOldSettingIfExist();
            }
        }
    }

    /**
     * 云卡在线情况下，云卡的IfName改变,只调用清除限速，再设置限速, 不参与逻辑
     */
    public void checkOnLinkPropertiesChanged(Network network, LinkProperties linkProperties){
        synchronized (bwccThrottleOpLock){
            boolean bwccThrottle = getBwccThrottle();
            if(!bwccThrottle){
                JLog.logd("NetSpeedCtrlLog: NetworkCallback.onLinkPropertiesChanged -> bwccThrottle = " + bwccThrottle);
                return;
            }
            String curCloudSimIfName = getCurCloudSimIfName();
            if(TextUtils.isEmpty(curCloudSimIfName)){
                JLog.logd("NetSpeedCtrlLog: NetworkCallback.onLinkPropertiesChanged -> bwccThrottle = " + bwccThrottle +", curCloudSimIfName = Null");
                return;
            }
            String linkIfName = "";
            if(linkProperties!=null){
                linkIfName = linkProperties.getInterfaceName();
            }
            if(TextUtils.isEmpty(linkIfName) || linkIfName.equals(curCloudSimIfName)){
                JLog.logd("NetSpeedCtrlLog: NetworkCallback.onLinkPropertiesChanged -> bwccThrottle = " + bwccThrottle +
                        ", curCloudSimIfName = " + curCloudSimIfName +", linkIfName = " + (linkIfName==null?"null":linkIfName));
                return;
            }

            long rxKbps = getBwccRxValue();
            long txKbps = getBwccTxValue();
            int resetRet = FlowBandWidthControl.getInstance().getINetSpeedCtrl().getINetSpeed().resetInterfaceThrottle(curCloudSimIfName, rxKbps, txKbps);
            int setRet = -1;
            if(resetRet > 0){
                setRet = FlowBandWidthControl.getInstance().getINetSpeedCtrl().getINetSpeed().setInterfaceThrottle(linkIfName, rxKbps, txKbps);
                if(setRet > 0){
                    synchronized (ifCloudNetworkOpLock){
                        ifCloudNetwork = linkIfName;
                    }
                }
            }

            JLog.logd("NetSpeedCtrlLog: NetworkCallback.onLinkPropertiesChanged -> " +
                    " curCloudSimIfName = " + curCloudSimIfName +", linkIfName = " + linkIfName +
                    ", bwccThrottle = " + bwccThrottle + ", rxKbps = " + rxKbps +", txKbps = " + txKbps +
                    ", resetRet = " + resetRet + ", setRet = " + setRet);
        }
    }

    /**
     * S2C下发设置限速
     */
    private void setBwProcessInner(S2c_limit_up_down_speed pcmd, ProtoPacket protoPacket) {
        synchronized (bwccThrottleOpLock) {
            JLog.logi("NetSpeedCtrlLog setBwProcessInner s2c_set :"+ pcmd);
            try {
                //不支持0
                if (pcmd.down_speed <= 0 || pcmd.up_speed <= 0) {
                    JLog.logi("NetSpeedCtrlLog setBwProcessInner() invalid parameters");
                    Requestor.INSTANCE.s2cCmdResp(2, protoPacket);
                    return;
                }
                int result;
                isFromS2C = true;
                if (pcmd.priority_level < bwccLevel){
                    JLog.logi("NetSpeedCtrlLog setBwProcessInner[fail]: priority_level < bwccLevel:"+ bwccLevel);
                    result = 3;
                    Requestor.INSTANCE.s2cCmdResp(3, protoPacket);
                } else {
                    boolean bwccResult = setBwccThrottle(pcmd.down_speed, pcmd.up_speed, pcmd.if_display);
                    if(bwccResult) {
                        Requestor.INSTANCE.s2cCmdResp(0, protoPacket);
                        bwccLevel = pcmd.priority_level;
                        bwccSource = pcmd.cmd_source;
                        bwccDisplay = pcmd.if_display;
                        result = 0;
                    } else {
                        Requestor.INSTANCE.s2cCmdResp(2, protoPacket);
                        result = 1;
                    }
                }
                setSystemPropertiesBandWidthLv(bwccDisplay, bwccLevel, "set_BwProcess_Inner");
                queryBwccThrottleInner(pcmd, result);
                JLog.logi("NetSpeedCtrlLog bwcc s2c_set bw:");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setSystemPropertiesBandWidthLv(int display, int level, String tag){
        JLog.logi("NetSpeedCtrlLog setSystemPropertiesBandWidthLv -> tag = "+ (tag==null?"null":tag) + ", display = " + display);

        String vl = display ==1 ? String.valueOf(level): String.valueOf(0);

        try{
            SystemProperties.set(UCLOUD_BANDWIDTH_LEVEL, vl);
        } catch (Exception e){
            JLog.logi("NetSpeedCtrlLog SystemProperties.set() -> UCLOUD_BANDWIDTH_LEVEL -> Exception: "+ e.toString());
            e.printStackTrace();
        }

        try{
            SystemProperties.set(SYS_PRE_FIX_RIL + UCLOUD_BANDWIDTH_LEVEL, vl);
        } catch (Exception e){
            JLog.logi("NetSpeedCtrlLog SystemProperties.set() -> SYS_PRE_FIX_RIL + UCLOUD_BANDWIDTH_LEVEL -> Exception: "+ e.toString());
            e.printStackTrace();
        }

        try{
            JLog.logi("NetSpeedCtrlLog bwcc s2c_set bw: level = " + level + ", vl = " + (vl==null?"null":vl)
                    + ", get(0) = " + SystemProperties.get(UCLOUD_BANDWIDTH_LEVEL)
                    + ", key(0).len = " + UCLOUD_BANDWIDTH_LEVEL.length()
                    +", get(1) = " + SystemProperties.get(SYS_PRE_FIX_RIL + UCLOUD_BANDWIDTH_LEVEL)
                    +", key(1).len = " + ((SYS_PRE_FIX_RIL + UCLOUD_BANDWIDTH_LEVEL).length()));
        }catch (Exception e){
            JLog.logi("NetSpeedCtrlLog SystemProperties.get() -> Exception: "+ e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 上报限速
     */
    private void queryBwccThrottleInner(S2c_limit_up_down_speed pcmd, int result){
        try {
            speed_limit_cuase upload_cause;

            if (pcmd.ctrl == Limit_speed_ctrl.LIMIT_SPEED_CTRL_GET_QOS){
                upload_cause = speed_limit_cuase.SPEED_LIMIT_CAUSE_QUERY_QOS;
            }else {
                if (result == 0) {
                    upload_cause = speed_limit_cuase.SPEED_LIMIT_CAUSE_QOS_SUCC;
                } else if (result == 1) {
                    upload_cause = speed_limit_cuase.SPEED_LIMIT_CAUSE_QOS_FAIL;
                } else if (result == 3) {
                    upload_cause = speed_limit_cuase.SPEED_LIMIT_CAUSE_QOS_PRIORITY_ERROR;
                }else {
                    upload_cause = speed_limit_cuase.SPEED_LIMIT_CAUSE_QOS_SUCC;
                }
            }
            JLog.logi("NetSpeedCtrlLog upload bw query: flow_id:" + pcmd.flow_id + ", cause:" + upload_cause + ", Tx/Rx:"
                    + bwccTxValue + "/" + bwccRxValue + ", level:" + bwccLevel + ", source:" + bwccSource + ", bwccDisplay:" + bwccDisplay);
            Requestor.INSTANCE.requstUploadCurrentSpeed(new Upload_Current_Speed(pcmd.flow_id,
                    upload_cause, speed_limit_req_from.SPEED_LIMIT_REQUEST_FROM_NETWORK, (int)bwccTxValue, (int)bwccRxValue, 2, bwccSource, bwccLevel, bwccDisplay))
                    .timeout(10, TimeUnit.SECONDS)
                    .subscribe(
                            new Action1<Object>() {
                                @Override
                                public void call(Object o) {
                                    JLog.logd("do nothing!" + o);
                                }
                            },
                            new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    JLog.logd("do nothing !" + throwable);
                                }
                            }
                    );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearBwProcessInner(S2c_limit_up_down_speed pcmd, ProtoPacket protoPacket){
        synchronized (bwccThrottleOpLock){
            JLog.logi("NetSpeedCtrlLog clearBwProcessInner() -> bwccThrottle1 = "+bwccThrottle);
            try {
                int result = 0;
                if(!bwccThrottle){
                    Requestor.INSTANCE.s2cCmdResp(0, protoPacket);
                    resetParams();
                } else {
                    if (pcmd.priority_level < bwccLevel){
                        Requestor.INSTANCE.s2cCmdResp(2, protoPacket);
                        result = 3;
                    } else if(resetBwccThrottle(ServiceManager.accessEntry.getAccessState())){
                        Requestor.INSTANCE.s2cCmdResp(0, protoPacket);
                        result = 0;
                    } else {
                        Requestor.INSTANCE.s2cCmdResp(2, protoPacket);
                        result = 1;
                    }
                }
                JLog.logi("NetSpeedCtrlLog clearBwProcessInner() -> bwccThrottle2 = "+bwccThrottle);
                queryBwccThrottleInner(pcmd, result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            JLog.logi("NetSpeedCtrlLog clearBwProcessInner() (end) bwccThrottle2 = " + bwccThrottle);
            setSystemPropertiesBandWidthLv(bwccDisplay, bwccLevel, "clear_BwProcess_Inner");
        }

    }

    private void resetParams(){
        JLog.logi("NetSpeedCtrlLog [resetParams()]");
        bwccLevel = 0;
        bwccRxValue = 0;
        bwccTxValue = 0;
        bwccSource = 0;
        bwccDisplay = 0;
        bwccThrottle = false;
    }

    private class HandlerParam{
        S2c_limit_up_down_speed cmd;
        ProtoPacket packet;

        public HandlerParam(S2c_limit_up_down_speed cmd, ProtoPacket packet) {
            this.cmd = cmd;
            this.packet = packet;
        }
    }

    public void setBwProcess(S2c_limit_up_down_speed pcmd, ProtoPacket protoPacket){
        mHandler.obtainMessage(SET_BW_CMD, new HandlerParam(pcmd, protoPacket)).sendToTarget();
    }

    public void clearBwProcess(S2c_limit_up_down_speed pcmd, ProtoPacket protoPacket){
        mHandler.obtainMessage(CLEAR_BW_CMD, new HandlerParam(pcmd, protoPacket)).sendToTarget();
    }

    public void queryBwProcess(S2c_limit_up_down_speed pcmd, ProtoPacket protoPacket) {
        mHandler.obtainMessage(GET_BW_CMD, new HandlerParam(pcmd, protoPacket)).sendToTarget();
    }

    private boolean clearOldSettingIfExist() {
        String iface = SharedPreferencesUtils.getString(ServiceManager.INSTANCE.getAppContext(), SAVE_IFACE_NAME, "");
        if (!TextUtils.isEmpty(iface)) {
            JLog.logd("NetSpeedCtrlLog [clearOldSettingIfExist()] iface = " + iface);
            if (resetInterfaceThrottle(iface, 0, 0)) {
                return true;
            }
        }
        return false;
    }

    public void clearBwSetting(AccessState accessState){
        JLog.logd("NetSpeedCtrlLog [clearBwSetting()]");
        clearOldSettingIfExist();
        isFromS2C = false;
        if (resetBwccThrottle(accessState)) {
            resetParams();
            accessState.updateCommMessage(4, "");
        }

        setSystemPropertiesBandWidthLv(bwccDisplay, bwccLevel, "clear_BwSetting");
    }

    private void saveBwSetting(String iface, long rxkbps, long txkbps){
        SharedPreferencesUtils.putString(ServiceManager.INSTANCE.getAppContext(), SAVE_IFACE_NAME, iface);
        SharedPreferencesUtils.putLong(ServiceManager.INSTANCE.getAppContext(), SAVE_RXKBPS_VALUE, rxkbps);
        SharedPreferencesUtils.putLong(ServiceManager.INSTANCE.getAppContext(), SAVE_TXKBPS_VALUE, txkbps);
    }

    public boolean isInSpeedLimit(){
        if((bwccRxValue  > 0) && (bwccTxValue > 0)){
            return true;
        }
        return false;
    }

    public String getSpeedLimitData(){
        return "up:" + bwccTxValue + ",down:" + bwccRxValue + ",display:" + ((bwccDisplay == 0)? "false":"true");
    }

}
