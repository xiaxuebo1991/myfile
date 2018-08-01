package com.ucloudlink.refact.business.cardprovisionstatus


import android.os.Handler
import android.os.Looper
import android.os.Message
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import rx.Observable
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.subscribeWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by zhangxian on 2016/12/21.
 */

object CardProvisionStatus : Thread() {
    val CARD_START_MONITOR = 0
    val SEEDCARD_START_MONITOR = 1
    val VSIMCARD_START_MONITOR = 2
    val CARD_CLOSE_MONITOR = 3
    val SEEDCARD_CLOSE_MONITOR = 4
    val VSIMCARD_CLOSE_MONITOR = 5
    val CARD_STATE_CHANGE = 6
    val SEEDCARD_STATE_CHANGE = 7
    val VSIMCARD_STATE_CHANGE = 8

    private val PROVISIONED = 1//卡处于启用状态
    private val NOT_PROVISIONED = 0//卡处于禁用状态
    private val INVALID_STATE = -1
    private val CARD_NOT_PRESENT = -2
    private val CARD_DETECT_TIME: Long = 1//单位s

    private var mLooper: Looper? = null
    internal var mHandler: myHandler? = null
    private var MonitorIns: CardProvisionStatus? = null
    var seedSimDetectObservable = BehaviorSubject<CardStatus>()
    var seedSimDetectOnlyOneFlag = true
    var LastSeedProvisionStatus = INVALID_STATE

    var cloudSimDetectObservable = BehaviorSubject<CardStatus>()
    var cloudSimDetectOnlyOneFlag = true
    var LastCloudProvisionStatus = INVALID_STATE

    fun getSimSetStatus(cardType: CardType): Int {
        var mUiccProvisionStatus = INVALID_STATE
        val slot = if(cardType == CardType.VSIM) Configuration.cloudSimSlot else Configuration.seedSimSlot
        mUiccProvisionStatus = ServiceManager.systemApi.getSimEnableState(slot)
        JLog.logd("CardProvisionStatus slot $slot getSimSetStatus: " + mUiccProvisionStatus)
        return mUiccProvisionStatus
    }

    fun sendSettingSimCmd(cardType: CardType, cmd: Int) {
        val mCmd: Message
        var sendCmd = -1

        if (cardType == CardType.VSIM) {
            when (cmd) {
                CARD_START_MONITOR -> {
                    sendCmd = VSIMCARD_START_MONITOR
                }
                CARD_CLOSE_MONITOR -> {
                    sendCmd = VSIMCARD_CLOSE_MONITOR
                }
                CARD_STATE_CHANGE -> {
                    sendCmd = VSIMCARD_STATE_CHANGE
                }
                else -> {
                    JLog.logd("CardProvisionStatus cmd err")
                }
            }
        } else {
            when (cmd) {
                CARD_START_MONITOR -> {
                    sendCmd = SEEDCARD_START_MONITOR
                }
                CARD_CLOSE_MONITOR -> {
                    sendCmd = SEEDCARD_CLOSE_MONITOR
                }
                CARD_STATE_CHANGE -> {
                    sendCmd = SEEDCARD_STATE_CHANGE
                }
                else -> {
                    JLog.logd("CardProvisionStatus cmd err")
                }
            }
        }
        JLog.logd("CardProvisionStatus cmd:" + sendCmd)
        if (sendCmd != -1) {
            mCmd = mHandler!!.obtainMessage(sendCmd)
            mHandler!!.sendMessage(mCmd)
        }
    }

    //关闭卡检测
    fun onSeedSimDetectStop() {
        LastSeedProvisionStatus = INVALID_STATE
        if (!seedSimDetectOnlyOneFlag) {
            seedSimDetectObservable.onCompleted()
        }
    }

    //在起卡阶段，检测卡是否一开始就被禁用，针对运行ucapp之前卡已被关闭
    fun onSeedSimDetectStart() {
        var mUiccProvisionStatus = INVALID_STATE
        var ret: Int = 0

        if (seedSimDetectOnlyOneFlag) {
            JLog.logd("CardProvisionStatus SeedSim DetectStart!!")
            seedSimDetectOnlyOneFlag = false
            seedSimDetectObservable.timeout(CARD_DETECT_TIME, TimeUnit.SECONDS).retryWhen { errors ->
                errors.flatMap({ error ->
                    if (error is TimeoutException) {
                        mUiccProvisionStatus = ServiceManager.systemApi.getSimEnableState(Configuration.seedSimSlot)
                        JLog.logd("CardProvisionStatus SeedSim detect: " + mUiccProvisionStatus)
                        LastSeedProvisionStatus = mUiccProvisionStatus
                        if (mUiccProvisionStatus > INVALID_STATE) {
                            if (mUiccProvisionStatus == PROVISIONED) {
                                Observable.error<Throwable>(error("CardProvisionStatus SeedSim PROVISIONED!") as Throwable)
                            } else if (mUiccProvisionStatus == NOT_PROVISIONED) {
                                //ret = mExtTelephony.activateUiccCard(Configuration.seedSimSlot) // TODO a
                                JLog.logd("CardProvisionStatus SeedSim activateUiccCard: " + ret)
                                com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE)
                                com.ucloudlink.refact.ServiceManager.accessMonitor.seedsimDisable()
                                Observable.error<Throwable>(error("CardProvisionStatus SeedSim NOT_PROVISIONED!") as Throwable)
                            } else {
                                Observable.just(1)
                            }
                        } else {
                            Observable.just(1)
                        }

                    } else {
                        Observable.error<Throwable>(error("CardProvisionStatus SeedSim error!") as Throwable)
                    }
                })
            }.subscribeWith {
                onNext {
                    JLog.logd("CardProvisionStatus SeedSim onNext")
                }
                onCompleted {
                    JLog.logd("CardProvisionStatus SeedSim DetectStop for onCompleted")
                    seedSimDetectOnlyOneFlag = true
                }
                onError {
                    JLog.logd("CardProvisionStatus SeedSim DetectStop for onError")
                    seedSimDetectOnlyOneFlag = true
                }
            }
        }

    }

    //在使用过程中，修改setting sim设置
    fun onSeedSimChange() {
        var mUiccProvisionStatus = INVALID_STATE
        mUiccProvisionStatus = ServiceManager.systemApi.getSimEnableState(Configuration.seedSimSlot)
        if (LastSeedProvisionStatus != mUiccProvisionStatus) {
            LastSeedProvisionStatus = mUiccProvisionStatus
            JLog.logd("CardProvisionStatus SeedSim change: " + LastSeedProvisionStatus)
            if (mUiccProvisionStatus == NOT_PROVISIONED) {
                com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_SEED_CARD_DISABLE)
                com.ucloudlink.refact.ServiceManager.accessMonitor.seedsimDisable()
            } else if (mUiccProvisionStatus == PROVISIONED) {
                com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_SEED_CARD_ENABLE)
                com.ucloudlink.refact.ServiceManager.accessMonitor.seedsimEnable()
            }
        }
    }

    //关闭卡检测
    fun onCloudSimDetectStop() {
        LastCloudProvisionStatus = INVALID_STATE
        if (!cloudSimDetectOnlyOneFlag) {
            cloudSimDetectObservable.onCompleted()
        }
    }

    //在起卡阶段，检测卡是否一开始就被禁用，针对运行ucapp之前卡已被关闭
    fun onCloudSimDetectStart() {
        var mUiccProvisionStatus = INVALID_STATE
        var ret: Int = 0

        if (cloudSimDetectOnlyOneFlag) {
            JLog.logd("CardProvisionStatus CloudSim DetectStart!!")
            cloudSimDetectOnlyOneFlag = false
            cloudSimDetectObservable.timeout(CARD_DETECT_TIME, TimeUnit.SECONDS).retryWhen {
                errors ->
                errors.flatMap({
                    error ->
                    if (error is TimeoutException) {
                        mUiccProvisionStatus = ServiceManager.systemApi.getSimEnableState(Configuration.cloudSimSlot)
                        JLog.logd("CardProvisionStatus CloudSim detect: " + mUiccProvisionStatus)
                        LastCloudProvisionStatus = mUiccProvisionStatus
                        if (mUiccProvisionStatus > INVALID_STATE) {
                            if (mUiccProvisionStatus == PROVISIONED) {
                                Observable.error<Throwable>(error("CardProvisionStatus CloudSim PROVISIONED!") as Throwable)
                            } else if (mUiccProvisionStatus == NOT_PROVISIONED) {
                                // ret = mExtTelephony.activateUiccCard(Configuration.cloudSimSlot) // TODO:  这里需要添加
                                JLog.logd("CardProvisionStatus SeedSim activateUiccCard: " + ret)
                                com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE)
                                com.ucloudlink.refact.ServiceManager.accessMonitor.cloudsimDisable()
                                Observable.error<Throwable>(error("CardProvisionStatus CloudSim NOT_PROVISIONED!") as Throwable)
                            } else {
                                Observable.just(1)
                            }
                        } else {
                            Observable.just(1)
                        }
                    } else {
                        Observable.error<Throwable>(error("CardProvisionStatus CloudSim error!") as Throwable)
                    }
                })
            }.subscribeWith {
                onNext {
                    JLog.logd("CardProvisionStatus CloudSim onNext")
                }
                onCompleted {
                    JLog.logd("CardProvisionStatus CloudSim DetectStop for onCompleted")
                    cloudSimDetectOnlyOneFlag = true
                }
                onError {
                    JLog.logd("CardProvisionStatus CloudSim DetectStop for onError")
                    cloudSimDetectOnlyOneFlag = true
                }
            }
        }
    }

    //在使用过程中，修改setting sim设置
    fun onCloudSimChange() {
        var mUiccProvisionStatus = INVALID_STATE

        mUiccProvisionStatus = ServiceManager.systemApi.getSimEnableState(Configuration.cloudSimSlot)
        if (LastCloudProvisionStatus != mUiccProvisionStatus) {
            LastCloudProvisionStatus = mUiccProvisionStatus
            JLog.logd("CardProvisionStatus CloudSim change: " + LastCloudProvisionStatus)
            if (mUiccProvisionStatus == NOT_PROVISIONED) {
                com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_COULD_CARD_DISABLE)
                com.ucloudlink.refact.ServiceManager.accessMonitor.cloudsimDisable()
            } else if (mUiccProvisionStatus == PROVISIONED) {
                com.ucloudlink.refact.ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_COULD_CARD_ENABLE)
                com.ucloudlink.refact.ServiceManager.accessMonitor.cloudsimEnale()
            }
        }
    }

    class myHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            JLog.logd("CardProvisionStatus handleMessage:" + msg.what)
            try {
                when (msg.what) {//不同状态不同处理
                    SEEDCARD_START_MONITOR -> {
                        onSeedSimDetectStart()
                    }
                    SEEDCARD_CLOSE_MONITOR -> {
                        onSeedSimDetectStop()
                    }
                    VSIMCARD_START_MONITOR -> {
                        onCloudSimDetectStart()
                    }
                    VSIMCARD_CLOSE_MONITOR -> {
                        onCloudSimDetectStop()
                    }
                    SEEDCARD_STATE_CHANGE -> {
                        onSeedSimChange()
                    }
                    VSIMCARD_STATE_CHANGE -> {
                        onCloudSimChange()
                    }
                }
            } catch (e: Exception) {
                JLog.loge("CardProvisionStatus handleMessage", e)
            }

        }
    }

    override fun run() {
        try {
            JLog.logd("CardProvisionStatus thread run")
            Looper.prepare()
            mLooper = Looper.myLooper()
            mHandler = myHandler(mLooper!!)
            Looper.loop()
        } catch (e: Exception) {
            JLog.loge("CardProvisionStatus thread run", e)
        } finally {
            JLog.logd("CardProvisionStatus thread end")
        }
    }
}