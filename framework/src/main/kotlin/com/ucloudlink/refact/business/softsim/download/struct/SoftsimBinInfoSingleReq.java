package com.ucloudlink.refact.business.softsim.download.struct;

/**
 * Created by shiqianhua on 2016/12/29.
 */

public class SoftsimBinInfoSingleReq {
    private int type; // 1: plmn  2:fee  3:fplmn
    private String ref;

    public SoftsimBinInfoSingleReq(int type, String ref) {
        this.type = type;
        this.ref = ref;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public String toString() {
        return "SoftsimBinInfoSingleReq{" +
                "type=" + type +
                ", ref='" + ref + '\'' +
                '}';
    }
}
