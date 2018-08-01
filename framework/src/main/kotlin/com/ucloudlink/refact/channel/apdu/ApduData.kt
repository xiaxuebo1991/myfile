package com.ucloudlink.refact.channel.apdu

/**
 * Created by chentao on 2016/8/24.
 */
//data class ApduData(val data:ByteArray)
//wlmark modify data struct
data class ApduData(val imsi:String,val apduData:ByteArray,val fieldId:ByteArray? = null)   