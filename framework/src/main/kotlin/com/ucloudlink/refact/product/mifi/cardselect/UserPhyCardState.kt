package com.ucloudlink.refact.product.mifi.cardselect

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkInfo
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.channel.enabler.simcard.UcPhoneStateListenerWrapper
import com.ucloudlink.refact.channel.monitors.CardStateMonitor
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgEncode
import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.product.mifi.connect.struct.*
import com.ucloudlink.refact.product.mifi.misc.MsgUpdate
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd

/**
 * Created by shiqianhua on 2018/2/5.
 */
class UserPhyCardState(context: Context, mqttClient: UcMqttClient, slot: Int, cardSelect: CardSelect, looper: Looper, imsi: String) {
    val ctx = context
    val mqtt = mqttClient
    var simState: Int = 0
    val superSelect  = cardSelect

    var isRegOn = false
    var isNetworkOk = false
    var isRoam = false
    var cardStatus = CardStateMonitor.SIM_STATE_ABSENT
    var mCardSubId = -1
    val mPhone = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var listener:InnerPhoneListener? = null

    val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network?, linkProperties: LinkProperties?) {
            logd("onLinkPropertiesChanged $network $linkProperties")
            val subid = ServiceManager.systemApi.getDefaultDataSubId()
            if (linkProperties != null && linkProperties.isProvisioned
                    && (subid > 0 && SubscriptionManager.getPhoneId(subid) == slot)) {
                val datacall = MsgUpdate.getDatacallInfoByLink(linkProperties)
                MsgEncode.sendLedDatacall(mqttClient, datacall)
                MsgEncode.sendWebDatacall(mqttClient, datacall)
            }
        }
    }

    val netListener = object : CardStateMonitor.NetworkStateListen {
        override fun NetworkStateChange(ddsId: Int, state: NetworkInfo.State?, type: Int, ifName: String?, isExistIfNameExtra: Boolean, subId: Int) {
            logd("NetworkStateChange $ddsId $state $type $ifName, $isExistIfNameExtra $subId")
            if (ddsId == slot) {
                var networkOk = false
                if (state == NetworkInfo.State.CONNECTED) {
                    val telephoneManager = TelephonyManager.from(ServiceManager.appContext)
                    var netRat = telephoneManager.getNetworkType(ServiceManager.phyCardWatcher.getSubIdBySlot(slot))
                    logd("UserPhyCardState Network Connected: netRat $netRat ")
                    MsgEncode.sendCloudSimRat(mqtt,netRat)
                    MsgUpdate.updateServiceOk(ctx, mqttClient)
                    ServiceManager.systemApi.registerDefaultNetworkCallback(netCallback)
                    networkOk = true
                } else {
                    ServiceManager.systemApi.unregisterNetworkCallback(netCallback)
                    MsgUpdate.updateServiceAb(mqttClient)
                    networkOk = false
                }
                if(networkOk != isNetworkOk){
                    isNetworkOk = networkOk
                    superSelect.updateCardStatus(null, superSelect.getCurrentCardStateList())
                }
            }
        }
    }

    val cardListener = object : CardStateMonitor.CardStateListen {
        override fun CardStateChange(slotId: Int, subId: Int, state: Int) {
            JLog.logi("CardStateChange() slotId:$slotId subId:$subId state:$state state:${UserPhyCardState@this}")
            val tm = TelephonyManager.from(ServiceManager.appContext)
            if (slot == slotId && imsi == tm.getSubscriberId(subId)) {
                if (cardStatus != state) {
                    if (state == CardStateMonitor.SIM_STATE_LOAD || state == CardStateMonitor.SIM_STATE_READY) {
                        logd("card load $slot $subId")
                        if(subId >= 0) {
                            mCardSubId = subId
                            MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_READY, 0, 0, ""))
                            if (mCardSubId >= 0 && listener == null) {
                                listener = InnerPhoneListener(mCardSubId, looper)
                                mPhone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
                            }
                            ServiceManager.systemApi.setDefaultDataSlotId(slot)
                        }
                    }
                }
            }
        }
    }

    private inner class InnerPhoneListener(subId: Int, looper: Looper) : UcPhoneStateListenerWrapper(subId, looper) {
        override fun onServiceStateChanged(serviceState: ServiceState?) {
            JLog.logi("onServiceStateChanged() $serviceState")
            var reg = false
            var needUpdate = false
            if (serviceState != null) {
                if (serviceState.dataRegState == ServiceState.STATE_IN_SERVICE) {
                    MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_REG_OK, 0, 0, ""))
                    reg = true
                } else {
                    // todo!拒绝消息拿不到
                    reg = false
                }
                if (isNetworkOk){
                    MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_COMPLETE, UC_RSIM_REG_OK, 0, 0, ""))
                }
                if(reg != isRegOn){
                    isRegOn = reg
                    needUpdate = true
                }

                if(serviceState.roaming != isRoam){
                    isRoam = serviceState.roaming
                    needUpdate = true
                }

                if(needUpdate){
                    superSelect.updateCardStatus(null, superSelect.getCurrentCardStateList())
                }
            }
        }
    }

    init {
        MsgEncode.sendLedServiceStart(mqtt)
        MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_START, 0, 0, ""))
        val imsiNow = ServiceManager.systemApi.getSubscriberIdBySlot(slot)
        logd("usephy card init: imsi $imsi imsiNow $imsiNow slot:$slot")
        if(imsi?.equals(imsiNow)){
            simState = ServiceManager.systemApi.getSimState(slot)
            logd("usephy card init: simState $simState")
            when (simState) {
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> {
                    cardStatus = CardStateMonitor.SIM_STATE_NOT_READY
                    MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_PIN, 0, 0, ""))
                }
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> {
                    cardStatus = CardStateMonitor.SIM_STATE_NOT_READY
                    MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_PUK, 0, 0, ""))
                }
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> {
                    cardStatus = CardStateMonitor.SIM_STATE_NOT_READY
                    //MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_PIN, 0, 0, ""))
                }
                TelephonyManager.SIM_STATE_READY -> {
                    cardStatus = CardStateMonitor.SIM_STATE_READY
                    mCardSubId = ServiceManager.systemApi.getDefaultDataSubId()
                    MsgEncode.sendWebExceptionPortal(mqtt, WebPortalInfo(EXP_RSIM_STATE, UC_RSIM_READY, 0, 0, ""))
                    if(ServiceManager.systemApi.getDefaultDataSubId() != mCardSubId){
                        ServiceManager.systemApi.setDefaultDataSubId(mCardSubId)
                    } else{
                        if(mPhone.dataState == TelephonyManager.DATA_CONNECTED && SubscriptionManager.getPhoneId(mCardSubId) == slot){
                            val telephoneManager = TelephonyManager.from(ServiceManager.appContext)
                            var netRat = telephoneManager.getNetworkType(ServiceManager.phyCardWatcher.getSubIdBySlot(slot))
                            logd("UserPhyCardState ready: netRat $netRat ")
                            MsgEncode.sendCloudSimRat(mqtt,netRat)
                            MsgUpdate.updateServiceOk(ctx, mqttClient)
                            isNetworkOk = true
                            ServiceManager.systemApi.registerDefaultNetworkCallback(netCallback)
                        }
                    }
                    if(mCardSubId >= 0) {
                        val regOpera = ServiceManager.systemApi.getNetworkOperatorForSubscription(mCardSubId)
                        logd("get reg opera $regOpera")
                        if(regOpera != null && regOpera.length != 0){
                            isRegOn = true
                        }
                        isRoam = mPhone.isNetworkRoaming(mCardSubId)
                    }
                }
            }
        }

        ServiceManager.simMonitor.addNetworkStateListen(netListener)
        ServiceManager.simMonitor.addCardStateListen(cardListener)
        if (mCardSubId >= 0 && listener == null) {
            listener = InnerPhoneListener(mCardSubId, looper)
            mPhone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
        }
        logd("start init UserPhyCardState over: mCardSubId $mCardSubId cardStatus $cardStatus isNetworkOk $isNetworkOk, isRegOn $isRegOn")
    }


    fun deInit() {
        ServiceManager.simMonitor.removeStatuListen(netListener)
        ServiceManager.simMonitor.removeStatuListen(cardListener)
        if(listener != null){
            mPhone.listen(listener, PhoneStateListener.LISTEN_NONE)
        }
        ServiceManager.systemApi.unregisterNetworkCallback(netCallback)
    }

    override fun toString(): String {
        return "UserPhyCardState(ctx=$ctx, mqtt=$mqtt, simState=$simState, superSelect=$superSelect, isRegOn=$isRegOn, isNetworkOk=$isNetworkOk, isRoam=$isRoam, cardStatus=$cardStatus, mCardSubId=$mCardSubId, mPhone=$mPhone, listener=$listener, netCallback=$netCallback, netListener=$netListener, cardListener=$cardListener)"
    }
}