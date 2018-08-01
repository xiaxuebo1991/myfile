package com.ucloudlink.refact.channel.transceiver.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ucloudlink.framework.remoteuim.LibDyn
import com.ucloudlink.framework.remoteuim.LibUc
import com.ucloudlink.framework.remoteuim.LibUcsslSignNative
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.transceiver.NettyTransceiver
import com.ucloudlink.refact.channel.transceiver.protobuf.SecurePacketResp
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.EncryptUtils
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.MemUtil
import okio.Okio
import java.io.File
import java.security.KeyStore
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.properties.Delegates

const val     OLD_SEC_HEAD:Int = 0xFAFA  //包头标志位
const val     SEC_HEAD:Int = 0xFBFB  //包头标志位
const val     devType:Byte = 102 //设备类型

//===============主命令===================
const val     CMD_GETLIB_REQ:Short = 0x0011
const val     CMD_GETLIB_RESP:Short = 0x1180

const val     CMD_GETCMD_REQ:Short = 0x0013
const val     CMD_GETCMD_RESP:Short = 0x1380

const val     CMD_SEC_CONNECT_REQ :Short= 0x0002
const val     CMD_SEC_CONNECT_RESP :Short= 0x0280


//===============次命令=======================
const val     CMD_GETPK8_REQ:Short = 0x0012
const val     CMD_GETPK8_RESP:Short = 0x1280

const val     CMD_GETDATA_REQ:Short = 0x0014
const val     CMD_GETDATA_RESP:Short = 0x1480

const val     CMD_BUSYNESS_PACKAGE_REQ :Short= 0x0003
const val     CMD_BUSYNESS_PACKAGE_RESP :Short= 0x0380

const val     CMD_BUSYNESS_PACKAGE_RECONN_REQ :Short= 0x0015
const val     CMD_BUSYNESS_PACKAGE_RECONN_RESP :Short= 0x1580


const val     CMD_CHANGE_KS_REQ:Short = 0x0004
const val     CMD_CHANGE_KS_RESP:Short = 0x0480

const val     ERROR_7015 :Short = 0x1570

//连接socket后，服务器会立刻返回一个带此命令值的包
const val     CMD_GET_RAND_NUM:Short = 0x0180

const val     CMD_ERR_RESP_MASK:Short = 0x007f
const val     CMD_GETCMD_DECRYPT_ERR_RESP:Short = 0x1371
const val     CMD_SECURE_AUTH_ERR_RESP:Short = 0x0270


const val     COMMON_LIB_FILE:String = "libdyn-common.so"
const val     DYN_LIB_FILE:String = "libdyn.so"
const val     CLIENT_KEY_FILE:String = "client.key"
const val     MD5_FILE_DATA:String = "filedata"
const val     SERVER_CRT_FILE:String = "server.crt"
const val     HELLO_MSG:String = "hello server"
const val     PSW_OF_CLIKEY_KEY:String = "123456"
const val     CLIENT_DATA_LEN:Int = 1024
const val     CUSTOM_CMD_LEN:Int = 48
const val     CUSTOM_CMDOUT_LEN:Int = 32
const val     SESSION_KEY_LEN:Int = 32
/**
 * Created by yongbin.qin on 2016/12/1.
 */
object SecureUtil{
    val useSecureChanel = true  //true：启用安全通道，false：关闭安全通道
//    val useSecureChanel = false  //true：启用安全通道，false：关闭安全通道
    var context: Context by Delegates.notNull()
    var tmpAK = ByteArray(32)  //鉴权阶段的临时密钥
    //var ak: ByteArray = ByteArray(32)  //服务器为每个终端激活时产生的随机数第33~64字节
    //var ik: ByteArray = ByteArray(32)  //服务器为每个终端激活时产生的随机数第1~32字节
    var ks: ByteArray = ByteArray(32)   //GETDATA命令或者CHANGE_KS命令成功后的解密加密密钥
    var oldKs: ByteArray = ByteArray(32) //
    var newKs: ByteArray = ByteArray(32)
    var randNum: Int = 0
    var isDeviceActivatd:Boolean = false //设备是否已经激活激活   true:成功； false：失败
    var isGetDataSuccessful = false  // GETDATA是否已经成功 true:成功； false：失败
    var transceiver: NettyTransceiver by Delegates.notNull()
    const val     RECONNECT_RESET = 0
    const val     RECONNECT_BEGIN = 1
    const val     RECONNECT_ERROR = 2
    var reconFlag = RECONNECT_RESET // 0:初始状态/已收到重连包回复；1：需要发送包含业务的重连包；2：;3: 收到重连失败的消息

    const val     SECURE_AUTH_UNKNOWN = 0
    const val     SECURE_AUTH_BEGIN   = 1
    const val     SECURE_AUTH_OK   = 2
    const val     SECURE_AUTH_FAIL= 3
    //标示是否经过签名认证
    var currSecuAuthStatus = SECURE_AUTH_UNKNOWN //安全连接错误
    //标示业务数据是否可发
    var currSecuConnetcStatus = 0

    var timer:Timer = Timer()
//    var delayTimeToChangeKS = 30*1000L //单位毫秒(30分钟)
//    val changeKSPeriod = 30*1000L //单位毫秒(30分钟)
    var delayTimeToChangeKS = 60*60*1000L //单位毫秒(60分钟)
   val changeKSPeriod = 60*60*1000L //单位毫秒(60分钟)

    var timeToKickOfChangeKS:Long = 0
    //val delaytime = 1000*60*2L //单位毫秒( ) TODO测试
    var changeAKTask: ChangeAKTask? = null
    //TODO:加入快速重连之后，需要适时才运行这个task！！！因为快速重连直接承载业务，还要在重连失败时重启超时task
    var oneSecureStepTimeoutTask: OneSecureStepTimeoutTask? = null
    var isLoaded = false
    //安全连接连续尝试次数
    var trySecureReconnectTimeoutCounter = 0
    //快速重连业务包中断计数
    var trySecureReconnectCounter = 0

    var mDest: ServerRouter.Dest = ServerRouter.Dest.ASS
    //安全连接超时时间
    var mStore:KeyStore ?= null
    //
    var secureTimeoutTime =  45*1000L//尝试重连等待时间,单位毫秒
    /*    get() {
            if(trySecureReconnectCounter>5)
                trySecureReconnectCounter = 5
            return 30*1000L + trySecureReconnectCounter*10*1000L
        }*/
    //安全连接连续收到的服务器安全错误次数
    var recvSecureErrorCounter = 0

    var reconnectable =false
        get(){
            logi("reconnectable ${currSecuAuthStatus},${reconFlag},${recvSecureErrorCounter}")
            if((currSecuAuthStatus == SECURE_AUTH_OK) &&(reconFlag == RECONNECT_BEGIN) && (recvSecureErrorCounter==0))
                return true
            return false
        }
    val secureTimeout = "secureTimeout" //安全连接错误

/*
 * Secure Connect Counter
 * SECURE_UTIL_UNKNOWN_STATE, unknow state
 * SECURE_UTIL_GETCMD_DECRYPT_ERROR, got a GETCMD decryption ERROR
 */
    private val SECURE_UTIL_UNKNOWN_STATE = 0
    private val SECURE_UTIL_GETCMD_DECRYPT_ERROR = -1
    var ucsslConnectCounter = SECURE_UTIL_UNKNOWN_STATE
    
    var secureErrorCmd:Short = 0 //最新的安全错误码
    /**
     * 初始化SecureUtil，在程序初始化时调用
     */
    fun init(context: Context, transceiver: NettyTransceiver){
        this.context = context
        isDeviceActivatd = checkDeviceActiviStatus()
        this.transceiver = transceiver
    }

    /**
     * 处理和通道相关的SecurePacket(
     * @param securePacket 未解密的数据包
     *
     */
    var activeMd5:ByteArray =ByteArray(40)
    fun handlerSecurePacket(securePacket: SecurePacketResp){
        logi("handlerSecurePacket threadName : ${Thread.currentThread().name},${securePacket}")
        secureErrorCmd = securePacket.cmd
        if (oneSecureStepTimeoutTask != null) {
            oneSecureStepTimeoutTask!!.cancel()
        }
        if((securePacket.cmd.toInt() and CMD_ERR_RESP_MASK.toInt())!=0)
        {
            JLog.logk("handlerSecurePacket error :${securePacket.cmd}")
            if(securePacket.cmd == CMD_GETCMD_DECRYPT_ERR_RESP){
                if(ucsslConnectCounter != SECURE_UTIL_GETCMD_DECRYPT_ERROR){
                    ucsslConnectCounter = SECURE_UTIL_GETCMD_DECRYPT_ERROR
                    renameFiles(false)
                    JLog.logk("need to restart app now")
                    //Toast.makeText(context,"uCloud正在重启...",Toast.LENGTH_LONG)
                    System.exit(1)
                }else{
                    JLog.logk("the app didn't exit?Exit.")
                    System.exit(1)
                }
            }
            else{
                renameFiles(true)
            }
        }

        when(securePacket.cmd){
            CMD_GET_RAND_NUM->{
                logd("handlerSecurePacket $ucsslConnectCounter,$currSecuAuthStatus,$reconFlag,$trySecureReconnectTimeoutCounter")
                logd("handlerSecurePacket $isLoaded,$isGetDataSuccessful,$isDeviceActivatd,$isLoaded")
                randNum = securePacket.randNum
                if (!isDeviceActivatd){ //设备未激活
                    logi("handlerSecurePacket GetLib request"+ServiceManager.systemApi.getSSLVersion())
                    transceiver?.send(SecureMessagePacker.createGetLibPacket(Configuration.getImei(ServiceManager.appContext), ServiceManager.systemApi.getSSLVersion()))
                    oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                    timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)
                } else if (!isGetDataSuccessful){//设备已激活,getDa ta命令未验证通过
                    if(!isLoaded){
                        //System.load(Configuration.simDataDir+DYN_LIB_FILE)
                        val libdynFile = File(Configuration.simDataDir+COMMON_LIB_FILE)
                        val patchFileData = libdynFile.readBytes()
                        isLoaded = (0 == LibUc.useLibDyn(DYN_LIB_FILE,patchFileData))
                    }
                    var clientData = ByteArray(CLIENT_DATA_LEN)
                    var ret = LibDyn.getClientInfo(context, clientData)
                    if(ret <= 0 ){
                        logd("getClientInfod failed,${ret}")
                        return
                    }
                    logd("getClientInfo java done ${ret}")
                    LibDyn.getDummy(randNum,tmpAK)
                    val encryptClientData  = encrypt(clientData.copyOf(ret),tmpAK) //加密数据
                    val msg = SecureMessagePacker.createGetCmdPacket(Configuration.getImei(ServiceManager.appContext), randNum, encryptClientData)
                    logd("handlerSecurePacket GETCMD request....")
                    transceiver?.send(msg)
                    oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                    timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)
                }
                else if((currSecuAuthStatus != SECURE_AUTH_OK) || (reconFlag == RECONNECT_ERROR)) {
                    var out_msg = ByteArray(CLIENT_DATA_LEN)
                    var out_ks = ByteArray(SESSION_KEY_LEN)
                    var reqlen = LibUcsslSignNative.createConnectRequest(Configuration.getImei(ServiceManager.appContext) + "     ",
                            HELLO_MSG.toByteArray(), Configuration.simDataDir + SERVER_CRT_FILE,
                            Configuration.simDataDir + CLIENT_KEY_FILE, PSW_OF_CLIKEY_KEY, out_msg, out_ks)
                    if (reqlen <= 0) {
                        logd("createConnectRequest failed,${reqlen}")
                        return
                    }
                    oldKs = ks
                    ks = out_ks
                    logd("handlerSecurePacket CMD_GET_RAND_NUM ks:$ks")
                    val newOutMsg = out_msg.copyOf(reqlen)
                    val newRandNum = HexTool.unsigned4BytesToInt(HexTool.intToByteArray(randNum), 0).toInt()

                    currSecuAuthStatus = SECURE_AUTH_BEGIN
                    val msg = SecureMessagePacker.createSecureConnectPacket(Configuration.getImei(ServiceManager.appContext), newOutMsg, newRandNum)
                    logd("handlerSecurePacket CMD_GET_RAND_NUM SECURE Connect request....")
                    transceiver?.send(msg)
                    oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                    timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)
                }
                else if(reconFlag==RECONNECT_BEGIN){
                        logd("handlerSecurePacket receive reconnect random:${randNum}")
                }
            }
            CMD_GETLIB_RESP->{
                logd("handlerSecurePacket GetLib response")
                LibUcsslSignNative.getDummy(randNum,tmpAK)  //获取鉴权阶段的临时密钥
                var decryptData = decrypt(securePacket.body,tmpAK)  //解密数据
                if (decryptData == null){
                    logd("handlerSecurePacket decrypt GETLIB response falil")
                    return
                }
                /*
                1.updateJniLib() don't merge patch data into libdata. just output all patch data
                2.use token to encrypt the patch data and save as FILE
                3.use token to decrypt patch data and pass into LibUc.useLibDyn()
                4.LibUc.useLibDyn() replaces the __sb and __sc section

                5.
                 */






                if(!isLoaded){
                    //open common lib
//                    val libFile = File(Configuration.simDataDir+COMMON_LIB_FILE)
//                    if(!libFile.exists()){
//                        logd("libFile not exist")
//                        return
//                    }
                    //the extra size is for the hash of bothData
                    var patchFileData = ByteArray(decryptData.size+32)
                    System.arraycopy(decryptData,0,patchFileData,0,decryptData.size)
                    logd("server getlib decryptData ${HexTool.bytes2HexString(decryptData)}")
                    if(securePacket.bodyLength >0 ){
                        if(LibUcsslSignNative.updateLib(context,Base64.encodeToString(getToken(""), Base64.URL_SAFE),patchFileData,decryptData) != 0){
                            logd("updateLib failed")
                            return
                        }
                    }else{
                        logd("server response error ${securePacket.cmd}")
                        return
                    }
                    logd("java updateLib done ${HexTool.bytes2HexString(patchFileData)}")
                    val libdynFile = File(Configuration.simDataDir+COMMON_LIB_FILE)
                    libdynFile.writeBytes(patchFileData)

                    memcpy(EncryptUtils.getSHA1(patchFileData),activeMd5,0,20)

                    logd("useLibDyn getlib"+Configuration.simDataDir+DYN_LIB_FILE)
                    //System.load(Configuration.simDataDir+DYN_LIB_FILE)
                    val useLibDynRet = LibUc.useLibDyn(DYN_LIB_FILE,patchFileData)
                    isLoaded = (0 == useLibDynRet)
                    logd("useLibDyn getlib done useLibDynRet=$useLibDynRet")
                }else{
                    logd(Configuration.simDataDir+DYN_LIB_FILE+" has been loaded.")
                }
                if(!isLoaded){
                    logd("secure useLibDyn falied")
                    System.exit(0)
                }

                var clienData = ByteArray(CLIENT_DATA_LEN)
                var ret = LibDyn.getClientInfo(context, clienData) //收集终端运行信息
                if(ret <= 0 ){
                    logd("getClientInfod failed,${ret}")
                    return
                }
                LibDyn.getDummy(randNum,tmpAK)
                val encryptClienData = encrypt(clienData.copyOf(ret),tmpAK) //加密数据
                logd("handlerSecurePacket  GETPK8 request....")
                transceiver?.send(SecureMessagePacker.createGetPK8(randNum,encryptClienData)) //GETPK8请求
                oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)
            }

            CMD_GETPK8_RESP->{  //GETPK8回复
                logd("handlerSecurePacket  GETPK8 response")
                var pk8res = decrypt(securePacket.body,tmpAK)
                if (pk8res == null){
                    logd("handlerSecurePacket decrypt GETPK8 response falil")
                    return
                }
                var cmd = pk8res.copyOfRange(0,CUSTOM_CMD_LEN)
                logd("cmd ${cmd.size} >>>>>${HexTool.bytes2HexString(cmd)}")
                var cmdOutput = ByteArray(CUSTOM_CMDOUT_LEN)
                if(LibDyn.runCmd(cmd,cmdOutput) <= 0){
                    logd("runCmd failed")
                    isDeviceActivatd = false
                    return
                }
                val clientKey = File(Configuration.simDataDir+CLIENT_KEY_FILE)
                val fileData = pk8res.copyOfRange(CUSTOM_CMD_LEN,pk8res.size)
                //logd("filedata ${pk8res.size} >>>>>${HexTool.bytes2HexString(fileData)}")
                clientKey.writeBytes(fileData)
                logd("GETDATA >>cmd-output before trim${cmdOutput.size}>${HexTool.bytes2HexString(cmdOutput)}")
                for (a in 0..cmdOutput.size-1){
                    if(cmdOutput[a] == 0x0a.toByte()) {
                        logd("GETDATA REPLACE")
                        cmdOutput[a] = 0
                        break
                    }
                }
                isDeviceActivatd = true

                memcpy(EncryptUtils.getSHA1(fileData),activeMd5,20,20)
                val md5FilData = File(Configuration.simDataDir+MD5_FILE_DATA)
                logd("md5FilData>>>>${HexTool.bytes2HexString(activeMd5)}")
                val sink = Okio.sink(md5FilData)
                val writer = Okio.buffer(sink)
                writer.write(activeMd5)
                writer.flush()

                logd("GETDATA >>cmd-output ${cmdOutput.size}>${HexTool.bytes2HexString(cmdOutput)}")
                val cmdBody = encrypt(cmdOutput,tmpAK) //加密cmd数据
                logd("handlerSecurePacket  GETDATA request...")
                transceiver?.send(SecureMessagePacker.createGetData(randNum, cmdBody))
                oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)
            }

            CMD_GETCMD_RESP->{
                logd("handlerSecurePacket  GETCMD response")
                var cmdOutput = ByteArray(CUSTOM_CMDOUT_LEN)
                var cmdRes = decrypt(securePacket.body,tmpAK)
                if (cmdRes == null){
                    logd("handlerSecurePacket decrypt GETCMD response falil")
                    return
                }
                var cmd = cmdRes.copyOfRange(0, CUSTOM_CMD_LEN)
                logd("cmd ${cmd.size} >>>>>${HexTool.bytes2HexString(cmd)}")
                if(LibDyn.runCmd(cmd,cmdOutput) <= 0){
                    logd("runCmd failed")
                    return
                }
                for (a in 0..cmdOutput.size-1){
                    if(cmdOutput[a] == 0x0a.toByte()) {
                        logd("GETDATA REPLACE")
                        cmdOutput[a] = 0
                        break
                    }
                }
                logd("GETDATA >>cmd-output ${cmdOutput.size}>${cmdOutput}")
                val encryptCmdOutput = encrypt(cmdOutput,tmpAK)
                val msg = SecureMessagePacker.createGetData( randNum, encryptCmdOutput)
                logd("handlerSecurePacket  GETDATA request....")
                transceiver?.send(msg)
                oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)
            }
            CMD_GETDATA_RESP->{
                logd("handlerSecurePacket  GETDATA response")
                var getDataRes = decrypt(securePacket.body,tmpAK)
                if (getDataRes == null){
                    logd("SecureUtil handlerSecurePacket decrypt GETCMD response falil")
                    return
                }
                logd("GETLIB->GETDATA response before getHashK")
                if(LibDyn.getHashK(getDataRes) < 0){
                    logd("GETDATA->getHashK failed")
                    isGetDataSuccessful = false
                    return
                }
                logd("GETLIB->GETDATA response done")
                isGetDataSuccessful = true
                //transceiver?.close()  //todo ,GETDATA完成关掉socket，待定
                var out_msg = ByteArray(CLIENT_DATA_LEN)
                var out_ks = ByteArray(SESSION_KEY_LEN)
                var reqlen = LibUcsslSignNative.createConnectRequest(Configuration.getImei(ServiceManager.appContext)+"     ",
                        HELLO_MSG.toByteArray(), Configuration.simDataDir+SERVER_CRT_FILE,
                        Configuration.simDataDir+CLIENT_KEY_FILE,PSW_OF_CLIKEY_KEY,out_msg,out_ks)
                if(reqlen <= 0){
                    logd("createConnectRequest failed,${reqlen}")
                    return
                }
                ks = out_ks
                logd("handlerSecurePacket CMD_GET_RAND_NUM ks:$ks")
                val newOutMsg = out_msg.copyOf(reqlen)
                val newRandNum = HexTool.unsigned4BytesToInt(HexTool.intToByteArray(randNum),0).toInt()
                currSecuAuthStatus = SECURE_AUTH_BEGIN
                val msg = SecureMessagePacker.createSecureConnectPacket(Configuration.getImei(ServiceManager.appContext),newOutMsg, newRandNum)
                logd("handlerSecurePacket  SECURE CONNECT request....")
                transceiver?.send(msg)
                oneSecureStepTimeoutTask = OneSecureStepTimeoutTask()
                timer.schedule(oneSecureStepTimeoutTask, secureTimeoutTime)

            }
            CMD_SEC_CONNECT_RESP->{
                logd("handlerSecurePacket  SESSION CONNECT respon")
                currSecuAuthStatus = SECURE_AUTH_OK
                if(ucsslConnectCounter>=0)
                    ucsslConnectCounter++
                //当成功连接上时，重置安全连接连续尝试次数
                trySecureReconnectTimeoutCounter = 0
                //当成功连接上时，重置快速重连业务包计数
                trySecureReconnectCounter = 0
                currSecuConnetcStatus = 1
                recvSecureErrorCounter = 0

                reconFlag = RECONNECT_RESET
                //初始化timeToKickOfChangeKS
                timeToKickOfChangeKS = System.currentTimeMillis()
                startChangeAKTask()

                transceiver?.channelStateObservable.onNext("SocketConnected")
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SOCKET_CONNECTED)

            }
            CMD_BUSYNESS_PACKAGE_RESP->{

            }
            CMD_CHANGE_KS_RESP->{
                oldKs = ks
                ks = newKs
                logi("handlerSecurePacket  changeKS OK!")
            }

            ERROR_7015 ->{
                JLog.logk("handlerSecurePacket ERROR_7015 = ${securePacket.cmd}")
                reconFlag = RECONNECT_ERROR
                currSecuAuthStatus = SECURE_AUTH_FAIL
            }
            CMD_SECURE_AUTH_ERR_RESP ->{
                JLog.logd("handlerSecurePacket secure auth failed")
                currSecuAuthStatus = SECURE_AUTH_FAIL
                isGetDataSuccessful = false
                recvSecureErrorCounter++
                transceiver?.channelStateObservable.onNext(transceiver?.secureError)
            }

            else ->{//安全认证错误
                JLog.logk("handlerSecurePacket fail securePacket.cmd=${securePacket.cmd},${recvSecureErrorCounter}")

                currSecuConnetcStatus = 0
                currSecuAuthStatus = SECURE_AUTH_FAIL

                recvSecureErrorCounter++
                if(recvSecureErrorCounter>1)
                    transceiver?.channelStateObservable.onNext(transceiver?.secureError)
                //ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_SECURITY_CHECK_FAIL, securePacket.cmd)
            }

        }
    }
    /**
     * 加密
     * @param data 需要被加密的数据
     * @param ak 密钥
     * @return 返回加密后的字节数组
     */
    fun encrypt(data:ByteArray, ak:ByteArray): ByteArray {
        val out_msg = ByteArray(data.size+64)
        //logd("encrypt_t tmpAK>>${HexTool.bytes2HexString(tmpAK)}")

        val reqlen = LibUcsslSignNative.encryptMsg(data,ak,out_msg)
        if(0 >= reqlen){
            loge("encrypt_t failed ${reqlen}")
            return ByteArray(1)
        }

        //logd("encrypt_t done ${reqlen}")
        return out_msg.copyOfRange(0,reqlen)
    }

    /**
     * 解密
     * @param data 需要被解密的数据
     * @param ak 密钥
     * @return 成功返回解密后的字节数组，失败返回null
     */
    fun decrypt(data:ByteArray, ak:ByteArray): ByteArray? {
        val out_msg = ByteArray(data.size+32)
        //logd("decrypt_t stmpAK>>${HexTool.bytes2HexString(tmpAK)}")
        val reqlen = LibUcsslSignNative.decryptMsg(data,ak,out_msg)
        if(0 >= reqlen){
            loge("decrypt_t failed ${reqlen}")
            return null
        }
        //logd("decrypt_t done")
        //logd("decrypt_t ${reqlen} done1>>${HexTool.bytes2HexString(out_msg)}")
        val return_array = out_msg.copyOfRange(0,reqlen)
        //logd("decrypt_t size ${return_array.size}")
        return return_array
    }



    /**
     * 检测设备激活状态
     * @return true：已激活； false：未激活
     */
    fun checkDeviceActiviStatus():Boolean {
        val libdyn = File(Configuration.simDataDir+COMMON_LIB_FILE)
        val client = File(Configuration.simDataDir+CLIENT_KEY_FILE)

        val md5file = File(Configuration.simDataDir+MD5_FILE_DATA)
        if (libdyn.exists() && client.exists() && md5file.exists()){
            var existedMd5 = md5file.readBytes()
            val libdynMd5 = EncryptUtils.getSHA1(libdyn.readBytes())
            var clientMd5 = EncryptUtils.getSHA1(client.readBytes())
            logd("checkDeviceActiviStatus ${clientMd5.size},${libdynMd5.size},${HexTool.bytes2HexString(existedMd5)}")
            if(MemUtil.memcmp(libdynMd5,existedMd5,20) && MemUtil.memcmp(clientMd5,existedMd5.copyOfRange(20,20),20)){
                logd("checkDeviceActiviStatus OK")
                return true
            }
            else{
                renameFiles(false)
            }
        }
        return false
    }
    fun updateDelayTimeToChangeKS(timeNow:Long) {
        val newDelayTimeToChangeKS = changeKSPeriod - (timeNow - timeToKickOfChangeKS);
        logd("updateDelayTimeToChangeKS(${timeNow},${timeToKickOfChangeKS}) >${delayTimeToChangeKS},${newDelayTimeToChangeKS} ")
        delayTimeToChangeKS = newDelayTimeToChangeKS
    }
    /**
     * 在disconnect时调用，以设置reconFlag等状态
     * @return void
     */
    fun setReconnectFlagsAtDisconnect() {
        currSecuConnetcStatus = 0
        logd("setReconnectFlagsAtDisconnect ${reconFlag},${currSecuAuthStatus}，${trySecureReconnectCounter}")
        if (reconFlag == RECONNECT_BEGIN) {
            trySecureReconnectCounter++
            //连续3次的业务重连包失败，下一次连接后才走SecuAuth，以避免频繁切换ks，可能导致ASS快速重连失败的问题
            if(trySecureReconnectCounter>3){
                reconFlag = RECONNECT_RESET
                if (currSecuAuthStatus == SECURE_AUTH_OK)
                    currSecuAuthStatus = SECURE_AUTH_FAIL
            }
        }
        else if (currSecuAuthStatus == SECURE_AUTH_OK) {
            //下次TCP连接后尝试直接发重连业务包
            reconFlag = RECONNECT_BEGIN
        }

        if (changeAKTask != null) {
            changeAKTask!!.cancel()
        }

        updateDelayTimeToChangeKS(System.currentTimeMillis())
    }


    fun memcpy(data1: ByteArray?, data2: ByteArray?, data2Offset:Int,len: Int) {
        if (data1 == null || data2 == null || data2.size<data1.size || len > data1.size || (data2Offset+len>data2.size)) {
            throw Exception("Range memcpy")
        }
        var i: Int = 0
        while (i < len) {
            data2[data2Offset + i] = data1[i]
            //logd("Range memcpy,${data2[data2Offset + i]} in filedadta[${data2Offset + i}]")
            i++
        }
    }

    /*
     * restore: 表示是否
     * 重命名备份文件
     */
    fun renameFiles( restore:Boolean):Boolean {
        val libdynBak = File(Configuration.simDataDir + COMMON_LIB_FILE + ".bak");
        val clientBak = File(Configuration.simDataDir + CLIENT_KEY_FILE + ".bak");
        val libdyn = File(Configuration.simDataDir + COMMON_LIB_FILE)
        val client = File(Configuration.simDataDir + CLIENT_KEY_FILE)
        if(restore) {
            if(!libdynBak.exists() || !clientBak.exists()||libdyn.exists()||client.exists()){
                loge("renameFiles failed1 :${restore},${clientBak.exists()}," +
                        "${libdynBak.exists()},${libdyn.exists()},${client.exists()}")
                return false;
            }
            libdynBak.renameTo(libdyn)
            clientBak.renameTo(client)
            isDeviceActivatd = true
        }
        else{
            if(!libdyn.exists() || !client.exists()){
                loge("renameFiles failed2 :${restore},${client.exists()},${libdyn.exists()}")
                return false;
            }
            libdyn.renameTo(libdynBak)
            client.renameTo(clientBak)

            isDeviceActivatd = false
            isGetDataSuccessful = false
        }
        logd("renameFiles done :${restore}")
        return true
    }
//    var mStore:KeyStore ?= null
    fun getToken(alias:String): ByteArray?{

        var token:String = "FFFF"
        try {
            //teste keystore...
            if(mStore == null) {
                mStore = KeyStore.getInstance("AndroidKeyStore")
            }
            mStore?.load(null)
            var sk = mStore?.getKey("ucloud-x",null)
            if(sk == null) {
                logd("SecretKey is null"+token)
                sk = createMacKey()
            }
            logd("SecretKey generated")
            return serverSign("com.ucloudlink.framework>"+alias,sk as SecretKey)

        } catch(e: Exception) {
            loge("getToken failed ${e.message}")
        }
        return null
    }
    fun createMacKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        keyGenerator.init(
                KeyGenParameterSpec.Builder("ucloud-x", KeyProperties.PURPOSE_SIGN).build())
        val key:SecretKey = keyGenerator.generateKey()
        logd("createMacKey####################2222>>"+key.encoded)
        return key
    }

    fun serverSign(plaintext: String, secretKey: SecretKey): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val encodedBytes = mac.doFinal(plaintext.toByteArray())
        return encodedBytes
    }

    /**
     * 启动更改密钥定时任务,（只启动一起）
     */
    fun startChangeAKTask(){
        if (timer== null){
            return
        }

        if (changeAKTask != null) {
            changeAKTask!!.cancel()
        }
         changeAKTask = ChangeAKTask()

        logd("startChangeAKTask delayTimeToChangeKS>>"+delayTimeToChangeKS)
        if(delayTimeToChangeKS<0)
            delayTimeToChangeKS = 0
        timer?.schedule(changeAKTask,delayTimeToChangeKS,changeKSPeriod)
    }

    /**
     * 更换密钥任务
     */
    class ChangeAKTask() : TimerTask() {
        override fun run() {

            logi("ChangeAKTask run()...")
            var new_ks = ByteArray(32)
            if(LibUcsslSignNative.createKS(Configuration.getImei(ServiceManager.appContext)+"     ",new_ks)<=0){
                logd("change key failed in createKS")
                return
            }
            newKs = new_ks
            var encryptEewKs = encrypt(new_ks, ks)
            val msg = SecureMessagePacker.createChangeKSPacket(randNum, encryptEewKs)
            logd("ChangeAKTask send KSPacket req ...")
            delayTimeToChangeKS = changeKSPeriod
            timeToKickOfChangeKS = System.currentTimeMillis()
            transceiver.send(msg)
        }
    }

    class OneSecureStepTimeoutTask() : TimerTask() {
        override fun run() {
            logd("OneSecureStepTimeoutTask $mDest,${trySecureReconnectTimeoutCounter}")
            transceiver.disconnectInternal(mDest)
            transceiver.channelStateObservable.onNext(secureTimeout)
            trySecureReconnectTimeoutCounter++
        }
    }
}