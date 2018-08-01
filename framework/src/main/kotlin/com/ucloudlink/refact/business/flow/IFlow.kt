package com.ucloudlink.refact.business.flow

/**
 * Created by jianguo.he on 2018/1/22.
 */
interface IFlow: IfNameFlow {

    fun getTotalTxBytes(ifName: String?): Long

    fun getTotalRxBytes(ifName: String?): Long

    fun getMobileTxBytes(ifName: String?): Long

    fun getMobileRxBytes(ifName: String?): Long

    fun getUidTxBytes(ifName: String?, uid: Int): Long

    fun getUidRxBytes(ifName: String?, uid: Int): Long
}