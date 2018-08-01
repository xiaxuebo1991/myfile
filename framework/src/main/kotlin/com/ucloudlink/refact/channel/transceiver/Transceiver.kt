package com.ucloudlink.refact.channel.transceiver
import com.ucloudlink.refact.business.routetable.ServerRouter.Dest
import com.ucloudlink.refact.channel.transceiver.protobuf.Message
import rx.Observable

/**
 * 传输通道
 * 负责建立和维护后台服务器的连接
 * 收发数据包
 */
const val REASON_USER_ENABLED="UserEnabled"
interface Transceiver {
//    fun available(dest: Dest):Boolean
//    fun open(dest: Dest): Observable<Any>
    
    
    fun send(packet: Message)
    fun receive(dest: Dest): Observable<Message>
    fun disconnect(dest: Dest) // wlmark do you need add close method?

    fun setNeedSocketConnect(dest: Dest, reason: String)

    fun setForbidSocketConnect(dest: Dest, reason: String)

    fun statusObservable(dest: Dest): Observable<String>

    fun exceptionStatusObservable(dest: Dest): Observable<Throwable>
    
    fun isSocketConnected(dest: Dest):Boolean
}