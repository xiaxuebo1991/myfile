package com.ucloudlink.refact.business.softsim.struct;

import java.util.ArrayList;

/**
 * Created by shiqianhua on 2016/12/6.
 */

public class SoftsimLocalInfo {
    private String imsi;
    private String ki;
    private String opc;
    private String apn;
    private boolean roam_enable;
    private int rat;
    private String iccid;
    private String msisdn;
    private String virtual_imei;
    private String plmnBin;
    private String rateBin;
    private Long timeStamp;
    private String fplmnbin;

    private boolean isLocalUsable = true;
    private ArrayList<SoftsimUnusable> localUnuseReason = new ArrayList<>();
    private boolean isServerUsable = true;

    private int pri;

    public SoftsimLocalInfo(String imsi, String ki, String opc, String apn, boolean roam_enable, int rat, String iccid, String msisdn, String virtual_imei, String plmnBin, String rateBin, Long timeStamp, String fplmnbin) {
        this.imsi = imsi;
        this.ki = ki;
        this.opc = opc;
        this.apn = apn;
        this.roam_enable = roam_enable;
        this.rat = rat;
        this.iccid = iccid;
        this.msisdn = msisdn;
        this.virtual_imei = virtual_imei;
        this.plmnBin = plmnBin;
        this.rateBin = rateBin;
        this.timeStamp = timeStamp;
        this.fplmnbin = fplmnbin;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public String getKi() {
        return ki;
    }

    public void setKi(String ki) {
        this.ki = ki;
    }

    public String getOpc() {
        return opc;
    }

    public void setOpc(String opc) {
        this.opc = opc;
    }

    public String getApn() {
        return apn;
    }

    public void setApn(String apn) {
        this.apn = apn;
    }

    public boolean isRoam_enable() {
        return roam_enable;
    }

    public void setRoam_enable(boolean roam_enable) {
        this.roam_enable = roam_enable;
    }

    public int getRat() {
        return rat;
    }

    public void setRat(int rat) {
        this.rat = rat;
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getVirtual_imei() {
        return virtual_imei;
    }

    public void setVirtual_imei(String virtual_imei) {
        this.virtual_imei = virtual_imei;
    }

    public String getPlmnBin() {
        return plmnBin;
    }

    public void setPlmnBin(String plmnBin) {
        this.plmnBin = plmnBin;
    }

    public String getRateBin() {
        return rateBin;
    }

    public void setRateBin(String rateBin) {
        this.rateBin = rateBin;
    }

    public Long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public boolean isLocalUsable() {
        return isLocalUsable;
    }

    public void setLocalUsable(boolean localUsable) {
        isLocalUsable = localUsable;
    }

    public ArrayList<SoftsimUnusable> getLocalUnuseReason() {
        return localUnuseReason;
    }

    public void setLocalUnuseReason(ArrayList<SoftsimUnusable> localUnuseReason) {
        this.localUnuseReason = localUnuseReason;
    }

    public void addLocalUnusableReason(SoftsimUnusable reason){
        if(reason.getErrcode() != 0) {// err serious?
            pri += 2;
        }else {
            pri += 1;
        }
        this.localUnuseReason.add(reason);
    }

    public void clearUnusableReason(){
        pri = 0;
        this.localUnuseReason.clear();
    }

    public int getPri() {
        return pri;
    }

    public void setPri(int pri) {
        this.pri = pri;
    }

    public boolean isServerUsable() {
        return isServerUsable;
    }

    public void setServerUsable(boolean serverUsable) {
        isServerUsable = serverUsable;
    }

    @Override
    public String toString() {
        return "SoftsimLocalInfo{" +
                "imsi='" + imsi + '\'' +
                ", ki='" + ki + '\'' +
                ", opc='" + opc + '\'' +
                ", apn='" + apn + '\'' +
                ", roam_enable=" + roam_enable +
                ", rat=" + rat +
                ", iccid='" + iccid + '\'' +
                ", msisdn='" + msisdn + '\'' +
                ", virtual_imei='" + virtual_imei + '\'' +
                ", plmnBin='" + plmnBin + '\'' +
                ", rateBin='" + rateBin + '\'' +
                ", timeStamp=" + timeStamp +
                ", isLocalUsable=" + isLocalUsable +
                ", localUnuseReason=" + localUnuseReason +
                ", isServerUsable=" + isServerUsable +
                ", pri=" + pri +
                '}';
    }

    public String getFplmnRef() {
        return fplmnbin;
    }

    public void setFplmnRef(String fplmnbin) {
        this.fplmnbin = fplmnbin;
    }
}
