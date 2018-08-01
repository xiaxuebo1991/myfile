package com.ucloudlink.refact.business.softsim

import android.net.NetworkInfo
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.*
import rx.Single
import rx.SingleSubscriber
import rx.Subscription
import rx.lang.kotlin.PublishSubject
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Created by shiqianhua on 2017/6/3.
 *
 * AwaitState    待机状态
 * WorkState     工作状态
 *      RunningState    工作运行状态
 *      PauseState      工作暂停状态
 *
 *
 * starSeed(userName,packageName,Timeout)
 * disableSeed()
 *
 * acquireHold(id)  申请暂停
 * releaseHold(id)  释放暂停
 *
 */
object SeedNetworkStart {
    private val TAG = "SeedNetworkStart"
    private var cardExceptSub: Subscription? = null
    private var waitTimeoutSub: Subscription? = null

    private fun safeUndescribe(sub: Subscription?) {
        if (sub != null && !sub.isUnsubscribed) {
            sub.unsubscribe()
        }
    }

    private var rootSub: SingleSubscriber<in StartSeedResult>? = null
    private var netSub: Subscription? = null
    private var mCloseSeedTimeOut = 600

    fun starSeed(UserName: String?, packageName: String?, closeSeedTimeOut: Int): Single<StartSeedResult> {

        return Single.create<StartSeedResult> { sub ->

            Configuration.orderId = packageName ?: "null"
            if (TextUtils.isEmpty(UserName)) {
                sub.onSuccess(getResult(ErrorCode.LOCAL_USERNAME_INVALID))
                return@create
            }
            if (TextUtils.isEmpty(packageName) || ServiceManager.accessEntry.isServiceRunning) {
                sub.onSuccess(getResult(ErrorCode.LOCAL_SERVICE_RUNNING))
                return@create
            }

            Configuration.username = UserName

            if (ServiceManager.seedCardEnabler.getNetState() == NetworkInfo.State.CONNECTED) {
                logd("network is already OK!")
                sub.onSuccess(getResult(0, "Succ"))
                ServiceManager.productApi.getNetRestrictOperater().setRestrict("StartSeedCardNet State.CONNECTED")
                return@create
            }

            rootSub = sub

            mCloseSeedTimeOut = closeSeedTimeOut

            if (holdList.isEmpty()) {
                startSeedNetwork()
            } else {
                logd("starSeed holdList is not empty")
            }

        }.doOnUnsubscribe {
            rootSub = null
            clearCardSub()
        }
    }

    private fun startSeedNetwork() {

        val _rootSub = rootSub
        _rootSub ?: return loge("task is cancel")

        val info = ServiceManager.accessEntry.softsimEntry.getOrderInfoByUserOrderId(Configuration.username, Configuration.orderId)
        logd("order info " + info)
        if (info == null) {
            _rootSub.onSuccess(getResult(ErrorCode.LOCAL_ORDER_INFO_IS_NULL))
            return
        }
        if (info.isOutOfDate) {
            _rootSub.onSuccess(getResult(ErrorCode.LOCAL_ORDER_OUT_OF_DATE))
            return
        }
        ServiceManager.accessSeedCard.startService()
        val ret = ServiceManager.seedCardEnabler.enable(ArrayList<Card>())
        logk("ServiceManager.seedCardEnabler.enable card return: $ret")
        if (ret != 0) {
            ServiceManager.accessSeedCard.stopService()
            ServiceManager.seedCardEnabler.disable("enable failed!" + ret)
            _rootSub.onSuccess(getResult(ret))
            return loge("enable failed!")
        }

        ServiceManager.productApi.getNetRestrictOperater().setRestrict("StartSeedCardNet enable")

//        netSub = ServiceManager.seedCardEnabler.netStatusObser().timeout(600, TimeUnit.SECONDS)
        netSub = ServiceManager.seedCardEnabler.netStatusObser()
                .subscribe({
                    logd("net change:" + it)
                    if (it == NetworkInfo.State.CONNECTED) {

                        _rootSub.onSuccess(getResult(0, "Succ"))
                        clearCardSub()

                        val timeout = if (mCloseSeedTimeOut == 0) 60 else mCloseSeedTimeOut.toLong()
                        waitTimeoutSub = PublishSubject<Int>().timeout(timeout, TimeUnit.SECONDS)
                                .subscribe({
                                    logd("cannot run here!!")
                                }, {
                                    loge("seed network timeout!, so close seed network!")
                                    safeUndescribe(waitTimeoutSub)
                                    ServiceManager.seedCardEnabler.disable("timeout")
                                })

                    }// else do nothing!
                }, {

                })


        cardExceptSub = ServiceManager.seedCardEnabler.exceptionObser().subscribe(
                {
                    loge("get card exception " + it)
//                    val retCode = ErrorCode.getErrCodeByCardExceptId(it)
//                    checkStopService()
//                    setSpeedByServiceState("cardExceptSub.onNext")
//                    ServiceManager.accessEntry.softsimEntry.updateStartSeedNetworkResult(Configuration.orderId, retCode, ErrorCode.getErrMsgByCode(retCode))
//                    _rootSub.onSuccess(getResult(retCode))
//                    clearCardSub()
                    val ret = ServiceManager.seedCardEnabler.enable(ArrayList<Card>())
                    logk("ServiceManager.seedCardEnabler.enable card return: $ret")
                }
        )

    }
    
    fun stopSeed(){
        rootSub = null
        clearCardSub()
        disableSeed()
    }

    private fun disableSeed(): Int {
        logv("stopSeedNetwork current thread: " + Thread.currentThread().id + " " + Thread.currentThread().name)
        setSpeedByServiceState("stopSeedNetwork")

        if (!ServiceManager.accessEntry.isServiceRunning) {
            ServiceManager.accessSeedCard.stopService()
            return ServiceManager.seedCardEnabler.disable("UI stop")
        }
        return -1
    }

    private fun getResult(errorCode: Int): StartSeedResult {
        return getResult(errorCode, ErrorCode.getErrMsgByCode(errorCode))
    }

    private fun getResult(errorCode: Int, Msg: String): StartSeedResult {
        return StartSeedResult(Configuration.orderId, errorCode, Msg)
    }

    private fun checkStopService() {
        if (!ServiceManager.accessEntry.isServiceRunning()) {
            ServiceManager.accessSeedCard.stopService()
        }
    }

    /** 检查是否正在执行流程业务 */
    private fun setSpeedByServiceState(tag: String) {
        logd("NetSpeedCtrlLog SeedCardNetLog, setSpeedByServiceState(): tag = $tag")
        if (!ServiceManager.accessEntry.isServiceRunning()) {
            ServiceManager.productApi.getNetRestrictOperater().resetRestrict("setSpeedByServiceState isServiceRunning = false")
        } else if (ServiceManager.accessEntry.accessState.isVsimServiceOK()) {
            ServiceManager.productApi.getNetRestrictOperater().resetRestrict("setSpeedByServiceState isVsimServiceOK = true")
        } else {
            logd("NetSpeedCtrlLog SeedCardNetLog setSpeedByServiceState: ")
        }
    }

    private val holdList = ArrayList<String>()

    fun acquireHold(id: String) {
        if (!holdList.contains(id)) {
            holdList.add(id)
        }
    }

   
    
    fun releaseHold(id: String) {
        if (holdList.contains(id)) {
            holdList.remove(id)
            if (holdList.isEmpty()) {
                startSeedNetwork()
            }
        }
    }

    fun clearCardSub() {

        safeUndescribe(netSub)
        netSub = null

        safeUndescribe(waitTimeoutSub)
        waitTimeoutSub = null
        
        safeUndescribe(cardExceptSub)
        cardExceptSub = null

    }
}

data class StartSeedResult(val orderId: String, val errorCode: Int, val Msg: String)