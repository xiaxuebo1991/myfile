package com.ucloudlink.refact.business.softsim.download.remote.struct;

/**
 * Created by shiqianhua on 2016/12/29.
 */

public class LoginNetworkInfo {
    private String imei;
    private String seedImsi;
    private int cellid;
    private int lac;
    private String iccid;
    private String plmn;
    private int rat;

    public LoginNetworkInfo(){

    }

    public LoginNetworkInfo(String imei, String seedImsi, int cellid, int lac, String iccid, String plmn, int rat) {
        this.imei = imei;
        this.seedImsi = seedImsi;
        this.cellid = cellid;
        this.lac = lac;
        this.iccid = iccid;
        this.plmn = plmn;
        this.rat = rat;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getSeedImsi() {
        return seedImsi;
    }

    public void setSeedImsi(String seedImsi) {
        this.seedImsi = seedImsi;
    }

    public int getCellid() {
        return cellid;
    }

    public void setCellid(int cellid) {
        this.cellid = cellid;
    }

    public int getLac() {
        return lac;
    }

    public void setLac(int lac) {
        this.lac = lac;
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    public String getPlmn() {
        return plmn;
    }

    public void setPlmn(String plmn) {
        this.plmn = plmn;
    }

    public int getRat() {
        return rat;
    }

    public void setRat(int rat) {
        this.rat = rat;
    }

    @Override
    public String toString() {
        return "LoginNetworkInfo{" +
                "imei='" + imei + '\'' +
                ", seedImsi='" + seedImsi + '\'' +
                ", cellid=" + cellid +
                ", lac=" + lac +
                ", iccid='" + iccid + '\'' +
                ", plmn='" + plmn + '\'' +
                ", rat=" + rat +
                '}';
    }
}
