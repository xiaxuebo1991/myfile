package com.ucloudlink.refact.business.netcheck

import java.util.*

/**
 * Created by chentao on 2016/8/26.
 */
data class NetInfo(
        val rat: Int,
        val imei: String,
        val cellid: Int,
        val lac: Int,
        val imsi: String,
        val iccid: String,
        val version: String,
        val signal:Int,
        var mccmnc: String,
        val sidList: ArrayList<CharSequence>
){
     fun checkNetInfoParamValid():Boolean = imsi.isNotEmpty()
             && cellid > 0
             && iccid.isNotEmpty()
             && lac > 0
             && imei.isNotEmpty()
             && version.isNotEmpty()
             && signal != -1
             && mccmnc.isNotEmpty()
             && rat >= 0
}