package com.ucloudlink.refact.channel

import android.os.Handler
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.framework.protocol.protobuf.CommonErrorcode
import com.ucloudlink.framework.protocol.protobuf.Upload_SessionId_Resp
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.ErrorCode.RPC_HEADER_STR
import com.ucloudlink.refact.channel.transceiver.protobuf.Message
import com.ucloudlink.refact.channel.transceiver.protobuf.MessagePacker
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.transceiver.Transceiver
import com.ucloudlink.refact.channel.transceiver.secure.SecureUtil
import rx.Observable
import rx.Single
import rx.SingleSubscriber
import rx.Subscription
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import java.util.concurrent.TimeUnit

/**
 * Created by wangliang on 2016/10/9.
 */
class Reconnector(val transceiver: Transceiver) {
    private var sessionId: String? = null
    private val packetUtil = ProtoPacketUtil.getInstance()

    fun setSessionId(s: String?) {
        sessionId = s
    }

    enum class ReconnectStates {
        RECONNECTED, // SUCC
        RECONNECT_FAILED, // FAILD TIMEOUT
        RECONNECT_FAILED_ERROR_FATAL, //ESYS0001

        SOCKET_DISCONNECT // FAILD
    }

    var currentStatus = ReconnectStates.SOCKET_DISCONNECT


    var statusObser = PublishSubject<Any>() //对外的状态
    var needReconnect = true // apdu情况不需要重连

    init {
        listenSocketSustainability()
    }

    private var currentException: Throwable? = null

    fun getException(): Throwable? {
        return currentException
    }

    private var timeForSeedSocket: Long = Configuration.soketConnnectTimeout

    enum class TimerControl {
        START_TIMER,
        RESTART_TIMER,
        STOP_TIMER
    }

    var subTimer: Subscription? = null
    var timerRunning: Boolean = false
    private fun timerForSeedSocket(timerCtrl: TimerControl) {
        if (subTimer == null) {
            timerRunning = false
        } else {
            timerRunning = !(subTimer as Subscription).isUnsubscribed
        }
        when (timerCtrl) {
            TimerControl.START_TIMER ->
                if (timerRunning) {
                    logd("timerForSeedSocket is runnning!")
                } else subTimer = newTimer()
            TimerControl.RESTART_TIMER ->
                if (timerRunning) {
                    subTimer?.unsubscribe()
                    subTimer = newTimer()
                } else {
                    subTimer = newTimer()
                }
            TimerControl.STOP_TIMER -> {
                logd("stop timerForSeedSocket")
                if (timerRunning) {
                    subTimer?.unsubscribe()
                }
            }
        }
    }

    fun newTimer(): Subscription {
        logd("timerForSeedSocket start!")
        return Observable.timer(timeForSeedSocket, TimeUnit.SECONDS).subscribe {
            loge("timerForSeedSocket time out!")
            currentStatus = ReconnectStates.RECONNECT_FAILED_ERROR_FATAL
            currentException = Exception("Socket Connect Timeout!")
            statusObser.onNext(currentStatus)
        }
    }

    var seedEnabled = false
    var userEnabled = false
    var enableLock = Object()

    fun setSocketConnect(dest: ServerRouter.Dest, reason: String){
        logd("setSocketConnect22 $dest $reason")
        var enable = false
        synchronized(enableLock){
            if(reason == "SeedEnabled" || reason == "SeedNotEnabled"){
                transceiver.disconnect(dest) // 为了解决 高通在后期鉴权的时候 dun拨号后没有断开socket,所以种子卡状态一变化就重建
                if(reason == "SeedEnabled"){
                    enable = true
                }else{
                    enable = false
                }
                if(enable != seedEnabled){
                    seedEnabled = enable
                    if(enable){
                        transceiver.setNeedSocketConnect(dest, "SeedNeed")
                    }else{
                        transceiver.setForbidSocketConnect(dest, "SeedNeed")
                    }
                }
            } else if(reason == "UserEnabled" || reason == "UserNotEnabled"){
                if(reason == "UserEnabled"){
                    enable = true
                }else{
                    enable = false
                }
                if(enable != userEnabled){
                    userEnabled = enable
                    if(enable){
                        transceiver.setNeedSocketConnect(dest, "UserNeed")
                    }else{
                        transceiver.setForbidSocketConnect(dest, "UserNeed")
                    }
                }
            }else{
                loge("setSocketConnect error reason: " + reason)
                return
            }
            logd("setSocketConnect22 2 $seedEnabled $userEnabled")

            if(seedEnabled || userEnabled) {
                if (transceiver.isSocketConnected(ServerRouter.Dest.ASS)) {
                    if (currentStatus != ReconnectStates.RECONNECTED) {
                        //#31319【U3C外场】云卡掉网恢复后（accessState = 3 ）却发生心跳，次超时后导致重登录
                        logd("need reconnect!! $needReconnect $currentStatus")
                        if(needReconnect){
                            requestReconnect()
                            logk("requestReconnect succ")
                        }else{
                            currentStatus = ReconnectStates.RECONNECTED
                            statusObser.onNext(currentStatus)
                        }
                    }

                } else {
                    timerForSeedSocket(TimerControl.RESTART_TIMER)
                }
            }else{
                transceiver.setForbidSocketConnect(dest, "SeedNeed")
                transceiver.setForbidSocketConnect(dest, "UserNeed")

                transceiver.disconnect(dest)

                timerForSeedSocket(TimerControl.STOP_TIMER)

                unSubRequestDirect()

                currentStatus = ReconnectStates.SOCKET_DISCONNECT
                statusObser.onNext(currentStatus)
            }
        }
    }



    private val myRunnable = object : Runnable {
        override fun run() {
            logd("Runnable ${needReconnect}")
            if (needReconnect) {
                requestReconnect()
            }
        }
    }
    val handler: Handler = Handler()
    private fun listenSocketSustainability() {
        transceiver.statusObservable(ServerRouter.Dest.ASS).subscribeWith {
            onNext {
                if (seedEnabled || userEnabled) {
                    logd("seed transceiver.statusObser onNext $it")
                    if ("SocketConnected" == it) {
                        timerForSeedSocket(TimerControl.STOP_TIMER)

                        if(needReconnect){
                            requestReconnect()
                        }else {
                            handler.postDelayed(myRunnable, 5 * 1000L);
                            logk("requestReconnect succ")
                            currentStatus = ReconnectStates.RECONNECTED
                            statusObser.onNext(currentStatus)
                        }
                    } else if ("SocketDisconnected" == it) {
                        loge("listenSocketSustainability SocketDisconnected")
                        handler.removeCallbacks(myRunnable)
                        needReconnect = true
                        unSubRequestDirect()
                        currentStatus = ReconnectStates.SOCKET_DISCONNECT
                        statusObser.onNext(currentStatus)
                    }else if("secureError" == it){
                        needReconnect = true
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SECURITY_CHECK_FAIL, SecureUtil.secureErrorCmd)
                    }else if("secureTimeout" == it){
                        needReconnect = true
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SECURITY_CHECK_TIMEOUT, SecureUtil.secureErrorCmd)
                    }

                }
            }
            onError {
                loge("seed transceiver.statusObser onError")
                it.printStackTrace()
            }
            onCompleted {
                loge("seed transceiver.statusObser onCompleted")
            }
        }
    }

//    var subDoRequestReconnect: Subscription? = null

    fun requestReconnect() {
//        if (publishReconnectStatus.hasThrowable() || publishReconnectStatus.hasCompleted()) {
//            publishReconnectStatus = PublishSubject()
//            currentStatus = ReconnectStates.RECONNECT_FAILED
//        }
//        if (currentStatus == ReconnectStates.RECONNECTING) {
//            return publishReconnectStatus
//        }
//        currentStatus = ReconnectStates.RECONNECTING
        //非服务器错误要重试 RPC_HEADER_STR不包含
        unSubRequestDirect()
        if (isNeedReconnect()) {
            doRequestReconnect().retry { count, err -> return@retry count <= 3 && RPC_HEADER_STR != err?.message }
                    .subscribe({
                        logd("send reconnect package success")
                        currentStatus = ReconnectStates.RECONNECTED
                        statusObser.onNext(currentStatus)
                    }, {
                        logd("send reconnect package fail")

                        transceiver.disconnect(ServerRouter.Dest.ASS)

                        if (RPC_HEADER_STR == it.message?.substring(0, RPC_HEADER_STR.length)) {
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_RECONNECT_MSG_FAIL, -1, 0, it)
                        }
                    })
        }else{
            logk("no need to reconnect!")
            currentStatus = ReconnectStates.RECONNECTED
            statusObser.onNext(currentStatus)
        }
//
//        subDoRequestReconnect = doRequestReconnect().subscribe({
//            currentStatus = ReconnectStates.RECONNECTED
//            publishReconnectStatus.onNext(it)
//        }, {
//            logd("doRequestReconnect RECONNECT_FAILED")
//            currentStatus = ReconnectStates.RECONNECT_FAILED
//            publishReconnectStatus.onError(it)
//        })
//        return publishReconnectStatus
    }

    fun doRequestReconnect(): Single<Any> {
        val msg = MessagePacker.createReconnectPacket()
        logd("debug requestReconnect reqPacket:$msg")
        logk("requestReconnect ...")
        var subRspOb: Subscription? = null
        return requestDirect(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRspOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) this.subscriber.unsubscribe()
                        logd("debug requestReconnect it:$it")
                        if (it is Upload_SessionId_Resp) {
                            if (it.errorCode == ErrorCode.RPC_RET_OK) {
                                logk("requestReconnect succ!")
                                sub.onSuccess(true)
                            } else {
                                sub.onError(Exception(RPC_HEADER_STR + it.errorCode))
                            }
                        } else {
                            if (it is Throwable) {
                                sub.onError(it)
                            } else {
                                sub.onError(Exception(ErrorCode.PARSE_HEADER_STR + it.toString()))
                            }
                        }
                    }
                    onError {
                        logk("requestReconnect error:${it.message},cause:${it.cause}")
                        sub.onError(it)
                    }
                }
            }.doOnUnsubscribe {
                if (subRspOb != null) {
                    logd("doRequestReconnect subRspOb doOnUnsubscribe!")
                    if (!(subRspOb as Subscription).isUnsubscribed) (subRspOb as Subscription).unsubscribe()
                }
            }
        }, 12) /*modify reconnect timeout to 12s as same as apdu*/
    }

    /**
     *
     * @param message 发送往服务器的消息
     * @return Observable<Message> 服务器响应消息监听器
     */
    fun observeResponse(message: Message): Observable<Any> {   //todo wlmark需过滤Dest过滤? 公共的解包动作在Channel里完成？注意外部取消订阅
        return transceiver.receive(message.dest).filter {
            logd("debug receiveMessageSubject ,it.id:${it.id},msg.hash:${message.hashCode()},message.id:${message.id}, filter:${it.id == message.id}")
            it.id == message.id //wlmark why the sendmsg changed? //wlmark if id not equal and have three 3 time then unsubscibe it?
        }!!.map {
            message.payload as ProtoPacket //wlmark use Avro in here?
            it.payload as ProtoPacket
            it.payload.sn = message.payload.sn
            logd("Reconnect observeResponse it.payload: $it")
            logd("Reconnect observeResponse message.payload: $message")
            val ret = packetUtil.decodeProtoPacket(it.payload)
            if (ret is CommonErrorcode) {
                Exception(RPC_HEADER_STR + ret.value)
            } else {
                ret
            }
        }
    }

    private fun unSubRequestDirect() {
        if (subRequestDirect != null) {
            if (!(subRequestDirect as SingleSubscriber<in Any>).isUnsubscribed) {
                (subRequestDirect as SingleSubscriber<in Any>).unsubscribe()
            }
        }
    }

    var subRequestDirect: SingleSubscriber<in Any>? = null

    private fun requestDirect(message: Message, handler: (response: Observable<Any>) -> Single<Any>, timeout: Long): Single<Any> {
        var subHandler: Subscription? = null
        return Single.create<Any> { sub ->
            subRequestDirect = sub
            subHandler = handler.invoke(observeResponse(message).timeout(timeout, TimeUnit.SECONDS)).timeout(timeout, TimeUnit.SECONDS).subscribe({
                sub.onSuccess(it)
            }, {
                sub.onError(it)
            })
            transceiver.send(message)

        }.doOnUnsubscribe {
            logd("requestDirect doOnUnsubscribe")
            if (subHandler != null) {
                if (!(subHandler as Subscription).isUnsubscribed) (subHandler as Subscription).unsubscribe()
            }
        }
    }

    fun isNeedReconnect(): Boolean { //新建socket 后判断
        logd("currentStatus:$currentStatus")
        if (sessionId == null) return false
        return true
    }
}