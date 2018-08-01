package com.ucloudlink.refact.product.mifi.downloadcfg;


import java.util.ArrayList;

/**
 * Created by ouyangjunchao on 2016/11/8.
 */
public class DownCfgReqData {

//    public static String STREAMNO = "streamNo";
//    public static String PARTNERCODE = "partnerCode";
//    public static String LOGINCUSTOMERID = "loginCustomerId";
//    public static String USERCODE = "userCode";
//    public static String IMEI = "imei";
//    public static String MCC = "mcc";
//    public static String MNC = "mnc";
//    public static String FUNREQLIST = "funReqList";

    private String streamNo;
    private String partnerCode;
    private String loginCustomerId;
    private String userCode;
    private String imei;
    private String mcc;
    private String mnc;

    private ArrayList<ReqListData> funReqList;

    public DownCfgReqData() {
        streamNo = "TML" + System.currentTimeMillis() + random();
        partnerCode = "UKTML";
        loginCustomerId = "";
        userCode = "";
        funReqList = new ArrayList<>();
    }

    public DownCfgReqData(String imei, String mcc, String mnc) {
        streamNo = "TML" + System.currentTimeMillis() + random();
        partnerCode = "UKTML";
        loginCustomerId = "";
        userCode = "";
        this.imei = imei;
        this.mcc = mcc;
        this.mnc = mnc;
        funReqList = new ArrayList<>();
    }

    private static String random() {
        String strRand="" ;
        for(int i = 0; i < 6; i++){
            strRand += String.valueOf((int)(Math.random() * 10)) ;
        }
        return strRand;
    }

/*    public String getStreamNo() {
        return streamNo;
    }

    public void setStreamNo(String streamNo) {
        this.streamNo = streamNo;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }

    public String getLoginCustomerId() {
        return loginCustomerId;
    }

    public void setLoginCustomerId(String loginCustomerId) {
        this.loginCustomerId = loginCustomerId;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }*/

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public String getMnc() {
        return mnc;
    }

    public void setMnc(String mnc) {
        this.mnc = mnc;
    }

    public ArrayList<ReqListData> getFunReqList() {
        return funReqList;
    }

    public void addFunReqListDatas(ReqListData data) {
        funReqList.add(data);
    }

    @Override
    public String toString() {
        return "DownCfgReqData{" +
                "streamNo='" + streamNo + '\'' +
                ", partnerCode='" + partnerCode + '\'' +
                ", loginCustomerId='" + loginCustomerId + '\'' +
                ", userCode='" + userCode + '\'' +
                ", imei='" + imei + '\'' +
                ", mcc='" + mcc + '\'' +
                ", mnc='" + mnc + '\'' +
                ", funReqList=" + funReqList +
                '}';
    }
}
