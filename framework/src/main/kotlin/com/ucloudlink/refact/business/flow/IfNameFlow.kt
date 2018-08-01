package com.ucloudlink.refact.business.flow

/**
 * Created by jianguo.he on 2018/1/22.
 */
interface IfNameFlow {

    fun getIfNameTxBytes(ifName: String?): Long

    fun getIfNameRxBytes(ifName: String?): Long

    /**
     * 获取iface数据，字符串形式返回
     * @param ifName 网口名称， 需要形成路径：/sys/kernel/uck/flowstat/<iface>/fstats/iface
     * @return  [<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>]
     */
    fun getIfNameArrayBytes(ifName: String?): String?

    /***
     * 根据uid获取iface数据，字符串形式返回
     * 前置条件：setReadBytesUids(ifName, strUids)
     * @param ifName 网口名称， 需要形成路径：/sys/kernel/uck/flowstat/<iface>/fstats/iface
     * @return  [<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>][<uid1>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
                 [<uid2>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]...[<uidn>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
     */
    fun getUidsArrayBytes(ifName: String?): String?

    /***
     * 获取所有的uid的数据
     * @param ifName 网口名称， 需要形成路径：/sys/kernel/uck/flowstat/<iface>/fstats/iface
     * @return  [<total txbytes>,<total txpkts>,<total rxbytes>,<total rxpkts>,<local txbytes>,<local tx pkts>,<local rx bytes>,<local rx pkts>][<uid1>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
    [<uid2>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]...[<uidn>,<tx bytes>,<tx pkts>,<rx bytes>,<rx pkts>]
     */
    fun getAllUidArrayBytes(ifName: String?): String?

    /**
     * 设置需要读取的uid的数据
     *  /sys/kernel/uck/flowstat/<iface>/fstats/iface_uids
        uid写入：<uid1>,<uid2>,...,<uidn>
     * @param ifName 网口名称， 需要形成路径：/sys/kernel/uck/flowstat/<iface>/fstats/iface
     */
    fun setReadBytesUids(ifName: String?, strUids: String?): Unit

}