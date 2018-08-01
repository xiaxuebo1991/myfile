package com.ucloudlink.refact.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.framework.remoteuim.*
import com.ucloudlink.framework.util.Callback

import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId

import com.ucloudlink.refact.utils.HexUtil
import org.codehaus.jackson.util.ByteArrayBuilder
import rx.Observable
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import java.util.*
import com.ucloudlink.framework.ucloudsocket.*
import com.ucloudlink.refact.card.*
import com.ucloudlink.refact.channel.enabler.datas.Card
import rx.functions.Action0

//import com.ucloudlink.framework.remoteuim
/**
 * Created by ucloudlink on 2017/09/28.
 * RemoteSim 接口
 */
class UcloudServiceSession(val context: Context) {
    var service: IUcloudServiceClientService? = null
    private val conn = Connection()
    private val callback: IUcloudServiceClientServiceCallback = RemoteCallback()
    val availableCard = HashMap<Int, Card>()
    //var ehplmnFlag : Boolean = false
    var fileFlag: Boolean = false
    var fileFlag2: Boolean = false
    var fileFlag3: Boolean = false
    var fileFlag4: Boolean = false
    var fileFlag5: Boolean = false
    var fileFlag6: Boolean = false
    //var EPLMNLENMAX : Int = 60
    var ehplmnBuff: ByteArray? = null
    private val APDU_INVALID = "apdu response invalid"
    //var ehplmnBuff = byteArrayOf(0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF)*/
    private val pm = ServiceManager.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mWakeLocks: Array<PowerManager.WakeLock?> = arrayOfNulls(2)
    private val mWakeLockMark: Array<Boolean> = arrayOf(false, false)

    /*连接Service*/
    inner class Connection : ServiceConnection {

        override fun onServiceDisconnected(p0: ComponentName?) {
            service = null

            isconnected = false
            JLog.logd("uim service disconnected")
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            service = IUcloudServiceClientService.Stub.asInterface(p1)
            JLog.logd("ucloudlink service connected")
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
        JLog.logd("bindService")

        context.bindService(Intent(context, UcloudServiceClientService::class.java), conn, Context.BIND_AUTO_CREATE)


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
    //适配要发送的事件
    fun UcloudServiceEvent.fit(): UcloudServiceEvent {
        val card = availableCard.get(slot)
        card ?: return this
        return this
    }

    //有可能调用这个方法的时候，service 还没连接上
    fun sendUcloudEvent(rmtEvent: UcloudServiceEvent): Int? {
        val fitRmtEvent = rmtEvent.fit()
        if (service == null) {
            if (paddingEvent == null) {
                JLog.logd("ucloudlink paddingEvent == null")
                paddingEvent = arrayListOf()
            }
            synchronized(lock) {
                if (service == null) {
                    paddingEvent!!.add(Callback { sendRmtEvent(fitRmtEvent) })
                    JLog.logd("Service is not connect padding to send")
                } else {
                    sendRmtEvent(fitRmtEvent)
                }
            }
        } else {
            sendRmtEvent(fitRmtEvent)
        }
        return 0
    }
     /*发送事件*/
     public  fun sendRmtEvent(fitRmtEvent: UcloudServiceEvent) {
         JLog.logd("ucloudlink sendRemoteEvent: ${fitRmtEvent.event} arthex:${fitRmtEvent.eventByte.toHex()}")
        val ret = service?.ucloudServiceEventProc(fitRmtEvent.slot, fitRmtEvent.event, fitRmtEvent.eventByte, fitRmtEvent.subEvent,fitRmtEvent.subEventString,fitRmtEvent.errorCode, fitRmtEvent.eventTimeout)
        //logd("ucloudlink sendRemoteEvent: $ret, event: ${fitRmtEvent.event} arthex:${fitRmtEvent.atr.toHex()}")
    }
    /*断开ucloud socket*/
    fun disconnectUcloudSocket(slot: Int): Int? {
        JLog.logd("disconnectUcloudSocket!!")
        val event = UcloudServiceEvent(slot, UcloudServiceEvent.UCLOUDLINK_DISCONNECT_SOCKET, "".toByteArray(),0,"",0,0)
        return sendUcloudEvent(event)
    }
    /*连接ucloud socket*/
    fun connectUcloudSocket(slot: Int): Int? {
        val event = UcloudServiceEvent(slot, UcloudServiceEvent.UCLOUDLINK_CONNECT_SOCKET, "".toByteArray(),0,"",0,0)
        return sendUcloudEvent(event)
    }
    fun getSocketDisConnectStatus():Boolean?{
        JLog.logd("getSocketDisConnectStatus!!")
        if(service == null){
            JLog.logd("getSocketDisConnectStatus!! service == null")
            return true;
        }else{
            return service?.getSocketDisConnectStatus()
        }
    }
    /*测试ucloud 通道*/
    fun setNetworkPref(slot:Int,network_pref:String): Int? {
        JLog.logd("setNetworkPref!!")
        //val event = UcloudServiceEvent(slot, UcloudServiceEvent.UCLOUDLINK_SET_NETWORK_PREF, network_pref.toByteArray(),0,"",0,0)
        val event = UcloudServiceEvent(slot, 0, network_pref.toByteArray(),0,"",0,0)
        return sendUcloudEvent(event)
    }
    /*Get refrash value*/
    fun getMcfgRefreshValue(slot:Int): Int? {
        JLog.logd("getMcfgRefreshValue!!")
        //val event = UcloudServiceEvent(slot, UcloudServiceEvent.UCLOUDLINK_SET_NETWORK_PREF, network_pref.toByteArray(),0,"",0,0)
        val event = UcloudServiceEvent(slot,0 ,"1".toByteArray(),0,"",0,0)
        return sendUcloudEvent(event)
    }
    /*发送plmn list到modem*/
    fun sendPlmnListBinToModem(rmtEvent: UcloudServiceEvent): Int?{
        JLog.logd("sendPlmnListBinToModem!!")
        return sendUcloudEvent(rmtEvent)
    }
    /*回调函数处理*/
    inner class RemoteCallback : IUcloudServiceClientServiceCallback.Stub() {
        override fun ucloudServiceEventResponse(slot: Int, responseCode: Int) {
            JLog.logv("ucloudlink UcloudServiceEventResponse: $slot,$responseCode")
            UcloudController.staRefreshOb.onNext(responseCode);
            //UcloudController.staRefreshOb.onCompleted();
           // UcloudController.staRefreshOb.doOnUnsubscribe(Action0 {  })

        }
        override fun ucloudServiceEventIndication(slot: Int, apduCmd: ByteArray?) {
            //logv("ucloudlink UcloudServiceApduIndication: $slot,$responseCode")
            }

        }


    //}
}