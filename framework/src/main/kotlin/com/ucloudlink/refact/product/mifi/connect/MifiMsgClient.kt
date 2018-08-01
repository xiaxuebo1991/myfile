package com.ucloudlink.refact.product.mifi.connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.telephony.SubscriptionManager
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessState
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgEncode
import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.product.mifi.connect.struct.*
import com.ucloudlink.refact.product.mifi.misc.MsgUpdate
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import okio.ByteString

/**
 * Created by shiqianhua on 2018/1/18.
 */
class MifiMsgClient(context: Context) {
    var WIFI_CONNET_CHANGED_ACTION = "android.net.wifi.WIFI_AP_CONNECTION_CHANGED_ACTION"
    var WIFI_EXTRA_MAC = "wifiap_station_mac"
    var WIFI_EXTRA_ISCONNECTED = "wifiap_station_is_connected"
    val ctx = context
    var lastPersent = 0
    lateinit var mqttClient:UcMqttClient
//    lateinit var m2mqttclient:M2MqttClient //add for test mzl at 2018.04.04
    //val mConnManager = ConnectivityManager.from(ctx)

    val netCallback = object : ConnectivityManager.NetworkCallback(){
        override fun onLinkPropertiesChanged(network: Network?, linkProperties: LinkProperties?) {
            logd("onLinkPropertiesChanged $network $linkProperties")
            val subid = ServiceManager.systemApi.getDefaultDataSubId()
            if(linkProperties != null && linkProperties.isProvisioned
                    && (ServiceManager.cloudSimEnabler.isCardOn() && !ServiceManager.cloudSimEnabler.isClosing())
                    && (subid > 0 && SubscriptionManager.getPhoneId(subid) == Configuration.cloudSimSlot)){
                val datacall = MsgUpdate.getDatacallInfoByLink(linkProperties)
                MsgEncode.sendLedDatacall(mqttClient, datacall)
                MsgEncode.sendWebDatacall(mqttClient, datacall)
            }
        }
    }

    init {
        mqttClient = UcMqttClient(context)
//        m2mqttclient = M2MqttClient(context)
        regMifiClientEventRev(ctx)

        val accListener = object : AccessState.AccessStateListen {
            override fun errorUpdate(errorCode: Int, message: String?) {
                JLog.logd("errorUpdate errorCode == $errorCode message == $message" )
                var portal = getPortalByCode(errorCode)

                if (portal != -1)
                    MsgEncode.sendWebExceptionPortal(mqttClient, WebPortalInfo(portal, errorCode, 0, if (message != null) 1 else 0, if (message != null) message else ""))
            }

            override fun processUpdate(persent: Int) {

            }

            override fun eventCloudSIMServiceStop(reason: Int, message: String?) {
                JLog.logd("eventCloudSIMServiceStop reason == $reason message == $message")
                var portal = getPortalByCode(reason)
                if (portal == -1)
                    portal = EXP_SYSTEM_BUSY
                MsgEncode.sendWebExceptionPortal(mqttClient, WebPortalInfo(portal, reason, 0, if (message != null) 1 else 0, if (message != null) message else ""))
                MsgEncode.sendLedAbnormal(mqttClient, true, reason)
            }

            override fun eventCloudsimServiceSuccess() {

            }

            override fun eventSeedState(persent: Int) {

            }

            override fun eventSeedError(code: Int, message: String?) {

            }

            private fun getPortalByCode(code: Int) : Int{
                var portal = -1
                when(code){
                    ErrorCode.RPC_NO_VSIM_AVAILABLE,ErrorCode.RPC_USER_NOT_EXIST,ErrorCode.RPC_PASSWD_CHECK_FAIL,ErrorCode.RPC_NO_ACTIVATE_AFTER_FREE_USE,ErrorCode.RPC_FEE_NOT_ENOUGH_FOR_DAILY_PACKAGE,
                    ErrorCode.RPC_NOT_IN_FAVOR_COUNTRY,ErrorCode.RPC_ASS_UNKNOWN_ERR ,ErrorCode.LOCAL_PHY_CARD_NOT_EXIST,ErrorCode.LOCAL_SOFT_CARD_NOT_EXIST,ErrorCode.LOCAL_PHONE_DATA_DISABLED,
                    ErrorCode.LOCAL_PHY_NETWORK_UNAVAILABLE,ErrorCode.LOCAL_PHONE_CALLING,ErrorCode.LOCAL_PHY_CARD_DISABLE,ErrorCode.LOCAL_AIR_MODE_ENABLED,ErrorCode.LOCAL_APP_RECOVERY_TIMEOUT,
                    ErrorCode.LOCAL_USER_LOGIN_OTHER_PLACE,ErrorCode.LOCAL_BIND_CHANGE,ErrorCode.LOCAL_ROAM_NOT_ENABLED,
                    ErrorCode.LOCAL_ORDER_IS_NULL,ErrorCode.LOCAL_ORDER_INFO_IS_NULL,ErrorCode.LOCAL_ORDER_SOFTSIM_NULL,ErrorCode.LOCAL_ORDER_INACTIVATE,
                    ErrorCode.LOCAL_ORDER_OUT_OF_DATE,ErrorCode.LOCAL_USERNAME_INVALID,ErrorCode.LOCAL_SERVICE_RUNNING,ErrorCode.LOCAL_SECURITY_FAIL,ErrorCode.LOCAL_SECURITY_TIMEOUT,
                    ErrorCode.LOCAL_BIND_NETWORK_FAIL,ErrorCode.LOCAL_USER_AIR_MODE_ENABLE,ErrorCode.LOCAL_USER_PHONE_DATA_DISABLE,ErrorCode.LOCAL_USER_CHANGE_DDS,ErrorCode.LOCAL_USER_PHONE_CALL_START,
                    ErrorCode.LOCAL_USER_SEED_SIM_DISABLE,ErrorCode.LOCAL_USER_WIFI_CONNECTED,ErrorCode.LOCAL_USER_APP_TO_BLACKLIST,ErrorCode.LOCAL_USER_CLOUD_SIM_DISABLE,ErrorCode.LOCAL_AIR_MODE_OVER_10MIN,
                    ErrorCode.LOCAL_SEED_CARD_DISABLE_OVER_10MIN,ErrorCode.LOCAL_CLOUD_CARD_DISABLE_OVER_10MIN,ErrorCode.LOCAL_PHONE_DATA_DISABLE_OVER_10MIN,ErrorCode.LOCAL_APP_IN_BLACKLIST_OVER_10MIN,ErrorCode.LOCAL_DDS_EXCEPTION_OVER_10MIN,
                    ErrorCode.LOCAL_DDS_IN_EXCEPTION,ErrorCode.LOCAL_DDS_SET_TO_NORMAL,ErrorCode.SOFTSIM_DL_LOGIN_TIMEOUT,ErrorCode.SOFTSIM_DL_DISPATCH_TIMEOUT,ErrorCode.SOFTSIM_DL_GET_SOFTSIM_INFO_TIMEOUT,
                    ErrorCode.SOFTSIM_DL_GET_BIN_TIMEOUT,ErrorCode.SOFTSIM_DL_USER_CANCEL,ErrorCode.SOFTSIM_DL_SOCKET_TIMEOUT,ErrorCode.SOFTSIM_DL_CHANGE_NEW_USER,ErrorCode.SOFTSIM_DL_NO_ORDER,
                    ErrorCode.SOFTSIM_DL_NO_SOFTSIM,ErrorCode.SOFTSIM_DL_BIN_FILE_NULL,ErrorCode.SOFTSIM_DL_NETWORK_TIMEOUT,ErrorCode.SOFTSIM_DL_SEED_NETWORK_FAIL,ErrorCode.SOFTSIM_UP_SESSION_INVALID,
                    ErrorCode.SOFTSIM_UP_USER_CANCEL,ErrorCode.SOFTSIM_UP_DL_START,ErrorCode.SOFTSIM_DOWNLOAD_ADDCARD_FAIL,ErrorCode.CARD_EXCEP_PHY_CARD_IS_NULL,ErrorCode.CARD_EXCEP_PHY_CARD_DEFAULT_LOST,
                    ErrorCode.CARD_DATA_ENABLE_CLOSED,ErrorCode.CARD_ROAM_DATA_ENABLE_CLOSED,ErrorCode.CARD_EXCEPT_NO_AVAILABLE_SOFTSIM,ErrorCode.CARD_EXCEPT_REG_DENIED,ErrorCode.CARD_EXCEPT_NET_FAIL,
                    ErrorCode.CARD_PHY_ROAM_DISABLE,ErrorCode.SEED_CARD_CANNOT_BE_CDMA,ErrorCode.LOCAL_DEVICE_IS_DISABLE,ErrorCode.LOCAL_HOST_LOGIN_SLEEP -> portal = EXP_LOGIN_ABNORMAL//8 73

                    ErrorCode.RPC_GET_USERINFO_FAIL,ErrorCode.RPC_EMPTY_MCC_FROM_TER,ErrorCode.RPC_NO_PRODUCT_BY_ID,ErrorCode.RPC_NO_POLICY_BY_ID,ErrorCode.RPC_NO_CARD_POOL_BY_POLICY,
                    ErrorCode.RPC_NO_GROUP_BY_IMSI,ErrorCode.RPC_RAT_ERROR,ErrorCode.RPC_USER_OR_PASSWD_NULL_OR_NOT_IN_BIND,ErrorCode.RPC_BOTH_HAVE_DAILY_MONTHLY_PACKAGE,ErrorCode.RPC_MONTHLY_USERS_FULL,
                    ErrorCode.RPC_GET_USER_INFO_FAIL,ErrorCode.RPC_IMEI_NOT_EXIST,ErrorCode.RPC_IMEI_ALREADY_DELETED,ErrorCode.RPC_GET_ACCESS_TOKEN_ERR,ErrorCode.RPC_USER_ACCOUNT_ERR,
                    ErrorCode.RPC_BSS_UNKNOWN_ERR,ErrorCode.RPC_ACCOUNT_IS_DISALBE,ErrorCode.RPC_CALL_BSS_FAIL,ErrorCode.RPC_CALL_CSS_FAIL,ErrorCode.RPC_CALL_OSS_FAIL,
                    ErrorCode.RPC_CALL_BAM_FAIL,ErrorCode.RPC_APDU_DEAL_ERR,ErrorCode.RPC_CALL_DUBBO_FAIL,ErrorCode.RPC_CALL_SYSTEM_SERVICE_FAIL,ErrorCode.RPC_DISPATCH_CARD_FAIL,
                    ErrorCode.RPC_CALL_LOGIN_AUTH,ErrorCode.LOCAL_SERVER_UNKNOWN_ERR,ErrorCode.LOCAL_SERVER_PACKAGE_PARSE_ERR,ErrorCode.LOCAL_LOGIN_TIMEOUT,ErrorCode.LOCAL_UNKNOWN_ERROR,
                    ErrorCode.LOCAL_INVALID_VSIM_APN,ErrorCode.LOCAL_INVALID_VSIM_IMSI,ErrorCode.LOCAL_INVALID_VSIM_VIRT_IMEI,ErrorCode.LOCAL_INVALID_SOFT_SIM_IMSI,ErrorCode.LOCAL_INVALID_SOFT_SIM_VIRT_IMEI,
                    ErrorCode.LOCAL_INVALID_SOFT_SIM_APN,ErrorCode.LOCAL_GET_ROUTE_TABLE_FAIL,ErrorCode.SOFTSIM_DL_PARAM_ERR-> portal = EXP_NETWORK_BUSY//9 38


                    ErrorCode.RPC_VSIM_BIN_GET_FAIL,ErrorCode.RPC_NO_AVAILABLE_SOFTSIM,ErrorCode.RPC_SOFTSIM_PARAM_ERROR,ErrorCode.RPC_ORDER_NOT_EXIST,ErrorCode.RPC_CALL_BSS_SERVER_FAIL,
                    ErrorCode.RPC_ORDER_COUNTRY_IS_NULL,ErrorCode.RPC_ORDER_GOODS_NOT_EXIST,ErrorCode.RPC_ORDER_GOODS_IS_NULL,ErrorCode.RPC_ODER_NO_NEED_SOFTSIM -> portal = EXP_SYSTEM_BUSY//10

                    ErrorCode.RPC_NO_NETWORK_AVAILABLE -> portal = EXP_NOSUITABLE_NETWORK//6
                    ErrorCode.RPC_FEE_NOT_ENOUGH ,ErrorCode.LOCAL_FORCE_LOGOUT_FEE_NOT_ENOUGH-> portal = EXP_ACCOUNT_INSUFFICIENT//3
                    ErrorCode.RPC_IMEI_BIND_NOT_EXIST -> portal = EXP_DEVICE_INACTIVE//2

                    ErrorCode.LOCAL_FLOW_CTRL_EXIT,ErrorCode.LOCAL_FORCE_LOGOUT_MANUAL,ErrorCode.LOCAL_TIMEOUT,ErrorCode.LOCAL_NO_SEED_CARD -> portal = EXP_DEVICE_ABNORMAL //5
                }
                return portal
            }
        }
        ServiceManager.accessEntry.accessState.AccessStateListenReg(accListener)
        ServiceManager.accessEntry.accessState.statePersentOb.subscribe(
                {
                    if(lastPersent != it) {
                        logd("persent change $lastPersent -> $it")
                        MsgEncode.sendWebExceptionPortal(mqttClient, WebPortalInfo(EXP_CONNECTING, 0, it,  0, ""))

                        if(lastPersent == 0 && it != 0){
                            MsgEncode.sendLedServiceStart(mqttClient)
                        }

                        if(it == 35) { // login
                            MsgEncode.sendWebLoginRsp(mqttClient, 1)
                        }else if(it == 45){     // login succ!
                            MsgEncode.sendWebLoginRsp(mqttClient, 3)
                        }

                        if(it == 100){
                            // network OK
                            MsgUpdate.updateServiceOk(ctx, mqttClient)
                            ServiceManager.systemApi.registerDefaultNetworkCallback(netCallback)

                        }else if(lastPersent == 100){
                            // datacall down!
                            ServiceManager.systemApi.unregisterNetworkCallback(netCallback)
                            MsgUpdate.updateServiceAb(mqttClient)
                        }

                        lastPersent = it
                    }
                }
        )

        ServiceManager.cloudSimEnabler.cardSignalStrengthObser().subscribe(
                {
                    MsgEncode.sendLedCloudSignalStrengthLevel(mqttClient, it)
                }
        )
    }

    fun regMifiClientEventRev(ctx:Context){
        val mifiClientEventRev = MifiClientEventRev()
        val fifter = IntentFilter(WIFI_CONNET_CHANGED_ACTION)
        ctx.registerReceiver(mifiClientEventRev,fifter )
    }
    //wifi 客户端上下线事件
   inner class MifiClientEventRev : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            JLog.logd("onReceive action=" + intent)
            if (intent != null){
                val action = intent.getAction()
                if (action == WIFI_CONNET_CHANGED_ACTION) {
                    val mac = intent.getStringExtra(WIFI_EXTRA_MAC)//格式：42:fc:89:a8:96:09
                    val isConnected = intent.getBooleanExtra(WIFI_EXTRA_ISCONNECTED, false)
                    logd("onReceive: mac=$mac , isConnected= $isConnected")
                    val macs = mac?.replace(":", "")
                    val updown = if (isConnected) "01" else "02"
                    val string = updown + macs
                    try {
                        val byteString = ByteString.decodeHex(string)
                        MsgEncode.sendWebWifiConnectionChanged(mqttClient, byteString)
                    } catch (e: Exception) {
                        loge(" error: \$e")
                    }
                }
            }
        }
    }
}