package com.ucloudlink.refact.product.mifi.seedUpdate

import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.restore.RunningStates
import com.ucloudlink.refact.business.AbortException
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.product.mifi.seedUpdate.event.*
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SeedFilesHelper
import com.ucloudlink.refact.product.mifi.seedUpdate.utils.SoftSimUtils
import com.ucloudlink.refact.utils.JLog.*
import rx.Subscription
import rx.lang.kotlin.subscribeWith

class SeedUpdateHandler(myLooper: Looper) : Handler(myLooper) {
    private var vsimSub: Subscription? = null
    private var seedSimSub: Subscription? = null
    private var uploadSub: Subscription? = null
    private var hasUploadOnce: Boolean = false
    private var lastCard: Card? = null
        set(value) {
            logd("Last seed card change: $field -> $value")
            field = value
        }
    private var lastSeedNetworkOperator: String? = null
        set(value) {
            logd("Last seed network operator change: $field -> $value")
            field = value
        }

    private val cloudSimEnabler = ServiceManager.cloudSimEnabler
    private val seedEnabler = ServiceManager.seedCardEnabler

    // 是否正在上传
    private var isUploading: Boolean = false
    // 上传超时设置
    private val upLoadTimeOut = 360L
    // 上传最大重试次数
    private val MAX_RETRY_COUNT: Int = 3
    // 上传重试次数
    private var retryTime = 0

    override fun handleMessage(msg: Message) {
        logd("handleMessage ${msg.what}")
        when (msg.what) {
            SEED_EVENT_INIT -> {
                // 初始化，开始监听
                startListenVsim()
            }
            SEED_EVENT_END -> {
                // 结束，清除监听
                clearTask()
            }
            SEED_DO_UPLOAD -> {
                // 上报当前种子卡列表
                if (cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED) {
                    doUpload()
                } else {
                    val message = obtainMessage()
                    message.copyFrom(msg)
                    sendMessageDelayed(message, 10 * 1000)
                }
            }
            SEED_BIND_WITH_SERVER -> {
                if (cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED) {
                    bindWithServer()
                } else {
                    val message = obtainMessage()
                    message.copyFrom(msg)
                    sendMessageDelayed(message, 2 * 1000)
                }
            }
        }
    }

    /**
     * 开始监听种子卡及云卡状态
     */
    private fun startListenVsim() {
        vsimSub = cloudSimEnabler.netStatusObser().asObservable().subscribe {
            if (it == NetworkInfo.State.CONNECTED) {
                sendMessage(obtainMessage(SEED_BIND_WITH_SERVER))
                sendMessageDelayed(obtainMessage(SEED_DO_UPLOAD), 10 * 1000)
            }
        }
        seedSimSub = seedEnabler.cardStatusObser().asObservable().subscribe {
            if (it == CardStatus.IN_SERVICE) {
                lastCard = seedEnabler.getCard().clone()
                lastSeedNetworkOperator = TelephonyManager.from(ServiceManager.appContext).networkOperator
            }
        }
    }

    /**
     * 结束
     */
    private fun clearTask() {
        retryTime = 0
        removeMessages(SEED_DO_UPLOAD)
        clearSub(vsimSub)
        vsimSub = null
        clearSub(seedSimSub)
        seedSimSub = null
        clearSub(uploadSub)
        uploadSub = null
        Requestor.abortRequest(TAG_UPLOAD_SOFTSIM_LIST)
    }

    /**
     * 清除监听事件
     */
    private fun clearSub(sub: Subscription?) {
        if (sub != null && !sub.isUnsubscribed) sub.unsubscribe()
    }

    /**
     * 上报当前种子卡列表
     */
    private fun doUpload() {
        logd("prepare do upload")
        if (isUploading || hasUploadOnce) {
            logd("Uploading = $isUploading, hasUploadOnce = $hasUploadOnce")
            return
        }
        synchronized(this) {
            if (isUploading) return
            isUploading = true
            // 卡列表（物理卡、软卡）
            val softSimList = SoftSimUtils.getCurrentSoftSimList(lastCard, lastSeedNetworkOperator)
            // 当前imei
            val imei = Configuration.getImei(ServiceManager.appContext)
            // 规则文件是否存在
            val ruleExist = SeedFilesHelper.checkRuleRefExist()
            logv("doUpload start ruleExist:$ruleExist, imei:$imei, simList:$softSimList")
            uploadSub = Requestor.uploadExtSoftsimList(softSimList, RunningStates.getUserName(), imei, ruleExist, 0, upLoadTimeOut, TAG_UPLOAD_SOFTSIM_LIST).subscribeWith {
                onSuccess {
                    logv("doUpload success")
                    hasUploadOnce = true
                    sendMessage(obtainMessage(SEED_EVENT_END))
                }
                onError {
                    logv("doUpload uploadExtSoftsimList error: ${it.message}")
                    if (it !is AbortException) {
                        // 十秒后重试，有最大重试次数
                        if (retryTime <= MAX_RETRY_COUNT) {
                            retryTime++
                            sendMessageDelayed(obtainMessage(SEED_DO_UPLOAD), 10 * 1000)
                        } else {
                            sendMessage(obtainMessage(SEED_EVENT_END))
                        }
                    }
                }
            }
        }
    }

    /**
     * 云卡通道与服务器建立绑定关系
     */
    private fun bindWithServer() {
        logd("bindWithServer")
        val imei = Configuration.getImei(ServiceManager.appContext)
        uploadSub = Requestor.cloudsimSocketOk(ServiceManager.cloudSimEnabler.getCard().imsi, imei, 360L).subscribeWith {
            onSuccess {
                logv("bindWithServer finish")
            }
            onError {
                loge("bindWithServer fail :${it.message}")
                if (it !is AbortException) {
                    //十秒后重试，有最大重试次数
                    if (retryTime <= MAX_RETRY_COUNT) {
                        retryTime++
                        sendMessageDelayed(obtainMessage(SEED_BIND_WITH_SERVER), 2 * 1000)
                    }
                }
            }
        }
    }
}
