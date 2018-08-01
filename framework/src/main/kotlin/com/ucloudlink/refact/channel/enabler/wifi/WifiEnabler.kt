package com.ucloudlink.refact.channel.enabler.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.*
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.monitors.WifiReceiver2
import com.ucloudlink.refact.config.ACCESS_CLEAR_CARD
import com.ucloudlink.refact.config.REASON_CHANGE
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.JLog.logv
import rx.Observable
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.PublishSubject
import java.util.*

/**
 * 只做wifi状态监控反馈,使能去使能的作用去掉
 *
 */
class WifiEnabler(val context: Context) : IDataEnabler {
    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        
    }

    override fun isDefaultNet(): Boolean {
        return true
    }

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = ConnectivityManager.from(context)

    private val mCardStateObservable = BehaviorSubject<CardStatus>(CardStatus.ABSENT)//这个的存在只是为了完整性,在wifi这个没有意义!
    private val mNetStatusObservable = BehaviorSubject<NetworkInfo.State>(NetworkInfo.State.DISCONNECTED)
    private val mExceptionObservable = PublishSubject<EnablerException>()
    private val mCardSignalStrengthOb = PublishSubject<Int>()

    private var mNetStatus: NetworkInfo.State = NetworkInfo.State.DISCONNECTED
        get() {
            return connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).state
        }

    private var isSeedOn = false

    override fun getDeType(): DeType {
        return DeType.WIFI
    }

    init {
        mNetStatusObservable.onNext(NetworkInfo.State.DISCONNECTED)
    }

    override fun enable(cardList: ArrayList<Card>): Int {
        logv("enable wifi do nothing")
//        notifyASS(AccessEventId.EVENT_SEEDSIM_ENABLE)
        isSeedOn = true
        updateNet(mNetStatus,true)
        return 0
    }

    fun notifyASS(eventId: Int): Unit {
        ServiceManager.accessEntry.notifyEvent(eventId)
    }

    /**
     * @param reason if reason==CHANGE_ENABLER means destroy this
     * 请记得清除示例并需要的时候重新创建,避免重复监听
     */
    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        logv("disable wifi do nothing reason:$reason")
        isSeedOn = false
//        updateNet(NetworkInfo.State.DISCONNECTED)
        if (reason == REASON_CHANGE) {
            wifisubscribe.unsubscribe()
            updateNet(NetworkInfo.State.DISCONNECTED)
        } else if (reason.contains(ACCESS_CLEAR_CARD)) {
            updateNet(NetworkInfo.State.DISCONNECTED)
        }
        return -1
    }

    override fun getCardState(): CardStatus {
        return CardStatus.READY
    }

    override fun cardStatusObser(): Observable<CardStatus> {
        return mCardStateObservable
    }

    override fun getNetState(): NetworkInfo.State {
        if (!isCardOn()) {
            return NetworkInfo.State.DISCONNECTED
        }
        return mNetStatus
    }

    override fun netStatusObser(): Observable<NetworkInfo.State> {
        return mNetStatusObservable
    }

    override fun exceptionObser(): Observable<EnablerException> {
        return mExceptionObservable
    }

    override fun cardSignalStrengthObser(): Observable<Int> {
        return mCardSignalStrengthOb
    }

    override fun switchRemoteSim(card: Card): Int {
        loge("wifi enabler do not invoke switchRemoteSim")
        return -1
    }

    override fun getCard(): Card {
        loge("wifi enabler do not invoke getCard()")
        return Card()
    }

    /**
     * 表示wifi开关是否打开
     */
    override fun isCardOn(): Boolean {
//        val wifiState = wifiManager.wifiState
//        if (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_AP_STATE_DISABLING) {
//            return false
//        }
        return isSeedOn
    }

    /**
     * 表示wifi是否正在关闭
     */
    override fun isClosing(): Boolean {
        val wifiState = wifiManager.wifiState
        return wifiState == WifiManager.WIFI_AP_STATE_DISABLING
    }

    override fun cloudSimRestOver() {
        JLog.logd("do nothing! cloudSimRestOver in wifi")
    }

    override fun getDataEnableInfo(): DataEnableInfo {
        return DataEnableInfo(iccid = "", imsi = "", ip = 0, lastException = null, dataReg = false, voiceReg = false, dataRoam = false, voiceRoam = false, dataConnect = true, singleStrength = 0)
    }

    var wifisubscribe = WifiReceiver2.wifiNetObser.asObservable().subscribe {
        if (isCardOn() && isSeedOn) {
            updateNet(it)
        }
    }

    private fun updateNet(state: NetworkInfo.State, notCheck: Boolean = false) {
        val lastValue = mNetStatusObservable.value
        if (lastValue == null || lastValue != state||notCheck) {
            logv("wifi new network info state:$state")
            mNetStatusObservable.onNext(state)
        }
    }
}