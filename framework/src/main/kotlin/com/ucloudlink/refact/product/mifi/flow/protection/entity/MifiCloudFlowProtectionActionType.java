package com.ucloudlink.refact.product.mifi.flow.protection.entity;

/**
 * Created by jianguo.he on 2018/2/6.
 */

public enum MifiCloudFlowProtectionActionType {

    IP_SPEEDLIMIT(1),

    IP_BLOCK(2),

    DNS_SPEEDLIMIT(3),

    DNS_BLOCK(4);

    public int type;

    MifiCloudFlowProtectionActionType(int type){
        this.type = type;
    }
}
