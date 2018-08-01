package com.ucloudlink.refact.product.mifi.downloadcfg;

/**
 * Created by ouyangjunchao on 2016/11/9.
 */
public class RespListData {

    private String funType;
    private String version;
    private int status;
    private String resUrl;
    private String md5;

    public String getFunType() {
        return funType;
    }

    public String getVersion() {
        return version;
    }

    public String getResUrl() {
        return resUrl;
    }

    public int getStatus() {
        return status;
    }

    public String getMd5num() {
        return md5;
    }

    @Override
    public String toString() {
        return "RespListData{" +
                "funType='" + funType + '\'' +
                ", version='" + version + '\'' +
                ", status=" + status +
                ", resUrl='" + resUrl + '\'' +
                ", md5num='" + md5 + '\'' +
                '}';
    }
}
