package com.ucloudlink.refact.channel.enabler.simcard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import com.ucloudlink.framework.remoteuim.*
import com.ucloudlink.framework.util.Callback
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.business.APDUStatus
import com.ucloudlink.refact.channel.apdu.ApduData
import com.ucloudlink.refact.channel.enabler.datas.*
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.HexUtil
import com.ucloudlink.refact.utils.JLog.*
import org.codehaus.jackson.util.ByteArrayBuilder
import rx.Observable
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import java.util.*

/**
 * Created by chentao on 2016/6/22.
 * RemoteSim 接口
 */
class RemoteUimSession(val context: Context) {
    var service: IUimRemoteClientService? = null
    private val conn = Connection()
    private val callback: IUimRemoteClientServiceCallback = RemoteCallback()

    val availableCard = HashMap<Int, Card>()

    internal var simBusy = byteArrayOf(0x6F, 0x00)
    //key is apduCmd hex string
//    val apduCache = ConcurrentHashMap<ByteString, ByteArray>(4)

    //var ehplmnFlag : Boolean = false
    var fileFlag: Boolean = false
    var fileFlag2: Boolean = false
    var fileFlag3: Boolean = false
    var fileFlag4: Boolean = false
    var fileFlag5: Boolean = false
    var fileFlag6: Boolean = false

    var fileFlag7: Boolean = false
    //var EPLMNLENMAX : Int = 60
    var ehplmnBuff: ByteArray? = null
    private val APDU_INVALID = "apdu response invalid"
    //var ehplmnBuff = byteArrayOf(0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF)*/
    private val pm = ServiceManager.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    val apduWakeLock: PowerManager.WakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "APDU LOCK_0")
    var mMccTypeMap: HashMap<Int, Int> = HashMap<Int, Int>()

    val MCC_TYPE_UNKNOW: Int = 0
    val MCC_TYPE_AE: Int = 1
    val MCC_TYPE_CN: Int = 2
    val MCC_TYPE_GB: Int = 3
    val MCC_TYPE_IN: Int = 4
    val MCC_TYPE_JP: Int = 5
    val MCC_TYPE_US: Int = 6

    fun Card.softSimType(): Boolean {
        val softType = this.ki != null && this.opc != null
        return softType
    }


    inner class Connection : ServiceConnection {

        override fun onServiceDisconnected(p0: ComponentName?) {
            service = null

            isconnected = false
            logd("uim service disconnected")
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            service = IUimRemoteClientService.Stub.asInterface(p1)
            logd("uim service connected")
            registerRemoteCallback()

            isconnected = true

            //send padding Event
            synchronized(lock) {
                paddingEvent?.forEach {
                    it.onCallback(null)
                }
                paddingEvent == null
            }

        }
    }

    var isconnected = false
    fun bindService()/*: Single<Boolean>*/ {
        logd("bindService")

        context.bindService(Intent(context, UimRemoteClientService::class.java), conn, Context.BIND_AUTO_CREATE)


    }

    fun unbindService() {
        unregisterRemoteCallback()
        context.unbindService(conn)
    }

    fun registerRemoteCallback() {
        service?.registerCallback(callback)
    }

    fun unregisterRemoteCallback() {
        service?.deregisterCallback(callback)
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

    private var paddingEvent: ArrayList<Callback<Any>>? = null

    private val lock = Any()

    //有可能调用这个方法的时候，service 还没连接上
    fun sendRemoteEvent(rmtEvent: RemoteUimEvent): Int? {
        val fitRmtEvent = rmtEvent.fit()
        if (service == null) {
            if (paddingEvent == null) {
                paddingEvent = arrayListOf()
            }
            synchronized(lock) {
                if (service == null) {
                    paddingEvent!!.add(Callback { sendRmtEvent(fitRmtEvent) })
                    logd("Service is not connect padding to send")
                } else {
                    sendRmtEvent(fitRmtEvent)
                }
            }
        } else {
            sendRmtEvent(fitRmtEvent)
        }
        return 0
    }

    private fun sendRmtEvent(fitRmtEvent: RemoteUimEvent) {
        val ret = service?.uimRemoteEvent(fitRmtEvent.slot, fitRmtEvent.event, fitRmtEvent.atr, fitRmtEvent.errorCode, fitRmtEvent.transport, fitRmtEvent.usage, fitRmtEvent.apduTimeout, fitRmtEvent.disableAllPolling, fitRmtEvent.pollTimer)
        logd("sendRemoteEvent: $ret, event: ${fitRmtEvent.event} arthex:${fitRmtEvent.atr.toHex()}")
    }


    fun sendRemoteApdu(slot: Int, apduStatus: Int, apduRsp: ByteArray): Int? {
        val ret = service?.uimRemoteApdu(slot, apduStatus, apduRsp)
        logv("debug apduReqRspCount:$apduReqRspCount,apduCount:$apduCount, sendRemoteApdu: $ret, slot: $slot, status: $apduStatus, rsp: ${apduRsp.toHex()}")
        return ret
    }

    fun disconnectUIMSocket(slot: Int): Int? {
        val event = RemoteUimEvent(slot, RemoteUimEvent.Companion.UIM_REMOTE_DISCONNECT_SOCKET, "".toByteArray())
        return sendRemoteEvent(event)

    }

    fun connectUIMSocket(slot: Int): Int? {
        val event = RemoteUimEvent(slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECT_SOCKET, "".toByteArray())
        return sendRemoteEvent(event)
    }

    fun insertCard(card: Card) {
        println("insertCard card: $card")
        if (card.status >= CardStatus.INSERTED) {
            logd("card had powerOn")
            return
        }
//        if (card.status!=CardStatus.IDLE){
//            throw CardException("card status error:${card.status}")
//        }
        val vslot = IntArray(1)
        val ret = SoftSimNative.insertCard(card.imsi, vslot)
        logd("insertCard card!!: $card ret:$ret  vslot:${vslot[0]}")
        when (ret) {
            SoftSimNative.E_SOFTSIM_SUCCESS, SoftSimNative.E_SOFTSIM_CARD_INSERTED
            -> {
                if (vslot[0] == 0) {
                    vslot[0] = 1
                }
                card.vslot = vslot[0]
                card.status = CardStatus.INSERTED
            }
            else -> {
                throw CardException("insert card failed: $ret")
            }
        }
    }

    fun powerUpCard(card: Card) {
        if (card.status >= CardStatus.POWERON) {
            logd("card had powerOn")
            return
        }
//        if (card.status != CardStatus.INSERTED)
//        {
//            throw CardException("card status error:${card.status}")
//        }

        logd("powerUp card: $card")
        val atr = ByteArray(128)
        val atrLen = IntArray(1)

        queryCardType(card)

        val ret = SoftSimNative.powerUp(card.vslot, atr, atrLen)
        logd("native powerup card: $atrLen, ${atr.toHex()}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS || ret == SoftSimNative.E_SOFTSIM_CARD_POWERUP) {
            if (card.cardType == CardType.VSIM) {
                setCardEplmnList(card)
            }
            if (card.cardType == CardType.SOFTSIM) {
                if (Configuration.isOpenRplmnTest) {
                    //getCardType(card)
                    if (card.cardModel == CardModel.UICC) {
                        setUiccCardLoci(card, null)
                        setUiccCardPsloci(card, null)
                        setUiccCardEpsloci(card, null)
                    } else if (card.cardModel == CardModel.GSM) {
                        setGsmCardLoci(card, null)
                        setGsmCardLocigprs(card, null)
                    }
                }
            }
//            card.status = CardStatus.POWERON  //在外面更新状态
            val atrBytes = atr.copyOfRange(0, atrLen[0])
            card.atr = atrBytes
            availableCard.put(card.slot, card)
            if (card.cardType == CardType.SOFTSIM) {
//                if (Configuration.isSoftCardAvailable == true) {
//                    sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_INSERTED, atrBytes))
//                } else {
//                    sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_AVAILABLE, atrBytes))
//                    Configuration.softCartAvailableSlot = card.slot
//                    Configuration.isSoftCardAvailable = true
//                }
//                ServiceManager.accessMonitor.setMonitorTimeoutFlag(false)
//                Configuration.softCartExceptFlag = false
            } else {
                sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_AVAILABLE, atrBytes))
            }
        } else {
            throw CardException("powerUp card failed: $ret")
        }
    }

    //适配要发送的事件
    fun RemoteUimEvent.fit(): RemoteUimEvent {
        val card = availableCard.get(slot)
        card ?: return this
        if (card.softSimType()) {
            transport = RemoteUimEvent.UIM_REMOTE_TRANSPORT_OTHER
            usage = RemoteUimEvent.UIM_REMOTE_USAGE_REDUCED
        } else {
            transport = RemoteUimEvent.UIM_REMOTE_TRANSPORT_IP
            usage = RemoteUimEvent.UIM_REMOTE_USAGE_NORMAL
        }

        return this
    }

    fun powerDownCard(card: Card) {
        if (card.status < CardStatus.POWERON) {
            throw CardException("card status error: ${card.status}")
        }

        val ret = SoftSimNative.powerDown(card.vslot)

        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            card.status = CardStatus.INSERTED
            logd("debug powerDownCard:$card")
            availableCard.remove(card.slot)
        } else if (ret == SoftSimNative.E_SOFTSIM_CARD_NOINSERTED) {
            loge("soft has no insert")
        } else {
            throw CardException("powerdown card failed: $ret")
        }
    }

//    fun unavailableSoftCard() {
//        logd("unavailableSoftCard enter")
//        if (Configuration.isSoftCardAvailable == true) {
//            sendRemoteEvent(RemoteUimEvent(Configuration.softCartAvailableSlot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_UNAVAILABLE, "".toByteArray()))
//        }
//
//        Configuration.isSoftCardAvailable = false
//        Configuration.isPhyCardAvailable = true
//        Configuration.softCartExceptFlag = false
//    }

    fun removeCard(card: Card) {

        if (card.status < CardStatus.INSERTED) {
            throw CardException("card status error: ${card.status}")
        }

        val ret = SoftSimNative.removeCard(card.vslot)
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
//            card.status = CardStatus.ABSENT   //在DataEnablerBase 中改变其状态
            logd("debug removeCard:$card")
//            RemoteEventDelay.removeSendInsertCmd(card.slot)
            if (card.cardType == CardType.SOFTSIM) {
                //不关软卡通道两个条件：
                //1如果软卡因为异常关闭，而且物理卡不可用时，
                //2大循环触发的关卡且当前为软卡时，不关通道时
//                if((ServiceManager.accessMonitor.getMonitorTimeoutFlag()&& Configuration.isSoftCardAvailable) ||
//                        Configuration.softCartExceptFlag == true && Configuration.isPhyCardAvailable == false){
//                    sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_REMOVED, card.atr))
//                } else {
//                    sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_UNAVAILABLE, card.atr))
//                    Configuration.isSoftCardAvailable = false
//                }
//                Configuration.softCartExceptFlag = false
            } else {
                sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_UNAVAILABLE, card.atr))
            }
        } else {
            throw CardException("remove card failed: $ret")
        }
    }

//    fun removeCardForSwap(card: Card) {
////        var success = false
//        logd("removeCardForSwap $card")
//        if (card.status.ordinal <= CardStatus.ABSENT.ordinal) {
//            throw CardException("card status error: ${card.status}")
//        }
//        sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_REMOVED, card.atr))
//        val ret = SoftSimNative.removeCard(card.vslot)
////        val ret =  SoftSimDrive.SoftSimRemoveCard(card.vslot)
//        logd("SoftSimNative.removeCard ret:$ret")
//        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
//            card.status = CardStatus.ABSENT
//        } else if (ret == SoftSimNative.E_SOFTSIM_CARD_NOINSERTED) {
//            //do notthing
//        } else {
//            throw CardException("remove card failed: $ret")
//        }
//    }

    fun insertCardForSwap(card: Card) {
        logd("card insertCardForSwap: $card")
        sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_INSERTED, card.atr))
    }

    fun resetCard(card: Card) {
        if (card.status != CardStatus.POWERON) {
            throw CardException("card status error:${card.status}")
        }

        logd("reset card: $card")
        val atr = ByteArray(128)
        val atrLen = IntArray(1)

        val ret = SoftSimNative.resetCard(card.vslot, atr, atrLen)
        logd("native resetCard ret:$ret $card: ${atrLen[0]}, ${atr.toHex()} ")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            card.status = CardStatus.POWERON
            val atrBytes = atr.copyOfRange(0, atrLen[0])
            card.atr = atrBytes
            sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_RESET, card.atr))
        } else {
            throw CardException("reset card failed: $ret")
        }
    }

    fun cardReset(card: Card) {
        logd("card reset: $card")
        sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_RESET, card.atr))
    }

    fun cardInserted(card: Card) {
        logd("card reset: $card")
        sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_INSERTED, card.atr))
    }

    fun cardReset(slot: Int) {
        val card = availableCard.get(slot)
        if (card != null) {
            cardReset(card)
        }
    }

    fun cardInserted(slot: Int) {
        val card = availableCard.get(slot)
        if (card != null) {
            cardInserted(card)
        }
    }

    fun cardDisconnect(slot: Int) {
        val card = availableCard.get(slot)
        if (card != null) {
            sendRemoteEvent(RemoteUimEvent(card.slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_UNAVAILABLE, card.atr))
//            if (card.cardType == CardType.SOFTSIM) {
//                Configuration.isSoftCardAvailable = false
//            }
        }
    }

    fun cardResetForSwap(slot: Int) {
        val card = availableCard.get(slot)
        if (card != null) {
            resetCard(card)
        }
    }

    fun isReady(): Boolean {
        return service != null
    }

    fun disconnectAllSim() {
        logd("card disconnectAllSim two simcard")
        //disconnect two sim card
        disconnectSim(0)
        disconnectSim(1)
//        Configuration.isSoftCardAvailable = false
    }

    fun disconnectSim(slot: Int) {
//        RemoteEventDelay.removeSendInsertCmd(slot)
        sendRemoteEvent(RemoteUimEvent(slot, RemoteUimEvent.Companion.UIM_REMOTE_CONNECTION_UNAVAILABLE, "".toByteArray()))
    }

    /*
    * ���ÿ���ehplmn��ֻ��plmnlist�е�mcc��imsi���е�mccһ�µ�����²�д����
    * */
    fun setCardEHplmn(vslot: Int, plmn: ByteArray): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb2)//6FD9
        buider.append(0x00)
        buider.append(plmn.size)

        for (x in plmn) {
            buider.append(x.toInt())
        }

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(16)

        logd("setCardEHplmn:${vslot} + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(vslot, request, rsp, rspLen)
        logd("setCardEHplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                loge("setCardEHplmn: success!!!!!")
                return 0
            } else {
                loge("setCardEHplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardEHplmn failed! ret:" + ret)
            return -1
        }
    }

    /*
    * ������д��������
    * */
    fun setCardUplmn(vslot: Int, plmn: ByteArray): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb0)//6F60
        buider.append(0x00)
        buider.append(plmn.size)

        for (x in plmn) {
            buider.append(x.toInt())
        }

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(16)

        logd("setCardUplmn:${vslot} + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(vslot, request, rsp, rspLen)
        logd("setCardUplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                logd("setCardUplmn: success!!!!!")
                return 0
            } else {
                loge("setCardUplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardUplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setMccTypeMap() {
        logd("seMccTypeMap start")

        mMccTypeMap.put(424, MCC_TYPE_AE)
        mMccTypeMap.put(431, MCC_TYPE_AE)
        mMccTypeMap.put(430, MCC_TYPE_AE)
        mMccTypeMap.put(460, MCC_TYPE_CN)
        mMccTypeMap.put(461, MCC_TYPE_CN)
        mMccTypeMap.put(234, MCC_TYPE_GB)
        mMccTypeMap.put(235, MCC_TYPE_GB)
        mMccTypeMap.put(406, MCC_TYPE_IN)
        mMccTypeMap.put(404, MCC_TYPE_IN)
        mMccTypeMap.put(405, MCC_TYPE_IN)
        mMccTypeMap.put(441, MCC_TYPE_JP)
        mMccTypeMap.put(440, MCC_TYPE_JP)
        mMccTypeMap.put(316, MCC_TYPE_US)
        mMccTypeMap.put(311, MCC_TYPE_US)
        mMccTypeMap.put(314, MCC_TYPE_US)
        mMccTypeMap.put(310, MCC_TYPE_US)
        mMccTypeMap.put(315, MCC_TYPE_US)
        mMccTypeMap.put(312, MCC_TYPE_US)
        mMccTypeMap.put(313, MCC_TYPE_US)

        logd("seMccTypeMap end")
    }

    fun setCardEplmnList(card: Card): Int {
        val ehplmnBuilder: ByteArrayBuilder = ByteArrayBuilder()
        val uplmnBuilder: ByteArrayBuilder = ByteArrayBuilder()
        val rplmnBuilder: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0
        var ret: Int = 0
        var rat1: Int
        var rat2: Int

        setMccTypeMap()
        //ehplmnFlag = false//ȷ��ehplmn���óɹ�������
        ehplmnBuff = null
        val eplmnlist = card.eplmnlist

        if (eplmnlist == null) {
            loge("card.eplmnlist.size is null! ")
            return -1
        }

        logd("setCardEplmnList: mcc${card.imsi.substring(0, 3)}, eplmnlist:$eplmnlist")

        for (i in eplmnlist.indices) {
            rat1 = 0
            rat2 = 0

            if (eplmnlist[i].supportedRat == 0) {
                continue
            }

            if (rplmnBuilder.toByteArray().isEmpty()) {
                if (eplmnlist[i].plmn.length == 5) {
                    rplmnBuilder.append((eplmnlist[i].plmn.get(0).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(1).toInt() - '0'.toInt()) * 0x10)
                    rplmnBuilder.append((eplmnlist[i].plmn.get(2).toInt() - '0'.toInt()) + 0xf0)
                    rplmnBuilder.append((eplmnlist[i].plmn.get(3).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(4).toInt() - '0'.toInt()) * 0x10)
                } else if (eplmnlist[i].plmn.length == 6) {
                    rplmnBuilder.append((eplmnlist[i].plmn.get(0).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(1).toInt() - '0'.toInt()) * 0x10)
                    rplmnBuilder.append((eplmnlist[i].plmn.get(2).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(5).toInt() - '0'.toInt()) * 0x10)
                    rplmnBuilder.append((eplmnlist[i].plmn.get(3).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(4).toInt() - '0'.toInt()) * 0x10)
                } else {
                    logd("setCardRplmnList: length err")
                    continue
                }
            }

            logd("mcc1 len:" + card.imsi.substring(0, 3).toInt() + ",mcc2 len:" +
                    eplmnlist[i].plmn.substring(0, 3).toInt() + ",mcc1:" +
                    mMccTypeMap[card.imsi.substring(0, 3).toInt()] + ",mcc2:" +
                    mMccTypeMap[eplmnlist[i].plmn.substring(0, 3).toInt()])

            if (card.imsi.substring(0, 3) == eplmnlist[i].plmn.substring(0, 3) ||
                    (mMccTypeMap[card.imsi.substring(0, 3).toInt()] == mMccTypeMap[eplmnlist[i].plmn.substring(0, 3).toInt()] &&
                            mMccTypeMap[card.imsi.substring(0, 3).toInt()] != null)) {
                if (eplmnlist[i].plmn.length == 5) {
                    ehplmnBuilder.append((eplmnlist[i].plmn.get(0).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(1).toInt() - '0'.toInt()) * 0x10)
                    ehplmnBuilder.append((eplmnlist[i].plmn.get(2).toInt() - '0'.toInt()) + 0xf0)
                    ehplmnBuilder.append((eplmnlist[i].plmn.get(3).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(4).toInt() - '0'.toInt()) * 0x10)
                } else if (eplmnlist[i].plmn.length == 6) {
                    ehplmnBuilder.append((eplmnlist[i].plmn.get(0).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(1).toInt() - '0'.toInt()) * 0x10)
                    ehplmnBuilder.append((eplmnlist[i].plmn.get(2).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(5).toInt() - '0'.toInt()) * 0x10)
                    ehplmnBuilder.append((eplmnlist[i].plmn.get(3).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(4).toInt() - '0'.toInt()) * 0x10)
                } else {
                    logd("setCardEplmnList: length err")
                    continue
                }
            } else {
                if (eplmnlist[i].plmn.length == 5) {
                    uplmnBuilder.append((eplmnlist[i].plmn.get(0).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(1).toInt() - '0'.toInt()) * 0x10)
                    uplmnBuilder.append((eplmnlist[i].plmn.get(2).toInt() - '0'.toInt()) + 0xf0)
                    uplmnBuilder.append((eplmnlist[i].plmn.get(3).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(4).toInt() - '0'.toInt()) * 0x10)
                } else if (eplmnlist[i].plmn.length == 6) {
                    uplmnBuilder.append((eplmnlist[i].plmn.get(0).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(1).toInt() - '0'.toInt()) * 0x10)
                    uplmnBuilder.append((eplmnlist[i].plmn.get(2).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(5).toInt() - '0'.toInt()) * 0x10)
                    uplmnBuilder.append((eplmnlist[i].plmn.get(3).toInt() - '0'.toInt()) + (eplmnlist[i].plmn.get(4).toInt() - '0'.toInt()) * 0x10)
                } else {
                    logd("setCarduplmnList: length err")
                    continue
                }
                //RAT_CDMA_RADIO          --0
                //RAT_HDR_RADIO            --1
                //RAT_GSM_RADIO            --2
                //RAT_WCDMA_RADIO         --3
                //RAT_LTE_RADIO             --4
                //RAT_TDS_RADIO             --5
                if ((eplmnlist[i].supportedRat and 0x04) == 0x04) {
                    rat2 = rat2 or 0x80
                }
                if ((eplmnlist[i].supportedRat and 0x01) == 0x01 ||
                        (eplmnlist[i].supportedRat and 0x02) == 0x02 ||
                        (eplmnlist[i].supportedRat and 0x08) == 0x08 ||
                        (eplmnlist[i].supportedRat and 0x20) == 0x20) {
                    rat1 = rat1 or 0x80
                }
                if ((eplmnlist[i].supportedRat and 0x10) == 0x10) {
                    rat1 = rat1 or 0x40
                }
                logd("ehplmnByteArray rat1 ${rat1}")
                logd("ehplmnByteArray rat2 ${rat2}")
                uplmnBuilder.append(rat1)
                uplmnBuilder.append(rat2)
            }
        }

        val ehplmnByteArray = ehplmnBuilder.toByteArray()
        val uplmnByteArray = uplmnBuilder.toByteArray()

        logd("ehplmnByteArray size ${ehplmnByteArray.size} uplmnByteArray size ${uplmnByteArray.size}")
        if (ehplmnByteArray.isNotEmpty()) {
            ret = setCardEHplmnCache(ehplmnByteArray)
            //ret = setCardEHplmn(card.vslot, ehplmnByteArray)
        }

        if (rplmnBuilder.toByteArray().isNotEmpty()) {
            ret += setCardRplmn(card, rplmnBuilder.toByteArray())
        }

        if (uplmnByteArray.isNotEmpty()) {
            ret += setCardUplmn(card.vslot, uplmnByteArray)
        }

        return ret
    }

    fun getCardType(card: Card): Int {
        val builder: ByteArrayBuilder = ByteArrayBuilder()


        builder.append(0x00)
        builder.append(0xa4)
        builder.append(0x00)//6f7e
        builder.append(0x04)
        builder.append(0x02)
        builder.append(0x3f)
        builder.append(0x00)

        val request = builder.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(280)

        logd("getCardType: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("getCardType return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if ((rsp.get(0) == 0x90.toByte()) || (rsp.get(0) == 0x61.toByte())) {
                card.cardModel = CardModel.UICC
                logd("getCardType: UICC!!!!!")
                return 0
            } else if (rsp.get(0) == 0x6E.toByte()) {
                card.cardModel = CardModel.GSM
                logd("getCardType: GSM!!!!!")
                return 0
            } else {
                loge("getCardType:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set getCardType failed! ret:" + ret)
            return -1
        }
    }

    fun setUiccCardLoci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb4)//6f7e
        buider.append(0x00)
        buider.append(0x0B)

        while (i++ < 4) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setUiccCardPsloci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb3)//6F73
        buider.append(0x00)
        buider.append(0x0E)

        while (i++ < 7) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setUiccCardEpsloci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb5)//6FE3
        buider.append(0x00)
        buider.append(0x12)

        while (i++ < 12) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setGsmCardLoci(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb3)//6F7E
        buider.append(0x00)
        buider.append(0x0B)

        while (i++ < 4) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logd("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logd("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setGsmCardLocigprs(card: Card, plmn: ByteArray?): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        var i = 0

        buider.append(0x00)
        buider.append(0xd3)
        buider.append(0xb4)//6F53
        buider.append(0x00)
        buider.append(0x0E)

        while (i++ < 7) {
            buider.append(0xFF)
        }
        if (plmn == null) {
            buider.append(0xFF)
            buider.append(0xFF)
            buider.append(0xFF)
        } else {
            buider.append(plmn[0].toInt())
            buider.append(plmn[1].toInt())
            buider.append(plmn[2].toInt())
        }
        buider.append(0x00)
        buider.append(0x00)
        buider.append(0xFF)
        buider.append(0x01)

        val request = buider.toByteArray()
        val rspLen: IntArray = IntArray(4)
        val rsp: ByteArray = ByteArray(4)

        logv("setCardRplmn: + request: ${HexUtil.encodeHexStr(request)}")
        val ret = SoftSimNative.apdu(card.vslot, request, rsp, rspLen)
        logv("setCardRplmn return ${HexUtil.encodeHexStr(rsp)}, ${rspLen.get(0)}")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            if (rsp.get(0) == 0x90.toByte() && rsp.get(1) == 0x00.toByte()) {
                logd("setCardRplmn: success!!!!!")
                return 0
            } else {
                loge("setCardRplmn:failed!!! ${HexUtil.encodeHexStr(rsp)}")
                return -1
            }
        } else {
            loge("set setCardRplmn failed! ret:" + ret)
            return -1
        }
    }

    fun setCardRplmn(card: Card, plmn: ByteArray): Int {
        //getCardType(card)
        card.cardModel = CardModel.UICC
        if (card.cardModel == CardModel.UICC) {
            setUiccCardLoci(card, plmn)
            setUiccCardPsloci(card, plmn)
            setUiccCardEpsloci(card, plmn)
        } else if (card.cardModel == CardModel.GSM) {
            setGsmCardLoci(card, plmn)
            setGsmCardLocigprs(card, plmn)
        }
        return 0
    }

    fun setCardEHplmnCache(plmn: ByteArray): Int {
        val buider: ByteArrayBuilder = ByteArrayBuilder()
        //var i : Int = 0

        logv("setCardEHplmnCache len: ${plmn.size}")
        /*if(plmn.size > EPLMNLENMAX){
            logd("setCardEHplmnCache len faile: ${plmn.size}")
            return -1
        }*/
        try {
            /*while(i < plmn.size){
                buider.append(plmn[i].toInt())
                i++
            }*/
            for (x in plmn) {
                buider.append(x.toInt())
            }
/*
            while(i++ < EPLMNLENMAX){
                buider.append(0xFF)//���ಹFF
            }*/
            ehplmnBuff = buider.toByteArray()
            //ehplmnFlag = true
            logv("setCardEHplmnCache: + buf: ${HexUtil.encodeHexStr(ehplmnBuff)}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    fun queryCardType(card: Card): Int {
        val cardType: ByteArray = ByteArray(1)
        val vsimType: ByteArray = ByteArray(1)

        logv("queryCardType:${card.vslot} + : ${card.imsi}")
        val ret = SoftSimNative.queryCardType(card.imsi, cardType, vsimType)
        logv("queryCardType return ${cardType[0]}, ${vsimType[0]}")
        when (cardType[0]) {
            SoftSimNative.MODE_SOFTSIM_GSM.toByte() -> {
                logv("queryCardType GSM!!!")
                card.cardModel = CardModel.GSM
            }
            SoftSimNative.MODE_SOFTSIM_UICC.toByte() -> {
                logv("queryCardType UICC!!!")
                card.cardModel = CardModel.UICC
            }
            else -> {
                loge("queryCardType ERR!!!")
                card.cardModel = CardModel.UNKNOWMODEL
            }
        }
        when (vsimType[0]) {
            SoftSimNative.CARD_VSIM.toByte() -> {
                logv("queryCardType VSIM!!!")
            }
            SoftSimNative.CARD_SOFTSIM.toByte() -> {
                logv("queryCardType SOFTSIM!!!")
            }
            else -> {
                loge("queryCardType ERR!!!")
            }
        }

        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            return 0
        } else {
            loge("queryCardType failed! ret:" + ret)
            return -1
        }
    }


    private val cloudSimStateObservable = PublishSubject<APDUStatus>()

    fun cloudSimStateObservable(): Observable<APDUStatus> {
        return cloudSimStateObservable
    }

    /**
     * 监视云卡apdu是否完成
     */


    fun printApduRsp(card: Card, rsp: String, flag: Boolean) {
        if (flag) {
            when (card.cardType) {
                CardType.VSIM -> {
                    logv("modem_apdu native rsp:VSIM,${card.slot},$rsp")
                }
                CardType.SOFTSIM -> {
                    logv("modem_apdu native rsp:SOFTSIM,${card.slot},$rsp")
                }
                CardType.PHYSICALSIM -> {
                    logv("modem_apdu native rsp:PHYSICALSIM,${card.slot},$rsp")
                }
//                CardType.UNKNOWCARD -> {
//                    logd("modem_apdu native rsp:UNKNOWCARD,${card.slot},$rsp")
//                }
                else -> {
                    logv("modem_apdu native rsp:ERRTYPE,${card.slot},$rsp")
                }
            }
        } else {
            when (card.cardType) {
                CardType.VSIM -> {
                    logv("modem_apdu #server# rsp:VSIM,${card.slot},$rsp")
                }
                CardType.SOFTSIM -> {
                    logv("modem_apdu #server# rsp:SOFTSIM,${card.slot},$rsp")
                }
                CardType.PHYSICALSIM -> {
                    logv("modem_apdu #server# rsp:PHYSICALSIM,${card.slot},$rsp")
                }
//                CardType.UNKNOWCARD -> {
//                    logd("modem_apdu #server# rsp:UNKNOWCARD,${card.slot},$rsp")
//                }
                else -> {
                    logv("modem_apdu #server# rsp:ERRTYPE,${card.slot},$rsp")
                }
            }
        }
    }

    fun printApduReq(card: Card, rsp: String, isNativeflag: Boolean) {
        if (isNativeflag) {
            when (card.cardType) {
                CardType.VSIM -> {
                    logv("modem_apdu native req:VSIM,${card.slot},$rsp")
                }
                CardType.SOFTSIM -> {
                    logv("modem_apdu native req:SOFTSIM,${card.slot},$rsp")
                }
                CardType.PHYSICALSIM -> {
                    logv("modem_apdu native req:PHYSICALSIM,${card.slot},$rsp")
                }
//                CardType.UNKNOWCARD -> {
//                    logd("modem_apdu native req:UNKNOWCARD,${card.slot},$rsp")
//                }
                else -> {
                    logv("modem_apdu native req:ERRTYPE,${card.slot},$rsp")
                }
            }
        } else {
            when (card.cardType) {
                CardType.VSIM -> {
                    logv("modem_apdu #server# req:VSIM,${card.slot},$rsp")
                }
                CardType.SOFTSIM -> {
                    logv("modem_apdu #server# req:SOFTSIM,${card.slot},$rsp")
                }
                CardType.PHYSICALSIM -> {
                    logv("modem_apdu #server# req:PHYSICALSIM,${card.slot},$rsp")
                }
//                CardType.UNKNOWCARD -> {
//                    logd("modem_apdu #server# req:UNKNOWCARD,${card.slot},$rsp")
//                }
                else -> {
                    logv("modem_apdu #server# req:ERRTYPE,${card.slot},$rsp")
                }
            }
        }
    }

    fun apduBeforehandProc(card: Card, request: ByteArray) {

        if ((request.get(1) == 0xA4.toByte()) && (card.cardType == CardType.VSIM)) {
            fileFlag = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0xD9.toByte())
        }
        if ((request.get(1) == 0xA4.toByte()) && (card.cardType == CardType.VSIM)) {
            fileFlag2 = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0x40.toByte())
        }
        if ((request.get(1) == 0xA4.toByte()) && (card.cardType == CardType.VSIM)) {
            fileFlag3 = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0xAD.toByte())

            fileFlag6 = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0x62.toByte())

        }
        if ((request.get(1) == 0xA4.toByte()) && (card.cardType == CardType.SOFTSIM)) {
            fileFlag4 = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0xAD.toByte())

            fileFlag5 = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0x73.toByte())

            fileFlag7 = (request.get(request.lastIndex - 1) == 0x6F.toByte()) &&
                    (request.get(request.lastIndex) == 0x7B.toByte())
        }
    }

    fun apduAfterhandProc(card: Card, apdu: Apdu) {
        var temp_mnc_numeric = 0

        val request = apdu.request
        var rspApdu = apdu.rspApdu

        if (fileFlag && (ehplmnBuff != null) && (request.get(1) == 0xB0.toByte()) && (card.cardType == CardType.VSIM)) {
            val buider: ByteArrayBuilder = ByteArrayBuilder()
            var i: Int = 0
            var j: Int = 0

            while (i < request.get(4).toInt()) {
                if (i < ehplmnBuff!!.size) {
                    buider.append(ehplmnBuff!!.get(i).toInt())
                    i++
                } else {
                    logv("i:" + i + "j:" + j)
                    if ((rspApdu.get(j) != 0xFF.toByte()) && (rspApdu.get(j + 1) != 0xFF.toByte()) &&
                            (rspApdu.get(j + 2) != 0xFF.toByte())) {
                        logv("rspApdu:" + rspApdu.toHex().substring(j * 2, j * 2 + 6) + "ehplmnBuff:" + ehplmnBuff!!.toHex())
                        if (!ehplmnBuff!!.toHex().contains(rspApdu.toHex().substring(j * 2, j * 2 + 6), true)) {
                            buider.append(rspApdu.get(j).toInt())
                            buider.append(rspApdu.get(j + 1).toInt())
                            buider.append(rspApdu.get(j + 2).toInt())
                            i += 3
                        }
                    } else {
                        buider.append(0xFF)
                        buider.append(0xFF)
                        buider.append(0xFF)
                        i += 3
                    }
                    j += 3
                }
            }

            buider.append(0x90)
            buider.append(0x00)

            rspApdu = buider.toByteArray()
            printApduRsp(card, rspApdu.toHex(), true)
        } else if ((fileFlag3 && (request.get(1) == 0xB0.toByte()) && (card.cardType == CardType.VSIM))) {
            temp_mnc_numeric = rspApdu.get(3).toInt()
            if (temp_mnc_numeric == 2 || temp_mnc_numeric == 3) {
                Configuration.vsim_mnc_numeric = temp_mnc_numeric
            }
            logv("vsim_mnc_numeric:" + Configuration.vsim_mnc_numeric + " temp_mnc_numeric:" + temp_mnc_numeric)
        } else if ((fileFlag4 && (request.get(1) == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {
            temp_mnc_numeric = rspApdu.get(3).toInt()
            if (temp_mnc_numeric == 2 || temp_mnc_numeric == 3) {
                Configuration.softsim_mnc_numeric = temp_mnc_numeric
            }
            logv("softsim_mnc_numeric:" + Configuration.softsim_mnc_numeric + " temp_mnc_numeric:" + temp_mnc_numeric)
        } else if ((fileFlag5 && (request.get(1) == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {
            if (rspApdu.get(7) == 0x64.toByte() && rspApdu.get(8) == 0xF0.toByte() && rspApdu.get(9) == 0x0.toByte()) {
                rspApdu.set(9, 0x10)
            }
            printApduRsp(card, rspApdu.toHex(), true)
        } else if ((fileFlag7 && (request.get(1) == 0xB0.toByte()) && (card.cardType == CardType.SOFTSIM))) {
            if (rspApdu.size >= 9) {
                rspApdu.set(0, 0x54)
                rspApdu.set(1, 0xF5.toByte())
                rspApdu.set(2, 0x00)
                rspApdu.set(3, 0x54)
                rspApdu.set(4, 0xf5.toByte())
                rspApdu.set(5, 0x50)
                rspApdu.set(6, 0x15)
                rspApdu.set(7, 0xf0.toByte())
                rspApdu.set(8, 0x01)
            }
            printApduRsp(card, rspApdu.toHex(), true)
        } else if ((fileFlag6 && (request.get(1) == 0xB0.toByte()) && (card.cardType == CardType.VSIM))) {
            var number = 0

            while (number < request.get(4).toInt()) {
                if (rspApdu.get(number) == 0xff.toByte()) {
                    logv("HPLMNwAcT break")
                    break
                }
                if (rspApdu.get(number + 3).toInt() and 0x40 != 0x40) {
                    rspApdu.set(number + 3, (rspApdu.get(number + 3).toInt() or 0x40).toByte())
                }
                number += 5
            }

            printApduRsp(card, rspApdu.toHex(), true)
        }
        apdu.rspApdu = rspApdu
    }

    fun getCardApduCmdResponse(card: Card, request: ByteArray): Observable<ByteArray> {
        return Observable.create { sub ->

            var rspApdu: ByteArray

            val nativeOutRsp = ByteArray(512)
            val rspLen = IntArray(1)

            apduBeforehandProc(card, request)

            if ((fileFlag2 && (request.get(1) == 0xB2.toByte()) && (card.cardType == CardType.VSIM))) {
                val buider: ByteArrayBuilder = ByteArrayBuilder()
                var i: Int = 0

                while (i < request.get(4).toInt()) {
                    buider.append(0xFF)
                    i++
                }
                buider.append(0x90)
                buider.append(0x00)

                rspApdu = buider.toByteArray()
                rspLen[0] = rspApdu.size
                logv("softsim apdu response len: ${rspLen[0]}")
                logv("softsim apdu response buf: ${rspApdu.toHex()}")
                sub.onNext(rspApdu)
                sub.onCompleted()
            } else {
                val ret = SoftSimNative.apdu(card.vslot, request, nativeOutRsp, rspLen)
                if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
                    if (!apduWakeLock.isHeld) {
                        lockCPU(5000)
                    } else {
                        logd("apduWakeLock.isHeld = true")
                    }
                    rspApdu = nativeOutRsp.copyOfRange(0, rspLen[0])
                    printApduRsp(card, rspApdu.toHex(), true)
                    //logd("softsim apdu response success: ${rspApdu.toHex()}")
                    val apdu = Apdu(request, rspApdu)
                    apduAfterhandProc(card, apdu)
                    sub.onNext(apdu.rspApdu)
                    sub.onCompleted()
                } else {
                    logv("modem_apdu rsp failed:VSIM,${card.slot},$ret")
                    if (!card.softSimType()) {
                        val isClosing = ServiceManager.cloudSimEnabler.isClosing()
                        if (isClosing) {
                            sub.onError(CardException("cloudsim is closing ignore apdu"))
                        } else {
                            lockCPU(36000)

                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_NEED_AUTH)
                            val reqApduData = ApduData(card.imsi, request)
                            printApduReq(card, request.toHex(), false)
                            //logd("send apdu to cloud server: ${request.toHex()}")
                            cloudSimStateObservable.onNext(APDUStatus.NEEDAUTH)
                            var hasRsp = false
                            /*card.apduDelegate!!.apdu(reqApduData).subscribe({
                                if (hasRsp) return@subscribe
                                hasRsp = true
                                val rsp = it.apduData
                                val rspHex = rsp.toHex()
                                printApduRsp(card, rspHex, false)

                                val isOk = checkApduRspValid(rspHex)
                                if (isOk) {
                                    cloudSimStateObservable.onNext(APDUStatus.AUTHSUCC)
                                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_AUTH_REPLIED)
                                    logk("requestApdu from server success!")

                                    sub.onNext(rsp)
                                    sub.onCompleted()
                                } else {
                                    sub.onError(Exception(APDU_INVALID))
                                }

                                unLockCPU()
                            }, {
                                if (hasRsp) return@subscribe
                                hasRsp = true
                                logk("requestApdu from server fail! $it")
                                sub.onError(it)
                            })*/
                        }
                    } else {
                        //is softsim but SoftSimNative.apdu failed.
                        sub.onError(CardException("softSim exception"))
                    }
                }
            }
        }
    }

    /**
     * 检查无效apdu 响应
     */
    private fun checkApduRspValid(rsp: String): Boolean {
        if (rsp.length <= 4) return false
        if (rsp == "009A4EE000016D800C") return false
        if (rsp.startsWith("DC", true)) return false

        return true
    }

    private fun lockCPU(timeout: Long = 36000) {
        apduWakeLock.setReferenceCounted(false)
        logd("lockCPU $apduWakeLock")
        apduWakeLock.acquire(timeout)
    }

    private fun unLockCPU() {
        logd("unLockCPU $apduWakeLock")
        apduWakeLock.release()
    }

    fun getCardApduCmdResponse(slot: Int, request: ByteArray): Observable<ByteArray> {
        val card = availableCard.get(slot)
//        val cloudsim = ServiceManager.cloudSimEnabler.getCard()
        if (card != null) {//云卡正在执行关闭动作时,忽略云卡的apdu
//            if (slot == cloudsim.slot) {//fixme is seedsim need same  dispose?
//                val isClosing = ServiceManager.cloudSimEnabler.isClosing()
//                if (isClosing) {
//                    return Observable.error(CardException("cloudsim is closeing ingore apdu"))
//                }
//            }
            return getCardApduCmdResponse(card, request)
        } else {
            return Observable.error(CardException("card in slot $slot is not available"))
        }
    }

    var apduReqRspCount = 0
    var apduCount = 0

    inner class RemoteCallback : IUimRemoteClientServiceCallback.Stub() {
        override fun uimRemoteEventResponse(slot: Int, responseCode: Int) {
            logv("uimRemoteEventResponse: $slot,$responseCode")
        }

        override fun uimRemoteApduResponse(slot: Int, responseCode: Int) {
            logv("uimRemoteApduResponse: $slot,$responseCode")
        }

        override fun uimRemoteApduIndication(slot: Int, apduCmd: ByteArray?) {
            val card = availableCard.get(slot)
            //card?.status = CardStatus.NEEDAUTH
            apduReqRspCount++
            apduCount++

            logv("debug apduReqRspCount:$apduReqRspCount ")
            printApduReq(card!!, apduCmd!!.toHex(), true)


            getCardApduCmdResponse(slot, apduCmd).subscribeWith {
                onNext {
                    if (!this.subscriber.isUnsubscribed) {
                        this.subscriber.unsubscribe()
                    }
                    val apduResponse = it
                    apduReqRspCount--
                    sendRemoteApdu(slot, 0, apduResponse)

                }
                onError {
                    apduReqRspCount--
                    loge("rsp uimRemoteApduIndication exception: ${it.message}")
                    sendRemoteApdu(slot, 0, simBusy)
                    logv("apdu fail $card")
                    cloudSimStateObservable.onNext(APDUStatus.AUTHFAIL)


                    if (it.message == APDU_INVALID) {
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_APDU_INVALID)
                    }

                    unLockCPU()
                }
            }

        }

        override fun uimRemoteConnectIndication(slot: Int) {
            logv("uimRemoteConnectIndication: $slot")
            //Thread.sleep(10000)
            //logv("uimRemoteConnectIndication sleep: $slot")
            //sendCardResetEvent(slot)
            sendRemoteEvent(RemoteUimEvent(slot, RemoteUimEvent.Companion.UIM_REMOTE_CARD_REMOVED, "".toByteArray()))
//            RemoteEventDelay.sendInsertCmd(slot)
        }

        override fun uimRemoteDisconnectIndication(slot: Int) {
            logv("uimRemoteDisconnectIndication: $slot")
        }

        override fun uimRemotePowerUpIndication(slot: Int) {
            logv("uimRemotePowerUpIndication: $slot")
            cardReset(slot)
        }

        override fun uimRemotePowerDownIndication(slot: Int) {
            logv("uimRemotePowerDownIndication: $slot")
        }

        override fun uimRemoteResetIndication(slot: Int) {
            logv("uimRemoteResetIndication: $slot")
            cardResetForSwap(slot)
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_REMOTE_UIM_RESET_INDICATION)
        }

    }
}

data class Apdu(val request: ByteArray, var rspApdu: ByteArray)