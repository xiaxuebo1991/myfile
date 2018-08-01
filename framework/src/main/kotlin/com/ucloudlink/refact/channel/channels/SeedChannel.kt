package com.ucloudlink.refact.channel.channels

import android.net.NetworkInfo
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.routetable.ServerRouter.Dest
import com.ucloudlink.refact.channel.Reconnector
import com.ucloudlink.refact.channel.enabler.DeType
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.transceiver.Transceiver
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.*
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
 *
 *
 */
class SeedChannel(val dataEnabler: IDataEnabler, val transceiver: Transceiver, val reconnector: Reconnector) { //需要进行业务层的reconnect操作
    var subSetForSeedChannelEnable = SubscriberManager<Any>()

    //标志是否有种子通道需求
    var hasRequire = false

    /**
     * 接入服务器socket状态
     */
    enum class ASSocketStatus {
        DISCONNECTED,
        CONNECTED,
    }

    var socketStatusObser: PublishSubject<Any> = PublishSubject()
    var currentSocketStatus = ASSocketStatus.DISCONNECTED

    init {
        listenEnabler(Dest.ASS)
    }

    fun close(dest: Dest) {
        subSetForSeedChannelEnable.cleanSubsAndunSubscriberAll()
        //增加判断种子卡dataEnable 启动了才执行setSocketConnect 避免无谓的关闭
        if (dataEnabler.getDeType() != DeType.WIFI && dataEnabler.isCardOn()) {
            reconnector.setSocketConnect(dest, "SeedNotEnabled")
        }
        logd("seedChannel close! state:${dataEnabler.getNetState()}")

        //关软卡时，直接关闭。确保释放channel
        //if (dataEnabler.getCardState() != CardStatus.ABSENT) {
            dataEnabler.disable("seed Channel close")
        //}
    }


    fun enableSocket(dest: Dest): Int {
        logd("enableSocket func4")
        var ret = 0

        val enablerNetState = dataEnabler.getNetState()
        logd("enableSocketImp currentState:$enablerNetState")

        if (enablerNetState != NetworkInfo.State.CONNECTED) {
            ret = dataEnabler.enable(ArrayList<Card>())
        }
        logi("enableSocketImp: $currentSocketStatus -- $ret")
        return ret
    }

    /**
     * 持续监听DataEnabler变化
     */
    private fun listenEnabler(dest: Dest) {
        dataEnabler.netStatusObser().subscribeWith {
            //statusObser 订阅就发射最近一次的数据
            onNext {
                logd("seed dataEnabler.statusObser it:$it")
                if (it == NetworkInfo.State.CONNECTED) {
                    Configuration.isDoingPsCall = false //dun拨号完成了
                    reconnector.setSocketConnect(dest, "SeedEnabled")
                    if (currentSocketStatus == ASSocketStatus.CONNECTED) {
                        socketStatusObser.onNext(ASSocketStatus.CONNECTED)
                    }
                } else if (it == NetworkInfo.State.DISCONNECTED) {
                    reconnector.setSocketConnect(dest, "SeedNotEnabled")
                } else { // not enabled
                    reconnector.setSocketConnect(dest, "SeedNotEnabled")
                }
            }
            onError {
                logke("seed listenEnabler onError")
                it.printStackTrace()
            }
            onCompleted {
                logke("seed listenEnabler onCompleted")
            }
        }

        dataEnabler.exceptionObser().subscribeWith {
            onNext {
                loge("SeedChannel exceptionObser occur!" + it)
                currentSocketStatus = ASSocketStatus.DISCONNECTED
                socketStatusObser.onNext(Exception(ErrorCode.getErrCodeByCardExceptId(it).toString()))
            }
            onError { loge("SeedChannel onError, shouldn't run here!") }
            onCompleted { loge("SeedChannel onCompleted, shouldn't run here!") }
        }

        reconnector.statusObser.subscribeWith {
            onNext {
                logd("reconnector statusObser: $it")
                if (it == Reconnector.ReconnectStates.RECONNECTED) {
                    currentSocketStatus = ASSocketStatus.CONNECTED
                    socketStatusObser.onNext(currentSocketStatus)
                } else if (it == Reconnector.ReconnectStates.RECONNECT_FAILED_ERROR_FATAL) { //致命错误
                    currentSocketStatus = ASSocketStatus.DISCONNECTED
                    socketStatusObser.onNext(reconnector.getException())
                } else if (it == Reconnector.ReconnectStates.SOCKET_DISCONNECT || it == Reconnector.ReconnectStates.RECONNECT_FAILED) { //自动尝试恢复
                    currentSocketStatus = ASSocketStatus.DISCONNECTED
                    socketStatusObser.onNext(currentSocketStatus)
                } else {

                }
            }
        }
    }

    fun available(dest: Dest): Boolean {
        return (dataEnabler.getNetState() == NetworkInfo.State.CONNECTED) && (currentSocketStatus == ASSocketStatus.CONNECTED)
    }

    fun enable(dest: Dest, timeout: Long): Single<Any> {
        var subEnableAccessServerSocket: Subscription? = null
        return Single.create<Any> { sub ->
            subEnableAccessServerSocket = socketStatusObser.timeout(timeout, TimeUnit.SECONDS).subscribeWith {
                onNext {

                    if (it is ASSocketStatus && it == ASSocketStatus.CONNECTED) {
                        logk("enableForRequestor succ")
                        subSetForSeedChannelEnable.delSubAndUnsubscribe(this.subscriber)
                        sub.onSuccess(true)
                    } else {
                        logk("enableForRequestor fail " + it)
                        if (it is Exception) {
                            subSetForSeedChannelEnable.delSubAndUnsubscribe(this.subscriber)
                            sub.onError(it)
                        } else {  //非异常不通知 重新判断是否需要拉种子通道
                            if (hasRequire) {
                                val ret = enableSocket(dest)
                                if (ret != 0) {
                                    loge("enable socket fail!!! $ret 22222")
                                    sub.onError(Exception(ret.toString()))
                                }
                            }
                        }
                    }
                }
                onError {
                    subSetForSeedChannelEnable.delSubAndUnsubscribe(this.subscriber)
                    if (it is TimeoutException) {
                        sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "enableSocket timeout!" + timeout))
                    } else {
                        sub.onError(it)
                    }
                }
                onCompleted {
                    sub.onError(Exception("enableForRequestor canceled!"))
                    subSetForSeedChannelEnable.delSubAndUnsubscribe(this.subscriber)
                }
                onStart { subSetForSeedChannelEnable.putSub(this.subscriber) }
            }
            if (available(dest)) {
                sub.onSuccess(true)
            } else {
                val ret = enableSocket(dest)
                if (ret != 0) {
                    loge("enable socket fail!!! $ret")
                    sub.onError(Exception(ret.toString()))
                }
            }
        }.doOnUnsubscribe {
            logd("doOnUnsubscribe subEnableAccessServerSocket")
            if (subEnableAccessServerSocket != null) {
                if (!(subEnableAccessServerSocket as Subscription).isUnsubscribed) {
                    (subEnableAccessServerSocket as Subscription).unsubscribe()
                }
            }
        }
    }
}