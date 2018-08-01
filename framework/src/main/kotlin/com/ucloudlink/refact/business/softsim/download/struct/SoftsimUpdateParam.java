package com.ucloudlink.refact.business.softsim.download.struct;

/**
 * Created by shiqianhua on 2017/7/12.
 */

public class SoftsimUpdateParam {
    public String username;
    public String curImsi;
    public String mcc;
    public String mnc;

    public SoftsimUpdateParam(String username, String curImsi, String mcc, String mnc) {
        this.username = username;
        this.curImsi = curImsi;
        this.mcc = mcc;
        this.mnc = mnc;
    }

    @Override
    public String toString() {
        return "SoftsimUpdateParam{" +
                "username='" + username + '\'' +
                ", curImsi='" + curImsi + '\'' +
                ", mcc='" + mcc + '\'' +
                ", mnc='" + mnc + '\'' +
                '}';
    }
}
