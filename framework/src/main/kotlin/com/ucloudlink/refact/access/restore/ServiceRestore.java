package com.ucloudlink.refact.access.restore;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.TimeoutValue;
import com.ucloudlink.refact.access.struct.LoginInfo;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.channel.enabler.datas.CardType;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;

import static com.ucloudlink.refact.config.UConstantKt.CLOUDSIM_IMSI_STATE_SLOT;
import static com.ucloudlink.refact.config.UConstantKt.SYSTEM_SP_KEY_LAST_SESSION_AND_TIME;
import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.loge;
import static com.ucloudlink.refact.utils.JLog.logi;
import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * Created by chunjiao.li on 2017/1/4.
 */

public class ServiceRestore {

    public static final int RECOVERY_MODE_CLEAR_ENV    = 0;
    public static final int RECOVERY_MODE_NORMAL       = 1;
    public static final int RECOVERY_MODE_SMART        = 2;
    public static final int RECOVERY_MODE_NOTHING_EXIT = 3;

    //是否异常启动判断----------------------使用临时prop属性记录--------------start
    private final static String MARK_BOOT            = "ril.radio.ucapprun.state";
    private final static String BOOT_MARK_NOT_REBOOT = "1002";
    private final static String BOOT_MARK_DEFAULT    = "1001";
    private final static String SP_MARK_LOGIN        = "MARK_LOGIN";
    private final static String SP_MARK_LOGOUT       = "MARK_LOGOUT";
    private final static String NORMAL_START         = "isNormalStart";

    /**
     * 是否异常启动
     *
     * @return true:是异常启动，false:正常启动
     */
    public static boolean isExceptionStart(Context ctx) {
        //        String isNormal = SystemProperties.get(MARK_BOOT, BOOT_MARK_DEFAULT);
        boolean isLogin = SharedPreferencesUtils.getBoolean(SP_MARK_LOGIN, false);
        return isLogin;
    }

    public static int getRecoverMode(Context ctx) {
        ServiceRestore.recoverCSim();

        int mode = RECOVERY_MODE_CLEAR_ENV;

        if (!ServiceRestore.isReBoot()) {
            if (ServiceManager.systemApi.isSupportSmartRecovery()) {
                mode = getRecoveryModeIfNotReboot(ctx);
            } else {
                mode = RECOVERY_MODE_CLEAR_ENV;
            }
        } else if (Configuration.INSTANCE.getRECOVE_WHEN_REBOOT() || Configuration.INSTANCE.getAUTO_WHEN_REBOOT()) {
            //已经
            mode = RECOVERY_MODE_NORMAL;
        } else {
            mode = RECOVERY_MODE_NORMAL;
        }

        if (mode != RECOVERY_MODE_SMART) {
            clearSettingsInfo();
        }

        return mode;
    }

    private static void clearSettingsInfo() {
        logd("[clearSettingsInfo]");
        try {
            Uri uri = Uri.parse("content://vsimcore.setting");
            ServiceManager.appContext.getContentResolver().call(uri, "clearCloudSimInfo", null, null);
        } catch (Exception e) {
            loge("clearCloudSimInfo fail " + e.getMessage());
        }
    }

    private static int getRecoveryModeIfNotReboot(Context context) {
        //这里做检查是否要回复云卡
        //Cloudsim_imsi_state_slot:imsi_state[on/off]_slot  ,loaded 之后才会是on
        String cloudsim_imsi_current_state = SharedPreferencesUtils.getString(CardType.VSIM + CLOUDSIM_IMSI_STATE_SLOT);
        if (!TextUtils.isEmpty(cloudsim_imsi_current_state)) {
            String[] strings = cloudsim_imsi_current_state.split("_");
            if (strings.length == 3) {
                String cloudsimImsi = strings[0];
                String state = strings[1];
                int slot = -1;
                try {
                    slot = Integer.parseInt(strings[2]);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(cloudsimImsi) && "on".equals(state) && slot != -1) {
                    //云卡可能在线，判断当前所在是否就是云卡
                    String virtual_sim_imsi = Settings.Global.getString(context.getContentResolver(), "virtual_sim_imsi");
                    if (!TextUtils.isEmpty(virtual_sim_imsi) && !virtual_sim_imsi.equals(cloudsimImsi)) {
                        //表示小米虚卡起来了，不再自动恢复
                        //返回exit
                        return RECOVERY_MODE_NOTHING_EXIT;
                    }
                    logd("getRecoveryModeIfNotReboot: cloudsimImsi:" + cloudsimImsi + " state:" + state + " slot:" + slot);
                    SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    SubscriptionInfo subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot);

                    if (subscriptionInfo == null) {
                        return RECOVERY_MODE_CLEAR_ENV;
                    }

                    int subId = subscriptionInfo.getSubscriptionId();

                    if (subId > -1) {
                        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                        String imsi = telephonyManager.getSubscriberId(subId);
                        if (TextUtils.isEmpty(imsi)) {
                            //imsi 是空的，可能云卡在未加载完成时崩溃
                            //使用CLEAR_MODE
                            logi("getRecoveryModeIfNotReboot: slot(" + slot + ") imsi is empty ");
                            return RECOVERY_MODE_CLEAR_ENV;

                        } else {
                            if (imsi.equals(cloudsimImsi)) {
                                //当前卡就是之前的云卡，使用smart_recovery
                                //判断session 是否存在或者超时
                                String session_time = SharedPreferencesUtils.getString(SYSTEM_SP_KEY_LAST_SESSION_AND_TIME);
                                if (TextUtils.isEmpty(session_time)) {
                                    logi("getRecoveryModeIfNotReboot: session time is empty ");
                                    return RECOVERY_MODE_CLEAR_ENV;
                                } else {
                                    try {
                                        String[] strings1 = session_time.split("_");
                                        long time = Long.parseLong(strings1[1]);
                                        long runnedTime = SystemClock.elapsedRealtime() - time;
                                        logv("check runned time :" + runnedTime);
                                        if (runnedTime > 0 && runnedTime < TimeoutValue.getHeartbeatMaxTimeout() * 1000 - 60000) {
                                            return RECOVERY_MODE_SMART;
                                        } else {
                                            logi("getRecoveryModeIfNotReboot: session is timeout ");
                                            return RECOVERY_MODE_CLEAR_ENV;
                                        }
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                        return RECOVERY_MODE_CLEAR_ENV;
                                    }

                                }
                            } else {
                                //当前卡不是之前云卡 使用clear_recovery
                                logi("getRecoveryModeIfNotReboot: current card is not old cloud sim ");
                                return RECOVERY_MODE_CLEAR_ENV;
                            }
                        }
                    } else {
                        /*
                         * 读不到当前卡槽的数据，表示卡槽可能为空，可能还没加载完
                         *
                         * 使用CLEAR_MODE 恢复模式
                         * */
                        logi("getRecoveryModeIfNotReboot: can not read current card data ");
                        return RECOVERY_MODE_CLEAR_ENV;
                    }

                }
            }
        }

        return RECOVERY_MODE_CLEAR_ENV;
    }

    public static boolean isReBoot() {
        //        测试,暂时去掉是否重启判断
        //        String isReboot = SystemProperties.get(MARK_BOOT, BOOT_MARK_DEFAULT);
        //        return isReboot.equals(BOOT_MARK_DEFAULT);
        return false;
    }

    // allow: 1 允许，0 禁止
    private static void setAutoStart(Context ctx, int allow) {
//        logd("setAutoStart: allow: " + allow);
//        try {
//            Bundle bundle = new Bundle();
//            bundle.putInt("allow", allow);
//            Uri uri = Uri.parse("content://com.miui.virtualsim.provider.virtualsimInfo");
//            ctx.getContentResolver().call(uri, "setAutoStart", null, bundle);
//            logd("setAutoStart: finish1");
//        } catch (Exception e) {
//            loge("fail to setAutoStart");
//        }
//        logd("setAutoStart: finish2");
    }

    /**
     * 登录启动时候设置
     *
     * @param ctx
     */
    public static void setStartServiceFlag(Context ctx) {
        logv("setStartServiceFlag set abnormal");
        //        测试,暂时去掉是否重启判断
        //        SystemProperties.set(MARK_BOOT, BOOT_MARK_NOT_REBOOT);
        setAutoStart(ctx, 1);
        SharedPreferencesUtils.putBoolean(ctx, SP_MARK_LOGIN, true);
    }

    /**
     * 登出正常停止时设置
     *
     * @param ctx
     */
    public static void setStopServiceFlag(Context ctx) {
        logv("setStopServiceFlag set normal");
        //        SystemProperties.set(MARK_BOOT, BOOT_MARK_DEFAULT);
        setAutoStart(ctx, 0);
        SharedPreferencesUtils.putBoolean(ctx, SP_MARK_LOGIN, false);
    }

    /**
     * 设置正常启动标志
     **/
    public static void setNormalStartFlag(Context ctx) {
        SharedPreferencesUtils.putBoolean(ctx, NORMAL_START, true);
    }

    /**
     * 正常登陆时停止设置
     **/
    public static void stopNormalStartFlag(Context ctx) {
        SharedPreferencesUtils.putBoolean(ctx, NORMAL_START, false);
    }

    /**
     * 获取异常启动标志
     **/
    public static boolean getExceptionStartFlag() {
        boolean flag = SharedPreferencesUtils.getBoolean(NORMAL_START, false);
        return flag;
    }

    public static void setLogoutFlag(Context ctx) {
        logv("setLogoutFlag --------");
        SharedPreferencesUtils.putBoolean(ctx, SP_MARK_LOGOUT, true);
    }

    public static void clearLogoutFlag(Context ctx) {
        logv("clearLogoutFlag --------");
        SharedPreferencesUtils.putBoolean(ctx, SP_MARK_LOGOUT, false);
    }

    public static boolean getLogoutFlag(Context ctx) {
        Boolean isLogout = SharedPreferencesUtils.getBoolean(ctx, SP_MARK_LOGOUT);
        logv("getLogoutFlag --------" + isLogout);
        return isLogout;
    }

    //是否异常启动判断----------------------使用prop属性记录--------------end

    //保存登陆参数
    public static void recordLoginParams(LoginInfo loginInfo) {
        logv("recordLoginParams");
        RunningStates.saveUserName(loginInfo.getUsername());
        RunningStates.savePassWord(loginInfo.getPasswd());
        RunningStates.saveApduMode(Configuration.INSTANCE.getApduMode());
        RunningStates.saveSeedSimSlot(Configuration.INSTANCE.getSeedSimSlot());
        RunningStates.saveCloudSimSlot(Configuration.INSTANCE.getCloudSimSlot());
        RunningStates.saveAssServerMode(ServerRouter.INSTANCE.getCurrent_mode());
        RunningStates.saveOrderId(Configuration.INSTANCE.getOrderId());
        RunningStates.saveStateUpdateTimeXml();
        RunningStates.saveRecordExist();
    }

    //    public static void restoreLoginParams(Context ctx) {
    //        if (ServiceRestore.isExceptionStart(ctx) && RunningStates.isRecordExist()) {
    //            if (!ServiceRestore.isReBoot()) {
    //                logv("abnormal setup restoreLoginParams and auto login!");
    //                CardController.INSTANCE.disconnectAllSim();//disconnect two remote sim
    //                recoverCSim();
    //            } else {
    //                if (Configuration.INSTANCE.getRECOVE_WHEN_REBOOT()) {
    //                    recoverCSim();
    //                }else {
    //                    logv("end with exception last boot and give recover");
    //                    ServiceRestore.setStopServiceFlag(ctx);
    //                }
    //            }
    //        } else {
    //            logv("restoreLoginParams normal start, do not auto restore login!");
    //            ServiceRestore.setStopServiceFlag(ctx);
    //        }
    //    }

    public static void recoverCSim() {
        Configuration.INSTANCE.setUsername(RunningStates.getUserName());
        Configuration.INSTANCE.setApduMode(RunningStates.getApduMode());
        Configuration.INSTANCE.setSeedSimSlot(Configuration.INSTANCE.getSeedSimSlot());
        Configuration.INSTANCE.setCloudSimSlot(Configuration.INSTANCE.getCloudSimSlot());
        ServerRouter.INSTANCE.setIpMode(RunningStates.getAssServerMode());
        Configuration.INSTANCE.setOrderId(RunningStates.getOrderId());
    }

    public static String getModeStr(int mode) {
        switch (mode) {
            case RECOVERY_MODE_CLEAR_ENV:
                return "RECOVERY_MODE_CLEAR_ENV";
            case RECOVERY_MODE_NORMAL:
                return "RECOVERY_MODE_NORMAL";
            case RECOVERY_MODE_SMART:
                return "RECOVERY_MODE_SMART";
            case RECOVERY_MODE_NOTHING_EXIT:
                return "RECOVERY_MODE_NOTHING_EXIT";
        }
        return "RECOVERY_MODE_UNKNOW";
    }

    //    public static void recoverLogin(){
    //        //crash恢复参数后，重新发起登陆
    //        ServiceManager.INSTANCE.getAccessEntry().loginRecovery(RunningStates.getUserName(), RunningStates.getPassWord());
    //    }
}
