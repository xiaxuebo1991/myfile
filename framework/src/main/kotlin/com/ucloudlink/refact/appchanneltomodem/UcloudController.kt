package com.ucloudlink.refact.network

import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.card.UcloudServiceEvent
import com.ucloudlink.refact.utils.JLog
import rx.lang.kotlin.PublishSubject
import rx.subjects.PublishSubject
/**
 * ucloud 发送信息到modem：
 * 参数下发
 */
object UcloudController {
    public  const val UCLOUD_CONNECT_SOCKET = 1
    public  const val UCLOUD_DISCONNECT_SOCKET = 2
    //ucloudlink
    public val staRefreshOb: PublishSubject<Any> = PublishSubject()
    val rmtSession: UcloudServiceSession = UcloudServiceSession(ServiceManager.appContext)// wlmark use rmtSession in here?

    init {
        rmtSession.bindService()
    }
    /*断开ucloud socket*/
    fun disconnectSocket(slot:Int){
        JLog.logd("ucloudlink rmtSession.disconnectSocket")
        rmtSession.disconnectUcloudSocket(slot)
    }
    /*连接ucloud socket*/
    fun connectSocket(slot:Int){
        rmtSession.connectUcloudSocket(slot)
    }
    fun getSocketDisConnectStatus():Boolean?{
        JLog.logd("ucloudlink rmtSession.getSocketDisConnectStatus")
        return rmtSession.getSocketDisConnectStatus()
    }
    /*ucloud 通道测试命令*/
    fun setNetworkPref(slot:Int,network_pref:String) {
        JLog.logd("ucloudlink rmtSession.setNetworkPref")
        rmtSession.setNetworkPref(slot,network_pref)
    }
    /*Get mcfg refresh value*/
    fun getMcfgRefreshValue(slot:Int){
        JLog.logd("ucloudlink rmtSession.getMcfgRefreshValue")
        rmtSession.getMcfgRefreshValue(slot)
    }
    /*传送PLMN LIST到MODEM*/
    fun sendPlmnListBinToModem(rmtEvent: UcloudServiceEvent){
        JLog.logd("ucloudlink sendPlmnListBinToModem")
        rmtSession.sendPlmnListBinToModem(rmtEvent)
    }

//    fun getStatePersentOb(): Observable<Int> {
//        return this.staRefreshOb
//    }
}