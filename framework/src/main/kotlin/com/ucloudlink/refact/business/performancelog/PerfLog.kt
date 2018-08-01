package com.ucloudlink.refact.business.performancelog

import android.content.*
import android.net.NetworkInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessState
import com.ucloudlink.refact.business.frequentauth.FrequentAuth
import com.ucloudlink.refact.business.performancelog.PerfUntil.saveEventToDatabase
import com.ucloudlink.refact.business.performancelog.PerfUntil.startUploadPerfLog
import com.ucloudlink.refact.business.performancelog.data.RebootCheck
import com.ucloudlink.refact.business.performancelog.db.DBHelper
import com.ucloudlink.refact.business.performancelog.logs.*
import com.ucloudlink.refact.business.performancelog.logs.PerfLogPownOn.SP_BOOT_POWER_LEFT
import com.ucloudlink.refact.business.performancelog.logs.PerfLogPownOn.SP_BOOT_TIME
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import rx.lang.kotlin.subscribeWith
import java.util.concurrent.TimeUnit


/**
 * Created by haiping.liu on 2018/1/12.
 */
object PerfLog {
    val MSG_CLOUDSIM_SUCCESS = 10001
    val MSG_CREATE_POWER_ON_EVENT_IF_NEED = 10002
    private val CLOUD_SUCCESS_DELAYED_TIME = 60L
    private var currentPersent = 0
    lateinit private var faHandler: Handler
    var WIFI_CONNET_CHANGED_ACTION = "android.net.wifi.WIFI_AP_CONNECTION_CHANGED_ACTION"
    var WIFI_EXTRA_MAC = "wifiap_station_mac"
    var WIFI_EXTRA_ISCONNECTED = "wifiap_station_is_connected"

    val  GLOCAL_ENBLE_KEY = "perflog_glocal_enble"//性能日志全局开关 key
    val  PERI_MR_INTVL_KEY = "perflog_glocal_enble"////周期测量时间间隔  单位 分钟 key
    val  ACESS_AB_TIMEOUT_KEY = "perflog_glocal_enble"//接入异常超时时间 单位 分钟 key

    var ip = 0
    var isFirstIn35 = false

    fun init() {
        logd("init()")

        DBHelper.instance().open(ServiceManager.appContext)

        var mHanderThread: HandlerThread = object : HandlerThread("PerfLog_HandlerThread") {
            override fun onLooperPrepared() {
                faHandler = object : Handler(Looper.myLooper()) {
                    override fun handleMessage(msg: Message) {
                        when (msg.what) {
                            MSG_CLOUDSIM_SUCCESS -> {
                                logd("mHandler rev : ---------------START UPLOAD!!!")
                                startUploadPerfLog()
                            }
                            MSG_CREATE_POWER_ON_EVENT_IF_NEED->{
                                logd("mHandler rev : ---------------MSG_CREATE_POWER_ON_EVENT_IF_NEED!")
                                creatPoweronEvent()
                            }
                        }
                    }
                }
            }
        }
        mHanderThread.start()
        PerfLogSsimEstFail.init(mHanderThread.looper)
        PerfLogSsimEstSucc.init(mHanderThread.looper)
        PerfLogSsimLogin.init(mHanderThread.looper)
        PerfLogVsimEstFail.init(mHanderThread.looper)
        PerfLogVsimEstSucc.init(mHanderThread.looper)
        PerfLogVsimResAllo.init(mHanderThread.looper)
        PerfLogVsimDelayStat.init(mHanderThread.looper)
        PerfLogTerAccess.init(mHanderThread.looper)
        PerfLogPownOn.init(mHanderThread.looper)
        PerfLogTerConnRel.init(mHanderThread.looper)
        PerfLogWifiUserChange.init(mHanderThread.looper)
        PerfLogVsimInterHO.init(mHanderThread.looper)
        PerfLogVsimMR.init(mHanderThread.looper)
        PerfLogPownOff.init(mHanderThread.looper)
        PerfLogDataEvent.init(mHanderThread.looper)
        PerfLogScreenEvent.init(mHanderThread.looper)
        PerfLogBigCycle.init(mHanderThread.looper)
        PerfLogSoftPhySwitch.init(mHanderThread.looper)
        registerCb()
        regMifiClientEventRev(ServiceManager.appContext)
        regScreenOnOffReceiver(ServiceManager.appContext)
        regBatteryReceiver(ServiceManager.appContext)
        ServiceManager.transceiver.statusObservable(ServerRouter.Dest.ASS)
                .subscribeWith { onNext {
                    socketStatus->
                    val persent = currentPersent
                    logd("socket status change:---------------------$socketStatus ,  persent=$persent")

                    when(socketStatus){
                        "SocketConnected"->{
                            socketConnectEvent(persent)
                            if (persent == 100){
                                PerfLogPownOff.create(PerfLogPownOff.ID_SOCKET_CONNECT,0,0)
                                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.EVENT_CLOUD_SOCKETOK, 0, "")
                            }
                            val mip = PerfUntil.getIPAddress(ServiceManager.appContext)
                            if (mip != 0){
                                ip = mip
                            }
                        }
                        "SocketDisconnected"->{
                            logd("SocketDisconnected isClosing= ${ServiceManager.cloudSimEnabler.isClosing()} ")
                            if(persent == 100){
                                PerfLogTerConnRel.create(PerfLogTerConnRel.ID_SOCKET_DISCONNECT,0,"")
                                PerfLogPownOff.create(PerfLogPownOff.ID_SOCKET_DISCONNECT,0,0)
                            }
                        }
                    }
                } }
        ServiceManager.cloudSimEnabler.cardStatusObser().subscribeWith {
            onNext { cardStatus ->
                val persent = currentPersent
                logd("cloud cardStatusObser --- :$cardStatus , persent=$persent")
                when (cardStatus) {
                    CardStatus.ABSENT -> {
                    }
                    CardStatus.INSERTED -> {
                    }
                    CardStatus.POWERON -> {
                        PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.VSIM_POWER_ON, 0, "")
                    }
                    CardStatus.READY -> {

                    }
                    CardStatus.LOAD -> {
                    }
                    CardStatus.OUT_OF_SERVICE -> {
                    }
                    CardStatus.EMERGENCY_ONLY -> {
                    }
                    CardStatus.IN_SERVICE -> {
                        if (persent >= 65 && persent < 90) {
                            PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.VSIM_INSERVICE, 0, "")
                        }
                    }
                }
            }
        }

        //BootReceiver 比 PerfLog.init()晚
        faHandler.sendEmptyMessageDelayed(MSG_CREATE_POWER_ON_EVENT_IF_NEED, TimeUnit.SECONDS.toMillis(30))
    }

    fun socketConnectEvent(persent: Int) {
        logd("socketConnectEvent persent=$persent isFirstIn35=$isFirstIn35")
        if (persent == 35 && isFirstIn35) {
            //一个周期只报一次，云卡鉴权，及异常恢复不报
            isFirstIn35 = false
            val data = ServiceManager.seedCardEnabler.getDataEnableInfo()
            PerfLogSsimEstSucc.create(0, 0, SsimEstSuccData(iccid = data.iccid, imsi = data.imsi, ip = data.ip, exception = data.lastException,
                    psReg = data.dataReg, csReg = data.voiceReg, psRoam = data.dataRoam, csRoam = data.voiceRoam))

        }
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
            val mPersent = persent
            if (mPersent != 100){
                PerfLogVsimMR.create(PerfLogVsimMR.ID_CLOUD_STOP,0,"")
                faHandler.removeMessages(MSG_CLOUDSIM_SUCCESS)
            }
            if (mPersent == 10) {
                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.SSIM_REG_START, 0, "")
            }
            if (mPersent == 20) {
                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.SSIM_REG_END_SOCKET_START, 0, "")
            }
            if (mPersent == 35) {
                isFirstIn35 = true
                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.SSIM_SOCKET_END, 0, "")
            }else{
                isFirstIn35 = false
            }
            if (mPersent == 65) {
                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.VSIM_DOWNLOAD_VSIM, 0, "")
            }
            if (mPersent == 75) {
                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.VSIM_REG_START, 0, "")
            }
            if (mPersent == 90) {
                PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.VSIN_DATA_CALL, 0, "")
            }

            if (mPersent == 100) {
                //云卡成功 即 将缓存eventList里面的事件保存到数据库
                saveEventToDatabase()
                faHandler.sendEmptyMessageDelayed(MSG_CLOUDSIM_SUCCESS, TimeUnit.SECONDS.toMillis(CLOUD_SUCCESS_DELAYED_TIME))
            }
            PerfLogVsimInterHO.create(persent,0,0)
        }

        override fun eventCloudSIMServiceStop(reason: Int, message: String?) {
            logd("eventCloudSIMServiceStop")
            PerfLogVsimDelayStat.create(PerfLogVsimDelayStat.END, 0, "")
            PerfLogTerAccess.create(PerfLogTerAccess.ID_CLOUD_STOP,0,"")
            PerfLogTerConnRel.createMsg(PerfLogTerConnRel.ID_ACCESS_EVENT_STOP,reason,if(message == null) "" else message)
            //云卡停止 将缓存eventList里面的事件保存到数据库
            saveEventToDatabase()
        }

        override fun eventCloudsimServiceSuccess() {
            logd("eventCloudsimServiceSuccess")
            PerfLogVsimMR.create(PerfLogVsimMR.ID_CLOUD_SUCCESS,0,"")
            val data = ServiceManager.cloudSimEnabler.getDataEnableInfo()
            PerfLogVsimEstSucc.create(0, 0, VsimSuccData(iccid = data.iccid, imsi = data.imsi, ip = data.ip, exception = data.lastException,
                    psReg = data.dataReg, csReg = data.voiceReg, psRoam = data.dataRoam, csRoam = data.voiceRoam,
                    upMbr = 0, downMbr = 0))
        }

        override fun eventSeedState(persent: Int) {
        }

        override fun eventSeedError(code: Int, message: String?) {
        }

        override fun errorUpdate(errorCode: Int, message: String) {
        }
    }

    fun regMifiClientEventRev(ctx: Context){
        logd("regMifiClientEventRev")
        val wifiClientEventRev = WifiClientEventRev()
        val fifter = IntentFilter(WIFI_CONNET_CHANGED_ACTION)
        ctx.registerReceiver(wifiClientEventRev,fifter )
    }

    //wifi 客户端上下线事件 todo s1无效
    class WifiClientEventRev : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logd("onReceive :"+intent?.getAction())
            if (intent != null){
                val action = intent.getAction()
                if (action == WIFI_CONNET_CHANGED_ACTION) {
                    val mac = intent.getStringExtra(WIFI_EXTRA_MAC)//格式：42:fc:89:a8:96:09
                    val isConnected = intent.getBooleanExtra(WIFI_EXTRA_ISCONNECTED, false)
                    logd("onReceive: mac=$mac , isConnected= $isConnected")
                }
            }
        }
    }

    fun getCurrentPersent():Int{
        return currentPersent
    }

    //全局开关变化 0-关 ，1-开
    fun glocalEnableChange(enable:Int){
        logd("glocalEnableChange enable=$enable")
        PerfLogPownOff.glocal_enble = enable
        PerfLogPownOn.glocal_enble = enable
        PerfLogSsimEstFail.glocal_enble = enable
        PerfLogSsimEstSucc.glocal_enble = enable
        PerfLogSsimLogin.glocal_enble = enable
        PerfLogTerAccess.glocal_enble = enable
        PerfLogTerConnRel.glocal_enble = enable
        PerfLogVsimDelayStat.glocal_enble = enable
        PerfLogVsimEstFail.glocal_enble = enable
        PerfLogVsimEstSucc.glocal_enble = enable
        PerfLogVsimInterHO.glocal_enble = enable
        PerfLogVsimMR.glocal_enble = enable
        PerfLogVsimResAllo.glocal_enble = enable
        PerfLogWifiUserChange.glocal_enble = enable
        PerfLogDataEvent.glocal_enble = enable
        PerfLogScreenEvent.glocal_enble = enable
        PerfLogBigCycle.glocal_enble = enable
        PerfLogSoftPhySwitch.glocal_enble = enable
        if (enable ==0){
            if (PerfLogVsimMR.perfLogRepeatTimeTask.isRunning){
                PerfLogVsimMR.perfLogRepeatTimeTask.stop()
            }
            if (PerfLogTerAccess.perfLogTimeTask.isRunning){
                PerfLogTerAccess.perfLogTimeTask.stop()
            }
        }
    }

    fun regScreenOnOffReceiver(ctx:Context){
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        ctx.registerReceiver(mScreenOnOffReceiver,filter)

    }

    var mScreenOnOffReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            logd("mScreenOnOffReceiver onReceive:$action")
            if (Intent.ACTION_SCREEN_ON == action) {
//                PerfLogScreenEvent.create(1,0,0)

                if (currentPersent == 100){
                    FrequentAuth.screenChange(true)
                }
            } else if (Intent.ACTION_SCREEN_OFF == action) {
//                PerfLogScreenEvent.create(2,0,0)

                if (currentPersent == 100){
                    FrequentAuth.screenChange(false)
                }
            }
        }
    }

    fun startUpload(){
        faHandler.sendEmptyMessage(MSG_CLOUDSIM_SUCCESS)
    }

    fun creatPoweronEvent(){
        val bootTime = SharedPreferencesUtils.getInt(ServiceManager.appContext,SP_BOOT_TIME)
        val power_left = SharedPreferencesUtils.getInt(ServiceManager.appContext, SP_BOOT_POWER_LEFT,-1)
        logd("creatPoweronEvent SP bootTime=$bootTime power_left=$power_left")
        SharedPreferencesUtils.putInt(ServiceManager.appContext, SP_BOOT_POWER_LEFT,-1)
        if (power_left != -1) {
            logd("creatPoweronEvent need")
            PerfLogPownOn.create(bootTime, power_left, "")
        }else{
            logd("creatPoweronEvent not need")
        }
    }

    fun regBatteryReceiver(ctx:Context){
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        ctx.registerReceiver(mBatteryReceiver,filter)
    }

    var mBatteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            logd("mBatteryReceiver onReceive:${intent.action}")
            if (intent.action.equals(Intent.ACTION_BATTERY_LOW)) {
                RebootCheck.saveLowBattery = true
            } else if (intent.action.equals(Intent.ACTION_BATTERY_OKAY)) {
                RebootCheck.saveLowBattery = false
            }
        }
    }
}