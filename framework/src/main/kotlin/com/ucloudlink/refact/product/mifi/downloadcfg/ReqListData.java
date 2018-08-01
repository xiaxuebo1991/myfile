package com.ucloudlink.refact.product.mifi.downloadcfg;

/**
 * Created by ouyangjunchao on 2016/11/9.
 */
public class ReqListData {
    /*
        funType  BW 黑名单, MACW 白名单, ROAM 漫游列表, FOAMFEE 种子卡漫游资费
     */
    private String funType;
    private String iccid;
    private String imsi;
    private String cardBatchId;
    private String cardType;
    private String version;

    //TODO 添加iccid
    public ReqListData(String funType, String imsi, String version) {
        this.funType = funType;
//        this.iccid = iccid;
        this.imsi = imsi;
        this.version = version;
    }
}
