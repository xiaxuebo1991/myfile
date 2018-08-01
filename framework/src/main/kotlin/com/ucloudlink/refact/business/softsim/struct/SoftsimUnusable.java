package com.ucloudlink.refact.business.softsim.struct;

import java.util.Date;

/**
 * Created by shiqianhua on 2016/12/19.
 */

public class SoftsimUnusable {
    private String mcc;
    private String mnc;
    private Long date;
    private int errcode;
    private int subErr;

    public SoftsimUnusable(String mcc, String mnc, Long date, int errcode, int subErr) {
        this.mcc = mcc;
        this.mnc = mnc;
        this.date = date;
        this.errcode = errcode;
        this.subErr = subErr;
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

    public Long getDate() {
        return date;
    }

    public void setDate(Long date) {
        this.date = date;
    }

    public int getErrcode() {
        return errcode;
    }

    public void setErrcode(int errcode) {
        this.errcode = errcode;
    }

    public int getSubErr() {
        return subErr;
    }

    public void setSubErr(int subErr) {
        this.subErr = subErr;
    }

    @Override
    public String toString() {
        return "SoftsimUnusable{" +
                "mcc='" + mcc + '\'' +
                ", mnc='" + mnc + '\'' +
                ", date=" + date +
                ", errcode=" + errcode +
                ", subErr=" + subErr +
                '}';
    }
}
