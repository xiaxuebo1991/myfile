package com.ucloudlink.refact.business.flow.speedlimit;

/**
 * Created by jianguo.he on 2017/8/7.
 */

public class NetSpeedInfo {

    public boolean isIP;        // true -: IP限速， false -: uid限速
    public boolean isSet;      // true -: 不限速， false -: 限速
    public int uid = - 1;       // uid 值
    public String ip;           // ip值
    public long txBytes;       // 上行限速值, 暂时不支持
    public long rxBytes;       // 下行限速值， 暂时不支持

    public String getBindKey(){
        return  "" + isIP + "," + ip + "," + isSet + "," + uid + "," + txBytes + "," + rxBytes;
    }
    public static NetSpeedInfo as(boolean isIP, boolean isSet, int uid, String ip, long txBytes, long rxBytes){
        NetSpeedInfo info = new NetSpeedInfo();
        info.isIP = isIP;
        info.isSet = isSet;
        info.uid = uid;
        info.ip = ip;
        info.txBytes = txBytes;
        info.rxBytes = rxBytes;
        return  info;
    }

    @Override
    public String toString() {
        return "NetSpeedInfo{" +
                ", isIP=" + isIP +
                ", isSet=" + isSet +
                ", uid=" + uid +
                ", ip='" + ip + '\'' +
                ", txBytes=" + txBytes +
                ", rxBytes=" + rxBytes +
                '}';
    }
}
