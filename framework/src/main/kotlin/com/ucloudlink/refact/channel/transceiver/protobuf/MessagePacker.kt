package com.ucloudlink.refact.channel.transceiver.protobuf

import android.text.TextUtils
import com.ucloudlink.framework.protocol.protobuf.*
import com.ucloudlink.refact.ServiceManager
import com.ucloudlink.refact.access.ErrorCode
import com.ucloudlink.refact.business.flow.SoftsimFlowStateInfo
import com.ucloudlink.refact.business.netcheck.NetworkManager
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo
import com.ucloudlink.refact.business.performancelog.logs.PerfLogSsimLogin
import com.ucloudlink.refact.business.routetable.ServerRouter.Dest
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimBinInfoSingleReq
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo
import com.ucloudlink.refact.channel.apdu.ApduData
import com.ucloudlink.refact.config.Configuration
import com.ucloudlink.refact.utils.JLog.logd
import com.ucloudlink.refact.utils.JLog.loge
import okio.ByteString
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by chentao on 2016/8/29.
 */
object MessagePacker {
    const val VSIM_BIN_FD = 2
    const val deviceType = "P2"
    //const val deviceType = "E1"
    internal var serialGenerator = AtomicInteger(0)
    var protoPacketUtil = ProtoPacketUtil.getInstance()
    fun createLoginPacket(userName: String?,
                          password: String?,
                          loginType: Int,
                          imei: String,
                          cellId: Int,
                          lac: Int,
                          seedImsi: String,
                          iccid: String,
                          softVersion: String,
                          reverse: String,
                          logId: Int,
                          flowSize: Int,
                          sidList: ArrayList<CharSequence>,
                          loginReason: Int): Message {
        protoPacketUtil.setSession(null)
        //TODO : 开机时间暂定为当前时间，
        var plmn = "00000"
        if (sidList.size > 0) {
            val sid0 = sidList.get(0).split(",")
            if (sid0.size > 0 && sid0.get(0).length >= 5) {
                plmn = sid0.get(0)
            }
            logd("createLoginPacket sidList.get(0) =${sidList.get(0)} plmn =$plmn")
        }

        val req = LoginReq(loginType, userName, password, imei.toLong(), seedImsi.toLong(), iccid, ServiceManager.sysModel.getDeviceName(), softVersion, plmn, lac, cellId, System.currentTimeMillis().toInt(), "biaozhibwei", 1, loginReason);
        val sn = serialGenerator.incrementAndGet().toShort()
        val packet = protoPacketUtil.createLoginPacket(req, sn);
 	PerfLogSsimLogin.setLoginSN(sn.toInt())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    fun createDispatchVsimPacket(): Message {
        var lac = NetworkManager.loginNetInfo.lac
        if(lac <=  1){
            val seedLac = OperatorNetworkInfo.lac
            val cloudsimLac = OperatorNetworkInfo.lacCloudSim
            if (seedLac>1){
                lac = seedLac
            }else if (cloudsimLac >1){
                lac = cloudsimLac
            }
        }
        val cellid = NetworkManager.loginNetInfo.cellid

        val req = DispatchVsimReq(OperatorNetworkInfo.seedPlmnList, lac, cellid, 113.46, 22.27) //todo 经纬暂用深圳的
        logd("createDispatchVsimPacket NetworkManager.plmnList: ${NetworkManager.plmnList}")
        logd("createDispatchVsimPacket OperatorNetworkInfo.seedPlmnList: ${OperatorNetworkInfo.seedPlmnList}")
        logd("createDispatchVsimPacket req: $req")
        val sn = serialGenerator.incrementAndGet().toShort()
        val packet = protoPacketUtil.createDispatchVsimPacket(req, sn);
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    fun createVsimInfoPacket(): Message {
        val req = GetVsimInfoReq(VSIM_BIN_FD)
        logd("createDispatchVsimPacket req: $req")
        val sn = serialGenerator.incrementAndGet().toShort()
        val packet = protoPacketUtil.createVsimInfoPacket(req, sn);
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    fun createDownloadCardPacket(): Message {
        // val packet = protoPacketUtil.createDownloadPacket(VSIM_BIN_FD)
        //val req = GetVsimInfoReq(VSIM_BIN_FD)
        val req = GetBinFileReq(VSIM_BIN_FD)
        logd("createDownloadCardPacket req: $req")
        val sn = serialGenerator.incrementAndGet().toShort()
        val packet = protoPacketUtil.createDownloadPacket(req, sn);
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    fun createApduReqPacket(apduReq: ApduData): Message {
        logd("createApduReqPacket....")
        val imsi = apduReq.imsi
        val apduData = apduReq.apduData
        val fieldId: ByteArray? = apduReq.fieldId
        val sn = serialGenerator.incrementAndGet()
        logd("createApduReqPacket  imsi:$imsi")
        logd("createApduReqPacket  apduData:$apduData")
        logd("createApduReqPacket  fieldId:$fieldId")
        logd("createApduReqPacket  sn:$sn")
        //val vsimPacket = newvsimpacket.newBuilder().setImsi(imsi).setSerial(sn).setData(ByteBuffer.wrap(apduData)).setFileID(ByteBuffer.wrap(fieldId)).setSeedflowsiz(1000).build()
        //val req = ApduAuth(imsi.toLong(), ByteString.of(apduData,0,apduData.size), sn,ByteString.of(fieldId!!,0,fieldId?.size), 1000)
        val req = ApduAuth(imsi.toLong(), ByteString.of(apduData, 0, apduData.size), sn, null, 1000)
        logd("createApduReqPacket req: $req")
        val packet = protoPacketUtil.createApduPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    fun createReconnectPacket(): Message {
        val sn = serialGenerator.incrementAndGet()
        val req = Upload_SessionId_Req()
        logd("createReconnectPacket req: $req")
        val packet = protoPacketUtil.createUploadSessionId(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun createHeartBeatPacket(priority: Priority = Priority.TRY_USER_CHANNEL): Message {
        val sn = serialGenerator.incrementAndGet()
        val req = HeartBeat()
        logd("createHeartBeatPacket req: $req")
        val packet = protoPacketUtil.createHeartBeat(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = priority,
                payload = packet)
        return msg
    }

    //todo 暂未完成，不能使用
    fun createSwitchCloudSimPacket(cause: Int, subReason: Int,reserve: CharSequence, plmnList: List<PlmnInfo>): Message {
        val lac = NetworkManager.loginNetInfo.lac
        val cellid = NetworkManager.loginNetInfo.cellid
        val req = SwitchVsimReq(cause, subReason,plmnList,lac,cellid, 113.46, 22.27) //todo 经纬暂用深圳的
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createSwitchCloudSimPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg
    }

    // NewFlowlogDTO2 varo
    fun createUploadFlowPacket(req: UploadFlowsizeReq): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createUploadFlowPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }
    /*
       // NewFlowlogDTO4 protobuf
       fun createUploadFlowPacket4(flowParam: NewFlowlogDTO4): Message {
           val sn = serialGenerator.incrementAndGet()
           val imei = NetworkManager.loginNetInfo.imei
           val lac = NetworkManager.loginNetInfo.lac
           val cellid = NetworkManager.loginNetInfo.cellid
           val longitude = 113.46  //todo 经纬暂用深圳的
           val latitude = 22.27
           val plmn = NetworkManager.loginNetInfo.sidList.get(0).substring(0,5)
           //todo 参数不够 种子卡只有seedflowSize，不区分上下行，新协议区分上下行流量
           val req = UploadFlowsizeReq(flowParam.logid, imei.toLong(), flowParam.flowSizeup, flowParam.flowSizedown,flowParam.businessSizeup,
                                       flowParam.businessSizedown, flowParam.seedflowSize,8,plmn,lac, cellid,longitude,latitude)
           val packet = protoPacketUtil.createUploadFlowPacket4(req ,sn.toShort())
           val msg = Message(id = packet.sn,
                   dest = Dest.ASS,
                   priority = Priority.ALWAYS_USER_CHANNEL,
                   payload = packet)
           return msg

           *//*val packet = protoPacketUtil.createUploadFlowPacket4(flowParam4)
        val msg = Message(id = packet.serial,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg*//*
    }

    */
    /**
     * 创建流量补报包
     */
    /*
        fun createFlowSupplementaryPacket(flows: flowsupplementaryforbusiness2): Message {
            val sn = serialGenerator.incrementAndGet()
            val plmn = NetworkManager.loginNetInfo.sidList.get(0).substring(0,5)
            val lac = NetworkManager.loginNetInfo.lac
            val cellid = NetworkManager.loginNetInfo.cellid
            val longitude = 113.46  //todo 经纬暂用深圳的
            val latitude = 22.27
            val supplemenUf = SupplemenUf(flows.sessionid.toString(), flows.usercode.toString(), flows.logid, flows.imsi.toString().toLong(),
                                          flows.starttime, flows.endtime,flows.up_flow_vsim, flows.down_flow_vsim, flows.up_business_flow_vsim,
                                          flows.down_business_flow_vsim,plmn , lac, cellid, longitude, latitude )
            val supplemenUfList: ArrayList<SupplemenUf> = ArrayList()
            supplemenUfList.add(supplemenUf)
            val req = SupplemenUploadFlowsize(flows.imsi.toString().toLong(),supplemenUfList)
            val packet = protoPacketUtil.createFlowSupplementaryPacket(req ,sn.toShort())
            val msg = Message(id = packet.sn,
                    dest = Dest.ASS,
                    priority = Priority.ALWAYS_USER_CHANNEL,
                    payload = packet)
            return msg
           *//* val packet = protoPacketUtil.createFlowSupplementaryPacket(flowsupplementary2)
        val msg = Message(id = packet.serial,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg*//*
    }*/

    fun createLogoutPacket(priority: Priority = Priority.TRY_USER_CHANNEL): Message {

        val sn = serialGenerator.incrementAndGet()
        val req = LogoutReq(10002) //todo 构造方法参数不确定
        val packet = protoPacketUtil.createLogoutPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = priority,
                payload = packet)
        return msg
        /*
         val packet = protoPacketUtil.createLogoutPacket()
         val msg = Message(id = packet.serial,
                 dest = Dest.ASS,
                 priority = Priority.TRY_USER_CHANNEL,
                 payload = packet)
         return msg*/
    }

    fun createConfigDataPacket(getConfPara: String): Message {

        //todo 以下创建包需要更正
        val sn = serialGenerator.incrementAndGet()
        val req = LogoutReq(10002) //todo 构造方法参数不确定
        val packet = protoPacketUtil.createLogoutPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

    fun createVersionCheckPacket(checkPara: String): Message {

        //todo 以下创建包需要更正
        val sn = serialGenerator.incrementAndGet()
        val req = System_config_data_req("", "", "", "", "") //todo 构造方法参数不确定
        val packet = protoPacketUtil.createSystemConfigDataReqPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_SEED_CHANNEL,
                payload = packet)
        return msg
    }

//    fun createDispatchSoftsimPacket(username: String, order: String): Message{
//        var orders = java.util.ArrayList<String>()
//        orders.add(order)
//        val sn = serialGenerator.incrementAndGet()
//        val req = DispatchSoftsimReq(username, orders)
//        val packet = protoPacketUtil.createDispatchSoftsimReqPacket(req, sn.toShort());
//        val msg = Message(id = packet.sn,
//                dest = Dest.ASS,
//                priority = Priority.TRY_USER_CHANNEL,
//                payload = packet)
//        return msg
//    }

    fun createGetSoftsimInfoPacket(imsis: ArrayList<String>): Message {
        var param = java.util.ArrayList<Long>()
        for (imsi in imsis) {
            param.add(imsi.toLong())
        }
        val sn = serialGenerator.incrementAndGet()
        val req = GetSoftsimInfoReq(param)
        val packet = protoPacketUtil.createGetSoftsimInfoReqPacket(req, sn.toShort(), ServiceManager.accessEntry.accessState.sessionId);
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun createGetSoftsimBinReqPacket(refList: ArrayList<SoftsimBinInfoSingleReq>): Message {
        var lists = ArrayList<SoftsimBinReqInfo>()
        for (t in refList) {
            var type: SoftsimBinType
            if (t.type == 1) {
                type = SoftsimBinType.PLMN_LIST_BIN
            } else if (t.type == 2) {
                type = SoftsimBinType.FEE_BIN
            } else if (t.type == 3) {
                type = SoftsimBinType.FPLMN_BIN
            } else {
                type = SoftsimBinType.PLMN_LIST_BIN // todo:
            }
            lists.add(SoftsimBinReqInfo(type, t.ref))
        }
        val sn = serialGenerator.incrementAndGet()
        val req = GetSoftsimBinReq(lists)
        val packet = protoPacketUtil.createGetSoftsimBinReqPacket(req, sn.toShort(), ServiceManager.accessEntry.accessState.sessionId)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    fun createUpdateSoftsimReqPacket(username: String, mcc: String, mnc: String, softsims: ArrayList<SoftsimLocalInfo>, usingImsi: String): Message {
        val sn = serialGenerator.incrementAndGet()
        var lists = java.util.ArrayList<SoftsimStatus>()
        for (t in softsims) {
            var unuseList = ArrayList<SoftsimDetailUnusable>()
            for (u in t.localUnuseReason) {
                var unuse = SoftsimDetailUnusable(u.mcc, u.mnc, u.errcode, u.subErr)
                unuseList.add(unuse)
            }
            var info = SoftsimStatus(t.imsi.toLong(), t.timeStamp, usingImsi == t.imsi, unuseList)
            lists.add(info)
        }

        val req = UpdateSoftsimStatusReq(username, mcc, mnc, Configuration.getImei(ServiceManager.appContext).toLong(), lists)
        val sessionid = ServiceManager.accessEntry.accessState.sessionId
        val packet = protoPacketUtil.createUpdateSoftsimStatusReqPacket(req, sn.toShort(), sessionid)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    //请求软卡规则文件
    fun createSoftSimRuleListPacket(imei: String): Message {
        val sn = serialGenerator.incrementAndGet()
        val req = ExtSoftsimRuleReq(imei)
        val sessionid = ServiceManager.accessEntry.accessState.sessionId
        val packet = protoPacketUtil.createSoftsimRuleReqPacket(req, sn.toShort(), sessionid)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    fun createGpsUploadCfgPacket(hardGps: Boolean, networkGps: Boolean): Message {
        val sn = serialGenerator.incrementAndGet()
        val hard = kotlin.run {
            if (hardGps) return@run Gps_switch_state.SWITCH_ON
            else return@run Gps_switch_state.SWITCH_OFF
        }
        val network = kotlin.run {
            if (networkGps) return@run Gps_switch_state.SWITCH_ON
            else return@run Gps_switch_state.SWITCH_OFF
        }
        val req = Upload_gps_cfg(hard, network)
        val sessionid = ServiceManager.accessEntry.accessState.sessionId
        val packet = protoPacketUtil.createGpsCfgUploadPacket(req, sn.toShort(), sessionid)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    fun getSoftsimListByDispatchRsp(orders: List<DispatchSoftsimRspOrder>): ArrayList<String> {
        val softimList = ArrayList<String>()
        var find = false

        for (order in orders) {
            for (imsi in order.softsims) {
                find = false
                for (tmp in softimList) {
                    if (tmp == imsi!!.toString()) {
                        find = true
                        break
                    }
                }
                if (!find) {
                    softimList.add(imsi!!.toString())
                }
            }
        }

        return softimList
    }

    fun getStringListByLongList(`in`: List<Long>): ArrayList<String> {
        val arrayList = ArrayList<String>()
        for (t in `in`) {
            arrayList.add(t.toString())
        }
        return arrayList
    }

    fun getLongListByStringList(`in`: ArrayList<String>): ArrayList<Long> {
        val arrayList = ArrayList<Long>()
        for (t in `in`) {
            arrayList.add(java.lang.Long.decode(t))
        }
        return arrayList
    }

    fun getSoftsimInfoListFromGetInfoRsp(rsp: GetSoftsimInfoRsp): ArrayList<SoftsimLocalInfo> {
        val softsimLocalInfos = ArrayList<SoftsimLocalInfo>()

        for (tmp in rsp.softsims) {
            val info = SoftsimLocalInfo(
                    if (tmp.imsi == null) "000000000000000" else tmp.imsi.toString(),
                    if (tmp.ki == null) "" else tmp.ki,
                    if (tmp.opc == null) "" else tmp.opc,
                    if (tmp.apn == null) "" else tmp.apn,
                    if (tmp.roamEanble == null) false else tmp.roamEanble,
                    if (tmp.rat == null) 0 else tmp.rat,
                    if (tmp.iccid == null) "" else tmp.iccid,
                    if (tmp.msisdn == null) "" else tmp.msisdn,
                    if (tmp.virtualImei == null) "" else tmp.virtualImei.toString(),
                    if (tmp.plmnBinRef == null) "" else tmp.plmnBinRef,
                    if (tmp.feeBinRef == null) "" else tmp.feeBinRef,
                    if (tmp.timeStamp == null) 0 else tmp.timeStamp,
                    if (tmp.fplmnRef == null) "" else tmp.fplmnRef)
            softsimLocalInfos.add(info)
        }

        return softsimLocalInfos
    }

    fun softsimListValid(rsp: GetSoftsimInfoRsp?): Int {
        if (rsp == null) {
            return ErrorCode.SOFTSIM_DL_RSP_INVALID
        }
        for (info in rsp.softsims) {
            if (info.imsi == 0L) {
                loge("softsimListValid: imsi:" + info.imsi + " imsi is 0")
                return ErrorCode.LOCAL_INVALID_SOFT_SIM_IMSI
            }
            if (TextUtils.isEmpty(info.apn)) {// TODO: 2017/7/14  need check apn valid
                loge("softsimListValid: imsi:" + info.imsi + " apn invalid " + info.apn)
                return ErrorCode.LOCAL_INVALID_SOFT_SIM_APN
            }
            if (TextUtils.isEmpty(info.ki)) {
                loge("softsimListValid: imsi:" + info.imsi + " ki invalid " + info.ki)
                return ErrorCode.SOFTSIM_INVALID_KI
            }
            if (TextUtils.isEmpty(info.opc)) {
                loge("softsimListValid: imsi:" + info.imsi + " opc invalid " + info.opc)
                return ErrorCode.SOFTSIM_INVALID_OPC
            }
        }
        return 0
    }

    fun createSoftsimFlowUploadReqPacket(reqid: Int, flow: SoftsimFlowStateInfo): Message {
        val sn = serialGenerator.incrementAndGet()
        val req = SoftsimFlowUploadReq(reqid, flow.username, Configuration.getImei(ServiceManager.appContext).toLong(), flow.imsi, (flow.startTime / 1000).toInt(), (flow.endTime / 1000).toInt(), flow.mcc,
                flow.upFlow, flow.downFlow, flow.upSysFlow, flow.downSysFlow, flow.upUserFlow, flow.downUserFlow, if (flow.isSoftsim) SeedCardType.SOFTSIM else SeedCardType.SOFTSIM)
        val packet = protoPacketUtil.createSoftsimUploadFlowReqPacket(req, sn.toShort(), ServiceManager.accessEntry.getAccessState().getSessionId())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    fun createSupplemenUploadFlowsize(req: SupplemenUploadFlowsize): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createFlowSupplementaryPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    /**
     * 创建限速结果数据包
     * @param req 限速结果Upload_Current_Speed
     * @return
     */
    fun createUploadCurrentSpeed(req: Upload_Current_Speed): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createUploadCurrentSpeed(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    /**
     * 创建云卡lac、cellid发生变化的数据包
     * @param req upload_lac_change_type
     * @return
     */
    fun createUploadLacChangeType(req: upload_lac_change_type): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createUploadLacChangeType(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    /**
     * 创建返回给服务器的命令处理结果数据包, 0表示失败，1表示处理成功
     * @param resutl 返回给服务器的结果 0 :失败； 1：成功
     * @param sn 服务器下发的序列号
     * @return
     */
    fun createS2cCmdResp(resutl: Int, sn: Short): Message {
        val resp = S2c_cmd_resp(resutl)
        val packet = protoPacketUtil.createS2cCmdResp(resp, sn)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg;
    }

    //云卡通道与服务器建立绑定关系
    fun createCloudsimSocketOk(imsi: String, imei: String): Message {
        var req = Cloudsim_socket_ok_req(imsi, imei,null)
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.creatCloudsimSocketOkPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    //上报种子卡列表（第一次物理卡）
    fun createUpExtSoftsimList(softsimList: ArrayList<ExtSoftsimItem>, user: String, imei: String, ruleExist: Boolean, reason: Int): Message {
        var req = UploadExtSoftsimListReq(softsimList, user, imei, ruleExist, reason)
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.creatExtUploadSoftsimListPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun createSimpleLoginPacket(loginType: Int, username: String, passwd: String, devideType: String, version: String, imei: Long): Message {
        var req = SimpleLoginReq(loginType, username, passwd, devideType, version, imei)
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createSimpleLoginReqPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    //请求规则文件
    fun createSoftsimRule(imei: String): Message {
        var req = ExtSoftsimRuleReq(imei)
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createSoftsimRulePacket(req, sn.toShort());
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    //下载软卡
    fun createExtSoftsimReqPacket(name: String, imei: Long, reason: Int): Message {
        var req = DispatchExtSoftsimReq(name, imei, reason)
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createSoftsimReqPacket(req, sn.toShort());
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg

    }

    //下载软卡bin文件
    fun createExtSoftsimBinReqPacket(reqInfoList: List<SoftsimBinReqInfo>): Message {
        var req = GetSoftsimBinReq(reqInfoList)
        val sn = serialGenerator.incrementAndGet()
        val sessionid = ServiceManager.accessEntry.getAccessState().getSessionId()
        val packet = protoPacketUtil.createGetExtSoftsimBinReqPacket(req, sn.toShort(), sessionid)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    //下载软卡bin文件
    fun createExtSoftsimBinReqPacket(reqInfoList: List<SoftsimBinReqInfo>, sessionid: String): Message {
        var req = GetSoftsimBinReq(reqInfoList)
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createGetExtSoftsimBinReqPacket(req, sn.toShort(), sessionid);
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun createReclaimExtSoftsimReq(softsimList: List<ReclaimImsi>, localSimList: List<ExtSoftsimItem>, user: String, imei: String): Message {
        val packet = protoPacketUtil.createReclaimExtSoftsimReq(softsimList, localSimList, user, imei, serialGenerator.incrementAndGet().toShort())
        return Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
    }

    fun createUpateSimInfoReqPacket(imsis: Array<String>): Message {
        val packet = protoPacketUtil.createUpateSimInfoReqPacket(imsis, serialGenerator.incrementAndGet().toShort())

        return Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
    }

    fun createExtSoftsimUploadErrorReq(reason: Int, user: String, IMEI: String, list: List<softsimError>): Message {
        val packet = protoPacketUtil.createExtSoftsimUploadErrorReq(reason, user, IMEI, list, getSn())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun createExtUploadSoftsimPacket(imei: String, type: Int, list: List<ExtSoftsimUpdateItem>): Message {
        var req = ExtSoftsimUpdateReq(imei, type, list)
        val sn = serialGenerator.incrementAndGet()
        val sessionid = ServiceManager.accessEntry.accessState.sessionId
        val packet = protoPacketUtil.createUploadExtSoftsimStateReqPacket(req, sn.toShort(), sessionid)
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun createServiceListReqPacket(mcc: String): Message {
        var req = QueryUserParamReq("")
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createServiceListReqPacket(req, sn.toShort());
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    /**
     * 创建创建性能日志数据包
     */
    fun createPerformanceLogReportMessage(req: Performance_log_report): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createPerformanceLogReportPacket(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    /**创建账号显示信息请求包**/
    fun createUserAccountDisplayReqPacketMessage(langtype: String): Message {
        val sn = serialGenerator.incrementAndGet()
        val req = user_account_display_req_type(langtype)
        val packet = protoPacketUtil.creatUserAccountDisplayReqPacke(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    /**
     * 创建上报云卡socket建立数据包
     */
    fun createCloudsimSocketOKMessage(req: Cloudsim_socket_ok_req): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createCloudsimSocketOKPacket(req ,sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    /**
     * 创建频繁鉴权检测结果数据包
     */
    fun createFrequentAuthResultMessage(req: Frequent_auth_detection_result_req): Message {
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createFrequentAuthResultPacket(req ,sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }
/**
     * 创建搜网结果数据包
     */
    fun createReportSearchNetResult(plmns:List<PlmnInfo> ,reason:Int):Message{
        val sn = serialGenerator.incrementAndGet()
        val req = Report_SearchNet_Result(plmns,1,reason)
        loge("OperatorNetworkInfo.cloudPlmnList:"+OperatorNetworkInfo.cloudPlmnList)
        val packet = protoPacketUtil.createReportSearchNetResult(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    /**
     * 创建请求测速url数据包
     */
    fun createSpeedDetectionUrl(req:SpeedDetectionUrlReq):Message{
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createSpeedDetectionUrl(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.TRY_USER_CHANNEL,
                payload = packet)
        return msg
    }

    /**
     * 创建上报测速结果数据包
     */
    fun createUploadSpeedDetectionResult(req:Upload_Speed_Detection_Result):Message{
        val sn = serialGenerator.incrementAndGet()
        val packet = protoPacketUtil.createUploadSpeedDetectionResult(req, sn.toShort())
        val msg = Message(id = packet.sn,
                dest = Dest.ASS,
                priority = Priority.ALWAYS_USER_CHANNEL,
                payload = packet)
        return msg
    }

    fun getSn(): Short {
        return serialGenerator.incrementAndGet().toShort()
    }
}