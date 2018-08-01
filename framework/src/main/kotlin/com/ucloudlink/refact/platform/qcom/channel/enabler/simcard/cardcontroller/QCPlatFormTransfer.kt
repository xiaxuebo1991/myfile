package com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.cardcontroller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.ucloudlink.framework.remoteuim.IUimRemoteClientService
import com.ucloudlink.framework.remoteuim.IUimRemoteClientServiceCallback
import com.ucloudlink.framework.remoteuim.UimRemoteClientService
import com.ucloudlink.framework.util.Callback
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.IPlatformTransfer
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.softSimType
import com.ucloudlink.refact.config.dunApnList
import com.ucloudlink.refact.config.dunApnListVersion
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv
import com.ucloudlink.refact.utils.toHex
import java.util.*

/**
 * Created by jiaming.liang on 2018/1/6.
 * sendRemoteEvent
 * 绑定QC UimRemoteClientService
 * 提供发送消息接口
 *
 */
class QCPlatFormTransfer : IPlatformTransfer() {

    private val context = ServiceManager.appContext
    private val connection = Connection()
    private var service: IUimRemoteClientService? = null

    private val paddingEventLock = Any()

    private var paddingEvent: ArrayList<com.ucloudlink.framework.util.Callback<Any>>? = null

    private val callback: IUimRemoteClientServiceCallback = RemoteCallback()

    lateinit var qcCardController: QCCardController

    init {
        //初始化dun配置
        iniDunConfig()
        //初始化时绑定服务
        bindService()
    }

    private fun iniDunConfig() {
        val sharedPreferences = context.getSharedPreferences("dunConfig", 0)
        val version = sharedPreferences.getInt("version", 0)
        if (dunApnListVersion != version) {
            val edit = sharedPreferences.edit()
            edit.putInt("version", dunApnListVersion)
            dunApnList.forEach {
                edit.putString(it.key, it.value)
            }
            edit.apply()
        }
    }

    private fun bindService() {
        logv("bindService")
        context.bindService(Intent(context, UimRemoteClientService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun sendRemoteEvent(rmtEvent: RemoteUimEvent, card: Card? = null): Int {
        val fitRmtEvent = rmtEvent.fit(card)
        if (service == null) {
            if (paddingEvent == null) {
                paddingEvent = arrayListOf()
            }
            synchronized(paddingEventLock) {
                if (service == null) {
                    paddingEvent!!.add(Callback { sendRmtEvent(fitRmtEvent) })
                    logd("Service is not connect padding to send")
                } else {
                    sendRmtEvent(fitRmtEvent)
                }
            }
            return 2
        } else {
            sendRmtEvent(fitRmtEvent)
            return 0
        }

    }

    //适配要发送的事件
    private fun RemoteUimEvent.fit(card: Card? = null): RemoteUimEvent {
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

    private fun sendRmtEvent(fitRmtEvent: RemoteUimEvent) {
        logv("sendRmtEvent")
        val ret = service?.uimRemoteEvent(fitRmtEvent.slot, fitRmtEvent.event, fitRmtEvent.atr, fitRmtEvent.errorCode, fitRmtEvent.transport, fitRmtEvent.usage, fitRmtEvent.apduTimeout, fitRmtEvent.disableAllPolling, fitRmtEvent.pollTimer)
        logd("sendRemoteEvent: $ret, event: ${fitRmtEvent.event} arthex:${fitRmtEvent.atr.toHex()}")
    }

    override fun isReady(): Boolean {
        return service != null
    }

    inner class Connection : ServiceConnection {

        override fun onServiceDisconnected(p0: ComponentName?) {
            service = null
            logv("uim service disconnected")
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            service = IUimRemoteClientService.Stub.asInterface(p1)
            logd("uim service connected")
            registerRemoteCallback()

            //send padding Event
            synchronized(paddingEventLock) {
                paddingEvent?.forEach {
                    it.onCallback(null)
                }
                paddingEvent == null
            }

        }
    }

    private fun registerRemoteCallback() {
        service?.registerCallback(callback)
    }

    private inner class RemoteCallback : IUimRemoteClientServiceCallback.Stub() {
        override fun uimRemoteEventResponse(slot: Int, responseCode: Int) {
            logv("uimRemoteEventResponse: $slot,$responseCode")
        }

        override fun uimRemoteApduResponse(slot: Int, responseCode: Int) {
            logv("uimRemoteApduResponse: $slot,$responseCode")
        }

        override fun uimRemoteApduIndication(slot: Int, apduCmd: ByteArray) {
            qcCardController.uimRemoteApduIndication(slot, apduCmd)


        }

        override fun uimRemoteConnectIndication(slot: Int) {
            logv("uimRemoteConnectIndication: $slot")
            qcCardController.uimRemoteConnectIndication(slot)
        }

        override fun uimRemoteDisconnectIndication(slot: Int) {
            logv("uimRemoteDisconnectIndication: $slot")
        }

        override fun uimRemotePowerUpIndication(slot: Int) {
            logv("uimRemotePowerUpIndication: $slot")
            qcCardController.uimRemotePowerUpIndication(slot)
        }

        override fun uimRemotePowerDownIndication(slot: Int) {
            logv("uimRemotePowerDownIndication: $slot")
        }

        override fun uimRemoteResetIndication(slot: Int) {
            logv("uimRemoteResetIndication: $slot")
            qcCardController.uimRemoteResetIndication(slot)

        }

    }


    fun sendRemoteApdu(slot: Int, apduStatus: Int, apduRsp: ByteArray) {
        val ret = service?.uimRemoteApdu(slot, apduStatus, apduRsp)
        logv("sendRemoteApdu: $ret, slot: $slot, status: $apduStatus, rsp: ${apduRsp.toHex()}")
    }
}

data class RemoteUimEvent(val slot: Int = UIM_REMOTE_SLOT0,
                          val event: Int,
                          val atr: ByteArray,
                          val errorCode: Int = UIM_REMOTE_CARD_ERROR_NONE,
                          var usage: Int = UIM_REMOTE_USAGE_NORMAL,
                          var transport: Int = UIM_REMOTE_TRANSPORT_IP,
                          val apduTimeout: Int = DEFAULT_APDU_TIMEOUT,
                          val disableAllPolling: Int = DISABLE_POLLING_FALSE,
                          val pollTimer: Int = POLL_TIMER) {
    companion object {
        const val UIM_REMOTE_CONNECTION_UNAVAILABLE = 0
        const val UIM_REMOTE_CONNECTION_AVAILABLE = 1
        const val UIM_REMOTE_CARD_INSERTED = 2
        const val UIM_REMOTE_CARD_REMOVED = 3
        const val UIM_REMOTE_CARD_ERROR = 4
        const val UIM_REMOTE_CARD_RESET = 5
        const val UIM_REMOTE_CONNECT_SOCKET = 6
        const val UIM_REMOTE_DISCONNECT_SOCKET = 7


        const val UIM_REMOTE_SLOT0 = 0;
        const val UIM_REMOTE_SLOT1 = 1;
        const val UIM_REMOTE_SLOT2 = 2;

        //      This param will be non-zero only for UIM_REMOTE_CARD_ERROR event
        const val UIM_REMOTE_CARD_ERROR_NONE = 0;
        const val UIM_REMOTE_CARD_ERROR_UNKNOWN = 1;
        const val UIM_REMOTE_CARD_ERROR_NO_LINK_EST = 2;
        const val UIM_REMOTE_CARD_ERROR_CMD_TIMEOUT = 3;
        const val UIM_REMOTE_CARD_ERROR_POWER_DOWN = 4;

        //  * 	@Transport
        const val UIM_REMOTE_TRANSPORT_OTHER = 0;
        const val UIM_REMOTE_TRANSPORT_BLUETOOTH = 1;
        const val UIM_REMOTE_TRANSPORT_IP = 2;

        //*  @Usage
        const val UIM_REMOTE_USAGE_REDUCED = 0
        const val UIM_REMOTE_USAGE_NORMAL = 1

        //polling time ms
//        const val POLL_TIMER = 180000
        const val POLL_TIMER = (45 * 60 * 1000)

        /* @return*/
        const val UIM_REMOTE_SUCCESS = 0
        const val UIM_REMOTE_ERROR = 1

        const val DISABLE_POLLING_TRUE = 1
        const val DISABLE_POLLING_FALSE = 0

        const val DEFAULT_APDU_TIMEOUT = 14000 //in miliseconds

        const val UIM_REMOTE_APDU_EXCHANGE_SUCCESS = 0
        const val UIM_REMOTE_APDU_EXCHANGE_FAILURE = 1
    }
}