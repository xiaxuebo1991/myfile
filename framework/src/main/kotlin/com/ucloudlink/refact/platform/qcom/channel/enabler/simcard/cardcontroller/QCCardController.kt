package com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.cardcontroller

import android.os.Message
import android.telephony.TelephonyManager
import com.ucloudlink.framework.remoteuim.CardException
import com.ucloudlink.framework.remoteuim.SoftSimNative
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.APDU
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthState
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.CardController
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv
import com.ucloudlink.refact.utils.toHex

/**
 * Created by jiaming.liang on 2018/1/6.
 *
 *适配QC起卡流程
 *
 * 处理一些QC流程才有的事件
 */
class QCCardController : CardController() {
    private val DELAY_EVENT = 0x1000
    private val DELAY_INSERT_EVENT = DELAY_EVENT or 0x0100
    var channelOn = arrayOf(false, false, false, false)

    //用于标志虚拟卡通道是否要保持与当前状态是否保持
//    private val softSimChannelsState = SparseArray<ChannelState>()

    init {
        (platformTransfer as QCPlatFormTransfer).qcCardController = this
//        softSimChannelsState.append(0, ChannelState(false, false))
//        softSimChannelsState.append(1, ChannelState(false, false))
    }

    override fun powerUpCard(card: Card) {
        super.powerUpCard(card)
        //判断当前起卡卡槽是否有卡存在
        val state = ServiceManager.systemApi.getSimState(card.slot)
        logv("getSimState:$state")
        card.lastCardExist = (state != TelephonyManager.SIM_STATE_ABSENT)

        if (card.cardType == CardType.SOFTSIM) {
            if (channelOn[card.slot]) {
                sendRemoteEvent(card, RemoteUimEvent.UIM_REMOTE_CARD_INSERTED)
            } else {
                channelOn[card.slot] = true
                sendRemoteEvent(card, RemoteUimEvent.UIM_REMOTE_CONNECTION_AVAILABLE)
            }
        } else {
            channelOn[card.slot] = true
            sendRemoteEvent(card, RemoteUimEvent.UIM_REMOTE_CONNECTION_AVAILABLE)
        }
    }


    /*初始化*/
    override fun initEnv(slot: Int) {
        super.initEnv(slot)
        disableRSIMChannel(-1)
        disconnectTransfer(-1)
    }

    private fun sendRemoteEvent(card: Card, eventId: Int): Int {
        val event = RemoteUimEvent(card.slot, eventId, card.atr)
        return (platformTransfer as QCPlatFormTransfer).sendRemoteEvent(event, card)
    }

    private fun sendRemoteEvent(slot: Int, eventId: Int): Int {
        val event = RemoteUimEvent(slot, eventId, "".toByteArray())
        logv("sendRemoteEvent $event")
        return (platformTransfer as QCPlatFormTransfer).sendRemoteEvent(event)
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        val event = msg.what

        if ((event and DELAY_INSERT_EVENT) == DELAY_INSERT_EVENT) {
            val slot = msg.arg1
            val card = availableCard[slot]
            card ?: return
            sendRemoteEvent(card, RemoteUimEvent.UIM_REMOTE_CARD_INSERTED)
        }

    }

    override fun connectTransfer(slot: Int) {
        super.connectTransfer(slot)
        logv("connectTransfer($slot)")
        if (slot != -1) {
            connectUIMSocket(slot)
        } else {
            connectUIMSocket(0)
            connectUIMSocket(1)
        }
    }

    override fun disconnectTransfer(slot: Int) {
        super.disconnectTransfer(slot)
        if (slot != -1) {
            disconnectUIMSocket(slot)
        } else {
            disconnectUIMSocket(0)
            disconnectUIMSocket(1)
        }
    }

    override fun enableRSIMChannel(slot: Int) {
        super.enableRSIMChannel(slot)
        logd("QC not support enable RSIM Channel outside")
    }

    override fun disableRSIMChannel(slot: Int, force: Boolean) {
        super.disableRSIMChannel(slot, force)
        if (force) {
            if (slot != -1) {
                connectUIMSocket(slot)
            } else {
                connectUIMSocket(0)
                connectUIMSocket(1)
            }
            Thread.sleep(if (platformTransfer.isReady()) 500 else 2500)
        }

        if (slot != -1) {
            closeSoftSimChannel(slot)
        } else {
            closeSoftSimChannel(0)
            closeSoftSimChannel(1)
        }
    }

    fun connectUIMSocket(slot: Int): Int {
        return sendRemoteEvent(slot, RemoteUimEvent.UIM_REMOTE_CONNECT_SOCKET)
    }

    fun disconnectUIMSocket(slot: Int): Int {
        return sendRemoteEvent(slot, RemoteUimEvent.UIM_REMOTE_DISCONNECT_SOCKET)
    }

    fun uimRemoteApduIndication(slot: Int, apduReq: ByteArray) {
        doGetApdu(APDU(slot, apduReq))
    }

    override fun replyApdu(apduRsp: APDU) {
        super.replyApdu(apduRsp)
        val card = availableCard[apduRsp.slot]
        if (card != null && card.cardType == CardType.VSIM && apduRsp.isAuth && apduRsp.isSucc) {
            apduHandler.onAuthStateChange(AuthState.AUTH_SUCCESS)
        }
        sendRemoteApdu(apduRsp.slot, 0, apduRsp.rsp)
    }

    private fun sendRemoteApdu(slot: Int, apduStatus: Int, response: ByteArray) {
        (platformTransfer as QCPlatFormTransfer).sendRemoteApdu(slot, apduStatus, response)
    }

    // 连上后马上发removed消息，5秒后再发insert消息，这个能否优化？
    fun uimRemoteConnectIndication(slot: Int) {
        sendRemoteEvent(slot, RemoteUimEvent.UIM_REMOTE_CARD_REMOVED)
        if((ServiceManager.seedCardEnabler.getCard().slot == slot && ServiceManager.seedCardEnabler.getCard().lastCardExist) ||
                (ServiceManager.cloudSimEnabler.getCard().slot == slot && ServiceManager.cloudSimEnabler.getCard().lastCardExist)) {
            JLog.logd("send insert delay 4s")
            sendMessageDelayed(obtainMessage(DELAY_INSERT_EVENT or slot, slot, 0), 4000)
            lockCPU(5000)
        } else {
            JLog.logd("send insert direct")
            sendMessage(obtainMessage(DELAY_INSERT_EVENT or slot, slot, 0))
        }
    }

    fun uimRemotePowerUpIndication(slot: Int) {
        sendCardResetEvent(slot)
    }

    fun uimRemoteResetIndication(slot: Int) {
        cardResetForSwap(slot)
        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_REMOTE_UIM_RESET_INDICATION)

    }

    private fun cardResetForSwap(slot: Int) {
        val card = availableCard[slot]
        card ?: return
        resetCard(card)

    }

    private fun resetCard(card: Card) {
        if (card.status != CardStatus.POWERON) {
            throw CardException("card status error:${card.status}")
        }

        val atr = ByteArray(128)
        val atrLen = IntArray(1)

        val ret = SoftSimNative.resetCard(card.vslot, atr, atrLen)
        JLog.logd("native resetCard ret:$ret $card: ${atrLen[0]}, ${atr.toHex()} ")
        if (ret == SoftSimNative.E_SOFTSIM_SUCCESS) {
            card.status = CardStatus.POWERON
            val atrBytes = atr.copyOfRange(0, atrLen[0])
            card.atr = atrBytes
            sendCardResetEvent(card.slot)
        } else {
            throw CardException("reset card failed: $ret")
        }
    }

    private fun sendCardResetEvent(slot: Int) {
        val card = availableCard[slot]
        card ?: return
        sendRemoteEvent(card, RemoteUimEvent.UIM_REMOTE_CARD_RESET)
    }

    override fun removeCard(card: Card, keepChannel: Boolean) {
        super.removeCard(card, keepChannel)
        logd("debug removeCard:$card")
        val slot = card.slot
        removeCmdWithSlot(DELAY_INSERT_EVENT, slot)
        if (keepChannel) {
            sendRemoteEvent(card, RemoteUimEvent.UIM_REMOTE_CARD_REMOVED)
        } else {
            closeSoftSimChannel(slot)
        }

    }

    private fun closeSoftSimChannel(slot: Int) {
        logv("closeSoftSimChannel")
        channelOn[slot] = false
        sendRemoteEvent(slot, RemoteUimEvent.UIM_REMOTE_CONNECTION_UNAVAILABLE)
    }

    private fun removeCmdWithSlot(event: Int, slot: Int) {
        removeMessages(event or slot)
    }

//    fun setSoftSimChannelStay(slot: Int, isStay: Boolean) {
//        softSimChannelsState[slot].exceptStay = isStay
//    }
}

//data class ChannelState(var exceptStay: Boolean, var realStay: Boolean)