package com.ucloudlink.refact.channel.channels

import android.net.NetworkInfo
import android.os.Handler
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.channel.channels.SubscriberManager
import com.ucloudlink.refact.channel.enabler.simcard.dds.switchDdsToNext
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.business.routetable.ServerRouter.Dest
import com.ucloudlink.refact.channel.transceiver.Transceiver
import com.ucloudlink.refact.channel.Reconnector
import rx.Observable
import rx.Single
import rx.Subscription
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by wangliang on 2016/9/30.
 */
class UserChannel(val dataEnabler: IDataEnabler, val transceiver: Transceiver, val reconnector: Reconnector) { //需要进行业务层的reconnect操作

    enum class EnableSocketStatus {
        DISCONNECTED,
        CONNECTED
    }

    var subSetForUserChannelEnable = SubscriberManager<Any>()
    var socketStatusObser: PublishSubject<Any> = PublishSubject()
    var currentSocketStatus = EnableSocketStatus.DISCONNECTED

    init {
        listenEnablerSustainability(Dest.ASS)
    }

    private fun delayEnableSeedCard(delayTime: Long) {
        Handler().postDelayed({
            logd("setDDS at vsim and delayEnableSeedCard")//fixme 自动拨号
            Requestor.requireChannel(Requestor.apduid)
            val ret = ServiceManager.seedCardEnabler.enable(ArrayList<Card>())
            if (ret != 0) {
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_ENABLE_FAIL, ret)
            }
            logd("ServiceManager.seedCardEnabler.enable ret:" + ret)
        }, delayTime)
    }

    private fun listenEnablerSustainability(dest: Dest) {
        dataEnabler.netStatusObser().subscribeWith {
            //statusObser 订阅就发射最近一次的数据
            onNext {
                logd("user dataEnabler.statusObser it:$it")
                if (it == NetworkInfo.State.CONNECTED) {
                    reconnector.setSocketConnect(dest, "UserEnabled")
                } else {
                    reconnector.setSocketConnect(dest, "UserNotEnabled")
                }
            }
            onError {
                loge("user listenEnablerSustainability onError")
                it.printStackTrace()
            }
            onCompleted {
                loge("user listenEnablerSustainability onCompleted")
            }
        }

        dataEnabler.exceptionObser().subscribeWith {
            onNext {
                loge("UserChannel exceptionObser occur!")
                currentSocketStatus = EnableSocketStatus.DISCONNECTED
                socketStatusObser.onNext(Exception(ErrorCode.getErrCodeByCardExceptId(it).toString()))
            }
            onError { loge("UserChannel onError, shouldn't run here!") }
            onCompleted { loge("UserChannel onCompleted, shouldn't run here!") }
        }
//
//        dataEnabler.cardStatusObser().subscribe(
//                {
//                    logd("card status change: $it")
//                    if (it == CardStatus.READY || it == CardStatus.LOAD) {
//                        Requestor.requireChannel(Requestor.apduid)
//                    }
//                }
//        )

        reconnector.statusObser.subscribeWith {
            onNext {
                logd("reconnector statusObser: $it")
                if (it == Reconnector.ReconnectStates.RECONNECTED) {
                    currentSocketStatus = EnableSocketStatus.CONNECTED
                    socketStatusObser.onNext(currentSocketStatus)
                } else if (it == Reconnector.ReconnectStates.RECONNECT_FAILED_ERROR_FATAL) { //致命错误
                    currentSocketStatus = EnableSocketStatus.DISCONNECTED
                    socketStatusObser.onNext(reconnector.getException())
                } else if (it == Reconnector.ReconnectStates.SOCKET_DISCONNECT || it == Reconnector.ReconnectStates.RECONNECT_FAILED) { //自动尝试恢复
                    currentSocketStatus = EnableSocketStatus.DISCONNECTED
                    socketStatusObser.onNext(currentSocketStatus)
                } else {

                }
            }
        }
    }

    fun close(dest: Dest) {
        logd("userChannel close transceiver")
        reconnector.setSocketConnect(dest, "UserNotEnabled")
        subSetForUserChannelEnable.cleanSubsAndunSubscriberAll()
    }

    fun isDataEnabled(): Boolean {
        if (dataEnabler.getNetState() == NetworkInfo.State.CONNECTED) return true
        return false
    }


    fun tryToEnableAccessServerSocket(dest: Dest) {
        logd("tryToEnableAccessServerSocket func4")
        val enablerNetState = dataEnabler.getNetState()
        logd("tryToEnableAccessServerSocket currentState:$enablerNetState")

//        if (enablerNetState != NetworkInfo.State.CONNECTED) {
//            dataEnabler.enable(ArrayList<Card>())
//        }
        JLog.logi("tryToEnableAccessServerSocket: $currentSocketStatus")
    }

    fun available(dest: Dest): Boolean {
        return currentSocketStatus == EnableSocketStatus.CONNECTED
    }

    fun enable(dest: Dest, timeout: Long): Single<Any> {
        var subTryToEnable: Subscription? = null

        return Single.create<Any> { sub ->
            subTryToEnable = socketStatusObser.timeout(timeout, TimeUnit.SECONDS).subscribeWith {
                onNext {
                    subSetForUserChannelEnable.delSubAndUnsubscribe(this.subscriber)
                    if (it is EnableSocketStatus && it == EnableSocketStatus.CONNECTED) {
                        sub.onSuccess(true)
                    } else {
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onError(Exception(it.toString()))
                        }
                    }
                }
                onError {
                    subSetForUserChannelEnable.delSubAndUnsubscribe(this.subscriber)
                    if (it is TimeoutException) {
                        sub.onError(Exception("TIMEOUT:subSetForUserChannelEnable timeout:" + timeout))
                    } else {
                        sub.onError(it)
                    }
                }
                onCompleted {
                    sub.onError(Exception("tryToEnableForRequestor canceled!"))
                    subSetForUserChannelEnable.delSubAndUnsubscribe(this.subscriber)
                }
                onStart { subSetForUserChannelEnable.putSub(this.subscriber) }
            }
            if (available(dest)) {
                sub.onSuccess(true)
            } else {
                tryToEnableAccessServerSocket(dest)
            }
        }.doOnUnsubscribe {
            logd("doOnUnsubscribe subTryToEnable")
            if (subTryToEnable != null) {
                if (!(subTryToEnable as Subscription).isUnsubscribed) {
                    (subTryToEnable as Subscription).unsubscribe()
                }
            }
        }
    }
}