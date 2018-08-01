package com.ucloudlink.refact.channel.transceiver

import android.net.ConnectivityManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.ucloudlink.framework.protocol.protobuf.AppCmd
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.flow.netlimit.common.ULoggingHandler
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.business.s2ccmd.ServerCmdHandle
import com.ucloudlink.refact.channel.transceiver.protobuf.Message
import com.ucloudlink.refact.channel.transceiver.protobuf.Packet
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket
import com.ucloudlink.refact.channel.transceiver.protobuf.SecurePacketResp
import com.ucloudlink.refact.channel.transceiver.secure.ConnectFailCounter
import com.ucloudlink.refact.channel.transceiver.secure.SecureUtil
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.PeriodPingDns
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.timeout.IdleStateHandler
import rx.Observable
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.PublishSubject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by jiaming.liang on 2016/11/4.
 */
class NettyTransceiver : Transceiver {

    private var callbackThread: HandlerThread? = null

    private val connectFailCounter: ConnectFailCounter by lazy { ConnectFailCounter() }

    private var callbackLooper: Looper? = null
        get() {
            var thread = callbackThread
            if (thread == null) {
                thread = HandlerThread("NettyCallBack")
                thread.start()
                callbackThread = thread
            }
            return thread.looper
        }

    private var isShortTimeout = false
    private val connected = "SocketConnected"
    private val disconnected = "SocketDisconnected"
    val secureError = "secureError" //安全连接错误

    val channelStateObservable = BehaviorSubject<String>()//Channel状态变更发射器  在安全模块上报socket状态
    var channelExceptionStateObservable = BehaviorSubject<Throwable>()
    private var receivedObservable = PublishSubject<Message>()//收到服务器消息发射器

    private val socketConnectReason: HashSet<String> = HashSet() //打开关闭socket的原因，打开添加，关闭清除，当HashSet为空时socket断开

    private var isNeedSocket = false
        get() {
            val ret = socketConnectReason.isNotEmpty()
            logd("checkIsNeedSocket ret: $ret ")
            return ret
        }

    private val lock = Any()
    private val lock1 = Any()
    private val delaytime = 2000L //尝试重连等待时间,单位haomiao
    private var isDoingConnect: Boolean = false
//    private var channel: Channel? = null

    private val boots = HashMap<ServerRouter.Dest, Bootstrap?>()
    private val channels = HashMap<ServerRouter.Dest, Channel?>()
    private val keepers = HashMap<ServerRouter.Dest, StateKeeper>()

    val timer: Timer by lazy { Timer() }

    var reconnectTask: reConnectTask? = null
    var hasConnectTask = false

    private var mTimeout: Int = 0  //netty连接超时时间 单位毫秒
        get() {
            val timeout = if (isShortTimeout) 10 * 1000 else 35 * 1000
            isShortTimeout = false
            return timeout
        }

    override fun isSocketConnected(dest: ServerRouter.Dest): Boolean {
        val channel = channels[dest]
        return channel != null && channel.isActive
    }

    override fun send(packet: Message) {
        //logd("NettyTransceiver send message: $packet}")
        val dest = packet.dest
        val channel = channels[dest]
        if (channel == null || !channel.isActive) {
            //"channel不可用时重连。本不应该调到这里的，但为了连接可用性，触发连接一下"
            logi("NettyTransceiver invalid channel,just try to connectSocket}")
            connectSocket(dest)
        } else {
            if (SecureUtil.useSecureChanel && packet.payload is ProtoPacket) {
                if (!((SecureUtil.currSecuConnetcStatus > 0) && (SecureUtil.reconFlag == SecureUtil.RECONNECT_RESET))
                        && !SecureUtil.reconnectable) {
                    loge("send failed: packet : $packet")
                    throw Exception(ErrorCode.LOCAL_SEND_FAILED_SINCE_NOT_READY.toString())
                }
            }
            channel.writeAndFlush(packet.payload)
            keepers[dest]?.write()
        }
    }

    override fun receive(dest: ServerRouter.Dest): Observable<Message> {
        if (receivedObservable.hasCompleted() || receivedObservable.hasThrowable()) receivedObservable = PublishSubject<Message>()
        return receivedObservable
    }

    fun disconnectInternal(dest: ServerRouter.Dest) {
        logd("disconnectInTternal dest:$dest ")
        val channel = channels[dest]
        channel?.disconnect()
    }

    override fun disconnect(dest: ServerRouter.Dest) {
        logi("disconnect dest:$dest ")
        disconnectInternal(dest)
        if (SecureUtil.useSecureChanel) {
            if (SecureUtil.oneSecureStepTimeoutTask != null) {
                SecureUtil.oneSecureStepTimeoutTask!!.cancel()
            }
            SecureUtil.trySecureReconnectTimeoutCounter = 0
        }
    }

    //打开关闭的reason要一致
    override fun setNeedSocketConnect(dest: ServerRouter.Dest, reason: String) {
        socketConnectReason.add(reason)
        logi("setNeedSocketConnect add reason = ${reason} socketConnectReason=${socketConnectReason}")
        val channel = channels[dest]
        logd("setNeedSocketConnect $channel ${(channel != null && channel.isActive)}")
        isShortTimeout = true
        if (channel == null || !channel.isActive) {
            connectSocket(dest)
        }
    }

    //打开关闭的reason要一致
    override fun setForbidSocketConnect(dest: ServerRouter.Dest, reason: String) {
        socketConnectReason.remove(reason)
        logi("setForbidSocketConnect remove reason = ${reason} socketConnectReason=${socketConnectReason}")
        if (reason == "SeedNeed") {
            connectFailCounter.clear()
        }
        if (socketConnectReason.isEmpty()) {//都不需要网络时
            logi("setForbidSocketConnect reason is null disconnect!")
            disconnect(dest)
        }
    }

    override fun statusObservable(dest: ServerRouter.Dest): Observable<String> {
        return channelStateObservable
    }

    override fun exceptionStatusObservable(dest: ServerRouter.Dest): Observable<Throwable> {
        return channelExceptionStateObservable
    }

    private fun doCommand(cmd: ArrayList<String>): String {
        var proc: Process? = null
        var mReader: BufferedReader? = null
        var stringBuf: StringBuffer = StringBuffer()
        try {
            proc = Runtime.getRuntime().exec(cmd.toTypedArray())
            mReader = BufferedReader(InputStreamReader(
                    proc.getInputStream()), 1024)
            var line: String? = null
            line = mReader.readLine()
            while (line != null) {
                if (!line.isEmpty()) {
                    stringBuf.append(line + "\n")
                }
                line = mReader.readLine()
            }
        } catch (e: Exception) {
            JLog.loge("cmd:" + cmd + " exec exception!")
            e.printStackTrace()
            stringBuf.append(e.toString())
        } finally {
            if (mReader != null) {
                mReader.close()
            }
        }

        return stringBuf.toString()
    }

    private fun dumpIpRouteRule() {
        val commandList = ArrayList<String>()
        var result: String

        commandList.add("ip")
        commandList.add("route")
        result = doCommand(commandList)
        logd("ip route: $result")

        commandList.clear()
        commandList.add("ip")
        commandList.add("rule")
        result = doCommand(commandList)
        logd("ip rule: $result")
    }

    private var threadMissDetect = 0
    private fun connectSocket(dest: ServerRouter.Dest) {
        val channel = channels[dest]
        logi("netty connectSocket isDoingConnect:$isDoingConnect, ${(channel != null && channel.isRegistered)}, " +
                "$isNeedSocket ${isNeedSocket.hashCode()},${(channel != null && channel.isActive)}")
        synchronized(lock) {
            try {
                //正在重连  和 不需要连接 都直接返回
                if ((channel != null && channel.isRegistered && !channel.isActive) || Configuration.isDoingPsCall || ServerRouter.ipAddrMap[dest] == null) {
                    logi("connectSocket return and tryReconnectIfNeed,${(channel != null && channel.isRegistered && !channel.isActive)}," +
                            "${Configuration.isDoingPsCall},${(ServerRouter.ipAddrMap[dest] == null)}")
                    tryReconnectIfNeed(dest)
                    return
                }
                if (isDoingConnect || (channel != null && channel.isActive) || !isNeedSocket) {
                    logi("connectSocket return directly")
                    return
                }
                channel?.disconnect()
                var bootstrap = boots[dest]
                if (bootstrap == null) {
                    bootstrap = initBoot(dest)
                    boots.put(dest, bootstrap)
                }

                val connectTimeout = mTimeout
                logv("set connect timeout :$connectTimeout")

                bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                isDoingConnect = true


                val future = bootstrap.connect(ServerRouter.ipAddrMap[dest])
                channels.get(dest)?.close()
                channels.put(dest, future.channel())
                future.addListener(object : ChannelFutureListener, Handler(callbackLooper) {
                    private val FORCE_STOP_CONNECT: Int = 1

                    init {
                        logv("init ChannelFutureListener start protect")
                        sendMessageDelayed(obtainMessage(FORCE_STOP_CONNECT), (mTimeout + 500).toLong())

                    }

                    override fun operationComplete(futureBack: ChannelFuture?) {
                        logi("operationComplete callback future:$futureBack")
                        futureBack ?: return

                        if (futureBack.isDone) {
                            removeMessages(FORCE_STOP_CONNECT)
                            isDoingConnect = false
                            logd("operationComplete callback is done futrue:$futureBack")
                            if (futureBack.isSuccess) {
                                logk("connect success!!!")
//                            channelObservable.onNext(true)//不在这里通知,在packetHander通知
                            } else {
                                tryReconnectIfNeed(dest)
                                logke("connect socket fail try again after $delaytime ms fail by cause:${futureBack.cause().message}")
                                val mConnectivityManager = ConnectivityManager.from(ServiceManager.appContext)
                                loge("is process bind network:  ${mConnectivityManager.boundNetworkForProcess}")
                                PeriodPingDns.dopingOnce()//失败时ping一直广东工业大学服务器
                                futureBack.cause().printStackTrace()
                                // 测试路由情况
                                NetworkManager.tracerouteToService()
                                if (futureBack.cause() is SocketTimeoutException) {
                                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SOCKET_TIMEOUT)
                                }

                                if (futureBack.cause() is ConnectException) {
                                    if (futureBack.cause().message != null) {
                                        if (futureBack.cause().message!!.indexOf("ENETUNREACH") != -1) {
                                            dumpIpRouteRule()
                                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SOCKET_TIMEOUT)
                                        }
                                    }
                                }
                                connectFailCounter.connectFail()
                                channelExceptionStateObservable.onNext(futureBack.cause())
                                future.removeListener(this)
                            }
                        }
                    }


                    override fun handleMessage(msg: android.os.Message) {
                        when (msg.what) {
                            FORCE_STOP_CONNECT -> {
                                loge("not callback force stop connect")

                                threadMissDetect++
                                if (threadMissDetect == 2) {
                                    loge("netty threadMissDetect again,just restart the process")
                                    System.exit(1)
                                }
                                isDoingConnect = false
                                channels[dest]?.close()
                                channels.remove(dest)
                                tryReconnectIfNeed(dest)
                                future.removeListener(this)
                                boots.remove(dest)
                                NetworkManager.tracerouteToService()
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                loge("recv netty exception!")
                e.printStackTrace()
            }
        }
    }

    private fun tryReconnectIfNeed(dest: ServerRouter.Dest) {

        synchronized(lock1) {
            if (hasConnectTask || !isNeedSocket) {//避免重复创建重连任务
                loge("tryReconnectIfNeed hasConnectTask=${hasConnectTask} and ...")
                return
            }

            val channel = channels[dest]
            if (channel != null && channel.isActive) {
                loge("tryReconnectIfNeed channel != null && channel.isActive =true")
                return
            }

            hasConnectTask = true
            if (reconnectTask != null) {
                reconnectTask!!.cancel()
            }

            reconnectTask = reConnectTask(dest)
            timer.schedule(reconnectTask, delaytime)
        }
    }

    inner class reConnectTask(private var dest: ServerRouter.Dest) : TimerTask() {
        override fun run() {
            hasConnectTask = false
            connectSocket(dest)
        }
    }

    fun initBoot(dest: ServerRouter.Dest): Bootstrap {
        val group = NioEventLoopGroup()
        val bootstrap = Bootstrap().group(group).channel(NioSocketChannel::class.java)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(object : ChannelInitializer<Channel>() {

                    override fun initChannel(ch: Channel) {
                        val pipline = ch.pipeline()
                        pipline.addLast(LoggingHandler(LogLevel.INFO))
                                .addLast(ULoggingHandler())
                                .addLast(IdleStateHandler(0, 0, 60, TimeUnit.SECONDS))
                                .addLast(SecureProtoNettyCodec())
                                .addLast(PacketHandler(dest))
                    }
                })
        val stateKeeper = StateKeeper()
        stateKeeper.doWhenLost { disconnect(dest) }
        keepers.put(dest, stateKeeper)
        return bootstrap
    }


    inner class PacketHandler(val dest: ServerRouter.Dest) : SimpleChannelInboundHandler<Packet>() {
        override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {

            /*  if (packet.type.equals(AvroPacketUtil.DATATYPE_STOC)) {
                  logd("DefaultTransceiver2 channelRead0 goto parseS2CCmdMethod")
                  CmdParse.parseS2CCmdMethod(packet)
                  return
              }*/
            if (packet is ProtoPacket) {
                logd("NettyTransceiver channel new read: ${packet.cmd},${packet.sn}")
                val message = Message(id = packet.sn, payload = packet)
                //服务器的指令
                if (packet.cmd == AppCmd.CMD_S2C_REQ.value ||
                        packet.cmd == AppCmd.CMD_RTU_SEND_SMS_RESULT_REPORT.value ||
                        packet.cmd == AppCmd.CMD_RTU_PHOEN_CALL_RESULT_REPORT.value ||
                        packet.cmd == AppCmd.CMD_RTU_SEND_SMS_UNSOLI.value ||
                        packet.cmd == AppCmd.CMD_RTU_REMOTE_AT_RESULT_REPORT.value ||
                        packet.cmd == AppCmd.CMD_RTU_TASK_REQ.value ||
                        packet.cmd == AppCmd.CMD_RTU_TASK_REQ_ACK.value ||
                        packet.cmd == AppCmd.CMD_RTU_TASK_RESULT_UPLOAD.value) {
                    ServerCmdHandle.handlerServerCmd(packet)
                } else {
                    keepers[dest]?.receive()
                    receivedObservable.onNext(message)
                }

            } else if (packet is SecurePacketResp) {
                keepers[dest]?.receive()
                SecureUtil.handlerSecurePacket(packet)
            }
        }


        override fun channelActive(ctx: ChannelHandlerContext?) {
            val channel = ctx!!.channel()
            channels.put(dest, channel)

            connectFailCounter.clear()

            threadMissDetect = 0
            //等待安全验证通过后才上报网络状态可用
            if (!SecureUtil.useSecureChanel) {
                //使用非安全通道
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SOCKET_CONNECTED)
                channelStateObservable.onNext(connected)
            } else {
                if (SecureUtil.reconnectable) {
                    //激活成功，通知业务层netty可用
                    SecureUtil.currSecuConnetcStatus = 1

                    logd("NettyTransceiver reconnect SocketConnected")
                    channelStateObservable.onNext("SocketConnected")
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SOCKET_CONNECTED)
                } else {
                    SecureUtil.mDest = dest
                    if (SecureUtil.oneSecureStepTimeoutTask != null) {
                        SecureUtil.oneSecureStepTimeoutTask!!.cancel()
                    }
                    logd("oneSecureStepTimeoutTask start :${SecureUtil.secureTimeoutTime}")
                    SecureUtil.oneSecureStepTimeoutTask = SecureUtil.OneSecureStepTimeoutTask()
                    SecureUtil.timer.schedule(SecureUtil.oneSecureStepTimeoutTask, SecureUtil.secureTimeoutTime)
                }
            }

        }

        override fun channelInactive(ctx: ChannelHandlerContext?) {
            channelStateObservable.onNext(disconnected)
            SecureUtil.setReconnectFlagsAtDisconnect()

            receivedObservable.onError(Exception("Socket disconnected after send!"))
            tryReconnectIfNeed(dest)
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SOCKET_DISCONNECT)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
            cause?.printStackTrace()
        }

        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {

        }

        override fun channelUnregistered(ctx: ChannelHandlerContext?) {
            super.channelUnregistered(ctx)
            keepers[dest]?.receive()
        }
    }

}