package com.ucloudlink.refact.access;

import android.content.Context;

import com.ucloudlink.framework.ui.IUcloudAccessCallback;
import com.ucloudlink.refact.access.struct.LoginInfo;
import com.ucloudlink.refact.business.softsim.SeedNetworkStart;
import com.ucloudlink.refact.business.softsim.SoftsimEntry;
import com.ucloudlink.refact.business.softsim.StartSeedResult;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.PerformanceStatistics;
import com.ucloudlink.refact.utils.ProcessState;

import java.util.ArrayList;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by shiqianhua on 2016/10/17.
 */
public class AccessEntry {
    private final static String TAG = "AccessEntry";
    private AccessState  accessState;
    private LoginInfo    loginInfo;
    public  SoftsimEntry softsimEntry;
    public final Boolean isAccessStateLock = true;
    public AccessMonitor accessMonitor;

    public AccessEntry(Context ctx) {
        synchronized (isAccessStateLock) {
            softsimEntry = new SoftsimEntry(ctx, this);
            accessState = new AccessState(ctx, this, softsimEntry);
            accessMonitor = new AccessMonitor(ctx, this);
        }
    }

    public String getCurSessionId() {
        if(accessState == null){
            return "";
        }
        return accessState.getSessionId();
    }

    public String getCurImis() {
        return accessState.getImis();
    }

    public String getCurUserName() {
        return loginInfo.getUsername();
    }

    public void setLoginInfo(LoginInfo loginInfo) {
        this.loginInfo = loginInfo;
    }

    /**
     * 登录请求
     *
     * @param username
     * @param passwd
     */
    public void loginReq(String username, String passwd) {
        logd("loginReq:" + username);
        loginInfo = new LoginInfo(username, passwd);
        accessState.sendMessage(StateMessageId.USER_LOGIN_REQ_CMD, loginInfo);
    }

    //    public void loginRecovery(String username, String passwd){
    //        logd( "loginReq:"+ username );
    //        loginInfo = new LoginInfo(username, passwd);
    //        accessState.sendMessage(StateMessageId.USER_LOGIN_RECOVERY_CMD, loginInfo);
    //    }

    /**
     * 退登录请求
     *
     * @param module
     */
    public void logoutReq(int module) {
        logd("logout req!!!" + module);
        PerformanceStatistics.INSTANCE.setProcess(ProcessState.UI_RESET_REQ);
        //        if (Configuration.INSTANCE.getCurrentSystemVersion() == Configuration.ANDROID_COOL_C103) {
        //            logd("recoveryImei for ANDROID_COOL_C103");
        //            //clear imei
        //            VirtImeiHelper.INSTANCE.recoveryImei(ServiceManager.appContext);
        //        }
        accessState.logoutReq(module);
    }

    public void notifyEvent(int event) {
        notifyEvent(event, 0, 0, null);
    }

    public void notifyEvent(int event, Object o) {
        notifyEvent(event, 0, 0, o);
    }

    /**
     * 换卡请求，当前只做调试使用
     */
    public void switchVsimReq(int module) {
        logd("Switch vsim req!!! user--> " + module);
        accessState.sendMessage(StateMessageId.SWITCH_VSIM_MANAUL_CMD);
    }

    /**
     * 获取当前百分比
     *
     * @return
     */
    public int getSystemPersent() {
        return accessState.getSystemPersent();
    }

    /**
     * 通知事件到状态机
     *
     * @param event 事件ID 需要在AccessEventId文件中增加ID
     * @param arg1
     * @param arg2
     * @param o
     */
    public void notifyEvent(int event, int arg1, int arg2, Object o) {
        accessState.sendMessage(event, arg1, arg2, o);
    }

    public void registerCb(IUcloudAccessCallback cb) {
        if (cb == null) {
            JLog.loge(TAG, "registerCb: cb is null");
        }
        accessState.registerUnsoliCb(cb);
    }

    public void unregisterCb(IUcloudAccessCallback cb) {
        accessState.unregisterUnsoliCb(cb);
    }

    public LoginInfo getLoginInfo() {
        return loginInfo;
    }

    public void registerAccessStateListen(AccessState.AccessStateListen listen) {
        accessState.AccessStateListenReg(listen);
    }

    public void unregisterAccessStateListen(AccessState.AccessStateListen listen) {
        accessState.AccessStateListenUnreg(listen);
    }

    /**
     * 获取当前异常事件列表
     *
     * @return
     */
    public ArrayList<Integer> getExceptionArray() {
        return accessState.getExceptionArray();
    }

    public boolean isServiceRunning() {
        return accessState.isServiceRunning();
    }

    public boolean isInExceptionState() {
        return accessState.isInExceptionState();
    }

    public int getSeedPersent() {
        return accessState.getSeedPersent();
    }

    public boolean isInRecoveryState() {
        return accessState.isInRecoveryState();
    }

    public AccessState getAccessState() {
        return accessState;
    }

    /**
     * 启动种子卡网络, 服务没有启动的时候才会成功
     *
     * @param username
     * @param packageName 套餐名称
     * @param timeout     超时时间
     * @return 0 succ, others failed
     */
    public int startSeedNetwork(String username, String packageName, int timeout) {

        SeedNetworkStart.INSTANCE.starSeed(username, packageName, timeout).subscribe(new Action1<StartSeedResult>() {
            @Override
            public void call(StartSeedResult startSeedResult) {
                softsimEntry.updateStartSeedNetworkResult(startSeedResult.getOrderId(),startSeedResult.getErrorCode(),startSeedResult.getMsg());
            }
        });
        return 0;
    }

    /**
     * 关闭种子网络, 服务没有启动的时候才会成功
     *
     * @return
     */
    public int stopSeedNetwork() {
        SeedNetworkStart.INSTANCE.stopSeed();
        return 0;
    }

    /**
     * 获取当前百分比监听器
     *
     * @return
     */
    public Observable<Integer> getStatePersentOb() {
        return accessState.getStatePersentOb();
    }
}
