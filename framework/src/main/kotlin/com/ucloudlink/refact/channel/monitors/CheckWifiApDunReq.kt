package com.ucloudlink.refact.channel.monitors

import android.content.Context
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.provider.Settings
import android.provider.Settings.Global.TETHER_DUN_REQUIRED
import com.ucloudlink.refact.channel.enabler.DeType
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.ServiceManager
import rx.lang.kotlin.subscribeWith

/**
 * Created by pengchugang on 2017/1/12.
 */
object CheckWifiApDunReq {
    private var srcDunRequired:Int = -1
    private var mRegState:Boolean = false

    fun registerCardSub() {
        if (mRegState){
            return
        }
        mRegState = true
        srcDunRequired = getDunRequired()

        var seedSimEnable = ServiceManager.seedCardEnabler
        seedSimEnable.cardStatusObser().subscribeWith {
            onNext {
                logd("seedCardStatus :$it")
                if ((it == CardStatus.LOAD) || (it == CardStatus.READY)) {
                    checkDunRequired()
                }
                else if (it == CardStatus.IN_SERVICE){
                    if ((seedSimEnable.isDefaultNet()) && (seedSimEnable.getDeType() == DeType.SIMCARD)) {
                        /*FlowBandWidthControl.getInstance().setForwardWithObserverOrRecovery()
                        FlowBandWidthControl.getInstance().setLocalPackagesRestrict()// 设置后uid > 10000的都不能上网*/
                    }
                    else {
                        logd("seedSim isDefault:" + seedSimEnable.isDefaultNet() + "," + seedSimEnable.getDeType())
                    }
                }
                else if (it == CardStatus.ABSENT){
                    // annotation by 2017-09-23 修改为在云卡启动服务时，恢复限制, 在状态机开始时, 同样执行恢复限制
                    //FlowBandWidthControl.getInstance().restoreLocalPackagesRestrict()

                }
            }
            onError {
                logd("seedCardStatus sub failed: " + it.message)
            }
        }

        seedSimEnable.netStatusObser().subscribeWith {
            onNext {
                logd("seedNetStatus :$it")
                if (it == NetworkInfo.State.CONNECTED) {
                    if ((seedSimEnable.isDefaultNet()) && (seedSimEnable.getDeType() == DeType.SIMCARD)) {
                        /*FlowBandWidthControl.getInstance().setForwardRetrict()
                        FlowBandWidthControl.getInstance().setLocalPackagesRestrict()*/
                    }
                    else {
                        logd("seedSim isDefault:" + seedSimEnable.isDefaultNet() + "," + seedSimEnable.getDeType())
                    }
                }
                else if (it == NetworkInfo.State.DISCONNECTED){
                    //FlowBandWidthControl.getInstance().restoreLocalPackagesRestrict()
                }
            }
            onError {
                logd("seedCardStatus sub failed: " + it.message)
            }
        }

        val cloudSimEnabler = ServiceManager.cloudSimEnabler
        cloudSimEnabler.cardStatusObser().subscribeWith {
            onNext {
                logd("cloudCardStatus :$it")
                if ((it == CardStatus.LOAD) || (it == CardStatus.READY)) {
                    checkDunRequired()
                }
                else if (it == CardStatus.IN_SERVICE){
                    //FlowBandWidthControl.getInstance().restoreLocalPackagesRestrict()
                }
                else if (it == CardStatus.ABSENT){
                    restoreDunRequired()
                    // annotation by 2017-09-23 修改为在云卡启动服务时，恢复限制, 在状态机开始时, 同样执行恢复限制
                    //FlowBandWidthControl.getInstance().restoreLocalPackagesRestrict()
                }
            }
            onError {
                logd("cloudCardStatus sub failed," + it.message)
            }
        }

        cloudSimEnabler.netStatusObser().subscribeWith {
            onNext {
                logd("cloudNetStatus :$it")
                if (it == NetworkInfo.State.CONNECTED) {
                    //FlowBandWidthControl.getInstance().restoreLocalPackagesRestrict()
                }
                else if (it == NetworkInfo.State.DISCONNECTED){
                    //FlowBandWidthControl.getInstance().restoreLocalPackagesRestrict()
                }
            }
            onError {
                logd("cloudNetStatus sub failed: " + it.message)
            }
        }

        logd("finish registerCardSub.")
    }

    fun setDunRequired(m : Int) {
        var mcontext: Context = ServiceManager.appContext
        logd("setDunRequired: $m")
        try {
            Settings.Global.putInt(mcontext.getContentResolver(),
                    TETHER_DUN_REQUIRED, m)
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
    }

    fun getDunRequired() :Int{
        var mcontext: Context = ServiceManager.appContext
        var mMode = 0

        try {
            mMode = Settings.Global.getInt(mcontext.getContentResolver(), TETHER_DUN_REQUIRED)
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
            mMode = -1
        }
        logd("get dun required :$mMode")
        return mMode
    }

    fun  getWifiApState():Int{
        var mContext: Context = ServiceManager.appContext
        var wifiManager : WifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        var wifiApState:Int = wifiManager.getWifiApState();  //获取wifi AP状态
        logd("wifiApState: $wifiApState")
        return wifiApState
    }

    fun checkDunRequired():Int{
       // var mState :Int = getWifiApState()
       // if ((mState == WIFI_AP_STATE_ENABLING) || (mState == WIFI_AP_STATE_ENABLED)){
        var mVal :Int = getDunRequired()
        if (mVal != 0){
            srcDunRequired = mVal
            setDunRequired(0)
        }

        return 0
    }

    fun restoreDunRequired(){
        if ((srcDunRequired != -1) && (srcDunRequired != getDunRequired())){
            logd("restore dun required")
            setDunRequired(srcDunRequired)
        }
        return
    }
}