package com.ucloudlink.refact.product.mifi.PhyCardApn;

/**
 * Created by yongxin.zhang on 2018/5/28.
 */

public interface ApnNetworkErrCB {
    void networkErrUpdate(int phoneId, int errcode);
}
