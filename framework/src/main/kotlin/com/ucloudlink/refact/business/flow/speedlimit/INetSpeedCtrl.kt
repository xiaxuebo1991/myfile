package com.ucloudlink.refact.business.flow.speedlimit

import android.net.ConnectivityManager

/**
 * Created by jianguo.he on 2018/1/10.
 */
interface INetSpeedCtrl {

    fun getINetSpeed(): INetSpeed

    /**
     *
     * @param ip         // ip 或 域名
     * @param uid        // app的uid值
     * @param isIp       // true-通过ip控制流量， false-通过uid控制流量
     * @param isSet      // true-放通流量， false-限制流量
     * @param isEnable   // 是否开关流量控制
     */
    fun configNetSpeedWithIP(ip: String?, uid: Int, isIp: Boolean, isSet: Boolean, isEnable: Boolean, txBytes: Long, rxBytes: Long )

    fun configNetworkBandWidth(jsonStr: String?)

    /**
     * 在云卡联网时，检测保存路由切换等情况下的dns域名
     */
    fun checkDnsOnNewThread()

    /**
     * register ConnectivityManager.NetworkCallback
     * @param type - (目前为预留的类型值)
     * @param tag - (目前为预留的标记值)
     */
    fun registerNetworkCallback(type: Int, tag: String?)

    /**
     * unRegister ConnectivityManager.NetworkCallback
     * @param type - (目前为预留的类型值)
     * @param tag - (目前为预留的标记值)
     */
    fun unRegisterNetworkCallback(type: Int, tag: String?)

    fun addNetworkCallbackListener(networkCallback: ConnectivityManager.NetworkCallback)
    fun removeNetworkCallbackListener(networkCallback: ConnectivityManager.NetworkCallback)

}