package com.ucloudlink.refact.business

import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo
import com.ucloudlink.refact.business.netcheck.NetInfo
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimBinInfoSingleReq
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo
import com.ucloudlink.refact.channel.Reconnector
import com.ucloudlink.refact.channel.channels.SeedChannel
import com.ucloudlink.refact.channel.channels.UserChannel
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthState
import com.ucloudlink.refact.channel.transceiver.protobuf.*
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.UcTimerTask
import okio.Buffer
import rx.Observable
import rx.Single
import rx.SingleSubscriber
import rx.Subscription
import rx.lang.kotlin.subscribeWith
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Requestor
 * 发送请求到MessageDispatcher
 */
object Requestor {
    //val packetUtil = AvroPacketUtil.getInstance()
    private val packetUtil = ProtoPacketUtil.getInstance()
    //val transceiver = DefaultTransceiver(PacketCodec()) //socket
    //val transceiver = NettyTransceiver() //netty
    val transceiver = ServiceManager.transceiver
    val reconnector = Reconnector(transceiver) //业务层面的reconnect超作
    val seedChannel = SeedChannel(ServiceManager.seedCardEnabler, transceiver, reconnector)
    val userChannel = UserChannel(ServiceManager.cloudSimEnabler, transceiver, reconnector)
    lateinit var mHandler: Handler

    /**
     * 设置当前请求的sessionId
     */
    fun setSessionId(s: String?): Requestor {
        reconnector.setSessionId(s)
        return this
    }

    /**
     *
     * @param message 发送往服务器的消息
     * @return Observable<Message> 服务器响应消息监听器
     */
    fun observeResponse(message: Message): Observable<Any> {
        return transceiver.receive(message.dest).filter {
            logd("debug receiveMessageSubject ,it.id:${it.id},msg.hash:${message.hashCode()},message.id:${message.id}, filter:${it.id == message.id}")
            it.id == message.id //wlmark why the sendmsg changed? //wlmark if id not equal and have three 3 time then unsubscibe it?
        }!!.map({
            message.payload as ProtoPacket //wlmark use Avro in here?
            it.payload as ProtoPacket
            it.payload.sn = message.payload.sn
            logd("Requestor observeResponse it.payload: $it")
            logd("Requestor observeResponse message.payload: $message")
            var ret = packetUtil.decodeProtoPacket(it.payload)
            if (ret is CommonErrorcode) {
                Exception(ErrorCode.RPC_HEADER_STR + ret.value)
            } else {
                ret
            }
        })
    }


    val runningRequest = Collections.synchronizedMap(HashMap<String, SingleSubscriber<in Any>>())

    /**
     * 根据tag 中止业务请求
     * 被中止的业务请求会抛出AbortException
     * note:如有需要，请中断请求后，自己释放种子通道
     *
     * @param tag 请求的标签
     * @return true 表示中断成功，false表示中断失败
     */
    fun abortRequest(tag: String): Boolean {
        val subscriber = runningRequest[tag]
        if (subscriber == null) {
            logd("abortRequest fail tag:$tag")
            return false
        }

        subscriber.onError(AbortException())
        runningRequest.remove(tag)
        return true
    }

    /**
     * 通过传入lambda表达式的方式来对后台返回消息进行处理，可以根据不同的业务场景传入不同的处理函数。
     * 例如：下载文件可能会返回多个数据包，此时可以传入一个lambda函数对这几个数据包进行合并，最终返回合并完成的文件。
     *
     * @param message 发送给服务器的消息
     * @param handler 服务器返回消息的处理函数，处理完成返回一个Observable携带处理之后的数据
     * @param canBeAbort 如果为true，当取消订阅时，中断请求
     * @param timeout
     *
     */
    private fun request(message: Message, handler: (response: Observable<Any>) -> Single<Any>, timeout: Long, canBeAbort: Boolean = false): Single<Any> {
        var subDispatchChannel: Subscription? = null
        var subHandler: Subscription? = null
        var hasAbort = false
        return Single.create<Any> { sub ->

            subDispatchChannel = dispatchChannel(message.priority, message.dest, getFixedTime()).timeout(getFixedTime(), TimeUnit.SECONDS).subscribe({
                //检查释放被打断

                if (hasAbort) return@subscribe logd("dispatchChannel success but request has aborted")


                logd("request send msg succ")
                subHandler = handler.invoke(observeResponse(message).timeout(timeout, TimeUnit.SECONDS)).timeout(timeout, TimeUnit.SECONDS).subscribe({

                    if (hasAbort) return@subscribe logd("observeResponse success but request has aborted")

                    logd("recv msg:" + it)
                    if (it is Exception) {
                        sub.onError(it)
                    } else {
                        sub.onSuccess(it)
                    }
                }, {
                    if (hasAbort) return@subscribe logd("observeResponse fail but request has aborted")

                    if (it is TimeoutException) {
                        sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "business time out! 1, " + timeout))
                    } else {
                        sub.onError(it)
                    }
                })
                transceiver.send(message)
            }, {


                if (hasAbort) return@subscribe logd("dispatchChannel fail but request has aborted")


                if (it is TimeoutException) {
                    loge("request error, can't run here")
                    it.printStackTrace()
                    sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "business time out! 2, " + getFixedTime()))
                } else {
                    sub.onError(it)
                }
            })
        }.doOnUnsubscribe {
            if (canBeAbort) hasAbort = true
            if (subDispatchChannel != null) {
                if (!(subDispatchChannel as Subscription).isUnsubscribed) (subDispatchChannel as Subscription).unsubscribe()
            }
            if (subHandler != null) {
                if (!(subHandler as Subscription).isUnsubscribed) (subHandler as Subscription).unsubscribe()
            }
        }
    }

    fun requestForShortTime(message: Message, handler: (response: Observable<Any>) -> Single<Any>, sendTimeout: Long, revTimeout: Long = sendTimeout): Single<Any> {
        var subDispatchChannel: Subscription? = null
        var subHandler: Subscription? = null
        val channelBeginTime = SystemClock.elapsedRealtime()
        return Single.create<Any> { sub ->
            subDispatchChannel = dispatchChannel(message.priority, message.dest, sendTimeout).timeout(sendTimeout, TimeUnit.SECONDS).subscribe({
                /*
                为保证超出发送时间的消息在通道可用时也不要发出，这里增加一个时差计算
                 */
                val channelSuccessTime = SystemClock.elapsedRealtime()
                if (channelSuccessTime - channelBeginTime >= sendTimeout * 1000) {
                    logd("seed message Time out message id:${message.id}")
                    sub.onError(Exception("TIMEOUT:business time out! 3, " + sendTimeout))
                } else {
                    logv("request send msg success, message id:${message.id}")
                    subHandler = handler.invoke(observeResponse(message).timeout(revTimeout, TimeUnit.SECONDS)).timeout(revTimeout, TimeUnit.SECONDS).subscribe({
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }, {
                        if (it is TimeoutException) {
                            sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "business time out! 3, " + sendTimeout))
                        } else {
                            sub.onError(it)
                        }
                    })
                    transceiver.send(message)
                }
            }, {
                if (it is TimeoutException) {
                    loge("requestForShortTime no time for dispatchChannel")
                    sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "business time out! 4, " + sendTimeout))
                } else {
                    sub.onError(it)
                }
            })
        }.doOnUnsubscribe {
            if (subDispatchChannel != null) {
                if (!(subDispatchChannel as Subscription).isUnsubscribed) (subDispatchChannel as Subscription).unsubscribe()
            }
            if (subHandler != null) {
                if (!(subHandler as Subscription).isUnsubscribed) (subHandler as Subscription).unsubscribe()
            }
        }
    }

    /**
     * 通过传入lambda表达式的方式来对后台返回消息进行处理，可以根据不同的业务场景传入不同的处理函数。
     * 例如：下载文件可能会返回多个数据包，此时可以传入一个lambda函数对这几个数据包进行合并，最终返回合并完成的文件。
     *
     * @param priority 消息通道选择优先权 云卡优先/只走用户通道/只有种子通道
     * @param dest 消息目的地
     * @param getMsg 生成消息的方法。此方法在通道建立成功，将要发送时回调，返回null表示不发消息，同时请求会报AbortException
     * @param handler 服务器返回消息的处理函数，处理完成返回一个Observable携带处理之后的数据
     * @param timeout 超时时间
     *
     */
    private fun request(priority: Priority, dest: ServerRouter.Dest, getMsg: () -> Message?, handler: (response: Observable<Any>) -> Single<Any>, timeout: Long, tag: String? = null): Single<Any> {
        var subDispatchChannel: Subscription? = null
        var subHandler: Subscription? = null
        return Single.create<Any> { sub ->
            if (tag != null) {
                runningRequest[tag] = sub
            }
            subDispatchChannel = dispatchChannel(priority, dest, getFixedTime()).timeout(getFixedTime(), TimeUnit.SECONDS).subscribe({
                //检查释放被打断
                if (tag != null) {
                    val subscriber = runningRequest[tag]
                    subscriber ?: return@subscribe logd("dispatchChannel success but request has aborted")
                }

                val message = getMsg.invoke()
                message ?: return@subscribe sub.onError(AbortException())

                logd("request send msg succ")
                subHandler = handler.invoke(observeResponse(message).timeout(timeout, TimeUnit.SECONDS)).timeout(timeout, TimeUnit.SECONDS).subscribe({
                    if (tag != null) {
                        val subscriber = runningRequest[tag]
                        subscriber ?: return@subscribe logd("observeResponse success but request has aborted")
                        runningRequest.remove(tag)
                    }

                    logd("recv msg:" + it)
                    if (it is Exception) {
                        sub.onError(it)
                    } else {
                        sub.onSuccess(it)
                    }
                }, {
                    if (tag != null) {
                        val subscriber = runningRequest[tag]
                        subscriber ?: return@subscribe logd("observeResponse fail but request has aborted")
                        runningRequest.remove(tag)
                    }

                    if (it is TimeoutException) {
                        sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "business time out! 1, " + timeout))
                    } else {
                        sub.onError(it)
                    }
                })
                transceiver.send(message)
            }, {

                if (tag != null) {
                    val subscriber = runningRequest[tag]
                    subscriber ?: return@subscribe logd("dispatchChannel fail but request has aborted")
                    runningRequest.remove(tag)
                }

                if (it is TimeoutException) {
                    loge("request error, can't run here")
                    it.printStackTrace()
                    sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "business time out! 2, " + getFixedTime()))
                } else {
                    sub.onError(it)
                }
            })
        }.doOnUnsubscribe {
            if (subDispatchChannel != null) {
                if (!(subDispatchChannel as Subscription).isUnsubscribed) (subDispatchChannel as Subscription).unsubscribe()
            }
            if (subHandler != null) {
                if (!(subHandler as Subscription).isUnsubscribed) (subHandler as Subscription).unsubscribe()
            }
        }
    }

    //var private AtomicInteger number = new AtomicInteger(0);
    val atomicInteger = AtomicInteger(0)

    val requireIdList: ArrayList<String> = ArrayList<String>()


    /**
     * 请求一个通道，可能是用户通道，可能是种子通道，确保有一个通道可用，并且锁定它
     * id 表示请求ID 相同ID 在释放之前,增加计数器只加一次
     */
    fun requireChannel(id: String) {
        var count: Int = atomicInteger.get()
        if (TextUtils.isEmpty(id)) {
            count = atomicInteger.incrementAndGet()
        } else {
            if (!requireIdList.contains(id)) {
                requireIdList.add(id!!)
                count = atomicInteger.incrementAndGet()
            }
        }
        seedChannel.hasRequire = true
        logd("requireChannel id: $id value: $count")

    }

    /**
     * 解除锁定的通道
     * id 表示请求ID
     */
    fun releaseChannel(id: String) {
        val count: Int
        if (TextUtils.isEmpty(id)) {
            count = atomicInteger.decrementAndGet()
        } else {
            if (requireIdList.contains(id)) {
                requireIdList.remove(id!!)
                count = atomicInteger.decrementAndGet()
            } else {
                loge("no such requireID")
//                count = atomicInteger.get()
//                if (!seedChannel.dataEnabler.isCardOn()) {
//                    logv("seedChannel had closed!")
                return
//                }
            }
        }

//        var value = atomicInteger.decrementAndGet()
        logd("releaseChannel value: $count id:$id")
        if (count == 0) {
            seedChannel.hasRequire = false
            seedChannel.close(ServerRouter.Dest.ASS)
        } else if (count < 0) {
            logke("releaseChannel error value: $count")
        }
    }

    fun clearRequireId(id: String) {
        if (requireIdList.contains(id)) {
            requireIdList.remove(id)
            atomicInteger.decrementAndGet()
        }
    }

    private fun dispatchChannel(channel: Priority, dest: ServerRouter.Dest, timeout: Long): Single<Any> {
        var subSeedEnable: Subscription? = null
        var subUserEnable: Subscription? = null
        return Single.create<Any> { sub ->
            when (channel) {
                Priority.ALWAYS_SEED_CHANNEL -> {
                    logd("ALWAYS_SEED_CHANNEL")
                    subSeedEnable = seedChannel.enable(dest, timeout).subscribe({
                        logd("seedChannel.enable get next1")
                        sub.onSuccess(it)
                    }, {
                        if (it is TimeoutException) {
                            sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "seedChannel.enable timeout" + timeout))
                        } else {
                            sub.onError(it)
                        }
                    })
                }
                Priority.ALWAYS_USER_CHANNEL -> {
                    subUserEnable = userChannel.enable(dest, timeout).subscribe({ sub.onSuccess(it) }, {
                        if (it is TimeoutException) {
                            sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "userChannel.enable 1!!!" + timeout))
                        } else {
                            sub.onError(it)
                        }
                    })
                }
                Priority.TRY_USER_CHANNEL -> {
                    logd("TRY_USER_CHANNEL")
                    if (userChannel.isDataEnabled()) {
                        subUserEnable = userChannel.enable(dest, timeout).subscribe(
                                {
                                    sub.onSuccess(it)
                                },
                                {
                                    if (it is TimeoutException) {
                                        sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "seedChannel.enable timeout 2!!!" + timeout))
                                    } else {
                                        sub.onError(it)
                                    }
                                })
                    } else {
                        subSeedEnable = seedChannel.enable(dest, timeout).subscribe({ sub.onSuccess(it) }, {
                            if (it is TimeoutException) {
                                sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "seedChannel.enable timeout 3!!!" + timeout))
                            } else {
                                sub.onError(it)
                            }
                        })
                    }
                }
            }
        }.doOnUnsubscribe {
            if (subSeedEnable != null) {
                if (!(subSeedEnable as Subscription).isUnsubscribed) {
                    (subSeedEnable as Subscription).unsubscribe()
                }
            }
            if (subUserEnable != null) {
                if (!(subUserEnable as Subscription).isUnsubscribed) {
                    (subUserEnable as Subscription).unsubscribe()
                }
            }
        }
    }

    fun resetSeedRequestor() {
        seedChannel.hasRequire = false
        seedChannel.close(ServerRouter.Dest.ASS)
        requireIdList.clear()
        atomicInteger.set(0)
        currentApduStatus = AuthState.AUTH_SUCCESS
    }

    fun resetApduStatus() {
        currentApduStatus = AuthState.AUTH_SUCCESS
    }

    fun resetUserRequestor() {
        userChannel.close(ServerRouter.Dest.ASS)
    }

    fun getFixedTime(): Long {
        var fixedTime: Long = 0
        if (Configuration.ApduMode == Configuration.ApduMode_Phy) {
            fixedTime = Configuration.enablePhysimTimeout + Configuration.soketConnnectTimeout
        } else if (Configuration.ApduMode == Configuration.ApduMode_soft) {
            fixedTime = Configuration.enableSoftsimTimeout + Configuration.soketConnnectTimeout
        } else {
            loge("getFixedTime error ApduMode: " + Configuration.ApduMode)
        }
        fixedTime += 2
        return fixedTime
    }

    fun requestLogin(getMsg: () -> LoginRequestInfo?, timeout: Int): Single<Any> {
        var subRespOb: Subscription? = null
        logk("requestLogin ...")
        return request(Priority.ALWAYS_SEED_CHANNEL, ServerRouter.Dest.ASS, {
            val requestInfo = getMsg.invoke()
            requestInfo ?: return@request null

            return@request MessagePacker.createLoginPacket(userName = requestInfo.userName,
                    password = requestInfo.password,
                    loginType = requestInfo.loginType,
                    imei = requestInfo.networkInfo.imei,
                    lac = requestInfo.networkInfo.lac,
                    cellId = requestInfo.networkInfo.cellid,
                    seedImsi = requestInfo.networkInfo.imsi,
                    iccid = requestInfo.networkInfo.iccid,
                    softVersion = requestInfo.networkInfo.version,
                    reverse = "reverse",
                    logId = 1,
                    flowSize = 1024,
                    sidList = requestInfo.networkInfo.sidList,
                    loginReason = requestInfo.loginReson
            )
        }, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("request login rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("Login failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug requestLogin onCompleted")
                        sub.onError(Exception("requestLogin canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong())
    }

    fun requestLogin(userName: String, password: String, loginType: Int, networkInfo: NetInfo, timeout: Int, loginReson: Int): Single<Any> {
        var subRespOb: Subscription? = null
        logd(" networkInfo : $networkInfo")
        val msg = MessagePacker.createLoginPacket(userName = userName,
                password = password,
                loginType = loginType,
                imei = networkInfo.imei,
                lac = networkInfo.lac,
                cellId = networkInfo.cellid,
                seedImsi = networkInfo.imsi,
                iccid = networkInfo.iccid,
                softVersion = networkInfo.version,
                reverse = "reverse",
                logId = 1,
                flowSize = 1024,
                sidList = networkInfo.sidList,
                loginReason = loginReson
        )
        logd("debug requestLogin reqPacket:$msg")
        logk("requestLogin ...")
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("request login rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("Login failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug requestLogin onCompleted")
                        sub.onError(Exception("requestLogin canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong())
    }

    fun requestDownloadSoftCard(): Observable<Any> {
        return Observable.just(true)
    }

    /**
     * 下卡
     */
    fun requestDownloadCloudCard(timeout: Int): Single<Any> {
        val msg = MessagePacker.createDownloadCardPacket()
        logd("debug downloadCard reqPacket:$msg")
        logk("requestDownloadCloudCard ...")
        //var subHandler: Subscription? = null
        var subRespOb: Subscription? = null
        var subObservable: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { subEnd ->
                subObservable = Observable.create<GetBinFileResp> { sub ->
                    var packetReceived = 0
                    subRespOb = responseOb.timeout(10, TimeUnit.SECONDS).subscribeWith {
                        onNext {
                            if (it is GetBinFileResp) {
                                if (it.errorCode == ErrorCode.RPC_RET_OK) {

                                    val rsp: GetBinFileResp = it as GetBinFileResp //wlmark not use MessagePacker?
                                    logd("debug requestDownloadCard recv rsp :$rsp")
                                    val total = rsp.packettotal
                                    sub.onNext(rsp)
                                    packetReceived++
                                    logd("total:$total,received:$packetReceived")
                                    if (total == packetReceived) {
                                        if (!this.subscriber.isUnsubscribed) {
                                            this.subscriber.unsubscribe()
                                        }
                                        sub.onCompleted()
                                    }
                                } else {
                                    sub.onError(Exception(ErrorCode.RPC_HEADER_STR + it.errorCode))
                                }
                            } else {
                                if (it is Exception) {
                                    sub.onError(it)
                                } else {
                                    sub.onError(Exception(ErrorCode.PARSE_HEADER_STR + it.toString()))
                                }
                            }

                        }
                        onError {
                            if (it is TimeoutException) {
                                sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "download card cb timeout!!!"))
                            } else {
                                sub.onError(it)
                            }
                        }
                    }
                }.toSortedList({ t1, t2 ->
                    when {
                        t1.packetindex > t2.packetindex -> 1
                        t1.packetindex < t2.packetindex -> -1
                        else -> 0
                    }
                }).map {
                    val buf = Buffer()
                    for (p in it) {
                        buf.write(p.data.toByteArray())
                    }
                    buf.readByteArray()
                }.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("debug downloadCard finished!")
                        logk("download Cloud Card succ!")
                        subEnd.onSuccess(it)
                    }
                    onError { subEnd.onError(it) }
                    onCompleted { subEnd.onError(Exception("requestDownloadCloudCard canceled!")) }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
                if (subObservable != null) {
                    if (!(subObservable as Subscription).isUnsubscribed) (subObservable as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong())
    }

    var currentApduStatus = AuthState.AUTH_SUCCESS
    internal var timer = Timer()
    internal var mRstCardTask: checkApduDoneTask? = null
    private val rstDelay: Long = Configuration.rstCardDelay

    val EVENT_RELEASE_CHANNEL = 1
    val EVENT_STOP_RELEASE_CHANNEL = 2
    val EVENT_RELEASE_APDU = 3
    val EVENT_STOP_RELEASE_APDU = 4

    fun stopTimerForApduDone() {
        mRstCardTask?.cancel()
        mRstCardTask = null
    }


    fun startTimerForApduDone(isAuthSuccess: Boolean = true, delayTime: Long = rstDelay) {

        logv("startTimerForApduDone para: $delayTime ms")
        mRstCardTask?.cancel()
        mRstCardTask = checkApduDoneTask(isAuthSuccess)
        mRstCardTask!!.whenTime = delayTime
        timer.schedule(mRstCardTask, delayTime)
    }

    class checkApduDoneTask(private val authSucc: Boolean) : UcTimerTask() {
        override fun run() {
            logv("checkApduDoneTask run releaseChannel authSucc :$authSucc")
            releaseChannel(apduid)
        }
    }

    fun init(seedCardEnabler: IDataEnabler, cloudSimEnabler: IDataEnabler, looper: Looper) {
        mHandler = Handler(looper) { msg ->
            when (msg.what) {
                EVENT_RELEASE_CHANNEL -> {
                    val delayTime = msg.arg1
                    val isAuthSuccess = msg.arg2 == 0
                    if (mRstCardTask == null || mRstCardTask!!.whenTime <= delayTime + System.currentTimeMillis()) {
                        startTimerForApduDone(isAuthSuccess, delayTime.toLong())
                    } else {
                        logv("do not start timer: $mRstCardTask ${mRstCardTask!!.whenTime} next timer: ${delayTime + System.currentTimeMillis()}")
                    }
                    true
                }
                EVENT_STOP_RELEASE_CHANNEL -> {
                    stopTimerForApduDone()
                    true
                }
                EVENT_RELEASE_APDU -> {
                    val delayTime = msg.arg1.toLong()
                    unDoDunCall(delayTime)
                    true
                }
                EVENT_STOP_RELEASE_APDU -> {
                    stopUndoDunCall()
                    true
                }
                else -> {
                    false
                }
            }
        }
        ServiceManager.cardController.addAuthListen { authState ->
            currentApduStatus = authState
            val cardStatus = cloudSimEnabler.getCardState()
            //云卡disable后忽略鉴权消息
            if (cardStatus >= CardStatus.READY && !cloudSimEnabler.isClosing()) {
                if (authState == AuthState.AUTH_BEGIN) {
                    mHandler.obtainMessage(EVENT_STOP_RELEASE_APDU).sendToTarget()
                    mHandler.obtainMessage(EVENT_STOP_RELEASE_CHANNEL).sendToTarget()
                    requireChannel(apduid)
                } else if (authState == AuthState.AUTH_SUCCESS) {
                    logk("cloudsim auth done,will close seedCard")

                    mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 30 * 1000, 0).sendToTarget()
                    //如果云卡已经拨号上，开始关dun拨号延时
                    if (cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED) {

                        mHandler.obtainMessage(EVENT_RELEASE_APDU, 6 * 1000, 0).sendToTarget()
                    } else if (cardStatus == CardStatus.IN_SERVICE) {

                        mHandler.obtainMessage(EVENT_RELEASE_APDU, 12 * 1000, 0).sendToTarget()
                    }
                } else if (authState == AuthState.AUTH_FAIL) {
                    val netState = cloudSimEnabler.getNetState()
                    logk("cloudsim auth Fail,check cloudSimNetState : $netState")
                    val seedState = seedCardEnabler.getCardState()
                    if (seedState == CardStatus.IN_SERVICE) {

                        mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 30 * 1000, 1).sendToTarget()
                    } else {
                        loge("seedState is not In_service need more time")

                        mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 180 * 1000, 1).sendToTarget()
                    }
                }
            }
        }
        /*CardController.cloudSimStateObservable().subscribe({
            currentApduStatus = it
            val cardStatus = cloudSimEnabler.getCardState()
            //云卡disable后忽略鉴权消息
            if (cardStatus >= CardStatus.READY && !cloudSimEnabler.isClosing()) {
                if (it == APDUStatus.NEEDAUTH) {
                    mHandler.obtainMessage(EVENT_STOP_RELEASE_APDU).sendToTarget()
                    mHandler.obtainMessage(EVENT_STOP_RELEASE_CHANNEL).sendToTarget()
                    requireChannel(apduid)
                } else if (it == APDUStatus.AUTHSUCC) {
                    logk("cloudsim auth done,will close seedCard")

                    mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 40 * 1000, 0).sendToTarget()
                    //如果云卡已经拨号上，开始关dun拨号延时
                    if (cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED) {

                        mHandler.obtainMessage(EVENT_RELEASE_APDU, 6 * 1000, 0).sendToTarget()
                    } else if (cardStatus == CardStatus.IN_SERVICE) {

                        mHandler.obtainMessage(EVENT_RELEASE_APDU, 12 * 1000, 0).sendToTarget()
                    }
                } else if (it == APDUStatus.AUTHFAIL) {
                    val netState = cloudSimEnabler.getNetState()
                    logk("cloudsim auth Fail,check cloudSimNetState : $netState")
                    val seedState = seedCardEnabler.getCardState()
                    if (seedState == CardStatus.IN_SERVICE) {

                        mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 60 * 1000, 1).sendToTarget()
                    } else {
                        loge("seedState is not In_service need more time")

                        mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 180 * 1000, 1).sendToTarget()
                    }
                }
            }*//* else {
                releaseChannel(apduid)
            }*//*
        }, {})*/
        cloudSimEnabler.cardStatusObser().subscribeWith {
            onNext {
                if (it == CardStatus.IN_SERVICE) {
                    val seedType = seedCardEnabler.getCard().cardType
                    if (currentApduStatus != AuthState.AUTH_BEGIN) {
                        if (seedType == CardType.PHYSICALSIM) {
                            mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 3 * 1000, 0).sendToTarget()
                        } else {
                            mHandler.obtainMessage(EVENT_RELEASE_CHANNEL, 60 * 1000, 0).sendToTarget()
                            mHandler.obtainMessage(EVENT_RELEASE_APDU, 5 * 1000, 0).sendToTarget()
                        }
                    } else {
                        logv("seedType != CardType.PHYSICALSIM || currentApduStatus == AuthState.AUTH_BEGIN")
                    }
                }
            }
        }
    }

    private var unDoDunCallTask: UnDoDunCallTask? = null
    private fun unDoDunCall(delayTime: Long) {
        logd("unDoDunCall delay:$delayTime")
        unDoDunCallTask?.cancel()
        unDoDunCallTask = UnDoDunCallTask()
        timer.schedule(unDoDunCallTask, delayTime)
    }

    fun stopUndoDunCall() {
        if (unDoDunCallTask != null) {
            logd("stopUndoDunCall")
        }
        unDoDunCallTask?.cancel()
        unDoDunCallTask = null
    }

    private class UnDoDunCallTask : TimerTask() {
        override fun run() {
            seedChannel.dataEnabler.notifyEventToCard(DataEnableEvent.EVENT_RELEASE_DUN_OUTSIDE, null)
        }
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
    fun ByteArray.toHex(): String {
        val result = StringBuffer()
        forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS[firstIndex])
            result.append(HEX_CHARS[secondIndex])
        }
        return result.toString()
    }

    val apduCache = ConcurrentHashMap<@Volatile String, @Volatile ByteArray>(50)//fixme 是否需要置空
    val apduid = "FOR_AUTH"
    var lastApdu: ByteArray? = null
/*
    fun requestApdu(apduReq: ApduData): Single<Any> {
        var subRespOb: Subscription? = null
        val reqStr = ByteString.of(apduReq.apduData, 0, apduReq.apduData.size).toString() + apduReq.imsi
        var timeout: Long = 0

        if (lastApdu.toString() == apduReq.apduData.toString()) {
            logd("=apduReq.apduData:" + apduReq.apduData.toString() + "lastApdu:" + lastApdu.toString())
            timeout = 6
        } else {
            lastApdu = apduReq.apduData
            logd("apduReq.apduData:" + apduReq.apduData.toString() + "lastApdu:" + lastApdu.toString())
            timeout = 12
        }

        if (apduCache.containsKey(reqStr)) {
            val rspApdu = apduCache[reqStr] as ByteArray
            logv("apdu cache: ${rspApdu.toHex()}")
            return single<Any> { sub -> sub.onSuccess(rspApdu) }
        }
        val msg = MessagePacker.createApduReqPacket(apduReq)
        msg.payload as ProtoPacket
        val sn = msg.payload.sn
        val ip = ServerRouter.current_AssIp
        val currentSocketStatus = seedChannel.currentSocketStatus
        val seedSignalStrength = OperatorNetworkInfo.signalStrength
        val rat = OperatorNetworkInfo.rat
        logk(" Action:RequestApdu,$sn,$ip,$currentSocketStatus,$seedSignalStrength,$rat")
//        logk("request Apdu from server...serial = ${msg.payload.id}")
        PerformanceStatistics.process = ProcessState.CLOUD_AUTH_REQ
        return requestForShortTime(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.map {
                    //it as ByteBuffer
                    //it.array()
                    if (it is ApduAuthResp) {
                        if (it.errorCode == ErrorCode.RPC_RET_OK) {
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, 0, 0, "Succ")
                            it.data.toByteArray()
                        } else {
                            val exception = Exception(ErrorCode.RPC_HEADER_STR + it.errorCode)

                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0, exception)
                            sub.onError(exception)
                        }
                    } else if (it is Exception) {
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0, it)
                        sub.onError(it)
                    } else {
                        val exception = ErrorCode.PARSE_HEADER_STR + it.toString()
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0, exception)

                        sub.onError(Exception(exception))
                    }
                }.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        PerformanceStatistics.process = ProcessState.CLOUD_AUTH_RSP
                        //再一次检查缓存，如果缓存有就发送缓存的
                        logd("get ApduRsp from server success! serial = ${msg.payload.sn}")
                        val apduRsp: ByteArray
                        if (apduCache.containsKey(reqStr)) {
                            apduRsp = apduCache[reqStr] as ByteArray
                            logd("use apdu rsp in cache :$apduRsp")
                        } else {
                            apduCache[reqStr] = it as ByteArray
                            apduRsp = it
                        }
                        sub.onSuccess(apduRsp)
                    }
                    onError {
                        logd("get ApduRsp from server failed! serial = ${msg.payload.sn}")
                        PerformanceStatistics.process = ProcessState.CLOUD_AUTH_FAIL
                        sub.onError(it)
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout, 30)
    }*/

    /**
     * 登出指令
     */
    fun requestLogout(timeout: Int): Single<Any> {
        logk("requestLogout ...")
        var subRespOb: Subscription? = null
        val msg = MessagePacker.createLogoutPacket(Priority.TRY_USER_CHANNEL)
        logd("debug requestLogout reqPacket:$msg")
        logk("requestLogout ...")
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) this.subscriber.unsubscribe()
                        logd("debug requestLogout it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logd("requestLogout error:${it.message}")
                        if (it is TimeoutException) {
                            sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "logout cb timeout!!!"))
                        } else {
                            sub.onError(it)
                        }
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong()) // set timeout 2s
    }

    /**
     * 心跳指令
     */
    fun requestHeartBeat(timeout: Int): Single<Any> {
        /*
        FIXME 2017年2月15日
        由于小米手机基线版本较低(v1.0),会如果心跳在无信号状态拉起种子卡拨dun网络,可能会导致种子卡PS不回复
        这里针对这个问题作出修改,如果 是MIUI系统 且 种子通道为SIMCARD 且 首选DDS不在种子卡时
        心跳只走用户通道,待小米手机基线升级后可以考虑去掉
         */
//        val DDSubId = SubscriptionManager.getDefaultDataSubId()
//        val DDSubId = ServiceManager.systemApi.getDefaultDataSubId()
//        val DDSlotId = SubscriptionManager.getSlotId(DDSubId)
//        val seedSimSlot = Configuration.seedSimSlot
        val priority = Priority.TRY_USER_CHANNEL
//        if (ServiceManager.getSystemType() == Configuration.ANDROID_MIUI_V8
//                && seedChannel.dataEnabler.getDeType() == DeType.SIMCARD
//                && DDSlotId != seedSimSlot) {
//            priority = Priority.ALWAYS_USER_CHANNEL
//        }
        val msg = MessagePacker.createHeartBeatPacket(priority)
        logd("debug requestHeartBeat reqPacket:$msg")
        logk("requestHeartBeat ...")
        var subRespOb: Subscription? = null
        return request(priority, ServerRouter.Dest.ASS, {
            val msg = MessagePacker.createHeartBeatPacket(priority)
            return@request msg
        }, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) this.subscriber.unsubscribe()
                        logk("debug requestHeartBeat it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requestHeartBeat error:${it.message}")
                        if (it is TimeoutException) {
                            sub.onError(Exception("TIMEOUT:request heartbeat cb timeout!!!"))
                        } else {
                            sub.onError(it)
                        }
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong())
    }

    /**
     * ip配置 system_config获取系统参数
     * getConfPara：获取系统参数上传参数。长度需要小于 len < 256
     *              示例："G2_TE_6290_WIN,0.0.01,imei,0,usercode"
     *                    "G2_TE_6290_WIN,config文件版本号,imei,0,用户名"
     */
    fun requestConfigData(getConfPara: String): Single<Any> {
        var msg = MessagePacker.createConfigDataPacket(getConfPara)
        logd("debug requestConfigData reqPacket:$msg")
        logk("requestConfigData ...")
        return request(msg, {
            responseOb ->
            Single.create { sub ->
                responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requestConfigData it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requestConfigData error:${it.message}")
                        sub.onError(it)
                    }
                }
            }
        }, 35)
    }

    /**
     * 流量上报指令3 uf3
     */
    /*fun requestUploadFlow(flowParam: NewFlowlogDTO2): Single<Any> {
        val msg = MessagePacker.createUploadFlowPacket(flowParam)
        logd("debug requestUploadFlow reqPacket:$msg")
        logd("requestUploadFlow content ${flowParam.toString()}")
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("get requestUploadFlow info succ! ...")
                        sub.onSuccess(true)
                    }
                    onError {
                        logk("get requestUploadFlow info failed! ...")
                        if(it is TimeoutException){
                            sub.onError(Exception("TIMEOUT:requestUploadFlow cb timeout!!!"))
                        }else {
                            sub.onError(it)
                        }
                    }
                    onCompleted {
                        logd("debug requestUploadFlow onCompleted")
                        sub.onError(Exception("requestSwitchCloudSim canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, 20)
    }*/

    /**
     * 流量上报指令3 uf3
     */
    fun requestUploadFlow3(uploadFlowsizeReq: UploadFlowsizeReq): Single<Any> {
        logk("uploadFlowsizeReq content $uploadFlowsizeReq")
        var subRespOb: Subscription? = null
        return request(MessagePacker.createUploadFlowPacket(uploadFlowsizeReq), {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("get requestUploadFlow3 info recv! ..." + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("get requestUploadFlow3 info failed! ...")
                        if (it is TimeoutException) {
                            sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "requestUploadFlow3 cb timeout!!!"))
                        } else {
                            sub.onError(it)
                        }
                    }
                    onCompleted {
                        logd("debug requestUploadFlow3 onCompleted")
                        sub.onError(Exception("requestUploadFlow3 canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, 35)
    }

    /**
     * 流量补报
     */
    fun requestSupplemenUploadFlowsize(imei: Long, supplementUf: List<SupplemenUf>): Single<Any> {
        var supple = SupplemenUploadFlowsize(imei, supplementUf)
        var msg = MessagePacker.createSupplemenUploadFlowsize(supple)
        var subRespOb: Subscription? = null
        logd("CCFlow SupplyFlowLog requestSupplemenUploadFlowsize: ${imei}," + supplementUf)
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("CCFlow SupplyFlowLog get requestSupplemenUploadFlowsize info recv! ..." + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("CCFlow SupplyFlowLog get requestSupplemenUploadFlowsize info failed! ... $it")
                        if (it is TimeoutException) {
                            sub.onError(Exception(ErrorCode.TIMEOUT_HEADER_STR + "requestSupplemenUploadFlowsize cb timeout!!!"))
                        } else {
                            sub.onError(it)
                        }
                    }
                    onCompleted {
                        logd("CCFlow SupplyFlowLog debug requestSupplemenUploadFlowsize onCompleted")
                        sub.onError(Exception("requestSupplemenUploadFlowsize canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, 35)
    }
    /**
     * 流量上报指令2 uf4
     */
    /*fun requestUploadFlow4(flowParam4: NewFlowlogDTO4): Single<Any> {
        val msg = MessagePacker.createUploadFlowPacket4(flowParam4)
        logd("debug requestUploadFlow4 reqPacket:$msg")
        logk("requestUploadFlow4 content ${flowParam4.toString()}")
        return request(msg, {
    fun requestUploadFlow3(uploadFlowsizeReq: UploadFlowsizeReq): Single<Any> {
        logk("uploadFlowsizeReq content $uploadFlowsizeReq")
        return request(MessagePacker.createUploadFlowPacket(uploadFlowsizeReq), {
            responseOb ->
            Single.create { sub ->
                responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("get requestUploadFlow3 info succ! ...")
                        sub.onSuccess(true)
                    }
                    onError {
                        logk("get requestUploadFlow3 info failed! ...")
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug requestUploadFlow3 onCompleted")
                        sub.onError(Exception("requestUploadFlow3 canceled!"))
                    }
                }
            }
        }, 20)
    }*/

    /**
     * 流量补报指令
     */
    /*fun requestFlowSupplementary(flowsupplementary2: flowsupplementaryforbusiness2): Single<Any> {
        val msg = MessagePacker.createFlowSupplementaryPacket(flowsupplementary2)
        logd("debug requestFlowSupplementary reqPacket:$msg")
        logk("requestFlowSupplementary content ${flowsupplementary2.toString()}")
        return request(msg, {
            responseOb ->
            Single.create { sub ->
                responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("get requestFlowSupplementary info succ! ...")
                        sub.onSuccess(true)
                    }
                    onError {
                        logk("get requestFlowSupplementary info failed! ...")
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug requestFlowSupplementary onCompleted")
                        sub.onError(Exception("requestFlowSupplementary canceled!"))
                    }
                }
            }
        }, 20)
    }*/

    /**
     * 换卡指令
     */
    fun requestSwitchCloudSim(cause: Int, subReason: Int, reserve: CharSequence, plmnList: List<PlmnInfo>, timeout: Int): Single<Any> {
        val msg = MessagePacker.createSwitchCloudSimPacket(cause, subReason, reserve, plmnList)
        logd("debug requestSwitchCloudSim reqPacket:$msg")
        logk("requestSwitchCloudSim ...")
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("get SwitchCloudSim info recv! ..." + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("get SwitchCloudSim info failed! ...")
                        if (it is TimeoutException) {
                            sub.onError(Exception("TIMEOUT:SwitchCloudSim cb timeout!!!"))
                        } else {
                            sub.onError(it)
                        }
                    }
                    onCompleted {
                        logd("debug requestSwitchCloudSim onCompleted")
                        sub.onError(Exception("requestSwitchCloudSim canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong(), true)
    }

    /**
     * ip配置 system_config版本更新检测
     * checkPara:检查更新上传参数。长度需要小于 len < 200
     *           示例："G2_TE_6290_WIN,0.0.01,0.0.01,imei,0,0,usercode"
     *                 UCLOUD_SW_SID_WIN,系统软件版本号,config文件版本号,imei,0,0,用户名
     */
    fun requestVersionCheck(checkPara: String): Single<Any> {  // todo:上层需要判断it类型是否为exception
        val msg = MessagePacker.createVersionCheckPacket(checkPara)
        logd("debug requestVersionCheck reqPacket:$msg")
        logk("requestVersionCheck ...")
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requestVersionCheck it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requestVersionCheck error:${it.message}")
                        if (it is TimeoutException) {
                            sub.onError(Exception("TIMEOUT:requestVersionCheck cb timeout!!!"))
                        } else {
                            sub.onError(it)
                        }
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, 35)
    }

    /**
     * 分卡
     */
    fun requestDispatchVsimReq(timeout: Int): Single<Any> {
        val msg = MessagePacker.createDispatchVsimPacket()
        logd("debug dispatch reqPacket:$msg")
        logk("requestDispatchVsim ...")
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("DispatchVsim OVER! ... it : $it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("DispatchVsim failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug requestDispatchVsim onCompleted")
                        sub.onError(Exception("requestDispatchVsim canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong())
    }

    /**
     * 获取卡信息
     */
    fun requestVsimInfo(timeout: Int): Single<Any> {
        val msg = MessagePacker.createVsimInfoPacket()
        logd("debug getVsimInfo reqPacket:$msg")
        logk("requestVsimInfo ...")
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("requestVsimInfo failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug requestVsimInfo onCompleted")
                        sub.onError(Exception("requestVsimInfo canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout.toLong())
    }

    fun requestSoftsimUpdate(username: String, mcc: String, mnc: String, softsims: ArrayList<SoftsimLocalInfo>, usingImsi: String, timeout: Int): Single<Any> {
        var msg = MessagePacker.createUpdateSoftsimReqPacket(username, mcc, mnc, softsims, usingImsi)
        logd("debug requestSoftsimUpdate reqPacket:$msg")
        logk("requestSoftsimUpdate ...")
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requestSoftsimUpdate it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requestSoftsimUpdate error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requestSoftsimUpdate error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, timeout.toLong())
    }

    fun requestGetSoftsimInfo(imsis: ArrayList<String>, timeout: Int): Single<Any> {
        logd("requestGetSoftsimInfo: ")
        logk("requestGetSoftsimInfo")

        val message = MessagePacker.createGetSoftsimInfoPacket(imsis)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requestGetSoftsimInfo it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requestGetSoftsimInfo error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requestGetSoftsimInfo error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, timeout.toLong())
    }

    fun reqeustSoftsimBinFile(bins: ArrayList<SoftsimBinInfoSingleReq>, timeout: Int): Single<Any> {
        logd("reqeustSoftsimBinFile: ")
        logk("reqeustSoftsimBinFile")

        val message = MessagePacker.createGetSoftsimBinReqPacket(bins)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug reqeustSoftsimBinFile it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("reqeustSoftsimBinFile error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("reqeustSoftsimBinFile error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, timeout.toLong())
    }

    fun requstSoftsimUploadFlow(reqid: Int, flow: SoftsimFlowStateInfo, timeout: Int): Single<Any> {
        logd("reqeustSoftsimUploadFlow: ")
        logk("reqeustSoftsimUploadFlow")

        val message = MessagePacker.createSoftsimFlowUploadReqPacket(reqid, flow)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug reqeustSoftsimUploadFlow it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("reqeustSoftsimUploadFlow error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("reqeustSoftsimUploadFlow error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, timeout.toLong())
    }

    /**
     * 请求测速url
     */
    fun requstSpeedDetectionUrl(req:SpeedDetectionUrlReq):Single<Any>{
        logd("requstSpeedDetectionUrl")
        val message = MessagePacker.createSpeedDetectionUrl(req)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requstSpeedDetectionUrl it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requstSpeedDetectionUrl error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requstSpeedDetectionUrl error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, 35)
    }

    /**
     * 上报测速结果
     */
    fun requstUploadSpeedDetectionResult(req:Upload_Speed_Detection_Result):Single<Any>{
        logd("requstUploadSpeedDetectionResult")
        val message = MessagePacker.createUploadSpeedDetectionResult(req)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requstUploadSpeedDetectionResult it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requstUploadSpeedDetectionResult error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requstUploadSpeedDetectionResult error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, 35)
    }


    /**
     * 上报搜网结果
     */
    fun requstReportSearchNetResult(plmns:List<PlmnInfo>,reason:Int): Single<Any>{
        logd("requstReportSearchNetResult")
        val message = MessagePacker.createReportSearchNetResult(plmns,reason)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requstReportSearchNetResult it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requstReportSearchNetResult error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requstUploadCurrentSpeed error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, 35)
    }


    /**
     * 上报限速结果
     * @param req 限速结果Upload_Current_Speed
     * @return
     */
    fun requstUploadCurrentSpeed(req: Upload_Current_Speed): Single<Any> {
        logd("requstUploadCurrentSpeed: ")
        logk("requstUploadCurrentSpeed")
        val message = MessagePacker.createUploadCurrentSpeed(req)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requstUploadCurrentSpeed it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requstUploadCurrentSpeed error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requstUploadCurrentSpeed error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, 35)
    }

    /**
     * 处理完服务器下发的命令后，调用此接口回复服务器, 0表示处理失败，1表示处理成功
     * (注：服务器不回复此接口)
     * @param resutl 返回给服务器的结果 0 :成功； 1：不支持 2：失败
     * @param protoPacket 服务器下发的protobuf数据包
     */
    fun s2cCmdResp(resutl: Int, protoPacket: ProtoPacket) {
        logk("s2cCmdResp")
        val message = MessagePacker.createS2cCmdResp(resutl, protoPacket.sn)
        transceiver.send(message)
/*        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if(!this.subscriber.isUnsubscribed){
                            this.subscriber.unsubscribe()
                        }
                        //logk("debug s2cCmdResp it:$it")
                        sub.onSuccess(it)
                    }
                    onError {
                        //logke("s2cCmdResp error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        //ogke("s2cCmdResp error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, 35)*/
    }

    /**
     * 上报云卡 lac elllid
     */
    fun requstUploadLacChangeType(req: upload_lac_change_type): Single<Any> {
        logd("requstUploadLacChangeType")
        val message = MessagePacker.createUploadLacChangeType(req)
        var subRespOb: Subscription? = null
        return request(message, {
            responseOb ->
            Single.create<Any> {
                sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("debug requstUploadLacChangeType it:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logke("requstUploadLacChangeType error:${it.message}")
                        sub.onError(it)
                    }
                    onCompleted {
                        logke("requstUploadLacChangeType error:complite")
                        sub.onError(Exception("complite!!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) {
                        (subRespOb as Subscription).unsubscribe()
                    }
                }
            }
        }, 35)
    }

    /*下载软卡时候登录*/
    fun startSimpleLogin(loginType: Int, username: String, passwd: String, devideType: String, version: String, imei: Long, timeout: Long): Single<Any> {
        val msg = MessagePacker.createSimpleLoginPacket(loginType, username, passwd, devideType, version, imei)
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("simple login rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("simple login failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug simple login onCompleted")
                        sub.onError(Exception("simple login canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }
    /**
     * socket建立成功后与服务器绑定关系
     */
    fun cloudsimSocketOk(imsi: String, imei: String,timeout: Long): Single<Any> {
        val msg = MessagePacker.createCloudsimSocketOk(imsi, imei)
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("request cloudsimSocketOk rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        loge("cloudsimSocketOk failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug cloudsimSocketOk onCompleted")
                        sub.onError(Exception("cloudsimSocketOk canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }
    /**
     * 上报种子卡列表
     */
    fun uploadExtSoftsimList(softsimList: ArrayList<ExtSoftsimItem>, user: String, imei: String, ruleExist: Boolean, reason: Int, timeout: Long, tag: String? = null): Single<Any> {
        val msg = MessagePacker.createUpExtSoftsimList(softsimList, user, imei, ruleExist, reason)
        var subRespOb: Subscription? = null
        return request(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("request uploadExtSoftsimList rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("uploadExtSoftsimList failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug uploadExtSoftsimList onCompleted")
                        sub.onError(Exception("uploadExtSoftsimList canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }

    /*
    * 请求规则文件
    * */
    fun softsimRuleReq(imei: String, timeout: Long): Single<Any> {
        val msg = MessagePacker.createSoftsimRule(imei)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("request softsimRule rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("softsimRule failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug softsimRule onCompleted")
                        sub.onError(Exception("softsimRule canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }


    /**下载软卡 cmd 97**/
    fun requestDownloadSoftsim(name: String, imei: Long, reason: Int, timeout: Long): Single<Any> {
        val msg = MessagePacker.createExtSoftsimReqPacket(name, imei, reason)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("request softsim rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("request softsim failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug softsim onCompleted")
                        sub.onError(Exception("request softsim canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout, true)
    }

    /*下软卡bin文件*/
    fun getExtSoftsimBin(reqInfoList: List<SoftsimBinReqInfo>, timeout: Long): Single<Any> {
        val msg = MessagePacker.createExtSoftsimBinReqPacket(reqInfoList)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logk("request softsimbin rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logk("request softsimbin failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("debug softsimbin onCompleted")
                        sub.onError(Exception("request softsimbin canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout,true)
    }

    /**
     * 获取sim的新info
     */
    fun getSimNewInfo(imsis: Array<String>, timeout: Long): Single<Any> {
        val reqPacket = MessagePacker.createUpateSimInfoReqPacket(imsis)
        var subRespOb: Subscription? = null
        return request(reqPacket, { responseOb ->
            Single.create<Any> { sub ->

                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        if (it is Exception) {
                            sub.onError(it)
                        } else if (it is GetSoftsimInfoRsp && it.errorCode == ErrorCode.RPC_RET_OK) {
                            sub.onSuccess(it)
                        } else {
                            sub.onError(Exception("getSimNewInfo unKnowResp $it"))
                        }
                    }
                    onError {
                        logd("getSimNewInfo failed! ..." + it.message)
                        sub.onError(it)
                    }
                }

            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }

    /*上报软卡状态*/
    fun uploadExtSoftsimState(imei: String, type: Int, list: List<ExtSoftsimUpdateItem>, timeout: Long): Single<Any> {
        val msg = MessagePacker.createExtUploadSoftsimPacket(imei, type, list)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("uploadExtSoftsimState rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logd("uploadExtSoftsimState failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("uploadExtSoftsimState onCompleted")
                        sub.onError(Exception("uploadExtSoftsimState canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }

    /*下载软卡规则文件*/
    fun downloadRuleList(imei: String, timeout: Long): Single<Any> {
        val msg = MessagePacker.createSoftSimRuleListPacket(imei)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("req softsim rulelist rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        loge("req softsim rulelist failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("req softsim rulelist onCompleted")
                        sub.onError(Exception("req softsim rulelist canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout,true)
    }

    fun uploadExtSoftsimUploadError(reason: Int, user: String, IMEI: String, list: List<softsimError>, timeout: Long): Single<Any> {
        val msg = MessagePacker.createExtSoftsimUploadErrorReq(reason, user, IMEI, list)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("uploadExtSoftsimUploadError rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else if (it is ExtSoftsimUploadErrorRsp) {
                            if (ErrorCode.RPC_RET_OK == it.errorCode) {
                                sub.onSuccess(it)
                            }else{
                                sub.onError(Exception("uploadExtSoftsimUploadError unKnow Error Code $it"))
                            }
                        } else {
                            sub.onError(Exception("uploadExtSoftsimUploadError unKnowResp $it"))
                        }
                    }
                    onError {
                        logd("uploadReclaimExtSoftsim failed! ..." + it.message)
                        sub.onError(it)
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }

    fun uploadReclaimExtSoftsim(softsimList: List<ReclaimImsi>, localSimList: List<ExtSoftsimItem>, user: String, imei: String, timeout: Long): Single<Any> {
        val msg = MessagePacker.createReclaimExtSoftsimReq(softsimList, localSimList, user, imei)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("uploadReclaimExtSoftsim rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else if (it is ReclaimExtSoftsimRsp) {
                            sub.onSuccess(it)
                        } else {
                            sub.onError(Exception("uploadReclaimExtSoftsim unKnowResp $it"))
                        }
                    }
                    onError {
                        logd("uploadReclaimExtSoftsim failed! ..." + it.message)
                        sub.onError(it)
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout, true)
    }

    fun uploadGspCfg(hardGps: Boolean, networkGps: Boolean, timeout: Long): Single<Any> {
        val msg = MessagePacker.createGpsUploadCfgPacket(hardGps, networkGps)
        var subRespOb: Subscription? = null
        return request(msg, { responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("uploadGspCfg rsp:" + it)
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logd("uploadGspCfg failed! ..." + it.message)
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("uploadGspCfg onCompleted")
                        sub.onError(Exception("uploadGspCfg canceled!"))
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout)
    }


    fun requestServiceList(mcc:String):Single<Any>{
        val msg = MessagePacker.createServiceListReqPacket(mcc)
        var subRespOb:Subscription? = null
        return request(msg, {responseOb -> Single.create<Any> {sub->
            subRespOb = responseOb.subscribeWith {
                onNext {
                    if (!this.subscriber.isUnsubscribed) {
                        this.subscriber.unsubscribe()
                    }
                    logd("requestServiceList rsp:" + it)
                    if (it is Exception) {
                        sub.onError(it)
                    } else {
                        sub.onSuccess(it)
                    }
                }
                onError {
                    logd("requestServiceList failed! ..." + it.message)
                    sub.onError(it)
                }
                onCompleted {
                    logd("requestServiceList onCompleted")
                    sub.onError(Exception("requestServiceList canceled!"))
                }
            }
        }.doOnUnsubscribe {
            if (subRespOb != null) {
                if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
            }
        }},35)
    }

    /**
     * 上报性能日志数据
     */
    fun requestPerformanceLogReportResult(performance_log_report: Performance_log_report,timeout:Int): Single<Any> {
        logd("PerfLog requestPerformanceLogReportResult req= $performance_log_report")
        var subRespOb: Subscription? = null
        return request(MessagePacker.createPerformanceLogReportMessage(performance_log_report), {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("PerfLog requestPerformanceLogReportResult onNext:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logd("PerfLog requestPerformanceLogReportResult onError:$it")
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("PerfLog requestPerformanceLogReportResult onCompleted")
                        sub.onError(Exception("requestPerformanceLogReportResult onCompleted!"))
                    }
                }
            }.doOnUnsubscribe {
                        if (subRespOb != null) {
                            if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                        }
                    }
        }, timeout.toLong())
    }

    /**请求账号显示信息**/
    fun getUserAccountDisplay(langtype: String, timeout: Int): Single<Any> {
        val reqPacket = MessagePacker.createUserAccountDisplayReqPacketMessage(langtype)
        var subRespOb: Subscription? = null
        return request(reqPacket, { responseOb ->
            Single.create<Any> { sub ->

                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        if (it is Exception) {
                            sub.onError(it)
                        } else if (it is user_account_display_resp_type && it.errorCode == ErrorCode.RPC_RET_OK) {
                            sub.onSuccess(it)
                        } else {
                            sub.onError(Exception("getUserAccountDisplay unKnowResp $it"))
                        }
                    }
                    onError {
                        logd("getUserAccountDisplay failed! ..." + it.message)
                        sub.onError(it)
                    }
                }

            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }

        }, timeout.toLong())
    }

    /**
     * 上报云卡socket建立
     */
    fun requestCloudsimSocketOK(cloudsim_socket_ok_req: Cloudsim_socket_ok_req,timeout:Int): Single<Any> {
        var subRespOb: Subscription? = null
        return request(MessagePacker.createCloudsimSocketOKMessage(cloudsim_socket_ok_req), {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("FrequentAuth requestCloudsimSocketOK onNext:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logd("FrequentAuth requestCloudsimSocketOK onError:$it")
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("FrequentAuth requestCloudsimSocketOK onCompleted")
                        sub.onError(Exception("requestCloudsimSocketOK onCompleted!"))
                    }
                }
            }.doOnUnsubscribe {
                        if (subRespOb != null) {
                            if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                        }
                    }
        },timeout.toLong())
    }

    /**
     * 上报频繁鉴权检测结果
     */
    fun requestFrequentAuthResult(frequent_auth_result: Frequent_auth_detection_result_req,timeout:Int): Single<Any> {
        logd("FrequentAuth requestFrequentAuthResult req= $frequent_auth_result")
        var subRespOb: Subscription? = null
        return request(MessagePacker.createFrequentAuthResultMessage(frequent_auth_result), {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        logd("FrequentAuth requestFrequentAuthResult onNext:$it")
                        if (it is Exception) {
                            sub.onError(it)
                        } else {
                            sub.onSuccess(it)
                        }
                    }
                    onError {
                        logd("FrequentAuth requestFrequentAuthResult onError:$it")
                        sub.onError(it)
                    }
                    onCompleted {
                        logd("FrequentAuth requestFrequentAuthResult onCompleted")
                        sub.onError(Exception("requestFrequentAuthResult onCompleted!"))
                    }
                }
            }.doOnUnsubscribe {
                        if (subRespOb != null) {
                            if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                        }
                    }
        }, timeout.toLong())
    }


}

enum class APDUStatus {
    NEEDAUTH,
    AUTHSUCC,
    AUTHFAIL
}

/**
 * 中止异常
 */
class AbortException : Exception()

data class LoginRequestInfo(val userName: String?, val password: String?, val loginType: Int, val networkInfo: NetInfo, val loginReson: Int) {
    override fun toString(): String {
        return "LoginRequestInfo{userName='$userName', loginType='$loginType', networkInfo=$networkInfo, loginReson=$loginReson}"
    }
}
