package com.ucloudlink.refact.business.netcheck

import android.annotation.SuppressLint
import android.content.Context
import android.net.NetworkInfo
import android.os.SystemProperties
import android.telephony.*
import android.text.TextUtils
import com.android.internal.telephony.CellNetworkScanResult
import com.ucloudlink.framework.protocol.protobuf.PlmnInfo
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.access.TimeoutValue
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo.addScanNetworkPlmnList
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.channel.enabler.DeType
import com.ucloudlink.refact.channel.enabler.IDataEnabler
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.AssetUtils
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import com.ucloudlink.refact.utils.PackageUtils
import com.ucloudlink.refact.utils.ShellUtils
import rx.Single
import rx.Subscription
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import rx.schedulers.Schedulers
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


object NetworkManager {
    var context: Context by Delegates.notNull<Context>()
    lateinit var simEnabler: IDataEnabler
    lateinit var cloudEnabler: IDataEnabler
    lateinit var loginNetInfo: NetInfo
     var RatMapNum: HashMap<Int, Int>  ? = null
        get() {
            if(field==null){
                field=initRatMap()
            }
            return field
        }
     var RadioRatMapNum: HashMap<Int, Int> ? = null
        get() {
            if(field==null){
                field=initRadioRatMap()
            }
            return field
        }
     var SignalStrengthMapNum: HashMap<Int, Int> ? = null
        get() {
            if(field==null){
                field=initSignalMap()
            }
            return field
        }
    var plmnList: ArrayList<PlmnInfo> = ArrayList()
    var sidList: ArrayList<CharSequence> = ArrayList()
    const val MAX_SIDLIST_NUM: Int = 10
    const val Slot_IMEI: Int = 1
    var pollingASSIpSub: Subscription? = null
    var subsNetstatus: Subscription? = null
    var subCardStatus: Subscription? = null
    var subCardExcept: Subscription? = null
    val cloudSimLacObservable = PublishSubject<Int>()

    var mccmnc = ""

    private var mIsTracerouteRunning: Boolean = false
    private var mLastPercent: Int = 0

    fun init(ctx: Context, sEnabler: IDataEnabler, cEnabler: IDataEnabler) {
        context = ctx
        simEnabler = sEnabler
        cloudEnabler = cEnabler

        simEnabler.cardStatusObser().subscribeWith {
            onNext {
                if (simEnabler.getDeType() == DeType.SIMCARD) {
                    if (it == CardStatus.IN_SERVICE) {
                        val tmp = ServiceManager.systemApi.getNetworkOperatorForSubscription(simEnabler.getCard().subId)
                        if (isMccmncValid(tmp)) {
                            mccmnc = tmp
                            logd("mccmnc update seedsim: $mccmnc")
                        }
                    }
                }
            }
        }

        simEnabler.netStatusObser().subscribeWith {
            onNext {
                if (it == NetworkInfo.State.CONNECTED) {
                    val tmp = ServiceManager.systemApi.getNetworkOperatorForSubscription(simEnabler.getCard().subId)
                    if (isMccmncValid(tmp)) {
                        mccmnc = tmp
                        logd("mccmnc update seedsim: $mccmnc")
                    }
                }
            }
        }

        cloudEnabler.cardStatusObser().subscribeWith {
            onNext {
                if (it == CardStatus.IN_SERVICE) {
                    val tmp = ServiceManager.systemApi.getNetworkOperatorForSubscription(cloudEnabler.getCard().subId)
                    if (isMccmncValid(tmp)) {
                        mccmnc = tmp;
                        logd("mccmnc update cloudsim: $mccmnc")
                    }
                }
            }
        }

        cloudEnabler.netStatusObser().subscribeWith {
            onNext {
                if (it == NetworkInfo.State.CONNECTED) {
                    val tmp = ServiceManager.systemApi.getNetworkOperatorForSubscription(cloudEnabler.getCard().subId)
                    if (isMccmncValid(tmp)) {
                        mccmnc = tmp
                        logd("mccmnc update cloudsim: $mccmnc")
                    }
                }
            }
        }
    }

    fun isMccmncValid(mccmnc: String?): Boolean {
        if (mccmnc == null || mccmnc.length != 5) {
            return false
        }
        return true
    }

    fun clearMccMnc() {
        mccmnc = ""
    }

    fun initSignalMap(): HashMap<Int, Int>{
        var map=HashMap<Int, Int>()
        map.put(com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_NONE, com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_NONE)
        map.put(com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_POOR, com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_POOR)
        map.put(com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_MODERATE, com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_MODERATE)
        map.put(com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GOOD, com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GOOD)
        map.put(com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT, com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GREAT)
        map.put(com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT_MORE, com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_GREAT_MORE)
        return map
    }

    fun getSignalMap(signalStrength: Int): Int {
        if (signalStrength <= com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT_MORE && signalStrength >= com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_NONE) {
            return SignalStrengthMapNum!![signalStrength] as Int
        } else {
            return com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_UC_NONE
        }
    }

    fun initRatMap(): HashMap<Int, Int>{
        //将TelephonyManager细分的网络类型转化为宽泛的0-2G; 1-3G; 2-4G
        var map=HashMap<Int, Int>()
        map.put(TelephonyManager.NETWORK_TYPE_UNKNOWN, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(TelephonyManager.NETWORK_TYPE_GPRS, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(TelephonyManager.NETWORK_TYPE_EDGE, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(TelephonyManager.NETWORK_TYPE_UMTS, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_CDMA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(TelephonyManager.NETWORK_TYPE_EVDO_0, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_EVDO_A, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_1xRTT, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(TelephonyManager.NETWORK_TYPE_HSDPA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_HSUPA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_HSPA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_EVDO_B, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_EHRPD, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_LTE, com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE)
        map.put(TelephonyManager.NETWORK_TYPE_HSPAP, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_GSM, com.ucloudlink.refact.business.netcheck.RAT_TYPE_GSM)
        map.put(TelephonyManager.NETWORK_TYPE_TD_SCDMA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(TelephonyManager.NETWORK_TYPE_IWLAN, com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE)
        map.put(TelephonyManager.NETWORK_TYPE_LTE_CA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE)
        return map
    }

    fun getRatMap(rat: Int): Int {
        if (rat <= TelephonyManager.NETWORK_TYPE_LTE_CA && rat >= TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            return RatMapNum!![rat] as Int
        } else if (rat > TelephonyManager.NETWORK_TYPE_LTE_CA) {
            return com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE
        } else {
            return com.ucloudlink.refact.business.netcheck.RAT_TYPE_UNKNOW
        }
    }

    fun initRadioRatMap(): HashMap<Int, Int>{
        //将ril.h细分的网络类型转化为宽泛的0-2G; 1-3G; 2-4G
        var map=HashMap<Int, Int>()
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_UNKNOWN, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_GPRS, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_EDGE, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_UMTS, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_IS95A, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_IS95B, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_1xRTT, com.ucloudlink.refact.business.netcheck.RAT_TYPE_CDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_EVDO_0, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_EVDO_A, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_HSDPA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_HSUPA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_HSPA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_EVDO_B, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_EHRPD, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_LTE, com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_HSPAP, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_GSM, com.ucloudlink.refact.business.netcheck.RAT_TYPE_GSM)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_TD_SCDMA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_WCDMA)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_IWLAN, com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE)
        map.put(com.ucloudlink.refact.business.netcheck.RADIO_TECH_LTE_CA, com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE)
        return map
    }

    fun getRadioRatMap(radioRat: Int): Int {
        if (radioRat <= com.ucloudlink.refact.business.netcheck.RADIO_TECH_LTE_CA && radioRat >= com.ucloudlink.refact.business.netcheck.RADIO_TECH_UNKNOWN) {
//            return RadioRatMapNum!![radioRat] as Int
            return RadioRatMapNum!![radioRat] ?:com.ucloudlink.refact.business.netcheck.RAT_TYPE_UNKNOW
        } else if (radioRat > com.ucloudlink.refact.business.netcheck.RADIO_TECH_LTE_CA) {
            return com.ucloudlink.refact.business.netcheck.RAT_TYPE_LTE
        } else {
            return com.ucloudlink.refact.business.netcheck.RAT_TYPE_UNKNOW
        }
    }

    /**
     * 刷新周边网络信息
     */
    fun refreshNetworkInfo(): Single<NetInfo> {
        val netState = ServiceManager.seedCardEnabler.getNetState()
        logd("refreshNetworkInfo $netState")
        return Single.create<NetInfo> { sub ->
            if ((netState != NetworkInfo.State.CONNECTED)) {
                val ret = ServiceManager.seedCardEnabler.enable(ArrayList<Card>())
                logd("refreshNetworkInfo ret:$ret")
                if (ret != 0) {
                    sub.onError(Exception(ret.toString()))
                    return@create
                }
                subsNetstatus = simEnabler.netStatusObser().subscribeOn(Schedulers.newThread())
                        .timeout(TimeoutValue.getSeedCardConnectedTimeout(), TimeUnit.SECONDS).subscribeWith {
                    onNext {
                        if (it == NetworkInfo.State.CONNECTED) {
                            logd("refreshNetworkInfo ${simEnabler.getNetState()} 1")
                            var info: NetInfo = prepareNetInfo()
                            if (info.checkNetInfoParamValid()) {
                                sub.onSuccess(info)
                            } else {
                                sub.onError(Exception("GET_NETWORK_INFO_FAIL1"))
                            }
                        }
                    }
                    onError {
                        logd("refreshNetworkInfo onError $it")
                        sub.onError(it)
                    }
                    onCompleted {

                    }
                }

                subCardStatus = simEnabler.cardStatusObser().subscribeOn(Schedulers.newThread()).subscribeWith {
                    onNext {
                        //logd("recv card exception" + it)
                        //sub.onError(Exception(it.toString()))
                    }
                    onError {
                        logd("subCardStatus onError $it")
                        sub.onError(Exception(it.toString()))
                    }
                    onCompleted {

                    }
                }

                subCardExcept = simEnabler.exceptionObser().subscribe(
                        {
                            val code = ErrorCode.getErrCodeByCardExceptId(it)
                            logd("recv card exception! $it  -> code: $code")
                            sub.onError(Exception(code.toString()))
                        }
                )

            } else if (netState == NetworkInfo.State.CONNECTED) {
                logd("refreshNetworkInfo ${netState} 2")
                val info: NetInfo = prepareNetInfo()

                if (info.checkNetInfoParamValid()) {
                    sub.onSuccess(info)
                } else {
                    sub.onError(Exception("GET_NETWORK_INFO_FAIL2"))
                }
            }
        }.doOnUnsubscribe {
            if (subsNetstatus != null) {
                if (!(subsNetstatus as Subscription).isUnsubscribed) {
                    logd("unsubscriptSub2 netStatusObser")
                    (subsNetstatus as Subscription).unsubscribe()
                }
            }
            if (subCardStatus != null) {
                if (!(subCardStatus as Subscription).isUnsubscribed) {
                    logd("unsub subCardStatus")
                    (subCardStatus as Subscription).unsubscribe()
                }
            }
            if (subCardExcept != null) {
                if (!(subCardExcept as Subscription).isUnsubscribed) {
                    logd("unsub subCardStatus")
                    (subCardExcept as Subscription).unsubscribe()
                }
            }
        }
    }

    private fun prepareNetInfo(): NetInfo {
        sidList.clear()
        plmnList.clear()

        //搜网，增加附近运营商sidlist.
        if (Configuration.NeedScanNetwork) {
            scanNetworkForPlmnList()
        }
        //获取当前注册sim card信息imei,imsi,iccid,lac,cellid,plmn(mccmnc,strength,rat)
        return getNetInfo(Configuration.seedSimSlot)
    }

    /**
     * 搜网，比较耗费时间，需要2分钟左右。
     */
    fun scanNetworkForPlmnList() {
        logd("scanNetworkForPlmnList: start")
        val teleMnger = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val mSubscriptionManager = SubscriptionManager.from(context)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(Configuration.seedSimSlot)

        var getTimes1: Int = 0;

        while (mSubscriptionInfo == null && getTimes1++ < 30) {
            Thread.sleep(1000)
            logd("scanNetworkForPlmnList $getTimes1")
            mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(Configuration.seedSimSlot)
        }

        if (mSubscriptionInfo != null) {
            val cellNetworkScanResult: CellNetworkScanResult = teleMnger.getCellNetworkScanResults(mSubscriptionInfo.subscriptionId)
            val operators = cellNetworkScanResult.operators

            var i = 1
            for (operator in operators) {
                logd("scanNetworkForPlmnList: OperatorInfo " + i++ + ": operatorAlphaLong " + operator.operatorAlphaLong +
                        ", operatorAlphaShort " + operator.operatorAlphaShort +
                        ", operatorNumeric " + operator.operatorNumeric +
                        ", RadioTech " + operator.radioTech +
                        ", State " + operator.state)

                //TODO: 搜网信号强度还获取不到
                val sidInfo = operator.operatorNumeric + "," + SignalStrengthMapNum!![3] + "," + RadioRatMapNum!![operator.radioTech.toInt()]
                if (!sidList.contains(sidInfo)) {
                    sidList.add(sidInfo)
                    /*  var plmnmb = plmnItem(operator.operatorNumeric.substring(0, 3),
                              operator.operatorNumeric.substring(3, 5),
                              RadioRatMapNum[operator.radioTech.toInt()],
                              SignalStrengthMapNum[3])
                      plmnList.add(plmnmb)*/
                    var plmnmb = PlmnInfo(operator.operatorNumeric,
                            RadioRatMapNum!![operator.radioTech.toInt()],
                            SignalStrengthMapNum!![3], 3)  //TODO 网络频段参数不正确,目前暂未有获取该参数的方法
                    plmnList.add(plmnmb)
                    addScanNetworkPlmnList(plmnmb)
                }
            }

            logd("scanNetworkForPlmnList: $sidList")
        }
    }

    @SuppressLint("MissingPermission")
            /**
     * 获取当前网络信息如PLMN,CELLID,LAC等
     */
    fun getNetInfo(slot: Int): NetInfo {
        val teleMnger = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val mSubscriptionManager = SubscriptionManager.from(context)
        var mSubscriptionInfo = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        val subId: Int
        var iccid = ""
        var mccmnc = ""
        var rat: Int = -1
        var imsi = ""

        if (mSubscriptionInfo != null) {
            subId = mSubscriptionInfo.subscriptionId
            iccid = mSubscriptionInfo.iccId
            mccmnc = ServiceManager.systemApi.getNetworkOperatorForSubscription(subId)
            rat = teleMnger.getNetworkType(subId)
            imsi = teleMnger.getSubscriberId(subId) ?: ""
        }

        var imei = Configuration.getImei(context)
//        var cellid: Int = 0
//        var lac: Int = 0
        var cellid: Int = 1
        var lac: Int = 1 // TODO:先跑起来

        var signalStrength: Int = -1
        val cellInfos = teleMnger.allCellInfo
        if (cellInfos == null) {
            logd("cellinfos is null")
        } else {
            var i = 0
            while (i < cellInfos.size) {
                if (cellInfos[i].isRegistered) {
                    if (cellInfos[i] is CellInfoWcdma) {
                        val cellInfoWcdma: CellInfoWcdma = cellInfos[i] as CellInfoWcdma
                        val cellSignalStrengthWcdma: CellSignalStrengthWcdma = cellInfoWcdma.cellSignalStrength
                        signalStrength = cellSignalStrengthWcdma.level
                        val cellIdentityWcdma = cellInfoWcdma.cellIdentity
                        cellid = cellIdentityWcdma.cid
                        lac = cellIdentityWcdma.lac
                        logd("getNetInfo CellInfoWcdma cellid:$cellid ,lac:$lac")
                    } else if (cellInfos[i] is CellInfoGsm) {
                        val cellInfoGsm: CellInfoGsm = cellInfos[i] as CellInfoGsm
                        val cellSignalStrengthGsm: CellSignalStrengthGsm = cellInfoGsm.cellSignalStrength
                        signalStrength = cellSignalStrengthGsm.level
                        val cellIdentityGsm = cellInfoGsm.cellIdentity
                        cellid = cellIdentityGsm.cid
                        lac = cellIdentityGsm.lac
                        logd("getNetInfo CellInfoGsm cellid:$cellid ,lac:$lac")
                    } else if (cellInfos[i] is CellInfoLte) {
                        val cellInfoLte: CellInfoLte = cellInfos[i] as CellInfoLte
                        val cellSignalStrengthLte: CellSignalStrengthLte = cellInfoLte.cellSignalStrength
                        signalStrength = cellSignalStrengthLte.level
                        val cellIdentityLte = cellInfoLte.cellIdentity
                        cellid = cellIdentityLte.ci
                        lac = cellIdentityLte.tac
                        logd("getNetInfo CellInfoLte cellid:$cellid ,lac:$lac")
                    } else if (cellInfos[i] is CellInfoCdma) {
                        val cellInfoCdma: CellInfoCdma = cellInfos[i] as CellInfoCdma
                        val cellSignalStrengthCdma: CellSignalStrengthCdma = cellInfoCdma.cellSignalStrength
                        signalStrength = cellSignalStrengthCdma.level
                        val cellIdentityCdma = cellInfoCdma.cellIdentity
                        cellid = cellIdentityCdma.basestationId
                        lac = cellIdentityCdma.systemId
                        logd("getNetInfo CellInfoCdma cellid:$cellid ,lac:$lac")
                    }
                }
                i++
            }
        }

        logd("getNetInfo pre:" + "mccmnc=" + mccmnc + "signal=" + signalStrength + "rat=" + rat);
        if (rat > TelephonyManager.NETWORK_TYPE_LTE_CA) rat = TelephonyManager.NETWORK_TYPE_LTE_CA
        if (signalStrength > com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT_MORE) signalStrength = com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT_MORE

        loginNetInfo = NetInfo(mccmnc = mccmnc, rat = getRatMap(rat), signal = getSignalMap(signalStrength), imei = imei, cellid = cellid, lac = lac, imsi = imsi, iccid = iccid, version = getSystemVersionName() + "|" + PackageUtils.getAppVersionName(), sidList = sidList)

        //#30308 优化
        ServiceManager.appThreadPool.execute{
            if (slot == Configuration.seedSimSlot) {
                OperatorNetworkInfo.rat = loginNetInfo.rat
                OperatorNetworkInfo.imei = loginNetInfo.imei
                OperatorNetworkInfo.cellid = loginNetInfo.cellid
                OperatorNetworkInfo.lac = loginNetInfo.lac
                OperatorNetworkInfo.imsi = loginNetInfo.imsi
                OperatorNetworkInfo.iccid = loginNetInfo.iccid
                OperatorNetworkInfo.version = loginNetInfo.version
                OperatorNetworkInfo.signalStrength = loginNetInfo.signal
                OperatorNetworkInfo.mccmnc = loginNetInfo.mccmnc
                logd("getNetInfo on seed slot: $loginNetInfo")
                OperatorNetworkInfo.reflashSeedPlmnList()
            } else if (slot == Configuration.cloudSimSlot) {
                OperatorNetworkInfo.ratCloudSim = loginNetInfo.rat
                OperatorNetworkInfo.imeiCloudSim = loginNetInfo.imei
                OperatorNetworkInfo.cellidCloudSim = loginNetInfo.cellid
                OperatorNetworkInfo.lacCloudSim = loginNetInfo.lac
                OperatorNetworkInfo.imsiCloudSim = loginNetInfo.imsi
                OperatorNetworkInfo.iccidCloudSim = loginNetInfo.iccid
                OperatorNetworkInfo.signalStrengthCloudSim = loginNetInfo.signal
                OperatorNetworkInfo.mccmncCloudSim = loginNetInfo.mccmnc
                logd("getNetInfo on cloud slot: $loginNetInfo")
                OperatorNetworkInfo.reflashCloudPlmnList()
            }
        }

        return loginNetInfo
    }

    /**
     * 使用BusyBox的Traceroute工具对当前网络状态进行分析
     * 使用Filter：“TraceRoute|AssetUtils|ShellUtils|System.out”进行log过滤
     */
    @JvmOverloads
    fun tracerouteToService(serviceIp: String = ServerRouter.current_RouteIp) {
        val tag = "TraceRoute"
        if (mIsTracerouteRunning || ServiceManager.accessMonitor.curPercent == mLastPercent) {
            logd(tag, "Traceroute is Running $mIsTracerouteRunning at $mLastPercent, IGNORE")
            return
        }
        mIsTracerouteRunning = true
        mLastPercent = ServiceManager.accessMonitor.curPercent
        Single.create<Long> {
            logd(tag, "Traceroute to service IP = $serviceIp")
            val startTime = System.currentTimeMillis()
            val appPath = context.filesDir.absolutePath + File.separator + "traceroute"
            if (!File(appPath).exists()) {
                // 从Asset拷贝traceroute到APP的file目录
                AssetUtils.copyAssetFileToAppPath(context, "traceroute")
            }
            // 允许执行权限
            ShellUtils.execCmd("chmod 777 $appPath")
            val ip: String = if (serviceIp == ServerRouter.ROUTE_IP_BUSSINESS) {
                ServerRouter.ROUTE_IP_BUSSINESS_BACKUP
            } else {
                serviceIp
            }
            // 执行traceroute命令
            ShellUtils.execCmd(".$appPath -m 64 -n ${ip.substringBefore(':')}")
            it.onSuccess(System.currentTimeMillis() - startTime)
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    logd(tag, "Traceroute finished in $it ms.")
                    mIsTracerouteRunning = false
                }, {
                    loge(tag, "Traceroute meet error: ${it.message}")
                    mIsTracerouteRunning = false
                })
    }

    /**
     * 限制sidList最大数量
     */
    private fun checkListNum(LimitCount: Int) {

        while (this.sidList.size > LimitCount) {
            this.sidList.removeAt(this.sidList.size - 1)
        }

        while (this.plmnList.size > LimitCount) {
            this.plmnList.removeAt(this.plmnList.size - 1)
        }
    }

    /**
     * 返回当前系统版本名
     */
    const val SYSTEM_VERSION_FLAG = "ro.build.display.id"

    fun getSystemVersionName(): String {
        var verionName: String = ""
        try {
            // --- get the system version ---
            verionName = SystemProperties.get(SYSTEM_VERSION_FLAG, "")
            if(!TextUtils.isEmpty(verionName)){
                if(verionName.contains(" ")){
                    verionName = verionName.substring(0,verionName.indexOf(" "))
                }
                logd("getSystemVersionName: $verionName")
            }
        } catch (e: Exception) {
            logd("getSystemVersionName: Exception$e")
            verionName = ""
        } finally {
            return verionName
        }
    }
}