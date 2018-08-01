package com.ucloudlink.refact.platform.qcom.channel.enabler.simcard

import android.content.Context
import android.net.NetworkInfo
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.ucloudlink.framework.softsim.OnDemandPsCallUtil
import com.ucloudlink.framework.util.APN_TYPE_DUN
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.DataEnableEvent
import com.ucloudlink.refact.channel.enabler.datas.Card
import com.ucloudlink.refact.channel.enabler.datas.CardStatus
import com.ucloudlink.refact.channel.enabler.datas.CardType
import com.ucloudlink.refact.channel.enabler.datas.MCCMNC_CODES_HAVING_3DIGITS_MNC
import com.ucloudlink.refact.channel.enabler.plmnselect.CardReject
import com.ucloudlink.refact.channel.enabler.simcard.SeedEnabler2
import com.ucloudlink.refact.channel.enabler.simcard.dds.switchDdsToNext
import com.ucloudlink.refact.platform.qcom.channel.enabler.simcard.watcher.QCSeedSimWatcher

/**
 * Created by jiaming.liang on 2017/11/30.
 */
class QCSeedEnabler(mContext: Context, mLooper: Looper) : SeedEnabler2(mContext, mLooper) {
    private var disableReason: String = ""

    private var isDefault: Boolean = true
    
    override fun isDefaultNet(): Boolean {
        return isDefault
    }

    override fun installSoftSimImpl(card: Card) {
        super.installSoftSimImpl(card)
    }

    override fun onCloseDataEnabler(reason: String): Long {
        super.onCloseDataEnabler(reason)
        var sleepTime = 0L
        if (isCardOn()) {
            sleepTime = OnDemandPsCallUtil.undoOnDemandPsCall()
            (timeoutWatcher as QCSeedSimWatcher).dunCallTrigger(false)
            logd(" onCloseDataEnabler OldSleepTime $sleepTime")
        }

        //mark last Close SoftSim Time
        if (mCard.cardType == CardType.SOFTSIM && mCard.status >= CardStatus.POWERON) {
            lastCloseSoftSimTime = SystemClock.elapsedRealtime() + sleepTime
        }

        return sleepTime
    }

    override fun onNetStateUpdated(networkState: NetworkInfo.State, type: Int) {
        super.onNetStateUpdated(networkState, type)
        logv("networkState:$networkState type:$type")
        if (!isCardOn()) {
            logv("seedsim is not on")
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        if (mCard.subId < 0) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            return
        }
        val imsiBySlot = getImsiBySubId(mCard.subId)
        if (mCard.imsi != imsiBySlot) {
            updateNetState(NetworkInfo.State.DISCONNECTED)
            logv("not same imsi at slot:$imsiBySlot card.imsi: ${mCard.imsi} ")
            return
        }
        if (curDDSSlotId == mCard.slot) {
            isDefault = true
            updateNetState(mDefaultNetState)
        } else {
            isDefault = false
            updateNetState(mDunNetState)
        }
    }

    override fun triggerCall() {
        super.triggerCall()
//        val defaultSubId = SubscriptionManager.getDefaultDataSubId()
        val defaultSubId = ServiceManager.systemApi.getDefaultDataSubId()
        if (isCloudSimOn && defaultSubId != mCard.subId) {//必须云卡在且dds不在种子卡上
            //需要dun
            if (TextUtils.isEmpty(mCard.numeric)) {
                logd("seed card do dun call but numeric is null get again")
                val numeric = ServiceManager.systemApi.getIccOperatorNumericForData(mCard.subId)
                if (!TextUtils.isEmpty(numeric) && mCard.imsi.startsWith(numeric)) {
                    mCard.numeric = numeric
                } else {
                    mCard.numeric = ""
                    loge("numeric null again cut form imsi")
                    for (mccmnc in MCCMNC_CODES_HAVING_3DIGITS_MNC) {
                        if (mCard.imsi.startsWith(mccmnc)) {
                            mCard.numeric = mccmnc
                            break
                        }
                    }
                    if (TextUtils.isEmpty(mCard.numeric)) {
                        mCard.numeric = mCard.imsi.substring(0, 5)
                    }
                }
            }

            doDunCall()
        } else {//需要default
            val SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub"
            var subIdInDb = -1
            try {
                subIdInDb = android.provider.Settings.Global.getInt(mContext.contentResolver, SETTING_USER_PREF_DATA_SUB)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (subIdInDb != -1 && subIdInDb != defaultSubId) {
                loge("subIdInDb!=defaultSubId subIdInDb:$subIdInDb  defaultSubId:$defaultSubId do set")
            }

            switchDdsToNext(mCard.subId, mCard.slot).subscribe({
                logd("set DDS to seedsim succ")
            }, {
                loge("set DDS to seedsim fail")
            })

            //判定当前网络是否可用 
            val manager = TelephonyManager.from(mContext)
            val dataState = manager.dataState
            logd("triggerCheckNetWork dataState:$dataState")
            if (dataState == TelephonyManager.DATA_CONNECTED) {
                isDefault = true
                updateNetState(NetworkInfo.State.CONNECTED)
            } else {
                loge("current seed default is not connect is need requestNetwork?")
            }
        }
    }

    private fun doDunCall() {
        //set apn
        (timeoutWatcher as QCSeedSimWatcher).dunCallTrigger(true)
        ServiceManager.apnCtrl.setUcApnSetting(mCard, APN_TYPE_DUN)
        OnDemandPsCallUtil.onDemandPsCall(mCard.subId)
    }

    override fun notifyEventToCard(event: DataEnableEvent, obj: Any?) {
        super.notifyEventToCard(event, obj)
        logd("notifyEventToCard $event")
        when (event) {
            DataEnableEvent.EVENT_RELEASE_DUN_OUTSIDE -> {
                val delayTime = OnDemandPsCallUtil.undoOnDemandPsCall()
                logd("do unDoDunCall from requestor ,will do delay $delayTime in fact ")
                (timeoutWatcher as QCSeedSimWatcher).dunCallTrigger(false)
            }
            DataEnableEvent.ENENT_CARD_REJECT->{
                val rej = obj as CardReject
                logd("rej == $rej  ")
                notifyException(rej.exception,rej.msg,rej.isShouldDisable)
            }
            else -> {
            }
        }
    }

    override fun disable(reason: String, isKeepChannel: Boolean): Int {
        disableReason = reason
        return super.disable(reason,isKeepChannel)
    }

    override fun initWatcher() {
        val watchThread = HandlerThread("${this.javaClass.simpleName}-watchThread")
        watchThread.start()
        timeoutWatcher = QCSeedSimWatcher(watchThread.looper, this, "${this.javaClass.simpleName}-TO")
        timeoutWatcher.exceptionObser.asObservable().subscribe {
            notifyException(it)
        }
    }

    override fun doReFlashPhyFplmn(imsi: String, newFplmn: Array<String>?) {
        /* qc do nothing*/
    }
    
/*    override fun uninstallCard(): Boolean {
        //不关软卡通道两个条件： 这个关不关通道由上层决定？
        //1如果软卡因为异常关闭，不主动关通道，由上层发命令关闭
        //2大循环触发的关卡且当前为软卡时，不关通道时
        *//*var exceptChannelStay = false
        if (disableReason.contains(REASON_MONITOR_RESTART) && mCard.cardType == CardType.SOFTSIM) {
            exceptChannelStay = true
        } else if () {

        }*//*

//        qcCardController.setSoftSimChannelStay(mCard.slot, exceptChannelStay)

        return super.uninstallCard()
    }*/

    /*override fun onCardAbsent(enablerClosing: Boolean, logout: Boolean) {
        super.onCardAbsent(enablerClosing, logout)
        if (mCard.cardType == CardType.SOFTSIM && enablerClosing && logout) {
            qcCardController.disconnectUIMSocket(mCard.slot)
        }
    }*/
}