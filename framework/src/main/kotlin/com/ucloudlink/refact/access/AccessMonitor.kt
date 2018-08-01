package com.ucloudlink.refact.access

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Message
import android.os.SystemClock
import com.android.internal.util.IState
import com.android.internal.util.State
import com.android.internal.util.StateMachine
import com.ucloudlink.refact.channel.monitors.AirplaneModeMonitor
import com.ucloudlink.refact.channel.monitors.WifiReceiver2
import com.ucloudlink.refact.utils.JLog
import java.util.concurrent.TimeUnit


/**
 * Created by shiqianhua on 2017/8/19.
 */
class AccessMonitor(ctx: Context, entry: AccessEntry): StateMachine("AccessMonitor") {

    private val TimeoutValueArr = arrayOf(4,8) // unit:min
    private val MAX_TIMEOUT_VALUE = 8 // unit:min
    private val MIN_TIMEOUT_VALUE = 4 // unit:min
    private val MULTI_CARDS_TIMEOUT_VALUE = 2 // unit:min
    private val context: Context = ctx
    private val accEntry: AccessEntry = entry

    private val WIFI_CHANGE_ENABLE = 1
    private val WIFI_CHANGE_DISABLE = 2
    private val SERVICE_START = 3
    private val SERVICE_STOP = 4
    private val AIRPLANE_START = 5
    private val AIRPLANE_STOP = 6
    private val PHONE_CALL_START = 7
    private val PHONE_CALL_STOP = 8
    private val CLOUDSIM_INSERVICE = 9
    private val SERVICE_MONITOR_TIMEOUT = 10
    private val SERVICE_OK = 11
    private val OUT_OF_SERVICE = 12
    private val SEED_SIM_DISABLE = 13
    private val SEED_SIM_ENABLE = 14
    private val CLOUD_SIM_DISABLE = 15
    private val CLOUD_SIM_ENABLE = 16
    private val DDS_SET_ILLEGAL = 17
    private val DDS_SET_NORMAL = 18
    private val PHONE_DATA_DISABLE = 19
    private val PHONE_DATA_ENABLE = 20
    private val MULTI_SEED_CARDS = 21

    private val mParentState = ParentState()
    private val mDefaultState = DefaultState()
    private val mServiceWaitReady = ServiceWaitReady()
    private val mServiceStartState = ServiceStartState()
    private val mExceptWaitState = ExceptWaitState()
    private val mServiceRunState = ServiceRunState()
    private val mWifiWaitState = WifiWaitState()

    private var isWifiOn = false
    private var isPhoneOn = false
    private var isAirplaneOn = false
    private var isSeedSimDisable = false
    private var isCloudsimDisable = false
    private var isDdsIllegal = false
    private var isPhoneDataDisable = false

    private var curState: State? = null
    private var lastState: State? = null
    private var nextState: State? = null

    var curPercent: Int = 0
        private set(value) {
            JLog.logd("curPercent change $field -> $value")
            if(field != value){
                if(value == 0){
                    sendMessage(SERVICE_STOP)
                }else{
                    when {
                        field == 0 -> sendMessage(SERVICE_START)
                        field == 100 -> sendMessage(OUT_OF_SERVICE)
                        value == 90 -> sendMessage(CLOUDSIM_INSERVICE)
                        value == 100 -> sendMessage(SERVICE_OK)
                    }
                }
                field = value
            }
        }

    init {
        addState(mParentState)
            addState(mDefaultState, mParentState)
            addState(mServiceWaitReady, mParentState)
                addState(mServiceStartState, mServiceWaitReady)
                addState(mExceptWaitState, mServiceWaitReady)
            addState(mServiceRunState, mParentState)
            addState(mWifiWaitState, mParentState)

        setInitialState(mParentState)
        start()

        wifiStateReg()
        servicePercentReg()
        airplaneStateReg()
    }

    private fun transToNextState(next:State){
        logd("state change: ${curState!!.name} -> ${next.name}")
        nextState = next
        transitionTo(nextState)
    }

    private fun wifiStateReg() {
        WifiReceiver2.wifiNetObser.asObservable().subscribe {
            JLog.logd("wifi change $it")
            if (it == NetworkInfo.State.CONNECTED) {
                isWifiOn = true
                sendMessage(WIFI_CHANGE_ENABLE)
            } else {
                isWifiOn = false
                sendMessage(WIFI_CHANGE_DISABLE)
            }
        }
    }

    private fun getTimeoutValueByCount(count: Int): Int = when {
        count <= 0 -> MAX_TIMEOUT_VALUE
        count <= TimeoutValueArr.size -> TimeoutValueArr[count - 1]
        else -> MAX_TIMEOUT_VALUE
    }

    private fun servicePercentReg() {
        accEntry.accessState.statePersentOb.subscribe({ curPercent = it })
    }

    private fun airplaneStateReg() {
        AirplaneModeMonitor.getObservable(context).subscribe {
            JLog.logd("airplane state change $it")
            sendMessage(if (it) AIRPLANE_START else AIRPLANE_STOP)
        }
    }

    private fun setExceptionValu(what:Int) {
        when(what){
            WIFI_CHANGE_ENABLE -> {
                isWifiOn = true
            }
            WIFI_CHANGE_DISABLE -> {
                isWifiOn = false
            }
            AIRPLANE_START -> {
                isAirplaneOn = true
            }
            AIRPLANE_STOP -> {
                isAirplaneOn = false
            }
            PHONE_CALL_START -> {
                isPhoneOn = true
            }
            PHONE_CALL_STOP -> {
                isPhoneOn = false
            }
            SEED_SIM_DISABLE -> {
                isSeedSimDisable = true
            }
            SEED_SIM_ENABLE -> {
                isSeedSimDisable = false
            }
            CLOUD_SIM_DISABLE -> {
                isCloudsimDisable = true
            }
            CLOUD_SIM_ENABLE -> {
                isCloudsimDisable = false
            }
            DDS_SET_ILLEGAL -> {
                isDdsIllegal = true
            }
            DDS_SET_NORMAL -> {
                isDdsIllegal = false
            }
            PHONE_DATA_DISABLE -> {
                isPhoneDataDisable = true
            }
            PHONE_DATA_ENABLE -> {
                isPhoneDataDisable = false
            }
        }
    }
    
    private fun clearExceptionInfo() {
        isPhoneOn = false
        isPhoneDataDisable = false
        isAirplaneOn = false
        isDdsIllegal = false
        isSeedSimDisable = false
        isCloudsimDisable = false
    }

    private inner class ParentState: State(){
        override fun processMessage(msg: Message?): Boolean {
            when(msg!!.what){
                WIFI_CHANGE_ENABLE, WIFI_CHANGE_DISABLE, AIRPLANE_START, AIRPLANE_STOP, PHONE_CALL_START, PHONE_CALL_STOP,
                SEED_SIM_DISABLE, SEED_SIM_ENABLE, CLOUD_SIM_DISABLE, CLOUD_SIM_ENABLE, DDS_SET_ILLEGAL, DDS_SET_NORMAL,
                PHONE_DATA_DISABLE, PHONE_DATA_ENABLE-> {
                    setExceptionValu(msg.what)
                }
                else -> {
                    return IState.NOT_HANDLED
                }
            }
            return IState.HANDLED
        }

        override fun enter() {
            curState = this
            transToNextState(mDefaultState)
        }

        override fun exit() {
            lastState = this
            super.exit()
        }
    }

    private inner class DefaultState: State(){
        override fun processMessage(msg: Message?): Boolean {
            when(msg!!.what){
                SERVICE_START -> {
                    transToNextState(mServiceStartState)
                }
                else -> {
                    return IState.NOT_HANDLED
                }
            }
            return IState.HANDLED
        }

        override fun enter() {
            curState = this
            super.enter()
        }

        override fun exit() {
            lastState = this
            super.exit()
        }
    }

    //超时计数器
    private var timeoutCount = 0
    // 是否是第一次超时
    private var oneTimesFlag = true
    // 是否已经超时
    private var monitorTimeoutFlag = false
    //超时时间点  从系统上电开始的时间
    private var monitorTimeoutStamp = 0L

    private inner class ServiceWaitReady:State(){
        override fun processMessage(msg: Message?): Boolean {
            return IState.NOT_HANDLED
        }

        override fun enter() {
            curState = this
        }

        override fun exit() {
            lastState = this
            timeoutCount = 0
            monitorTimeoutStamp = 0
            monitorTimeoutFlag = false
        }
    }

    private inner class ServiceStartState: State(){
        private fun startToSendTimeoutMsg(){
            timeoutCount++
            val delayTime = TimeUnit.MINUTES.toMillis(getTimeoutValueByCount(timeoutCount).toLong())
            monitorTimeoutStamp = SystemClock.uptimeMillis() + delayTime
            sendMessageDelayed(SERVICE_MONITOR_TIMEOUT, delayTime)
        }

        override fun processMessage(msg: Message?): Boolean {
            when(msg!!.what){
                SERVICE_MONITOR_TIMEOUT -> {
                    monitorTimeoutFlag = true
                    accEntry.notifyEvent(AccessEventId.EVENT_BUSINESS_RESTART)
                    startToSendTimeoutMsg()
                }
                AIRPLANE_START, PHONE_CALL_START, SEED_SIM_DISABLE, CLOUD_SIM_DISABLE, DDS_SET_ILLEGAL, PHONE_DATA_DISABLE -> {
                    setExceptionValu(msg.what)
                    transToNextState(mExceptWaitState)
                }
                SERVICE_OK -> {
                    transToNextState(mServiceRunState)
                }
                CLOUDSIM_INSERVICE -> {
                    if(isWifiOn){
                        transToNextState(mWifiWaitState)
                    }
                }
                SERVICE_STOP -> {
                    transToNextState(mDefaultState)
                }
                WIFI_CHANGE_ENABLE -> {
                    setExceptionValu(msg.what)
                    if(curPercent == 90){
                        transToNextState(mWifiWaitState)
                    }
                }
                MULTI_SEED_CARDS-> {
                    if((getTimeoutValueByCount(timeoutCount) == MIN_TIMEOUT_VALUE) && oneTimesFlag){
                        oneTimesFlag = false

                        logd("muti seed cards, lefttime! -> ${monitorTimeoutStamp - SystemClock.uptimeMillis()}")
                        monitorTimeoutStamp += TimeUnit.MINUTES.toMillis(MULTI_CARDS_TIMEOUT_VALUE.toLong())
                        removeMessages(SERVICE_MONITOR_TIMEOUT)
                        sendMessageDelayed(SERVICE_MONITOR_TIMEOUT, monitorTimeoutStamp - SystemClock.uptimeMillis())
                        logd("new timeout value is ${monitorTimeoutStamp - SystemClock.uptimeMillis()} ms")
                    }
                }
                else -> {
                    return IState.NOT_HANDLED
                }
            }
            return IState.HANDLED
        }

        override fun enter() {
            curState = this
            if (isPhoneOn || isAirplaneOn || isSeedSimDisable || isCloudsimDisable || isDdsIllegal || isPhoneDataDisable){
                transToNextState(mExceptWaitState)
            }else {
                if (monitorTimeoutStamp == 0L) {
                    startToSendTimeoutMsg()
                } else {
                    sendMessageDelayed(SERVICE_MONITOR_TIMEOUT, monitorTimeoutStamp - SystemClock.uptimeMillis())
                    logd("send delay time ${monitorTimeoutStamp - SystemClock.uptimeMillis()} ms")
                }
            }
        }

        override fun exit() {
            oneTimesFlag = true
            lastState = this
            removeMessages(SERVICE_MONITOR_TIMEOUT)
        }
    }

    private inner class ExceptWaitState: State(){
        private var mEntryTime = 0L
        private fun isInException() : Boolean{
            if(isPhoneOn || isAirplaneOn || isSeedSimDisable || isCloudsimDisable || isDdsIllegal || isPhoneDataDisable){
                return true
            }
            return false
        }

        private fun checkExit(){
            if(!isInException()){
                if(isWifiOn && curPercent == 90){
                    transToNextState(mWifiWaitState)
                }else if(curPercent == 100){
                    transToNextState(mServiceRunState)
                }else {
                    transToNextState(mServiceStartState)
                }
            }
        }
        override fun processMessage(msg: Message?): Boolean {
            when(msg!!.what){
                AIRPLANE_STOP, PHONE_CALL_STOP, SEED_SIM_ENABLE, CLOUD_SIM_ENABLE, DDS_SET_NORMAL, PHONE_DATA_ENABLE -> {
                    setExceptionValu(msg.what)
                    checkExit()
                }
                WIFI_CHANGE_ENABLE -> {
                    setExceptionValu(msg.what)
                    JLog.logd("wifi enable do nothing")
                }
                else -> {
                    return IState.NOT_HANDLED
                }
            }
            return IState.HANDLED
        }

        override fun enter() {
            curState = this
            mEntryTime = SystemClock.uptimeMillis()
        }

        override fun exit() {
            lastState = this
            clearExceptionInfo()
            if(monitorTimeoutStamp != 0L){
                monitorTimeoutStamp += SystemClock.uptimeMillis() - mEntryTime
            }
        }
    }

    private inner class ServiceRunState: State(){
        override fun processMessage(msg: Message?): Boolean {
            when(msg!!.what){
                OUT_OF_SERVICE -> {
                    if(curPercent == 90 && isWifiOn){
                        transToNextState(mWifiWaitState)
                    }else{
                        transToNextState(mServiceStartState)
                    }
                }
                AIRPLANE_START, PHONE_CALL_START, SEED_SIM_DISABLE, CLOUD_SIM_DISABLE, DDS_SET_ILLEGAL, PHONE_DATA_DISABLE -> {
                    setExceptionValu(msg.what)
                    transToNextState(mExceptWaitState)
                }
                SERVICE_STOP -> {
                    transToNextState(mDefaultState)
                }
                else -> {
                    return IState.NOT_HANDLED
                }
            }
            return IState.HANDLED
        }

        override fun enter() {
            curState = this
            super.enter()
        }

        override fun exit() {
            lastState = this
            super.exit()
        }
    }


    private inner class WifiWaitState: State(){
        override fun processMessage(msg: Message?): Boolean {
            when(msg!!.what){
                WIFI_CHANGE_DISABLE -> {
                    transToNextState(mServiceStartState)
                }
                SERVICE_OK -> {
                    transToNextState(mServiceRunState)
                }
                AIRPLANE_START, PHONE_CALL_START, SEED_SIM_DISABLE, CLOUD_SIM_DISABLE, DDS_SET_ILLEGAL, PHONE_DATA_DISABLE -> {
                    setExceptionValu(msg.what)
                    transToNextState(mExceptWaitState)
                }
                SERVICE_STOP -> {
                    transToNextState(mDefaultState)
                }
                OUT_OF_SERVICE -> {
                    if(curPercent != 90){
                        transToNextState(mServiceStartState)
                    }
                }
                else -> {
                    return IState.NOT_HANDLED
                }
            }
            return IState.HANDLED
        }

        override fun enter() {
            curState = this
            super.enter()
        }

        override fun exit() {
            lastState = this
            super.exit()
        }
    }

    fun startPhoneCall(){
        sendMessage(PHONE_CALL_START)
    }
    fun stopPhoneCall(){
        sendMessage(PHONE_CALL_STOP)
    }

    fun seedsimDisable(){
        sendMessage(SEED_SIM_DISABLE)
    }

    fun seedsimEnable(){
        sendMessage(SEED_SIM_ENABLE)
    }

    fun cloudsimDisable(){
        sendMessage(CLOUD_SIM_DISABLE)
    }

    fun cloudsimEnale(){
        sendMessage(CLOUD_SIM_ENABLE)
    }

    fun ddsSetIllegal(){
        sendMessage(DDS_SET_ILLEGAL)
    }

    fun ddsSetNormal(){
        sendMessage(DDS_SET_NORMAL)
    }

    fun phoneDataDisable(){
        sendMessage(PHONE_DATA_DISABLE)
    }

    fun phoneDataEnable(){
        sendMessage(PHONE_DATA_ENABLE)
    }

    fun sendMultiSeedMsg(){
        sendMessage(MULTI_SEED_CARDS)
    }

    fun getMonitorTimeoutFlag(): Boolean{
        return monitorTimeoutFlag
    }

    fun setMonitorTimeoutFlag(flag : Boolean){
        monitorTimeoutFlag = flag
    }
}