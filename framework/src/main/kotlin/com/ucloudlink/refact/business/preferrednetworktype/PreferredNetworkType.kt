package com.ucloudlink.refact.business.preferrednetworktype

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.android.internal.telephony.RILConstants
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.PhoneStateUtil

/**
 * Created by zhangxian on 2016/12/21.
 */
object PreferredNetworkType : Thread() {
    const val NETWORKTYPE_SET = 1
    const val SEEDCARD_NETWORKTYPE_SET = 2
    const val VSIMCARD_NETWORKTYPE_SET = 3
    const val NETWORKTYPE_SET_CANCEL = 4
    const val SEEDCARD_NETWORKTYPE_SET_CANCEL = 5
    const val VSIMCARD_NETWORKTYPE_SET_CANCEL = 6
    const val SOFTCARD_NETWORKTYPE_SET_CANCEL = 7

    /**
     * 数据漫游
     */
    var mDataRoaming: Int = 0
    /**
     * 网络制式设置
     */
    var mPreferredNetworkType: Int = 0

    private var mLooper: Looper? = null
    internal var mHandler: MyHandler? = null
    private var mDelayTime: Long = 2000

    /*
    软卡服务器制式定义如下：多种制式采用相加求和的方式
    4 8 16 32(tds)
    gsm	wcdma lte tds
     */
    private const val SERVER_RAT_TYPE_UNKNOWN: Int = 0
    private const val SERVER_RAT_TYPE_2G: Int = 4
    private const val SERVER_RAT_TYPE_3G: Int = 8
    private const val SERVER_RAT_TYPE_2G_3G: Int = 12
    const val SERVER_RAT_TYPE_4G: Int = 16
    private const val SERVER_RAT_TYPE_2G_4G: Int = 20
    private const val SERVER_RAT_TYPE_3G_4G: Int = 24
    private const val SERVER_RAT_TYPE_2G_3G_4G: Int = 28

    /**
     * 设置卡制式
     */
    fun setPreferredNetworkType(cardType: CardType, cmd: Int) {
        var sendCmd = -1
        when (cardType) {
            CardType.VSIM -> when (cmd) {
                NETWORKTYPE_SET -> {
                    sendCmd = VSIMCARD_NETWORKTYPE_SET
                }
                NETWORKTYPE_SET_CANCEL -> {
                    sendCmd = VSIMCARD_NETWORKTYPE_SET_CANCEL
                }
            }
            CardType.SOFTSIM -> when (cmd) {
                NETWORKTYPE_SET -> {
                    sendCmd = SEEDCARD_NETWORKTYPE_SET
                }
                NETWORKTYPE_SET_CANCEL -> {
                    sendCmd = SOFTCARD_NETWORKTYPE_SET_CANCEL
                }
            }
            else -> when (cmd) {
                NETWORKTYPE_SET -> {
                    sendCmd = SEEDCARD_NETWORKTYPE_SET
                }
                NETWORKTYPE_SET_CANCEL -> {
                    sendCmd = SEEDCARD_NETWORKTYPE_SET_CANCEL
                }
            }
        }
        if (sendCmd != -1) {
            mHandler?.let {
                mHandler!!.sendMessage(mHandler!!.obtainMessage(sendCmd))
            }
        } else {
            loge("setPreferredNetworkType failed on type = $cardType on cmd = $cmd")
        }
    }

    val resetSeedCardStatus: Runnable = Runnable {
        logd("setCardStatus resetSeedCardStatus start")

        val subId = ServiceManager.seedCardEnabler.getCard().subId
        val beforeSetPrefer = PhoneStateUtil.getNetworkModeBySubId(subId)
        logd("setCardStatus resetSeedCardStatus before set is $beforeSetPrefer")

        val ret = ServiceManager.systemApi.resetPreferredNetworkType(Configuration.seedSimSlot)

        val afterSetPrefer = PhoneStateUtil.getNetworkModeBySubId(subId)
        logd("setCardStatus resetSeedCardStatus ret: $ret, afterSetPrefer $afterSetPrefer")
    }

    val resetVsimCardStatus: Runnable = Runnable {
        logd("setCardStatus resetVsimCardStatus start")

        val subId = ServiceManager.cloudSimEnabler.getCard().subId
        val beforeSetPrefer = PhoneStateUtil.getNetworkModeBySubId(subId)
        logd("setCardStatus resetVsimCardStatus before set is $beforeSetPrefer")

        val ret = ServiceManager.systemApi.resetPreferredNetworkType(Configuration.cloudSimSlot)

        val afterSetPrefer = PhoneStateUtil.getNetworkModeBySubId(subId)
        logd("setCardStatus resetVsimCardStatus ret: $ret, afterSetPrefer $afterSetPrefer")
    }

    class MyHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            logd("PreferredNetworkType handleMessage:" + msg.what)
            try {
                when (msg.what) {
                    SEEDCARD_NETWORKTYPE_SET -> {
                        // 种子卡设置制式
                        mHandler!!.removeCallbacks(resetSeedCardStatus)
                        mHandler!!.post {
                            logd("setCardStatus setSeedCardStatus start")
                            setCardStatus(ServiceManager.seedCardEnabler.getCard())
                        }
                    }
                    VSIMCARD_NETWORKTYPE_SET -> {
                        // 云卡设置制式
                        mHandler!!.removeCallbacks(resetVsimCardStatus)
                        mHandler!!.post {
                            logd("setCardStatus setVsimCardStatus start")
                            setCardStatus(ServiceManager.cloudSimEnabler.getCard())
                        }
                    }
                    SEEDCARD_NETWORKTYPE_SET_CANCEL -> {
                        // 种子卡 仅物理卡 关卡
                    }
                    VSIMCARD_NETWORKTYPE_SET_CANCEL -> {
                        // 云卡关卡
                        mHandler!!.postDelayed(resetVsimCardStatus, mDelayTime)
                    }
                    SOFTCARD_NETWORKTYPE_SET_CANCEL -> {
                        // 种子卡 仅软卡 关卡
                        mHandler!!.postDelayed(resetSeedCardStatus, mDelayTime)
                    }
                }
            } catch (e: Exception) {
                loge("PreferredNetworkType handleMessage", e)
            }
        }
    }

    /**
     * 将服务器定义的RAT_TYPE转换为Android定义的RILConstants
     */
    private fun getRealNetworkType(networkType: Int): Int = when (networkType) {
        SERVER_RAT_TYPE_UNKNOWN -> RILConstants.NETWORK_MODE_WCDMA_PREF
        SERVER_RAT_TYPE_2G -> RILConstants.NETWORK_MODE_GSM_ONLY
        SERVER_RAT_TYPE_3G -> RILConstants.NETWORK_MODE_WCDMA_ONLY
        SERVER_RAT_TYPE_2G_3G -> RILConstants.NETWORK_MODE_WCDMA_PREF
        SERVER_RAT_TYPE_4G -> RILConstants.NETWORK_MODE_LTE_ONLY
        SERVER_RAT_TYPE_2G_4G -> RILConstants.NETWORK_MODE_LTE_GSM_WCDMA
        SERVER_RAT_TYPE_3G_4G -> RILConstants.NETWORK_MODE_LTE_WCDMA
        SERVER_RAT_TYPE_2G_3G_4G -> RILConstants.NETWORK_MODE_LTE_GSM_WCDMA
        else -> -1
    }

    /**
     * 设置卡制式
     */
    private fun setCardStatus(card: Card) {
        var ret: Int
        logd("currentSystemVersion: ${Configuration.currentSystemVersion}")

        if (card.cardType == CardType.PHYSICALSIM) {
            logd("setCardStatus mDataRoaming: ${card.roamenable}, PreferredNetworkType: ${card.rat}")
            // 物理卡只有开关打开的情况下，才设置漫游，其他情况让用户自己决定是否打开
            if (card.roamenable) {
                PhoneStateUtil.setRoamEnable(ServiceManager.appContext, card.subId, card.roamenable)
                ServiceManager.systemApi.setDataRoaming(card.subId, card.roamenable)
            }
            return
        }
        if (card.cardType == CardType.SOFTSIM && ServiceManager.systemApi.isUnderDevelopMode()) {
            loge("Device is under develop mode, ignore softsim network type set")
            return
        }
        try {
            logd("setCardStatus mDataRoaming: ${card.roamenable}")
            PhoneStateUtil.setRoamEnable(ServiceManager.appContext, card.subId, card.roamenable)
            ret = ServiceManager.systemApi.setDataRoaming(card.subId, card.roamenable)
            logd("set data roam return $ret")

            val dataRoaming = if (PhoneStateUtil.isRoamEnabled(ServiceManager.appContext, card.subId)) 1 else 0
            logd("isRoamEnabled: $dataRoaming")

            val beforeSetPrefer = PhoneStateUtil.getNetworkModeBySubId(card.subId)
            logd("setPreferredNetworkType before set is $beforeSetPrefer")

            val realRat = getRealNetworkType(card.rat)
            if (realRat >= 0) {
                logd("setPreferredNetworkType rat: ${card.rat} realRat = $realRat on slot ${card.slot}")
                ret = ServiceManager.systemApi.setPreferredNetworkType(card.slot, card.subId, realRat)
                val afterSetPrefer = PhoneStateUtil.getNetworkModeBySubId(card.subId)
                logd("setPreferredNetworkType ret: $ret, afterSetPrefer $afterSetPrefer")
            } else {
                logke("setPreferredNetworkType rat: ${card.rat} realRat = $realRat on slot ${card.slot} FAILED!")
            }
        } catch (e: Exception) {
            loge("setCardStatus failed", e)
        }
    }

    override fun run() {
        try {
            logd("PreferredNetworkType thread run")
            Looper.prepare()
            mLooper = Looper.myLooper()
            mHandler = MyHandler(mLooper!!)
            Looper.loop()
            mDelayTime = ServiceManager.systemApi.getNetTypeDelayTime()
        } catch (e: Exception) {
            loge("PreferredNetworkType thread run", e)
        } finally {
            logd("PreferredNetworkType thread end")
        }
    }
}


/*
var mPreferredNetworkTypeMap: HashMap<Int, Int> = HashMap<Int, Int>()
var mMIUIPreferredNetworkTypeMap: HashMap<Int, Int> = HashMap<Int, Int>()
var mCOOLPreferredNetworkTypeMap: HashMap<Int, Int> = HashMap<Int, Int>()
var mCOOLCDMAPreferredNetworkTypeMap: HashMap<Int, Int> = HashMap<Int, Int>()

const val CART_RAT_TYPE_UNKNOW: Int = 0
const val CART_RAT_TYPE_2G: Int = 1
const val CART_RAT_TYPE_3G: Int = 2
const val CART_RAT_TYPE_2G_3G: Int = 3
const val CART_RAT_TYPE_4G: Int = 4
const val CART_RAT_TYPE_2G_4G: Int = 5
const val CART_RAT_TYPE_3G_4G: Int = 6
const val CART_RAT_TYPE_2G_3G_4G: Int = 7
*/

//    /*
//    小米首选网络类型
//    2G优先对应值分别为1
//    3G优先对应值分别为18
//    4G优先对应值分别为20
//     */
//    const val MI_RAT_TYPE_2G: Int = 1
//    const val MI_RAT_TYPE_3G: Int = 18
//    const val MI_RAT_TYPE_4G: Int = 20
//
//    /*
//    酷派首选网络类型
//    2G对应值分别为1
//    3G对应值分别为14
//    2/3G优先对应值分别为18
//    2/3/4G优先对应值分别为20
//     */
//
//    const val COOL_RAT_TYPE_2G: Int = 1
//    //不支持TD-SCDMA
//    const val COOL_RAT_TYPE_3G: Int = 2//14
//    const val COOL_RAT_TYPE_2G_3G: Int = 0//18
//    const val COOL_RAT_TYPE_2G_3G_4G: Int = 9//20
//
//    const val COOL_CMDA_RAT_TYPE_2G_3G: Int = 4
//    const val COOL_CMDA_RAT_TYPE_2G_3G_4G: Int = 8
