package com.ucloudlink.refact.business.flow.netlimit.common;

import java.io.Serializable;

/**
 * Created by jianguo.he on 2017/11/22.
 */

public class SeedCardNetInfo implements Serializable {

    private static final long serialVersionUID = 8442943205004777506L;
    /**
     * true -: 打开;  false -: 关闭
     */
    public boolean enable;

    /**
     * 通用属性，目前已配置的有：dns, ip, ifName
     */
    public String vl;

    public int uid = -1;

    public SeedCardNetInfo(boolean enable, String vl, int uid){
        this.enable = enable;
        this.vl = vl;
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "SeedCardNetInfo{" +
                "enable=" + enable +
                ", vl='" + vl + '\'' +
                ", uid=" + uid +
                '}';
    }
}
