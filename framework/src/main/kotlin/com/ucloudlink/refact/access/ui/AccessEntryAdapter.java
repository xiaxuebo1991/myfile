package com.ucloudlink.refact.access.ui;

import android.content.Context;
import android.os.RemoteException;

import com.ucloudlink.framework.ui.ExceptionValue;
import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.framework.ui.IUcloudAccessCallback;
import com.ucloudlink.framework.ui.IUcloudDownloadSoftsimCallback;
import com.ucloudlink.framework.ui.PlmnInfo;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEntry;
import com.ucloudlink.refact.access.restore.RunningStates;
import com.ucloudlink.refact.access.struct.LoginInfo;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.SeedCardNetRemote;
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.business.smartcloud.SmartCloudController;
import com.ucloudlink.refact.business.smartcloud.SmartCloudUserSwitchCard;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.statebar.NoticeStatusBarServiceStatus;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave;
import com.ucloudlink.refact.systemapi.platform.UnSupportSystem;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.PackageUtils;

import java.util.List;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;

/**
 * Created by shiqianhua on 2016/10/20.
 */
public class AccessEntryAdapter extends com.ucloudlink.framework.ui.IUcloudAccess.Stub {
    private Context ctx;
    private AccessEntry entry;

    public AccessEntryAdapter(Context context) {
        this.ctx = context;
    }

    private void waitForServiceOk() {
        if (entry == null) {
            logd("wait for service ok");
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void startCloudsimService(String userName, String password, String packageId) throws RemoteException {
        // TODO: 2017/3/7
        Configuration.INSTANCE.setOrderId(packageId);
        Configuration.INSTANCE.setUsername(userName);

        waitForServiceOk();
        entry.loginReq(userName, password);
        logd("startCloudsimService --> userName:" + userName + ",orderrelationid:" + packageId);
    }

    @Override
    public void stopCloudsimService() throws RemoteException {
        logd("stopCloudsimService: from UI");
        waitForServiceOk();
        entry.logoutReq(1);
    }

    @Override
    public void registerCallback(IUcloudAccessCallback callback) throws RemoteException {
        logd("registerCallback: " + callback);
        waitForServiceOk();
        entry.registerCb(callback);
    }

    @Override
    public void unRegisterCallback(IUcloudAccessCallback callback) throws RemoteException {
        logd("unRegisterCallback: " + callback);
        waitForServiceOk();
        entry.unregisterCb(callback);
    }

    @Override
    public int getProcessState() throws RemoteException {
        waitForServiceOk();
        return entry.getSystemPersent();
    }

    @Override
    public void setParam(int apdu) {
//        Configuration.INSTANCE.setApduMode(mode);
        logd("setParam: mode:" + apdu);
        int xx = (apdu == 0) ? 1 : 0;
        Configuration.INSTANCE.setSlots(apdu, xx);
    }

    @Override
    public int addConfig(String cmd, String args) throws RemoteException {
        logd("addConfig: " + "cmd:" + cmd + ",args:" + args);
        if (cmd == null) {
            return -1;
        }
        if (cmd.equals("BOOT_AUTO_RECOVER")) {
            if (args.equals("ON")) {
                Configuration.INSTANCE.setRECOVE_WHEN_REBOOT(true);
            } else if (args.equals("OFF")) {
                Configuration.INSTANCE.setRECOVE_WHEN_REBOOT(false);
            } else {
                return -2;
            }
        } else if (cmd.equals("SEED_TYPE")) {
            if (args.equals("PSIM")) {
                Configuration.INSTANCE.setApduMode(Configuration.INSTANCE.getApduMode_Phy());
            } else if (args.equals("SSIM")) {
                Configuration.INSTANCE.setApduMode(Configuration.INSTANCE.getApduMode_soft());
            } else {
                return -2;
            }
        } else if (cmd.equals("CLOUDSIM_SLOT")) {
            if (args.equals("0")) {
                Configuration.INSTANCE.setSlots(1, 0);
            } else if (args.equals("1")) {
                Configuration.INSTANCE.setSlots(0, 1);
            } else {
                return -2;
            }
        } else if (cmd.equals("AUTO_WHEN_REBOOT")) {
            if (args.equals("ON")) {
                Configuration.INSTANCE.setAUTO_WHEN_REBOOT(true);
            } else if (args.equals("OFF")) {
                Configuration.INSTANCE.setAUTO_WHEN_REBOOT(false);
            } else {
                return -2;
            }
        } else if (cmd.equals("USERINFO")) {
            logd("addConfig: USERINFO param:" + args);
            String[] info = args.split(";");
            if (info.length != 2) {
                return -2;
            }
            if (info[0].isEmpty() || info[1].isEmpty()) {
                return -2;
            }
            waitForServiceOk();
            entry.setLoginInfo(new LoginInfo(info[0], info[1]));
            RunningStates.saveUserName(info[0]);
            RunningStates.savePassWord(info[1]);
        } else if (cmd.equals("SERVER_TYPE")) {
            if (args.equals("0")) {
                ServerRouter.INSTANCE.setIpMode(ServerRouter.BUSINESS);
                RunningStates.saveAssServerMode(ServerRouter.INSTANCE.getCurrent_mode());
            } else if (args.equals("1")) {
                ServerRouter.INSTANCE.setIpMode(ServerRouter.SAAS2);
                RunningStates.saveAssServerMode(ServerRouter.INSTANCE.getCurrent_mode());
            } else if (args.equals("2")) {
                ServerRouter.INSTANCE.setIpMode(ServerRouter.SAAS3);
                RunningStates.saveAssServerMode(ServerRouter.INSTANCE.getCurrent_mode());
            } else if (args.equals("3")) {
                ServerRouter.INSTANCE.setIpMode(ServerRouter.FACTORY);
                RunningStates.saveAssServerMode(ServerRouter.INSTANCE.getCurrent_mode());
            } else {
                return -2;
            }
        } else if (cmd.equals("CHANGE_PACKAGE")) {
            String orderId = args;
            if (orderId == null || orderId.length() == 0) {
                loge("addConfig: orderid invalid!");
                return -2;
            }
            waitForServiceOk();
            if (!entry.isServiceRunning()) {
                loge("addConfig: service is not running!");
                return -3;
            }
            if (Configuration.INSTANCE.getOrderId().equals(orderId)) {
                logd("addConfig: order not change:" + orderId);
                return 0;
            }
            OrderInfo info = entry.softsimEntry.getOrderInfoByUserOrderId(Configuration.INSTANCE.getUsername(), orderId);
            if (info == null) {
                loge("addConfig: order" + orderId + " is not exist!");
                return -4;
            }
            if (info.isOutOfDate()) {
                loge("addConfig: order " + orderId + " is out of date");
                return -5;
            }
            logd("addConfig: order change:" + Configuration.INSTANCE.getOrderId() + " -> " + orderId);
            Configuration.INSTANCE.setOrderId(orderId);
        } else if (cmd.equals("PHY_ROAM_ENABLE")) {
            if (args.equals("true")) {
                Configuration.INSTANCE.setPHY_ROAM_ENABLE(true);
            } else if (args.equals("false")) {
                Configuration.INSTANCE.setPHY_ROAM_ENABLE(false);
            } else {
                return -1;
            }
        } else if (cmd.equals("QXDM_ENABLE")) {
            if (args.equals("true")) {
                ServiceManager.systemApi.startModemLog(QxdmLogSave.QXDM_APP_CMD, 0, null);
                Configuration.INSTANCE.setMODEM_LOG_ENABLE(true);
            } else if (args.equals("false")) {
                ServiceManager.systemApi.stopModemLog(QxdmLogSave.QXDM_APP_CMD, 0, null);
                Configuration.INSTANCE.setMODEM_LOG_ENABLE(false);
            } else {
                return -1;
            }
        } else if (cmd.equals("BAND_WIDTH") || cmd.equals("SEED_NETWORK_BAND_WIDTH")) {
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().configNetworkBandWidth(args);
        } else if (cmd.equals("FORWARD_ENABLE") || cmd.equals("SEED_NETWORK_LIMIT_BY_UID")) {
            SeedCardNetRemote.setUiSupportSeedNetworkLimitByUidAndIp("false");
            return SeedCardNetRemote.parseSeedNetworkLimitByUid(args);
        } else if (cmd.equals("FORWARD_DNS_ENABLE") || cmd.equals("SEED_NETWORK_LIMIT_BY_UID_AND_IP")) {
            SeedCardNetRemote.setUiSupportSeedNetworkLimitByUidAndIp("true");
            return SeedCardNetRemote.parseSeedNetworkLimitByUidAndIp(args);
        } else if (cmd.equals("LOCAL_SEEDSIM_DEPTH_OPT")) {
            if (args.equals("true")) {
                Configuration.INSTANCE.setLOCAL_SEEDSIM_DEPTH_OPT(true);
            } else if (args.equals("false")) {
                Configuration.INSTANCE.setLOCAL_SEEDSIM_DEPTH_OPT(false);
            } else {
                return -1;
            }
        } else if(cmd.equals("SWITCH_VSIM_MANUAL")) {
            entry.switchVsimReq(1);
        } else if(cmd.equals("UPDATE_SOFTSIM")){
            JLog.logd("update_softsim right now");
            entry.softsimEntry.softsimUpdateManager.updateSoftsim();
        } else if(cmd.equals("NETWORK_OPTIMIZATION")){
            logi("NETWORK_OPTIMIZATION");
            SmartCloudController.getInstance().oneKeySwitchCardDeep();
        } else {
            return -3;
        }
        return 0;
    }

    /**
     * IS_SUPPORT_DEVICE  true|false   查询本设备是否支持云卡
     *
     * @param cmd
     * @return
     * @throws RemoteException
     */
    @Override
    public String queryConfig(String cmd) throws RemoteException {
        logd("queryConfig: " + cmd);
        if (cmd == null) {
            return null;
        }
        if (cmd.equals("BOOT_AUTO_RECOVER")) {
            return Configuration.INSTANCE.getRECOVE_WHEN_REBOOT() ? "ON" : "OFF";
        } else if (cmd.equals("SEED_TYPE")) {
            return Configuration.INSTANCE.getApduMode() == Configuration.INSTANCE.getApduMode_Phy() ? "PSIM" : "SSIM";
        } else if (cmd.equals("CLOUDSIM_SLOT")) {
            return Configuration.INSTANCE.getCloudSimSlot() == 0 ? "0" : "1";
        } else if (cmd.equals("AUTO_WHEN_REBOOT")) {
            return Configuration.INSTANCE.getAUTO_WHEN_REBOOT() ? "ON" : "OFF";
        } else if (cmd.equals("USERINFO")) {
            return RunningStates.getUserName() + ";********";
        } else if (cmd.equals("SERVER_TYPE")) {
            return RunningStates.getAssServerMode() + "";
        } else if (cmd.equals("CHANGE_PACKAGE")) {
            return Configuration.INSTANCE.getOrderId();
        } else if (cmd.equals("PHY_ROAM_ENABLE")) {
            return Configuration.INSTANCE.getPHY_ROAM_ENABLE() ? "true" : "false";
        } else if (cmd.equals("ROM_SUPPORT_SEED_NETWORK_LIMIT_BY_UID_AND_IP")) {
            return String.valueOf(SeedCardNetRemote.isRomSupportSeedNetworkLimitByUidAndIp());
        } else if (cmd.equals("LOCAL_SEEDSIM_DEPTH_OPT")) {
            return String.valueOf(Configuration.INSTANCE.getLOCAL_SEEDSIM_DEPTH_OPT());
        } else if (cmd.equals("IS_SUPPORT_DEVICE")) {
            return String.valueOf(!(ServiceManager.systemApi instanceof UnSupportSystem));
        }
        return null;
    }

    @Override
    public ExceptionValue getExceptionState() throws RemoteException {
        logd("getExceptionState: " + entry.getExceptionArray());
        waitForServiceOk();
        return new ExceptionValue(entry.getExceptionArray());
    }

    @Override
    public boolean isInException() throws RemoteException {
        waitForServiceOk();
        logd("isInException: " + entry.isInExceptionState());
        return entry.isInExceptionState();
    }

    @Override
    public PlmnInfo getSeedSimPlmn() {
        // TODO: 2017/3/7
        PlmnInfo plmnInfo = new PlmnInfo();
        plmnInfo.setPlmnList(OperatorNetworkInfo.INSTANCE.getSeedPlmnListString());
        logd("getSeedSimPlmn: " + plmnInfo);
        return plmnInfo;
    }

    @Override
    public boolean isInRecovery() throws RemoteException {
        waitForServiceOk();
        logd("isInRecovery: " + entry.isInRecoveryState());
        return entry.isInRecoveryState();
    }

    @Override
    public void downloadSoftsim(String username, String password, List<FlowOrder> orders) throws RemoteException {
        logd("downloadSoftsim: " + username + "," + orders);
        waitForServiceOk();
        entry.softsimEntry.startDownloadSoftsim(username, password, orders);
    }

    @Override
    public void registerDownloadSoftsimCallback(IUcloudDownloadSoftsimCallback callback) throws RemoteException {
        logd("registerDownloadSoftsimCallback: " + callback);
        waitForServiceOk();
        entry.softsimEntry.registerCb(callback);
    }

    @Override
    public void unregisterDownloadSoftsimCb(IUcloudDownloadSoftsimCallback callback) throws RemoteException {
        logd("unregisterDownloadSoftsimCb: " + callback);
        entry.softsimEntry.unregisterCb(callback);
    }

    @Override
    public int startSeedSoftsim(String username, String packageName, int timeout) throws RemoteException {
        logd("startSeedSoftsim: " + username + ", " + packageName);

        waitForServiceOk();
        NoticeStatusBarServiceStatus.INSTANCE.noticStatusBarServiceStatus(NoticeStatusBarServiceStatus.STATUS_RUNNING);
        return entry.startSeedNetwork(username, packageName, timeout);
    }

    @Override
    public int stopSeedSoftsim() throws RemoteException {
        logd("stopSeedSoftsim: ");
        waitForServiceOk();
        NoticeStatusBarServiceStatus.INSTANCE.noticStatusBarServiceStatus(NoticeStatusBarServiceStatus.STATUS_STOP);
        return entry.stopSeedNetwork();
    }

    @Override
    public int activateUserOrder(String username, String order, long activateTime, long deadlineTime) throws RemoteException {
        logd("activateUserOrder: " + username + "," + order + "," + activateTime + "," + deadlineTime);
        waitForServiceOk();
        return entry.softsimEntry.activateUserOrder(username, order, activateTime, deadlineTime);
    }

    @Override
    public String getServiceSidAndVersion() throws RemoteException {
        String ver = PackageUtils.INSTANCE.getAppVersionName();
        String sid = Configuration.INSTANCE.getSID();
        logd("getServiceSidAndVersion: " + sid + "&" + ver);
        return sid + "&" + ver;
    }

    /**
     * 获取状态
     * 1 ---> 软卡更新状态， 返回true or false
     * 2 ---> 限速状态      返回 true or false
     * 3 ---> 限速的值      返回 "up:xxx,down:xxx,display:(true|false)"
     * 4 ---> wifi状态      返回 true or false
     **/
    @Override
    public String getStateStatus(int module) throws RemoteException {
        waitForServiceOk();
        switch (module) {
            case 1:
                return (entry.softsimEntry.softsimUpdateManager.getNeedUpdate()) ? "true" : "false";
            case 2:
                return FlowBandWidthControl.getInstance().isInSpeedLimit() ? "true" : "false";
            case 3:
                return FlowBandWidthControl.getInstance().getSpeedLimitData();
            case 4:
                return (entry.getAccessState().isWifiOn) ? "true" : "false";
            case 5://smartcloud check cloud sim signal is well
                if (SmartCloudController.getInstance().isSignalStrengthWell()){
                    return "true";
                }else{
                    return "false";
                }
            case 6://smartcloud get switch card state
                if (SmartCloudUserSwitchCard.getInstance().isOptimizing()){
                    return "true";
                }else{
                    return "false";
                }
            default:
                return "unkown module";
        }
    }

    public void setEntry(AccessEntry entry) {
        this.entry = entry;
    }


}
