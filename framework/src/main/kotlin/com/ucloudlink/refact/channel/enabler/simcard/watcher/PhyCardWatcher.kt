package com.ucloudlink.refact.channel.enabler.simcard.watcher

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
import android.telephony.ServiceState
import android.telephony.ServiceState.*
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.DATA_CONNECTED
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.datas.Plmn
import com.ucloudlink.refact.channel.enabler.plmnselect.SwitchNetHelper
import com.ucloudlink.refact.channel.enabler.simcard.UcPhoneStateListenerWrapper
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.channel.monitors.CardStateMonitor.SIM_STATE_LOAD
import com.ucloudlink.refact.channel.monitors.CardStateMonitor.SIM_STATE_READY
import com.ucloudlink.refact.channel.monitors.NwMonitor
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.PhoneStateUtil
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Created by jiaming.liang on 2017/11/22.
 *
 * 尽早开始对物理卡进行监控，根据以下判断条件，提前判断物理卡是否可用
 * （参考注册拒绝，参考搜网记录，判断卡是否漫游状态）
 * 1，注册PS拒绝信息
 *
 * 2，保存搜网记录，根据附近网络情况判断卡是否漫游并注册超时
 *      当搜网记录一直保存，直到新的搜网记录上报，就去掉5分钟之前的记录
 *
 * 3，提供物理卡是否漫游的判断
 *      当有一个非本国mcc的强信号时，判断为漫游态
 *      黄云建议umts 小于95 lte小于110
 *
 */
open class PhyCardWatcher(context: Context, looper: Looper) : Handler(looper) {
    private val PERIOD_OF_NETWORK_VALIDITY: Long = 300000 /*搜网结果有效时间*/
    private val WAIT_TO_REG_TIME: Long = 180000 /*等待注册有效时间*/
    private val MAX_SAME_DENIED_HIT = 3 /*相同拒绝原因，最多接受次数*/

    private val EVENT_REFRESH_NETWORK: Int = 1
    private val EVENT_CARD_STATE_CHANGE: Int = 2 //obj is SimCardState 
    private val EVENT_REMOTE_CARD_ON = 3
    private val EVENT_CHECK_PHYCARD = arrayOf(4, 5, 6)
    private val EVENT_CARD_REAL_STATE_CHANGE = 7

    private val plmnsList = LinkedList<Plmn>()
    private val EVENT_CARD_REJECT_ERROR = 8

    private val listenMap = HashMap<Int, phoneStateListener?>()
    private val resultMap = HashMap<Int, MccState?>()
    private val interfaceMap = HashMap<Int, exceptionListener>()

    private val subIdList = arrayOf(-1, -1, -1)
    private val phyCardState = arrayOf(true, true, true)

    private val ctx = context
    val mCardStateList = ArrayList<PhyCardState>()
    private val MAX_CARD_SLOT = 3
    private val mPhone = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val mySeedCard = ServiceManager.accessSeedCard
    private val myCloudCard = ServiceManager.cloudSimEnabler

    private var DDSubId = -1

    private val lock = ReentrantReadWriteLock()
    
    val nwMonitor = NwMonitor(context)

    var onCardUnavailable: (Int) -> Unit = { slotId -> }
    var onCardRoamStateChange: (Int, Boolean) -> Unit = { subId, RoamState -> }

    init {
        synchronized(mCardStateList) {
            for (slot in 0..(MAX_CARD_SLOT - 1)) {
                mCardStateList.add(PhyCardState(slot))
            }

            for (slot in 0..(MAX_CARD_SLOT - 1)) {
                val state = ServiceManager.systemApi.getSimState(slot)
                mCardStateList[slot].state = kotlin.run {
                    when (state) {
                        TelephonyManager.SIM_STATE_UNKNOWN, TelephonyManager.SIM_STATE_ABSENT -> {
                            return@run CardStateMonitor.SIM_STATE_ABSENT
                        }
                        TelephonyManager.SIM_STATE_READY -> {
                            return@run CardStateMonitor.SIM_STATE_LOAD
                        }
                        TelephonyManager.SIM_STATE_PUK_REQUIRED, TelephonyManager.SIM_STATE_PIN_REQUIRED -> {
                            return@run CardStateMonitor.SIM_STATE_NOT_READY
                        }
                        else -> {
                            return@run CardStateMonitor.SIM_STATE_ABSENT
                        }
                    }
                }

                if (mCardStateList[slot].state != CardStateMonitor.SIM_STATE_ABSENT) {
                    initSlotState(mCardStateList[slot], -1)
                }
            }
        }
    }

    private fun initSlotState(cardState: PhyCardState, subId: Int) {
        logd("initSlotState $cardState $subId")
        var localSubId = subId
        if (localSubId >= 0) {

        } else {
            val subId = ServiceManager.systemApi.getSubIdBySlotId(cardState.slot)
            logd("get subid for slot ${cardState.slot} subId:$subId")
            if (subId != null) {
                localSubId = subId
            }
        }
        logd("initSlotState 2 $cardState $localSubId")
        if (localSubId <= 0){
            logd("subid $localSubId is invalid,stop init slot ${cardState.slot} state!!!")
            return
        }
        cardState.subId = localSubId
        cardState.imsi = ServiceManager.systemApi.getSubscriberIdBySlot(cardState.slot)
        cardState.lastInfo.imsi = cardState.imsi
        cardState.lastInfo.isOn = true
        if (ServiceManager.systemApi.getDefaultDataSubId() == cardState.subId) {
            logd("this slot ${cardState.slot} ${cardState.subId} is default data slot data ${mPhone.dataState}")
            try {
                cardState.networkOk = (mPhone.dataState == TelephonyManager.DATA_CONNECTED)
            } catch (e: Exception) {
                e.printStackTrace()
                cardState.networkOk = false
            }
        }
        val serviceState = ServiceManager.systemApi.getServiceStateForSubscriber(cardState.subId)
        logd("get slot ${cardState.slot} serviceState:  $serviceState")
        if (serviceState != null) {
            cardState.dataReg.reg = (serviceState.dataRegState == ServiceState.STATE_IN_SERVICE)
            if (cardState.dataReg.reg) {
                cardState.dataReg.regTime = SystemClock.elapsedRealtime()
            }
            cardState.dataReg.roam = serviceState.dataRoaming

            cardState.voiceReg.reg = (serviceState.voiceRegState == ServiceState.STATE_IN_SERVICE)
            cardState.voiceReg.regTime = SystemClock.elapsedRealtime()
            cardState.voiceReg.roam = serviceState.voiceRoaming
        }
    }

    private fun updateSlotStateBySubid(cardState: PhyCardState, subId: Int) {
        logd("updateSlotStateBySubid $cardState $subId")
        if (subId <= 0){
            logd("subid $subId is invalid,stop update slot ${cardState.slot} state!!!")
            return
        }
        cardState.subId = subId
        cardState.imsi = mPhone.getSubscriberId(cardState.subId)
        cardState.lastInfo.imsi = cardState.imsi
        cardState.lastInfo.isOn = true
        if (ServiceManager.systemApi.getDefaultDataSubId() == cardState.subId) {
            logd("this slot ${cardState.slot} ${cardState.subId} is default data slot data ${mPhone.dataState}")
            try {
                cardState.networkOk = (mPhone.dataState == TelephonyManager.DATA_CONNECTED)
            } catch (e: Exception) {
                e.printStackTrace()
                cardState.networkOk = false
            }
        }
        val serviceState = ServiceManager.systemApi.getServiceStateForSubscriber(cardState.subId)
        logd("get slot ${cardState.slot} serviceState:  $serviceState")
        if (serviceState != null) {
            cardState.dataReg.reg = (serviceState.dataRegState == ServiceState.STATE_IN_SERVICE)
            if (cardState.dataReg.reg) {
                cardState.dataReg.regTime = SystemClock.elapsedRealtime()
            }
            cardState.dataReg.roam = serviceState.dataRoaming

            cardState.voiceReg.reg = (serviceState.voiceRegState == ServiceState.STATE_IN_SERVICE)
            cardState.voiceReg.regTime = SystemClock.elapsedRealtime()
            cardState.voiceReg.roam = serviceState.voiceRoaming
        }
        logd("updateSlotState cardstate $cardState")
    }

    val ddsSubscribe = ServiceManager.monitor.ddsObser.asObservable().subscribe {
        if (DDSubId != it) {
            //发个消息,让旧的关闭，再打开新的
            val log = StringBuilder("ddsSubscribe ")
            val oldSlot = getSlotBySubId(DDSubId)
            if (oldSlot >= 0) {
                obtainMessage(EVENT_CARD_STATE_CHANGE, SimCardState(oldSlot, DDSubId, SIM_STATE_READY)).sendToTarget()
            }
            log.append("oldSlot $oldSlot oldSubId:$DDSubId ")
            DDSubId = it
            val slotId = SubscriptionManager.getPhoneId(DDSubId)

            obtainMessage(EVENT_CARD_STATE_CHANGE, SimCardState(slotId, DDSubId, SIM_STATE_LOAD)).sendToTarget()
            log.append("newSlot $slotId newSubId:$DDSubId ")
            logd(log.toString())
        }
    }

    private val nwListen = object : CardStateMonitor.ScanNwlockListen {
        override fun hashCode(): Int {
            return 112548
        }

        override fun onScanNwChanged(phoneId: Int, plmns: ArrayList<Plmn>?) {
            obtainMessage(EVENT_REFRESH_NETWORK, plmns).sendToTarget()
        }

    }

    private val cardListen = object : CardStateMonitor.CardStateListen {
        override fun hashCode(): Int {
            return 343245
        }

        override fun CardStateChange(slotId: Int, subId: Int, stateExtra: Int) {
            obtainMessage(EVENT_CARD_STATE_CHANGE, SimCardState(slotId, subId, stateExtra)).sendToTarget()
            obtainMessage(EVENT_CARD_REAL_STATE_CHANGE, SimCardState(slotId, subId, stateExtra)).sendToTarget()
        }
    }

    init {
        ServiceManager.simMonitor.addScanNwlockListen(nwListen)
        ServiceManager.simMonitor.addCardStateListen(cardListen)
    }

    fun isCardAvailable(slot: Int, subId: Int): Boolean {

        if (slot < 0 || subId < 0) {
            return true
        }

        if (subIdList[slot] != subId) {
            logd("[isCardAvailable] subIdList[slot] != subId return true  ${subIdList[slot]} != $subId")
            return true
        }

        val mccState = resultMap[subId]
        val numeric = ServiceManager.systemApi.getIccOperatorNumericForData(subId)
        if (mccState != null && !TextUtils.isEmpty(numeric)) {
//            val telephonyManager = TelephonyManager.from(ServiceManager.appContext)
//            val numeric = telephonyManager.getIccOperatorNumericForData(subId)
            logd("numeric is $numeric")
            if (numeric.length >= 3 && numeric.substring(0, 3) == mccState.mcc) {
                logv("isCardAvailable $mccState")
                return mccState.state
            } else {
                resultMap[subId] = null
                logd("resultMap[subId] = null numeric:$numeric")
            }
        }

        return phyCardState[slot]
    }

    fun isCardRoam(slot: Int, subId: Int): Boolean {
        if (slot < 0 || subId < 0) {
            return false
        }
        if (subIdList[slot] != subId) {
            logd("[isCardRoam] subIdList[slot] != subId return false  ${subIdList[slot]} != $subId")
            return false
        }
        val mccState = resultMap[subId]
        if (mccState != null) {
            logv("isCardRoam $mccState")
            return mccState.isRoam
        } else {
            logd("[isCardRoam] mccState == null   return false")
            return false
        }

    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            EVENT_REFRESH_NETWORK -> {
                val plmns = msg.obj
                if (plmns == null) {
                    nwMonitor.refreshNw()
                } else {
                    nwMonitor.refreshNw(plmns as ArrayList<Plmn>?)
                }
                //判断两个卡是否漫游态
                for (i in 0..1) {
                    val subId = subIdList[i]
                    if (subId != -1) {
                        val mccState = resultMap[subId]
                        if (mccState != null) {
                            val isRoam = nwMonitor.checkIsRoam(mccState.mcc)
                            mccState.isRoam = isRoam
                            onCardRoamStateChange(subId, isRoam)
                        }
                    }
                }
            }
            EVENT_CARD_STATE_CHANGE -> { //2
                val simCardState = msg.obj as SimCardState
                val slot = simCardState.slot
                if (slot == -1) {
                    loge("slot invalid $slot")
                    return
                }
                if (simCardState.state == SIM_STATE_LOAD) {

                    //忽略种子软卡
                    if (mySeedCard.isCardOn() && mySeedCard.getCard().cardType != CardType.PHYSICALSIM && mySeedCard.getCard().slot == slot) {
                        logd("mySeedCard ${mySeedCard.isCardOn()} && ${mySeedCard.getCard().cardType} && ${mySeedCard.getCard().slot}")
                        stopWatchReg(slot)
                        return
                    }
                    //忽略云卡
                    if (myCloudCard.isCardOn() && myCloudCard.getCard().cardType != CardType.PHYSICALSIM && myCloudCard.getCard().slot == slot) {
                        logd("myCloudCard ${myCloudCard.isCardOn()} && ${myCloudCard.getCard().cardType} && ${myCloudCard.getCard().slot}")
                        stopWatchReg(slot)
                        return
                    }
                    val subId = simCardState.subId
                    if (subId < 0x40000000 && subId != -1) {
                        subIdList[slot] = subId

                        val telephonyManager = TelephonyManager.from(ctx)
                        val imsi = telephonyManager.getSubscriberId(subId)
                        /*imsi 可能是null 也可能是非空但空字符*/
                        if (TextUtils.isEmpty(imsi)) {
                            return logd("imsi is null ,return")
                        }

                        //设置内置物理卡的imsi
                        Configuration.internalPhyCardImsi = imsi

                        val mcc = imsi.substring(0, 3)

                        val isRoam = nwMonitor.checkIsRoam(mcc)

                        val mccState = resultMap[subId]
                        if (mccState == null || mcc != mccState.mcc) {
                            resultMap[subId] = MccState(mcc, true, isRoam)
                            logd("new MccState ${resultMap[subId]}")
                        } else {
                            mccState.isRoam = isRoam
                        }

                        onCardRoamStateChange(subId, isRoam)

                        startWatchReg(slot, subId)
                    }
                } else {
                    stopWatchReg(slot)
                }
            }
            EVENT_CHECK_PHYCARD[0], EVENT_CHECK_PHYCARD[1], EVENT_CHECK_PHYCARD[2] -> {//4
                //判断mcc
                if (nwMonitor.getRoundNetCount() <= 0) return logd("plmnsList.size <= 0 ignore check")

                val subId = msg.obj as Int
                val mccState = resultMap[subId]
                mccState ?: return loge("EVENT_CHECK_PHYCARD mccState is null")
                if (!mccState.isRoam) {
                    val hasNw = nwMonitor.isMccInNwResult(mccState.mcc)
                    setCardState(getSlotBySubId(subId), hasNw)
                    mccState.state = hasNw
                }
                logd("handle EVENT_CHECK_PHYCARD $mccState")
            }
            EVENT_CARD_REAL_STATE_CHANGE -> {
                val simCardState = msg.obj as SimCardState
                logd("recv EVENT_CARD_REAL_STATE_CHANGE $simCardState")
                updateCardState(simCardState.slot, simCardState.subId, simCardState.state)
            }
            EVENT_CARD_REJECT_ERROR->{
                val slot = msg.arg1
                val reason = msg.arg2
                var subId = ServiceManager.systemApi.getSubIdBySlotId(slot)
                interfaceMap[subId]!!.processMessage(reason,slot)
            }
        }
    }


    //物理卡处理值
    private val phySimDeniedReason = arrayOf(
            NETWORK_AUTHENTICATION_AND_CIPHERION_REJECT,
            NETWORK_PDN_CONNECTIVITY_REJECT,
            NETWORK_ATTACH_REJECT,
            NETWORK_TRACKING_AREA_REJECT,
            NETWORK_EMM_AUTHENTICATION_REJECT,
            NETWORK_EMM_SECURITY_MODE_REJECT
    )
    private fun startWatchReg(slot: Int, subId: Int) {

        // 非dds slot也需要监听
//        val ddsSlotId = SubscriptionManager.getPhoneId(ServiceManager.systemApi.getDefaultDataSubId())
//        if (slot >= 0 && ddsSlotId != slot) {
//            logd("ignore ,not dds ddsSlotId=$ddsSlotId slot=$slot")
//            return
//        }
        synchronized(listenMap) {
            if (listenMap[subId] == null) {
                val mPhone = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                synchronized(mCardStateList) {
                    val UcPsListen = phoneStateListener(mCardStateList[slot], slot, subId, looper)
                    listenMap[subId] = UcPsListen
                    mPhone.listen(UcPsListen, PhoneStateListener.LISTEN_SERVICE_STATE or LISTEN_DATA_CONNECTION_STATE)
                    logd("startWatchReg slot:$slot subId :$subId")
                }
            } else {
                logd("[startWatchReg] listenMap[subId] != null slot:$slot subId :$subId")
            }
        }
        synchronized(interfaceMap) {
            if (interfaceMap[subId] == null) {
                val excListener = exceptionListener()
                interfaceMap[subId] = excListener
            }else {
                logd("[startWatchReg] interfaceMap[subId] != null slot:$slot subId :$subId")
            }
        }
    }

    private fun stopWatchReg(slot: Int) {

        if (slot < 0) {
            logd("[stopWatchReg] slot < 0 slot:$slot ")
            return
        }
        synchronized(listenMap) {
            val subId = subIdList[slot]
            setCardState(slot, true)
            if (subId > 0 && listenMap[subId] != null) {
                val ucPhoneStateListener = listenMap[subId]
                val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                mPhone.listen(ucPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                listenMap[subId] = null
                logd("stopWatchReg slot:$slot subId :$subId")
            } else {
                logd("[stopWatchReg] subId = $subId || listenMap[subId] = ${if (subId != -1) listenMap[subId] else "null"} ")
            }
        }
        synchronized(interfaceMap) {
            val subId = subIdList[slot]
            if (subId > 0 && listenMap[subId] != null) {
                listenMap[subId] = null
            }else {
                logd("[startWatchReg] interfaceMap[subId] != null slot:$slot subId :$subId")
            }
        }
    }
    private inner class exceptionListener(){
        private var sameDeniedReasonHit = 0
        private var lastDeniedReason = -1
         fun processMessage(reason:Int,slot: Int){
            logd("reason == "+reason)
            logd("slot == "+slot)
            val mSubId = getSubIdBySlot(slot)
            //deniedReason初始值可能是-1 或者 0
            if (lastDeniedReason == reason) {
                sameDeniedReasonHit++
            } else {
                sameDeniedReasonHit = 1
            }
            if(sameDeniedReasonHit>=MAX_SAME_DENIED_HIT){
                val mccState = resultMap[mSubId]
                mccState ?: return loge("mccState should not be null")
                if (mccState.state) {
                    setCardState(slot, false)
                    mccState.state = false
                }
            }
            lastDeniedReason = reason
            when (reason) {
                in phySimDeniedReason -> {
                    setCardState(slot, false)
                    resultMap[mSubId]?.state = false
                }
                NO_SUITABLE_CELLS_IN_LOCATION_AREA -> {
                    //save regcount
                    mCardStateList[slot].dataRegReject++
                    if (mCardStateList[slot].dataRegReject == 1) {
                        mCardStateList[slot].dataRegRejectTime = SystemClock.elapsedRealtime()
                    }
                    logd("cardState.regReject value ${mCardStateList[slot].dataRegReject}")
                }
            }
        }
    }
    private inner class phoneStateListener(cstate: PhyCardState, slot: Int, subId: Int, looper: Looper) : UcPhoneStateListenerWrapper(subId, looper) {
        val slot = slot
        var cardState = cstate
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
            logd("onDataConnectionStateChanged $state   ")
            if (state == DATA_CONNECTED) {
                onCardDataConnected()
                cardState.networkOk = true
            } else {
                cardState.networkOk = false
            }
        }

        override fun onServiceStateChanged(state: ServiceState) {
            logd("onServiceStateChanged + $state")
            when (state.dataRegState) {
                STATE_OUT_OF_SERVICE, STATE_EMERGENCY_ONLY -> {
                    if (!hasMessages(EVENT_CHECK_PHYCARD[slot])) {
                        sendMessageDelayed(obtainMessage(EVENT_CHECK_PHYCARD[slot], uSubId), WAIT_TO_REG_TIME)
                    }

                    //数据没注册上就检查语音注册状态是否漫游
                    if (state.voiceRegState == STATE_IN_SERVICE) {
                        val mccState = resultMap[uSubId]
                        if (mccState != null) {
                            mccState.isRoam = state.voiceRoaming
                            onCardRoamStateChange(uSubId, mccState.isRoam)
                        }
                    }
                }

                STATE_IN_SERVICE -> {
                    val mccState = resultMap[uSubId]
                    if (mccState != null) {
                        mccState.isRoam = state.dataRoaming
                        onCardRoamStateChange(uSubId, mccState.isRoam)
                    }
                }
            }

            if (state == null) {
                loge("serviceState is null $cardState")
                return
            }

            if (state.dataRegState == ServiceState.STATE_IN_SERVICE) {
                if (!cardState.dataReg.reg) {
                    cardState.dataReg.reg = true
                    cardState.dataReg.regTime = SystemClock.elapsedRealtime()
                    cardState.dataReg.roam = state.dataRoaming
                }
                cardState.dataRegReject = 0
                cardState.dataRegRejectTime = 0
            } else {
                if (cardState.dataReg.reg) {
                    cardState.dataReg.reg = false
                    cardState.dataReg.roam = false
                    cardState.dataRegReject = 0
                    cardState.dataRegRejectTime = 0
                }
            }

            if (state.voiceRegState == ServiceState.STATE_IN_SERVICE) {
                if (!cardState.voiceReg.reg) {
                    cardState.voiceReg.reg = true
                    cardState.voiceReg.regTime = SystemClock.elapsedRealtime()
                    cardState.voiceReg.roam = state.voiceRoaming
                }
            } else {
                cardState.voiceReg.reg = false
                cardState.voiceReg.roam = false
            }


        }

        private fun onCardDataConnected() {
            removeMessages(EVENT_CHECK_PHYCARD[slot])
            setCardState(slot, true)
            resultMap[uSubId]?.state = true
            logd("onCardDataConnected  ${resultMap[uSubId]}")
        }
    }


    private fun setCardState(slot: Int, state: Boolean) {

        if (slot == -1) {
            return loge("mSlot must mot be -1")
        }
        removeMessages(EVENT_CHECK_PHYCARD[slot])

        phyCardState[slot] = state
        logd("setCardState slot:$slot  state:$state")
        if (!state) {
            onCardUnavailable(slot)
        }
    }

    private fun getSlotBySubId(subId: Int): Int {
        logd("subId == " + subId)
        if (subId >= 0) {
            for (i in 0..2) {
                if (subIdList[i] == subId) {
                    return i
                }
            }
        }
        return -1
    }

    private fun setCardStart(cardState: PhyCardState, subId: Int, state: Int) {
        if (cardState.state == CardStateMonitor.SIM_STATE_ABSENT) {
            cardState.subId = subId
            cardState.state = state
            initSlotState(cardState, subId)
        }
    }

    protected fun setCardAbsent(cardState: PhyCardState, subId: Int) {
        if (cardState.state != CardStateMonitor.SIM_STATE_ABSENT) {
            cardState.subId = -1
            cardState.imsi = null
            cardState.state = CardStateMonitor.SIM_STATE_ABSENT
            cardState.networkOk = false
            cardState.dataReg.reg = false
            cardState.voiceReg.reg = false

        }
    }

    open protected fun updateCardState(slot: Int, subId: Int, state: Int) {
        logd("updateCardState: $slot $subId $state")

        if (slot < 0 || slot > 2) {
            loge("slot invalid ! $slot")
            return
        }

        if (isCardVirtual(slot)) {
            setCardAbsent(mCardStateList[slot], subId)
            return
        }

        updateRealCardState(slot, subId, state)
    }

    /**
     * 有可能 card状态不变，但是subid会变
     */
    @SuppressLint("HardwareIds")
    protected fun updateRealCardState(slot: Int, subId: Int, state: Int) {
        val cardState = mCardStateList[slot]
        logd("current cardState $cardState")

        if (cardState.state != state) {
            logd("state update slot $slot ${cardState.state} -> $state")
            if (cardState.state == CardStateMonitor.SIM_STATE_ABSENT
                    && (state == CardStateMonitor.SIM_STATE_LOAD || state == CardStateMonitor.SIM_STATE_READY)) {
                logd("card ready")
                setCardStart(cardState, subId, state)
            } else if (cardState.state != CardStateMonitor.SIM_STATE_ABSENT && state == CardStateMonitor.SIM_STATE_ABSENT) {
                logd("card absent!")
                setCardAbsent(cardState, subId)
            }
            if (state == CardStateMonitor.SIM_STATE_LOAD) {
                val imsi = ServiceManager.teleMnger.getSubscriberId(subId)
                if (imsi != null) {
                    cardState.imsi = imsi
                    cardState.lastInfo.imsi = imsi
                }
                logd("set card state imsi : $imsi $cardState")
            }
            cardState.state = state
        }

        if (subId > 0 && subId < 0x40000000) {
            if (cardState.subId != subId) {
                logd("subid update slot $slot ${cardState.subId} -> $subId")
                if (cardState.state == SIM_STATE_LOAD || cardState.state == SIM_STATE_READY){
                    updateSlotStateBySubid(cardState, subId)
                }
            }
        }
    }

    /**
     * return ms
     */
    private fun getRegAvailableTh(regReject: Int, regRejctTime: Long): Long {
        val th: Long = kotlin.run {
            when (regReject) {
                0 -> {
                    return@run 2 * 60 * 1000  // 第一次2min
                }
                1 -> {
                    return@run 1 * 60 * 60 * 1000
                }
                2 -> {
                    return@run 6 * 60 * 60 * 1000
                }
                3 -> {
                    return@run 12 * 60 * 60 * 1000
                }
                else -> {
                    return@run 12 * 60 * 60 * 1000
                }
            }
        }
        logd("getRegAvailableTh regreject $regReject time $th")
        return th
    }

    private fun isRegAvailable(cardState: PhyCardState): Boolean {
        if (cardState.voiceReg.reg && (SystemClock.elapsedRealtime() - cardState.voiceReg.regTime > getRegAvailableTh(cardState.dataRegReject, cardState.dataRegRejectTime))) {
            return true
        }
        if (cardState.dataReg.reg && (SystemClock.elapsedRealtime() - cardState.dataReg.regTime > getRegAvailableTh(cardState.dataRegReject, cardState.dataRegRejectTime))) {
            return true
        }
        return false
    }

    protected fun isCardVirtual(slot: Int): Boolean {
        if (ServiceManager.seedCardEnabler.isCardOn()
                && ServiceManager.seedCardEnabler.getCard().slot == slot
                && ServiceManager.seedCardEnabler.getCard().cardType == CardType.SOFTSIM) {
            logd("this slot $slot is soft")
            return true
        }

        if (ServiceManager.cloudSimEnabler.isCardOn()
                && ServiceManager.cloudSimEnabler.getCard().slot == slot
                && ServiceManager.cloudSimEnabler.getCard().cardType == CardType.VSIM) {
            logd("this slot $slot is vsim ")
            return true
        }
        return false
    }

    /**
     * 判断物理卡是不是漫游，条件：
     * 1、物理卡注册上漫游
     * 2、物理卡当前所在的网络有国外的强信号
     * 3、
     */
    private fun isCardRoamInner(cardState: PhyCardState): Boolean {
        logd("isCardRoam  $cardState")
        if (cardState.state == CardStateMonitor.SIM_STATE_ABSENT) {
            return false
        }

        if (cardState.dataReg.roam || cardState.voiceReg.roam) {
            return true
        }

        if (cardState.imsi == null || cardState.imsi!!.length<3) {
            return false
        }

        if (nwMonitor.checkIsRoam(cardState.imsi!!.substring(0, 3))) {
            return true
        }

        return false
    }

    fun isCardRoamBySlot(slot: Int): Boolean {
        if (slot < 0 || slot > 2) {
            loge("slot invalid $slot")
            return false
        }

        return isCardRoamInner(mCardStateList[slot])
    }

    fun isCardRoam(slot: Int): Boolean {
        if (slot < 0 || slot > 2) {
            loge("slot invalid $slot")
            return false
        }

        return isCardRoamInner(mCardStateList[slot])
    }

    fun isCardOn(slot: Int): Boolean {
        logd("isCardOn $slot")
        if (slot < 0 || slot > 2) {
            loge("slot invalid $slot")
            return false
        }

        logd("isCardOn $slot state${mCardStateList[slot].state}")
        if (mCardStateList[slot].state != CardStateMonitor.SIM_STATE_ABSENT
                && !isCardVirtual(slot)) {
            logd("card $slot is on!")
            return true
        }
        return false
    }

    fun getSubIdBySlot(slot: Int): Int {
        if (slot < 0 || slot > 2) {
            loge("slot invalid $slot")
            return -1
        }
        if (mCardStateList[slot].state != CardStateMonitor.SIM_STATE_ABSENT
                && !isCardVirtual(slot)) {
            logd("getSubIdBySlot $slot subid ${mCardStateList[slot].subId}")
            return mCardStateList[slot].subId
        }

        return -1
    }

    fun isPhyCardLocalOk(slot: Int): Boolean {
        if (slot < 0 || slot > 2) {
            loge("slot $slot invalid !")
            return false
        }

        val cardState = mCardStateList[slot]
        logd("isPhyCardLocalOk $slot $cardState")

        if (cardState.state == CardStateMonitor.SIM_STATE_ABSENT) {
            return false
        }

        if (cardState.networkOk) {
            return true
        }

        if (!isCardRoamInner(cardState) && isRegAvailable(cardState)) {
            return true
        }

        return false
    }

    /**
     *  物理卡真不可用，暂时2个条
     *  1. 漫游，但是漫游开关没开
     *  2. 物理卡在位，但是物理卡数据开关没开
     *  3. 物理卡不在位
     */
    fun isPhyRealValid(slot: Int): Int {
        lock.readLock().lock()
        logd("isPhyRealValid ${mCardStateList[slot]}")
        lock.readLock().unlock()
        if (ServiceManager.systemApi.isPhySeedExist()) {
            logd("phy card is exist by vsim state")
            return 0
        }
        if (isCardRoamBySlot(slot)) {
            if (!Configuration.PHY_ROAM_ENABLE) {
                logd("phy card roam disabled by congfiguration")
                return ErrorCode.CARD_PHY_ROAM_UI_CONFIG_DISABLE
            }

            logd("phy card subid ${mCardStateList[slot].subId} ")
            if (mCardStateList[slot].subId > 0 && !PhoneStateUtil.isRoamEnabled(ServiceManager.appContext, mCardStateList[slot].subId)) {
                logd("phy card roam disable by user config")
                return ErrorCode.CARD_PHY_ROAM_DISABLE
            }
        }

        if (mCardStateList[slot].state != CardStateMonitor.SIM_STATE_ABSENT
                && !PhoneStateUtil.isMobileDataEnabled(ctx, mCardStateList[slot].subId)) {
            logd("phy sim dataconnect disabled!")
            return ErrorCode.LOCAL_PHY_CARD_DISABLE
        }

        if (ServiceManager.systemApi.isPhySeedExist()) {
            logd("phy card has present")
            return 0
        }

        if (mCardStateList[slot].state == CardStateMonitor.SIM_STATE_ABSENT
                && !isCardVirtual(slot)) {
            logd("card is not on!!")
            return ErrorCode.LOCAL_PHY_CARD_NOT_EXIST
        }

        if (isCardVirtual(slot)) {
            logd("if virtual card on, we thought no card here")
            return ErrorCode.LOCAL_PHY_CARD_NOT_EXIST
        }

        val subId = getSubIdBySlot(slot)
        if (subId > 0 && subId < 0x40000000) {
            val telephonyManager = TelephonyManager.from(ServiceManager.appContext)
            val phoneType = telephonyManager.getCurrentPhoneType(subId)
            logd("subid $subId phoneType $phoneType")
            if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                return ErrorCode.SEED_CARD_CANNOT_BE_CDMA
            }
        }
        return 0
    }

    fun getImsiBySlot(slot: Int): String? {
        if (slot < 0 || slot > MAX_CARD_SLOT) {
            loge("slot invalid!")
            return null
        }

        return mCardStateList[slot].imsi
    }

    fun getLastInfoBySlot(slot: Int): CardLastInfo? {
        if (slot < 0 || slot > MAX_CARD_SLOT) {
            loge("slot invalid!")
            return null
        }

        return mCardStateList[slot].lastInfo
    }
}
    //物理卡，云卡拨号被拒切网
    private const val NETWORK_AUTHENTICATION_AND_CIPHERION_REJECT = 3//ps 255
    private const val NETWORK_PDN_CONNECTIVITY_REJECT = 4
    private const val NETWORK_ATTACH_REJECT = 5
    private const val NETWORK_TRACKING_AREA_REJECT = 6
    private const val NETWORK_EMM_AUTHENTICATION_REJECT = 7
    private const val NETWORK_EMM_SECURITY_MODE_REJECT = 8

    private const val NO_SUITABLE_CELLS_IN_LOCATION_AREA = 15


data class SimCardState(val slot: Int, val subId: Int, val state: Int)
data class CardLastInfo(var imsi: String? = null, var isOn: Boolean = false)

data class RegState(var reg: Boolean = false, var regTime: Long = 0, var roam: Boolean = false)
data class PhyCardState(val slot: Int,
                        var subId: Int = -1,
                        var state: Int = CardStateMonitor.SIM_STATE_ABSENT,
                        var imsi: String? = null,
                        var networkOk: Boolean = false,
                        var dataReg: RegState = RegState(false, 0),
                        var voiceReg: RegState = RegState(false, 0),
                        var dataRegReject: Int = 0,
                        var dataRegRejectTime: Long = 0,
                        var lastRoam: Long = 0,
                        var lastInfo: CardLastInfo = CardLastInfo())

/**
 * 是否可用态，与是否漫游态都是猜测状态，不完全可信
 * @param mcc 卡的mcc
 * @param state 卡是否可用
 * @param isRoam 是否漫游态
 */
data class MccState(var mcc: String, var state: Boolean, var isRoam: Boolean = false)
