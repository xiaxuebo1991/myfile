package com.ucloudlink.refact.channel.enabler.simcard

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.telephony.*
import android.telephony.ServiceState.STATE_POWER_OFF
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.ServiceManager.simMonitor
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.AccessEventId.EVENT_SEEDSIM_PHYCARD_DEFAULT_FAIL
import com.ucloudlink.refact.business.cardprovisionstatus.CardProvisionStatus
import com.ucloudlink.refact.business.performancelog.logs.PerfLogSsimEstFail
import com.ucloudlink.refact.business.performancelog.logs.PerfLogVsimEstFail
import com.ucloudlink.refact.business.performancelog.logs.SsimEstFailData
import com.ucloudlink.refact.business.performancelog.logs.VsimFailData
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType
import com.ucloudlink.refact.channel.enabler.*
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.datas.Plmn
import com.ucloudlink.refact.channel.enabler.simcard.watcher.TimeOutWatchBase
import com.ucloudlink.refact.channel.monitors.CardStateMonitor.*
import com.ucloudlink.refact.config.ACCESS_CLEAR_CARD
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.REASON_CHANGE
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logk
import com.ucloudlink.refact.utils.PhoneStateUtil
import rx.Observable
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import java.net.InetAddress
import java.util.*


/**
 * Created by jiaming.liang on 2016/12/1.
 * 卡的数据使能基类
 */
abstract class CardDeBase(val mContext: Context, mLooper: Looper) : IDataEnabler, Handler(mLooper) {
    private val UNKNOWN_STATE = -1
    private val INIT_STATE = 0
    private val INSERT_STATE = 1
    private val READY_STATE = 2
    private val IN_SERVICE_STATE = 3
    private val CONNECTED_STATE = 4
    private val CLOSING_STATE = 5

    private val MSG_ENABLE = 1
    private val MSG_DISABLE = 2

    val MAX_SAME_DENIED_HIT = 3 /*相同拒绝原因，最多接受次数*/


    private var state = INIT_STATE
        get() = getStateByCardState()

    private val CLOSE_CARD_TIMEOUT = 60 * 1000L  //关卡超时

    protected lateinit var timeoutWatcher: TimeOutWatchBase

    private var isLogout = false
    /**
     * 标记需要到网络连上
     * enable时为true
     * 只要一连上就为false
     */
    private var needConnect = false

    @Volatile
    private var enablerClosing = false
        set(value) {
            logd("enablerClosing is changed $field --> $value")
            field = value
        }
    @Volatile
    private var enableWhenDisableFinish = false

    private var nextCard: ArrayList<Card>? = null

    protected val mCard = Card()//本使能对应的卡
    //    var mEnablerStatus = EnablerState.DISABLED
    private var mNetworkState = NetworkInfo.State.DISCONNECTED
    private var mCardStatus: CardStatus
        get() = mCard.status
        set(value) {
            if (mCard.cardType == CardType.VSIM) {
                logk("Action:CloudSimNetStateChange,$value")
            }
            logd("mCard get a new status:$value")
            mCard.status = value
        }

    private var dataReg:Boolean = false
    private var voiceReg:Boolean = false
    private var isDataRoam:Boolean = false
    private var isVoiceRoam:Boolean = false
    private var ipAddr: InetAddress? = null
    private var lastException:EnablerException? = null

    //    val mStatusObservable = PublishSubject<EnablerState>()
    private val mCardStateObservable = BehaviorSubject<CardStatus>()
    private val mNetStatusObservable = BehaviorSubject<NetworkInfo.State>(NetworkInfo.State.DISCONNECTED)
    private val mExceptionObservable = PublishSubject<EnablerException>()
    private val mCardSignalStrenghtOb = PublishSubject<Int>()

    protected var mSignalStrengthLevel = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT
        set(value) {
            if (field == value) return
            logd("mSignalStrengthLevel is changed $field --> $value")
            field = value
        }

    protected var mVoiceState = STATE_POWER_OFF
        set(value) {
            if (field == value) return
            logd("mVoiceState is changed $field --> $value")
            field = value
        }

    private val pm = ServiceManager.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock: PowerManager.WakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "APDU LOCK_0")

    companion object {
        //持续监听并保存卡槽信息(卡imsi,卡状态),网络状态信息,dds信息,
        //        val imsiAtSlot = arrayOf("", "")
        private var mCardStatusAtSlot = arrayOf(CardStatus.ABSENT, CardStatus.ABSENT) //这个记录广播状态
        private var subIdAtSlot = arrayOf(-1, -1)
        var mDefaultNetState: NetworkInfo.State = NetworkInfo.State.UNKNOWN
        var mDunNetState: NetworkInfo.State = NetworkInfo.State.UNKNOWN
        var curDDSSlotId: Int = 0
    }


    override fun getDeType(): DeType {
        return DeType.SIMCARD
    }

    override fun handleMessage(msg: Message) {
        when (state) {//不同状态不同处理
            UNKNOWN_STATE -> {
                loge("UNKNOWN_STATE get a msg msg")
            }
            INIT_STATE -> {
                handleMsgWhenInit(msg)
            }
            INSERT_STATE -> {
                handleMsgWhenInsert(msg)
            }
            READY_STATE -> {
                handleMsgWhenReady(msg)
            }
            IN_SERVICE_STATE -> {
                handleMsgWhenInService(msg)
            }
            CONNECTED_STATE -> {
                handleMsgWhenConnected(msg)
            }
            CLOSING_STATE -> {
                handleMsgWhenClosing(msg)
            }
        }
    }

    override fun isClosing(): Boolean {
        return enablerClosing
    }

    override fun isCardOn(): Boolean {
        return mCard.slot > -1 && mCard.status > CardStatus.ABSENT
    }

    private fun handleMsgWhenConnected(msg: Message) {
        when (msg.what) {
            MSG_ENABLE -> {
                //do nothing?
                loge("enable $mCard when card is In Connected")
            }
            MSG_DISABLE -> {
                enablerClosing = true
                val reason = msg.obj as String
                val keepChannel = msg.arg1 == 1
                val sleepTime = onCloseDataEnabler(reason)
                postDelayed({ uninstallCard(reason, keepChannel) }, sleepTime)

            }
            else -> {
                loge("get unExcept msg when Connect msg.id=${msg.what}")
            }
        }
    }

    private fun handleMsgWhenInService(msg: Message) {//inService后但还没拨号成功
        when (msg.what) {
            MSG_ENABLE -> {
                logk("enable $mCard when card is In Service")
                triggerCall()
            }
            MSG_DISABLE -> {
                enablerClosing = true
                val reason = msg.obj as String
                val keepChannel = msg.arg1 == 1
                val sleepTime = onCloseDataEnabler(reason)
                postDelayed({ uninstallCard(reason, keepChannel) }, sleepTime)

            }
            else -> {
                loge("get unExcept msg when InService msg.id=${msg.what}")
            }
        }
    }

    private fun handleMsgWhenReady(msg: Message) {//ready后还没InService的状态
        when (msg.what) {
            MSG_ENABLE -> {
                logk("enable $mCard when card is Ready")
                triggerCall()
            }
            MSG_DISABLE -> {
                enablerClosing = true
                val reason = msg.obj as String
                val keepChannel = msg.arg1 == 1
                val sleepTime = onCloseDataEnabler(reason)
                postDelayed({ uninstallCard(reason, keepChannel) }, sleepTime)

            }
            else -> {
                loge("get unExcept msg when Ready msg.id=${msg.what}")
            }
        }
    }

    protected open fun triggerCall() {//触发拨号

    }

    private fun handleMsgWhenInsert(msg: Message) {//正在起卡还没到ready的状态
        when (msg.what) {
            MSG_DISABLE -> {
                val reason = msg.obj as String
                val keepChannel = msg.arg1 == 1
                enablerClosing = true
                onCloseDataEnabler(reason)
                uninstallCard(reason, keepChannel)
            }
            else -> {
                loge("get unExcept msg when Insert msg.id=${msg.what}")
            }
        }
    }

    private fun handleMsgWhenClosing(msg: Message) {
        when (msg.what) {
            MSG_ENABLE -> {
                logk("enable ${msg.obj} when card is Closing")
                enableWhenDisableFinish = true//表示当关卡完成后自动重新enable
            }
            else -> {
                loge("get unExcept msg when Closing msg.id=${msg.what}")
            }
        }
    }

    //    private var isNeedClose = false
    //初始化状态是时收到消息
    private fun handleMsgWhenInit(msg: Message) {//初始化的状态,准备卡和触发起卡
        when (msg.what) {
            MSG_ENABLE -> {
                val card = msg.obj as Card
                notifyASS(AccessEventId.EVENT_SEEDSIM_ENABLE)
                getReadyCard(card)
            }
            else -> {
                loge("get unExcept msg when Init msg.id=${msg.what}")
            }

        }
    }

    protected open fun onCloseDataEnabler(reason: String): Long {
        timeoutWatcher.stopWatch()
        UnRegisterRoamObservable()
        UnRegisterDataEnableObservable()
        return 0L
    }

    private var isKeepChannel = false
    protected open fun uninstallCard(reason: String, isKeepChannel: Boolean): Boolean {//表示不需要等待
        logv("uninstallCard")
        lockCPU()
        clearTOTask()
        startTOTask(EnablerException.CLOSE_CARD_TIMEOUT, CLOSE_CARD_TIMEOUT, {
            if (isCardOn()) {//检查是否卡还在
                if (mCard.subId != -1) stopCardServiceStateListen(mCard.subId)
//                enablerClosing = false
                updateCardState(CardStatus.ABSENT)
            }
            return@startTOTask false
        })
        if (mCard.cardType != CardType.PHYSICALSIM) {
            val status = mCard.status
            this@CardDeBase.isKeepChannel = isKeepChannel
            ServiceManager.cardController.pullOutCard(mCard, isKeepChannel).subscribe({
                logd("status:$status")
                if (status <= CardStatus.POWERON) {//如果关卡时还没ready 主动发起absent
                    val delay: Long = if (status == CardStatus.POWERON) 200 else 0
                    logd("auto send Absent:$status delay $delay ms")
                    postDelayed({ updateCardState(CardStatus.ABSENT) }, delay)
                }
            }, {
                loge("uninstallCard failed: $it")
            })
            return false
        } else {
            updateCardState(CardStatus.ABSENT)
            return true
        }
    }


    protected abstract fun getReadyCard(card: Card)

    override fun enable(cardList: ArrayList<Card>): Int {
        removeMessages(MSG_DISABLE)
        logv("get enable $cardList Enabler state:$state")
        nextCard = cardList

        isLogout = false
        needConnect = true
        val card = cardList[0]

        sendMsg(MSG_ENABLE, 0, 0, card)
        return 0
    }

    private fun removeListen() {
        logd("removeListen")
        simMonitor.removeStatuListen(networkListen)
        simMonitor.removeStatuListen(cardListen)
        simMonitor.removeScanNwlockListen(scanNwListen)
    }

    /**
     * 表示释放所有
     */
    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        removeMessages(MSG_ENABLE)
        logk("disable due to $reason - EnablerState:$state - $mCard ")
        printCloudSimLog(" Action:CloudsimStop,$reason")

        PreferredNetworkType.setPreferredNetworkType(mCard.cardType, PreferredNetworkType.NETWORKTYPE_SET_CANCEL)

        nextCard = null

        if (reason == REASON_CHANGE) {
//            logd("REASON_CHANGE mCard.subId " + mCard.subId)
//            if (mCard.subId > 0) {
//                stopCardServiceStateListen(mCard.subId)
//            }
//            removeListen()
        } else if (reason.contains(ACCESS_CLEAR_CARD)) {
            isLogout = true
        }

        needConnect = false
        CardProvisionStatus.sendSettingSimCmd(mCard.cardType, CardProvisionStatus.CARD_CLOSE_MONITOR)
        enableWhenDisableFinish = false

        if (isCardOn() || (mCard.cardType == CardType.PHYSICALSIM)) {
            sendMsg(MSG_DISABLE, if (isKeepChannel) 1 else 0, 0, reason)
            val isSeed = mCard.cardType != CardType.VSIM
            notifyASS(if (isSeed) AccessEventId.EVENT_SEEDSIM_DISABLE else AccessEventId.EVENT_CLOUDSIM_DISABLE)
            return mCard.subId
        } else {
            return -1
        }

    }

    //更新卡状态并发出通知,如果ready,自动注册phoneListener
    @Synchronized
    protected fun updateCardState(newStatus: CardStatus) {
        logv(" updateCardState thread:${Thread.currentThread().id} in ")
        try {
            val oldCardStatus = mCardStatus
            logv("updateCardState newStatus :$newStatus $mCard")
            if (isCardOn() || newStatus == CardStatus.INSERTED) {
                if (oldCardStatus != newStatus) {
                    if (oldCardStatus >= CardStatus.IN_SERVICE && newStatus <= CardStatus.LOAD && newStatus != CardStatus.ABSENT) {
                        return loge("unExpect state update current:$oldCardStatus newStatus:$newStatus")
                    }

                    if (newStatus == CardStatus.READY || newStatus == CardStatus.LOAD) {//ready
                        mCardStatus = newStatus
                        if (mCard.subId < 0) {
                            loge("please set mCard.subId before updateCardState ready ${mCard.subId}")
                        } else {
                            startCardServiceStateListen(mCard.subId)
                        }

                    } else if (newStatus == CardStatus.ABSENT) { //absent
                        stopTOTask(EnablerException.CLOSE_CARD_TIMEOUT)
                        onCardAbsent(enablerClosing, isLogout, isKeepChannel)
                        unLockCPU()
                        enablerClosing = false//优先设置isClosing=false 不然后面enable是会认为还在关闭中

                        if (mCard.subId < 0) {
                            loge("please set mCard.subId after updateCardState absent ${mCard.subId}")
                        } else {
                            stopCardServiceStateListen(mCard.subId)
                        }

                        mCard.reset()

                        updateNetState(NetworkInfo.State.DISCONNECTED)

                    } else if (newStatus == CardStatus.IN_SERVICE) {//in Service
                        onCardInService()

                    } else if (newStatus == CardStatus.OUT_OF_SERVICE) {//out of service.

                        if (mCard.cardType == CardType.VSIM) {
                            logd("OUT_OF_SERVICE to start NetOperator")
                            //CrossBorder.netOperatorTimeout = 1000
                            //CrossBorder.crossBorderCheck()
                            notifyASS(AccessEventId.EVENT_CLOUDSIM_OUT_OF_SERVICE)
                        }

                    }

                    mCardStatus = newStatus
                    logd("updateCardState :$newStatus")
                    mCardStateObservable.onNext(newStatus)

                    //关闭完成是否要重新启动
                    logd("enableWhenDisableFinish:$enableWhenDisableFinish")
                    if (newStatus == CardStatus.ABSENT && enableWhenDisableFinish) {
                        enableWhenDisableFinish = false
                        val nextCardList = nextCard
                        nextCardList
                                ?: return loge("need to enable card when disable finish but nextCard is null")

                        enable(nextCardList)
                    }

                } else {
                    logv("mCardStatus == newStatus")
                }
            } else {
                mCardStatus = CardStatus.ABSENT
                loge(" updateCardState mCard is not init ,can not update Card State card:$mCard")
            }
        } finally {
            logv(" updateCardState thread:${Thread.currentThread().id} out ")
        }
    }

    private fun printCloudSimLog(msg: String) {
        if (mCard.cardType == CardType.VSIM) {
            logd(msg)
        }
    }

    protected open fun onCardReady() {
        if (needConnect) {
            //检查数据开关是否打开
            val isEnabled = PhoneStateUtil.isMobileDataEnabled(mContext, mCard.subId)
            if (isEnabled) {
                triggerCall()
            }
        }
    }

    private val mContentObserver: ContentObserver = object : ContentObserver(this) {
        override fun onChange(selfChange: Boolean, uri: Uri, userId: Int) {
            val pathSegment = uri.lastPathSegment
            if (pathSegment.contains(Settings.Global.DATA_ROAMING)) {
                val isRoamEnabler = Settings.Global.getInt(mContext.contentResolver, pathSegment) != 0
                onRoamEnablerChange(isRoamEnabler)

            } else if (pathSegment.contains(Settings.Global.MOBILE_DATA)) {
                if (mCard.subId != -1) {
                    val isDataEnable = Settings.Global.getInt(mContext.contentResolver, pathSegment) != 0
                    onDataEnablerChanged(isDataEnable)
                }
            }
        }
    }

    private fun registerRoamObservable(subId: Int) {
        val uri = Settings.Global.getUriFor(Settings.Global.DATA_ROAMING + subId)
        mContext.contentResolver.registerContentObserver(uri, true, mContentObserver)
    }

    private fun UnRegisterRoamObservable() {
        mContext.contentResolver.unregisterContentObserver(mContentObserver)
    }

    private fun registerDataEnableObservable(subId: Int) {
        val uri = Settings.Global.getUriFor(Settings.Global.MOBILE_DATA + subId)
        mContext.contentResolver.registerContentObserver(uri, true, mContentObserver)
    }

    private fun UnRegisterDataEnableObservable() {
        mContext.contentResolver.unregisterContentObserver(mContentObserver)
    }


    open protected fun onRoamEnablerChange(roamEnabler: Boolean) {

    }

    protected open fun onCardInService() {}

    /**
     * 通知状态机
     */
    protected fun notifyASS(eventId: Int): Unit {
        ServiceManager.accessEntry.notifyEvent(eventId)
    }

    //更新网络状态,并发出通知
    protected fun updateNetState(newState: NetworkInfo.State) {
        if (isCardOn() || newState == NetworkInfo.State.DISCONNECTED) {
            if (mNetworkState != newState) {
                mNetworkState = newState
                mNetStatusObservable.onNext(newState)
                onMyCardNetChange(newState)
                if (mCard.cardType == CardType.VSIM) {
                    logk("Action:CloudSimNetStateChange,$mNetworkState")
                } else {
                    logd("updateNetState :$mNetworkState")
                }
            }
        }
    }

    protected open fun onMyCardNetChange(newState: NetworkInfo.State) {
//        if (newState == NetworkInfo.State.CONNECTED) {
//            stopTOTask(EnablerException.CONNECT_TIMEOUT)
//        }
    }

    private val networkListen: NetworkStateListen = object : NetworkStateListen {
        override fun hashCode(): Int {
            return 1234567899
        }

        override fun NetworkStateChange(ddsId: Int, networkState: NetworkInfo.State, type: Int, ifName: String, isExistIfNameExtra: Boolean, subId: Int) {
            logv("NetStatuChange isNetAvailable:$ddsId, $networkState, $type, $ifName, $isExistIfNameExtra, $subId")
            curDDSSlotId = ddsId
            if (type == ConnectivityManager.TYPE_MOBILE) {
                mDefaultNetState = networkState
            } else if (type == ConnectivityManager.TYPE_MOBILE_DUN) {
                mDunNetState = networkState
            } else {
                return
            }
            post { onNetStateUpdated(networkState, type) }
        }
    }

    //注册监听网络变化广播回调
    private fun addNetworkListen() {
        simMonitor.addNetworkStateListen(networkListen)
    }


    /**
     * 当卡变为absent状态时触发
     * @param enablerClosing 是否主动关闭Enabler
     */
    protected open fun onCardAbsent(enablerClosing: Boolean, logout: Boolean, keepChannel: Boolean) {

    }

    protected open fun onNetStateUpdated(networkState: NetworkInfo.State, type: Int) {
    }

    private val cardListen: CardStateListen = object : CardStateListen {
        override fun hashCode(): Int {
            return 12345874
        }

        override fun CardStateChange(slotId: Int, subId: Int, stateExtra: Int) {
            logv("CardStatuChange slotId:$slotId, $subId, $stateExtra")
            var simState = CardStatus.ABSENT

            if (subId < 0) {
                loge("subid $subId invalid!!!")
                return
            }

            if (SIM_STATE_READY == stateExtra) {
                simState = CardStatus.READY
            } else if (SIM_STATE_ABSENT == stateExtra) {
                simState = CardStatus.ABSENT
            } else if (SIM_STATE_LOAD == stateExtra) {
                simState = CardStatus.LOAD //
            } else if (SIM_STATE_NOT_READY == stateExtra) {
                simState = CardStatus.POWERON//表示notReady状态
            }
            mCardStatusAtSlot[slotId] = simState
            logv("updateSimState:slotId:$slotId,currSubId:$subId")

            if (subId < 0x40000000) {
                logv("Card State Change mCard:$mCard")
                subIdAtSlot[slotId] = subId
            } else {
                subIdAtSlot[slotId] = -1
            }

            post { onSlotStateUpdate(slotId, mCardStatusAtSlot[slotId], subId) }

        }
    }
    private val scanNwListen: ScanNwlockListen = object : ScanNwlockListen {
        override fun hashCode(): Int {
            return 12325874
        }

        override fun onScanNwChanged(phoneId: Int, plmns: ArrayList<Plmn>?) {

        }

    }

    //注册监听卡槽变化广播回调
    private fun addCardStateListen() {
        logv("add cardListen:$cardListen")
        simMonitor.addCardStateListen(cardListen)
    }
    open protected fun onDataEnablerChanged(ret: Boolean) {}

    override fun getCardState(): CardStatus {

        if (!isCardOn()) {
            return CardStatus.ABSENT
        }
        if (mCard.subId == -1) {
            return mCardStatus
        }
        val imsi = getImsiBySubId(mCard.subId)
        if (TextUtils.isEmpty(imsi)) {
            return mCardStatus
        }
        if (imsi != mCard.imsi) {
            logd("getCardState :imsi != mCard.imsi:" + imsi)
            return CardStatus.ABSENT
        }
        return mCardStatus
    }

    protected fun getImsiBySubId(subId: Int): String? {
        val telephonyManager = TelephonyManager.from(mContext)
        val imsi = telephonyManager.getSubscriberId(subId)
        return imsi
    }

    override fun cardStatusObser(): Observable<CardStatus> {
        return mCardStateObservable
    }

    override fun getNetState(): NetworkInfo.State {
        if (!isCardOn()) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
        } else if (mCard.subId == -1) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
        } /*else {
            val imsi = getImsiBySubId(mCard.subId)
            if (imsi != mCard.imsi) {
                updateNetState(NetworkInfo.State.DISCONNECTED)
            }
        }*/
        return mNetworkState
    }

    override fun netStatusObser(): Observable<NetworkInfo.State> {
        return mNetStatusObservable
    }

    override fun cardSignalStrengthObser(): Observable<Int> {
        return mCardSignalStrenghtOb
    }

    override fun getDataEnableInfo(): DataEnableInfo {
         return DataEnableInfo(iccid = (if(mCard.iccId != null) mCard.iccId else "")!!, imsi = mCard.imsi, ip = 0, lastException = lastException,
                 dataReg = dataReg, dataRoam = isDataRoam, voiceReg = voiceReg, voiceRoam = isVoiceRoam, dataConnect = (ipAddr != null),
                 singleStrength = mSignalStrengthLevel)
    }

    override fun getCard(): Card {
        return mCard
    }

    @SuppressLint("UseSparseArrays")
    private val listenMap = HashMap<Int, UcPhoneStateListener?>()
    val handlerThread = HandlerThread("phoneListen")
    var regListenTime: Long = 0
    //    private val roamSubIdMap = SparseBooleanArray(2)
    private fun startCardServiceStateListen(subId: Int) {
        synchronized(listenMap) {
            logv("startCardServiceStateListen subId:$subId listen:${listenMap[subId]}")
            if (mCard.cardFirstReadyFlag || listenMap[subId] == null) {
                mCard.cardFirstReadyFlag = false
                //CardSetMonitor.setCardStatus(mContext, mCard, TelephonyManager.from(mContext))
                PreferredNetworkType.setPreferredNetworkType(mCard.cardType, PreferredNetworkType.NETWORKTYPE_SET)
                val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val UcPsListen = UcPhoneStateListener(subId, handlerThread.looper)
                listenMap[subId] = UcPsListen
                logd("UcPsListen:$UcPsListen")
                mPhone.listen(UcPsListen, PhoneStateListener.LISTEN_SERVICE_STATE or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or PhoneStateListener.LISTEN_DATA_ACTIVITY or PhoneStateListener.LISTEN_CELL_LOCATION)
                regListenTime = SystemClock.uptimeMillis()
                Thread.sleep(10)//fixme 暂时延时,可以测试下,看能不能去掉
                post { registerDataEnableObservable(subId) }
                post { registerRoamObservable(subId) }
                post { onCardReady() }//放这里是为了保证这个onReady 只被执行一次
            } else {
                loge("start a card Service listen which has listened")
            }
        }
    }

    private fun stopCardServiceStateListen(subId: Int) {
        synchronized(listenMap) {
            logv("stopCardServiceStateListen subId:$subId")
            if (listenMap[subId] != null) {
                val ucPhoneStateListener = listenMap[subId]
                val mPhone = ServiceManager.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                PreferredNetworkType.setPreferredNetworkType(mCard.cardType, PreferredNetworkType.NETWORKTYPE_SET_CANCEL)
                mPhone.listen(ucPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                ucPhoneStateListener?.stopListen()
                listenMap[subId] = null
//            roamSubIdMap.delete(subId)
            } else {
                loge("stop a card Service listen which no listen")
            }
        }
    }

    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        when (event) {
            DataEnableEvent.EVENT_NET_FAIL -> notifyException(EnablerException.EXCEPTION_CARD_NET_FAIL)


            DataEnableEvent.EVENT_PHY_TO_SOFT ->{
                //物理卡切软卡
                //onDataEnablerChanged(false)
                notifyException(EnablerException.EXCEPTION_CARD_NET_FAIL)

            }

            DataEnableEvent.EVENT_MODEM_RESET -> handleModemReset()
        }

    }

    private fun handleModemReset() {
        if (isCardOn()) {
            onCardCrash()
        }
    }

    private inner class UcPhoneStateListener(subId: Int, looper: Looper) : UcPhoneStateListenerWrapper(subId, looper) {

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            if (isCardOn() && mCard.subId == uSubId) {
                mSignalStrengthLevel = signalStrength.level
                mCardSignalStrenghtOb.onNext(mSignalStrengthLevel)
            }
        }

        var isStop = false

        fun stopListen() {
            isStop = true
        }

        override fun onServiceStateChanged(state: ServiceState) {
            if (isStop) {
                return loge("is stop listen onServiceStateChanged")
            }
            logv("onServiceStateChanged subId:$uSubId status:$state ")
            if (uSubId < 0) {
                loge("subid invalid!! $uSubId  do not process!")
                return
            }
            logd("perflog onServiceStateChanged======$state")
            dataReg = (state.dataRegState == ServiceState.STATE_IN_SERVICE)
            voiceReg = (state.voiceRegState == ServiceState.STATE_IN_SERVICE)
            isDataRoam = state.dataRoaming
            isVoiceRoam = state.voiceRoaming

            if (isCardOn() && mCard.subId == uSubId && !isClosing()) {
                var newState: CardStatus? = null
                if (state.dataRegState == ServiceState.STATE_IN_SERVICE && mCardStatus >= CardStatus.READY) {
                    newState = CardStatus.IN_SERVICE
                } else if (state.dataRegState == ServiceState.STATE_OUT_OF_SERVICE && mCardStatus >= CardStatus.READY) {
                    newState = CardStatus.OUT_OF_SERVICE
                } else if (state.dataRegState == ServiceState.STATE_EMERGENCY_ONLY) {
                    newState = CardStatus.EMERGENCY_ONLY
                }

                if (newState != null) {
                    updateCardState(newState)
                }

                mVoiceState = state.voiceRegState
            }
            post { onRegisterStateChange(uSubId, state) }

        }

        /**
         * Callback invoked when connection status changes.
         *
         * @see TelephonyManager#DATA_DISCONNECTED
         * @see TelephonyManager#DATA_CONNECTING
         * @see TelephonyManager#DATA_CONNECTED
         * @see TelephonyManager#DATA_SUSPENDED
         */
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
//            mDataState = status
            logv("onDataConnectionStateChanged subId:$uSubId status:$state networkType:$networkType")

            post { onNetConnectionStateChanged(uSubId, state, networkType) }

        }

        override fun onCellLocationChanged(location: CellLocation?) {//位置更新
//            Log.d(TAG, "onCellLocationChanged () called")
//            updateCellInfo()
        }
    }

    protected open fun onNetConnectionStateChanged(mSubId: Int, state: Int, networkType: Int) {
        if(state == TelephonyManager.DATA_CONNECTED){
            val connectManager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val link = connectManager.getLinkProperties(networkType)
            logd("get link from network $networkType $link")
            if(link != null && link.addresses != null && link.addresses.size != 0){
                ipAddr = link.addresses[0]
            }
        }else{
            ipAddr = null
        }
    }

    protected open fun onRegisterStateChange(subId: Int, state: ServiceState) {

    }

    protected fun checkIsCDMA(subId: Int): Boolean {
        val telephonyManager = TelephonyManager.from(mContext)
        val phoneType = telephonyManager.getCurrentPhoneType(subId)
        logd("getCurrentPhoneType subid $subId phoneType:$phoneType")
        return phoneType == TelephonyManager.PHONE_TYPE_CDMA
    }


    //当卡槽发生变化时更新
    @SuppressLint("NewApi")
    private fun onSlotStateUpdate(slotId: Int, cardStatus: CardStatus, subId: Int) {

        if (!isCardOn()) {
            loge("onSlotStateUpdate but card is not on")
            updateCardState(CardStatus.ABSENT)
            return
        }

        if (slotId == mCard.slot) {
            logd("cardStatus update :$cardStatus")

            CardProvisionStatus.sendSettingSimCmd(mCard.cardType, CardProvisionStatus.CARD_STATE_CHANGE)
            if ((cardStatus == CardStatus.READY || cardStatus == CardStatus.LOAD) && subId < 0x40000000) {

                val telephonyManager = TelephonyManager.from(mContext)
                val imsi: String? = telephonyManager.getSubscriberId(subId)
                logv("updateSimState:imsi:$imsi")
                if (!TextUtils.isEmpty(mCard.imsi) && mCard.imsi == imsi) {
                    val numeric = ServiceManager.systemApi.getIccOperatorNumericForData(subId)
                    logv("$imsi -> numeric:$numeric")

                    if (!TextUtils.isEmpty(numeric) && imsi.startsWith(numeric)) {
                        mCard.numeric = numeric
                    } else {
                        if (!TextUtils.isEmpty(imsi)) {
                            if (mCard.cardType == CardType.VSIM) {
                                mCard.numeric = imsi.substring(0, 3 + Configuration.vsim_mnc_numeric)
                            } else {
                                mCard.numeric = imsi.substring(0, 3 + Configuration.softsim_mnc_numeric)
                            }
                        }
                    }

                    if (mCard.cardType == CardType.PHYSICALSIM) {
                        //判断是否为电信卡,不支持电信卡作种子卡
                        val isCdma = checkIsCDMA(subId)
                        if (isCdma || !ServiceManager.systemApi.checkCardCanBeSeedsim(mCard.slot)) {
                            notifyException(EnablerException.EXCEPTION_UNSUPPORT_CDMA_PHY_CARD)
                            return
                        }
                    }

                    mCard.subId = subId
                    updateCardState(cardStatus)//ready/load
                }

            } else if (cardStatus == CardStatus.POWERON) {//这个表示广播上报No_ready
                if (mCardStatus < CardStatus.READY) {
                    logd("close from code")
                } else {
                    updateCardState(CardStatus.POWERON)
                    logd("close from setting")
                }
            } else if (cardStatus == CardStatus.ABSENT) {
                if (enablerClosing) {
                    updateCardState(CardStatus.ABSENT)
                } else {
                    /*
                    这里属于卡异常关闭状态,有可能出现误报，也可能确实是卡异常
                    到收到这个异常状态，先检查一下对应卡槽状态，如果非absent则忽略 ，继续观测
                    如果确实是卡异常，报到状态机处理
                    
                    如果是软卡模式，卡状态为poweron 此时有可能会获得一个absent（原来物理卡被挤下时发出的），这个此时这个应该能获取到suid的
                    
                     */

                    val powerOnState = mCard.status == CardStatus.POWERON

                    val needToIgnore = powerOnState

                    val telephonyManager = TelephonyManager.from(mContext)
                    val simState = telephonyManager.getSimState(mCard.slot)
                    if (simState == TelephonyManager.SIM_STATE_ABSENT && !needToIgnore) {
                        loge("sim card crash!")
                        onCardCrash()
                    } else {
                        loge("is not closing ignore Absent simState = $simState ")
                    }

                }
//                updateEnablerState(EnablerState.DISABLED)
            }
        } else {
            loge("slotId != mCard.slot cardStatus:$cardStatus")
        }
    }

    protected open fun onCardCrash() {
        if (getCardState() > CardStatus.POWERON) {
            loge("direct change mCard status to POWER ON due to card crash")
            mCard.status = CardStatus.POWERON
        }

        val tempCard = mCard.clone()
        notifyException(EnablerException.SIM_CRASH)

        //应等待卡关闭完成再发出卡card crash 的通知
        cardStatusObser().subscribeWith {
            onNext {
                if (it == CardStatus.ABSENT) {
                    if (!this.subscriber.isUnsubscribed) this.subscriber.unsubscribe()
                    notifyASS(AccessEventId.EVENT_SEEDSIM_CRASH)

                    if (needConnect) {
                        val cardList = ArrayList<Card>()
                        cardList.add(tempCard)
                        enable(cardList)
                    }

                }
            }
        }
    }

    //根据卡的状态,网络变化,更新状态
    private fun getStateByCardState(): Int {
        var state = UNKNOWN_STATE
        if (!isCardOn()) {
            state = INIT_STATE //初始化状态
        } else if (enablerClosing) {
            state = CLOSING_STATE
        } else if (mCardStatus > CardStatus.ABSENT && mCardStatus < CardStatus.READY) {
            state = INSERT_STATE
        } else if (mCardStatus >= CardStatus.READY && mCardStatus < CardStatus.IN_SERVICE) {
            state = READY_STATE
        } else if (mCardStatus == CardStatus.IN_SERVICE && mNetworkState != NetworkInfo.State.CONNECTED) {
            state = IN_SERVICE_STATE
        } else if (mCardStatus == CardStatus.IN_SERVICE && mNetworkState == NetworkInfo.State.CONNECTED) {
            state = CONNECTED_STATE
        }
        logv("getStateByCardState $state  mCardStatus :$mCardStatus  mNetworkState:$mNetworkState")
        return state
    }

    private fun sendMsg(what: Int, arg1: Int = 0, arg2: Int = 0, obj: Any?): Unit {
        obtainMessage(what, arg1, arg2, obj).sendToTarget()
    }

    /**
     * 通知异常事件到异常监听
     * @param e 异常事件
     * @param msg 携带打印的数据
     * @param isCloseCard 是否要disable 默认true
     */
    open protected fun notifyException(e: EnablerException, msg: String = "", isCloseCard: Boolean = true) {
        loge("notifyException :$e  msg:$msg")

        if (isCloseCard) {
//            if (mCard.cardType == CardType.SOFTSIM) {
//                Configuration.softCartExceptFlag = true
//            }
            disable("Exception($e)") //出现异常,停止任务

            if (e == EnablerException.SIM_CRASH) {
                logv("needConnect")
                needConnect = true
            }
        }

        mExceptionObservable.onNext(e)

        val isSeed = mCard.cardType != CardType.VSIM
        when (e) {
        //EnablerException.EXCEP_PHY_CARD_IS_NULL -> notifyASS(AccessEventId.EVENT_SEEDSIM_CARD_LOST)
            EnablerException.INSERT_SOFT_SIM_TIMEOUT -> notifyASS(if (isSeed) AccessEventId.EVENT_SEEDSIM_INSERT_TIMEOUT else AccessEventId.EVENT_CLOUDSIM_INSERT_TIMEOUT)
            EnablerException.ADD_SOFT_SIM_TIMEOUT -> notifyASS(if (isSeed) AccessEventId.EVENT_SEEDSIM_ADD_TIMEOUT else AccessEventId.EVENT_CLOUDSIM_ADD_TIMEOUT)
            EnablerException.READY_TIMEOUT -> notifyASS(if (isSeed) AccessEventId.EVENT_SEEDSIM_READY_TIMEOUT else AccessEventId.EVENT_CLOUDSIM_READY_TIMEOUT)
            EnablerException.INSERVICE_TIMEOUT -> notifyASS(if (isSeed) AccessEventId.EVENT_SEEDSIM_INSERVICE_TIMEOUT else AccessEventId.EVENT_CLOUDSIM_INSERVICE_TIMEOUT)
            EnablerException.CONNECT_TIMEOUT -> notifyASS(if (isSeed) AccessEventId.EVENT_SEEDSIM_CONNECT_TIMEOUT else AccessEventId.EVENT_CLOUDSIM_CONNECT_TIMEOUT)
            EnablerException.EXCEP_PHY_CARD_DEFAULT_LOST -> notifyASS(EVENT_SEEDSIM_PHYCARD_DEFAULT_FAIL)
            EnablerException.DATA_ENABLE_CLOSED -> {
                notifyASS(AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE)
                ServiceManager.accessMonitor.phoneDataDisable()
            }

            else -> {
                loge("get a Exception but not notify ASS")
            }
        }
        lastException = e

        if(isSeed){
            PerfLogSsimEstFail.create(0,0, SsimEstFailData(iccid = (if(mCard.iccId != null) mCard.iccId else "")!!, imsi = mCard.imsi,
                    errType = e, errCode = e.reason.errorCode, psReg = dataReg, psRoam = isDataRoam, csReg = voiceReg, csRoam = isVoiceRoam))
        }else {
            PerfLogVsimEstFail.create(0,0, VsimFailData(iccid = (if(mCard.iccId != null) mCard.iccId else "")!!, imsi = mCard.imsi,
                    exception = e, errType = 0, errCode = e.reason.errorCode, psReg = dataReg, psRoam = isDataRoam, csReg = voiceReg, csRoam = isVoiceRoam,
                    upMbr = 0, downMbr = 0))
        }
    }

    override fun exceptionObser(): Observable<EnablerException> {
        return mExceptionObservable
    }

    protected fun logd(log: String) {
        JLog.logd(this.javaClass.simpleName, log)
    }

    protected fun logv(log: String) {
        JLog.logd(this.javaClass.simpleName, log)
    }

    protected fun loge(log: String) {
        JLog.loge(this.javaClass.simpleName, log)
    }

//    inner class CardStateHandler(looper: Looper) : Handler(looper) {
//
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            val newState = msg.obj as CardStatus
////            updateCardStateHandler(newState)
//        }
//    }

    //超时控制任务
    private val mTOTimer = Timer()
    @Volatile
    private var mTOTask: TOTask? = null

    private fun clearTOTask() {
        mTOTask?.cancel()
        mTOTask = null

    }

    /**
     * 开始计时,如果时间到,就会发出对应的异常
     * @param e 异常枚举
     * @param timeout 超时时间
     */
    protected fun startTOTask(e: EnablerException, timeout: Long, doWhenTimeOut: () -> Boolean = { true }): Boolean {//返回false 表示有任务进行中,开始失败
        logv("startTOTask $e")
        if (mTOTask == null) {
            mTOTask = TOTask(e, doWhenTimeOut)
            mTOTimer.schedule(mTOTask, timeout)
            return true
        } else {
            val exception = mTOTask?.getException()
            loge("startTOTask unExpect TOTask is not null current:$exception  which one want to start $e")
            return false
        }
    }

    /**
     * @param e 作为一个标记,标记关闭对应TOTask
     */
    protected fun stopTOTask(e: EnablerException) {
        logv("stopTOTask $e")
        val task = mTOTask
        if (task != null) {
            val exception = task.getException()
            if (exception == e) {
                task.cancel()
                mTOTask = null
            } else {
                loge("stopTOTask wrong task current: $exception  which one want to stop $e")
            }
        } else {
            loge("stopTOTask unExpect TOTask is null")

        }
    }

    private inner class TOTask(val e: EnablerException, val doWhenTimeOut: () -> Boolean) : TimerTask() {
        override fun run() {
            if (ServiceManager.seedCardEnabler.getDeType() == DeType.WIFI
                    && (e == EnablerException.INSERVICE_TIMEOUT || e == EnablerException.CONNECT_TIMEOUT)) {
                //enable 云卡时，如果种子通道为wifi
                logv("wifiEnabler as seedEnabler ignore $e")

            } else {
                val isCloseCard = doWhenTimeOut.invoke()
                notifyException(e, "", isCloseCard)

            }
            mTOTask = null
        }

        fun getException(): EnablerException {
            return e
        }
    }

    protected open fun initWatcher() {
        val watchThread = HandlerThread("${this.javaClass.simpleName}-watchThread")
        watchThread.start()
        timeoutWatcher = TimeOutWatchBase(watchThread.looper, this, "${this.javaClass.simpleName}-TO")
        timeoutWatcher.exceptionObser.asObservable().subscribe {
            notifyException(it)
        }
    }

    init {
        addNetworkListen()
        addCardStateListen()
        simMonitor.addScanNwlockListen(scanNwListen)

        handlerThread.start()
        initWatcher()
    }


    private fun lockCPU(timeout: Long = 6000) {
        wakeLock.setReferenceCounted(false)
        logd("cardDebase lockCPU $wakeLock")
        wakeLock.acquire(timeout)
    }

    private fun unLockCPU() {
        logd("cardDebase unLockCPU $wakeLock")
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

}


