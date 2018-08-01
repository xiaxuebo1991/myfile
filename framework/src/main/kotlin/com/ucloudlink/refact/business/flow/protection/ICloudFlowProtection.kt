package com.ucloudlink.refact.business.flow.protection

/**
 * Created by jianguo.he on 2018/3/1.
 */
interface ICloudFlowProtection {

    /** 开启流量防护功能 */
    fun enableFlowfilter()

    /** 关闭流量防护功能 */
    fun disableFlowfilter()

    /** 获取流量防护功能的状态 */
    fun isFlowfilterEnabled(): Boolean

    /**
     * 设置域名规则
     * @param domain  指定的域名或者字符串
     * @param protocol tcp or udp, 域名一般设置为udp
     * @param port 端口号，设置为53
     * @param type 1: 表示全字符串匹配;
     *              2: 表示16进制的全字符串匹配，就是固定的域名，如指定baidu.com，则只禁用baidu.com
     *              3: 表示部分字符串匹配
     *              4: 表示16进制部分字符串匹配，如baidu.com则像map.baidu.com这样的域名都无法访问
     * @param allow true: 表示从链中删除规则;
     *               false: 表示增加规则到链中;
     * @return 0: 成功;  -1: 失败
     */
    fun setFlowfilterDomainRule(domain: String?, protocol: String?, port: Int, type: Int, allow: Boolean): Int
    /**
     * 设置mac规则，可以通过该函数禁止mac访问网络
     * @param mac 设备的网卡地址
     * @param allow true: 表示从链中删除规则;
     *               false: 表示增加规则到链中
     * @return 成功返回0, 失败返回-1
     */
    fun setFlowfilterMacRule(mac: String?, allow: Boolean): Int
    /**
     * 设置网口规则，可以通过该函数禁止网卡访问网络
     * @param ifName 网卡名
     * @param allow true: 表示从链中删除规则;
     *               false: 表示增加规则到链中
     * @return 成功返回0, 失败返回-1
     */
    fun setFlowfilterInterfaceRule(ifName: String?, allow: Boolean): Int

    /**
     * 设置ip规则，可以通过该函数禁止设备访问某一ip
     * @param addr ip地址
     * @param allow true: 表示从链中删除规则;
     *               false: 表示增加规则到链中
     * @return 成功返回0, 失败返回-1
     */
    fun setFlowfilterAddrRule(addr: String?, allow: Boolean): Int

    /**
     * 设置端口规则，可以通过该函数禁止设备使用某一服务
     * @param protocol TCP,UDP,ICMP
     * @param port 端口号
     * @param allow true: 表示从链中删除规则;
     *               false: 表示增加规则到链中
     * @return 成功返回0, 失败返回-1
     */
    fun setFlowfilterPortRule(protocol: String?, port: Int, allow: Boolean): Int

    /**
     * 设置网卡的出站规则，对某一ip更加精确的设置就是用该函数
     * @param addr ip地址
     * @param protocol TCP,UDP,ICMP
     * @param port 端口号
     * @param allow true: 表示从链中删除规则;
     *               false: 表示增加规则到链中
     * @return 成功返回0, 失败返回-1
     */
    fun setFlowfilterEgressDestRule(addr: String?, protocol: String?, port: Int, allow: Boolean): Int

    /**
     * 清除所有设置的规则，用于防护更新
     * @return 0: 成功;   -1：失败;
     */
    fun clearAllRules(): Int

}