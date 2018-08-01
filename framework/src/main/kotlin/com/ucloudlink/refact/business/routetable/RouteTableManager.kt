package com.ucloudlink.refact.business.routetable

import android.content.Context
import android.net.ConnectivityManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.flow.netlimit.common.DnsUtils
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo
import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetLimitHolder
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils
import com.ucloudlink.refact.business.routetable.ServerRouter.BUSINESS
import com.ucloudlink.refact.business.routetable.ServerRouter.SAAS2
import com.ucloudlink.refact.business.routetable.ServerRouter.current_mode
import com.ucloudlink.refact.business.routetable.ServerRouter.setAssIp
import com.ucloudlink.refact.channel.transceiver.protobuf.MessagePacker
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacketUtil
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import rx.Single
import rx.Subscription
import rx.subjects.PublishSubject
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by haiping.liu on 2017/6/29.
 */
object RouteTableManager {
    //路由表文件名
    private const val BUSINESS_ASS_LIST = "bussinessAssList"
    private const val BUSINESS_ROUTE_LIST = "bussinessRouteList"
    private const val SAAS2_ASS_LIST = "saas2AssList"
    private const val SAAS2_ROUTE_LIST = "saas2RouteList"
    private const val SAAS3_ASS_LIST = "saas3AssList"
    private const val SAAS3_ROUTE_LIST = "saas3RouteList"
    private const val FACTORY_ASS_LIST = "factoryAssList"
    private const val FACTORY_ROUTE_LIST = "factoryRouteList"

    private val requestGetRouteTable: RequestGetRouteTable = RequestGetRouteTable.getRequestGetRouteTable()
    private val AssIpObservable = PublishSubject.create<String>()
    private val RcIpObservable = PublishSubject.create<String>()

    private var subAssIpObservable: Subscription? = null
    private var subRcIpObservable: Subscription? = null
    private var subSocketStatusObservable: Subscription? = null
    private var subRequestGetRouteTable: Subscription? = null
    private var subGetRouteTableFromRC: Subscription? = null

    const val RT_SOCKET_TIME_OUT = 35
    const val GET_RT_TOTAL_TIME_OUT = RT_SOCKET_TIME_OUT * 3 //总超时时间（监听有效ip+建立socket+服务器响应）

    // Socket连接超时重试次数
    private val SOCKET_CONNECT_RETRY = 15
    // Socket连接超时重试等待时间
    private val SOCKET_CONNETC_WAIT = 1000

    //向路由中心(RC)请求路由表（请求结束需要关闭socket）
    fun getRouteTableFromRCIfNeed(timeout: Int, userName: String?, module: String): Single<Any> {
        logd("tRoute getRouteTableFromRCIfNeed() module:$module")
        return Single.create<Any> { sub ->
            if (module == "SoftsimDownloadState") {
                val percent = ServiceManager.accessEntry.systemPersent
                if (percent in 1..99) {
                    if (!isWifiConnected(ServiceManager.appContext) && percent != 90) {
                        //云卡启动中，不下载软卡
                        sub.onError(Throwable("AccessState Running"))
                        return@create
                    }
                    //如果连接了wifi 进度在90%可以下载软卡
                }
                if (!isNetworkConnected(ServiceManager.appContext)) {
                    //网络不可用 不下载软卡
                    sub.onError(Throwable("Network unuse waite"))
                    return@create
                }
            }

            //路由时断开socket，清空ip，避免socket连接超时触发切网络
            ServiceManager.transceiver.disconnect(ServerRouter.Dest.ASS)
            ServerRouter.removeAssIP()

            val assIpList = getLocalAssIPList(current_mode)
            if (assIpList != null) {
                val assIpCount = assIpList.size
                logd("tRoute have RouteTable size = $assIpCount")
                var msgCount = 0
                for (ip in assIpList) {
                    ServiceManager.appThreadPool.execute {
                        connectAssIpSocketTask(ip, timeout)
                    }
                }
                subAssIpObservable = AssIpObservable.asObservable()
                        .timeout(RT_SOCKET_TIME_OUT.toLong(), TimeUnit.SECONDS)
                        .subscribe({ msg ->
                            logd("tRoute --------Listening------ass ip message= $msg")

                            if (msg != "Exception"){
                                subAssIpObservable?.unsubscribe()
                                setAssIp(msg)
                                sub.onSuccess(true)
                                return@subscribe
                            }else{
                                msgCount++
                                logd("tRoute --------Listening------ass msgCount=$msgCount, assIpCount=$assIpCount")

                                if (msgCount == assIpCount){
                                    //所有ip出错，立即连接rc ip
                                    loge("tRoute --------Listening------valid ass ip timeout")
                                    subAssIpObservable?.unsubscribe()

                                    val rcIPList = getLocalRcIPList(current_mode)
                                    if (rcIPList != null) {
                                        val rcIpCount = rcIPList.size
                                        var rcMsgCount  = 0
                                        for (ip in rcIPList) {
                                            ServiceManager.appThreadPool.execute {
                                                connectRCIpSocketTask(ip, timeout)
                                            }
                                        }
                                        subRcIpObservable = RcIpObservable.asObservable()
                                                .timeout(RT_SOCKET_TIME_OUT.toLong(), TimeUnit.SECONDS)
                                                .subscribe({ msg ->
                                                    logd("tRoute --------Listening------valid Rc Msg= $msg")
                                                    if (!msg.equals("Exception")){
                                                        subRcIpObservable?.unsubscribe()
                                                        val persent = ServiceManager.accessEntry.systemPersent
                                                        if (module == "SoftsimDownloadState") {

                                                            if (persent in 1..99) {
                                                                if (!isWifiConnected(ServiceManager.appContext) && persent != 90) {
                                                                    //云卡启动中，不下载软卡
                                                                    sub.onError(Throwable("AccessState Running"))
                                                                    return@subscribe
                                                                }
                                                                //如果连接了wifi 进度在90%可以下载软卡

                                                            }
                                                        } else {
                                                            if (persent != 20) {
                                                                loge("tRoute AccessState:set rc ip,but not in 20%")
                                                                sub.onError(Throwable("AccessState:set rc ip,but not in 20%"))
                                                                return@subscribe
                                                            }
                                                        }

                                                        setAssIp(msg as String)

                                                        subGetRouteTableFromRC = getRouteTableFromRC(timeout, userName)
                                                                .subscribe({ o ->
                                                                    unsubscribeAllSub()

                                                                    sub.onSuccess(o)
                                                                    return@subscribe
                                                                }) { throwable ->
                                                                    unsubscribeAllSub()
                                                                    ServerRouter.initIpByCurrentMode()
                                                                    sub.onError(throwable)
                                                                    return@subscribe
                                                                }
                                                    }else{
                                                        rcMsgCount++
                                                        logd("tRoute --------Listening------valid Rc rcMsgCount= " + rcMsgCount+", rcIpCount="+rcIpCount)
                                                        if (rcMsgCount == rcIpCount){
                                                            //所有rc ip失败
                                                            subRcIpObservable?.unsubscribe()
                                                            loge("tRoute --------Listening------valid Rc ip timeout")
                                                            sub.onError(Throwable("Fail:Ass and RC IP Socket Timeout"))
                                                            return@subscribe
                                                        }
                                                    }

                                                }, {
                                                    loge("tRoute --------Listening------valid Rc ip timeout")
                                                })
                                    } else {
                                        loge("tRoute Have routeTable but no RC ip")
                                        sub.onError(Throwable("Fail:Have routeTable but no RC ip"))
                                        return@subscribe
                                    }
                                }

                            }
                        }, {
                            logd("tRoute --------Listening------timeout")
                        })
            } else {
                logd("tRoute no RouteTable")

                if (current_mode == BUSINESS) {
                    if (connectSocketTest(ServerRouter.current_RouteIp, timeout)) {
                        setAssIp(ServerRouter.current_RouteIp)
                    } else {
                        loge("tRoute main rc ip timeout use other rc ip")
                        setAssIp(ServerRouter.ROUTE_IP_BUSSINESS_BACKUP)
                    }
                } else {
                    setAssIp(ServerRouter.current_RouteIp)
                }

                subGetRouteTableFromRC = getRouteTableFromRC(timeout, userName)
                        .subscribe({ o ->
                            unsubscribeAllSub()

                            sub.onSuccess(o)
                            return@subscribe
                        }) { throwable ->
                            unsubscribeAllSub()
                            ServerRouter.initIpByCurrentMode()
                            sub.onError(throwable)
                            return@subscribe
                        }
            }
        }.doOnUnsubscribe {
            unsubscribeAllSub()
        }
    }

    fun unsubscribeAllSub() {
        subAssIpObservable?.unsubscribe()
        subRcIpObservable?.unsubscribe()
        subGetRouteTableFromRC?.unsubscribe()
        subSocketStatusObservable?.unsubscribe()
        subRequestGetRouteTable?.unsubscribe()
    }


    private fun getRouteTableFromRC(timeout: Int, userName: String?): Single<Any> {
        logd("tRoute getRouteTableFromRC()")
        return Single.create<Any> { sub ->
            requestGetRouteTable.connectSocket()
            subSocketStatusObservable = ServiceManager.transceiver.statusObservable(ServerRouter.Dest.ASS)
                    .asObservable()
                    .timeout(timeout.toLong(), TimeUnit.SECONDS)
                    .subscribe(
                            { s ->
                                subSocketStatusObservable?.unsubscribe()
                                if (s == "SocketConnected") {
                                    val req = Get_Route_Req(Route_Type.TML, Configuration.getImei(ServiceManager.appContext), userName, getMcc(ServiceManager.appContext), "DSDS")
                                    val data = req.encode()
                                    val packet = ProtoPacket(startTag = ProtoPacketUtil.START_TAG, cmd = Route_Cmd.GET_ROUTE_REQ.value, length = data.size.toShort(), sn = MessagePacker.getSn(), data = data)
                                    logd("tRoute getRouteTableFromRC req=$req")
                                    subRequestGetRouteTable = requestGetRouteTable.requestGetRouteTable(packet, timeout).subscribe({
                                        logd("tRoute getRouteTableFromRC response:$it")
                                        subRequestGetRouteTable?.unsubscribe()
                                        if (it is Get_Route_Resp) {
                                            if (it.result == Response_result.RESULT_SUCCE) {

                                                if (ifRouteTableNeetUpdate(it, current_mode)) {
                                                    saveRouteTable(it, current_mode)
                                                    sub.onSuccess(it)
                                                    return@subscribe
                                                } else {
                                                    sub.onError(Throwable("Fail:No Update"))
                                                }
                                            } else {
                                                sub.onError(Throwable("Fail:Result Fail"))
                                            }
                                        } else {
                                            sub.onError(Throwable("Fail:Not RouteTable"))
                                        }
                                    }, {
                                        subRequestGetRouteTable?.unsubscribe()
                                        sub.onError(it)
                                    })
                                } else if (s == "SocketDisconnected") {
                                    logd("tRoute SocketDisconnected")
                                } else if (s == "secureError") {
                                    sub.onError(Throwable("Fail:Socket SecureError"))
                                } else if (s == "secureTimeout") {
                                    sub.onError(Throwable("Fail:Socket SecureTimeout"))
                                }
                            },
                            { throwable ->
                                subSocketStatusObservable?.unsubscribe()
                                logd("Socket statusObservable error:" + throwable + " " + throwable.message)
                                if (throwable is TimeoutException) {
                                    sub.onError(Throwable("Fail:Netty Socket StatusObservable Timeout"))
                                } else {
                                    sub.onError(throwable)
                                }
                            }
                    )
        }.doOnUnsubscribe {
            subRequestGetRouteTable?.unsubscribe()
            subSocketStatusObservable?.unsubscribe()
        }
    }

    private fun connectAssIpSocketTask(ip: String, timeout: Int) {
        if (connectSocketTest(ip, timeout)) {
            AssIpObservable.onNext(ip)
        }else{
            AssIpObservable.onNext("Exception")
        }
    }

    private fun connectRCIpSocketTask(ip: String, timeout: Int) {
        if (connectSocketTest(ip, timeout)) {
            RcIpObservable.onNext(ip)
        }else{
            RcIpObservable.onNext("Exception")
        }
    }

    private fun connectSocketTest(ipAndPort: String?, timeout: Int): Boolean {
        // Socket连接超时次数
        var socketConnectRetry = 0
        while (socketConnectRetry < SOCKET_CONNECT_RETRY) {
            //SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(true, DnsUtils.getDnsOrIp(ipAndPort), SysUtils.getUServiceUid()))
            try {
                if (ipAndPort == null) {
                    //SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(false, DnsUtils.getDnsOrIp(ipAndPort), SysUtils.getUServiceUid()))
                    return false
                }
                val ipPort: List<String> = ipAndPort.split(":")
                if (ipPort.size != 2) {
                    //SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(false, DnsUtils.getDnsOrIp(ipAndPort), SysUtils.getUServiceUid()))
                    return false
                }
                val ipaddr: String = ipPort[0]
                val port: Int = ipPort[1].toInt()
                val socket = Socket()
                socket.connect(InetSocketAddress(ipaddr, port), timeout * 1000)
                return socket.isConnected
                //SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(false, DnsUtils.getDnsOrIp(ipAndPort), SysUtils.getUServiceUid()))
            } catch (e: ConnectException) {
                // 连接socket报ConnectException单独处理
                // 当报错网络不可用时，在N次以内进行延迟尝试
                // 当报其他类型错误时直接停止尝试当前路由

                if (e.message != null && e.message!!.contains("Network is unreachable")) {
                    loge("tRoute connectSocketTest ConnectException: $e sleep 1s and restart.")
                    socketConnectRetry++
                    Thread.sleep(1000)
                } else {
                    loge("tRoute connectSocketTest ConnectException: $e")
                    return false
                }
            } catch (e: Exception) {
                loge("tRoute connectSocketTest Exception: $e")
                //SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(false, DnsUtils.getDnsOrIp(ipAndPort), SysUtils.getUServiceUid()))
                return false
            }
        }
        return false
    }

    /**
     *  路由表是否需要更新(路由表不存在也需要更新)
     * routeTable     主动获取返回的路由表
     * ipMode         ip模式
     */
    fun ifRouteTableNeetUpdate(routeTable: Get_Route_Resp, ipMode: Int): Boolean {
        var isNeed: Boolean = true
        val localAssIpList = getLocalAssIPList(ipMode)
        val localRcIpList = getLocalRcIPList(ipMode)

        var assIPList: List<String>? = null
        var rcIPList: List<String>? = null

        val routeAddress = routeTable.routeAddress
        for (routeIP: RouteAddress in routeAddress) {
            if (routeIP.type == 1) {
                assIPList = routeIP.address
            } else if (routeIP.type == 3) {
                rcIPList = routeIP.address
            }
        }
        if (localAssIpList != null && localRcIpList != null && localAssIpList == assIPList && localRcIpList == rcIPList) {
            isNeed = false
        }

        return isNeed
    }

    /**
     *  路由表是否需要更新(路由表不存在也需要更新)
     * s2c_redirect_route     推送的路由表
     * ipMode         ip模式
     */
    fun ifRouteTableNeetUpdate(s2c_redirect_route: S2c_redirect_route, ipMode: Int): Boolean {
        var isNeed: Boolean = true
        val localAssIpList = getLocalAssIPList(ipMode)
        val localRcIpList = getLocalRcIPList(ipMode)

        var assIPList: List<String>? = null
        var rcIPList: List<String>? = null

        val routeAddress = s2c_redirect_route.route
        for (routeIP: S2c_route_address in routeAddress) {
            if (routeIP.type == 1) {
                assIPList = routeIP.route_address
            } else if (routeIP.type == 3) {
                rcIPList = routeIP.route_address
            }
        }
        if (localAssIpList != null && localRcIpList != null && localAssIpList == assIPList && localRcIpList == rcIPList) {
            isNeed = false
        }

        return isNeed
    }

    /**
     *保存路由表
     * s2cRouteTable  主动获取的路由表
     * ipMode         ip模式
     */
    fun saveRouteTable(routeTable: Get_Route_Resp, ipMode: Int) {
        logd("tRoute saveRouteTable routeTable:${routeTable} ipMode=${ipMode} 100-business 101-test")
        var assIPList: List<String>? = null
        var rcIPList: List<String>? = null
        val routeAddress = routeTable.routeAddress
        for (routeIP: RouteAddress in routeAddress) {
            if (routeIP.type == 1) {
                assIPList = routeIP.address
            } else if (routeIP.type == 3) {
                rcIPList = routeIP.address
            }
        }

        if (assIPList != null && rcIPList != null) {
            val mUServiceUid = SysUtils.getUServiceUid()
            val tempIPList = ArrayList<String>()
            if(assIPList.isNotEmpty()){
                tempIPList.addAll(assIPList)
            }
            if(rcIPList.isNotEmpty()){
                tempIPList.addAll(rcIPList)
            }
            if(tempIPList.isNotEmpty()){
                tempIPList.forEach {
                    logd("tRoute saveRouteTable -> open net limit ip = $it");
                    SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(true, DnsUtils.getDnsOrIp(it), mUServiceUid));
                }
            }

            val gson = Gson()

            val assIPListJson = gson.toJson(assIPList)
            val rcIPListJson = gson.toJson(rcIPList)
            if (ipMode == BUSINESS) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, BUSINESS_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, BUSINESS_ROUTE_LIST, rcIPListJson)

            } else if (ipMode == SAAS2) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS2_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS2_ROUTE_LIST, rcIPListJson)

            } else if (ipMode == ServerRouter.SAAS3) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS3_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS3_ROUTE_LIST, rcIPListJson)

            } else if (ipMode == ServerRouter.FACTORY) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, FACTORY_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, FACTORY_ROUTE_LIST, rcIPListJson)
            }

        } else {
            loge("tRoute save faile！ assIPList == null || rcIPList == null")
        }
    }

    /**
     *保存路由表
     * s2cRouteTable  推送的路由表
     * ipMode         商用模式还是测试模式
     */
    fun saveRouteTable(s2cRouteTable: S2c_redirect_route, ipMode: Int) {
        var assIPList: List<String>? = null
        var rcIPList: List<String>? = null
        val routeAddress = s2cRouteTable.route
        for (routeIP: S2c_route_address in routeAddress) {
            if (routeIP.type == 1) {
                assIPList = routeIP.route_address
            } else if (routeIP.type == 3) {
                rcIPList = routeIP.route_address
            }
        }

        if (assIPList != null && rcIPList != null) {
            val mUServiceUid = SysUtils.getUServiceUid()
            val tempIPList = ArrayList<String>()
            if(assIPList.isNotEmpty()){
                tempIPList.addAll(assIPList)
            }
            if(rcIPList.isNotEmpty()){
                tempIPList.addAll(rcIPList)
            }
            if(tempIPList.isNotEmpty()){
                tempIPList.forEach {
                    logd("tRoute s2c saveRouteTable -> open net limit ip = $it");
                    SeedCardNetLimitHolder.getInstance().configDnsOrIp(SeedCardNetInfo(true, DnsUtils.getDnsOrIp(it), mUServiceUid));
                }
            }


            val gson = Gson()
            //转换成json数据，再保存
            val assIPListJson = gson.toJson(assIPList)
            val rcIPListJson = gson.toJson(rcIPList)
            if (ipMode == BUSINESS) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, BUSINESS_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, BUSINESS_ROUTE_LIST, rcIPListJson)

            } else if (ipMode == SAAS2) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS2_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS2_ROUTE_LIST, rcIPListJson)

            } else if (ipMode == ServerRouter.SAAS3) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS3_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, SAAS3_ROUTE_LIST, rcIPListJson)

            } else if (ipMode == ServerRouter.FACTORY) {
                SharedPreferencesUtils.putString(ServiceManager.appContext, FACTORY_ASS_LIST, assIPListJson)
                SharedPreferencesUtils.putString(ServiceManager.appContext, FACTORY_ROUTE_LIST, rcIPListJson)
            }
        } else {
            loge("tRoute save faile！ assIPList == null || rcIPList == null")
        }
    }

    /**
     * 获取本地 ass ip 列表
     */
    fun getLocalAssIPList(ipMode: Int): List<String>? {
        val strJson = when (ipMode) {
            BUSINESS -> SharedPreferencesUtils.getString(ServiceManager.appContext, BUSINESS_ASS_LIST)
            SAAS2 -> SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS2_ASS_LIST)
            ServerRouter.SAAS3 -> SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS3_ASS_LIST)
            ServerRouter.FACTORY -> SharedPreferencesUtils.getString(ServiceManager.appContext, FACTORY_ASS_LIST)
            else -> null
        }
        val assIPList = Gson().fromJson<List<String>>(strJson, object : TypeToken<List<String>>() {}.type)
        logd("tRoute getLocalAssIPList:${assIPList?.toString()}  ipMode=$ipMode 101-test")
        return assIPList
    }

    /**
     * 设置IP，目前只在M2 At指令中用的到
     * **/
    fun setLocalAssIPList(ipMode:Int,ip:String,index:Int):Int{
        val strJson = when (ipMode) {
            BUSINESS -> SharedPreferencesUtils.getString(ServiceManager.appContext, BUSINESS_ASS_LIST)
            SAAS2 -> SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS2_ASS_LIST)
            ServerRouter.SAAS3 -> SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS3_ASS_LIST)
            ServerRouter.FACTORY -> SharedPreferencesUtils.getString(ServiceManager.appContext, FACTORY_ASS_LIST)
            else -> null
        }
        var assIPList  = Gson().fromJson<List<String>>(strJson, object : TypeToken<List<String>>() {}.type) as MutableList
        if(index<assIPList.size && index<5) {
            assIPList.set(index, ip)
            when (ipMode) {
                BUSINESS -> SharedPreferencesUtils.putString(ServiceManager.appContext, BUSINESS_ASS_LIST, assIPList.toString())
                SAAS2 -> SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS2_ASS_LIST, assIPList.toString())
                ServerRouter.SAAS3 -> SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS3_ASS_LIST, assIPList.toString())
                ServerRouter.FACTORY -> SharedPreferencesUtils.getString(ServiceManager.appContext, FACTORY_ASS_LIST, assIPList.toString())
            }
            return 0
        }
        return 1

    }

    //获取本地路由中心 ip 列表
    fun getLocalRcIPList(ipMode: Int): List<String>? {
        var strJson: String? = null
        if (ipMode == BUSINESS) {
            strJson = SharedPreferencesUtils.getString(ServiceManager.appContext, BUSINESS_ROUTE_LIST)
        } else if (ipMode == SAAS2) {
            strJson = SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS2_ROUTE_LIST)
        } else if (ipMode == ServerRouter.SAAS3) {
            strJson = SharedPreferencesUtils.getString(ServiceManager.appContext, SAAS3_ROUTE_LIST)
        } else if (ipMode == ServerRouter.FACTORY) {
            strJson = SharedPreferencesUtils.getString(ServiceManager.appContext, FACTORY_ROUTE_LIST)
        }
        val gson = Gson()
        val rcIPList = gson.fromJson<List<String>>(strJson, object : TypeToken<List<String>>() {
        }.type)
        logd("tRoute getLocalRcIPList:${rcIPList?.toString()}  ipMode=${ipMode} 101-test")
        return rcIPList
    }

    //默认slot1的
    fun getMcc(ctx: Context): String {
        val teleMnger = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val mSubscriptionManager = SubscriptionManager.from(ctx)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(0)
        var subId: Int
        var mccmnc: String = ""

        mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(0)
        if (mSubscriptionInfo != null) {
            subId = mSubscriptionInfo.subscriptionId
//            mccmnc = teleMnger.getNetworkOperatorForSubscription(subId)
            mccmnc = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
            if (mccmnc != null && mccmnc.length > 3) {
                return mccmnc.substring(0, 3)
            }
        } else {
            mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1)
            if (mSubscriptionInfo != null) {
                subId = mSubscriptionInfo.subscriptionId
//                mccmnc = teleMnger.getNetworkOperatorForSubscription(subId)
                mccmnc = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
                //mccmnc = teleMnger.getNetworkOperatorForSubscription(subId)
                mccmnc = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
                if (mccmnc != null && mccmnc.length > 3) {
                    return mccmnc.substring(0, 3)
                }
            }
        }
        return mccmnc
    }

    fun getErrMsg(t: Throwable?): String {
        if (t == null) {
            return "UNKNOWN"
        } else if (t.message == null) {
            return "UNKNOWN"
        } else {
            return t.message!!
        }
    }

    fun isNetworkConnected(context: Context?): Boolean {
        if (context != null) {
            val mConnectivityManager = context
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val mNetworkInfo = mConnectivityManager.getActiveNetworkInfo()
            if (mNetworkInfo != null) {
                return mNetworkInfo!!.isAvailable()
            }
        }
        return false
    }

    fun isNetWorkAvailable(context: Context): Boolean {
        val runtime = Runtime.getRuntime()
        try {
            val pingProcess = runtime.exec("/system/bin/ping -c 1 www.baidu.com")
            val exitCode = pingProcess.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    fun isWifiConnected(context: Context?): Boolean {
        if (context != null) {
            val mConnectivityManager = context
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val mWiFiNetworkInfo = mConnectivityManager
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            if (mWiFiNetworkInfo != null) {
                return mWiFiNetworkInfo.isAvailable
            }
        }
        return false
    }
}