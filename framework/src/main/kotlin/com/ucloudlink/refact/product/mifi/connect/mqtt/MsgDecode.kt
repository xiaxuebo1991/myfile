package com.ucloudlink.refact.product.mifi.connect.mqtt

import android.os.Looper
import android.os.SystemClock
import com.ucloudlink.framework.protocol.protobuf.mifi.*
import com.ucloudlink.framework.protocol.protobuf.user_account_display_resp_type
import com.ucloudlink.framework.util.APN_TYPE_DEFAULT
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.flow.protection.CloudFlowProtectionMgr
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.product.mifi.MifiProductForm
import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.product.mifi.connect.struct.*
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import rx.lang.kotlin.PublishSubject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by shiqianhua on 2018/1/17.
 */
object MsgDecode {
    val mifiProduct = ServiceManager.productApi as MifiProductForm
    var recvMsgOb = PublishSubject<uc_msg_api>()//收到服务器消息发射器
    var tempTime:Long = 0
    var curTime :Long = 0
    fun decodePackage(mqtt: UcMqttClient, src: String, data: ByteArray) {
        logd("recv msg from $src")
        try {
            val msgApi = uc_msg_api.ADAPTER.decode(data)
            processMsg(mqtt, msgApi)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun getApnByStruct(apnSet: uc_sim_apn):Apn?{
        if(apnSet.imsi==null){
            return null
        }
        val apn = Apn()

        apn.apn = apnSet.apn
        apn.user = apnSet.apn_username
        apn.password = apnSet.apn_passwd
        apn.authtype = apnSet.apn_auth.value.toString()
        apn.type = fromStringValue(apnSet.type.value)
        return apn
    }


    private fun fromStringValue(value: Int): String {
        when (value) {
            0 -> return "default"
            1 -> return "ia"
            2 -> return "supl"
            else -> return "default"
        }
    }

    fun processMsg(mqtt: UcMqttClient, msgApi: uc_msg_api) {
        logd("recv msgapi: $msgApi")
        when (msgApi.msg_id) {
        /** led ****/
            uc_msg_id_e.MSG_ID_LED_ABNORMAL_QUERY -> {

            }
            uc_msg_id_e.WEB_REQ_SET_SIM_CHANNEL -> {
                if (msgApi.sim_channel != null) {
                    val dataChannel = kotlin.run {
                        when(msgApi.sim_channel){
                            uc_sim_channel.GLOCALME -> return@run DATA_CHANNEL_GLOCALME
                            uc_sim_channel.SIM1 -> return@run DATA_CHANNEL_SIM1
                            uc_sim_channel.SIM2 -> return@run DATA_CHANNEL_SIM2
                            uc_sim_channel.SMART -> return@run DATA_CHANNEL_SMART
                        }
                    }
                    mifiProduct.phyCardSelect.setPhyCard(dataChannel)
                } else {
                    loge("msgApi.sim_channel is NULL!!!")
                }
            }
            uc_msg_id_e.WEB_REQ_APN_SETTINGS -> {
                if(msgApi.apn_set != null){
                    if(msgApi.apn_set.apn_sim1 != null){
                        mifiProduct.phyCardSelect.setPhySimApn(1, getApnByStruct(msgApi.apn_set.apn_sim1))
                    }
                    if(msgApi.apn_set.apn_sim2 != null){
                        mifiProduct.phyCardSelect.setPhySimApn(2, getApnByStruct(msgApi.apn_set.apn_sim2))
                    }
                }else{
                    loge("msgApi.apn_set is null!")
                }
            }
            uc_msg_id_e.GPS_UPLOAD_POSITION -> {
                logd("recv GPS_RSP_SET_STATE_RESULT")
                if(msgApi.gps_upload_position != null){
                    logd("msgApi.gps_upload_position ${msgApi.gps_upload_position}")
                }else{
                    loge("GPS_UPLOAD_POSITION is null!")
                }
            }
            uc_msg_id_e.MSG_ID_LED_POWEROFF_REQ -> {
                ServiceManager.accessEntry.logoutReq(5)
                val logoutSub = ServiceManager.accessEntry.statePersentOb.timeout(2, TimeUnit.SECONDS)
                        .filter({
                            it == 0
                        })
                        .subscribe(
                                {
                                    MsgEncode.sendLedPoweroffRsp(mqtt, msgApi.token, 0)
                                },
                                {
                                    MsgEncode.sendLedPoweroffRsp(mqtt, msgApi.token, -1)
                                }
                        )

            }
            uc_msg_id_e.WEB_REQ_ROAM_SET -> {
                if(msgApi.roam_set != null) {
                    val value = (msgApi.roam_set == uc_sim_roam.ROAM_ENABLE)
                    mifiProduct.phyCardSelect.setRoamEnable(value)
                }
            }
            uc_msg_id_e.FOTA_UPDATE_START -> {
                ServiceManager.accessEntry.logoutReq(6)
                // TODO need rsp?
            }

            uc_msg_id_e.WEB_REQ_SET_ECONOMIZE_DATA_USAGE -> { // 设置流量防护开关

                if(msgApi.econ_data_usage!=null){
                    loge("CloudFlowProtectionLog econ_data_usage WEB_REQ_SET_ECONOMIZE_DATA_USAGE  <set> +++ value=${msgApi.econ_data_usage.value}")
                    FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.webEnableFlowProtection = (msgApi.econ_data_usage.value == 1)
                    CloudFlowProtectionMgr.handlerWebEnableFlowProtection()
                } else{
                    loge("CloudFlowProtectionLog econ_data_usage is null!")
                }
            }

            uc_msg_id_e.WEB_REQ_ECONOMIZE_DATA_USAGE_STATUS -> {// 查询流量防护开关
                loge("CloudFlowProtectionLog econ_data_usage WEB_REQ_SET_ECONOMIZE_DATA_USAGE <query> +++")
                var ret = 0
                if(CloudFlowProtectionMgr.isFlowfilterEnabled()){
                    ret = 1
                }
                MsgEncode.sendWebEconomizeDataUsageStatus(mqtt, msgApi.token, ret)//ret: 0为关闭/1为开启
            }
            uc_msg_id_e.WEB_REQ_ACCOUNT_INFO  -> {
                val type =  msgApi.req_action
                if(type == uc_req_refresh_type.NOW){
                    getAccountInfo()
                    tempTime = SystemClock.uptimeMillis()
                }else if(type == uc_req_refresh_type.INTERVAL){
                    curTime =  SystemClock.uptimeMillis()
                    if(curTime - tempTime > 30*1000){
                        getAccountInfo()
                        tempTime = SystemClock.uptimeMillis()
                    }
                }
            }
            else -> {
                recvMsgOb.onNext(msgApi)
            }
        }
    }
    fun getAccountInfo(){
        Requestor.getUserAccountDisplay(android.os.SystemProperties.get("ucloud.oem.conf.language"), 30).subscribe({
            if (it is user_account_display_resp_type) {
                JLog.logd("get user account display  " + it)
                if (it.errorCode == 100) {
                    val accountServer = it.user_account_combo
                    var accountWebList: ArrayList<uc_glome_account> = ArrayList()
                    for (account in accountServer) {
                        var isUsed = false
                        if (account.isused == 1) {
                            isUsed = true
                        }
                        val accountItem = uc_glome_account(account.name, account.intflowbyte, account.surplusflowbyte, account.start_time, account.end_time, isUsed)
                        accountWebList.add(accountItem)
                    }
                    val acount_info = UserAccountInfo(it.amount, it.rate, it.country_name, it.user_account_combo.size, accountWebList, 0,
                            PERSONAL, it.accumulated_flow, "", it.dispaly_flag, it.cssType, it.unit)
                    val product = ServiceManager.productApi as MifiProductForm
                    MsgEncode.sendWebUserAccountInfo(product.mifiMsgClient.mqttClient, acount_info)
                }
            } else {
                JLog.loge("get user account display fail")
            }
        }, { t ->
            t.printStackTrace()
            JLog.loge("get user account display fail:" + t.message)
        })
    }
}