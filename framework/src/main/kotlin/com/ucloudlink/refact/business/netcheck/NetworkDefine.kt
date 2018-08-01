package com.ucloudlink.refact.business.netcheck

/**
 * Created by chunjiao.li on 2016/10/17.
 */

const val RAT_TYPE_UNKNOW: Int = -1
const val RAT_TYPE_2G: Int = 0
const val RAT_TYPE_3G: Int = 1
const val RAT_TYPE_4G: Int = 2

//0:CDMA;1:HDR;2:GSM;3:WCDMA;4:LTE;5:TDS
const val RAT_TYPE_CDMA = 0
const val RAT_TYPE_HDR = 1
const val RAT_TYPE_GSM = 2
const val RAT_TYPE_WCDMA = 3
const val RAT_TYPE_LTE = 4
const val RAT_TYPE_TDS = 5


//signalStrength
const val SIGNAL_STRENGTH_NONE: Int = 0
const val SIGNAL_STRENGTH_POOR: Int = 1
const val SIGNAL_STRENGTH_MODERATE: Int = 2
const val SIGNAL_STRENGTH_GOOD: Int = 3
const val SIGNAL_STRENGTH_GREAT: Int = 4
const val SIGNAL_STRENGTH_GREAT_MORE: Int = 5
//转换为ucloudlink使用范围
const val SIGNAL_STRENGTH_UC_NONE: Int = 3
const val SIGNAL_STRENGTH_UC_POOR: Int = 9
const val SIGNAL_STRENGTH_UC_MODERATE: Int = 14
const val SIGNAL_STRENGTH_UC_GOOD: Int = 21
const val SIGNAL_STRENGTH_UC_GREAT: Int = 25
const val SIGNAL_STRENGTH_UC_GREAT_MORE: Int = 27
const val SIGNAL_STRENGTH_UC_UNKNOWN: Int = 5

// RADIO TECH和TelephonyManager里面的移动网络制式数值定义不一致，
// 见源码/hardware/ril/include/telephony/ril.h定义 和TelephonyManager.java源码
const val RADIO_TECH_UNKNOWN = 0
const val RADIO_TECH_GPRS = 1
const val RADIO_TECH_EDGE = 2
const val RADIO_TECH_UMTS = 3
const val RADIO_TECH_IS95A = 4
const val RADIO_TECH_IS95B = 5
const val RADIO_TECH_1xRTT =  6
const val RADIO_TECH_EVDO_0 = 7
const val RADIO_TECH_EVDO_A = 8
const val RADIO_TECH_HSDPA = 9
const val RADIO_TECH_HSUPA = 10
const val RADIO_TECH_HSPA = 11
const val RADIO_TECH_EVDO_B = 12
const val RADIO_TECH_EHRPD = 13
const val RADIO_TECH_LTE = 14
const val RADIO_TECH_HSPAP = 15 // HSPA+
const val RADIO_TECH_GSM = 16 // Only supports voice
const val RADIO_TECH_TD_SCDMA = 17
const val RADIO_TECH_IWLAN = 18
const val RADIO_TECH_LTE_CA = 19	//set by lcj 不一定准确


