package com.ucloudlink.framework.flow

/**
 * Created by jianguo.he on 2017/11/20.
 * 限制/放开 种子卡网络
 */
interface ISeedCardNet {

    /**
     * 绑定链
     * 限制指定网口上网
     * @param iface 接口名称 rmnet_data0 \ rmnet_data1
     * @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
    fun setRestrictAllNetworks(ifName: String?) : Int

    /**
     * 解绑链
     * 移除指定网口的限制上网
     * @param iface 接口名称 rmnet_data0 \ rmnet_data1
     * @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
    fun removeRestrictAllNetworks(ifName: String?) : Int

    /**
     * 设置规则
     * 允许指定ip上网
     * @param iface 接口名称 rmnet_data0 \ rmnet_data1
     * @param uid
     * @param ip  指定ip
     * @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
    fun setNetworkPassByIp(ifName: String?, uid: Int, ip: String?) : Int

    /**
     * 移除规则
     * 移除允许指定ip上网
     * @param iface  接口名称 rmnet_data0 \ rmnet_data1
     * @param ip 指定ip
     * @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
     fun removeNetworkPassByIp(ifName: String?, uid: Int, ip: String?) : Int

    /**
     * 设置规则
     * 允许指定域名的dns
     * @param iface 接口名称 rmnet_data0 \ rmnet_data1
     * @param uid
     * @param domain  指定dns域名
     * @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
    fun setEnableDNSByDomain(ifName: String?, uid: Int, domain: String?): Int

    /**
     * 移除规则
     * 移除允许指定域名的dns
     * @param iface 接口名称 rmnet_data0 \ rmnet_data1
     * @param uid
     * @param domain 指定dns域名
     * @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
    fun removeEnableDNSByDomain(ifName: String?, uid: Int, domain: String?) : Int

    /**
     * 移除指定iface下所有新接口的规则
     * 即：执行完该接口后,参数iface下所有app都可上网，所有dns都可被解析, 并且通过iface设置的ip,dns记录会被删除
     * @param ifName 接口名称 rmnet_data0 \ rmnet_data1
     @return 0 -: 命令下发成功,不一定生效;  其他值 -: 命令下发失败
     */
    fun clearRestrictAllRule(ifName: String?) : Int

}