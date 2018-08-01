package com.ucloudlink.refact.business.softsim.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.*
import android.telephony.TelephonyManager
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEntry
import com.ucloudlink.refact.business.softsim.SoftsimEntry
import com.ucloudlink.refact.access.AccessPersentMonitor
import com.ucloudlink.refact.access.AccessPersentMonitor.STATUS_STOP
import com.ucloudlink.refact.access.AccessPersentMonitor.STATUS_SUCCESS
import com.ucloudlink.refact.business.netcheck.NetworkManager
import io.netty.util.internal.StringUtil
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by shiqianhua on 2017/7/13.
 */
class SoftsimUpdateManager(private val ctx: Context,
                           private val entry: AccessEntry,
                           softEntry: SoftsimEntry) {
//    var accStatePersentSub: Subscription? = null
    var lastPercent: Int = 0
    var networkOk: Boolean = false
    var curSoftsim: String = ""

    var lastUpdateTime: Long
        get() = SharedPreferencesUtils.getLong(ctx, "LAST_UPDATE_TIME", 0)
        set(value) {
            SharedPreferencesUtils.putLong(ctx, "LAST_UPDATE_TIME", value)
        }

    val MIN_UPDATE_INTVL = (10 * 60L) // s
    val SOFTSIM_UPDATE_INTVL = (12 * 60 * 60L) // s
    val PERSENT_100_LATE_TIME = (2 * 60L) // s

    val EV_NETWORK_CONNECT = 1
    val EV_ENTWORK_DISCONNECT = 2
    val EV_EV_INTVL_UPDATE = 3
    val EV_STATE_PERSENT_UPDATE = 4
    val EV_TRIGGER_UPDATE = 5
    val EV_PERSENT_100 = 6
    val EV_UPDATE_RIGHTNOW = 7

    var needUpdate = false

    private lateinit var mHandler: Handler
    private var mHandlerThread: HandlerThread = object : HandlerThread("SoftsimUpdateManager"){
        override fun onLooperPrepared() {
            mHandler = object : Handler(Looper.myLooper()) {
                override fun handleMessage(msg: Message?) {
                    var curTime: Long = 0
                    val accessState = AccessPersentMonitor.accessState
                    logd("accessState = "+accessState+"(1-stop,2-running,3-success) , msg!!.what="+msg!!.what)
                    if (accessState == STATUS_SUCCESS ||accessState == STATUS_STOP){
                        when (msg.what) {
                            EV_NETWORK_CONNECT -> {
                                curTime = Date().time
                                logd("network connect $curTime $lastUpdateTime $needUpdate")
                                if (curTime - lastUpdateTime > TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL) || needUpdate ) {
                                    lastUpdateTime = curTime
                                    logd("start to update softsim(EV_NETWORK_CONNECT)! $lastPercent $lastUpdateTime $curSoftsim")
                                    softEntry.startUpdateAllSoftsim(curSoftsim, getOperatorMcc(getOperatorNumeric()), getOperatorMnc(getOperatorNumeric()))
                                    removeMessages(EV_EV_INTVL_UPDATE)
                                    sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL))
                                    if(needUpdate){
                                        entry.accessState.updateCommMessage(1, "")
                                    }
                                }
                            }
                            EV_ENTWORK_DISCONNECT -> {

                        }
                        EV_EV_INTVL_UPDATE -> {
                            removeMessages(EV_EV_INTVL_UPDATE)
                            if (networkOk) {
                                lastUpdateTime = Date().time
                                // todo how to get mcc and mnc?
                                logd("start to update softsim(EV_EV_INTVL_UPDATE)! $lastPercent $lastUpdateTime $curSoftsim")
                                softEntry.startUpdateAllSoftsim(curSoftsim, getOperatorMcc(getOperatorNumeric()), getOperatorMnc(getOperatorNumeric()))
                            }
                            sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL))
                        }
                        EV_STATE_PERSENT_UPDATE -> {

                        }
                        EV_TRIGGER_UPDATE -> {
                            removeMessages(EV_EV_INTVL_UPDATE)
                            if (networkOk) {
                                lastUpdateTime = Date().time
                                // todo how to get mcc and mnc?
                                logd("start to update softsim(EV_TRIGGER_UPDATE)! $lastPercent $lastUpdateTime $curSoftsim")
                                softEntry.startUpdateAllSoftsim(curSoftsim, getOperatorMcc(getOperatorNumeric()), getOperatorMnc(getOperatorNumeric()))
                            }
                            sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL))
                        }
                            EV_PERSENT_100 ->{
                                curTime = Date().time
                                logd("EV_PERSENT_100: $curTime  $lastUpdateTime  $SOFTSIM_UPDATE_INTVL needUpdate=$needUpdate, networkOk=$networkOk")
                                if (networkOk){
                                    if (curTime - lastUpdateTime > TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL) || needUpdate ) {
                                        lastUpdateTime = curTime
                                        logd("start to update softsim(EV_PERSENT_100)! $lastPercent $lastUpdateTime $curSoftsim")
                                        softEntry.startUpdateAllSoftsim(curSoftsim, getOperatorMcc(getOperatorNumeric()), getOperatorMnc(getOperatorNumeric()))
                                        removeMessages(EV_EV_INTVL_UPDATE)
                                        sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL))
                                        if(needUpdate){
                                            entry.accessState.updateCommMessage(1, "")
                                        }
                                    }
                                }
                            }
                            EV_UPDATE_RIGHTNOW->{
                                    lastUpdateTime = curTime
                                    logd("start to update softsim(EV_UPDATE_RIGHTNOW)! $lastUpdateTime $curSoftsim")
                                    softEntry.startUpdateAllSoftsim(curSoftsim, getOperatorMcc(getOperatorNumeric()), getOperatorMnc(getOperatorNumeric()))
                                    removeMessages(EV_EV_INTVL_UPDATE)
                                    sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL))
                            }
                        }
                    }else{
                        removeMessages(EV_EV_INTVL_UPDATE)
                        sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL))
                    }
                }
            }
            addListener()
            val curTime = Date().time
            if (curTime - lastUpdateTime >= TimeUnit.SECONDS.toMillis(SOFTSIM_UPDATE_INTVL)) {
                mHandler.sendEmptyMessage(EV_EV_INTVL_UPDATE)
            } else {
                val intvl = if (curTime - lastUpdateTime > 0) (curTime - lastUpdateTime) else SOFTSIM_UPDATE_INTVL
                mHandler.sendEmptyMessageDelayed(EV_EV_INTVL_UPDATE, intvl)
            }
            /*
            synchronized(accEntry.isAccessStateLock) {
                accStatePersentSub = accEntry.getStatePersentOb().subscribe(
                        {
                            if (lastPercent != it) {
                                JLog.logd("accessstate persent change: $lastPercent -> $it")
                                lastPercent = it
                                mHandler.obtainMessage(EV_STATE_PERSENT_UPDATE, lastPercent).sendToTarget()
                            }
                        }
                )
            }*/
        }
    }

    private fun addListener() {
        ServiceManager.simMonitor.addNetworkStateListen{ ddsId, state, type, ifName, isExistIfNameExtra, subId ->
            JLog.logd("network change!! $ddsId, $state, $type, $ifName, $isExistIfNameExtra")
            if (type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_WIFI) {
                if (networkOk && state != NetworkInfo.State.CONNECTED) {
                    logd("network disconnected!")
                    networkOk = false
                    mHandler.obtainMessage(EV_ENTWORK_DISCONNECT, type).sendToTarget()
                } else if (!networkOk && state == NetworkInfo.State.CONNECTED) {
                    logd("network connected!")
                    networkOk = true
                    mHandler.obtainMessage(EV_NETWORK_CONNECT, type).sendToTarget()
                }
            }
        }
    }

    init {
        // 修改为先初始化mHandlerThread，mHandler创建成功后注册监听器,避免lateinit未初始化crash
        mHandlerThread.start()
    }

    fun updateSoftsimOver(result: Int, msg: String) {
        if (needUpdate) {
            needUpdate = false
            entry.accessState.updateCommMessage(2, if (result == 0) "succ" else "fail:$result,$msg")
        }
    }

    fun accessStateSuccess() {
        logd("accessStateSuccess")
        mHandler.removeMessages(EV_EV_INTVL_UPDATE)
        mHandler.sendEmptyMessageDelayed(EV_PERSENT_100, TimeUnit.SECONDS.toMillis(PERSENT_100_LATE_TIME))
    }

    fun updateSoftsim(){
        mHandler.sendEmptyMessage(EV_UPDATE_RIGHTNOW)
    }

    fun accessStateRunningOrStop() {
        logd("accessStateRunningOrStop")
        mHandler.removeMessages(EV_EV_INTVL_UPDATE)
    }

    private fun getOperatorNumeric(): String {
        val tm = NetworkManager.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.networkOperator
    }

    private fun getOperatorMcc(numeric:String):String{
        if(StringUtil.isNullOrEmpty(numeric) || numeric.length < 5){
            return ""
        }
        return numeric.substring(0,3)
    }

    private fun getOperatorMnc(numeric: String):String{
        if(StringUtil.isNullOrEmpty(numeric) || numeric.length < 5){
            return ""
        }
        return numeric.substring(3,5)
    }
}