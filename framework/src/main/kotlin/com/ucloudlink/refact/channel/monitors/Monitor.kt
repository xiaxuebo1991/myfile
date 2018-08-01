package com.ucloudlink.refact.channel.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.PhoneStateListener
import android.telephony.PreciseDataConnectionState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.android.internal.telephony.TelephonyIntents
import com.ucloudlink.refact.business.cardprovisionstatus.CardProvisionStatus
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.utils.JLog
import rx.subjects.BehaviorSubject

/**
 * Created by wangliang on 2016/10/10.
 */
class Monitor(val context: Context) {
    val ddsObser: BehaviorSubject<Int> by lazy { BehaviorSubject.create<Int>(getDDS()) }

    private fun getDDS(): Int {
        val subscriptionManager = SubscriptionManager.from(context)
        val defaultDataSubscriptionInfo = subscriptionManager.defaultDataSubscriptionInfo
        if (defaultDataSubscriptionInfo != null) {
            return defaultDataSubscriptionInfo.subscriptionId
        }
        return -1
    }

    //------------DDS 监控--start--------------//
    var isDDSMoniON = false
    var mDDSBroadcastReceiver: DDSBroadcastReceiver? = null
    /**
     * 开始DDS监控
     * 建议autoRun开始时开启
     */
    fun startDDSMonitor() {
        if (isDDSMoniON) {
            return loge("DDSMonitor has started")
        }
        isDDSMoniON = true
        //    ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED
        val filter = IntentFilter()
        filter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
        mDDSBroadcastReceiver = DDSBroadcastReceiver()
        context.registerReceiver(mDDSBroadcastReceiver, filter)
    }

    /**
     * 结束DDS监控
     * 登出时退出
     */
    fun stopDDSMonitor() {
        if (!isDDSMoniON) {
            return loge("DDSMonitor has's started")
        }
        isDDSMoniON = false
        context.unregisterReceiver(mDDSBroadcastReceiver)
        mDDSBroadcastReceiver = null
    }

    inner class DDSBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            JLog.logd("onReceive action=" + intent.action)
            if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED == action) {
                val key = "subscription"
                val defaultDataSubId = intent.getIntExtra(key, -1)
                logv("ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED newDDSsubid=$defaultDataSubId")
                if (defaultDataSubId != -1 && defaultDataSubId < 2147483000) {
                    if (isSameDDS(defaultDataSubId)) {
                        loge("same as previous DDS ignore it")
                        return
                    }


                    ddsObser.onNext(defaultDataSubId)
                    val isLegal = checkDDSLegal(defaultDataSubId)
                    if (!isLegal) {
                        loge("detect DDS illegality!!")
                        if (CardProvisionStatus.getSimSetStatus(CardType.VSIM) != 0) {
//                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY)
                        }

                    } else {
                        logv("detect DDS Legal")
                    }
                } else {
                    loge("get new defaultDataSubId ==-1")
                }
            }
        }
    }

    private var previousDDS = -1;
    /**
     * 检查是否与之前的ddssubid相等
     */
    private fun isSameDDS(newDDSSubID: Int): Boolean {
        if (previousDDS == newDDSSubID) {
            return true
        } else {
            previousDDS = newDDSSubID
            return false
        }

    }

    /**
     * 检查新的DDS subid是否合法
     * 1,检查云卡是否ready 如果大于等于POWERON 如果sub==-1 忽略 当合法
     *                                      sub!=-1  切到DDS云卡-合法
     *                                              切到非云卡-非法(fixme 中间4秒怎么办?)
     *                    非ready  判断种子卡是否POWERON 如果sub==-1  忽略 当合法
     *                                                   sub!=-1   切到种子卡-合法
     *                                                          切到非种子卡-非法
     *                                                 非POWERON 忽略全部
     *@param newDefaultDDS 新的DDS subid
     * @return 如果合法,返回true 非法返回 fasle
     */
    private fun checkDDSLegal(newDefaultDDS: Int): Boolean {
//        val cloudsimState = ServiceManager.cloudSimEnabler.getCardState()
        val cloudsim = ServiceManager.cloudSimEnabler.getCard()
//        logv("cloudsimState $cloudsimState  cloudsim.state:${cloudsim.status}")
        if (cloudsim.status >= CardStatus.POWERON) {//poweron 时有可能自动切换DDS
            val subId = cloudsim.subId
            if (subId == -1 || cloudsim.status < CardStatus.READY) {//还没到ready也认为合法,因为之后会设置过去
                loge("checkDDSLegal cloudsim subid ==-1")
                return true
            }
            return subId == newDefaultDDS
        } else {
            logv("check seedsim")
//            val seedsimState = ServiceManager.seedCardEnabler.getCardState()
            val seedsim = ServiceManager.seedCardEnabler.getCard()
            if (seedsim.status >= CardStatus.READY) {
                val subId = seedsim.subId
                if (subId == -1) {
                    loge("checkDDSLegal seedsim subid ==-1")
                    return true
                }
                return subId == newDefaultDDS
            } else {
                logv("cloudsim and seedsim both not ready ingore DDS change")
                return true
            }
        }

    }
    //------------DDS 监控--end--------------//

    //------------phoneState 监控--start--------------//
    var isPSSMoniON: Boolean = false


    /**
     * 开始phoneState监控
     * 建议autoRun开始时开启
     */
    fun startPSMonitor() {
        if (isPSSMoniON) {
            return loge("startPSMonitor has started")
        }
        isPSSMoniON = true
        val telephonyManager = TelephonyManager.from(context)
        telephonyManager.listen(listen, PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE)
    }

    private val listen = PhoneListen()

    private inner class PhoneListen : PhoneStateListener() {
        override fun onPreciseDataConnectionStateChanged(dataConnectionState: PreciseDataConnectionState) {
            logd(dataConnectionState)
        }
    }

    /**
     * 结束phoneState监控
     * 登出时退出
     */
    fun stopPSMonitor() {
        if (!isPSSMoniON) {
            return loge("startPSMonitor has's started")
        }
        isPSSMoniON = false

    }
    //------------phoneState 监控--end--------------//

    fun cardMonitor() {

    }

    fun socketMonitor() {

    }
}