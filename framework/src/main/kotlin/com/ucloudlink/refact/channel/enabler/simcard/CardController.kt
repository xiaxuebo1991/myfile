package com.ucloudlink.refact.channel.enabler.simcard

import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.utils.JLog.logd
import rx.Observable

/**
 * 卡控制器：
 * 软卡和物理卡底层接口
 * 实现卡插入，拔出，启动，停止操作
 */

@Deprecated("use new CardController") 
object CardController {
    val rmtSession: RemoteUimSession = RemoteUimSession(ServiceManager.appContext)// wlmark use rmtSession in here?

    init {
        rmtSession.bindService()
    }

    fun disconnectSocket(slot:Int){
        rmtSession.disconnectUIMSocket(slot)
    } 
    
    fun connectSocket(slot:Int){
        rmtSession.connectUIMSocket(slot)
    }
    
    fun insertCard(card: Card): Unit {
        rmtSession.insertCard(card)
    }

    fun enableCard(card: Card): Observable<Any> {
        throw UnsupportedOperationException()
    }

    fun powerUpCard(card: Card) {
        rmtSession.powerUpCard(card)
    }

    fun powerDownCard(card: Card) {
        rmtSession.powerDownCard(card)
    }

    fun removeCard(card: Card) {
        rmtSession.removeCard(card)
    }

//    fun unavailableSoftCard(){
//        rmtSession.unavailableSoftCard()
//    }
    fun cardInserted(slot: Int) {
        rmtSession.cardInserted(slot)
    }
    fun diconnectCard(slot: Int) {
        rmtSession.cardDisconnect(slot)
    }


    fun cloudSimStateObservable() = rmtSession.cloudSimStateObservable()

//    fun removeCardForSwap(card: Card) {
//        logd(" rmtSession.removeCardForSwap:$card")
//        rmtSession.removeCardForSwap(card)
//    }

    fun insertCardForSwap(card: Card) {
        logd(" rmtSession.insertCardForSwap:$card")
        rmtSession.insertCardForSwap(card)
    }

    fun setCardUplmn(card: Card): Int {
        logd("rmtSession.setCardUplmn:$card, ${card.eplmnlist}")
        return rmtSession.setCardEplmnList(card)
    }

    fun isReady(): Boolean {
        return rmtSession.isReady()
    }

    fun disconnectAllSim() {
        logd(" rmtSession.disconnect two remote sim")
        rmtSession.disconnectAllSim()
    }
}