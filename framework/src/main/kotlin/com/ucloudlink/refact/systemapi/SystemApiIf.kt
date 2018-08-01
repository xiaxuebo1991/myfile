package com.ucloudlink.refact.systemapi

import android.content.Context
import android.net.ConnectivityManager
import android.telephony.ServiceState
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.systemapi.interfaces.Platforms
import com.ucloudlink.refact.systemapi.interfaces.PlatformsConfig
import com.ucloudlink.refact.systemapi.interfaces.Product
import com.ucloudlink.refact.systemapi.struct.FileWapper
import java.net.InetAddress
import java.util.*

/**
 * Created by shiqianhua on 2018/1/6.
 */
interface SystemApiIf : Platforms, Product, PlatformsConfig {

    /**
     * 是否在调试模式下，用于在调试模式下临时区分调试逻辑
     * Base的默认值为false；需要调试的设备类型为true
     */
    fun isUnderDevelopMode(): Boolean

    /**
     * 是否使用NativeAPI
     */
    fun isUseKeyNativeApi(): Boolean

    fun getDefaultDataSubId(): Int
    fun setDefaultDataSubId(subId: Int): Int
    fun saveDefaultDataSubId(subId: Int, slot: Int): Int
    fun setDefaultSmsSubId(subId: Int, slot: Int): Int
    fun setVoiceSlotId(slot: Int): Int
    fun setDataRoaming(subId: Int, roam: Boolean): Int


    /*
    networkType 定义如下：
    int NETWORK_MODE_WCDMA_PREF     = 0; /* GSM/WCDMA (WCDMA preferred) */
        int NETWORK_MODE_GSM_ONLY       = 1; /* GSM only */
        int NETWORK_MODE_WCDMA_ONLY     = 2; /* WCDMA only */
        int NETWORK_MODE_GSM_UMTS       = 3; /* GSM/WCDMA (auto mode, according to PRL)
        AVAILABLE Application Settings menu*/
        int NETWORK_MODE_CDMA           = 4; /* CDMA and EvDo (auto mode, according to PRL)
        AVAILABLE Application Settings menu*/
        int NETWORK_MODE_CDMA_NO_EVDO   = 5; /* CDMA only */
        int NETWORK_MODE_EVDO_NO_CDMA   = 6; /* EvDo only */
        int NETWORK_MODE_GLOBAL         = 7; /* GSM/WCDMA, CDMA, and EvDo (auto mode, according to PRL)
        AVAILABLE Application Settings menu*/
        int NETWORK_MODE_LTE_CDMA_EVDO  = 8; /* LTE, CDMA and EvDo */
        int NETWORK_MODE_LTE_GSM_WCDMA  = 9; /* LTE, GSM/WCDMA */
        int NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA = 10; /* LTE, CDMA, EvDo, GSM/WCDMA */
        int NETWORK_MODE_LTE_ONLY       = 11; /* LTE Only mode. */
        int NETWORK_MODE_LTE_WCDMA      = 12; /* LTE/WCDMA */
        int NETWORK_MODE_TDSCDMA_ONLY            = 13; /* TD-SCDMA only */
        int NETWORK_MODE_TDSCDMA_WCDMA           = 14; /* TD-SCDMA and WCDMA */
        int NETWORK_MODE_LTE_TDSCDMA             = 15; /* TD-SCDMA and LTE */
        int NETWORK_MODE_TDSCDMA_GSM             = 16; /* TD-SCDMA and GSM */
        int NETWORK_MODE_LTE_TDSCDMA_GSM         = 17; /* TD-SCDMA,GSM and LTE */
        int NETWORK_MODE_TDSCDMA_GSM_WCDMA       = 18; /* TD-SCDMA, GSM/WCDMA */
        int NETWORK_MODE_LTE_TDSCDMA_WCDMA       = 19; /* TD-SCDMA, WCDMA and LTE */
        int NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA   = 20; /* TD-SCDMA, GSM/WCDMA and LTE */
        int NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA  = 21; /*TD-SCDMA,EvDo,CDMA,GSM/WCDMA*/
        int NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA = 22; /* TD-SCDMA/LTE/GSM/WCDMA, CDMA, and EvDo */
    return 0 succ
     return -1 fail
     */
    fun setPreferredNetworkType(slot: Int, subId: Int, networkType: Int): Int
    fun resetPreferredNetworkType(slot: Int): Int

    fun setDefaultDataSlotId(slot: Int): Int
    fun setPreferredNetworkTypeToGlobal(): Boolean
    fun setDataEnabled(slot: Int, subId: Int, isDataEnabled: Boolean)

    fun wirteVirtImei(context: Context, slot: Int, virtualImei: String): Int
    fun recoveryImei(context: Context, slot: Int): Int


    fun getDeniedReasonByState(phoneId:Int, state: ServiceState): Int
    fun geDeniedReasonTime(phoneId: Int): Long
    fun isNewReason(phoneId: Int, time: Long): Boolean
    /* unit: ms */
    fun getNetTypeDelayTime(): Long

    /**
     *
     *      private val PROVISIONED = 1//卡处于启用状态
    private val NOT_PROVISIONED = 0//卡处于禁用状态
    private val INVALID_STATE = -1
    private val CARD_NOT_PRESENT = -2
     * 获取SIM卡是否可用
     * 判断用户是否手动关闭了卡
     *
     * @return 1 已激活 0 表示未激活  -2
     *
     */
    fun getSimEnableState(slot: Int): Int

    /**
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     * @see #SIM_STATE_NOT_READY
     * @see #SIM_STATE_PERM_DISABLED
     * @see #SIM_STATE_CARD_IO_ERROR
     */
    fun getSimState(slot: Int):Int

    fun getNetworkOperatorForSubscription(subId: Int): String
    fun getIccOperatorNumericForData(subId: Int): String
    fun getServiceStateForSubscriber(subId: Int): ServiceState?

    // imsi
    fun getSubscriberIdBySlot(slot:Int):String
    fun getSubIdBySlotId(slot: Int):Int
    fun getNetworkOperator(slot: Int):String

    fun isFrameworkSupportSeedNetworkLimitByUidAndIp(): Boolean

    // platform configure
    fun getModemLogEnable(): Boolean

    fun getTcpdumpEnable(): Boolean
    fun getExtLibs(): ArrayList<FileWapper>
    fun getSimCommFiles(): ArrayList<FileWapper>
    fun getModemCfgFiles(): ArrayList<FileWapper>
    fun getSSLVersion(): Int

    fun checkCardCanBeSeedsim(slotId:Int):Boolean

    // ConnectivityManager
    fun registerDefaultNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback): Int

    fun unregisterNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback): Int

    fun getModelIf(context: Context):ModelIf

    fun getDnsServers(networkType: Int):ArrayList<InetAddress>?
}