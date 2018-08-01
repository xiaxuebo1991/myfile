package com.ucloudlink.refact.channel.enabler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.HandlerThread
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.SeedManager
import com.ucloudlink.refact.channel.enabler.wifi.WifiEnabler
import com.ucloudlink.refact.channel.monitors.WifiReceiver2
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.*
import com.ucloudlink.refact.utils.decodeApns
import rx.Observable
import java.util.*

/**
 * Created by shiqianhua on 2017/6/6.
 */
class AccessSeedCard(val context: Context): IDataEnabler {


    val initEnabler = getInitByWifi(context)
    val deProxy = DeProxy(context, initEnabler)
    //var lastOkImsi:String ?= null
    var lastSoftsim: String = ""
    var serviceEnable:Boolean = false
    var haveEnableBefore:Boolean = false

    init {
//        var subNetOb = deProxy.netStatusObser().subscribe(
//                {
//                    if(it == NetworkInfo.State.CONNECTED){
//                        logd("seed network connected!! so save seedsim!")
//                        if(deProxy.getDeType() == DeType.SIMCARD){
//                            lastOkImsi = deProxy.getCard().imsi
//                            logd("save seedsim imsi $lastOkImsi")
//                        }
//                    }
//                }
//        )
    }

//    fun clearLastOkImsi(){
//        logd("clear last ok imsi")
//        lastOkImsi = null
//    }
//
//    fun setSeedCardFail(imsi:String){
//        logd("imsi $imsi is not ok!")
//        if(imsi == lastOkImsi){
//            lastOkImsi = null
//        }
//    }


    fun clearLastSoftsim(imsi:String){
        logd("clearLastSoftsim")
        if(lastSoftsim == imsi) {
            lastSoftsim = ""
        }
    }
    
    fun clearLastSoftsimNoCheck(){
        lastSoftsim = ""
    }

    fun startService(){
        serviceEnable = true
        haveEnableBefore = false
    }

    fun stopService(){
        serviceEnable = false
    }

    private fun getInitByWifi(context: Context): IDataEnabler {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNwInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        logd("$wifiNwInfo")
        if (wifiNwInfo != null && wifiNwInfo.isConnected) {
            
            WifiReceiver2.wifiNetObser.onNext(NetworkInfo.State.CONNECTED)
            
            return WifiEnabler(context)
        } else {
            logd("return SeedManager")
            val seedHanler = HandlerThread("seed")
            seedHanler.start()
            return SeedManager(context, seedHanler.looper)
        }
    }

    private fun getCardListBySoftsimList(softsimList: ArrayList<SoftsimLocalInfo>?): ArrayList<Card> {
        if(softsimList == null || softsimList.size == 0){
            loge("softsim list is null!")
            return ArrayList<Card>()
        }else{
            var cardList = ArrayList<Card>()
            for(sim in softsimList){
                cardList.add(Card(slot= Configuration.seedSimSlot, cardType = CardType.SOFTSIM, ki = sim.ki, opc = sim.opc,
                        imsi = sim.imsi, rat = sim.rat, roamenable = sim.isRoam_enable,
                        apn = decodeApns(sim.apn, sim.imsi.subSequence(0,5).toString())))
            }
            return cardList
        }
    }

    private fun sortSoftsimList(softList: ArrayList<SoftsimLocalInfo>): ArrayList<SoftsimLocalInfo> {
        Collections.sort(softList, Comparator { lhs, rhs ->
            lhs.pri.compareTo(rhs.pri)
        })

        return softList
    }

    private fun sortSimlistWithLastSoftsim(cardList: ArrayList<Card>, sim:String?) : ArrayList<Card> {
        var cardTmp: Card
        if(sim == null || sim == ""){
            return cardList;
        }

        for(card in cardList){
            if (card.imsi == sim){
                cardList.remove(card)
                cardTmp = card
                cardList.add(0, card)
                return cardList
            }
        }

        return cardList
    }

    private fun stripPhySimInSimList(cardList: ArrayList<Card>):ArrayList<Card>{
        val intr = cardList.iterator()
        while (intr.hasNext()){
            val card = intr.next()
            if(card.cardType == CardType.PHYSICALSIM){
                intr.remove()
            }
        }

        return cardList
    }

    private fun stripAllSoftsimInSimList(cardList: ArrayList<Card>):ArrayList<Card>{
        val intr = cardList.iterator()
        while (intr.hasNext()){
            val card = intr.next()
            if(card.cardType == CardType.SOFTSIM){
                intr.remove()
            }
        }

        return cardList
    }

    private fun getPhyCardFirst(cardList: ArrayList<Card>):ArrayList<Card>{
        var cardTmp: Card

        for(card in cardList){
            if (card.cardType == CardType.PHYSICALSIM){
                cardList.remove(card)
                cardTmp = card
                cardList.add(0, card)
                return cardList
            }
        }

        return cardList
    }

    private fun hasSoftsimInList(cardList: ArrayList<Card>):Boolean{
        for(card in cardList){
            if(card.cardType == CardType.SOFTSIM){
                return true
            }
        }
        return false
    }

    private fun checkIsCDMA(subId: Int): Boolean {
        val telephonyManager = TelephonyManager.from(ServiceManager.appContext)
        val phoneType = telephonyManager.getCurrentPhoneType(subId)
        logd("getCurrentPhoneType subid $subId phoneType:$phoneType")

        return phoneType == TelephonyManager.PHONE_TYPE_CDMA
    }

    /**
     * 获取套餐，根据套餐类型生成对应的软卡列表，同时将上次使用的软卡优先级设置为最高
     */
    private fun enableCard():Int{
        logd("cur thread: " + Thread.currentThread().id)
        logd("current detype:" + deProxy.getDeType())

        var cardList = ArrayList<Card>()
        var curOrder = Configuration.orderId
        val ret = ServiceManager.productApi.processOrderInfo(curOrder,serviceEnable,haveEnableBefore,cardList)

        logd("get cardlist $cardList , $deProxy")
        if(ret != 0){
            return ret
        }else {
            //logd("start seedcard list: $cardList")
            var trueCardList:ArrayList<Card> = cardList

            if (trueCardList.size == 0) {
                loge("trueCardList size is 0  ------ 1")
                return ErrorCode.CARD_NO_AVAILABLE_SEEDCARD
            }

            if(hasSoftsimInList(trueCardList)) { // 有软卡才需要做策略处理，没有软卡则不处理
                // 如果没有物理卡相关的
                logd("last softsim $lastSoftsim")
                if (!lastSoftsim.isEmpty()) {
                    trueCardList = sortSimlistWithLastSoftsim(trueCardList, lastSoftsim)
                }
                //logd("after sort softsim $trueCardList")
                //如果物理卡不可用，则不使用物理卡
                val errcode = ServiceManager.phyCardWatcher.isPhyRealValid(Configuration.seedSimSlot)
                if (errcode != 0) {
                    trueCardList = stripPhySimInSimList(trueCardList)
                    if (trueCardList.size == 0) {
                        loge("trueCardList size is 0  ------ 2 $errcode")
                        return errcode
                    }
                    // 本国的情况下，深度优化开关关闭，sim卡在位，而且不是电信卡，则提示用户打开深度优化
                    val phySubId = ServiceManager.phyCardWatcher.getSubIdBySlot(Configuration.seedSimSlot)
                    if (!ServiceManager.phyCardWatcher.isCardRoam(Configuration.seedSimSlot)
                            && !Configuration.LOCAL_SEEDSIM_DEPTH_OPT
                            && ServiceManager.phyCardWatcher.isCardOn(Configuration.seedSimSlot)
                            && phySubId > 0
                            && !checkIsCDMA(phySubId)
                            && ServiceManager.systemApi.checkCardCanBeSeedsim(Configuration.seedSimSlot)) {
                        logd("Configuration.LOCAL_SEEDSIM_DEPTH_OPT so need to open it!")
                        return ErrorCode.SEED_CARD_DEPTH_OPT_CLOSE
                    }
                } else{
                    // 判断是否为本国
                    if(!ServiceManager.phyCardWatcher.isCardRoam(Configuration.seedSimSlot)){
                        logd("isCardRoam ${Configuration.seedSimSlot} false")
                        if(Configuration.LOCAL_SEEDSIM_DEPTH_OPT){
                            if (ServiceManager.phyCardWatcher.isPhyCardLocalOk(Configuration.seedSimSlot)) {
                                trueCardList = getPhyCardFirst(trueCardList)
                            }
                        }else{
                            trueCardList = stripAllSoftsimInSimList(trueCardList)
                            if (trueCardList.size == 0) {
                                loge("trueCardList size is 0  ------ 3 $errcode")
                                return ErrorCode.SEED_CARD_DEPTH_OPT_CLOSE
                            }
                        }
                    }
                }
            }

            logd("true card list: $trueCardList")
            if (serviceEnable && !haveEnableBefore) {
                haveEnableBefore = true
            }

            if(trueCardList.size == 0){
                loge("seed card unavailable!!!")
                return ErrorCode.CARD_NO_AVAILABLE_SEEDCARD
            }

            return deProxy.enable(trueCardList)
        }
    }

    private fun disableCard(reason: String, keepChannel: Boolean):Int{
        logk("stop seedcard: $reason")
        return deProxy.disable(reason,keepChannel)
    }

    override fun getDeType(): DeType {
        return deProxy.getDeType()
    }

    override fun enable(cardList: ArrayList<Card>): Int {
        return enableCard()
    }

    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        return disableCard(reason,isKeepChannel)
    }

    override fun getCardState(): CardStatus {
        return deProxy.getCardState()
    }

    override fun cardStatusObser(): Observable<CardStatus> {
        return deProxy.cardStatusObser()
    }

    override fun getNetState(): NetworkInfo.State {
        return deProxy.getNetState()
    }

    override fun netStatusObser(): Observable<NetworkInfo.State> {
        return deProxy.netStatusObser()
    }

    override fun exceptionObser(): Observable<EnablerException> {
        return deProxy.exceptionObser()
    }

    override fun switchRemoteSim(card: Card): Int {
        return deProxy.switchRemoteSim(card)
    }

    override fun getCard(): Card {
        return deProxy.getCard()
    }

    override fun isCardOn(): Boolean {
        return deProxy.isCardOn()
    }

    override fun isClosing(): Boolean {
        return deProxy.isClosing()
    }

    override fun isDefaultNet(): Boolean {
        return deProxy.isDefaultNet()
    }

    override fun cloudSimRestOver() {
        return deProxy.cloudSimRestOver()
    }
    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        deProxy.notifyEventToCard(event,obj)
    }

    override fun cardSignalStrengthObser(): Observable<Int> {
        return deProxy.cardSignalStrengthObser()
    }

    override fun getDataEnableInfo(): DataEnableInfo {
        return deProxy.getDataEnableInfo()
    }
}