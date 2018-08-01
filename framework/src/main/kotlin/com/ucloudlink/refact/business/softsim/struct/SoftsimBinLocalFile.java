package com.ucloudlink.refact.business.softsim.struct;

import java.util.Arrays;

/**
 * Created by shiqianhua on 2016/12/28.
 */

public class SoftsimBinLocalFile {
    private String ref;
    private int type; // 1: plmn  2:EXPENSES_BIN
    private byte[] data;

    public SoftsimBinLocalFile(String ref, int type, byte[] data) {
        this.ref = ref;
        this.type = type;
        this.data = data;
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "SoftsimBinFile{" +
                "ref='" + ref + '\'' +
                ", type=" + type +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
