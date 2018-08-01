package com.ucloudlink.refact.product.mifi.flow.protection.entity;

/**
 * Created by ouyangjunchao on 2016/1/5.
 */

public class GetProCfgRequestData {

    public static final String IMEI = "imei";
    public static final String USERNAME = "username";
    public static final String MCC = "mcc";
    public static final String MNC = "mnc";
    public static final String LAC = "lac";
    public static final String CELLID = "cellId";
    public static final String BU = "bu";
    public static final String CFG_VERSION = "cfgVersion";
    public static final String SOF_TVERSION = "softVersion";

    private String imei = "";
    private String username = "";
    private String mcc = "";
    private String mnc = "";
    private String lac = "";
    private String cellId = "";
    private String bu = "";
    private String cfgVersion = "";
    private String softVersion = "";

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getLac() {
        return lac;
    }

    public void setLac(String lac) {
        this.lac = lac;
    }

    public String getCellId() {
        return cellId;
    }

    public void setCellId(String cellId) {
        this.cellId = cellId;
    }

    public String getBu() {
        return bu;
    }

    public void setBu(String bu) {
        this.bu = bu;
    }

    public String getCfgVersion() {
        return cfgVersion;
    }

    public void setCfgVersion(String cfgVersion) {
        this.cfgVersion = cfgVersion;
    }

    public String getSoftVersion() {
        return softVersion;
    }

    public void setSoftVersion(String softVersion) {
        this.softVersion = softVersion;
    }

    @Override
    public String toString() {
        return "GetProCfgRequestData{" +
                "imei='" + imei + '\'' +
                ", username='" + username + '\'' +
                ", mcc='" + mcc + '\'' +
                ", mnc='" + mnc + '\'' +
                ", lac='" + lac + '\'' +
                ", cellId='" + cellId + '\'' +
                ", bu='" + bu + '\'' +
                ", cfgVersion='" + cfgVersion + '\'' +
                ", softVersion='" + softVersion + '\'' +
                '}';
    }

}
