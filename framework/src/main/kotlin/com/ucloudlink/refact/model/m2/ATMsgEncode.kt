package com.ucloudlink.refact.model.m2

import com.ucloudlink.framework.protocol.protobuf.mifi.*
import com.ucloudlink.refact.product.mifi.connect.mqtt.msgpack.UcMqttClient
import com.ucloudlink.refact.utils.JLog
import java.util.*

/**
 * Created by zhanlin.ma on 2018/3/28.
 */
object ATMsgEncode{

    // M2AT: 80001 - 90000
    val SEQ_CODE_MIN = 80001
    val SEQ_CODE_MAX = 90000

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

    //获取工作模式响应（云卡还是用户卡）
    fun sendGetModeRsp(mqtt: M2MqttClient, token:Int, mode :Int, result: String){
        JLog.logd("获取 工作模式响应")
        val workMode = m2at_work_mode.fromValue(mode)
        val rsp = m2at_get_gmode_resp.Builder().work_mode(workMode).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_GMODE_RESP).token(token).at_get_gmode_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //设置工作模式的响应
    fun sendSetModeRsp(mqtt: M2MqttClient, token:Int,result :String){
        JLog.logd("设置工作模式的响应")
        val rsp = m2at_set_gmode_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_GMODE_RESP).token(token).at_set_gmode_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //设置wifi的响应
    fun sendSetWifiRsp(mqtt: M2MqttClient, token:Int,result :String){
        JLog.logd("设置wifi的响应")
        val rsp = m2at_set_wifi_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_WIFI_RESP).token(token).at_set_wifi_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    //get wifi ssid response
    fun sendGetWifiSsidRsp(mqtt: M2MqttClient, token:Int,ssid:String,result :String){
        JLog.logd("send get wifi ssid response ")
        val rsp = m2at_get_wifissid_resp.Builder().ssid(ssid).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_WIFISSID_RESP).token(token).at_get_wifissid_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    //get wifi pwd response
    fun sendGetWifiPwdRsp(mqtt: M2MqttClient, token:Int,pwd:String,result :String){
        JLog.logd("send get wifi pwd response ")
        val rsp = m2at_get_wifipwd_resp.Builder().pwd(pwd).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_WIFIPWD_RESP).token(token).at_get_wifipwd_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    //set qos response
    fun sendSetQosRsp(mqtt: M2MqttClient, token:Int,result :String){
        JLog.logd("send set qos  response ")
        val rsp = m2at_set_qos_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_QOS_RESP).token(token).at_set_qos_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendGetQosRsp(mqtt: M2MqttClient, token:Int,upSpeed:Int,downSpeed:Int,result :String){
        JLog.logd("send get qos  response ")
        val rsp = m2at_get_qos_resp.Builder().vsim_speed(upSpeed).wifi_usb_speed(downSpeed).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_QOS_RESP).token(token).at_get_qos_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //查询漫游开关响应
    fun sendGetRoamKeySendRsp(mqtt: M2MqttClient, token:Int,roamType :Int,result:String){
        JLog.logd("查询漫游开关响应")
        val type = m2at_roam_type.fromValue(roamType)
        val rsp = m2at_get_roamkey_resp.Builder().roam_staus(type).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_ROMEKY_RESP).token(token).at_get_roamkey_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //设置漫游开关响应
    fun sendSetRoamTypeRsp(mqtt: M2MqttClient, token:Int,result:String){
        val rsp = m2at_set_roamkey_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_ROMEKY_RESP).token(token).at_set_roamkey_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())

    }

    //恢复出厂设置
    fun sendRestoreRsp(mqtt: M2MqttClient, token:Int,result:String){
        val rsp = m2at_restore_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_RESTORE_RESP).token(token).at_restore_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule,msg.encode())
    }

    fun sendSetLogModeRsp(mqtt: M2MqttClient, token:Int,result:String){
        val rsp = m2at_logmode_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_LOGMODE_RESP).token(token).at_logmode_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule,msg.encode())
    }

    //打开关闭热点
    fun sendSetWifiApRsp(mqtt: M2MqttClient, token:Int,result:String){
        val rsp = m2at_set_wifiap_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_WIFIAP_RESP).token(token).at_set_wifiap_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    //设置流量防护开关
    fun sendSetUcffRsp(mqtt: M2MqttClient, token:Int,result:String){
        val rsp = m2at_set_ucff_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_UCFF_RESP).token(token).at_set_ucff_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    fun sendGetApnRsp(mqtt: M2MqttClient, token:Int,result:String){

    }
    //查询wifi热点接入人数
    fun sendGetWifiClientRsp(mqtt: M2MqttClient, token:Int,count:Int,result:String){
        val rsp = m2at_get_wificlient_resp.Builder().cli_num(count).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_WIFICLIENT_RESP).token(token).at_get_wificlient_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //查询wifi热点接入客户端ip
    fun sendGetWifiIpRsp(mqtt: M2MqttClient, token:Int,ip:String,result:String){
        val rsp = m2at_get_wifiip_resp.Builder().cli_ip(ip).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_WIFIIP_RESP).token(token).at_get_wifiip_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //查询wifi热点接入客户端名称
    fun sendGetWifiNameRsp(mqtt: M2MqttClient, token:Int,name:String,result:String){
        val rsp = m2at_get_wifiname_resp.Builder().cli_name(name).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_WIFINAME_RESP).token(token).at_get_wifiname_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //查询当前GLOCALME业务套餐模式及名称响应
    fun sendGetPkgRsp(mqtt:M2MqttClient, token:Int, amount:String,rate:String,mcc:String,pakagename:String,intflow:String,surplusflow:String,activetime:String,expiretime:String,result:String){
        val rsp = m2at_get_pkg_resp.Builder().amount(amount).rate(rate).mcc(mcc).pakagename(pakagename).intflow(intflow).surplusflow(surplusflow).activetime(activetime).expiretime(expiretime).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_PKG_RESP).token(token).at_get_pkg_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //退出glocalme业务
    fun sendLogoutRsp(mqtt:M2MqttClient, token:Int,result: String){
        val rsp = m2at_logout_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_LOGOUT_RESP).token(token).at_logout_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //查询GLOCALME业务接入IP地址响应
    fun sendGetLoginIpRsp(mqtt:M2MqttClient, token:Int, ipList: ArrayList<String?>, result: String){
        val rsp = m2at_get_loginip_resp.Builder().loginip(ipList.toString()).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_LOGINIP_RESP).token(token).at_get_loginip_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    //设置GLOCALME业务的接入IP地址响应
    fun sendSetLoginIpRsp(mqtt:M2MqttClient, token:Int, result: String){
        val rsp = m2at_set_loginip_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_LOGINIP_RESP).token(token).at_set_loginip_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //查询登录模式
    fun sendGetLoginModeRsp(mqtt:M2MqttClient, token:Int, mode: Int, result: String){
        val rsp = m2at_get_loginmode_resp.Builder().loginmode(mode).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_LOGINMODE_RESP).token(token).at_get_loginmode_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    //设置登录模式
    fun sendSetLoginModeRsp(mqtt:M2MqttClient, token:Int,result: String){
        val rsp = m2at_set_loginmode_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_LOGINMODE_RESP).token(token).at_set_loginmode_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }
    fun sendGetFlowCtlRsp(mqtt: M2MqttClient, token:Int, mode:Int,result: String){
        JLog.logd("sendQueryFlowCtlRsp mode:$mode")
        val rsp = m2at_get_ucff_resp.Builder().ucff_mode(mode).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_UCFF_RESP).token(token).at_get_ucff_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendGetVsimUpRsp(mqtt: M2MqttClient, token: Int, upEnable:Int,result: String){
        JLog.logd("sendGetVsimUpRsp upEnable:$upEnable")
        val rsp = m2at_get_vsimup_resp.Builder().up_enable(upEnable).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_VSIMUP_RESP).token(token).at_get_vsimup_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendSetVsimUpRsp(mqtt: M2MqttClient, token: Int,result: String){
        JLog.logd("sendGetVsimUpRsp")
        val rsp = m2at_set_vsimup_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SET_VSIMUP_RESP).token(token).at_set_vsimup_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendGetRunStepRsp(mqtt: M2MqttClient, token: Int, step:Int,result: String){
        JLog.logd("sendGetRunStepRsp step:$step")
        val rsp = m2at_get_runstep_resp.Builder().step(step.toString()).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_RUNSTEP_RESP).token(token).at_get_runstep_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendGetAccentRsp(mqtt: M2MqttClient, token: Int, userName:String, pwd:String,result: String){
        JLog.logd("sendGetAccentRsp username:$userName ,pwd:$pwd")
        val rsp = m2at_get_account_resp.Builder().username(userName).passwd(pwd).result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_GET_ACCOUNT_RESP).token(token).at_get_account_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendSwitchCardRsp(mqtt: M2MqttClient, token: Int,result: String){
        val rsp = m2at_switchvsim_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SWITCHVSIM_RESP).token(token).at_switchvsim_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendReloginRsp(mqtt: M2MqttClient, token: Int,result: String){
        val rsp = m2at_relogin_resp.Builder().result(result).build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_RELOGIN_RESP).token(token).at_relogin_resp(rsp).build()
        mqtt.sendMsg(mqtt.atModule, msg.encode())
    }

    fun sendTest(mqtt: M2MqttClient){
        val send = m2at_switchvsim_send.Builder().cmd("test").build()
        val msg = uc_msg_api.Builder().msg_id(uc_msg_id_e.M2AT_SWITCHVSIM_SEND).token(getSeqId()).at_switchvsim_send(send).build()
        mqtt.sendMsg(mqtt.myModule, msg.encode())
    }
}