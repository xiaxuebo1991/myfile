package com.ucloudlink.framework.flow

import com.ucloudlink.refact.business.flow.netlimit.common.SeedCardNetInfo
import com.ucloudlink.refact.channel.enabler.IDataEnabler

/**
 * Created by jianguo.he on 2017/11/21.
 */
interface ISeedCardNetCtrl {

    /** 初始化 */
    fun initState(seedSimEnable: IDataEnabler, cloudSimEnabler: IDataEnabler): Unit

    /**
     * 设置限制上网
     */
    fun setRestrictAllNet(tag: String?): Unit

    /**
     * 清除限制上网
     */
    fun clearRestrictAllRuleNet(tag: String?): Unit

    /**
     * 添加dns 或者 ip
     */
    fun configDnsOrIp(info: SeedCardNetInfo): Unit

}