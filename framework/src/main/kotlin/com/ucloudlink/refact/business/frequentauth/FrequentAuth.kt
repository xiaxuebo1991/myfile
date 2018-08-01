package com.ucloudlink.refact.business.frequentauth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.*
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessState
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import rx.Subscription
import rx.lang.kotlin.subscribeWith
import java.util.*
import java.util.concurrent.TimeUnit
import com.ucloudlink.refact.business.performancelog.PerfUntil
import com.ucloudlink.refact.platform.sprd.api.SprdApiInst


/**
 * @author haiping.liu
 * @date   2017/11/30
 */
object FrequentAuth {
    private val NEED_SOCKET_REASON = "frequent_auth_need"
    private val SOCKET_TIME_OUT = 35
    private val CLOUD_SUCCESS_DELAYED_TIME = 60L
    private val STATU_IN_SERVICE = 4     //云卡状态：IN_SERVICE
    private val STATU_NOT_IN_SERVICE = 6//云卡状态：ABSENT-IN_SERVICE之间
    private val STATU_ABSENT = 7          //云卡状态：ABSENT
    private val STATU_DEFAULT = 8
    private var currentStatus = STATU_DEFAULT

    private val MSG_SOCKET_UPLOAD_FAIL = 9
    private val MSG_RESULT_UPLOAD_FAIL = 10
    private val MSG_REV_APDU_FROM_SERVICE = 11
    private val MSG_CLOUD_SOCKET_CONNECT = 12
    val MSG_REV_APDU_RSP = 13

    private val uploadSocketRetryCount = 1
    private var curRetryCount = 0
    private val uploadResultRetryCount = 1
    private var curUploadResultRetryCount = 0


    private var currentPersent = 0
    lateinit var faHandler: Handler

    lateinit var fDetection: FrequentDetection//鉴权检测
    lateinit var fPing: FrequentPing //ping操作
    private  var wifiReceiver: ConnectchangeReceiver = ConnectchangeReceiver()
    private var isInPlugPullCloudSimState = false
    private var isGettingParam = false
    fun init() {
        logd("init")
        fDetection = FrequentDetection()
        fPing = FrequentPing()

        val mHanderThread: HandlerThread = object : HandlerThread("FrequentAuthHandlerThread") {
            override fun onLooperPrepared() {
                faHandler = object : Handler(Looper.myLooper()) {
                    override fun handleMessage(msg: Message?) {
                        when (msg!!.what) {
                            STATU_IN_SERVICE -> {
                                logd("mHandler rev : ---------------STATU_IN_SERVICE")
                                faHandler.removeMessages(STATU_IN_SERVICE)
                                isGettingParam = false
                                if (!fDetection.ifNeedDetection()) {
                                    //如果不需要检测

                                    if (fPing.ifNeedPing()) {
                                        //需要ping
                                        fPing.startPingTask()
                                        fPing.screenChange(PerfUntil.ifScreenOn(ServiceManager.appContext))
                                    } else {
                                        //不需要ping
                                        uploadSocketOk(SOCKET_TIME_OUT)
                                    }
                                }
                            }
                            STATU_NOT_IN_SERVICE -> {
                                logd("mHandler rev : ---------------STATU_NOT_IN_SERVICE")
                                faHandler.removeMessages(STATU_IN_SERVICE)
                                isGettingParam = false
                                fPing.stopPingTask()
                            }
                            STATU_ABSENT -> {
                                logd("mHandler rev : ---------------STATU_ABSENT")
                                faHandler.removeMessages(STATU_IN_SERVICE)
                                isGettingParam = false
                                fPing.stopPingTask()
//                                if(currentPersent <63){
//                                    fDetection.stopDetection()
//                                    fPing.delFrequentAuthAction()
//                                }
                            }
                            MSG_SOCKET_UPLOAD_FAIL -> {
                                curRetryCount++
                                logd("mHandler rev : ---------------MSG_SOCKET_UPLOAD_FAIL， curRetryCount=$curRetryCount ,currentStatus=$currentStatus")
                                if (curRetryCount <= uploadSocketRetryCount && currentStatus == STATU_IN_SERVICE) {
                                    uploadSocketOk(SOCKET_TIME_OUT)
                                } else {
                                    curRetryCount = 0
                                }
                            }
                            MSG_REV_APDU_FROM_SERVICE -> {
                                logd("mHandler rev : ---------------MSG_REV_APDU_FROM_SERVICE")
                                uploadDetectionResult(SOCKET_TIME_OUT)
                            }
                            MSG_CLOUD_SOCKET_CONNECT -> {
                                logd("mHandler rev : ---------------MSG_CLOUD_SOCKET_CONNECT")
                                uploadDetectionResult(SOCKET_TIME_OUT)
                            }
                            MSG_RESULT_UPLOAD_FAIL -> {
                                curUploadResultRetryCount++
                                logd("mHandler rev : ---------------MSG_RESULT_UPLOAD_FAIL curUploadResultRetryCount=$curUploadResultRetryCount , uploadResultRetryCount=$uploadResultRetryCount")
                                if (curUploadResultRetryCount <= uploadResultRetryCount) {
                                    uploadDetectionResult(SOCKET_TIME_OUT)
                                } else {
                                    curUploadResultRetryCount = 0
                                }
                            }
                            MSG_REV_APDU_RSP -> {
                                revApduRspFromeService()
                            }
                        }
                    }
                }
            }
        }
        mHanderThread.start()
        registerCb()
        ServiceManager.cloudSimEnabler.cardStatusObser().subscribeWith {
            onNext {
                var newStatus = STATU_DEFAULT
                if (it == CardStatus.IN_SERVICE) {
                    newStatus = STATU_IN_SERVICE
                } else {
                    newStatus = STATU_NOT_IN_SERVICE
                }
                if (it == CardStatus.ABSENT) {
                    newStatus = STATU_ABSENT
                }
                logd("cloudEnabler onNext it=$it, statu:(new:$newStatus , old:$currentStatus)")
                if (newStatus != currentStatus) {
                    var ret = false
                    currentStatus = newStatus
                    if (newStatus != STATU_IN_SERVICE){
                        faHandler.removeMessages(STATU_IN_SERVICE)
                    }
                    isGettingParam = false
                    if (currentStatus == STATU_IN_SERVICE) {

                    } else if (currentStatus == STATU_ABSENT) {
                        ret = faHandler.sendEmptyMessage(STATU_ABSENT)
                    } else if (currentStatus == STATU_NOT_IN_SERVICE) {
                        ret = faHandler.sendEmptyMessage(STATU_NOT_IN_SERVICE)
                    }
                    logd("sendMessage ret = $ret")
                }
            }
        }
        ServiceManager.transceiver.statusObservable(ServerRouter.Dest.ASS)
                .subscribeWith {
                    onNext { socketStatus ->
                        logd("socket status change:---------------------$socketStatus ,currentPersent=$currentPersent")
                        when (socketStatus) {
                            "SocketConnected" -> {
                                if (currentPersent == 100) {
                                    faHandler.sendEmptyMessage(MSG_CLOUD_SOCKET_CONNECT)
                                }
                            }
                            "SocketDisconnected" -> {

                            }
                        }
                    }
                }
        registerWifiRev()
    }

    fun registerCb() {
        val mAccessListener = AccessStateListener()
        val accessEntry = ServiceManager.accessEntry
        accessEntry.registerAccessStateListen(mAccessListener)
    }

    private class AccessStateListener : AccessState.AccessStateListen {
        override fun processUpdate(persent: Int) {
            logd("processUpdate------------------ $persent ")
            currentPersent = persent

            if(currentPersent == 63){
                isInPlugPullCloudSimState = true
            }

            //状态机0% 登录35% 换卡45% 删除配置表
            if(currentPersent == 0||currentPersent ==35||currentPersent ==45){
                fDetection.stopDetection()
                fPing.delFrequentAuthAction()
            }

            if(currentPersent == 90){
                logd("isGettingParam: $isGettingParam")
                if(!isGettingParam && fDetection.ifNeedGetAuthParam()){
                    isGettingParam = true
                    faHandler.sendEmptyMessageDelayed(STATU_IN_SERVICE, TimeUnit.SECONDS.toMillis(CLOUD_SUCCESS_DELAYED_TIME))
                    logd("uploadSocketOk after 60 second")
                }
            }

            if(currentPersent == 100){
                //当进度100%时尝试恢复ping
                if (fPing.ifNeedPing()) {
                    fPing.startPingTask()
                    fPing.screenChange(PerfUntil.ifScreenOn(ServiceManager.appContext))
                } else {
                    fPing.stopPingTask()
                }
                isInPlugPullCloudSimState = false
            }

        }

        override fun eventCloudSIMServiceStop(reason: Int, message: String?) {
        }

        override fun eventCloudsimServiceSuccess() {
        }

        override fun eventSeedState(persent: Int) {
        }

        override fun eventSeedError(code: Int, message: String?) {
        }

        override fun errorUpdate(errorCode: Int, message: String) {
        }
    }

    /**
     * 服务器返回鉴权包，统计鉴权包数量及类型
     */
    fun revApduRspFromeService() {
        logd("revApduRspFromeService")
        if (isInPlugPullCloudSimState){
            loge("revApduRspFromeService isInPlugPullCloudSimState=$isInPlugPullCloudSimState")
            return
        }
        val result = fDetection.saveApduInfo()
        if (result) {
            faHandler.sendEmptyMessage(MSG_REV_APDU_FROM_SERVICE)
        }
    }

    /**
     *  s2c收到配置表
     */
    fun revFrequentAuthParam(s2c: S2c_frequent_auth_detection_param) {
        fDetection.saveFrequentAuthParam(s2c)
    }

    /**
     *  s2c收到ping
     */
    fun revFrequentAuthAction(s2c: S2c_frequent_auth_action) {
        //收到ping配置表，停止频繁鉴权检测
        fDetection.stopDetection()
        fPing.saveFrequentAuthAction(s2c)
    }

    //ping
    fun pingOnetime(ipAddress: String) {
        fPing.pingOnetime(ipAddress)
    }

    //ping
    fun screenChange(statu: Boolean) {
        fPing.screenChange(statu)
    }

    //ping:亮灭屏定时器时间到
    fun screenTaskRev() {
        if (PerfUntil.ifScreenOn(ServiceManager.appContext)) {
            logd("screenTaskRev()---------------SCREEN_ON")
            if (fPing.ifNeedPing()) {
                fPing.startPingTask()
            } else {
                fPing.stopPingTask()
            }
        } else {
            logd("screenTaskRev() ---------------SCREEN_OFF")
            fPing.stopPingTask()
        }
    }

    //停止modem记录
    fun stopModem() {

    }

    /**
     * 上报云卡socket建立(云卡成功情况下)
     */
    private fun uploadSocketOk(timeout: Int) {
        logd("uploadSocketOk()")
        val imsi = ServiceManager.accessEntry.accessState.imis
        val imei = Configuration.getImei(ServiceManager.appContext)
        if (imei != null && imsi != null) {
            val req = Cloudsim_socket_ok_req(imsi, imei, 1)
            Requestor.requestCloudsimSocketOK(req, timeout)
                    .subscribe({
                        if (it is Cloudsim_socket_ok_rsp) {
                            logd("requestCloudsimSocketOK success errorCode=${it.errorCode}")
                        } else {
                            logd("requestCloudsimSocketOK fail1:$it")
                            faHandler.sendEmptyMessage(MSG_SOCKET_UPLOAD_FAIL)
                        }
                    }, {
                        logd("requestCloudsimSocketOK fail2:$it")
                        faHandler.sendEmptyMessage(MSG_SOCKET_UPLOAD_FAIL)
                    })
        } else {
            loge("uploadSocketOk error:imei == null or imsi == null")
            faHandler.sendEmptyMessage(MSG_SOCKET_UPLOAD_FAIL)
        }
    }

    /**
     * 上报云卡频繁鉴权结果
     */
    private fun uploadDetectionResult(timeout: Int) {
        val apduList = fDetection.getApduList()
        logd("uploadDetectionResult apduList=$apduList")
        if (apduList.size <= 0) {
            loge("uploadDetectionResult return apduList.size <= 0")
            return
        }
        if (currentPersent == 100) {
            val apduListTemp: ArrayList<Frequent_auth_detection_result_req> = apduList.clone() as ArrayList<Frequent_auth_detection_result_req>
            for (apdu in apduListTemp) {
                Requestor.requestFrequentAuthResult(apdu, timeout).subscribe({
                    if (it is Frequent_auth_detection_result_rsp) {
                        if (it.errorCode == 100) {
                            logd("uploadDetectionResult:success")
                            apduList.remove(apdu)
                            logd("uploadDetectionResult after apduList=$apduList")
                        } else {
                            logd("uploadDetectionResult:fail:${it.errorCode}")
                            if (apduList.size > 0) {
                                //重试
                                var msg = Message()
                                msg.what = MSG_RESULT_UPLOAD_FAIL
                                faHandler.sendMessage(msg)
                            }
                        }
                    } else {
                        logd("uploadDetectionResult:fail1: $it")
                        if (apduList.size > 0) {
                            //重试
                            var msg = Message()
                            msg.what = MSG_RESULT_UPLOAD_FAIL
                            faHandler.sendMessage(msg)
                        }
                    }
                }, {
                    logd("uploadDetectionResult:fail2: $it")
                    if (apduList.size > 0) {
                        //重试
                        var msg = Message()
                        msg.what = MSG_RESULT_UPLOAD_FAIL
                        faHandler.sendMessage(msg)
                    }
                })
            }
        } else {
            loge("uploadDetectionResult error: persent != 100")
        }
    }



    private fun registerWifiRev() {
        val filter = IntentFilter()
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        ServiceManager.appContext.registerReceiver(wifiReceiver, filter)
    }

    private fun unregisterWifiRev() {
        ServiceManager.appContext.unregisterReceiver(wifiReceiver)

    }

    private class ConnectchangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent != null) {
                val action = intent.action
                if(action.equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                    logd("ConnectchangeReceiver change")
                    if(isWifiConnected(ServiceManager.appContext)){
                        logd("ConnectchangeReceiver wifi connect stopPingTask")
                        fPing.stopPingTask()
                    }else{
                        logd("ConnectchangeReceiver wifi disconnect startPingTask currentPersent=$currentPersent")
                        if(currentPersent == 100){
                            if (fPing.ifNeedPing()) {
                                fPing.startPingTask()
                                fPing.screenChange(PerfUntil.ifScreenOn(ServiceManager.appContext))
                            } else {
                                fPing.stopPingTask()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (wifiNetworkInfo.isConnected()) {
            return true
        }
        return false
    }
}