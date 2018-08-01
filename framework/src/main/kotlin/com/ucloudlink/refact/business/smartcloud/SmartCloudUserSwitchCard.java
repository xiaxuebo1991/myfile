package com.ucloudlink.refact.business.smartcloud;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.netqueryservice.INetQueryInterface;
import com.ucloudlink.framework.protocol.protobuf.PlmnInfo;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.channel.enabler.datas.Plmn;
import com.ucloudlink.refact.channel.monitors.CardStateMonitor;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Created by zhanlin.ma on 2018/2/2.
 */

public class SmartCloudUserSwitchCard {

    static final   String                   TAG      = "smartcloud SmartCloudUserSwitchCard";
    private static SmartCloudUserSwitchCard instance = null;
    private CardStateMonitor.ScanNwlockListen nwListen;
    private              int          mSwitchCardState            = SWITCH_CARD_STATE_INIT;
    private static final int          SWITCH_CARD_STATE_INIT      = 0;
    private static final int          SWITCH_CARD_STATE_SWITCHING = 1;
    public static final  int          SWITCH_CARD_STATE_COMPLETE  = 2;
    private static final int          EVENT_SCAN_NETWORK_TIMEOUT  = 3;
    private static final int          EVENT_RECEIVE_SCAN_NET_RET  = 4;
    private static final int          EVENT_DEEP_SWITCH_CARD_FAIL = 5;
    private static final int          EVENT_DEEP_SWITCH_CARD_SUCC = 6;
    private Subscription mPersentSub = null;
    ArrayList<PlmnInfo> plmnInfos = new ArrayList<>();

    private INetQueryInterface IQueryNetService = null;

    public boolean isOptimizing() {
        if (mSwitchCardState == SWITCH_CARD_STATE_SWITCHING){
            JLog.logi(TAG,"isOptimizing true");
            return true;
        }else{
            JLog.logi(TAG,"isOptimizing false");
            return false;
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SCAN_NETWORK_TIMEOUT:
                    JLog.logi(TAG,"scan timeout recover cloud data enable true.");
                    ServiceManager.systemApi.setDataEnabled(Configuration.INSTANCE.getCloudSimSlot(),
                            ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()), true);
                    unRegisterListen();
                    mHandler.sendEmptyMessage(EVENT_DEEP_SWITCH_CARD_FAIL);
                    break;
                case EVENT_RECEIVE_SCAN_NET_RET:
                    stopScanNetTimeoutTimer();
                    unRegisterListen();

                    JLog.logi(TAG,"scan complete recover cloud data enable true.");
                    ServiceManager.systemApi.setDataEnabled(Configuration.INSTANCE.getCloudSimSlot(),
                            ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()), true);

                    ArrayList<Plmn> plmns = (ArrayList<Plmn>) msg.obj;
                    JLog.logi(TAG, "EVENT_RECEIVE_SCAN_NET_RET plmns:" + plmns);
                    plmnInfos.clear();
                    for (int i = 0; i < plmns.size(); i++){
                        PlmnInfo plmnInfo = new PlmnInfo(plmns.get(i).getMccmnc(),plmns.get(i).getRat(),plmns.get(i).getSignalStrength(),0);
                        plmnInfos.add(plmnInfo);
                    }

                    Message req = SmartCloudController.getInstance().getHandler()
                            .obtainMessage(SmartCloudEventId.EVENT_SWITCH_CARD_REQ, 0, SmartCloudSwitchReason.SWITCH_VSIM_ONE_KEY_SWITCH_DEEP, plmnInfos);

                    SmartCloudController.getInstance().getHandler().sendMessageDelayed(req, 10 * 1000);
                    break;
                case EVENT_DEEP_SWITCH_CARD_FAIL:
                    JLog.logi(TAG, "deep switch card fail.");
                    ServiceManager.accessEntry.getAccessState().updateCommMessage(10, "fail");
                    ServiceManager.appContext.unbindService(mServiceConnection);
                    IQueryNetService = null;
                    unRegisterPersentSub();
                    SmartCloudController.getInstance().recoverEnable();
                    mSwitchCardState = SWITCH_CARD_STATE_COMPLETE;
                    JLog.logi(TAG, "deepSwitchCard end");
                    break;
                case EVENT_DEEP_SWITCH_CARD_SUCC:
                    JLog.logi(TAG, "deep switch card succ.");
                    ServiceManager.accessEntry.getAccessState().updateCommMessage(10, "succ");
                    unRegisterPersentSub();
                    ServiceManager.appContext.unbindService(mServiceConnection);
                    IQueryNetService = null;
                    mSwitchCardState = SWITCH_CARD_STATE_COMPLETE;
                    JLog.logi(TAG, "deepSwitchCard end");
                    break;
                default:
                    break;
            }
        }
    };

    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IQueryNetService = INetQueryInterface.Stub.asInterface(service);
            JLog.logi(TAG, "IQueryNetService connected. subId:" + ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()));
            try {
                IQueryNetService.scanSimNetwork(ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()));
                JLog.logi(TAG, "start query network");
                //startScanNetTimeoutTimer();
            } catch (Exception e) {
                JLog.loge(TAG, "scanSimNetwork execption:");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            JLog.logi(TAG,"IQueryNetService disconnected.");
            IQueryNetService = null;
        }
    };

    private SmartCloudUserSwitchCard() {
        init();
    }

    private void init() {
        nwListen = new CardStateMonitor.ScanNwlockListen() {
            @Override
            public void onScanNwChanged(int phoneId, ArrayList<Plmn> plmns) {
                JLog.logi(TAG, "onScanNwChanged phoneId:" + phoneId + ",plmns:" + plmns);
                mHandler.obtainMessage(EVENT_RECEIVE_SCAN_NET_RET, plmns).sendToTarget();
            }
        };
    }

    private void registerPersentSub() {
        if (mPersentSub != null) {
            if (!mPersentSub.isUnsubscribed()) {
                mPersentSub.unsubscribe();
            }
        }
        mPersentSub = ServiceManager.INSTANCE.getAccessEntry()
                .getStatePersentOb().timeout(30, TimeUnit.SECONDS).filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer persent) {
                        return persent == 100;
                    }
                })
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        JLog.logi(TAG,"deep switch card succ.");
                        mHandler.sendEmptyMessage(EVENT_DEEP_SWITCH_CARD_SUCC);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        JLog.logi(TAG,"after 30s timeout deep switch card fail.");
                        mHandler.sendEmptyMessage(EVENT_DEEP_SWITCH_CARD_FAIL);
                    }
                });
    }

    private void unRegisterPersentSub() {
        if (mPersentSub != null) {
            if (!mPersentSub.isUnsubscribed()) {
                mPersentSub.unsubscribe();
            }
        }
    }

    private void registerListen() {
        ServiceManager.simMonitor.addScanNwlockListen(nwListen);
    }

    private void unRegisterListen() {
        ServiceManager.simMonitor.removeScanNwlockListen(nwListen);
    }

    private boolean bindQueryNetService(Context ctx) {
        boolean ret = false;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.ucloudlink.uservice", "com.ucloudlink.refact.business.smartcloud.QueryNetService"));
        ret = ctx.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        JLog.logi(TAG, "bindService ret:" + ret);
        return ret;
    }

    public static SmartCloudUserSwitchCard getInstance() {
        if (instance == null) {
            instance = new SmartCloudUserSwitchCard();
        }
        return instance;
    }

    public void setSwichCardRet(int ret) {
        JLog.logi(TAG,"setSwichCardRet ret:"+ret);

        if (ret != 0) {
            mHandler.sendEmptyMessage(EVENT_DEEP_SWITCH_CARD_FAIL);
            return;
        }

        registerPersentSub();

    }

    public void deepSwitchCard() {
        JLog.logi(TAG, "deepSwitchCard start:");
        mSwitchCardState = SWITCH_CARD_STATE_SWITCHING;
        JLog.logi(TAG, "deepSwitchCard close datacall subid:" + ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()));
        startScanNetTimeoutTimer();
        ServiceManager.systemApi.setDataEnabled(Configuration.INSTANCE.getCloudSimSlot(),
                ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()), false);
        registerListen();

        if (IQueryNetService == null) {
            JLog.logi(TAG, "IQueryNetService is null,bindQueryNetService");
            bindQueryNetService(ServiceManager.appContext);
            return;
        }

        try {
            IQueryNetService.scanSimNetwork(ServiceManager.systemApi.getSubIdBySlotId(Configuration.INSTANCE.getCloudSimSlot()));
            JLog.logi(TAG, "start query network");
        } catch (Exception e) {
            JLog.loge(TAG, "scanSimNetwork execption:");
        }

    }

    private void startScanNetTimeoutTimer() {
        if (mHandler.hasMessages(EVENT_SCAN_NETWORK_TIMEOUT)) {
            mHandler.removeMessages(EVENT_SCAN_NETWORK_TIMEOUT);
        }
        mHandler.sendEmptyMessageDelayed(EVENT_SCAN_NETWORK_TIMEOUT, 3 * 60 * 1000);
    }

    private void stopScanNetTimeoutTimer() {
        if (mHandler.hasMessages(EVENT_SCAN_NETWORK_TIMEOUT)) {
            mHandler.removeMessages(EVENT_SCAN_NETWORK_TIMEOUT);
        }
    }
}
