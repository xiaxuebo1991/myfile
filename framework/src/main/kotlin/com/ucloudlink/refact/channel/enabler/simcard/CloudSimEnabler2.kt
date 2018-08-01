package com.ucloudlink.refact.channel.enabler.simcard

import android.content.Context
import android.net.NetworkInfo
import android.os.HandlerThread
import android.os.Looper
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.ucloudlink.framework.util.APN_TYPE_DEFAULT
import com.ucloudlink.framework.util.ApnUtil
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.ServiceManager.cardController
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.business.cardprovisionstatus.CardProvisionStatus
import com.ucloudlink.refact.business.performancelog.logs.PerfLogDataEvent
import com.ucloudlink.refact.business.preferrednetworktype.PreferredNetworkType
import com.ucloudlink.refact.channel.enabler.EnablerException
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.channel.enabler.simcard.cardcontroller.AuthController
import com.ucloudlink.refact.channel.enabler.simcard.watcher.CloudSimWatcher
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logk
import com.ucloudlink.refact.utils.PhoneStateUtil
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by jiaming.liang on 2016/12/9.
 */

open class CloudSimEnabler2(mContext: Context, mLooper: Looper) : CardDeBase(mContext, mLooper) {
    override fun isDefaultNet(): Boolean {
        return true
    }

    open protected val InsertTimeout: Long = 20 * 1000
    //private val apduDelegate = CloudSimApduDelegate()

    override fun enable(cardList: ArrayList<Card>): Int {
        return super.enable(cardList)
    }

    override fun getReadyCard(card: Card) {
        val isOk = checkCardVail(card)
        if (!isOk) {
            loge("cloudsim is not OK : $card")
            return notifyException(EnablerException.EXCEP_CARD_PARAMETER_WRONG)
        }

        //check is multiCountry Card or not 
        val isMultiCountryCard = checkMultiCountryCard(card)
        timeoutWatcher.startWatch(isMultiCountryCard)

        mCard.cardType = card.cardType
        mCard.slot = card.slot
        mCard.imsi = card.imsi
        mCard.vritimei = card.vritimei
        //mCard.apduDelegate = apduDelegate
        mCard.eplmnlist = card.eplmnlist
        mCard.rat = PreferredNetworkType.mPreferredNetworkType
        mCard.apn = card.apn

        mCard.roamenable = (PreferredNetworkType.mDataRoaming == 1)

        installCloudSim(mCard)
    }

    open protected fun installCloudSim(card: Card) {
        cardController.connectTransfer(card.slot)
        if (card.vritimei.isEmpty()) {
            ServiceManager.systemApi.recoveryImei(mContext, card.slot)
        } else {
            ServiceManager.systemApi.wirteVirtImei(mContext, card.slot, card.vritimei)
        }
        cardController.insertCard(card).timeout(InsertTimeout, TimeUnit.MILLISECONDS)
                .subscribe({
                    updateCardState(CardStatus.POWERON)
                    CardProvisionStatus.sendSettingSimCmd(card.cardType, CardProvisionStatus.CARD_START_MONITOR)
                    JLog.logk("install Cloudsim success wait card ready")
                }, {
                    if (it is TimeoutException) loge("startCloudSim timeOut")
                    it.printStackTrace()
                })
    }

    /**
     * 当卡ready时回调
     * 通知状态机更新状态
     * 设置打开云卡的数据开关，漫游开关
     * 设置云卡apn
     */
    override fun onCardReady() {
        super.onCardReady()
        notifyASS(AccessEventId.EVENT_CLOUDSIM_CARD_READY)

        PhoneStateUtil.setMobileDataEnable(ServiceManager.appContext, mCard.subId, true)//默认打开云卡数据开关
        PhoneStateUtil.setRoamEnable(ServiceManager.appContext, mCard.subId, mCard.roamenable)//默认云卡漫游开关打开

        (timeoutWatcher as CloudSimWatcher).dataEnabled = true

        val preferredApn_id = insertCloudsimApn(mCard.apn, mCard.subId, Configuration.cloudSimApns)
        if (preferredApn_id != null) {
            ApnUtil.selectApnBySubId(ServiceManager.appContext, preferredApn_id, mCard.subId, true)
        }

    }

    /**
     * 云卡注册上时回调
     */
    override fun onCardInService() {
        super.onCardInService()
        logk("onCardInService")
        notifyASS(AccessEventId.EVENT_CLOUDSIM_REGISTER_NETWORK)
    }

    /**
     * 网络（可能云卡的，也可能种子卡的）发送变化时回调
     */
    override fun onNetStateUpdated(networkState: NetworkInfo.State, type: Int) {
        super.onNetStateUpdated(networkState, type)
        logv("networkState:$networkState type:$type")
        if (!isCardOn()) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        if (mCard.subId < 0) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        if (mCard.imsi != getImsiBySubId(mCard.subId)) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        /*
        这个判断,由于有时候卡注册上运营商的消息上报会比网络连上的消息慢,
        会出现得到了卡网络连上,但卡状态处于没inService的情况
        看过最大的时间差大概400毫秒
        针对此问题,作出如下修改,如果得到connected状态,但卡非inService,等待一秒后再检查一次
        
        如果这里有可能行程死循环,但是后面connect超时会保证卡能正常退出
        
         */
        if (curDDSSlotId == mCard.slot) {
            logv("update default network")

            if (mCard.status == CardStatus.IN_SERVICE) {
                logv("update net state IN_SERVICE")
                updateNetState(mDefaultNetState)
            } else {
                if (mDefaultNetState == NetworkInfo.State.CONNECTED) {
                    logv("get default connected but not in service")
                    //获取当前卡的注册网络类型，如果大于0，就认为卡已经注册上了，防止有时候没有注册信息上报的情况
                    if (TelephonyManager.from(mContext).getNetworkType(mCard.subId) > 0) {
                        updateCardState(CardStatus.IN_SERVICE)
                    }

                    postDelayed({
                        onNetStateUpdated(mDefaultNetState, 0)
                    }, 1000)
                } else {
                    updateNetState(NetworkInfo.State.DISCONNECTED)
                }
            }
        } else {
            updateNetState(NetworkInfo.State.DISCONNECTED)
        }
    }

    /**
     * （云卡的）网络发送变化时回调
     */
    override fun onMyCardNetChange(newState: NetworkInfo.State) {
        super.onMyCardNetChange(newState)
        if (newState == NetworkInfo.State.CONNECTED) {
            notifyASS(AccessEventId.EVENT_CLOUDSIM_DATA_ENABLED)
        } else {
            notifyASS(AccessEventId.EVENT_CLOUDSIM_DATA_LOST)
        }
    }

    /**
     * 数据开关发送变化时回调
     */
    override fun onDataEnablerChanged(ret: Boolean) {
        super.onDataEnablerChanged(ret)
        if (isCardOn() && !isClosing()) {
            (timeoutWatcher as CloudSimWatcher).dataEnabled = ret
            var eventId = 0
            if (ret) {
                eventId = AccessEventId.EVENT_EXCEPTION_PHONE_DATA_ENABLE
                ServiceManager.accessMonitor.phoneDataEnable()
                PerfLogDataEvent.create(1,0,0)
            } else {
                eventId = AccessEventId.EVENT_EXCEPTION_PHONE_DATA_DISABLE
                ServiceManager.accessMonitor.phoneDataDisable()
                PerfLogDataEvent.create(2,0,0)
            }
            notifyASS(eventId)
        }
    }

    /**
     * 漫游数据开关发送变化时回调
     */
    override fun onRoamEnablerChange(roamEnabler: Boolean) {
        super.onRoamEnablerChange(roamEnabler)
        if (isCardOn() && !isClosing()) {

            if (roamEnabler != mCard.roamenable && mCard.subId > -1) {
                logv("enable cloudsim roam data subId = ${mCard.subId}")
                PhoneStateUtil.setRoamEnable(ServiceManager.appContext, mCard.subId, mCard.roamenable)
            }
        }
    }

    /**
     * 要关闭此DataEnabler前回调
     * 取消正在执行鉴权请求
     *
     */
    override fun onCloseDataEnabler(reason: String): Long {
        AuthController.releaseAuthRequest()
        super.onCloseDataEnabler(reason)
        logd("onCloseDataEnabler")

        return 0L
    }

    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        val ret = super.disable(reason, isKeepChannel)
        return ret
    }

    private fun checkMultiCountryCard(card: Card): Boolean {
        card.eplmnlist?.forEach {
            if (it.plmnType == 1 && !it.plmn.startsWith(card.imsi.substring(0, 3))) {
                return true
            }
        }
        return false
    }

    private fun checkCardVail(card: Card): Boolean {
        if (card.cardType != CardType.VSIM || TextUtils.isEmpty(card.imsi)) {
            return false
        }
        if (card.slot < 0 || card.slot > 2) {
            return false
        }
        return true
    }


    private fun insertCloudsimApn(apn: List<Apn>?, subId: Int, apns: List<Apn>?): String? {
        //首选default,没有default就首选第一个
        logv("cloudsimhandler insertCloudsimApn")
        val cloudSimApns = apns
        if (cloudSimApns == null) {
            logd("server not provided for apn")
            return null
        }

        val setApn = run {
            var secondApn: Apn? = null
            cloudSimApns.forEach {
                if (it.type.contains(APN_TYPE_DEFAULT)) {
                    return@run it
                }
                if (secondApn == null) {
                    secondApn = it
                }
            }
            return@run secondApn
        } ?: return null

        return ApnUtil.InsertCloudSimApnIfNeed(ServiceManager.appContext, setApn!!, subId)
    }

    override fun switchRemoteSim(card: Card): Int {
        /*  这个没使用，去掉
        val isOk = checkCardVail(card)
        if (!isOk) {
            notifyException(EnablerException.EXCEP_CARD_PARAMETER_WRONG)
            return -1
        }
        Observable.just(CardController.removeCardForSwap(mCard)).flatMap {
            mCard.cardType = card.cardType
            mCard.slot = card.slot
            mCard.imsi = card.imsi
            mCard.apduDelegate = apduDelegate
            mCard.eplmnlist = card.eplmnlist
            Observable.just(CardController.insertCard(mCard))//插卡
            Observable.just(CardController.powerUpCard(mCard))//启动，以获取atr
            Observable.just(CardController.insertCardForSwap(mCard))//发送UIM REMOTE EVENT，socket收到reset Ind后在remote uim session里面进行reset
        }.timeout(InsertTimeout, TimeUnit.MILLISECONDS).subscribe({
            logk("installCloud success wait card ready")
        }, {
            if (it is TimeoutException) loge("startCloudSim timeOut")
            it.printStackTrace()
        })
        */
        return 0
    }

    override fun cloudSimRestOver() {
        logd("do nothing in " + this.javaClass.simpleName)
    }

    override fun onCardAbsent(enablerClosing: Boolean, logout: Boolean, keepChannel: Boolean) {
        super.onCardAbsent(enablerClosing, logout, keepChannel)
        ServiceManager.systemApi.recoveryImei(mContext, mCard.slot)

        if (keepChannel) {
            cardController.disableRSIMChannel(mCard.slot)
            cardController.disconnectTransfer(mCard.slot)
        }
        if (logout && !ServiceManager.systemApi.isUnderDevelopMode()) {
            ApnUtil.clearApnByNumeric(ServiceManager.appContext, mCard.imsi.substring(0, 5))
        }
    }

    override fun initWatcher() {
        val watchThread = HandlerThread("${this.javaClass.simpleName}-watchThread")
        watchThread.start()
        timeoutWatcher = CloudSimWatcher(watchThread.looper, this, "${this.javaClass.simpleName}-TO")
        timeoutWatcher.exceptionObser.asObservable().subscribe {
            notifyException(it)
        }
    }

    override fun notifyException(e: EnablerException, msg: String, isCloseCard: Boolean) {
        super.notifyException(e, msg, isCloseCard)
        when (e) {
            EnablerException.EXCEPTION_REG_DENIED -> notifyASS(AccessEventId.EVENT_EXCEPTION_VSIM_REJECT)
            EnablerException.EXCEPTION_CARD_NET_FAIL -> notifyASS(AccessEventId.EVENT_EXCEPTION_VSIM_NET_FAIL)
        }
    }

}