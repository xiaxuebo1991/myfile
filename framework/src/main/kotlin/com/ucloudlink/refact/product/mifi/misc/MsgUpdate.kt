package com.ucloudlink.refact.product.mifi.misc

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgEncode
import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.product.mifi.connect.struct.EXP_COMPLETE
import com.ucloudlink.refact.product.mifi.connect.struct.MsgDatacallInfo
import com.ucloudlink.refact.product.mifi.connect.struct.WebPortalInfo
import com.ucloudlink.refact.utils.JLog

/**
 * Created by shiqianhua on 2018/2/6.
 */
class MsgUpdate {
    companion object {
        fun updateServiceOk(ctx:Context, mqttClient: UcMqttClient){
            val mConnManager = ConnectivityManager.from(ctx)
            MsgEncode.sendWebExceptionPortal(mqttClient, WebPortalInfo(EXP_COMPLETE, 0, 100,  0, ""))
            val link = mConnManager.getActiveLinkProperties()
            JLog.logd("LinkProperties :$link")
            if(link == null){
                JLog.loge("link is nul!!!!!!!!!!!!")
                return
            }
            val datacall = getDatacallInfoByLink(link)
            MsgEncode.sendLedDatacall(mqttClient, datacall)
            MsgEncode.sendWebDatacall(mqttClient, datacall)
            MsgEncode.sendFotaDatacall(mqttClient, datacall)
        }

        fun updateServiceAb(mqttClient: UcMqttClient){
            MsgEncode.sendLedDatacall(mqttClient, MsgDatacallInfo(0, "IPV4", "", "", "", "", 1))
            MsgEncode.sendWebDatacall(mqttClient, MsgDatacallInfo(0, "IPV4", "", "", "", "", 1))
            MsgEncode.sendFotaDatacall(mqttClient, MsgDatacallInfo(0, "IPV4", "", "", "", "", 1))

        }

        fun getDatacallInfoByLink(link: LinkProperties): MsgDatacallInfo {
            val datacallInfo = MsgDatacallInfo(1, "IPV4", "0.0.0.0", "1.1.1.1", "1.1.1.1", "1.1.1.1", 1);
            datacallInfo.iptype = kotlin.run {
                if(link.isIPv4Provisioned) return@run "IPV4"
                else if(link.isIPv6Provisioned) return@run "IPV6"
                else return@run "NONE"
            }
            datacallInfo.dns = link.dnsServers[0].hostAddress
            datacallInfo.ipaddr = link.addresses[0].hostAddress
            datacallInfo.gateWay = link.routes[0].gateway.hostAddress
            datacallInfo.netMask = "255.255.255.255"
            datacallInfo.sleepStatus = 0
            return datacallInfo
        }
    }

}