package com.ucloudlink.framework.tasks


import android.net.NetworkInfo
import android.os.*
import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.flow.*
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.business.netcheck.NetworkManager.getNetInfo
import com.ucloudlink.refact.business.netcheck.NetworkTest
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.*
import rx.lang.kotlin.subscribeWith
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

/**
 *   Created by jiaming.liang on 2016/8/11.
 */

object UploadFlowTask {

    val CCFLOWLOG_TAG = "CCFlowLog"

    private var logId: Int = 1

    //private var flowstats: CloudFlowCtrl = CloudFlowCtrl.getInstance()
    private val mFlowStatsCtrl by lazy { CloudFlowCtrl() }

    private var myAppUid: Int = 0
    val flowStatsSavePath: String = ServiceManager.appContext.getApplicationContext().applicationInfo.dataDir + "/flowStats2"
    private var uploadFlowIng: Boolean = false
    private var uploadLock = Any()
    private var cloudCardUpTimes = 0

    private var curSeedIfName: String? = null

    private var requestingUploadFlowPolicy = false
    /** 流量上传阈值，单位：byte */
    private var uploadFlowThreshold: Long = 0
    private var flowStatsReadPeriod: Long = 0
    private var uploadLastTime: Long = 0
    private var lastUploadTimestamp: Long = 0
    private val SUPPLYFLOW_MIN_BYTES = 10 * 1024   /*未上报流量低于10K不补报*/
    private var flowReachable: Boolean = true

    private val mCloudFlowCtrl by lazy { CloudFlowDataHolder() }
    private val mSeedFlowCtrl by lazy { SeedFlowDataHolder() }

    private val USER_NET_OK = 1
    private val UPLOAD_EVENT = 2
    private val UPLOAD_SCFLOW_DELAY_EVENT = 3
    private val CLOUD_SIM_IN_SERVICE = 41
    private val CLOUD_SIM_NET_CONNECTED = 42
    private val SCFLOW_START_EVENT = 61
    private val SCFLOW_STOP_EVENT = 62
    private val SCFLOW_IFACE_CHANGE_EVENT = 63
    private val MSG_CHECK_DNS_PARSE = 71
    private val MSG_NETWORK_STATE_CHANGE = 80
    private val MSG_CLOUD_SIM_CARD_STATUS = 81
    private val MSG_CLOUD_SIM_NET_STATUS = 82
    private val MSG_SEED_SIM_CARD_STATUS = 83
    private val MSG_SEED_SIM_NET_STATUS = 84

    private var mHandler: Handler? = null
    private var mHandlerThread = object : HandlerThread("UploadFlowTask Handler") {
        override fun onLooperPrepared() {
            logd("CCFlow FlowLog:mHandler init")
            mHandler = object : Handler(Looper.myLooper()) {
                override fun handleMessage(msg: Message?) {
                    logd("CCFlow FlowLog: msg.what = " + msg!!.what)
                    when (msg!!.what) {
                        MSG_CHECK_DNS_PARSE -> {// dns parse
                            if (ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED
                                    || ServiceManager.cloudSimEnabler.getCardState() == CardStatus.IN_SERVICE) {
                                FlowBandWidthControl.getInstance().iNetSpeedCtrl.checkDnsOnNewThread()
                            }
                        }
                        USER_NET_OK -> {// cloud sim net ok
                            checkLocalCacheFlowStats()
                            FlowBandWidthControl.getInstance().iNetSpeedCtrl.checkDnsOnNewThread()
                        }
                        UPLOAD_EVENT -> { // upload flow
                            uploadFlowStats(true)
                            refreshUploadFlowTimer()
                        }

                        UPLOAD_SCFLOW_DELAY_EVENT -> {// delay check upload seed sim flow
                            if (ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED
                                    /*|| ServiceManager.cloudSimEnabler.getCardState() == CardStatus.IN_SERVICE*/) {
                                SCFlowController.getInstance().uploadFlow(false)
                                mHandler?.removeMessages(UPLOAD_SCFLOW_DELAY_EVENT)
                                mHandler?.sendEmptyMessageDelayed(UPLOAD_SCFLOW_DELAY_EVENT, SCFlowController.MIN_UPLOAD_CHECK_TIMEMILLIS)
                            }
                        }

                        CLOUD_SIM_IN_SERVICE, CLOUD_SIM_NET_CONNECTED -> {// upload seed sim flow
                            if (ServiceManager.cloudSimEnabler.getNetState() == NetworkInfo.State.CONNECTED
                                    /*|| ServiceManager.cloudSimEnabler.getCardState() == CardStatus.IN_SERVICE*/) {
                                SCFlowController.getInstance().uploadFlow(false)
                                mHandler?.removeMessages(UPLOAD_SCFLOW_DELAY_EVENT)
                                mHandler?.sendEmptyMessageDelayed(UPLOAD_SCFLOW_DELAY_EVENT, SCFlowController.MIN_UPLOAD_CHECK_TIMEMILLIS)
                            }
                        }

                        SCFLOW_START_EVENT -> {// seed sim flow start
                            if (ServiceManager.seedCardEnabler.getCardState() == CardStatus.IN_SERVICE
                                    || ServiceManager.seedCardEnabler.getNetState() == NetworkInfo.State.CONNECTED) {
                                SCFlowController.getInstance().start(curSeedIfName
                                        , Configuration.username
                                        , ServiceManager.seedCardEnabler.getCard().imsi
                                        , NetworkManager.mccmnc
                                        , ServiceManager.seedCardEnabler.getCard().cardType.toString())
                            }
                        }

                        SCFLOW_STOP_EVENT -> {// seed sim flow stop
                            SCFlowController.getInstance().stop()
                        }

                        SCFLOW_IFACE_CHANGE_EVENT -> {// seed sim iface change
                            if(ServiceManager.seedCardEnabler.getCardState()==CardStatus.IN_SERVICE
                                    || ServiceManager.seedCardEnabler.getNetState() == NetworkInfo.State.CONNECTED){
                                SCFlowController.getInstance().start(curSeedIfName
                                        , Configuration.username
                                        , ServiceManager.seedCardEnabler.getCard().imsi
                                        , NetworkManager.mccmnc
                                        , ServiceManager.seedCardEnabler.getCard().cardType.toString())
                            }
                            SCFlowController.getInstance().checkIfNameChange(curSeedIfName)
                        }

                        MSG_SEED_SIM_CARD_STATUS -> {// seed sim card status change
                            if(msg.obj!=null && msg.obj is CardStatus){
                                seedSimCardStatusOnNext(msg.obj as CardStatus, ServiceManager.seedCardEnabler)
                            }
                        }

                        MSG_SEED_SIM_NET_STATUS -> {// seed sim net status change
                            if(msg.obj!=null && msg.obj is NetworkInfo.State){
                                seedSimNetStatusOnNext(msg.obj as NetworkInfo.State)
                            }
                        }

                        MSG_CLOUD_SIM_CARD_STATUS -> {// cloud sim card status change
                            if(msg.obj!=null && msg.obj is CardStatus){
                                cloudSimCardStatusOnNext(msg.obj as CardStatus)
                            }
                        }

                        MSG_CLOUD_SIM_NET_STATUS -> {// cloud sim net status change
                            if(msg.obj!=null && msg.obj is NetworkInfo.State){
                                cloudSimNetStatusOnNext(msg.obj as  NetworkInfo.State)
                            }
                        }

                        MSG_NETWORK_STATE_CHANGE -> {// receiver ROM broadcast card monitor status change
                            handlerMsgNetworkStateChange(msg)
                        }
                    }
                }
            }
            ServiceManager.simMonitor.addNetworkStateListen(networkListen)
            subscribeAll(ServiceManager.seedCardEnabler, ServiceManager.cloudSimEnabler)
        }
    }

    fun getFlowStatsCtrl(): CloudFlowCtrl {
        return mFlowStatsCtrl
    }

    fun checkDnsParse(){
        mHandler?.removeMessages(MSG_CHECK_DNS_PARSE)
        mHandler?.sendEmptyMessageDelayed(MSG_CHECK_DNS_PARSE, 0)
    }

    private var mPeriodTask: PeriodTask = object : PeriodTask() {
        override fun getDelayTime(): Long {
            return Configuration.FlowStatsReadPeriod
        }

        override fun getPeriodTime(): Long {
            return Configuration.FlowStatsReadPeriod
        }

        override fun onStart() {
            logd("CCFlow CloudFlowCtrl:start")
            initData()

            logd("CCFlow initData and stats logId:$logId.")
            refreshUploadFlowTimer()

            getFlowStatsCtrl().startStats()
        }

        override fun onStop() {
            logd("CCFlow task stop.")
            stopUploadFlowTimer()
            getFlowStatsCtrl().stopStats()
        }

        /**
         *如果云卡状态可用,连续尝试3次失败,报告接入层,流量上报失败
         *如果云卡不可用,流量上传任务暂停,当云卡变得可用时,马上继续执行
         */
        override fun taskRun() {
            pauseTask()
            if (uploadFlowStats(false)) {// 增量流量达到上报阈值检查
                refreshUploadFlowTimer()
            }
            resumeTask()
        }

    }

    private fun refreshUploadFlowTimer() {
        mHandler?.removeMessages(UPLOAD_EVENT)
        mHandler?.sendEmptyMessageDelayed(UPLOAD_EVENT, Configuration.UploadFlowPeriod)
    }

    private fun stopUploadFlowTimer() {
        mHandler?.removeMessages(UPLOAD_EVENT)
    }

    fun getSeedIfName(): String? {
        return curSeedIfName
    }

    fun saveLocalFlowStats(): Int {
        logd("CCFlow SupplyFlowLog task.saveLocalFlowStats, CacheFlow: ${mCloudFlowCtrl.mobileUpAdd}, ${mCloudFlowCtrl.mobileDownAdd}, $cloudCardUpTimes")
        if (cloudCardUpTimes == 0) {
            return 0
        }

        updateData()

        logd("CCFlow SupplyFlowLog task.saveLocalFlowStats -> after updateData -> saveLocalFlowStats,CacheFlow:${mCloudFlowCtrl.mobileUpAdd},${mCloudFlowCtrl.mobileDownAdd}, $cloudCardUpTimes")

        if ((mCloudFlowCtrl.mobileUpAdd > SUPPLYFLOW_MIN_BYTES) || (mCloudFlowCtrl.mobileDownAdd > SUPPLYFLOW_MIN_BYTES)) {
            logd("CCFlow SupplyFlowLog task.saveLocalFlowStats Stats logId = $logId")
            mCloudFlowCtrl.saveLocalFlowStats(logId, if(lastUploadTimestamp == 0L) System.currentTimeMillis() else lastUploadTimestamp)
        }

        return 0
    }

    fun getUploadFlowStatus(): Boolean {
        return uploadFlowIng
    }

    /**
     * logid:Int, usercode:CharSequence, sessionid:CharSequence, imis:CharSequence,
     * mcc:CharSequence, startTime:Long, userTx:Long, userRx:Long, sysTx:Long, sysRx:Long, endTime:Long
     */
    fun uploadSupplyFlowStats(fs: ArrayList<SupplemenUf>) {
        logi("CCFlow SupplyFlowLog uploadSupplyFlowStats content: $fs")
        Requestor.requestSupplemenUploadFlowsize(NetworkManager.loginNetInfo.imei.toLong(), fs).subscribe({
            if (it is SupplemenUploadFlowsizeResp) {
                if (it.errorCode == ErrorCode.RPC_RET_OK) {
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_SUCC)
                    getFlowStatsCtrl().resetTmpSd()
                    val fl = File(flowStatsSavePath)
                    if (fl.exists()) {
                        fl.delete()
                        logi("CCFlow SupplyFlowLog remove localStatsFiles")
                    }
                    logi("CCFlow SupplyFlowLog CloudFlowCtrl success: $it")
                } else {
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL, Exception(ErrorCode.RPC_HEADER_STR + it.errorCode))
                }
            } else {
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL, Exception(ErrorCode.PARSE_HEADER_STR + it.toString()))
            }
        }, {
            loge("CCFlow SupplyFlowLog CloudFlowCtrl fail: $it")
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL, it)
        })
    }

    fun initData() {
        logId = 1
        mCloudFlowCtrl.initData()
    }

    private val networkListen = CardStateMonitor.NetworkStateListen { ddsId, state, type, ifName, isExistIfNameExtra, subId ->
        val mHandlerMsg = HandlerMsg(ddsId
                , if(state==null) NetworkInfo.State.UNKNOWN else state
                , type
                , if(ifName==null) "" else ifName
                , isExistIfNameExtra, subId)

        mHandler?.obtainMessage(MSG_NETWORK_STATE_CHANGE , mHandlerMsg)?.sendToTarget()
    }

    private fun handlerMsgNetworkStateChange(msg: Message){
        if (msg.obj != null && msg.obj is HandlerMsg) {
            val mHandlerMsg = msg.obj as HandlerMsg

            val seedSimCardState = ServiceManager.seedCardEnabler.getCardState()
            val seedSimNetState = ServiceManager.seedCardEnabler.getNetState()
            val seedSimSubId = ServiceManager.seedCardEnabler.getCard().subId

            val cloudSimCardState = ServiceManager.cloudSimEnabler.getCardState()
            val cloudSimNetState = ServiceManager.cloudSimEnabler.getNetState()
            val cloudSimSubId = ServiceManager.cloudSimEnabler.getCard().subId

            JLog.logi("FlowLog, NetworkStateChange -> " + mHandlerMsg.toString() +
                    ", seedSim.cardState = $seedSimCardState" +
                    ", seedSim.netState = $seedSimNetState" +
                    ", seedSim.subId = $seedSimSubId" +
                    ", cloudSim.cardState = $cloudSimCardState" +
                    ", cloudSim.netState = $cloudSimNetState" +
                    ", cloudSim.subId = $cloudSimSubId" +
                    ", curSeedIfName = "+(if(curSeedIfName==null) "null" else curSeedIfName) +
                    ", cloudIfName = "+(if(getFlowStatsCtrl().cloudIfName==null) "null" else getFlowStatsCtrl().cloudIfName))


            if (seedSimCardState >= CardStatus.READY && seedSimSubId > -1) {
                if (mHandlerMsg.subId == seedSimSubId) {
                    curSeedIfName = mHandlerMsg.ifName
                    mHandler?.removeMessages(SCFLOW_IFACE_CHANGE_EVENT)
                    mHandler?.sendEmptyMessageDelayed(SCFLOW_IFACE_CHANGE_EVENT, 0)
                } else {
                    if(!TextUtils.isEmpty(mHandlerMsg.ifName) && !TextUtils.isEmpty(curSeedIfName) && mHandlerMsg.ifName.equals(curSeedIfName)){
                        curSeedIfName = null
                        mHandler?.removeMessages(SCFLOW_IFACE_CHANGE_EVENT)
                        mHandler?.sendEmptyMessageDelayed(SCFLOW_IFACE_CHANGE_EVENT, 0)
                    }
                }
            }

            if(cloudSimCardState >= CardStatus.READY && cloudSimSubId > -1){

                if(mHandlerMsg.subId == cloudSimSubId){
                    if(mPeriodTask.isRunning){
                        if(!TextUtils.isEmpty(mHandlerMsg.ifName) && mHandlerMsg.ifName.equals(getFlowStatsCtrl().cloudIfName)){
                            //do nothing
                        } else {
                            logi("FlowLog, cloudIfName change... task running"
                                    + ", curCloudIfName = "+(if(getFlowStatsCtrl().cloudIfName==null) "" else getFlowStatsCtrl().cloudIfName)
                                    + ", param ifName = " + (if(mHandlerMsg.ifName==null) "" else mHandlerMsg.ifName))
                            mPeriodTask.stop()
                            getFlowStatsCtrl().cloudIfName = mHandlerMsg.ifName
                            if(cloudSimNetState == NetworkInfo.State.CONNECTED
                                    || cloudSimCardState == CardStatus.IN_SERVICE){
                                mPeriodTask.start()
                            }
                        }
                    } else {
                        logi("FlowLog, cloudIfName change... task not running" )
                        getFlowStatsCtrl().cloudIfName = mHandlerMsg.ifName
                    }
                } else {
                    if(!TextUtils.isEmpty(mHandlerMsg.ifName) && !TextUtils.isEmpty(getFlowStatsCtrl().cloudIfName) && mHandlerMsg.ifName.equals(getFlowStatsCtrl().cloudIfName)){
                        if(mPeriodTask.isRunning){
                            logi("FlowLog, cloudIfName change... will be null ")
                            mPeriodTask.stop()
                            getFlowStatsCtrl().cloudIfName = null
                            if(cloudSimNetState == NetworkInfo.State.CONNECTED
                                    || cloudSimCardState == CardStatus.IN_SERVICE){
                                mPeriodTask.start()
                            }
                        } else {
                            getFlowStatsCtrl().cloudIfName = null
                        }
                    }
                }
            }
        }
    }

    fun init(seedSimEnable: IDataEnabler, cloudSimEnabler: IDataEnabler) {
        if (myAppUid != 0) {
            return
        }
        // 修改为先初始化mHandlerThread，mHandler创建成功后注册监听器,避免lateinit未初始化crash
        mHandlerThread.start()
    }

    private fun subscribeAll(seedSimEnable: IDataEnabler, cloudSimEnabler: IDataEnabler){
        seedSimEnable.cardStatusObser().subscribeWith {
            onNext {
                logd("SCFlow FlowLog: send MSG_SEED_SIM_CARD_STATUS,  $it" )
                mHandler?.obtainMessage(MSG_SEED_SIM_CARD_STATUS, it)?.sendToTarget()
                //seedSimCardStatusOnNext(it)
            }
            onError {
                loge("SCFlow FlowLog, TODO -> seedCardStatus sub failed: " + it.message)
            }
        }

        seedSimEnable.netStatusObser().subscribeWith() {
            onNext {
                logd("SCFlow FlowLog: send MSG_SEED_SIM_NET_STATUS,  $it " )
                mHandler?.obtainMessage(MSG_SEED_SIM_NET_STATUS, it)?.sendToTarget()
                //seedSimNetStatusOnNext(it)
            }
            onError {
                loge("SCFlow FlowLog, TODO -> seedNetWorkStatus:" + it.message)
            }
        }

        cloudSimEnabler.cardStatusObser().subscribeWith {
            onNext {
                logd("CCFlow FlowLog: send MSG_CLOUD_SIM_CARD_STATUS,  $it " )
                mHandler?.obtainMessage(MSG_CLOUD_SIM_CARD_STATUS, 0,0, it)?.sendToTarget()
                //cloudSimCardStatusOnNext(it)
            }
            onError {
                loge("CCFlow FlowLog, TODO -> cloudCardStatus sub failed," + it.message)
            }
        }

        cloudSimEnabler.netStatusObser().subscribeWith {
            onNext {
                logd("CCFlow FlowLog: send MSG_CLOUD_SIM_NET_STATUS,  $it " )
                mHandler?.obtainMessage(MSG_CLOUD_SIM_NET_STATUS, 0,0, it)?.sendToTarget()
                //cloudSimNetStatusOnNext(it)
            }
            onError {
                loge("CCFlow FlowLog, TODO -> cloudNetworkStatus sub failed," + it.message)
            }
        }

        myAppUid = SysUtils.getUServiceUid()
        logd("FlowLog, registerSubscription: uid " + myAppUid)
    }

    private fun seedSimCardStatusOnNext(state: CardStatus, seedSimEnable: IDataEnabler){
        logd("SCFlow FlowLog, <seed card> status change -> seedCardStatus = $state "
                + ", seedNetworkStatus = ${ServiceManager.seedCardEnabler.getNetState()}" )

        if (state == CardStatus.LOAD) {
        } else if (state == CardStatus.ABSENT) {
            disableSeedCard()
            mHandler?.removeMessages(SCFLOW_STOP_EVENT)
            mHandler?.sendEmptyMessageDelayed(SCFLOW_STOP_EVENT, 0)

        } else if (state == CardStatus.IN_SERVICE) {
            enableSeedCard()
            mHandler?.removeMessages(SCFLOW_START_EVENT)
            mHandler?.obtainMessage(SCFLOW_START_EVENT)?.sendToTarget()
        }

    }

    private fun seedSimNetStatusOnNext(state: NetworkInfo.State){
        logd("SCFlow FlowLog, <seed net> status change -> seedCardStatus = ${ServiceManager.seedCardEnabler.getCardState()}"
                + ", seedNetWorkStatus = $state")
        if (state == NetworkInfo.State.CONNECTED) {
            enableSeedCard()
            mHandler?.removeMessages(SCFLOW_START_EVENT)
            mHandler?.obtainMessage(SCFLOW_START_EVENT)?.sendToTarget()
        } else if (state == NetworkInfo.State.DISCONNECTED) {
            disableSeedCard()
            mHandler?.removeMessages(SCFLOW_STOP_EVENT)
            mHandler?.sendEmptyMessageDelayed(SCFLOW_STOP_EVENT, 0)
        }
    }

    private fun cloudSimCardStatusOnNext(state: CardStatus){
        logd("CCFlow FlowLog, <cloud card> status change -> cloudCardStatus = $state"
                +", cloudNetworkStatus = ${ServiceManager.cloudSimEnabler.getNetState()}")
        if (state == CardStatus.IN_SERVICE) {
            try {
                enableCloudCard()
                mPeriodTask.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            mHandler?.removeMessages(CLOUD_SIM_IN_SERVICE)
            mHandler?.sendEmptyMessageDelayed(CLOUD_SIM_IN_SERVICE, 0)

        } else if (state == CardStatus.ABSENT) {
            mHandler?.removeMessages(CLOUD_SIM_IN_SERVICE)
            mHandler?.removeMessages(CLOUD_SIM_NET_CONNECTED)
            mHandler?.removeMessages(UPLOAD_SCFLOW_DELAY_EVENT)

            try {
                disableCloudCard()
                mPeriodTask.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun cloudSimNetStatusOnNext(state: NetworkInfo.State){
        logd("CCFlow FlowLog, <cloud net> status change -> cloudCardStatus = ${ServiceManager.cloudSimEnabler.getCardState()}"
                +", cloudNetworkStatus = $state")
        if (state == NetworkInfo.State.CONNECTED) {
            try {
                enableCloudCard()
                uploadLastTime = SystemClock.uptimeMillis()
                lastUploadTimestamp = System.currentTimeMillis()
                mPeriodTask.start()
                cloudCardUpTimes++
                if (mPeriodTask.isRunning) {
                    mPeriodTask.resumeTask()
                    logd("CCFlow FlowLog, userChannel.netStatus is Connected, resume upload flow Task.")
                }
                val timeDelay: Long = if (Configuration.ApduMode == Configuration.ApduMode_Phy) 20 else 60
                mHandler?.sendEmptyMessageDelayed(USER_NET_OK, TimeUnit.SECONDS.toMillis(timeDelay))

            } catch (e: Exception) {
                e.printStackTrace()
            }

            mHandler?.removeMessages(CLOUD_SIM_NET_CONNECTED)
            mHandler?.sendEmptyMessageDelayed(CLOUD_SIM_NET_CONNECTED, 0)

        } else if (state == NetworkInfo.State.DISCONNECTED) {
            mHandler?.removeMessages(CLOUD_SIM_IN_SERVICE)
            mHandler?.removeMessages(CLOUD_SIM_NET_CONNECTED)
            mHandler?.removeMessages(UPLOAD_SCFLOW_DELAY_EVENT)

            try {
                logd("CCFlow SupplyFlowLog cloudNetStatus.DISCONNECTED -> saveLocalFlowStats, $cloudCardUpTimes")
                disableCloudCard()
                saveLocalFlowStats()
                mPeriodTask.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if ((state != NetworkInfo.State.CONNECTED) && mPeriodTask.isRunning) {
            mPeriodTask.pauseTask()
            logd("CCFlow FlowLog, userChannel.netStatus is Disconnected, pause upload flow Task.")
        }
    }

    fun updateData(): Int {
        //#26779 StatsData->StatsData?
        val stats : StatsData? = getFlowStatsCtrl().getStats(getLogId())
        var ret = 0

        mCloudFlowCtrl.updateData(stats)
        mSeedFlowCtrl.updateData(stats)

        if (stats == null) {
            logd("CCFlow SCFlow SupplyFlowLog, get invalid stats data.")
            ret = -1
        }
        return ret
    }

    private fun enableSeedCard() {
        if (getFlowStatsCtrl().getSeedSimCardStatus() == 1) {
            return
        }
        getFlowStatsCtrl().enableSeedSimCard()
    }

    private fun disableSeedCard() {
        if (getFlowStatsCtrl().getSeedSimCardStatus() == 0) {
            return
        }
        getFlowStatsCtrl().disableSeedSimCard()
    }

    private fun enableCloudCard() {
        if (getFlowStatsCtrl().getCloudSimCardStatus() == 1) {
            return
        }
        logd("FlowLog, enable cloudCard.")
        getFlowStatsCtrl().enableCloudSimCard()
    }

    private fun disableCloudCard() {
        if (getFlowStatsCtrl().getCloudSimCardStatus() == 0) {
            return
        }
        logd("FlowLog, disable cloudCard")
        getFlowStatsCtrl().disableCloudSimCard()
    }

    fun getSeedTxFlow(): Long{
        return getFlowStatsCtrl().getSeedTxFlow()
    }

    fun getSeedRxFlow(): Long {
        return getFlowStatsCtrl().getSeedRxFlow()
    }

    private fun getUploadFlowThreshold(): Long {
        if (uploadFlowThreshold == 0L) {
            requestUploadFlowPolicy()
            return Configuration.UploadFlowThreosHold
        }
        return uploadFlowThreshold
    }

    private fun getUploadFlowFrequency(): Long {
        if (flowStatsReadPeriod == 0L) {
            requestUploadFlowPolicy()
            return Configuration.UploadFlowPeriod
        }
        return flowStatsReadPeriod
    }

    fun getLogId(): Int {
        return logId
    }

    private fun incrLogId() {
        logId++
    }

    private fun getFlowReachableStatus(): Boolean {
        return flowReachable
    }

    private fun checkLocalCacheFlowStats(){
        val list: ArrayList<SupplemenUf> = mCloudFlowCtrl.getListLocalCacheFlowStats()
        if(list!=null && list.size > 0){
            JLog.logd("CCFlow SupplyFlowLog, checkLocalCacheFlowStats-> LocalStatsFiles records is not empty,send supplyment flow.")
            UploadFlowTask.uploadSupplyFlowStats(list)
        } else {
            JLog.logd("CCFlow SupplyFlowLog, checkLocalCacheFlowStats -> LocalStatsFiles records is empty ")
        }
    }

    /* true: upload  false: not upload*/
    @Synchronized fun uploadFlowStats(enforce: Boolean): Boolean {
        val ret: Int = updateData()
        val cloudCardStatus = ServiceManager.cloudSimEnabler.getCardState()
        val cloudNetworkStatus = ServiceManager.cloudSimEnabler.getNetState()


        logd("CCFlow FlowLog, uploadFlowStats, ret:$ret, enforce:${enforce}")
        if (ret != 0) {
            return false
        }

        if ((!enforce) && (!checkUploadFlowCondition())) {
            return false
        }
        if (cloudCardStatus != CardStatus.IN_SERVICE) {
            loge("CCFlow FlowLog, cloudCardStatus is not inservice:$cloudCardStatus")
        }
        if (cloudNetworkStatus != NetworkInfo.State.CONNECTED) {
            loge("FlowLog, cloudNetworkStatus is not connected:$cloudNetworkStatus")
        }
        if ((ret == 0) && (cloudCardStatus == CardStatus.IN_SERVICE)) {
            if (cloudNetworkStatus == NetworkInfo.State.CONNECTED) {//云卡可用时
                val imsi = com.ucloudlink.refact.ServiceManager.accessEntry.getCurImis()
                if(TextUtils.isEmpty(imsi)){// MIUI上有报imsi为空,imsi空时等待下次流量上报，下次上报时间为5s后
                    logd("CCFlow FlowLog, uploadFlowStats, imsi is Null ==== NOTE ====")
                    return false
                }
                if(uploadFlowIng){
                    logd("CCFlow FlowLog, uploadFlowStats, uploadFlowIng")
                    return false
                }
                uploadFlowIng = true

                val netinfo = getNetInfo(Configuration.cloudSimSlot)
                /**
                 * Requestor.requestUploadFlow3(NewFlowlogDTO3(getLogId(), userUpAdd, userUpTotal, userDownAdd, userDownTotal, sysUpAdd, sysDownAdd, seedFlow)).subscribe({
                 * String sessionId, Integer ufId, Long imsi, Long flowSizeup, Long flowSizedown, Long systemFlowSizeUp,
                 * Long systemFlowSizeDown, Long seedflowSizeUp, Long seedflowSizeDown,
                 * String plmn, Integer lac, Integer cid, Float longitude, Float latitude
                 */
                val uploadFlowsizeReq = UploadFlowsizeReq(logId, imsi.toLong(),
                        mCloudFlowCtrl.userUpAdd, mCloudFlowCtrl.userDownAdd, mCloudFlowCtrl.sysUpAdd, mCloudFlowCtrl.sysDownAdd
                        , mSeedFlowCtrl.seedUpAdd, mSeedFlowCtrl.seedDownAdd, NetworkManager.loginNetInfo.mccmnc, netinfo.lac, netinfo.cellid, 0.0, 0.0)

                Requestor.requestUploadFlow3(uploadFlowsizeReq).subscribe({
                    if (it is UploadFlowsizeResp) {
                        if (it.errorCode == ErrorCode.RPC_RET_OK) {
                            getFlowStatsCtrl().resetTmpSd()
                            incrLogId()
                            uploadLastTime = SystemClock.uptimeMillis()
                            lastUploadTimestamp = System.currentTimeMillis()
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_SUCC)
                            flowReachable = true
                        } else {
                            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL, Exception(ErrorCode.RPC_HEADER_STR + it.errorCode))
                        }
                    } else {
                        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL, Exception(ErrorCode.PARSE_HEADER_STR + it.toString()))
                    }
                    uploadFlowIng = false
                }, {
                    loge("CCFlow FlowLog, upLoadFlow fail: $it")
                    loge("CCFlow FlowLog, TODO save flow stats, logId:$logId,UU: ${mCloudFlowCtrl.userUpAdd}, ${mCloudFlowCtrl.userUpTotal}" +
                            ", UD:${mCloudFlowCtrl.userDownAdd}, ${mCloudFlowCtrl.userDownTotal}" +
                            ",SYS: ${mCloudFlowCtrl.sysUpAdd}, ${mCloudFlowCtrl.sysDownAdd}, ${mSeedFlowCtrl.seedFlow}")
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_C2SCMD_UPLOAD_FLOW_FAIL, it)
                    if (flowReachable) {
                        NetworkTest.startNetworkTest()
                    }
                    uploadFlowIng = false
                    flowReachable = false
                })

                return true
            }
        }
        return false
    }

    fun release() {
        logd("CCFlow SupplyFlowLog release -> saveLocalFlowStats, $cloudCardUpTimes")
        uploadFlowStats(true)
        saveLocalFlowStats()
        cloudCardUpTimes = 0
    }

    private fun checkUploadFlowCondition(): Boolean {
        val uploadFlowThreshold = getUploadFlowThreshold()
        val uploadFlowFrequency = getUploadFlowFrequency()
        val distanceTimeMills = SystemClock.uptimeMillis() - uploadLastTime
        val flowBytes = mCloudFlowCtrl.mobileUpAdd + mCloudFlowCtrl.mobileDownAdd

        logd("CCFlow FlowLog, flowBytes = $flowBytes, threshold = $uploadFlowThreshold"
                + ", frequency = $uploadFlowFrequency, distanceTimeMills = $distanceTimeMills ")

        if(distanceTimeMills > uploadFlowFrequency){
            return true
        }
        if (flowBytes >= uploadFlowThreshold) {
            return true
        }
        return false
    }

    fun forceUploadFlowWithHeartbeat() {
        uploadFlowStats(true)
        refreshUploadFlowTimer()
    }

    private fun requestUploadFlowPolicy() {
        logi("CCFlow FlowLog, requestUploadFlowPolicy mccmnc = ${NetworkManager.mccmnc}, requestingUploadFlowPolicy = $requestingUploadFlowPolicy")
        if(NetworkManager.mccmnc == null || NetworkManager.mccmnc.length < 3){
            return
        }
        if (requestingUploadFlowPolicy) {
            return
        }
        requestingUploadFlowPolicy = true
        Requestor.requestServiceList(NetworkManager.mccmnc.substring(0,3)).subscribe({
            if (it is QueryUserServiceListResp) {
                if (it.errorCode == ErrorCode.RPC_RET_OK) {
                    for (scv in it.serCodeValueList) {
                        for (param in scv.paramValues) {
                            if (param.param.equals("flow_upload_frequency")) {
                                flowStatsReadPeriod = TimeUnit.SECONDS.toMillis(param.value.toLong())
                            } else if (param.param.equals("flow_upload_threshold")) {
                                uploadFlowThreshold = param.value.toLong()
                            }
                        }
                    }
                } else {
                    loge("CCFlow FlowLog, requestUploadFlowPolicy fail: $it")
                }
            } else {
                loge("CCFlow FlowLog, requestUploadFlowPolicy fail: $it")
            }
            requestingUploadFlowPolicy = false
        }, {
            loge("CCFlow FlowLog, requestUploadFlowPolicy fail: $it")
            requestingUploadFlowPolicy = false
        })
    }


    private class HandlerMsg(var ddsId: Int, var state: NetworkInfo.State, var type: Int, var ifName: String
                             , var isExistIfNameExtra: Boolean, var subId: Int) {

        override fun toString(): String {
            return "HandlerMsg{" +
                    "ddsId=" + ddsId +
                    ", state=" + state +
                    ", type=" + type +
                    ", ifName='" + ifName + '\'' +
                    ", isExistIfNameExtra=" + isExistIfNameExtra +
                    ", subId=" + subId +
                    '}'
        }
    }


}
