package com.ucloudlink.refact.product.mifi.PhyCardApn


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemProperties
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.google.common.base.CharMatcher
import com.ucloudlink.framework.util.APN_TYPE_DEFAULT
import com.ucloudlink.framework.util.ApnUtil
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.channel.enabler.simcard.watcher.PhyCardState
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import rx.Subscription
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*

/**
 * Created by zhifeng.gao on 2018/1/31.
 */
class PhyCardApnSetting(context: Context){
    val TAG = "PhyCardApnSetting"
    val PARA_NUM = 7
    var defaultApns: ArrayList<Apn>? = null
    val PROPERTY_SUCC_APN = "persist.ucloud.succ.apn"
    var telephonyManager: TelephonyManager? = null
    var curApnId = 0
    var isSpeApn = false
    var isSetApn = false
    var currentApn = ""
    lateinit var networkStateSub:Subscription
    var mHandlerThread:HandlerThread
    lateinit var seedCardSub: Subscription
    var cxt:Context

    val errcodeCb = ApnNetworkErrCB { phoneId, errcode ->
        JLog.logd("networkErrUpdate errcode $errcode ")
        if (errcode == 33 && ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].state != CardStateMonitor.SIM_STATE_ABSENT) {
            mHandler.sendEmptyMessage(EVENT_CHANGE_APN)
        } else {
            JLog.logd("ignore")
        }
    }

    init {
        JLog.logk("init phyCardApnSetting")
        cxt = context
        mHandlerThread = HandlerThread("apnThread")
        mHandlerThread.start()
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        initDefaultApns(context)
        if(isCardLoad(ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot])){
            readApnFromSms()
        }else{
            cardStateListen()
        }
        ServiceManager.systemApi.registerNetworkErrCB(errcodeCb)

        ServiceManager.simMonitor.addNetworkStateListen( { ddsId, state, type, ifName, isExistIfNameExtra, subId ->

            if (NetworkInfo.State.CONNECTED == state && SubscriptionManager.getPhoneId(subId) == Configuration.seedSimSlot){
                JLog.logd("updateNetworkState: ddsId: $ddsId, state: $state, type: $type, ifName: $ifName, isExistIfaceExtra: $isExistIfNameExtra, subId = $subId")
                mHandler.sendEmptyMessage(EVENT_SAVE_APN_SYSTEMPRO)
            }
        })
    }

    private fun cardStateListen(){
        JLog.logk("cardStateListen : star cardState Listen")
        ServiceManager.simMonitor.addCardStateListen { slotId, subId, state ->
            if(slotId == Configuration.seedSimSlot){
                if(state == CardStateMonitor.SIM_STATE_LOAD){
                    JLog.logk("cardStateListen : start read apn")
                    readApnFromSms()
                }
            }
        }
    }

    fun readApnFromSms(){
        var smsList: ArrayList<SmsMessage>?
        var count:Int = 0
        JLog.logd(TAG, "readApnFromSms start !!!")

        smsList = getSmsMessageList()
        if(smsList == null){
            JLog.logd(TAG, "smsList is null")
            return
        }
        JLog.logd(TAG, "readApnFromSms: " + smsList + " count:" + smsList.size)

        for(sms in smsList ){
            count++
            JLog.logd(TAG, "apnsms " + count + " :" + sms.getMessageBody())
            var s1 :Array<String> = sms.getMessageBody().split(":").toTypedArray()
            if(s1.size < 2){
                JLog.loge(TAG, "parse sms failed!")
            }else {
                if(s1[0].equals("APNSET")) {
                    parseApnsFromSms(s1[1])
                }
            }
        }
    }

    fun getSmsMessageList(): ArrayList<SmsMessage>?{

        var getAllSmsName = "getAllMessagesFromIcc"
        var getAllMessagesFromIccMethod: Method? = null
        var smsList : ArrayList<SmsMessage>? = null

        var subid = 0
        val subList = SubscriptionManager.getSubId(Configuration.seedSimSlot)
        if (subList != null && subList.size != 0 && subList[0] > 0) {
            subid = subList[0]
            JLog.logd(TAG, "seedsim "+ Configuration.seedSimSlot + "get subid  $subid")
        } else {
            JLog.loge(TAG, "seedsim " + Configuration.seedSimSlot + "get subid  failure")
            return null
        }

        var sm: SmsManager = SmsManager.getSmsManagerForSubscriptionId(subid)

        if(sm == null){
            JLog.loge(TAG, "sm is null")
            return null
        }

        try{
            getAllMessagesFromIccMethod = sm.javaClass.getMethod(getAllSmsName)
            smsList =  getAllMessagesFromIccMethod.invoke(sm) as (ArrayList<SmsMessage>)
        }catch (e:NoSuchMethodException){
            e.printStackTrace();
        }catch (e:IllegalAccessException){
            e.printStackTrace();
        }catch (e: InvocationTargetException){
            e.printStackTrace();
        }finally {

        }
        return smsList;
    }

    fun parseApnsFromSms(apnStr: String) {
        var apnStr = apnStr
        JLog.logd(TAG, "parseApnsFromSms: apnstr:" + apnStr)
        //#30878 init到执行到此处时PhyCardWatcher中cardListen监听卡状态变更为absent,导致imsi为空
        if(!isCardLoad(ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot])){
            JLog.logd(TAG, "parseApnsFromSms: STOP,card state is "+ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].state)
            return
        }
        val imsi=ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].imsi ?: IMSI_DEFAULT
        val apns = decodeSimApns(imsi,apnStr)
        if (apns == null || apns.size == 0) {
            JLog.loge(TAG, "parseApnsFromSms: apn is null or size is 0," + apns!!)
            return
        }
        JLog.logd(TAG, "parseApnsFromSms: apns:" + apns)
        JLog.logd(TAG, "parseApnsFromSms: get the first apn!:" + apns[0])
        val apn = apns[0]

        val mCardSubId = ServiceManager.systemApi.getDefaultDataSubId()
        if (getSystemNetworkConn() && SubscriptionManager.getPhoneId(mCardSubId) == Configuration.seedSimSlot) {
            JLog.logd(TAG, "data is already connected!! do not set apn!")
            return
        }

        if (isContainsSepcailSingleApn(apn)) {
            isSpeApn = true
            val saveApn = SystemProperties.get(PROPERTY_SUCC_APN, "")
            var saveApnInfo: ArrayList<Apn>? = null
            JLog.logd(TAG, "parseApnsFromSms: save apn:" + saveApn!!)
            if (saveApn != null && saveApn.length > 0) {
                saveApnInfo = decodeSimApns(imsi,saveApn)
                JLog.logd(TAG, "decodeApns save apn:" + saveApnInfo!!)
            }

            if (saveApnInfo != null && isContainsSepcailApn(saveApnInfo)) {
                addApnToDatabase(saveApnInfo[0])
                curApnId = getSpecialApnIdx(saveApnInfo[0])
            } else {
                addApnToDatabase(apn)
                curApnId = getSpecialApnIdx(apn)
                isSetApn = true
            }
        } else {
            val succApn = SystemProperties.get(PROPERTY_SUCC_APN, "")
            if (succApn != apnStr) {
                addApnToDatabase(apn)
                isSetApn = true
                currentApn = apnStr
            } else {
                JLog.logd(TAG, "parseApnsFromSms: succ apn is sms apn!")
            }
        }
    }

    private fun isCardLoad(card: PhyCardState): Boolean {
        JLog.logd(TAG, "isCardLoad: $card")
        if(card==null)
            return false
        if(card.state != CardStateMonitor.SIM_STATE_LOAD)
            return false
        return true
    }

    /**
     * 种子卡拨号成功时将apn保存到系统属性中
     */
    fun saveSuccApnSystemPro(){
        var succApn = currentApn
        JLog.logd(TAG, "saveSuccApnSystemPro: isSpeApn $isSpeApn curApnId $curApnId")
        if (isSpeApn){
            if (curApnId == 0){
                succApn = "spe.inetd.gdsp,,,IP,,,"
            } else if(curApnId == 1){
                succApn = "ucloudlink,,,IP,,,"
            } else {
                JLog.logd(TAG, "saveSuccApnSystemPro: curApnId is invalid!!!")
                return
            }
        }
        JLog.logd(TAG, "saveSuccApnSystemPro: succApn $succApn set to SystemProperties")
        if (!TextUtils.isEmpty(succApn)){
            SystemProperties.set(PROPERTY_SUCC_APN, succApn)
        }
    }

    private fun getSystemNetworkConn(): Boolean {
        val connectivityManager = cxt.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connectivityManager.allNetworkInfo
        var connect = false
        if (networks != null && networks.size > 0) {
            for (i in networks.indices) {
                JLog.logd(TAG, "getSystemNetworkConn: count:" + i + " type:" + networks[i].typeName + " status:" + networks[i].state)
                if (networks[i].type == ConnectivityManager.TYPE_MOBILE && networks[i].state == NetworkInfo.State.CONNECTED) {
                    connect = true
                }
            }
        }
        return connect
    }

    private fun isContainsSepcailSingleApn(apn: Apn): Boolean {
        val apns = ArrayList<Apn>()
        apns.add(apn)
        return isContainsSepcailApn(apns)
    }

    private fun isContainsSepcailApn(apns: ArrayList<Apn>): Boolean {
        JLog.logd(TAG, "isContainsSepcailApn: cout:" + apns.size + "," + apns)
        for (a in apns) {
            JLog.logd(TAG, "isContainsSepcailApn: " + a.apn + " ucloudlink")
            if (a.apn.equals("ucloudlink")) {
                JLog.logd(TAG, "isContainsSepcailApn: apn:$a contains ucloudlink")
                return true
            }
            if (a.apn.equals("spe.inetd.gdsp")) {
                JLog.logd(TAG, "isContainsSepcailApn: apn:$a contains spe.inetd.gdsp")
                return true
            }
        }

        return false
    }

    fun addApnToDatabase(apn: Apn){
        val preferredApn_id = ApnUtil.InsertCloudSimApnIfNeed(ServiceManager.appContext, apn!!, ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].subId)
        if (preferredApn_id != null) {
            JLog.logd("apnid == "+preferredApn_id+"  subid == " + ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].subId)
            ApnUtil.selectApnBySubId(ServiceManager.appContext, preferredApn_id, ServiceManager.phyCardWatcher.mCardStateList[Configuration.seedSimSlot].subId, true)
        }
    }

    private fun getSpecialApnIdx(apn: Apn): Int {
        var idx = 0
        for (t in defaultApns!!) {
            if (t.apn.equals(apn.apn)) {
                return idx
            }
            idx++
        }
        if (idx >= defaultApns!!.size) {
            android.util.Log.e(TAG, "getSpecialApnIdx: cannot find apn in default apn!" + apn)
            return 0
        }
        return idx
    }

    private fun initDefaultApns(ctx: Context) {
        JLog.logk("init default apns")
        defaultApns = decodeSimApns(IMSI_DEFAULT,"spe.inetd.gdsp,,,IP,,,,ucloudlink,,,IP,,,")
        JLog.logk("default apns : "+defaultApns)
        for (apnInfo in defaultApns!!) {
            apnInfo.password = "20404"
            apnInfo.mcc = "204"
            apnInfo.mnc = "04"
        }
    }

    fun decodeSimApns(imsi:String?, apnStr: String): ArrayList<Apn>? {
        if (imsi == null){
            JLog.loge(TAG, "decodeSimApns imsi is null")
            return null
        }

        val apnParams = apnStr.split(",".toRegex()).toTypedArray()
        val apns = ArrayList<Apn>()
        if (apnParams.size < 1) {
            return null
        }
        //APN格式:APN,用户名,密码,身份验证类型(NULL 0 PAP 1 CHAP 2 PAPorCHAP 3),APN类型(default supl ia),APN协议(IP,IPV6,IPV4V6),APN漫游协议(IP,IPV6,IPV4V6),主DNS,副DNS
        //3gwap,,,3,default,IP,,,,3gnet,,,3,ia,IPV4V6,,,
        //一组apn数据8个逗号，所以apn组数可以按下面的方法算出来

        // 副版种子卡 6个逗号，7个数据
        // <APN>,<username>,<password>,<PDP_type>,<PDP_address>,<d_comp>,<h_comp>
        val cNum = CharMatcher.`is`(',').countIn(apnStr)
        val apnNum = (cNum + 1) / PARA_NUM
        val oldApnMode = false

        if (cNum < PARA_NUM - 1) {
            JLog.loge(TAG, "APN string error:" + apnStr)
            return null
        }

        for (i in 0 until apnNum) {
            val apn = Apn()
            apn.numeric = imsi.substring(0,5)
            apn.mcc = imsi.substring(0,3)
            apn.mnc = imsi.substring(3,5)
            apn.apn = apnParams[0 + i * PARA_NUM]
            apn.user = apnParams[1 + i * PARA_NUM]
            apn.password = apnParams[2 + i * PARA_NUM]
            val name = "VSim_Apn_" + i
            apn.name = name
            apn.type = APN_TYPE_DEFAULT
            apns.add(apn)
            JLog.logd(TAG, "decoded apn: $i,$apnNum : $apn")
        }
        return apns
    }

    fun ChangeNextApn() {
        JLog.logk("start change next apn")
        if (!isSpeApn) {
            JLog.loge(TAG, "ChangeNextApn: isSpeApn:" + isSpeApn)
            return
        }
        val nextid = if (curApnId == 0) 1 else 0
        JLog.logd(TAG, "ChangeNextApn: " + defaultApns!!.get(nextid))
        addApnToDatabase(defaultApns!!.get(nextid))
        curApnId = nextid
        isSetApn = true
    }

//    fun saveDatacallApn() {
//        if (isSetApn) {
//            if (isSpeApn) {
//                JLog.logd(TAG, "saveDatacallApn: succ apn:" + defaultApns!!.get(curApnId).getOrgValue())
//                SystemProperties.set(PROPERTY_SUCC_APN, defaultApns!!.get(curApnId).getOrgValue())
//            } else {
//                JLog.logd(TAG, "saveDatacallApn: succ apn:" + currentApn)
//                SystemProperties.set(PROPERTY_SUCC_APN, currentApn)
//            }
//        } else {
//            JLog.logd(TAG, "saveDatacallApn: " + isSetApn)
//        }
//    }

    private val EVENT_SAVE_APN_SYSTEMPRO =0
    private val EVENT_CHANGE_APN = 1

    val mHandler = object : Handler(mHandlerThread.looper) {
        override fun handleMessage(msg: Message?) {
            if (msg == null) return
            when (msg!!.what) {
                EVENT_SAVE_APN_SYSTEMPRO->saveSuccApnSystemPro()
                EVENT_CHANGE_APN->ChangeNextApn()
            }
        }
    }

    companion object {
        private const val IMSI_DEFAULT = "204040000000000"
    }
}