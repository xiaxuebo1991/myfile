package com.ucloudlink.refact.model.m2

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.framework.protocol.protobuf.mifi.m2at_restore_resp
import com.ucloudlink.framework.protocol.protobuf.mifi.uc_msg_api
import com.ucloudlink.framework.protocol.protobuf.mifi.uc_msg_id_e
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.AccessEventId
import com.ucloudlink.refact.access.struct.LoginInfo
import com.ucloudlink.refact.business.Requestor
import com.ucloudlink.refact.business.flow.FlowBandWidthControl
import com.ucloudlink.refact.business.flow.netlimit.common.SysUtils
import com.ucloudlink.refact.business.flow.protection.CloudFlowProtectionMgr
import com.ucloudlink.refact.business.routetable.RouteTableManager
import com.ucloudlink.refact.business.routetable.ServerRouter
import com.ucloudlink.refact.business.s2ccmd.TERMINAL_DO_QUIT
import com.ucloudlink.refact.business.s2ccmd.TERMINAL_NOTICE_QUIT
import com.ucloudlink.refact.business.s2ccmd.logcmd.LogUploadCommand
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.product.mifi.connect.mqtt.MsgDecode
import com.ucloudlink.refact.product.module.ATUtils
import com.ucloudlink.refact.utils.JLog
import com.ucloudlink.refact.utils.SharedPreferencesUtils
import com.ucloudlink.refact.utils.WifiApUtil
import rx.lang.kotlin.PublishSubject
import java.io.*
import java.util.*


//import jdk.nashorn.internal.runtime.ScriptingFunctions.readLine


/**
 * Created by zhanlin.ma on 2018/3/28.
 */
object ATMsgDecode{
    var recvMsgOb = PublishSubject<uc_msg_api>()//收到服务器消息发射器
    val file = File("/productinfo/info.obj")
    val file2 = File("/productinfo/info2.obj")
    val WIFIAP_STATUS ="wifiapstatus"
    var inforead: LoginInfo = LoginInfo("", "")
    fun decodePackage(mqtt: M2MqttClient, src: String, data: ByteArray) {
        JLog.logd("recv msg from $src")
        try {
            val msgApi = uc_msg_api.ADAPTER.decode(data)
            ATMsgDecode.processMsg(mqtt, msgApi)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun processMsg(mqtt: M2MqttClient, msgApi: uc_msg_api){
        JLog.logd("recv msgapi: $msgApi")
        when(msgApi.msg_id){
            uc_msg_id_e.M2AT_SET_WIFI_SEND ->{//设置wifi名称和密码（ok）
                val wifiManager= ServiceManager.appContext.applicationContext.getSystemService(Context.WIFI_SERVICE)
                val ssid = msgApi.at_set_wifi_send.ssid
                var passwd = msgApi.at_set_wifi_send.passwd
                JLog.logd("passwd == $passwd")
                JLog.logd("passwd.length == ${passwd.length}")
                val config = WifiApUtil.getWifiConfig()
                if(ssid!=null && ssid != "") {
                    config!!.SSID = ssid
                }else{
                    config!!.SSID = config!!.SSID
                }
                if(passwd!=null && passwd != "") {
                    config!!.preSharedKey = passwd
                }else{
                    config!!.preSharedKey = config!!.preSharedKey
                }
                WifiApUtil.setWifiApStatus(config,false)
                ATMsgEncode.sendSetWifiRsp(mqtt, msgApi.token, "0")
                Thread.sleep(1000)
                WifiApUtil.setWifiApStatus(config,true)
                SharedPreferencesUtils.putInt(ServiceManager.appContext,WIFIAP_STATUS,1)
            }
            uc_msg_id_e.M2AT_GET_WIFISSID_SEND->{//查询WiFi SSID
                JLog.logd("M2AT_GET_WIFISSID_SEND req")
                val config = WifiApUtil.getWifiConfig()
                if(config!=null) {
                    val ssid = config.SSID
                    JLog.logd("ssid $ssid")
                    ATMsgEncode.sendGetWifiSsidRsp(mqtt, msgApi.token, ssid, "0")
                }else{
                    ATMsgEncode.sendGetWifiSsidRsp(mqtt, msgApi.token, "","1")
                }
            }

            uc_msg_id_e.M2AT_GET_WIFIPWD_SEND->{//查询WiFi pwd
                JLog.logd("M2AT_GET_WIFIPWD_SEND req")
                val config = WifiApUtil.getWifiConfig()
                if(config!=null) {
                    val pwd = config.preSharedKey
                    JLog.logd("pwd $pwd")
                    ATMsgEncode.sendGetWifiPwdRsp(mqtt, msgApi.token, pwd,"0")
                }else{
                    ATMsgEncode.sendGetWifiPwdRsp(mqtt, msgApi.token, "","1")
                }
            }
            uc_msg_id_e.M2AT_SET_APN_SEND ->{//设置当前云卡APN（不要）

            }
            uc_msg_id_e.M2AT_GET_ROMEKY_SEND ->{//查询当前漫游开关(ok)
                JLog.logd("查询当前漫游开关")
                val roam = Configuration.PHY_ROAM_ENABLE
                val roamType: Int
                if(roam){
                    roamType = 1
                }else{
                    roamType = 0
                }
                ATMsgEncode.sendGetRoamKeySendRsp(mqtt, msgApi.token, roamType,"0")
            }
            uc_msg_id_e.M2AT_SET_ROMEKY_SEND ->{//设置漫游开关(ok)
                JLog.logd("设置漫游开关")
                val roam = msgApi.at_set_roamkey_send.roam_staus.value
                if(roam == 1){
                    Configuration.PHY_ROAM_ENABLE = true
                }else if(roam == 0){
                    Configuration.PHY_ROAM_ENABLE = false
                }
                ATMsgEncode.sendSetRoamTypeRsp(mqtt, msgApi.token,"0")
            }
            uc_msg_id_e.M2AT_RESTORE_SEND ->{//恢复出厂设置（ok）
                val intent = Intent("android.intent.action.MASTER_CLEAR")
                ServiceManager.appContext.sendBroadcast(intent)
                ATMsgEncode.sendRestoreRsp(mqtt, msgApi.token,"0")
            }
            uc_msg_id_e.M2AT_LOGMODE_SEND ->{//设置上传LOG及IP
                val logtype = msgApi.at_logmode_send.log_type
                val logip = msgApi.at_logmode_send.ip
                var file_type = Log_opt_file_type.LOG_TYPE_UC

                var upload_info = log_opt_upload_info.Builder().server_addr(logip).build()
                when(logtype){
                    Log_opt_file_type.LOG_TYPE_UC.value -> file_type = Log_opt_file_type.LOG_TYPE_UC
                    Log_opt_file_type.LOG_TYPE_RADIO.value -> file_type = Log_opt_file_type.LOG_TYPE_RADIO
                    Log_opt_file_type.LOG_TYPE_QXDM.value -> file_type = Log_opt_file_type.LOG_TYPE_QXDM
                    Log_opt_file_type.LOG_TYPE_FOTA.value -> file_type = Log_opt_file_type.LOG_TYPE_FOTA
                    Log_opt_file_type.LOG_TYPE_COMMON.value -> file_type = Log_opt_file_type.LOG_TYPE_COMMON
                }
                var param = file_opt_config_param.Builder().build()
                var control = Log_opt_control_type.LOG_CONTROL_UPLOAD_ONLY
                val s2c = S2c_upload_log_file.Builder().file_type(file_type).upload_info(upload_info).config_param(param).
                        channel_opt(Log_opt_channel_type.LOG_OPT_CHANNEL_HOST).
                        control(control)
                        .file_board(Log_opt_file_board_type.LOG_OPT_LOCAL_FILE).protocol(Log_opt_protocol_type.LOG_OPT_PROTOCOL_TYPE_FTP).build()
                LogUploadCommand(s2c).Invoke()
                ATMsgEncode.sendSetLogModeRsp(mqtt, msgApi.token,"0")
            }
            uc_msg_id_e.M2AT_GET_WIFICLIENT_SEND ->{//查询wifi热点接入人数(ok)
                val hotspotNum = printHotCount()
                ATMsgEncode.sendGetWifiClientRsp(mqtt, msgApi.token,hotspotNum,"0")
            }
            uc_msg_id_e.M2AT_GET_WIFINAME_SEND ->{//查询wifi热点接入客户端名称(ok)
                val index = msgApi.at_get_wifiname_send.cli_index
                val name = getDevideName(index)
                if(!name.isEmpty()){
                    ATMsgEncode.sendGetWifiNameRsp(mqtt, msgApi.token,name,"0")
                }else{
                    ATMsgEncode.sendGetWifiNameRsp(mqtt, msgApi.token,name,"1")
                }
            }
            uc_msg_id_e.M2AT_GET_WIFIIP_SEND ->{//查询wifi热点接入客户端IP(ok)
                val index = msgApi.at_get_wifiip_send.cli_index
                val ipList = printHotIp()
                var ip = ""
                if(index<ipList.size){
                    ip = ipList.get(index)
                    ATMsgEncode.sendGetWifiIpRsp(mqtt, msgApi.token,ip,"0")
                }else{
                    ATMsgEncode.sendGetWifiIpRsp(mqtt, msgApi.token,ip,"1")
                }
            }
            uc_msg_id_e.M2AT_SET_WIFIAP_SEND ->{//打开关闭热点(ok)
                JLog.logd("打开关闭热点")
                val ap = msgApi.at_set_wifiap_send.wifiap_enable
                if (ap == 0) {
                    WifiApUtil.setWifiApStatus(null,false)
                    SharedPreferencesUtils.putInt(ServiceManager.appContext,WIFIAP_STATUS,0)
                }else if(ap == 1){
                    WifiApUtil.setWifiApStatus(null,true)
                    SharedPreferencesUtils.putInt(ServiceManager.appContext,WIFIAP_STATUS,1)
                }
                ATMsgEncode.sendSetWifiApRsp(mqtt, msgApi.token,"0")
            }
            uc_msg_id_e.M2AT_GET_UCFF_SEND ->{//查询流量防护开关(ok)
                JLog.logd("M2AT_GET_UCFF_SEND req")
                var mode = 0
                if (CloudFlowProtectionMgr.isFlowfilterEnabled()){
                    mode = 1
                }
                ATMsgEncode.sendGetFlowCtlRsp(mqtt, msgApi.token, mode,"0")
            }
            uc_msg_id_e.M2AT_SET_UCFF_SEND ->{//设置流量防护开关(ok)
                JLog.logd("设置流量防护开关")
                val  mode = msgApi.at_set_ucff_send.ucff_mode
                FlowBandWidthControl.getInstance().cloudFlowProtectionMgr.webEnableFlowProtection = (mode== 1)
                CloudFlowProtectionMgr.handlerWebEnableFlowProtection()
                ATMsgEncode.sendSetUcffRsp(mqtt, msgApi.token, "0")

            }
            uc_msg_id_e.M2AT_GET_LOGINMODE_SEND ->{//查询GLOCALME业务登录模式（ok）
                val mode = Configuration.LOGIN_TYPE
                ATMsgEncode.sendGetLoginModeRsp(mqtt, msgApi.token, mode,"0")
            }
            uc_msg_id_e.M2AT_SET_LOGINMODE_SEND ->{//设置GLOCALME业务登录模式(ok)
                val mode = msgApi.at_set_loginmode_send.loginmode
                Configuration.LOGIN_TYPE = mode
                ATMsgEncode.sendSetLoginModeRsp(mqtt, msgApi.token, "0")
            }
            uc_msg_id_e.M2AT_GET_LOGINIP_SEND ->{//查询GLOCALME业务接入IP地址
                val ass_ip = RouteTableManager.getLocalAssIPList(ServerRouter.current_mode) as ArrayList<String?>
                ATMsgEncode.sendGetLoginIpRsp(mqtt, msgApi.token, ass_ip,"0")
            }
            uc_msg_id_e.M2AT_SET_LOGINIP_SEND ->{//设置GLOCALME业务接入IP地址
                val ip = msgApi.at_set_loginip_send.loginip
                val n = msgApi.at_set_loginip_send.loginip_index
                val result = RouteTableManager.setLocalAssIPList(ServerRouter.current_mode,ip,n)
                ATMsgEncode.sendSetLoginIpRsp(mqtt, msgApi.token, result.toString())

            }
            uc_msg_id_e.M2AT_GET_ACCOUNT_SEND ->{//查询GLOCALME业务账号用户名和密码(ok)
                JLog.logd("M2AT_GET_ACCOUNT_SEND req")
                var temp_file:File = file
                when(Configuration.LOGIN_TYPE){
                    0 -> temp_file = file
                    1-> temp_file = file2
                }
                try {
                    //从productNV分区读取用户信息
                    val inn = ObjectInputStream(FileInputStream(temp_file))
                    inforead = inn.readObject() as LoginInfo
                    Configuration.username = inforead.username
                    inn.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    inforead = LoginInfo("", "")
                    JLog.logd("读取用户信息失败 ：$e")
                }
                ATMsgEncode.sendGetAccentRsp(mqtt, msgApi.token, inforead.username, inforead.passwd,"0")
            }
            uc_msg_id_e.M2AT_SET_ACCOUNT_SEND ->{//设置GLOCALME业务账号用户名和密码（ok）
                val name = msgApi.at_set_account_send.username
                val psd = msgApi.at_set_account_send.passwd
                when(Configuration.LOGIN_TYPE){
                    0 -> return
                    1 -> {
                        if (!file2.exists()) {
                            file2.createNewFile()
                        }
                        try {
                            val out = ObjectOutputStream(FileOutputStream(file2))
                            inforead.username = name
                            inforead.passwd = psd
                            out.writeObject(inforead)
                            out.close()
                            JLog.logk("save success")
                        } catch (e: IOException) {
                            e.printStackTrace()
                            JLog.logk("save fail :" + e)
                        }
                    }
                }
            }
            uc_msg_id_e.M2AT_GET_PKG_SEND ->{//查询当前GLOCALME业务套餐模式及名称(ok)
                JLog.logd("receive m2at_get_pkg_send")
                Requestor.getUserAccountDisplay(android.os.SystemProperties.get("ucloud.oem.conf.language"), 30).subscribe({
                    if (it is user_account_display_resp_type) {
                        JLog.logd("get user account display  " + it)
                        if (it.errorCode == 100) {
                            val amount = it.amount.toString()
                            val rate = it.rate.toString()
                            val mcc = it.country_name
                            if(it.user_account_combo != null && it.user_account_combo.size > 0) {
                                val pakagename = it.user_account_combo.get(0).name
                                val intflow = it.user_account_combo.get(0).intflowbyte.toString()
                                val surplusflow = it.user_account_combo.get(0).surplusflowbyte.toString()
                                val activetime = it.user_account_combo.get(0).start_time.toString()
                                val expiretime = it.user_account_combo.get(0).end_time.toString()
                                ATMsgEncode.sendGetPkgRsp(mqtt, msgApi.token, amount,rate,mcc,pakagename,intflow,surplusflow,activetime,expiretime,"0")
                            }else{
                                ATMsgEncode.sendGetPkgRsp(mqtt, msgApi.token, amount,rate,mcc,"","","","","","0")
                            }
                        }
                    } else {
                        JLog.loge("get user account display fail")
                        ATMsgEncode.sendGetPkgRsp(mqtt, msgApi.token, "","","","","","","","","1")
                    }
                }, { t ->
                    ATMsgEncode.sendGetPkgRsp(mqtt, msgApi.token, "","","","","","","","","1")
                    t.printStackTrace()
                    JLog.loge("get user account display fail:" + t)

                })
            }
            uc_msg_id_e.M2AT_SWITCHVSIM_SEND ->{//VSIM一键换卡
                JLog.logd("M2AT_SWITCHVSIM_SEND req")
                ServiceManager.accessEntry.switchVsimReq(1)
                ATMsgEncode.sendSwitchCardRsp(mqtt, msgApi.token,"0")
            }
            uc_msg_id_e.M2AT_RELOGIN_SEND ->{//重登录GLOCALME业务(ok)
                JLog.logd("M2AT_RELOGIN_SEND req")
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_RELOGIN)
                ATMsgEncode.sendReloginRsp(mqtt, msgApi.token,"0")
            }
            uc_msg_id_e.M2AT_LOGOUT_SEND ->{//退出GLOCALME业务(ok)
                JLog.logd("M2AT_LOGOUT_SEND req")
                ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_S2CCMD_LOGOUT, TERMINAL_DO_QUIT, 0, TERMINAL_NOTICE_QUIT)
                ATMsgEncode.sendLogoutRsp(mqtt, msgApi.token,"0")
            }
           /* 限速方案待定，暂时屏蔽*/
            uc_msg_id_e.M2AT_GET_QOS_SEND ->{//查询QOS限速
                val ifName = msgApi.at_get_qos_send.network
                val ret0 = ATUtils.getQosState(ifName)
                JLog.logd("ret0 =$ret0")
                var ret1:Array<String>
                when(ret0) {//网卡被限速
                    1-> {
                        ret1 = ATUtils.getIfaceSpeed(ifName)
                        if (ret1 != null) {
                            JLog.logd("ret1[0] == $ret1[0]")
                            JLog.logd("ret.length == ${ret1.size}")
                            JLog.logd("ret1[1] == $ret1[1]")
                            val convert = ret1[0].split(" ")
                            ATMsgEncode.sendGetQosRsp(mqtt, msgApi.token,convert.get(0).toInt(), convert.get(1).toInt(), "0")
                        } else {
                            ATMsgEncode.sendGetQosRsp(mqtt, msgApi.token, 0, 0, "1")
                        }
                    }
                    0->{//没有被限速
                        ATMsgEncode.sendGetQosRsp(mqtt, msgApi.token, 0, 0, "0")
                    }
                    -1->{//查询失败
                        ATMsgEncode.sendGetQosRsp(mqtt, msgApi.token, 0, 0, "1")
                    }
                }
            }

            uc_msg_id_e.M2AT_SET_QOS_SEND ->{//设置QOS限速
                val ifName = msgApi.at_set_qos_send.network
                val rxKbps = msgApi.at_set_qos_send.wifi_usb_speed
                val txKbps = msgApi.at_set_qos_send.vsim_speed
                val ret =ATUtils.setQosState(ifName,rxKbps, txKbps)
                JLog.logd("ret =$ret")
                if(ret==0){
                    ATMsgEncode.sendSetQosRsp(mqtt, msgApi.token,"0")
                }else{
                    ATMsgEncode.sendSetQosRsp(mqtt, msgApi.token,"1")
                }

            }

            uc_msg_id_e.M2AT_GET_AGPS_SEND ->{//查询软GPS功能

            }
            uc_msg_id_e.M2AT_SET_AGPS_SEND ->{//设置软GPS功能

            }
            uc_msg_id_e.M2AT_GET_BGPS_SEND ->{//查询硬GPS功能

            }
            uc_msg_id_e.M2AT_SET_BGPS_SEND ->{//设置硬GPS功能

            }
            uc_msg_id_e.M2AT_GET_BGPSINFO_SEND ->{//查询当前GPS位置信息

            }
            uc_msg_id_e.M2AT_GET_RUNSTEP_SEND ->{//查询GLOCALME当前启动状态(ok)
                JLog.logd("M2AT_GET_RUNSTEP_SEND req")
                ServiceManager.accessEntry.statePersentOb.subscribe { persent ->
                    ATMsgEncode.sendGetRunStepRsp(mqtt, msgApi.token, persent,"0")
                }
            }
            uc_msg_id_e.M2AT_GET_VSIMUP_SEND ->{//查询云卡是否开机自启动(不要)
//                JLog.logd("M2AT_GET_VSIMUP_SEND req")
//                var upEnable = 1
//                if (Configuration.AUTO_WHEN_REBOOT){
//                    upEnable = 0
//                }
//                ATMsgEncode.sendGetVsimUpRsp(mqtt, msgApi.token, upEnable,"0")
            }
            uc_msg_id_e.M2AT_SET_VSIMUP_SEND ->{//设置云卡是否开机自启动（不要）
//                JLog.logd("M2AT_SET_VSIMUP_SEND req")
//                if (msgApi.at_set_vsimup_send != null){
//                    JLog.logd("M2AT_SET_VSIMUP_SEND req up_enable:"+msgApi.at_set_vsimup_send.up_enable)
//                    if (msgApi.at_set_vsimup_send.up_enable == 0){
//                        Configuration.AUTO_WHEN_REBOOT = true
//                    }else if (msgApi.at_set_vsimup_send.up_enable == 1){
//                        Configuration.AUTO_WHEN_REBOOT = false
//                    }
//                    ATMsgEncode.sendSetVsimUpRsp(mqtt, msgApi.token,"0")
//                }
            }
            uc_msg_id_e.M2AT_GET_APN_SEND ->{//查询当前APN（不要）
//                //TODO
//                if(ServiceManager.cloudSimEnabler.isCardOn()) {
//                    val apn = ServiceManager.cloudSimEnabler.getCard().apn
//                    ATMsgEncode.sendGetApnRsp(mqtt, msgApi.token,"0")
//                }
            }
            else -> {
                MsgDecode.recvMsgOb.onNext(msgApi)
            }
        }
    }

    //获取连接当前热点客户端的IP
    private fun getConnectedHotIP(): ArrayList<String> {
        val connectedIP = ArrayList<String>()
        try {
            val br = BufferedReader(FileReader(
                    "/proc/net/arp"))
            val line: String = br.readLine()
            while (line != null) {
                val splitted = line.split(" +".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (splitted != null && splitted.size >= 4) {
                    val ip = splitted[0]
                    connectedIP.add(ip)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return connectedIP
    }

    //输出链接到当前设备的IP地址
    fun printHotIp():ArrayList<String> {

        val connectedIP = getConnectedHotIP()
        return connectedIP
    }

    //输出链接到当前设备的客户端数目
    fun printHotCount():Int{
        val connectedIP = getConnectedHotIP()
        return connectedIP.size
    }

    //获取连接当前设备的客户端名称
    fun getDevideName(index:Int):String{
        val connectedName = ArrayList<String>()
        try {
            val br = BufferedReader(FileReader(
                    "/data/misc/dhcp/dnsmasq.leases"))
            var line: String = br.readLine()
            while (line != null) {
                val splitted = line.split(" +".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (splitted != null && splitted.size >= 4) {
                    val name = splitted[3]
                    connectedName.add(name)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if(index<connectedName.size){
            return connectedName.get(index)
        } else{
            return ""
        }

    }
}