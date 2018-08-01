package com.ucloudlink.refact.platform.sprd.nativeapi;

/**
 * Created by shiqianhua on 2017/11/6.
 */

public interface SprdNativeIntf {
    byte[] cb(int slot, byte[] apdu_req);
}
