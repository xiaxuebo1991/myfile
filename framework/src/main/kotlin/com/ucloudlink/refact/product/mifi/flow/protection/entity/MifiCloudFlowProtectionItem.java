package com.ucloudlink.refact.product.mifi.flow.protection.entity;

/**
 * Created by jianguo.he on 2018/2/5.
 */

public class MifiCloudFlowProtectionItem {

    /**
     ruleid=1
     flowname=baiduyun
     protocol=udp
     dnsreq=pcs.baidu.com
     dport=53
     cmpmode=2
     #actiontype  1 ip-speedlimit, 2 ip block, 3 dns speedlimit, 4 dns block
     actiontype=4
     */
    /***
     * cmpmode 的 值 对应 setBlockDomain()时的type值
     * actiontype 目前只支持 2， 4 值
     */

    public int ruleid;
    public String flowname;
    public String sip;                   // 源ip
    public String dip;                   // 目的ip
    public int sport;                   //  源port
    public int dport;                   //  目的port
    public String protocol_type;       //  协议类型 如： udp, tcp
    public int actiontype;             // 操作类型, 1,2,3,4
    public String dnsreq;
    public int dnsreqlen;
    public int cmpmode;
    public int speedlimit;
    public int dnsreqdone;
    public int learnip;
    public int trytimes;

    @Override
    public String toString() {
        return "MifiCloudFlowProtectionItem{" +
                "ruleid=" + ruleid +
                ", flowname='" + flowname + '\'' +
                ", sip='" + sip + '\'' +
                ", dip='" + dip + '\'' +
                ", sport=" + sport +
                ", dport=" + dport +
                ", protocol_type='" + protocol_type + '\'' +
                ", actiontype=" + actiontype +
                ", dnsreq='" + dnsreq + '\'' +
                ", dnsreqlen=" + dnsreqlen +
                ", cmpmode=" + cmpmode +
                ", speedlimit=" + speedlimit +
                ", dnsreqdone=" + dnsreqdone +
                ", learnip=" + learnip +
                ", trytimes=" + trytimes +
                '}';
    }
}
