package com.ucloudlink.refact.product.mifi.flow.protection.entity;

/**
 * Created by ouyangjunchao on 2016/3/1.
 */

public class ProCfgResponseData {
    //0:关闭， 1：打开
    private int enable;

    private String version;

    private String url;

    private String msg;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getEnable() {
        return enable;
    }

    public void setEnable(int enable) {
        this.enable = enable;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "ProCfgResponseData{" +
                "enable=" + enable +
                ", version='" + version + '\'' +
                ", url='" + url + '\'' +
                ", msg='" + msg + '\'' +
                '}';
    }
}
