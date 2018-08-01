package com.ucloudlink.refact.platform.sprd.nativeapi;

/**
 * Created by shiqianhua on 2018/1/11.
 */

public class SprdNative {
    public static native int vsim_init(int phoneId, SprdNativeIntf cb, int mode);
    public static native int vsim_exit(int phoneId);
    public static native int vsim_set_authid(int authId);
    public static native int vsim_query_authid();
    public static native int vsim_set_virtual(int phoneId, int mode);
    public static native int vsim_query_virtual(int phoneId);
    public static native String send_at_cmd(int phoneId, String cmd); // 会阻塞，时间不确定
    public static native int vsim_get_auth_cause(int slot);
    public static native int vsim_set_timeout(int time);
    public static native int vsim_set_nv(int phoneId, int type, int isWrite);  // vsim_set_virtual 替换接口，isWrite true表示modem保存虚拟卡配置
}
