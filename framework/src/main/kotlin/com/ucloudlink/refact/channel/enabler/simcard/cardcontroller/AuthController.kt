package com.ucloudlink.refact.channel.enabler.simcard.cardcontroller

import com.ucloudlink.framework.protocol.protobuf.ApduAuthResp
import com.ucloudlink.framework.remoteuim.CardException
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.frequentauth.FrequentAuth
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.performancelog.logs.PerfLogVsimDelayStat
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.apdu.ApduData
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.transceiver.protobuf.MessagePacker
import com.ucloudlink.refact.channel.transceiver.protobuf.ProtoPacket
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.PerformanceStatistics
import com.ucloudlink.refact.utils.ProcessState
import com.ucloudlink.refact.utils.toHex
import okio.ByteString
import rx.Single
import rx.Subscription
import rx.lang.kotlin.single
import rx.lang.kotlin.subscribeWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Created by zhangxian on 2018/1/15.
 */
object AuthController {
    private val apduCache = ConcurrentHashMap<@Volatile String, @Volatile ByteArray>(50)//fixme 是否需要置空
    private var lastApdu: ByteArray? = null
    private val SAME_AUTH_RSP_TIME: Long = 6
    private val UNLIKE_AUTH_RSP_TIME: Long = 12
    private val SERVER_RSP_TIME: Long = 30
    private var authSendSub: Subscription? = null
    //pause cloudsim auth request when handling out going call
    var pauseCloudSimAuth = false
    public val APDU_INVALID = "apdu response invalid"
    /**
     * 释放当前鉴权请求，云卡释放时调用
     */
    fun releaseAuthRequest() {
        val _authSendSub1 = authSendSub
        if (_authSendSub1 != null && !_authSendSub1.isUnsubscribed) {
            _authSendSub1.unsubscribe()
        }
    }

    fun authHandler(card: Card, cmd: APDU): Single<APDU> {
        return Single.create {
            sub ->
            val isClosing = ServiceManager.cloudSimEnabler.isClosing()
            if (isClosing) {
                sub.onError(CardException("cloudsim is closing ignore apdu"))
            } else {
                val reqApduData = ApduData(card.imsi, cmd.req)
                var hasRsp = false
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_NEED_AUTH)
                authSendSub = authSend(reqApduData).subscribe({
                    if (hasRsp) return@subscribe
                    hasRsp = true
                    val rsp = it.apduData
                    val rspHex = rsp.toHex()

                    if (checkApduRspValid(card,rspHex)) {
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_AUTH_REPLIED)
                        JLog.logk("requestApdu from server success!")
                        cmd.rsp = rsp
                        sub.onSuccess(cmd)
                    } else {
                        JLog.logk("requestApdu from server:$rspHex")
                        sub.onError(Exception(APDU_INVALID))
                    }
                    
                    authSendSub = null
                }, {
                    if (hasRsp) return@subscribe
                    hasRsp = true
                    JLog.logk("requestApdu from server fail! $it")
                    sub.onError(it)
                    authSendSub = null
                })
            }
        }
    }

    private fun authSend(reqData: ApduData): Single<ApduData> {
        if (pauseCloudSimAuth) {
            return Single.error<ApduData>(Exception("pauseCloudSimAuth == true"))
        }
        var apduRequestSub: Subscription?=null
        return single<ApduData> { sub ->
            apduRequestSub = requestApdu(reqData).subscribe({
                sub.onSuccess(ApduData(reqData.imsi, it as ByteArray))
            }, {
                JLog.loge("requestApdu error:${it.message}")
                it.printStackTrace()
                sub.onError(it)
            })
        }.timeout(UNLIKE_AUTH_RSP_TIME, TimeUnit.SECONDS).doOnUnsubscribe {
            val sub = apduRequestSub
            if (sub != null && !sub.isUnsubscribed) {
                sub.unsubscribe()
            }
        }
    }

    private fun requestApdu(apduReq: ApduData): Single<Any> {
        var subRespOb: Subscription? = null
        val reqStr = ByteString.of(apduReq.apduData, 0, apduReq.apduData.size).toString() + apduReq.imsi
        var timeout: Long = 0

        if (lastApdu.toString() == apduReq.apduData.toString()) {
            JLog.logd("=apduReq.apduData:" + apduReq.apduData.toString() + "lastApdu:" + lastApdu.toString())
            timeout = SAME_AUTH_RSP_TIME
        } else {
            lastApdu = apduReq.apduData
            JLog.logd("apduReq.apduData:" + apduReq.apduData.toString() + "lastApdu:" + lastApdu.toString())
            timeout = UNLIKE_AUTH_RSP_TIME
        }

        if (apduCache.containsKey(reqStr)) {
            val rspApdu = apduCache[reqStr] as ByteArray
            JLog.logv("apdu cache: ${rspApdu.toHex()}")
            return single<Any> { sub -> sub.onSuccess(rspApdu) }
        }
        val msg = MessagePacker.createApduReqPacket(apduReq)
        msg.payload as ProtoPacket
        val sn = msg.payload.sn
        val ip = ServerRouter.current_AssIp
        val currentSocketStatus = Requestor.seedChannel.currentSocketStatus
        val seedSignalStrength = OperatorNetworkInfo.signalStrength
        val rat = OperatorNetworkInfo.rat
        JLog.logk(" Action:RequestApdu,$sn,$ip,$currentSocketStatus,$seedSignalStrength,$rat")

        Requestor.reconnector.needReconnect = false
        PerformanceStatistics.process = ProcessState.CLOUD_AUTH_REQ
        PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.AUTHAPDU_START,sn.toInt(),0)
        return Requestor.requestForShortTime(msg, {
            responseOb ->
            Single.create<Any> { sub ->
                subRespOb = responseOb.map {
                    if (it is ApduAuthResp) {
                        if (it.errorCode == ErrorCode.RPC_RET_OK) {
                            FrequentAuth.faHandler.sendEmptyMessage(FrequentAuth.MSG_REV_APDU_RSP)
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, 0, 0, "Succ")
                            it.data.toByteArray()
                        } else {
                            Requestor.reconnector.needReconnect = true
                            val exception = Exception(ErrorCode.RPC_HEADER_STR + it.errorCode)

                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0, exception)
                            sub.onError(exception)
                        }
                    } else if (it is Exception) {
                        Requestor.reconnector.needReconnect = true
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0, it)
                        sub.onError(it)
                    } else {
                        Requestor.reconnector.needReconnect = true
                        val exception = ErrorCode.PARSE_HEADER_STR + it.toString()
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0, exception)

                        sub.onError(Exception(exception))
                    }
                }.subscribeWith {
                    onNext {
                        if (!this.subscriber.isUnsubscribed) {
                            this.subscriber.unsubscribe()
                        }
                        JLog.logd("get ApduRsp from server success! serial = ${msg.payload.sn}")
                        PerformanceStatistics.process = ProcessState.CLOUD_AUTH_RSP

                        //再一次检查缓存，如果缓存有就发送缓存的
                        val apduRsp: ByteArray
                        if (apduCache.containsKey(reqStr)) {
                            apduRsp = apduCache[reqStr] as ByteArray
                            JLog.logd("use apdu rsp in cache :$apduRsp")
                        } else {
                            apduCache[reqStr] = it as ByteArray
                            apduRsp = it
                        }
                        sub.onSuccess(apduRsp)
                    }
                    onError {
                        Requestor.reconnector.needReconnect = true
                        JLog.logd("get ApduRsp from server failed! serial = ${msg.payload.sn}")
                        PerformanceStatistics.process = ProcessState.CLOUD_AUTH_FAIL
                        PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.AUTHAPDU_END,-1,0)
                        sub.onError(it)
                    }
                }
            }.doOnUnsubscribe {
                if (subRespOb != null) {
                    if (!(subRespOb as Subscription).isUnsubscribed) (subRespOb as Subscription).unsubscribe()
                }
            }
        }, timeout, SERVER_RSP_TIME)
    }

    /**
     * 检查无效apdu 响应
     */
    private fun checkApduRspValid(card: Card,rsp: String): Boolean {
        if (rsp.length <= 4) return false
        if (rsp == "009A4EE000016D800C") return false
        if (rsp.startsWith("DC", true)) {
            //若是为DC则给予五次重试机会
            JLog.logd("authDcRetryTimes:${card.authDcRetryTimes}")
            if(++ card.authDcRetryTimes >= 5){
                return false
            }
        }
        card.authDcRetryTimes = 0
        return true
    }
}
