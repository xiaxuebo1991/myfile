package com.ucloudlink.refact.channel.transceiver.protobuf

import com.ucloudlink.refact.business.routetable.ServerRouter


/**
 * Created by cthun on 2016/6/24.
 */

enum class Priority{
        ALWAYS_USER_CHANNEL,//总是使用用户通道
        ALWAYS_SEED_CHANNEL,//总是使用种子通道
        TRY_USER_CHANNEL,//优先尝试使用用户通道
}

data class ProtoPacket(
        var startTag: Short = 0,

        var cmd: Int = 0,

        var length: Short = 0,

        var sn: Short = 0,

//        var timestamp: Int = (System.currentTimeMillis()/1000).toInt(),

//        var reserved: Short = 0,

//        var check: Int = 0,

        var data: ByteArray? = null
        ) : Packet

/**
 *加密数据包
 */
interface SecurePacket  : Packet{
         var st: Short
        var cmd:Short
        var bodyLength:Int
        var randNum: Int
        var body: ByteArray
}



/**
 * 主命令请求包
 */
data class PrimarySecurePacket (
        override var st:Short = 0 ,
        override var cmd:Short = 0 ,
        override var bodyLength:Int = 0,
        var deviceId: String  = "",   //IMEI+5个空格，总共20个字符长度
        var deviceType: Byte = 0,
        override var randNum: Int = 0,
        override var body: ByteArray = "".toByteArray()
) : SecurePacket


/**
 * 次命令请求包
 */
data class SecondarySecurePacket (
        override var st:Short = 0 ,
        override var cmd:Short = 0 ,
        override var bodyLength:Int = 0,
        override var randNum: Int = 0,
        override var body: ByteArray = "".toByteArray()
) : SecurePacket

/**
 * 主次命令回复包，如果bodyLength == 0，则没有body变量
 */
data class SecurePacketResp(
        override var st:Short = 0 ,
        override var cmd:Short = 0 ,
        override var bodyLength:Int = 0,
        override var randNum: Int = 0,
        override var body: ByteArray = "".toByteArray()
) : SecurePacket

interface Packet{
}

/*interface Packet{
        var sn: Short
}*/

data class Message(val id:Short,
                   val dest: ServerRouter.Dest = ServerRouter.Dest.ASS,
                   val priority: Priority = Priority.ALWAYS_SEED_CHANNEL,
                   val payload: Packet)