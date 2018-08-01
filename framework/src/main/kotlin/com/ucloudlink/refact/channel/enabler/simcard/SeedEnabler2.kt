package com.ucloudlink.refact.channel.enabler.simcard

import android.content.Context
import android.net.NetworkInfo
import android.os.Looper
import android.os.SystemClock
import android.telephony.ServiceState
import android.telephony.ServiceState.STATE_IN_SERVICE
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.SIM_STATE_NOT_READY
import android.telephony.TelephonyManager.SIM_STATE_READY
import android.text.TextUtils
import com.ucloudlink.framework.util.APN_TYPE_DEFAULT
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.ServiceManager.cardController
import com.ucloudlink.refact.ServiceManager.phyCardWatcher
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.AccessEventId.EVENT_SOFTSIM_OFF
import com.ucloudlink.refact.access.AccessEventId.EVENT_SOFTSIM_ON
import com.ucloudlink.refact.business.cardprovisionstatus.CardProvisionStatus
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.plmnselect.SeedPlmnSelector
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.ENV_MCC_CHANGED
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.PhoneStateUtil
import rx.Subscription
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by jiaming.liang on 2016/12/9.
 */
abstract class SeedEnabler2(mContext: Context, mLooper: Looper) : CardDeBase(mContext, mLooper) {

    var staRefreshSub: Subscription? = null
    private fun unsubscribestaRefreshSub() {
        //if (staRefreshSub != null && !staRefreshSub.isUnsubscribed() == 1) {
        if ((staRefreshSub != null) && !((staRefreshSub as Subscription).isUnsubscribed)) {
            (staRefreshSub as Subscription).unsubscribe()
        }
    }

    private val MIN_SOFTSIM_ENABLE_INTERVAL = 10 * 1000L

    protected open var lastCloseSoftSimTime: Long = 0

    protected open val INSERT_TIMEOUT: Long = 10 * 1000

    protected open var isCloudSimOn: Boolean = false
        get() {
            return ServiceManager.cloudSimEnabler.getCardState() >= CardStatus.READY
        }

    var temp_nextCard: ArrayList<Card>? = null

    private val superEnable = Runnable {
        val card = temp_nextCard
        card ?: return@Runnable
        super.enable(card)
    }

    override fun enable(cardList: ArrayList<Card>): Int {
        var delay = 0L

        removeCallbacks(superEnable)

        temp_nextCard = cardList
        if (cardList[0].cardType == CardType.SOFTSIM && cardList[0].status == CardStatus.ABSENT) {

            val currentInterval = SystemClock.elapsedRealtime() - lastCloseSoftSimTime

            val shouldDelay = MIN_SOFTSIM_ENABLE_INTERVAL - currentInterval

            delay = if (shouldDelay <= 0) 0 else shouldDelay

            if (delay > 0) {
                logd("SOFTSIM_ENABLE_INTERVAL delay = $delay")
            }
        }

        postDelayed(superEnable, delay)
        return 0
/*        unsubscribestaRefreshSub()
        logd("ucloudlink getMcfgRefreshValue")
        UcloudController.getMcfgRefreshValue(0)
        var mcfgrefresh: Subscription? = null

        staRefreshSub = UcloudController.staRefreshOb.timeout(500, TimeUnit.MILLISECONDS).subscribe({
            logd("ucloudlink getMcfgRefreshValue change " + it)
            if (it == 1) {
                if (cardList[0].cardType == CardType.SOFTSIM && cardList[0].status == CardStatus.ABSENT) {

                    val currentInterval = SystemClock.elapsedRealtime() - lastCloseSoftSimTime

                    val shouldDelay = MIN_SOFTSIM_ENABLE_INTERVAL - currentInterval

                    delay = if (shouldDelay <= 0) 0 else shouldDelay

                    if (delay > 0) {
                        logd("SOFTSIM_ENABLE_INTERVAL delay = $delay")
                    }
                }
                postDelayed(superEnable, delay)
            } else {
                postDelayed(superEnable, delay)
            }
            unsubscribestaRefreshSub()
        }, {
            logd("ucloudlink getMcfgRefreshValue change timneout " + it)
            logd("ucloudlink getMcfgRefreshValue enable again")
            postDelayed(superEnable, delay)
            unsubscribestaRefreshSub()
        }
        )
        return 0*/
    }


    override fun getReadyCard(card: Card) {
        removeCallbacks(handleReadyPhyCard)

        //检查参数
        val isOk = checkCardVail(card)

        if (!isOk) {
            loge("getReadyCard fail,card vail $card ")
            return notifyException(EnablerException.EXCEP_CARD_PARAMETER_WRONG)
        }

        //启动超时监视器
        timeoutWatcher.startWatch(card.cardType == CardType.SOFTSIM)
        //准备卡
        mCard.cardType = card.cardType
        mCard.slot = card.slot

        when (card.cardType) {
            CardType.SOFTSIM -> {
                installSoftSim(card)
            }
            CardType.PHYSICALSIM -> {
                installPhyCard(card)
            }
        }
    }

    protected open fun installPhyCard(card: Card) {
        /* FIXME 强制关闭一下软卡通道，其实这个应该没多大必要，根据上层关卡处理，应该可以去掉 */
        mCard.roamenable = card.roamenable
        cardController.disableRSIMChannel(mCard.slot)
        updateCardState(CardStatus.INSERTED)
        post(handleReadyPhyCard)
    }

    private fun installSoftSim(card: Card) {
        mCard.imsi = card.imsi
        mCard.ki = card.ki
        mCard.opc = card.opc
        mCard.rat = card.rat
        mCard.roamenable = card.roamenable
        mCard.apn = card.apn
        val fplmn = SeedPlmnSelector.getCurrentMccFplmnByImsi(card.imsi, CardType.SOFTSIM)
        mCard.fplmn = fplmn
        SeedPlmnSelector.markSoftSeedLastFplmn(card.imsi, fplmn)
        val preferredPlmn = SeedPlmnSelector.getCurrentMccPreferredPlmn(card.imsi, CardType.SOFTSIM)
        mCard.preferredPlmn = preferredPlmn
        //implement softSim install 
        installSoftSimImpl(card)
    }

    protected open fun installSoftSimImpl(card: Card) {
        val ret = cardController.queryCard(card.imsi)
        if (ret != cardController.SUCCESS) {
            notifyException(EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM, "query card ${mCard.imsi} failed $ret")
            return
        }

        cardController.connectTransfer(card.slot)

        notifyASS(AccessEventId.EVENT_SEEDSIM_INSERT)
        notifyASS(EVENT_SOFTSIM_ON)
        cardController.insertCard(mCard).timeout(INSERT_TIMEOUT, TimeUnit.MILLISECONDS).subscribe({
            updateCardState(CardStatus.POWERON)
            CardProvisionStatus.sendSettingSimCmd(mCard.cardType, CardProvisionStatus.CARD_START_MONITOR)
            JLog.logk("installSoftSim success wait card ready")
        }, {
            if (it is TimeoutException) {
                notifyException(EnablerException.INSERT_SOFT_SIM_TIMEOUT)
            } else {
                it.printStackTrace()
            }
        })
    }

    private val handleReadyPhyCard = object : Runnable {
        override fun hashCode(): Int {
            return 88555878
        }

        override fun run() {
            /*检查指定卡槽位置有没有物理卡
            * 如果读不到物理卡信息,在一段时间内每隔一秒读一次,直到超时
            * 由于可能阻塞,放到子线程处理
            * */
            if (mCard.status < CardStatus.POWERON) {//先判断卡是否ready,真正意义的ready
                val stateForPhy = ServiceManager.systemApi.getSimState(mCard.slot)

                /* not ready 可以认为radio powering */
                when (stateForPhy) {
                    SIM_STATE_READY, SIM_STATE_NOT_READY -> {
                        updateCardState(CardStatus.POWERON)
                        post(this)
                    }
                    else -> {
                        logv("handleReadyPhyCard card may not insert ,check again after 1s.[stateForPhy:$stateForPhy]")
                        postDelayed(this, 1000)
                    }
                }

            } else if (mCard.status == CardStatus.POWERON) {
                val subscriptionManager = SubscriptionManager.from(mContext)
                val info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(mCard.slot)
                if (info == null) {
                    logd("handleReadyPhyCard info == null")
                    postDelayed(this, 1000)
                } else {
                    CardProvisionStatus.sendSettingSimCmd(mCard.cardType, CardProvisionStatus.CARD_START_MONITOR)

                    val subId = info.subscriptionId
                    if (subId < 0 || subId > 0x40000000) {
                        postDelayed(this, 1000)
                        return
                    }

                    val telephonyManager = TelephonyManager.from(mContext)
                    val imsi = telephonyManager.getSubscriberId(subId)
                    if (TextUtils.isEmpty(imsi)) {
                        postDelayed(this, 1000)
                        return
                    }

                    //判断是否为电信卡,不支持电信卡作种子卡
                    val isCdma = checkIsCDMA(subId)
                    if (isCdma || !ServiceManager.systemApi.checkCardCanBeSeedsim(mCard.slot)) {
                        notifyException(EnablerException.EXCEPTION_UNSUPPORT_CDMA_PHY_CARD)
                        return
                    }

                    //预判卡是否可用
                    val cardAvailable = phyCardWatcher.isCardAvailable(mCard.slot, subId)
                    if (!cardAvailable) {
                        notifyException(EnablerException.EXCEPTION_PHY_CARD_MAY_UNAVAILABLE)
                        return
                    }

                    mCard.imsi = imsi
                    mCard.subId = subId
                    mCard.cardType = CardType.PHYSICALSIM
                    mCard.vslot = -1
//                    val numeric = telephonyManager.getIccOperatorNumericForData(subId)
                    val numeric = ServiceManager.systemApi.getIccOperatorNumericForData(subId)

                    if (!TextUtils.isEmpty(numeric) && imsi.startsWith(numeric)) {
                        mCard.numeric = numeric
                    } else {
                        logv("update numeric fail:imsi:$imsi numeric:$numeric")
                        mCard.numeric = ""
                    }
                    logv("getReadyCard success $mCard ")

                    updateCardState(CardStatus.READY)//物理卡需要手动ready

                }
            } else {
                loge("unExcept State mCard.status:${mCard.status}")
            }

        }

    }

    override fun onCardReady() {
        super.onCardReady()
        if (mCard.cardType == CardType.SOFTSIM) {
            //设置漫游开关与数据开关自动打开
            PhoneStateUtil.setRoamEnable(ServiceManager.appContext, mCard.subId, true)
            PhoneStateUtil.setMobileDataEnable(ServiceManager.appContext, mCard.subId, true)

            if (!isCloudSimOn) {
                ServiceManager.apnCtrl.setUcApnSetting(mCard, APN_TYPE_DEFAULT)
            }
        } else {
            val mobileDataEnable = PhoneStateUtil.getMobileDataEnable(ServiceManager.appContext, mCard.subId)
            if (!mobileDataEnable) {
                notifyException(EnablerException.EXCEPTION_DATA_ENABLE_CLOSED, "Phy_Card data is not enable")
            }

            //如果卡使用中途检测到不能用，就直接抛异常
            phyCardWatcher.onCardUnavailable = { slotId ->
                if (slotId == mCard.slot) {
                    notifyException(EnablerException.EXCEPTION_PHY_CARD_MAY_UNAVAILABLE)
                }
                unWatchPhyCard()
            }

            phyCardWatcher.onCardRoamStateChange = { subId, isRoam ->
                //收到物理卡监听漫游状态发生变化时，判断如果没注册，且不允许漫游时，抛出异常
                if (subId == mCard.subId && isRoam && mCard.status != CardStatus.IN_SERVICE && !isUseAllowRoam(subId)) {
                    notifyException(EnablerException.EXCEPTION_USER_PHY_ROAM_DISABLE)
                }
                unWatchPhyCard()
            }


        }
        notifyASS(AccessEventId.EVENT_SEEDSIM_READY)
    }


    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        unWatchPhyCard()
        removeCallbacks(superEnable)
        return super.disable(reason, isKeepChannel)
    }

    override fun onMyCardNetChange(newState: NetworkInfo.State) {
        super.onMyCardNetChange(newState)
        if (newState == NetworkInfo.State.CONNECTED) {
            notifyASS(AccessEventId.EVENT_SEEDSIM_DATA_CONNECT)
        } else if (newState == NetworkInfo.State.DISCONNECTED) {
            notifyASS(AccessEventId.EVENT_SEEDSIM_DATA_DISCONNECT)
        }
    }


    override fun onCloseDataEnabler(reason: String): Long {
        super.onCloseDataEnabler(reason)

        removeCallbacks(handleReadyPhyCard)
        temp_nextCard = null

        //mark last Close SoftSim Time
        if (mCard.cardType == CardType.SOFTSIM && mCard.status >= CardStatus.POWERON) {
            lastCloseSoftSimTime = SystemClock.elapsedRealtime()
        }

        return 0
    }

    private fun checkCardVail(card: Card): Boolean {
        if (card.cardType == CardType.SOFTSIM && TextUtils.isEmpty(card.imsi)) {
            return false
        }
        if (card.cardType == CardType.VSIM) {
            return false
        }
        /*if (card.cardType == CardType.SOFTSIM && (TextUtils.isEmpty(card.ki) || TextUtils.isEmpty(card.opc))) {
            return false
        }*/
        if (card.slot < 0 || card.slot > 2) {
            return false
        }
        return true
    }


    override fun triggerCall() {}

    private fun unWatchPhyCard() {
        phyCardWatcher.onCardUnavailable = { slotId -> }
        phyCardWatcher.onCardRoamStateChange = { subId, roamState -> }
    }

    override fun onCardInService() {
        super.onCardInService()
        notifyASS(AccessEventId.EVENT_SEEDSIM_IN_SERVICE)
    }

    override fun onRegisterStateChange(subId: Int, state: ServiceState) {
        super.onRegisterStateChange(subId, state)
        if (!isCardOn() || mCard.subId != subId) return

        if (mCard.cardType == CardType.PHYSICALSIM && !isClosing()) {
            val roaming: Boolean

            if (state.voiceRegState == STATE_IN_SERVICE || state.dataRegState == STATE_IN_SERVICE) {
                roaming = state.voiceRoaming || state.dataRoaming
            } else {
                //当没注册上时，预判卡是否漫游
                roaming = phyCardWatcher.isCardRoam(mCard.slot, subId)
            }

            if (roaming && !isUseAllowRoam(mCard.subId)) {
                notifyException(EnablerException.EXCEPTION_USER_PHY_ROAM_DISABLE)
            }
        }
    }

    /**
     * 卡的数据开关发生变化时回调
     * 软卡：数据开关被关掉时自动重新打开
     * 物理卡：数据开关被关掉时报错
     */
    override fun onDataEnablerChanged(ret: Boolean) {
        super.onDataEnablerChanged(ret)
        if (isCardOn() && !isClosing()) {
            if (mCard.cardType == CardType.SOFTSIM) {
                if (!ret && mCard.subId > -1) {
                    logv("enable softsim data subId = ${mCard.subId}")
                    PhoneStateUtil.setMobileDataEnable(ServiceManager.appContext, mCard.subId, true)
                }
            } else if (mCard.cardType == CardType.PHYSICALSIM) {
                if (!ret) {
                    notifyException(EnablerException.EXCEPTION_DATA_ENABLE_CLOSED, "Phy_Card data is UnEnable")
                }
            }
        }
    }

    /**
     * 卡的漫游开关发生变化时回调
     * 软卡：漫游开关被关掉时自动重新打开
     * 物理卡：漫游开关被关掉时报错
     */
    override fun onRoamEnablerChange(roamEnabler: Boolean) {
        super.onRoamEnablerChange(roamEnabler)
        if (isCardOn() && !isClosing()) {
            val subId = mCard.subId

            if (!roamEnabler && subId > -1) {
                if (mCard.cardType == CardType.SOFTSIM) {
                    logv("enable softsim roam data subId = $subId")
                    PhoneStateUtil.setRoamEnable(ServiceManager.appContext, subId, true)

                } else if (mCard.cardType == CardType.PHYSICALSIM) {
                    val telephonyManager = TelephonyManager.from(mContext)
                    val isRoam: Boolean
                    if (mCard.status == CardStatus.IN_SERVICE) {
                        isRoam = telephonyManager.isNetworkRoaming(subId)
                    } else {
                        isRoam = phyCardWatcher.isCardRoam(mCard.slot, mCard.subId)
                    }
                    if (isRoam) {
                        notifyException(EnablerException.ROAM_DATA_ENABLE_CLOSED, "Phy_Card roam data is UnEnable")
                    }
                }
            }

        }
    }

    override fun switchRemoteSim(card: Card): Int {//软卡不支持换卡
        return -1
    }

    override fun cloudSimRestOver() {
        logd("do nothing in " + this.javaClass.simpleName)
    }

    /**
     * 检查 漫游开关 是否打开 与 用户是否允许使用用户物理卡漫游
     */
    private fun isUseAllowRoam(subId: Int): Boolean {
        val USER_PHY_ROAM_ENABLE = Configuration.PHY_ROAM_ENABLE

        val SETTING_PHY_ROAM_ENABLE = PhoneStateUtil.isRoamEnabled(mContext, subId)

        val isUserAllowRoam = USER_PHY_ROAM_ENABLE && SETTING_PHY_ROAM_ENABLE
        return isUserAllowRoam
    }

    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        super.notifyEventToCard(event, obj)
        when (event) {
            DataEnableEvent.EVENT_SOFTSIM_IMAGE_UPDATED -> {
                obj ?: return
                val imsi = obj as String
                if (isCardOn() && imsi == mCard.imsi) {
                    disable("softsim image update")
                }

            }
            DataEnableEvent.ENV_MCC_CHANGED -> {
                if (isCardOn() && !isClosing()) {
                    val result = SeedPlmnSelector.checkSeedFplmnUpdate(mCard.imsi, mCard.subId, mCard.cardType)
                    logd("result.first  "+result.first)
                    if (result.first) {
                        val newFplmn = result.second
                        doReFreshFplmn(mCard.imsi, newFplmn)
                    }

                }
            }
        }
    }

    protected fun doReFreshFplmn(imsi: String, newFplmn: Array<String>?) {
        if (getCard().cardType == CardType.SOFTSIM) {
            logd("doReFrashFplmn")
            disable(ENV_MCC_CHANGED)
        } else if (getCard().cardType == CardType.PHYSICALSIM) {
            doReFlashPhyFplmn(imsi, newFplmn)
        }
    }

    protected open fun doReFlashPhyFplmn(imsi: String, newFplmn: Array<String>?) {
  
    }

    

    override fun onCardAbsent(enablerClosing: Boolean, logout: Boolean, keepChannel: Boolean) {
        super.onCardAbsent(enablerClosing, logout, keepChannel)
        if (!keepChannel) {
            notifyASS(EVENT_SOFTSIM_OFF)
        }
    }
}