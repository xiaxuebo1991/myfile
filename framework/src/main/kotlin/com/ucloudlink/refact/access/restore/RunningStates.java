package com.ucloudlink.refact.access.restore;

import com.ucloudlink.refact.utils.EncryptUtils;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;
import com.ucloudlink.refact.business.log.TimeConver;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.business.routetable.ServerRouter;

/**
 * Created by chunjiao.li on 2016/12/30.
 */

public class RunningStates {
    /**
     * 做服务恢复的时候，disconnect remote sim之后，先要校验所有参数的有效性，才能发起登陆流程,否则正常退出服务。
     */

    //账号密码需要保存到安全区域----------------------使用安全区域保存--------------start
    //todo 时间也需要保存
    private final static String USER_NAME = "username";//String
    private final static String PASS_WORD = "password";//String
    private final static String ORDER_ID = "orderid";//String
    private final static String GLOCALME_WORD = "glocalme-service-11-23_A";
    //账号密码需要保存到安全区域----------------------使用安全区域保存--------------end

    //------------------------使用SharedPreferences保存到xml文件------------------start
    //状态更新时间 格式:"yyyyMMddHHmmssSSS"
    private final static String LATEST_STATE_UPDATETIME = "latest_state_update_time";//String

    private final static String PREFERENCE_NAME = "restore_record";
    private final static String RECORD_EXIST = "restore_record_exit";//boolean 恢复文件存在才能恢复

    private final static String APDU_MODE = "apdu_mode";
    private final static int APDU_MODE_SOFT = 0;
    private final static int APDU_MODE_PHY = 1;

    private final static String SEED_SIM_SLOT = "seed_sim_slot";
    private final static String CLOUD_SIM_SLOT = "cloud_sim_slot";
    private final static int SIM_SLOT_0 = 0;
    private final static int SIM_SLOT_1 = 1;

    private final static String ASS_SERVER_MODE = "ass_mode";
    //------------------------使用SharedPreferences保存到xml文件------------------end

    public static boolean isRecordExist() {
        return SharedPreferencesUtils.getBoolean(ServiceManager.appContext, PREFERENCE_NAME, RECORD_EXIST, false);
    }

    public static void saveRecordExist() {
        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, PREFERENCE_NAME, RECORD_EXIST, true);
    }

    public static int getApduMode() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME, APDU_MODE, APDU_MODE_SOFT);
    }

    public static void saveApduMode(int apduMode) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME, APDU_MODE, apduMode);
    }

    public static int getSeedSimSlot() {
        int slot = SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME, SEED_SIM_SLOT, -1);
        JLog.logd("getSeedSimSlot: " + slot);
        return slot;
    }

    public static void saveSeedSimSlot(int slot) {
        JLog.logd("saveSeedSimSlot: " + slot);
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME, SEED_SIM_SLOT, slot);
    }

    public static int getCloudSimSlot() {
        int slot = SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME, CLOUD_SIM_SLOT, -1);
        JLog.logd("getCloudSimSlot: " + slot);
        return slot;
    }

    public static void saveCloudSimSlot(int slot) {
        JLog.logd("saveCloudSimSlot: " + slot);
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME, CLOUD_SIM_SLOT, slot);
    }

    public static String getUserName() {
        return SharedPreferencesUtils.getString(ServiceManager.appContext, PREFERENCE_NAME, USER_NAME, "");
    }

    public static void saveUserName(String userName) {
        SharedPreferencesUtils.putString(ServiceManager.appContext, PREFERENCE_NAME, USER_NAME, userName);
    }

    public static String getPassWord() {

        String getValue = SharedPreferencesUtils.getString(ServiceManager.appContext, PREFERENCE_NAME, PASS_WORD, "");
        return EncryptUtils.decyption(ServiceManager.appContext,getValue,PREFERENCE_NAME,GLOCALME_WORD);
    }

    public static void savePassWord(String passWord) {
        SharedPreferencesUtils.putString(ServiceManager.appContext, PREFERENCE_NAME, PASS_WORD,
                EncryptUtils.encyption(ServiceManager.appContext,passWord,PREFERENCE_NAME,GLOCALME_WORD));
    }

    public static String getOrderId() {
        return SharedPreferencesUtils.getString(ServiceManager.appContext, PREFERENCE_NAME, ORDER_ID, "");
    }

    public static void saveOrderId(String orderId) {
        SharedPreferencesUtils.putString(ServiceManager.appContext, PREFERENCE_NAME, ORDER_ID, orderId);
    }

    public static int getAssServerMode() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME, ASS_SERVER_MODE, ServerRouter.BUSINESS);
    }

    public static void saveAssServerMode(int assServerMode) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME, ASS_SERVER_MODE, assServerMode);
    }

    /**
     * 设置数据最后更新的时间，xml文件
     */
    public static void saveStateUpdateTimeXml() {
        SharedPreferencesUtils.putString(ServiceManager.appContext, PREFERENCE_NAME, LATEST_STATE_UPDATETIME, TimeConver.getDateTimeNow());
    }

    /**
     * 获取数据最后更新的时间，xml文件
     */
    public static void getStateUpdateTimeXml() {
        SharedPreferencesUtils.getString(ServiceManager.appContext, PREFERENCE_NAME, LATEST_STATE_UPDATETIME, TimeConver.getDateTimeNow());
    }

    /**
     * 设置数据最后更新的时间，安全区域
     */
    public static void saveStateUpdateTimeSec() {
        // TODO: 2017/1/3 写入安全区域数据更新时间
    }

    /**
     * 获取数据最后更新的时间，安全区域
     */
    public static void getStateUpdateTimeSec() {
        // TODO: 2017/1/3 读取安全区域数据更新时间
    }
}
