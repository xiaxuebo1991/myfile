package com.ucloudlink.refact.product.phone.restore;

import com.android.internal.telephony.RILConstants;
import com.ucloudlink.refact.utils.SharedPreferencesUtils;
import com.ucloudlink.refact.ServiceManager;

/**
 * Created by chunjiao.li on 2017/1/3.
 */

public class MobileStates {
    /**
     * 手机状态保存，分卡槽1，卡槽2进行保存。
     * 保存和恢复之前首先应该看卡槽是否有卡，然后才能对每个卡槽的卡的状态进行保存和恢复。
     */
    private final static String PREFERENCE_NAME = "restore_mobset";
    public final static String SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub";
    public final static int SIM_SLOT_0 = 0;
    public final static int SIM_SLOT_1 = 1;

    //卡槽位是否有插卡,boolean值
    private final static String SLOT0_SIM_EXIST = "slot0_simExist";
    private final static String SLOT1_SIM_EXIST = "slot1_simExist";

    //subid记录,int值
    private final static String SLOT0_SUBID = "slot0_subid";
    private final static String SLOT1_SUBID = "slot1_subid";

    //首选网络,制式,int值
    private final static String SLOT0_PRE_NET_TYPE = "slot0_preferredNetworkType";
    private final static String SLOT1_PRE_NET_TYPE = "slot1_preferredNetworkType";

    //数据开关是否打开,boolean值
    private final static String SLOT0_DATA_ENABLED = "slot0_dataEnabled";
    private final static String SLOT1_DATA_ENABLED = "slot1_dataEnabled";

    //漫游开关,isNetworkRoaming,boolean值
    private final static String SLOT0_NET_ROAMING = "slot0_networkRoaming";
    private final static String SLOT1_NET_ROAMING = "slot1_networkRoaming";

    //数据卡槽位置 DefaultDataPhoneId,int值
    private final static String SLOT_OF_DDP = "slot_defData";

    //电话卡槽位置 DefaultVoicePhoneId,int值
    private final static String SLOT_OF_DVP = "slot_defVoice";

    //短息卡槽位置 DefaultSmsPhoneId,int值
    private final static String SLOT_OF_DSP = "slot_defSms";

    //后期考虑，特殊版本的搜网开关，用户apn，手机imei的恢复//

    public static boolean getSlot0SimExist() {
        return SharedPreferencesUtils.getBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_SIM_EXIST, false);
    }

    public static void saveSlot0SimExist(boolean isExist) {
        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_SIM_EXIST, isExist);
    }

    public static boolean getSlot1SimExist() {
        return SharedPreferencesUtils.getBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_SIM_EXIST, false);
    }

    public static void saveSlot1SimExist(boolean isExist) {
        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_SIM_EXIST, isExist);
    }

    public static boolean getSlotSimExist(int slotIndex) {
        if (slotIndex == SIM_SLOT_0) {
            return getSlot0SimExist();
        } else {
            return getSlot1SimExist();
        }
    }

    public static void saveSlotSimExist(int slotIndex, boolean isExist) {
        if (slotIndex == SIM_SLOT_0) {
            saveSlot0SimExist(isExist);
        } else if (slotIndex == SIM_SLOT_1) {
            saveSlot1SimExist(isExist);
        }
    }

    public static int getSlot0SubId() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_SUBID, -1);
    }

    public static void saveSlot0SubId(int subId) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_SUBID, subId);
    }

    public static int getSlot1SubId() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_SUBID, -1);
    }

    public static void saveSlot1SubId(int subId) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_SUBID, subId);
    }

    public static int getSlotSubId(int slotIndex) {
        if (slotIndex == SIM_SLOT_0) {
            return getSlot0SubId();
        } else {
            return getSlot1SubId();
        }
    }

    public static void saveSlotSubId(int slotIndex, int subId) {
        if (slotIndex == SIM_SLOT_0) {
            saveSlot0SubId(subId);
        } else if (slotIndex == SIM_SLOT_1) {
            saveSlot1SubId(subId);
        }
    }

    public static int getSlot0PreNetType() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_PRE_NET_TYPE, RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
    }

    public static void saveSlot0PreNetType(int preNetType) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_PRE_NET_TYPE, preNetType);
    }

    public static int getSlot1PreNetType() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_PRE_NET_TYPE, RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
    }

    public static void saveSlot1PreNetType(int preNetType) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_PRE_NET_TYPE, preNetType);
    }

    public static void saveSlotPreNetType(int slotIndex, int preNetType) {
        if (slotIndex == SIM_SLOT_0) {
            saveSlot0PreNetType(preNetType);
        } else if (slotIndex == SIM_SLOT_1) {
            saveSlot1PreNetType(preNetType);
        }
    }

    public static int getSlotPreNetType(int slotIndex) {
        if (slotIndex == SIM_SLOT_0) {
            return getSlot0PreNetType();
        } else {
            return getSlot1PreNetType();
        }
    }

    public static boolean getSlot0DataEnabled() {
        return SharedPreferencesUtils.getBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_DATA_ENABLED, false);
    }

    public static void saveSlot0DataEnabled(boolean isDataEnable) {
        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_DATA_ENABLED, isDataEnable);
    }

    public static boolean getSlot1DataEnabled() {
        return SharedPreferencesUtils.getBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_DATA_ENABLED, false);
    }

    public static void saveSlot1DataEnabled(boolean isDataEnable) {
        SharedPreferencesUtils.putBoolean(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_DATA_ENABLED, isDataEnable);
    }

    public static boolean getSlotDataEnabled(int slotIndex) {
        if (slotIndex == SIM_SLOT_0) {
            return getSlot0DataEnabled();
        } else {
            return getSlot1DataEnabled();
        }
    }

    public static void saveSlotDataEnabled(int slotIndex, boolean isDataEnable) {
        if (slotIndex == SIM_SLOT_0) {
            saveSlot0DataEnabled(isDataEnable);
        } else if (slotIndex == SIM_SLOT_1) {
            saveSlot1DataEnabled(isDataEnable);
        }
    }

    public static int getSlot0NetRoaming() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_NET_ROAMING, 0);
    }

    public static void saveSlot0NetRoaming(int dataRoaming) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT0_NET_ROAMING, dataRoaming);
    }

    public static int getSlot1NetRoaming() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_NET_ROAMING, 0);
    }

    public static void saveSlot1NetRoaming(int dataRoaming) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT1_NET_ROAMING, dataRoaming);
    }

    public static int getSlotNetRoaming(int slotIndex) {
        if (slotIndex == SIM_SLOT_0) {
            return getSlot0NetRoaming();
        } else {
            return getSlot1NetRoaming();
        }
    }


    public static void saveSlotNetRoaming(int slotIndex, int dataroaming) {
        if (slotIndex == SIM_SLOT_0) {
            saveSlot0NetRoaming(dataroaming);
        } else if (slotIndex == SIM_SLOT_1) {
            saveSlot1NetRoaming(dataroaming);
        }
    }

    public static int getSlotOfDdp() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT_OF_DDP, SIM_SLOT_0);
    }

    public static void saveSlotOfDdp(int phoneId) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT_OF_DDP, phoneId);
    }

    public static int getSlotOfDvp() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT_OF_DVP, SIM_SLOT_0);
    }

    public static void saveSlotOfDvp(int phoneId) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT_OF_DVP, phoneId);
    }

    public static int getSlotOfDsp() {
        return SharedPreferencesUtils.getInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT_OF_DSP, SIM_SLOT_0);
    }

    public static void saveSlotOfDsp(int phoneId) {
        SharedPreferencesUtils.putInt(ServiceManager.appContext, PREFERENCE_NAME,
                SLOT_OF_DSP, phoneId);
    }

}
