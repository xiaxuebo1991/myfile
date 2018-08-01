package com.ucloudlink.refact.channel.transceiver.protobuf;


import com.ucloudlink.framework.protocol.protobuf.DispatchExtSoftsimReq;
import com.ucloudlink.framework.protocol.protobuf.ExtSoftsimRuleReq;
import com.ucloudlink.framework.protocol.protobuf.ExtSoftsimUpdateReq;
import com.ucloudlink.framework.protocol.protobuf.Frequent_auth_detection_result_req;
import com.ucloudlink.framework.protocol.protobuf.HeartBeatResp;
import com.ucloudlink.framework.protocol.protobuf.Performance_log_report;
import com.ucloudlink.framework.protocol.protobuf.QueryUserParamReq;
import com.ucloudlink.framework.protocol.protobuf.UploadExtSoftsimListReq;
import com.ucloudlink.framework.protocol.protobuf.Upload_gps_cfg;
import com.ucloudlink.framework.protocol.protobuf.user_account_display_req_type;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.protocol.protobuf.ApduAuth;
import com.ucloudlink.framework.protocol.protobuf.AppCmd;
import com.ucloudlink.framework.protocol.protobuf.AssReq;
import com.ucloudlink.framework.protocol.protobuf.AssResp;
import com.ucloudlink.framework.protocol.protobuf.Cloudsim_socket_ok_req;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimReq;
import com.ucloudlink.framework.protocol.protobuf.DispatchVsimReq;
import com.ucloudlink.framework.protocol.protobuf.ExtSoftsimItem;
import com.ucloudlink.framework.protocol.protobuf.ExtSoftsimUploadErrorReq;
import com.ucloudlink.framework.protocol.protobuf.GetBinFileReq;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimBinReq;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimInfoReq;
import com.ucloudlink.framework.protocol.protobuf.GetVsimInfoReq;
import com.ucloudlink.framework.protocol.protobuf.Get_Route_Resp;
import com.ucloudlink.framework.protocol.protobuf.HeartBeat;
import com.ucloudlink.framework.protocol.protobuf.LoginReq;
import com.ucloudlink.framework.protocol.protobuf.LogoutReq;
import com.ucloudlink.framework.protocol.protobuf.ReclaimExtSoftsimReq;
import com.ucloudlink.framework.protocol.protobuf.ReclaimImsi;
import com.ucloudlink.framework.protocol.protobuf.Report_SearchNet_Result;
import com.ucloudlink.framework.protocol.protobuf.Route_Cmd;
import com.ucloudlink.framework.protocol.protobuf.S2c_cmd_id;
import com.ucloudlink.framework.protocol.protobuf.S2c_cmd_req;
import com.ucloudlink.framework.protocol.protobuf.S2c_cmd_resp;
import com.ucloudlink.framework.protocol.protobuf.S2c_detail_msg_all;
import com.ucloudlink.framework.protocol.protobuf.SimpleLoginReq;
import com.ucloudlink.framework.protocol.protobuf.SimpleLogoutReq;
import com.ucloudlink.framework.protocol.protobuf.SoftsimFlowUploadReq;
import com.ucloudlink.framework.protocol.protobuf.SpeedDetectionUrlReq;
import com.ucloudlink.framework.protocol.protobuf.SupplemenUploadFlowsize;
import com.ucloudlink.framework.protocol.protobuf.SwitchVsimReq;
import com.ucloudlink.framework.protocol.protobuf.System_config_data_req;
import com.ucloudlink.framework.protocol.protobuf.UpdateSoftsimStatusReq;
import com.ucloudlink.framework.protocol.protobuf.UploadFlowsizeReq;
import com.ucloudlink.framework.protocol.protobuf.Upload_Current_Speed;
import com.ucloudlink.framework.protocol.protobuf.Upload_SessionId_Req;
import com.ucloudlink.framework.protocol.protobuf.Upload_Speed_Detection_Result;
import com.ucloudlink.framework.protocol.protobuf.softsimError;
import com.ucloudlink.framework.protocol.protobuf.upload_lac_change_type;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.utils.JLog;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.util.Utf8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * Created by yongbin.qin on 2016/11/4.
 */
public class ProtoPacketUtil {

    public static final int C2S_CONFIG_VERSION_UPDATE_129 = 129;    //IP配置文件，版本更新
    public static final int CONFIG_VERSION_CHECK = 1;   //版本更新检查
    public static final int SYSTEM_CONFIG_DATA = 2;     //获取系统参数
    private final static CharSequence KEY = new Utf8("session");
    private static final EncoderFactory ENCODER_FACTORY = new EncoderFactory();
    public static Short START_TAG = 0xfb;
    private static ProtoPacketUtil instance;
    SpecificData data;
    Map<String, ByteBuffer> requestCallMeta = new HashMap<String, ByteBuffer>();
    private String TAG = "ProtoPacketUtil";
    private Protocol local;

    public ProtoPacketUtil(Protocol protocol, SpecificData data) {
        this.local = protocol;
        this.data = data;
    }

    public ProtoPacketUtil(Class<?> iface, SpecificData data) {
        this(data.getProtocol(iface),data);
    }

    public ProtoPacketUtil(Class<?> iface) {
        this(iface,new SpecificData(iface.getClassLoader()));
    }

    public ProtoPacketUtil() {
        //this(UserAuthenticationBPO.class); // TODO: 2016/11/4 安全认证，暂时去除
    }

    public static ProtoPacketUtil getInstance() {
        synchronized (ProtoPacketUtil.class) {
            if (instance == null) {
                instance = new ProtoPacketUtil();
            }
        }
        return instance;
    }

    protected DatumWriter<Object> getDatumWriter(Schema schema) {
        return new SpecificDatumWriter<Object>(schema, data);
    }

    protected DatumReader<Object> getDatumReader(Schema schema) {
        return getDatumReader(schema, schema);
    }

    protected DatumReader<Object> getDatumReader(Schema writer, Schema reader) {
        return new SpecificDatumReader<Object>(writer, reader, data);
    }

    public void writeRequest(Schema schema, Object request, Encoder out)
            throws IOException {
        Object[] args = (Object[])request;
        int i = 0;
        for (Schema.Field param : schema.getFields())
            getDatumWriter(param.schema()).write(args[i++], out);
    }

    public Object readResponse(Schema writer, Schema reader, Decoder in)
            throws IOException {
        return getDatumReader(writer, reader).read(null, in);
    }

    public Exception readError(Schema writer, Schema reader, Decoder in)
            throws IOException {
        Object value = getDatumReader(writer, reader).read(null, in);
        if (value instanceof Exception)
            return (Exception)value;
        return new AvroRuntimeException(value.toString());
    }

    public Protocol getLocal() {
        return local;
    }

    public ProtoPacket  createLoginPacket(LoginReq req, short sn) {
        AssReq assReq = new AssReq.Builder().loginReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_LOGIN_REQ.getValue());
        return packet;
    }

    public ProtoPacket createDispatchVsimPacket(DispatchVsimReq req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).dispatchVsimReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_DSIPACTH_VSIM_REQ.getValue());
        return packet;
    }

    public ProtoPacket createVsimInfoPacket(GetVsimInfoReq req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).getVsimInfoReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_VISM_INFO_REQ.getValue());
        return packet;
    }

    public ProtoPacket createDownloadPacket(GetBinFileReq req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).getBinFileReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_BIN_FILE_REQ.getValue());
        return packet;
    }

    public ProtoPacket createApduPacket(ApduAuth req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).apduAuth(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_APDU_REQ.getValue());
        return packet;
    }

    //云卡通道与服务器建立绑定关系
    public ProtoPacket creatCloudsimSocketOkPacket(Cloudsim_socket_ok_req req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).cloudsimSocketOkReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq,sn);
        packet.setCmd(AppCmd.CMD_CLOUDSIM_SOCKET_OK_REQ.getValue());
        return packet;
    }

    //上报种子卡列表
    public ProtoPacket creatExtUploadSoftsimListPacket(UploadExtSoftsimListReq req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).uploadExtSoftsimListReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq,sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_UPLOAD_LIST_REQ.getValue());
        return packet;
    }

    //请求规则文件
    public ProtoPacket createSoftsimRulePacket(ExtSoftsimRuleReq req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).extSoftsimRuleReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq,sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_RULE_LIST_REQ.getValue());
        return packet;
    }

    //请求下载软卡
    public ProtoPacket createSoftsimReqPacket(DispatchExtSoftsimReq req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).extSoftsimReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq,sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_REQ.getValue());
        return packet;
    }

    //上报下载的软卡状态
    public ProtoPacket createUploadExtSoftsimStateReqPacket(ExtSoftsimUpdateReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).extSoftsimUpdateReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_UPDATE_STATUS_REQ.getValue());
        logv("createUploadExtSoftsimStateReqPacket:" + assReq.toString());
        return packet;
    }

    public ProtoPacket createExtSoftsimUploadErrorReq(Integer reason, String user, String imei, List<softsimError> list, short sn){

        ExtSoftsimUploadErrorReq req  = new ExtSoftsimUploadErrorReq(reason,user,imei,list);
        AssReq assReq = new AssReq.Builder().sessionid(getSessionId()).extSoftsimUploadErrorReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_UPLOAD_ERROR_REQ.getValue());
        return  packet;
    }

    public ProtoPacket createReclaimExtSoftsimReq(List<ReclaimImsi> softsimList, List<ExtSoftsimItem> localSimList, String user, String imei, short sn){
        String sessionid = getSessionId();
        ReclaimExtSoftsimReq reclaimExtSoftsimReq = new ReclaimExtSoftsimReq(0, softsimList,localSimList,user,imei);
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).reclaiExtSoftsimReq(reclaimExtSoftsimReq).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_RECLAIM_REQ.getValue());
        return  packet;

    }

    protected String getSessionId() {
        return ServiceManager.accessEntry.getAccessState().getSessionId();
    }

    //获取要更新的软卡的信息
    public ProtoPacket createUpateSimInfoReqPacket(String[] imsi, short sn){
        String sessionid = getSessionId();
        ArrayList<Long> list = new ArrayList<>();
        for (String s : imsi) {
            list.add(Long.valueOf(s));
        }
        logd("createUpateSimInfoReqPacket list: " + list);
        GetSoftsimInfoReq req = new GetSoftsimInfoReq(list);
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).getSoftsimInfoReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_UPDATE_IMSI_INFO_REQ.getValue());
        return  packet;
    }

    //请求软卡规则文件
    public ProtoPacket createSoftsimRuleReqPacket(ExtSoftsimRuleReq req,short sn,String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).extSoftsimRuleReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_RULE_LIST_REQ.getValue());
        return packet;
    }

    public ProtoPacket createGpsCfgUploadPacket(Upload_gps_cfg gps_cfg, short sn, String sessionId){
        AssReq assReq = new AssReq.Builder().sessionid(sessionId).UploadGpsCfg(gps_cfg).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UPLOAD_GPS_CFG.getValue());
        return packet;
    }

    public ProtoPacket createHeartBeat(HeartBeat req, short sn) {
        AssReq assReq = new AssReq.Builder().heartBeat(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_HEART_BEAT.getValue());
        logd("ProtoPacketUtil", "createHeartBeat packet : " +packet.toString());
        return packet;
    }

    public ProtoPacket createUploadSessionId(Upload_SessionId_Req req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).UploadSessionId_Req(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UPLOAD_SESSIONID_REQ.getValue());
        JLog.logi(TAG, "createUploadSessionId: " + packet.toString());
        return  packet;
    }

    public ProtoPacket createSwitchCloudSimPacket(SwitchVsimReq req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).switchVsimReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SWITCH_VSIM_REQ.getValue());
        return packet;
    }

    public ProtoPacket createUploadFlowPacket(UploadFlowsizeReq req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).uploadFlowsizeReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UF_REQ.getValue());
        return packet;
    }

    public ProtoPacket createFlowSupplementaryPacket(SupplemenUploadFlowsize req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).supplemenUploadFlowsize(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UF_SUPPLEMENT_REQ.getValue()); //todo CMD_UF_SUPPLEMENT_REQ 流量补报命令不确定
        return packet;
    }
    public ProtoPacket createLogoutPacket(LogoutReq req, short sn) {
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).logoutReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_LOGOUT_REQ.getValue());
        return packet;
    }

    public ProtoPacket createSystemConfigDataReqPacket(System_config_data_req req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).SystemConfigDataReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SYSTEM_CONFIG_DATA_REQ.getValue());
        return packet;
    }

    public ProtoPacket createUpdateSoftsimStatusReqPacket(UpdateSoftsimStatusReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).updateSoftsimStatusReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SOFTSIM_STATUS_UPDATE_REQ.getValue());
        return packet;
    }

    public ProtoPacket createDispatchSoftsimReqPacket(DispatchSoftsimReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).dispatchSoftsimReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SOFTSIM_DISPATCH_REQ.getValue());
        return packet;
    }

    public ProtoPacket createGetSoftsimInfoReqPacket(GetSoftsimInfoReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).getSoftsimInfoReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SOFTSIM_GET_SIMINFO_REQ.getValue());
        return packet;
    }

    public ProtoPacket createGetSoftsimBinReqPacket(GetSoftsimBinReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).getSoftsimBinReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SOFTSIM_GET_BIN_REQ.getValue());
        return packet;
    }

    public ProtoPacket createGetExtSoftsimBinReqPacket(GetSoftsimBinReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).getSoftsimBinReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_EXTSOFTSIM_GETBIN_REQ.getValue());
        return packet;
    }


    public ProtoPacket createSoftsimUploadFlowReqPacket(SoftsimFlowUploadReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).softsimFlowUploadReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SOFTSIM_UPLOAD_FLOW_REQ.getValue());
        return packet;
    }

    public ProtoPacket createSimpleLoginReqPacket(SimpleLoginReq req, short sn){
        AssReq assReq = new AssReq.Builder().simpleLoginReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SIMPLE_LOGIN_REQ.getValue());
        return packet;
    }

    public ProtoPacket createSimpleLogoutReqPacket(SimpleLogoutReq req, short sn, String sessionid){
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).simpleLogoutReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SIMPLE_LOGOUT_REQ.getValue());
        return packet;
    }

    /**
     * 创建返回给服务器的命令处理结果数据包
     * @param resp 回复服务器数据包
     * @param sn
     * @return
     */
    public ProtoPacket  createS2cCmdResp (S2c_cmd_resp resp, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).s2c_resp(resp).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_S2C_RESP.getValue());
        return packet;
    }

    /**
     * 创建限速结果数据包
     * @param req 限速结果Upload_Current_Speed
     * @return
     */
    public ProtoPacket createUploadCurrentSpeed(Upload_Current_Speed req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).UploadCurrentSpeed(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UPLOAD_CURRENT_SPEED.getValue());
        return packet;
    }

    /**
     * 创建云卡lac、cellid发生变化的数据包
     * @param req upload_lac_change_type
     * @return
     */
    public ProtoPacket createUploadLacChangeType(upload_lac_change_type req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).upload_lac_change(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UPLOAD_LAC_CHANGE.getValue());
        return packet;
    }

    /**
     * 创建搜网结果数据包
     *
     */
    public ProtoPacket createReportSearchNetResult(Report_SearchNet_Result req, short sn){
        String sessionid = ServiceManager.accessEntry.getAccessState().getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).ReportSearchNetResult(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_REPORT_SEARCH_NET_RESULT.getValue());
        return packet;
    }

    /**
     * 创建请求测速url数据包
     *
     */
    public ProtoPacket createSpeedDetectionUrl(SpeedDetectionUrlReq req, short sn){
        String sessionid = ServiceManager.accessEntry.getAccessState().getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).speeddetectionreq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SPEED_DETECTION_REQ.getValue());
        return packet;
    }

    /**
     * 创建测速结果数据包
     * @param req
     * @param sn
     * @return
     */
    public ProtoPacket createUploadSpeedDetectionResult(Upload_Speed_Detection_Result req, short sn){
        String sessionid = ServiceManager.accessEntry.getAccessState().getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).UploadSpeedDetectionResult(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_UPLOAD_SPEED_DETECTION_RESULT.getValue());
        return packet;
    }

public ProtoPacket createServiceListReqPacket(QueryUserParamReq queryUserParamReq, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).queryUserParamReq(queryUserParamReq).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_SERVICELIST_REQ.getValue());
        return packet;
    }

    private ProtoPacket createRequestPacket(AssReq req, short sn) {
//        JLog.logd("ProtoPacketUtil", "createRequestPacket AssReq : " +req);
        logd("package sessionid " + req.sessionid + " sn: " +sn);
        byte[] data = req.encode();
        ProtoPacket packet = new ProtoPacket();
        packet.setStartTag(START_TAG);
        packet.setSn(sn);
        packet.setData(data);
        packet.setLength(new Long(data.length).shortValue());
        return packet;
    }

    /**终端请求用户信息显示**/
    public ProtoPacket creatUserAccountDisplayReqPacke(user_account_display_req_type req, short sn){
        String sessionid = getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).user_account_display_req(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_GET_USER_ACCOUNT_DISPLAY_REQ.getValue());
        return packet;
    }

    /**
     * 创建性能日志数据包
     * @param req
     * @param sn
     * @return
     */
    public ProtoPacket createPerformanceLogReportPacket(Performance_log_report req, short sn) {
        String sessionid = ServiceManager.accessEntry.getAccessState().getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).PerformanceLogReport(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_PERFORMANCE_LOG_REPORT.getValue());
        return packet;
    }

    public ProtoPacket createCloudsimSocketOKPacket(Cloudsim_socket_ok_req req, short sn) {
        String sessionid = ServiceManager.accessEntry.getAccessState().getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).cloudsimSocketOkReq(req).build();
        logd("FrequentAuth createCloudsimSocketOKPacket: assReq="+assReq);
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_CLOUDSIM_SOCKET_OK_REQ.getValue());
        return packet;
    }

    public ProtoPacket createFrequentAuthResultPacket(Frequent_auth_detection_result_req req, short sn) {
        String sessionid = ServiceManager.accessEntry.getAccessState().getSessionId();
        AssReq assReq = new AssReq.Builder().sessionid(sessionid).freAuthDetectResultReq(req).build();
        ProtoPacket packet = createRequestPacket(assReq, sn);
        packet.setCmd(AppCmd.CMD_FREQUENT_AUTH_DETECTION_RESULT_REQ.getValue());
        return packet;
    }


    public void setSession(String session) {
        if (session == null) {
            if (requestCallMeta.get(KEY.toString()) != null) {
                System.out.println("clean session key:" + KEY.toString());
                requestCallMeta.remove(KEY.toString());
            }
            return;
        }
        requestCallMeta.put(KEY.toString(),ByteBuffer.wrap(session.getBytes(Charset.forName("UTF-8"))));
        System.out.println("debug setSession: " + new String(requestCallMeta.get(KEY.toString()).array(), Charset.forName("UTF-8")));
    }

    /**
     * 解析ProtoPacket数据包，返回protobuf定义的对象
     * @param packet 客户段的protobuf请求，或者服务器的protobuf回复
     * @return 返回protobuf定义的对象
     * @throws Exception
     */
    public Object decodeProtoPacket(ProtoPacket packet) throws Exception {

        //路由返回包解码
        if (packet.getCmd() == Route_Cmd.GET_ROUTE_RESP.getValue()){
            Get_Route_Resp get_Route_Resp = Get_Route_Resp.ADAPTER.decode(packet.getData());
            return get_Route_Resp;
        }

        //精简协议后心跳没有包体
        if (packet.getCmd() == AppCmd.CMD_HEART_BEAT_RESP.getValue()){
            logd("ProtoPacketUtil", "CMD_HEART_BEAT_RESP : " + AppCmd.CMD_HEART_BEAT_RESP.getValue());
            return new HeartBeatResp();
        }

        AssResp resp = null;
        try {
            resp = AssResp.ADAPTER.decode(packet.getData());
            //JLog.logd("ProtoPacketUtil", "resp : " + resp + " " + packet.getCmd());

            if(resp.errorcode != null){
                JLog.loge(TAG, "decodeProtoPacket: get common errcode" + resp.errorcode);
                return resp.errorcode;
            }

            if (packet.getCmd() == AppCmd.CMD_LOGIN_RESP.getValue()){
                return resp.loginResp;
            }else if (packet.getCmd() ==AppCmd.CMD_DISPACTH_VISM_RESP.getValue()){
                logd("ProtoPacketUtil", "CMD_DISPACTH_VISM_RESP : " + AppCmd.CMD_DISPACTH_VISM_RESP.getValue());
                return resp.dispatchVsimResp;
            } else if(packet.getCmd() ==AppCmd.CMD_BIN_FILE_RESP.getValue()){
                return resp.getBinFileResp;
            } else if(packet.getCmd() == AppCmd.CMD_APDU_RESP.getValue()){
                return resp.apduAuthResp;
            } else if (packet.getCmd() == AppCmd.CMD_BIN_FILE_RESP.getValue()){
                logd("ProtoPacketUtil", "CMD_BIN_FILE_RESP : " + AppCmd.CMD_BIN_FILE_RESP.getValue());
                return resp.getBinFileResp;
            } else if (packet.getCmd() == AppCmd.CMD_LOGOUT_RESP.getValue()){
                return resp.logoutResp;
            } else if (packet.getCmd() == AppCmd.CMD_VISM_INFO_RESP.getValue()){
                logd("ProtoPacketUtil", "CMD_VISM_INFO_RESP : " + AppCmd.CMD_VISM_INFO_RESP.getValue());
                return resp.getVsimInfoResp;
            } else if (packet.getCmd() == AppCmd.CMD_SWITCH_VISM_RESP.getValue()){
                return resp.switchVsimResp;
            } else if (packet.getCmd() == AppCmd.CMD_UF_RESP.getValue()){
                return resp.uploadFlowsizeResp;
            } else if (packet.getCmd() == AppCmd.CMD_SYSTEM_CONFIG_DATA_RESP.getValue()){
                return resp.SystemConfigRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SOFTSIM_STATUS_UPDATE_RESP.getValue()){
                return resp.updateSoftsimStatusRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SOFTSIM_DISPATCH_RESP.getValue()){
                return resp.dispatchSoftsimRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SOFTSIM_GET_SIMINFO_RESP.getValue()){
                return resp.getSoftsimInfoRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SOFTSIM_GET_BIN_RESP.getValue()){
                return resp.getSoftsimBinRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SOFTSIM_UPLOAD_FLOW_RESP.getValue()){
                return resp.softsimFlowUploadRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SIMPLE_LOGIN_RSP.getValue()){
                return resp.simpleLoginRsp;
            }else if (packet.getCmd() == AppCmd.CMD_SIMPLE_LOGOUT_RSP.getValue()){
                return resp.simpleLogoutRsp;
            } else if (packet.getCmd() == AppCmd.CMD_S2C_REQ.getValue()){
                return resp.s2c_req;
            } else if (packet.getCmd() == AppCmd.CMD_UF_SUPPLEMENT_RESP.getValue()){
                return resp.supplemenUploadFlowsizeResp;
            } else if(packet.getCmd() == AppCmd.CMD_UPLOAD_SESSION_RESP.getValue()){
                return resp.UploadSessionIdResp;
            } else if (packet.getCmd() == AppCmd.CMD_UPLOAD_LAC_CHANGE_RESP.getValue()){
                return resp.upload_lac_change_resp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_UPLOAD_LIST_RSP.getValue()){
                return resp.uploadExtSoftsimRsp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_RSP.getValue()){
                return resp.extSoftsimRsp;
            }else if (packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_RULE_LIST_RSP.getValue()){
                return resp.extSoftsimRuleRsp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_UPDATE_STATUS_RSP.getValue()){
                return  resp.extSoftsimUpdateRsp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_GETBIN_RSP.getValue()){
                return  resp.getSoftsimBinRsp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_UPDATE_IMSI_INFO_RSP.getValue()){
                return  resp.getSoftsimInfoRsp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_RECLAIM_RSP.getValue()){
                return  resp.reclaiExtSoftsimRsp;
            }else if(packet.getCmd() == AppCmd.CMD_EXTSOFTSIM_UPLOAD_ERROR_RSP.getValue()){
                return  resp.extSoftsimUploadErrorRsp;
            }else if(packet.getCmd() == AppCmd.CMD_CLOUDSIM_SOCKET_OK_RSP.getValue()){
                return  resp.cloudsimSocketOkRsp;
            }else if(packet.getCmd() == AppCmd.CMD_SERVICELIST_RESP.getValue()){
                return  resp.queryUserServiceListResp;
            }else if(packet.getCmd() == AppCmd.CMD_PERFORMANCE_LOG_REPORT_RESP.getValue()){
                return  resp.PerformanceLogResp;
            }
            else if(packet.getCmd() == AppCmd.CMD_GET_USER_ACCOUNT_DISPLAY_RESP.getValue()){
                return  resp.user_account_display_resp;
            }else if (packet.getCmd() == AppCmd.CMD_FREQUENT_AUTH_DETECTION_RESULT_RSP.getValue()){
                return resp.freAuthDetectResultRsp;
             } else if (packet.getCmd() == AppCmd.CMD_SPEED_DETECTION_RESP.getValue()){
                return resp.speeddetectionresp;
            } else if (packet.getCmd() == AppCmd.CMD_UPLOAD_SPEED_DETECTION_RESULT_RESP.getValue()){
                return resp.UploadSpeedResultreResp;
            } else if (packet.getCmd() == AppCmd.CMD_REPORT_SEARCH_NET_RESULT_RESP.getValue()){
                return resp.ReportSearchResultResp;
            }

        } catch (IOException e) {
            logd("ProtoPacketUtil", "packetToMessage e : " + e.toString());
            e.printStackTrace();
        }
        throw  new ProtocolException(resp.toString());
    }

    /**
     * 解析服务器命令
     * @param packet 服务器下发的ProtoPacket
     * @return 返回服务器命令
     * @throws Exception
     */
    public Object decodeServerCmd(ProtoPacket packet) {
        S2c_detail_msg_all s2c = null;
        try {
            Object result = decodeProtoPacket(packet);
            if(!(result instanceof S2c_cmd_req)){
                JLog.loge(TAG, "decodeServerCmd: result is not S2c_cmd_req"   + result);
                return null;
            }
            s2c = S2c_detail_msg_all.ADAPTER.decode(((S2c_cmd_req)result).data);
            logd("s2c", "packetToServerMessage  cmd : " +s2c.cmd_id.getValue());
            logd("s2c", "packetToServerMessage  : " +s2c.toString());
            if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_LOG_FILE_OPT.getValue()){
                return s2c.log_opt; //LOG文件操作

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_SET_VSIM_VISIT_MODE.getValue()){
                return s2c.vsim_visit_mode;  // 设置访问vsim模式

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_RESET_HOST_AND_RELOGIN .getValue()){
                return s2c.reset_relogin;  //重启主板并重新登陆

            }else if (s2c.cmd_id == S2c_cmd_id.S2C_CMD_RTT_WORK_MODE_SET){
                return s2c.rtt_mode; // RTT工作模式设置

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_UP_DOWN_SPEED .getValue()){
                return s2c.speed_limit;   // 限速

            } else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_WARING_FLOW.getValue()){
                return s2c.waring_flow;   // 流量预警
            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_QUIT_FORCE.getValue()){
                return s2c.quit_force;   // 强制退出

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_SWITCH_VSIM.getValue()){
                return s2c.switch_vsim;   // 换卡

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_SPEED_DETECTION.getValue()){
                return s2c.speed_detect;   // 启动3G速率检测

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_SEARCH_NETWORK.getValue()){
                return s2c.search_network;   // 请求终端搜集网络信息

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_REDIRECT_ROUTE.getValue()){
                return s2c.redirect_route;   // 路由重定向

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_PUSH_SPEED_URL.getValue()){
                return s2c.PushSpeedDetectionUrl;   //推送测速网址到终端

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_UpdatePlmnList.getValue()){
                return s2c.UpdatePlmnListRequest;   //开启/关闭优选3G请求

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_SYSTEM_CALL.getValue()) {
                return s2c.system_call;   //下发系统命令（ADB）

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_perf_log_cfg.getValue()){
                return s2c.perf_log_cfg;   //性能日志配置

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_gps_func_ctrl.getValue()){
                return s2c.gps_func_ctrl;   //gpsG功能设置

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_Local_opt_switch.getValue()){
                return s2c.Local_opt_switch;   //副板优化功能开关控制

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_RTU_Phone_Call.getValue()){
                return s2c.RTU_phone_call;   //请求终端打电话

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_Send_SMS.getValue()){
                return s2c.RTU_send_sms;   //请求终端发短信

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_send_remote_at.getValue()){
                return s2c.send_remote_at;   //远程AT

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_auto_switch_vsim.getValue()){
                return s2c.auto_switch_vsim;   //自动换卡

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_lac_change_report_interval.getValue()){
                return s2c.lac_change_report_interval;   //lac上报时间间隔

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_RTU_CONTROL.getValue()){
                return s2c.rtu_ctrl;   //开启/关闭RTU

            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_softsim_recycle.getValue()) {
                return s2c.softsim_recycle;   //软卡回收
            }else if(s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_ext_softsim_req.getValue()){
                return s2c.ext_softsim;//下载软卡
            }else if(s2c.cmd_id.getValue() == S2c_cmd_id.s2c_CMD_ext_softsim_update_req.getValue()){
                return s2c.update_softsim;//更新软卡
            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_FREQUENT_AUTH_DETECTION_PARAM.getValue()){
                return s2c.fre_auth_detect_param;   //频繁鉴权配置表
            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_FREQUENT_AUTH_ACTION.getValue()){
                return s2c.fre_auth_action;   //解决频繁鉴权操作
            }else if(s2c.cmd_id.getValue() == S2c_cmd_id.S2C_CMD_SWITCH_VSIM_RESULT.getValue()){
                return s2c.switch_card_result; //云卡智能优选是否有卡可换
            }else if (s2c.cmd_id.getValue() == S2c_cmd_id.S2c_CMD_weak_signal_ctrl.getValue()){
                return s2c.weak_signal_ctrl;   //云卡智能优先开启/关闭弱信号检测
            }
        }catch (IOException e) {
            logd("ProtoPacketUtil", "packetToMessage e : " + e.toString());
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
