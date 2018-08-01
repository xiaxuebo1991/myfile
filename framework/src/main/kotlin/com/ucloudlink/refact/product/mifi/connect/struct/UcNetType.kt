package com.ucloudlink.refact.product.mifi.connect.struct


/**
 * Created by zhifeng.gao on 2018/4/19.
 */

val RADIO_UNKNOWN = 0
val RADIO_GPRS = 1
val RADIO_EDGE = 2
val RADIO_UMTS = 3
val RADIO_IS95A = 4                    //cdma A
val RADIO_IS95B = 5                   //cdma B
val RADIO_1xRTT = 6
val RADIO_EVDO_0 = 7
val RADIO_EVDO_A = 8
val RADIO_HSDPA = 9
val RADIO_HSUPA = 10
val RADIO_HSPA = 11
val RADIO_EVDO_B = 12
val RADIO_EHRPD = 13
val RADIO_LTE = 14
val RADIO_HSPAP = 15                   //HSPA+
val RADIO_GSM = 16                     //Only supports voice
val RADIO_TD_SCDMA = 17
val RADIO_IWLAN = 18

data class UcNetType( var radio_tech:Int)