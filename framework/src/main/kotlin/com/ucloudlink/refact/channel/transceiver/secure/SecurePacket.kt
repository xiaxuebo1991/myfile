package com.ucloudlink.refact.channel.transceiver.secure

/**
 * Created by yongbin.qin on 2016/11/30.
 */

interface SecPacket {
    var st: Short
    var cmdId: Short
    var bodyLen:Int
    var randNum: Int
}

/**
 * 主命令请求
 */
data class MainCMDReq(
        override  var st: Short = 0,
        override var cmdId: Short = 0,
        override var bodyLen:Int = 0 ,
        var deviceId: String = "",
        var deviceYype: Byte = 0 ,
        override var randNum: Int = 0

):SecPacket

/**
 * 主命令返回值
 */
data class MainCMDResp(
        override  var st: Short = 0,
        override var cmdId: Short = 0,
        override var bodyLen:Int = 0 ,
        var deviceId: String = "",
        var deviceYype: Byte = 0 ,
        override var randNum: Int = 0,
        var data: ByteArray? = null
): SecPacket

data class SecPacket2(
        var st:Short = 0 ,
        var cmd_id:Short = 0 ,
        var body_len:Int = 0 ,
        var device_id: String = "",
        var device_type: Byte = 0 ,
        var rand_num: Int = 0 ,
        var data: ByteArray = "".toByteArray()
)