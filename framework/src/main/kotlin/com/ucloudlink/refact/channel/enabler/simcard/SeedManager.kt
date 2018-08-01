package com.ucloudlink.refact.channel.enabler.simcard

import android.content.Context
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.SeedStatusSaveForCloudsim
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo
import com.ucloudlink.refact.channel.enabler.*
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.plmnselect.CLEAN_TEMP_FPLMN
import com.ucloudlink.refact.channel.enabler.plmnselect.SeedPlmnSelector
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.*
import rx.Observable
import rx.Subscription
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.PublishSubject
import java.util.*

/**
 * Created by shiqianhua on 2017/5/24.
 */
class SeedManager(mContext: Context, mLooper: Looper) : IDataEnabler {


    private val localLooper = mLooper
    private val localContext = mContext
    private val seedEnabler = ServiceManager.systemApi.getSeedEnabler(mContext, mLooper)
    private var cardList = ArrayList<Card>()
        set(value) {
            logd("cardList change old:$field -> new :$value")
            field = value
        }
    private var mcc: String? = null
    private var curIdx = 0
    private var netStateOb: Subscription? = null
    private var cardExceptOb: Subscription? = null
    private var cardStatusOb: Subscription? = null
    private var curCard: Card? = null
    private var lastCardStatus = CardStatus.ABSENT

    private val EVENT_ENABLE_CARD = 1
    private val EVENT_DISABLE_CARD = 2
    private val EVENT_DATA_CONNECT = 3
    private val EVENT_CARD_EXCEPTION = 4
    private val EVENT_CLOUDSIM_RESET_OVER = 5
    private val EVENT_CARD_STATUS_CHANGE = 6
    private val EVENT_WIAT_NET_TIMEOUT = 7

    private var isRunning = false
    private var isCardEnable = false
    private var isWaitingResetCloud = false
    private var phyWaitCount = 0
    private val PHY_WAIT_COUNT_MAX = 2
    private var isSoftsimTimeout = false
    private var mLastCard: Card? = null
    private var isInException = false
    private var lastException: EnablerException? = null
    private var isWaitNetwork: Boolean = false

    private val WAIT_FOR_NET_UPLOAD_TIMEOUT = 20 // unit:s

    private val cardManagerNetStateOb = BehaviorSubject<NetworkInfo.State>(NetworkInfo.State.DISCONNECTED)
    private val cardManagerExceptionOb = PublishSubject<EnablerException>()

    init {
        listenCardStatus()
    }

    private fun listenCardStatus() {
        logd("seed manager listen card status")
        listenNetAndException()

        cardStatusOb = seedEnabler.cardStatusObser().subscribe(
                {
                    logd("card status change! $it  last: $lastCardStatus  ${seedEnabler.getCard()}")
                    mHandler.obtainMessage(EVENT_CARD_STATUS_CHANGE, it).sendToTarget()
                },
                {
                    loge("cardStatusOb received exception! cannot run here")
                }
        )
    }

    private fun listenNetAndException() {
        netStateOb = seedEnabler.netStatusObser().subscribe(
                {
                    logd("netStatusObser change!!!" + it)
                    if (it == NetworkInfo.State.CONNECTED) {
                        mHandler.obtainMessage(EVENT_DATA_CONNECT).sendToTarget()
                    }
                    cardManagerNetStateOb.onNext(it)
                },
                {
                    loge("recv exception from netstatusOb cannot run here!! $it")
                    it.printStackTrace()
                }
        )

        cardExceptOb = seedEnabler.exceptionObser().subscribe(
                {
                    logd("recv seed enable exception!" + it)
                    mHandler.obtainMessage(EVENT_CARD_EXCEPTION, it).sendToTarget()
                },
                {
                    loge("seedEnabler.exceptionObser exception " + it + " cannot run here!!")
                }
        )
    }

    private fun disableSubCardStatus() {
        unsubscribeOb(netStateOb)
        unsubscribeOb(cardExceptOb)
//        unsubscribeOb(cardStatusOb)
    }

    private fun enableCardTask(card: Card) {
        var list = ArrayList<Card>()
        list.add(card)
        if (card.cardType == CardType.SOFTSIM) {
            Configuration.ApduMode = Configuration.ApduMode_soft
            ServiceManager.accessEntry.softsimEntry.softsimUpdateManager.curSoftsim = card.imsi
            ServiceManager.accessSeedCard.lastSoftsim = card.imsi
        } else {
            Configuration.ApduMode = Configuration.ApduMode_Phy
        }

//        if(card.cardType == CardType.PHYSICALSIM && Configuration.isPhyCardAvailable == false){
//            logk("skip physicalsim enable")
//            isCardEnable = true
//            mHandler.obtainMessage(EVENT_CARD_EXCEPTION, Configuration.phyCardUnavailableReason).sendToTarget()
//            return
//        }

        logk("enable card: $card")
        seedEnabler.enable(list)
        isCardEnable = true
    }


    private fun checkNeedResetCloudSim(lastCard: Card?, nextCard: Card):Boolean{
        logd("checkNeedResetCloudSim lastcard:$lastCard nextcard:$nextCard")
        if(ServiceManager.systemApi.switchSeedMode() == 0){
            return false
        }else if(ServiceManager.systemApi.switchSeedMode() == 1){
            if(lastCard!= null && lastCard.cardType == nextCard.cardType){
                return false
            }else if ((SeedStatusSaveForCloudsim.seedSaveType == SeedStatusSaveForCloudsim.SaveType.NONE)
                    || (curCard!!.cardType == CardType.PHYSICALSIM && SeedStatusSaveForCloudsim.seedSaveType == SeedStatusSaveForCloudsim.SaveType.PHYSIM)
                    || (curCard!!.cardType == CardType.SOFTSIM && SeedStatusSaveForCloudsim.seedSaveType == SeedStatusSaveForCloudsim.SaveType.SOFTSIM)) {
                return false
            } else {
                return true
            }
        }else if(ServiceManager.systemApi.switchSeedMode() == 2){
            // 当前种子卡没有到inservice的时候，需要关闭云卡，先让种子卡inservice
            val state = ServiceManager.systemApi.getSimState(Configuration.seedSimSlot)
            val imsi = ServiceManager.systemApi.getSubscriberIdBySlot(Configuration.seedSimSlot)
            val subId = ServiceManager.systemApi.getSubIdBySlotId(Configuration.seedSimSlot)
            var serviceState:ServiceState ? = null
            if(subId >= 0){
                serviceState = ServiceManager.systemApi.getServiceStateForSubscriber(subId)
            }
            logd("seed state $state imsi $imsi subid $subId serviceState:$serviceState")
            if(state == TelephonyManager.SIM_STATE_READY && serviceState != null && serviceState.dataRegState == ServiceState.STATE_IN_SERVICE){
                logd("seed sim is already inservice ,do not send msg to accessState")
                return false
            }else{
                logd("send msg to accessState to reset cloudsim!")
                return true
            }

        }else{
            loge("unknown mode ${ServiceManager.systemApi.switchSeedMode()}!!!")
            return false
        }
    }


    /**
     * 启动种子卡列表，第一次enable卡调用，启动第一张卡
     */
    private fun startCardList() {
        curIdx = 0
        curCard = cardList[0]

        logd("start card mode(${ServiceManager.systemApi.switchSeedMode()}), saveType(${SeedStatusSaveForCloudsim.seedSaveType}), card:" + curCard)
        val reset = checkNeedResetCloudSim(null, curCard!!)
        logd("checkNeedResetCloudSim return $reset")
        if(reset){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM)  //wait for the reset over msg! to enable phy card
        }else{
            enableCardTask(curCard!!)
        }
    }

//    private fun startNextCard():Int{
//        if(cardList.size <= curIdx + 1){
//            loge("no enough card $curIdx ${cardList.size}")
//            return -1
//        }
//        curIdx++
//        curCard = cardList[curIdx]
//        enableCardTask(curCard!!)
//        return 0
//    }

    private fun setNextCard(): Int {
        if (cardList.size <= curIdx + 1) {
            loge("no enough card $curIdx ${cardList.size}")
            return -1
        }
        curIdx++
        curCard = cardList[curIdx]
        return 0
    }

    private fun setFirstCard() {
        curIdx = 0
        curCard = cardList[curIdx]
    }

    private fun resetCloudsimForSwitchCard(entry:Int){
        logk("send reset cloudsim ! AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM entry $entry")
        ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM)  //wait for the reset over msg! to enable phy card
        isWaitingResetCloud = true
    }

    /**
     * return true, has other network
     */
    private fun checkOtherNetwork():Boolean{
        if (ServiceManager.phyCardWatcher.nwMonitor.hasAvailableNetwork()) {
            logd("has other network here!!!")
            return true
        }else{
            logd("donot have any other network here! exit")
            procException(EnablerException.EXCEPTION_NO_NETWORK_SIGNAL)
            return false
        }
    }

    private fun procWaitNetTimeout(){
        if(checkOtherNetwork()){
            resetCloudsimForSwitchCard(2)
        }
    }

    private fun changeToNextCard(lastCard: Card, nextCard: Card, except: EnablerException?) {
        logd("changeToNextCard $lastCard -> $nextCard  except:$except")
        logd("lastCardType ${lastCard.cardType} --> curCardType ${nextCard.cardType}")
        logd("save type:" + SeedStatusSaveForCloudsim.seedSaveType)

        if (lastCard.cardType == CardType.PHYSICALSIM && nextCard.cardType == CardType.SOFTSIM) {
            phyWaitCount = 0
            isSoftsimTimeout = false

//            if(except != null && except != EnablerException.EXCEPTION_UNSUPPORT_CDMA_PHY_CARD
//                    &&!ServiceManager.localPhySimState.hasAvaliableNetwork()){
//                logk("no available network, so donot use softsim!")
//                procException(EnablerException.EXCEPTION_NO_NETWORK_SIGNAL)
//                return
//            }
        }


        val reset = checkNeedResetCloudSim(lastCard, nextCard)
        logd("checkNeedResetCloudSim return $reset")
        if(reset){
            if (lastCard.cardType == CardType.SOFTSIM && nextCard.cardType == CardType.PHYSICALSIM) {
                ServiceManager.cardController.disableRSIMChannel(nextCard.slot, true)
            }
            if (lastCard.cardType == CardType.PHYSICALSIM && nextCard.cardType == CardType.SOFTSIM) {
                if (except != null) {
                    if(except == EnablerException.EXCEPTION_REG_DENIED) {
                        mHandler.sendEmptyMessageDelayed(EVENT_WIAT_NET_TIMEOUT, WAIT_FOR_NET_UPLOAD_TIMEOUT * 1000L)
                        isWaitNetwork = true
                        logd("card excpetion reg denied, so wait for 20s for net plmn upload!")
                        return
                    }else  if(except == EnablerException.INSERVICE_TIMEOUT){
                        if(!checkOtherNetwork()){
                            logd("no other network so wait!!!")
                            return
                        }
                    }
                }
            }
            resetCloudsimForSwitchCard(1)
        }else{
            if (lastCardStatus == CardStatus.ABSENT) {
                enableCardTask(nextCard)
            } else {
                logd("wait for card absent!!!")
            }
        }
    }

    private fun startCurCard() {
        logd("start startCurCard $isRunning $curCard")
        if (curCard == null) {
            startCardList()
        } else {
            enableCardTask(curCard!!)
        }
    }

    private fun clearCardParam() {
        cardList.clear()
        curIdx = 0
        curCard = null
        mLastCard = null
    }

    private fun disableCard(reason: String) {
        seedEnabler.disable(reason, false)
        mHandler.removeMessages(EVENT_WIAT_NET_TIMEOUT)
        isWaitNetwork = false
        isCardEnable = false
        clearCardParam()
        isRunning = false
    }

    private fun procException(except: EnablerException) {
        disableCard(except.toString())
        cardManagerExceptionOb.onNext(except)
    }

    private fun updateSoftsimUnusable(card: Card, except: EnablerException) {
        logd("update card exception! $except $card")

        if (true) {
            var mccmnc = NetworkManager.mccmnc
            if (mccmnc.length < 5) {
                val stringBuffer = StringBuffer("")
                stringBuffer.append(mccmnc)
                for (i in 0..(5 - mccmnc.length - 1)) {
                    stringBuffer.append("0")
                }
                mccmnc = stringBuffer.toString()
            }
            var errcode = if (except.reason.errorCode == 0) except.ordinal + 1000 else except.reason.errorCode
            ServiceManager.accessEntry.softsimEntry.updateSoftsimUnusable(card.imsi, errcode,
                    0, mccmnc.substring(0, 3), mccmnc.substring(3, 5))
            if (except == EnablerException.EXCEPTION_REG_DENIED) { // todo...............
                ServiceManager.accessEntry.softsimEntry.softsimUpdateManager.needUpdate = true
            }
        }
    }

    private fun isSoftsimInCardList(cardList: ArrayList<Card>): Boolean {
        for (card in cardList) {
            if (card.cardType == CardType.SOFTSIM) {
                return true
            }
        }
        return false
    }

    private fun isAllSoftsimSimDenied(cardList: ArrayList<Card>, errList: ArrayList<Int>): Boolean {
        var find = false
        var simInfo: SoftsimLocalInfo?
        for (card in cardList) {
            if (card != null && card.cardType == CardType.SOFTSIM && !TextUtils.isEmpty(card.imsi)) {
                simInfo = ServiceManager.accessEntry.softsimEntry.getSoftsimByImsi(card.imsi)
                if (simInfo != null) {
                    find = false
                    for (err in errList) {
                        for (unuse in simInfo.localUnuseReason) {
                            if (unuse.errcode == err) {
                                find = true
                                break
                            }
                        }
                        if (find) {
                            break
                        }
                    }
                    if (!find) {
                        return false
                    }
                }

            }
        }
        return true
    }

    private fun isExceptionTimeout(except: EnablerException): Boolean {
//        if (except == EnablerException.INSERVICE_TIMEOUT
//                || except == EnablerException.CONNECT_TIMEOUT) {
//            return true
//        }
        // 暂时废弃， 硬硬硬软这种模式
        return false
    }

    private fun noNextCardProcess(except: EnablerException) {
        if (isSoftsimInCardList(cardList)) {
            var except = EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM;
            var errList = ArrayList<Int>()
            errList.add(EnablerException.EXCEPTION_REG_DENIED.ordinal)
            if (isAllSoftsimSimDenied(cardList, errList)) {
                except.reason.errorCode = 1
            } else {
                except.reason.errorCode = 0
            }
            procException(EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM)
        } else {
            procException(except)
        }
        SeedPlmnSelector.updateEvent(CLEAN_TEMP_FPLMN,null)
    }

    private fun updateSoftsimErrInfo(card: Card, except: EnablerException): Boolean {
        if (card.cardType == CardType.SOFTSIM) {
            if (except == EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE) {
                updateSoftsimUnusable(card, except)
                return true
            }

            if (except == EnablerException.EXCEPTION_REG_DENIED) {
                updateSoftsimUnusable(card, except)
            }
            ServiceManager.accessEntry.softsimEntry.softsimUpdateManager.curSoftsim = ""
            if (isExceptionTimeout(except)) {
                isSoftsimTimeout = true
            }
        }
        return false
    }

    private fun updatePhyCardErrInfo(card: Card, except: EnablerException): Boolean {
        if (card.cardType == CardType.PHYSICALSIM && isExceptionTimeout(except) && isSoftsimTimeout) {
            if (phyWaitCount < PHY_WAIT_COUNT_MAX) {
                phyWaitCount++
                if (lastCardStatus == CardStatus.ABSENT) {
                    changeToNextCard(card, card, except)
                } else {
                    mLastCard = card
                }
                return true
            }
        }
        return false
    }

    private fun procCardException(except: EnablerException) {
        loge("get card exception: $except ${except.reason.errorCode} ${except.reason.reason} is running!" + isRunning)
        if (except == EnablerException.CLOSE_CARD_TIMEOUT
                || except == EnablerException.SIM_CRASH) {
            return
        }

        if (isRunning) {
            val lastCard = curCard!!
            logd("process card exception!  $isInException $lastCard")
            if (isInException) {
                logd("in exception, return")
                return
            }

            lastException = except

            if (except != EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE) {
                ServiceManager.accessSeedCard.clearLastSoftsim(lastCard.imsi)
            }

            if (updateSoftsimErrInfo(lastCard, except)
                    || except == EnablerException.EXCEPTION_REG_DENIED_NOT_DISABLE
                    || updatePhyCardErrInfo(lastCard, except)) {
                return
            }

//            if(except == EnablerException.EXCEPTION_REG_DENIED ||
//                    except == EnablerException.EXCEPTION_USER_PHY_ROAM_DISABLE ||
//                    except == EnablerException.EXCEPTION_PHY_CARD_MAY_UNAVAILABLE ||
//                    except == EnablerException.EXCEPTION_DATA_ENABLE_CLOSED){
//                if(curCard!!.cardType == CardType.PHYSICALSIM){
//                    Configuration.isPhyCardAvailable = false
//                    Configuration.phyCardUnavailableReason = except
//                }
//
//            }

            isInException = true
            val ret = setNextCard()
            if (ret != 0) {
                if (isSoftsimTimeout) {
                    setFirstCard()
                    if (lastCardStatus == CardStatus.ABSENT) {
                        changeToNextCard(lastCard, curCard!!, except)
                    } else {
                        mLastCard = lastCard
                    }
                } else {
                    loge("get no next card!")
                    noNextCardProcess(except)
                }
            } else {
                if (lastCardStatus == CardStatus.ABSENT) {
                    changeToNextCard(lastCard, curCard!!, except)
                } else {
                    mLastCard = lastCard
                }
            }
        } else {
            cardManagerExceptionOb.onNext(except)
        }
    }

    var mHandler = object : Handler(mLooper) {
        override fun handleMessage(msg: Message?) {
            //logd("seedmanager, isRunning(${isRunning}) process: $msg")
            when (msg!!.what) {
                EVENT_ENABLE_CARD -> {
                    //logd("new cardlist ${msg.obj}")
                    logd("old cardlist $cardList")
                    if (isRunning) {
                        logd("card is still running!" + "so wait for card enabled!!")
                        if (curCard != null) {
                            if (!seedEnabler.isClosing()) {
                                startCurCard()
                            }
                        }
                        return
                    }
                    cardList = msg.obj as ArrayList<Card>
                    if (cardList.size == 0) {
                        procException(EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM)
                        return
                    }
                    if (cardList.size > 1) {
                        ServiceManager.accessMonitor.sendMultiSeedMsg()
                    }
                    logd("last card status: $lastCardStatus")
                    if (lastCardStatus == CardStatus.ABSENT) {
                        startCurCard()
                    }
                    isRunning = true
                    lastException = null
                }
                EVENT_DISABLE_CARD -> {
                    logd("disable card $msg")
                    val reason = msg.obj as String
                    if (isRunning) {
                        lastException = null
                        isSoftsimTimeout = false
                        phyWaitCount = 0
                        logd("disable card :" + reason)
                        if (curCard != null && curCard!!.cardType == CardType.SOFTSIM) {
                            ServiceManager.accessEntry.softsimEntry.softsimUpdateManager.curSoftsim = ""
                        }
                        disableCard(reason)
                    } else {
                        loge("enable is not running!")
                    }
//                    if (reason == REASON_CHANGE) {
//                        disableSubCardStatus()
//                    }
                }
                EVENT_DATA_CONNECT -> {
                    logd("event data connect $msg")
                    // do nothing!
                }
                EVENT_CARD_EXCEPTION -> {
                    logd("recv EVENT_CARD_EXCEPTION " + msg.obj + " " + isCardEnable)
                    if (isCardEnable) {
                        procCardException(msg.obj as EnablerException)
                    }
                }
                EVENT_CLOUDSIM_RESET_OVER -> {
                    logd("event cloudsim reset over")
                    isWaitingResetCloud = false
                    if (lastCardStatus == CardStatus.ABSENT) {
                        if (isRunning) {
                            startCurCard()
                        } else {
                            logd("seed manager is not runnnig!")
                        }
                    } else {
                        loge("last card is not absent, so wait for it! ${seedEnabler.getCard()}")
                    }
                }
                EVENT_CARD_STATUS_CHANGE -> {
                    logd("event card status change ${msg.obj}")
                    val cardStatus = msg.obj as CardStatus

                    if (lastCardStatus != cardStatus) {
                        logk("seedcard card status change: $lastCardStatus -> $cardStatus")
                        lastCardStatus = cardStatus
                        logd("card get net status $cardStatus $curCard")
                        if (lastCardStatus == CardStatus.ABSENT) {
                            isInException = false
                            if (isRunning) {
                                if (isWaitingResetCloud) {
                                    loge("wait for reset cloudsim!!!")
                                } else {
                                    if (mLastCard != null) {
                                        changeToNextCard(mLastCard!!, curCard!!, lastException)  //TOOD 需要保存上次的错误
                                        mLastCard = null
                                    } else {
                                        startCurCard()
                                    }
                                }
                            } else {
                                logd("seed card enable is not running!")
                            }
                        }
                    }
                }
                EVENT_WIAT_NET_TIMEOUT -> {
                    logd("event wait net timeout!!")
                    procWaitNetTimeout()
                }
            }
        }
    }

    override fun netStatusObser(): Observable<NetworkInfo.State> {
        return cardManagerNetStateOb
    }

    override fun exceptionObser(): Observable<EnablerException> {
        return cardManagerExceptionOb;
    }

    override fun enable(cardList: ArrayList<Card>): Int {
        logd("enter enable! $cardList")
        mHandler.obtainMessage(EVENT_ENABLE_CARD, cardList).sendToTarget()

        return 0
    }

    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        logd("seedmanager isrunning($isRunning) disable:  $reason")
        if (!isRunning) {
            loge("is not running, no need disable!")
            return 0
        }
        mHandler.obtainMessage(EVENT_DISABLE_CARD, reason).sendToTarget()
        return 0
    }

    override fun cardStatusObser(): Observable<CardStatus> {
        logd("seedmanager cardStatusObser")
        return seedEnabler.cardStatusObser()
    }

    override fun cardSignalStrengthObser(): Observable<Int> {
        return seedEnabler.cardSignalStrengthObser()
    }

    override fun switchRemoteSim(card: Card): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCard(): Card {
        logd("seedmanager get card!")
        return seedEnabler.getCard()
    }

    override fun getCardState(): CardStatus {
        logd("seedmanager getCardState")
        return seedEnabler.getCardState()
    }

    override fun getNetState(): NetworkInfo.State {
        logd("seedmanager getNetState")
        return seedEnabler.getNetState()
    }

    override fun getDeType(): DeType {
        logd("seedmanager getDeType")
        return seedEnabler.getDeType()
    }

    override fun isCardOn(): Boolean {
        logd("seedmanager isCardOn $isRunning")
//        return seedEnabler.isCardOn()
        return isRunning
    }

    override fun isClosing(): Boolean {
        logd("seedmanager isClosing")
        return seedEnabler.isClosing()
    }

    override fun cloudSimRestOver() {
        logk("seedmanager cloudSimRestOver recv in " + this.javaClass.simpleName)
        mHandler.obtainMessage(EVENT_CLOUDSIM_RESET_OVER).sendToTarget()
    }

    override fun isDefaultNet(): Boolean {
        return seedEnabler.isDefaultNet()
    }

    override fun getDataEnableInfo(): DataEnableInfo {
        return seedEnabler.getDataEnableInfo()
    }

    private fun unsubscribeOb(sub: Subscription?) {
        if (sub != null && !sub.isUnsubscribed()) {
            sub.unsubscribe()
        }
    }

    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        seedEnabler.notifyEventToCard(event, obj)
        when (event) {
            DataEnableEvent.EVENT_ATTACH_ENABLER -> {
                disableSubCardStatus()
                listenNetAndException()
            }
            DataEnableEvent.EVENT_DETACH_ENABLER -> {
                disableSubCardStatus()
            }
        }
    }
}