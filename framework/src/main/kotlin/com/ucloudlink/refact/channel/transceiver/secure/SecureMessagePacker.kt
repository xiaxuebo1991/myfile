package com.ucloudlink.refact.channel.transceiver.secure

import com.ucloudlink.refact.channel.transceiver.protobuf.*
import com.ucloudlink.refact.business.routetable.ServerRouter

/**
 * Created by yongbin.qin on 2016/12/3.
 */

object SecureMessagePacker{
    //GETLIB主命令
    fun createGetLibPacket(imei: String, randNum:Int): Message {
        //val packet = createPrimarySecureSecurePacket(CMD_SEC_CONNECT_REQ, imei, randNum)
        val packet = PrimarySecurePacket(st = SEC_HEAD.toShort(),// SEC_HEAD（0xFAFA）超出Short类型范围，SecurePacket包类型在编码时直接packetOut.writeShort(SEC_HEAD)
                cmd = CMD_GETLIB_REQ,
                deviceId=imei+"                 ",
                deviceType = devType,
                randNum =randNum )
        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    /**
     * GETPK8次命令
     */
    fun createGetPK8(randNum:Int, body: ByteArray): Message {
        val packet = SecondarySecurePacket(st = SEC_HEAD.toShort(),
                                            cmd = CMD_GETPK8_REQ,
                                            bodyLength = body.size,
                                            randNum =randNum,
                                            body = body)
        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    //GETCMD主命令
    fun createGetCmdPacket(imei: String, randNum:Int, body:ByteArray): Message {
       // val packet = createPrimarySecureSecurePacket(CMD_GETCMD_REQ, imei, randNum)
        val packet = PrimarySecurePacket(st = SEC_HEAD.toShort(),// SEC_HEAD（0xFAFA）超出Short类型范围，SecurePacket包类型在编码时直接packetOut.writeShort(SEC_HEAD)
                cmd = CMD_GETCMD_REQ,
                deviceId=imei+"                 ",
                deviceType = devType,
                randNum =randNum,
                bodyLength = body.size,
                body = body)
        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    //GETDATA次命令
    fun createGetData(randNum:Int, body: ByteArray): Message {
        val packet = SecondarySecurePacket(st = SEC_HEAD.toShort(),
                cmd = CMD_GETDATA_REQ,
                bodyLength = body.size,
                randNum =randNum,
                body = body)

        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    /**创建安全连接包
     * @param body
     * @param 随机数  SecureUtil.randNum
     */
    fun createSecureConnectPacket(imei: String,body: ByteArray, randNum:Int): Message {
        val packet = PrimarySecurePacket(st = SEC_HEAD.toShort(),cmd = CMD_SEC_CONNECT_REQ,deviceId=imei+"                 ",deviceType = devType,randNum =randNum,bodyLength = body.size, body = body)
        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    /**BUSYNESS_PACKET次命令，所以使用protobuf协议相关的业务包都使用该方法加密打包
     * @param protoBody protoPacket包的字节数组（已加密的）
     * @param 随机数  SecureUtil.randNum
     */
    fun createBusinessPacket(protoBody: ByteArray, randNum:Int): Message {
        val packet = SecondarySecurePacket(st = SEC_HEAD.toShort(),cmd = CMD_BUSYNESS_PACKAGE_REQ,randNum =randNum,bodyLength = protoBody.size, body = protoBody)
        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    /**安全通过认证后，发的第一个业务包使用此方法封装，为了传IEMI给服务器做sessionID判断，之后使用createBusinessPacket(protoBody: ByteArray, randNum:Int): Message
     * @param imei  SecureUtil.imei
     * @param protoBody protoPacket包的字节数组（已加密的）
     * @param randNum 随机数  SecureUtil.randNum
     */
    fun createBusinessPrimaryPacket(imei: String,protoBody: ByteArray, randNum:Int): Message {
        val packet =  PrimarySecurePacket(st = SEC_HEAD.toShort(),// SEC_HEAD（0xFAFA）超出Short类型范围，SecurePacket包类型在编码时直接packetOut.writeShort(SEC_HEAD)
                cmd = CMD_BUSYNESS_PACKAGE_RECONN_REQ,
                deviceId=imei+"                 ",
                deviceType = devType,
                randNum =randNum,
                bodyLength = protoBody.size,
                body = protoBody)

        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    //CHANGE_KS次命令
    fun createChangeKSPacket(randNum:Int, body:ByteArray): Message {
        val packet = SecondarySecurePacket(st = SEC_HEAD.toShort(),cmd = CMD_CHANGE_KS_REQ,randNum =randNum,bodyLength = body.size, body = body)
        val msg = Message(id = packet.cmd,
                dest = ServerRouter.Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }
}
