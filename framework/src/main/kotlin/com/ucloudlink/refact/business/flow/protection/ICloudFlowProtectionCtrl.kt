package com.ucloudlink.refact.business.flow.protection

import com.ucloudlink.refact.channel.enabler.IDataEnabler

/**
 * Created by jianguo.he on 2018/2/5.
 */
interface ICloudFlowProtectionCtrl {

    fun getICloudFlowProtection(): ICloudFlowProtection

    fun init(cloudSimEnabler: IDataEnabler)

    fun setRetrict(tag: String?)

    fun clearRetrict(tag: String?)

    fun updateXML(xml: String)

//    /** 开启流量防护功能 */
//    fun enableFlowfilter()
//
//    /** 关闭流量防护功能 */
//    fun disableFlowfilter()
//
//    /** 获取流量防护功能的状态 */
//    fun isFlowfilterEnabled(): Boolean

}