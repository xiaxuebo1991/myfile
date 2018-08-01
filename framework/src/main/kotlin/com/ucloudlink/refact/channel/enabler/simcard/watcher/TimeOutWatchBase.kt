package com.ucloudlink.refact.channel.enabler.simcard.watcher

import android.net.NetworkInfo
import android.os.DeadObjectException
import android.os.Looper
import android.os.Message
import com.android.internal.util.IState
import com.android.internal.util.State
import com.android.internal.util.StateMachine
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId.*
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.monitors.AirplaneModeMonitor
import com.ucloudlink.refact.channel.monitors.WifiReceiver2
import com.ucloudlink.refact.utils.JLog
import rx.Subscription
import rx.lang.kotlin.PublishSubject
import java.util.*

/**
 * Created by jiaming.liang on 2017/5/4.
 */
open class TimeOutWatchBase(looper: Looper, private val dataEnabler: IDataEnabler, name: String) : StateMachine(name, looper) {
    private var ddsSubscribe: Subscription? = null
    private var wifiSubscribe: Subscription? = null
    private var phonecallSubscribe: Subscription? = null
    private var airPhoneModeSubscribe: Subscription? = null
    private var dataSwitchSubscribe: Subscription? = null

    private var dataStateSubscribe: Subscription? = null
    private var cardNetSubscribe: Subscription? = null

    private val EVENT_CMD = 0x10000
    open protected val EVENT_EXCEPTION = 0x0100
    private val EVENT_TIMEOUT = 0x1000
    private val EVENT_STATE_CHANGE = 0x0010

    /*本状态机中的消息---start----*/
    private val EVENT_START_WATCH = EVENT_CMD or 1
    private val EVENT_STOP_WATCH = EVENT_CMD or 2
    private val EVENT_ENABLE_CARD = EVENT_CMD or 3

    private val EVENT_DDS_CHANGE = EVENT_EXCEPTION or 1
    private val EVENT_WIFI_STATE_CHANGE = EVENT_EXCEPTION or 2
    private val EVENT_PHONE_CALL_STATE_CHANGE = EVENT_EXCEPTION or 3
    private val EVENT_AIRPHONE_MODE_STATE_CHANGE = EVENT_EXCEPTION or 4
    private val EVENT_DATA_SWITCH_STATE_CHANGE = EVENT_EXCEPTION or 5
    protected val EVENT_DUN_STATE_CHANGE = EVENT_EXCEPTION or 6

    private val EVENT_ADD_TIMEOUT = EVENT_TIMEOUT or 1
    private val EVENT_READY_TIMEOUT = EVENT_TIMEOUT or 2
    private val EVENT_IN_SERVICE_TIMEOUT = EVENT_TIMEOUT or 3
    private val EVENT_DATA_CALL_TIMEOUT = EVENT_TIMEOUT or 4

    private val EVENT_CARD_STATE_CHANGE = EVENT_STATE_CHANGE or 1
    private val EVENT_CARD_NET_CHANGE = EVENT_STATE_CHANGE or 2

//    private val events= intArrayOf()
    /*本状态机中的消息---start----*/

    private var isMultiCountryCard = false
    
    private val MAX_ADD_TIME = 20 * 1000L //开始到执行powerOn成功的超时
    private val MAX_READY_TIME = 15 * 1000L //开始到READY 成功的超时
    private var MAX_IN_SERVICE_TIME = 180 * 1000L //开始到in service 成功的超时  180
        get() {
            return (if (isMultiCountryCard) 300 else 180) * 1000L
        }
    private val MAX_DATA_CALL_TIME = 60 * 1000L //开始到拨号成功的超时

    val exceptionObser = PublishSubject<EnablerException>()

    private var isCloudSim: Boolean = true
        get() {
            return dataEnabler.getCard().cardType == CardType.VSIM
        }
    private var ACCESS_EVENT_ADD_TIMEOUT: Int = EVENT_SEEDSIM_INSERT_TIMEOUT
        get() {
            return if (isCloudSim) EVENT_CLOUDSIM_INSERT_TIMEOUT else EVENT_SEEDSIM_INSERT_TIMEOUT
        }

    private var ACCESS_EVENT_READY_TIMEOUT: Int = EVENT_CLOUDSIM_READY_TIMEOUT
        get() {
            return if (isCloudSim) EVENT_CLOUDSIM_READY_TIMEOUT else EVENT_SEEDSIM_READY_TIMEOUT
        }

    private var ACCESS_EVENT_IN_SERVICE_TIMEOUT: Int = EVENT_CLOUDSIM_INSERVICE_TIMEOUT
        get() {
            return if (isCloudSim) EVENT_CLOUDSIM_INSERVICE_TIMEOUT else EVENT_SEEDSIM_INSERVICE_TIMEOUT
        }

    private var ACCESS_EVENT_SETUP_CALL_TIMEOUT: Int = EVENT_CLOUDSIM_CONNECT_TIMEOUT
        get() {
            return if (isCloudSim) EVENT_CLOUDSIM_CONNECT_TIMEOUT else EVENT_SEEDSIM_CONNECT_TIMEOUT
        }

    protected var DDSubId = -1
        set(value) {
            if (field != value) logv("DDSubId changed old:$field new:$value")
            field = value
        }
    protected var isWifiOn = false
        set(value) {
            if (field != value) logv("isWifiOn changed old:$field new:$value")
            field = value
        }
    protected var isPhoneCalling = false
        set(value) {
            if (field != value) logv("isPhoneCalling changed old:$field new:$value")
            field = value
        }
    protected var isAirModeOn = false
        set(value) {
            if (field != value) logv("isAirModeOn changed old:$field new:$value")
            field = value
        }

    //可去掉
    protected var isDataSwitchOn = true
        set(value) {
            if (field != value) logv("isDataSwitchOn changed old:$field new:$value")
            field = value
        }
//    protected var isDataRoamOn = true    //漫游开关
//    protected var isCardDisabled = false //被禁用


    open fun startWatch(isMultiCountryCard: Boolean = false) {
        logd("startWatch currentState:${currentState.name} isMultiCountryCard:$isMultiCountryCard")
        this.isMultiCountryCard = isMultiCountryCard
        sendMessage(EVENT_START_WATCH)
    }
    

    private fun registListen() {
        //注册各种监听
        //dds
        ddsSubscribe = ServiceManager.monitor.ddsObser.asObservable().subscribe {
            if (DDSubId != it) {
                DDSubId = it
                sendMessage(EVENT_DDS_CHANGE)
            }
        }
        //wifi
        wifiSubscribe = WifiReceiver2.wifiNetObser.asObservable().subscribe {
            val isConn = it == NetworkInfo.State.CONNECTED
            if (isWifiOn != isConn) {
                isWifiOn = isConn
                sendMessage(EVENT_WIFI_STATE_CHANGE)
            }
        }
        //phonecall
        phonecallSubscribe = ServiceManager.simMonitor.planeCallObser.asObservable().subscribe {
            if (isPhoneCalling != it) {
                isPhoneCalling = it
                sendMessage(EVENT_PHONE_CALL_STATE_CHANGE)
            }
        }
        //airPhoneMode
        airPhoneModeSubscribe = AirplaneModeMonitor.getObservable(ServiceManager.appContext).subscribe {
            if (isAirModeOn != it) {
                isAirModeOn = it
                sendMessage(EVENT_AIRPHONE_MODE_STATE_CHANGE)
            }
        }
        //data switch enabler
//        dataSwitchSubscribe = ServiceManager.simMonitor.dataSwitchObser.asObservable().subscribe {
//            if (isDataSwitchOn != it) {
//                isDataSwitchOn = it
//                sendMessage(EVENT_DATA_SWITCH_STATE_CHANGE)
//            }
//        }
        //卡状态监听
        dataStateSubscribe = dataEnabler.cardStatusObser().asObservable().subscribe {
            logd("get new card State:$it")
            sendMessage(EVENT_CARD_STATE_CHANGE, it)
        }
        cardNetSubscribe = dataEnabler.netStatusObser().asObservable().subscribe {
            logd("get new net State:$it")
            sendMessage(EVENT_CARD_NET_CHANGE, it)
        }
    }

    /*
        遇到过卡注册网络成功的消息 比 广播报告网络拨号成功的消息 上报慢17s
        因此，如果由于网络变化触发变动，先判断网络是否拨号成功，如果是直接到dataCalled状态
     */
    private fun transitionByCardState(cardState: CardStatus, state: NetworkInfo.State) {

        val nextState: IState
        if (state == NetworkInfo.State.CONNECTED) {
            nextState = dataCalled
        } else {
            when (cardState) {
                CardStatus.ABSENT, CardStatus.INSERTED -> nextState = adding
                CardStatus.POWERON -> nextState = waitReadyState
                CardStatus.READY, CardStatus.LOAD, CardStatus.OUT_OF_SERVICE, CardStatus.EMERGENCY_ONLY -> nextState = outOfService
                CardStatus.IN_SERVICE -> {
                    nextState = if (state == NetworkInfo.State.CONNECTED) dataCalled else inService
                }
            }
        }

        if (currentState != nextState) {
            logd("transitionByCardState cardState $cardState  NetworkInfo :$state")
            logd("currentState:${currentState.name}  nextState :${nextState.name}")
            transitionTo(nextState)
        }


    }

    open fun stopWatch() {
        logd("stopWatch")

        sendMessage(EVENT_STOP_WATCH)
    }

    override fun logd(s: String?) {
        JLog.logd(name,s)
    }

    private val initState = object : State() {
        override fun hashCode(): Int {
            return 12301
        }

        override fun getName(): String {
            return "initState"
        }

        override fun enter() {
            logd("initState enter")
        }

        override fun processMessage(msg: Message): Boolean {
            if (msg.what == EVENT_START_WATCH) {
                DDSubId = ServiceManager.monitor.ddsObser.value
                isWifiOn = WifiReceiver2.wifiNetObser.value == NetworkInfo.State.CONNECTED
                isPhoneCalling = ServiceManager.simMonitor.planeCallObser.value
                isAirModeOn = AirplaneModeMonitor.getObservable(ServiceManager.appContext).value
//                isDataSwitchOn = ServiceManager.simMonitor.dataSwitchObser.value

                registListen()

                val cardState = dataEnabler.getCardState()
                transitionByCardState(cardState, dataEnabler.getNetState())
                return IState.HANDLED
            }
            return IState.NOT_HANDLED
        }

        override fun exit() {
            logd("initState exit")
        }
    }

    private val workingState = object : State() {
        override fun hashCode(): Int {
            return 12302
        }

        override fun getName(): String {
            return "workingState"
        }

        override fun enter() {

        }

        override fun processMessage(msg: Message): Boolean {
            logd("$name processMessage :$msg")
            when (msg.what) {
                EVENT_CARD_STATE_CHANGE -> {
                    val cardState = msg.obj as CardStatus
                    transitionByCardState(cardState, dataEnabler.getNetState())
                    return IState.NOT_HANDLED
                }
                EVENT_CARD_NET_CHANGE -> {
                    val netState = msg.obj as NetworkInfo.State
                    transitionByCardState(dataEnabler.getCardState(), netState)
                    return IState.NOT_HANDLED
                }
                EVENT_STOP_WATCH -> {
                    unRegistListen()
                    transitionTo(initState)
                    return IState.NOT_HANDLED
                }
                EVENT_ENABLE_CARD -> {
                    val card = msg.obj as Card
                    var cardList = ArrayList<Card>()
                    cardList.add(card)
                    dataEnabler.enable(cardList)
                    return IState.NOT_HANDLED
                }
            }
            return IState.NOT_HANDLED
        }

        override fun exit() {

        }
    }

    private fun unRegistListen() {
        //取消监听注册
        ddsSubscribe?.unsubscribe()
        wifiSubscribe?.unsubscribe()
        phonecallSubscribe?.unsubscribe()
        airPhoneModeSubscribe?.unsubscribe()
        dataSwitchSubscribe?.unsubscribe()
        dataStateSubscribe?.unsubscribe()
        cardNetSubscribe?.unsubscribe()

        ddsSubscribe = null
        wifiSubscribe = null
        phonecallSubscribe = null
        airPhoneModeSubscribe = null
        dataSwitchSubscribe = null
        dataStateSubscribe = null
        cardNetSubscribe = null
    }

    inner abstract class cardStateBase(val mName: String) : State() {
        var isCounting = false
        open var TIMEOUT_EVENT = EVENT_ADD_TIMEOUT
        open var TIMEOUT_TIME = MAX_ADD_TIME

        override fun getName(): String {
            return mName
        }

        override fun enter() {
            logd("$name enter")
            val isOk = checkStateValid(this@cardStateBase)
            if (isOk) {
                startTimer()
            }
        }

        override fun processMessage(msg: Message): Boolean {
            logd("$name processMessage msg:$msg ")
            if (msg.what == TIMEOUT_EVENT) {//超时处理
//                loge("$name timeout ")
                isCounting = false
                onTimeout(TIMEOUT_EVENT)
                return IState.HANDLED
            } else if ((msg.what and EVENT_EXCEPTION) == EVENT_EXCEPTION) {
                val isOk = checkStateValid(this)

                if (!isCounting && isOk) {//没正在计时且状态有效，开始计时
                    startTimer()
                } else if (isCounting && !isOk) {//正在计时且状态无效，结束计时
                    stopTimer()
                } else {
                    logv("ignore msg:${msg.what} isCounting:$isCounting isOk:$isOk")
                }
                return IState.HANDLED
            } else if ((msg.what and EVENT_STATE_CHANGE) == EVENT_STATE_CHANGE) {
                return handlerStateEvent(msg)
            } else {
                logv("unHandler msg : $msg")
                return IState.NOT_HANDLED
            }
        }

        abstract fun onTimeout(timeout_event: Int)

        protected open fun handlerStateEvent(msg: Message): Boolean {

            return IState.NOT_HANDLED
        }

        override fun exit() {
            logd("$name exit")
            stopTimer()
        }

        private fun startTimer() {
            if (!isCounting) {
                isCounting = true
                logd("startTimer :${getEventStr(TIMEOUT_EVENT)} max time:$TIMEOUT_TIME")
                sendMessageDelayed(TIMEOUT_EVENT, TIMEOUT_TIME)
            } else {
                loge("startTimer but had started currentState:$name")
            }
        }

        private fun stopTimer() {
            if (isCounting) {
                isCounting = false
                logd("stopTimer :${getEventStr(TIMEOUT_EVENT)} max time:$TIMEOUT_TIME")
                removeMessages(TIMEOUT_EVENT)
            } else {
                loge("stopTimer but not started currentState:$name")
            }
        }
    }


    private val adding = object : cardStateBase("adding") {
        override fun onTimeout(timeout_event: Int) {
//            reSwapCard(timeout_event)
            if (dataEnabler.getCard().cardType == CardType.PHYSICALSIM) {
                exceptionObser.onNext(EnablerException.EXCEP_PHY_CARD_IS_NULL)
            } else {
                exceptionObser.onNext(EnablerException.ADD_SOFT_SIM_TIMEOUT)
            }
        }

        override fun hashCode(): Int {
            return 12303
        }

        override var TIMEOUT_EVENT = EVENT_ADD_TIMEOUT
        override var TIMEOUT_TIME = MAX_ADD_TIME

    }
    private val waitReadyState = object : cardStateBase("waitReadyState") {
        override fun onTimeout(timeout_event: Int) {
//            reSwapCard(timeout_event)
            if (dataEnabler.getCard().cardType == CardType.PHYSICALSIM) {
                exceptionObser.onNext(EnablerException.EXCEP_PHY_CARD_IS_NULL)
            } else {
                exceptionObser.onNext(EnablerException.READY_TIMEOUT)
            }
        }

        override fun hashCode(): Int {
            return 12304
        }


        //powon 等待ready
        override var TIMEOUT_EVENT = EVENT_READY_TIMEOUT
        override var TIMEOUT_TIME: Long = 0
            get() = if (ServiceManager.systemApi.isUnderDevelopMode()) {
                MAX_READY_TIME
            } else {
                MAX_READY_TIME * 3
            }
    }
    protected val outOfService = object : cardStateBase("outOfServiceState") {
        override fun onTimeout(timeout_event: Int) {
            exceptionObser.onNext(EnablerException.INSERVICE_TIMEOUT)
            notifiAss(ACCESS_EVENT_IN_SERVICE_TIMEOUT)
        }

        override fun hashCode(): Int {
            return 12305
        }

        //收到ready 等待 注册上
        override var TIMEOUT_EVENT = EVENT_IN_SERVICE_TIMEOUT
        override var TIMEOUT_TIME = MAX_IN_SERVICE_TIME
    }
    private val inService = object : cardStateBase("inServiceState") {
        override fun onTimeout(timeout_event: Int) {
            exceptionObser.onNext(EnablerException.CONNECT_TIMEOUT)
            notifiAss(ACCESS_EVENT_SETUP_CALL_TIMEOUT)
        }

        override fun hashCode(): Int {
            return 12306
        }

        //收到注册上，等待拨号成功
        override var TIMEOUT_EVENT = EVENT_DATA_CALL_TIMEOUT
        override var TIMEOUT_TIME = MAX_DATA_CALL_TIME
    }
    private val dataCalled = object : State() {
        override fun hashCode(): Int {
            return 12307
        }

        override fun getName(): String {
            return "dataCalledState"
        }

        override fun enter() {
            logd("dataCalled enter")
        }

        override fun exit() {
            logd("dataCalled exit")
        }
    }

    private fun getEventStr(event: Int): String {
        when (event) {
            EVENT_ADD_TIMEOUT -> return "EVENT_ADD_TIMEOUT"
            EVENT_READY_TIMEOUT -> return "EVENT_READY_TIMEOUT"
            EVENT_IN_SERVICE_TIMEOUT -> return "EVENT_IN_SERVICE_TIMEOUT"
            EVENT_DATA_CALL_TIMEOUT -> return "EVENT_DATA_CALL_TIMEOUT"
        }
        return "unKnow Event :$event"
    }

    
    protected open fun checkStateValid(currentState: State): Boolean {
        // 检查状态是否正常有效
        val isSimCardEnaler: Boolean
        val slot = dataEnabler.getCard().slot
        try {
            if (slot > -1) {
                isSimCardEnaler = ServiceManager.systemApi.getSimEnableState(slot) != 0
            } else {
                isSimCardEnaler = true
            }
        }catch (e: DeadObjectException){
            loge("object dead!")
            e.printStackTrace()
            return checkStateValid(currentState)
        }

        //dds检查是否有效，判断当前DDS是否为当前，注意种子有非DDS也得注册的情况
        var ddsOk = true
        if (shouldcheckDDS(currentState)) {
            val cardSubId = dataEnabler.getCard().subId
            logd("cardSubId:$cardSubId")
            if (cardSubId >= 0) {
                ddsOk = cardSubId == DDSubId
            }
        }
//waitReadyState  addingState 这个两个状态下，忽略其他网络相关干扰
        val isIngore = currentState.name == "waitReadyState" || currentState.name == "adding"
        val _isDataSwitchOn = if (isIngore) true else isDataSwitchOn
        val _isAirModeOn = if (isIngore) false else isAirModeOn
        val _isPhoneCalling = if (isIngore) false else isPhoneCalling
        val _isWifiOn = if (isIngore) false else isWifiOn

        //wifi 是否为关闭状态
        //电话状态是否正在拨号
        //飞行模式是否关闭
        //数据开关是否打开
        if (ddsOk && !_isWifiOn && !_isPhoneCalling && !_isAirModeOn && _isDataSwitchOn && isSimCardEnaler) {
            return true
        } else {
            if (!ddsOk) {
                logd("[checkStateValid] dds is not ok")
            }
            if (_isWifiOn) {
                logd("[checkStateValid] Wifi is On")
            }
            if (_isPhoneCalling) {
                logd("[checkStateValid] phone is calling")
            }
            if (_isAirModeOn) {
                logd("[checkStateValid] air Mode is on")
            }
            if (!_isDataSwitchOn) {
                logd("[checkStateValid] Data Switch is not on")
            }
            if (!isSimCardEnaler) {
                logd("[checkStateValid] Sim Card is disabled")
            }
            return false
        }
    }

    /**
     * 返回是否要检查dds
     * 种子卡下，高通平台dun拨号过程中，不需要检查DDS
     * 云卡下，展讯平台不需要dds也能注册。但拨号还是要检查
     * 具体看各自实现
     */
    open protected fun shouldcheckDDS(currentState: State):Boolean{
        return true
    }

    

    private fun notifiAss(what: Int) {
//        ServiceManager.accessEntry.notifyEvent(what)
    }


    init {
        addState(initState)
        addState(workingState)
        addState(adding, workingState)
        addState(waitReadyState, workingState)
        addState(outOfService, workingState)
        addState(inService, workingState)
        addState(dataCalled, workingState)

        setInitialState(initState)
//        isDbg = true
        start()
    }
}