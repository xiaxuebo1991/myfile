package com.ucloudlink.refact.channel.enabler

import android.content.Context
import android.net.NetworkInfo
import android.os.HandlerThread
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.netcheck.SIGNAL_STRENGTH_GREAT
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.simcard.SeedManager
import com.ucloudlink.refact.channel.enabler.wifi.WifiEnabler
import com.ucloudlink.refact.channel.monitors.WifiReceiver2
import com.ucloudlink.refact.config.ACCESS_CLEAR_CARD
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.config.REASON_CHANGE
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.logv
import rx.Observable
import rx.Subscription
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.subscribeWith
import java.util.*

/**
 * Created by jiaming.liang on 2017/1/6.
 * DateEnabler 的代理类
 */
class DeProxy(val context: Context, initSource: IDataEnabler) : IDataEnabler {

    private var mDeSource: IDataEnabler
    private var mWifiDataEnabler: IDataEnabler
    private var mCardDataEnabler: IDataEnabler
    
//    private var cacheDe: IDataEnabler? = null

    private val mCardStateObservable = BehaviorSubject(CardStatus.ABSENT)
    private val mNetStatusObservable = BehaviorSubject(NetworkInfo.State.DISCONNECTED)
    private val mExceptionObservable = PublishSubject<EnablerException>()
    private val mCardSignalStrengthObservable = PublishSubject<Int>()

    private var mCardSubscribe: Subscription? = null
    private var mNetSubscribe: Subscription? = null
    private var mExceptionSubscribe: Subscription? = null
    private var mCardSignalStrengthSubscribe:Subscription? = null

    private var mCardList = ArrayList<Card>()

    private var mEnabled = false
    private var mIsLogin = false
        set(value) {
            field = value
            logd("mIsLogin -->$value")
        }

    private fun isNoSoftsimInList() :Boolean {
        if(mCardList != null && mCardList.size > 0){
            for(c in mCardList){
                if (c.cardType == CardType.SOFTSIM) {
                    return false
                }
            }
            return true
        }
        return false
    }

    private fun procNoSoftSim(exception: EnablerException){
        // 是否需要增加纯物理卡套餐判断？ todo
        if(ServiceManager.phyCardWatcher.isCardRoam(Configuration.seedSimSlot)){
            logd("phone is in roam for phy card!")
            mExceptionObservable.onNext(EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM)
        }else{
            if(Configuration.LOCAL_SEEDSIM_DEPTH_OPT){
                mExceptionObservable.onNext(EnablerException.EXCEPT_NO_AVAILABLE_SOFTSIM)
            }else{
                logd("Configuration.LOCAL_SEEDSIM_DEPTH_OPT is close!!!!")
                mExceptionObservable.onNext(EnablerException.EXCEPTION_LOCAL_DEPTH_OPT_CLOSE)
            }
        }
    }

    /*连接订阅者*/
    private fun connectObser() {
        disconnectOld(mCardSubscribe)
        mCardSubscribe = mDeSource.cardStatusObser().asObservable().subscribe {
            mCardStateObservable.onNext(it)
        }

        disconnectOld(mNetSubscribe)
        mNetSubscribe = mDeSource.netStatusObser().asObservable().subscribe {
            mNetStatusObservable.onNext(it)
        }

        disconnectOld(mExceptionSubscribe)
        mExceptionSubscribe = mDeSource.exceptionObser().asObservable().subscribe {
            if(mDeSource.getDeType() == DeType.SIMCARD){
                if(isNoSoftsimInList()){
                    logd("only phy card in list!!!")
                    procNoSoftSim(it)
                    return@subscribe
                }
            }
            mExceptionObservable.onNext(it)
        }

        disconnectOld(mCardSignalStrengthSubscribe)
        mCardSignalStrengthSubscribe = mDeSource.cardSignalStrengthObser().subscribe{
            mCardSignalStrengthObservable.onNext(it)
        }
    }

    private fun disconnectOld(subscribe: Subscription?) {
        subscribe ?: return
        if (!subscribe.isUnsubscribed) subscribe.unsubscribe()
    }

//    var cardEnablerSub: Subscription? = null

    private fun changeDe(newDeType: DeType) {
        logv("changeDe:$newDeType mDeSource:${mDeSource.getDeType()}")

        //关闭旧的,替换上新的,先检查是否有缓存的DataEnable ，如果有就持有，如果没有就新建
        
//        val enablerSub = cardEnablerSub
        if (mDeSource.getDeType() == DeType.WIFI && newDeType == DeType.SIMCARD) {
            //disble处理时若mDeSource
            mDeSource = mCardDataEnabler
            mWifiDataEnabler.disable(REASON_CHANGE)

            mCardDataEnabler.notifyEventToCard(DataEnableEvent.EVENT_ATTACH_ENABLER,null)
            connectObser()

            if (mEnabled) {
                //获取要启动卡的信息
                enable(mCardList)
            }

        } else if (mDeSource.getDeType() == DeType.SIMCARD && newDeType == DeType.WIFI) {
            //card----->wifi
            val cardState = mCardDataEnabler.getCardState()
        

            mCardDataEnabler.disable(REASON_CHANGE)
            mCardDataEnabler.notifyEventToCard(DataEnableEvent.EVENT_DETACH_ENABLER,null)
            
            mDeSource = mWifiDataEnabler
            connectObser()

            if (cardState != CardStatus.ABSENT && !mCardDataEnabler.isClosing()) {

//                cacheDe = oldCardDE
//                cardEnablerSub = oldCardDE.cardStatusObser().subscribeWith {
//                    onNext {
//                        if (CardStatus.ABSENT != it) return@onNext
//                        
//                        synchronized(this) {
//                            val sub = this.subscriber
//                            if (!sub.isUnsubscribed) sub.unsubscribe()
//                            cardEnablerSub = null
//                            cacheDe?.notifyEventToCard(DataEnableEvent.EVENT_CLEAR_ENABLER,null)
//                            cacheDe = null
//                        }
//                    }
//                }

                enable(mCardList)

            } else {
                if (mIsLogin) {
                    mNetStatusObservable.onNext(NetworkInfo.State.CONNECTED)
                }
            }


        }/* else if (mDeSource.getDeType() == DeType.SIMCARD && newDeType == DeType.SIMCARD) {
            *//*
            一般来说不会出现这种新类型是SIMCARD，旧的类型也是SIMCARD 的情况
            目前遇到是刚切换simcard wifi 又马上断开，导致simcard还没关闭完成就收到要重新切回simcard 
            
            这个会导致收到关卡完成，切换成wifiEnabler 但实际并不是使用wifi这种情况
            
            为处理这个问题，这里增加处理，遇到这种情况，取消cardEnabler 得监听，这样就不会收到absent 更换成wifiEnabler
             *//*
            val cardEnablerSub1 = enablerSub
            if (cardEnablerSub1 != null) {
                if (!cardEnablerSub1.isUnsubscribed) {
                    cardEnablerSub1.unsubscribe()
                }
            }
            if (mEnabled) {
                mDeSource.enable(mCardList)
            }
        } else {
            return loge("unExcept!newDeType:$newDeType mDeSource:${mDeSource.getDeType()}")
        }*/

    }

    /* private fun getLaunchCard(): Card {
         val seedCard: Card = Card()
         if (Configuration.ApduMode == Configuration.ApduMode_soft) {
             softCardList.forEach {
                 if (it.imsi == Configuration.softSimImsi) {
                     seedCard.slot = Configuration.seedSimSlot
                     seedCard.imsi = it.imsi
                     seedCard.ki = it.ki
                     seedCard.opc = it.opc
                     seedCard.eplmnlist = it.eplmnlist
                     seedCard.cardType = CardType.SOFTSIM
                     return@forEach
                 }
             }
         } else if (Configuration.ApduMode == Configuration.ApduMode_Phy) {
             seedCard.slot = Configuration.seedSimSlot
             seedCard.cardType = CardType.PHYSICALSIM
         }
         return seedCard
     }*/

    override fun getDeType(): DeType {
        return mDeSource.getDeType()
    }

    override fun enable(cardList: ArrayList<Card>): Int {
        if(!(mDeSource.getDeType() == DeType.SIMCARD && mDeSource.isCardOn())) {
            this.mCardList = cardList
        }
        mEnabled = true
        mIsLogin = true
        //FIX #22472 重新建一个对象赋值给SeedManager
        return mDeSource.enable(ArrayList<Card>(cardList))
    }

    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        mCardList.clear()
        mEnabled = false
        if (reason.contains(ACCESS_CLEAR_CARD)) {
            mIsLogin = false
        }
        return mDeSource.disable(reason,isKeepChannel)
    }

    override fun getCardState(): CardStatus {
        return mDeSource.getCardState()
    }

    override fun cardStatusObser(): Observable<CardStatus> {
        return mCardStateObservable
    }

    override fun getNetState(): NetworkInfo.State {
        return mDeSource.getNetState()
    }

    override fun netStatusObser(): Observable<NetworkInfo.State> {
        return mNetStatusObservable
    }

    override fun exceptionObser(): Observable<EnablerException> {
        return mExceptionObservable
    }

    override fun cardSignalStrengthObser(): Observable<Int> {
        return mCardSignalStrengthObservable
    }

    override fun switchRemoteSim(card: Card): Int {
        return mDeSource.switchRemoteSim(card)
    }

    override fun getCard(): Card {
        return mDeSource.getCard()
    }

    override fun isCardOn(): Boolean {
        return mDeSource.isCardOn()
    }

    override fun isClosing(): Boolean {
        return mDeSource.isClosing()
    }

    override fun cloudSimRestOver() {
        mDeSource.cloudSimRestOver()
    }

    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        mDeSource.notifyEventToCard(event, obj)
    }

    override fun isDefaultNet(): Boolean {
        return mDeSource.isDefaultNet()
    }

    override fun getDataEnableInfo(): DataEnableInfo {
        return mDeSource.getDataEnableInfo()
    }

    init {
        //初始化
        mDeSource = initSource
        connectObser()

        if (initSource.getDeType()== DeType.WIFI) {
            mWifiDataEnabler = initSource
            
            val seedHandler = HandlerThread("seed")
            seedHandler.start()
            mCardDataEnabler = SeedManager(context, seedHandler.looper)
            
        }else {
            mCardDataEnabler = initSource
            mWifiDataEnabler = WifiEnabler(context)
        }
        
        //注册监听wifi开启关闭的广播
        WifiReceiver2.wifiNetObser.asObservable().subscribeWith {
            onNext {
                logd("WifiReceiver2  :$it")
                if (it == NetworkInfo.State.CONNECTED) {
                    //表示连上了wifi的网络
                    changeDe(DeType.WIFI)
                } else if (it == NetworkInfo.State.DISCONNECTED) {
                    changeDe(DeType.SIMCARD)
                }
            }
        }
    }
}