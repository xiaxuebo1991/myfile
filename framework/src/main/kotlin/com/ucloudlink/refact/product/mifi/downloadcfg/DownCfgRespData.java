package com.ucloudlink.refact.product.mifi.downloadcfg;

import java.util.ArrayList;

/**
 * Created by ouyangjunchao on 2016/11/9.
 */
public class DownCfgRespData {

    private String streamNo;
    private String resultCode;
    private String resultDesc;
    private ArrayList<RespListData> data;

    public String getStreamNo() {
        return streamNo;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultDesc() {
        return resultDesc;
    }

    public ArrayList<RespListData> getRespListDatas() {
        return data;
    }

    @Override
    public String toString() {
        return "DownCfgRespData{" +
                "streamNo='" + streamNo + '\'' +
                ", resultCode='" + resultCode + '\'' +
                ", resultDesc='" + resultDesc + '\'' +
                ", data=" + data.toString() +
                '}';
    }
}
