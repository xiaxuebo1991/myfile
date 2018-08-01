package com.ucloudlink.refact.business.flow.speedlimit

/**
 * Created by jianguo.he on 2018/1/9.
 * 限速具有的接口
 */
interface INetSpeed {

    /**
     * U3C上限速不需要设置白名单
     * 所以以下接口在U3C上没有
     * setFlowPermitByPassIpstr()
     * clearFlowPermitByPassIpstr()
     * setFlowPermitByPassUid()
     * clearFlowPermitByPassUid()
     *
     * 只在U3C上有的接口：
     * getIfaceThrottleStates()
     * getIfaceSpeed()
     */

    /**
     * 获取网卡的限速状态
     * @param ifName 网卡名
     * @return  0： 成功， -1： 失败
     */
    fun getIfaceThrottleStates(ifName: String?): Int

    /**
     * 获取网络所设置的速率
     * @param ifName
     * @return up:down 如 128:256
     */
    fun getIfaceSpeed(ifName: String?): Array<String?>

    /** 该接口通过SystemProperties设置
     * 查看：adb shell getprop |grep dns*/
    fun getCloudInterfaceName(): String?

    /**
     * 设置限速
     */
    fun setInterfaceThrottle(ifName: String?, rxKbps: Long, txKbps: Long): Int

    /**
     * 清除限速
     */
    fun resetInterfaceThrottle(ifName: String?, rxKbps: Long, txKbps: Long): Int

    /**
     * 设置指定 ip 流量限速放通，即：ip 流量不限速访问
     */
    fun setFlowPermitByPassIpstr(ip: String?): Int

    /**
     * 清除指定 ip 流量限速放通，即：ip 流量限速访问
     */
    fun clearFlowPermitByPassIpstr(ip: String?): Int

    /**
     * 设置指定 uid 流量限速放通，即：uid 流量不限速访问
     */
    fun setFlowPermitByPassUid(uid: Int): Int

    /**
     * 清除指定 uid 流量限速放通，即：uid 流量限速访问
     */
    fun clearFlowPermitByPassUid(uid: Int): Int

}