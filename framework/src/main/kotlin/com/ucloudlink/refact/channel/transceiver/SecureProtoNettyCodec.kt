package com.ucloudlink.refact.channel.transceiver

import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logi
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.transceiver.protobuf.*
import com.ucloudlink.refact.channel.transceiver.secure.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec


/**
 * Created by yongbin.qin on 2016/11/30.
 */
class SecureProtoNettyCodec :  ByteToMessageCodec<Packet>(){
    val securePacktMinLenght: Int =  12  //完整安全回复包最小长度
    val protoPacketMinLenght: Int = 10   //完整proto回复包最小长度
    val UPLOAD_FLOW_Period = 2*60*1000L //单位毫秒(2分钟)
    override fun decode(ctx: ChannelHandlerContext, inputBuf: ByteBuf, out: MutableList<Any>) {
        if (!SecureUtil.useSecureChanel){
            val packet = decodeProtobufPacket(inputBuf)
            if (packet != null){
                out.add(packet)
            }
        } else{
            val securePacket = decodeSecurePacket(inputBuf)
            if (securePacket == null){
                return
            }
            //logd("SecureProtoNettyCodec decode securePacket: ${securePacket}")
            //和业务相关的ProtoPacket数据包
            if (securePacket.cmd == CMD_BUSYNESS_PACKAGE_RESP || securePacket.cmd == CMD_BUSYNESS_PACKAGE_RECONN_RESP){
                //JLog.logd("SecureUtil reconnect securePacket.cmd=${securePacket.cmd}")

                val timeNow = System.currentTimeMillis()
                SecureUtil.updateDelayTimeToChangeKS(timeNow)//
                if(timeNow- SecureUtil.timeToKickOfChangeKS > UPLOAD_FLOW_Period) {
                    //JLog.logd("连接时间超过流量上传的定期时间，重新计算更新密钥的时间")
                    SecureUtil.startChangeAKTask()
                }
                if (SecureUtil.reconFlag == SecureUtil.RECONNECT_BEGIN){
                    SecureUtil.reconFlag = SecureUtil.RECONNECT_RESET
                    SecureUtil.trySecureReconnectCounter = 0
                    JLog.logd("SecureUtil reconnect reconFlag reset")
                }
                if (securePacket.body != null){
                    //解密
                    var data = SecureUtil.decrypt(securePacket.body, SecureUtil.ks)
                    if (data == null){ //解密失败，使用旧密钥再解一遍
                        data = SecureUtil.decrypt(securePacket.body, SecureUtil.oldKs)
                        if (data == null) { //解密失败，使用旧密钥再解一遍
                            //加日志
                            JLog.loge("busyness package resp decode faild")
                        }
                    }
                    if (data != null && data.size >= protoPacketMinLenght){//完整的ProtoPacket数据包长度最小为20字节长度
                        val protoInputBuf = Unpooled.buffer(data.size)
                        protoInputBuf.writeBytes(data)
                        val packet = decodeProtobufPacket(protoInputBuf)
                        if (packet != null){
                            out.add(packet)
                            return
                        }
                    }
                } else{
                    JLog.loge("securePacket.body == null")
                }
            } else{  //和安全通道相关的SecurePacket数据包
                out.add(securePacket)
            }
        }
    }

    /**
     * 解码加密数据包
     */
    fun decodeSecurePacket(inputBuf: ByteBuf): SecurePacketResp?{
        //logd("SecureProtoNettyCodec inputBuf.readableBytes() : ${inputBuf.readableBytes()}")
        //最小的包长度12
        if (inputBuf.readableBytes() < securePacktMinLenght) {
            return null
        }
        inputBuf.markReaderIndex()

        val st = inputBuf.readShort()
        if(st != SEC_HEAD.toShort() && st != OLD_SEC_HEAD.toShort()){
            inputBuf.resetReaderIndex()
            return null
        }

        //val cmd = inputBuf.readShort()
        val cmdArray = inputBuf.readBytes(2)
        val cmd = HexTool.hex2short(cmdArray.array().reversedArray()).toShort()

        val bodyArray =  inputBuf.readBytes(4)
        val bodyLength = HexTool.unsigned4BytesToInt(bodyArray.array().reversedArray(),0).toInt()
        val randNumArray =  inputBuf.readBytes(4)
        val randNum = HexTool.unsigned4BytesToInt(randNumArray.array().reversedArray(),0).toInt()
        if (inputBuf.readableBytes() < bodyLength) {
            inputBuf.resetReaderIndex()
            return null
        }
        val body = inputBuf.readBytes(bodyLength).array()
        return SecurePacketResp(st, cmd,bodyLength, randNum, body)
    }

    /**
     * 解码protobuf数据包
     */
    fun decodeProtobufPacket(inputBuf: ByteBuf): Packet? {

        if (inputBuf.readableBytes() < protoPacketMinLenght) {
            return null
        }
        inputBuf.markReaderIndex()
        val flag = inputBuf.readShort()
        if(flag != ProtoPacketUtil.START_TAG){
            inputBuf.resetReaderIndex()
            return null
        }
        val cmd = inputBuf.readInt()
        val dataLength = inputBuf.readShort()

        if (dataLength < 0 ) {
            inputBuf.resetReaderIndex()
            return null
        }
        val sn = inputBuf.readShort()
        if (sn < 0 ) {
        }

//        val timeStamp = inputBuf.readInt()
//        if (timeStamp < 0 ) {
//            throw(ProtocolException("Invalid packet timestamp: " + timeStamp))
//        }
//        val reserved = inputBuf.readShort()
        if (inputBuf.readableBytes() < dataLength){
            inputBuf.resetReaderIndex()
            return null
        }
        val decoded = inputBuf.readBytes(dataLength.toInt()).array()

//        if (inputBuf.readableBytes() < 4){
//            inputBuf.resetReaderIndex()
//            return null
//        }
//        val check = inputBuf.readInt()
        val packet = ProtoPacket(
                startTag = flag,
                cmd = cmd,
                sn = sn,
//                timestamp = timeStamp,
                data = decoded,
                length = dataLength
//                ,
//                reserved = reserved,
//                check = check
        )
        return packet
    }

    /**
     * 编码
     * @param packet 如果packet 是 SecurePacket类型，数据已加密过，如果是ProtoPacket，需要把ProtoPacket转换为ByteArray整个加密，
     */
    override fun encode(ctx: ChannelHandlerContext, packet: Packet, out: ByteBuf) {

        if (packet is ProtoPacket){
            logd("encoded packet :${packet.cmd},${packet.sn}")
            val packetOut = Unpooled.buffer(protoPacketMinLenght+packet.length)
            packetOut.writeShort(packet.startTag.toInt())
            packetOut.writeInt(packet.cmd)
            packetOut.writeShort(packet.length.toInt())
            packetOut.writeShort(packet.sn.toInt())
//            packetOut.writeInt(packet.timestamp)
//            packetOut.writeShort(packet.reserved.toInt())
            packetOut.writeBytes(packet.data)
//            packetOut.writeInt(packet.check)

            if (!SecureUtil.useSecureChanel){
                out.writeBytes(packetOut.array())
                //logd("use not secure channel")
                return
            }
            //logd("encoded 加密前 packetOut.array().size: ${packetOut.array().size}")
            //logd("encoded 加密前 packetOut:  ${HexTool.bytes2HexString(packetOut.array())}")
            //加密
            val bodyData = SecureUtil.encrypt(packetOut.array(), SecureUtil.ks)
            //logd("encoded bodyData 加密后 bodyData:  ${bodyData}")
            //logd("encoded bodyData 加密后 bodyData.size :  ${bodyData.size}")
            //logd("encoded out.readableBytes() :  ${out.readableBytes()}")
            //logd("encoded out.array().lenght :  ${HexTool.bytes2HexString(out.array()).length}")
            //logd("encoded out.array() :  ${HexTool.bytes2HexString(out.array())}")
            if (SecureUtil.reconnectable ){

                val securePacket = SecureMessagePacker.createBusinessPrimaryPacket(Configuration.getImei(ServiceManager.appContext),bodyData, SecureUtil.randNum).payload as PrimarySecurePacket
                //JLog.logd("SecureUtil reconnect 发送重连包 $securePacket")
                out.writeBytes(HexTool.shortToByteArray(securePacket.st))
                out.writeBytes(HexTool.shortToByteArray(securePacket.cmd))
                out.writeBytes(HexTool.intToByteArray(securePacket.bodyLength))
                out.writeBytes(securePacket.deviceId.toByteArray())
                out.writeByte(securePacket.deviceType.toInt())
                out.writeBytes(HexTool.intToByteArray(securePacket.randNum).reversedArray())
                out.writeBytes(securePacket.body)
                //JLog.logd("SecureUtil reconnect out =  ${HexTool.bytes2HexString(out.array())}")
                if(SecureUtil.currSecuConnetcStatus!=2)
                    SecureUtil.currSecuConnetcStatus = 2

            } else if((SecureUtil.currSecuConnetcStatus >0 ) && (SecureUtil.reconFlag == SecureUtil.RECONNECT_RESET)){
                val securePacket = SecureMessagePacker.createBusinessPacket(bodyData, SecureUtil.randNum).payload as SecondarySecurePacket
                //logd("encoded packet is ProtoPacket : ${securePacket}")
                /*  out.writeShort(securePacket.st.toInt())
                out.writeShort(securePacket.cmd.toInt())
                out.writeInt(securePacket.bodyLength)
                out.writeInt(securePacket.randNum)*/
                out.writeBytes(HexTool.shortToByteArray(securePacket.st))
                out.writeBytes(HexTool.shortToByteArray(securePacket.cmd))
                out.writeBytes(HexTool.intToByteArray(securePacket.bodyLength))
                out.writeBytes(HexTool.intToByteArray(securePacket.randNum).reversedArray())
                out.writeBytes(securePacket.body)
                //logd("encoded 加密后 securePacket: $securePacket")
                //logd("encoded 加密后 out.array().size: ${out.array().size}")
                //logd("encoded 加密后 out:  ${HexTool.bytes2HexString(out.array())}")
                //logd("encoded 添加数据后 out.readableBytes() :  ${out.readableBytes()}")
                if(SecureUtil.currSecuConnetcStatus!=2)
                    SecureUtil.currSecuConnetcStatus = 2
            } else {
                logd("Should not send any data now")

            }

        } else if (packet is SecurePacket){
            //logd("encoded packet is SecurePacket : ${packet}")
            //如果是主命令需要deviceId和deviceType
            //if (packet.cmd == CMD_GETLIB_REQ || packet.cmd == CMD_GETCMD_REQ || packet.cmd == CMD_SEC_CONNECT_REQ){
            if (packet is PrimarySecurePacket){
                out.writeBytes(HexTool.shortToByteArray(packet.st))
                //out.writeShort(packet.st.toInt())
                //out.writeShort(0xFAFA) //SEC_HEAD(0xFAFA)超出Short类型范围，在此写入out
                out.writeBytes(HexTool.shortToByteArray(packet.cmd))
                out.writeBytes(HexTool.intToByteArray(packet.bodyLength))
                //out.writeInt(packet.bodyLength)
                out.writeBytes(packet.deviceId.toByteArray())
                out.writeByte(packet.deviceType.toInt())
                //out.writeInt(packet.randNum)
                out.writeBytes(HexTool.intToByteArray(packet.randNum).reversedArray())
                out.writeBytes(packet.body)
            } else if (packet is SecondarySecurePacket){
                out.writeBytes(HexTool.shortToByteArray(packet.st))
                //out.writeShort(packet.st.toInt())
                //out.writeShort(SEC_HEAD) //SEC_HEAD(0xFAFA)超出Short类型范围，在此写入out
                out.writeBytes(HexTool.shortToByteArray(packet.cmd))
                out.writeBytes(HexTool.intToByteArray(packet.bodyLength))
                //out.writeInt(packet.bodyLength)
                out.writeBytes(HexTool.intToByteArray(packet.randNum).reversedArray())
                //out.writeInt(packet.randNum)
                out.writeBytes(packet.body)
            }
        }
    }
}