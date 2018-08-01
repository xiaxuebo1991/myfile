package com.ucloudlink.refact.business.phonecall

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.SYSTEM_HIGH_PRIORITY
import android.content.IntentFilter.SYSTEM_LOW_PRIORITY
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.ServiceState
import android.telephony.ServiceState.STATE_IN_SERVICE
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.android.internal.telephony.IccCardConstants.*
import com.android.internal.telephony.PhoneConstants
import com.android.internal.telephony.TelephonyIntents.ACTION_SERVICE_STATE_CHANGED
import com.android.internal.telephony.TelephonyIntents.ACTION_SIM_STATE_CHANGED
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END
import com.ucloudlink.refact.access.AccessEventId.EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.PHY_CARD_STATE_CS_ON
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.PHY_CARD_STATE_OFF
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark.isProcessOutCall
import com.ucloudlink.refact.business.softsim.SeedNetworkStart
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.DeType
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthController
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logv

/**
 * Created by jiaming.liang on 2018/3/1.
 *
 * 用于记录各状态，以便于软卡下打电话恢复物理卡呼出
 *
 */
object SoftSimStateMark {

    /**
     * 等待物理卡读卡最大时间
     */
    val MAX_WAIT_PHY_CARD_ON_TIME = 10 * 1000
    /**
     * 等待物理卡注册的最大时间
     */
    val MAX_WAIT_PHY_CARD_CS_ON_TIME = 20 * 1000
    /**
     * 一个广播持续最大时间
     */
    val MAX_HOLD_TIME: Int = 7 * 1000
    /*
    软卡状态常量
     */
    val SOFTSIM_STATE_ON: Int = 0
    val SOFTSIM_STATE_OFF: Int = 1
    val SOFTSIM_STATE_CLOSING: Int = 2
    /*
    物理卡状态常量
     */
    val PHY_CARD_STATE_INSERTED: Int = 0
    val PHY_CARD_STATE_OFF: Int = 1
    val PHY_CARD_STATE_CS_ON: Int = 2

    private var nowTime: Long = 0
        get() = SystemClock.elapsedRealtime()

    /**
     * 标志是否在处理呼出电话
     */
    var isProcessOutCall = false
        set(value) {
            field = value
            OutCallProcessState(value)
        }

    /**
     * 物理卡最新插入时间
     */
    var phySimLastInsertTime: Long = nowTime

    var SoftSimLastClosedTime: Long = nowTime

    var phySimState: Int = PHY_CARD_STATE_CS_ON
        set(value) {
            if ((value == PHY_CARD_STATE_INSERTED && field != value) || (value == PHY_CARD_STATE_CS_ON && field == PHY_CARD_STATE_OFF)) {
                phySimLastInsertTime = nowTime
            }
            if (field != value) {
                logv("phySimState old($field) -> new($value)")
            }
            field = value

        }

    //软卡状态
    var softSimState: Int = SOFTSIM_STATE_OFF
        set(value) {
            if (value == SOFTSIM_STATE_OFF && field != value) {
                SoftSimLastClosedTime = nowTime
            } else if (value == SOFTSIM_STATE_ON) {
                phySimState = PHY_CARD_STATE_OFF
            }
            if (field != value) {
                logv("softSimState old($field) -> new($value)")
            }
            field = value

        }

    /*
        初始化软卡，物理卡状态
        初始化其时间
         */
    init {
        val seedCardEnabler = ServiceManager.seedCardEnabler
        seedCardEnabler.cardStatusObser().asObservable().subscribe {
            if (seedCardEnabler.getDeType() == DeType.SIMCARD && seedCardEnabler.getCard().cardType == CardType.SOFTSIM) {
                when (softSimState) {
                    SOFTSIM_STATE_ON, SOFTSIM_STATE_OFF -> {
                        if (it == CardStatus.ABSENT) {
                            softSimState = SOFTSIM_STATE_OFF
                        } else {
                            softSimState = SOFTSIM_STATE_ON
                        }
                    }
                    SOFTSIM_STATE_CLOSING -> {
                        if (it == CardStatus.ABSENT) {
                            softSimState = SOFTSIM_STATE_OFF
                        }
                    }
                }
            } else {
                //非card　Data enabler 或　不是软卡类型
                softSimState = SOFTSIM_STATE_OFF
            }
        }

        val filters = IntentFilter(ACTION_SIM_STATE_CHANGED)
        filters.addAction(ACTION_SERVICE_STATE_CHANGED)
        filters.addAction(ACTION_CLOSE_CALL)
        filters.addAction(ACTION_CALL_ERROR)
        filters.priority = SYSTEM_HIGH_PRIORITY
        ServiceManager.appContext.registerReceiver(CardStateReceiver(), filters)


        //开始监听呼出动作
        val permission = Manifest.permission.MODIFY_PHONE_STATE
        val thread = HandlerThread("handleOutCall")
        thread.start()
        val handle = Handler(thread.looper)

        //注册6个
        for (i in 0..8) {
            val filter1 = IntentFilter(Intent.ACTION_NEW_OUTGOING_CALL)
            filter1.priority = SYSTEM_LOW_PRIORITY
            ServiceManager.appContext.registerReceiver(OutgoingCallReceiver(i.toString()), filter1, permission, handle)
        }

    }

    fun closeSoftSim() {
        if(ServiceManager.seedCardEnabler.getDeType() == DeType.SIMCARD && ServiceManager.seedCardEnabler.getCard().cardType == CardType.SOFTSIM && ServiceManager.seedCardEnabler.isCardOn()){
            android.provider.Settings.Global.putInt(ServiceManager.appContext.contentResolver, "softsimCall",1)
            softSimState = SOFTSIM_STATE_CLOSING
            ServiceManager.seedCardEnabler.disable("outgoing Call", false)
        }
    }

    fun start() {

    }

    fun OutCallProcessState(isProcessing: Boolean) {
        ServiceManager.accessEntry.notifyEvent(if (isProcessing) EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_START else EVENT_EXCEPTION_OUT_GOING_CALL_DISABLE_SOFTSIM_END)
        handlePauseCloudSimAction(isProcessing)
        if (isProcessing) {
            closeSoftSim()
        }
    }

    fun handlePauseCloudSimAction(isProcessing: Boolean) {
        ServiceManager.cloudSimEnabler.notifyEventToCard(DataEnableEvent.OUT_GOING_CALL, isProcessing)
        AuthController.pauseCloudSimAuth = isProcessing

        if (isProcessing) {
            SeedNetworkStart.acquireHold("CallHold")
        }else{
            SeedNetworkStart.releaseHold("CallHold")
        }
    }

}

class CardStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        logv("onReceive action=" + intent.action)
        when (intent.action) {
            ACTION_SIM_STATE_CHANGED -> {
                handleSimStateChange(intent)
            }
            ACTION_SERVICE_STATE_CHANGED -> {
                handleServiceStateChanged(intent)
            }
            ACTION_CLOSE_CALL -> {
                logv("onReceive :ACTION_CLOSE_CALL")
                isProcessOutCall = false

            }
            ACTION_CALL_ERROR -> {
                val isErrorCall = intent.getBooleanExtra("isErrorCall", false)
                logv("onReceive :ACTION_CALL_ERROR isErrorCall:$isErrorCall")

                if (isErrorCall) {
                    isProcessOutCall = false
                }
            }

        }
    }

    fun handleServiceStateChanged(intent: Intent) {

        val serviceState = ServiceState.newFromBundle(intent.extras)
        val subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val slot = intent.getIntExtra(PhoneConstants.SLOT_KEY, -1)

        logv("handleServiceStateChanged slot:$slot subId:$subId serviceState:$serviceState ")

        val isNotSeedAndCloudsim = isPhyCard(slot)

        if (isNotSeedAndCloudsim) {
            when (serviceState.voiceRegState) {
                STATE_IN_SERVICE -> {
                    if (SoftSimStateMark.phySimState != PHY_CARD_STATE_CS_ON && SoftSimStateMark.softSimState != SoftSimStateMark.SOFTSIM_STATE_ON) {
                        if (TelecomManager.from(ServiceManager.appContext).callCapablePhoneAccounts.size > 0) {
                            SoftSimStateMark.phySimState = PHY_CARD_STATE_CS_ON
                        }
                    }
                }
//                else -> {
//                    SoftSimStateMark.phySimState = PHY_CARD_STATE_INSERTED
//                }
            }
        }

//        val ss = intent.getExtra("state") as ServiceState
//        logv("handleServiceStateChanged $ss")

    }

    private fun handleSimStateChange(intent: Intent) {
        var slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, SubscriptionManager.INVALID_SIM_SLOT_INDEX)
        if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            JLog.loge("updateSimState: get invalid sim slot index")
            slotId = SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultVoiceSubId())
        }

        val stateExtra = intent.getStringExtra(INTENT_KEY_ICC_STATE)

        //判断这张卡不是云卡，也不是软卡

        logv("onReceive slotId:$slotId stateExtra:$stateExtra")
        val isNotSeedAndCloudsim = isPhyCard(slotId)

        if (isNotSeedAndCloudsim) {
            val goodState = arrayOf(INTENT_VALUE_ICC_READY, INTENT_VALUE_ICC_IMSI, INTENT_VALUE_ICC_LOADED)
            val badState = arrayOf(INTENT_VALUE_ICC_CARD_IO_ERROR, INTENT_VALUE_ICC_LOCKED, INTENT_VALUE_ICC_INTERNAL_LOCKED)
            when (stateExtra) {
                INTENT_VALUE_ICC_NOT_READY -> {
                    //正在起卡
                    if (SoftSimStateMark.phySimState == PHY_CARD_STATE_OFF) {
                        SoftSimStateMark.phySimState = SoftSimStateMark.PHY_CARD_STATE_INSERTED
                    }
                }
                in goodState -> {
                    if (SoftSimStateMark.phySimState == PHY_CARD_STATE_OFF) {
                        SoftSimStateMark.phySimState = SoftSimStateMark.PHY_CARD_STATE_INSERTED
                    }
//                    if (SoftSimStateMark.phySimState != PHY_CARD_STATE_CS_ON) {
//                        startWaitCSReg()
//                    }
                }
                INTENT_VALUE_ICC_ABSENT, INTENT_VALUE_ICC_UNKNOWN -> {
                    //卡槽空闲，忽略处理即可
                }
                in badState -> {
                    //表示卡无法再注册，直接标志为cs注册上，如果需要对物理卡不可用处理时再修改
                    SoftSimStateMark.phySimState = PHY_CARD_STATE_CS_ON
                }
            }

            if (INTENT_VALUE_ICC_ABSENT != stateExtra) {

                val manager = TelephonyManager.from(ServiceManager.appContext)
                manager.isVideoTelephonyAvailable
            }
        }
    }

    private fun isPhyCard(slotId: Int): Boolean {
        val isNotCloudSim = !ServiceManager.cloudSimEnabler.isCardOn() || ServiceManager.cloudSimEnabler.getCard().slot != slotId

        val seedCardEnabler = ServiceManager.seedCardEnabler
        val isNotSoftSim = !seedCardEnabler.isCardOn() || seedCardEnabler.getCard().slot != slotId || seedCardEnabler.getCard().cardType != CardType.SOFTSIM


        val isNotSeedAndCloudsim = isNotCloudSim && isNotSoftSim
        logv("isPhyCard isNotCloudSim:$isNotCloudSim isNotSoftSim:$isNotSoftSim ")
        return isNotSeedAndCloudsim
    }
}

/**
 * 用户点击挂掉电话的广播
 * isEndCall:true
 */
const val ACTION_CLOSE_CALL = "com.ucloudlink.action.close.call"

/**
 * 呼出状态变化时
 * isErrorCall:true/false
 */
const val ACTION_CALL_ERROR = "com.ucloudlink.action.error.call"

