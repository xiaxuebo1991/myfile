package com.ucloudlink.refact.business.netcheck

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.SystemClock
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.DeType
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import rx.Subscription
import rx.lang.kotlin.subscribeWith
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Created by shiqianhua on 2017/8/7.
 */
object NetworkTest {
    lateinit var ctx:Context
    lateinit var seedEnabler: IDataEnabler
    lateinit var cloudEnabler: IDataEnabler
    var isSeedOn: Boolean = false
    var isCloudOn: Boolean = false
    var isCloudSimTest :Boolean = true

    var curType:CurNetworkType = CurNetworkType.NONE
    var testSub: Subscription? = null
    var vsimOkTime:Long = 0
    var ssimOkTime:Long = 0
    val VSIM_OK_WAIT_TIME:Long = 35 // unit:s
    val SSIM_OK_WAIT_TIME:Long = 35 // unit:s
    var netWorkType:Int = ConnectivityManager.TYPE_NONE
    var socketFailCnt = 0

    enum class CurNetworkType{
        NONE,
        WIFI,
        SEEDCARD,
        CLOUDCARD,
    }

    private fun unSubTest(){
        if(testSub != null && !(testSub as Subscription).isUnsubscribed){
            (testSub as Subscription).unsubscribe()
        }
    }

    private fun updateCurNetworkType(){
        var nextType:CurNetworkType = CurNetworkType.NONE
        if(isSeedOn){
            if(seedEnabler.getDeType() == DeType.WIFI){
                nextType = CurNetworkType.WIFI
                netWorkType = ConnectivityManager.TYPE_WIFI
            }else if(seedEnabler.getDeType() == DeType.SIMCARD){
                nextType = CurNetworkType.SEEDCARD
                if(cloudEnabler.isCardOn()){
                    netWorkType = ConnectivityManager.TYPE_MOBILE_DUN
                }else{
                    netWorkType = ConnectivityManager.TYPE_MOBILE
                }
            }
        }else if(isCloudOn){
            nextType = CurNetworkType.CLOUDCARD
            netWorkType = ConnectivityManager.TYPE_MOBILE
        }else{
            nextType = CurNetworkType.NONE
            netWorkType = ConnectivityManager.TYPE_NONE
        }

        if(curType != nextType){
            JLog.logd("type change, unsub $curType -> $nextType")
            curType = nextType
            unSubTest()
        }
    }

    fun init(context:Context, sEnabler: IDataEnabler, cEnabler: IDataEnabler){
        ctx = context;
        seedEnabler = sEnabler;
        cloudEnabler = cEnabler;

        seedEnabler.netStatusObser().subscribe(
                {
                    isSeedOn = (it == NetworkInfo.State.CONNECTED)
                    if(it == NetworkInfo.State.CONNECTED){
                        ssimOkTime = SystemClock.uptimeMillis()
                    }
                    updateCurNetworkType()
                }
        )

        cloudEnabler.netStatusObser().subscribe(
                {
                    isCloudOn = (it == NetworkInfo.State.CONNECTED)
                    if(it == NetworkInfo.State.CONNECTED){
                        JLog.logd("isCloudOn:$it")
                        vsimOkTime = SystemClock.uptimeMillis()
                    }
                    updateCurNetworkType()
                    if(it != NetworkInfo.State.CONNECTED){
                        socketFailCnt = 0
                    }
                }
        )

        cloudEnabler.cardStatusObser().subscribe(
                {
                    if(it <= CardStatus.READY){
                        isCloudSimTest = false
                    }
                }
        )

        ServiceManager.transceiver.statusObservable(ServerRouter.Dest.ASS)
                .subscribeWith {
                    onNext { socketStatus ->
                        when (socketStatus) {
                            "SocketConnected" -> {
                                if (isCloudOn){
                                    socketFailCnt = 0
                                }
                            }
                        }
                    }
                }

        ServiceManager.transceiver.exceptionStatusObservable(ServerRouter.Dest.ASS)
                .subscribe(
                        {
                            if (isCloudOn && !isSeedOn){
                                socketFailCnt++
                                JLog.logd("socket conn consecutive fails cnt:$socketFailCnt")
                                if (socketFailCnt >= 3){
                                    startNetworkTest()
                                    socketFailCnt = 0
                                }
                            }
                        }
                )

    }

    private fun networkTestRsultAction(result: Ncsi.NcsiResult, netType:CurNetworkType){
        if(result != Ncsi.NcsiResult.NETWORK_OK){
            // TODO:暂时没有启用种子卡的流程
//            if(netType == CurNetworkType.SEEDCARD || netType == CurNetworkType.WIFI){
//                seedEnabler.notifyEventToCard(DataEnableEvent.EVENT_NET_FAIL, result)
//            }
            if(netType == CurNetworkType.CLOUDCARD){
                cloudEnabler.notifyEventToCard(DataEnableEvent.EVENT_NET_FAIL, result)
            }
        }
    }

    fun startNetworkTest(){
        if(testSub != null && !(testSub as Subscription).isUnsubscribed){
            JLog.logd("network test is still running!")
            return
        }

        if(curType == CurNetworkType.SEEDCARD
                || curType == CurNetworkType.WIFI){
            if((SystemClock.uptimeMillis() - ssimOkTime) < TimeUnit.SECONDS.toMillis(SSIM_OK_WAIT_TIME)){
                logd("1111time is short " + (SystemClock.uptimeMillis() - ssimOkTime))
                return
            }
        } else if(curType == CurNetworkType.CLOUDCARD){
            if(isCloudSimTest) {
                loge("cloudsim is on, but is not the first time!")
                return
            }else{
                if((SystemClock.uptimeMillis() - vsimOkTime) < TimeUnit.SECONDS.toMillis(VSIM_OK_WAIT_TIME)){
                    logd("2222time is short " + (SystemClock.uptimeMillis() - vsimOkTime))
                    return
                }
            }
        }

        logd("startNetworkTest $curType $testSub $netWorkType")
        if(curType != CurNetworkType.NONE) {
            val isMobile:Boolean = (curType != CurNetworkType.WIFI)
            testSub = Ncsi.getInstance().startNetworkTest(isMobile, netWorkType)
                    .subscribeOn(Schedulers.io())
                    .timeout(60, TimeUnit.SECONDS)
                    .subscribe(
                            {
                                JLog.logd("network test result: $it")
                                if(curType == CurNetworkType.CLOUDCARD && !isCloudSimTest){
                                    isCloudSimTest = true
                                }
                                networkTestRsultAction(it, curType)
                            },
                            {
                                JLog.loge("recv exception!!!")
                                networkTestRsultAction(Ncsi.NcsiResult.DISCONNECTED, curType)
                            }
                    )
        }
    }

}