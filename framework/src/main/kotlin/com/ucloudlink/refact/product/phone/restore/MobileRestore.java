package com.ucloudlink.refact.product.phone.restore;

import android.content.Context;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.restore.ServiceRestore;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * Created by chunjiao.li on 2017/1/4.
 */

public class MobileRestore {
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private TelecomManager mTelecomManager;
    private Context mContext;
    private boolean needRestroeDefChoice = false;
    private boolean twoPhySim = false;

    private boolean startRestore = false;
    private boolean restoreDone = false;

    public MobileRestore(Context context) {
        mTelephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        mSubscriptionManager = SubscriptionManager.from(context);
        mTelecomManager = TelecomManager.from(context);
        mContext = context;
    }

    //记录状态，恢复状态


    //卡槽有卡与否判断
    public void recordMobileUserSettings_nouse() {
        if (!ServiceRestore.isExceptionStart(mContext)||ServiceRestore.isReBoot()) {
            startRestore = false;

            //卡槽0是否有卡
            boolean isSlot0Sim = isSlotHasSim(MobileStates.SIM_SLOT_0);
            if (isSlot0Sim) {
                saveAllSettings(MobileStates.SIM_SLOT_0);
            } else {
                MobileStates.saveSlot0SimExist(false);
                logv("slot0 has no sim, so do not record slot0 state");
            }

            //卡槽1是否有卡
            boolean isSlot1Sim = isSlotHasSim(MobileStates.SIM_SLOT_1);
            if (isSlot1Sim) {
                saveAllSettings(MobileStates.SIM_SLOT_1);
            } else {
                MobileStates.saveSlot1SimExist(false);
                logv("slot1 has no sim, so do not record slot1 state");
            }

            if (isSlot0Sim && isSlot1Sim) {
                twoPhySim = true;
            } else {
                twoPhySim = false;
            }
        } else {
            logv("before start has exception crash, so do not record settings");
        }
    }

    public void recordMobileUserSettings(){
        //卡槽0是否有卡
        boolean isSlot0Sim = isSlotHasSim(MobileStates.SIM_SLOT_0);
        if (isSlot0Sim) {
            saveAllSettings(MobileStates.SIM_SLOT_0);
        } else {
            MobileStates.saveSlot0SimExist(false);
            logv("slot0 has no sim, so do not record slot0 state");
        }

        //卡槽1是否有卡
        boolean isSlot1Sim = isSlotHasSim(MobileStates.SIM_SLOT_1);
        if (isSlot1Sim) {
            saveAllSettings(MobileStates.SIM_SLOT_1);
        } else {
            MobileStates.saveSlot1SimExist(false);
            logv("slot1 has no sim, so do not record slot1 state");
        }

        if (isSlot0Sim && isSlot1Sim) {
            twoPhySim = true;
        } else {
            twoPhySim = false;
        }
    }

    //恢复的时候需要判断卡槽是否有卡，以及之前记录的状态是卡槽有卡还是无卡。
    public void restoreMobileUserSettings() {
        try {
            //记录卡槽有卡，且当前卡槽确实有卡，进行卡槽记录恢复，需要一些10s时间来等待。
            restoreDone = false;
            boolean slot0NeedRestore = isSlotHasSim(MobileStates.SIM_SLOT_0) && MobileStates.getSlot0SimExist();
            boolean slot1NeedRestore = isSlotHasSim(MobileStates.SIM_SLOT_1) && MobileStates.getSlot1SimExist();
            int waitingTime = 0;

            while (!(slot0NeedRestore && slot1NeedRestore) && twoPhySim && waitingTime < 18) {
                Thread.sleep(500);
                slot0NeedRestore = isSlotHasSim(MobileStates.SIM_SLOT_0) && MobileStates.getSlot0SimExist();
                slot1NeedRestore = isSlotHasSim(MobileStates.SIM_SLOT_1) && MobileStates.getSlot1SimExist();
                waitingTime++;
            }

            logv("slot0NeedRestore is exist " + slot0NeedRestore + ", slot1NeedRestore is exist " + slot1NeedRestore);

            if (slot0NeedRestore && slot1NeedRestore) {
                needRestroeDefChoice = true;
            }

            if (slot0NeedRestore) {
                restoreAllSettings(MobileStates.SIM_SLOT_0);
            } else {
                logv("slot0 has no sim, so do not need restore slot0 state");
            }

            if (slot1NeedRestore) {
                restoreAllSettings(MobileStates.SIM_SLOT_1);
            } else {
                logv("slot1 has no sim, so do not need restore slot1 state");
            }
        } catch (Exception e) {
            logd(e);
            logd("restoreMobileUserSettings, failed!");
        }

    }

    public Boolean checkeRestore() {
        try {
            return !isStartRestore() || isRestoreDone();
        } catch (Exception e) {
            logd(e);
            logd("checkeRestore, failed!");
            return false;
        }
    }

    private void saveAllSettings(int slotIndex) {
        try {
            SubscriptionInfo subscriptionInfo;
            int retryTime = 0;
            do {
                subscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex);
                Thread.sleep(800);
                retryTime++;
            } while (subscriptionInfo == null && retryTime <= 20);

            if (subscriptionInfo != null) {
                int subId = subscriptionInfo.getSubscriptionId();
                logd("saveAllSettings subscriptionInfo " + subscriptionInfo.toString());
                MobileStates.saveSlotSimExist(slotIndex, true);//卡槽有卡
                MobileStates.saveSlotSubId(slotIndex, subId);//卡槽对应的subId保存
                savePreferredNetworkType(slotIndex, subId);//保存首选网络
                saveDataEnabledState(slotIndex, subId);//保存数据开关
                saveNetRoamingState(slotIndex, subId);//保存漫游开关

                saveDefaultChoice();//保存默认数据，短信，通话卡的选择
            } else {
                MobileStates.saveSlotSimExist(slotIndex, false);//卡槽无卡
            }

        } catch (Exception e) {
            logd(e);
            logd("saveAllSettings for slot " + slotIndex + ",failed!");
        }
    }

    private void restoreAllSettings(int slotIndex) {
        try {
            SubscriptionInfo subscriptionInfo;
            int recordSubId = MobileStates.getSlotSubId(slotIndex);
            int retryTime = 0;
            do {
                subscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex);
                Thread.sleep(700);
                retryTime++;
            } while (subscriptionInfo == null && retryTime <= 20);

            if (subscriptionInfo != null) {
                Thread.sleep(1500);
                int subId = subscriptionInfo.getSubscriptionId();

                if (recordSubId == subId) {
                    logd("restoreAllSettings for slot " + slotIndex + ",subId " + subId);

                    restoreDefaultChoice(slotIndex, subId);//恢复默认数据，短信，通话卡的选择

                    restoreDataEnabledState(slotIndex, subId);//恢复数据开关
                    restorePreferredNetworkType(slotIndex, subId);//恢复首选网络
                    restoreNetRoamingState(slotIndex, subId);//恢复漫游开关
                } else {
                    logd("restoreAllSettings for slot " + slotIndex + ",may be this slot have change sim card!");
                }
            } else {
                MobileStates.saveSlotSimExist(slotIndex, false);//卡槽无卡
            }

        } catch (Exception e) {
            logd(e);
            logd("restoreAllSettings for slot " + slotIndex + ",failed!");
        }
    }

    /**
     * 判断卡槽中是否有卡
     *
     * @param slotIndex
     * @return
     */
    private boolean isSlotHasSim(int slotIndex) {
        int State = mTelephonyManager.getSimState(slotIndex);

        if (State <= TelephonyManager.SIM_STATE_ABSENT) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 保存首选网络类型
     *
     * @param slotIndex
     */
    private void savePreferredNetworkType(int slotIndex, int subId) {
        try {
//            int preferNetType = mTelephonyManager.getPreferredNetworkType(subId);
            int preferNetType = TelephonyManager.getIntWithSubId(ServiceManager.appContext.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, subId);
            MobileStates.saveSlotPreNetType(slotIndex, preferNetType);
            logv("savePreferredNetworkType slotIndex:" + slotIndex + ",subid:" + subId + ",preferNetType:" + preferNetType);
        } catch (Exception e) {
            logd(e);
            logd("PreferredNetworkType save for slot " + slotIndex + ",failed!");
        }
    }

    private void restorePreferredNetworkType(int slotIndex, int subId) {
        try {
            int preferNetType = MobileStates.getSlotPreNetType(slotIndex);
            boolean setPreNetRet;
            int retrySetPrefer = 0;
            int afterSetPrefer = 0;
            int ret = 0;
            if (preferNetType < 0) {
                setPreNetRet = ServiceManager.systemApi.setPreferredNetworkTypeToGlobal();
                ret = setPreNetRet ? 0 : -1;
            } else {
                do {
                    ret = ServiceManager.systemApi.setPreferredNetworkType(slotIndex, subId, preferNetType);
                    afterSetPrefer = TelephonyManager.getIntWithSubId(ServiceManager.appContext.getContentResolver(),
                            Settings.Global.PREFERRED_NETWORK_MODE, subId);
                    Thread.sleep(500);
                    retrySetPrefer++;
                } while ((preferNetType != afterSetPrefer) && retrySetPrefer < 12);
            }
            logv("restorePreferredNetworkType setPreNetRet: " + ret + ",slotIndex:"
                    + slotIndex + ",subid:" + subId + ",networkType:" + preferNetType + ",afterSetPrefer:" + afterSetPrefer);
        } catch (Exception e) {
            logd(e);
            logd("restorePreferredNetworkType for slot " + slotIndex + ",failed!");
        }
    }

    private void saveDataEnabledState(int slotIndex, int subId) {
        try {
            boolean isDataEnabled = mTelephonyManager.getDataEnabled(subId);
            MobileStates.saveSlotDataEnabled(slotIndex, isDataEnabled);
            logv("saveDataEnabledState slotIndex:" + slotIndex + ",subid:" + subId + ",isDataEnabled:" + isDataEnabled);
        } catch (Exception e) {
            logd(e);
            logd("saveDataEnabledState for slot:" + slotIndex + ",failed!");
        }
    }

    private void restoreDataEnabledState(int slotIndex, int subId) {
        try {
            boolean isDataEnabled = MobileStates.getSlotDataEnabled(slotIndex);
            ServiceManager.systemApi.setDataEnabled(slotIndex, subId, isDataEnabled);
            logv("restoreDataEnabledState slotIndex:" + slotIndex + ",subid:" + subId + ",isDataEnabled:" + isDataEnabled);
        } catch (Exception e) {
            logd(e);
            logd("restoreDataEnabledState for slot:" + slotIndex + ",failed!");
        }
    }

    private void saveNetRoamingState(int slotIndex, int subId) {
        try {
            int dataRoaming = TelephonyManager.getIntWithSubId(ServiceManager.appContext.getContentResolver(), Settings.Global.DATA_ROAMING, subId);
            MobileStates.saveSlotNetRoaming(slotIndex, dataRoaming);

            logv("saveNetRoamingState slotIndex:" + slotIndex + ",subid:" + subId + ",dataroaming:" + dataRoaming);
        } catch (Exception e) {
            logd(e);
            logd("saveNetRoamingState for slot" + slotIndex + ",failed!");
        }
    }

    private void restoreNetRoamingState(int slotIndex, int subId) {
        try {
            int dataRoaming = MobileStates.getSlotNetRoaming(slotIndex);
            Settings.Global.putInt(ServiceManager.appContext.getContentResolver(), Settings.Global.DATA_ROAMING + subId, dataRoaming);

            int setDataRoamingret = ServiceManager.systemApi.setDataRoaming(subId, (dataRoaming == 1));

            logv("setDataRoamingret:" + setDataRoamingret);

            logv("restoreNetRoamingState slotIndex:" + slotIndex + ",subid:" + subId + ",dataRoaming:" + dataRoaming);
        } catch (Exception e) {
            logd(e);
            logd("restoreNetRoamingState for slot" + slotIndex + ",failed!");
        }
    }

    private void saveDefaultChoice() {
        try {
            int defDataSlot = mSubscriptionManager.getDefaultDataPhoneId();
            int defVoiceSlot = SubscriptionManager.getDefaultVoicePhoneId();
            int defSmsSlot = mSubscriptionManager.getDefaultSmsPhoneId();

            MobileStates.saveSlotOfDdp(defDataSlot);
            MobileStates.saveSlotOfDvp(defVoiceSlot);
            MobileStates.saveSlotOfDsp(defSmsSlot);

            logv("saveDefaultChoice defDataSlot:" + defDataSlot + ",defVoiceSlot:" + defVoiceSlot + ",defSmsSlot:" + defSmsSlot);
        } catch (Exception e) {
            logd(e);
            logd("saveDefaultChoice, failed!");
        }
    }

    private void restoreDefaultChoice(int slotIndex, int subId) {
        try {
            if (!needRestroeDefChoice) {//如果只有一张卡就没有恢复的必要了
                return;
            }

            int defDataSlot = MobileStates.getSlotOfDdp();
            int defVoiceSlot = MobileStates.getSlotOfDvp();
            int defSmsSlot = MobileStates.getSlotOfDsp();

            if (defDataSlot == slotIndex) {
                setDefaultDataSimcard(slotIndex, subId);
            }

//            /*
            if (defVoiceSlot == slotIndex) {
                setDefaultVoiceSlotId(slotIndex);
            }

            if (defSmsSlot == slotIndex) {
                setDefaultSmsSimcard(slotIndex, subId);
            }

            logv("restoreDefaultChoice defDataSlot:" + defDataSlot + ",defVoiceSlot:" + defVoiceSlot + ",defSmsSlot:" + defSmsSlot);
//            */

//            logv("restoreDefaultChoice defDataSlot:" + defDataSlot);
        } catch (Exception e) {
            logd(e);
            logd("restoreDefaultChoice, failed!");
        }
    }

    public void setDefaultDataSimcard(int slotIndex, int subId) {
        try {
            logd("set dds to subid " + subId);
            ServiceManager.systemApi.setDefaultDataSubId(subId);
            ServiceManager.systemApi.saveDefaultDataSubId(subId, slotIndex);
        } catch (Exception e) {
            logd(e);
            logd("setDefaultDataSimcard, failed Exception!");
        }
    }

    public void setDefaultSmsSimcard(int slotIndex, int subId) {
        try {
            ServiceManager.systemApi.setDefaultSmsSubId(subId, slotIndex);
        } catch (Exception e) {
            logd(e);
            logd("setDefaultSmsSimcard, failed Exception!");
        }
    }

    /**
     * 设置默认通话卡
     * @param slotIndex
     */
    public void setDefaultVoiceSlotId(int slotIndex) {
        try {
            ServiceManager.systemApi.setVoiceSlotId(slotIndex);
        } catch (Exception e) {
            logd(e);
            logd("setDefaultVoiceSlotId, failed Exception!");
        }
    }

    public boolean isStartRestore() {
        return startRestore;
    }

    public void setStartRestore(boolean startRestore) {
        this.startRestore = startRestore;
    }

    public boolean isRestoreDone() {
        return restoreDone;
    }

    public void setRestoreDone(boolean restoreDone) {
        this.restoreDone = restoreDone;
    }
}
