package com.ucloudlink.refact.business.flow

/**
 * Created by jianguo.he on 2018/1/19.
 */
interface ICloudFlowCtrl {
    // TODO 下一步需要把流量相关的接口抽成与 TrafficStats 类一样

    fun initStats()
    fun resetTmpSd()

    fun enableSeedSimCard()
    fun disableSeedSimCard()
    fun enableCloudSimCard()
    fun disableCloudSimCard()

    fun startStats(statis_mode: Int, curUid: Int)
    fun stopStats(statis_mode: Int, curUid: Int)
    fun getStats(statsStatus: Int, statis_mode: Int, logid: Int, curUid: Int, dataChannelCardType: Int): StatsData?

    fun getSeedTxFlow(): Long
    fun getSeedRxFlow(): Long

    fun setIfName(ifName: String?)
    fun getIfName(): String?

}