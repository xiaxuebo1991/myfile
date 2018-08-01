package com.ucloudlink.refact

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.ucloudlink.framework.BuildConfig
import com.ucloudlink.framework.tasks.UploadFlowTask
import com.ucloudlink.refact.access.AccessEntry
import com.ucloudlink.refact.access.AccessMonitor
import com.ucloudlink.refact.access.AccessPersentMonitor
import com.ucloudlink.refact.access.ui.AccessEntryAdapter
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.cardprovisionstatus.CardProvisionStatus
import com.ucloudlink.refact.business.crossborder.CrossBorder
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.flow.PowerOffReceiver
import com.ucloudlink.refact.business.flow.netlimit.uiddnsnet.SeedCardNetRemote
import com.ucloudlink.refact.business.frequentauth.FrequentAuth
import com.ucloudlink.refact.business.keepalive.JobSchedulerIntervalTimeCtrl
import com.ucloudlink.refact.business.log.logcat.LogcatHelper
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.business.netcheck.NetworkTest
import com.ucloudlink.refact.business.performancelog.PerfLog
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType
import com.ucloudlink.refact.business.realtime.RealTimeManager
import com.ucloudlink.refact.business.smartcloud.SmartCloudController
import com.ucloudlink.refact.business.statebar.NoticeStatusBarServiceStatus
import com.ucloudlink.refact.business.wifitrigger.WifiNetTrigger
import com.ucloudlink.refact.channel.enabler.AccessSeedCard
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.ApnSetting
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.ICardController
import com.ucloudlink.refact.channel.enabler.simcard.watcher.PhyCardWatcher
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.channel.monitors.Monitor
import com.ucloudlink.refact.channel.transceiver.NettyTransceiver
import com.ucloudlink.refact.channel.transceiver.secure.SecureUtil
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.platform.qcom.business.qx.QxdmLogSave
import com.ucloudlink.refact.product.ProductForm
import com.ucloudlink.refact.systemapi.SystemApiIf
import com.ucloudlink.refact.systemapi.SystemApiInst
import com.ucloudlink.refact.systemapi.interfaces.ModelIf
import com.ucloudlink.refact.utils.HandlerThreadFactory
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.LoadFiles
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.utils.SleepWatcher
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SuppressLint("StaticFieldLeak")
/**
 * Created by chentao on 2016/6/23.
 */
object ServiceManager {
    lateinit var appThreadPool: ExecutorService
    lateinit var handlerThreadFactory: HandlerThreadFactory
    lateinit var apnCtrl: ApnSetting  //apn配置
    lateinit var simMonitor: CardStateMonitor  //sim卡监听器
    lateinit var subMnger: SubscriptionManager //卡管理
    lateinit var teleMnger: TelephonyManager
    lateinit var appContext: Context
    //    lateinit var seedTransceiver: Transceiver
    lateinit var seedCardEnabler: IDataEnabler
    lateinit var accessSeedCard: AccessSeedCard
    lateinit var cloudSimEnabler: IDataEnabler
    lateinit var handler: Handler
    lateinit var monitor: Monitor
    lateinit var accessEntry: AccessEntry
    lateinit var transceiver: NettyTransceiver
    lateinit var accessMonitor: AccessMonitor
    lateinit var phyCardWatcher: PhyCardWatcher
//    lateinit var localPhySimState:LocalPhyState
    lateinit var cardController: ICardController
    lateinit var systemApi:SystemApiIf
    lateinit var productApi:ProductForm
    lateinit var commHandlerThread:HandlerThread
    lateinit var sysModel:ModelIf

    fun initDependencies(context: Context, adapter:AccessEntryAdapter) {
        //获取系统版本
        logk("system time:" + SystemClock.uptimeMillis() + ", addd sleep:" + SystemClock.elapsedRealtime())
        logd("system info:BUILD_TIMESTAMP ${BuildConfig.BUILD_TIMESTAMP}, username:${BuildConfig.USER_NAME}, devname:${BuildConfig.DEV_NAME}")
//        logd("git commit info: ${BuildConfig.GIT_LAST_COMMIT}")

        /************** 初始化第一阶段 只做不依赖别的模块初始化，不做业务 ************************/
        appContext = context
        systemApi = SystemApiInst.getModelApi(context)
        sysModel = systemApi.getModelIf(context)
        Configuration.initDir(context)
//        val systemType = getSystemType()
//        Configuration.currentSystemVersion = systemType
        subMnger = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        teleMnger = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        handler = Handler()
        appThreadPool = Executors.newFixedThreadPool(15)
        handlerThreadFactory = HandlerThreadFactory()
        commHandlerThread = HandlerThread("commonHandler")
        commHandlerThread.start()


        productApi = systemApi.getProductObj(context)

        LoadFiles.loadFiles(context, systemApi)

        appThreadPool.execute {
            productApi.dataRecover()//需要再loadFiles之后
        }
        transceiver = NettyTransceiver() //netty
        SecureUtil.init(context, transceiver)
        SleepWatcher.start()
        SharedPreferencesUtils.init(context)
        systemApi.initPlatfrom(context)

        apnCtrl = ApnSetting(context)
        simMonitor = CardStateMonitor(context)

        JobSchedulerIntervalTimeCtrl.getInstance().initListener()
        regBroadcastReceiver()

        /********************** 第二阶段初始化，依赖第一阶段，会做一些业务 *************************/
        cardController = systemApi.getCardController()
        accessSeedCard = AccessSeedCard(context)
        seedCardEnabler = accessSeedCard
        val cloudHanler = HandlerThread("cloud")
        cloudHanler.start()
        cloudSimEnabler = systemApi.getCloudSimEnabler(context, cloudHanler.looper)
        NetworkManager.init(context, seedCardEnabler, cloudSimEnabler)
        NetworkTest.init(context, seedCardEnabler, cloudSimEnabler)
        UploadFlowTask.init(seedCardEnabler, cloudSimEnabler)
        SeedCardNetRemote.init(seedCardEnabler, cloudSimEnabler)
        Requestor.init(seedCardEnabler, cloudSimEnabler, Looper.myLooper())
        //SeedCardNetLimitHolder.getInstance().init(seedCardEnabler, cloudSimEnabler)
        FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mICloudFlowProtectionCtrl = systemApi.getICloudFlowProtectionCtrl()
        FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.mICloudFlowProtectionCtrl?.init(cloudSimEnabler)

        SoftSimStateMark.start()

        accessEntry = AccessEntry(context)
        phyCardWatcher = PhyCardWatcher(context, handlerThreadFactory.getShareLooper())

        PerfLog.init()
        adapter.setEntry(accessEntry)
        accessMonitor = accessEntry.accessMonitor
        NoticeStatusBarServiceStatus.registerCb()
        AccessPersentMonitor.accessPersentReg()
        monitor.startDDSMonitor()

        RealTimeManager.start()
        CardProvisionStatus.start()
        PreferredNetworkType.start()
        CrossBorder.start()

//        localPhySimState = LocalPhyState(context)

        productApi.init()
        SmartCloudController.getInstance()
//        sysModel = systemApi.getModelIf(context)
        sysModel.initModel()

        try {
            val imei = Configuration.getImei(appContext)
            logd("this phone imei:$imei")
        } catch (e: Exception) {
            loge("get phone imei failed!!!$e")
        }
        WifiNetTrigger.run()

        if (Build.MODEL == "MI MAX") {
            val PROPERTY_RADIO_MBN_UPDATE = "persist.radio.sw_mbn_update"

            val value = SystemProperties.get(PROPERTY_RADIO_MBN_UPDATE)
            if (!TextUtils.isEmpty(value)) {
                if (value == "1") {
                    SystemProperties.set(PROPERTY_RADIO_MBN_UPDATE, "0")
                }
            }
//            MbnLoad(context).enableAPPSMbnSelection()//小米的需要设置persist.radio.sw_mbn_update为1 才能自动load mbn
        }


        if (Configuration.MODEM_LOG_ENABLE) {
            ServiceManager.systemApi.startModemLog(QxdmLogSave.QXDM_APP_CMD, 0, null)
        }

        LogcatHelper.getInstance(context).addOnCrashListenr {
            cardController.EventCmd(cardController.obtainMessage(cardController.APP_CRASH_EVENT))
            Thread.sleep(200)
        }
        synchronized(adapter) {
            (adapter as java.lang.Object).notifyAll()
        }

        FrequentAuth.init()
    }

    fun getSystemType(): Int {
        /*val miuiVersionName = getSystemProperty("ro.miui.ui.version.name")
        logd("ro.miui.ui.version.name:$miuiVersionName")
        if(TextUtils.isEmpty(miuiVersionName)){
            return  Configuration.ANDROID_ORGIN
        }else if(miuiVersionName.equals("V8")){
            return Configuration.ANDROID_MIUI_V8
        }*/
        //val productModelName = getSystemProperty("ro.product.model")
        val productModelName = getPhoneModel()
        logd("ro.product.model:$productModelName")
        if (TextUtils.isEmpty(productModelName)) {
            logd("ro.product.model:ANDROID_ORGIN")
            return Configuration.ANDROID_ORGIN
        } else if (productModelName.equals("MI MAX")) {
            logd("ro.product.model:ANDROID_MIUI_V8")
            return Configuration.ANDROID_MIUI_V8
        } else if (productModelName.contains("C103")) {
            logd("ro.product.model:ANDROID_COOL_C103")
            return Configuration.ANDROID_COOL_C103
        } else if (productModelName.contains("C106")) {
            logd("ro.product.model:ANDROID_COOL_C106")
            return Configuration.ANDROID_COOL_C103
        } else if (productModelName.equals("msm8976 for arm64")) {
            logd("ro.product.model:ANDROID_ORGIN")
            return Configuration.ANDROID_ORGIN
        } else {
            val sysVerName = getSystemVersionName()
            if (sysVerName.contains("S1")) {
                logd("ro.fota.version:$sysVerName")
                return Configuration.ANDROID_COOL_C103
            }
            logd("ro.product.model:ANDROID_ORGIN")
            return Configuration.ANDROID_ORGIN
        }
    }

    private val SYSTEM_VERSION_FLAG = "ro.fota.version"

    /**
     * 获取系统版本名
     * @return
     */
    fun getSystemVersionName(): String {
        var verionName: String = ""
        try {
            verionName = getSystemProperty(SYSTEM_VERSION_FLAG)
            logd("ro.fota.version:$verionName")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            return verionName
        }
    }

    fun getSystemProperty(propertyName: String): String {
        val process = Runtime.getRuntime().exec("getprop $propertyName")
        process ?: return ""
        val reader = BufferedReader(InputStreamReader(process.inputStream), 1024)
        val result = reader.readLine()
        return result
    }

    fun getPhoneModel(): String {
        return Build.MODEL
    }

    fun regBroadcastReceiver(){
        val poweroffRecver = PowerOffReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SHUTDOWN)
        ServiceManager.appContext.registerReceiver(poweroffRecver, intentFilter)
    }
}



