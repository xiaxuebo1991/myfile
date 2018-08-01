package com.ucloudlink.refact.product.mifi.connect.mqtt

import android.net.NetworkInfo
import android.telephony.TelephonyManager.*
import com.ucloudlink.framework.protocol.protobuf.mifi.*
import com.ucloudlink.refact.ServiceManager

import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.product.mifi.connect.struct.*
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import okio.ByteString
import rx.Single
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


/**
 * Created by shiqianhua on 2018/1/17.
 */
object MsgEncode {

    // Uservice: 20001 - 30000
    val SEQ_CODE_MIN = 20001
    val SEQ_CODE_MAX = 30000

    var mSeqCode = SEQ_CODE_MIN
    fun getSeqId():Int{
        synchronized(mSeqCode){
            mSeqCode++
            if(mSeqCode >= SEQ_CODE_MAX) {
                mSeqCode = SEQ_CODE_MIN
            }
            return mSeqCode
        }
    }

    fun sendLedAbnormal(mqtt:UcMqttClient, abnormal:Boolean, type:Int){
        logd("send sendLedAbnormal $mqtt, $abnormal $type")
        val ab = if(abnormal) 1 else 0
        val led_ab = uc_led_abnormal.Builder().is_abnormal(ab).abnormal_type(led_abnormal_type.LED_ABNORMAL_ACCOUNT_ERROR).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.MSG_ID_LED_ABNORMAL).token(getSeqId()).led_abnormal(led_ab).build()
        mqtt.sendMsg(mqtt.ledModule, ucMsg.encode())
    }

    fun sendLedDatacall(mqtt:UcMqttClient, datacall: MsgDatacallInfo){
        logd("send sendLedDatacall $mqtt $datacall")
        val dataC = uc_led_datacall.Builder().datacall_status(datacall.datacall).ip_type(datacall.iptype).dns(datacall.dns).ipaddress(datacall.ipaddr)
                .gateway(datacall.gateWay).netmask(datacall.netMask).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.MSG_ID_LED_DATACALL).token(getSeqId()).led_data_call(dataC).build()
        mqtt.sendMsg(mqtt.ledModule, ucMsg.encode())
    }

    fun sendLedServiceStart(mqtt:UcMqttClient){
        logd("sendLedServiceStart mqtt!")
        val comm = uc_led_comm_msg.Builder().ret(0).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.MSG_ID_LED_SERVICE_START).token(getSeqId()).led_service_start(comm).build()
        mqtt.sendMsg(mqtt.ledModule, ucMsg.encode())
    }

    fun sendLedCloudSignalStrengthLevel(mqtt: UcMqttClient, level:Int){
        val cloudNetworkStatus = ServiceManager.cloudSimEnabler.getNetState()
        //云卡网络状态不在CONNECTED状态时，不发送信号强度消息给led
        if (cloudNetworkStatus != NetworkInfo.State.CONNECTED){
            logd("cloudSimEnabler.netState:$cloudNetworkStatus not CONNECTED,don't send strength msg to led!")
            return
        }
        logd("send level to led! $level")
        val level = uc_cloud_signal_strength_level.Builder().level(level).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.MSG_ID_CLOUDSIM_SIGNAL_STRENGTH_LEVEL).token(getSeqId()).cloud_signal_level(level).build()
        mqtt.sendMsg(mqtt.ledModule, ucMsg.encode())
    }

    fun sendLedPoweroffRsp(mqtt: UcMqttClient, token:Int, result:Int){
        logd("sendLedPoweroffRsp! $token $result")
        val msg = uc_led_comm_msg.Builder().ret(result).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.MSG_ID_CLOUDSIM_SIGNAL_STRENGTH_LEVEL).token(token).led_poweroff_rsp(msg).build()
        mqtt.sendMsg(mqtt.ledModule, ucMsg.encode())
    }

    /**
     *  LOGIN_INIT = 0;                     /*初始状态*/
        LOGIN_ING = 1;                      /*登录中*/
        LOGIN_FAIL = 2;                     /*登录失败*/
        LOGIN_OK = 3;                       /*登录成功*/
     */
    fun sendWebLoginRsp(mqtt: UcMqttClient, status:Int){
        logd("sendWebLoginRsp $mqtt, $status")
        val login = uc_glome_login.fromValue(status)
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_LOGIN_STAT).token(getSeqId()).login_status(login).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }

    fun sendWebDatacall(mqtt: UcMqttClient, datacall: MsgDatacallInfo){
        logd("sendWebDatacall $mqtt $datacall")
        val status = uc_glome_datacall.fromValue(datacall.datacall)
        val dataC = uc_glome_datacall_state.Builder().dataCallState(status).ip_type(datacall.iptype).dns(datacall.dns).ipaddress(datacall.ipaddr)
                .gateway(datacall.gateWay).netmask(datacall.netMask).sleep_status(datacall.sleepStatus).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_DATACALL_STAT).token(getSeqId()).datacall_status(dataC).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }

    fun sendWebExceptionPortal(mqtt: UcMqttClient, portalInfo: WebPortalInfo){
        logd("sendWebExceptionPortal $mqtt, $portalInfo")
        val portal_status = uc_portal_status.fromValue(portalInfo.portal)
        val exp_info :uc_glome_exp_info
        if (portalInfo.portal != EXP_SERVICE_NOTICE_ONLYONCE){
            exp_info = uc_glome_exp_info.Builder().state_type(portal_status).err_code(portalInfo.errcode).run_step(portalInfo.runStep)
                    .more_info_flag(0).more_info("").build()
        }else {
            exp_info = uc_glome_exp_info.Builder().state_type(portal_status).err_code(portalInfo.errcode).run_step(portalInfo.runStep)
                    .more_info_flag(portalInfo.infoFlag).more_info(portalInfo.info).build()
        }
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_EXCEPTION_NOTIFY).token(getSeqId()).exception_info(exp_info).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }

    fun sendWebUserAccountInfo(mqtt: UcMqttClient, userAccountInfo: UserAccountInfo){
        logd("sendWebUserAccountInfo $mqtt, $userAccountInfo")
        val user_type = uc_user_type.fromValue(userAccountInfo.user_type)
        val account_info = uc_glome_account_info.Builder().amount(userAccountInfo.amount).rate(userAccountInfo.rate).country_name(userAccountInfo.country_name)
                    .package_num(userAccountInfo.package_num).user_type(user_type).is_show_3g(userAccountInfo.is_show_3g).package_(userAccountInfo.packge)
                    .accumulated_flow(userAccountInfo.accumulated_flow).reserved(userAccountInfo.reserved).display_flag(userAccountInfo.display_flag)
                    .cssType(userAccountInfo.cssType).unit(userAccountInfo.unit).build()

        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_ACCOUNT_INFO).token(getSeqId()).account_info(account_info).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }

    /**
     * 卡制式
     */
    fun sendCloudSimRat(mqtt: UcMqttClient,ucNetType: Int){
        logd("sendCloudSimRat $mqtt, $ucNetType")
        var type :uc_radio_tech = uc_radio_tech.RADIO_UNKNOWN
        when(ucNetType){
                NETWORK_TYPE_UNKNOWN  ->{
                    type = uc_radio_tech.RADIO_UNKNOWN
                }
                NETWORK_TYPE_GPRS ->{
                    type = uc_radio_tech.RADIO_GPRS
                }
                NETWORK_TYPE_EDGE->{
                    type = uc_radio_tech.RADIO_EDGE
                }
                NETWORK_TYPE_UMTS->{
                    type = uc_radio_tech.RADIO_UMTS
                }
                NETWORK_TYPE_HSDPA->{
                    type = uc_radio_tech.RADIO_HSDPA
                }
                NETWORK_TYPE_HSUPA->{
                    type = uc_radio_tech.RADIO_HSUPA
                }
                NETWORK_TYPE_HSPA->{
                    type = uc_radio_tech.RADIO_HSPA
                }
                NETWORK_TYPE_CDMA->{
                    type = uc_radio_tech.RADIO_IS95A
                }
                NETWORK_TYPE_EVDO_0->{
                    type = uc_radio_tech.RADIO_EVDO_0
                }
                NETWORK_TYPE_EVDO_A->{
                    type = uc_radio_tech.RADIO_EVDO_A
                }
                NETWORK_TYPE_EVDO_B->{
                    type = uc_radio_tech.RADIO_EVDO_B
                }
                NETWORK_TYPE_1xRTT->{
                    type = uc_radio_tech.RADIO_1xRTT
                }
                NETWORK_TYPE_IDEN->{
                    type = uc_radio_tech.RADIO_GSM
                }
                NETWORK_TYPE_LTE->{
                    type = uc_radio_tech.RADIO_LTE
                }
                NETWORK_TYPE_EHRPD->{
                    type = uc_radio_tech.RADIO_EHRPD
                }
                NETWORK_TYPE_HSPAP->{
                    type = uc_radio_tech.RADIO_HSPAP
                }
        }
//        val net_type = uc_radio_tech.fromValue(ucNetType)
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_RADIO_TECH_STAT).token(getSeqId()).radio_tech(type).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }
    /**
     * wifi客户端上下线事件
     */
    fun sendWebWifiConnectionChanged(mqtt: UcMqttClient,eventInfo: ByteString){
        logd("sendWebWifiConnectionChanged $mqtt, $eventInfo")
        val updownevent =  uc_glome_wifi_client_up_down_event.Builder().data(eventInfo).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_WIFI_CLIENT_UP_DOWN_EVENT).token(getSeqId()).up_down_event(updownevent).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }

    /**
     * web界面状态
     */
    fun sendWebCardStatus(mqtt: UcMqttClient, websimCfg:WebSimUserConfig){
        logd("sendWebCardStatus $websimCfg")
        val channel = kotlin.run {
            when(websimCfg.dataChannel){
                0 -> return@run uc_sim_channel.GLOCALME
                1 -> return@run uc_sim_channel.SIM1
                2 -> return@run uc_sim_channel.SIM2
                3 -> return@run uc_sim_channel.SMART
                else -> return@run uc_sim_channel.GLOCALME
            }
        }
        val userCfg = uc_glome_sim_user_config.Builder().data_channel(channel).sim1_cfg(uc_sim_cfg.DATA_CHANNEL).sim2_cfg(uc_sim_cfg.DATA_CHANNEL)
                .sim1_exist(websimCfg.sim1Exist).sim2_exist(websimCfg.sim2Exist).imsi1(websimCfg.imsi1?:"").imsi2(websimCfg.imsi2?:"")
                .pin1(websimCfg.pin1).pin2(websimCfg.pin2).new_insert1(websimCfg.sim1New).new_insert2(websimCfg.sim2New)
                .puk1(websimCfg.puk1).puk2(websimCfg.puk2).build()

        var roamCfg = uc_rsim_roam.Builder().roam_sim1(uc_sim_roam.fromValue(websimCfg.roam1)).roam_sim2(uc_sim_roam.fromValue(websimCfg.roam2)).build()

        logd("sendWebCardStatus upload sim info")
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_SIM_CHANNEL_STAT).token(getSeqId()).sim_config_status(userCfg).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
        logd("sendWebCardStatus upload sim roam")
        val ucMsgRoam = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_RSIM_ROAM_STAT).token(getSeqId()).rsim_roam(roamCfg).build()
        mqtt.sendMsg(mqtt.webModule, ucMsgRoam.encode())
    }

    //gps
    fun setNetworkGpsSwitch(mqtt: UcMqttClient, hardGps:Boolean, networkGps:Boolean, timeout:Long):Single<Any>{
        val switch = kotlin.run {
            if(hardGps && networkGps){
                return@run uc_gps_switch_state.ALL
            }else if(hardGps){
                return@run uc_gps_switch_state.SATELLITE
            }else if(networkGps){
                return@run uc_gps_switch_state.NETWORK
            }else{
                return@run uc_gps_switch_state.NONE
            }
        }

        val gps_state_set = uc_gps_state_set.Builder().gps_switch_state(switch).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.SER_SET_GPS_STATE).token(getSeqId()).gps_state_set(gps_state_set).build()

        return Single.create<Any> { sub ->
            requestMsg(mqtt, ucMsg, timeout).subscribe(
                    {
                        it as uc_msg_api
                        if(it.gps_state_set_rsp == null){
                            sub.onError(Exception("gps_state_set_rsp is null"))
                        }else{
                            sub.onSuccess(it.gps_state_set_rsp)
                        }
                    },
                    {
                        sub.onError(it)
                    }
            )
        }
    }


    fun requestMsg(mqtt: UcMqttClient, msg:uc_msg_api, timeout:Long):Single<Any>{
        return Single.create<Any> {sub ->
            val ret = mqtt.sendMsg(mqtt.gpsModule, msg.encode())
            if(ret != 0){
                sub.onError(Exception("send msg failed!"))
                return@create
            }

            MsgDecode.recvMsgOb.timeout(timeout, TimeUnit.SECONDS)
                    .filter {
                        if(it is uc_msg_api){
                            it.token == msg.token
                        }else{
                            false
                        }
                    }
                    .subscribe(
                        {
                            sub.onSuccess(it)
                        },
                        {
                            loge("timeout!")
                            sub.onError(TimeoutException("timeout!"))
                        }
                    )
        }
    }

    //fota
    fun sendFotaDatacall(mqtt: UcMqttClient, datacall: MsgDatacallInfo){
        logd("sendFotaDatacall $mqtt $datacall")
        val status = uc_glome_datacall.fromValue(datacall.datacall)
        val dataC = uc_glome_datacall_state.Builder().dataCallState(status).ip_type(datacall.iptype).dns(datacall.dns).ipaddress(datacall.ipaddr)
            .gateway(datacall.gateWay).netmask(datacall.netMask).sleep_status(datacall.sleepStatus).build()
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_DATACALL_STAT).token(getSeqId()).datacall_status(dataC).build()
        mqtt.sendMsg(mqtt.fotaModule, ucMsg.encode())
    }

    fun sendWebEconomizeDataUsageStatus(mqtt: UcMqttClient, token: Int, value: Int){ //uc_msg_id_e.WEB_REQ_ECONOMIZE_DATA_USAGE_STATUS
        logd("CloudFlowProtectionLog sendWebEconomizeDataUsageStatus $mqtt, $token, $value")
        val status = uc_econ_data_usage.fromValue(value)
        val ucMsg = uc_msg_api.Builder().msg_id(uc_msg_id_e.GM_RSP_ECONOMIZE_DATA_USAGE_STATUS).token(token).rsp_econ_data_usage_status(status).build()
        mqtt.sendMsg(mqtt.webModule, ucMsg.encode())
    }


}